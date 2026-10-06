package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Validates the immutable full set carried by an ABORTED TEMP continuation request. */
public final class TemporaryRetainedManifest {
    private TemporaryRetainedManifest() { }

    public static List<Item> require(JsonNode manifest, Collection<Expected> expected,
        String expectedHash, long expectedVersion) {
        var actual = parse(manifest, expectedHash, expectedVersion);
        if (actual.size() != expected.size()) throw conflict("RETAINED_MANIFEST_INCOMPLETE");
        var expectedSorted = expected.stream().sorted(Comparator.comparing(Expected::baleId)).toList();
        var actualSorted = actual.stream().sorted(Comparator.comparing(Item::baleId)).toList();
        if (!actualSorted.equals(expectedSorted.stream().map(e -> new Item(e.baleId(), e.cycle(), e.recordSha256())).toList()))
            throw conflict("RETAINED_MANIFEST_SET_CONFLICT");
        return List.copyOf(actualSorted);
    }

    public static List<Item> parse(JsonNode manifest, String expectedHash, long expectedVersion) {
        if (manifest == null || !manifest.isObject() || manifest.size() != 2
            || !manifest.path("version").isIntegralNumber() || manifest.path("version").longValue() != expectedVersion)
            throw conflict("RETAINED_MANIFEST_VERSION_CONFLICT");
        if (!SubmissionJson.sha(manifest).equals(expectedHash)) throw conflict("RETAINED_MANIFEST_HASH_CONFLICT");
        var values = manifest.path("items");
        if (!values.isArray()) throw conflict("RETAINED_MANIFEST_INCOMPLETE");
        var actual = new ArrayList<Item>();
        for (JsonNode value : values) actual.add(item(value));
        if (actual.stream().map(Item::baleId).distinct().count() != actual.size())
            throw conflict("RETAINED_MANIFEST_SET_CONFLICT");
        return List.copyOf(actual);
    }

    private static Item item(JsonNode value) {
        if (!value.isObject() || value.size() != 3 || !value.path("baleId").isTextual()
            || !value.path("expectedDispatchCycle").isTextual() || !value.path("recordSha256").isTextual())
            throw conflict("RETAINED_MANIFEST_ITEM_INVALID");
        try {
            UUID bale = UUID.fromString(value.path("baleId").asText());
            String cycle = value.path("expectedDispatchCycle").asText();
            if (!cycle.matches("[1-9][0-9]{0,17}")) throw new IllegalArgumentException();
            String record = value.path("recordSha256").asText();
            if (!record.matches("[0-9a-f]{64}")) throw new IllegalArgumentException();
            return new Item(bale, Long.parseLong(cycle), record);
        } catch (RuntimeException invalid) { throw conflict("RETAINED_MANIFEST_ITEM_INVALID"); }
    }

    private static BusinessException conflict(String code) {
        return new BusinessException(409, code, "retained continuation manifest cannot be verified");
    }

    public record Expected(UUID baleId, long cycle, String recordSha256) {
        public Expected {
            if (baleId == null || cycle <= 0 || recordSha256 == null || !recordSha256.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("invalid retained manifest expectation");
        }
    }

    public record Item(UUID baleId, long cycle, String recordSha256) implements Comparable<Item> {
        @Override public int compareTo(Item other) { return baleId.compareTo(other.baleId); }
    }
}
