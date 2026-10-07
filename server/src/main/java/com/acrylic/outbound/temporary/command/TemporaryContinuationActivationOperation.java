package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.TemporaryCommands;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Consumes one issued ticket and transfers its retained K rows atomically. */
public final class TemporaryContinuationActivationOperation implements CommandOperation {
    private final JdbcTemplate jdbc;
    private final TemporaryContinuationAuthorizationIssuer authorizations;

    public TemporaryContinuationActivationOperation(JdbcTemplate jdbc, TemporaryContinuationAuthorizationIssuer authorizations) {
        this.jdbc = jdbc; this.authorizations = authorizations;
    }
    @Override public String commandType() { return "ACTIVATE_CONTINUATION"; }
    @Override public boolean recordsDecision() { return true; }
    @Override public boolean requiresCandidateContext() { return false; }
    @Override public void apply(CommandContext context) { throw new IllegalStateException("Full command required"); }

    @Override public JsonNode execute(CommandWrite write) {
        var body = write.command().body(); UUID ticket = UUID.fromString(body.path("continuationTicketId").asText());
        var expected = write.command().expected();
        var items = TemporaryRetainedManifest.parse(body.path("retainedManifest"),
            expected.path("retainedManifestSha256").asText(), expected.path("retainedManifestVersion").asLong());
        var source = authorizations.issue(write, ticket, write.context().id("scopeId"), write.context().id("submissionId"));
        require(source.source().nextOrderScanSeq() == Long.parseLong(body.path("nextOrderScanSeq").asText()), "CONTINUATION_SEQUENCE_CHANGED");
        require(source.source().manifestSha().equals(expected.path("retainedManifestSha256").asText())
            && source.source().manifestVersion() == expected.path("retainedManifestVersion").asLong(), "CONTINUATION_MANIFEST_CHANGED");
        requireItems(ticket, items);
        int moved = jdbc.update("""
            UPDATE bale_commitment k JOIN temporary_source_continuation_bale r ON r.bale_id=k.bale_id
            SET k.state='TEMP_CONTINUATION_RESERVED',k.temporary_continuation_id=?,k.source_evidence_set_id=?,
              k.submission_id=NULL,k.order_id=NULL
            WHERE r.continuation_id=? AND k.source_scope_id=? AND k.source_warehouse_id=?
              AND k.dispatch_cycle=r.expected_cycle AND k.temporary_continuation_id IS NULL
              AND k.disposition_id IS NULL AND k.continuation_id IS NULL AND k.recovery_package_id IS NULL
              AND k.disposition_evidence_id IS NULL AND k.pc_work_id IS NULL AND k.pc_disposition_id IS NULL
              AND ((k.state='SOURCE_RESERVED' AND k.submission_id IS NULL AND k.source_evidence_set_id=?)
                OR (k.state='REVIEW_RESERVED' AND k.source_evidence_set_id IS NULL AND k.submission_id=?
                  AND EXISTS(SELECT 1 FROM submission s JOIN submission_scope c ON c.id=s.scope_id
                    WHERE s.id=k.submission_id AND c.id=k.source_scope_id AND c.canonical_order_id=k.order_id
                      AND c.current_submission_id=s.id AND c.work_round=s.work_round AND s.status='ABORTED')))
            """, Ids.bytes(ticket),source.source().evidenceSet(),Ids.bytes(ticket),Ids.bytes(write.context().id("scopeId")),
            source.source().warehouse(),source.source().evidenceSet(),Ids.bytes(write.context().id("submissionId")));
        require(moved == items.size(), "CONTINUATION_OWNER_CHANGED");
        var decision = decision(write, ticket, source, items);
        insertActivation(write, ticket, source, decision);
        if (jdbc.update("UPDATE temporary_source_continuation SET status='ACTIVATED',new_authorization_id=?,receipt_text=? WHERE id=? AND status='TICKET_ISSUED'",
            Ids.bytes(source.authorization()), SubmissionJson.canonical(decision), Ids.bytes(ticket)) != 1)
            throw conflict("CONTINUATION_TICKET_CHANGED");
        return decision;
    }

