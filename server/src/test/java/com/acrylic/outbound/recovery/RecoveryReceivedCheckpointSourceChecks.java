package com.acrylic.outbound.recovery;

import com.acrylic.outbound.e2averify.E2aMigrationSnapshot;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Actual original preparation, registration, E2A and historical permission, never fixture replacements. */
public final class RecoveryReceivedCheckpointSourceChecks {
    public static void verify(JdbcTemplate jdbc,Path attempt,RecoveryReceivedCheckpointEnrollment.Begin begin) throws Exception {
        var end=new RecoveryPermissionBoundary(jdbc.getDataSource(),attempt).captureControlled(begin.expected());
        var history=RecoveryTemporaryEvidenceSourceEnrollment.retain(attempt,begin.expected(),begin.boundary(),end);
        var permission=RecoveryPermissionContinuity.verifyControlled(history);
        var transfer=readCanonical(Files.readAllBytes(attempt.resolve("received-transfer.json")));
        var stop=RecoverySourceIdentity.read(transfer.path("source")); var frozen=freeze(stop);
        var originalRoot=Path.of(text(transfer,"originalRoot")); byte[] payload=Files.readAllBytes(originalRoot.resolve(text(transfer,"payloadPin")+".bin"));
        var input=SubmissionJson.parse(Files.readAllBytes(attempt.resolve("private/android-http-input.json")));
        var snapshots=new E2aMigrationSnapshot(jdbc,attempt,attempt.resolve("storage")); var before=snapshots.capture();
        var preparation=new RecoveryTemporaryPreparationCapture(jdbc.getDataSource()).capture(new RecoveryTemporaryPreparationCapture.Request(
            begin.database(),frozen,input.path("activationCommand").toString()));
        assertThat(preparation.originalOrigin()).containsExactly(begin.origin());
        var occupancy=new RecoveryOriginalOccupancyCapture(jdbc.getDataSource()).capture(new RecoveryOriginalOccupancyCapture.Request(begin.database(),frozen,payload));
        var source=RecoverySourceFixture.forRestore(attempt,frozen); source.advance();
        replace(source,"origin",preparation.originalOrigin()); replace(source,"payload",payload);
        replace(source,"result",Files.readAllBytes(originalRoot.resolve(text(transfer,"resultPin")+".bin")));
        replace(source,"occupancy",SubmissionJson.canonical(readCanonical(occupancy.originalBytes()).path("occupancy")).getBytes(StandardCharsets.UTF_8));
        var windows=SubmissionJson.object().put("formatVersion",1).put("payloadSha256",SubmissionJson.sha(SubmissionJson.parse(payload)))
            .put("credentialSha256",sha256(preparation.originalOrigin())); windows.set("source",RecoveryOriginalOccupancyFacts.source(frozen));
        var window=windows.putArray("windows").addObject().put("from",permission.from().toString()).put("through",permission.through().toString())
            .put("userId",frozen.operatorId().toString()).put("deviceId",frozen.deviceId().toString()); window.setAll((ObjectNode)permission.effective());
        replace(source,"permission",SubmissionJson.canonical(windows).getBytes(StandardCharsets.UTF_8)); source.publish();
        retain(attempt,begin,history,transfer,source,preparation,occupancy);
        assertThat(snapshots.capture()).isEqualTo(before); Files.writeString(attempt.resolve("checkpoint-source-final.json"),before.toPrettyString());
    }

