package com.acrylic.outbound.recovery;

import java.io.IOException;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.require;

/**
 * Explicit initial NORMAL admission. It is the only component that may set server_state back to
 * NORMAL, and it does so only when the durable external intent and the database projection agree on
 * the same installation, control revision and hash chain. It never runs at startup, is not a Spring
 * bean, exposes no route, and grants no production capability by itself.
 */
public final class RecoveryNormalRegistration {
    /** Initial registration is deliberately distinct from the historical closed-chain register(). */
    public static InitialNormalRegistration.Result registerInitial(DataSource source, InitialNormalStore store,
            Path closedAuthority) {
        return new InitialNormalRegistration(source, store, closedAuthority).register();
    }
    /** Without a durable external intent the operator identity is not evidence; the record says so. */
    private static final String NO_EXTERNAL_INTENT = "UNRECORDED_EXTERNAL_INTENT";
    private static final String REGISTERED = "REGISTERED";
    private static final String ALREADY = "ALREADY_REGISTERED";
    private final RecoveryControlLocation location;
    private final long baseline;
    private final RecoveryLeasedTransaction transaction;
    private final RecoveryProjectionRows projection;
    private final RecoveryNormalRows rows;

    public RecoveryNormalRegistration(DataSource source, RecoveryControlLocation authority, Path checkpoint, long baseline) {
        require(baseline > 0, "CONTROL_BASELINE_REQUIRED");
        this.location = authority;
        this.baseline = baseline;
        this.transaction = new RecoveryLeasedTransaction(source, authority, checkpoint);
        this.projection = new RecoveryProjectionRows(new JdbcTemplate(source));
        this.rows = new RecoveryNormalRows(new JdbcTemplate(source));
    }

    /** Registers NORMAL for the durable intent that matches this installation, or reports the existing one. */
    public Result register() {
        RecoveryNormalIntent.Intent intent = intent();
        RecoveryProjectionRows.Database database = projection.database();
        return transaction.execute(status -> registerLocked(intent, database));
    }

    private Result registerLocked(RecoveryNormalIntent.Intent intent, RecoveryProjectionRows.Database database) {
        var control = transaction.snapshot();
        var state = projection.lockState();
        var binding = projection.binding();
        require(binding != null, "CONTROL_DATABASE_NOT_REGISTERED");
        require(binding.installation().equals(control.installationId()) && binding.baseline() == baseline,
            "CONTROL_DATABASE_INSTALLATION_MISMATCH");
        require(binding.serverUuid().equals(database.serverUuid()) && binding.schema().equals(database.schema()),
            "CONTROL_DATABASE_INSTANCE_MISMATCH");
        if (intent != null) crossCheck(intent, control, binding, database);
        var terminal = RecoveryProjectionHistory.validate(projection, binding);
        require(terminal.phase().equals("RELEASE_READY"), "CONTROL_NORMAL_PHASE_REQUIRED");
        require(terminal.numeric() == state.numeric(), "CONTROL_DATABASE_PROJECTION_MISMATCH");
        var existing = rows.existing();
        if (existing != null) return decide(existing, terminal, state, intent);
        require(state.mode().equals("RECOVERY_PENDING"), "CONTROL_NORMAL_STATE_MISMATCH");
        String operator = intent == null ? NO_EXTERNAL_INTENT : intent.operatorId();
        String intentHash = intent == null ? terminal.hash() : intent.intentSha256();
        rows.admit(binding.installation(), terminal.revision(), terminal.revision(), terminal.hash(), terminal.previousHash(),
            intentHash, terminal.numeric(), operator);
        rows.enableNormal();
        return result(REGISTERED, binding.installation().toString(), terminal, operator);
    }

    private RecoveryNormalIntent.Intent intent() {
        try {
            return RecoveryNormalIntent.load(location, location.root(), baseline).intent();
        } catch (IOException error) {
            throw new RecoveryControlException("CONTROL_NORMAL_INTENT_UNREADABLE", error);
        }
    }

    /** The durable intent must name this database, the same chain head and the same chain continuity. */
    private void crossCheck(RecoveryNormalIntent.Intent intent, RecoveryControlSnapshot control,
            RecoveryProjectionRows.Binding binding, RecoveryProjectionRows.Database database) {
        RecoveryNormalIntent.requireBound(intent, control);
        require(intent.baseline() == binding.baseline(), "CONTROL_NORMAL_INTENT_BASELINE_MISMATCH");
        require(intent.schema().equals(database.schema()), "CONTROL_NORMAL_INTENT_SCHEMA_MISMATCH");
    }

    private Result decide(RecoveryNormalRows.Record existing, RecoveryProjectionRows.Projection terminal,
            RecoveryProjectionRows.State state, RecoveryNormalIntent.Intent intent) {
        require(existing.revision() == terminal.revision(), "CONTROL_NORMAL_RECORD_CONFLICT");
        require(existing.hash().equals(terminal.hash()), "CONTROL_NORMAL_RECORD_CONFLICT");
        require(existing.previousHash().equals(terminal.previousHash()), "CONTROL_NORMAL_RECORD_CONFLICT");
        require(existing.numeric() == terminal.numeric() && terminal.numeric() == state.numeric(),
            "CONTROL_NORMAL_RECORD_CONFLICT");
        require(state.mode().equals("NORMAL"), "CONTROL_NORMAL_RECORD_INCOMPLETE");
        if (intent != null) require(existing.intentHash().equals(intent.intentSha256()), "CONTROL_NORMAL_INTENT_CONFLICT");
        return result(ALREADY, transaction.snapshot().installationId().toString(), terminal, existing.operator());
    }

    private static Result result(String outcome, String installation, RecoveryProjectionRows.Projection terminal, String operator) {
        // Registration makes the database state NORMAL; it does not by itself open ordinary business
        // writes. The runtime gate is wired separately after this evidence exists.
        return new Result(outcome, installation, Long.toString(terminal.revision()), terminal.hash(),
            Long.toString(terminal.numeric()), operator, true, false);
    }

    public record Result(String outcome, String installationId, String controlRevision, String recordSha256,
        String numericEpoch, String operatorId, boolean registered, boolean ordinaryWritesAllowed) { }
}
