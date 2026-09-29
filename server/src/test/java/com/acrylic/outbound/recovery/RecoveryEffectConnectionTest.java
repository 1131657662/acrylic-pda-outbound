package com.acrylic.outbound.recovery;

import java.sql.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecoveryEffectConnectionTest {
    @Test void s19ImplicitCommitIsDeniedBeforeDelegate() throws Exception {
        try (var f = new EffectUnitFixture(true)) {
            f.connection.setAutoCommit(false);
            f.deny();
            assertThatThrownBy(() -> f.connection.setAutoCommit(true)).hasMessage("RECOVERY_LEASE_OUTSTANDING");
            verify(f.physical, never()).setAutoCommit(true);
            assertThat(f.authority.checks).isEqualTo(1);
        }
    }

    @Test void s19ConfirmedCommitAllowsCleanupAfterFence() throws Exception {
        try (var f = new EffectUnitFixture(true)) {
            f.connection.setAutoCommit(false);
            f.connection.commit();
            f.gate.fence();
            f.connection.setAutoCommit(true);
            verify(f.physical).setAutoCommit(true);
            assertThat(f.authority.checks).isEqualTo(1);
        }
    }

    @Test void s19RollbackAndSavepointCleanupNeverNeedAuthority() throws Exception {
        try (var f = new EffectUnitFixture(true)) {
            f.connection.setAutoCommit(false);
            f.gate.fence();
            f.connection.rollback();
            f.connection.setAutoCommit(true);
            f.connection.setSavepoint();
            f.connection.releaseSavepoint(mock(Savepoint.class));
            assertThat(f.authority.checks).isZero();
        }
    }

    @Test void s17ExplicitCommitAlwaysChecksEvenAfterEarlierCommit() throws Exception {
        try (var f = new EffectUnitFixture(true)) {
            f.connection.commit();
            f.deny();
            assertThatThrownBy(f.connection::commit).hasMessage("RECOVERY_LEASE_OUTSTANDING");
            verify(f.physical, times(1)).commit();
        }
    }

    @Test void s21PreviouslyCreatedStatementSeesSharedMemoryFence() throws Exception {
        try (var f = new EffectUnitFixture(true)) {
            var raw = mock(Statement.class);
            when(f.physical.createStatement()).thenReturn(raw);
            var statement = f.connection.createStatement();
            f.gate.fence();
            assertThatThrownBy(() -> statement.executeUpdate("INSERT INTO t VALUES(1)"))
                .hasMessage("RECOVERY_LEASE_FENCED");
            verifyNoInteractions(raw);
        }
    }

    @Test void defaultOffPreservesLegacyUnknownAndImplicitCommitDelegation() throws Exception {
        try (var f = new EffectUnitFixture(false)) {
            var raw = mock(Statement.class);
            when(f.physical.createStatement()).thenReturn(raw);
            f.deny(); f.gate.fence();
            f.connection.createStatement().execute("/* hint */ CALL p()");
            f.connection.setAutoCommit(false);
            f.connection.setAutoCommit(true);
            f.connection.commit();
            verify(raw).execute("/* hint */ CALL p()");
            verify(f.physical).setAutoCommit(true);
            verify(f.physical).commit();
            assertThat(f.authority.checks).isZero();
        }
    }

    @Test void failedNewConnectionInitializationFencesPreviouslyBorrowedStatement() throws Exception {
        try (var f = new EffectUnitFixture(true)) {
            var raw = mock(Statement.class);
            when(f.physical.createStatement()).thenReturn(raw);
            var statement = f.connection.createStatement();
            when(f.physical.getAutoCommit()).thenThrow(new SQLException("state unavailable", "08006"));
            assertThatThrownBy(f.adapter::getConnection).hasMessage("state unavailable");
            assertThatThrownBy(() -> statement.executeUpdate("INSERT INTO t VALUES(1)"))
                .hasMessage("RECOVERY_LEASE_FENCED");
            verifyNoInteractions(raw);
        }
    }
}
