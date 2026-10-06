package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Validates the immutable, ordered per-bale manifest before any reservation can be changed. */
public final class PhysicalDispositionManifest {
    private static final Set<String> ACTIONS = Set.of("RELEASE", "RETAIN", "QUARANTINE", "PHYSICAL_DISPOSED");
    private PhysicalDispositionManifest() { }

    public static List<Item> require(JsonNode manifest, Collection<UUID> activeBales,
        String expectedHash, long expectedVersion) {
        requireHeader(manifest, expectedVersion); verifyHash(manifest, expectedHash);
        var values = manifest.path("items");
        if (!values.isArray() || values.size() != activeBales.size()) throw conflict("DISPOSITION_MANIFEST_INCOMPLETE");
        var expected = activeBales.stream().sorted().toList(); var actual = new ArrayList<Item>();
        for (JsonNode item : values) actual.add(item(item));
        var ids = actual.stream().map(Item::baleId).toList();
        if (!ids.equals(expected) || new HashSet<>(ids).size() != ids.size()) throw conflict("DISPOSITION_MANIFEST_SET_CONFLICT");
        return List.copyOf(actual);
    }

    private static void requireHeader(JsonNode manifest, long expectedVersion) {
        if (manifest == null || !manifest.isObject() || !manifest.path("version").isIntegralNumber()
            || manifest.path("version").longValue() != expectedVersion) throw conflict("DISPOSITION_MANIFEST_VERSION_CONFLICT");
    }

    private static void verifyHash(JsonNode manifest, String expectedHash) {
        var canonical = SubmissionJson.canonical(manifest);
        if (!ImportFiles.sha256(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)).equals(expectedHash))
            throw conflict("DISPOSITION_MANIFEST_HASH_CONFLICT");
    }

    private static Item item(JsonNode item) {
        if (!item.isObject() || item.size() != 2 || !item.path("baleId").isTextual()
            || !item.path("disposition").isTextual() || !ACTIONS.contains(item.path("disposition").asText()))
            throw conflict("DISPOSITION_MANIFEST_ITEM_INVALID");
        try { return new Item(UUID.fromString(item.path("baleId").asText()), normalize(item.path("disposition").asText())); }
        catch (IllegalArgumentException invalid) { throw conflict("DISPOSITION_MANIFEST_ITEM_INVALID"); }
    }

    private static String normalize(String action) { return action.equals("PHYSICAL_DISPOSED") ? "QUARANTINE" : action; }
    private static BusinessException conflict(String code) { return new BusinessException(409, code, "disposition manifest cannot be verified"); }
    public record Item(UUID baleId, String action) { }
}
