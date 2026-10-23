package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.submissions.SubmissionJson;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** JDBC integer widths must not reject an identical persisted identity or hide changed facts. */
class E2aSplitCopyTargetTest {
    private static final String UUID = "00000000-0000-4000-8000-000000000001";

    @Test void jdbcLongValuesMatchTheOriginalJsonReceipt() throws Exception {
        var jdbc = Map.of("uuid", UUID, "port", (Object) 23764L, "logBin", 1L);
        var disk = SubmissionJson.parse("{\"uuid\":\"" + UUID + "\",\"port\":23764,\"logBin\":1}");
        com.fasterxml.jackson.databind.JsonNode unnormalized = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(jdbc);
        assertThat(unnormalized).isNotEqualTo(disk);
        assertThat(E2aSplitCopyTarget.identity(jdbc)).isEqualTo(disk);
        assertThat(E2aSplitCopyTarget.identity(jdbc).path("port").asLong()).isEqualTo(23764L);
    }

    @Test void aChangedPortStillRejectsTheOriginalReceipt() throws Exception {
        var expected = E2aSplitCopyTarget.identity(Map.of("uuid", UUID, "port", 23764));
        var actual = E2aSplitCopyTarget.identity(Map.of("uuid", UUID, "port", 23765L));
        assertThat(actual).isNotEqualTo(expected);
    }

    @Test void aChangedServerUuidStillRejectsTheOriginalReceipt() throws Exception {
        var expected = E2aSplitCopyTarget.identity(Map.of("uuid", UUID, "port", 23764));
        var actual = E2aSplitCopyTarget.identity(Map.of("uuid", "00000000-0000-4000-8000-000000000002", "port", 23764L));
        assertThat(actual).isNotEqualTo(expected);
    }
}
