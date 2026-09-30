package com.acrylic.outbound.e2averify;

import java.nio.file.Path;

/**
 * The tree the E2a harness provisions and verifies. It defaults to this repository tree (the shared migration worktree was archived and removed on 2026-09-30);
 * {@code E2A_CROSS_ROOT} points the whole harness at the tree under test, so a re-run exercises that
 * tree's code instead of the worktree's revision at a fixed commit.
 *
 * <p>Every harness path (attempt directories, datadirs, migration scripts, probes) resolves through
 * this single root; individual tests must not carry their own copy of the default.
 */
public final class E2aWorktree {
    public static final Path ROOT = Path.of(System.getenv().getOrDefault("E2A_CROSS_ROOT",
        "D:/acrylic-pda-outbound"));

    private E2aWorktree() { }
}
