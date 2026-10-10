package com.acrylic.outbound.recovery;

import com.acrylic.outbound.e2averify.E2aMigrationSnapshot;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Rebinds the same actual source objects to explicit synthetic target control; no business evidence is generated. */
final class RecoveryReceivedArchiveSetup {
    static Context create(JdbcTemplate jdbc,Path attempt,Path original) throws Exception {
        var checkpoint=readCanonical(Files.readAllBytes(original.resolve("checkpoint-transfer.json")));
        var received=readCanonical(Files.readAllBytes(original.resolve("received-transfer.json")));
        var source=bind(jdbc,attempt,checkpoint); var supply=RecoveryClosedTestSupply.of(source);
        var request=request(source,attempt,original,checkpoint,received);
        var keys=new RecoveryReceiptKeys(List.of(Base64.getDecoder().decode(text(received,"serverPublicKey"))));
        var material=RecoveryTemporaryEvidenceMaterial.decode(Files.readAllBytes(request.rows().materialRoot().resolve(request.rows().independentSha256()+".json")));
        var expected=RecoveryUnsubmittedEvidence.expected(request.evidence()); var set=material.path("rows").path("evidence").path("source_evidence_set").get(0);
        var archive=new RecoveryRecoveredTemporaryArchive(request.evidence().expected(),rowId(set,"id"),rowId(set,"stop_object_id"),
            new RecoveryRecoveredTemporaryArchive.Provenance(source.control.read().recordSha256(),source.pin,request.rows().independentSha256(),
                request.occupancy().independentSha256(),request.registration().independentSha256(),expected.proof().setHash(),expected.proof().payloadSha256()),
            source.read().original("payload"),source.read().original("result"),expected.stopProofBytes());
        var facts=RecoveryTemporaryNativeHandoverFixture.input(archive);
        ((ObjectNode)facts.path("archive")).put("verificationId",text(material.path("verification"),"id"))
            .put("verificationSha256",sha256(RecoveryTemporaryEvidenceMaterial.encode(material.path("verification"))));
        jdbc.execute("CREATE TABLE recovery_received_consumer_probe(id INT PRIMARY KEY,source_pin CHAR(64) NOT NULL) ENGINE=InnoDB");
        return new Context(jdbc,attempt,source,request,facts,new RecoveryReceivedTemporaryArchiveConsumer(jdbc.getDataSource(),supply.authority(),keys),
            new E2aMigrationSnapshot(jdbc,attempt,attempt.resolve("storage")));
    }

    private static RecoverySourceFixture bind(JdbcTemplate jdbc,Path attempt,JsonNode transfer) throws Exception {
        var source=RecoverySourceFixture.forRestore(attempt.resolve("storage"),RecoverySourceIdentity.read(transfer.path("source")));
        Path root=Path.of(text(transfer,"sourceRoot")); byte[] bytes=Files.readAllBytes(root.resolve(text(transfer,"sourcePin")+".json"));
        assertThat(sha256(bytes)).isEqualTo(text(transfer,"sourcePin")); var manifest=readCanonical(bytes);
        for(String kind:RecoverySourceManifest.OBJECTS) {
            var descriptor=manifest.path("objects").path(kind); String hash=text(descriptor,"sha256");
            byte[] object=Files.readAllBytes(root.resolve(hash+".bin")); assertThat(sha256(object)).isEqualTo(hash);
            Files.write(source.materials.resolve(hash+".bin"),object); ((ObjectNode)source.manifest.path("objects")).set(kind,descriptor.deepCopy());
        }
        source.publish(); RecoveryClosedTestSupply.writers(source.control);
        new RecoveryClosedProjection(jdbc.getDataSource(),RecoveryClosedTestSupply.of(source).authority(),1).project();
        source.advance(); new RecoveryClosedProjection(jdbc.getDataSource(),RecoveryClosedTestSupply.of(source).authority(),1).project(); return source;
    }

    private static RecoveryReceivedTemporaryArchiveConsumer.Request request(RecoverySourceFixture source,Path attempt,Path original,
            JsonNode checkpoint,JsonNode received) throws Exception {
        var db=new RecoveryDeviceRegistrationCapture.Database(uuid(checkpoint,"mysqlServerUuid"),text(checkpoint,"databaseSchema"),"1");
        Path root=Path.of(text(checkpoint,"originalRoot")); var id=source.identity;
        var prep=new RecoveryOriginalTemporaryPreparation.Request(root,text(checkpoint,"preparationPin"),db,observed(root,text(checkpoint,"preparationPin")));
        var registration=new RecoveryDeviceRegistration.Request(root,text(checkpoint,"registrationPin"),new RecoveryDeviceRegistration.Expected(
            db.mysqlServerUuid(),db.schema(),"1",id.warehouseId(),id.deviceId(),observed(root,text(checkpoint,"registrationPin"))));
        var occupancy=new RecoveryOriginalOccupancy.Request(root,text(checkpoint,"occupancyPin"),db,observed(root,text(checkpoint,"occupancyPin")));
        var history=new RecoveryPermissionContinuity.Request(new RecoveryPermissionBoundary.Request(db,id.operatorId(),id.deviceId()),
            Files.readAllBytes(original.resolve("evidence-permission-begin.json")),text(checkpoint,"beginPin"),
            Files.readAllBytes(original.resolve("evidence-permission-end.json")),text(checkpoint,"endPin"),
            Files.readAllBytes(original.resolve("evidence-permission-binlog.bin")),text(checkpoint,"binlogPin"));
        var rows=new RecoveryReceivedTemporaryMaterial.Request(Path.of(text(received,"originalRoot")),text(received,"rowsPin"),db,instant(received,"observedAt"));
        return new RecoveryReceivedTemporaryArchiveConsumer.Request(source.materials,prep,registration,RecoveryTemporaryEvidenceTransfer.evidence(checkpoint.path("evidence")),
            occupancy,List.of(history),rows,attempt.resolve("storage"));
    }

    private static Instant observed(Path root,String pin) throws Exception { return instant(readCanonical(Files.readAllBytes(root.resolve(pin+".json"))),"observedAt"); }
    private static UUID rowId(JsonNode row,String field) { var buffer=java.nio.ByteBuffer.wrap(Base64.getDecoder().decode(text(row,field))); return new UUID(buffer.getLong(),buffer.getLong()); }
    record Context(JdbcTemplate jdbc,Path attempt,RecoverySourceFixture source,RecoveryReceivedTemporaryArchiveConsumer.Request request,
            JsonNode nativeFacts,RecoveryReceivedTemporaryArchiveConsumer consumer,E2aMigrationSnapshot snapshots) { }
}
