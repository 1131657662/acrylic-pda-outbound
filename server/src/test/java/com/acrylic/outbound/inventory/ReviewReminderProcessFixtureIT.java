package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;

/** Creates synthetic pending batches for a separate real API/Worker process rehearsal. */
@SpringBootTest @AutoConfigureMockMvc
class ReviewReminderProcessFixtureIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;

    @Test void prepareIndependentPendingBatchesForReminderProcessChecks() throws Exception {
        var scenarios = json.createObjectNode();
        for (String name : new String[]{"overdue", "fresh", "approved", "rejected"}) {
            scenarios.set(name, pendingBatch());
        }
        Files.writeString(Path.of("build", "review-reminder-process-fixture.json"), json.writeValueAsString(scenarios));
    }

    private ObjectNode pendingBatch() throws Exception {
        var identity = IdentityFixture.create(jdbc, passwords, "WAREHOUSE_ADMIN");
        var fixture = new SubmissionFixture(new PublicationDriver(mvc, json, identity), jdbc, true, true);
        assertThat(fixture.submit().path("status").asText()).isEqualTo("REVIEW_REQUIRED");
        assertThat(fixture.count("dispatch_record")).isZero();
        assertThat(fixture.count("inventory_ledger")).isZero();
        assertThat(fixture.count("bale_commitment")).isEqualTo(2);
        return json.createObjectNode().put("username", identity.user().username()).put("password", "Test-password-123")
            .put("warehouseId", identity.warehouse().toString()).put("orderId", fixture.order)
            .put("submissionId", fixture.id()).put("payloadSha256", SubmissionJson.sha(fixture.body));
    }
}
