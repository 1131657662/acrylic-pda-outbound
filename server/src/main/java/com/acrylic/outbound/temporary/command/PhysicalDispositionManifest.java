package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Validates the immutable, ordered per-bale manifest before any reservation can be released. */
final class PhysicalDispositionManifest {
    private PhysicalDispositionManifest() { }

    static List<UUID> require(JsonNode manifest, Collection<UUID> activeBales,
        String expectedHash, long expectedVersion) {
        if (manifest == null || !manifest.isObject() || !manifest.path("version").isIntegralNumber()
            || manifest.path("version").longValue() != expectedVersion)
            throw conflict("DISPOSITION_MANIFEST_VERSION_CONFLICT");
        var canonical = SubmissionJson.canonical(manifest);
        if (!ImportFiles.sha256(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)).equals(expectedHash))
            throw conflict("DISPOSITION_MANIFEST_HASH_CONFLICT");
        var values = manifest.path("items");
        if (!values.isArray() || values.size() != activeBales.size()) throw conflict("DISPOSITION_MANIFEST_INCOMPLETE");
        var expected = activeBales.stream().sorted().toList();
        var actual = new ArrayList<UUID>();
        for (JsonNode item : values) {
            if (!item.isObject() || item.size() != 2 || !item.path("baleId").isTextual()
                || !item.path("disposition").isTextual() || !item.path("disposition").asText().equals("PHYSICAL_DISPOSED"))
                throw conflict("DISPOSITION_MANIFEST_ITEM_INVALID");
            try { actual.add(UUID.fromString(item.path("baleId").asText())); }
            catch (IllegalArgumentException invalid) { throw conflict("DISPOSITION_MANIFEST_ITEM_INVALID"); }
        }
        if (!actual.equals(expected) || new HashSet<>(actual).size() != actual.size())
            throw conflict("DISPOSITION_MANIFEST_SET_CONFLICT");
        return List.copyOf(actual);
    }

    private static BusinessException conflict(String code) {
        return new BusinessException(409, code, "逐包处置清单不可核验");
    }
}
