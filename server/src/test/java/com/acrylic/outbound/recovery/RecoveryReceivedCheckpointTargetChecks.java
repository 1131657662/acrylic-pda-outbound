package com.acrylic.outbound.recovery;

import com.acrylic.outbound.e2averify.E2aMigrationSnapshot;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Original checkpoint plus authenticated MySQL binlog replay into a newly allocated empty development schema. */
public final class RecoveryReceivedCheckpointTargetChecks {
    public static void verify(JdbcTemplate jdbc,Path attempt,Path original) throws Exception {
        assertThat(original.toRealPath().getParent()).isEqualTo(attempt.toRealPath().getParent());
        assertThat(Files.exists(original.resolve("test-completed.marker"))).isTrue();
        var transfer=readCanonical(Files.readAllBytes(original.resolve("checkpoint-transfer.json")));
        Path dump=original.resolve("checkpoint.sql"); String pin=Files.readString(original.resolve("checkpoint.sha256")).trim();
        assertThat(sha256(Files.readAllBytes(dump))).isEqualTo(pin);
        String schema=jdbc.queryForObject("SELECT DATABASE()",String.class); require(schema.matches("acrylic_e2a_cross_20260925_[1-9][0-9]{0,3}"),"CHECKPOINT_TARGET_SCHEMA");
        var context=new Context(jdbc,attempt,original,schema,new E2aMigrationSnapshot(jdbc,attempt,attempt.resolve("storage")));
        var history=history(original,transfer);
        assertThat(RecoveryPermissionContinuity.verifyControlled(history).historicalAuthorityEstablished()).isTrue();
        mysql(context,dump,"checkpoint-import");
        triggers(context);
        checkpoint(context);
        Path replay=decode(context,history); mysql(context,replay,"checkpoint-replay");
        copyStorage(original,attempt);
        restored(context);
        assertThat(sha256(Files.readAllBytes(dump))).isEqualTo(pin);
    }

