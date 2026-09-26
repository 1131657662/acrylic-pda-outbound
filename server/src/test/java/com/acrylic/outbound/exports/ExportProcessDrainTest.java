package com.acrylic.outbound.exports;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExportProcessDrainTest {
    @Test void destroyRequestDoesNotMeanExitAndInterruptedDrainStillWaits() throws Exception {
        var exited = new CompletableFuture<ProcessHandle>(); var killed = new CountDownLatch(1);
        var alive = new AtomicBoolean(true); var restoredInterrupt = new AtomicBoolean();
        Process process = fixture(alive, exited, killed);
        var finished = new CountDownLatch(1);
        Thread caller = new Thread(() -> {
            Thread.currentThread().interrupt();
            ExportProcessDrain.stop(process, new HashSet<>(), Duration.ofSeconds(5));
            restoredInterrupt.set(Thread.currentThread().isInterrupted()); finished.countDown();
        });
        caller.start();
        try {
            assertThat(killed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(finished.await(100, TimeUnit.MILLISECONDS)).isFalse();
        } finally { alive.set(false); exited.complete(process.toHandle()); caller.join(3000); }
        assertThat(finished.getCount()).isZero(); assertThat(restoredInterrupt).isTrue();
    }

    @Test void unconfirmedExitFailsInsteadOfClaimingDrained() {
        Process process = fixture(new AtomicBoolean(true), new CompletableFuture<>(), new CountDownLatch(1));
        assertThatThrownBy(() -> ExportProcessDrain.stop(process, new HashSet<>(), Duration.ofMillis(50)))
            .isInstanceOf(IllegalStateException.class).hasMessage("EXPORT_PROCESS_DRAIN_INCOMPLETE");
    }

    private Process fixture(AtomicBoolean alive, CompletableFuture<ProcessHandle> exited, CountDownLatch killed) {
        var process = mock(Process.class); var handle = mock(ProcessHandle.class);
        when(process.toHandle()).thenReturn(handle); when(process.descendants()).thenAnswer(call -> java.util.stream.Stream.empty());
        when(process.isAlive()).thenAnswer(call -> alive.get()); when(handle.isAlive()).thenAnswer(call -> alive.get());
        when(handle.onExit()).thenReturn(exited);
        when(process.destroyForcibly()).thenAnswer(call -> { killed.countDown(); return process; });
        return process;
    }
}