    private void requireItems(UUID ticket, List<TemporaryRetainedManifest.Item> expected) {
        var actual = jdbc.query("SELECT BIN_TO_UUID(bale_id),CAST(expected_cycle AS CHAR),record_sha256 FROM temporary_source_continuation_bale WHERE continuation_id=? ORDER BY bale_id",
            (rs, n) -> rs.getString(1)+":"+rs.getString(2)+":"+rs.getString(3), Ids.bytes(ticket));
        var wanted = expected.stream().map(i -> i.baleId()+":"+i.cycle()+":"+i.recordSha256()).sorted().toList();
        if (!actual.equals(wanted)) throw conflict("CONTINUATION_RETAINED_SET_INVALID");
    }

    private JsonNode decision(CommandWrite write, UUID ticket, TemporaryContinuationAuthorizationIssuer.Issued issued,
        List<TemporaryRetainedManifest.Item> items) {
        var result = SubmissionJson.object().put("kind", commandType()).put("status", "ACTIVATED")
            .put("ticketId", ticket.toString()).put("fromWorkRound", Long.toString(issued.source().fromRound()))
            .put("toWorkRound", Long.toString(issued.source().fromRound()+1)).put("nextOrderScanSeq", Long.toString(issued.source().nextOrderScanSeq()))
            .put("newAuthorizationId", issued.authorization().toString()).put("newBundleId", issued.bundle().toString())
            .put("newClientOrderId", issued.clientOrder().toString()).put("targetDeviceId", write.actor().path("deviceId").asText())
            .put("targetOperatorId", write.actor().path("principalId").asText()).put("scopeId", write.context().id("scopeId").toString())
            .put("retainedManifestSha256", issued.source().manifestSha()).put("retainedManifestVersion", Long.toString(issued.source().manifestVersion()));
        var authorization = result.putObject("authorization").put("authorizationId", issued.authorization().toString())
            .put("bundleId", issued.bundle().toString()).put("offlineToken", issued.receipt().offlineToken())
            .put("manifest", issued.receipt().manifest()).put("manifestSha256", issued.receipt().manifestSha256())
            .put("signedManifest", issued.receipt().signedManifest());
        var products = result.putArray("retained");
        for (var item : items) products.addObject().put("baleId", item.baleId().toString())
            .put("expectedDispatchCycle", Long.toString(item.cycle())).put("recordSha256", item.recordSha256());
        return result;
    }

    private void insertActivation(CommandWrite write, UUID ticket, TemporaryContinuationAuthorizationIssuer.Issued issued, JsonNode decision) {
        UUID device = UUID.fromString(write.actor().path("deviceId").asText()), operator = UUID.fromString(write.actor().path("principalId").asText());
        if (jdbc.update("""
            INSERT INTO temporary_source_continuation_activation(ticket_id,scope_id,from_work_round,new_authorization_id,new_client_order_id,
              target_device_id,target_operator_id,command_receipt_id,receipt_text,activated_at)
            VALUES(?,?,?,?,?,?,?,?,?,?)
            """, Ids.bytes(ticket), Ids.bytes(write.context().id("scopeId")), issued.source().fromRound(), Ids.bytes(issued.authorization()),
            Ids.bytes(issued.clientOrder()), Ids.bytes(device), Ids.bytes(operator), Ids.bytes(write.receiptId()),
            SubmissionJson.canonical(decision), java.sql.Timestamp.from(write.recordedAt())) != 1)
            throw new IllegalStateException("CONTINUATION_ACTIVATION_INSERT");
    }
    private static void require(boolean valid, String code) { if (!valid) throw conflict(code); }
    private static BusinessException conflict(String code) { return new BusinessException(409, code, "TEMP continuation activation cannot be committed"); }
}
