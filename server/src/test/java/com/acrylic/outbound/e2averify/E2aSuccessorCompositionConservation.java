package com.acrylic.outbound.e2averify;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Successor registration appends only its exact control rows; every old logical fact remains. */
final class E2aSuccessorCompositionConservation {
    private E2aSuccessorCompositionConservation() { }

    static void registration(ObjectNode before, ObjectNode after) {
        Map<String, Integer> appended = Map.of("recovery_control_projection", 4, "recovery_control_epoch", 1,
            "recovery_successor_binding", 1, "recovery_successor_normal", 1);
        assertThat(after.path("schema")).isEqualTo(before.path("schema"));
        assertThat(after.path("files")).isEqualTo(before.path("files"));
        assertThat(after.path("flywayHistory")).isEqualTo(before.path("flywayHistory"));
        assertThat(after.path("tables").size()).isEqualTo(before.path("tables").size());
        before.path("tables").properties().forEach(entry -> {
            var current = after.path("tables").path(entry.getKey());
            if (appended.containsKey(entry.getKey())) {
                assertThat(current.path("columns")).isEqualTo(entry.getValue().path("columns"));
                var oldRows = new ArrayList<com.fasterxml.jackson.databind.JsonNode>(); entry.getValue().path("rows").forEach(oldRows::add);
                var newRows = new ArrayList<com.fasterxml.jackson.databind.JsonNode>(); current.path("rows").forEach(newRows::add);
                assertThat(newRows).containsAll(oldRows).hasSize(oldRows.size() + appended.get(entry.getKey()));
            } else if (!entry.getKey().equals("server_state")) assertThat(current).as(entry.getKey()).isEqualTo(entry.getValue());
        });
    }
}
