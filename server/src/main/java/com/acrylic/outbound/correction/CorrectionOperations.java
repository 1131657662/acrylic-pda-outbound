package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Document creation and withdrawal initiation; result/accounting effects have their own operations. */
public final class CorrectionOperations {
    private CorrectionOperations() { }

    public static final class Create implements CorrectionCommand.Operation {
        private final CorrectionStore store;
        public Create(CorrectionStore store) { this.store = store; }
        @Override public String commandType() { return "CREATE"; }

        @Override public JsonNode execute(CorrectionCommand.Write write) {
            var body = write.command().body(); String type = body.path("type").asText(), reason = body.path("reason").asText();
            validate(write, type, reason);
            UUID document = Ids.next(); long expected = write.anchor().path("currentResultVersion").asLong();
            store.insertDocument(document, UUID.fromString(write.actor().path("warehouseId").asText()), write.orderId(), type,
                expected, reason, write.command().digest(), "SUBMITTED", write.anchor().path("sourceRecoveryEpoch").asLong(1),
                UUID.fromString(write.actor().path("principalId").asText()), write.recordedAt());
            var lines = "HEADER_CORRECTION".equals(type) ? SubmissionJson.object().putArray("lines") : body.path("lines");
            var decided = new CorrectionCreateLines(store).freeze(write, document, lines);
            var result = SubmissionJson.object().put("documentId", document.toString()).put("documentSha256", write.command().digest())
                .put("status", "SUBMITTED").put("type", type).put("orderId", write.orderId().toString())
                .put("expectedResultVersion", Long.toString(expected)).put("lineCount", Integer.toString(decided.size()));
            var array = result.putArray("lines"); decided.forEach(array::add); return result;
        }

        private void validate(CorrectionCommand.Write write, String type, String reason) {
            if (reason.isBlank() || reason.length() > 500) throw invalid("更正原因必须为 1..500 字");
            if (!Set.of("PHYSICAL_RETURN", "DISPATCH_CANCELLATION", "WEIGHT_CORRECTION", "HEADER_CORRECTION").contains(type))
                throw invalid("更正类型无效");
            var body = write.command().body(); var lines = body.path("lines");
            if ("HEADER_CORRECTION".equals(type)) {
                var patch = body.path("headerPatch");
                if (!patch.isObject() || patch.isEmpty()) throw invalid("表头更正必须给出非空 headerPatch");
                if (lines.isArray() && !lines.isEmpty()) throw invalid("表头更正不接受明细行");
                CorrectionHeader.merge(store.currentHeaderSnapshot(write.orderId()), patch);
            } else if (!lines.isArray() || lines.isEmpty() || lines.size() > 200) throw invalid("更正行必须为 1..200 条");
        }
        private static BusinessException invalid(String message) { return new BusinessException(400, "INVALID_CORRECTION_COMMAND", message); }
    }

    public static final class Withdraw implements CorrectionCommand.Operation {
        private final CorrectionStore store;
        public Withdraw(CorrectionStore store) { this.store = store; }
        @Override public String commandType() { return "WITHDRAW"; }
        @Override public JsonNode execute(CorrectionCommand.Write write) {
            if (write.documentId() == null) throw new BusinessException(400, "INVALID_CORRECTION_COMMAND", "撤销必须指定更正单");
            var facts = write.anchor(); String actor = write.actor().path("principalId").asText();
            if (!"EFFECTIVE".equals(facts.path("status").asText())) throw CorrectionCommand.stale();
            if (!actor.equals(facts.path("approvedBy").asText())) throw CorrectionCommand.denied();
            if (store.requestWithdrawal(write.documentId(), UUID.fromString(actor), write.recordedAt()) != 1) throw CorrectionCommand.stale();
            return SubmissionJson.object().put("documentId", write.documentId().toString()).put("status", "WITHDRAW_PENDING")
                .put("requestedBy", actor).put("awaitingConfirmationBy", facts.path("requestedBy").asText()).put("rolledBack", false);
        }
    }
}
