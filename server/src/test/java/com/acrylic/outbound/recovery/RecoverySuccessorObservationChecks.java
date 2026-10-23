package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Pure adverse consumers of an actual single-statement observation; no database row is forged. */
final class RecoverySuccessorObservationChecks {
    private RecoverySuccessorObservationChecks() { }
    private record Adverse(String code, Consumer<ObjectNode> mutate) { }

    static void verify(JdbcTemplate jdbc, RecoveryDatabaseControl external, long baseline, Path evidence) throws Exception {
        String raw = jdbc.queryForObject(RecoverySuccessorAdmissionSql.SELECT, String.class);
        var original = (ObjectNode) RecoveryControlFixture.JSON.readTree(raw);
        RecoverySuccessorAdmissionChecks.save(evidence.resolve("single-statement-observation.json"), original);
        var valid = RecoverySuccessorAdmissionQueries.verify(original, external, baseline);
        assertThat(valid.actual()).isEqualTo(valid.registered());
        var outcomes = new TreeMap<String, String>();
        for (var entry : cases().entrySet()) {
            var changed = original.deepCopy(); entry.getValue().mutate().accept(changed);
            assertThatThrownBy(() -> RecoverySuccessorAdmissionQueries.verify(changed, external, baseline))
                .isInstanceOf(RecoveryControlException.class).hasMessage(entry.getValue().code());
            RecoverySuccessorAdmissionChecks.save(evidence.resolve("adverse-" + entry.getKey() + ".json"), changed);
            outcomes.put(entry.getKey(), entry.getValue().code());
        }
        assertThat(RecoveryControlFixture.JSON.readTree(raw)).isEqualTo(original);
        RecoverySuccessorAdmissionChecks.save(evidence.resolve("adverse-results.json"), outcomes);
    }

    private static Map<String, Adverse> cases() {
        var cases = new LinkedHashMap<String, Adverse>();
        cases.put("actual-uuid", new Adverse("CONTROL_DATABASE_INSTANCE_MISMATCH", v -> v.put("actualServer", UUID.randomUUID().toString())));
        cases.put("actual-schema", new Adverse("CONTROL_DATABASE_INSTANCE_MISMATCH", v -> v.put("actualSchema", "synthetic_other_schema")));
        cases.put("old-prefix", new Adverse("CONTROL_DATABASE_CHAIN_CONFLICT", v -> ((ArrayNode) v.path("projections")).remove(1)));
        cases.put("missing-epoch", new Adverse("CONTROL_DATABASE_EPOCH_CONFLICT", v -> ((ArrayNode) v.path("epochs")).remove(0)));
        cases.put("wrong-initial-normal", new Adverse("CONTROL_SUCCESSOR_NORMAL_CONFLICT", v -> object(v, "initialNormal").put("intent_sha256", "a".repeat(64))));
        cases.put("source-row-time", new Adverse("CONTROL_SUCCESSOR_SOURCE_PIN_MISMATCH", v -> object(v, "binding").put("registered_at", "2001-01-01T00:00:00.000000")));
        cases.put("wrong-source-pin", new Adverse("CONTROL_SUCCESSOR_BINDING_CONFLICT", v -> first(v, "bindings").put("sourceBinding", "b".repeat(64))));
        cases.put("wrong-round-normal", new Adverse("CONTROL_SUCCESSOR_NORMAL_CONFLICT", v -> first(v, "normals").put("intentHash", "c".repeat(64))));
        cases.put("orphan-normal", new Adverse("CONTROL_SUCCESSOR_ORPHAN_FACT", v -> ((ArrayNode) v.path("normals")).add(first(v, "normals").deepCopy().put("revision", 999L))));
        cases.put("missing-normal", new Adverse("CONTROL_SUCCESSOR_NORMAL_REQUIRED", RecoverySuccessorObservationChecks::removeLatestNormal));
        return cases;
    }

    private static ObjectNode object(ObjectNode root, String field) { return (ObjectNode) root.path(field); }
    private static ObjectNode first(ObjectNode root, String field) { return (ObjectNode) root.path(field).get(0); }

    private static void removeLatestNormal(ObjectNode root) {
        var rows = (ArrayNode) root.path("normals"); int latest = 0;
        for (int index = 1; index < rows.size(); index++)
            if (rows.get(index).path("revision").asLong() > rows.get(latest).path("revision").asLong()) latest = index;
        rows.remove(latest);
    }
}
