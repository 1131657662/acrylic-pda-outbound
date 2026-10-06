package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Historical receipts bind immutable issuance/consumption facts, not the current ticket or K state. */
@Component
public final class TemporaryContinuationIntegrity {
    private final JdbcTemplate jdbc;
    public TemporaryContinuationIntegrity(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    void verify(JsonNode receipt) {
        if ("ACTIVATE_CONTINUATION".equals(receipt.path("commandType").asText())) { verifyActivation(receipt); return; }
        var context = receipt.path("originalContext"); var decision = receipt.path("businessDecision");
        var rows = jdbc.queryForList("""
            SELECT BIN_TO_UUID(id) ticket_id,from_work_round,to_work_round,
              BIN_TO_UUID(scope_id) scope_id,BIN_TO_UUID(previous_submission_id) submission_id,
              BIN_TO_UUID(previous_authorization_id) authorization_id,BIN_TO_UUID(previous_operator_id) operator_id,
              BIN_TO_UUID(freeze_device_id) freeze_device_id,source_recovery_epoch,
              BIN_TO_UUID(evidence_set_id) evidence_set_id,BIN_TO_UUID(evidence_verification_id) verification_id,
              retained_manifest_sha256,retained_manifest_version,next_order_scan_seq,
              BIN_TO_UUID(command_receipt_id) command_receipt_id,command_sha256
            FROM temporary_source_continuation WHERE id=? AND scope_id=? AND previous_submission_id=?
            """, Ids.bytes(UUID.fromString(decision.path("ticketId").asText())), Ids.bytes(UUID.fromString(context.path("scopeId").asText())),
            Ids.bytes(UUID.fromString(context.path("submissionId").asText())));
        CommandErrors.integrity(rows.size()==1); var row=rows.getFirst();
        CommandErrors.integrity(row.get("ticket_id").toString().equals(decision.path("ticketId").asText())
            && row.get("scope_id").toString().equals(context.path("scopeId").asText())
            && row.get("submission_id").toString().equals(context.path("submissionId").asText())
            && row.get("authorization_id").toString().equals(context.path("authorizationId").asText())
            && row.get("operator_id").toString().equals(context.path("originalOperatorId").asText())
            && row.get("freeze_device_id").toString().equals(context.path("freezeDeviceId").asText())
            && row.get("source_recovery_epoch").toString().equals(context.path("sourceRecoveryEpoch").asText())
            && row.get("command_receipt_id").toString().equals(receipt.path("receiptId").asText())
            && row.get("command_sha256").toString().equals(receipt.path("commandSha256").asText())
            && row.get("from_work_round").toString().equals(decision.path("fromWorkRound").asText())
            && row.get("to_work_round").toString().equals(decision.path("toWorkRound").asText())
            && row.get("evidence_set_id").toString().equals(decision.path("evidenceSetId").asText())
            && row.get("verification_id").toString().equals(decision.path("verificationId").asText())
            && row.get("retained_manifest_sha256").toString().equals(decision.path("retainedManifestSha256").asText())
            && row.get("retained_manifest_version").toString().equals(decision.path("retainedManifestVersion").asText())
            && row.get("next_order_scan_seq").toString().equals(decision.path("nextOrderScanSeq").asText()));
        CommandErrors.integrity("ABORTED".equals(jdbc.queryForObject("SELECT status FROM submission WHERE id=?", String.class,
            Ids.bytes(UUID.fromString(context.path("submissionId").asText())))));
        var expected = new ArrayList<String>();
        for (var item : decision.path("products")) expected.add(item.path("baleId").asText()+":"+item.path("expectedDispatchCycle").asText()+":"+item.path("recordSha256").asText());
        var actual = jdbc.query("SELECT BIN_TO_UUID(bale_id),CAST(expected_cycle AS CHAR),record_sha256 FROM temporary_source_continuation_bale WHERE continuation_id=? ORDER BY bale_id",
            (rs, n) -> rs.getString(1)+":"+rs.getString(2)+":"+rs.getString(3),
            Ids.bytes(UUID.fromString(decision.path("ticketId").asText())));
        CommandErrors.integrity(expected.stream().sorted().toList().equals(actual.stream().sorted().toList()));
    }

    private void verifyActivation(JsonNode receipt) {
        var context = receipt.path("originalContext"); var decision = receipt.path("businessDecision");
        UUID ticket = UUID.fromString(decision.path("ticketId").asText());
        var rows = jdbc.queryForList("""
            SELECT BIN_TO_UUID(t.previous_authorization_id) previous_auth_id,
              t.retained_manifest_sha256,t.retained_manifest_version,t.next_order_scan_seq,
              t.from_work_round,BIN_TO_UUID(a.new_authorization_id) auth_id,BIN_TO_UUID(a.new_client_order_id) client_id,
              BIN_TO_UUID(a.target_device_id) device_id,BIN_TO_UUID(a.target_operator_id) operator_id,
              BIN_TO_UUID(a.command_receipt_id) receipt_id,a.receipt_text
            FROM temporary_source_continuation t JOIN temporary_source_continuation_activation a ON a.ticket_id=t.id
            WHERE t.id=? AND t.scope_id=? AND t.previous_submission_id=?
            """, Ids.bytes(ticket), Ids.bytes(UUID.fromString(context.path("scopeId").asText())),
            Ids.bytes(UUID.fromString(context.path("submissionId").asText())));
        CommandErrors.integrity(rows.size()==1
            && rows.getFirst().get("previous_auth_id").toString().equals(context.path("authorizationId").asText())
            && rows.getFirst().get("from_work_round").toString().equals(decision.path("fromWorkRound").asText())
            && rows.getFirst().get("retained_manifest_sha256").toString().equals(decision.path("retainedManifestSha256").asText())
            && rows.getFirst().get("retained_manifest_version").toString().equals(decision.path("retainedManifestVersion").asText())
            && rows.getFirst().get("next_order_scan_seq").toString().equals(decision.path("nextOrderScanSeq").asText()));
        var row = rows.getFirst();
        CommandErrors.integrity(decision.path("newAuthorizationId").asText().equals(row.get("auth_id").toString())
            && decision.path("newClientOrderId").asText().equals(row.get("client_id").toString())
            && decision.path("targetDeviceId").asText().equals(row.get("device_id").toString())
            && decision.path("targetOperatorId").asText().equals(row.get("operator_id").toString())
            && receipt.path("receiptId").asText().equals(row.get("receipt_id").toString()));
        var auth = jdbc.queryForList("SELECT BIN_TO_UUID(bundle_id) bundle_id,signed_token,manifest_text,manifest_sha256,signed_manifest FROM temporary_authorization WHERE id=?",
            Ids.bytes(UUID.fromString(decision.path("newAuthorizationId").asText())));
        CommandErrors.integrity(auth.size()==1);
        var stored = auth.getFirst(); var value = decision.path("authorization");
        CommandErrors.integrity(value.path("bundleId").asText().equals(stored.get("bundle_id").toString())
            && value.path("offlineToken").asText().equals(stored.get("signed_token").toString())
            && value.path("manifest").asText().equals(stored.get("manifest_text").toString())
            && value.path("manifestSha256").asText().equals(stored.get("manifest_sha256").toString())
            && value.path("signedManifest").asText().equals(stored.get("signed_manifest").toString()));
        CommandErrors.integrity(!decision.path("newAuthorizationId").asText().equals(context.path("authorizationId").asText())
            && rows.getFirst().get("receipt_text").toString().equals(SubmissionJson.canonical(decision)));
        CommandErrors.integrity(jdbc.queryForObject("SELECT COUNT(*) FROM temporary_work_origin WHERE creator_device_id=? AND client_order_id=? AND authorization_id=?",
            Integer.class, Ids.bytes(UUID.fromString(decision.path("targetDeviceId").asText())),
            Ids.bytes(UUID.fromString(decision.path("newClientOrderId").asText())), Ids.bytes(UUID.fromString(decision.path("newAuthorizationId").asText())))==1);
        var expected = new ArrayList<String>();
        for (var item : decision.path("retained")) expected.add(item.path("baleId").asText()+":"+item.path("expectedDispatchCycle").asText()+":"+item.path("recordSha256").asText());
        var actual = jdbc.query("SELECT BIN_TO_UUID(bale_id),CAST(expected_cycle AS CHAR),record_sha256 FROM temporary_source_continuation_bale WHERE continuation_id=? ORDER BY bale_id",
            (rs, n) -> rs.getString(1)+":"+rs.getString(2)+":"+rs.getString(3), Ids.bytes(ticket));
        CommandErrors.integrity(expected.stream().sorted().toList().equals(actual.stream().sorted().toList()));
        CommandErrors.integrity("ABORTED".equals(jdbc.queryForObject("SELECT status FROM submission WHERE id=?", String.class,
            Ids.bytes(UUID.fromString(context.path("submissionId").asText())))));
    }
}
