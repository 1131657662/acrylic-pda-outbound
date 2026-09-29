package com.acrylic.outbound.recovery;

import java.sql.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class RecoveryEffectAdversarialViewIT {
    @BeforeAll static void environment() throws Exception { EffectRealEnvironment.requireEnvironment(); }

    @RepeatedTest(3) void tableShapedSelectCannotExecuteMutatingViewAfterFence() throws Exception {
        try (var f = new EffectAdversarialFixture(true, false)) {
            installView(f);
            String before = f.observed();
            var statement = f.connection.createStatement();
            f.gate.fence();
            Throwable failure = catchThrowable(() -> statement.executeQuery("SELECT n FROM v_" + f.table));
            f.record("view-function|delegate=" + f.calls.count("Statement.executeQuery") + "|failure=" + failure);
            assertThat(failure).hasMessage("RECOVERY_LEASE_FENCED");
            assertThat(f.calls.count("Statement.executeQuery")).isZero();
            assertThat(f.observed()).isEqualTo(before);
        }
    }

    @RepeatedTest(3) void allowedViewIsObservedAsRealEffect() throws Exception {
        try (var f = new EffectAdversarialFixture(true, false)) {
            installView(f);
            try (var s = f.connection.createStatement(); var r = s.executeQuery("SELECT n FROM v_" + f.table)) {
                assertThat(r.next()).isTrue();
                assertThat(r.getInt(1)).isEqualTo(1);
            }
            f.record("view-function-allowed|delegate=" + f.calls.count("Statement.executeQuery"));
            assertThat(f.observed()).contains("2:62:new;");
            assertThat(f.authority.checks).isEqualTo(1);
        }
    }

    private static void installView(EffectAdversarialFixture f) throws SQLException {
        try (var c = EffectRealEnvironment.open(false); var s = c.createStatement()) {
            s.execute("CREATE FUNCTION fn_" + f.table + "() RETURNS INT MODIFIES SQL DATA BEGIN "
                + f.writeSql() + "; RETURN 1; END");
            s.execute("CREATE VIEW v_" + f.table + " AS SELECT fn_" + f.table + "() AS n");
        }
    }
}
