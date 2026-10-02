package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.devices.DeviceCrypto;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Synthetic PDA session; real HTTP sync consumption of new and legacy handover/correction outbox events. */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aHandoverSyncHttpIT {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test void newLegacyAndCorrectionEventsPublishOnceAndReplayThroughRealHttp() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34");
        var seed = E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "44"); E2aCrossSourceHttpSetup.signing(profile.attempt);
        String token = session(jdbc, seed);
        try (var context = E2aCrossSourceHttpIT.start(profile)) {
            verify(jdbc, seed, token, profile.attempt);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "real HTTP: new/legacy handover and legacy correction events publish once; cursor replay and projection verified\n");
    }

    private void verify(JdbcTemplate jdbc, Map<String, String> seed, String token, Path attempt) throws Exception {
        var baseline = sync(token, "", attempt.resolve("baseline.json"));
        String cursor = baseline.path("nextCursor").asText();
        assertThat(baseline.path("baselineComplete").asBoolean()).isTrue();
        insert(jdbc, seed, 2, "{\"status\":\"ASSIGNED\",\"caseId\":\"new\"}");
        var modern = sync(token, cursor, attempt.resolve("modern.json"));
        assigned(modern, "2");
        assertThat(sync(token, cursor, attempt.resolve("modern-replay.json"))).isEqualTo(modern);
        String next = modern.path("nextCursor").asText();
        insert(jdbc, seed, 3, "{\"caseId\":\"legacy\"}");
        var legacy = sync(token, next, attempt.resolve("legacy.json"));
        assigned(legacy, "3");
        assertThat(sync(token, next, attempt.resolve("legacy-replay.json"))).isEqualTo(legacy);
        insert(jdbc, seed, 4, "CORRECTION_EFFECTIVE", "{\"documentId\":\"legacy-correction\"}");
        var correction = sync(token, legacy.path("nextCursor").asText(), attempt.resolve("correction.json"));
        assertThat(correction.path("events")).hasSize(1);
        assertThat(correction.at("/events/0/eventType").asText()).isEqualTo("CORRECTION_EFFECTIVE");
        assertThat(correction.at("/events/0/status").asText()).isEqualTo("COMPLETED");
        assertThat(correction.at("/events/0/entityVersion").asText()).isEqualTo("4");
        assertThat(sync(token, legacy.path("nextCursor").asText(), attempt.resolve("correction-replay.json")))
            .isEqualTo(correction);
        assertThat(sync(token, correction.path("nextCursor").asText(), attempt.resolve("after.json")).path("events")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_sync_outbox WHERE published_seq IS NOT NULL", Integer.class))
            .isEqualTo(3);
        var projection = jdbc.queryForMap("SELECT status,row_version FROM sync_order_projection WHERE order_id=UUID_TO_BIN(?)",
            seed.get("orderId"));
        assertThat(projection.get("status")).isEqualTo("COMPLETED");
        assertThat(((Number) projection.get("row_version")).longValue()).isEqualTo(4);
        Files.writeString(attempt.resolve("projection.txt"), projection.toString());
    }

    private void assigned(JsonNode response, String version) {
        assertThat(response.path("events")).hasSize(1);
        assertThat(response.at("/events/0/eventType").asText()).isEqualTo("HANDOVER_ACTIVATED");
        assertThat(response.at("/events/0/status").asText()).isEqualTo("ASSIGNED");
        assertThat(response.at("/events/0/entityVersion").asText()).isEqualTo(version);
        assertThat(response.at("/events/0/sequence").asText()).isNotBlank();
    }

    private String session(JdbcTemplate jdbc, Map<String, String> seed) {
        jdbc.update("INSERT INTO user_role SELECT UUID_TO_BIN(?),id FROM role WHERE code='PDA_OPERATOR'", seed.get("operatorId"));
        String token = DeviceCrypto.secret();
        jdbc.update("""
            INSERT INTO pda_session(id,device_id,user_id,user_auth_version,device_version,access_hash,refresh_hash,
              access_expires_at,expires_at,created_at)
            VALUES(?,UUID_TO_BIN(?),UUID_TO_BIN(?),1,1,?,?,DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 1 DAY),
              DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 2 DAY),UTC_TIMESTAMP(3))
            """, Ids.bytes(Ids.next()), seed.get("deviceId"), seed.get("operatorId"),
            DeviceCrypto.sha(token), DeviceCrypto.sha(DeviceCrypto.secret()));
        return token;
    }

    private void insert(JdbcTemplate jdbc, Map<String, String> seed, long version, String payload) {
        insert(jdbc, seed, version, "HANDOVER_ACTIVATED", payload);
    }

    private void insert(JdbcTemplate jdbc, Map<String, String> seed, long version, String eventType, String payload) {
        jdbc.update("""
            INSERT INTO order_sync_outbox(id,recovery_epoch,warehouse_id,order_id,entity_version,event_type,payload,created_at)
            VALUES(?,1,UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,?,UTC_TIMESTAMP(3))
            """, Ids.bytes(Ids.next()), seed.get("warehouseId"), seed.get("orderId"), version, eventType, payload);
    }

    private JsonNode sync(String token, String cursor, Path evidence) throws Exception {
        String query = cursor.isBlank() ? "" : "?cursor=" + java.net.URLEncoder.encode(cursor, java.nio.charset.StandardCharsets.UTF_8);
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1/pda/sync/orders" + query))
            .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + token)
            .header("X-Server-Recovery-Epoch", "1").GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        Files.write(evidence, response.body(), StandardOpenOption.CREATE_NEW);
        assertThat(response.statusCode()).as(evidence.toString()).isEqualTo(200);
        return SubmissionJson.parse(response.body()).path("data");
    }
}
