package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.e2averify.E2aMigrationSnapshot;
import com.acrylic.outbound.submissions.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.web.context.WebApplicationContext;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Actual Android/HTTP native source. The target dump is an explicit missing-table sample, not a temporal cutoff. */
public final class RecoveryReceivedTemporarySourceChecks {
    public static void capture(WebApplicationContext app, Path attempt) throws Exception {
        var jdbc=app.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
        var input=SubmissionJson.parse(Files.readAllBytes(attempt.resolve("private/android-http-input.json")));
        var output=SubmissionJson.parse(Files.readAllBytes(attempt.resolve("android-result.json")));
        UUID submission=uuid(output,"submissionId"); var head=app.getBean(SubmissionStore.class).required(submission);
        abort(attempt,input,head);
        var identity=identity(jdbc,head);
        String hash=jdbc.queryForObject("SELECT payload_sha256 FROM submission_payload WHERE submission_id=?",String.class,Ids.bytes(submission));
        byte[] payload=app.getBean(SubmissionFiles.class).read(hash);
        UUID verification=UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM source_evidence_verification WHERE submission_id=? AND result='VERIFIED_COMPLETE_AT_STOP'",
            String.class,Ids.bytes(submission)));
        var database=new RecoveryDeviceRegistrationCapture.Database(UUID.fromString(jdbc.queryForObject("SELECT @@server_uuid",String.class)),
            jdbc.queryForObject("SELECT DATABASE()",String.class),identity.sourceRecoveryEpoch());
        var keys=new RecoveryReceiptKeys(List.of(Files.readAllBytes(attempt.resolve("private/server-public.der"))));
        var request=new RecoveryReceivedTemporaryCapture.Request(database,identity,payload,verification,keys,attempt.resolve("storage"));
        var snapshots=new E2aMigrationSnapshot(jdbc,attempt,attempt.resolve("storage")); var before=snapshots.capture();
        var observation=new RecoveryReceivedTemporaryCapture(jdbc.getDataSource()).capture(request);
        assertThat(snapshots.capture()).isEqualTo(before);
        retain(attempt,request,observation);
        Files.writeString(attempt.resolve("received-source-before.json"),before.toPrettyString());
        Files.writeString(attempt.resolve("received-source-after.json"),snapshots.capture().toPrettyString());
        dump(jdbc,attempt);
    }

    private static void retain(Path attempt,RecoveryReceivedTemporaryCapture.Request request,
            RecoveryReceivedTemporaryCapture.Observation observation) throws Exception {
        var database=request.database(); var identity=request.source(); byte[] payload=request.payload();
        var root=Files.createDirectory(attempt.resolve("received-originals"));
        Files.write(root.resolve(observation.independentSha256()+".json"),observation.originalBytes());
        Files.write(root.resolve(sha256(payload)+".bin"),payload);
        var material=RecoveryTemporaryEvidenceMaterial.decode(observation.originalBytes());
        var receipt=text(material.path("rows").path("head"),"receipt_text").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(root.resolve(sha256(receipt)+".bin"),receipt);
        var transfer=SubmissionJson.object().put("originalRoot",root.toString()).put("rowsPin",observation.independentSha256())
            .put("observedAt",observation.observedAt().toString()).put("payloadPin",sha256(payload)).put("resultPin",sha256(receipt))
            .put("mysqlServerUuid",database.mysqlServerUuid().toString()).put("databaseSchema",database.schema())
            .put("serverPublicKey",Base64.getEncoder().encodeToString(Files.readAllBytes(attempt.resolve("private/server-public.der"))));
        transfer.set("source",RecoveryOriginalOccupancyFacts.source(identity));
        Files.write(attempt.resolve("received-transfer.json"),RecoveryControlJson.encode(transfer));
    }

    private static RecoverySourceIdentity identity(org.springframework.jdbc.core.JdbcTemplate jdbc, SubmissionStore.Head head) {
        return jdbc.queryForObject("""
            SELECT BIN_TO_UUID(s.scope_id),BIN_TO_UUID(c.canonical_order_id),BIN_TO_UUID(c.creator_device_id),BIN_TO_UUID(c.client_order_id),
                e.capture_high_watermark,e.audit_through_seq,e.order_scan_high_watermark
            FROM submission s JOIN submission_scope c ON c.id=s.scope_id JOIN source_evidence_set e ON e.submission_id=s.id WHERE s.id=?
            """,(rs,n) -> new RecoverySourceIdentity("TEMP",head.warehouse(),UUID.fromString(rs.getString(1)),head.control().id(),
                head.control().device(),head.actor(),head.control().temporary().authorization(),UUID.fromString(rs.getString(2)),
                UUID.fromString(rs.getString(3)),UUID.fromString(rs.getString(4)),head.control().epoch(),1,rs.getLong(5),rs.getLong(6),rs.getLong(7)),
            Ids.bytes(head.control().id()));
    }

    private static void abort(Path attempt, com.fasterxml.jackson.databind.JsonNode input, SubmissionStore.Head head) throws Exception {
        var c=head.control(); var body=SubmissionJson.object().putNull("assignmentId").putNull("continuationId")
            .put("authorizationId",c.temporary().authorization().toString()).put("freezeDeviceId",c.device().toString())
            .put("workRound","1").put("sourceRecoveryEpoch",c.epoch()).put("reason","isolated received-source retention"); body.set("source",c.source());
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1/pda/submissions/"+c.id()+"/abort"))
            .header("Authorization","Bearer "+text(input,"accessToken")).header("X-Server-Recovery-Epoch","1").header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(SubmissionJson.canonical(body))).build();
        var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofByteArray());
        Files.write(attempt.resolve("received-abort-http.json"),response.body()); assertThat(response.statusCode()).isEqualTo(200);
        assertThat(SubmissionJson.parse(response.body()).path("data").path("payloadPresent").booleanValue()).isTrue();
    }

    private static void dump(org.springframework.jdbc.core.JdbcTemplate jdbc, Path attempt) throws Exception {
        String schema=jdbc.queryForObject("SELECT DATABASE()",String.class);
        dumpPart(attempt,List.of("--no-data",schema),"schema");
        dumpPart(attempt,List.of("--no-create-info","--ignore-table="+schema+".source_evidence_verification",schema),"data");
        try(var output=Files.newOutputStream(attempt.resolve("received-missing-verification.sql"),StandardOpenOption.CREATE_NEW)) {
            Files.copy(attempt.resolve("received-schema.sql"),output); Files.copy(attempt.resolve("received-data.sql"),output);
        }
        Files.writeString(attempt.resolve("received-missing-verification.sha256"),sha256(Files.readAllBytes(attempt.resolve("received-missing-verification.sql"))));
    }
    private static void dumpPart(Path attempt,List<String> options,String part) throws Exception {
        var args=new ArrayList<>(List.of("C:/Program Files/MySQL/MySQL Server 8.0/bin/mysqldump.exe","--defaults-file="+attempt.resolve("client.cnf"),
            "--single-transaction","--no-tablespaces","--set-gtid-purged=OFF","--skip-routines","--skip-events","--skip-triggers"));
        args.addAll(options);
        var process=new ProcessBuilder(args).redirectOutput(attempt.resolve("received-"+part+".sql").toFile())
            .redirectError(attempt.resolve("received-"+part+".err").toFile()).start();
        assertThat(process.waitFor(30,TimeUnit.SECONDS)).isTrue(); assertThat(process.exitValue()).isZero();
    }
}
