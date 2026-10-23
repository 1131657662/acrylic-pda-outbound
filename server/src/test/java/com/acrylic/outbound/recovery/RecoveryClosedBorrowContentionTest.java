package com.acrylic.outbound.recovery;

import java.nio.file.Files;
import java.sql.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecoveryClosedBorrowContentionTest {
    @Test void closedBorrowWaitsForCoordinatorThenChecksThePhysicalDatabase() throws Exception {
        var fixture = prepared();
        var physical = mock(Connection.class);
        when(physical.createStatement()).thenThrow(new SQLException("MODEL-database-unavailable"));
        try (var held = new Held(fixture)) {
            var result = held.executor.submit(() -> refusal(fixture, physical));
            assertThatThrownBy(() -> result.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            held.release.countDown();
            assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo("RECOVERY_DATABASE_ADMISSION_REFUSED:DATABASE_EVIDENCE_UNAVAILABLE");
        }
        verify(physical).createStatement();
        verify(physical, never()).close();
    }

    @Test void exhaustedCoordinatorBudgetRefusesWithoutDatabaseAccess() throws Exception {
        var fixture = prepared(); var physical = mock(Connection.class);
        try (var held = new Held(fixture)) {
            long start = System.nanoTime();
            assertThat(refusal(fixture, physical)).isEqualTo("RECOVERY_DATABASE_ADMISSION_REFUSED:AUTHORITY_UNAVAILABLE");
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertThat(elapsed).isBetween(900L, 5000L);
        }
        verifyNoInteractions(physical);
    }

    @Test void queuedBorrowRechecksCorruptedAuthorityAfterAcquiringTheLock() throws Exception {
        var fixture = prepared(); var physical = mock(Connection.class);
        try (var held = new Held(fixture)) {
            var result = held.executor.submit(() -> refusal(fixture, physical));
            assertThatThrownBy(() -> result.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            Files.move(fixture.root.resolve("current.json"), fixture.root.getParent().resolve("original-current.json"));
            Files.writeString(fixture.root.resolve("current.json"), "{}");
            held.release.countDown();
            assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo("RECOVERY_DATABASE_ADMISSION_REFUSED:AUTHORITY_UNAVAILABLE");
        }
        verifyNoInteractions(physical);
    }

    @Test void existingExplicitReaderKeepsImmediateRefusal() throws Exception {
        var fixture = prepared();
        try (var held = new Held(fixture)) {
            assertThatThrownBy(fixture::read).hasMessage("CONTROL_COORDINATOR_BUSY");
        }
        assertThat(fixture.read().recordSha256()).isEqualTo(fixture.latest.path("recordSha256").asText());
    }

    private static RecoveryControlFixture prepared() throws Exception {
        var fixture = RecoveryControlFixture.create();
        fixture.toReleaseReady();
        RecoveryControlFixture.normal(fixture, "model_schema").write();
        return fixture;
    }

    private static String refusal(RecoveryControlFixture fixture, Connection physical) {
        try {
            new RecoveryClosedConnectionAdmission(fixture.location(), fixture.checkpoint, 1).verify(physical);
            return "UNEXPECTED_ADMISSION";
        } catch (SQLException refused) { return refused.getMessage(); }
    }

    private static final class Held implements AutoCloseable {
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        final CountDownLatch release = new CountDownLatch(1);
        private final Future<?> holder;

        Held(RecoveryControlFixture fixture) throws Exception {
            var acquired = new CountDownLatch(1);
            holder = executor.submit(() -> RecoveryControlLock.locked(fixture.root, () -> {
                acquired.countDown();
                try { assertThat(release.await(10, TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt(); throw new java.io.IOException(interrupted);
                }
                return null;
            }));
            assertThat(acquired.await(5, TimeUnit.SECONDS)).isTrue();
        }

        @Override public void close() throws Exception {
            release.countDown();
            try { holder.get(5, TimeUnit.SECONDS); }
            finally { executor.shutdownNow(); }
        }
    }
}