    private static void triggers(Context context) throws Exception {
        Path attempt=context.attempt(),original=context.original(); String schema=context.schema();
        String secret=Files.readAllLines(attempt.resolve("private/root-client.cnf")).stream().filter(line -> line.startsWith("password=")).findFirst().orElseThrow().substring(9);
        var admin=new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
            "jdbc:mysql://127.0.0.1:19330/"+schema+"?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC","root",secret));
        var triggers=SubmissionJson.object().putArray("triggers");
        admin.query("SELECT TRIGGER_NAME,EVENT_MANIPULATION,ACTION_STATEMENT,ACTION_TIMING,ACTION_ORDER,DEFINER,SQL_MODE,CHARACTER_SET_CLIENT,COLLATION_CONNECTION,DATABASE_COLLATION FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE() ORDER BY TRIGGER_NAME",
            rs -> {var row=triggers.addObject();for(int column=1;column<=rs.getMetaData().getColumnCount();column++)row.put(rs.getMetaData().getColumnLabel(column),rs.getString(column));});
        assertThat(triggers).isEqualTo(SubmissionJson.parse(Files.readAllBytes(original.resolve("checkpoint-triggers.json"))).path("triggers"));
        Files.writeString(attempt.resolve("checkpoint-target-triggers.json"),triggers.toPrettyString());
    }

    private static void checkpoint(Context context) throws Exception {
        var before=context.snapshots().capture(); var expectedBefore=SubmissionJson.parse(Files.readAllBytes(context.original().resolve("checkpoint-snapshot.json")));
        assertThat(before.path("tables")).isEqualTo(expectedBefore.path("tables")); schemaSame(before.path("schema"),expectedBefore.path("schema"));
        Files.writeString(context.attempt().resolve("checkpoint-target-before.json"),before.toPrettyString());
    }

    private static RecoveryPermissionContinuity.Request history(Path original,JsonNode transfer) throws Exception {
        var db=new RecoveryDeviceRegistrationCapture.Database(uuid(transfer,"mysqlServerUuid"),text(transfer,"databaseSchema"),"1");
        var id=RecoverySourceIdentity.read(transfer.path("source"));
        return new RecoveryPermissionContinuity.Request(new RecoveryPermissionBoundary.Request(db,id.operatorId(),id.deviceId()),
            Files.readAllBytes(original.resolve("evidence-permission-begin.json")),text(transfer,"beginPin"),
            Files.readAllBytes(original.resolve("evidence-permission-end.json")),text(transfer,"endPin"),
            Files.readAllBytes(original.resolve("evidence-permission-binlog.bin")),text(transfer,"binlogPin"));
    }

    private static void restored(Context context) throws Exception {
        var after=context.snapshots().capture(); var expected=SubmissionJson.parse(Files.readAllBytes(context.original().resolve("checkpoint-source-final.json")));
        assertThat(after.path("tables")).isEqualTo(expected.path("tables")); schemaSame(after.path("schema"),expected.path("schema"));
        assertThat(after.path("files")).isEqualTo(expected.path("files")); assertThat(after.path("flywayHistory")).isEqualTo(expected.path("flywayHistory"));
        Files.writeString(context.attempt().resolve("checkpoint-target-after.json"),after.toPrettyString());
    }

    private static Path decode(Context context,RecoveryPermissionContinuity.Request history) throws Exception {
        Path replay=context.attempt().resolve("checkpoint-replay.sql");
        var begin=readCanonical(history.begin()); var end=readCanonical(history.end());
        var process=new ProcessBuilder("C:/Program Files/MySQL/MySQL Server 8.0/bin/mysqlbinlog.exe","--no-defaults","--verify-binlog-checksum",
            "--start-position="+revision(begin,"binlogPosition"),"--stop-position="+revision(end,"binlogPosition"),"--database="+context.schema(),
            "--rewrite-db="+history.expected().database().schema()+"->"+context.schema(),context.original().resolve("evidence-permission-binlog.bin").toString())
            .redirectOutput(replay.toFile()).redirectError(context.attempt().resolve("checkpoint-decode.err").toFile()).start();
        assertThat(process.waitFor(30,TimeUnit.SECONDS)).isTrue(); assertThat(process.exitValue()).isZero(); return replay;
    }
    private static void schemaSame(JsonNode actual,JsonNode expected) {
        assertThat(actual.size()).isEqualTo(expected.size());
        expected.fields().forEachRemaining(entry -> assertThat(actual.path(entry.getKey()).asText()
            .replace(" CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs"," COLLATE utf8mb4_0900_as_cs"))
            .as(entry.getKey()).isEqualTo(entry.getValue().asText().replace(" CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs"," COLLATE utf8mb4_0900_as_cs")));
    }
    private static void mysql(Context context,Path input,String name) throws Exception {
        Path attempt=context.attempt(); String schema=context.schema();
        var process=new ProcessBuilder("C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe","--defaults-file="+attempt.resolve("private/root-client.cnf"),
            "--default-character-set=utf8mb4",schema).redirectInput(input.toFile()).redirectOutput(attempt.resolve(name+".out").toFile())
            .redirectError(attempt.resolve(name+".err").toFile()).start();
        assertThat(process.waitFor(30,TimeUnit.SECONDS)).isTrue(); assertThat(process.exitValue()).isZero();
    }
    private static void copyStorage(Path original,Path attempt) throws Exception {
        try(var files=Files.walk(original.resolve("storage"))) {
            for(Path file:files.toList()) {
                Path target=attempt.resolve("storage").resolve(original.resolve("storage").relativize(file));
                if(Files.isDirectory(file)) Files.createDirectories(target); else Files.copy(file,target);
            }
        }
    }
    private record Context(JdbcTemplate jdbc,Path attempt,Path original,String schema,E2aMigrationSnapshot snapshots) { }
}
