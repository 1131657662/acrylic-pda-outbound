package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.e2averify.E2aMigrationSnapshot;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** New empty target; original native rows are supplied, never recomputed or reissued. */
public final class RecoveryReceivedTemporaryTargetChecks {
    public static void verify(JdbcTemplate jdbc, Path attempt, Path original) throws Exception {
        importOriginal(jdbc,attempt,original);
        var transfer=readCanonical(Files.readAllBytes(original.resolve("received-transfer.json")));
        var source=bind(attempt,transfer); var supply=RecoveryClosedTestSupply.of(source);
        new RecoveryClosedProjection(jdbc.getDataSource(),supply.authority(),1).project(); source.advance();
        supply=RecoveryClosedTestSupply.of(source); new RecoveryClosedProjection(jdbc.getDataSource(),supply.authority(),1).project();
        var database=new RecoveryDeviceRegistrationCapture.Database(uuid(transfer,"mysqlServerUuid"),text(transfer,"databaseSchema"),"1");
        var request=new RecoveryReceivedTemporaryVerificationRestore.Request(source.materials,
            new RecoveryReceivedTemporaryMaterial.Request(Path.of(text(transfer,"originalRoot")),text(transfer,"rowsPin"),database,
                Instant.parse(text(transfer,"observedAt"))),attempt.resolve("storage"));
        var keys=new RecoveryReceiptKeys(List.of(Base64.getDecoder().decode(text(transfer,"serverPublicKey"))));
        var restorer=new RecoveryReceivedTemporaryVerificationRestore(jdbc.getDataSource(),supply.authority(),keys);
        var snapshots=new E2aMigrationSnapshot(jdbc,attempt,attempt.resolve("storage")); var before=snapshots.capture();
        Files.writeString(attempt.resolve("received-target-before.json"),before.toPrettyString());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM source_evidence_verification",Long.class)).isZero();
        var probes=new Probes(jdbc,source,restorer,request,snapshots,before);
        lockedStock(probes);
        materialRefusals(request,restorer);
        fileRefusal(request,restorer);
        ownerRefusal(probes);
        refusals(probes);
        var result=restorer.restore(request); assertThat(result.insertedRows()).isEqualTo(1);
        assertThat(result.ordinaryWritesAllowed()).isFalse(); assertThat(result.localDeletionAllowed()).isFalse();
        assertThat(restorer.restore(request).insertedRows()).isZero();
        var after=snapshots.capture(); conserved(before,after);
        Files.writeString(attempt.resolve("received-target-after.json"),after.toPrettyString());
        Files.write(attempt.resolve("received-restore-result.json"),RecoveryControlJson.encode(SubmissionJson.object()
            .put("verificationId",result.verificationId().toString()).put("verificationSha256",result.verificationSha256())
            .put("ordinaryWritesAllowed",false).put("localDeletionAllowed",false)));
        conflict(probes,result.verificationId(),after);
    }

    private static void lockedStock(Probes p) throws Exception {
        var id=Ids.bytes(p.source().identity.submissionId());
        byte[] bale=p.jdbc().queryForObject("SELECT bale_id FROM bale_commitment WHERE submission_id=?",byte[].class,id);
        try(var connection=p.jdbc().getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try(var statement=connection.prepareStatement("SELECT bale_id FROM inventory_position WHERE bale_id=? FOR UPDATE")) {
                statement.setBytes(1,bale); try(var rows=statement.executeQuery()) { assertThat(rows.next()).isTrue(); }
            }
            try { assertThatThrownBy(() -> p.restore().restore(p.request())).hasMessageContaining("Lock wait timeout exceeded"); }
            finally { connection.rollback(); }
        }
        assertThat(p.snapshots().capture()).isEqualTo(p.before());
    }

