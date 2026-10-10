package com.acrylic.outbound.recovery;

import com.acrylic.outbound.e2averify.E2aMigrationSnapshot;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Actual new target dump import, exact original four-table restore and all-table/file conservation. */
public final class RecoveryTemporaryEvidenceTargetChecks {
    public static void consumeArchive(JdbcTemplate jdbc,Path attempt,Path original) throws Exception {
        RecoveryTemporaryRestoreImport.restore(jdbc,attempt,original);
        var transfer=readCanonical(Files.readAllBytes(original.resolve("evidence-transfer.json")));
        var snapshots=new E2aMigrationSnapshot(jdbc,attempt,attempt.resolve("storage"));
        Files.writeString(attempt.resolve("evidence-imported-snapshot.json"),snapshots.capture().toPrettyString());
        var source=bind(jdbc,attempt,transfer); var supply=RecoveryClosedTestSupply.of(source);
        var keys=new RecoveryReceiptKeys(List.of(Base64.getDecoder().decode(text(transfer,"publicKey"))));
        var request=RecoveryTemporaryEvidenceTransfer.request(source,attempt,original,transfer);
        var context=new Context(jdbc,attempt,source,supply,keys,request,snapshots,snapshots.capture());
        RecoveryArchiveConsumerChecks.verify(context);
        save(context,original,transfer);
    }

    public static void verifyArchive(JdbcTemplate jdbc,Path attempt,Path original) throws Exception {
        RecoveryTemporaryRestoreImport.restore(jdbc,attempt,original);
        var transfer=readCanonical(Files.readAllBytes(original.resolve("evidence-transfer.json")));
        var snapshots=new E2aMigrationSnapshot(jdbc,attempt,attempt.resolve("storage"));
        Files.writeString(attempt.resolve("evidence-imported-snapshot.json"),snapshots.capture().toPrettyString());
        var source=bind(jdbc,attempt,transfer); var supply=RecoveryClosedTestSupply.of(source);
        var keys=new RecoveryReceiptKeys(List.of(Base64.getDecoder().decode(text(transfer,"publicKey"))));
        var request=RecoveryTemporaryEvidenceTransfer.request(source,attempt,original,transfer);
        var context=new Context(jdbc,attempt,source,supply,keys,request,snapshots,snapshots.capture());
        assertThatThrownBy(() -> context.restorer().verifyArchive(request)).hasMessage("RECOVERY_ARCHIVE_RESTORED_ROWS_REQUIRED");
        context.preserved(); context.restorer().restore(request); var complete=snapshots.capture();
        RecoveryRecoveredArchiveChecks.verify(context,complete);
        Files.writeString(attempt.resolve("evidence-target-before.json"),context.before().toPrettyString());
        Files.writeString(attempt.resolve("evidence-target-after.json"),snapshots.capture().toPrettyString()); save(context,original,transfer);
    }

