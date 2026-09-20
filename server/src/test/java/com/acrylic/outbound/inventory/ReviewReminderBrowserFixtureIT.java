package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.review.ReviewReminderStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc
class ReviewReminderBrowserFixtureIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired ReviewReminderStore reminders;

    @Test void prepareCurrentInboxWithRealApprovableBatchAndQueryOnlyPagingRows() throws Exception {
        var owner = IdentityFixture.create(jdbc, passwords, "WAREHOUSE_ADMIN");
        var web = new PublicationDriver(mvc, json, owner);
        var fixture = new SubmissionFixture(web, jdbc, true, true);
        assertThat(fixture.submit().path("status").asText()).isEqualTo("REVIEW_REQUIRED");
        jdbc.update("UPDATE submission SET created_at=UTC_TIMESTAMP(3)-INTERVAL 31 MINUTE WHERE id=?", Ids.bytes(UUID.fromString(fixture.id())));
        reminders.createDueReminders(owner.warehouse());
        var paging = new ReminderListRows(fixture);
        for (int i = 0; i < 22; i++) paging.add();
        var other = IdentityFixture.create(jdbc, passwords, "ORDER_CLERK");
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)", Ids.bytes(owner.user().id()), Ids.bytes(other.warehouse()));
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)", Ids.bytes(other.user().id()), Ids.bytes(owner.warehouse()));
        var result = json.createObjectNode().put("username", owner.user().username()).put("password", "Test-password-123")
            .put("warehouseId", owner.warehouse().toString()).put("otherWarehouseId", other.warehouse().toString())
            .put("clerk", other.user().username()).put("orderId", fixture.order).put("submissionId", fixture.id());
        Files.writeString(Path.of("build", "review-reminder-browser-fixture.json"), json.writeValueAsString(result));
    }
}
