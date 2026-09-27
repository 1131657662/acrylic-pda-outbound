package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Correction operations. Step 3 opens with CREATE: freeze the document body into a SUBMITTED document plus its lines.
 *
 * <p>Target values are recomputed <b>server-side</b>: the client may state the target it wants, but the "before" side
 * of every line is read from the current version's facts (dispatch + its ledger row) rather than trusted from the
 * client, and a line whose target equals that before value is refused. Effects, the result version, the ledger, the
 * cycle reopening and the reconciliation cases are the later steps - this operation writes no accounting and changes
 * no result version.
 */
public final class CorrectionOperations {
    private CorrectionOperations() { }

    /**
     * Header keys the server owns: receipt identity plus every fact that lines, effects or accounting already express.
     * They are refused even though the snapshot carries them, so a "header" correction can never smuggle a count, a
     * weight or a scan fact past the line model. Patch values must be scalars for the same reason. Validated at CREATE
     * (fail fast against the then-current snapshot) and again at APPROVE (the snapshot may have moved since).
     */
    private static final Set<String> SERVER_OWNED_HEADER_KEYS=Set.of("submissionId","source","orderId","status",
        "payloadSha256","freezeDeviceId","workRound","issuedRecoveryEpoch","resultVersion","receiptVersion",
        "actualCount","actualWeightGrams","inventoryDeducted","items","signedReceipt","firstOrderScanSeq",
        "lastOrderScanSeq","completedAt","captureHighWatermark","mayResumeScanning","stockReleased","orderStatus");

    /** Merge a header patch into the snapshot: unknown keys are refused, owned keys are refused, no-op is refused. */
    private static String mergedHeader(String snapshot,JsonNode patch) {
        var current=com.acrylic.outbound.submissions.SubmissionJson.parse(snapshot);
        com.fasterxml.jackson.databind.node.ObjectNode merged=
            (com.fasterxml.jackson.databind.node.ObjectNode)current.deepCopy();
        boolean changed=false;
        var fields=patch.fields();
        while(fields.hasNext()) {
            var field=fields.next();
            String key=field.getKey();
            if(!current.has(key))
                throw new BusinessException(400,"INVALID_CORRECTION_COMMAND","表头字段不存在："+key);
            if(SERVER_OWNED_HEADER_KEYS.contains(key))
                throw new BusinessException(400,"INVALID_CORRECTION_COMMAND","该表头字段由服务端持有，不能更正："+key);
            if(!field.getValue().isValueNode())
                throw new BusinessException(400,"INVALID_CORRECTION_COMMAND","表头更正值必须是标量："+key);
            if(!current.get(key).equals(field.getValue())) { merged.replace(key,field.getValue()); changed=true; }
        }
        if(!changed) throw CorrectionCommand.targetUnchanged();
        return com.acrylic.outbound.submissions.SubmissionJson.canonical(merged);
    }

    public static final class Create implements CorrectionCommand.Operation {
        private static final Duration BACKFILL_WINDOW = Duration.ofDays(3);
        private static final long ATTACHMENT_MAX_BYTES = 10L*1024*1024;
        private static final long ATTACHMENTS_MAX_BYTES = 50L*1024*1024;
        private static final Set<String> PHYSICAL = Set.of("PHYSICAL_RETURN", "DISPATCH_CANCELLATION");

        /** P-R07-09 capacity breach, with the design's own code so the client can tell it from a shape error. */
        private static BusinessException attachmentLimit(String message) {
            return new BusinessException(400,"ATTACHMENT_LIMIT_EXCEEDED",message);
        }

        private final CorrectionStore store;
        public Create(CorrectionStore store) { this.store=store; }

        @Override public String commandType() { return "CREATE"; }

