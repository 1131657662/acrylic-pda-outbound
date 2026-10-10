package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** New synthetic checkpoint fixture with one native source; real database time is supplied after the permission begin boundary. */
final class RecoveryTemporaryEvidenceDatabaseSeed {
    final RecoveryTemporaryActivationFixture activation;
    final RecoveryInitialFixture initial;
    final RecoveryOriginalEvidenceFixture evidence;
    final Map<String,String> seed;

    RecoveryTemporaryEvidenceDatabaseSeed(JdbcTemplate jdbc,Map<String,String> seed) throws Exception {
        this.seed=seed;
        var id=new RecoverySourceIdentity("TEMP",id("warehouseId"),id("mapping0ScopeId"),id("mapping0SubmissionId"),id("deviceId"),id("operatorId"),
            id("authorizationId"),null,id("deviceId"),id("mapping0ClientOrderId"),"1",1,1,5,1);
        activation=new RecoveryTemporaryActivationFixture(new RecoveryTemporaryBaselineFixture(
            new RecoveryPreparationFixture(new RecoverySourceStateFixture(RecoverySourceFixture.original(id)))));
        align(jdbc); activation.baseline.publish();
        activation.command.put("manifestSha256",text(activation.baseline.prep.receipt,"manifestSha256"));
        activation.proof.put("manifestSha256",text(activation.baseline.prep.receipt,"manifestSha256")).put("commandSha256",activation.digest()); activation.publish();
        initial=new RecoveryInitialFixture(activation.baseline.prep);
        evidence=new RecoveryOriginalEvidenceFixture(initial,ignored -> { });
        ((ObjectNode)initial.state.authority.source.manifest.path("source")).put("captureHighWatermark","1").put("auditThroughSeq","5");
        initial.state.authority.source.publish();
        clearOldEvidence(jdbc);
        require(jdbc.update("DELETE FROM bale_commitment WHERE bale_id=? AND source_scope_id=?",Ids.bytes(id("baleId")),Ids.bytes(id("mappedScopeId")))==1,
            "EVIDENCE_SYNTHETIC_SEED_OWNER");
        jdbc.update("UPDATE bale SET dispatch_cycle=2 WHERE id=?",Ids.bytes(id("baleId")));
    }

    private void align(JdbcTemplate jdbc) {
        var prep=activation.baseline.prep;
        var baseline=jdbc.queryForMap("SELECT BIN_TO_UUID(inventory_snapshot_id) snapshot,BIN_TO_UUID(policy_id) policy FROM temporary_authorization WHERE id=?",
            Ids.bytes(id("authorizationId")));
        prep.manifest.put("inventorySnapshotId",baseline.get("snapshot").toString()); prep.state.payload.put("inventorySnapshotId",baseline.get("snapshot").toString());
        ((ObjectNode)prep.state.payload.at("/captures/0")).put("snapshotId",baseline.get("snapshot").toString());
        ((ObjectNode)prep.state.payload.at("/scans/0")).put("baleId",seed.get("baleId"));
        prep.state.row().put("baleId",seed.get("baleId"));
        ((ObjectNode)prep.content.get("availability").path("items").get(0)).put("baleId",seed.get("baleId"));
        activation.baseline.policy.put("id",baseline.get("policy").toString());
        var mapping=jdbc.queryForMap("SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(product_id) product FROM delivery_mapping WHERE warehouse_id=?",Ids.bytes(id("warehouseId")));
        activation.baseline.mapping.put("id",mapping.get("id").toString()).put("productId",mapping.get("product").toString());
        activation.baseline.line().put("productId",mapping.get("product").toString());
        jdbc.update("UPDATE delivery_mapping SET delivery_code='012' WHERE id=UUID_TO_BIN(?)",mapping.get("id"));
        jdbc.update("UPDATE product SET model='MODEL',name='Product' WHERE id=UUID_TO_BIN(?)",mapping.get("product"));
        jdbc.update("UPDATE bale SET barcode='01234567890' WHERE id=?",Ids.bytes(id("baleId")));
        jdbc.update("UPDATE inventory_snapshot_bale SET delivery_code='012',model_snapshot='MODEL',name_snapshot='Product',raw_weight='1.234',weight_g=1234 WHERE bale_id=?",
            Ids.bytes(id("baleId")));
    }

    private void clearOldEvidence(JdbcTemplate jdbc) {
        byte[] scope=Ids.bytes(id("mapping0ScopeId"));
        for(String table:List.of("source_evidence_verification","source_evidence_item","source_evidence_set","source_evidence_object"))
            jdbc.update("DELETE FROM "+table+" WHERE scope_id=?",scope);
    }

    void atTime(JdbcTemplate jdbc,Instant saved) throws Exception {
        var prep=activation.baseline.prep; var issued=saved.minusSeconds(1).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        prep.state.authority.claims.put("iat",issued.getEpochSecond()).put("exp",issued.plusSeconds(172800).getEpochSecond());
        prep.manifest.put("issuedAt",issued.toString()).put("expiresAt",issued.plusSeconds(172800).toString());
        prep.state.payload.put("closedAt",saved.toString());
        ((ObjectNode)prep.state.payload.at("/captures/0")).put("confirmedAt",saved.toString());
        ((ObjectNode)prep.state.payload.at("/scans/0")).put("savedAt",saved.toString());
        activation.baseline.publish();
        activation.command.put("manifestSha256",text(prep.receipt,"manifestSha256")).put("activatedAt",issued.plusSeconds(1).toString());
        activation.proof.put("manifestSha256",text(prep.receipt,"manifestSha256")).put("commandSha256",activation.digest())
            .put("activatedAt",issued.plusSeconds(1).toString()).put("acknowledgedAt",saved.toString()); activation.publish();
        evidence.frozen.put("closedAt",saved.toString());
        ((ObjectNode)evidence.frozen.at("/captures/0")).put("confirmedAt",saved.toString());
        ((ObjectNode)evidence.frozen.at("/scans/0")).put("savedAt",saved.toString());
        for(var capture:evidence.frozen.path("captures")) evidence.replace("CAPTURE",capture);
        for(var scan:evidence.frozen.path("scans")) evidence.replace("SCAN",scan);
        evidence.replace("STOP",evidence.frozen); evidence.rebuild();
        jdbc.update("UPDATE temporary_authorization SET bundle_id=UUID_TO_BIN(?),user_auth_version=3,device_version=7,issued_at=?,expires_at=?,signed_token=?,"
            +"manifest_text=?,manifest_sha256=?,signed_manifest=?,key_id=? WHERE id=?",text(prep.receipt,"bundleId"),Timestamp.from(issued),
            Timestamp.from(issued.plusSeconds(172800)),text(prep.receipt,"offlineToken"),text(prep.receipt,"manifest"),text(prep.receipt,"manifestSha256"),
            text(prep.receipt,"signedManifest"),prep.state.authority.key.kid,Ids.bytes(id("authorizationId")));
        RecoveryTemporaryRestoreSeed.complete(jdbc,activation);
    }
    UUID id(String name) { return UUID.fromString(seed.get(name)); }
    void savedAt(Instant saved) {
        evidence.frozen.put("closedAt",saved.toString());
        ((ObjectNode)evidence.frozen.at("/captures/0")).put("confirmedAt",saved.toString());
        ((ObjectNode)evidence.frozen.at("/scans/0")).put("savedAt",saved.toString());
        for(var capture:evidence.frozen.path("captures")) evidence.replace("CAPTURE",capture);
        for(var scan:evidence.frozen.path("scans")) evidence.replace("SCAN",scan);
        evidence.replace("STOP",evidence.frozen); evidence.rebuild();
    }
}
