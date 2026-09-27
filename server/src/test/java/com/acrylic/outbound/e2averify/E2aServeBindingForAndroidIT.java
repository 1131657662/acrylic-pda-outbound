package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.devices.DeviceCrypto;
import com.acrylic.outbound.inventory.TemporaryLinkConfirmHttpChecks;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Server half of the Android LINK mapping-install L4 evidence: it stays alive until the orchestrator writes the stop
 * file, so the Android unit test can hit this very server and database.
 *
 * <p>Sequence: reuse the already-verified R06 chain (TEMP source -&gt; LINK -&gt; NORMAL_CONFIRM over real HTTP) via
 * {@link TemporaryLinkConfirmHttpChecks}; locate the linked scope and the device/operator that created it; mint one
 * PDA Bearer for that device (the construction attempt 153 used); prove over real HTTP that the linked binding is
 * served to that device; write the handshake file for the Android half; then wait (bounded) for the stop file so the
 * orchestration script can run the Android test against the same server before teardown.
 */
@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aServeBindingForAndroidIT {
    private static final String BASE="http://127.0.0.1:19434/api/v1/";
    private static final Duration STOP_WAIT=Duration.ofMinutes(15);
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test void servesTheLinkedBindingToTheAndroidDeviceUntilStopped() throws Exception {
        var profile=new E2aCrossSourceProfile();var jdbc=profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc,"34");E2aV34Seed.seed(jdbc,profile.attempt.resolve("storage"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission",Integer.class)).isPositive();
        profile.migrate(jdbc,"41");E2aCrossSourceHttpSetup.signing(profile.attempt);
        var handshakeFile=profile.attempt.resolve("android-binding-handshake.json");
        var stop=profile.attempt.resolve("android-binding-stop");
        Files.deleteIfExists(handshakeFile);Files.deleteIfExists(stop);
        try(var context=E2aCrossSourceHttpIT.start(profile,"--outbound.temporary.commands.enabled=true")) {
            TemporaryLinkConfirmHttpChecks.verify((WebApplicationContext)context);
            var linked=linkedBinding(jdbc);
            var token=mintSession(jdbc,linked);
            var page=readBindings(token,linked.epoch());
            var item=itemFor(page,linked.scopeId().toString());
            assertThat(item.path("canonicalOrderId").asText()).as("linked canonical order over real HTTP")
                .isEqualTo(linked.canonicalOrderId().toString());
            assertThat(item.path("mappingVersion").asText()).isEqualTo(linked.mappingVersion());
            assertThat(item.path("mappingOrigin").asText()).isEqualTo("CONTROLLED_LINK");
            assertThat(item.path("installRequired").asBoolean()).as("only a CONTROLLED_LINK installs locally").isTrue();
            assertThat(item.path("orderNo").asText()).isEqualTo(linked.orderNo());
            Files.writeString(handshakeFile,SubmissionJson.canonical(handshake(token,linked,item)),StandardCharsets.UTF_8);
            awaitStop(stop);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "Android LINK L4 server half: the linked binding was served to the device over real HTTP on 19434 and the server stayed up until the stop file appeared\n");
    }

    /** The one scope the LINK chain moved to another canonical order, with the identity that created it. */
    private LinkedBinding linkedBinding(JdbcTemplate jdbc) {
        var rows=jdbc.query("""
            SELECT BIN_TO_UUID(m.scope_id),BIN_TO_UUID(c.canonical_order_id),CAST(c.mapping_version AS CHAR),
              BIN_TO_UUID(c.creator_device_id),BIN_TO_UUID(c.warehouse_id),IFNULL(o.order_no,'null')
            FROM temporary_source_mapping m
            JOIN submission_scope c ON c.id=m.scope_id
            LEFT JOIN outbound_order o ON o.id=c.canonical_order_id
            WHERE m.origin='CONTROLLED_LINK' AND m.mapping_version=c.mapping_version
            ORDER BY m.id DESC LIMIT 1
            """,(rs,row)->new ScopeRow(UUID.fromString(rs.getString(1)),UUID.fromString(rs.getString(2)),
                rs.getString(3),UUID.fromString(rs.getString(4)),UUID.fromString(rs.getString(5)),rs.getString(6)));
        assertThat(rows).as("the LINK chain must leave exactly one CONTROLLED_LINK mapping").hasSize(1);
        var found=rows.getFirst();
        var identity=jdbc.queryForList("""
            SELECT BIN_TO_UUID(a.user_id) user_id FROM temporary_authorization a
            WHERE a.device_id=? ORDER BY a.issued_at DESC LIMIT 1
            """,Ids.bytes(found.deviceId()));
        assertThat(identity).as("the device that created the linked scope must have an authorization").hasSize(1);
        var epoch=jdbc.queryForObject("SELECT CAST(recovery_epoch AS CHAR) FROM server_state WHERE id=1",String.class);
        return new LinkedBinding(found.scopeId(),found.canonicalOrderId(),found.mappingVersion(),found.deviceId(),
            UUID.fromString((String)identity.getFirst().get("user_id")),found.warehouseId(),found.orderNo(),epoch);
    }

    /** One PDA Bearer for exactly this device/operator, minted the way attempt 153 verified. */
    private String mintSession(JdbcTemplate jdbc,LinkedBinding linked) {
        String token=DeviceCrypto.secret();
        jdbc.update("""
            INSERT INTO pda_session(id,device_id,user_id,user_auth_version,device_version,access_hash,refresh_hash,
              access_expires_at,expires_at,created_at)
            VALUES(?,?,?,1,1,?,?,DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 1 DAY),
              DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 2 DAY),UTC_TIMESTAMP(3))
            """,Ids.bytes(UUID.randomUUID()),Ids.bytes(linked.deviceId()),Ids.bytes(linked.userId()),
            DeviceCrypto.sha(token),DeviceCrypto.sha(DeviceCrypto.secret()));
        return token;
    }

    /** The production read route over real HTTP, consumed exactly as the device consumes it (envelope data). */
    private JsonNode readBindings(String token,String epoch) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(BASE+"pda/temporary-source-bindings?limit=50"))
            .timeout(Duration.ofSeconds(30)).header("Authorization","Bearer "+token)
            .header("X-Server-Recovery-Epoch",epoch).GET().build();
        var response=client.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(response.statusCode()).as("GET temporary-source-bindings: "+response.body()).isEqualTo(200);
        return SubmissionJson.parse(response.body()).path("data");
    }

    private JsonNode itemFor(JsonNode page,String scopeId) {
        for(var item:page.path("items")) if(scopeId.equals(item.path("scopeId").asText())) return item;
        throw new AssertionError("the linked scope is missing from the device page: "+page);
    }

    private ObjectNode handshake(String token,LinkedBinding linked,JsonNode served) {
        return SubmissionJson.object().put("baseUrl",BASE).put("token",token)
            .put("deviceId",linked.deviceId().toString()).put("userId",linked.userId().toString())
            .put("warehouseId",linked.warehouseId().toString()).put("serverRecoveryEpoch",linked.epoch())
            .put("userAuthVersion","1")
            .put("scopeId",linked.scopeId().toString()).put("canonicalOrderId",linked.canonicalOrderId().toString())
            .put("mappingVersion",served.path("mappingVersion").asText()).put("mappingOrigin",served.path("mappingOrigin").asText())
            .put("orderNo",served.path("orderNo").asText()).put("phase",served.path("phase").asText())
            .put("clientOrderId",served.path("clientOrderId").asText());
    }

    private void awaitStop(Path stop) throws Exception {
        long deadline=System.nanoTime()+STOP_WAIT.toNanos();
        while(!Files.exists(stop)) {
            assertThat(System.nanoTime()).as("orchestrator must write "+stop+" within "+STOP_WAIT).isLessThan(deadline);
            Thread.sleep(500);
        }
    }

    private record ScopeRow(UUID scopeId,UUID canonicalOrderId,String mappingVersion,UUID deviceId,UUID warehouseId,
        String orderNo) { }
    private record LinkedBinding(UUID scopeId,UUID canonicalOrderId,String mappingVersion,UUID deviceId,UUID userId,
        UUID warehouseId,String orderNo,String epoch) { }
}
