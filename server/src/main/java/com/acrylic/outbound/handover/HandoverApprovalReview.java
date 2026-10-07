package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Runs after source locks and before command writes; prepare/activate consume the immutable signed approval. */
@Component
final class HandoverApprovalReview {
    private final HandoverApprovalEvidence evidence;
    private final HandoverChecklist checklist;
    private final HandoverApprovalStock stock;
    private final HandoverStore store;
    private final HandoverReadReceipt receipts;
    private final JdbcTemplate jdbc;
    private final HandoverTemporaryApprovalStock temporary;

    HandoverApprovalReview(HandoverApprovalEvidence evidence, HandoverChecklist checklist, HandoverApprovalStock stock,
            HandoverStore store, HandoverReadReceipt receipts, JdbcTemplate jdbc, HandoverTemporaryApprovalStock temporary) {
        this.evidence = evidence; this.checklist = checklist; this.stock = stock;
        this.store = store; this.receipts = receipts; this.jdbc = jdbc;
        this.temporary = temporary;
    }

    void require(HandoverCommand.Input input, JsonNode facts) {
        boolean approved = !input.kind().equals("APPROVE");
        JsonNode command = approved ? originalApproval(facts) : input.body();
        var verified = evidence.verify(facts, number(command, "inputHighWatermark"));
        var body = verified.body(); var lines = command.path("checklist");
        var dispositions = checklist.dispositions(body, lines);
        checklist.compareStored(store.checklist(uuid(facts, "caseId")), lines, approved);
        if ("TEMP".equals(facts.path("sourceKind").asText())) temporary.require(facts, verified, dispositions);
        else stock.require(facts, body, dispositions);
    }

    private JsonNode originalApproval(JsonNode facts) {
        var rows = jdbc.queryForList("SELECT * FROM handover_command_receipt WHERE case_id=? AND command_type='APPROVE' LIMIT 2",
            Ids.bytes(uuid(facts, "caseId")));
        HandoverCommand.corruptUnless(rows.size() == 1);
        var verified = receipts.verify(rows.getFirst()); var command = verified.command();
        HandoverCommand.corruptUnless(command.path("caseId").equals(facts.path("caseId"))
            && verified.receipt().path("actor").path("principalId").equals(facts.path("approvedBy"))
            && "APPROVED".equals(verified.receipt().path("businessDecision").path("status").asText()));
        for (String field : java.util.List.of("inputHighWatermark", "stopState", "integrityState", "originalResultState", "physicalState"))
            HandoverCommand.corruptUnless(command.path(field).equals(facts.path(field)));
        return command;
    }
}
