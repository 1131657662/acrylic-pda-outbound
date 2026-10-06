package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TemporaryContinuationIntegrityTest {
    @Test void rejectsTicketWhenOriginalAuthorizationBindingChanged() {
        UUID scope = UUID.randomUUID(), submission = UUID.randomUUID(), authorization = UUID.randomUUID();
        UUID operator = UUID.randomUUID(), device = UUID.randomUUID(), ticket = UUID.randomUUID(), bale = UUID.randomUUID();
        var jdbc = mock(JdbcTemplate.class);
        var row = new HashMap<String, Object>();
        row.put("ticket_id", ticket.toString()); row.put("status", "TICKET_ISSUED");
        row.put("from_work_round", 1L); row.put("to_work_round", 2L); row.put("scope_id", scope.toString());
        row.put("submission_id", submission.toString()); row.put("authorization_id", UUID.randomUUID().toString());
        row.put("operator_id", operator.toString()); row.put("freeze_device_id", device.toString());
        row.put("source_recovery_epoch", 1L); row.put("evidence_set_id", UUID.randomUUID().toString());
        row.put("verification_id", UUID.randomUUID().toString()); row.put("retained_manifest_sha256", "a".repeat(64));
        row.put("retained_manifest_version", 1L); row.put("next_order_scan_seq", 3L);
        row.put("command_receipt_id", UUID.randomUUID().toString()); row.put("command_sha256", "b".repeat(64));
        row.put("new_authorization_id", null);
        when(jdbc.queryForList(contains("FROM temporary_source_continuation"), any(Object[].class))).thenReturn(List.of(row));

        assertThatThrownBy(() -> new TemporaryContinuationIntegrity(jdbc).verify(receipt(scope, submission, authorization,
            operator, device, ticket, bale, row)))
            .isInstanceOfSatisfying(com.acrylic.outbound.common.BusinessException.class,
                error -> assertThat(error.code()).isEqualTo("COMMAND_RECEIPT_INTEGRITY_FAILURE"));
    }

    private static ObjectNode receipt(UUID scope, UUID submission, UUID authorization, UUID operator, UUID device,
        UUID ticket, UUID bale, Map<String, Object> row) {
        var receipt = SubmissionJson.object().put("receiptId", row.get("command_receipt_id").toString())
            .put("commandSha256", row.get("command_sha256").toString());
        receipt.putObject("originalContext").put("scopeId", scope.toString()).put("submissionId", submission.toString())
            .put("authorizationId", authorization.toString()).put("originalOperatorId", operator.toString())
            .put("freezeDeviceId", device.toString()).put("sourceRecoveryEpoch", "1");
        var decision = receipt.putObject("businessDecision").put("ticketId", ticket.toString())
            .put("fromWorkRound", "1").put("toWorkRound", "2").put("evidenceSetId", row.get("evidence_set_id").toString())
            .put("verificationId", row.get("verification_id").toString()).put("retainedManifestSha256", "a".repeat(64))
            .put("retainedManifestVersion", "1").put("nextOrderScanSeq", "3");
        decision.putArray("products").addObject().put("baleId", bale.toString()).put("expectedDispatchCycle", "7")
            .put("recordSha256", "c".repeat(64));
        return receipt;
    }
}
