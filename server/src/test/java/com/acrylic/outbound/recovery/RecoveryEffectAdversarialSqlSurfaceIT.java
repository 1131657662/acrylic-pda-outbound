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
