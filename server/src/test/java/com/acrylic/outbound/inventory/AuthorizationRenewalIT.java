package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class AuthorizationRenewalIT {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired PasswordEncoder passwords;
    private PublicationDriver web; private PdaTestDriver pda; private JsonNode tokens; private String order;

    @BeforeEach void fixture() throws Exception {
        web = new PublicationDriver(mvc, json, IdentityFixture.create(jdbc, passwords, "WAREHOUSE_ADMIN"));
        pda = new PdaTestDriver(web); order = pda.readyOrder(); tokens = pda.login(pda.register());
    }

    @Test void renewsTheSameAssignmentWithASecondFixedCredentialAndPreservesTheFirst() throws Exception {
        var first = pda.data(pda.claim(order, UUID.randomUUID().toString()), tokens); var oldExpires = first.get("expiresAt").asText();
        var key = UUID.randomUUID().toString(); var renewed = pda.data(renew(first, key), tokens);
        assertThat(renewed.get("id")).isEqualTo(first.get("id"));
        assertThat(renewed.get("offlineAuthorizationId")).isNotEqualTo(first.get("offlineAuthorizationId"));
        assertThat(renewed.get("authorizationSequence").asText()).isEqualTo("1");
        assertThat(Duration.between(Instant.parse(renewed.get("issuedAt").asText()), Instant.parse(renewed.get("expiresAt").asText())))
            .isEqualTo(Duration.ofHours(48));
        assertThat(jdbc.queryForObject("SELECT expires_at FROM offline_authorization WHERE id=?", java.sql.Timestamp.class,
            Ids.bytes(UUID.fromString(first.get("offlineAuthorizationId").asText()))).toInstant().toString()).isEqualTo(oldExpires);
        assertThat(pda.data(renew(first, key), tokens)).isEqualTo(renewed);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM offline_authorization WHERE assignment_id=?", Integer.class,
            Ids.bytes(UUID.fromString(first.get("id").asText())))).isEqualTo(2);
    }

    @Test void expiredCredentialCanBeRenewedWithoutReleasingTheOrder() throws Exception {
        var first = pda.data(pda.claim(order, UUID.randomUUID().toString()), tokens); var id = Ids.bytes(UUID.fromString(first.get("id").asText()));
        jdbc.update("UPDATE order_assignment SET issued_at=TIMESTAMPADD(HOUR,-49,UTC_TIMESTAMP(3)),expires_at=TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(3)) WHERE id=?", id);
        jdbc.update("UPDATE offline_authorization SET issued_at=TIMESTAMPADD(HOUR,-49,UTC_TIMESTAMP(3)),expires_at=TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(3)) WHERE id=?",
            Ids.bytes(UUID.fromString(first.get("offlineAuthorizationId").asText())));
        var renewed = pda.data(renew(first, UUID.randomUUID().toString()), tokens);
        assertThat(renewed.get("authorizationSequence").asText()).isEqualTo("1");
        assertThat(jdbc.queryForObject("SELECT status FROM order_assignment WHERE id=?", String.class, id)).isEqualTo("ACTIVE");
    }

    @Test void pendingInputCannotReplaceTheCurrentCredential() throws Exception {
        var first = pda.data(pda.claim(order, UUID.randomUUID().toString()), tokens);
        pda.send(renew(first, UUID.randomUUID().toString(), 1), tokens).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("OFFLINE_AUTH_PENDING_INPUT"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM offline_authorization WHERE assignment_id=?", Integer.class,
            Ids.bytes(UUID.fromString(first.get("id").asText())))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT revoked_at FROM offline_authorization WHERE id=?", java.sql.Timestamp.class,
            Ids.bytes(UUID.fromString(first.get("offlineAuthorizationId").asText())))).isNull();
    }

    @Test void recalledAssignmentCannotBeRenewed() throws Exception {
        var first = pda.data(pda.claim(order, UUID.randomUUID().toString()), tokens);
        jdbc.update("UPDATE order_assignment SET status='RECALL_PENDING' WHERE id=?", Ids.bytes(UUID.fromString(first.get("id").asText())));
        pda.send(renew(first, UUID.randomUUID().toString()), tokens).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ASSIGNMENT_NOT_RENEWABLE"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM offline_authorization WHERE assignment_id=?", Integer.class,
            Ids.bytes(UUID.fromString(first.get("id").asText())))).isEqualTo(1);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder renew(JsonNode assignment, String key) {
        return renew(assignment, key, 0);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder renew(JsonNode assignment, String key, int pending) {
        ObjectNode body = json.createObjectNode().put("authorizationId", assignment.get("offlineAuthorizationId").asText())
            .put("assignmentEpoch", assignment.get("epoch").asText()).put("orderId", assignment.get("orderId").asText())
            .put("revision", assignment.get("revision").asInt()).put("pendingInputCount", pending);
        try {
            return post("/api/v1/pda/assignments/" + assignment.get("id").asText() + "/renewal")
                .header("Idempotency-Key", key).header("X-Server-Recovery-Epoch", "1")
                .contentType("application/json").content(json.writeValueAsBytes(body));
        } catch (Exception error) { throw new IllegalStateException(error); }
    }
}
