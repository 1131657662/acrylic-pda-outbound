package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Explicit pre-failure read seam. Actual database time and instance, no backdated inputs or automatic custody. */
public final class RecoveryDeviceRegistrationCapture {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public RecoveryDeviceRegistrationCapture(DataSource source) {
        Objects.requireNonNull(source); jdbc = new JdbcTemplate(source);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    public Observation capture(Request request) {
        require(request != null && !TransactionSynchronizationManager.isActualTransactionActive(), "RECOVERY_DEVICE_CAPTURE_TRANSACTION");
        return transaction.execute(status -> {
            var value = state(request.database());
            value.set("device", device(request)); value.set("pairing", pairing(request.deviceId()));
            var expected = new RecoveryDeviceRegistration.Expected(request.database().mysqlServerUuid(),
                request.database().schema(), request.database().sourceRecoveryEpoch(), request.warehouseId(), request.deviceId(),
                instant(value, "observedAt"));
            RecoveryDeviceRegistrationFacts.verify(value, expected);
            byte[] bytes = RecoveryControlJson.encode(value);
            return new Observation(bytes, sha256(bytes), instant(value, "observedAt"));
        });
    }

    private ObjectNode state(Database expected) {
        var rows = jdbc.query("SELECT @@server_uuid,DATABASE(),recovery_epoch,service_mode,UTC_TIMESTAMP(3) FROM server_state WHERE id=1",
            (rs, index) -> SubmissionJson.object().put("formatVersion", 1).put("observationKind", "ORIGINAL_DEVICE_REGISTRATION")
                .put("mysqlServerUuid", rs.getString(1)).put("databaseSchema", rs.getString(2)).put("sourceRecoveryEpoch", rs.getString(3))
                .put("serviceMode", rs.getString(4)).put("observedAt", rs.getTimestamp(5).toInstant().toString()));
        require(rows.size() == 1, "RECOVERY_DEVICE_CAPTURE_STATE"); var value = rows.getFirst();
        require(uuid(value, "mysqlServerUuid").equals(expected.mysqlServerUuid()) && text(value, "databaseSchema").equals(expected.schema())
            && text(value, "sourceRecoveryEpoch").equals(expected.sourceRecoveryEpoch())
            && "NORMAL".equals(text(value, "serviceMode")), "RECOVERY_DEVICE_CAPTURE_DATABASE");
        value.remove("serviceMode"); return value;
    }

    private ObjectNode device(Request request) {
        var rows = jdbc.query("SELECT BIN_TO_UUID(warehouse_id),BIN_TO_UUID(id),public_key,key_sha256,created_at,row_version,status,"
            + "temporary_order_enabled FROM device WHERE id=?", (rs, index) -> SubmissionJson.object()
                .put("warehouseId", rs.getString(1)).put("deviceId", rs.getString(2)).put("publicKey", rs.getString(3))
                .put("keyTextSha256", rs.getString(4)).put("registeredAt", rs.getTimestamp(5).toInstant().toString())
                .put("deviceVersion", rs.getString(6)).put("status", rs.getString(7)).put("temporaryOrderEnabled", rs.getBoolean(8)),
            Ids.bytes(request.deviceId()));
        require(rows.size() == 1, "RECOVERY_DEVICE_CAPTURE_UNKNOWN"); return rows.getFirst();
    }

    private com.fasterxml.jackson.databind.JsonNode pairing(UUID device) {
        var rows = jdbc.query("SELECT BIN_TO_UUID(id),status,BIN_TO_UUID(device_id),public_key,key_sha256,BIN_TO_UUID(approved_by),"
            + "approved_auth_version,approval_json,approval_sha256 FROM device_pairing WHERE device_id=?", (rs, index) -> {
                var value = SubmissionJson.object().put("applicationId", rs.getString(1)).put("status", rs.getString(2))
                    .put("deviceId", rs.getString(3)).put("publicKey", rs.getString(4)).put("keyTextSha256", rs.getString(5))
                    .put("approvedBy", rs.getString(6)).put("approvedAuthVersion", rs.getString(7)).put("approvalSha256", rs.getString(9));
                value.set("approval", rs.getString(8) == null ? com.fasterxml.jackson.databind.node.NullNode.instance
                    : SubmissionJson.parse(rs.getString(8).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                return value;
            }, Ids.bytes(device));
        require(rows.size() <= 1, "RECOVERY_DEVICE_CAPTURE_PAIRING");
        return rows.isEmpty() ? com.fasterxml.jackson.databind.node.NullNode.instance : rows.getFirst();
    }

    public record Database(UUID mysqlServerUuid, String schema, String sourceRecoveryEpoch) {
        public Database {
            require(mysqlServerUuid != null && !mysqlServerUuid.equals(new UUID(0, 0)) && schema != null
                && schema.matches("[A-Za-z][A-Za-z0-9_]{0,63}") && sourceRecoveryEpoch != null
                && sourceRecoveryEpoch.matches("[1-9][0-9]{0,17}"), "RECOVERY_DEVICE_CAPTURE_DATABASE");
        }
    }
    public record Request(Database database, UUID warehouseId, UUID deviceId) {
        public Request {
            require(database != null, "RECOVERY_DEVICE_CAPTURE_DATABASE");
            new RecoveryDeviceRegistration.Expected(database.mysqlServerUuid(), database.schema(), database.sourceRecoveryEpoch(),
                warehouseId, deviceId, Instant.EPOCH);
        }
    }
    public record Observation(byte[] originalBytes, String originalSha256, Instant observedAt) {
        public Observation { originalBytes = originalBytes.clone(); }
        @Override public byte[] originalBytes() { return originalBytes.clone(); }
        public boolean ordinaryWritesAllowed() { return false; }
        public boolean localDeletionAllowed() { return false; }
    }
}
