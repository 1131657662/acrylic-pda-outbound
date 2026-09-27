package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.correction.CorrectionCommandController;
import com.acrylic.outbound.devices.DeviceCrypto;
import com.acrylic.outbound.inventory.TemporaryLinkConfirmHttpChecks;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * R07-3 step 2 wrap-up + step 3 opening, over real HTTP and a real MySQL: the switch is the whole boundary (no route
 * with the default, open route when enabled) and CREATE freezes a SUBMITTED document whose line values are recomputed
 * server-side instead of trusted from the client.
 *
 * <p>The confirmed order comes from the already-verified R06 chain ({@link TemporaryLinkConfirmHttpChecks}), which
 * leaves dispatch records with ledger rows - exactly the state a first correction starts from. Nothing in the R06 or
 * R07-1 surface is modified; this IT only reads their result.
 */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aCorrectionCreateIT {
    private static final String ROUTE = "http://127.0.0.1:19434/api/v1/corrections";
    private static final String BEFORE_COUNT = "CAST(IFNULL((SELECT -SUM(l.quantity_delta) FROM inventory_ledger l"
        + " WHERE l.dispatch_id=d.id),1) AS CHAR)";
    private static final String BEFORE_WEIGHT = "CAST(IFNULL((SELECT -SUM(l.weight_delta_g) FROM inventory_ledger l"
        + " WHERE l.dispatch_id=d.id),d.weight_g) AS CHAR)";
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test void correctionCreateIsClosedByDefaultAndFreezesServerRecomputedLinesWhenEnabled() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "45"); E2aCrossSourceHttpSetup.signing(profile.attempt);
        var order = confirmedOrder(profile, jdbc);
        assertThat(order.dispatchIds()).as("the R06 chain must leave dispatches to correct").isNotEmpty();
        var token = mintSession(jdbc, order);
        grantCorrect(jdbc, order.operatorId());
        var expected = expectedBlock(order);
        var body = createBody(order, expected);

        // (a) default switch: the correction route is not assembled at all.
        try (var closed = E2aCrossSourceHttpIT.start(profile, "--outbound.temporary.commands.enabled=true")) {
            assertThat(closed.getBeanNamesForType(CorrectionCommandController.class))
                .as("no correction route by default").isEmpty();
            assertThat(post(token, body).statusCode()).as("route absent by default").isEqualTo(404);
        }
        // (b) enabled: CREATE is open and freezes the document.
        try (var open = E2aCrossSourceHttpIT.start(profile, "--outbound.temporary.commands.enabled=true",
                "--outbound.corrections.commands.enabled=true")) {
            assertThat(open.getBeanNamesForType(CorrectionCommandController.class)).hasSize(1);
            var response = post(token, body);
            assertThat(response.statusCode()).as(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo(200);
            var receipt = SubmissionJson.parse(response.body());
            assertThat(receipt.path("status").asText()).isEqualTo("RECORDED");
            assertThat(receipt.path("commandType").asText()).isEqualTo("CREATE");
            for (String key : new String[]{"writeConfirmationAvailable", "releaseAllowed", "mayResumeScanning",
                "stockReleased", "localDeletionAllowed"})
                assertThat(receipt.path("capabilities").path(key).asBoolean()).as(key).isFalse();
            var decision = receipt.path("businessDecision");
            String documentId = decision.path("documentId").asText();
            assertThat(decision.path("status").asText()).isEqualTo("SUBMITTED");
            assertThat(decision.path("lineCount").asInt()).isEqualTo(order.dispatchIds().size());
            assertThat(jdbc.queryForObject("SELECT status FROM correction_document WHERE id=UUID_TO_BIN(?)",
                String.class, documentId)).isEqualTo("SUBMITTED");
            // The "before" side must equal the server's own facts, not whatever the client claimed.
            for (var line : decision.path("lines")) {
                String dispatchId = line.path("dispatchId").asText();
                assertThat(line.path("beforeCount").asText()).isEqualTo(before(jdbc, dispatchId, BEFORE_COUNT));
                assertThat(line.path("beforeWeightG").asText()).isEqualTo(before(jdbc, dispatchId, BEFORE_WEIGHT));
                assertThat(Long.parseLong(line.path("countDelta").asText())).isNegative();
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM correction_line WHERE document_id=UUID_TO_BIN(?)",
                Integer.class, documentId)).isEqualTo(order.dispatchIds().size());
            // (c) the same original key replays the stored bytes and adds no second document.
            assertThat(post(token, body).body()).isEqualTo(response.body());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM correction_document", Integer.class)).isEqualTo(1);
            // (d) counterexample: a target equal to the server's before-value is refused, never silently accepted.
            String firstDispatch = order.dispatchIds().getFirst();
            var unchanged = createBody(order, expected).put("commandId", UUID.randomUUID().toString());
            ((ObjectNode) unchanged.path("lines").get(0))
                .put("targetCount", before(jdbc, firstDispatch, BEFORE_COUNT))
                .put("targetWeightG", before(jdbc, firstDispatch, BEFORE_WEIGHT));
            var refused = post(token, unchanged);
            assertThat(refused.statusCode()).as(new String(refused.body(), StandardCharsets.UTF_8)).isEqualTo(409);
            assertThat(SubmissionJson.parse(refused.body()).path("code").asText())
                .isEqualTo("CORRECTION_LINE_TARGET_UNCHANGED");
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "R07-3: correction route absent by default; with the switch on, CREATE froze a SUBMITTED document whose line before-values were recomputed server-side, replay was byte-identical and an unchanged target was refused\n");
    }

    /** Runs the verified R06 chain, then collects what a CREATE needs: order, dispatches, operator, warehouse. */
    private Order confirmedOrder(E2aCrossSourceProfile profile, JdbcTemplate jdbc) throws Exception {
        try (var context = E2aCrossSourceHttpIT.start(profile, "--outbound.temporary.commands.enabled=true")) {
            TemporaryLinkConfirmHttpChecks.verify((WebApplicationContext) context);
        }
        var row = jdbc.queryForMap("""
            SELECT BIN_TO_UUID(v.order_id) orderId,CAST(v.result_version AS CHAR) resultVersion,
              CAST(v.actual_count AS CHAR) resultCount,CAST(v.weight_g AS CHAR) resultWeight,
              BIN_TO_UUID(s.operator_id) operatorId,BIN_TO_UUID(s.freeze_device_id) deviceId,
              BIN_TO_UUID(o.warehouse_id) warehouseId,CAST(u.auth_version AS CHAR) authVersion
            FROM order_result_version v JOIN outbound_order o ON o.id=v.order_id
            JOIN submission s ON s.id=v.submission_id JOIN app_user u ON u.id=s.operator_id
            WHERE o.current_result_version=v.result_version LIMIT 1
            """);
        String orderId = String.valueOf(row.get("orderId"));
        var dispatchIds = jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM dispatch_record WHERE order_id=UUID_TO_BIN(?)"
            + " ORDER BY order_scan_seq", String.class, orderId);
        return new Order(orderId, String.valueOf(row.get("resultVersion")), String.valueOf(row.get("resultCount")),
            String.valueOf(row.get("resultWeight")), UUID.fromString(String.valueOf(row.get("operatorId"))),
            UUID.fromString(String.valueOf(row.get("deviceId"))), String.valueOf(row.get("warehouseId")),
            String.valueOf(row.get("authVersion")), List.copyOf(dispatchIds));
    }

    private String before(JdbcTemplate jdbc, String dispatchId, String expression) {
        return jdbc.queryForObject("SELECT " + expression + " FROM dispatch_record d WHERE d.id=UUID_TO_BIN(?)",
            String.class, dispatchId);
    }

    private ObjectNode expectedBlock(Order order) {
        return SubmissionJson.object().put("orderId", order.orderId())
            .put("currentResultVersion", order.resultVersion())
            .put("currentResultEffectiveCount", order.resultCount())
            .put("currentResultEffectiveWeightG", order.resultWeight())
            .put("userAuthVersion", order.authVersion());
    }

    /** One WEIGHT_CORRECTION line per dispatch, each asking for zero packages (the design's return-to-zero shape). */
    private ObjectNode createBody(Order order, ObjectNode expected) {
        var body=(ObjectNode) SubmissionJson.object().put("commandVersion", 1).put("commandType", "CREATE")
            .put("commandId", UUID.randomUUID().toString()).put("transportRecoveryEpoch", "1")
            .put("warehouseId", order.warehouseId()).put("orderId", order.orderId()).put("type", "WEIGHT_CORRECTION")
            .put("reason", "主管核对实物后更正重量");
        var lines=body.putArray("lines");
        for (String dispatchId : order.dispatchIds())
            lines.add(SubmissionJson.object().put("dispatchId", dispatchId).put("targetCount", "0").put("targetWeightG", "0"));
        body.set("expected", expected);
        return body;
    }

    private String mintSession(JdbcTemplate jdbc, Order order) {
        String token = DeviceCrypto.secret();
        jdbc.update("""
            INSERT INTO pda_session(id,device_id,user_id,user_auth_version,device_version,access_hash,refresh_hash,
              access_expires_at,expires_at,created_at)
            VALUES(?,?,?,1,1,?,?,DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 1 DAY),
              DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 2 DAY),UTC_TIMESTAMP(3))
            """, Ids.bytes(UUID.randomUUID()), Ids.bytes(order.deviceId()), Ids.bytes(order.operatorId()),
            DeviceCrypto.sha(token), DeviceCrypto.sha(DeviceCrypto.secret()));
        return token;
    }

    /** V45 registers outbound:correct; this binds it to the acting user, as the R06 checks do for LINK. */
    private void grantCorrect(JdbcTemplate jdbc, UUID actor) {
        UUID role = Ids.next();
        jdbc.update("INSERT INTO role VALUES(?,?,?)", Ids.bytes(role), "CORR_" + role.toString().substring(24), "隔离更正权限");
        jdbc.update("INSERT INTO role_permission SELECT ?,id FROM permission WHERE code='outbound:correct'", Ids.bytes(role));
        jdbc.update("INSERT INTO user_role VALUES(?,?)", Ids.bytes(actor), Ids.bytes(role));
    }

    private HttpResponse<byte[]> post(String token, JsonNode command) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(ROUTE))
            .timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + token)
            .POST(HttpRequest.BodyPublishers.ofByteArray(SubmissionJson.canonical(command).getBytes(StandardCharsets.UTF_8)))
            .build();
        return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    private record Order(String orderId, String resultVersion, String resultCount, String resultWeight,
        UUID operatorId, UUID deviceId, String warehouseId, String authVersion, List<String> dispatchIds) { }
}
