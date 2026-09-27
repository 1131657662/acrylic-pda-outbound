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
     * APPROVE: the supervisor gate ({@code correction:approve}) plus SUBMITTED -&gt; APPROVED under the document row lock.
     *
     * <p>Design §3 makes approval the point where the effect is applied inside one transaction; that body arrives with
     * steps 4-5. Until then an approved document carries {@code effect=DEFERRED_TO_STEP_4} in its decision, so this
     * intermediate state is visible rather than implied.
     */
    public static final class Approve implements CorrectionCommand.Operation {
        private final CorrectionStore store;
        public Approve(CorrectionStore store) { this.store=store; }

        @Override public String commandType() { return "APPROVE"; }

        @Override public JsonNode execute(CorrectionCommand.Write write) {
            UUID documentId=write.documentId();
            if(documentId==null) throw new BusinessException(400,"INVALID_CORRECTION_COMMAND","审批必须指定更正单");
            var facts=write.anchor();
            if(!"SUBMITTED".equals(facts.path("status").asText())) throw CorrectionCommand.stale();
            String approvedBy=write.actor().path("principalId").asText();
            if(store.approveDocument(documentId,UUID.fromString(approvedBy),write.recordedAt())!=1)
                throw CorrectionCommand.stale();
            return com.acrylic.outbound.submissions.SubmissionJson.object()
                .put("documentId",documentId.toString()).put("status","APPROVED")
                .put("approvedBy",approvedBy).put("approvedAt",write.recordedAt().toString())
                .put("documentSha256",facts.path("documentSha256").asText())
                .put("expectedResultVersion",facts.path("expectedResultVersion").asText())
                .put("effect","DEFERRED_TO_STEP_4");
        }
    }
}
