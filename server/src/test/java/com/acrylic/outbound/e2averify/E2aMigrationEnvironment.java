package com.acrylic.outbound.e2averify;

import java.nio.file.Path;

/** Test-only guard values; callers must invoke before any database or process action. */
public final class E2aMigrationEnvironment {
    private static final Path EXPECTED_WORKSPACE = Path.of("D:/acrylic-pda-worktrees/r06-e2a-migration");
    public static final String HOST = "127.0.0.1";
    public static final int PORT = 19329;
    public static final String DATABASE = "acrylic_outbound_e2a_migrate_20260921";
    public static final String USER = "e2a_migrate_20260921";
    public static final Path DATA_DIR = Path.of(".local/mysql19329/data");
    public static final Path STORAGE_DIR = Path.of(".local/private-files");
    public static final Path SIGNING_DIR = Path.of(".local/signing");

    private E2aMigrationEnvironment() { }

    public static void requireExact(boolean portFree, Path workspace) {
        Path actualWorkspace = workspace.toAbsolutePath().normalize();
        Path expectedWorkspace = EXPECTED_WORKSPACE.toAbsolutePath().normalize();
        if (!portFree || !actualWorkspace.equals(expectedWorkspace)) {
            throw new IllegalStateException("E2a migration environment guard failed: port/workspace");
        }
        requireUnderWorkspace(actualWorkspace, DATA_DIR, "datadir");
        requireUnderWorkspace(actualWorkspace, STORAGE_DIR, "storage");
        requireUnderWorkspace(actualWorkspace, SIGNING_DIR, "signing");
    }

    private static void requireUnderWorkspace(Path workspace, Path relative, String label) {
        Path resolved = workspace.resolve(relative).normalize();
        if (!resolved.startsWith(workspace)) {
            throw new IllegalStateException("E2a migration environment guard failed: " + label);
        }
    }
}
