package com.acrylic.outbound.recovery;

import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecoveryLeasedTransactionTest {
    @Test void changedAuthorityRejectsBeforeOpeningDatabaseOrExecutingCallback() throws Exception {
        var fixture = RecoveryWriterLeaseTest.protocol();
        var dataSource = mock(DataSource.class);
        var transaction = create(dataSource, fixture);
        fixture.append(fixture.next("RECOVERING"));

        assertThatThrownBy(() -> transaction.execute(status -> { throw new AssertionError("Callback ran"); }))
            .hasMessage("CONTROL_INSTANCE_STALE");
        verifyNoInteractions(dataSource);
    }

    @Test void existingTransactionScopeCannotInvertCoordinatorDatabaseLockOrder() throws Exception {
        var fixture = RecoveryWriterLeaseTest.protocol();
        var dataSource = mock(DataSource.class);
        var transaction = create(dataSource, fixture);
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThatThrownBy(() -> transaction.execute(status -> { throw new AssertionError("Callback ran"); }))
                .hasMessage("CONTROL_OUTER_TRANSACTION_FORBIDDEN");
            verifyNoInteractions(dataSource);
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }

    @Test void missingWriterProtocolDoesNotStartDatabaseTransaction() throws Exception {
        var fixture = RecoveryControlFixture.create();
        var dataSource = mock(DataSource.class);
        var transaction = create(dataSource, fixture);

        assertThatThrownBy(() -> transaction.execute(status -> { throw new AssertionError("Callback ran"); }))
            .hasMessage("CONTROL_WRITER_PROTOCOL_REQUIRED");
        verifyNoInteractions(dataSource);
    }

    @Test void ambiguousCommitRetainsLeaseAndPoisonsSubsequentTransactions() throws Exception {
        var fixture = RecoveryWriterLeaseTest.protocol();
        var source = mock(DataSource.class);
        var connection = mock(java.sql.Connection.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        doThrow(new java.sql.SQLException("lost acknowledgement", "08007")).when(connection).commit();
        var transaction = create(source, fixture);

        assertThatThrownBy(() -> transaction.execute(status -> null))
            .isInstanceOf(org.springframework.transaction.TransactionSystemException.class)
            .satisfies(error -> assertThat(error.getSuppressed()).anyMatch(
                suppressed -> "CONTROL_TRANSACTION_OUTCOME_UNCONFIRMED".equals(suppressed.getMessage())));
        assertThatThrownBy(() -> transaction.execute(status -> { throw new AssertionError("Callback ran"); }))
            .hasMessage("CONTROL_TRANSACTION_OUTCOME_UNCONFIRMED");
        verify(source, times(1)).getConnection();
        verify(connection, never()).rollback();
    }

    @Test void independentNestedTransactionIsRefusedBeforeOpeningSecondConnection() throws Exception {
        var fixture = RecoveryWriterLeaseTest.protocol();
        var source = mock(DataSource.class); var connection = mock(java.sql.Connection.class);
        when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true);
        var transaction = create(source, fixture);
        var nested = new org.springframework.transaction.support.TransactionTemplate(
            new org.springframework.jdbc.datasource.DataSourceTransactionManager(source));
        nested.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> transaction.execute(status -> nested.execute(inner -> {
            throw new AssertionError("Nested callback ran");
        }))).hasMessage("CONTROL_NESTED_TRANSACTION_FORBIDDEN");
        verify(source, times(1)).getConnection(); verify(connection).rollback();
    }

    private RecoveryLeasedTransaction create(DataSource dataSource, RecoveryControlFixture fixture) {
        return new RecoveryLeasedTransaction(dataSource,
            new RecoveryControlLocation(fixture.root, fixture.installation, List.of(fixture.restoreRoot)), fixture.checkpoint);
    }
}
