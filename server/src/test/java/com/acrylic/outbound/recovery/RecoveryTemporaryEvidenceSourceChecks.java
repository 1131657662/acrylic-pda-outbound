package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.e2averify.E2aMigrationSnapshot;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Actual source checkpoint before four-table intake, then immutable native capture under one actual controlled window. */
public final class RecoveryTemporaryEvidenceSourceChecks {
    public static void capture(JdbcTemplate jdbc,Path attempt,Map<String,String> seed) throws Exception {
        var fixture=new RecoveryTemporaryEvidenceDatabaseSeed(jdbc,seed);
        RecoveryTemporaryEvidenceSourceEnrollment.enroll(jdbc,attempt,fixture);
        var database=new RecoveryDeviceRegistrationCapture.Database(UUID.fromString(jdbc.queryForObject("SELECT @@server_uuid",String.class)),
            jdbc.queryForObject("SELECT DATABASE()",String.class),"1"); var id=fixture.initial.expected();
        var registration=new RecoveryDeviceRegistrationCapture(jdbc.getDataSource()).capture(
            new RecoveryDeviceRegistrationCapture.Request(database,id.warehouseId(),id.deviceId()));
        Thread.sleep(2100);
        fixture.atTime(jdbc,jdbc.queryForObject("SELECT UTC_TIMESTAMP(3)",(rs,n) -> rs.getTimestamp(1).toInstant()));
        var expected=new RecoveryPermissionBoundary.Request(database,id.operatorId(),id.deviceId());
        var boundaries=new RecoveryPermissionBoundary(jdbc.getDataSource(),attempt); var begin=boundaries.captureControlled(expected);
        fixture.savedAt(jdbc.queryForObject("SELECT UTC_TIMESTAMP(3)",(rs,n) -> rs.getTimestamp(1).toInstant()));
        var original=new RecoveryUnsubmittedOccupancyFixture(fixture.evidence,UUID.randomUUID(),database);
        jdbc.update("UPDATE submission SET status='ABORTED',receipt_text=? WHERE id=?",new String(original.initial.source().original("result"),
            java.nio.charset.StandardCharsets.UTF_8),Ids.bytes(id.submissionId()));
        var snapshots=new E2aMigrationSnapshot(jdbc,attempt,attempt.resolve("storage")); var checkpoint=snapshots.capture();
        Files.writeString(attempt.resolve("restore-checkpoint-snapshot.json"),checkpoint.toPrettyString()); dump(jdbc,attempt);
        var rows=new RecoveryTemporaryEvidenceRowsFixture(original,attempt.resolve("storage"),Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        for(var table:RecoveryTemporaryEvidenceTable.values()) for(var row:rows.rows.path(table.table))
            jdbc.update(table.insert(),RecoveryTemporaryEvidenceColumns.values(row,table,table.columns));
        var end=boundaries.captureControlled(expected); var permission=RecoveryTemporaryEvidenceSourceEnrollment.retain(attempt,expected,begin,end);
        var proof=RecoveryPermissionContinuity.verifyControlled(permission); window(original,proof); original.nullHashTerminal();
        retain(jdbc,attempt,original,fixture,registration,permission,snapshots);
    }
    private static void window(RecoveryUnsubmittedOccupancyFixture original,RecoveryPermissionContinuity.Proof proof) throws Exception {
        var row=original.initial.state.permission.putArray("windows").addObject().put("from",proof.from().toString()).put("through",proof.through().toString())
            .put("userId",proof.identity().operatorId().toString()).put("deviceId",proof.identity().deviceId().toString());
        row.setAll((ObjectNode)proof.effective()); original.initial.state.object("permission",original.initial.state.permission);
    }
    private static void retain(JdbcTemplate jdbc,Path attempt,RecoveryUnsubmittedOccupancyFixture original,RecoveryTemporaryEvidenceDatabaseSeed fixture,
            RecoveryDeviceRegistrationCapture.Observation registration,RecoveryPermissionContinuity.Request permission,E2aMigrationSnapshot snapshots) throws Exception {
        var source=original.initial.source(); var root=original.initial.state.authority.source.materials; var database=original.database;
        var preparation=new RecoveryTemporaryPreparationCapture(jdbc.getDataSource()).capture(
            new RecoveryTemporaryPreparationCapture.Request(database,source.identity(),fixture.activation.command.toString()));
        var occupancy=new RecoveryUnsubmittedOccupancyCapture(jdbc.getDataSource()).capture(new RecoveryUnsubmittedOccupancyCapture.Request(
            database,source.identity(),source.original("payload"),original.evidence.request()));
        var before=snapshots.capture();
        var rows=new RecoveryTemporaryEvidenceCapture(jdbc.getDataSource()).capture(new RecoveryTemporaryEvidenceCapture.Request(database,source,occupancy,
            original.evidence.request(),original.initial.keys(),attempt.resolve("storage")));
        assertThat(snapshots.capture()).isEqualTo(before);
        for(byte[] bytes:List.of(registration.originalBytes(),preparation.originalMetadata(),occupancy.originalBytes(),rows.originalBytes()))
            Files.write(root.resolve(sha256(bytes)+".json"),bytes);
        var transfer=SubmissionJson.object().put("originalRoot",root.toString()).put("originalManifest",original.initial.state.authority.source.pin)
            .put("preparationPin",preparation.independentSha256()).put("preparationAt",preparation.observedAt().toString())
            .put("occupancyPin",occupancy.originalSha256()).put("occupancyAt",occupancy.observedAt().toString())
            .put("rowsPin",rows.independentSha256()).put("rowsAt",rows.observedAt().toString()).put("registrationPin",registration.originalSha256())
            .put("registrationAt",registration.observedAt().toString()).put("databaseSchema",database.schema()).put("mysqlServerUuid",database.mysqlServerUuid().toString())
            .put("sourceRecoveryEpoch","1").put("publicKey",Base64.getEncoder().encodeToString(original.initial.state.authority.key.pair.getPublic().getEncoded()));
        transfer.set("source",RecoveryOriginalOccupancyFacts.source(source.identity()));
        transfer.set("evidence",RecoveryTemporaryEvidenceTransfer.evidence(original.evidence.request()));
        transfer.put("beginPin",permission.beginSha256()).put("endPin",permission.endSha256()).put("binlogPin",permission.binlogSha256());
        Files.write(attempt.resolve("evidence-transfer.json"),RecoveryControlJson.encode(transfer));
        Files.writeString(attempt.resolve("evidence-source-after.json"),snapshots.capture().toPrettyString());
    }
    private static void dump(JdbcTemplate jdbc,Path attempt) throws Exception {
        var process=new ProcessBuilder("C:/Program Files/MySQL/MySQL Server 8.0/bin/mysqldump.exe","--defaults-file="+attempt.resolve("client.cnf"),
            "--single-transaction","--no-create-info","--no-tablespaces","--set-gtid-purged=OFF","--skip-routines","--skip-events","--skip-triggers",
            jdbc.queryForObject("SELECT DATABASE()",String.class)).redirectOutput(attempt.resolve("evidence-checkpoint-data.sql").toFile())
            .redirectError(attempt.resolve("restore-checkpoint-dump.err").toFile()).start();
        assertThat(process.waitFor(30,TimeUnit.SECONDS)).isTrue(); assertThat(process.exitValue()).isZero();
        try(var output=Files.newOutputStream(attempt.resolve("restore-checkpoint.sql"),StandardOpenOption.CREATE_NEW)) {
            Files.copy(attempt.resolve("evidence-original-schema.sql"),output); Files.copy(attempt.resolve("evidence-checkpoint-data.sql"),output);
        }
        Files.writeString(attempt.resolve("restore-checkpoint.sha256"),sha256(Files.readAllBytes(attempt.resolve("restore-checkpoint.sql"))));
    }
}
