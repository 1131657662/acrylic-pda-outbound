package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.E2aAndroidPreparation;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aAndroidHttpIT {
    @Test void androidOriginalRoomRequestsSurviveLostHttpReceiptsAgainstNonemptyMysql() throws Exception {
        run(false);
    }

    static void run(boolean abortBeforePayload) throws Exception {
        run(abortBeforePayload, false);
    }

    static void run(boolean abortBeforePayload, boolean partialUpload) throws Exception {
        var profile = new E2aCrossSourceProfile();
        var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34");
        E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission", Integer.class)).isPositive();
        profile.migrate(jdbc, "36");
        E2aCrossSourceHttpSetup.signing(profile.attempt);
        try (var context = E2aCrossSourceHttpIT.start(profile)) {
            E2aAndroidPreparation.write((WebApplicationContext) context, profile.attempt.resolve("private"));
            if (abortBeforePayload) {
                var input = profile.attempt.resolve("private/android-http-input.json");
                var body = (com.fasterxml.jackson.databind.node.ObjectNode) SubmissionJson.parse(Files.readString(input));
                body.put("abortBeforePayload", true);
                body.put("partialUploadBeforeAbort", partialUpload);
                Files.writeString(input, SubmissionJson.canonical(body));
            }
            E2aAndroidProcess.run(profile.attempt);
            verifyDatabase(profile, jdbc, abortBeforePayload, partialUpload);
            if (abortBeforePayload) {
                var result = SubmissionJson.parse(Files.readString(profile.attempt.resolve("android-result.json")));
                new com.acrylic.outbound.temporary.evidence.E2aAbortedEvidenceChecks(context,
                    result.path("submissionId").textValue()).verify();
                Files.writeString(profile.attempt.resolve("abort-negative-checks.marker"),
                    "missing/duplicate/source/warehouse/audit/stock/capture-owner rejected; original state retained\n");
            }
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Android Room21 freeze/upload/audit and OPEN/RAW/PAGE/STOP/VERIFY over real HTTP; five lost receipts and reopen\n");
    }

    private static void verifyDatabase(E2aCrossSourceProfile profile, org.springframework.jdbc.core.JdbcTemplate jdbc,
        boolean abortBeforePayload, boolean partialUpload)
        throws Exception {
        var result = SubmissionJson.parse(Files.readString(profile.attempt.resolve("android-result.json")));
        String submission = result.path("submissionId").textValue();
        assertThat(result.path("state").textValue()).isEqualTo("PARTIAL");
        var rows = jdbc.queryForList("SELECT BIN_TO_UUID(original_key) original_key,operation_kind,original_response_bytes FROM source_evidence_intake_receipt "
            + "WHERE submission_id=UUID_TO_BIN(?)", submission);
        assertThat(rows.size()).isEqualTo(result.path("receipts").size()).isGreaterThanOrEqualTo(4);
        for (var row : rows) {
            byte[] saved = java.util.Base64.getDecoder().decode(result.path("receipts")
                .path((String) row.get("original_key")).textValue());
            assertThat((byte[]) row.get("original_response_bytes")).isEqualTo(saved);
        }
        assertThat(jdbc.queryForObject("SELECT status FROM submission WHERE id=UUID_TO_BIN(?)", String.class,
            submission)).isEqualTo(abortBeforePayload ? "ABORTED" : "REVIEW_REQUIRED");
        if (abortBeforePayload) E2aAndroidAbortHttpIT.unchanged(jdbc, result, partialUpload);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM source_evidence_verification "
            + "WHERE submission_id=UUID_TO_BIN(?) AND result='VERIFIED_COMPLETE_AT_STOP'", Integer.class, submission)).isEqualTo(1);
    }
}
