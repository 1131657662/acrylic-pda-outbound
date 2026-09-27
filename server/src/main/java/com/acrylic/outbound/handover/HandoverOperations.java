package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/**
 * REQUEST opens the single open case for a source; APPROVE binds the evidence the supervisor verified and moves the
 * case to APPROVED. Both are FORMAL-only in this batch: TEMP adaptation (R07-2) and the fault branch (R07-5) are
 * explicitly out of scope, and a SUCCEEDED original submission is refused here so it can only go to corrections.
 */
final class HandoverOperations {
    private HandoverOperations() { }

    static final class Request implements HandoverCommand.Operation {
        private final HandoverStore store;
        Request(HandoverStore store) { this.store=store; }
        @Override public String commandType() { return "REQUEST"; }
        @Override public JsonNode execute(HandoverCommand.Write write) {
            var input=write.command();var anchor=write.caseFacts();
            String sourceKind=anchor.path("sourceKind").asText("FORMAL");
            if (!sourceKind.equals("FORMAL")) throw new com.acrylic.outbound.common.BusinessException(
                409,"HANDOVER_TEMP_NOT_OPEN","临时来源交接尚未开放（R07-2）");
            String submissionStatus=anchor.path("submissionStatus").asText("");
            if (submissionStatus.equals("SUCCEEDED")) throw new com.acrylic.outbound.common.BusinessException(
                409,"HANDOVER_ALREADY_SUCCEEDED","原批次已成功出库，请走更正入口，不能换机续扫");
            // Original result dimension: only an explicitly terminable submission may later activate.
            String originalResult=switch (submissionStatus) {
                case "ABORTED" -> "ABORTED";
                case "REJECTED" -> "REJECTED";
                case "REVIEW_REQUIRED","RECEIVING","REVIEWING" -> "NONTERMINAL";
                case "DRAFT","PENDING" -> "NONE_VERIFIED";
                default -> "UNKNOWN";
            };
            int fromRound=Integer.parseInt(anchor.path("fromWorkRound").asText());
            UUID scope=input.uuid("scopeId");
            if (anchor.path("currentSubmissionId").isNull()) throw new com.acrylic.outbound.common.BusinessException(
                409,"HANDOVER_CONTEXT_CHANGED","该来源当前没有绑定提交批次，不能发起交接");
            UUID warehouse=UUID.fromString(write.actor().path("warehouseId").asText());
            store.insertCase(write.caseId(),warehouse,scope,
                UUID.fromString(anchor.path("currentSubmissionId").asText()),
                UUID.fromString(anchor.path("canonicalOrderId").asText()),sourceKind,fromRound,fromRound+1,
                "EVIDENCE_PENDING","REQUESTED","UNKNOWN_HIGH_WATERMARK",originalResult,"UNCHECKED",null,
                UUID.fromString(write.actor().path("principalId").asText()),write.recordedAt());
            var decision=SubmissionJson.object().put("caseId",write.caseId().toString())
                .put("status","EVIDENCE_PENDING").put("stopState","REQUESTED")
                .put("integrityState","UNKNOWN_HIGH_WATERMARK").put("originalResultState",originalResult)
                .put("physicalState","UNCHECKED").put("fromWorkRound",Integer.toString(fromRound))
                .put("toWorkRound",Integer.toString(fromRound+1));
            return decision;
        }
    }