    private static void materialRefusals(RecoveryReceivedTemporaryVerificationRestore.Request request,
            RecoveryReceivedTemporaryVerificationRestore restore) {
        var zero=new RecoveryReceivedTemporaryMaterial.Request(request.rows().materialRoot(),ZERO_HASH,request.rows().database(),request.rows().notAfter());
        assertThatThrownBy(() -> restore.restore(new RecoveryReceivedTemporaryVerificationRestore.Request(request.sourceRoot(),zero,request.storage())))
            .hasMessageContaining("RECOVERY_RECEIVED_PIN_REQUIRED");
        var early=new RecoveryReceivedTemporaryMaterial.Request(request.rows().materialRoot(),request.rows().independentSha256(),request.rows().database(),Instant.EPOCH);
        assertThatThrownBy(() -> restore.restore(new RecoveryReceivedTemporaryVerificationRestore.Request(request.sourceRoot(),early,request.storage())))
            .hasMessageContaining("RECOVERY_RECEIVED_PIN_SOURCE");
        var wrongDatabase=new RecoveryDeviceRegistrationCapture.Database(UUID.randomUUID(),request.rows().database().schema(),"1");
        var wrong=new RecoveryReceivedTemporaryMaterial.Request(request.rows().materialRoot(),request.rows().independentSha256(),wrongDatabase,request.rows().notAfter());
        assertThatThrownBy(() -> restore.restore(new RecoveryReceivedTemporaryVerificationRestore.Request(request.sourceRoot(),wrong,request.storage())))
            .hasMessageContaining("RECOVERY_RECEIVED_PIN_SOURCE");
    }

    private static void fileRefusal(RecoveryReceivedTemporaryVerificationRestore.Request request,
            RecoveryReceivedTemporaryVerificationRestore restore) throws Exception {
        var material=RecoveryTemporaryEvidenceMaterial.decode(Files.readAllBytes(request.rows().materialRoot().resolve(request.rows().independentSha256()+".json")));
        Path file=request.storage().resolve(text(material.path("rows").path("evidence").path("source_evidence_object").get(0),"file_key"));
        byte[] original=Files.readAllBytes(file),changed=original.clone(); changed[0]^=1; Files.write(file,changed);
        try { assertThatThrownBy(() -> restore.restore(request)).hasMessageContaining("RECOVERY_RECEIVED_EVIDENCE_FILE"); }
        finally { Files.write(file,original); }
    }

    private static void ownerRefusal(Probes p) {
        var jdbc=p.jdbc(); var source=p.source(); var restore=p.restore(); var request=p.request();
        byte[] id=Ids.bytes(source.identity.submissionId());
        jdbc.update("UPDATE bale_commitment SET dispatch_cycle=dispatch_cycle+1 WHERE submission_id=?",id);
        try { assertThatThrownBy(() -> restore.restore(request)).hasMessageContaining("RECOVERY_OCCUPANCY_CAPTURE_OWNER"); }
        finally { jdbc.update("UPDATE bale_commitment SET dispatch_cycle=dispatch_cycle-1 WHERE submission_id=?",id); }
    }

    private static void refusals(Probes p) throws Exception {
        var jdbc=p.jdbc(); var source=p.source(); var restore=p.restore(); var request=p.request(); var snapshots=p.snapshots(); var before=p.before();
        jdbc.update("UPDATE outbound_order SET row_version=row_version+1 WHERE id=?",Ids.bytes(source.identity.canonicalOrderId()));
        var changed=snapshots.capture();
        assertThatThrownBy(() -> restore.restore(request)).hasMessageContaining("RECOVERY_RECEIVED_PREREQUISITES_CHANGED");
        assertThat(snapshots.capture()).isEqualTo(changed);
        jdbc.update("UPDATE outbound_order SET row_version=row_version-1 WHERE id=?",Ids.bytes(source.identity.canonicalOrderId()));
        assertThat(snapshots.capture()).isEqualTo(before);
        jdbc.execute("CREATE TRIGGER received_restore_tail AFTER INSERT ON source_evidence_verification FOR EACH ROW UPDATE outbound_order SET row_version=row_version+1 WHERE id=UNHEX('"
            + java.util.HexFormat.of().formatHex(Ids.bytes(source.identity.canonicalOrderId()))+"')");
        try { assertThatThrownBy(() -> restore.restore(request)).hasMessageContaining("RECOVERY_RECEIVED_PREREQUISITES_CHANGED"); }
        finally { jdbc.execute("DROP TRIGGER received_restore_tail"); }
        assertThat(snapshots.capture()).isEqualTo(before);
        var wrong=new RecoveryReceivedTemporaryMaterial.Request(request.rows().materialRoot(),"f".repeat(64),request.rows().database(),request.rows().notAfter());
        assertThatThrownBy(() -> restore.restore(new RecoveryReceivedTemporaryVerificationRestore.Request(request.sourceRoot(),wrong,request.storage())))
            .hasMessageContaining("CONTROL_FILE_MISSING");
        assertThat(snapshots.capture()).isEqualTo(before);
    }

