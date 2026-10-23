package com.acrylic.outbound.recovery;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * Closed-chain read-only check on the connection the adapter will actually return. No pool lookup,
 * registration, initialization, writes or effect permission. Initial NORMAL is a separate protocol;
 * this capability has no initialization bypass; the Spring request entry installs it for every
 * business borrow.
 */
public final class RecoveryClosedConnectionAdmission
        implements RecoveryRequestAdmittedDataSource.ConnectionAdmission {
    private final RecoveryControlLocation location;
    private final Path checkpoint;
    private final long baseline;

    public RecoveryClosedConnectionAdmission(RecoveryControlLocation location, Path checkpoint, long baseline) {
        this.location = Objects.requireNonNull(location);
        this.checkpoint = Objects.requireNonNull(checkpoint);
        if (baseline <= 0) throw new IllegalArgumentException("CONTROL_BASELINE_REQUIRED");
        this.baseline = baseline;
    }

    @Override public void verify(Connection physical) throws SQLException {
        // JdbcTemplate may close its handle; suppressClose preserves the adapter's ownership.
        // A fresh wrapper cannot resolve another transaction-bound datasource or borrow a pool peer.
        var source = new SingleConnectionDataSource(Objects.requireNonNull(physical), true);
        var result = RecoveryAdmissionVerifier.of(new RecoveryAdmissionQueries(source),
            new RecoveryControlReader(location), checkpoint, location, baseline).verify();
        if (!result.admitted()) throw new RecoveryDatabaseAdmissionException(result.gaps());
    }
}
