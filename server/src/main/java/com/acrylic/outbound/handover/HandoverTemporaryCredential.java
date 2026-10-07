package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.*;
import com.acrylic.outbound.temporary.draft.DraftPreparationProof;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Re-proves the receiving operator's original general TEMP credential and durable download acknowledgement. */
@Component
final class HandoverTemporaryCredential {
    private final OfflineSigner signer;
    private final TemporaryStore store;
    private final JdbcTemplate jdbc;

    HandoverTemporaryCredential(OfflineSigner signer, TemporaryStore store, JdbcTemplate jdbc) {
        this.signer = signer; this.store = store; this.jdbc = jdbc;
    }

    JsonNode verify(TemporaryStore.Stored row) {
        try {
            var receipt = row.receipt(); var manifest = SubmissionJson.parse(receipt.manifest());
            require(HandoverCommand.sha(receipt.manifest().getBytes(StandardCharsets.UTF_8)).equals(receipt.manifestSha256()));
            String kid = jdbc.queryForObject("SELECT key_id FROM temporary_authorization WHERE id=?", String.class, Ids.bytes(row.id()));
            var key = signer.publicKeyFor(kid);
            var auth = DraftPreparationProof.verify(receipt.offlineToken(), key, "acrylic-pda-temporary-work");
            var proof = DraftPreparationProof.verify(receipt.signedManifest(), key, "acrylic-pda-temporary-preparation");
            identity(row, manifest, auth); times(row, manifest, List.of(auth, proof));
            for (var claim : List.of(auth, proof)) require(receipt.manifestSha256().equals(claim.path("manifestSha256").asText()));
            require(row.id().toString().equals(proof.path("authorizationId").asText())
                && receipt.bundleId().equals(proof.path("bundleId").asText()));
            baseline(row, manifest); acknowledged(row);
            return manifest;
        } catch (RuntimeException invalid) { throw invalid(); }
    }

    private void identity(TemporaryStore.Stored row, JsonNode manifest, JsonNode auth) {
        var identity = manifest.path("identity");
        var expected = SubmissionJson.object().put("authorizationId", row.id().toString())
            .put("bundleId", row.receipt().bundleId()).put("userId", row.userId().toString())
            .put("deviceId", row.deviceId().toString()).put("warehouseId", row.warehouseId().toString())
            .put("authorizationEpoch", row.authorizationEpoch()).put("userAuthVersion", row.userVersion())
            .put("deviceVersion", row.deviceVersion()).put("serverRecoveryEpoch", row.recoveryEpoch());
        require(identity.equals(expected) && auth.path("identity").equals(expected)
            && row.id().toString().equals(auth.path("jti").asText())
            && "TEMP_WORK".equals(auth.path("purpose").asText()) && "TEMP_WORK".equals(manifest.path("purpose").asText())
            && manifest.path("schemaVersion").isIntegralNumber() && manifest.path("schemaVersion").intValue() == 1
            && "1".equals(manifest.path("barcodeRuleVersion").asText())
            && "temporary_order:create pda:scan pda:submit".equals(auth.path("scope").asText()));
    }

    private void times(TemporaryStore.Stored row, JsonNode manifest, List<JsonNode> claims) {
        require(row.issuedAt().equals(Instant.parse(manifest.path("issuedAt").asText()))
            && row.expiresAt().equals(Instant.parse(manifest.path("expiresAt").asText()))
            && row.expiresAt().isAfter(row.issuedAt()) && !row.issuedAt().isAfter(Instant.now()));
        for (var claim : claims) require(claim.path("iat").isIntegralNumber() && claim.path("exp").isIntegralNumber()
            && claim.path("iat").longValue() == row.issuedAt().getEpochSecond()
            && claim.path("exp").longValue() == row.expiresAt().getEpochSecond());
    }

    private void baseline(TemporaryStore.Stored row, JsonNode manifest) {
        var binding = jdbc.queryForMap("SELECT BIN_TO_UUID(inventory_snapshot_id) snapshot,CAST(mapping_revision AS CHAR) mapping,"
            + "BIN_TO_UUID(policy_id) policy FROM temporary_authorization WHERE id=?", Ids.bytes(row.id()));
        String policy = jdbc.queryForObject("SELECT CAST(version AS CHAR) FROM policy_version WHERE id=UUID_TO_BIN(?) AND warehouse_id=?",
            String.class, binding.get("policy"), Ids.bytes(row.warehouseId()));
        require(manifest.path("inventorySnapshotId").asText().equals(binding.get("snapshot"))
            && manifest.path("mappingRevision").asText().equals(binding.get("mapping"))
            && manifest.path("policyVersion").asText().equals(policy));
    }

    private void acknowledged(TemporaryStore.Stored row) {
        var ack = store.ack(row); require(ack != null);
        var header = SubmissionJson.parse(java.util.Base64.getUrlDecoder().decode(ack.signedAck().split("\\.", -1)[0]));
        var key = signer.publicKeyFor(SubmissionJson.text(header, "kid"));
        var proof = DraftPreparationProof.verify(ack.signedAck(), key, "acrylic-pda-temporary-activation");
        String digest = jdbc.queryForObject("SELECT command_sha256 FROM temporary_preparation_ack WHERE authorization_id=?",
            String.class, Ids.bytes(row.id()));
        require(row.id().toString().equals(proof.path("authorizationId").asText())
            && row.receipt().bundleId().equals(proof.path("bundleId").asText())
            && row.receipt().manifestSha256().equals(proof.path("manifestSha256").asText())
            && row.recoveryEpoch().equals(proof.path("serverRecoveryEpoch").asText())
            && row.userId().toString().equals(proof.path("userId").asText())
            && row.deviceId().toString().equals(proof.path("deviceId").asText())
            && ack.activatedAt().equals(proof.path("activatedAt").asText())
            && ack.acknowledgedAt().equals(proof.path("acknowledgedAt").asText())
            && digest != null && digest.equals(proof.path("commandSha256").asText()));
        Instant activated = Instant.parse(ack.activatedAt()), acknowledged = Instant.parse(ack.acknowledgedAt());
        require(!activated.isBefore(row.issuedAt()) && activated.isBefore(row.expiresAt())
            && !acknowledged.isBefore(row.issuedAt()) && !activated.isAfter(acknowledged.plusSeconds(300)));
    }

    private static void require(boolean valid) { if (!valid) throw invalid(); }
    private static BusinessException invalid() {
        return new BusinessException(409, "HANDOVER_TARGET_CREDENTIAL_INVALID", "接收人临时授权或下载确认不可核验，请保留原资料");
    }
}
