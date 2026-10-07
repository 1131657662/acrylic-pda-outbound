package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.command.CommandExpected;
import com.acrylic.outbound.temporary.command.CommandFacts;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.UUID;

/** Explicit test request construction; does not bypass the real HTTP command writer. */
final class ContinuationReceiptHttpCommands {
    private final ContinuationReceiptHttpFixture fixture;
    ContinuationReceiptHttpCommands(ContinuationReceiptHttpFixture fixture) { this.fixture = fixture; }

    ObjectNode request(UUID ticket) {
        return request(ticket,fixture.database.id("mappedSubmissionId"),fixture.database.id("mappedSetId"),5);
    }
    ObjectNode request(UUID ticket,UUID submission,UUID set,long next) {
        var database = fixture.database;
        var context = fixture.app.getBean(CommandFacts.class).read(submission);
        var body = SubmissionJson.object().put("commandVersion", 1)
            .put("commandType", ticket == null ? "REOPEN_WORK" : "ACTIVATE_CONTINUATION")
            .put("commandId", UUID.randomUUID().toString()).put("transportRecoveryEpoch", "1")
            .put("reason", "Synthetic stopped-source HTTP replay proof").put("nextOrderScanSeq", Long.toString(next));
        if (ticket != null) body.put("continuationTicketId", ticket.toString());
        for (String key : List.of("warehouseId", "submissionId", "workRound", "authorizationId", "freezeDeviceId", "sourceRecoveryEpoch"))
            body.set(key, context.json().get(key));
        body.putObject("source").put("kind", "TEMP").put("creatorDeviceId", context.text("creatorDeviceId"))
            .put("clientOrderId", context.text("clientOrderId"));
        var expected = CommandExpected.current(context, "1");
        for (String key : List.of("evidenceSetId", "evidenceSetSha256", "verificationId", "verificationVersion", "verificationAlgorithmVersion"))
            expected.set(key, context.json().get(key));
        var manifest = manifest(set); body.set("retainedManifest", manifest);
        expected.put("retainedManifestSha256", SubmissionJson.sha(manifest)).put("retainedManifestVersion", "1");
        body.set("expected", expected); return body;
    }

    private ObjectNode manifest(UUID set) {
        var manifest = SubmissionJson.object().put("version", 1); var rows = manifest.putArray("items");
        fixture.database.jdbc.query("""
            SELECT BIN_TO_UUID(bale_id),CAST(original_cycle AS CHAR),record_sha256 FROM source_evidence_item
            WHERE set_id=? AND item_kind='SCAN' AND record_status='ACTIVE' ORDER BY bale_id
            """, (rs, n) -> {
                rows.addObject().put("baleId", rs.getString(1)).put("expectedDispatchCycle", rs.getString(2))
                    .put("recordSha256", rs.getString(3)); return rs.getString(1);
            }, Ids.bytes(set));
        return manifest;
    }
}
