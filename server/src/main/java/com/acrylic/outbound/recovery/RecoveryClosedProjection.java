package com.acrylic.outbound.recovery;

import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.require;

/** Explicit closed-phase operator primitive; no bean, route, automatic registration or NORMAL release. */
public final class RecoveryClosedProjection {
    private static final Map<String, String> NEXT = Map.of(
        "QUIESCING", "RECOVERING", "RECOVERING", "VERIFYING", "VERIFYING", "RELEASE_READY");
    private final RecoveryLeasedTransaction transaction;
    private final RecoveryProjectionRows rows;
    private final long baseline;
    private final RecoveryClosedAuthority authority;
    private final JdbcTemplate jdbc;

    public RecoveryClosedProjection(DataSource source, RecoveryClosedAuthority authority, long baseline) {
        require(baseline > 0, "CONTROL_BASELINE_REQUIRED");
        this.authority = authority; this.jdbc = new JdbcTemplate(source);
        this.transaction = new RecoveryLeasedTransaction(source, authority);
        this.rows = new RecoveryProjectionRows(new JdbcTemplate(source)); this.baseline = baseline;
    }

    public Result project() { return transaction.execute(status -> projectLocked()); }

    private Result projectLocked() {
        var control = transaction.snapshot(); var state = rows.lockState();
        var external = new RecoveryDatabaseControl(authority);
        if (external.head().path("command").path("formatVersion").intValue() == 2)
            return result(control, RecoverySuccessorProjection.project(jdbc, external, control, state, baseline));
        var binding = rows.binding(); var database = rows.database();
        if (binding == null) return register(control, state, database);
        require(binding.installation().equals(control.installationId()) && binding.baseline() == baseline,
            "CONTROL_DATABASE_INSTALLATION_MISMATCH");
        require(binding.serverUuid().equals(database.serverUuid()) && binding.schema().equals(database.schema()),
            "CONTROL_DATABASE_INSTANCE_MISMATCH");
        var previous = RecoveryProjectionHistory.validate(rows, binding);
        require(state.numeric() == previous.numeric() && state.mode().equals("RECOVERY_PENDING"),
            "CONTROL_DATABASE_PROJECTION_MISMATCH");
        if (previous.revision() == Long.parseLong(control.revision())) {
            require(matches(previous, control), "CONTROL_DATABASE_RECORD_CONFLICT");
            return result(control, previous.numeric());
        }
        long numeric = advance(previous, control);
        if (!previous.epoch().equals(control.epochId())) rows.map(control, numeric);
        rows.append(control, numeric); return result(control, numeric);
    }

    private Result register(RecoveryControlSnapshot control, RecoveryProjectionRows.State state,
            RecoveryProjectionRows.Database database) {
        require(control.revision().equals("1") && control.phase().equals("QUIESCING")
            && control.previousRecordSha256().equals(RecoveryControlFormat.ZERO_HASH), "CONTROL_DATABASE_INITIAL_RECORD_REQUIRED");
        require(state.numeric() == baseline, "CONTROL_DATABASE_BASELINE_MISMATCH");
        rows.register(control, baseline, database); rows.map(control, baseline); rows.append(control, baseline);
        return result(control, baseline);
    }

    private long advance(RecoveryProjectionRows.Projection previous, RecoveryControlSnapshot control) {
        require(Long.parseLong(control.revision()) == Math.addExact(previous.revision(), 1)
            && control.previousRecordSha256().equals(previous.hash()), "CONTROL_DATABASE_CHAIN_CONFLICT");
        require(control.phase().equals(NEXT.get(previous.phase())), "CONTROL_DATABASE_PHASE_CONFLICT");
        boolean changed = !previous.epoch().equals(control.epochId());
        require(changed == control.phase().equals("RECOVERING"), "CONTROL_DATABASE_EPOCH_CONFLICT");
        return changed ? Math.addExact(previous.numeric(), 1) : previous.numeric();
    }

    private boolean matches(RecoveryProjectionRows.Projection row, RecoveryControlSnapshot control) {
        return row.hash().equals(control.recordSha256()) && row.previousHash().equals(control.previousRecordSha256())
            && row.epoch().equals(control.epochId()) && row.phase().equals(control.phase());
    }

    private Result result(RecoveryControlSnapshot control, long numeric) {
        return new Result(control.installationId().toString(), control.epochId().toString(), control.revision(),
            control.recordSha256(), Long.toString(numeric), control.phase(), false, false);
    }

    public record Result(String installationId, String epochId, String controlRevision, String recordSha256,
        String numericEpoch, String phase, boolean ordinaryWritesAllowed, boolean localDeletionAllowed) { }
}
