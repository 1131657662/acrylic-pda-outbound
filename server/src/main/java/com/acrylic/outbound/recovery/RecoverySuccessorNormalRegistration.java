package com.acrylic.outbound.recovery;

import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Explicit per-round NORMAL evidence only. No route, bean, runtime admission or production capability. */
public final class RecoverySuccessorNormalRegistration {
    private final RecoveryLeasedTransaction transaction;
    private final RecoveryClosedAuthority authority;
    private final JdbcTemplate jdbc;
    private final long baseline;

    public RecoverySuccessorNormalRegistration(DataSource source, RecoveryClosedAuthority authority, long baseline) {
        require(baseline > 0, "CONTROL_BASELINE_REQUIRED"); this.baseline = baseline;
        this.authority = authority; this.jdbc = new JdbcTemplate(source);
        transaction = new RecoveryLeasedTransaction(source, authority);
    }

    public Result register() { return transaction.execute(status -> registerLocked()); }

    private Result registerLocked() {
        var projection = new RecoveryProjectionRows(jdbc); var state = projection.lockState();
        var control = new RecoveryDatabaseControl(authority); var lineage = new RecoverySuccessorLineage(jdbc, control, baseline);
        var view = lineage.read(); var terminal = view.terminal();
        require(view.binding() != null && terminal.phase().equals("RELEASE_READY"), "CONTROL_NORMAL_PHASE_REQUIRED");
        require(terminal.revision() == revision(control.head(), "revision"), "CONTROL_DATABASE_CHAIN_CONFLICT");
        view.requireDatabase(projection.database());
        require(terminal.numeric() == state.numeric(), "CONTROL_DATABASE_PROJECTION_MISMATCH");
        var intent = control.normal(terminal.revision()); lineage.requireNormal(intent, view.schema());
        var expected = RecoverySuccessorRows.normal(view.binding(), intent, terminal.numeric());
        if (view.normal() != null) {
            require(view.normal().equals(expected) && state.mode().equals("NORMAL"), "CONTROL_NORMAL_RECORD_INCOMPLETE");
            return result("ALREADY_REGISTERED", terminal, intent);
        }
        require(state.mode().equals("RECOVERY_PENDING"), "CONTROL_NORMAL_STATE_MISMATCH");
        new RecoverySuccessorRows(jdbc).admit(expected); new RecoveryNormalRows(jdbc).enableNormal();
        return result("REGISTERED", terminal, intent);
    }

    private static Result result(String outcome, RecoveryProjectionRows.Projection terminal, RecoveryNormalIntent.Intent intent) {
        return new Result(outcome, intent.installation().toString(), Long.toString(terminal.revision()), terminal.hash(),
            Long.toString(terminal.numeric()), intent.intentSha256(), false, false);
    }

    public record Result(String outcome, String installationId, String controlRevision, String recordSha256,
        String numericEpoch, String intentSha256, boolean ordinaryWritesAllowed, boolean localDeletionAllowed) { }
}
