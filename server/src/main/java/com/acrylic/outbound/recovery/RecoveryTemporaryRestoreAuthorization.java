package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Locks the retained authorization; restoration cannot insert, renew or alter credentials. */
final class RecoveryTemporaryRestoreAuthorization {
    private static final String FIELDS = "authorizationId bundleId warehouseId deviceId userId authorizationEpoch userAuthVersion deviceVersion "
        + "serverRecoveryEpoch inventorySnapshotId mappingRevision policyId issuedAt expiresAt keyId offlineToken manifest manifestSha256 signedManifest";
    private final JdbcTemplate jdbc;
    RecoveryTemporaryRestoreAuthorization(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    void requireMatches(RecoveryTemporaryRestoreMaterial material) {
        var actual = jdbc.query("""
            SELECT BIN_TO_UUID(id) authorizationId,BIN_TO_UUID(bundle_id) bundleId,BIN_TO_UUID(warehouse_id) warehouseId,
             BIN_TO_UUID(device_id) deviceId,BIN_TO_UUID(user_id) userId,authorization_epoch authorizationEpoch,
             user_auth_version userAuthVersion,device_version deviceVersion,recovery_epoch serverRecoveryEpoch,
             BIN_TO_UUID(inventory_snapshot_id) inventorySnapshotId,mapping_revision mappingRevision,BIN_TO_UUID(policy_id) policyId,
             issued_at issuedAt,expires_at expiresAt,key_id keyId,signed_token offlineToken,manifest_text manifest,
             manifest_sha256 manifestSha256,signed_manifest signedManifest
            FROM temporary_authorization WHERE id=? FOR UPDATE
            """, (rs, n) -> {
                var row = SubmissionJson.object();
                for (String field : FIELDS.split(" ")) row.put(field, rs.getString(field));
                row.put("issuedAt", rs.getTimestamp("issuedAt").toInstant().toString());
                row.put("expiresAt", rs.getTimestamp("expiresAt").toInstant().toString()); return row;
            }, Ids.bytes(material.identity().qualificationId()));
        require(actual.size() == 1, "RECOVERY_TEMP_RESTORE_AUTHORIZATION_MISSING");
        require(actual.getFirst().equals(expected(material)), "RECOVERY_TEMP_RESTORE_AUTHORIZATION_CONFLICT");
        new RecoveryTemporaryRestoreBaseline(jdbc).requireMatches(material);
    }

    private ObjectNode expected(RecoveryTemporaryRestoreMaterial material) {
        var value = SubmissionJson.object(); var manifest = material.manifest(); var receipt = material.receipt();
        for (String field : "authorizationId bundleId warehouseId deviceId userId authorizationEpoch userAuthVersion deviceVersion serverRecoveryEpoch".split(" "))
            value.put(field, text(manifest.path("identity"), field));
        for (String field : List.of("inventorySnapshotId", "mappingRevision", "issuedAt", "expiresAt")) value.put(field, text(manifest, field));
        for (String field : List.of("offlineToken", "manifest", "manifestSha256", "signedManifest")) value.put(field, text(receipt, field));
        value.put("policyId", text(material.content().get("policy"), "id")).put("keyId", material.keyId());
        value.put("issuedAt", RecoveryPreparationBinding.time(manifest, "issuedAt").toString());
        value.put("expiresAt", RecoveryPreparationBinding.time(manifest, "expiresAt").toString()); return value;
    }
}
