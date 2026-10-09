package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Native compressed preparation bytes; decompression cannot exceed the issuer's size limit. */
final class RecoveryPreparationParts {
    private static final int MAX_BYTES = 16 * 1024 * 1024;
    private RecoveryPreparationParts() { }

    static Map<String, JsonNode> read(JsonNode manifest, JsonNode originals, boolean temporary) {
        var names = temporary ? Set.of("inventory", "mapping", "policy", "rules", "availability") : Set.of("plan", "inventory", "availability");
        fields(originals, String.join(" ", names));
        var result = new HashMap<String, JsonNode>();
        for (var descriptor : SubmissionJson.array(manifest, "parts")) {
            fields(descriptor, "name compressedBytes bytes compressedSha256 sha256 rows");
            String name = text(descriptor, "name");
            require(names.contains(name) && !result.containsKey(name), "RECOVERY_SOURCE_PREPARATION_PART_SET");
            JsonNode value = unpack(descriptor, text(originals, name));
            long actualRows = switch (name) {
                case "inventory", "mapping" -> arraySize(value);
                case "availability" -> arraySize(temporary ? value.path("items") : value);
                default -> 1;
            };
            require(integer(descriptor, "rows") == actualRows, "RECOVERY_SOURCE_PREPARATION_ROWS");
            result.put(name, value);
        }
        require(result.keySet().equals(names), "RECOVERY_SOURCE_PREPARATION_PART_SET");
        return Map.copyOf(result);
    }

    private static JsonNode unpack(JsonNode descriptor, String encoded) {
        require(encoded.length() <= ((MAX_BYTES + 2L) / 3) * 4, "RECOVERY_SOURCE_PREPARATION_SIZE");
        byte[] compressed = Base64.getDecoder().decode(encoded);
        require(Base64.getEncoder().encodeToString(compressed).equals(encoded), "RECOVERY_SOURCE_PREPARATION_ENCODING");
        require(compressed.length > 0 && compressed.length <= MAX_BYTES
            && compressed.length == integer(descriptor, "compressedBytes"), "RECOVERY_SOURCE_PREPARATION_SIZE");
        require(sha256(compressed).equals(text(descriptor, "compressedSha256")), "RECOVERY_SOURCE_PREPARATION_COMPRESSED_HASH");
        long expected = integer(descriptor, "bytes");
        require(expected > 0 && expected <= MAX_BYTES, "RECOVERY_SOURCE_PREPARATION_SIZE");
        try (var gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            byte[] raw = gzip.readNBytes(Math.toIntExact(expected + 1));
            require(raw.length == expected, "RECOVERY_SOURCE_PREPARATION_SIZE");
            require(sha256(raw).equals(text(descriptor, "sha256")), "RECOVERY_SOURCE_PREPARATION_RAW_HASH");
            return SubmissionJson.parse(raw);
        } catch (IOException invalid) { throw new RecoveryControlException("RECOVERY_SOURCE_PREPARATION_GZIP", invalid); }
    }

    static long integer(JsonNode value, String key) {
        var number = value.path(key);
        require(number.isIntegralNumber() && number.canConvertToLong() && number.longValue() >= 0,
            "RECOVERY_SOURCE_PREPARATION_INTEGER");
        return number.longValue();
    }

    private static long arraySize(JsonNode value) {
        require(value.isArray(), "RECOVERY_SOURCE_PREPARATION_ROWS"); return value.size();
    }
}
