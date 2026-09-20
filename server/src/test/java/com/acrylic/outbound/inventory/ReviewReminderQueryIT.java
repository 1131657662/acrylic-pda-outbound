package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.review.ReviewReminderStore;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class ReviewReminderQueryIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired ReviewReminderStore reminders;

    @Test void onlyGeneratedCurrentRemindersAppearWithoutChangingBusinessFacts() throws Exception {
        var fixture = pending(admin());
        assertThat(page(fixture.web, "", 20).path("items")).isEmpty();
        remind(fixture);
        var result = page(fixture.web, "", 20);
        assertThat(result.path("items")).hasSize(1);
        assertThat(result.at("/items/0/submissionId").asText()).isEqualTo(fixture.id());
        assertThat(result.at("/items/0/orderId").asText()).isEqualTo(fixture.order);
        assertThat(result.at("/items/0/orderNo").asText()).startsWith("OB");
        assertThat(result.path("nextCursor").isNull()).isTrue();
        assertThat(result.path("hasMore").asBoolean()).isFalse();
        assertThat(fixture.count("dispatch_record")).isZero();
        assertThat(fixture.count("bale_commitment")).isEqualTo(2);
    }

    @ParameterizedTest @ValueSource(strings = {"APPROVE", "REJECT", "ABORT"})
    void handledSubmissionsLeaveCurrentInboxButRetainReminder(String decision) throws Exception {
        var fixture = pending(admin()); remind(fixture);
        if (decision.equals("ABORT")) fixture.abort(); else fixture.review(decision);
        assertThat(page(fixture.web, "", 20).path("items")).isEmpty();
        assertThat(reminders.list(fixture.web.identity().warehouse(), 20)).hasSize(1);
    }

    @Test void historicalSubmissionIsHiddenEvenIfItsOldStatusIsStillPending() throws Exception {
        var fixture = pending(admin()); remind(fixture);
        jdbc.update("UPDATE submission_scope SET current_submission_id=NULL WHERE source_order_id=?", Ids.bytes(UUID.fromString(fixture.order)));
        assertThat(page(fixture.web, "", 20).path("items")).isEmpty();
        assertThat(reminders.list(fixture.web.identity().warehouse(), 20)).hasSize(1);
    }

    @Test void equalTimestampCursorSurvivesRemovalOfItsBoundaryRowFromInbox() throws Exception {
        var web = admin(); var first = pending(web); remind(first);
        new ReminderListRows(first).add();
        jdbc.update("UPDATE submission_review_reminder SET reminded_at='2099-01-01 00:00:00.000' WHERE warehouse_id=?", Ids.bytes(web.identity().warehouse()));
        var page1 = page(web, "", 1); var boundary = page1.at("/items/0/submissionId").asText();
        assertThat(page1.path("hasMore").asBoolean()).isTrue();
        jdbc.update("UPDATE submission SET status='REJECTED' WHERE id=?", Ids.bytes(UUID.fromString(boundary)));
        var page2 = page(web, page1.path("nextCursor").asText(), 1);
        assertThat(page2.path("items")).hasSize(1);
        assertThat(page2.at("/items/0/submissionId").asText()).isNotEqualTo(boundary);
        assertThat(page2.path("hasMore").asBoolean()).isFalse();
        assertThat(page2.path("nextCursor").isNull()).isTrue();
    }

    @Test void permissionAndWarehouseScopeAreEnforced() throws Exception {
        var owner = admin(); var fixture = pending(owner); remind(fixture);
        var stranger = admin();
        mvc.perform(get("/api/v1/review-reminders").param("warehouseId", owner.identity().warehouse().toString())
            .with(user(stranger.identity().user()))).andExpect(status().isNotFound());
        assertThat(page(stranger, "", 20).path("items")).isEmpty();
        var clerk = IdentityFixture.create(jdbc, passwords, "ORDER_CLERK");
        mvc.perform(get("/api/v1/review-reminders").param("warehouseId", clerk.warehouse().toString())
            .with(user(clerk.user()))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/review-reminders").param("warehouseId", owner.identity().warehouse().toString()))
            .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest @ValueSource(strings = {"!invalid", "other-warehouse", "invalid-time", "invalid-id", "too-long"})
    void invalidCursorIsRejectedBeforeQuerying(String kind) throws Exception {
        var web = admin(); var warehouse = web.identity().warehouse().toString();
        String raw = switch (kind) {
            case "other-warehouse" -> UUID.randomUUID() + "|2026-09-19T00:00:00Z|" + UUID.randomUUID();
            case "invalid-time" -> warehouse + "|+10000-01-01T00:00:00Z|" + UUID.randomUUID();
            case "invalid-id" -> warehouse + "|2026-09-19T00:00:00Z|1-1-1-1-1";
            default -> kind;
        };
        String cursor = kind.equals("too-long") ? "x".repeat(201) : kind.equals("!invalid") ? raw
            : Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        web.send(get("/api/v1/review-reminders").param("cursor", cursor)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
    }

    @ParameterizedTest @ValueSource(ints = {0, -1, 201})
    void pageLimitIsBounded(int limit) throws Exception {
        admin().send(get("/api/v1/review-reminders").param("limit", Integer.toString(limit)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PAGE_SIZE"));
    }

    private PublicationDriver admin() {
        return new PublicationDriver(mvc, json, IdentityFixture.create(jdbc, passwords, "WAREHOUSE_ADMIN"));
    }
    private SubmissionFixture pending(PublicationDriver web) throws Exception {
        var fixture = new SubmissionFixture(web, jdbc, false, true);
        assertThat(fixture.submit().path("status").asText()).isEqualTo("REVIEW_REQUIRED"); return fixture;
    }
    private void remind(SubmissionFixture fixture) {
        jdbc.update("UPDATE submission SET created_at=UTC_TIMESTAMP(3)-INTERVAL 31 MINUTE WHERE id=?", Ids.bytes(UUID.fromString(fixture.id())));
        reminders.createDueReminders(fixture.web.identity().warehouse());
    }
    private JsonNode page(PublicationDriver web, String cursor, int limit) throws Exception {
        return web.data(get("/api/v1/review-reminders").param("cursor", cursor).param("limit", Integer.toString(limit)));
    }
}
