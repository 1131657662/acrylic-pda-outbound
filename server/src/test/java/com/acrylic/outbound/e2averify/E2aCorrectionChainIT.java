package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.correction.CorrectionCommandController;
import com.acrylic.outbound.devices.DeviceCrypto;
import com.acrylic.outbound.inventory.CorrectionExportSnapshotChecks;
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
        profile.migrate(jdbc, "48"); E2aCrossSourceHttpSetup.signing(profile.attempt);
        var order = confirmedOrder(profile, jdbc);
        assertThat(order.dispatchIds()).as("the R06 chain must leave dispatches to correct").isNotEmpty();
        var notes = new StringBuilder("验证层级=真服 19434 + 真 HTTP（PDA 通道）+ 真 MySQL\n");
        var token = mintSession(jdbc, order);
        grant(jdbc, order.operatorId(), "outbound:correct");
        // P-R07-03: the approval body must be a different person from the creator, so the chain carries its own
        // approver. Nothing is granted to it beyond the existing role's bindings.
        UUID approverId = approverUser(jdbc, order);
        var approverToken = mintSessionFor(jdbc, order, approverId);
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
                + " WHERE ur.user_id=? AND p.code='correction:approve'", String.class, Ids.bytes(approverId));
            assertThat(supervisorRoles).as("V45 must bind correction:approve to the approver's role").isNotEmpty();
            jdbc.update("DELETE rp FROM role_permission rp JOIN permission p ON p.id=rp.permission_id"
                + " JOIN user_role ur ON ur.role_id=rp.role_id WHERE ur.user_id=? AND p.code='correction:approve'",
                Ids.bytes(approverId));
            var denied = post(approverToken, approve);
            notes.append("⑥APPROVE（撤销 correction:approve 后）-> ").append(denied.statusCode()).append(' ')
                .append(SubmissionJson.parse(denied.body()).path("code").asText()).append(" (期望 403)\n");
            assertThat(denied.statusCode()).isEqualTo(403);
            assertThat(SubmissionJson.parse(denied.body()).path("code").asText()).isEqualTo("CORRECTION_PERMISSION_DENIED");
            assertThat(jdbc.queryForObject("SELECT status FROM correction_document WHERE id=UUID_TO_BIN(?)",
                String.class, documentId)).as("a denied approval changes nothing").isEqualTo("SUBMITTED");
            for (String role : supervisorRoles)
                jdbc.update("INSERT INTO role_permission VALUES(?,(SELECT id FROM permission WHERE code='correction:approve'))",
                    Ids.bytes(UUID.fromString(role)));

            // (c2) maker-checker (P-R07-03): the body that created the document may not approve it, even though it
            // holds correction:approve. A fresh key so this is a real decision, not a replay of the denial above.
            var selfApprove = post(token, freshKey(approve));
            notes.append("⑥b 建档人自批 -> ").append(selfApprove.statusCode()).append(' ')
                .append(SubmissionJson.parse(selfApprove.body()).path("code").asText())
                .append(" (期望 403 CORRECTION_SELF_APPROVAL_DENIED)\n");
            assertThat(selfApprove.statusCode()).as(shortBody(selfApprove.body())).isEqualTo(403);
            assertThat(SubmissionJson.parse(selfApprove.body()).path("code").asText())
                .isEqualTo("CORRECTION_SELF_APPROVAL_DENIED");
            assertThat(jdbc.queryForObject("SELECT status FROM correction_document WHERE id=UUID_TO_BIN(?)",
                String.class, documentId)).as("a self-approval attempt changes nothing").isEqualTo("SUBMITTED");
            // (d) with the supervisor code the document is approved and effected in one transaction (§3).
            var approved = post(approverToken, approve);
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
            assertThat(post(approverToken, approve).body()).as("APPROVE replay returns the stored original")
                .isEqualTo(approved.body());
            // (d2) D 片1：更正净结果（v>1）快照能被组装（服务端只读，不渲染），逐条对照库内 v2 事实。
            CorrectionExportSnapshotChecks.verify((WebApplicationContext) open, order.orderId(), 2L);
            com.acrylic.outbound.inventory.CorrectionExportHttpChecks.verify((WebApplicationContext) open,
                new com.acrylic.outbound.exports.ExportModels.Scope(order.operatorId(),
                    UUID.fromString(order.warehouseId()),UUID.fromString(order.orderId())),profile.attempt);
            notes.append("⑧-1D 片1 更正净结果快照组装（v2：版本/逐包/合计/凭据对照库内事实 + 默认 v1 不变）-> OK\n");
            // (e) a second correction on the moved order must not be accepted against the old version.
            var stale = post(token, freshKey(createBody(order, expectedBlock(order, "1"), "0", "0")));
            notes.append("⑧再次更正（仍按 v1 预期）-> ").append(stale.statusCode()).append(' ')
                .append(codeOf(stale.body())).append(" (期望 409 CONTEXT_CHANGED)\n");
            assertThat(stale.statusCode()).isEqualTo(409);
            assertThat(codeOf(stale.body())).isEqualTo("CORRECTION_CONTEXT_CHANGED");
            // (f) and a repeated approval under a fresh key is refused the same way.
            var again = post(approverToken, approveBody(order, documentId, documentSha256, "SUBMITTED",
                order.resultVersion()));
            notes.append("⑨重复审批（新原键、旧 expected）-> ").append(again.statusCode()).append(' ')
                .append(codeOf(again.body())).append(" (期望 409 CONTEXT_CHANGED)\n");
            assertThat(again.statusCode()).isEqualTo(409);
            assertThat(codeOf(again.body())).isEqualTo("CORRECTION_CONTEXT_CHANGED");

            // (g) WITHDRAW (P-R07-08) first half: the original approver initiates, and nothing is rolled back yet.
            var withdraw = withdrawBody(order, documentId, documentSha256, "EFFECTIVE");
            var initiated = post(approverToken, withdraw);
            notes.append("⑩WITHDRAW 发起（原审批人）-> ").append(initiated.statusCode()).append(' ')
                .append(shortBody(initiated.body())).append('\n');
            assertThat(initiated.statusCode()).as(shortBody(initiated.body())).isEqualTo(200);
            var request = SubmissionJson.parse(initiated.body()).path("businessDecision");
            assertThat(request.path("status").asText()).isEqualTo("WITHDRAW_PENDING");
            assertThat(request.path("rolledBack").asBoolean()).isFalse();
            assertThat(jdbc.queryForObject("SELECT status FROM correction_document WHERE id=UUID_TO_BIN(?)",
                String.class, documentId)).isEqualTo("WITHDRAW_PENDING");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM correction_document WHERE id=UUID_TO_BIN(?)"
                + " AND withdrawal_requested_by IS NOT NULL AND withdrawal_confirmed_by IS NULL",
                Integer.class, documentId)).as("initiation is recorded, confirmation is not").isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM correction_ledger WHERE reverses_entry_id IS NOT NULL",
                Integer.class)).as("a pending withdrawal reverses nothing yet").isZero();
            assertThat(jdbc.queryForObject("SELECT CAST(current_result_version AS CHAR) FROM outbound_order"
                + " WHERE id=UUID_TO_BIN(?)", String.class, order.orderId())).as("no new version yet").isEqualTo("2");
            // (g2) the confirmation belongs to the requester, not to the approver who opened it.
            String otherUser = jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM app_user WHERE id<>? LIMIT 1",
                String.class, Ids.bytes(order.operatorId()));
            jdbc.update("UPDATE correction_document SET requested_by=UUID_TO_BIN(?) WHERE id=UUID_TO_BIN(?)",
                otherUser, documentId);
            var intruder = post(token, withdrawConfirmBody(order, documentId, documentSha256));
            notes.append("⑩b 非申请人确认 -> ").append(intruder.statusCode()).append(' ')
                .append(codeOf(intruder.body())).append(" (期望 403)\n");
            assertThat(intruder.statusCode()).isEqualTo(403);
            assertThat(codeOf(intruder.body())).isEqualTo("CORRECTION_PERMISSION_DENIED");
            jdbc.update("UPDATE correction_document SET requested_by=? WHERE id=UUID_TO_BIN(?)",
                Ids.bytes(order.operatorId()), documentId);
            // (g3) the requester confirms: only now is the mistake rolled back, entry by entry.
            var confirm = withdrawConfirmBody(order, documentId, documentSha256);
            var withdrawn = post(token, confirm);
            notes.append("⑩c WITHDRAW_CONFIRM（申请人）-> ").append(withdrawn.statusCode()).append(' ')
                .append(shortBody(withdrawn.body())).append('\n');
            assertThat(withdrawn.statusCode()).as(shortBody(withdrawn.body())).isEqualTo(200);
            var withdrawal = SubmissionJson.parse(withdrawn.body()).path("businessDecision");
            assertThat(withdrawal.path("status").asText()).isEqualTo("WITHDRAWN");
            assertThat(withdrawal.path("resultVersion").asText()).isEqualTo("3");
            assertThat(withdrawal.path("confirmedBy").asText()).isEqualTo(order.operatorId().toString());
            assertThat(withdrawal.path("effectiveCount").asText()).isEqualTo(order.resultCount());
            assertThat(withdrawal.path("effectiveWeightG").asText()).isEqualTo(order.resultWeight());
            assertThat(jdbc.queryForObject("SELECT status FROM correction_document WHERE id=UUID_TO_BIN(?)",
                String.class, documentId)).isEqualTo("WITHDRAWN");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM correction_document WHERE id=UUID_TO_BIN(?)"
                + " AND withdrawal_confirmed_by IS NOT NULL AND withdrawal_confirmed_at IS NOT NULL",
                Integer.class, documentId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT CAST(current_result_version AS CHAR) FROM outbound_order"
                + " WHERE id=UUID_TO_BIN(?)", String.class, order.orderId())).isEqualTo("3");
            assertThat(jdbc.queryForObject("SELECT CAST(result_version AS CHAR) FROM order_current_result"
                + " WHERE order_id=UUID_TO_BIN(?)", String.class, order.orderId())).isEqualTo("3");
            // The reversal invariant: every forward entry has exactly one opposite entry pointing back at it.
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM correction_ledger rev JOIN correction_ledger fwd"
                + " ON rev.reverses_entry_id=fwd.id WHERE fwd.business_key LIKE 'CORR:%'"
                + " AND rev.quantity_delta=-fwd.quantity_delta AND rev.weight_delta_g=-fwd.weight_delta_g"
                + " AND rev.business_key LIKE 'WD:%'", Integer.class))
                .as("reverses_entry_id is unique and the deltas are exactly opposite")
                .isEqualTo(order.dispatchIds().size());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM correction_ledger WHERE reverses_entry_id IS NOT NULL",
                Integer.class)).isEqualTo(order.dispatchIds().size());
            // Nothing was rewritten: the forward entries and the effects are still there.
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM correction_ledger WHERE business_key LIKE 'CORR:%'",
                Integer.class)).isEqualTo(order.dispatchIds().size());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM correction_effect", Integer.class))
                .as("one effect per line, ever (UNIQUE(line_id)) - the rollback lives in the ledger")
                .isEqualTo(order.dispatchIds().size());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_effective_scan WHERE order_id=UUID_TO_BIN(?)"
                + " AND result_version=3 AND is_effective=1", Integer.class, order.orderId()))
                .as("the packages are effective again").isEqualTo(order.dispatchIds().size());
            assertThat(jdbc.queryForObject("SELECT IFNULL(SUM(actual_count),-1) FROM order_effective_line"
                + " WHERE order_id=UUID_TO_BIN(?) AND result_version=3", Integer.class, order.orderId()))
                .as("effective lines are back to the pre-correction aggregate").isEqualTo(order.dispatchIds().size());
            assertThat(post(token, confirm).body()).as("WITHDRAW_CONFIRM replay returns the stored original")
                .isEqualTo(withdrawn.body());
            // (h) a second withdrawal under a fresh key is refused: the document is no longer EFFECTIVE.
            var twice = post(approverToken, freshKey(withdrawBody(order, documentId, documentSha256, "EFFECTIVE")));
            notes.append("⑪重复撤销 -> ").append(twice.statusCode()).append(' ').append(codeOf(twice.body()))
                .append(" (期望 409 CONTEXT_CHANGED)\n");
            assertThat(twice.statusCode()).isEqualTo(409);
            assertThat(codeOf(twice.body())).isEqualTo("CORRECTION_CONTEXT_CHANGED");

            // (i) A2 step 6: cancelling a dispatch reopens its cycle exactly once, and a reopening is not availability.
            String cancelled = order.dispatchIds().getFirst();
            var cancelCreate = post(token, lineBody(jdbc, order, "DISPATCH_CANCELLATION", "3", List.of(cancelled), "0", "0", true));
            assertThat(cancelCreate.statusCode()).as(shortBody(cancelCreate.body())).isEqualTo(200);
            var cancelDecision = SubmissionJson.parse(cancelCreate.body()).path("businessDecision");
            var cancelApprove = post(approverToken, approveBody(order, cancelDecision.path("documentId").asText(),
                cancelDecision.path("documentSha256").asText(), "SUBMITTED", "3"));
            notes.append("⑫DISPATCH_CANCELLATION APPROVE -> ").append(cancelApprove.statusCode()).append(' ')
                .append(shortBody(cancelApprove.body())).append('\n');
            assertThat(cancelApprove.statusCode()).as(shortBody(cancelApprove.body())).isEqualTo(200);
            var cancelEffect = SubmissionJson.parse(cancelApprove.body()).path("businessDecision");
            assertThat(cancelEffect.path("resultVersion").asText()).isEqualTo("4");
            assertThat(cancelEffect.path("reopenedCycles")).hasSize(1);
            assertThat(jdbc.queryForMap("SELECT CAST(cycle_before AS CHAR) cb,CAST(cycle_after AS CHAR) ca,"
                + "evidence_sha256,CAST(state_version AS CHAR) sv,CAST(issued_recovery_epoch AS CHAR) ep"
                + " FROM cycle_reopening WHERE original_dispatch_id=UUID_TO_BIN(?)", cancelled))
                .satisfies(row -> {
                    assertThat(Long.parseLong(String.valueOf(row.get("ca"))))
                        .as("cycle_after = cycle_before + 1").isEqualTo(Long.parseLong(String.valueOf(row.get("cb"))) + 1);
                    assertThat(String.valueOf(row.get("evidence_sha256"))).isEqualTo("a".repeat(64));
                    assertThat(String.valueOf(row.get("sv"))).isEqualTo("4");
                    assertThat(Long.parseLong(String.valueOf(row.get("ep")))).isPositive();
                });
            assertThat(jdbc.queryForObject("SELECT is_effective FROM order_effective_scan WHERE order_id=UUID_TO_BIN(?)"
                + " AND result_version=4 AND dispatch_id=UUID_TO_BIN(?)", Boolean.class, order.orderId(), cancelled))
                .as("a reopened cycle is not availability").isFalse();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_effective_scan WHERE order_id=UUID_TO_BIN(?)"
                + " AND result_version=4 AND is_effective=1", Integer.class, order.orderId()))
                .isEqualTo(order.dispatchIds().size() - 1);
            // (j) the same dispatch cannot be reopened twice: CYCLE_REOPENING_EXISTS.
            var againCancel = post(token, lineBody(jdbc, order, "DISPATCH_CANCELLATION", "4", List.of(cancelled), "0", "500", true));
            assertThat(againCancel.statusCode()).as(shortBody(againCancel.body())).isEqualTo(200);
            var againDecision = SubmissionJson.parse(againCancel.body()).path("businessDecision");
            var reopenTwice = post(approverToken, approveBody(order, againDecision.path("documentId").asText(),
                againDecision.path("documentSha256").asText(), "SUBMITTED", "4"));
            notes.append("⑬再次重开同一 dispatch -> ").append(reopenTwice.statusCode()).append(' ')
                .append(codeOf(reopenTwice.body())).append(" (期望 409 CYCLE_REOPENING_EXISTS)\n");
            assertThat(reopenTwice.statusCode()).isEqualTo(409);
            assertThat(codeOf(reopenTwice.body())).isEqualTo("CYCLE_REOPENING_EXISTS");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cycle_reopening", Integer.class)).isEqualTo(1);

            // (k) A3 step 7: a discrepancy case walks the state machine and never touches availability.
            String versionBefore = jdbc.queryForObject("SELECT CAST(current_result_version AS CHAR) FROM outbound_order"
                + " WHERE id=UUID_TO_BIN(?)", String.class, order.orderId());
            int effectiveBefore = jdbc.queryForObject("SELECT COUNT(*) FROM order_effective_scan WHERE order_id=UUID_TO_BIN(?)"
                + " AND result_version=4 AND is_effective=1", Integer.class, order.orderId());
            grant(jdbc, order.operatorId(), "inventory:reconcile");
            var openCase = post(token, reconcileOpenBody(order, "WEIGHT_MISMATCH"));
            notes.append("⑭RECONCILE_OPEN -> ").append(openCase.statusCode()).append(' ')
                .append(shortBody(openCase.body())).append('\n');
            assertThat(openCase.statusCode()).as(shortBody(openCase.body())).isEqualTo(200);
            String caseId = SubmissionJson.parse(openCase.body()).path("businessDecision").path("caseId").asText();
            assertThat(jdbc.queryForObject("SELECT status FROM inventory_reconciliation WHERE id=UUID_TO_BIN(?)",
                String.class, caseId)).isEqualTo("OPEN");
            for (String[] step : new String[][]{{"OPEN", "1", null}, {"EVIDENCE_PENDING", "2", null},
                {"REVIEW_READY", "3", "实物短少 1 包，已按现场照片结案"}}) {
                var advanced = post(token, reconcileAdvanceBody(order, caseId, step[0], step[1], step[2], false));
                assertThat(advanced.statusCode()).as(shortBody(advanced.body())).isEqualTo(200);
                notes.append("⑮").append(step[0]).append(" -> ")
                    .append(SubmissionJson.parse(advanced.body()).path("businessDecision").path("status").asText()).append('\n');
            }
            assertThat(jdbc.queryForMap("SELECT status,conclusion,CAST(row_version AS CHAR) rv"
                + " FROM inventory_reconciliation WHERE id=UUID_TO_BIN(?)", caseId))
                .satisfies(row -> {
                    assertThat(String.valueOf(row.get("status"))).isEqualTo("RESOLVED");
                    assertThat(String.valueOf(row.get("conclusion"))).contains("现场照片");
                    assertThat(String.valueOf(row.get("rv"))).isEqualTo("4");
                });
            assertThat(jdbc.queryForObject("SELECT CAST(current_result_version AS CHAR) FROM outbound_order"
                + " WHERE id=UUID_TO_BIN(?)", String.class, order.orderId()))
                .as("a case conclusion is not availability").isEqualTo(versionBefore);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_effective_scan WHERE order_id=UUID_TO_BIN(?)"
                + " AND result_version=4 AND is_effective=1", Integer.class, order.orderId())).isEqualTo(effectiveBefore);
            var afterResolved = post(token, reconcileAdvanceBody(order, caseId, "RESOLVED", "4", null, false));
            notes.append("⑯已结案再推进 -> ").append(afterResolved.statusCode()).append(' ')
                .append(codeOf(afterResolved.body())).append(" (期望 409)\n");
            assertThat(afterResolved.statusCode()).isEqualTo(409);
            // (l) a conflict blocks a non-terminal case, and blocking needs a reason.
            String blockedCase = SubmissionJson.parse(post(token, reconcileOpenBody(order, "MISSING_PACKAGE")).body())
                .path("businessDecision").path("caseId").asText();
            var noReason = post(token, reconcileAdvanceBody(order, blockedCase, "OPEN", "1", null, true));
            assertThat(noReason.statusCode()).as("标记冲突必须写原因").isEqualTo(400);
            var blocked = post(token, reconcileAdvanceBody(order, blockedCase, "OPEN", "1", "账面与实物互相矛盾", true));
            notes.append("⑰BLOCKED -> ").append(blocked.statusCode()).append(' ')
                .append(SubmissionJson.parse(blocked.body()).path("businessDecision").path("status").asText()).append('\n');
            assertThat(blocked.statusCode()).as(shortBody(blocked.body())).isEqualTo(200);
            assertThat(jdbc.queryForObject("SELECT status FROM inventory_reconciliation WHERE id=UUID_TO_BIN(?)",
                String.class, blockedCase)).isEqualTo("BLOCKED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_event WHERE entity_type='CORRECTION_COMMAND'",
                Integer.class)).as("one audit event per accepted receipt so far").isEqualTo(13);
            // (m) A4 step 8: one unpublished state notification per published version. The outbox's entity_version space
            // is shared with the R05/R06 order events, so the correction takes the next free state version and the
            // result version it belongs to is carried in the payload.
            assertThat(jdbc.queryForList("SELECT event_type,JSON_UNQUOTE(JSON_EXTRACT(payload,'$.resultVersion')) v"
                + " FROM order_sync_outbox WHERE order_id=UUID_TO_BIN(?) AND event_type IN"
                + " ('CORRECTION_EFFECTIVE','CORRECTION_WITHDRAWN','CYCLE_REOPENED') ORDER BY entity_version",
                order.orderId()))
                .as("v2 effect, v3 withdrawal, v4 cancellation - and nothing from the reconciliation cases")
                .satisfies(rows -> {
                    assertThat(rows).hasSize(3);
                    assertThat(String.valueOf(rows.get(0).get("event_type"))).isEqualTo("CORRECTION_EFFECTIVE");
                    assertThat(String.valueOf(rows.get(0).get("v"))).isEqualTo("2");
                    assertThat(String.valueOf(rows.get(1).get("event_type"))).isEqualTo("CORRECTION_WITHDRAWN");
                    assertThat(String.valueOf(rows.get(1).get("v"))).isEqualTo("3");
                    assertThat(String.valueOf(rows.get(2).get("event_type"))).isEqualTo("CYCLE_REOPENED");
                    assertThat(String.valueOf(rows.get(2).get("v"))).isEqualTo("4");
                });
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_sync_outbox"
                + " WHERE order_id=UUID_TO_BIN(?) AND event_type='SUBMISSION_COMPLETED'", Integer.class, order.orderId()))
                .as("the confirmation chain's own event is untouched").isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_sync_outbox WHERE published_seq IS NOT NULL",
                Integer.class)).as("nothing is published by the write path").isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT entity_version) FROM order_sync_outbox"
                + " WHERE order_id=UUID_TO_BIN(?)", Integer.class, order.orderId())).isEqualTo(4);

            // (n) B1: the 3-day window is measured against the ORIGINAL shipment, not the current version. Backdate the
            // current version by 10 days; a business time 5 days old is then inside a "current version" window but
            // outside the original shipment's, so only a v1-anchored check can refuse it.
            jdbc.update("UPDATE order_result_version SET created_at=DATE_SUB(created_at,INTERVAL 10 DAY)"
                + " WHERE order_id=UUID_TO_BIN(?) AND result_version=4", order.orderId());
            var backdatedBody = lineBody(jdbc, order, "WEIGHT_CORRECTION", "4", order.dispatchIds(), "0", "0", false);
            backdatedBody.set("lines", datedLines(order));
            var backdated = post(token, backdatedBody);
            notes.append("⑱业务时间早于原出库日 3 天以上 -> ").append(backdated.statusCode()).append(' ')
                .append(codeOf(backdated.body())).append(" (期望 409 CORRECTION_WINDOW_EXCEEDED)\n");
            assertThat(backdated.statusCode()).as(shortBody(backdated.body())).isEqualTo(409);
            assertThat(codeOf(backdated.body())).isEqualTo("CORRECTION_WINDOW_EXCEEDED");

            // (o) B2: attachment capacity (P-R07-09) - 5 per line, 10MB each, 50MB per command. The still-effective
            // dispatch is targeted so the target differs from its current values and the capacity check is reached.
            var live = List.of(order.dispatchIds().get(1));
            assertThat(post(token, evidenceBody(jdbc, order, "4", live, 1, 1024L * 1024, "0")).statusCode())
                .as("one 1MB attachment is accepted").isEqualTo(200);
            var tooMany = post(token, evidenceBody(jdbc, order, "4", live, 6, 1024L, "0"));
            notes.append("⑲6 个附件 -> ").append(tooMany.statusCode()).append(' ').append(codeOf(tooMany.body()))
                .append(" (期望 400 ATTACHMENT_LIMIT_EXCEEDED)\n");
            assertThat(tooMany.statusCode()).isEqualTo(400);
            assertThat(codeOf(tooMany.body())).isEqualTo("ATTACHMENT_LIMIT_EXCEEDED");
            var tooBig = post(token, evidenceBody(jdbc, order, "4", live, 1, 11L * 1024 * 1024, "0"));
            assertThat(tooBig.statusCode()).as("11MB single file").isEqualTo(400);
            assertThat(codeOf(tooBig.body())).isEqualTo("ATTACHMENT_LIMIT_EXCEEDED");
            var tooMuch = post(token, evidenceBody(jdbc, order, "4", order.dispatchIds(), 5, 10L * 1024 * 1024, "500"));
            assertThat(tooMuch.statusCode()).as("5x10MB on each of two lines exceeds the 50MB command total").isEqualTo(400);
            assertThat(codeOf(tooMuch.body())).isEqualTo("ATTACHMENT_LIMIT_EXCEEDED");

            // (p) B3: a header correction patches the frozen receipt that IS the header snapshot.
            var headerCreate = post(token, headerBody(jdbc, order, "4", "orderNo", "CORR-HEADER-1"));
            notes.append("⑳HEADER_CORRECTION CREATE -> ").append(headerCreate.statusCode()).append(' ')
                .append(shortBody(headerCreate.body())).append('\n');
            assertThat(headerCreate.statusCode()).as(shortBody(headerCreate.body())).isEqualTo(200);
            var headerDecision = SubmissionJson.parse(headerCreate.body()).path("businessDecision");
            assertThat(headerDecision.path("lineCount").asInt()).isZero();
            var headerApprove = post(approverToken, approveBody(order, headerDecision.path("documentId").asText(),
                headerDecision.path("documentSha256").asText(), "SUBMITTED", "4"));
            assertThat(headerApprove.statusCode()).as(shortBody(headerApprove.body())).isEqualTo(200);
            assertThat(jdbc.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(header_snapshot,'$.orderNo'))"
                + " FROM order_result_version WHERE order_id=UUID_TO_BIN(?) AND result_version=5",
                String.class, order.orderId())).as("the patch landed on the new version").isEqualTo("CORR-HEADER-1");
            assertThat(jdbc.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(header_snapshot,'$.actualCount'))"
                + " FROM order_result_version WHERE order_id=UUID_TO_BIN(?) AND result_version=5",
                String.class, order.orderId())).as("server-owned facts are untouched").isNotNull();
            assertThat(jdbc.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(header_snapshot,'$.orderNo'))"
                + " FROM order_result_version WHERE order_id=UUID_TO_BIN(?) AND result_version=4",
                String.class, order.orderId())).as("the earlier version's snapshot stays immutable").isNotEqualTo("CORR-HEADER-1");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM correction_effect", Integer.class))
                .as("weight correction (2 lines) + cancellation (1 line); the header correction adds none")
                .isEqualTo(3);
            assertThat(jdbc.queryForObject("SELECT CAST(actual_count AS CHAR) FROM order_result_version"
                + " WHERE order_id=UUID_TO_BIN(?) AND result_version=5", String.class, order.orderId()))
                .as("counts do not move for a header correction").isEqualTo("1");
            // The patch is deny-by-default: unknown keys, server-owned keys and no-op patches are all refused.
            var unknown = post(token, headerBody(jdbc, order, "5", "notAHeaderField", "x"));
            assertThat(unknown.statusCode()).as("unknown header key").isEqualTo(400);
            var owned = post(token, headerBody(jdbc, order, "5", "actualCount", "9"));
            assertThat(owned.statusCode()).as("server-owned header key").isEqualTo(400);
            var noop = post(token, headerBody(jdbc, order, "5", "orderNo", "CORR-HEADER-1"));
            assertThat(noop.statusCode()).as("a patch that changes nothing").isEqualTo(409);
            assertThat(codeOf(noop.body())).isEqualTo("CORRECTION_LINE_TARGET_UNCHANGED");
            // Every refusal above (window, capacity, patch shape) wrote no audit event: 13 + the three accepted ones.
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_event WHERE entity_type='CORRECTION_COMMAND'",
                Integer.class)).as("refused commands leave no audit trail").isEqualTo(16);
        }
        Files.writeString(profile.attempt.resolve("correction-chain.txt"), notes.toString());
        Files.writeString(profile.attempt.resolve("test-completed.marker"), notes.toString());
    }

    /** Runs the verified R06 chain, then collects what a correction needs: order, dispatches, operator, device. */
    Order confirmedOrder(E2aCrossSourceProfile profile, JdbcTemplate jdbc) throws Exception {
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

    /**
     * A CREATE body for one type over an explicit dispatch subset at an explicit version. The order facts are stable
     * across this chain (the withdrawal returns them to the pre-correction values), so the expected block is built
     * from the freshly read row rather than from a stale copy.
     */
    ObjectNode lineBody(JdbcTemplate jdbc, Order order, String type, String currentVersion,
        List<String> dispatchIds, String targetCount, String targetWeight, boolean withEvidence) {
        var facts = jdbc.queryForMap("SELECT CAST(actual_count AS CHAR) c,CAST(weight_g AS CHAR) w"
            + " FROM order_result_version WHERE order_id=UUID_TO_BIN(?) AND result_version=?",
            order.orderId(), Long.parseLong(currentVersion));
        var expected = SubmissionJson.object().put("orderId", order.orderId())
            .put("currentResultVersion", currentVersion)
            .put("currentResultEffectiveCount", String.valueOf(facts.get("c")))
            .put("currentResultEffectiveWeightG", String.valueOf(facts.get("w"))).put("userAuthVersion", "1");
        var body = (ObjectNode) SubmissionJson.object().put("commandVersion", 1).put("commandType", "CREATE")
            .put("commandId", UUID.randomUUID().toString()).put("transportRecoveryEpoch", "1")
            .put("warehouseId", order.warehouseId()).put("orderId", order.orderId()).put("type", type)
            .put("reason", "取消派发并重开周期");
        var lines = body.putArray("lines");
        for (String dispatchId : dispatchIds) {
            var line = SubmissionJson.object().put("dispatchId", dispatchId)
                .put("targetCount", targetCount).put("targetWeightG", targetWeight);
            if (withEvidence) line.put("evidenceId", UUID.randomUUID().toString()).put("evidenceSha256", "a".repeat(64));
            lines.add(line);
        }
        body.set("expected", expected);
        return body;
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

    /** A case is opened against a warehouse, optionally naming a dispatch - it carries no order anchor. */
    private ObjectNode reconcileOpenBody(Order order, String kind) {
        var expected = SubmissionJson.object().put("warehouseId", order.warehouseId()).put("userAuthVersion", "1");
        return (ObjectNode) SubmissionJson.object().put("commandVersion", 1).put("commandType", "RECONCILE_OPEN")
            .put("commandId", UUID.randomUUID().toString()).put("transportRecoveryEpoch", "1")
            .put("warehouseId", order.warehouseId()).put("kind", kind)
            .put("dispatchId", order.dispatchIds().getFirst()).set("expected", expected);
    }

    private ObjectNode reconcileAdvanceBody(Order order, String caseId, String status, String rowVersion,
        String conclusion, boolean blocked) {
        var expected = SubmissionJson.object().put("caseId", caseId).put("status", status)
            .put("rowVersion", rowVersion).put("userAuthVersion", "1");
        var body = (ObjectNode) SubmissionJson.object().put("commandVersion", 1).put("commandType", "RECONCILE_ADVANCE")
            .put("commandId", UUID.randomUUID().toString()).put("transportRecoveryEpoch", "1")
            .put("warehouseId", order.warehouseId()).put("caseId", caseId);
        if (conclusion != null) body.put("conclusion", conclusion);
        if (blocked) body.put("blocked", true);
        body.set("expected", expected);
        return body;
    }

    /** Current-version facts, so a body's expected block always matches what the server will compare it with. */
    private String[] versionFacts(JdbcTemplate jdbc, String version, Order order) {
        String count = jdbc.queryForObject("SELECT CAST(actual_count AS CHAR) FROM order_result_version"
            + " WHERE order_id=UUID_TO_BIN(?) AND result_version=?", String.class, order.orderId(),
            Long.parseLong(version));
        String weight = jdbc.queryForObject("SELECT CAST(weight_g AS CHAR) FROM order_result_version"
            + " WHERE order_id=UUID_TO_BIN(?) AND result_version=?", String.class, order.orderId(),
            Long.parseLong(version));
        return new String[]{count, weight};
    }

    /** One PHYSICAL_RETURN line per dispatch, each carrying {@code count} attachments of {@code sizeBytes}. */
    private ObjectNode evidenceBody(JdbcTemplate jdbc, Order order, String currentVersion, List<String> dispatchIds,
        int count, long sizeBytes, String targetWeight) {
        var facts = versionFacts(jdbc, currentVersion, order);
        var expected = SubmissionJson.object().put("orderId", order.orderId())
            .put("currentResultVersion", currentVersion).put("currentResultEffectiveCount", facts[0])
            .put("currentResultEffectiveWeightG", facts[1]).put("userAuthVersion", "1");
        String sha = "b".repeat(64);
        var body = (ObjectNode) SubmissionJson.object().put("commandVersion", 1).put("commandType", "CREATE")
            .put("commandId", UUID.randomUUID().toString()).put("transportRecoveryEpoch", "1")
            .put("warehouseId", order.warehouseId()).put("orderId", order.orderId()).put("type", "PHYSICAL_RETURN")
            .put("reason", "实物退回并附照片");
        var lines = body.putArray("lines");
        for (String dispatchId : dispatchIds) {
            var line = SubmissionJson.object().put("dispatchId", dispatchId).put("targetCount", "0")
                .put("targetWeightG", targetWeight).put("evidenceId", UUID.randomUUID().toString())
                .put("evidenceSha256", sha);
            var attachments = line.putArray("attachments");
            for (int index = 0; index < count; index++)
                attachments.add(SubmissionJson.object().put("evidenceSha256", sha)
                    .put("sizeBytes", Long.toString(sizeBytes)));
            lines.add(line);
        }
        body.set("expected", expected);
        return body;
    }

    /** A HEADER_CORRECTION body: a scalar patch and no lines at all. */
    ObjectNode headerBody(JdbcTemplate jdbc, Order order, String currentVersion, String key, String value) {
        var facts = versionFacts(jdbc, currentVersion, order);
        var expected = SubmissionJson.object().put("orderId", order.orderId())
            .put("currentResultVersion", currentVersion).put("currentResultEffectiveCount", facts[0])
            .put("currentResultEffectiveWeightG", facts[1]).put("userAuthVersion", "1");
        var patch = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        patch.put(key, value);
        ObjectNode body = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        body.put("commandVersion", 1).put("commandType", "CREATE")
            .put("commandId", UUID.randomUUID().toString()).put("transportRecoveryEpoch", "1")
            .put("warehouseId", order.warehouseId()).put("orderId", order.orderId()).put("type", "HEADER_CORRECTION")
            .put("reason", "更正表头信息");
        body.set("headerPatch", patch);
        body.set("expected", expected);
        return body;
    }

    /** Lines dated 5 days ago with a target that differs from the current values, so the window check is reached. */
    private com.fasterxml.jackson.databind.node.ArrayNode datedLines(Order order) {
        var lines = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        for (String dispatchId : order.dispatchIds())
            lines.add(SubmissionJson.object().put("dispatchId", dispatchId).put("targetCount", "0")
                .put("targetWeightG", "500").put("businessOccurredAt",
                    java.time.Instant.now().minus(5, java.time.temporal.ChronoUnit.DAYS).toString()));
        return lines;
    }

    /** The requester's half of P-R07-08: the same frozen digest, now under WITHDRAW_PENDING. */
    ObjectNode withdrawConfirmBody(Order order, String documentId, String documentSha256) {
        var expected = SubmissionJson.object().put("documentId", documentId).put("documentSha256", documentSha256)
            .put("status", "WITHDRAW_PENDING").put("expectedResultVersion", order.resultVersion())
            .put("userAuthVersion", "1");
        return (ObjectNode) SubmissionJson.object().put("commandVersion", 1).put("commandType", "WITHDRAW_CONFIRM")
            .put("commandId", UUID.randomUUID().toString()).put("transportRecoveryEpoch", "1")
            .put("warehouseId", order.warehouseId()).put("documentId", documentId)
            .put("reason", "申请人确认撤销").set("expected", expected);
    }

    /** WITHDRAW anchors on the same frozen digest, the current status and the approver who is undoing it. */
    ObjectNode withdrawBody(Order order, String documentId, String documentSha256, String status) {
        var expected = SubmissionJson.object().put("documentId", documentId).put("documentSha256", documentSha256)
            .put("status", status).put("expectedResultVersion", order.resultVersion()).put("userAuthVersion", "1");
        return (ObjectNode) SubmissionJson.object().put("commandVersion", 1).put("commandType", "WITHDRAW")
            .put("commandId", UUID.randomUUID().toString()).put("transportRecoveryEpoch", "1")
            .put("warehouseId", order.warehouseId()).put("documentId", documentId)
            .put("reason", "误批撤销").set("expected", expected);
    }

    /** A copy of a command body under a fresh original key, for the "same intent, new key" counterexamples. */
    private ObjectNode freshKey(ObjectNode body) {
        var copy = body.deepCopy();
        copy.put("commandId", UUID.randomUUID().toString());
        return copy;
    }

    /** APPROVE anchors on the frozen document digest and its current status, never on the client's word alone. */
    ObjectNode approveBody(Order order, String documentId, String documentSha256, String status,
        String expectedResultVersion) {
        var expected = SubmissionJson.object().put("documentId", documentId).put("documentSha256", documentSha256)
            .put("status", status).put("expectedResultVersion", expectedResultVersion).put("userAuthVersion", "1");
        return (ObjectNode) SubmissionJson.object().put("commandVersion", 1).put("commandType", "APPROVE")
            .put("commandId", UUID.randomUUID().toString()).put("transportRecoveryEpoch", "1")
            .put("warehouseId", order.warehouseId()).put("documentId", documentId)
            .put("reason", "主管核对后批准").set("expected", expected);
    }

    /**
     * The independent approver for P-R07-03 (ruling B, 2026-09-30): a different person from the document's creator.
     * Fixture-level only - it rides an existing product role that carries correction:approve in a V48 schema, so the
     * product gains no new role for this test.
     */
    private UUID approverUser(JdbcTemplate jdbc, Order order) {
        UUID id = Ids.next();
        jdbc.update("INSERT INTO app_user(id,username,display_name,password_hash,created_at)"
            + " VALUES(?,?,?,'x',UTC_TIMESTAMP(3))", Ids.bytes(id), "corr-approver-" + id, "更正审批人");
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)", Ids.bytes(id),
            Ids.bytes(UUID.fromString(order.warehouseId())));
        jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code='WAREHOUSE_ADMIN'", Ids.bytes(id));
        return id;
    }

    /** A PDA session for an arbitrary bound user on the order's device (the approver is not the operator). */
    String mintSessionFor(JdbcTemplate jdbc, Order order, UUID userId) {
        String token = DeviceCrypto.secret();
        jdbc.update("""
            INSERT INTO pda_session(id,device_id,user_id,user_auth_version,device_version,access_hash,refresh_hash,
              access_expires_at,expires_at,created_at)
            VALUES(?,?,?,1,1,?,?,DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 1 DAY),
              DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 2 DAY),UTC_TIMESTAMP(3))
            """, Ids.bytes(UUID.randomUUID()), Ids.bytes(order.deviceId()), Ids.bytes(userId),
            DeviceCrypto.sha(token), DeviceCrypto.sha(DeviceCrypto.secret()));
        return token;
    }

    String mintSession(JdbcTemplate jdbc, Order order) {
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
    void grant(JdbcTemplate jdbc, UUID actor, String permission) {
        var roles = jdbc.queryForList("SELECT BIN_TO_UUID(r.id) id FROM role r JOIN user_role ur ON ur.role_id=r.id"
            + " WHERE ur.user_id=? AND r.code LIKE 'CR_%'", String.class, Ids.bytes(actor));
        UUID role = roles.isEmpty() ? Ids.next() : UUID.fromString(roles.getFirst());
        if (roles.isEmpty()) {
            jdbc.update("INSERT INTO role VALUES(?,?,?)", Ids.bytes(role), "CR_" + role.toString().substring(24), "隔离更正权限");
            jdbc.update("INSERT INTO user_role VALUES(?,?)", Ids.bytes(actor), Ids.bytes(role));
        }
        jdbc.update("INSERT INTO role_permission SELECT ?,id FROM permission WHERE code=?", Ids.bytes(role), permission);
    }

    HttpResponse<byte[]> post(String token, JsonNode command) throws Exception {
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

    record Order(String orderId, String resultVersion, String resultCount, String resultWeight,
        String warehouseId, UUID operatorId, UUID deviceId, List<String> dispatchIds) { }
}
