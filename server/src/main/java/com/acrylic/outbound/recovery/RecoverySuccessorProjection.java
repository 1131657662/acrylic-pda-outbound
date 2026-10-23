package com.acrylic.outbound.recovery;

import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import com.acrylic.outbound.recovery.RecoveryProjectionRows.*;

/** Successor projection under the same closed transaction as the original projection entry. */
final class RecoverySuccessorProjection {
    private RecoverySuccessorProjection() { }

    static long project(JdbcTemplate jdbc, RecoveryDatabaseControl control, RecoveryControlSnapshot snapshot,
            State state, long baseline) {
        var rows = new RecoveryProjectionRows(jdbc); var lineage = new RecoverySuccessorLineage(jdbc, control, baseline);
        var view = lineage.read(); var previous = view.terminal(); var actual = rows.database();
        require(state.numeric() == previous.numeric(), "CONTROL_DATABASE_PROJECTION_MISMATCH");
        long revision = Long.parseLong(snapshot.revision());
        if (revision == previous.revision()) {
            view.requireDatabase(actual);
            require(state.mode().equals("RECOVERY_PENDING"), "CONTROL_DATABASE_PROJECTION_MISMATCH");
            return previous.numeric();
        }
        require(revision == Math.addExact(previous.revision(), 1), "CONTROL_DATABASE_CHAIN_CONFLICT");
        var binding = snapshot.phase().equals("QUIESCING") ? RecoverySuccessorRows.binding(control.head()) : null;
        if (binding != null) {
            lineage.validateBinding(binding, revision, view);
            require(state.mode().equals("NORMAL"), "CONTROL_NORMAL_STATE_MISMATCH");
            require(text(binding, "serverUuid").equals(actual.serverUuid()) && text(binding, "schema").equals(actual.schema()),
                "CONTROL_DATABASE_INSTANCE_MISMATCH");
        } else {
            view.requireDatabase(actual);
            require(state.mode().equals("RECOVERY_PENDING"), "CONTROL_DATABASE_PROJECTION_MISMATCH");
        }
        long numeric = snapshot.phase().equals("RECOVERING") ? Math.addExact(previous.numeric(), 1) : previous.numeric();
        if (!previous.epoch().equals(snapshot.epochId())) rows.map(snapshot, numeric);
        rows.append(snapshot, numeric);
        if (binding != null) new RecoverySuccessorRows(jdbc).bind(binding);
        return numeric;
    }
}
