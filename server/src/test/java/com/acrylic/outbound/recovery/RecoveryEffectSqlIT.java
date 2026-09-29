package com.acrylic.outbound.recovery;

import java.sql.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RecoveryEffectSqlIT {
    @BeforeAll static void environment() throws Exception { EffectRealEnvironment.requireEnvironment(); }

    @ParameterizedTest @ValueSource(strings = {"comment", "multi", "session", "vendor", "ddl", "call"})
    void s20uUnknownSqlIsRefusedBeforeRealDriver(String kind) throws Exception {
        try (var f = new EffectRealFixture(true, true)) {
            String sql = unknown(f, kind);
            var statement = f.connection.createStatement();
            int before = f.calls.count("Statement.execute");
            assertThatThrownBy(() -> statement.execute(sql)).hasMessage("CONTROL_EFFECT_UNCLASSIFIED");
            assertThat(f.calls.count("Statement.execute")).isEqualTo(before);
            assertThat(f.authority.checks).isZero();
            assertThat(f.count()).isZero();
            f.record("S20-u-" + kind + "|delegateDelta=0|allowMultiQueries=true");
        }
    }

    @Test void s20PreparedWriteChecksAtExecutionAndCommitsWhenAllowed() throws Exception {
        try (var f = new EffectRealFixture(true, false)) {
            var statement = f.connection.prepareStatement("INSERT INTO " + f.table + "(id,val) VALUES (?,?)");
            statement.setInt(1, 1); statement.setString(2, "prepared");
            assertThat(statement.executeUpdate()).isEqualTo(1);
            assertThat(f.count()).isEqualTo(1);
            statement.setInt(1, 2); f.deny();
            int before = f.calls.count("PreparedStatement.executeUpdate");
            assertThatThrownBy(statement::executeUpdate).hasMessage("RECOVERY_LEASE_OUTSTANDING");
            assertThat(f.calls.count("PreparedStatement.executeUpdate")).isEqualTo(before);
            assertThat(f.count()).isEqualTo(1);
            f.record("S20-prepared-noarg|allowed=1|deniedDelegateDelta=0");
        }
    }

    @Test void s20CallableBodyIsRejectedWithRememberedSql() throws Exception {
        try (var f = new EffectRealFixture(true, false)) {
            createProcedure(f);
            var statement = f.connection.prepareCall("{call p_" + f.table + "()}");
            assertThatThrownBy(statement::execute).hasMessage("CONTROL_EFFECT_UNCLASSIFIED");
            assertThatThrownBy(statement::addBatch).hasMessage("CONTROL_EFFECT_UNCLASSIFIED");
            assertThat(f.calls.count("CallableStatement.execute")).isZero();
            assertThat(f.calls.count("CallableStatement.addBatch")).isZero();
            assertThat(f.count()).isZero();
            f.record("S20-callable-noarg|delegateDelta=0|body=real-procedure");
        }
    }

    @Test void s20ProvenReadDoesNotConsultDeniedAuthority() throws Exception {
        try (var f = new EffectRealFixture(true, false)) {
            f.gate.fence();
            try (var read = f.connection.prepareStatement("SELECT id FROM " + f.table);
                    var rows = read.executeQuery()) { assertThat(rows.next()).isFalse(); }
            assertThat(f.authority.checks).isZero();
            f.record("S20-read|gateChecks=0");
        }
    }

    @Test void s20uMissingSqlMemoryRefusesRealPreparedDriver() throws Exception {
        try (var f = new EffectRealFixture(true, false); var statement = f.withoutSqlMemory()) {
            assertThatThrownBy(statement::execute).hasMessage("CONTROL_EFFECT_UNCLASSIFIED");
            assertThatThrownBy(statement::executeQuery).hasMessage("CONTROL_EFFECT_UNCLASSIFIED");
            assertThatThrownBy(statement::addBatch).hasMessage("CONTROL_EFFECT_UNCLASSIFIED");
            assertThatThrownBy(statement::executeLargeBatch).hasMessage("CONTROL_EFFECT_UNCLASSIFIED");
            assertThat(f.calls.count("PreparedStatement.execute")).isZero();
            assertThat(f.calls.count("PreparedStatement.executeQuery")).isZero();
            assertThat(f.calls.count("PreparedStatement.addBatch")).isZero();
            assertThat(f.calls.count("PreparedStatement.executeLargeBatch")).isZero();
            assertThat(f.count()).isZero();
            f.record("S20-u-missing-memory|delegateDelta=0|memoryLoss=MODEL-input");
        }
    }

    @Test void defaultOffRetainsRealCommentMultiAndSessionBehavior() throws Exception {
        try (var f = new EffectRealFixture(false, true)) {
            f.gate.fence();
            var statement = f.connection.createStatement();
            statement.execute("/* hint */ " + f.insert(1));
            statement.execute(f.insert(2) + ";" + f.insert(3));
            statement.execute("SET SESSION sql_mode='STRICT_TRANS_TABLES'");
            assertThat(f.count()).isEqualTo(3);
            assertThat(f.authority.checks).isZero();
            f.record("DEFAULT-OFF|comment+multi+session-delegated|gateChecks=0");
        }
    }

    private static String unknown(EffectRealFixture f, String kind) {
        return switch (kind) {
            case "comment" -> "/* hint */ " + f.insert(1);
            case "multi" -> f.insert(1) + ";" + f.insert(2);
            case "session" -> "SET SESSION sql_mode='ANSI'";
            case "vendor" -> "SOME VENDOR SYNTAX";
            case "ddl" -> "CREATE PROCEDURE p_" + f.table + "() " + f.insert(1);
            case "call" -> "CALL p_" + f.table + "()";
            default -> throw new IllegalArgumentException(kind);
        };
    }

    private static void createProcedure(EffectRealFixture f) throws SQLException {
        try (var setup = EffectRealEnvironment.open(false); var statement = setup.createStatement()) {
            statement.execute("CREATE PROCEDURE p_" + f.table + "() " + f.insert(1));
        }
    }
}
