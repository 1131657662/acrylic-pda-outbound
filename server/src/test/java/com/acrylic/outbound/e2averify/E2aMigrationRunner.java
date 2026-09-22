package com.acrylic.outbound.e2averify;

import java.nio.file.Path;
import java.util.Map;

/** Opt-in entry point for future target-34/35 execution; disabled without the full guard. */
public final class E2aMigrationRunner {
    private E2aMigrationRunner() { }

    public static void main(String[] args) {
        E2aMigrationEnvironment.requireExact(System.getenv(), portFree(), workspace());
        throw new IllegalStateException("E2a migration runner is prepared but dynamic execution is not scheduled");
    }

    static Path workspace() {
        return Path.of("D:/acrylic-pda-worktrees/r06-e2a-migration");
    }

    static boolean portFree() {
        String value = System.getenv().getOrDefault("E2A_MIGRATION_PORT_FREE", "false");
        return "true".equals(value);
    }

    static void requireProfile(Map<String, String> env) {
        E2aMigrationEnvironment.requireExact(env, "true".equals(env.get("E2A_MIGRATION_PORT_FREE")), workspace());
    }
}