        @Override public JsonNode execute(CorrectionCommand.Write write) {
            var body=write.command().body();
            String type=text(body,"type","");
            String reason=text(body,"reason","");
            if(reason.isBlank() || reason.length()>500) throw new BusinessException(400,"INVALID_CORRECTION_COMMAND","更正原因必须为 1..500 字");
            if(!Set.of("PHYSICAL_RETURN","DISPATCH_CANCELLATION","WEIGHT_CORRECTION","HEADER_CORRECTION").contains(type))
                throw new BusinessException(400,"INVALID_CORRECTION_COMMAND","更正类型无效");
            var lines=body.path("lines");
            boolean header="HEADER_CORRECTION".equals(type);
            var patch=body.path("headerPatch");
            if(header) {
                // A header correction carries no numeric lines: it patches the frozen receipt that IS the header
                // snapshot. The patch lives inside the signed command bytes, so nothing extra has to be stored.
                if(!patch.isObject() || patch.isEmpty())
                    throw new BusinessException(400,"INVALID_CORRECTION_COMMAND","表头更正必须给出非空 headerPatch");
                if(lines.isArray() && !lines.isEmpty())
                    throw new BusinessException(400,"INVALID_CORRECTION_COMMAND","表头更正不接受明细行");
                // Validate the patch against the current snapshot now, so a document is never created for a patch that
                // could only fail later (unknown key, server-owned key, non-scalar, no-op).
                mergedHeader(store.currentHeaderSnapshot(write.orderId()),patch);
            } else if(!lines.isArray() || lines.isEmpty() || lines.size()>200)
                throw new BusinessException(400,"INVALID_CORRECTION_COMMAND","更正行必须为 1..200 条");
            long expectedVersion=write.anchor().path("currentResultVersion").asLong();
            // P-R07-06 compares business time against the ORIGINAL shipment day, never against a later correction.
            Instant shipment=store.shipmentCreatedAt(write.orderId());
            UUID documentId=Ids.next();
            // The document row must exist before its lines: correction_line.document_id is a real foreign key.
            String digest=write.command().digest();
            store.insertDocument(documentId,UUID.fromString(write.actor().path("warehouseId").asText()),
                write.orderId(),type,expectedVersion,reason,digest,"SUBMITTED",
                write.anchor().path("sourceRecoveryEpoch").asLong(1),
                UUID.fromString(write.actor().path("principalId").asText()),write.recordedAt());
            List<ObjectNode> decided=new ArrayList<>();
            long attachmentBytes=0;
            int lineNo=1;
            for(var line:header?com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode():lines) {
                UUID dispatchId=uuid(line,"dispatchId");
                var facts=dispatchId==null?null:store.dispatchFacts(write.orderId(),dispatchId);
                long beforeCount=facts==null?0:facts.path("count").asLong();
                long beforeWeight=facts==null?0:facts.path("weightG").asLong();
                long targetCount=number(line,"targetCount",beforeCount);
                long targetWeight=number(line,"targetWeightG",beforeWeight);
                if(targetCount<0 || targetWeight<0) throw new BusinessException(400,"INVALID_CORRECTION_COMMAND","目标值不能为负");
                if(targetCount==beforeCount && targetWeight==beforeWeight) throw CorrectionCommand.targetUnchanged();
                Instant occurred=occurredAt(line,write.recordedAt());
                requireInsideWindow(occurred,shipment,write.recordedAt());
                UUID evidenceId=uuid(line,"evidenceId");
                String evidenceSha=text(line,"evidenceSha256",null);
                if(dispatchId!=null && PHYSICAL.contains(type) && (evidenceId==null || evidenceSha==null))
                    throw CorrectionCommand.evidenceRequired();
                // P-R07-09 capacity: at most 5 attachments per line, at most 10MB each, at most 50MB per command.
                var attachments=line.path("attachments");
                if(!attachments.isMissingNode() && !attachments.isNull()) {
                    if(!attachments.isArray() || attachments.isEmpty() || attachments.size()>5)
                        throw attachmentLimit("单条凭据最多 5 个附件");
                    long lineBytes=0;boolean referenced=false;
                    for(var attachment:attachments) {
                        long size=number(attachment,"sizeBytes",-1);
                        String sha=text(attachment,"evidenceSha256",null);
                        if(size<0 || sha==null) throw new BusinessException(400,"INVALID_CORRECTION_COMMAND","附件项必须给出 evidenceSha256 与 sizeBytes");
                        if(size>ATTACHMENT_MAX_BYTES) throw attachmentLimit("单个附件不得超过 10MB");
                        lineBytes+=size;
                        if(sha.equals(evidenceSha)) referenced=true;
                    }
                    if(evidenceSha!=null && !referenced)
                        throw new BusinessException(400,"INVALID_CORRECTION_COMMAND","明细引用的凭据不在附件清单中");
                    attachmentBytes+=lineBytes;
                    if(attachmentBytes>ATTACHMENTS_MAX_BYTES) throw attachmentLimit("单次提交附件总量不得超过 50MB");
                }
                UUID lineId=Ids.next();
                store.insertLine(lineId,documentId,lineNo,dispatchId,
                    facts==null?null:UUID.fromString(facts.path("baleId").asText()),
                    facts==null?null:facts.path("deliveryCode").asText(),
                    beforeCount,beforeWeight,targetCount,targetWeight,evidenceId,evidenceSha,occurred);
                decided.add(com.acrylic.outbound.submissions.SubmissionJson.object()
                    .put("lineNo",lineNo).put("lineId",lineId.toString())
                    .put("dispatchId",dispatchId==null?null:dispatchId.toString())
                    .put("beforeCount",Long.toString(beforeCount)).put("beforeWeightG",Long.toString(beforeWeight))
                    .put("targetCount",Long.toString(targetCount)).put("targetWeightG",Long.toString(targetWeight))
                    .put("countDelta",Long.toString(targetCount-beforeCount))
                    .put("weightDeltaG",Long.toString(targetWeight-beforeWeight))
                    .put("businessOccurredAt",occurred.toString()));
                lineNo++;
            }
            var decision=com.acrylic.outbound.submissions.SubmissionJson.object()
                .put("documentId",documentId.toString()).put("documentSha256",digest).put("status","SUBMITTED")
                .put("type",type).put("orderId",write.orderId().toString())
                .put("expectedResultVersion",Long.toString(expectedVersion))
                .put("lineCount",Integer.toString(decided.size()));
            var array=decision.putArray("lines");decided.forEach(array::add);
            return decision;
        }

