package com.acrylic.outbound.recovery;

import java.nio.file.*;
import java.security.PublicKey;
import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Independently pinned pre-failure registration; never selects its trust root from the restored database. */
public final class RecoveryDeviceRegistration {
    private final RecoveryControlLocation configured;
    private final Path checkpoint;

    public RecoveryDeviceRegistration(RecoveryControlLocation configured, Path checkpoint) {
        this.configured = Objects.requireNonNull(configured); this.checkpoint = Objects.requireNonNull(checkpoint);
    }

    public Proof read(Request request) {
        require(request != null && request.independentSha256() != null && request.independentSha256().matches("[a-f0-9]{64}")
            && !ZERO_HASH.equals(request.independentSha256()), "RECOVERY_DEVICE_REGISTRATION_PIN");
        try {
            var location = configured.checked(); var reader = new RecoveryControlReader(location);
            var before = reader.read(checkpoint);
            require(Set.of("RECOVERING", "VERIFYING", "RELEASE_READY").contains(before.phase()), "RECOVERY_SOURCE_PHASE");
            require(before.affectedWarehouseIds().contains(request.expected().warehouseId().toString()), "RECOVERY_SOURCE_WAREHOUSE_UNKNOWN");
            Path root = RecoveryControlPaths.checked(request.materialRoot()); outside(root, location);
            byte[] bytes = readFile(root.resolve(request.independentSha256() + ".json"));
            require(sha256(bytes).equals(request.independentSha256()), "RECOVERY_DEVICE_REGISTRATION_PIN_MISMATCH");
            var value = readCanonical(bytes);
            var key = RecoveryDeviceRegistrationFacts.verify(value, request.expected());
            require(before.equals(reader.read(checkpoint)), "RECOVERY_SOURCE_CONTROL_CHANGED");
            return new Proof(request.expected(), request.independentSha256(), instant(value, "observedAt"), key);
        } catch (RecoveryControlException refusal) { throw refusal; }
        catch (java.io.IOException unavailable) { throw new RecoveryControlException("RECOVERY_DEVICE_REGISTRATION_UNAVAILABLE", unavailable); }
        catch (RuntimeException malformed) { throw new RecoveryControlException("RECOVERY_DEVICE_REGISTRATION_INVALID", malformed); }
    }

    private void outside(Path root, RecoveryControlLocation location) throws java.io.IOException {
        require(!RecoveryControlPaths.overlap(root, location.root()), "RECOVERY_SOURCE_INSIDE_AUTHORITY");
        for (Path restored : location.restoreRoots())
            require(!RecoveryControlPaths.overlap(root, restored), "RECOVERY_SOURCE_INSIDE_RESTORE_SET");
        require(!RecoveryControlPaths.overlap(root, location.checkpoint(checkpoint)), "RECOVERY_SOURCE_INSIDE_CHECKPOINT");
    }

    private byte[] readFile(Path path) throws java.io.IOException {
        Path file = RecoveryControlPaths.file(path);
        try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(262145);
            require(bytes.length > 0 && bytes.length <= 262144, "RECOVERY_DEVICE_REGISTRATION_SIZE");
            return bytes;
        }
    }

    public record Expected(UUID mysqlServerUuid, String databaseSchema, String sourceRecoveryEpoch, UUID warehouseId,
            UUID deviceId, Instant notAfter) {
        public Expected {
            for (UUID id : new UUID[]{mysqlServerUuid, warehouseId, deviceId})
                require(id != null && !id.equals(new UUID(0, 0)), "RECOVERY_DEVICE_REGISTRATION_EXPECTED");
            require(databaseSchema != null && databaseSchema.matches("[A-Za-z][A-Za-z0-9_]{0,63}") && notAfter != null
                && sourceRecoveryEpoch != null && sourceRecoveryEpoch.matches("[1-9][0-9]{0,17}"), "RECOVERY_DEVICE_REGISTRATION_EXPECTED");
        }
    }
    public record Request(Path materialRoot, String independentSha256, Expected expected) {
        public Request { require(materialRoot != null && expected != null, "RECOVERY_DEVICE_REGISTRATION_REQUEST"); }
    }
    public record Proof(Expected expected, String originalSha256, Instant observedAt, PublicKey trustedKey) {
        public boolean ordinaryWritesAllowed() { return false; }
        public boolean localDeletionAllowed() { return false; }
    }
}
