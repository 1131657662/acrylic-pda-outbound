package com.acrylic.outbound.recovery;

import com.acrylic.outbound.e2averify.E2aMigrationSnapshot;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** All real source inputs and failed probes are retained. Synthetic fixture timestamps never stand in for these boundaries. */
public final class RecoveryPermissionDatabaseChecks {
    public static void verify(JdbcTemplate jdbc, Path attempt, Map<String,String> seed) throws Exception {
        seedPermissions(jdbc, seed);
        var database = new RecoveryDeviceRegistrationCapture.Database(UUID.fromString(jdbc.queryForObject("SELECT @@server_uuid", String.class)),
            jdbc.queryForObject("SELECT DATABASE()", String.class), "1");
        var request = new RecoveryPermissionBoundary.Request(database, UUID.fromString(seed.get("operatorId")), UUID.fromString(seed.get("deviceId")));
        var capture = new RecoveryPermissionBoundary(jdbc.getDataSource(), attempt);
        var snapshots = new E2aMigrationSnapshot(jdbc, attempt, attempt.resolve("storage")); var before = snapshots.capture();
        Files.writeString(attempt.resolve("permission-before.json"), before.toPrettyString(), StandardOpenOption.CREATE_NEW);
        var first = capture.capture(request); assertThat(snapshots.capture()).isEqualTo(before);
        assertThat(readCanonical(first.originalBytes()).at("/effective/permissions")).anySatisfy(permission ->
            assertThat(permission.asText()).isEqualTo("temporary_order:create"));
        jdbc.update("UPDATE submission SET status='ABORTED',receipt_text='permission-window-probe' WHERE id=UUID_TO_BIN(?)", seed.get("mappedSubmissionId"));
        var afterWrite = snapshots.capture(); var last = capture.capture(request); assertThat(snapshots.capture()).isEqualTo(afterWrite);
        var context = new Context(jdbc, attempt, request, capture);
        var proof = verify(context, new Window("positive", first, last));
        assertThat(proof.committedTransactions()).isEqualTo(1); assertThat(proof.rowEvents()).isEqualTo(1);
        assertThat(proof.historicalAuthorityEstablished()).isFalse(); assertThat(proof.localDeletionAllowed()).isFalse();
        assertThat(verify(context, new Window("empty", last, capture.capture(request))).committedTransactions()).isZero();
        probes(context, seed); captureRefusals(context);
        assertThat(snapshots.capture()).isEqualTo(afterWrite);
        Files.writeString(attempt.resolve("permission-after.json"), snapshots.capture().toPrettyString(), StandardOpenOption.CREATE_NEW);
        Files.writeString(attempt.resolve("permission-checks.txt"),
            "PASS real begin/end UTC microsecond and stable original DB identity, business ROW/XID interval, empty interval, every authority table changed+restored refusal, failed capture releases global lock, nested transaction rejected, application credential cannot disable session binlog; logged facts only\n");
    }

    private static void seedPermissions(JdbcTemplate jdbc, Map<String,String> seed) {
        jdbc.update("INSERT INTO user_role(user_id,role_id) SELECT UUID_TO_BIN(?),id FROM role WHERE code IN ('WAREHOUSE_ADMIN','TEMPORARY_ORDER_CREATOR')", seed.get("operatorId"));
        jdbc.update("UPDATE device SET temporary_order_enabled=1,row_version=row_version+1 WHERE id=UUID_TO_BIN(?)", seed.get("deviceId"));
    }

    private static RecoveryPermissionContinuity.Proof verify(Context c, Window window) throws Exception {
        var request = materials(c, window);
        return RecoveryPermissionContinuity.verify(request);
    }

    private static RecoveryPermissionContinuity.Request materials(Context c, Window window) throws Exception {
        var begin = window.begin(); var end = window.end();
        var value = readCanonical(end.originalBytes()); int finish = Math.toIntExact(revision(value, "binlogPosition"));
        Path path = c.attempt().resolve(text(value, "binlogFile"));
        byte[] bytes;
        try (var input = Files.newInputStream(path)) { bytes = input.readNBytes(finish); }
        require(bytes.length == finish, "RECOVERY_BINLOG_TRUNCATED");
        Path output = Files.createDirectory(c.attempt().resolve("permission-" + window.name()));
        Files.write(output.resolve("begin.json"), begin.originalBytes()); Files.write(output.resolve("end.json"), end.originalBytes());
        Files.write(output.resolve("binlog-prefix.bin"), bytes);
        var pin = SubmissionJson.object().put("beginSha256", begin.originalSha256()).put("endSha256", end.originalSha256()).put("binlogSha256", sha256(bytes));
        Files.write(output.resolve("pins.json"), RecoveryControlJson.encode(pin));
        return new RecoveryPermissionContinuity.Request(c.request(), begin.originalBytes(), begin.originalSha256(), end.originalBytes(),
            end.originalSha256(), bytes, sha256(bytes));
    }

