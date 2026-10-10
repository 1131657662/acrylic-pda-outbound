package com.acrylic.outbound.recovery;

import com.acrylic.outbound.e2averify.E2aMigrationSnapshot;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Actual target restoration and all-table conservation; this is preparation recovery, not body/occupancy reconstruction. */
public final class RecoveryTemporaryRestoreTargetChecks {
    public static void verify(JdbcTemplate jdbc,Path attempt,Path original) throws Exception {
        RecoveryTemporaryRestoreImport.restore(jdbc,attempt,original);
        var transfer = readCanonical(Files.readAllBytes(original.resolve("restore-transfer.json")));
        var snapshots = new E2aMigrationSnapshot(jdbc,attempt,attempt.resolve("storage"));
        var restored = snapshots.capture(); var checkpoint = SubmissionJson.parse(Files.readAllBytes(original.resolve("restore-checkpoint-snapshot.json")));
        assertThat(restored.path("tables")).isEqualTo(checkpoint.path("tables")); assertThat(restored.path("files")).isEqualTo(checkpoint.path("files"));
        Files.writeString(attempt.resolve("restore-imported-snapshot.json"),restored.toPrettyString());
        var source = bind(jdbc,attempt,transfer); var supply = RecoveryClosedTestSupply.of(source);
        var keys = new RecoveryReceiptKeys(List.of(Base64.getDecoder().decode(text(transfer,"publicKey"))));
        var request = request(source,transfer); var before = snapshots.capture();
        var context = new Context(jdbc,attempt,source,supply,keys,request,snapshots,before);
        Files.writeString(attempt.resolve("restore-pending-before.json"),before.toPrettyString());
        RecoveryTemporaryRestoreFaultChecks.verify(context);
        var result = context.restorer().restore(request);
        assertThat(result.insertedParts()).isEqualTo(5); assertThat(result.insertedAck()).isTrue();
        assertThat(result.ordinaryWritesAllowed()).isFalse(); assertThat(result.localDeletionAllowed()).isFalse();
        var after = snapshots.capture(); conserved(before,after); originals(jdbc,source,original);
        assertThat(context.restorer().restore(request)).isEqualTo(new RecoveryTemporaryPreparationRestore.Result(0,false));
        assertThat(snapshots.capture()).isEqualTo(after);
        Files.writeString(attempt.resolve("restore-pending-after.json"),after.toPrettyString());
        saveContext(context,transfer); assertThat(jdbc.queryForObject("SELECT service_mode FROM server_state WHERE id=1",String.class)).isEqualTo("RECOVERY_PENDING");
    }

    private static RecoverySourceFixture bind(JdbcTemplate jdbc,Path attempt,JsonNode transfer) throws Exception {
        var source = RecoverySourceFixture.forRestore(attempt,RecoverySourceIdentity.read(transfer.path("source")));
        var original = readCanonical(Files.readAllBytes(Path.of(text(transfer,"originalRoot")).resolve(text(transfer,"originalManifest")+".json")));
        for(String kind:RecoverySourceManifest.OBJECTS) {
            var descriptor = original.path("objects").path(kind); String hash = text(descriptor,"sha256");
            byte[] bytes = Files.readAllBytes(Path.of(text(transfer,"originalRoot")).resolve(hash+".bin"));
            assertThat(sha256(bytes)).isEqualTo(hash); Files.write(source.materials.resolve(hash+".bin"),bytes);
            ((com.fasterxml.jackson.databind.node.ObjectNode)source.manifest.path("objects")).set(kind,descriptor.deepCopy());
        }
        source.publish(); RecoveryClosedTestSupply.writers(source.control);
        new RecoveryClosedProjection(jdbc.getDataSource(),RecoveryClosedTestSupply.of(source).authority(),1).project();
        source.advance(); new RecoveryClosedProjection(jdbc.getDataSource(),RecoveryClosedTestSupply.of(source).authority(),1).project(); return source;
    }

    private static RecoveryTemporaryPreparationRestore.Request request(RecoverySourceFixture source,JsonNode transfer) {
        var database = new RecoveryDeviceRegistrationCapture.Database(uuid(transfer,"mysqlServerUuid"),text(transfer,"databaseSchema"),text(transfer,"sourceRecoveryEpoch"));
        var original = new RecoveryOriginalTemporaryPreparation.Request(Path.of(text(transfer,"originalRoot")),text(transfer,"preparationPin"),database,
            Instant.parse(text(transfer,"observedAt")));
        return new RecoveryTemporaryPreparationRestore.Request(source.materials,source.pin,original);
    }

    private static void conserved(JsonNode before,JsonNode after) {
        before.path("tables").fields().forEachRemaining(row -> {
            if(!Set.of("temporary_preparation_part","temporary_preparation_ack").contains(row.getKey()))
                assertThat(after.path("tables").path(row.getKey())).as(row.getKey()).isEqualTo(row.getValue());
        });
        assertThat(after.path("schema")).isEqualTo(before.path("schema")); assertThat(after.path("files")).isEqualTo(before.path("files"));
        assertThat(after.path("flywayHistory")).isEqualTo(before.path("flywayHistory"));
    }

    private static void originals(JdbcTemplate jdbc,RecoverySourceFixture source,Path original) throws Exception {
        var origin = SubmissionJson.parse(Files.readAllBytes(original.resolve("restore-original-origin.json")));
        byte[] id = com.acrylic.outbound.common.Ids.bytes(source.identity.qualificationId());
        for(String name:List.of("inventory","mapping","policy","rules","availability"))
            assertThat(jdbc.queryForObject("SELECT compressed FROM temporary_preparation_part WHERE authorization_id=? AND part_name=?",byte[].class,id,name))
                .containsExactly(Base64.getDecoder().decode(text(origin.path("parts"),name)));
        var ack = SubmissionJson.parse(text(origin.path("activation"),"receipt"));
        assertThat(jdbc.queryForObject("SELECT signed_ack FROM temporary_preparation_ack WHERE authorization_id=?",String.class,id)).isEqualTo(text(ack,"signedAck"));
    }

    private static void saveContext(Context context,JsonNode transfer) throws Exception {
        var source = context.source(); var pins = context.supply().pins();
        var value = SubmissionJson.object().put("controlRoot",source.control.root.toString()).put("checkpoint",source.control.checkpoint.toString())
            .put("installationId",source.control.installation.toString()).put("authorityRoot",pins.root().toString()).put("authorityPin",pins.authoritySha256())
            .put("sourceRoot",source.materials.toString()).put("sourcePin",source.pin);
        value.set("source",transfer.path("source")); value.set("transfer",transfer);
        Files.write(context.attempt().resolve("restore-reopen-context.json"),RecoveryControlJson.encode(value));
    }

    record Context(JdbcTemplate jdbc,Path attempt,RecoverySourceFixture source,RecoveryClosedTestSupply supply,RecoveryReceiptKeys keys,
            RecoveryTemporaryPreparationRestore.Request request,E2aMigrationSnapshot snapshots,JsonNode before) {
        RecoveryTemporaryPreparationRestore restorer() { return new RecoveryTemporaryPreparationRestore(jdbc.getDataSource(),supply.authority(),keys); }
        void preserved() throws Exception { assertThat(snapshots.capture()).isEqualTo(before); }
    }
}