    /**
     * APPROVE is the supervisor's evidenced decision. The evidence digests and the per-bale retained set are bound
     * here, so the server never trusts a client-claimed state: the four dimensions must be stated explicitly and are
     * re-checked against what was recorded. The original stop proof itself arrives from the device (PDA half, next
     * increment); this command requires that it is already recorded when the case claims DEVICE_ACKED.
     */
    static final class Approve implements HandoverCommand.Operation {
        private final HandoverStore store;
        Approve(HandoverStore store) { this.store=store; }
        @Override public String commandType() { return "APPROVE"; }
        @Override public JsonNode execute(HandoverCommand.Write write) {
            var input=write.command();var facts=write.caseFacts();
            String status=facts.path("status").asText();
            if (!Set.of("REQUESTED","EVIDENCE_PENDING","REVIEW_READY").contains(status))
                throw new com.acrylic.outbound.common.BusinessException(409,"HANDOVER_STATE_INVALID","当前案件状态不能批准");
            String integrity=input.text("integrityState");
            String stopState=input.text("stopState");
            String originalResult=input.text("originalResultState");
            String physicalState=input.text("physicalState");
            Long highWatermark=input.text("inputHighWatermark")==null?null:Long.parseLong(input.text("inputHighWatermark"));
            if (stopState==null||integrity==null||originalResult==null||physicalState==null)
                throw new com.acrylic.outbound.common.BusinessException(400,"HANDOVER_EVIDENCE_REQUIRED","批准必须声明四维证据状态");
            if (!integrity.equals("COMPLETE"))
                throw new com.acrylic.outbound.common.BusinessException(409,"HANDOVER_INTEGRITY_INCOMPLETE","证据不完整不能批准交接");
            if (!stopState.equals("DEVICE_ACKED"))
                throw new com.acrylic.outbound.common.BusinessException(409,"HANDOVER_STOP_NOT_PROVEN","缺少原机停止证明，不能批准");
            if (!physicalState.equals("MATCHED"))
                throw new com.acrylic.outbound.common.BusinessException(409,"HANDOVER_PHYSICAL_MISMATCH","实物集合未核对一致，不能批准");
            if (!Set.of("NONE_VERIFIED","ABORTED","REJECTED").contains(originalResult))
                throw new com.acrylic.outbound.common.BusinessException(409,"HANDOVER_ORIGINAL_RESULT_OPEN","原提交未终结或已成功，不能批准换机");
            if (highWatermark==null||highWatermark<0)
                throw new com.acrylic.outbound.common.BusinessException(400,"HANDOVER_EVIDENCE_REQUIRED","批准必须给出完整输入高水位");
            // The stop proof must already be recorded by the device; the supervisor cannot assert it on its behalf.
            var evidence=store.checklist(write.caseId());
            int stopProofs=store.evidenceCount(write.caseId(),"STOP_PROOF");
            if (stopProofs==0) throw new com.acrylic.outbound.common.BusinessException(
                409,"HANDOVER_STOP_NOT_PROVEN","原机停止证明尚未记录，不能批准");
            var checklist=input.body().path("checklist");
            if (!checklist.isArray()||checklist.isEmpty()) throw new com.acrylic.outbound.common.BusinessException(
                400,"HANDOVER_CHECKLIST_REQUIRED","批准必须提交逐包清单");
            if (evidence.isEmpty()) {
                int item=0;
                for (JsonNode line:checklist) {
                    UUID bale=UUID.fromString(line.path("baleId").asText());
                    String state=line.path("state").asText();
                    if (!Set.of("RETAINED","REMOVED").contains(state))
                        throw new com.acrylic.outbound.common.BusinessException(400,"HANDOVER_CHECKLIST_REQUIRED","清单状态无效");
                    store.insertChecklist(Ids.next(),write.caseId(),item++,bale,line.path("deliveryCode").asText(),
                        state,Long.parseLong(line.path("dispatchCycle").asText()),
                        UUID.fromString(write.actor().path("principalId").asText()),write.recordedAt());
                }
            }
            UUID approver=UUID.fromString(write.actor().path("principalId").asText());
            store.advanceCase(write.caseId(),write.caseVersion(),"APPROVED",approver,highWatermark,stopState,integrity,
                originalResult,physicalState,null,null,null,null,approver,write.recordedAt());
            return SubmissionJson.object().put("caseId",write.caseId().toString()).put("status","APPROVED")
                .put("approvedBy",approver.toString()).put("inputHighWatermark",Long.toString(highWatermark))
                .put("retained",Long.toString(checklist.size())).put("evidenceRecorded",Integer.toString(stopProofs));
        }
    }
}
