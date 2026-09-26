package com.acrylic.outbound.recovery;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RecoveryAdmittedDataSourceTest {
    @Test void staleAuthorityIsRejectedBeforeBorrowingConnection() throws Exception {
        var fixture = RecoveryWriterLeaseTest.protocol();
        var source = mock(DataSource.class);
        var adapter = adapter(source, fixture);
        fixture.append(fixture.next("RECOVERING"));

        assertThatThrownBy(adapter::getConnection).hasMessage("CONTROL_INSTANCE_STALE");
        verifyNoInteractions(source);
    }

    @Test void successfulConnectionCloseReleasesOnlyTheSharedWriterLease() throws Exception {
        var fixture = RecoveryWriterLeaseTest.protocol();
        var source = mock(DataSource.class); var connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        var adapter = adapter(source, fixture);

        try (Connection admitted = adapter.getConnection()) {
            assertThat(admitted.isWrapperFor(Connection.class)).isTrue();
            assertThatThrownBy(() -> admitted.unwrap(DataSource.class))
                .hasMessage("CONTROL_CONNECTION_UNWRAP_FORBIDDEN");
        }
        try (Connection reopened = adapter.getConnection()) { assertThat(reopened).isNotNull(); }
        verify(connection, times(2)).close();
    }

    @Test void commitAcknowledgementFailureKeepsWriterFenceAfterClose() throws Exception {
        var fixture = RecoveryWriterLeaseTest.protocol();
        var source = mock(DataSource.class); var connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        doThrow(new SQLException("lost", "08007")).when(connection).commit();
        var adapter = adapter(source, fixture);
        Connection admitted = adapter.getConnection();

        assertThatThrownBy(admitted::commit).hasMessage("lost");
        admitted.close();
        assertThatThrownBy(adapter::getConnection).hasMessage("CONTROL_WRITERS_ACTIVE");
    }

    @Test void closingAnUnfinishedTransactionRollsBackBeforeReleasingWriterFence() throws Exception {
        var fixture = RecoveryWriterLeaseTest.protocol();
        var source = mock(DataSource.class); var connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        var adapter = adapter(source, fixture);
        Connection admitted = adapter.getConnection();

        admitted.setAutoCommit(false); admitted.close();
        verify(connection).rollback();
        try (Connection reopened = adapter.getConnection()) { assertThat(reopened).isNotNull(); }
    }

    @Test void abortKeepsWriterFenceEvenWhenTheDriverReportsSuccess() throws Exception {
        var fixture = RecoveryWriterLeaseTest.protocol();
        var source = mock(DataSource.class); var connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        var adapter = adapter(source, fixture);
        Connection admitted = adapter.getConnection();

        admitted.abort(command -> { });
        admitted.close();
        assertThatThrownBy(adapter::getConnection).hasMessage("CONTROL_WRITERS_ACTIVE");
    }

    @Test void connectionCloseFailurePoisonsTheWriterFence() throws Exception {
        var fixture = RecoveryWriterLeaseTest.protocol();
        var source = mock(DataSource.class); var connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        doThrow(new SQLException("close lost", "08007")).when(connection).close();
        var adapter = adapter(source, fixture);
        Connection admitted = adapter.getConnection();

        assertThatThrownBy(admitted::close).hasMessage("close lost");
        assertThatThrownBy(adapter::getConnection).hasMessage("CONTROL_WRITERS_ACTIVE");
    }

    @Test void jdbcChildrenKeepReverseReferencesInsideTheAdmissionBoundary() throws Exception {
        var fixture = RecoveryWriterLeaseTest.protocol();
        var source = mock(DataSource.class); var connection = mock(Connection.class);
        var statement = mock(Statement.class); var result = mock(ResultSet.class); var metadata = mock(ResultSetMetaData.class);
        var databaseMetadata = mock(java.sql.DatabaseMetaData.class);
        when(source.getConnection()).thenReturn(connection); when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT 1")).thenReturn(result); when(result.getStatement()).thenReturn(statement);
        when(statement.getConnection()).thenReturn(connection); when(result.getMetaData()).thenReturn(metadata);
        when(connection.getMetaData()).thenReturn(databaseMetadata); when(databaseMetadata.getConnection()).thenReturn(connection);
        var adapter = adapter(source, fixture);

        try (Connection admitted = adapter.getConnection()) {
            Statement guarded = admitted.createStatement();
            ResultSet guardedResult = guarded.executeQuery("SELECT 1");
            assertThat(guarded.getConnection()).isSameAs(admitted);
            assertThat(guardedResult.getStatement()).isNotSameAs(statement);
            assertThat(admitted.getMetaData().getConnection()).isSameAs(admitted);
            assertThat(guarded.unwrap(Statement.class)).isSameAs(guarded);
            assertThatThrownBy(() -> guarded.unwrap(DataSource.class)).hasMessage("CONTROL_JDBC_OBJECT_UNWRAP_FORBIDDEN");
        }
    }

    @Test void knownSqlFailureStillRollsBackManualTransactionOnClose() throws Exception {
        var fixture = RecoveryWriterLeaseTest.protocol();
        var source = mock(DataSource.class); var connection = mock(Connection.class); var statement = mock(Statement.class);
        when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(false);
        when(connection.createStatement()).thenReturn(statement);
        doThrow(new SQLException("bad SQL", "42000")).when(statement).execute("bad SQL");
        var adapter = adapter(source, fixture);
        try (Connection admitted = adapter.getConnection()) {
            Statement guarded = admitted.createStatement();
            assertThatThrownBy(() -> guarded.execute("bad SQL")).hasMessage("bad SQL");
        }
        verify(connection).rollback();
        try (Connection reopened = adapter.getConnection()) { assertThat(reopened).isNotNull(); }
    }

    @Test void transactionStartedByRawSqlIsRolledBackOnClose() throws Exception {
        var fixture = RecoveryWriterLeaseTest.protocol();
        var source = mock(DataSource.class); var connection = mock(Connection.class); var statement = mock(Statement.class);
        when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true);
        when(connection.createStatement()).thenReturn(statement);
        var adapter = adapter(source, fixture);
        try (Connection admitted = adapter.getConnection()) {
            admitted.createStatement().execute("START TRANSACTION");
        }
        verify(connection).rollback();
    }

    @Test void physicalBorrowFailureReleasesTheWriterLease() throws Exception {
        var fixture = RecoveryWriterLeaseTest.protocol();
        var source = mock(DataSource.class); var connection = mock(Connection.class);
        when(source.getConnection()).thenThrow(new SQLException("borrow failed")).thenReturn(connection);
        var adapter = adapter(source, fixture);
        assertThatThrownBy(adapter::getConnection).hasMessage("borrow failed");
        try (Connection reopened = adapter.getConnection()) { assertThat(reopened).isNotNull(); }
    }

    @Test void connectionIdentityMethodsDoNotReenterTheProxy() throws Exception {
        var fixture = RecoveryWriterLeaseTest.protocol();
        var source = mock(DataSource.class); var connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        var adapter = adapter(source, fixture);

        try (Connection admitted = adapter.getConnection()) {
            assertThat(admitted).isEqualTo(admitted);
            assertThat(admitted).isNotEqualTo(connection);
            assertThat(admitted.hashCode()).isNotZero();
            assertThat(admitted.toString()).isEqualTo("RecoveryConnectionProxy");
        }
    }

    @Test void isClosedFailurePoisonsTheWriterFence() throws Exception {
        var fixture = RecoveryWriterLeaseTest.protocol();
        var source = mock(DataSource.class); var connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        doThrow(new SQLException("state lost", "08006")).when(connection).isClosed();
        var adapter = adapter(source, fixture);
        Connection admitted = adapter.getConnection();

        assertThatThrownBy(admitted::isClosed).hasMessage("state lost");
        admitted.close();
        assertThatThrownBy(adapter::getConnection).hasMessage("CONTROL_WRITERS_ACTIVE");
    }

    @Test void probeForcedOverlapKeepsAConcurrentCloseOutOfTheInFlightDelegateCall() throws Exception {
        var fixture = RecoveryWriterLeaseTest.protocol();
        var source = mock(DataSource.class); var connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true);
        var adapter = adapter(source, fixture);

        var gate = new ProbeGate();
        Connection admitted = adapter.admit(gate);
        CountDownLatch finished = new CountDownLatch(1);
        var writerOutcome = new AtomicReference<String>();
        var executor = Executors.newFixedThreadPool(2, runnable -> new Thread(runnable, "writer"));
        try {
            Future<?> writer = executor.submit(() -> {
                try { admitted.setReadOnly(true); } catch (SQLException error) { writerOutcome.set(error.getMessage()); }
                finished.countDown();
            });
            assertThat(gate.writerStarted.await(30, TimeUnit.SECONDS)).isTrue();
            Future<?> closer = gate.submitCloser(executor, admitted);
            assertThat(gate.closerReached.await(30, TimeUnit.SECONDS)).isTrue();
            // The closer now attempts its whole close while the writer is pinned at the probe. A
            // serialized boundary keeps that close waiting, so the writer's own delegate call still
            // completes; a boundary that admits the close concurrently poisons that call instead.
            assertThat(gate.closeFinished.await(2, TimeUnit.SECONDS))
                .as("a serialized boundary must not let close finish inside an in-flight delegate call")
                .isFalse();
            gate.releaseWriter.countDown();
            assertThat(finished.await(30, TimeUnit.SECONDS)).isTrue();
            closer.get(30, TimeUnit.SECONDS);
            writer.get(30, TimeUnit.SECONDS);
        } finally {
            gate.releaseWriter.countDown();
            executor.shutdownNow();
        }

        assertThat(writerOutcome.get())
            .as("the in-flight delegate call must not be poisoned by a concurrent close")
            .isNull();
        assertThat(gate.closeFinished.getCount()).isZero();
        assertThat(finished.getCount()).isZero();
        assertThat(admitted.isClosed()).isTrue();
    }

    /** Forces the overlap order: the writer blocks inside the boundary while the closer runs. */
    private static final class ProbeGate implements RecoveryAdmissionProbe {
        private final CountDownLatch writerStarted = new CountDownLatch(1);
        private final CountDownLatch closerReached = new CountDownLatch(1);
        private final CountDownLatch releaseWriter = new CountDownLatch(1);
        private final CountDownLatch closeFinished = new CountDownLatch(1);

        @Override public void aroundDelegateCall() {
            if (!Thread.currentThread().getName().equals("writer")) return;
            writerStarted.countDown();
            await(releaseWriter);
        }

        private void await(CountDownLatch latch) {
            try {
                if (!latch.await(30, TimeUnit.SECONDS)) throw new AssertionError("probe wait timed out");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
        }

        Future<?> submitCloser(ExecutorService executor, Connection admitted) {
            return executor.submit(() -> {
                closerReached.countDown();
                try { admitted.close(); } catch (SQLException error) { throw new IllegalStateException(error); }
                closeFinished.countDown();
            });
        }
    }

    @Test void concurrentStatementExecuteIsAccountedForBeforeCloseReturns() throws Exception {
        var fixture = RecoveryWriterLeaseTest.protocol();
        var source = mock(DataSource.class); var connection = mock(Connection.class); var statement = mock(Statement.class);
        when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true);
        when(connection.createStatement()).thenReturn(statement);
        var adapter = adapter(source, fixture);
        Connection admitted = adapter.getConnection();
        Connection reopened = adapter.getConnection();

        CountDownLatch executing = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        CountDownLatch releaseExecution = new CountDownLatch(1);
        CountDownLatch closingStarted = new CountDownLatch(1);
        AtomicBoolean closeReturnedWhileExecuting = new AtomicBoolean();
        // Real statement path: the execution thread pins the delegate statement call and the closing
        // thread is released only afterwards. Asserted contract of that overlap: close does not
        // return while the admitted execution is still in flight, and the shared writer lease of the
        // second admitted connection keeps the fence closed for new admissions.
        // Limit: the scheduler cannot be forced to pre-empt the connection monitor from outside, so
        // this asserts the correct behaviour deterministically but does not itself prove that a
        // de-serialized adapter would fail; see the batch notes for that explicit gap.
        doAnswer(call -> {
            executing.countDown();
            releaseExecution.await(30, TimeUnit.SECONDS);
            finished.countDown();
            return true;
        }).when(statement).execute("UPDATE t SET v = 1");
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> execution = executor.submit(() -> {
                try {
                    Statement guarded = admitted.createStatement();
                    guarded.execute("UPDATE t SET v = 1");
                } catch (SQLException error) { throw new IllegalStateException(error); }
            });
            assertThat(executing.await(30, TimeUnit.SECONDS)).isTrue();
            Future<?> closing = executor.submit(() -> {
                try {
                    closingStarted.countDown();
                    admitted.close();
                } catch (SQLException error) { throw new IllegalStateException(error); }
                if (finished.getCount() != 0) closeReturnedWhileExecuting.set(true);
            });
            assertThat(closingStarted.await(30, TimeUnit.SECONDS)).isTrue();
            releaseExecution.countDown();
            execution.get(30, TimeUnit.SECONDS);
            closing.get(30, TimeUnit.SECONDS);
        } finally {
            releaseExecution.countDown();
            executor.shutdownNow();
        }

        assertThat(closeReturnedWhileExecuting)
            .as("close must not return while an admitted statement execution is still running")
            .isFalse();
        assertThat(admitted.isClosed()).isTrue();
        try (Connection stillAdmitted = adapter.getConnection()) { assertThat(stillAdmitted).isNotNull(); }
        reopened.close();
        try (Connection after = adapter.getConnection()) { assertThat(after).isNotNull(); }
    }

    @Test void concurrentAmbiguousExecutionPoisonsOnceAndCloseCannotReleaseTheFence() throws Exception {
        var fixture = RecoveryWriterLeaseTest.protocol();
        var source = mock(DataSource.class); var connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true);
        var adapter = adapter(source, fixture);
        Connection admitted = adapter.getConnection();

        CountDownLatch failing = new CountDownLatch(1);
        CountDownLatch releaseFailure = new CountDownLatch(1);
        doAnswer(call -> { failing.countDown(); releaseFailure.await(30, TimeUnit.SECONDS); throw new SQLException("link broke", "08006"); })
            .when(connection).setReadOnly(true);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> execution = executor.submit(() -> {
                try { admitted.setReadOnly(true); } catch (SQLException error) { return error.getMessage(); }
                return "admitted";
            });
            assertThat(failing.await(30, TimeUnit.SECONDS)).isTrue();
            Future<?> closing = executor.submit(() -> {
                try { admitted.close(); } catch (SQLException error) { throw new IllegalStateException(error); }
            });
            releaseFailure.countDown();
            assertThat(execution.get(30, TimeUnit.SECONDS)).isEqualTo("link broke");
            closing.get(30, TimeUnit.SECONDS);
        } finally {
            releaseFailure.countDown();
            executor.shutdownNow();
        }

        admitted.close();
        admitted.close();
        assertThatThrownBy(adapter::getConnection).hasMessage("CONTROL_WRITERS_ACTIVE");
    }

    @Test void statementExecutionInsideTheBoundaryForcesCloseToObserveTheActiveTransaction() throws Exception {
        var fixture = RecoveryWriterLeaseTest.protocol();
        var source = mock(DataSource.class); var connection = mock(Connection.class); var statement = mock(Statement.class);
        when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true);
        when(connection.createStatement()).thenReturn(statement);
        var adapter = adapter(source, fixture);
        var gate = new ProbeGate();
        Connection admitted = adapter.admit(gate);
        Statement guarded = admitted.createStatement();

        var writer = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "writer"));
        var closer = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "closer"));
        try {
            var outcome = new AtomicReference<Object>();
            Future<?> execution = writer.submit(() -> {
                try { outcome.set(guarded.execute("START TRANSACTION")); }
                catch (SQLException error) { outcome.set(error); }
            });
            assertThat(gate.writerStarted.await(30, TimeUnit.SECONDS)).isTrue();
            Future<?> closing = closer.submit(() -> {
                gate.closerReached.countDown();
                try { admitted.close(); } catch (SQLException error) { throw new IllegalStateException(error); }
                gate.closeFinished.countDown();
            });
            assertThat(gate.closerReached.await(30, TimeUnit.SECONDS)).isTrue();
            // The execution is pinned before its delegate call, so the transaction the statement is
            // about to open is not recorded yet. Close must still wait for the execution.
            assertThat(gate.closeFinished.await(2, TimeUnit.SECONDS))
                .as("close must not pass an admitted statement execution on the same connection")
                .isFalse();
            gate.releaseWriter.countDown();
            execution.get(30, TimeUnit.SECONDS);
            closing.get(30, TimeUnit.SECONDS);
            assertThat(outcome.get()).isEqualTo(false);
        } finally {
            gate.releaseWriter.countDown();
            writer.shutdownNow();
            closer.shutdownNow();
        }

        // The close decision therefore saw the transaction started by the raw statement path.
        verify(connection).rollback();
        assertThat(admitted.isClosed()).isTrue();
        try (Connection reopened = adapter.getConnection()) { assertThat(reopened).isNotNull(); }
    }

    @Test void ambiguousStatementExecutionInsideTheBoundaryPoisonsBeforeCloseDecides() throws Exception {
        var fixture = RecoveryWriterLeaseTest.protocol();
        var source = mock(DataSource.class); var connection = mock(Connection.class); var statement = mock(Statement.class);
        when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true);
        when(connection.createStatement()).thenReturn(statement);
        doThrow(new SQLException("link broke", "08006")).when(statement).execute("UPDATE t SET v = 1");
        var adapter = adapter(source, fixture);
        var gate = new ProbeGate();
        Connection admitted = adapter.admit(gate);
        Statement guarded = admitted.createStatement();

        var writer = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "writer"));
        var closer = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "closer"));
        try {
            var outcome = new AtomicReference<Object>();
            Future<?> execution = writer.submit(() -> {
                try { outcome.set(guarded.execute("UPDATE t SET v = 1")); }
                catch (SQLException error) { outcome.set(error); }
            });
            assertThat(gate.writerStarted.await(30, TimeUnit.SECONDS)).isTrue();
            Future<?> closing = closer.submit(() -> {
                gate.closerReached.countDown();
                try { admitted.close(); } catch (SQLException error) { throw new IllegalStateException(error); }
                gate.closeFinished.countDown();
            });
            assertThat(gate.closerReached.await(30, TimeUnit.SECONDS)).isTrue();
            assertThat(gate.closeFinished.await(2, TimeUnit.SECONDS))
                .as("close must not pass an admitted statement execution on the same connection")
                .isFalse();
            gate.releaseWriter.countDown();
            execution.get(30, TimeUnit.SECONDS);
            closing.get(30, TimeUnit.SECONDS);
            assertThat(outcome.get()).isInstanceOfSatisfying(SQLException.class,
                (SQLException error) -> assertThat(error.getMessage()).isEqualTo("link broke"));
        } finally {
            gate.releaseWriter.countDown();
            writer.shutdownNow();
            closer.shutdownNow();
        }

        assertThatThrownBy(adapter::getConnection).hasMessage("CONTROL_WRITERS_ACTIVE");
        assertThatThrownBy(guarded::getConnection).hasMessage("CONTROL_CONNECTION_CLOSED");
    }

    private static RecoveryAdmittedDataSource adapter(DataSource source, RecoveryControlFixture fixture) {
        return new RecoveryAdmittedDataSource(source,
            new RecoveryControlLocation(fixture.root, fixture.installation, List.of(fixture.restoreRoot)), fixture.checkpoint);
    }
}