        /** The 3-day backfill window (policy P-R07-06): business time must sit inside it, never be rewritten into it. */
        private static void requireInsideWindow(Instant occurred,Instant shipment,Instant now) {
            if(shipment==null) return;
            if(occurred.isBefore(shipment.minus(BACKFILL_WINDOW)) || occurred.isAfter(now.plus(Duration.ofMinutes(5))))
                throw CorrectionCommand.windowExceeded();
        }

        private static Instant shipmentTime(JsonNode anchor) {
            String text=anchor.path("currentResultCreatedAt").asText("null");
            return text==null || text.isBlank() || "null".equals(text)?null:Instant.parse(text);
        }

        private static Instant occurredAt(JsonNode line,Instant fallback) {
            String text=text(line,"businessOccurredAt",null);
            return text==null || text.isBlank()?fallback:Instant.parse(text);
        }

        private static String text(JsonNode node,String field,String fallback) {
            var value=node.get(field);
            return value==null || value.isNull()?fallback:value.asText();
        }

        private static UUID uuid(JsonNode node,String field) {
            String text=text(node,field,null);
            return text==null || text.isBlank()?null:UUID.fromString(text);
        }

        private static long number(JsonNode node,String field,long fallback) {
            String text=text(node,field,null);
            return text==null || text.isBlank()?fallback:Long.parseLong(text);
        }
    }

    /**
     * APPROVE: the supervisor gate ({@code correction:approve}), SUBMITTED -&gt; APPROVED, and - in the same transaction
     * as design §3 requires - the effect, the correction ledger, the next result version, the per-dispatch effective
     * snapshot and the published pointer, ending at status EFFECTIVE.
     *
     * <p>Every delta comes from the line's frozen before/target pair, never from the request. The new version carries
     * no submission of its own ({@code original_submission_id} keeps v1's), which is why V46 had to relax
     * {@code submission_id NOT NULL} (see the migration header for that recorded conflict).
     */
    public static final class Approve implements CorrectionCommand.Operation {
        private static final Map<String,String> ACTIONS=Map.of("PHYSICAL_RETURN","RETURN",
            "DISPATCH_CANCELLATION","CANCEL_DISPATCH","WEIGHT_CORRECTION","WEIGHT","HEADER_CORRECTION","HEADER");

        private final CorrectionStore store;
        public Approve(CorrectionStore store) { this.store=store; }

