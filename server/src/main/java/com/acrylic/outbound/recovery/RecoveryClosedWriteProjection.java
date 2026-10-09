package com.acrylic.outbound.recovery;

import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.require;

/** Locks and compares the actual closed database projection; never registers or repairs it. */
final class RecoveryClosedWriteProjection {
    private final RecoveryProjectionRows rows;
    private final RecoveryProjectionRows.Database expected;

    RecoveryClosedWriteProjection(JdbcTemplate jdbc) {
        rows = new RecoveryProjectionRows(jdbc);
        expected = rows.database();
        require(expected != null && expected.serverUuid() != null && expected.schema() != null,
            "CONTROL_DATABASE_INSTANCE_MISMATCH");
    }

    void requireMatches(RecoveryControlSnapshot control) {
        var state = rows.lockState(); var binding = rows.binding(); var database = rows.database();
        require(binding != null, "CONTROL_DATABASE_NOT_REGISTERED");
        require(binding.installation().equals(control.installationId()), "CONTROL_DATABASE_INSTALLATION_MISMATCH");
        require(expected.equals(database) && binding.serverUuid().equals(database.serverUuid())
            && binding.schema().equals(database.schema()), "CONTROL_DATABASE_INSTANCE_MISMATCH");
        var latest = RecoveryProjectionHistory.validate(rows, binding);
        require(latest.revision() == Long.parseLong(control.revision())
            && latest.hash().equals(control.recordSha256()) && latest.previousHash().equals(control.previousRecordSha256())
            && latest.epoch().equals(control.epochId()) && latest.phase().equals(control.phase())
            && latest.numeric() == state.numeric() && "RECOVERY_PENDING".equals(state.mode()),
            "CONTROL_DATABASE_PROJECTION_MISMATCH");
    }
}
