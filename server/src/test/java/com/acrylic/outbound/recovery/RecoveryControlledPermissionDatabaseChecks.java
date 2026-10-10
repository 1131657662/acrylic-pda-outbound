package com.acrylic.outbound.recovery;

import com.acrylic.outbound.e2averify.E2aMigrationSnapshot;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Actual source enrollment. Root is locked and its CLI exits before any original boundary is collected. */
public final class RecoveryControlledPermissionDatabaseChecks {
    public static void verify(JdbcTemplate jdbc, Path attempt, Map<String,String> seed) throws Exception {
        seedPermissions(jdbc, seed);
        var database = new RecoveryDeviceRegistrationCapture.Database(UUID.fromString(jdbc.queryForObject("SELECT @@server_uuid", String.class)),
            jdbc.queryForObject("SELECT DATABASE()", String.class), "1");
        var request = new RecoveryPermissionBoundary.Request(database, UUID.fromString(seed.get("operatorId")), UUID.fromString(seed.get("deviceId")));
        var capture = new RecoveryPermissionBoundary(jdbc.getDataSource(), attempt);
        var context = new Context(jdbc, attempt, request, capture); enroll(context);
        var snapshots = new E2aMigrationSnapshot(jdbc, attempt, attempt.resolve("storage"));
        var afterWrite = positive(context, seed, snapshots);
        privileges(jdbc); assertThat(snapshots.capture()).isEqualTo(afterWrite);
        revocation(context); assertThat(snapshots.capture()).isEqualTo(afterWrite);
        Files.writeString(attempt.resolve("controlled-after.json"), afterWrite.toPrettyString(), StandardOpenOption.CREATE_NEW);
        Files.writeString(attempt.resolve("controlled-checks.txt"),
            "PASS actual original constrained DML writer, all other accounts locked/no role inheritance, same FTWRL writer+permission boundaries and ROW/XID/prefix; cannot disable logging/change format/grant/global flags/create DDL/unlock root. Ordinary admission remains closed; synthetic development original source, not actual business restoration\n");
    }

    private static void enroll(Context context) throws Exception {
        var jdbc = context.jdbc(); var attempt = context.attempt();
        try (var root = rootConnection(attempt)) {
            constrain(jdbc, attempt);
            try (var statement = root.createStatement()) {
                statement.execute("SET SESSION sql_log_bin=0"); statement.execute("SET SESSION sql_log_bin=1");
            }
            assertThatThrownBy(() -> context.capture().captureControlled(context.request())).hasMessage("RECOVERY_PERMISSION_WRITER_SESSIONS");
        }
        Files.writeString(attempt.resolve("locked-root-session-refused.txt"), "PASS locked root retains old session privileges; capture refuses until that original connection is closed\n");
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode positive(Context context, Map<String,String> seed,
            E2aMigrationSnapshot snapshots) throws Exception {
        var jdbc = context.jdbc(); var attempt = context.attempt(); var capture = context.capture(); var request = context.request();
        var before = snapshots.capture();
        Files.writeString(attempt.resolve("controlled-before.json"), before.toPrettyString(), StandardOpenOption.CREATE_NEW);
        Files.writeString(attempt.resolve("controlled-live-threads.json"), new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
            jdbc.queryForList("SELECT NAME,TYPE,PROCESSLIST_ID,PROCESSLIST_USER,PROCESSLIST_COMMAND FROM performance_schema.threads WHERE TYPE='FOREGROUND'")),
            StandardOpenOption.CREATE_NEW);
        var begin = capture.captureControlled(request);
        jdbc.update("UPDATE submission SET status='ABORTED',receipt_text='controlled-original-window' WHERE id=UUID_TO_BIN(?)", seed.get("mappedSubmissionId"));
        var afterWrite = snapshots.capture(); var end = capture.captureControlled(request);
        var input = materials(attempt, request, new Window("positive", begin, end)); var proof = RecoveryPermissionContinuity.verifyControlled(input);
        assertThat(proof.historicalAuthorityEstablished()).isTrue(); assertThat(proof.ordinaryWritesAllowed()).isFalse();
        assertThat(proof.committedTransactions()).isEqualTo(1); assertThat(proof.localDeletionAllowed()).isFalse();
        return afterWrite;
    }

    private static java.sql.Connection rootConnection(Path attempt) throws Exception {
        String password = Files.readAllLines(attempt.resolve("private/root-client.cnf")).stream()
            .filter(line -> line.startsWith("password=")).findFirst().orElseThrow().substring(9);
        return java.sql.DriverManager.getConnection("jdbc:mysql://127.0.0.1:19330/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
            "root", password);
    }

    private static void revocation(Context context) throws Exception {
        var jdbc = context.jdbc(); var capture = context.capture(); var request = context.request();
        var begin = capture.captureControlled(request);
        assertThat(jdbc.update("UPDATE permission SET name=CONCAT(name,'x') WHERE code='pda:scan'")).isEqualTo(1);
        assertThat(jdbc.update("UPDATE permission SET name=LEFT(name,CHAR_LENGTH(name)-1) WHERE code='pda:scan'")).isEqualTo(1);
        var end = capture.captureControlled(request);
        var input = materials(context.attempt(), request, new Window("revoked", begin, end));
        assertThatThrownBy(() -> RecoveryPermissionContinuity.verifyControlled(input)).hasMessage("RECOVERY_BINLOG_AUTHORITY_OR_UNKNOWN_TABLE");
    }

    private static void seedPermissions(JdbcTemplate jdbc, Map<String,String> seed) {
        jdbc.update("INSERT INTO user_role(user_id,role_id) SELECT UUID_TO_BIN(?),id FROM role WHERE code IN ('WAREHOUSE_ADMIN','TEMPORARY_ORDER_CREATOR')", seed.get("operatorId"));
        jdbc.update("UPDATE device SET temporary_order_enabled=1,row_version=row_version+1 WHERE id=UUID_TO_BIN(?)", seed.get("deviceId"));
    }

