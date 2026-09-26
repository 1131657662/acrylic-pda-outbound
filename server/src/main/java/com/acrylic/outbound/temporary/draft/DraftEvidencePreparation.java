package com.acrylic.outbound.temporary.draft;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.pda.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.*;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeDeviceKeys;
import com.acrylic.outbound.temporary.protocol.LocalDraftStopProtocol;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DraftEvidencePreparation {
    private final TemporaryStore store;
    private final SourceEvidenceIntakeDeviceKeys keys;
    private final OfflineSigner signer;
    private final JsonText json;
    private final JdbcTemplate jdbc;
    public DraftEvidencePreparation(TemporaryStore store,SourceEvidenceIntakeDeviceKeys keys,OfflineSigner signer,JsonText json,JdbcTemplate jdbc) {
        this.store=store;this.keys=keys;this.signer=signer;this.json=json;this.jdbc=jdbc;
    }

    public LocalDraftStopProtocol.Expected require(JsonNode metadata,PdaSessions.Principal actor) {
        var draft=metadata.path("draft"); UUID authorization=SubmissionJson.uuid(draft,"authorizationId");
        var stored=store.get(authorization);
        if(!stored.userId().equals(actor.userId()) || !stored.deviceId().equals(actor.deviceId()) || !stored.warehouseId().equals(actor.warehouseId()))
            throw DraftEvidenceWire.missing();
        JsonNode receipt=SubmissionJson.parse(metadata.path("preparationReceipt").asText());
        DraftEvidenceWire.require(receipt.equals(SubmissionJson.parse(json.encode(stored.receipt()))));
        JsonNode manifest=SubmissionJson.parse(stored.receipt().manifest());
        DraftEvidenceWire.require(DraftEvidenceWire.sha(stored.receipt().manifest().getBytes(java.nio.charset.StandardCharsets.UTF_8)).equals(stored.receipt().manifestSha256()));
        String kid=jdbc.queryForObject("SELECT key_id FROM temporary_authorization WHERE id=?",String.class,Ids.bytes(authorization));
        var trusted=signer.publicKeyFor(kid);
        DraftEvidenceWire.require(trusted!=null && SubmissionJson.parse(metadata.path("preparationSigningKey").asText()).equals(SubmissionJson.parse(json.encode(trusted))));
        claims(stored,manifest,trusted); request(metadata,stored,manifest);
        baseline(stored,manifest);
        return new LocalDraftStopProtocol.Expected(SubmissionJson.uuid(metadata,"evidenceId"),SubmissionJson.uuid(draft,"clientOrderId"),
            stored.deviceId(),stored.id(),stored.warehouseId(),stored.userId(),SubmissionJson.uuid(manifest,"inventorySnapshotId"),
            Long.parseLong(stored.recoveryEpoch()),keys.trusted(stored.warehouseId(),stored.deviceId()));
    }

    private void claims(TemporaryStore.Stored stored,JsonNode manifest,OfflineSigner.PublicKeyView key) {
        var auth=DraftPreparationProof.verify(stored.receipt().offlineToken(),key,"acrylic-pda-temporary-work");
        var proof=DraftPreparationProof.verify(stored.receipt().signedManifest(),key,"acrylic-pda-temporary-preparation");
        var identity=manifest.path("identity");
        DraftEvidenceWire.require(auth.path("identity").equals(identity) && stored.id().toString().equals(auth.path("jti").asText())
            && "TEMP_WORK".equals(auth.path("purpose").asText()) && "TEMP_WORK".equals(manifest.path("purpose").asText()));
        for(var claim:new JsonNode[]{auth,proof})DraftEvidenceWire.require(stored.receipt().manifestSha256().equals(claim.path("manifestSha256").asText()));
        DraftEvidenceWire.require(stored.id().toString().equals(proof.path("authorizationId").asText())
            && stored.receipt().bundleId().equals(proof.path("bundleId").asText()));
        DraftEvidenceWire.require(stored.id().toString().equals(identity.path("authorizationId").asText())
            && stored.deviceId().toString().equals(identity.path("deviceId").asText()) && stored.userId().toString().equals(identity.path("userId").asText())
            && stored.warehouseId().toString().equals(identity.path("warehouseId").asText()) && stored.recoveryEpoch().equals(identity.path("serverRecoveryEpoch").asText()));
        DraftEvidenceWire.require(identity.size()==9 && stored.receipt().bundleId().equals(identity.path("bundleId").asText())
            && stored.authorizationEpoch().equals(identity.path("authorizationEpoch").asText()) && stored.userVersion().equals(identity.path("userAuthVersion").asText())
            && stored.deviceVersion().equals(identity.path("deviceVersion").asText()));
        DraftEvidenceWire.require(manifest.path("schemaVersion").isIntegralNumber() && manifest.path("schemaVersion").intValue()==1
            && "1".equals(manifest.path("barcodeRuleVersion").asText()) && "temporary_order:create pda:scan pda:submit".equals(auth.path("scope").asText()));
        for(var claim:new JsonNode[]{auth,proof})DraftEvidenceWire.require(claim.path("iat").isIntegralNumber() && claim.path("exp").isIntegralNumber()
            && stored.issuedAt().getEpochSecond()==claim.path("iat").longValue() && stored.expiresAt().getEpochSecond()==claim.path("exp").longValue());
        DraftEvidenceWire.require(stored.issuedAt().equals(java.time.Instant.parse(manifest.path("issuedAt").asText()))
            && stored.expiresAt().equals(java.time.Instant.parse(manifest.path("expiresAt").asText())) && stored.expiresAt().isAfter(stored.issuedAt()));
    }

    private void baseline(TemporaryStore.Stored stored,JsonNode manifest) {
        var rows=jdbc.queryForList("""
            SELECT BIN_TO_UUID(a.inventory_snapshot_id) AS snapshot_id,a.mapping_revision,s.version AS inventory_version,p.version AS policy_version
            FROM temporary_authorization a JOIN inventory_snapshot s ON s.id=a.inventory_snapshot_id AND s.warehouse_id=a.warehouse_id
            JOIN policy_version p ON p.id=a.policy_id AND p.warehouse_id=a.warehouse_id WHERE a.id=?
            """,Ids.bytes(stored.id()));
        DraftEvidenceWire.require(rows.size()==1);var row=rows.getFirst();
        DraftEvidenceWire.require(manifest.path("inventorySnapshotId").asText().equals(row.get("snapshot_id").toString())
            && manifest.path("mappingRevision").asText().equals(row.get("mapping_revision").toString())
            && manifest.path("inventoryVersion").asText().equals(row.get("inventory_version").toString())
            && manifest.path("policyVersion").asText().equals(row.get("policy_version").toString()));
    }

    private void request(JsonNode metadata,TemporaryStore.Stored stored,JsonNode manifest) {
        JsonNode original=SubmissionJson.parse(metadata.path("preparationRequest").asText());
        DraftEvidenceWire.require(original.size()==3 && original.path("inventorySnapshotId").equals(manifest.path("inventorySnapshotId"))
            && original.path("mappingRevision").equals(manifest.path("mappingRevision")) && original.path("policyVersion").equals(manifest.path("policyVersion")));
        DraftEvidenceWire.require(metadata.path("manifestSha256").asText().equals(stored.receipt().manifestSha256()));
        // The opaque original request key is retained, not promoted to a server receipt or business source S.
        SubmissionJson.uuid(metadata,"preparationRequestKey");
    }
}
