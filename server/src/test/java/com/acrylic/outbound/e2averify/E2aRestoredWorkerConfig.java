package com.acrylic.outbound.e2averify;

import java.nio.file.Path;

/** Explicit runtime binding for one restored-worker attempt. */
public final class E2aRestoredWorkerConfig {
    private E2aRestoredWorkerConfig() { }

    public static String run() {
        String value = configured("E2A_CROSS_RUN", "670");
        if (!value.matches("[1-9][0-9]{0,3}")) throw new IllegalStateException("Invalid restored-worker run");
        return value;
    }

    public static Path attempt() {
        String fallback = "D:/acrylic-pda-outbound/.local/e2a-cross-20260925-" + run();
        return Path.of(configured("E2A_CROSS_ATTEMPT", fallback)).toAbsolutePath().normalize();
    }

    public static String database() {
        return configured("E2A_CROSS_DATABASE", "acrylic_e2a_cross_20260925_" + run());
    }

    public static String user() {
        return configured("E2A_CROSS_USER", "e2a_cross_20260925_" + run());
    }

    public static int databasePort() { return E2aTestPorts.database(); }

    public static int httpPort() { return E2aTestPorts.http(); }

    public static String databaseUrl(String schema) {
        return "jdbc:mysql://127.0.0.1:" + databasePort() + "/" + schema
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }

    private static String configured(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
