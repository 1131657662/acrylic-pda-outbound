package com.acrylic.outbound.recovery;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real local writer/root protocol, MODEL JDBC and effect authority; no database or P5 proof. */
class RecoveryConnectionUncheckedCloseTest {
    static Stream<Throwable> failures() {
        return Stream.of(new SQLException("driver outcome unknown"),
            new IllegalStateException("driver outcome unknown"), new AssertionError("driver outcome unknown"));
    }

    @ParameterizedTest @MethodSource("failures")
    void closeFailurePoisonsTheRootBeforeAnotherBorrow(Throwable failure) throws Exception {
        try (var f = fixture(true)) {
            doThrow(failure).when(f.physical).close();
            Connection borrowed = f.adapter.getConnection();

            assertThat(catchThrowable(borrowed::close)).isSameAs(failure);

            f.assertBorrowRefused();
            assertThat(borrowed.isClosed()).as("the proxy is terminal even when physical close is unknown").isTrue();
            borrowed.close();
            verify(f.physical, times(1)).close();
        }
    }

    @ParameterizedTest @MethodSource("failures")
    void rollbackFailureStillAttemptsPhysicalClose(Throwable failure) throws Exception {
        try (var f = fixture(false)) {
            doThrow(failure).when(f.physical).rollback();
            Connection borrowed = f.adapter.getConnection();

            assertThat(catchThrowable(borrowed::close)).isSameAs(failure);

            verify(f.physical).close();
            f.assertBorrowRefused();
        }
    }

    @ParameterizedTest @MethodSource("failures")
    void aSecondCloseFailureIsSuppressedOnTheOriginalRollbackFailure(Throwable failure) throws Exception {
        try (var f = fixture(false)) {
            var closeFailure = new SQLException("physical close also unknown");
            doThrow(failure).when(f.physical).rollback();
            doThrow(closeFailure).when(f.physical).close();
            Connection borrowed = f.adapter.getConnection();

            assertThat(catchThrowable(borrowed::close)).isSameAs(failure);

            assertThat(failure.getSuppressed()).containsExactly(closeFailure);
            f.assertBorrowRefused();
        }
    }

    @ParameterizedTest @MethodSource("failures")
    void theSameDriverFailureIsNeverSuppressedOnItself(Throwable failure) throws Exception {
        try (var f = fixture(false)) {
            doThrow(failure).when(f.physical).rollback();
            doThrow(failure).when(f.physical).close();
            Connection borrowed = f.adapter.getConnection();

            assertThat(catchThrowable(borrowed::close)).isSameAs(failure);

            assertThat(failure.getSuppressed()).isEmpty();
            verify(f.physical).close();
            f.assertBorrowRefused();
        }
    }

    @Test void confirmedCloseKeepsTheRootUsableUntilItsOwnerRetiresIt() throws Exception {
        try (var f = fixture(true)) {
            try (Connection first = f.adapter.getConnection()) { assertThat(first).isNotNull(); }
            try (Connection second = f.adapter.getConnection()) { assertThat(second).isNotNull(); }

            assertThat(f.root.openSubOperations()).isZero();
            assertThat(f.authority.unknownFences).isZero();
            assertThat(f.root.phase()).isEqualTo(RecoveryOperationPhase.SERVING);
        }
    }

    private static Fixture fixture(boolean autoCommit) throws Exception {
        var control = RecoveryWriterLeaseTest.protocol();
        var registry = new RecoveryOperationRegistry(control.location(), control.checkpoint);
        var source = mock(DataSource.class);
        var physical = mock(Connection.class);
        when(physical.getAutoCommit()).thenReturn(autoCommit);
        when(source.getConnection()).thenReturn(physical);
        var authority = new EffectTestAuthority();
        var adapter = new RecoveryRequestAdmittedDataSource(source, registry, RecoveryEffectGate.enabled(authority));
        var token = new RecoveryOperationToken(UUID.randomUUID(), RecoveryOperationKind.HTTP_REQUEST,
            control.installation, registry.startup().recordSha256(), null);
        var root = RecoveryControlLock.locked(control.root, () -> registry.register(token));
        root.bindDispatch(Thread.currentThread());
        return new Fixture(source, physical, authority, adapter, root);
    }

    private record Fixture(DataSource source, Connection physical, EffectTestAuthority authority,
            RecoveryRequestAdmittedDataSource adapter, RecoveryOperationLease root) implements AutoCloseable {
        void assertBorrowRefused() throws Exception {
            assertThatThrownBy(adapter::getConnection).hasMessage("CONTROL_WRITERS_ACTIVE");
            verify(source, times(1)).getConnection();
            assertThat(authority.unknownFences).isPositive();
        }

        @Override public void close() {
            root.unbindAll();
            try { root.close(); }
            catch (RecoveryControlException unconfirmed) {
                if (!unconfirmed.code().equals("CONTROL_OPERATION_RETIREMENT_UNCONFIRMED")) throw unconfirmed;
                assertThat(root.phase()).isEqualTo(RecoveryOperationPhase.FENCED);
            }
        }
    }
}
