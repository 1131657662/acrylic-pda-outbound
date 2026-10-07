package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.JsonText;
import com.acrylic.outbound.pda.*;
import com.acrylic.outbound.policies.PolicyStore;
import com.acrylic.outbound.temporary.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** New synthetic source only, before immutable migrations; shared bytes for the Android consumer. */
final class TemporaryAndroidIntakeSeed {
    private final RetentionDatabaseFixture f;
    private final JsonText json=new JsonText(new ObjectMapper());
    TemporaryAndroidIntakeSeed(RetentionDatabaseFixture fixture) { f=fixture; }

    void prepare(ObjectNode body) throws java.io.IOException {
        normalizeInventory();
        try { E2aCrossSourceHttpSetup.signing(f.profile.attempt); }
        catch(java.security.GeneralSecurityException error) { throw new java.io.IOException(error); }
        catch(Exception error) { throw new java.io.IOException(error); }
        var signer=new OfflineSigner(f.profile.attempt.resolve("private/server-private.der").toString(),
            f.profile.attempt.resolve("private/server-public.der").toString(),json);
        var parts=new TemporaryRoundIntakeParts(f).build();
        var principal=new PdaSessions.Principal(UUID.randomUUID(),f.id("operatorId"),"synthetic",
            f.id("deviceId"),f.id("warehouseId"),"1","1");
        var old=new TemporaryStore(f.jdbc).get(f.id("authorizationId"));
        var snapshot=f.jdbc.queryForMap("SELECT row_count,manifest_sha256 FROM inventory_snapshot WHERE id=UUID_TO_BIN(?)",f.seed.get("intakeSnapshot"));
        var base=new TemporaryBaseline.View(f.seed.get("warehouseId"),f.seed.get("intakeSnapshot"),"1","1",
            ((Number)snapshot.get("row_count")).intValue(),(String)snapshot.get("manifest_sha256"),new PolicyStore(f.jdbc).current(f.id("warehouseId")));
        var work=new TemporaryIssuer(signer,json).issue(new TemporaryIssuer.Binding(old.id(),UUID.fromString(old.receipt().bundleId()),1,"1",principal),base,parts);
        save(work);normalizeBody(body,work.issued().toString());
        export(body,work,signer);
    }

    private void normalizeInventory() {
        var rows=f.jdbc.queryForList("SELECT BIN_TO_UUID(b.id) id,b.barcode FROM inventory_snapshot_bale s JOIN bale b ON b.id=s.bale_id WHERE s.snapshot_id=UUID_TO_BIN(?)",f.seed.get("intakeSnapshot"));
        for(var row:rows)f.jdbc.update("UPDATE bale SET barcode=? WHERE id=UUID_TO_BIN(?)",
            "001"+String.format("%08d",Long.parseLong((String)row.get("barcode"))),row.get("id"));
        f.jdbc.update("UPDATE inventory_snapshot_bale SET raw_weight='1',weight_g=1000 WHERE snapshot_id=UUID_TO_BIN(?)",f.seed.get("intakeSnapshot"));
    }

    private void save(TemporaryIssuer.Work work) {
        var r=work.receipt();
        f.jdbc.update("UPDATE temporary_authorization SET issued_at=?,expires_at=?,key_id=?,signed_token=?,manifest_text=?,manifest_sha256=?,signed_manifest=? WHERE id=UUID_TO_BIN(?)",
            java.sql.Timestamp.from(work.issued()),java.sql.Timestamp.from(work.expires()),work.keyId(),r.offlineToken(),r.manifest(),r.manifestSha256(),r.signedManifest(),r.authorizationId());
        for(var part:work.parts())f.jdbc.update("UPDATE temporary_preparation_part SET compressed=? WHERE authorization_id=UUID_TO_BIN(?) AND part_name=?",
            part.compressed(),r.authorizationId(),part.descriptor().name());
    }

    private void normalizeBody(ObjectNode body,String at) {
        body.put("closedAt",at);
        for(var c:array(body,"captures")) {
            String barcode="001"+String.format("%08d",number(c,"orderScanSeq"));
            ((ObjectNode)c).put("barcode",barcode).put("rawInput",barcode).put("confirmedAt",at).put("failureAckAt",at);
        }
        for(var s:array(body,"scans"))((ObjectNode)s).put("rawWeight","1").put("savedAt",at)
            .put("barcode","001"+String.format("%08d",number(s,"orderScanSeq")));
    }

    private void export(ObjectNode body,TemporaryIssuer.Work work,OfflineSigner signer) throws java.io.IOException {
        var vector=object();vector.set("receipt",parse(json.encode(work.receipt())));vector.set("signingKey",parse(json.encode(signer.publicKey())));
        var session=vector.putObject("session").put("serverRecoveryEpoch","1").put("userAuthVersion","1").put("deviceVersion","1");
        session.put("userId",f.seed.get("operatorId")).put("deviceId",f.seed.get("deviceId")).put("warehouseId",f.seed.get("warehouseId"));
        session.putObject("capabilities").put("temporaryWork",true).put("barcodeSync",true).put("auditUpload",true).put("recoveryRead",true).put("formalOrders",false);
        var gzip=vector.putObject("gzip");for(var part:work.parts())gzip.put(part.descriptor().name(),Base64.getEncoder().encodeToString(part.compressed()));
        vector.putObject("activationCommand").put("activatedAt",work.issued().toString());vector.putObject("activationReceipt");
        vector.set("original",body);
        Files.writeString(f.profile.attempt.resolve("private/android-source.json"),canonical(vector),StandardOpenOption.CREATE_NEW);
    }
}