    private static void conflict(Probes p,UUID verification,JsonNode expected) throws Exception {
        var jdbc=p.jdbc(); var restore=p.restore(); var request=p.request(); var snapshots=p.snapshots();
        var id=Ids.bytes(verification); String hash=jdbc.queryForObject("SELECT known_facts_sha256 FROM source_evidence_verification WHERE id=?",String.class,id);
        jdbc.update("UPDATE source_evidence_verification SET known_facts_sha256=? WHERE id=?","0".repeat(64),id);
        var before=snapshots.capture(); assertThatThrownBy(() -> restore.restore(request)).hasMessageContaining("RECOVERY_RECEIVED_VERIFICATION_CONFLICT");
        assertThat(snapshots.capture()).isEqualTo(before); jdbc.update("UPDATE source_evidence_verification SET known_facts_sha256=? WHERE id=?",hash,id);
        assertThat(snapshots.capture()).isEqualTo(expected);
    }

    private static RecoverySourceFixture bind(Path attempt,JsonNode transfer) throws Exception {
        var source=RecoverySourceFixture.forRestore(attempt.resolve("storage"),RecoverySourceIdentity.read(transfer.path("source")));
        for(String kind:List.of("payload","result")) {
            String hash=text(transfer,kind+"Pin"); byte[] bytes=Files.readAllBytes(Path.of(text(transfer,"originalRoot")).resolve(hash+".bin"));
            assertThat(sha256(bytes)).isEqualTo(hash); Files.write(source.materials.resolve(hash+".bin"),bytes);
            ((com.fasterxml.jackson.databind.node.ObjectNode)source.manifest.path("objects")).putObject(kind).put("sha256",hash).put("byteCount",Integer.toString(bytes.length));
        }
        source.publish(); RecoveryClosedTestSupply.writers(source.control); return source;
    }

    private static void conserved(JsonNode before,JsonNode after) {
        before.path("tables").fields().forEachRemaining(entry -> {
            if(!entry.getKey().equals("source_evidence_verification")) assertThat(after.path("tables").path(entry.getKey())).as(entry.getKey()).isEqualTo(entry.getValue());
        });
        assertThat(after.path("files")).isEqualTo(before.path("files")); assertThat(after.path("schema")).isEqualTo(before.path("schema"));
        assertThat(after.path("flywayHistory")).isEqualTo(before.path("flywayHistory"));
    }

    private static void importOriginal(JdbcTemplate jdbc,Path attempt,Path original) throws Exception {
        assertThat(original.toRealPath().getParent()).isEqualTo(attempt.toRealPath().getParent());
        assertThat(Files.exists(original.resolve("test-completed.marker"))).isTrue();
        Path dump=original.resolve("received-missing-verification.sql"); String pin=Files.readString(original.resolve("received-missing-verification.sha256")).trim();
        assertThat(sha256(Files.readAllBytes(dump))).isEqualTo(pin);
        var process=new ProcessBuilder("C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe","--defaults-file="+attempt.resolve("client.cnf"),
            "--default-character-set=utf8mb4",jdbc.queryForObject("SELECT DATABASE()",String.class)).redirectInput(dump.toFile())
            .redirectOutput(attempt.resolve("received-import.out").toFile()).redirectError(attempt.resolve("received-import.err").toFile()).start();
        assertThat(process.waitFor(30,TimeUnit.SECONDS)).isTrue(); assertThat(process.exitValue()).isZero();
        try(var files=Files.walk(original.resolve("storage"))) {
            for(Path file:files.toList()) {
                Path target=attempt.resolve("storage").resolve(original.resolve("storage").relativize(file));
                if(Files.isDirectory(file)) Files.createDirectories(target); else Files.copy(file,target);
            }
        }
        assertThat(sha256(Files.readAllBytes(dump))).isEqualTo(pin); Files.writeString(attempt.resolve("received-input-dump.sha256"),pin);
    }

    private record Probes(JdbcTemplate jdbc,RecoverySourceFixture source,RecoveryReceivedTemporaryVerificationRestore restore,
            RecoveryReceivedTemporaryVerificationRestore.Request request,E2aMigrationSnapshot snapshots,JsonNode before) { }
}
