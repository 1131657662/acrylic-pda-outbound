package com.acrylic.outbound.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
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
class PdaSyncIT {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired PasswordEncoder passwords; @Autowired JdbcTemplate jdbc;
    private PublicationDriver web; private PdaTestDriver pda; private JsonNode tokens; private String order;

    @BeforeEach void fixture() throws Exception {
        web = new PublicationDriver(mvc, json, IdentityFixture.create(jdbc, passwords, "WAREHOUSE_ADMIN"));
    }

    @Test void firstSyncReturnsCurrentBaselineAndStableHighWatermark() throws Exception {
        setup();
        var first = pda.data(get("/api/v1/pda/sync/orders").header("X-Server-Recovery-Epoch", "1"), tokens);
        assertThat(first.get("streamType").asText()).isEqualTo("ORDER");
        assertThat(first.get("baselineId").isTextual()).isTrue();
        assertThat(first.get("baselineComplete").asBoolean()).isTrue();
        assertThat(first.get("baseline").findValue("orderId").asText()).isEqualTo(order);
        assertThat(first.get("events")).isEmpty();
        var repeat = pda.data(get("/api/v1/pda/sync/orders").param("cursor", first.get("nextCursor").asText())
            .header("X-Server-Recovery-Epoch", "1"), tokens);
        assertThat(repeat.get("events")).isEmpty();
        assertThat(repeat.get("nextCursor").asText()).isEqualTo(first.get("nextCursor").asText());
    }

    @Test void newOutboxEventIsPublishedOnceAfterBaselineCursor() throws Exception {
        setup();
        var first = pda.data(get("/api/v1/pda/sync/orders").header("X-Server-Recovery-Epoch", "1"), tokens);
        var orders = new OrderTestDriver(web);
        web.data(orders.command("/" + order + "/cancel", orders.cancellation("2"), UUID.randomUUID().toString()));
        var delta = pda.data(get("/api/v1/pda/sync/orders").param("cursor", first.get("nextCursor").asText())
            .header("X-Server-Recovery-Epoch", "1"), tokens);
        assertThat(delta.get("events")).hasSize(1);
        assertThat(delta.at("/events/0/eventType").asText()).isEqualTo("ORDER_CANCELLED");
        var replay = pda.data(get("/api/v1/pda/sync/orders").param("cursor", first.get("nextCursor").asText())
            .header("X-Server-Recovery-Epoch", "1"), tokens);
        assertThat(replay.at("/events/0/sequence")).isEqualTo(delta.at("/events/0/sequence"));
    }

    @Test void oldEpochAndMalformedCursorAreRejectedWithoutResettingLocalNamespace() throws Exception {
        setup();
        pda.send(get("/api/v1/pda/sync/orders").header("X-Server-Recovery-Epoch", "2"), tokens)
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SERVER_RECOVERY_EPOCH_MISMATCH"));
        pda.send(get("/api/v1/pda/sync/orders").param("cursor", "bad-cursor").header("X-Server-Recovery-Epoch", "1"), tokens)
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SYNC_CURSOR"));
    }

    @Test void fixedBaselinePagesIgnoreLaterCancellationUntilDeltaAndNeverExposeEarlyCursor() throws Exception {
        setup(); var orders = new OrderTestDriver(web);
        String second = orders.create(orders.plan()).get("orderId").asText();
        web.data(orders.command("/" + second + "/publish", orders.release("1", "1"), UUID.randomUUID().toString()));
        var first = pda.data(sync().param("limit", "1"), tokens);
        assertThat(first.get("baselineComplete").asBoolean()).isFalse();
        assertThat(first.get("nextCursor").isNull()).isTrue();
        assertThat(first.get("highWatermark").isTextual()).isTrue();
        String later = first.at("/baseline/0/orderId").asText().equals(order) ? second : order;
        web.data(orders.command("/" + later + "/cancel", orders.cancellation("2"), UUID.randomUUID().toString()));
        var next = pda.data(sync().param("limit", "1").param("baselineId", first.get("baselineId").asText())
            .param("baselineCursor", first.get("baselineNextCursor").asText()), tokens);
        assertThat(next.at("/baseline/0/orderId").asText()).isEqualTo(later);
        assertThat(next.at("/baseline/0/status").asText()).isEqualTo("READY");
        assertThat(next.at("/baseline/0/rowVersion").isTextual()).isTrue();
        assertThat(next.get("highWatermark")).isEqualTo(first.get("highWatermark"));
        var delta = pda.data(sync().param("cursor", next.get("nextCursor").asText()), tokens);
        assertThat(delta.at("/events/0/status").asText()).isEqualTo("CANCELLED");
        assertThat(delta.at("/events/0/sequence").isTextual()).isTrue();
        assertThat(delta.at("/events/0/entityVersion").isTextual()).isTrue();
        assertThat(delta.at("/events/0").has("payload")).isFalse();
    }

    @Test void cursorNamespaceFuturePositionAndExpiredBaselineAreRejected() throws Exception {
        setup(); var first = pda.data(sync(), tokens);
        String raw = new String(java.util.Base64.getUrlDecoder().decode(first.get("nextCursor").asText()), java.nio.charset.StandardCharsets.US_ASCII);
        for (String changed : java.util.List.of(raw.replace(web.identity().warehouse().toString(), UUID.randomUUID().toString()),
                raw.replace("|ORDER|", "|STOCK|"), raw.substring(0, raw.lastIndexOf('|') + 1) + "999999")) {
            String cursor = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(changed.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            pda.send(sync().param("cursor", cursor), tokens).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SYNC_CURSOR"));
        }
        jdbc.update("UPDATE sync_baseline SET expires_at=TIMESTAMPADD(SECOND,1,created_at),created_at=TIMESTAMPADD(HOUR,-1,created_at) WHERE id=UUID_TO_BIN(?)",
            first.get("baselineId").asText());
        jdbc.update("UPDATE sync_baseline SET expires_at=TIMESTAMPADD(SECOND,1,created_at) WHERE id=UUID_TO_BIN(?)", first.get("baselineId").asText());
        pda.send(sync().param("baselineId", first.get("baselineId").asText()), tokens).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SYNC_BASELINE_EXPIRED"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder sync() {
        return get("/api/v1/pda/sync/orders").header("X-Server-Recovery-Epoch", "1");
    }

    private void setup() throws Exception {
        pda = new PdaTestDriver(web); order = pda.readyOrder(); tokens = pda.login(pda.register());
    }
}
