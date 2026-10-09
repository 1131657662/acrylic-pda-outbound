package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Actual native preparation/ACK reads, with explicitly supplied original command; no signature generation. */
public final class RecoveryTemporaryPreparationCapture {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public RecoveryTemporaryPreparationCapture(DataSource database) {
        jdbc = new JdbcTemplate(Objects.requireNonNull(database));
        transaction = new TransactionTemplate(new DataSourceTransactionManager(database));
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    public Observation capture(Request request) {
        require(request != null && !TransactionSynchronizationManager.isActualTransactionActive(), "RECOVERY_TEMP_CAPTURE_TRANSACTION");
        return transaction.execute(status -> {
            var metadata = state(request.database());
            var origin = new RecoveryTemporaryPreparationRows(jdbc).read(request);
            byte[] bytes = SubmissionJson.canonical(origin).getBytes(StandardCharsets.UTF_8);
            require(bytes.length <= 33554432, "RECOVERY_TEMP_CAPTURE_SIZE");
            metadata.set("source", RecoveryOriginalOccupancyFacts.source(request.identity()));
            metadata.put("originSha256", sha256(bytes));
            byte[] control = RecoveryControlJson.encode(metadata);
            return new Observation(control, sha256(control), bytes, instant(metadata, "observedAt"));
        });
    }

    private ObjectNode state(RecoveryDeviceRegistrationCapture.Database expected) {
        var rows = jdbc.query("SELECT @@server_uuid,DATABASE(),recovery_epoch,service_mode,UTC_TIMESTAMP(3) FROM server_state WHERE id=1",
            (rs, index) -> SubmissionJson.object().put("formatVersion", 1).put("observationKind", "ORIGINAL_TEMP_PREPARATION")
                .put("mysqlServerUuid", rs.getString(1)).put("databaseSchema", rs.getString(2)).put("sourceRecoveryEpoch", rs.getString(3))
                .put("serviceMode", rs.getString(4)).put("observedAt", rs.getTimestamp(5).toInstant().toString()));
        require(rows.size() == 1, "RECOVERY_TEMP_CAPTURE_STATE"); var value = rows.getFirst();
        require(uuid(value, "mysqlServerUuid").equals(expected.mysqlServerUuid()) && text(value, "databaseSchema").equals(expected.schema())
            && text(value, "sourceRecoveryEpoch").equals(expected.sourceRecoveryEpoch())
            && "NORMAL".equals(text(value, "serviceMode")), "RECOVERY_TEMP_CAPTURE_DATABASE");
        value.remove("serviceMode"); return value;
    }

    public record Request(RecoveryDeviceRegistrationCapture.Database database, RecoverySourceIdentity identity, String originalAckCommand) {
        public Request {
            require(database != null && identity != null && identity.sourceKind().equals("TEMP") && identity.workRound() == 1
                && originalAckCommand != null && originalAckCommand.length() > 0 && originalAckCommand.length() <= 16384,
                "RECOVERY_TEMP_CAPTURE_REQUEST");
            require(database.sourceRecoveryEpoch().equals(identity.sourceRecoveryEpoch()), "RECOVERY_TEMP_CAPTURE_EPOCH");
        }
    }
    public record Observation(byte[] originalMetadata, String independentSha256, byte[] originalOrigin, Instant observedAt) {
        public Observation { originalMetadata = originalMetadata.clone(); originalOrigin = originalOrigin.clone(); }
        @Override public byte[] originalMetadata() { return originalMetadata.clone(); }
        @Override public byte[] originalOrigin() { return originalOrigin.clone(); }
        public boolean ordinaryWritesAllowed() { return false; }
        public boolean localDeletionAllowed() { return false; }
    }
}
