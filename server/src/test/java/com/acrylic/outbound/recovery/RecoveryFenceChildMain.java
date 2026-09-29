package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.sql.SQLException;

/** Destructive process termination is confined to this independent test JVM. */
public final class RecoveryFenceChildMain {
    private static Path directory;
    public static void main(String[] args) throws Exception {
        directory = Path.of(args[1]);
        status("java=" + System.getProperty("java.version") + "|pid=" + ProcessHandle.current().pid());
        var supply = FenceTestSupply.read(Path.of(args[0]));
        switch (args[2]) {
            case "signal" -> signal(supply);
            case "matrix" -> matrix(supply, args[3]);
            case "effect" -> effect(supply, args[3]);
            case "unknown" -> unknown(supply, args[3]);
            default -> throw new IllegalArgumentException(args[2]);
        }
    }
    private static void signal(FenceTestSupply supply) throws Exception {
        RecoveryFenceLock.forRoots(supply.roots()).hold(supply.roots());
        status("LOCKED"); waitFor("stop");
        Runtime.getRuntime().halt(70);
    }
    private static void effect(FenceTestSupply supply, String table) throws Exception {
        EffectRealEnvironment.requireEnvironment();
        var gate = RecoveryEffectGate.enabled(supply.authority());
        try (var jdbc = new FenceRealJdbc(gate, table)) {
            jdbc.connection.createStatement().executeUpdate(jdbc.insert(1));
            status("EFFECT|delegate=" + jdbc.calls.count("Statement.executeUpdate") + "|observer=" + jdbc.count());
        }
    }
    private static void unknown(FenceTestSupply supply, String table) throws Exception {
        EffectRealEnvironment.requireEnvironment();
        var authority = supply.authority();
        var gate = RecoveryEffectGate.enabled(authority);
        authority.registerOperation();
        try (var jdbc = new FenceRealJdbc(gate, table)) {
            jdbc.connection.setAutoCommit(false);
            jdbc.connection.createStatement().executeUpdate(jdbc.insert(1));
            jdbc.calls.failNext("Connection.commit", new SQLException("MODEL commit transport failure", "08006"));
            status("MODEL|commit failure before driver; real uncommitted insert; production UNKNOWN sequence next");
            jdbc.connection.commit();
            throw new AssertionError("Runtime.halt did not terminate JVM");
        }
    }
    private static void matrix(FenceTestSupply supply, String bits) throws Exception {
        var authority = supply.authority();
        var gate = RecoveryEffectGate.enabled(authority);
        authority.registerOperation();
        status("READY"); waitFor("go");
        gate.fence();
        try { gate.check(); throw new AssertionError("local fence missing"); }
        catch (SQLException expected) { status("LOCAL|" + expected.getMessage()); }
        var real = authority.actions();
        status("MODEL|faultSelection=" + bits + "|F2 forced failure; real locks and halt underneath");
        RecoveryFenceSequence.run(new RecoveryFenceSequence.Actions() {
            public void hold() throws Exception { allowed(0); real.hold(); }
            public void audit() throws Exception { throw new java.io.IOException("MODEL audit failure"); }
            public void release() throws Exception { allowed(1); real.release(); }
            public void close() throws Exception { real.close(); throw new java.io.IOException("MODEL close receipt failure after close"); }
            public void halt() { if (bits.charAt(2) == '1') real.halt(); }
            private void allowed(int index) throws Exception {
                if (bits.charAt(index) != '1') throw new java.io.IOException("MODEL step unconfirmed");
            }
        });
        status("MATRIX-DONE"); waitFor("stop");
    }
    private static void waitFor(String command) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(90);
        while (!Files.exists(directory.resolve(command))) {
            if (System.nanoTime() > deadline) throw new AssertionError("timeout " + command);
            Thread.sleep(20);
        }
    }
    private static void status(String text) throws Exception {
        Files.writeString(directory.resolve("status.txt"), text + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