    private static void probes(Context c, Map<String,String> seed) throws Exception {
        var jdbc = c.jdbc(); String user = seed.get("operatorId"), device = seed.get("deviceId"), warehouse = seed.get("warehouseId");
        var probes = List.of(
            new Probe("user", "UPDATE app_user SET auth_version=auth_version+1 WHERE id=UUID_TO_BIN(?)", "UPDATE app_user SET auth_version=auth_version-1 WHERE id=UUID_TO_BIN(?)", user),
            new Probe("device", "UPDATE device SET temporary_order_enabled=NOT temporary_order_enabled WHERE id=UUID_TO_BIN(?)", "UPDATE device SET temporary_order_enabled=NOT temporary_order_enabled WHERE id=UUID_TO_BIN(?)", device),
            new Probe("warehouse", "UPDATE warehouse SET status='DISABLED' WHERE id=UUID_TO_BIN(?)", "UPDATE warehouse SET status='ACTIVE' WHERE id=UUID_TO_BIN(?)", warehouse),
            new Probe("role", "UPDATE role SET name=CONCAT(name,'x') WHERE id=(SELECT role_id FROM user_role WHERE user_id=UUID_TO_BIN(?) LIMIT 1)", "UPDATE role SET name=LEFT(name,CHAR_LENGTH(name)-1) WHERE id=(SELECT role_id FROM user_role WHERE user_id=UUID_TO_BIN(?) LIMIT 1)", user),
            new Probe("permission", "UPDATE permission SET name=CONCAT(name,'x') WHERE code=?", "UPDATE permission SET name=LEFT(name,CHAR_LENGTH(name)-1) WHERE code=?", "pda:scan"));
        for (var probe : probes) reject(c, probe.name(), () -> {
            assertThat(jdbc.update(probe.apply(), probe.id())).isEqualTo(1);
            assertThat(jdbc.update(probe.undo(), probe.id())).isEqualTo(1);
        });
        relations(c); reject(c, "state", () -> { jdbc.update("UPDATE server_state SET recovery_epoch=2 WHERE id=1"); jdbc.update("UPDATE server_state SET recovery_epoch=1 WHERE id=1"); });
    }

    private static void relations(Context c) throws Exception {
        for (String table : List.of("user_role", "role_permission", "user_warehouse")) {
            String a = table.equals("role_permission") ? "role_id" : "user_id";
            String b = table.equals("user_warehouse") ? "warehouse_id" : table.equals("user_role") ? "role_id" : "permission_id";
            var row = c.jdbc().queryForMap("SELECT BIN_TO_UUID(" + a + ") a,BIN_TO_UUID(" + b + ") b FROM " + table + " LIMIT 1");
            reject(c, table, () -> {
                assertThat(c.jdbc().update("DELETE FROM " + table + " WHERE " + a + "=UUID_TO_BIN(?) AND " + b + "=UUID_TO_BIN(?)", row.get("a"), row.get("b"))).isEqualTo(1);
                assertThat(c.jdbc().update("INSERT INTO " + table + "(" + a + "," + b + ") VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?))", row.get("a"), row.get("b"))).isEqualTo(1);
            });
        }
    }

    private static void reject(Context c, String name, Runnable changes) throws Exception {
        var begin = c.capture().capture(c.request()); changes.run(); var end = c.capture().capture(c.request());
        assertThat(readCanonical(begin.originalBytes()).path("effective")).isEqualTo(readCanonical(end.originalBytes()).path("effective"));
        var request = materials(c, new Window(name, begin, end));
        assertThatThrownBy(() -> RecoveryPermissionContinuity.verify(request)).hasMessage("RECOVERY_BINLOG_AUTHORITY_OR_UNKNOWN_TABLE");
    }

    private static void captureRefusals(Context c) {
        var database = c.request().database();
        var wrong = new RecoveryPermissionBoundary.Request(new RecoveryDeviceRegistrationCapture.Database(UUID.randomUUID(), database.schema(), "1"),
            c.request().operatorId(), c.request().deviceId());
        assertThatThrownBy(() -> c.capture().capture(wrong)).hasMessage("RECOVERY_PERMISSION_DATABASE");
        assertThatThrownBy(() -> new RecoveryPermissionBoundary(c.jdbc().getDataSource(), c.attempt().resolve("storage")).capture(c.request()))
            .hasMessage("RECOVERY_PERMISSION_LOG_ROOT");
        c.jdbc().execute("SET SESSION lock_wait_timeout=2");
        c.jdbc().update("UPDATE server_state SET recovery_epoch=2 WHERE id=1"); c.jdbc().update("UPDATE server_state SET recovery_epoch=1 WHERE id=1");
        new TransactionTemplate(new DataSourceTransactionManager(c.jdbc().getDataSource())).execute(status -> {
            assertThatThrownBy(() -> c.capture().capture(c.request())).hasMessage("RECOVERY_PERMISSION_TRANSACTION"); return null;
        });
        try (var connection = c.jdbc().getDataSource().getConnection(); var statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.execute("SET SESSION sql_log_bin=0")).isInstanceOf(java.sql.SQLException.class);
        } catch (java.sql.SQLException unexpected) { throw new AssertionError(unexpected); }
    }

    private record Window(String name, RecoveryPermissionBoundary.Observation begin, RecoveryPermissionBoundary.Observation end) { }
    private record Probe(String name, String apply, String undo, String id) { }
    private record Context(JdbcTemplate jdbc, Path attempt, RecoveryPermissionBoundary.Request request, RecoveryPermissionBoundary capture) { }
}
