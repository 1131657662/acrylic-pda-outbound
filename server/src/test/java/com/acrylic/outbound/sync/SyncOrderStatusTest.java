package com.acrylic.outbound.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SyncOrderStatusTest {
    private final SyncOrderStatus statuses = new SyncOrderStatus(new ObjectMapper());

    @Test void handoverPayloadWithStatusProjectsAssigned() {
        assertThat(statuses.from("HANDOVER_ACTIVATED", "{\"status\":\"ASSIGNED\"}"))
            .isEqualTo("ASSIGNED");
    }

    @Test void legacyHandoverPayloadWithoutStatusProjectsAssigned() {
        assertThat(statuses.from("HANDOVER_ACTIVATED", "{\"caseId\":\"legacy\"}"))
            .isEqualTo("ASSIGNED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CORRECTION_EFFECTIVE", "CORRECTION_WITHDRAWN", "CYCLE_REOPENED"})
    void legacyCorrectionPayloadWithoutStatusProjectsCompleted(String eventType) {
        assertThat(statuses.from(eventType, "{\"documentId\":\"legacy\"}"))
            .isEqualTo("COMPLETED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"status\":\"BROKEN\"}", "{\"status\":\"\"}", "{\"status\":null}",
        "{\"status\":1}", "{\"status\":true}", "null", "[]", "\"ASSIGNED\"", "{broken"})
    void invalidHandoverPayloadIsRejected(String payload) {
        assertThatThrownBy(() -> statuses.from("HANDOVER_ACTIVATED", payload))
            .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"status\":null}", "{\"status\":1}", "{\"status\":true}",
        "{\"status\":\"BROKEN\"}", "{broken"})
    void invalidCorrectionPayloadIsRejected(String payload) {
        assertThatThrownBy(() -> statuses.from("CORRECTION_EFFECTIVE", payload))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test void missingStatusOnOtherEventIsRejected() {
        assertThatThrownBy(() -> statuses.from("UNKNOWN_EVENT", "{\"caseId\":\"legacy\"}"))
            .isInstanceOf(IllegalStateException.class);
    }
}
