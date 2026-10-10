package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Lossless test transport of captured public native inputs. Contains no device/server private keys. */
final class RecoveryTemporaryEvidenceTransfer {
    static ObjectNode evidence(RecoveryOriginalEvidence.Request request) {
        var value=SubmissionJson.object().put("openKey",request.openOriginalKey().toString())
            .put("publicKey",Base64.getEncoder().encodeToString(request.trustedKey().getEncoded()))
            .put("open",Base64.getEncoder().encodeToString(request.bundle().openBytes()))
            .put("stop",Base64.getEncoder().encodeToString(request.bundle().stopBytes()));
        value.set("source",RecoveryOriginalOccupancyFacts.source(request.expected()));
        var pages=value.putArray("pages"); request.bundle().pageBytes().forEach(bytes -> pages.add(Base64.getEncoder().encodeToString(bytes)));
        var objects=value.putObject("objects"); request.bundle().originalObjects().forEach((hash,bytes) -> objects.put(hash,Base64.getEncoder().encodeToString(bytes)));
        return value;
    }
    static RecoveryOriginalEvidence.Request evidence(JsonNode value) throws Exception {
        var pages=new ArrayList<byte[]>(); for(var page:value.path("pages")) pages.add(Base64.getDecoder().decode(page.textValue()));
        var objects=new HashMap<String,byte[]>(); value.path("objects").fields().forEachRemaining(entry -> objects.put(entry.getKey(),Base64.getDecoder().decode(entry.getValue().textValue())));
        var key=KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(text(value,"publicKey"))));
        var bundle=new RecoveryOriginalEvidence.Bundle(Base64.getDecoder().decode(text(value,"open")),pages,Base64.getDecoder().decode(text(value,"stop")),objects);
        return new RecoveryOriginalEvidence.Request(bundle,uuid(value,"openKey"),key,RecoverySourceIdentity.read(value.path("source")));
    }
    static RecoveryTemporaryEvidenceRestore.Request request(RecoverySourceFixture source,Path attempt,Path original,JsonNode transfer) throws Exception {
        var db=new RecoveryDeviceRegistrationCapture.Database(uuid(transfer,"mysqlServerUuid"),text(transfer,"databaseSchema"),"1");
        Path root=Path.of(text(transfer,"originalRoot")); var id=source.identity;
        var prep=new RecoveryOriginalTemporaryPreparation.Request(root,text(transfer,"preparationPin"),db,Instant.parse(text(transfer,"preparationAt")));
        var registration=new RecoveryDeviceRegistration.Request(root,text(transfer,"registrationPin"),new RecoveryDeviceRegistration.Expected(
            db.mysqlServerUuid(),db.schema(),"1",id.warehouseId(),id.deviceId(),Instant.parse(text(transfer,"registrationAt"))));
        var occupancy=new RecoveryUnsubmittedOccupancy.Request(root,text(transfer,"occupancyPin"),db,Instant.parse(text(transfer,"occupancyAt")));
        var history=new RecoveryPermissionContinuity.Request(new RecoveryPermissionBoundary.Request(db,id.operatorId(),id.deviceId()),
            Files.readAllBytes(original.resolve("evidence-permission-begin.json")),text(transfer,"beginPin"),
            Files.readAllBytes(original.resolve("evidence-permission-end.json")),text(transfer,"endPin"),
            Files.readAllBytes(original.resolve("evidence-permission-binlog.bin")),text(transfer,"binlogPin"));
        var rows=new RecoveryTemporaryEvidenceMaterial.Request(root,text(transfer,"rowsPin"),db,Instant.parse(text(transfer,"rowsAt")));
        return new RecoveryTemporaryEvidenceRestore.Request(new RecoveryTemporaryPreparationRestore.Request(source.materials,source.pin,prep),registration,
            evidence(transfer.path("evidence")),occupancy,List.of(history),rows,attempt.resolve("storage"));
    }
}
