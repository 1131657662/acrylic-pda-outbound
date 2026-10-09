package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Original first-round FORMAL preparation across a complete renewal chain; not ACK, revocation or write authority. */
public final class RecoverySourceFormalRenewals {
    private final RecoverySourceAuthority credentials;
    private final RecoverySourceQualifications qualifications;
    private final RecoveryPreparationBinding binding;

    public RecoverySourceFormalRenewals(RecoveryReceiptKeys keys) {
        credentials = new RecoverySourceAuthority(keys); qualifications = new RecoverySourceQualifications(keys);
        binding = new RecoveryPreparationBinding(keys);
    }

    public Proof verify(RecoveryPinnedSource current, Map<UUID, RecoveryPinnedSource> originals) {
        require(current != null && current.identity().sourceKind().equals("FORMAL") && current.identity().workRound() == 1,
            "RECOVERY_FORMAL_RENEWAL_SOURCE");
        require(originals != null && originals.size() >= 2 && originals.size() <= 1024, "RECOVERY_FORMAL_RENEWAL_CATALOG");
        try {
            var entries = catalog(current, originals); var first = entries.getFirst(); chain(entries);
            require(uuid(credentials.verifyCredential(current), "jti").equals(uuid(entries.getLast().claims(), "jti")),
                "RECOVERY_FORMAL_RENEWAL_CURRENT");
            qualifications.verify(current, originals);
            var receipt = first.origin().path("preparationReceipt");
            var manifest = binding.verify(receipt, first.claims(), current.identity());
            var parts = RecoveryPreparationParts.read(manifest, first.origin().path("parts"), false);
            var payload = SubmissionJson.parse(current.original("payload")); payload(payload, manifest);
            RecoveryPreparationInventory.verify(current.identity(), payload, new RecoveryPreparationInventory.Material(manifest, parts));
            var hashes = new HashMap<String, String>();
            for (var part : SubmissionJson.array(manifest, "parts")) hashes.put(text(part, "name"), text(part, "sha256"));
            return new Proof(new RecoverySourcePreparation.Proof(current.identity(), text(receipt, "manifestSha256"),
                SubmissionJson.sha(payload), Map.copyOf(hashes)), entries.stream().map(entry -> uuid(entry.claims(), "jti")).toList());
        } catch (RecoveryControlException refusal) { throw refusal; }
        catch (RuntimeException invalid) { throw new RecoveryControlException("RECOVERY_FORMAL_RENEWAL_INVALID", invalid); }
    }

    private List<Entry> catalog(RecoveryPinnedSource current, Map<UUID, RecoveryPinnedSource> originals) {
        var entries = new ArrayList<Entry>(); var sequences = new HashSet<Long>();
        for (var pair : Map.copyOf(originals).entrySet()) {
            var source = pair.getValue();
            require(source.identity().equals(current.identity()), "RECOVERY_FORMAL_RENEWAL_SOURCE");
            require(source.control() != null && source.control().equals(current.control()) && source.manifestSha256() != null
                && source.manifestSha256().matches("[a-f0-9]{64}") && !ZERO_HASH.equals(source.manifestSha256()),
                "RECOVERY_FORMAL_RENEWAL_PIN");
            var claims = credentials.verifyCredential(source);
            require(pair.getKey().equals(uuid(claims, "jti")), "RECOVERY_SOURCE_CREDENTIAL_ID");
            var origin = SubmissionJson.parse(source.original("origin"));
            fields(origin, origin.has("activation") ? "offlineToken preparationReceipt parts activation" : "offlineToken preparationReceipt parts");
            long sequence = claims.has("authorizationSequence") ? revision(claims, "authorizationSequence") : 0;
            require(sequences.add(sequence), "RECOVERY_FORMAL_RENEWAL_CHAIN");
            entries.add(new Entry(claims, origin, sequence));
        }
        entries.sort(Comparator.comparingLong(Entry::sequence)); return List.copyOf(entries);
    }

    private void chain(List<Entry> entries) {
        var first = entries.getFirst();
        require(first.sequence() == 0 && !first.claims().has("authorizationSequence")
            && !first.claims().has("renewedFromAuthorizationId"), "RECOVERY_FORMAL_RENEWAL_INITIAL");
        for (int index = 0; index < entries.size(); index++) {
            var current = entries.get(index); baseline(first, current);
            if (index == 0) continue;
            var previous = entries.get(index - 1);
            require(current.sequence() == index && uuid(current.claims(), "renewedFromAuthorizationId").equals(uuid(previous.claims(), "jti")),
                "RECOVERY_FORMAL_RENEWAL_CHAIN");
            var window = RecoverySourceSavedTimes.window(current.claims());
            require(!window.issued().isBefore(RecoverySourceSavedTimes.window(previous.claims()).issued())
                && java.time.Duration.between(window.issued(), window.expires()).getSeconds() == 172800,
                "RECOVERY_FORMAL_RENEWAL_WINDOW");
        }
    }

    private void baseline(Entry first, Entry current) {
        for (String field : List.of("scope", "sub", "deviceId", "warehouseId", "assignmentId", "orderId", "revision", "assignmentEpoch",
                "serverRecoveryEpoch", "inventorySnapshotId", "planSha256", "inventorySha256", "userAuthVersion", "deviceVersion",
                "policyVersion", "autoDeductInventory", "startOrderScanSeq", "allocationVersion"))
            require(first.claims().hasNonNull(field) && first.claims().path(field).equals(current.claims().path(field)),
                "RECOVERY_FORMAL_RENEWAL_BASELINE");
        for (String field : List.of("preparationReceipt", "parts", "activation"))
            require(first.origin().path(field).equals(current.origin().path(field)), "RECOVERY_FORMAL_RENEWAL_PREPARATION");
    }

    private void payload(JsonNode payload, JsonNode manifest) {
        require(uuid(payload, "inventorySnapshotId").equals(uuid(manifest, "inventorySnapshotId"))
            && payload.path("orderRevision").equals(manifest.path("revision")), "RECOVERY_SOURCE_PREPARATION_PAYLOAD_BASELINE");
        for (String field : List.of("continuationId", "handoverId", "handoverSessionId", "recoveryPackageId"))
            require(!payload.hasNonNull(field), "RECOVERY_SOURCE_PREPARATION_CHAIN_UNSUPPORTED");
        require(!payload.has("dispositions") || (payload.path("dispositions").isArray() && payload.path("dispositions").isEmpty()),
            "RECOVERY_SOURCE_PREPARATION_CHAIN_UNSUPPORTED");
    }

    private record Entry(JsonNode claims, JsonNode origin, long sequence) { }
    public record Proof(RecoverySourcePreparation.Proof preparation, List<UUID> authorizationIds) { }
}
