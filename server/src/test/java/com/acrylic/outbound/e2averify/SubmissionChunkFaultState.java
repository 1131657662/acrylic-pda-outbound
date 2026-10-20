package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/** Raw isolated-database snapshots independent of the guarded application datasource. */
final class SubmissionChunkFaultState {
    final JdbcTemplate raw;
    private final Path storage;

    SubmissionChunkFaultState(JdbcTemplate raw, Path storage) { this.raw = raw; this.storage = storage; }

    ObjectNode database(Set<String> excluded) {
        var result = SubmissionJson.object();
        for (String name : raw.queryForList("SHOW TABLES", String.class)) {
            if (excluded.contains(name)) continue;
            if (!name.matches("[a-z0-9_]+")) throw new IllegalArgumentException("Unexpected isolated table name");
            var rows = raw.queryForList("SELECT * FROM " + name).stream()
                .map(HandoverRollbackFacts::encodeRow).sorted().toList();
            var array = result.putArray(name); rows.forEach(text -> array.add(SubmissionJson.parse(text.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        }
        return result;
    }

    ObjectNode files() throws Exception {
        var result = SubmissionJson.object();
        try (var paths = Files.walk(storage)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList())
                result.put(storage.relativize(path).toString().replace('\\', '/'), ImportFiles.sha256(Files.readAllBytes(path)));
        }
        return result;
    }

    void succeeded(SubmissionChunkFaultWire wire, JsonNode receipt) {
        assertThat(receipt.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(receipt.path("payloadSha256").asText()).isEqualTo(SubmissionJson.sha(wire.input.body()));
        assertThat(receipt.path("actualWeightGrams").asText()).isEqualTo("601500");
        assertThat(receipt.path("localDeletionAllowed").asBoolean(false)).isFalse();
        assertThat(count("submission_payload", wire.input.submissionId())).isEqualTo(1);
        assertThat(count("dispatch_record", wire.input.submissionId())).isEqualTo(2);
        assertThat(raw.queryForObject("SELECT COUNT(*) FROM inventory_ledger WHERE dispatch_id IN "
            + "(SELECT id FROM dispatch_record WHERE submission_id=?)", Integer.class, id(wire))).isEqualTo(2);
    }

    int count(String table, String id) {
        if (!Set.of("submission_payload", "dispatch_record", "submission_chunk").contains(table)) throw new IllegalArgumentException();
        return raw.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE submission_id=?", Integer.class, Ids.bytes(UUID.fromString(id)));
    }

    static byte[] id(SubmissionChunkFaultWire wire) { return Ids.bytes(UUID.fromString(wire.input.submissionId())); }
}
