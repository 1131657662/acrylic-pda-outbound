package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.review.ReviewReminderStore;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc
class ReviewReminderIT {
    @Autowired MockMvc mvc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired ReviewReminderStore reminders;

    @Test void createsOneReminderAndOneOrderTimelineEventAfterThirtyMinutes() throws Exception {
        var fixture = fixture();
        JsonNode receipt = fixture.submit();
        assertThat(receipt.get("status").asText()).isEqualTo("REVIEW_REQUIRED");
        age(fixture.id());
        assertThat(reminders.createDueReminders(fixture.web.identity().warehouse())).isEqualTo(1);
        assertThat(reminders.createDueReminders(fixture.web.identity().warehouse())).isZero();
        var row = reminders.list(fixture.web.identity().warehouse(), 20);
        assertThat(row).singleElement().satisfies(reminder -> {
            assertThat(reminder.submissionId().toString()).isEqualTo(fixture.id());
            assertThat(reminder.orderId().toString()).isEqualTo(fixture.order);
            assertThat(reminder.dueAt()).isBefore(reminder.remindedAt());
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_event WHERE event_type='SUBMISSION_REVIEW_REMINDER' AND entity_id=?",
            Integer.class, Ids.bytes(UUID.fromString(fixture.order)))).isEqualTo(1);
    }

    @Test void concurrentSweepsDoNotDuplicateReminderOrTimelineEvent() throws Exception {
        var fixture = fixture(); fixture.submit(); age(fixture.id());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Integer> sweep = () -> { assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                return reminders.createDueReminders(fixture.web.identity().warehouse()); };
            var first = pool.submit(sweep); var second = pool.submit(sweep); start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS)).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission_review_reminder WHERE submission_id=?", Integer.class,
            Ids.bytes(UUID.fromString(fixture.id())))).isEqualTo(1);
    }

    @Test void approvedSubmissionDoesNotReceiveLateReminder() throws Exception {
        var fixture = fixture(); fixture.submit(); age(fixture.id()); fixture.review("APPROVE");
        assertThat(reminders.createDueReminders(fixture.web.identity().warehouse())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission_review_reminder WHERE submission_id=?", Integer.class,
            Ids.bytes(UUID.fromString(fixture.id())))).isZero();
    }

    private SubmissionFixture fixture() throws Exception {
        return new SubmissionFixture(new PublicationDriver(mvc, json, IdentityFixture.create(jdbc, passwords, "WAREHOUSE_ADMIN")), jdbc, false, true);
    }

    private void age(String submission) {
        jdbc.update("UPDATE submission SET created_at=UTC_TIMESTAMP(3)-INTERVAL 31 MINUTE WHERE id=?", Ids.bytes(UUID.fromString(submission)));
    }
}
