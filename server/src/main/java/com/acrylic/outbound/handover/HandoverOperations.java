package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** FORMAL request and approval effects; locked evidence/stock review is required before these operations run. */
final class HandoverOperations {
    private HandoverOperations() { }

    static final class Request implements HandoverCommand.Operation {
        private final HandoverStore store;
        Request(HandoverStore store) { this.store=store; }
        @Override public String commandType() { return "REQUEST"; }
        @Override public JsonNode execute(HandoverCommand.Write write) {
            var input=write.command();var anchor=write.caseFacts();
            String sourceKind=anchor.path("sourceKind").asText("FORMAL");
            if (!sourceKind.equals("FORMAL")) throw new BusinessException(409,"HANDOVER_TEMP_NOT_OPEN","临时来源交接尚未开放（R07-2）");
            String originalResult=originalResult(anchor.path("submissionStatus").asText(""));
            int fromRound=Integer.parseInt(anchor.path("fromWorkRound").asText());
            if (anchor.path("currentSubmissionId").isNull()) throw new BusinessException(
                409,"HANDOVER_CONTEXT_CHANGED","该来源当前没有绑定提交批次，不能发起交接");
            store.insertCase(write.caseId(),UUID.fromString(write.actor().path("warehouseId").asText()),input.uuid("scopeId"),
                UUID.fromString(anchor.path("currentSubmissionId").asText()),UUID.fromString(anchor.path("canonicalOrderId").asText()),
                sourceKind,fromRound,fromRound+1,"EVIDENCE_PENDING","REQUESTED","UNKNOWN_HIGH_WATERMARK",originalResult,"UNCHECKED",null,
                UUID.fromString(write.actor().path("principalId").asText()),write.recordedAt());
            return SubmissionJson.object().put("caseId",write.caseId().toString()).put("status","EVIDENCE_PENDING")
                .put("stopState","REQUESTED").put("integrityState","UNKNOWN_HIGH_WATERMARK").put("originalResultState",originalResult)
                .put("physicalState","UNCHECKED").put("fromWorkRound",Integer.toString(fromRound)).put("toWorkRound",Integer.toString(fromRound+1));
        }

        private String originalResult(String status) {
            return switch (status) {
                case "ABORTED", "REJECTED" -> status;
                case "SUCCEEDED" -> throw new BusinessException(409,"HANDOVER_ALREADY_SUCCEEDED","原批次已成功出库，请走更正入口，不能换机续扫");
                case "REGISTERED" -> "NONE_VERIFIED";
                case "RECEIVING", "REVIEW_REQUIRED" -> "NONTERMINAL";
                default -> "UNKNOWN";
            };
        }
    }

    static final class Approve implements HandoverCommand.Operation {
        private final HandoverStore store;
        Approve(HandoverStore store) { this.store=store; }
        @Override public String commandType() { return "APPROVE"; }
        @Override public JsonNode execute(HandoverCommand.Write write) {
            var input=write.command();var facts=write.caseFacts();
            if (!Set.of("REQUESTED","EVIDENCE_PENDING","REVIEW_READY").contains(facts.path("status").asText()))
                throw new BusinessException(409,"HANDOVER_STATE_INVALID","当前案件状态不能批准");
            dimensions(input);
            long high=SubmissionJson.number(input.body(),"inputHighWatermark");
            var lines=input.body().path("checklist");
            if (store.checklist(write.caseId()).isEmpty()) append(write,lines);
            UUID approver=UUID.fromString(write.actor().path("principalId").asText());
            store.advanceCase(write.caseId(),write.caseVersion(),"APPROVED",approver,high,"DEVICE_ACKED","COMPLETE",
                input.text("originalResultState"),"MATCHED",null,null,null,null,approver,write.recordedAt());
            long retained=0;for (var line:lines) if (line.path("state").asText().equals("RETAINED")) retained++;
            return SubmissionJson.object().put("caseId",write.caseId().toString()).put("status","APPROVED")
                .put("approvedBy",approver.toString()).put("inputHighWatermark",Long.toString(high))
                .put("retained",Long.toString(retained)).put("evidenceRecorded","1");
        }

        private void dimensions(HandoverCommand.Input input) {
            if (!"COMPLETE".equals(input.text("integrityState")))
                throw new BusinessException(409,"HANDOVER_INTEGRITY_INCOMPLETE","证据不完整不能批准交接");
            if (!"DEVICE_ACKED".equals(input.text("stopState")))
                throw new BusinessException(409,"HANDOVER_STOP_NOT_PROVEN","缺少原机停止证明，不能批准");
            if (!"MATCHED".equals(input.text("physicalState")))
                throw new BusinessException(409,"HANDOVER_PHYSICAL_MISMATCH","实物集合未核对一致，不能批准");
            if (!Set.of("NONE_VERIFIED","ABORTED","REJECTED").contains(input.text("originalResultState")))
                throw new BusinessException(409,"HANDOVER_ORIGINAL_RESULT_OPEN","原提交未终结或已成功，不能批准换机");
        }

        private void append(HandoverCommand.Write write,JsonNode lines) {
            int item=0;
            for (var line:lines) store.insertChecklist(Ids.next(),write.caseId(),item++,SubmissionJson.uuid(line,"baleId"),
                SubmissionJson.text(line,"deliveryCode"),SubmissionJson.text(line,"state"),SubmissionJson.number(line,"dispatchCycle"),
                UUID.fromString(write.actor().path("principalId").asText()),write.recordedAt());
        }
    }
}
