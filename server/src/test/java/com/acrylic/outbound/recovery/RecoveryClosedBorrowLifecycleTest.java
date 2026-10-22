package com.acrylic.outbound.recovery;

import java.sql.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecoveryClosedBorrowLifecycleTest {
    @Test void admissionAndBusinessUseTheSameSingleBorrow() throws Exception {
        try (var f = new ClosedBorrowLifecycleFixture()) {
            var statement = mock(Statement.class);
            when(f.physical.createStatement()).thenReturn(statement);
            var checks = new AtomicInteger();
            var adapter = f.adapter(physical -> {
                assertThat(physical).isSameAs(f.physical);
                checks.incrementAndGet();
            });

            try (Connection borrowed = adapter.getConnection()) {
                borrowed.createStatement().executeQuery("SELECT 1");
                assertThat(f.root.openSubOperations()).isEqualTo(1);
                verify(f.physical, never()).close();
            }

            assertThat(checks.get()).isEqualTo(1);
            verify(f.source, times(1)).getConnection();
            verify(statement).executeQuery("SELECT 1");
            verify(f.physical).close();
            assertThat(f.root.openSubOperations()).isZero();
        }
    }

    @ParameterizedTest @MethodSource("failures")
    void confirmedRejectionClosesOnlyItsBorrowAndAllowsTheNext(Throwable denial) throws Exception {
        var f = new ClosedBorrowLifecycleFixture();
        try (f) {
            var checks = new AtomicInteger();
            var adapter = f.adapter(physical -> {
                if (checks.getAndIncrement() == 0) failAdmission(denial);
            });

            assertThatThrownBy(adapter::getConnection).isSameAs(denial);

            verify(f.physical).close();
            verify(f.physical, never()).getAutoCommit();
            assertThat(f.root.openSubOperations()).isZero();
            assertThat(f.root.phase()).isEqualTo(RecoveryOperationPhase.SERVING);
            try (Connection accepted = adapter.getConnection()) { assertThat(accepted).isNotNull(); }
            assertThat(f.root.openSubOperations()).isZero();
            assertThat(f.unknown).isZero();
        }
        try (var exclusive = f.exclusive()) { assertThat(exclusive.confirmed()).isTrue(); }
    }

    @ParameterizedTest @MethodSource("failures")
    void unconfirmedCloseRetainsWriterRootAndChild(Throwable closeFailure) throws Exception {
        var f = new ClosedBorrowLifecycleFixture();
        var denial = new SQLException("MODEL-admission-denied");
        doAnswer(invocation -> { throw closeFailure; }).when(f.physical).close();
        var adapter = f.adapter(physical -> { throw denial; });
        try {
            assertThatThrownBy(adapter::getConnection).isSameAs(denial);

            assertThat(denial.getSuppressed()).containsExactly(closeFailure);
            assertThat(f.root.openSubOperations()).isEqualTo(1);
            assertThat(f.root.phase()).isEqualTo(RecoveryOperationPhase.FENCED);
            assertThat(f.registry.liveRoots()).isEqualTo(1);
            assertWriterBlocked(f);
            assertThatThrownBy(adapter::getConnection).hasMessage("RECOVERY_LEASE_FENCED");
        } finally {
            assertThatThrownBy(f::close).hasMessage("CONTROL_OPERATION_RETIREMENT_UNCONFIRMED");
        }
        assertRetained(f);
    }

    @Test void oldEffectOnlyAdapterAlsoRetainsAnUnconfirmedConstructionFailure() throws Exception {
        var f = new ClosedBorrowLifecycleFixture();
        var construction = new IllegalStateException("MODEL-driver-state-failed");
        when(f.physical.getAutoCommit()).thenThrow(construction);
        doThrow(new SQLException("MODEL-close-unknown")).when(f.physical).close();
        var adapter = new RecoveryRequestAdmittedDataSource(f.source, f.registry, f.effects);

        assertThatThrownBy(adapter::getConnection).isSameAs(construction);

        assertThat(f.root.openSubOperations()).isEqualTo(1);
        assertThat(f.root.phase()).isEqualTo(RecoveryOperationPhase.FENCED);
        assertThatThrownBy(f::close).hasMessage("CONTROL_OPERATION_RETIREMENT_UNCONFIRMED");
        assertRetained(f);
    }

    private static void assertRetained(ClosedBorrowLifecycleFixture f) throws Exception {
        assertThat(f.unknown).isEqualTo(1);
        assertThat(f.root.openSubOperations()).isEqualTo(1);
        assertThat(f.registry.liveRoots()).isEqualTo(1);
        assertWriterBlocked(f);
        assertThatThrownBy(f.effects::check).hasMessage("RECOVERY_LEASE_FENCED");
        f.root.close();
        assertThat(f.unknown).isEqualTo(1);
        assertThat(f.root.openSubOperations()).isEqualTo(1);
        assertThat(f.registry.liveRoots()).isEqualTo(1);
    }

    private static void assertWriterBlocked(ClosedBorrowLifecycleFixture f) {
        assertThatThrownBy(() -> {
            try (var exclusive = f.exclusive()) { fail("uncertain writer must stay held"); }
        }).hasMessage("CONTROL_WRITERS_ACTIVE");
    }

    private static Stream<Throwable> failures() {
        return Stream.of(new SQLException("MODEL-sql-failure"),
            new IllegalStateException("MODEL-runtime-failure"), new AssertionError("MODEL-error-failure"));
    }

    private static void failAdmission(Throwable failure) throws SQLException {
        if (failure instanceof SQLException sql) throw sql;
        if (failure instanceof RuntimeException runtime) throw runtime;
        throw (Error) failure;
    }
}
