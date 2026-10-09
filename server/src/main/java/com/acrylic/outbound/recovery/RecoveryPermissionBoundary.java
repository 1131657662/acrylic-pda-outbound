package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Explicit original-instance operational read. The global lock is never an application or recovery bean. */
public final class RecoveryPermissionBoundary {
    private final DataSource source;
    private final RecoveryPermissionLogPrefix prefixes;
    public RecoveryPermissionBoundary(DataSource source, java.nio.file.Path originalBinlogRoot) {
        this.source = Objects.requireNonNull(source); prefixes = new RecoveryPermissionLogPrefix(originalBinlogRoot);
    }

    public Observation capture(Request request) {
        require(request != null && !TransactionSynchronizationManager.isActualTransactionActive(), "RECOVERY_PERMISSION_TRANSACTION");
        try (Connection connection = source.getConnection()) {
            require(connection.getAutoCommit(), "RECOVERY_PERMISSION_TRANSACTION");
            var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            jdbc.setQueryTimeout(5);
            jdbc.execute("SET SESSION lock_wait_timeout=5");
            jdbc.execute("FLUSH TABLES WITH READ LOCK");
            try { return observe(jdbc, request); }
            finally { jdbc.execute("UNLOCK TABLES"); }
        } catch (SQLException unavailable) { throw new RecoveryControlException("RECOVERY_PERMISSION_DATABASE_UNAVAILABLE", unavailable); }
    }

    private Observation observe(JdbcTemplate jdbc, Request request) {
        var state = state(jdbc, request.database());
        state.put("userId", request.operatorId().toString()).put("deviceId", request.deviceId().toString());
        state.set("effective", effective(jdbc, request));
        var positions = jdbc.query("SHOW MASTER STATUS", (rs, n) -> new Position(rs.getString(1), rs.getLong(2)));
        require(positions.size() == 1, "RECOVERY_PERMISSION_BINLOG_DISABLED");
        var position = positions.getFirst();
        state.put("binlogFile", position.file()).put("binlogPosition", Long.toString(position.offset()));
        String basename = jdbc.queryForObject("SELECT @@global.log_bin_basename", String.class);
        state.put("binlogPrefixSha256", prefixes.capture(new RecoveryPermissionLogPrefix.Request(basename, position.file(), position.offset())));
        byte[] bytes = RecoveryControlJson.encode(state);
        return new Observation(bytes, sha256(bytes));
    }

    private ObjectNode state(JdbcTemplate jdbc, RecoveryDeviceRegistrationCapture.Database database) {
        var rows = jdbc.query("""
            SELECT @@server_uuid,DATABASE(),recovery_epoch,service_mode,UTC_TIMESTAMP(6),@@global.binlog_format,
             @@global.binlog_checksum,@@global.log_bin,@@session.sql_log_bin,@@global.binlog_row_image,@@version
            FROM server_state WHERE id=1
            """, (rs, n) -> {
                require("NORMAL".equals(rs.getString(4)), "RECOVERY_PERMISSION_MODE");
                require("ROW".equals(rs.getString(6)) && "CRC32".equals(rs.getString(7)) && rs.getBoolean(8)
                    && rs.getBoolean(9) && "FULL".equals(rs.getString(10)) && rs.getString(11).startsWith("8.0."),
                    "RECOVERY_PERMISSION_BINLOG_PROFILE");
                return SubmissionJson.object().put("formatVersion", 1).put("observationKind", "ORIGINAL_PERMISSION_BOUNDARY")
                    .put("mysqlServerUuid", rs.getString(1)).put("databaseSchema", rs.getString(2))
                    .put("sourceRecoveryEpoch", rs.getString(3)).put("observedAt", rs.getTimestamp(5).toInstant().toString());
            });
        require(rows.size() == 1, "RECOVERY_PERMISSION_STATE"); var value = rows.getFirst();
        require(uuid(value, "mysqlServerUuid").equals(database.mysqlServerUuid()) && text(value, "databaseSchema").equals(database.schema())
            && text(value, "sourceRecoveryEpoch").equals(database.sourceRecoveryEpoch()), "RECOVERY_PERMISSION_DATABASE");
        return value;
    }

    private ObjectNode effective(JdbcTemplate jdbc, Request request) {
        var users = jdbc.query("SELECT status,auth_version FROM app_user WHERE id=?", (rs, n) ->
            SubmissionJson.object().put("userActive", "ACTIVE".equals(rs.getString(1))).put("userAuthVersion", rs.getString(2)), Ids.bytes(request.operatorId()));
        require(users.size() == 1, "RECOVERY_PERMISSION_USER"); var value = users.getFirst();
        var devices = jdbc.query("SELECT BIN_TO_UUID(warehouse_id),status,row_version,temporary_order_enabled FROM device WHERE id=?",
            (rs, n) -> SubmissionJson.object().put("warehouseId", rs.getString(1)).put("deviceStatus", rs.getString(2))
                .put("deviceVersion", rs.getString(3)).put("deviceTemporaryOrderEnabled", rs.getBoolean(4)), Ids.bytes(request.deviceId()));
        require(devices.size() == 1, "RECOVERY_PERMISSION_DEVICE"); value.setAll(devices.getFirst());
        var permissions = value.putArray("permissions");
        jdbc.queryForList("""
            SELECT p.code FROM permission p WHERE EXISTS (SELECT 1 FROM role_permission rp
             INNER JOIN user_role ur ON ur.role_id=rp.role_id WHERE rp.permission_id=p.id AND ur.user_id=?) ORDER BY p.code
            """, String.class, Ids.bytes(request.operatorId())).forEach(permissions::add);
        var warehouses = value.putArray("warehouseIds");
        jdbc.queryForList("""
            SELECT BIN_TO_UUID(w.id) FROM warehouse w INNER JOIN user_warehouse uw ON uw.warehouse_id=w.id
             WHERE uw.user_id=? AND w.status='ACTIVE' ORDER BY w.id
            """, String.class, Ids.bytes(request.operatorId())).forEach(warehouses::add); return value;
    }

    public record Request(RecoveryDeviceRegistrationCapture.Database database, UUID operatorId, UUID deviceId) {
        public Request {
            require(database != null && operatorId != null && deviceId != null
                && !operatorId.equals(new UUID(0, 0)) && !deviceId.equals(new UUID(0, 0)), "RECOVERY_PERMISSION_REQUEST");
        }
    }
    public record Observation(byte[] originalBytes, String originalSha256) {
        public Observation { originalBytes = originalBytes.clone(); }
        @Override public byte[] originalBytes() { return originalBytes.clone(); }
        public boolean ordinaryWritesAllowed() { return false; }
        public boolean localDeletionAllowed() { return false; }
    }
    private record Position(String file, long offset) { }
}
