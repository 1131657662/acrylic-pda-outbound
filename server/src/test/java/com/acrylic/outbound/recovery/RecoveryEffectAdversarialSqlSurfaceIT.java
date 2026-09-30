package com.acrylic.outbound.recovery;

import java.sql.*;
import java.util.stream.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.assertj.core.api.Assertions.*;

/** Independent concrete cases for every SQL allowlist branch; no classifier-derived oracle. */
class RecoveryEffectAdversarialSqlSurfaceIT {
    @BeforeAll static void environment() throws Exception { EffectRealEnvironment.requireEnvironment(); }

    static Stream<Arguments> cases() {
        return IntStream.rangeClosed(1, 3).boxed().flatMap(n -> Stream.concat(
            Stream.of("SELECT 1", "SELECT NULL", "SELECT TRUE", "SELECT FALSE", "SELECT 'text'",
                "SHOW TABLES", "SHOW DATABASES", "ROLLBACK", "ROLLBACK WORK",
                "ROLLBACK TO safe", "ROLLBACK TO SAVEPOINT safe").map(sql -> Arguments.of(n, sql, false)),
            Stream.of("SELECT * FROM $T", "SELECT id,c FROM $T WHERE id=1", "SELECT COUNT(*) FROM $T",
                "SELECT id FROM b11_adv_20260929.$T",
                "DESC $T", "DESCRIBE $T", "EXPLAIN SELECT 1", "EXPLAIN SELECT id FROM $T",
                "INSERT INTO $T VALUES(2,'b','new')", "REPLACE INTO $T VALUES(2,'b','new')",
                "MERGE INTO $T VALUES(2)", "DELETE FROM $T", "UPDATE $T SET c='changed'",
                "CREATE TABLE new_$T(id INT)", "ALTER TABLE $T ADD COLUMN n INT",
                "DROP TABLE $T", "TRUNCATE TABLE $T", "COMMIT", "COMMIT WORK", "BEGIN",
                "START TRANSACTION", "SET AUTOCOMMIT=1", "SET AUTOCOMMIT = 1")
                .map(sql -> Arguments.of(n, sql, true))));
    }

    /**
     * The forms the frozen grammar deliberately refuses rather than guessing: session-scoped SET, more than one
     * statement, a comment-led statement, and a procedural CALL body. Each has to be refused <b>before</b> the
     * delegate is reached, which is what {@code delegate=0} records. This is the gate-enabled counterpart of the
     * default-off pass-through baseline in {@link RecoveryEffectAdversarialDefaultOffIT}.
     */
    static Stream<Arguments> unclassifiableForms() {
        return IntStream.rangeClosed(1, 3).boxed().flatMap(n -> Stream.of(
            "SET SESSION sql_mode='ANSI'", "SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED",
            "SET NAMES utf8mb4",
            "SELECT 1;SELECT 2",
            "INSERT INTO $T VALUES(2,'b','new');SELECT 1",
            "/* allowed by legacy */ INSERT INTO $T VALUES(2,'b','new')",
            "-- legacy comment\nSELECT 1",
            "CALL p_$T(1)",
            "{call p_$T(?)}")
            .map(sql -> Arguments.of(n, sql)));
    }

    @ParameterizedTest(name = "{0}: unclassifiable {1}")
    @MethodSource("unclassifiableForms")
    void unclassifiableFormIsRefusedBeforeTheDelegateIsReached(int repeat, String template) throws Exception {
        try (var f = new EffectAdversarialFixture(true, false); var s = f.connection.createStatement()) {
            f.connection.setAutoCommit(false);
            String sql = template.replace("$T", f.table);
            f.gate.fence();
            String refusal = refusalOf(() -> s.execute(sql));
            assertThat(refusal).as("refused rather than delegated: " + template)
                .isIn("RECOVERY_LEASE_FENCED", "CONTROL_EFFECT_UNCLASSIFIED");
            assertThat(f.calls.count("Statement.execute"))
                .as("the driver never saw " + template).isZero();
            assertThat(f.observed()).isEqualTo("1:62:original;");
            f.record("sql-unclassifiable|repeat=" + repeat + "|sql=" + template
                + "|refusal=" + refusal + "|delegate=" + f.calls.count("Statement.execute"));
        }
    }

    /**
     * The CALL body reaches the gate through {@code prepareCall} and is classified when the callable statement is
     * executed, so the fence is raised only against a statement that was already prepared.
     */
    static Stream<Arguments> callableBodies() {
        return unclassifiableForms()
            .filter(args -> {
                String sql = (String) args.get()[1];
                return sql.startsWith("CALL") || sql.startsWith("{call");
            });
    }

    @ParameterizedTest(name = "{0}: callable body {1}")
    @MethodSource("callableBodies")
    void callableBodyIsRefusedBeforeTheDelegateIsReached(int repeat, String template) throws Exception {
        try (var f = new EffectAdversarialFixture(true, false)) {
            f.connection.setAutoCommit(false);
            String sql = template.replace("$T", f.table);
            try (CallableStatement prepared = f.connection.prepareCall(sql)) {
                f.gate.fence();
                String refusal = refusalOf(() -> prepared.execute());
                assertThat(refusal).as("the call body is refused rather than delegated: " + template)
                    .isEqualTo("CONTROL_EFFECT_UNCLASSIFIED");
            }
            assertThat(f.observed()).as("the call body had no effect").isEqualTo("1:62:original;");
            f.record("sql-callable-body|repeat=" + repeat + "|sql=" + template
                + "|refusal=CONTROL_EFFECT_UNCLASSIFIED|effect=none");
        }
    }

    /** Runs one call and returns the refusal message it produced, failing the case when nothing was refused. */
    private static String refusalOf(ThrowingCall call) {
        try { call.run(); }
        catch (Exception refused) { return refused.getMessage(); }
        throw new AssertionError("expecting a refusal, but the call returned normally");
    }

    @FunctionalInterface private interface ThrowingCall { void run() throws Exception; }

    @ParameterizedTest(name = "{0}: fenced {1} deny={2}") @MethodSource("cases")
    void eachSqlBranchHasOneMeasuredEffectConclusion(int repeat, String template, boolean denied) throws Exception {
        try (var f = new EffectAdversarialFixture(true, false); var s = f.connection.createStatement()) {
            f.connection.setAutoCommit(false);
            f.connection.setSavepoint("safe");
            String sql = template.replace("$T", f.table);
            f.gate.fence();
            if (denied) assertThatThrownBy(() -> s.execute(sql)).hasMessage("RECOVERY_LEASE_FENCED");
            else s.execute(sql);
            assertThat(f.calls.count("Statement.execute")).isEqualTo(denied ? 0 : 1);
            assertThat(f.observed()).isEqualTo("1:62:original;");
            f.record("sql-surface|repeat=" + repeat + "|sql=" + template + "|denied=" + denied
                + "|delegate=" + f.calls.count("Statement.execute") + "|committedDelta=0");
        }
    }
}
