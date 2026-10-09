package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/**
 * Explicit closed-session authority. Its identity is outside the normal control tree and is
 * supplied with an independent file pin and source-material pin. It never grants ordinary writes.
 */
public final class RecoveryClosedAuthority {
    private static final String FIELDS = "formatVersion authorityKind authorityRoot installationId recoverySessionId epochId "
        + "controlRevision controlRecordSha256 previousRecordSha256 sourcePinSha256 source witnessFileIdentity witnessHeaderSha256";
    public record Source(String manifestSha256, RecoverySourceIdentity identity) { }
    public record Pins(Path root, String authoritySha256, Source source) { }
    private final RecoveryControlLocation location;
    private final Path checkpoint;
    private final Path root;
    private final String authorityPin;
    private final String sourcePin;
    private final RecoverySourceIdentity sourceIdentity;
    private final RecoveryControlSnapshot startup;

    public RecoveryClosedAuthority(RecoveryControlLocation location, Path checkpoint, Pins pins) {
        this.location = checked(location);
        this.checkpoint = checkedCheckpoint(this.location, checkpoint);
        Objects.requireNonNull(pins); Objects.requireNonNull(pins.source());
        this.root = checkedRoot(this.location, pins.root());
        require(!RecoveryControlPaths.overlap(this.root, this.checkpoint), "CONTROL_CLOSED_INSIDE_CHECKPOINT");
        this.authorityPin = pin(pins.authoritySha256(), "RECOVERY_CLOSED_AUTHORITY_PIN_REQUIRED");
        this.sourcePin = pin(pins.source().manifestSha256(), "RECOVERY_CLOSED_SOURCE_PIN_REQUIRED");
        this.sourceIdentity = Objects.requireNonNull(pins.source().identity());
        this.startup = coordinatedRead(null);
    }

    public RecoveryControlSnapshot control() { return startup; }
    RecoveryControlLocation location() { return location; }
    Path checkpoint() { return checkpoint; }
    public Path authorityRoot() { return root; }
    public String sourcePinSha256() { return sourcePin; }
    public RecoverySourceIdentity sourceIdentity() { return sourceIdentity; }
    public boolean ordinaryWritesAllowed() { return false; }
    public boolean localDeletionAllowed() { return false; }

    /** Re-read all external and control inputs before a transaction acquires a database lease. */
    public RecoveryControlSnapshot read() {
        RecoveryControlSnapshot current = coordinatedRead(null);
        require(startup.equals(current), "CONTROL_INSTANCE_STALE");
        return current;
    }

    RecoveryControlSnapshot readLocked() throws IOException { return readLocked(null); }

    private RecoveryControlSnapshot readLocked(RecoveryClosedJournal.Operation operation) throws IOException {
        RecoveryControlLock.requireOwner(location.root());
        var checked = location.checked();
        require(checked.equals(location), "CONTROL_AUTHORITY_CHANGED");
        var control = RecoveryControlHistory.load(checked, checked.checkpoint(checkpoint), RecoveryControlPurpose.CLOSED_CONTROL_HISTORY);
        byte[] bytes = bytes(root.resolve("identity.json"));
        require(authorityPin.equals(sha256(bytes)), "RECOVERY_CLOSED_AUTHORITY_PIN_MISMATCH");
        JsonNode identity = readCanonical(bytes);
        fields(identity, FIELDS); version(identity);
        RecoveryControlPurpose.CLOSED_PROJECTION.requireKind(identity, "CONTROL_CLOSED_AUTHORITY_KIND_FORBIDDEN");
        requireBinding(identity, control);
        var registered = RecoveryControlHistory.read(location.root().resolve("closed-sessions/" + control.recordSha256() + ".json"));
        fields(registered, "formatVersion authorityRoot authoritySha256"); version(registered);
        require(text(registered, "authorityRoot").equals(root.toString())
            && text(registered, "authoritySha256").equals(authorityPin), "CONTROL_CLOSED_SESSION_AUTHORITY_MISMATCH");
        require(!Files.exists(root.resolve("outcome-unconfirmed.json"), LinkOption.NOFOLLOW_LINKS),
            "CONTROL_TRANSACTION_OUTCOME_UNCONFIRMED");
        journal(identity).check(operation);
        return control;
    }

    private RecoveryControlSnapshot coordinatedRead(RecoveryClosedJournal.Operation operation) {
        try {
            return RecoveryControlLock.locked(location.root(), () -> readLocked(operation));
        } catch (IOException error) {
            throw new RecoveryControlException("CONTROL_CLOSED_AUTHORITY_UNAVAILABLE", error);
        }
    }

