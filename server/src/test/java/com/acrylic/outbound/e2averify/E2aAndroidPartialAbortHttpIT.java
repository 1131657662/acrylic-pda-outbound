package com.acrylic.outbound.e2averify;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aAndroidPartialAbortHttpIT {
    @Test void partialBusinessUploadStaysIncompleteAfterIndependentEvidenceRecovery() throws Exception {
        E2aAndroidHttpIT.run(true, true);
    }

    static void unchanged(JdbcTemplate jdbc, JsonNode result) {
        String id = result.path("submissionId").textValue();
        var upload = jdbc.queryForMap("SELECT * FROM submission_upload WHERE submission_id=UUID_TO_BIN(?)", id);
        assertThat(upload.get("payload_sha256")).isEqualTo(result.path("payloadSha256").textValue());
        assertThat(((Number) upload.get("chunk_count")).intValue()).isGreaterThan(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission_chunk WHERE submission_id=UUID_TO_BIN(?)",
            Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT byte_count FROM submission_chunk WHERE submission_id=UUID_TO_BIN(?)",
            Integer.class, id)).isEqualTo(262144);
    }
}
