package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.auth.SessionUser;
import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.correction.CorrectionCommandService;
import com.acrylic.outbound.inventory.TemporaryLinkConfirmHttpChecks;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

/**
 * R07-4 (A2.4) policy boundaries for the two {@code P-R07-*}-confirmed numeric rules that the correction command
 * kernel already enforces:
 *
 * <ul>
 *   <li><b>P-R07-06</b> 3-day backfill window: business time must sit inside ±3 days of the <i>original shipment</i>
 *       (v1 result version), must not be rewritten into it, and must not sit far in the future.</li>
 *   <li><b>P-R07-09</b> attachments: at most 5 per line, at most 10MB each, at most 50MB per submission, with the
 *       exact reject code {@code ATTACHMENT_LIMIT_EXCEEDED}.</li>
 * </ul>
 *
 * <p>The order fixture is the verified R06 chain (the same entry point {@link E2aCorrectionChainIT} uses) and the
 * command is driven at the <b>service layer</b> — no HTTP, no PDA device. What is asserted is the accept/reject
 * boundary, not a UI path.
 *
 * <p>Out of scope here, and recorded as such in the S40 record: supervisor role mapping (needs a business decision),
 * real attachment bytes/storage (there is no upload endpoint), and the fault-handover branch (still a stub).
 */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aCorrectionPolicyLimitsIT {
    @Test void backfillWindowAndAttachmentLimitsAcceptTheBoundaryAndRefuseOneStepBeyond() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "48"); E2aCrossSourceHttpSetup.signing(profile.attempt);
        var notes = new StringBuilder("verification level=service layer on the verified R06 order; HTTP not verified\n");

        try (var context = E2aCrossSourceHttpIT.start(profile, "--outbound.temporary.commands.enabled=true")) {
            TemporaryLinkConfirmHttpChecks.verify((WebApplicationContext) context);
        }
        try (var context = E2aCrossSourceHttpIT.start(profile, "--outbound.corrections.commands.enabled=true")) {
            verify(jdbc, context.getBean(CorrectionCommandService.class), notes);
        }
        Files.writeString(profile.attempt.resolve("limits.txt"), notes.toString());
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "P-R07-06 window and P-R07-09 attachment boundaries\n");
    }

    private void verify(JdbcTemplate jdbc, CorrectionCommandService service, StringBuilder notes) throws Exception {
        var row = jdbc.queryForMap("""
            SELECT BIN_TO_UUID(v.order_id) orderId,CAST(v.result_version AS CHAR) resultVersion,
              CAST(v.actual_count AS CHAR) resultCount,CAST(v.weight_g AS CHAR) resultWeight,
              BIN_TO_UUID(o.warehouse_id) warehouseId,BIN_TO_UUID(s.operator_id) operatorId,u.username operatorName
            FROM order_result_version v JOIN outbound_order o ON o.id=v.order_id
            JOIN submission s ON s.id=v.submission_id JOIN app_user u ON u.id=s.operator_id
            WHERE o.current_result_version=v.result_version LIMIT 1
            """);
        var order = new Order(String.valueOf(row.get("orderId")), String.valueOf(row.get("resultVersion")),
            String.valueOf(row.get("resultCount")), String.valueOf(row.get("resultWeight")),
            String.valueOf(row.get("warehouseId")), UUID.fromString(String.valueOf(row.get("operatorId"))),
            String.valueOf(row.get("operatorName")));
        var dispatchIds = jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM dispatch_record WHERE order_id=UUID_TO_BIN(?)"
            + " ORDER BY order_scan_seq", String.class, order.orderId());
        assertThat(dispatchIds).as("the R06 chain must leave dispatches to correct").isNotEmpty();
        grant(jdbc, "outbound:correct");
        String second = dispatchIds.size() > 1 ? dispatchIds.get(1) : dispatchIds.getFirst();

        Instant shipment = shipmentCreatedAt(jdbc, order.orderId());
        notes.append("shipment(v1 result version createdAt)=").append(shipment).append('\n');

        // ---- P-R07-09 attachments -------------------------------------------------------------------------------
        // The count cap is checked before any line is written, so one line with 6 attachments is the count probe.
        assertRefused(service, body(order, dispatchIds.getFirst(), null, attachments(6, 1024L), null), order,
            "ATTACHMENT_LIMIT_EXCEEDED", "附件数 6（上限 5）", notes, jdbc);
        // One 10MB attachment is exactly the per-file cap.
        assertAccepted(service, body(order, dispatchIds.getFirst(), null, attachments(1, 10L * 1024 * 1024), null), order,
            "单个附件 10MB（正好上限）", notes, jdbc);
        // 10MB + 1 byte per file is over the cap.
        assertRefused(service, body(order, dispatchIds.getFirst(), null, attachments(1, 10L * 1024 * 1024 + 1), null), order,
            "ATTACHMENT_LIMIT_EXCEEDED", "单个附件 10MB+1", notes, jdbc);
        // The per-submission cap is reachable on ONE line: 5 x (10MB-1B) = 50MB - 5B stays inside, and one byte
        // more (5 x 10MB) is exactly 50MB - which the policy allows - so the smallest over-step is 50MB + 1B.
        assertAccepted(service, body(order, dispatchIds.getFirst(), null, attachments(5, 10L * 1024 * 1024 - 1), null),
            order, "单行合计 50MB-5B", notes, jdbc);
        assertRefused(service, body(order, dispatchIds.getFirst(), null, attachments(5, 5L * 1024 * 1024), attachments(5, 5L * 1024 * 1024 + 1)),
            order, "ATTACHMENT_LIMIT_EXCEEDED", "第二行把合计推过 50MB", notes, jdbc);

        // ---- P-R07-06 backfill window ---------------------------------------------------------------------------
        // Exactly 3 days before the original shipment is inside the window (the guard is `isBefore(shipment-3d)`).
        assertAccepted(service, body(order, dispatchIds.getFirst(), shipment.minus(3, ChronoUnit.DAYS).toString(), null, null),
            order, "业务时间＝出库日-3天（正好边界）", notes, jdbc);
        // One millisecond earlier is outside.
        assertRefused(service, body(order, dispatchIds.getFirst(),
            shipment.minus(3, ChronoUnit.DAYS).minusMillis(1).toString(), null, null), order,
            "CORRECTION_WINDOW_EXCEEDED", "业务时间＝出库日-3天-1ms", notes, jdbc);
        // A far-past business time is outside too (the window is two-sided).
        assertRefused(service, body(order, dispatchIds.getFirst(), shipment.minus(30, ChronoUnit.DAYS).toString(), null, null),
            order, "CORRECTION_WINDOW_EXCEEDED", "业务时间＝出库日-30天", notes, jdbc);
        // Beyond the small future grace is outside.
        assertRefused(service, body(order, dispatchIds.getFirst(),
            Instant.now().plus(30, ChronoUnit.MINUTES).toString(), null, null), order,
            "CORRECTION_WINDOW_EXCEEDED", "业务时间＝当前+30分钟", notes, jdbc);
    }

    private static Instant shipmentCreatedAt(JdbcTemplate jdbc, String orderId) {
        Object value = jdbc.queryForMap("SELECT created_at FROM order_result_version WHERE order_id=UUID_TO_BIN(?)"
            + " ORDER BY (result_version=1) DESC,result_version ASC LIMIT 1", orderId).get("created_at");
        if (value instanceof java.sql.Timestamp stamp) return stamp.toInstant();
        if (value instanceof java.time.LocalDateTime local) return local.toInstant(java.time.ZoneOffset.UTC);
        throw new IllegalStateException("shipment time not readable: " + value);
    }

    private void assertAccepted(CorrectionCommandService service, ObjectNode command, Order order, String label,
        StringBuilder notes, JdbcTemplate jdbc) throws Exception {
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM correction_document", Integer.class);
        String outcome;
        try { execute(service, command, order); outcome = "ACCEPTED"; }
        catch (BusinessException refused) { outcome = refused.code(); }
        notes.append("P-R07 ").append(label).append(" -> ").append(outcome).append('\n');
        assertThat(outcome).as(label).isEqualTo("ACCEPTED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM correction_document", Integer.class))
            .as(label + ": an accepted CREATE adds exactly one document").isEqualTo(before + 1);
    }

    private void assertRefused(CorrectionCommandService service, ObjectNode command, Order order, String expectedCode, String label,
        StringBuilder notes, JdbcTemplate jdbc) throws Exception {
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM correction_document", Integer.class);
        String outcome;
        try { execute(service, command, order); outcome = "NO_ERROR"; }
        catch (BusinessException refused) { outcome = refused.code(); }
        notes.append("P-R07 ").append(label).append(" -> ").append(outcome)
            .append(" (期望 ").append(expectedCode).append(")\n");
        assertThat(outcome).as(label).isEqualTo(expectedCode);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM correction_document", Integer.class))
            .as(label + ": a refused CREATE adds no document").isEqualTo(before);
    }

    /**
     * A WEIGHT_CORRECTION CREATE (a type that requires no physical evidence) over one or two dispatches, carrying the
     * explicit attachment sets and optional business time.
     */
    private ObjectNode body(Order order, String dispatchId, String businessOccurredAt, ArrayNode first, ArrayNode second,
        ArrayNode... more) {
        var command = (ObjectNode) SubmissionJson.object().put("commandVersion", 1).put("commandType", "CREATE")
            .put("commandId", UUID.randomUUID().toString()).put("transportRecoveryEpoch", "1")
            .put("warehouseId", order.warehouseId()).put("orderId", order.orderId()).put("type", "WEIGHT_CORRECTION")
            .put("reason", "A2.4 政策边界验收");
        addLine(command, dispatchId, first, businessOccurredAt);
        if (second != null) addLine(command, dispatchId, second, null);
        for (ArrayNode extra : more) addLine(command, dispatchId, extra, null);
        command.set("expected", SubmissionJson.object().put("orderId", order.orderId())
            .put("currentResultVersion", order.resultVersion())
            .put("currentResultEffectiveCount", order.resultCount())
            .put("currentResultEffectiveWeightG", order.resultWeight()).put("userAuthVersion", "1"));
        return command;
    }

    private static void addLine(ObjectNode command, String dispatchId, ArrayNode attachments, String businessOccurredAt) {
        var line = SubmissionJson.object().put("dispatchId", dispatchId)
            .put("targetCount", "0").put("targetWeightG", "0");
        if (attachments != null) line.set("attachments", attachments);
        if (businessOccurredAt != null) line.put("businessOccurredAt", businessOccurredAt);
        if (!command.has("lines")) command.putArray("lines");
        ((ArrayNode) command.get("lines")).add(line);
    }

    private static ArrayNode attachments(int count, long sizeBytes) {
        // A fresh array node: SubmissionJson.array(...) reads an existing field and would refuse a missing one.
        var array = new com.fasterxml.jackson.databind.ObjectMapper().createArrayNode();
        for (int i = 0; i < count; i++) {
            array.addObject().put("evidenceSha256", "%064x".formatted(i + 1)).put("sizeBytes", sizeBytes);
        }
        return array;
    }

    private static void grant(JdbcTemplate jdbc, String permission) {
        jdbc.update("INSERT IGNORE INTO role_permission SELECT r.id,p.id FROM role r CROSS JOIN permission p "
            + "WHERE r.code='WAREHOUSE_ADMIN' AND p.code=?", permission);
    }

    private static JsonNode execute(CorrectionCommandService service, ObjectNode command, Order order) throws Exception {
        // Access.current() re-loads the user by username, so the actor must carry the real one, not a label.
        var actor = new SessionUser(order.operatorId(), order.operatorName(), "更正边界", "x", 1, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, "unused", List.of()));
        try {
            byte[] response = service.execute(SubmissionJson.canonical(command).getBytes(StandardCharsets.UTF_8), "limits-check");
            return SubmissionJson.parse(new String(response, StandardCharsets.UTF_8));
        } finally { SecurityContextHolder.clearContext(); }
    }

    private record Order(String orderId, String resultVersion, String resultCount, String resultWeight,
        String warehouseId, UUID operatorId, String operatorName) { }
}
