package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Uses only newly allocated schema fixture IDs; never reissues a prior device or signature. */
final class RecoveryTemporaryPreparationDatabaseSeed {
    private RecoveryTemporaryPreparationDatabaseSeed() { }

    static RecoveryTemporaryActivationFixture seed(JdbcTemplate jdbc, Map<String,String> seed) throws Exception {
        var id = new RecoverySourceIdentity("TEMP", uuid(seed, "warehouseId"), uuid(seed, "mapping0ScopeId"), uuid(seed, "mapping0SubmissionId"),
            uuid(seed, "deviceId"), uuid(seed, "operatorId"), uuid(seed, "authorizationId"), null,
            uuid(seed, "deviceId"), uuid(seed, "mapping0ClientOrderId"), "1", 1, 7, 12, 9);
        var state = new RecoverySourceStateFixture(RecoverySourceFixture.original(id));
        var f = new RecoveryTemporaryActivationFixture(new RecoveryTemporaryBaselineFixture(new RecoveryPreparationFixture(state)));
        var prep = f.baseline.prep;
        String snapshot = jdbc.queryForObject("SELECT BIN_TO_UUID(inventory_snapshot_id) FROM temporary_authorization WHERE id=UUID_TO_BIN(?)",
            String.class, id.qualificationId().toString());
        prep.manifest.put("inventorySnapshotId", snapshot); state.payload.put("inventorySnapshotId", snapshot);
        ((ObjectNode)state.payload.path("captures").get(0)).put("snapshotId", snapshot); f.baseline.publish();
        f.command.put("manifestSha256", prep.receipt.path("manifestSha256").asText());
        f.proof.put("manifestSha256", prep.receipt.path("manifestSha256").asText()).put("commandSha256", f.digest()); f.publish();
        authorization(jdbc, f); parts(jdbc, f);
        jdbc.update("INSERT INTO temporary_preparation_ack VALUES(UUID_TO_BIN(?),?,?,?,?)", id.qualificationId().toString(), f.digest(),
            Timestamp.from(Instant.parse(f.receipt.path("activatedAt").asText())), Timestamp.from(Instant.parse(f.receipt.path("acknowledgedAt").asText())),
            f.receipt.path("signedAck").asText()); return f;
    }

    private static void authorization(JdbcTemplate jdbc, RecoveryTemporaryActivationFixture f) {
        var p = f.baseline.prep; var receipt = p.receipt; var id = p.state.authority.source.identity;
        jdbc.update("UPDATE temporary_authorization SET bundle_id=UUID_TO_BIN(?),user_auth_version=3,device_version=7,issued_at=?,expires_at=?,"
            + "signed_token=?,manifest_text=?,manifest_sha256=?,signed_manifest=?,key_id=? WHERE id=UUID_TO_BIN(?)",
            receipt.path("bundleId").asText(), Timestamp.from(Instant.parse(p.manifest.path("issuedAt").asText())),
            Timestamp.from(Instant.parse(p.manifest.path("expiresAt").asText())), receipt.path("offlineToken").asText(), receipt.path("manifest").asText(),
            receipt.path("manifestSha256").asText(), receipt.path("signedManifest").asText(), p.state.authority.key.kid, id.qualificationId().toString());
    }

    private static void parts(JdbcTemplate jdbc, RecoveryTemporaryActivationFixture f) {
        var p = f.baseline.prep;
        for (String name : List.of("inventory", "mapping", "policy", "rules", "availability"))
            jdbc.update("INSERT INTO temporary_preparation_part VALUES(UUID_TO_BIN(?),?,?)", p.state.authority.source.identity.qualificationId().toString(),
                name, Base64.getDecoder().decode(p.origin.path("parts").path(name).asText()));
    }

    private static UUID uuid(Map<String,String> seed, String field) { return UUID.fromString(seed.get(field)); }
}