    private static void constrain(JdbcTemplate jdbc, Path attempt) throws Exception {
        String schema = jdbc.queryForObject("SELECT DATABASE()", String.class), account = jdbc.queryForObject("SELECT CURRENT_USER()", String.class);
        require(schema.matches("acrylic_e2a_cross_20260925_[1-9][0-9]{0,3}") && account.equals(schema.replace("acrylic_", "") + "@127.0.0.1"),
            "CONTROLLED_TEST_IDENTITY"); String user = account.substring(0, account.indexOf('@')), target = "'" + user + "'@'127.0.0.1'";
        captureSchema(attempt, schema);
        String sql = "REVOKE ALL PRIVILEGES ON " + schema + ".* FROM " + target + ";\n"
            + "GRANT SELECT,INSERT,UPDATE,DELETE ON " + schema + ".* TO " + target + ";\n"
            + "GRANT SELECT(User,Host,account_locked) ON mysql.user TO " + target + ";\n"
            + "GRANT SELECT ON mysql.role_edges TO " + target + ";\nALTER USER 'root'@'localhost' ACCOUNT LOCK;\n";
        Path input = attempt.resolve("private/constrain-original.sql"); Files.writeString(input, sql, StandardOpenOption.CREATE_NEW);
        var builder = new ProcessBuilder("C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe",
            "--defaults-file=" + attempt.resolve("private/root-client.cnf"), "--protocol=TCP", "--host=127.0.0.1", "--port=19330");
        builder.environment().remove("MYSQL_PWD"); builder.environment().put("MYSQL_TEST_LOGIN_FILE", attempt.resolve("private/unused-login").toString());
        builder.redirectInput(input.toFile()).redirectOutput(attempt.resolve("private/constrain.out").toFile())
            .redirectError(attempt.resolve("private/constrain.err").toFile());
        var process = builder.start(); assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue(); assertThat(process.exitValue()).isZero();
        Files.writeString(attempt.resolve("controlled-enrollment-exit.json"), "{\"exit\":0,\"rootProcessExited\":true}\n", StandardOpenOption.CREATE_NEW);
    }

    private static void captureSchema(Path attempt, String schema) throws Exception {
        var builder = new ProcessBuilder("C:/Program Files/MySQL/MySQL Server 8.0/bin/mysqldump.exe",
            "--defaults-file=" + attempt.resolve("private/root-client.cnf"), "--protocol=TCP", "--host=127.0.0.1", "--port=19330",
            "--no-data", "--no-tablespaces", "--set-gtid-purged=OFF", "--routines", "--events", "--triggers", schema);
        builder.environment().remove("MYSQL_PWD"); builder.environment().put("MYSQL_TEST_LOGIN_FILE", attempt.resolve("private/unused-login").toString());
        builder.redirectOutput(attempt.resolve("controlled-original-schema.sql").toFile())
            .redirectError(attempt.resolve("private/schema.err").toFile());
        var process = builder.start(); assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue(); assertThat(process.exitValue()).isZero();
        assertThat(Files.size(attempt.resolve("controlled-original-schema.sql"))).isPositive();
    }

    private static RecoveryPermissionContinuity.Request materials(Path attempt, RecoveryPermissionBoundary.Request expected, Window window) throws Exception {
        var begin = window.begin(); var end = window.end(); var metadata = readCanonical(end.originalBytes());
        int position = Math.toIntExact(revision(metadata, "binlogPosition")); byte[] log;
        try (var input = Files.newInputStream(attempt.resolve(text(metadata, "binlogFile")))) { log = input.readNBytes(position); }
        require(log.length == position, "CONTROLLED_TEST_LOG"); Path output = Files.createDirectory(attempt.resolve("controlled-" + window.name()));
        Files.write(output.resolve("begin.json"), begin.originalBytes()); Files.write(output.resolve("end.json"), end.originalBytes());
        Files.write(output.resolve("binlog-prefix.bin"), log);
        var pins = SubmissionJson.object().put("beginSha256", begin.originalSha256()).put("endSha256", end.originalSha256()).put("binlogSha256", sha256(log));
        Files.write(output.resolve("pins.json"), RecoveryControlJson.encode(pins));
        return new RecoveryPermissionContinuity.Request(expected, begin.originalBytes(), begin.originalSha256(), end.originalBytes(),
            end.originalSha256(), log, sha256(log));
    }

    private static void privileges(JdbcTemplate jdbc) throws Exception {
        for (String sql : List.of("SET SESSION sql_log_bin=0", "SET SESSION binlog_format='STATEMENT'", "SET GLOBAL log_bin_trust_function_creators=OFF",
                "SET GLOBAL binlog_format='STATEMENT'", "CREATE TABLE impossible(id INT)", "ALTER USER 'root'@'localhost' ACCOUNT UNLOCK",
                "GRANT SELECT ON mysql.* TO CURRENT_USER", "UPDATE mysql.user SET account_locked='N' WHERE User='root'")) {
            try (var connection = jdbc.getDataSource().getConnection(); var statement = connection.createStatement()) {
                assertThatThrownBy(() -> statement.execute(sql)).isInstanceOf(java.sql.SQLException.class);
            }
        }
    }
    private record Window(String name, RecoveryPermissionBoundary.Observation begin, RecoveryPermissionBoundary.Observation end) { }
    private record Context(JdbcTemplate jdbc, Path attempt, RecoveryPermissionBoundary.Request request, RecoveryPermissionBoundary capture) { }
}
