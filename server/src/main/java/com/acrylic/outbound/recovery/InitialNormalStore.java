package com.acrylic.outbound.recovery;

import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Set;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.require;

/**
 * Explicit initial-NORMAL evidence storage, separate from the closed recovery chain. The original
 * trusted request must already exist outside the authority and database restore set. Provisioning
 * must supply a protected root with a one-byte coordinator.lock; this class never bootstraps trust.
 */
public final class InitialNormalStore {
    static final String REQUEST = "initial-normal-request.json";
    static final String COMPLETE = "initial-normal-committed.json";
    static final String UNKNOWN = "initial-normal-unknown.json";
    private final RecoveryControlLocation location;
    private final Path trustedRequest;

    public InitialNormalStore(RecoveryControlLocation location, Path trustedRequest) {
        this.location = location;
        this.trustedRequest = trustedRequest;
    }

    public InitialNormalEvidence prepare(byte[] original) {
        InitialNormalEvidence proposed = InitialNormalEvidence.parse(original);
        return locked(() -> {
            var trusted = trusted();
            require(Arrays.equals(trusted.originalBytes(), proposed.originalBytes()), "INITIAL_NORMAL_REQUEST_CONFLICT");
            InitialNormalDisk.publish(location.checked().root(), REQUEST, proposed.originalBytes());
            return proposed;
        });
    }

    /** A completion here is only file evidence; the verifier must also read the live database. */
    public Snapshot inspect() { return locked(this::inspectLocked); }

    <T> T locked(RecoveryControlLock.Read<T> action) {
        try {
            var checked = location.checked();
            return RecoveryControlLock.locked(checked.root(), () -> {
                layout(checked.root());
                return action.run();
            });
        } catch (IOException error) { throw new RecoveryControlException("INITIAL_NORMAL_IO", error); }
    }

    InitialNormalEvidence trusted() throws IOException {
        var checked = location.checked();
        RecoveryControlLock.requireOwner(checked.root());
        var original = InitialNormalEvidence.parse(InitialNormalDisk.read(checked.checkpoint(trustedRequest)));
        require(original.installation().equals(checked.installation()), "INITIAL_NORMAL_INSTALLATION");
        return original;
    }

    Snapshot inspectLocked() throws IOException {
        var expected = trusted();
        Path root = location.checked().root();
        var actual = InitialNormalEvidence.parse(InitialNormalDisk.read(root.resolve(REQUEST)));
        require(Arrays.equals(expected.originalBytes(), actual.originalBytes()), "INITIAL_NORMAL_REQUEST_CONFLICT");
        require(!Files.exists(root.resolve(REQUEST + ".pending"), LinkOption.NOFOLLOW_LINKS), "INITIAL_NORMAL_INCOMPLETE");
        boolean committed = Files.exists(root.resolve(COMPLETE), LinkOption.NOFOLLOW_LINKS);
        if (committed) {
            require(!Files.exists(root.resolve(COMPLETE + ".pending"), LinkOption.NOFOLLOW_LINKS), "INITIAL_NORMAL_INCOMPLETE");
            InitialNormalCompletion.parse(InitialNormalDisk.read(root.resolve(COMPLETE))).requireMatches(actual);
        }
        return new Snapshot(actual, committed);
    }

    /** Only the R09 database coordinator may call this after confirmed commit and matching readback. */
    void completeLocked(String committedRequestSha256) throws IOException {
        var request = inspectLocked().request();
        var completion = new InitialNormalCompletion(request.sha256(), committedRequestSha256);
        completion.requireMatches(request);
        InitialNormalDisk.publish(location.checked().root(), COMPLETE, completion.bytes());
    }

    /** A persistent poison marker; no in-process retry or automatic UNKNOWN reconciliation. */
    void unknownLocked() throws IOException {
        InitialNormalDisk.publish(location.checked().root(), UNKNOWN, trusted().originalBytes());
    }

    void requireUnclosed(Path closedAuthority) throws IOException {
        var checked = location.checked();
        Path closed = RecoveryControlPaths.checked(closedAuthority);
        require(!RecoveryControlPaths.overlap(checked.root(), closed), "INITIAL_NORMAL_CONTROL_OVERLAP");
        require(!RecoveryControlPaths.overlap(closed, checked.checkpoint(trustedRequest)), "INITIAL_NORMAL_CONTROL_OVERLAP");
        for (Path restored : checked.restoreRoots())
            require(!RecoveryControlPaths.overlap(closed, restored), "CONTROL_INSIDE_RESTORE_SET");
        // Even an incomplete closed bootstrap is terminal for this initial-only registration path.
        require(!Files.exists(closed, LinkOption.NOFOLLOW_LINKS), "INITIAL_NORMAL_CLOSED_AUTHORITY");
    }

    private static void layout(Path root) throws IOException {
        require(!Files.exists(root.resolve(UNKNOWN), LinkOption.NOFOLLOW_LINKS)
            && !Files.exists(root.resolve(UNKNOWN + ".pending"), LinkOption.NOFOLLOW_LINKS), "INITIAL_NORMAL_UNKNOWN");
        Set<String> allowed = Set.of("coordinator.lock", REQUEST, COMPLETE, REQUEST + ".pending", COMPLETE + ".pending");
        try (var paths = Files.newDirectoryStream(root)) {
            for (Path path : paths) require(allowed.contains(path.getFileName().toString()), "INITIAL_NORMAL_UNKNOWN_ARTIFACT");
        }
    }

    public record Snapshot(InitialNormalEvidence request, boolean committed) { }
}
