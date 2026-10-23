package com.acrylic.outbound.recovery;

import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Explicit source-pin inspection under a closed lease; never infers ownership from an imported binding. */
public final class RecoveryDatabaseSource {
    private RecoveryDatabaseSource() { }

    public static Pins inspect(DataSource source, RecoveryClosedAuthority authority, long baseline) {
        require(baseline > 0, "CONTROL_BASELINE_REQUIRED");
        var transaction = new RecoveryLeasedTransaction(source, authority); var jdbc = new JdbcTemplate(source);
        return transaction.execute(status -> inspectLocked(jdbc, authority, baseline));
    }

    private static Pins inspectLocked(JdbcTemplate jdbc, RecoveryClosedAuthority authority, long baseline) {
        var projection = new RecoveryProjectionRows(jdbc); var state = projection.lockState();
        var control = new RecoveryDatabaseControl(authority); var view = new RecoverySuccessorLineage(jdbc, control, baseline).read();
        view.requireDatabase(projection.database());
        require(view.terminal().revision() == revision(control.head(), "revision") && view.terminal().phase().equals("RELEASE_READY")
            && view.normalHash() != null && view.intent() != null, "CONTROL_SUCCESSOR_PREDECESSOR_REQUIRED");
        require(state.mode().equals("NORMAL") && state.numeric() == view.terminal().numeric(), "CONTROL_NORMAL_STATE_MISMATCH");
        return new Pins(view.terminal().hash(), view.intent().intentSha256(), view.bindingHash(), view.normalHash(), false, false);
    }

    public record Pins(String predecessorReleaseSha256, String predecessorNormalIntentSha256, String sourceBindingSha256,
        String sourceNormalSha256, boolean ordinaryWritesAllowed, boolean localDeletionAllowed) { }
}
