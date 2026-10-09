package com.acrylic.outbound.recovery;

import java.sql.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Lifecycle faults are synthetic; actual database projection/rollback is exercised by the isolated IT. */
class RecoveryClosedTransactionTest {
    @Test void controlAdvanceIsRejectedBeforeOpeningAnyDatabaseConnection() throws Exception {
        var source = RecoverySourceFixture.create(); RecoveryClosedTestSupply.writers(source.control);
        var authority = RecoveryClosedTestSupply.of(source).authority(); var database = mock(DataSource.class);
        var transaction = new RecoveryLeasedTransaction(database, authority);
        source.control.append(source.control.next("VERIFYING"));

        assertThatThrownBy(() -> transaction.execute(status -> { throw new AssertionError("Stale callback"); }))
            .hasMessage("CONTROL_CLOSED_HEAD_MISMATCH");
        verifyNoInteractions(database);
    }

    @Test void normalAuthorityCannotConstructAClosedRecoveryTransaction() throws Exception {
        var source = RecoverySourceFixture.create(); var supply = RecoveryClosedTestSupply.of(source);
        supply.identity.put("authorityKind", "INITIAL_NORMAL_INSTALL"); supply.publish();

        assertThatThrownBy(supply::authority).hasMessage("CONTROL_CLOSED_AUTHORITY_KIND_FORBIDDEN");
    }

    @Test void unknownCommitKeepsTheLeaseAndWritesARestartFence() throws Exception {
        var source = RecoverySourceFixture.create(); RecoveryClosedTestSupply.writers(source.control);
        var supply = RecoveryClosedTestSupply.of(source); var authority = supply.authority();
        var database = mock(DataSource.class); var connection = mock(Connection.class);
        when(database.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true);
        doThrow(new SQLException("lost commit response", "08007")).when(connection).commit();
        var transaction = new RecoveryLeasedTransaction(database, authority);

        assertThatThrownBy(() -> transaction.execute(status -> null))
            .isInstanceOf(org.springframework.transaction.TransactionSystemException.class);
        assertThatThrownBy(() -> transaction.execute(status -> null)).hasMessage("CONTROL_TRANSACTION_OUTCOME_UNCONFIRMED");
        assertThatThrownBy(supply::authority).hasMessage("CONTROL_TRANSACTION_OUTCOME_UNCONFIRMED");
        assertThatThrownBy(() -> RecoveryControlLock.locked(source.control.root,
            () -> RecoveryWriterLease.acquire(source.control.root))).hasMessage("CONTROL_WRITERS_ACTIVE");
        verify(database, times(1)).getConnection(); verify(connection, never()).rollback();
    }

    @Test void rollbackFailureAlsoFencesTheSessionWithoutRetryingDml() throws Exception {
        var source = RecoverySourceFixture.create(); RecoveryClosedTestSupply.writers(source.control);
        var supply = RecoveryClosedTestSupply.of(source); var database = mock(DataSource.class);
        var connection = mock(Connection.class); when(database.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        doThrow(new SQLException("lost rollback response", "08007")).when(connection).rollback();
        var transaction = new RecoveryLeasedTransaction(database, supply.authority());

        assertThatThrownBy(() -> transaction.execute(status -> { throw new IllegalStateException("DML failed"); }))
            .isInstanceOf(org.springframework.transaction.TransactionSystemException.class);
        assertThatThrownBy(supply::authority).hasMessage("CONTROL_TRANSACTION_OUTCOME_UNCONFIRMED");
        verify(database, times(1)).getConnection(); verify(connection, never()).commit();
    }

    @Test void outerTransactionCannotInvertTheControlDatabaseLockOrder() throws Exception {
        var source = RecoverySourceFixture.create(); RecoveryClosedTestSupply.writers(source.control);
        var database = mock(DataSource.class);
        var transaction = new RecoveryLeasedTransaction(database, RecoveryClosedTestSupply.of(source).authority());
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThatThrownBy(() -> transaction.execute(status -> null)).hasMessage("CONTROL_OUTER_TRANSACTION_FORBIDDEN");
            verifyNoInteractions(database);
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }

    @Test void failureToOpenTheDatabaseLeavesTheDurableStartUnconfirmed() throws Exception {
        var source = RecoverySourceFixture.create(); RecoveryClosedTestSupply.writers(source.control);
        var supply = RecoveryClosedTestSupply.of(source); var database = mock(DataSource.class);
        when(database.getConnection()).thenThrow(new SQLException("connection refused"));
        var transaction = new RecoveryLeasedTransaction(database, supply.authority());

        assertThatThrownBy(() -> transaction.execute(status -> { throw new AssertionError("No connection"); }))
            .isInstanceOf(org.springframework.transaction.CannotCreateTransactionException.class);
        assertThatThrownBy(supply::authority).hasMessage("CONTROL_TRANSACTION_OUTCOME_UNCONFIRMED");
        verify(database, times(1)).getConnection();
    }

    @Test void completionPublicationFailurePoisonsTheLeaseAfterAConfirmedDatabaseCommit() throws Exception {
        var source = RecoverySourceFixture.create(); RecoveryClosedTestSupply.writers(source.control);
        var supply = RecoveryClosedTestSupply.of(source); var database = mock(DataSource.class);
        var connection = mock(Connection.class); when(database.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        var transaction = new RecoveryLeasedTransaction(database, supply.authority());
        doAnswer(invocation -> {
            java.nio.file.Files.createDirectory(supply.root.resolve("transactions/000000000000000001.complete.json.pending"));
            return null;
        }).when(connection).commit();

        assertThatThrownBy(() -> transaction.execute(status -> null)).hasMessage("CONTROL_TRANSACTION_OUTCOME_UNCONFIRMED");
        assertThatThrownBy(supply::authority).hasMessage("CONTROL_TRANSACTION_OUTCOME_UNCONFIRMED");
        verify(connection, times(1)).commit(); verify(connection, never()).rollback();
    }
}
