package com.acrylic.outbound.recovery;

import com.acrylic.outbound.e2averify.E2aMigrationSnapshot;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Captures a real dump while native authorization exists and parts/ACK do not. */
public final class RecoveryTemporaryRestoreSourceChecks {
    public static void capture(JdbcTemplate jdbc, Path attempt, Map<String,String> seed) throws Exception {
        var f = RecoveryTemporaryRestoreSeed.authorization(jdbc,seed); var source = f.baseline.prep.state.authority.source;
        var snapshots = new E2aMigrationSnapshot(jdbc,attempt,attempt.resolve("storage")); var before = snapshots.capture();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM temporary_preparation_part",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM temporary_preparation_ack",Integer.class)).isZero();
        Files.writeString(attempt.resolve("restore-checkpoint-snapshot.json"),before.toPrettyString()); dump(jdbc,attempt);
        RecoveryTemporaryRestoreSeed.complete(jdbc,f);
        var database = new RecoveryDeviceRegistrationCapture.Database(UUID.fromString(jdbc.queryForObject("SELECT @@server_uuid",String.class)),
            jdbc.queryForObject("SELECT DATABASE()",String.class),"1");
        var observation = new RecoveryTemporaryPreparationCapture(jdbc.getDataSource())
            .capture(new RecoveryTemporaryPreparationCapture.Request(database,source.identity,f.command.toString()));
        assertThat(observation.originalOrigin()).containsExactly(source.read().original("origin"));
        Files.write(source.materials.resolve(observation.independentSha256()+".json"),observation.originalMetadata());
        var transfer = SubmissionJson.object().put("originalRoot",source.materials.toString()).put("originalManifest",source.pin)
            .put("preparationPin",observation.independentSha256()).put("observedAt",observation.observedAt().toString())
            .put("databaseSchema",database.schema()).put("mysqlServerUuid",database.mysqlServerUuid().toString())
            .put("sourceRecoveryEpoch","1").put("publicKey",Base64.getEncoder().encodeToString(f.baseline.prep.state.authority.key.pair.getPublic().getEncoded()));
        transfer.set("source",RecoveryOriginalOccupancyFacts.source(source.identity));
        Files.write(attempt.resolve("restore-transfer.json"),RecoveryControlJson.encode(transfer));
        Files.write(attempt.resolve("restore-original-origin.json"),observation.originalOrigin());
        Files.writeString(attempt.resolve("restore-complete-snapshot.json"),snapshots.capture().toPrettyString());
    }

    private static void dump(JdbcTemplate jdbc,Path attempt) throws Exception {
        var builder = new ProcessBuilder("C:/Program Files/MySQL/MySQL Server 8.0/bin/mysqldump.exe",
            "--defaults-file="+attempt.resolve("client.cnf"),"--single-transaction","--no-tablespaces","--set-gtid-purged=OFF",
            "--routines","--events","--triggers",jdbc.queryForObject("SELECT DATABASE()",String.class));
        builder.environment().remove("MYSQL_PWD"); builder.environment().put("MYSQL_TEST_LOGIN_FILE",attempt.resolve("private/unused-login").toString());
        builder.redirectOutput(attempt.resolve("restore-checkpoint.sql").toFile()).redirectError(attempt.resolve("restore-checkpoint-dump.err").toFile());
        var process = builder.start(); assertThat(process.waitFor(30,TimeUnit.SECONDS)).isTrue(); assertThat(process.exitValue()).isZero();
        assertThat(Files.size(attempt.resolve("restore-checkpoint.sql"))).isPositive();
        Files.writeString(attempt.resolve("restore-checkpoint.sha256"),sha256(Files.readAllBytes(attempt.resolve("restore-checkpoint.sql"))));
    }
}
