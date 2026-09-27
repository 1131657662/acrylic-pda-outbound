package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.devices.DeviceCrypto;
import com.acrylic.outbound.inventory.TemporaryLinkConfirmHttpChecks;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

/**
 * Android LINK mapping install, <b>server half over real HTTP</b>: a TEMP source is linked (reusing the public
 * attempt-123 checks), then the device-scoped binding endpoint is read with a PDA bearer session — no Web session,
 * so the known Web CSRF problem is bypassed. The assertions are exactly the payload the Android
 * {@code SourceBindingRepository} consumes.
 *
 * <p>The Android install code itself stays at the Robolectric/mock tier: reaching HTTP level for it needs a
 * two-process harness that keeps the real server alive while the Android test JVM runs.
 */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aSourceBindingHttpIT {
    private static final String BASE = "http://127.0.0.1:19434/api/v1";
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test void deviceReadsItsLinkedBindingOverRealHttp() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "42"); E2aCrossSourceHttpSetup.signing(profile.attempt);
        try (var context = E2aCrossSourceHttpIT.start(profile, "--outbound.temporary.commands.enabled=true")) {
            // The linked state is produced by the already-verified R06 mechanism (LINK + NORMAL_CONFIRM + counterexample).
            TemporaryLinkConfirmHttpChecks.verify((WebApplicationContext) context);

            var linked = jdbc.queryForMap("""
                SELECT BIN_TO_UUID(c.creator_device_id) device,BIN_TO_UUID(s.operator_id) operator_id,
                  BIN_TO_UUID(c.warehouse_id) warehouse,BIN_TO_UUID(c.id) scope_id,
                  CAST(c.mapping_version AS CHAR) mapping_version
                FROM submission_scope c JOIN submission s ON s.id=c.current_submission_id
                JOIN temporary_source_mapping m ON m.scope_id=c.id AND m.mapping_version=c.mapping_version
                WHERE m.origin='CONTROLLED_LINK' ORDER BY c.id DESC LIMIT 1
                """);
            UUID device = UUID.fromString(linked.get("device").toString());
            UUID operator = UUID.fromString(linked.get("operator_id").toString());
            String warehouse = linked.get("warehouse").toString();
            jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code='PDA_OPERATOR'", Ids.bytes(operator));
            String token = DeviceCrypto.secret();
            jdbc.update("""
                INSERT INTO pda_session(id,device_id,user_id,user_auth_version,device_version,access_hash,refresh_hash,
                  access_expires_at,expires_at,created_at)
                VALUES(?,?,?,1,1,?,?,DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 1 DAY),DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 2 DAY),UTC_TIMESTAMP(3))
                """, Ids.bytes(Ids.next()), Ids.bytes(device), Ids.bytes(operator),
                DeviceCrypto.sha(token), DeviceCrypto.sha(DeviceCrypto.secret()));

            var page = page(token, warehouse);
            Files.writeString(profile.attempt.resolve("binding-payload.json"), page.toString());
            JsonNode installable = null;
            for (JsonNode item : page.path("items")) if (item.path("installRequired").asBoolean()) installable = item;
            assertThat(installable).as("a CONTROLLED_LINK binding must be offered: " + page).isNotNull();
            assertThat(installable.path("mappingVersion").asText()).isEqualTo(linked.get("mapping_version").toString());
            assertThat(installable.path("canonicalOrderId").asText()).isNotEmpty();
            assertThat(installable.path("orderNo").asText()).isNotEmpty();
            assertThat(installable.path("scopeCursor").asText()).isEqualTo(installable.path("scopeId").asText());
            for (String key : List.of("scopeId","clientOrderId","canonicalOrderId","mappingVersion","mappingOrigin",
                "workRound","phase","orderNo","installRequired","scopeCursor"))
                assertThat(installable.has(key)).as("payload key " + key + " (Android parser requires it)").isTrue();
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "server half of the device binding channel verified over real HTTP (PDA bearer)\n");
    }

    private JsonNode page(String token, String warehouse) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(BASE + "/pda/temporary-source-bindings?warehouseId=" + warehouse + "&limit=50"))
            .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + token)
            .header("X-Server-Recovery-Epoch", "1").GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).as("bindings: " + new String(response.body(), StandardCharsets.UTF_8)).isEqualTo(200);
        return SubmissionJson.parse(response.body()).path("data");
    }
}