    public static void verify(JdbcTemplate jdbc,Path attempt,Path original) throws Exception {
        RecoveryTemporaryRestoreImport.restore(jdbc,attempt,original);
        var transfer=readCanonical(Files.readAllBytes(original.resolve("evidence-transfer.json")));
        var snapshots=new E2aMigrationSnapshot(jdbc,attempt,attempt.resolve("storage")); var restored=snapshots.capture();
        Files.writeString(attempt.resolve("evidence-imported-snapshot.json"),restored.toPrettyString());
        var checkpoint=SubmissionJson.parse(Files.readAllBytes(original.resolve("restore-checkpoint-snapshot.json")));
        assertThat(restored.path("tables")).isEqualTo(checkpoint.path("tables"));
        var originalAfter=SubmissionJson.parse(Files.readAllBytes(original.resolve("evidence-source-after.json")));
        assertThat(restored.path("files")).isEqualTo(originalAfter.path("files"));
        var source=bind(jdbc,attempt,transfer); var supply=RecoveryClosedTestSupply.of(source);
        var keys=new RecoveryReceiptKeys(List.of(Base64.getDecoder().decode(text(transfer,"publicKey"))));
        var request=RecoveryTemporaryEvidenceTransfer.request(source,attempt,original,transfer); var before=snapshots.capture();
        var context=new Context(jdbc,attempt,source,supply,keys,request,snapshots,before);
        Files.writeString(attempt.resolve("evidence-target-before.json"),before.toPrettyString());
        RecoveryTemporaryEvidenceFaultChecks.verify(context);
        var result=context.restorer().restore(request);
        var material=readCanonical(Files.readAllBytes(request.rows().materialRoot().resolve(request.rows().independentSha256()+".json")));
        int expected=0; for(var table:RecoveryTemporaryEvidenceTable.values()) expected+=material.path("rows").path(table.table).size();
        assertThat(result.insertedRows()).isEqualTo(expected); assertThat(result.ordinaryWritesAllowed()).isFalse(); assertThat(result.localDeletionAllowed()).isFalse();
        assertThat(new RecoveryTemporaryEvidenceRows(jdbc).readOriginal(source.identity)).isEqualTo(material.path("rows"));
        var after=snapshots.capture(); conserved(before,after);
        assertThat(context.restorer().restore(request).insertedRows()).isZero(); assertThat(snapshots.capture()).isEqualTo(after);
        RecoveryTemporaryEvidenceConflictChecks.verify(context,after);
        Files.writeString(attempt.resolve("evidence-target-after.json"),after.toPrettyString()); save(context,original,transfer);
        assertThat(jdbc.queryForObject("SELECT service_mode FROM server_state WHERE id=1",String.class)).isEqualTo("RECOVERY_PENDING");
    }
    private static RecoverySourceFixture bind(JdbcTemplate jdbc,Path attempt,JsonNode transfer) throws Exception {
        var source=RecoverySourceFixture.forRestore(attempt,RecoverySourceIdentity.read(transfer.path("source")));
        var original=readCanonical(Files.readAllBytes(Path.of(text(transfer,"originalRoot")).resolve(text(transfer,"originalManifest")+".json")));
        for(String kind:RecoverySourceManifest.OBJECTS) {
            var descriptor=original.path("objects").path(kind); String hash=text(descriptor,"sha256");
            byte[] bytes=Files.readAllBytes(Path.of(text(transfer,"originalRoot")).resolve(hash+".bin")); assertThat(sha256(bytes)).isEqualTo(hash);
            Files.write(source.materials.resolve(hash+".bin"),bytes);
            ((com.fasterxml.jackson.databind.node.ObjectNode)source.manifest.path("objects")).set(kind,descriptor.deepCopy());
        }
        source.publish(); RecoveryClosedTestSupply.writers(source.control);
        new RecoveryClosedProjection(jdbc.getDataSource(),RecoveryClosedTestSupply.of(source).authority(),1).project();
        source.advance(); new RecoveryClosedProjection(jdbc.getDataSource(),RecoveryClosedTestSupply.of(source).authority(),1).project(); return source;
    }
    private static void conserved(JsonNode before,JsonNode after) {
        before.path("tables").fields().forEachRemaining(row -> {
            if(Arrays.stream(RecoveryTemporaryEvidenceTable.values()).noneMatch(table -> table.table.equals(row.getKey())))
                assertThat(after.path("tables").path(row.getKey())).as(row.getKey()).isEqualTo(row.getValue());
        });
        assertThat(after.path("files")).isEqualTo(before.path("files")); assertThat(after.path("schema")).isEqualTo(before.path("schema"));
        assertThat(after.path("flywayHistory")).isEqualTo(before.path("flywayHistory"));
    }
    private static void save(Context context,Path original,JsonNode transfer) throws Exception {
        var source=context.source(); var pins=context.supply().pins();
        var value=SubmissionJson.object().put("controlRoot",source.control.root.toString()).put("checkpoint",source.control.checkpoint.toString())
            .put("installationId",source.control.installation.toString()).put("authorityRoot",pins.root().toString()).put("authorityPin",pins.authoritySha256())
            .put("sourceRoot",source.materials.toString()).put("sourcePin",source.pin).put("originalAttempt",original.toString());
        value.set("source",transfer.path("source")); value.set("transfer",transfer);
        Files.write(context.attempt().resolve("evidence-reopen-context.json"),RecoveryControlJson.encode(value));
    }
    record Context(JdbcTemplate jdbc,Path attempt,RecoverySourceFixture source,RecoveryClosedTestSupply supply,RecoveryReceiptKeys keys,
            RecoveryTemporaryEvidenceRestore.Request request,E2aMigrationSnapshot snapshots,JsonNode before) {
        RecoveryTemporaryEvidenceRestore restorer() { return new RecoveryTemporaryEvidenceRestore(jdbc.getDataSource(),supply.authority(),keys); }
        void preserved() throws Exception { assertThat(snapshots.capture()).isEqualTo(before); }
    }
}
