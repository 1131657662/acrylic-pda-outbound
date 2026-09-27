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
 * R07-3 correction command chain over real HTTP and a real MySQL, on the verified R06 PDA channel
 * ({@code /api/v1/pda/corrections}, Bearer session, CSRF disabled for that prefix).
 *
 * <p>Covered so far: the switch boundary (absent route by default), CREATE (document + lines with server-recomputed
 * "before" values, replay, unchanged-target counterexample) and APPROVE (supervisor gate, SUBMITTED -&gt; APPROVED,
 * replay, double-approval refusal). Effects, the result version and the ledger arrive with steps 4-5.
 *
 * <p>The confirmed order comes from the already-verified R06 chain ({@link TemporaryLinkConfirmHttpChecks}), which
 * leaves dispatch records with ledger rows - exactly the state a first correction starts from. Nothing in the R06 or
 * R07-1 surface is modified; this IT only reads their result.
 */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aCorrectionChainIT {
    private static final String ROUTE = "http://127.0.0.1:19434/api/v1/pda/corrections";
    private static final String BEFORE_COUNT = "CAST(IFNULL((SELECT -SUM(l.quantity_delta) FROM inventory_ledger l"
        + " WHERE l.dispatch_id=d.id),1) AS CHAR)";
    private static final String BEFORE_WEIGHT = "CAST(IFNULL((SELECT -SUM(l.weight_delta_g) FROM inventory_ledger l"
        + " WHERE l.dispatch_id=d.id),d.weight_g) AS CHAR)";
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test void correctionChainIsClosedByDefaultAndFreezesThenApprovesWithServerRecomputedLines() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "46"); E2aCrossSourceHttpSetup.signing(profile.attempt);
        var order = confirmedOrder(profile, jdbc);
        assertThat(order.dispatchIds()).as("the R06 chain must leave dispatches to correct").isNotEmpty();
        var notes = new StringBuilder("验证层级=真服 19434 + 真 HTTP（PDA 通道）+ 真 MySQL\n");
        var token = mintSession(jdbc, order);
        grant(jdbc, order.operatorId(), "outbound:correct");
        var expected = expectedBlock(order, "1");
        var create = createBody(order, expected, "0", "0");

        // (a) default switch: the correction route is not assembled at all.
        try (var closed = E2aCrossSourceHttpIT.start(profile)) {
            assertThat(closed.getBeanNamesForType(CorrectionCommandController.class))
                .as("no correction route by default").isEmpty();
            int status = post(token, create).statusCode();
            notes.append("①开关关闭 -> ").append(status).append(" (期望 404)\n");
            assertThat(status).as("① switch off must be an absent route").isEqualTo(404);
        }
        // (b) enabled: CREATE then APPROVE.
        try (var open = E2aCrossSourceHttpIT.start(profile, "--outbound.corrections.commands.enabled=true")) {
            assertThat(open.getBeanNamesForType(CorrectionCommandController.class)).hasSize(1);
            int anonymous = post(DeviceCrypto.secret(), create).statusCode();
            notes.append("②开关开启+无效会话 -> ").append(anonymous).append(" (期望 401)\n");
            assertThat(anonymous).as("② no session").isEqualTo(401);

            var response = post(token, create);
            notes.append("③CREATE -> ").append(response.statusCode()).append(' ').append(shortBody(response.body())).append('\n');
            assertThat(response.statusCode()).as(shortBody(response.body())).isEqualTo(200);
            var receipt = SubmissionJson.parse(response.body());
            assertThat(receipt.path("status").asText()).isEqualTo("RECORDED");
            assertThat(receipt.path("commandType").asText()).isEqualTo("CREATE");
            for (String key : new String[]{"writeConfirmationAvailable", "releaseAllowed", "mayResumeScanning",
                "stockReleased", "localDeletionAllowed"})
                assertThat(receipt.path("capabilities").path(key).asBoolean()).as(key).isFalse();
            var decision = receipt.path("businessDecision");
            String documentId = decision.path("documentId").asText();
            String documentSha256 = decision.path("documentSha256").asText();
            assertThat(decision.path("status").asText()).isEqualTo("SUBMITTED");
            assertThat(decision.path("lineCount").asInt()).isEqualTo(order.dispatchIds().size());
            assertThat(jdbc.queryForObject("SELECT status FROM correction_document WHERE id=UUID_TO_BIN(?)",
                String.class, documentId)).isEqualTo("SUBMITTED");
            for (var line : decision.path("lines")) {
                String dispatchId = line.path("dispatchId").asText();
                assertThat(line.path("beforeCount").asText()).isEqualTo(before(jdbc, dispatchId, BEFORE_COUNT));
                assertThat(line.path("beforeWeightG").asText()).isEqualTo(before(jdbc, dispatchId, BEFORE_WEIGHT));
                assertThat(Long.parseLong(line.path("countDelta").asText())).isNegative();
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM correction_line WHERE document_id=UUID_TO_BIN(?)",
                Integer.class, documentId)).isEqualTo(order.dispatchIds().size());
            assertThat(post(token, create).body()).as("CREATE replay returns the stored original").isEqualTo(response.body());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM correction_document", Integer.class)).isEqualTo(1);
            notes.append("④CREATE 断言：文档 SUBMITTED、before 值等于服务端事实、回放一致：PASS\n");

            String firstDispatch = order.dispatchIds().getFirst();
            var refused = post(token, createBody(order, expectedBlock(order, "1"),
                before(jdbc, firstDispatch, BEFORE_COUNT), before(jdbc, firstDispatch, BEFORE_WEIGHT)));
            notes.append("⑤目标值等于 before -> ").append(refused.statusCode()).append(' ')
                .append(SubmissionJson.parse(refused.body()).path("code").asText()).append(" (期望 409 TARGET_UNCHANGED)\n");
            assertThat(refused.statusCode()).isEqualTo(409);
            assertThat(SubmissionJson.parse(refused.body()).path("code").asText())
                .isEqualTo("CORRECTION_LINE_TARGET_UNCHANGED");

            // (c) The supervisor gate must really deny: V45 binds correction:approve to WAREHOUSE_ADMIN, so this actor
            // already holds it. Revoke exactly his bindings, prove 403, then restore them byte-for-byte.
            var approve = approveBody(order, documentId, documentSha256, "SUBMITTED", order.resultVersion());
            var supervisorRoles = jdbc.queryForList("SELECT BIN_TO_UUID(rp.role_id) FROM role_permission rp"
                + " JOIN permission p ON p.id=rp.permission_id JOIN user_role ur ON ur.role_id=rp.role_id"
                + " WHERE ur.user_id=? AND p.code='correction:approve'", String.class, Ids.bytes(order.operatorId()));
            assertThat(supervisorRoles).as("V45 must bind correction:approve to this actor's role").isNotEmpty();
            jdbc.update("DELETE rp FROM role_permission rp JOIN permission p ON p.id=rp.permission_id"
                + " JOIN user_role ur ON ur.role_id=rp.role_id WHERE ur.user_id=? AND p.code='correction:approve'",
                Ids.bytes(order.operatorId()));
            var denied = post(token, approve);
            notes.append("⑥APPROVE（撤销 correction:approve 后）-> ").append(denied.statusCode()).append(' ')
                .append(SubmissionJson.parse(denied.body()).path("code").asText()).append(" (期望 403)\n");
            assertThat(denied.statusCode()).isEqualTo(403);
            assertThat(SubmissionJson.parse(denied.body()).path("code").asText()).isEqualTo("CORRECTION_PERMISSION_DENIED");
            assertThat(jdbc.queryForObject("SELECT status FROM correction_document WHERE id=UUID_TO_BIN(?)",
                String.class, documentId)).as("a denied approval changes nothing").isEqualTo("SUBMITTED");
            for (String role : supervisorRoles)
                jdbc.update("INSERT INTO role_permission VALUES(?,(SELECT id FROM permission WHERE code='correction:approve'))",
                    Ids.bytes(UUID.fromString(role)));

            // (d) with the supervisor code the document is approved and effected in one transaction (§3).
            var approved = post(token, approve);
            notes.append("⑦APPROVE（有 correction:approve）-> ").append(approved.statusCode()).append(' ')
                .append(shortBody(approved.body())).append('\n');
            assertThat(approved.statusCode()).as(shortBody(approved.body())).isEqualTo(200);
            var approvedReceipt = SubmissionJson.parse(approved.body());
            var effect = approvedReceipt.path("businessDecision");
            assertThat(approvedReceipt.path("commandType").asText()).isEqualTo("APPROVE");
            assertThat(effect.path("status").asText()).isEqualTo("EFFECTIVE");
            assertThat(effect.path("previousResultVersion").asText()).isEqualTo("1");
            assertThat(effect.path("resultVersion").asText()).isEqualTo("2");
            assertThat(effect.path("effectiveCount").asText()).isEqualTo("0");
            assertThat(effect.path("effectiveWeightG").asText()).isEqualTo("0");
            assertThat(effect.path("scanCoverage").asInt()).isEqualTo(order.dispatchIds().size());
            assertThat(jdbc.queryForObject("SELECT status FROM correction_document WHERE id=UUID_TO_BIN(?)",
                String.class, documentId)).isEqualTo("EFFECTIVE");
            assertThat(jdbc.queryForObject("SELECT CAST(effective_result_version AS CHAR) FROM correction_document"
                + " WHERE id=UUID_TO_BIN(?)", String.class, documentId)).isEqualTo("2");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM correction_document WHERE approved_by IS NOT NULL",
                Integer.class)).isEqualTo(1);
            // steps 4-5: the new version, the pointer, the effects, the correction ledger and the scan snapshot.
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_result_version WHERE order_id=UUID_TO_BIN(?)"
                + " AND result_version=2 AND actual_count=0 AND weight_g=0 AND submission_id IS NULL"
                + " AND source_correction_id=UUID_TO_BIN(?) AND original_submission_id IS NOT NULL",
                Integer.class, order.orderId(), documentId))
                .as("v2 is a correction-driven version: 0 包/0g, no submission of its own, linked to the document")
                .isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT CAST(current_result_version AS CHAR) FROM outbound_order"
                + " WHERE id=UUID_TO_BIN(?)", String.class, order.orderId())).isEqualTo("2");
            assertThat(jdbc.queryForObject("SELECT CAST(result_version AS CHAR) FROM order_current_result"
                + " WHERE order_id=UUID_TO_BIN(?)", String.class, order.orderId())).isEqualTo("2");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM correction_effect WHERE order_id=UUID_TO_BIN(?)"
                + " AND result_version=2 AND count_delta=-1 AND weight_delta_g<0",
                Integer.class, order.orderId())).as("one effect per line, deltas from the frozen line")
                .isEqualTo(order.dispatchIds().size());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM correction_ledger WHERE entry_kind='REVERSAL'"
                + " AND quantity_delta=-1 AND weight_delta_g<0 AND reverses_entry_id IS NULL"
                + " AND business_key LIKE 'CORR:%:REV'", Integer.class))
                .as("a count reversal carries the correction's own delta; a first correction reverses no earlier entry")
                .isEqualTo(order.dispatchIds().size());
            assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT business_key) FROM correction_ledger",
                Integer.class)).isEqualTo(order.dispatchIds().size());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_effective_scan WHERE order_id=UUID_TO_BIN(?)"
                + " AND result_version=2 AND is_effective=0 AND effective_weight_g=0",
                Integer.class, order.orderId())).as("the snapshot covers every original dispatch, zeroed ones included")
                .isEqualTo(order.dispatchIds().size());
            assertThat(jdbc.queryForObject("SELECT IFNULL(SUM(actual_count),-1) FROM order_effective_line"
                + " WHERE order_id=UUID_TO_BIN(?) AND result_version=2", Integer.class, order.orderId()))
                .as("effective lines are the scan aggregate, so 0/0 as well").isZero();
            assertThat(post(token, approve).body()).as("APPROVE replay returns the stored original")
                .isEqualTo(approved.body());
            // (e) a second correction on the moved order must not be accepted against the old version.
            var stale = post(token, freshKey(createBody(order, expectedBlock(order, "1"), "0", "0")));
            notes.append("⑧再次更正（仍按 v1 预期）-> ").append(stale.statusCode()).append(' ')
                .append(codeOf(stale.body())).append(" (期望 409 CONTEXT_CHANGED)\n");
            assertThat(stale.statusCode()).isEqualTo(409);
            assertThat(codeOf(stale.body())).isEqualTo("CORRECTION_CONTEXT_CHANGED");
            // (f) and a repeated approval under a fresh key is refused the same way.
            var again = post(token, approveBody(order, documentId, documentSha256, "SUBMITTED",
                order.resultVersion()));
            notes.append("⑨重复审批（新原键、旧 expected）-> ").append(again.statusCode()).append(' ')
                .append(codeOf(again.body())).append(" (期望 409 CONTEXT_CHANGED)\n");
            assertThat(again.statusCode()).isEqualTo(409);
            assertThat(codeOf(again.body())).isEqualTo("CORRECTION_CONTEXT_CHANGED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_event WHERE entity_type='CORRECTION_COMMAND'",
                Integer.class)).as("one audit event per accepted receipt").isEqualTo(2);
        }
        Files.writeString(profile.attempt.resolve("correction-chain.txt"), notes.toString());
        Files.writeString(profile.attempt.resolve("test-completed.marker"), notes.toString());
    }

    /** Runs the verified R06 chain, then collects what a correction needs: order, dispatches, operator, device. */
    private Order confirmedOrder(E2aCrossSourceProfile profile, JdbcTemplate jdbc) throws Exception {
        try (var context = E2aCrossSourceHttpIT.start(profile, "--outbound.temporary.commands.enabled=true")) {
            TemporaryLinkConfirmHttpChecks.verify((WebApplicationContext) context);
        }
        var row = jdbc.queryForMap("""
            SELECT BIN_TO_UUID(v.order_id) orderId,CAST(v.result_version AS CHAR) resultVersion,
              CAST(v.actual_count AS CHAR) resultCount,CAST(v.weight_g AS CHAR) resultWeight,
              BIN_TO_UUID(o.warehouse_id) warehouseId,BIN_TO_UUID(s.operator_id) operatorId,
              BIN_TO_UUID(s.freeze_device_id) deviceId
            FROM order_result_version v JOIN outbound_order o ON o.id=v.order_id
            JOIN submission s ON s.id=v.submission_id
            WHERE o.current_result_version=v.result_version LIMIT 1
            """);
        String orderId = String.valueOf(row.get("orderId"));
        var dispatchIds = jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM dispatch_record WHERE order_id=UUID_TO_BIN(?)"
            + " ORDER BY order_scan_seq", String.class, orderId);
        return new Order(orderId, String.valueOf(row.get("resultVersion")), String.valueOf(row.get("resultCount")),
            String.valueOf(row.get("resultWeight")), String.valueOf(row.get("warehouseId")),
            UUID.fromString(String.valueOf(row.get("operatorId"))), UUID.fromString(String.valueOf(row.get("deviceId"))),
            List.copyOf(dispatchIds));
    }

    private String before(JdbcTemplate jdbc, String dispatchId, String expression) {
        return jdbc.queryForObject("SELECT " + expression + " FROM dispatch_record d WHERE d.id=UUID_TO_BIN(?)",
            String.class, dispatchId);
    }

    private ObjectNode expectedBlock(Order order, String authVersion) {
        return SubmissionJson.object().put("orderId", order.orderId())
            .put("currentResultVersion", order.resultVersion())
            .put("currentResultEffectiveCount", order.resultCount())
            .put("currentResultEffectiveWeightG", order.resultWeight())
            .put("userAuthVersion", authVersion);
    }

    /** One WEIGHT_CORRECTION line per dispatch; the design's return-to-zero shape unless targets are overridden. */
    private ObjectNode createBody(Order order, ObjectNode expected, String targetCount, String targetWeight) {
        var body = (ObjectNode) SubmissionJson.object().put("commandVersion", 1).put("commandType", "CREATE")
            .put("commandId", UUID.randomUUID().toString()).put("transportRecoveryEpoch", "1")
            .put("warehouseId", order.warehouseId()).put("orderId", order.orderId()).put("type", "WEIGHT_CORRECTION")
            .put("reason", "主管核对实物后更正重量");
        var lines = body.putArray("lines");
        for (String dispatchId : order.dispatchIds())
            lines.add(SubmissionJson.object().put("dispatchId", dispatchId)
                .put("targetCount", targetCount).put("targetWeightG", targetWeight));
        body.set("expected", expected);
        return body;
    }

    /** A copy of a command body under a fresh original key, for the "same intent, new key" counterexamples. */
    private ObjectNode freshKey(ObjectNode body) {
        var copy = body.deepCopy();
        copy.put("commandId", UUID.randomUUID().toString());
        return copy;
    }

    /** APPROVE anchors on the frozen document digest and its current status, never on the client's word alone. */
    private ObjectNode approveBody(Order order, String documentId, String documentSha256, String status,
        String expectedResultVersion) {
        var expected = SubmissionJson.object().put("documentId", documentId).put("documentSha256", documentSha256)
            .put("status", status).put("expectedResultVersion", expectedResultVersion).put("userAuthVersion", "1");
        return (ObjectNode) SubmissionJson.object().put("commandVersion", 1).put("commandType", "APPROVE")
            .put("commandId", UUID.randomUUID().toString()).put("transportRecoveryEpoch", "1")
            .put("warehouseId", order.warehouseId()).put("documentId", documentId)
            .put("reason", "主管核对后批准").set("expected", expected);
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

    /** V45 registers both correction codes; this binds one of them to the acting user. */
    private void grant(JdbcTemplate jdbc, UUID actor, String permission) {
        var roles = jdbc.queryForList("SELECT BIN_TO_UUID(r.id) id FROM role r JOIN user_role ur ON ur.role_id=r.id"
            + " WHERE ur.user_id=? AND r.code LIKE 'CR_%'", String.class, Ids.bytes(actor));
        UUID role = roles.isEmpty() ? Ids.next() : UUID.fromString(roles.getFirst());
        if (roles.isEmpty()) {
            jdbc.update("INSERT INTO role VALUES(?,?,?)", Ids.bytes(role), "CR_" + role.toString().substring(24), "隔离更正权限");
            jdbc.update("INSERT INTO user_role VALUES(?,?)", Ids.bytes(actor), Ids.bytes(role));
        }
        jdbc.update("INSERT INTO role_permission SELECT ?,id FROM permission WHERE code=?", Ids.bytes(role), permission);
    }

    private HttpResponse<byte[]> post(String token, JsonNode command) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(ROUTE))
            .timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + token).header("X-Server-Recovery-Epoch", "1")
            .POST(HttpRequest.BodyPublishers.ofByteArray(SubmissionJson.canonical(command).getBytes(StandardCharsets.UTF_8)))
            .build();
        return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    private static String codeOf(byte[] body) { return SubmissionJson.parse(body).path("code").asText(); }

    private static String shortBody(byte[] body) {
        String text = body == null ? "" : new String(body, StandardCharsets.UTF_8).replaceAll("\\s+", " ");
        return text.length() > 200 ? text.substring(0, 200) : text;
    }

    private record Order(String orderId, String resultVersion, String resultCount, String resultWeight,
        String warehouseId, UUID operatorId, UUID deviceId, List<String> dispatchIds) { }
}