        @Override public String commandType() { return "APPROVE"; }

        /** MySQL BOOLEAN arrives as Boolean or as a tinyint Number depending on the column mapping. */
        private static boolean asBoolean(Object value) {
            if(value instanceof Boolean flag) return flag;
            if(value instanceof Number number) return number.intValue()!=0;
            return Boolean.parseBoolean(String.valueOf(value));
        }

        /** MySQL DATETIME(3) arrives as Timestamp or LocalDateTime depending on the connector mapping; both are UTC. */
        private static Instant occurredAt(Object value) {
            if(value instanceof java.sql.Timestamp stamp) return stamp.toInstant();
            if(value instanceof java.time.LocalDateTime local) return local.toInstant(java.time.ZoneOffset.UTC);
            if(value instanceof Instant instant) return instant;
            throw new BusinessException(500,"INVALID_CORRECTION_COMMAND","业务时间不可解析");
        }

        @Override public JsonNode execute(CorrectionCommand.Write write) {
            UUID documentId=write.documentId();
            if(documentId==null) throw new BusinessException(400,"INVALID_CORRECTION_COMMAND","审批必须指定更正单");
            var facts=write.anchor();
            if(!"SUBMITTED".equals(facts.path("status").asText())) throw CorrectionCommand.stale();
            UUID orderId=write.orderId();
            String type=facts.path("type").asText();
            String approvedBy=write.actor().path("principalId").asText();
            if(store.approveDocument(documentId,UUID.fromString(approvedBy),write.recordedAt())!=1)
                throw CorrectionCommand.stale();

            // The order lock is held by the kernel by now; this re-reads the version under it (§3 lock order).
            var current=store.lockCurrentVersion(orderId);
            long expected=Long.parseLong(facts.path("expectedResultVersion").asText());
            if(Long.parseLong(String.valueOf(current.get("result_version")))!=expected) throw CorrectionCommand.stale();
            long version=expected+1;
            if(store.resultVersionExists(orderId,version)!=0) throw CorrectionCommand.stale();

            var lines=store.lines(documentId);
            long countDelta=0,weightDelta=0;
            for(var line:lines) {
                countDelta+=((Number)line.get("target_count")).longValue()-((Number)line.get("before_count")).longValue();
                weightDelta+=((Number)line.get("target_weight_g")).longValue()-((Number)line.get("before_weight_g")).longValue();
            }
            long effectiveCount=Long.parseLong(String.valueOf(current.get("actual_count")))+countDelta;
            long effectiveWeight=Long.parseLong(String.valueOf(current.get("weight_g")))+weightDelta;
            if(effectiveCount<0 || effectiveWeight<0)
                throw new BusinessException(409,"CORRECTION_CONTEXT_CHANGED","更正后结果不得为负");
            // The version row must exist before anything references it: correction_effect and the scan snapshot both
            // carry a real foreign key to (order_id, result_version).
            String originalSubmission=String.valueOf(current.get("original_submission_id"));
            // A header correction patches the frozen success receipt that IS the header snapshot; every other type
            // carries the snapshot forward untouched.
            String headerSnapshot=String.valueOf(current.get("header_snapshot"));
            if("HEADER_CORRECTION".equals(type))
                headerSnapshot=mergedHeader(headerSnapshot,
                    com.acrylic.outbound.submissions.SubmissionJson.parse(store.createCommandBytes(documentId))
                        .path("headerPatch"));
            store.insertResultVersion(orderId,version,"null".equals(originalSubmission)?null
                    :UUID.fromString(originalSubmission),documentId,effectiveCount,effectiveWeight,
                asBoolean(current.get("inventory_deducted")),headerSnapshot,
                write.recordedAt());
            var dispatches=store.dispatchesForUpdate(orderId);
            Map<String,Map<String,Object>> changed=new LinkedHashMap<>();
            var effects=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
            var ledgerKeys=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
            var reopenedCycles=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
            for(var line:lines) {
                UUID lineId=UUID.fromString(String.valueOf(line.get("line_id")));
                String dispatchText=line.get("dispatch_id")==null?null:String.valueOf(line.get("dispatch_id"));
                long beforeCount=((Number)line.get("before_count")).longValue();
                long beforeWeight=((Number)line.get("before_weight_g")).longValue();
                long targetCount=((Number)line.get("target_count")).longValue();
                long targetWeight=((Number)line.get("target_weight_g")).longValue();
                long lineCountDelta=targetCount-beforeCount,lineWeightDelta=targetWeight-beforeWeight;
                UUID dispatchId=dispatchText==null?null:UUID.fromString(dispatchText);
                Instant occurred=occurredAt(line.get("business_occurred_at"));
                store.insertEffect(Ids.next(),lineId,orderId,version,dispatchId,
                    ACTIONS.getOrDefault(type,"WEIGHT"),lineCountDelta,lineWeightDelta,occurred,write.recordedAt());
                if(dispatchId!=null) {
                    var dispatch=store.dispatchFacts(orderId,dispatchId);
                    // A count reversal references the entry it reverses; a weight-only difference must not occupy that
                    // unique slot (design §2), so it is written as WEIGHT_DIFF with no reverse reference.
                    boolean reversal=lineCountDelta!=0;
                    String businessKey="CORR:"+lineId+":"+(reversal?"REV":"WD");
                    store.insertCorrectionLedger(Ids.next(),dispatchId,
                        UUID.fromString(dispatch.path("baleId").asText()),lineId,reversal?"REVERSAL":"WEIGHT_DIFF",
                        (int)lineCountDelta,lineWeightDelta,null,businessKey,write.recordedAt());
                    ledgerKeys.add(businessKey);
                    changed.put(dispatchText,line);
                    // Step 6: cancelling a dispatch reopens the bale's scan cycle exactly once. A reopening is not
                    // availability - the snapshot below stays is_effective=false for this dispatch.
                    if("DISPATCH_CANCELLATION".equals(type)) {
                        int cycleBefore=dispatch.path("dispatchCycle").asInt();
                        if(store.cycleReopeningExists(dispatchId))
                            throw new BusinessException(409,"CYCLE_REOPENING_EXISTS","该派发周期已重开过");
                        String evidence=line.get("evidence_sha256")==null?null:String.valueOf(line.get("evidence_sha256"));
                        if(evidence==null || evidence.isBlank()) throw CorrectionCommand.evidenceRequired();
                        store.insertCycleReopening(Ids.next(),dispatchId,lineId,cycleBefore,cycleBefore+1,evidence,
                            version,store.recoveryEpoch(),write.recordedAt());
                        reopenedCycles.add(com.acrylic.outbound.submissions.SubmissionJson.object()
                            .put("dispatchId",dispatchText).put("cycleBefore",Integer.toString(cycleBefore))
                            .put("cycleAfter",Integer.toString(cycleBefore+1)));
                    }
                }
                effects.add(com.acrylic.outbound.submissions.SubmissionJson.object()
                    .put("lineId",lineId.toString()).put("dispatchId",dispatchText)
                    .put("countDelta",Long.toString(lineCountDelta)).put("weightDeltaG",Long.toString(lineWeightDelta)));
            }
            // The snapshot covers every original dispatch, including the ones this correction zeroed.
            Map<String,long[]> perDelivery=new LinkedHashMap<>();
            for(var dispatch:dispatches) {
                String dispatchText=String.valueOf(dispatch.get("dispatch_id"));
                var line=changed.get(dispatchText);
                long count,weight;
                if(line!=null) {
                    count=((Number)line.get("target_count")).longValue();
                    weight=((Number)line.get("target_weight_g")).longValue();
                } else {
                    var facts0=store.dispatchFacts(orderId,UUID.fromString(dispatchText));
                    count=facts0.path("count").asLong();weight=facts0.path("weightG").asLong();
                }
                store.insertEffectiveScan(orderId,version,UUID.fromString(dispatchText),
                    UUID.fromString(String.valueOf(dispatch.get("capture_id"))),
                    Long.parseLong(String.valueOf(dispatch.get("scan_seq"))),count>0,Math.max(weight,0),
                    line==null?null:UUID.fromString(String.valueOf(line.get("line_id"))));
                long[] aggregate=perDelivery.getOrDefault(String.valueOf(dispatch.get("delivery_code")),new long[2]);
                if(count>0) { aggregate[0]++;aggregate[1]+=Math.max(weight,0); }
                perDelivery.put(String.valueOf(dispatch.get("delivery_code")),aggregate);
            }
            for(var entry:perDelivery.entrySet())
                store.insertEffectiveLine(orderId,version,entry.getKey(),entry.getValue()[0],entry.getValue()[1]);
            store.moveCurrentPointer(orderId,version,write.recordedAt());
            if(store.effectDocument(documentId,version)!=1) throw CorrectionCommand.stale();
            // Step 8: one state notification per published version. The name follows the design's CYCLE_REOPENED state
            // when a cycle was reopened; otherwise the correction itself is the state change.
            var outboxPayload=com.acrylic.outbound.submissions.SubmissionJson.object()
                .put("documentId",documentId.toString()).put("resultVersion",Long.toString(version))
                .put("type",type).put("effectiveCount",Long.toString(effectiveCount))
                .put("effectiveWeightG",Long.toString(effectiveWeight));
            outboxPayload.set("reopenedCycles",reopenedCycles.deepCopy());
            long outboxEpoch=store.recoveryEpoch();
            store.insertOutbox(orderId,outboxEpoch,reopenedCycles.isEmpty()?"CORRECTION_EFFECTIVE":"CYCLE_REOPENED",
                store.nextOutboxEntityVersion(orderId,outboxEpoch),
                com.acrylic.outbound.submissions.SubmissionJson.canonical(outboxPayload),write.recordedAt());
            var decision=com.acrylic.outbound.submissions.SubmissionJson.object()
                .put("documentId",documentId.toString()).put("status","EFFECTIVE")
                .put("approvedBy",approvedBy).put("approvedAt",write.recordedAt().toString())
                .put("documentSha256",facts.path("documentSha256").asText())
                .put("orderId",orderId.toString())
                .put("previousResultVersion",Long.toString(expected)).put("resultVersion",Long.toString(version))
                .put("effectiveCount",Long.toString(effectiveCount)).put("effectiveWeightG",Long.toString(effectiveWeight))
                .put("scanCoverage",Integer.toString(dispatches.size()));
            decision.set("effects",effects);decision.set("ledgerKeys",ledgerKeys);
            decision.set("reopenedCycles",reopenedCycles);
            return decision;
        }
    }

