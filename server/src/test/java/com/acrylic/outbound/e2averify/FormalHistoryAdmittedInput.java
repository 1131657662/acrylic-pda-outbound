package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.assertThat;

/** Only a fresh, identity-checked copy of preserved synthetic FORMAL evidence. */
final class FormalHistoryAdmittedInput {
    final E2aCrossSourceProfile profile;
    final JdbcTemplate raw;
    final JsonNode browser;

    FormalHistoryAdmittedInput() throws Exception {
        profile = new E2aCrossSourceProfile();
        raw = new JdbcTemplate(new DriverManagerDataSource(profile.url, profile.user, System.getenv("E2A_CROSS_PASSWORD")));
        assertThat(raw.queryForObject("SELECT @@datadir", String.class).replace('\\', '/'))
            .isEqualToIgnoringCase(profile.attempt.resolve("data").toString().replace('\\', '/') + "/");
        assertThat(raw.queryForObject("SELECT DATABASE()", String.class)).isEqualTo(profile.database);
        assertThat(raw.queryForObject("SELECT CURRENT_USER()", String.class)).isEqualTo(profile.user + "@127.0.0.1");
        assertThat(raw.queryForObject("SELECT @@port", Integer.class)).isEqualTo(E2aTestPorts.database());
        assertThat(raw.queryForObject("SELECT @@log_bin", Integer.class)).isEqualTo(1);
        assertThat(Files.isRegularFile(profile.attempt.resolve("historical-source.json"))).isTrue();
        browser = SubmissionJson.parse(Files.readAllBytes(profile.attempt.resolve("private/historical-browser.json")));
        assertThat(raw.queryForObject("SELECT COUNT(*) FROM physical_disposition_evidence WHERE applied_submission_id IS NOT NULL", Integer.class))
            .isGreaterThan(0);
    }

    String route(String suffix) {
        return "/corrections/quarantine/" + suffix + "?warehouseId=" + browser.path("warehouseId").asText()
            + "&caseId=" + raw.queryForObject("SELECT BIN_TO_UUID(case_id) FROM quarantine_origin WHERE bale_id=UUID_TO_BIN(?)",
                String.class, browser.path("baleId").asText()) + "&commandType=RECONCILE_QUARANTINE_APPROVE";
    }
}