    private void requireBinding(JsonNode identity, RecoveryControlSnapshot control) {
        require(text(identity, "authorityRoot").equals(root.toString()), "CONTROL_CLOSED_ROOT_MISMATCH");
        require(uuid(identity, "installationId").equals(control.installationId()), "CONTROL_CLOSED_INSTALLATION_MISMATCH");
        require(uuid(identity, "recoverySessionId").equals(control.recoverySessionId())
            && uuid(identity, "epochId").equals(control.epochId()), "CONTROL_CLOSED_SESSION_MISMATCH");
        require(revision(identity, "controlRevision") == Long.parseLong(control.revision())
            && text(identity, "controlRecordSha256").equals(control.recordSha256())
            && text(identity, "previousRecordSha256").equals(control.previousRecordSha256()), "CONTROL_CLOSED_HEAD_MISMATCH");
        require(text(identity, "sourcePinSha256").equals(sourcePin), "CONTROL_CLOSED_SOURCE_PIN_MISMATCH");
        require(sourceIdentity.equals(RecoverySourceIdentity.read(identity.path("source"))), "CONTROL_CLOSED_SOURCE_MISMATCH");
    }

    RecoveryClosedJournal.Operation beginLocked() throws IOException {
        RecoveryControlLock.requireOwner(location.root());
        return journal(RecoveryControlHistory.read(root.resolve("identity.json"))).begin();
    }

    private RecoveryClosedJournal journal(JsonNode identity) {
        return new RecoveryClosedJournal(root, authorityPin, new RecoveryClosedWitness(root,
            text(identity, "witnessFileIdentity"), pin(text(identity, "witnessHeaderSha256"), "CONTROL_CLOSED_WITNESS_IDENTITY")));
    }

    void requireDuring(RecoveryClosedJournal.Operation operation) {
        require(startup.equals(coordinatedRead(operation)), "CONTROL_INSTANCE_STALE");
    }

    void complete(RecoveryClosedJournal.Operation operation, int status) {
        try {
            RecoveryControlLock.locked(location.root(), () -> {
                readLocked(operation);
                journal(RecoveryControlHistory.read(root.resolve("identity.json"))).complete(operation, status); return null;
            });
        } catch (IOException error) { throw new RecoveryControlException("CONTROL_TRANSACTION_FENCE_UNCONFIRMED", error); }
    }

    /** No retry or restart may use this session after an unknown commit/rollback outcome. */
    void fenceUnknown() {
        var value = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
            .put("formatVersion", 1).put("authoritySha256", authorityPin)
            .put("controlRecordSha256", startup.recordSha256()).put("event", "CONTROL_TRANSACTION_OUTCOME_UNCONFIRMED");
        try (var file = java.nio.channels.FileChannel.open(root.resolve("outcome-unconfirmed.json"),
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            var bytes = java.nio.ByteBuffer.wrap(RecoveryControlJson.encode(value));
            while (bytes.hasRemaining()) file.write(bytes);
            file.force(true);
        } catch (FileAlreadyExistsException alreadyFenced) {
            // Preserve the original fence. Its presence alone refuses this session.
        } catch (IOException error) {
            throw new RecoveryControlException("CONTROL_TRANSACTION_FENCE_UNCONFIRMED", error);
        }
    }

    private static RecoveryControlLocation checked(RecoveryControlLocation value) {
        try { return Objects.requireNonNull(value).checked(); }
        catch (IOException error) { throw new RecoveryControlException("CONTROL_AUTHORITY_UNAVAILABLE", error); }
    }

    private static Path checkedCheckpoint(RecoveryControlLocation value, Path path) {
        try { return value.checkpoint(Objects.requireNonNull(path)); }
        catch (IOException error) { throw new RecoveryControlException("CONTROL_CHECKPOINT_UNAVAILABLE", error); }
    }

    private static Path checkedRoot(RecoveryControlLocation value, Path path) {
        try {
            Path candidate = RecoveryControlPaths.checked(Objects.requireNonNull(path));
            require(!RecoveryControlPaths.overlap(candidate, value.root()), "CONTROL_CLOSED_INSIDE_AUTHORITY");
            for (Path restore : value.restoreRoots())
                require(!RecoveryControlPaths.overlap(candidate, restore), "CONTROL_CLOSED_INSIDE_RESTORE_SET");
            require(Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS), "CONTROL_CLOSED_AUTHORITY_MISSING");
            return candidate;
        } catch (IOException error) {
            throw new RecoveryControlException("CONTROL_CLOSED_AUTHORITY_UNAVAILABLE", error);
        }
    }

    private static String pin(String value, String code) {
        require(value != null && value.matches("[a-f0-9]{64}") && !ZERO_HASH.equals(value), code);
        return value;
    }

    private static byte[] bytes(Path path) throws IOException {
        Path file = RecoveryControlPaths.file(path);
        try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] result = input.readNBytes(RecoveryControlJson.MAX_BYTES + 1);
            require(result.length > 0 && result.length <= RecoveryControlJson.MAX_BYTES, "CONTROL_CLOSED_AUTHORITY_SIZE");
            return result;
        }
    }
}
