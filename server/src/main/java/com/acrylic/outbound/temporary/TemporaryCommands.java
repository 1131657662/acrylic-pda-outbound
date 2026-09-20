package com.acrylic.outbound.temporary;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class TemporaryCommands {
    private TemporaryCommands() { }
    public record Context(String epoch,String key,String requestId) { }
    public record Prepare(@NotNull UUID inventorySnapshotId,
        @NotNull @Pattern(regexp="0|[1-9][0-9]{0,17}") String mappingRevision,
        @NotNull @Pattern(regexp="[1-9][0-9]{0,17}") String policyVersion) { }
    public record Ack(@NotNull UUID bundleId,@NotNull @Pattern(regexp="[0-9a-f]{64}") String manifestSha256,
        @NotNull @Pattern(regexp="[1-9][0-9]{0,17}") String authorizationEpoch,@NotNull Instant activatedAt) { }
    public record Receipt(String authorizationId,String bundleId,String offlineToken,String manifest,
        String manifestSha256,String signedManifest) { }
    public record Acknowledgement(String authorizationId,String bundleId,String activatedAt,String acknowledgedAt,String signedAck) { }
}
