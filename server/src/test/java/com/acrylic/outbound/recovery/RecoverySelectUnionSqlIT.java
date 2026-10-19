package com.acrylic.outbound.recovery;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

/** Real isolated MySQL/JDBC below the product gate; authority remains the existing MODEL fixture. */
class RecoverySelectUnionSqlIT {
    @BeforeAll static void environment() throws Exception { EffectRealEnvironment.requireEnvironment(); }

    @Test void statementUnionRechecksAuthorityBeforeTheSecondExecution() throws Exception {
        try (var f = new EffectRealFixture(true, false); var statement = f.connection.createStatement()) {
            f.write(1); f.write(2);
            String sql = "SELECT id FROM " + f.table + " WHERE id=1 UNION ALL SELECT id FROM "
                + f.table + " WHERE id=2 ORDER BY id";
            try (var rows = statement.executeQuery(sql)) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getInt(1)).isEqualTo(1);
                assertThat(rows.next()).isTrue(); assertThat(rows.getInt(1)).isEqualTo(2);
                assertThat(rows.next()).isFalse();
            }
            int checks = f.authority.checks;
            f.deny();
            assertThatThrownBy(() -> statement.executeQuery(sql)).hasMessage("RECOVERY_LEASE_OUTSTANDING");
            assertThat(f.authority.checks).isGreaterThan(checks);
            assertThat(f.calls.count("Statement.executeQuery")).isEqualTo(1);
            assertThat(f.count()).isEqualTo(2);
            f.record("union-statement|rows=1,2|deniedAfterRead=true|driverExecutions=1");
        }
    }

    @Test void preparedUnionIsRefusedAfterFenceWithoutExecutingAgain() throws Exception {
        try (var f = new EffectRealFixture(true, false)) {
            f.write(1); f.write(2);
            String sql = "SELECT id FROM " + f.table + " WHERE id=? UNION ALL SELECT id FROM "
                + f.table + " WHERE id=? ORDER BY id";
            try (var statement = f.connection.prepareStatement(sql)) {
                statement.setInt(1, 1); statement.setInt(2, 2);
                try (var rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue(); assertThat(rows.getInt(1)).isEqualTo(1);
                    assertThat(rows.next()).isTrue(); assertThat(rows.getInt(1)).isEqualTo(2);
                    assertThat(rows.next()).isFalse();
                }
                f.gate.fence();
                assertThatThrownBy(statement::executeQuery).hasMessage("RECOVERY_LEASE_FENCED");
            }
            assertThat(f.calls.count("PreparedStatement.executeQuery")).isEqualTo(1);
            assertThat(f.count()).isEqualTo(2);
            f.record("union-prepared|rows=1,2|fenced=true|driverExecutions=1");
        }
    }

    @Test void nestedUnionUsesTheRealSubqueryResults() throws Exception {
        try (var f = new EffectRealFixture(true, false); var statement = f.connection.createStatement()) {
            f.write(1); f.write(2);
            String sql = "SELECT id FROM " + f.table + " WHERE id IN (SELECT id FROM " + f.table
                + " WHERE id=1 UNION ALL SELECT id FROM " + f.table + " WHERE id=2) ORDER BY id";
            try (var rows = statement.executeQuery(sql)) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getInt(1)).isEqualTo(1);
                assertThat(rows.next()).isTrue(); assertThat(rows.getInt(1)).isEqualTo(2);
                assertThat(rows.next()).isFalse();
            }
            assertThat(f.calls.count("Statement.executeQuery")).isEqualTo(1);
            assertThat(f.count()).isEqualTo(2);
            f.record("union-nested|rows=1,2|driverExecutions=1");
        }
    }

    @Test void sixtyFourBranchesPreserveDuplicateRows() throws Exception {
        try (var f = new EffectRealFixture(true, false); var statement = f.connection.createStatement()) {
            f.write(1);
            String branch = "SELECT id FROM " + f.table;
            String sql = branch + (" UNION ALL " + branch).repeat(63);
            int count = 0;
            try (var rows = statement.executeQuery(sql)) {
                while (rows.next()) { assertThat(rows.getInt(1)).isEqualTo(1); count++; }
            }
            assertThat(count).isEqualTo(64);
            assertThat(f.calls.count("Statement.executeQuery")).isEqualTo(1);
            assertThat(f.count()).isEqualTo(1);
            f.record("union-boundary|branches=64|rows=64|driverExecutions=1");
        }
    }

    @Test void sixtyFifthBranchIsRefusedBeforeTheRealDriver() throws Exception {
        try (var f = new EffectRealFixture(true, false); var statement = f.connection.createStatement()) {
            f.write(1);
            String branch = "SELECT id FROM " + f.table;
            String sql = branch + (" UNION ALL " + branch).repeat(64);
            assertThatThrownBy(() -> statement.executeQuery(sql)).hasMessage("CONTROL_EFFECT_UNCLASSIFIED");
            assertThat(f.calls.count("Statement.executeQuery")).isZero();
            assertThat(f.count()).isEqualTo(1);
            f.record("union-boundary|branches=65|refused=true|driverExecutions=0");
        }
    }

    @ParameterizedTest @ValueSource(strings = {
        "SELECT id FROM $T UNION SELECT id FROM $T",
        "SELECT id FROM $T UNION ALL UPDATE $T SET val='changed'",
        "SELECT id FROM $T UNION ALL SELECT SLEEP(1) FROM $T",
        "SELECT id FROM $T UNION ALL",
        "SELECT id FROM $T UNION ALL SELECT id FROM $T WHERE"
    })
    void unsupportedUnionSyntaxHasNoDriverExecution(String template) throws Exception {
        try (var f = new EffectRealFixture(true, false); var statement = f.connection.createStatement()) {
            f.write(1);
            String sql = template.replace("$T", f.table);
            assertThatThrownBy(() -> statement.execute(sql)).hasMessage("CONTROL_EFFECT_UNCLASSIFIED");
            assertThat(f.calls.count("Statement.execute")).isZero();
            assertThat(f.count()).isEqualTo(1);
            assertThat(f.value(1)).isEqualTo("value");
            f.record("union-invalid|sql=" + template + "|refused=true|driverExecutions=0");
        }
    }
}
