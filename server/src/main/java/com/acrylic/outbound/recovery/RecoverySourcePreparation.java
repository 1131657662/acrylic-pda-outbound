package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Reads pinned native preparation bytes; this does not prove ACK, current policy or restored stock. */
public final class RecoverySourcePreparation {
    private final RecoverySourceAuthority credentials;
    private final RecoveryPreparationBinding binding;

    public RecoverySourcePreparation(RecoveryReceiptKeys keys) {
        credentials = new RecoverySourceAuthority(keys); binding = new RecoveryPreparationBinding(keys);
    }

    public Proof verify(RecoveryPinnedSource source) {
        require(source != null, "RECOVERY_SOURCE_IDENTITY");
        try {
            var claims = credentials.verify(source); var origin = SubmissionJson.parse(source.original("origin"));
            fields(origin, "offlineToken preparationReceipt parts");
            var receipt = origin.path("preparationReceipt"); boolean temporary = source.identity().sourceKind().equals("TEMP");
            if (temporary) require(text(origin, "offlineToken").equals(text(receipt, "offlineToken")), "RECOVERY_SOURCE_PREPARATION_TOKEN");
            var manifest = binding.verify(receipt, claims, source.identity());
            var parts = RecoveryPreparationParts.read(manifest, origin.path("parts"), temporary);
            var payload = SubmissionJson.parse(source.original("payload"));
            require(uuid(payload, "inventorySnapshotId").equals(uuid(manifest, "inventorySnapshotId")),
                "RECOVERY_SOURCE_PREPARATION_PAYLOAD_BASELINE");
            if (!temporary) require(payload.path("orderRevision").equals(manifest.path("revision")),
                "RECOVERY_SOURCE_PREPARATION_PAYLOAD_BASELINE");
            for (String field : List.of("continuationId", "handoverId", "handoverSessionId", "recoveryPackageId"))
                require(!payload.hasNonNull(field), "RECOVERY_SOURCE_PREPARATION_CHAIN_UNSUPPORTED");
            require(!payload.has("dispositions") || (payload.path("dispositions").isArray() && payload.path("dispositions").isEmpty()),
                "RECOVERY_SOURCE_PREPARATION_CHAIN_UNSUPPORTED");
            RecoveryPreparationInventory.verify(source.identity(), payload, new RecoveryPreparationInventory.Material(manifest, parts));
            var partHashes = new HashMap<String, String>();
            for (var row : SubmissionJson.array(manifest, "parts")) partHashes.put(text(row, "name"), text(row, "sha256"));
            return new Proof(source.identity(), text(receipt, "manifestSha256"), SubmissionJson.sha(payload), Map.copyOf(partHashes));
        } catch (RecoveryControlException refusal) { throw refusal; }
        catch (RuntimeException invalid) { throw new RecoveryControlException("RECOVERY_SOURCE_PREPARATION_INVALID", invalid); }
    }

    public record Proof(RecoverySourceIdentity identity, String manifestSha256, String payloadSha256, Map<String, String> partHashes) { }
}