    private static void retain(Path attempt,RecoveryReceivedCheckpointEnrollment.Begin begin,RecoveryPermissionContinuity.Request history,
            JsonNode received,RecoverySourceFixture source,RecoveryTemporaryPreparationCapture.Observation preparation,
            RecoveryOriginalOccupancyCapture.Observation occupancy) throws Exception {
        Path root=begin.originals();
        for(byte[] bytes:List.of(preparation.originalMetadata(),occupancy.originalBytes())) Files.write(root.resolve(sha256(bytes)+".json"),bytes);
        Files.write(root.resolve(sha256(preparation.originalOrigin())+".bin"),preparation.originalOrigin());
        var evidence=evidence(attempt,RecoverySourceIdentity.read(received.path("source")),begin.registration());
        var keys=new RecoveryReceiptKeys(List.of(Base64.getDecoder().decode(text(received,"serverPublicKey"))));
        var proof=new RecoveryInitialSource(source.reader(),keys).readTemporary(new RecoveryInitialSource.Request(source.materials,source.pin,source.identity,List.of()),evidence);
        var registration=new RecoveryDeviceRegistration(source.control.location(),source.control.checkpoint).read(new RecoveryDeviceRegistration.Request(root,
            begin.registration().originalSha256(),new RecoveryDeviceRegistration.Expected(begin.database().mysqlServerUuid(),begin.database().schema(),"1",
                source.identity.warehouseId(),source.identity.deviceId(),begin.registration().observedAt())));
        RecoveryTemporaryEvidenceTrust.verify(source.read(),registration,evidence,new RecoverySourcePermission(keys).verify(source.read()),List.of(history),begin.database());
        var value=SubmissionJson.object().put("sourceRoot",source.materials.toString()).put("sourcePin",source.pin).put("originalRoot",root.toString())
            .put("registrationPin",begin.registration().originalSha256()).put("preparationPin",preparation.independentSha256())
            .put("occupancyPin",occupancy.originalSha256()).put("beginPin",history.beginSha256()).put("endPin",history.endSha256()).put("binlogPin",history.binlogSha256())
            .put("databaseSchema",begin.database().schema()).put("mysqlServerUuid",begin.database().mysqlServerUuid().toString())
            .put("initialAuditEvents",Long.toString(proof.auditEventCount())).put("ordinaryWritesAllowed",false).put("localDeletionAllowed",false);
        value.set("source",RecoveryOriginalOccupancyFacts.source(source.identity)); value.set("evidence",RecoveryTemporaryEvidenceTransfer.evidence(evidence));
        Files.write(attempt.resolve("checkpoint-transfer.json"),RecoveryControlJson.encode(value));
    }

    private static RecoveryOriginalEvidence.Request evidence(Path attempt,RecoverySourceIdentity stop,RecoveryDeviceRegistrationCapture.Observation registration) throws Exception {
        var requests=SubmissionJson.parse(Files.readAllBytes(attempt.resolve("android-result.json"))).path("originalRequests");
        byte[] open=null,end=null; UUID key=null; var pages=new TreeMap<Integer,byte[]>(); var objects=new HashMap<String,byte[]>();
        for(var entries=requests.fields();entries.hasNext();) {
            var entry=entries.next(); var request=entry.getValue(); String operation=text(request,"operation");
            if(operation.equals("OPEN")) { open=Base64.getDecoder().decode(text(request,"body")); key=UUID.fromString(entry.getKey()); }
            if(operation.equals("STOP")) end=Base64.getDecoder().decode(text(request,"body"));
            if(operation.equals("PAGE")) { byte[] bytes=Base64.getDecoder().decode(text(request,"body")); pages.put(SubmissionJson.parse(bytes).path("pageCore").path("pageNo").intValue(),bytes); }
            if(operation.equals("ITEM")) { byte[] bytes=Base64.getDecoder().decode(text(request,"raw")); objects.put(sha256(bytes),bytes); }
        }
        String publicKey=text(readCanonical(registration.originalBytes()).path("device"),"publicKey");
        var trusted=java.security.KeyFactory.getInstance("EC").generatePublic(new java.security.spec.X509EncodedKeySpec(Base64.getDecoder().decode(publicKey)));
        return new RecoveryOriginalEvidence.Request(new RecoveryOriginalEvidence.Bundle(open,List.copyOf(pages.values()),end,objects),key,trusted,stop);
    }

    static RecoverySourceIdentity freeze(RecoverySourceIdentity id) {
        return new RecoverySourceIdentity(id.sourceKind(),id.warehouseId(),id.scopeId(),id.submissionId(),id.deviceId(),id.operatorId(),id.qualificationId(),
            id.canonicalOrderId(),id.creatorDeviceId(),id.clientOrderId(),id.sourceRecoveryEpoch(),id.workRound(),id.captureHighWatermark(),id.auditThroughSeq()-1,id.orderScanHighWatermark());
    }
    static void replace(RecoverySourceFixture source,String kind,byte[] bytes) throws Exception {
        Files.write(source.materials.resolve(sha256(bytes)+".bin"),bytes);
        ((ObjectNode)source.manifest.path("objects")).putObject(kind).put("sha256",sha256(bytes)).put("byteCount",Integer.toString(bytes.length));
    }
}
