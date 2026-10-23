package com.acrylic.outbound.e2averify;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Offline registration may change only its five protocol tables; all restored business facts stay. */
final class E2aRestoredAdmissionConservation {
    static void registration(ObjectNode before, ObjectNode after) {
        var protocol = Set.of("server_state", "recovery_installation_binding", "recovery_control_epoch",
            "recovery_control_projection", "recovery_normal_admission");
        assertThat(after.path("tables").size()).isEqualTo(before.path("tables").size());
        before.path("tables").fields().forEachRemaining(entry -> {
            if (!protocol.contains(entry.getKey().toLowerCase(Locale.ROOT)))
                assertThat(after.path("tables").path(entry.getKey())).as(entry.getKey()).isEqualTo(entry.getValue());
        });
        for (String key : List.of("schema", "flywayHistory", "files"))
            assertThat(after.path(key)).as(key).isEqualTo(before.path(key));
    }
}
