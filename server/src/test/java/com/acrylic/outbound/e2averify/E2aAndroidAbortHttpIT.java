package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aAndroidAbortHttpIT {
    @Test void abortedBeforePayloadRetainsOriginalFactsWhileRecoveringLocalEvidence() throws Exception {
        E2aAndroidHttpIT.run(true);
    }

    static void unchanged(JdbcTemplate jdbc, JsonNode result, boolean partialUpload) {
        String id = result.path("submissionId").textValue();
        assertThat(jdbc.queryForObject("SELECT receipt_text FROM submission WHERE id=UUID_TO_BIN(?)",
            String.class, id)).isEqualTo(SubmissionJson.canonical(SubmissionJson.parse(
                result.path("submissionReceipt").textValue())));
        var receipt = SubmissionJson.parse(result.path("submissionReceipt").textValue());
        assertThat(receipt.path("payloadPresent").booleanValue()).isFalse();
        assertThat(receipt.path("payloadSha256").isNull()).isTrue();
        for (String table : java.util.List.of("submission_payload", "submitted_capture")) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE submission_id=UUID_TO_BIN(?)",
                Integer.class, id)).as(table).isZero();
        }
        if (partialUpload) E2aAndroidPartialAbortHttpIT.unchanged(jdbc, result);
        else assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission_upload WHERE submission_id=UUID_TO_BIN(?)",
            Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission_scope c JOIN submission s ON s.scope_id=c.id "
            + "WHERE s.id=UUID_TO_BIN(?) AND c.canonical_order_id IS NOT NULL", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM source_evidence_set WHERE submission_id=UUID_TO_BIN(?)",
            Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment k JOIN source_evidence_set e "
            + "ON e.id=k.source_evidence_set_id WHERE e.submission_id=UUID_TO_BIN(?) "
            + "AND k.state='SOURCE_RESERVED' AND k.order_id IS NULL AND k.submission_id IS NULL",
            Integer.class, id)).isEqualTo(1);
    }
}
