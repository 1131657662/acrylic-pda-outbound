package com.acrylic.outbound.e2averify;

/** Runtime-selected ports for one isolated E2A attempt. */
public final class E2aTestPorts {
    private E2aTestPorts() { }

    public static int database() { return port("E2A_CROSS_PORT", 19330); }

    public static int http() { return port("E2A_HTTP_PORT", 19434); }

    private static int port(String name, int fallback) {
        String raw = System.getenv(name);
        if (raw == null || raw.isBlank()) return fallback;
        try {
            int value = Integer.parseInt(raw);
            if (value < 1024 || value > 65535) throw new IllegalArgumentException(name + " out of range");
            return value;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(name + " must be numeric", error);
        }
    }
}
