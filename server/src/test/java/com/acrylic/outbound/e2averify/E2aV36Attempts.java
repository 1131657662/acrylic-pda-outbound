package com.acrylic.outbound.e2averify;

import java.nio.file.Path;

/**
 * The per-item attempt directory of the six V36 gated ITs, with an explicit environment override.
 *
 * <p>Each V36 IT is a one-shot experiment: it creates {@code test-started.marker} and refuses to run
 * again, and its attempt path used to be a compile-time constant, so the same class could only ever
 * run once per tree. The override lets the harness point an item at a fresh attempt directory (and a
 * matching fresh database/account) without editing the test, which is what the A0.2 addendum of the
 * 2026-09-30 continuation ruling authorized.
 *
 * <p>Only the attempt directory name is derived here. The database and account names stay explicit
 * environment variables ({@code E2A_<PREFIX>_URL/_USER/_PASSWORD}) so the harness keeps provisioning
 * them itself; when the override is absent every default is byte-for-byte what it was before.
 *
 * <p>Recognized overrides, one per item: {@code E2A_V36_ATTEMPT} (migration),
 * {@code E2A_DEVICE_KEY_ATTEMPT}, {@code E2A_KEY_ATTEMPT} (paired key), {@code E2A_STOP_ATTEMPT},
 * {@code E2A_AUDIT_PREFIX_ATTEMPT}, {@code E2A_AUDIT_ARCHIVE_ATTEMPT}. Each value is a directory name
 * relative to {@code <root>/.local}, or an absolute path so an attempt may live outside the tree.
 */
public final class E2aV36Attempts {
    private E2aV36Attempts() { }

    /** Migration / stage constraints ({@code E2aV36MigrationIT}). */
    public static Path migration() { return resolve("E2A_V36_ATTEMPT", "e2a-v36-attempt4"); }

    /** Device key ({@code E2aV36DeviceKeyIT}). */
    public static Path deviceKey() { return resolve("E2A_DEVICE_KEY_ATTEMPT", "e2a-v36-key-attempt3"); }

    /** Paired key ({@code E2aV36PairedKeyIT}). */
    public static Path pairedKey() { return resolve("E2A_KEY_ATTEMPT", "e2a-v36-key-attempt1"); }

    /** Stop writer ({@code E2aV36StopWriterIT}). */
    public static Path stopWriter() { return resolve("E2A_STOP_ATTEMPT", "e2a-v36-stop-attempt1"); }

    /** Audit prefix ({@code E2aV36AuditPrefixIT}). */
    public static Path auditPrefix() { return resolve("E2A_AUDIT_PREFIX_ATTEMPT", "e2a-v36-audit-attempt5"); }

    /** Audit archive ({@code E2aV36AuditArchiveIT}). */
    public static Path auditArchive() { return resolve("E2A_AUDIT_ARCHIVE_ATTEMPT", "e2a-v36-audit-attempt6"); }

    private static Path resolve(String variable, String fallback) {
        var supplied = System.getenv(variable);
        if (supplied == null || supplied.isBlank()) return E2aWorktree.ROOT.resolve(".local/" + fallback);
        var path = Path.of(supplied);
        return path.isAbsolute() ? path : E2aWorktree.ROOT.resolve(".local").resolve(path);
    }
}