    /**
     * WITHDRAW, first half of P-R07-08 (误批撤销): the original approver <b>initiates</b>. The document moves to
     * WITHDRAW_PENDING and <b>no accounting, result version or availability is touched</b> - the rollback happens only
     * when the requester confirms ({@link WithdrawConfirm}).
     *
     * <p>The design names both participants but defines no confirmation mechanism; V48 supplies the model
     * ({@code withdrawal_requested_by/at}, {@code withdrawal_confirmed_by/at} and the WITHDRAW_PENDING status) and this
     * pair of commands is the flow. Whether a pending request expires, and whether the requester may refuse, remain
     * open policy items and are not invented here.
     */
    public static final class Withdraw implements CorrectionCommand.Operation {
        private final CorrectionStore store;
        public Withdraw(CorrectionStore store) { this.store=store; }

        @Override public String commandType() { return "WITHDRAW"; }

        @Override public JsonNode execute(CorrectionCommand.Write write) {
            UUID documentId=write.documentId();
            if(documentId==null) throw new BusinessException(400,"INVALID_CORRECTION_COMMAND","撤销必须指定更正单");
            var facts=write.anchor();
            if(!"EFFECTIVE".equals(facts.path("status").asText())) throw CorrectionCommand.stale();
            String actor=write.actor().path("principalId").asText();
            if(!actor.equals(facts.path("approvedBy").asText())) throw CorrectionCommand.denied();
            if(store.requestWithdrawal(documentId,UUID.fromString(actor),write.recordedAt())!=1)
                throw CorrectionCommand.stale();
            return com.acrylic.outbound.submissions.SubmissionJson.object()
                .put("documentId",documentId.toString()).put("status","WITHDRAW_PENDING")
                .put("requestedBy",actor)
                .put("awaitingConfirmationBy",facts.path("requestedBy").asText())
                .put("rolledBack",false);
        }
    }

