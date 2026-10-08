package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Rechecks the live source after its scope lock, before any stop, approval, preparation or activation effects. */
@Component
final class HandoverSourceGuard {
    private final JdbcTemplate jdbc;
    private final HandoverStore store;
    private final HandoverReadStore requests;
    private final HandoverReadReceipt receipts;

    HandoverSourceGuard(JdbcTemplate jdbc, HandoverStore store, HandoverReadStore requests,
            HandoverReadReceipt receipts) {
        this.jdbc = jdbc; this.store = store; this.requests = requests; this.receipts = receipts;
    }

    void require(HandoverCommand.Input command, JsonNode facts) {
        var submission = lock(facts);
        String result = originalResult(submission.path("status").asText());
        String expected = switch (command.kind()) {
            case "APPROVE" -> command.text("originalResultState");
            case "FAULT_FENCE" -> "ABORTED";
            default -> facts.path("originalResultState").asText();
        };
        requireSame(result.equals(expected));
    }

    JsonNode lock(JsonNode facts) {
        var original = receipts.verify(requests.originalRequest(id(facts, "caseId")));
        var slot = lockSlot(facts);
        var submission = lockSubmission(facts);
        var scope = store.scopeFacts(id(facts, "scopeId"));
        requireSource(facts, scope, original);
        requireSlot(facts, slot);
        requireSubmission(facts, submission);
        return submission;
    }

    private JsonNode lockSlot(JsonNode facts) {
        var order = jdbc.queryForList("SELECT id FROM outbound_order WHERE id=? AND warehouse_id=? FOR UPDATE",
            Ids.bytes(id(facts, "canonicalOrderId")), Ids.bytes(id(facts, "warehouseId")));
        requireSame(order.size() == 1);
        var rows = jdbc.queryForList("""
            SELECT JSON_OBJECT('scopeId',BIN_TO_UUID(scope_id),'workRound',CAST(work_round AS CHAR),
              'submissionId',BIN_TO_UUID(current_submission_id))
            FROM order_submission_slot WHERE order_id=? FOR UPDATE
            """, String.class, Ids.bytes(id(facts, "canonicalOrderId")));
        requireSame(rows.size() == 1);
        return SubmissionJson.parse(rows.getFirst());
    }

    private JsonNode lockSubmission(JsonNode facts) {
        var rows = jdbc.queryForList("""
            SELECT JSON_OBJECT('scopeId',BIN_TO_UUID(scope_id),'workRound',CAST(work_round AS CHAR),'status',status)
            FROM submission WHERE id=? FOR UPDATE
            """, String.class, Ids.bytes(id(facts, "submissionId")));
        requireSame(rows.size() == 1);
        return SubmissionJson.parse(rows.getFirst());
    }

    private void requireSource(JsonNode facts, JsonNode scope, HandoverReadReceipt.Verified original) {
        var receipt = original.receipt();
        HandoverCommand.corruptUnless("REQUEST".equals(original.command().path("commandType").asText())
            && facts.path("caseId").equals(receipt.path("businessDecision").path("caseId"))
            && facts.path("requestedBy").equals(receipt.path("actor").path("principalId")));
        var requested = receipt.path("caseContext");
        for (String field : List.of("scopeId", "warehouseId", "canonicalOrderId", "sourceKind", "fromWorkRound")) {
            requireSame(facts.path(field).equals(scope.path(field)));
            requireSame(requested.path(field).equals(scope.path(field)));
        }
        requireSame(requested.path("mappingVersion").equals(scope.path("mappingVersion")));
        requireSame(facts.path("submissionId").equals(scope.path("currentSubmissionId"))
            && requested.path("currentSubmissionId").equals(scope.path("currentSubmissionId")));
    }

    private void requireSlot(JsonNode facts, JsonNode slot) {
        requireSame(facts.path("scopeId").equals(slot.path("scopeId"))
            && facts.path("fromWorkRound").equals(slot.path("workRound"))
            && facts.path("submissionId").equals(slot.path("submissionId")));
    }

    private void requireSubmission(JsonNode facts, JsonNode submission) {
        requireSame(facts.path("scopeId").equals(submission.path("scopeId"))
            && facts.path("fromWorkRound").equals(submission.path("workRound")));
    }

    private String originalResult(String status) {
        return switch (status) {
            case "REGISTERED" -> "NONE_VERIFIED";
            case "ABORTED", "REJECTED" -> status;
            default -> throw new BusinessException(409, "HANDOVER_ORIGINAL_RESULT_OPEN",
                "原提交未终结或已成功，不能继续交接");
        };
    }

    private UUID id(JsonNode facts, String field) { return UUID.fromString(facts.path(field).asText()); }

    private void requireSame(boolean same) {
        if (!same) throw new BusinessException(409, "HANDOVER_CONTEXT_CHANGED", "交接来源或原结果已变化，请重新核对");
    }
}
