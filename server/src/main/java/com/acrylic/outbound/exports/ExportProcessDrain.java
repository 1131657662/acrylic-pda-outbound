package com.acrylic.outbound.exports;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Stops the renderer and observed descendants; return means their actual exit was observed. */
final class ExportProcessDrain {
    private ExportProcessDrain() { }

    static void stop(Process process, Set<ProcessHandle> children, Duration timeout) {
        boolean interrupted = Thread.interrupted();
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            var all = terminate(process, children);
            for (ProcessHandle child : all) interrupted |= awaitExit(child, deadline);
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private static List<ProcessHandle> terminate(Process process, Set<ProcessHandle> children) {
        synchronized (children) {
            process.descendants().forEach(children::add);
            var known = List.copyOf(children);
            known.forEach(child -> child.descendants().forEach(children::add));
            if (process.isAlive()) process.destroyForcibly();
            children.forEach(child -> { if (child.isAlive()) child.destroyForcibly(); });
            var all = new ArrayList<>(children); all.add(process.toHandle());
            return all;
        }
    }

    private static boolean awaitExit(ProcessHandle process, long deadline) {
        boolean interrupted = false;
        while (process.isAlive()) {
            try {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new TimeoutException();
                process.onExit().get(remaining, TimeUnit.NANOSECONDS);
            } catch (InterruptedException ignored) {
                interrupted = true;
            } catch (ExecutionException | TimeoutException error) {
                if (interrupted) Thread.currentThread().interrupt();
                throw new IllegalStateException("EXPORT_PROCESS_DRAIN_INCOMPLETE", error);
            }
        }
        return interrupted;
    }
}
