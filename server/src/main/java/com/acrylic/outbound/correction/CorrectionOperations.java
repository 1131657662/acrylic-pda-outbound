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

    public static final class Create implements CorrectionCommand.Operation {
        private static final Duration BACKFILL_WINDOW = Duration.ofDays(3);
        private static final Set<String> PHYSICAL = Set.of("PHYSICAL_RETURN", "DISPATCH_CANCELLATION");

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
            if(!lines.isArray() || lines.isEmpty() || lines.size()>200)
                throw new BusinessException(400,"INVALID_CORRECTION_COMMAND","更正行必须为 1..200 条");
            long expectedVersion=write.anchor().path("currentResultVersion").asLong();
            Instant shipment=shipmentTime(write.anchor());
            UUID documentId=Ids.next();
            // The document row must exist before its lines: correction_line.document_id is a real foreign key.
            String digest=write.command().digest();
            store.insertDocument(documentId,UUID.fromString(write.actor().path("warehouseId").asText()),
                write.orderId(),type,expectedVersion,reason,digest,"SUBMITTED",
                write.anchor().path("sourceRecoveryEpoch").asLong(1),
                UUID.fromString(write.actor().path("principalId").asText()),write.recordedAt());
            List<ObjectNode> decided=new ArrayList<>();
            int lineNo=1;
            for(var line:lines) {
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
            store.insertResultVersion(orderId,version,"null".equals(originalSubmission)?null
                    :UUID.fromString(originalSubmission),documentId,effectiveCount,effectiveWeight,
                asBoolean(current.get("inventory_deducted")),String.valueOf(current.get("header_snapshot")),
                write.recordedAt());
            var dispatches=store.dispatchesForUpdate(orderId);
            Map<String,Map<String,Object>> changed=new LinkedHashMap<>();
            var effects=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
            var ledgerKeys=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
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
            var decision=com.acrylic.outbound.submissions.SubmissionJson.object()
                .put("documentId",documentId.toString()).put("status","EFFECTIVE")
                .put("approvedBy",approvedBy).put("approvedAt",write.recordedAt().toString())
                .put("documentSha256",facts.path("documentSha256").asText())
                .put("orderId",orderId.toString())
                .put("previousResultVersion",Long.toString(expected)).put("resultVersion",Long.toString(version))
                .put("effectiveCount",Long.toString(effectiveCount)).put("effectiveWeightG",Long.toString(effectiveWeight))
                .put("scanCoverage",Integer.toString(dispatches.size()));
            decision.set("effects",effects);decision.set("ledgerKeys",ledgerKeys);
            return decision;
        }
    }
}
