package com.acrylic.outbound.recovery;

import java.sql.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecoveryEffectStatementTest {
    @ParameterizedTest @ValueSource(strings = {"COMMIT", "SET AUTOCOMMIT=1", "CREATE TABLE x(id INT)",
        "INSERT INTO t VALUES(1)", "START TRANSACTION"})
    void s20ManualModeCannotBypassKnownEffect(String sql) throws Exception {
        try (var f = new EffectUnitFixture(true)) {
            var raw = mock(Statement.class);
            when(f.physical.createStatement()).thenReturn(raw);
            f.connection.setAutoCommit(false); f.deny();
            assertThatThrownBy(() -> f.connection.createStatement().execute(sql)).hasMessage("RECOVERY_LEASE_OUTSTANDING");
            verifyNoInteractions(raw);
        }
    }

    @Test void s20PreparedNoArgumentExecutionRemembersSql() throws Exception {
        try (var f = new EffectUnitFixture(true)) {
            var raw = mock(PreparedStatement.class);
            when(f.physical.prepareStatement("INSERT INTO t VALUES(?)")).thenReturn(raw);
            var statement = f.connection.prepareStatement("INSERT INTO t VALUES(?)");
            f.deny();
            assertThatThrownBy(statement::executeUpdate).hasMessage("RECOVERY_LEASE_OUTSTANDING");
            verifyNoInteractions(raw);
        }
    }

    @Test void s20ReadOnlyPreparedExecutionSkipsAuthority() throws Exception {
        try (var f = new EffectUnitFixture(true)) {
            var raw = mock(PreparedStatement.class);
            when(f.physical.prepareStatement("SELECT ?")).thenReturn(raw);
            var statement = f.connection.prepareStatement("SELECT ?");
            f.gate.fence(); statement.executeQuery();
            verify(raw).executeQuery();
            assertThat(f.authority.checks).isZero();
        }
    }

    @Test void s20CallableNoArgumentExecutionRefusesBody() throws Exception {
        try (var f = new EffectUnitFixture(true)) {
            var raw = mock(CallableStatement.class);
            when(f.physical.prepareCall("{call p()}")).thenReturn(raw);
            var statement = f.connection.prepareCall("{call p()}");
            assertThatThrownBy(statement::execute).hasMessage("CONTROL_EFFECT_UNCLASSIFIED");
            assertThatThrownBy(statement::addBatch).hasMessage("CONTROL_EFFECT_UNCLASSIFIED");
            verifyNoInteractions(raw);
        }
    }

    @Test void s20BatchExecutionRechecksDecisionAfterEnqueue() throws Exception {
        try (var f = new EffectUnitFixture(true)) {
            var raw = mock(Statement.class);
            when(f.physical.createStatement()).thenReturn(raw);
            var statement = f.connection.createStatement();
            statement.addBatch("DELETE FROM t"); f.deny();
            assertThatThrownBy(statement::executeLargeBatch).hasMessage("RECOVERY_LEASE_OUTSTANDING");
            verify(raw, never()).executeLargeBatch();
            assertThat(f.authority.checks).isEqualTo(2);
            statement.clearBatch(); statement.executeBatch();
            verify(raw).executeBatch();
        }
    }

    @Test void s20uNoArgumentWithoutMemoryIsRejected() throws Exception {
        try (var f = new EffectUnitFixture(true)) {
            var raw = mock(PreparedStatement.class);
            when(f.physical.createStatement()).thenReturn(raw);
            var statement = (PreparedStatement) f.connection.createStatement();
            assertThatThrownBy(statement::execute).hasMessage("CONTROL_EFFECT_UNCLASSIFIED");
            assertThatThrownBy(statement::addBatch).hasMessage("CONTROL_EFFECT_UNCLASSIFIED");
            assertThatThrownBy(statement::executeBatch).hasMessage("CONTROL_EFFECT_UNCLASSIFIED");
            verifyNoInteractions(raw);
        }
    }

    @Test void s20uUnknownIsRefusedEvenWhenAuthorityWouldApprove() throws Exception {
        try (var f = new EffectUnitFixture(true)) {
            var raw = mock(Statement.class);
            when(f.physical.createStatement()).thenReturn(raw);
            assertThatThrownBy(() -> f.connection.createStatement().execute("SET SESSION sql_mode='ANSI'"))
                .hasMessage("CONTROL_EFFECT_UNCLASSIFIED");
            verifyNoInteractions(raw);
            assertThat(f.authority.checks).isZero();
        }
    }

    @Test void s19FailedPartialExecutionInvalidatesPriorCleanupProof() throws Exception {
        try (var f = new EffectUnitFixture(true)) {
            var raw = mock(Statement.class);
            when(f.physical.createStatement()).thenReturn(raw);
            doThrow(new SQLException("partial", "23000")).when(raw).executeBatch();
            f.connection.commit();
            var statement = f.connection.createStatement();
            statement.addBatch("INSERT INTO t VALUES(1)");
            assertThatThrownBy(statement::executeBatch).hasMessage("partial");
            f.deny();
            assertThatThrownBy(() -> f.connection.setAutoCommit(true)).hasMessage("RECOVERY_LEASE_OUTSTANDING");
            verify(f.physical, never()).setAutoCommit(true);
        }
    }

    @Test void failedBatchRequiresConfirmedClearBeforeReuse() throws Exception {
        try (var f = new EffectUnitFixture(true)) {
            var raw = mock(Statement.class);
            when(f.physical.createStatement()).thenReturn(raw);
            doThrow(new SQLException("partial", "23000")).when(raw).executeBatch();
            var statement = f.connection.createStatement();
            statement.addBatch("INSERT INTO t VALUES(1)");
            assertThatThrownBy(statement::executeBatch).hasMessage("partial");
            assertThatThrownBy(statement::executeLargeBatch).hasMessage("CONTROL_EFFECT_UNCLASSIFIED");
            verify(raw, never()).executeLargeBatch();
            statement.clearBatch(); statement.executeLargeBatch();
            verify(raw).executeLargeBatch();
        }
    }

    @Test void unknownBatchItemNeverReachesDelegate() throws Exception {
        try (var f = new EffectUnitFixture(true)) {
            var raw = mock(Statement.class);
            when(f.physical.createStatement()).thenReturn(raw);
            var statement = f.connection.createStatement();
            assertThatThrownBy(() -> statement.addBatch("/* comment */ INSERT INTO t VALUES(1)"))
                .hasMessage("CONTROL_EFFECT_UNCLASSIFIED");
            verifyNoInteractions(raw);
        }
    }

    @Test void explicitNullSqlCannotFallBackToPreparedBinding() throws Exception {
        try (var f = new EffectUnitFixture(true)) {
            var raw = mock(PreparedStatement.class);
            when(f.physical.prepareStatement("SELECT 1")).thenReturn(raw);
            var statement = f.connection.prepareStatement("SELECT 1");
            assertThatThrownBy(() -> statement.execute((String) null)).hasMessage("CONTROL_EFFECT_UNCLASSIFIED");
            verifyNoInteractions(raw);
        }
    }
}