    /**
     * WITHDRAW_CONFIRM, second half of P-R07-08: the <b>requester</b> confirms, and only then is the mistake rolled
     * back <b>entry by entry</b> - every {@code correction_ledger} row this document wrote gets a new row that points at
     * it through {@code reverses_entry_id} with exactly opposite deltas, and a new result version is published from the
     * reversed totals. Nothing is rewritten or deleted, so the original approval, the withdrawal request and the
     * confirmation all stay on record.
     */
    public static final class WithdrawConfirm implements CorrectionCommand.Operation {
        private final CorrectionStore store;
        public WithdrawConfirm(CorrectionStore store) { this.store=store; }

        @Override public String commandType() { return "WITHDRAW_CONFIRM"; }

        @Override public JsonNode execute(CorrectionCommand.Write write) {
            UUID documentId=write.documentId();
            if(documentId==null) throw new BusinessException(400,"INVALID_CORRECTION_COMMAND","确认撤销必须指定更正单");
            var facts=write.anchor();
            if(!"WITHDRAW_PENDING".equals(facts.path("status").asText())) throw CorrectionCommand.stale();
            String actor=write.actor().path("principalId").asText();
            if(!actor.equals(facts.path("requestedBy").asText())) throw CorrectionCommand.denied();
            UUID orderId=write.orderId();
            var current=store.lockCurrentVersion(orderId);
            long previous=Long.parseLong(String.valueOf(current.get("result_version")));
            long version=previous+1;
            if(store.resultVersionExists(orderId,version)!=0) throw CorrectionCommand.stale();
            var entries=store.ledgerEntries(documentId);
            if(entries.isEmpty())
                throw new BusinessException(409,"CORRECTION_LEDGER_CONFLICT","该更正文档没有可回退的账务行");
            Map<String,Map<String,Object>> scan=new LinkedHashMap<>();
            for(var row:store.effectiveScan(orderId,previous)) scan.put(String.valueOf(row.get("dispatch_id")),row);
            var lines=store.lines(documentId);
            Map<String,Map<String,Object>> reverted=new LinkedHashMap<>();
            for(var line:lines) {
                String dispatchText=line.get("dispatch_id")==null?null:String.valueOf(line.get("dispatch_id"));
                if(dispatchText==null) continue;
                var row=scan.get(dispatchText);
                // The effect must still be exactly what this document set; otherwise something else moved the order in
                // between and a per-entry rollback would silently clobber it (design §5: CORRECTION_LEDGER_CONFLICT).
                boolean effective=row!=null && asBoolean(row.get("is_effective"));
                long weight=row==null?0:Long.parseLong(String.valueOf(row.get("effective_weight_g")));
                if(row==null || effective!=(long)((Number)line.get("target_count")).longValue()>0
                    || weight!=((Number)line.get("target_weight_g")).longValue())
                    throw new BusinessException(409,"CORRECTION_LEDGER_CONFLICT","该明细的状态已被其他更正改变，不能整单回退");
                reverted.put(dispatchText,line);
            }
            var reversed=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
            long countDelta=0,weightDelta=0;
            for(var entry:entries) {
                int quantity=((Number)entry.get("quantity_delta")).intValue();
                long weight=((Number)entry.get("weight_delta_g")).longValue();
                countDelta-=quantity;weightDelta-=weight;
                String original=String.valueOf(entry.get("entry_id"));
                String businessKey="WD:"+documentId+":"+original;
                store.insertCorrectionLedger(Ids.next(),UUID.fromString(String.valueOf(entry.get("dispatch_id"))),
                    UUID.fromString(String.valueOf(entry.get("bale_id"))),
                    entry.get("line_id")==null?null:UUID.fromString(String.valueOf(entry.get("line_id"))),
                    String.valueOf(entry.get("entry_kind")),-quantity,-weight,UUID.fromString(original),
                    businessKey,write.recordedAt());
                reversed.add(com.acrylic.outbound.submissions.SubmissionJson.object()
                    .put("reversesEntryId",original).put("businessKey",businessKey)
                    .put("quantityDelta",Integer.toString(-quantity)).put("weightDeltaG",Long.toString(-weight)));
            }
            long effectiveCount=Long.parseLong(String.valueOf(current.get("actual_count")))+countDelta;
            long effectiveWeight=Long.parseLong(String.valueOf(current.get("weight_g")))+weightDelta;
            if(effectiveCount<0 || effectiveWeight<0)
                throw new BusinessException(409,"CORRECTION_CONTEXT_CHANGED","回退后结果不得为负");
            String originalSubmission=String.valueOf(current.get("original_submission_id"));
            store.insertResultVersion(orderId,version,"null".equals(originalSubmission)?null
                    :UUID.fromString(originalSubmission),documentId,effectiveCount,effectiveWeight,
                asBoolean(current.get("inventory_deducted")),String.valueOf(current.get("header_snapshot")),
                write.recordedAt());
            Map<String,long[]> perDelivery=new LinkedHashMap<>();
            for(var dispatch:store.dispatchesForUpdate(orderId)) {
                String dispatchText=String.valueOf(dispatch.get("dispatch_id"));
                var line=reverted.get(dispatchText);
                long count,weight;UUID lineId;
                if(line!=null) {
                    count=((Number)line.get("before_count")).longValue();
                    weight=((Number)line.get("before_weight_g")).longValue();
                    lineId=UUID.fromString(String.valueOf(line.get("line_id")));
                } else {
                    var row=scan.get(dispatchText);
                    count=row!=null && asBoolean(row.get("is_effective"))?1:0;
                    weight=row==null?0:Long.parseLong(String.valueOf(row.get("effective_weight_g")));
                    String previousLine=row==null?null:String.valueOf(row.get("latest_correction_line_id"));
                    lineId=previousLine==null || "null".equals(previousLine)?null:UUID.fromString(previousLine);
                }
                store.insertEffectiveScan(orderId,version,UUID.fromString(dispatchText),
                    UUID.fromString(String.valueOf(dispatch.get("capture_id"))),
                    Long.parseLong(String.valueOf(dispatch.get("scan_seq"))),count>0,Math.max(weight,0),lineId);
                long[] aggregate=perDelivery.getOrDefault(String.valueOf(dispatch.get("delivery_code")),new long[2]);
                if(count>0) { aggregate[0]++;aggregate[1]+=Math.max(weight,0); }
                perDelivery.put(String.valueOf(dispatch.get("delivery_code")),aggregate);
            }
            for(var entry:perDelivery.entrySet())
                store.insertEffectiveLine(orderId,version,entry.getKey(),entry.getValue()[0],entry.getValue()[1]);
            store.moveCurrentPointer(orderId,version,write.recordedAt());
            if(store.confirmWithdrawal(documentId,UUID.fromString(actor),write.recordedAt())!=1)
                throw CorrectionCommand.stale();
            // Step 8: the withdrawal is a state change of its own, published under the next free state version.
            long withdrawEpoch=store.recoveryEpoch();
            store.insertOutbox(orderId,withdrawEpoch,"CORRECTION_WITHDRAWN",
                store.nextOutboxEntityVersion(orderId,withdrawEpoch),
                com.acrylic.outbound.submissions.SubmissionJson.canonical(
                    com.acrylic.outbound.submissions.SubmissionJson.object()
                        .put("documentId",documentId.toString()).put("resultVersion",Long.toString(version))
                        .put("reversedEntryCount",Integer.toString(entries.size()))
                        .put("effectiveCount",Long.toString(effectiveCount))
                        .put("effectiveWeightG",Long.toString(effectiveWeight))),write.recordedAt());
            var decision=com.acrylic.outbound.submissions.SubmissionJson.object()
                .put("documentId",documentId.toString()).put("status","WITHDRAWN")
                .put("orderId",orderId.toString()).put("resultVersion",Long.toString(version))
                .put("effectiveCount",Long.toString(effectiveCount)).put("effectiveWeightG",Long.toString(effectiveWeight))
                .put("reversedEntryCount",Integer.toString(entries.size()))
                .put("confirmedBy",actor);
            decision.set("reversedEntries",reversed);
            return decision;
        }

        /** MySQL BOOLEAN arrives as Boolean or as a tinyint Number depending on the column mapping. */
        private static boolean asBoolean(Object value) {
            if(value instanceof Boolean flag) return flag;
            if(value instanceof Number number) return number.intValue()!=0;
            return Boolean.parseBoolean(String.valueOf(value));
        }
    }
}
