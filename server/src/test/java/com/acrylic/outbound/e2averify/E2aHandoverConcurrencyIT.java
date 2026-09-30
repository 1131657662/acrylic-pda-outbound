package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.auth.SessionUser;
import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.handover.HandoverCommandService;
import com.acrylic.outbound.handover.HandoverStore;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;

/**
 * R07-1 concurrency assertions that had <b>no landing</b> before this round (A2.2 matrix rows M10/M11/M12).
 *
 * <p>Real threads against the real command service on an isolated non-empty MySQL. The service owns the outer
 * transaction boundary and serialises on the case/scope row lock, so this is a genuine concurrent write path — not a
 * simulated interleaving. Verification level is the <b>service layer</b> (no HTTP, no device); each worker sets its
 * own {@code SecurityContextHolder}, because that is thread-local and the service reads the actor from it.
 *
 * <p>What is asserted is the <b>outcome multiset</b>, never a schedule: exactly one winner, the losers refused with a
 * named business code, and exactly one receipt / one case row / no half-written state.
 */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aHandoverConcurrencyIT {
    @Test void concurrentApprovalsElectOneWinnerAndConcurrentReplayKeepsOneReceipt() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "42"); E2aCrossSourceHttpSetup.signing(profile.attempt);
        profile.migrate(jdbc, "44");
        try (var context = E2aCrossSourceHttpIT.start(profile, "--outbound.handover.commands.enabled=true")) {
            verify(context, profile.attempt);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "concurrency: %s\n".formatted("two approvals elect one winner; concurrent same-key replay keeps one receipt"));
    }

    private void verify(org.springframework.context.ApplicationContext context, java.nio.file.Path attempt) throws Exception {
        var jdbc = context.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
        var store = context.getBean(HandoverStore.class);
        var service = context.getBean(HandoverCommandService.class);
        var notes = new StringBuilder("verification level=service layer, real threads; HTTP layer not verified\n");

        var host = jdbc.queryForMap("""
            SELECT BIN_TO_UUID(a.id) assignment_id,BIN_TO_UUID(a.order_id) order_id,BIN_TO_UUID(a.warehouse_id) warehouse_id,
              BIN_TO_UUID(a.device_id) device_id,BIN_TO_UUID(a.operator_id) operator_id,CAST(a.recovery_epoch AS CHAR) recovery_epoch
            FROM order_assignment a WHERE a.status='ACTIVE' LIMIT 1
            """);
        UUID warehouse = UUID.fromString(host.get("warehouse_id").toString());
        UUID order = UUID.fromString(host.get("order_id").toString());
        UUID assignment = UUID.fromString(host.get("assignment_id").toString());
        UUID device = UUID.fromString(host.get("device_id").toString());

        String requesterName = "conc-req-"+Ids.next(); UUID requester = user(jdbc, warehouse, "order:resume", requesterName);
        String approverName = "conc-app-"+Ids.next(); UUID approver = user(jdbc, warehouse, "order:handover", approverName);

        UUID scope = UUID.randomUUID(); UUID submission = UUID.randomUUID();
        jdbc.update("INSERT INTO submission_scope(id,warehouse_id,source_order_id,work_round,current_submission_id) VALUES(?,?,?,1,NULL)",
            Ids.bytes(scope),Ids.bytes(warehouse),Ids.bytes(order));
        jdbc.update("INSERT INTO submission(id,scope_id,assignment_id,freeze_device_id,operator_id,work_round,"
            + "source_recovery_epoch,status,issues_json,receipt_text,created_at,updated_at) "
            + "VALUES(?,?,?,?,?,1,?,'REGISTERED',NULL,NULL,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
            Ids.bytes(submission),Ids.bytes(scope),Ids.bytes(assignment),Ids.bytes(device),
            Ids.bytes(UUID.fromString(host.get("operator_id").toString())),Long.parseLong(host.get("recovery_epoch").toString()));
        jdbc.update("UPDATE submission_scope SET current_submission_id=? WHERE id=?",Ids.bytes(submission),Ids.bytes(scope));

        var scopeFacts = store.scopeFacts(scope);
        var request = SubmissionJson.object().put("commandVersion",1).put("commandType","REQUEST")
            .put("commandId",UUID.randomUUID().toString()).put("transportRecoveryEpoch","1").put("reason","原机可用，申请交接")
            .put("warehouseId",warehouse.toString()).put("scopeId",scope.toString());
        var requestExpected = SubmissionJson.object();
        for (String key : List.of("scopeId","canonicalOrderId","mappingVersion","fromWorkRound")) requestExpected.set(key,scopeFacts.get(key));
        requestExpected.put("userAuthVersion","1");
        request.set("expected",requestExpected);
        JsonNode requestReceipt = call(service,request,requester,requesterName);
        UUID caseId = UUID.fromString(requestReceipt.path("businessDecision").path("caseId").asText());
        long baselineVersion = jdbc.queryForObject("SELECT row_version FROM handover_case WHERE id=?",Long.class,Ids.bytes(caseId));

        // --- M12: two threads replay the SAME original key and the SAME bytes ------------------------------------
        // The original-key reservation is a unique row in api_idempotency_request, so at most one execution may ever
        // happen for a key. The receipt is deliberately seeded first: this isolates the *concurrent replay* behaviour
        // (the write race itself is what M10 covers). Recorded as such — not claimed as a full write-race probe.
        var replayOutcomes = runTogether(
            () -> callCode(service,request,requester,requesterName),
            () -> callCode(service,request,requester,requesterName));
        notes.append("M12 同键并发重放 -> ").append(replayOutcomes).append('\n');
        assertThat(replayOutcomes).as("M12 both concurrent replays return the stored response").containsOnly("OK");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM handover_command_receipt WHERE case_id=?",Integer.class,Ids.bytes(caseId)))
            .as("M12 concurrent replays never add a second receipt").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM handover_case WHERE scope_id=?",Integer.class,Ids.bytes(scope)))
            .as("M12 concurrent replays never add a second case").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_event WHERE entity_type='HANDOVER_COMMAND' "
            + "AND entity_id IN (SELECT id FROM handover_command_receipt WHERE case_id=?)",Integer.class,Ids.bytes(caseId)))
            .as("M12 concurrent replays add no audit row").isEqualTo(1);

        // --- M10: two threads APPROVE the same case version ---------------------------------------------
        recordStopProof(jdbc,caseId,device);
        int checklistBaseline = count(jdbc,"handover_checklist",caseId);
        int receiptBaseline = count(jdbc,"handover_command_receipt",caseId);
        var first = approve(store,caseId,warehouse,device,baselineVersion,"RETAINED");
        var second = approve(store,caseId,warehouse,device,baselineVersion,"RETAINED");
        var approvals = runTogether(
            () -> callCode(service,first,approver,approverName),
            () -> callCode(service,second,approver,approverName));
        notes.append("M10 双 APPROVE 同版本 -> ").append(approvals).append('\n');
        long approved = approvals.stream().filter(o -> o.equals("OK")).count();
        long refused = approvals.stream().filter(o -> o.equals("HANDOVER_CONTEXT_CHANGED") || o.equals("HANDOVER_STATE_INVALID")).count();
        assertThat(approved).as("M10 exactly one approval wins: " + approvals).isEqualTo(1);
        assertThat(refused).as("M10 the loser is refused with a named code: " + approvals).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM handover_case WHERE id=?",String.class,Ids.bytes(caseId)))
            .as("M10 the case ends APPROVED, never halfway").isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM handover_command_receipt WHERE case_id=?",Integer.class,Ids.bytes(caseId)))
            .as("M10 only the winner records a receipt; the refused command rolls back without one")
            .isEqualTo(receiptBaseline + 1);
        assertThat(count(jdbc,"handover_checklist",caseId))
            .as("M10 the checklist is written once (the loser writes nothing)").isEqualTo(checklistBaseline + 1);

        // --- M11: a REQUEST racing the APPROVE that closes the same source's case --------------------------------
        // handover_case carries UNIQUE(scope_id, open_flag): one OPEN case per source. A second REQUEST for the same
        // scope therefore cannot execute at all, and the interleaving must not let both succeed or create a second
        // case. (Observed: the REQUEST loses twice over — it either collides on the unique open case or finds the case
        // already advanced — so the outcome multiset is data, not a single predicted code.)
        var raced = approve(store,caseId,warehouse,device,
            jdbc.queryForObject("SELECT row_version FROM handover_case WHERE id=?",Long.class,Ids.bytes(caseId)),"RETAINED");
        var secondRequest = request.deepCopy();
        ((ObjectNode)secondRequest).put("commandId",UUID.randomUUID().toString());
        var interleaved = runTogether(
            () -> callCode(service,raced,approver,approverName),
            () -> callCode(service,secondRequest,requester,requesterName));
        notes.append("M11 REQUEST 与 APPROVE 交错 -> ").append(interleaved).append('\n');
        assertThat(interleaved.stream().filter(o -> o.equals("OK")).count())
            .as("M11 the interleaving must not let both commands succeed: " + interleaved).isLessThanOrEqualTo(1);
        assertThat(interleaved.stream().allMatch(o -> o.equals("OK") || o.equals("HANDOVER_RESULT_UNKNOWN")
            || o.equals("HANDOVER_CONTEXT_CHANGED") || o.equals("HANDOVER_STATE_INVALID")))
            .as("M11 every outcome is a named result, never an unclassified failure: " + interleaved).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM handover_case WHERE scope_id=?",Integer.class,Ids.bytes(scope)))
            .as("M11 one case per source is preserved").isEqualTo(1);
        // Whatever the schedule, the case row must end in a legal terminal-or-progress state, never a torn one.
        assertThat(jdbc.queryForObject("SELECT status FROM handover_case WHERE id=?",String.class,Ids.bytes(caseId)))
            .as("M11 the case ends in a legal state").isIn("EVIDENCE_PENDING","APPROVED","TARGET_PREPARED","ACTIVATED");
        Files.writeString(attempt.resolve("concurrency.txt"),notes.toString());
    }

    /** Records the device-produced stop proof the release guard requires, without asserting it again here. */
    private static void recordStopProof(org.springframework.jdbc.core.JdbcTemplate jdbc, UUID caseId, UUID device) {
        jdbc.update("INSERT INTO handover_evidence(id,case_id,kind,device_id,payload_sha256,high_watermark,receipt_bytes,recorded_at) "
            + "VALUES(?,?,'STOP_PROOF',?,?,7,?,UTC_TIMESTAMP(3))",
            Ids.bytes(Ids.next()),Ids.bytes(caseId),Ids.bytes(device),
            java.util.HexFormat.of().parseHex("b".repeat(64)),"stop-proof-receipt".getBytes(StandardCharsets.UTF_8));
        jdbc.update("UPDATE handover_case SET stop_state='DEVICE_ACKED' WHERE id=?",Ids.bytes(caseId));
    }

    /** Runs two callables on real threads released by one latch, and returns their outcome codes in completion order. */
    private static List<String> runTogether(Callable<String> a, Callable<String> b) throws Exception {
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (Callable<String> work : List.of(a,b)) {
                futures.add(pool.submit(() -> { start.await(); return work.call(); }));
            }
            start.countDown();
            List<String> outcomes = new ArrayList<>();
            for (Future<String> future : futures) outcomes.add(future.get(120,TimeUnit.SECONDS));
            return outcomes;
        } finally { pool.shutdownNow(); }
    }

    private static int count(org.springframework.jdbc.core.JdbcTemplate jdbc, String table, UUID caseId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE case_id=?",Integer.class,Ids.bytes(caseId));
    }

    private static ObjectNode approve(HandoverStore store, UUID caseId, UUID warehouse, UUID targetDevice,
        long caseVersion, String checklistState) {
        var facts = store.caseFacts(caseId);
        var command = SubmissionJson.object().put("commandVersion",1).put("commandType","APPROVE")
            .put("commandId",UUID.randomUUID().toString()).put("transportRecoveryEpoch","1").put("reason","并发批准")
            .put("warehouseId",warehouse.toString()).put("caseId",caseId.toString())
            .put("stopState","DEVICE_ACKED").put("integrityState","COMPLETE").put("originalResultState","ABORTED")
            .put("physicalState","MATCHED").put("inputHighWatermark","7");
        var expected = SubmissionJson.object();
        for (String key : List.of("caseId","caseVersion","status")) expected.set(key,facts.get(key));
        expected.put("caseVersion",Long.toString(caseVersion));
        expected.put("userAuthVersion","1");
        command.set("expected",expected);
        command.putArray("checklist").addObject().put("baleId",Ids.next().toString())
            .put("deliveryCode","D1").put("state",checklistState).put("dispatchCycle","1");
        return (ObjectNode)command;
    }

    private static UUID user(org.springframework.jdbc.core.JdbcTemplate jdbc, UUID warehouse, String permission, String username) {
        UUID id = Ids.next();
        jdbc.update("INSERT INTO app_user(id,username,display_name,password_hash,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(3))",
            Ids.bytes(id),username,"交接并发","x");
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(id),Ids.bytes(warehouse));
        jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code='WAREHOUSE_ADMIN'",Ids.bytes(id));
        if (permission != null) {
            jdbc.update("INSERT IGNORE INTO role_permission SELECT r.id,p.id FROM role r CROSS JOIN permission p "
                + "WHERE r.code='WAREHOUSE_ADMIN' AND p.code=?",permission);
        }
        return id;
    }

    /** Sequential call on the test thread: it must present the same identity the workers do. */
    private static JsonNode call(HandoverCommandService service, ObjectNode command, UUID actorId, String username) throws Exception {
        var actor = new SessionUser(actorId,username,"交接并发","x",1,true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,"unused",List.of()));
        try {
            byte[] response = service.execute(SubmissionJson.canonical(command).getBytes(StandardCharsets.UTF_8),"conc-"+Ids.next());
            return SubmissionJson.parse(new String(response,StandardCharsets.UTF_8));
        } finally { SecurityContextHolder.clearContext(); }
    }

    /** Executes once under the given identity and returns "OK" or the refusal code; each worker has its own context. */
    private static String callCode(HandoverCommandService service, ObjectNode command, UUID actorId, String username) {
        var actor = new SessionUser(actorId,username,"交接并发","x",1,true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,"unused",List.of()));
        try {
            service.execute(SubmissionJson.canonical(command).getBytes(StandardCharsets.UTF_8),"conc-check");
            return "OK";
        } catch (BusinessException refused) {
            return refused.code();
        } catch (Exception other) {
            return other.getClass().getSimpleName();
        } finally { SecurityContextHolder.clearContext(); }
    }
}
