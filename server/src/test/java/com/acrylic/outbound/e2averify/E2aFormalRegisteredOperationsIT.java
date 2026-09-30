package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.auth.SessionUser;
import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.handover.HandoverActivation;
import com.acrylic.outbound.handover.HandoverCommandService;
import com.acrylic.outbound.handover.HandoverStore;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

/**
 * R07-1 operations-layer assertions that had <b>no landing</b> before this round (A2.2, matrix rows M5/M8/M9/M13-2).
 *
 * <p>Verification level is the <b>service layer</b> (SecurityContextHolder + the real command service / activation
 * object), on top of the SQL-built REGISTERED FORMAL source — the same level and fixture the four counterexamples of
 * {@link E2aFormalRegisteredCounterexamplesIT} use. It is <b>NOT</b> HTTP end to end and says nothing about the HTTP
 * layer, the device half or a real PDA. The release guards that stop the chain before the checklist is read are
 * satisfied by <b>directly recording</b> the state the guard requires (an appended STOP_PROOF row and the case's
 * {@code stop_state}): that is exactly the seam whose refusal the existing counterexample already proves, so asserting
 * it again here would prove nothing new.
 */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aFormalRegisteredOperationsIT {
    @Test void operationsLayerRefusesInvalidChecklistStaleVersionUnfinishedResultAndMissingCase() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "42"); E2aCrossSourceHttpSetup.signing(profile.attempt);
        profile.migrate(jdbc, "44");
        try (var context = E2aCrossSourceHttpIT.start(profile, "--outbound.handover.commands.enabled=true")) {
            verify(context, profile.attempt);
        }
        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "operations layer: %s\n".formatted("invalid checklist / stale version / unfinished result / missing case / atomic refusal"));
    }

    private void verify(org.springframework.context.ApplicationContext context, java.nio.file.Path attempt) throws Exception {
        var jdbc = context.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
        var store = context.getBean(HandoverStore.class);
        var service = context.getBean(HandoverCommandService.class);
        var activation = context.getBean(HandoverActivation.class);
        var notes = new StringBuilder("verification level=service layer (SecurityContextHolder + HandoverCommandService/HandoverActivation); HTTP layer not verified\n");

        var host = jdbc.queryForMap("""
            SELECT BIN_TO_UUID(a.id) assignment_id,BIN_TO_UUID(a.order_id) order_id,BIN_TO_UUID(a.warehouse_id) warehouse_id,
              BIN_TO_UUID(a.device_id) device_id,BIN_TO_UUID(a.operator_id) operator_id,CAST(a.recovery_epoch AS CHAR) recovery_epoch
            FROM order_assignment a WHERE a.status='ACTIVE' LIMIT 1
            """);
        UUID warehouse = UUID.fromString(host.get("warehouse_id").toString());
        UUID order = UUID.fromString(host.get("order_id").toString());
        UUID assignment = UUID.fromString(host.get("assignment_id").toString());
        UUID device = UUID.fromString(host.get("device_id").toString());

        String requesterName = "ops-"+Ids.next(); UUID requester = user(jdbc, warehouse, "order:resume", requesterName);
        String approverName = "ops-"+Ids.next(); UUID approver = user(jdbc, warehouse, "order:handover", approverName);

        UUID scope = UUID.randomUUID(); UUID submission = UUID.randomUUID();
        jdbc.update("INSERT INTO submission_scope(id,warehouse_id,source_order_id,work_round,current_submission_id) VALUES(?,?,?,1,NULL)",
            Ids.bytes(scope),Ids.bytes(warehouse),Ids.bytes(order));
        jdbc.update("INSERT INTO submission(id,scope_id,assignment_id,freeze_device_id,operator_id,work_round,"
            + "source_recovery_epoch,status,issues_json,receipt_text,created_at,updated_at) "
            + "VALUES(?,?,?,?,?,1,?,'REGISTERED',NULL,NULL,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
            Ids.bytes(submission),Ids.bytes(scope),Ids.bytes(assignment),Ids.bytes(device),
            Ids.bytes(UUID.fromString(host.get("operator_id").toString())),Long.parseLong(host.get("recovery_epoch").toString()));
        jdbc.update("UPDATE submission_scope SET current_submission_id=? WHERE id=?",Ids.bytes(submission),Ids.bytes(scope));

        // REQUEST opens the single open case; it is the only accepted call before the assertions below.
        var scopeFacts = store.scopeFacts(scope);
        var request = SubmissionJson.object().put("commandVersion",1).put("commandType","REQUEST")
            .put("commandId",UUID.randomUUID().toString()).put("transportRecoveryEpoch","1").put("reason","原机可用，申请交接")
            .put("warehouseId",warehouse.toString()).put("scopeId",scope.toString());
        var requestExpected = SubmissionJson.object();
        for (String key : List.of("scopeId","canonicalOrderId","mappingVersion","fromWorkRound")) requestExpected.set(key,scopeFacts.get(key));
        requestExpected.put("userAuthVersion","1");
        request.set("expected",requestExpected);
        JsonNode requestReceipt = execute(service,request,requester,requesterName);
        assertThat(requestReceipt.path("businessDecision").path("status").asText()).isEqualTo("EVIDENCE_PENDING");
        UUID caseId = UUID.fromString(requestReceipt.path("businessDecision").path("caseId").asText());
        assertThat(jdbc.queryForObject("SELECT status FROM handover_case WHERE id=?",String.class,Ids.bytes(caseId)))
            .isEqualTo("EVIDENCE_PENDING");

        String baselineStatus = jdbc.queryForObject("SELECT status FROM handover_case WHERE id=?",String.class,Ids.bytes(caseId));
        long baselineVersion = jdbc.queryForObject("SELECT row_version FROM handover_case WHERE id=?",Long.class,Ids.bytes(caseId));
        int baselineChecklist = count(jdbc,"handover_checklist",caseId);
        int baselineReceipts = count(jdbc,"handover_command_receipt",caseId);
        int baselineAudits = audits(jdbc,caseId);

        // --- M5: checklist state is outside the fixed vocabulary -------------------------------------------------
        // Record the state the release guards demand (device stop proof + DEVICE_ACKED), then send a checklist line
        // whose state is neither RETAINED nor REMOVED. The checklist rows are inserted before the four-dimension
        // checks are re-evaluated, so a refusal must leave no checklist row behind.
        // The stop-proof row is the *precondition* of this probe, so the evidence baseline is taken after it.
        recordStopProof(jdbc,caseId,device);
        int baselineEvidence = count(jdbc,"handover_evidence",caseId);
        String m5;
        try { execute(service,approve(store,caseId,warehouse,baselineVersion,"DEVICE_ACKED","COMPLETE","ABORTED","MATCHED","7","BROKEN"),approver,approverName); m5="NO_ERROR"; }
        catch (BusinessException error) { m5=error.code(); }
        notes.append("M5 清单状态无效 -> ").append(m5).append('\n');
        assertThat(m5).as("M5 invalid checklist state").isEqualTo("HANDOVER_CHECKLIST_REQUIRED");
        assertThat(count(jdbc,"handover_checklist",caseId)).as("M5 must persist no checklist row").isEqualTo(baselineChecklist);

        // --- M9 precondition: a stale case version is refused before any work ------------------------------------
        String m9;
        try { execute(service,approve(store,caseId,warehouse,baselineVersion+1,"DEVICE_ACKED","COMPLETE","ABORTED","MATCHED","7","RETAINED"),approver,approverName); m9="NO_ERROR"; }
        catch (BusinessException error) { m9=error.code(); }
        notes.append("M9a 案件版本过期 -> ").append(m9).append('\n');
        assertThat(m9).as("M9a stale case version").isEqualTo("HANDOVER_CONTEXT_CHANGED");

        // --- M8: the original submission is still open (REGISTERED == NONE_VERIFIED is accepted; RECEIVING is not) --
        jdbc.update("UPDATE submission SET status='RECEIVING' WHERE id=?",Ids.bytes(submission));
        String m8;
        try { execute(service,approve(store,caseId,warehouse,baselineVersion,"DEVICE_ACKED","COMPLETE","NONTERMINAL","MATCHED","7","RETAINED"),approver,approverName); m8="NO_ERROR"; }
        catch (BusinessException error) { m8=error.code(); }
        notes.append("M8 原结果未终结 -> ").append(m8).append('\n');
        assertThat(m8).as("M8 unfinished original result").isEqualTo("HANDOVER_ORIGINAL_RESULT_OPEN");
        jdbc.update("UPDATE submission SET status='REGISTERED' WHERE id=?",Ids.bytes(submission));

        // --- M9: a 404 must not be recorded as if nothing happened ----------------------------------------------
        ObjectNode ghost = approve(store,caseId,warehouse,baselineVersion,"DEVICE_ACKED","COMPLETE","ABORTED","MATCHED","7","RETAINED");
        UUID ghostCase = UUID.randomUUID();
        ghost.put("caseId",ghostCase.toString());
        var ghostFacts = SubmissionJson.object().put("caseId",ghostCase.toString())
            .put("caseVersion",Long.toString(baselineVersion)).put("status","EVIDENCE_PENDING").put("userAuthVersion","1");
        ghost.set("expected",ghostFacts);
        String m9b;
        try { execute(service,ghost,approver,approverName); m9b="NO_ERROR"; }
        catch (BusinessException error) { m9b=error.code(); }
        notes.append("M9b 案件不存在 -> ").append(m9b).append('\n');
        assertThat(m9b).as("M9b missing case").isEqualTo("HANDOVER_CASE_NOT_FOUND");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM handover_command_receipt WHERE case_id=?",Integer.class,Ids.bytes(ghostCase)))
            .as("M9b a 404 must leave no receipt").isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM handover_case WHERE id=?",Integer.class,Ids.bytes(ghostCase)))
            .as("M9b a 404 must leave no case").isZero();

        // --- M13-②: an unwritten checklist state is refused atomically -------------------------------------------
        assertThat(count(jdbc,"handover_checklist",caseId)).as("M13-2 no checklist row survives a refusal").isEqualTo(baselineChecklist);
        assertThat(count(jdbc,"handover_evidence",caseId)).as("M13-2 evidence rows unchanged by refusals").isEqualTo(baselineEvidence);
        assertThat(count(jdbc,"handover_command_receipt",caseId)).as("M13-2 receipt count unchanged by refusals").isEqualTo(baselineReceipts);
        assertThat(audits(jdbc,caseId)).as("M13-2 no audit row for a refused command").isEqualTo(baselineAudits);
        assertThat(jdbc.queryForObject("SELECT status FROM handover_case WHERE id=?",String.class,Ids.bytes(caseId)))
            .as("M13-2 case status unchanged").isEqualTo(baselineStatus);
        assertThat(jdbc.queryForObject("SELECT row_version FROM handover_case WHERE id=?",Long.class,Ids.bytes(caseId)))
            .as("M13-2 case version unchanged").isEqualTo(baselineVersion);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_assignment WHERE order_id=? AND status='ACTIVE'",Integer.class,Ids.bytes(order)))
            .as("M13-2 the assignment is not touched by a refused approval").isEqualTo(1);

        // The activation entry is present and gated on the same case state, which is what M13 will exercise once the
        // R07-1 outbox lands; recording it here keeps the seam visible without claiming the transaction is complete.
        notes.append("M13-1 note: activation object present=").append(activation!=null)
            .append("; full activation+outbox atomicity deferred to A2.3\n");

        // --- M7: activation refuses a publication from a different recovery epoch --------------------------------
        // The guard lives in HandoverActivation.activate: it compares the publication's recovery epoch with the
        // caller's. Driving the activation object directly is the same seam the ACTIVATE command reaches, without
        // fabricating an APPROVED+TARGET_PREPARED case first.
        UUID targetDevice = device;
        UUID targetUser = UUID.fromString(host.get("operator_id").toString());
        String publicationEpoch = jdbc.queryForObject(
            "SELECT CAST(a.recovery_epoch AS CHAR) FROM order_assignment a WHERE a.order_id=? AND a.status='ACTIVE' LIMIT 1",
            String.class,Ids.bytes(order));
        String wrongEpoch = "999999"; // deliberately not any real epoch: the guard must refuse before the baseline check
        String m7;
        try { activation.activate(warehouse,order,targetDevice,targetUser,wrongEpoch,"M7 旧代次"); m7="NO_ERROR"; }
        catch (BusinessException error) { m7=error.code(); }
        notes.append("M7 恢复代次不符 -> ").append(m7).append('\n');
        assertThat(m7).as("M7 recovery epoch mismatch").isEqualTo("ORDER_RECOVERY_REQUIRED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_assignment WHERE order_id=? AND status='ACTIVE'",Integer.class,Ids.bytes(order)))
            .as("M7 a refused activation must not disturb the active round").isEqualTo(1);

        // No control call is made here. With a matching epoch the next guard is the warehouse-baseline comparison — a
        // different seam — and the earlier "control" stopped on ORDER_BASELINE_CHANGED, so it proved nothing about M7.
        // The asserted branch is the epoch comparison and it is the last thing this test touches.

        // --- M6: a stored receipt that cannot be re-proved is refused as an integrity failure, never replayed -------
        // The receipt table is protected by BEFORE UPDATE/DELETE triggers, so corruption cannot be *simulated* by
        // editing a real row. Instead the test writes the row the read path must meet: a structurally valid receipt
        // whose signature does not verify (a forged token), with the row's digests self-consistent. That is the shape a
        // damaged signature write would leave behind, and the read path must refuse it rather than replay it.
        UUID forgedCommandId = UUID.randomUUID();
        var forgedCommand = SubmissionJson.object().put("commandVersion",1).put("commandType","REQUEST")
            .put("commandId",forgedCommandId.toString()).put("transportRecoveryEpoch","1").put("reason","M6 伪造回执")
            .put("warehouseId",warehouse.toString()).put("scopeId",scope.toString());
        forgedCommand.set("expected",requestExpected);
        byte[] forgedCommandBytes = SubmissionJson.canonical(forgedCommand).getBytes(StandardCharsets.UTF_8);
        var forgedReceipt = SubmissionJson.object().put("receiptVersion",1)
            .put("receiptId",UUID.randomUUID().toString()).put("commandId",forgedCommandId.toString())
            .put("routeScope","r07.handover.v1").put("commandType","REQUEST")
            .put("commandSha256",sha256Hex(forgedCommandBytes))
            .put("status","RECORDED").put("recordedAt",java.time.Instant.now().toString())
            .put("eventId",UUID.randomUUID().toString())
            .putNull("businessDecision");
        forgedReceipt.set("caseContext",store.caseFacts(caseId));
        forgedReceipt.set("actor",SubmissionJson.object().put("principalId",approver.toString())
            .put("warehouseId",warehouse.toString()).put("userAuthVersion","1")
            .putNull("deviceId").putNull("sessionId"));
        forgedReceipt.set("expected",forgedCommand.path("expected"));
        forgedReceipt.set("capabilities",capabilities());
        forgedReceipt.put("signedReceipt","forged.signature.value");
        byte[] forgedBytes = SubmissionJson.canonical(forgedReceipt).getBytes(StandardCharsets.UTF_8);
        jdbc.update("INSERT INTO handover_command_receipt(id,case_id,original_key,command_type,command_sha256,"
            + "canonical_command_bytes,response_bytes,response_sha256,actor_id,expected_json,status,event_id,recorded_at) "+
            "VALUES(?,?,?,?,?,?,?,?,?,?,'RECORDED',?,UTC_TIMESTAMP(3))",
            Ids.bytes(Ids.next()),Ids.bytes(caseId),Ids.bytes(forgedCommandId),"REQUEST",
            java.util.HexFormat.of().parseHex(sha256Hex(forgedCommandBytes)),
            forgedCommandBytes,forgedBytes,
            java.util.HexFormat.of().parseHex(sha256Hex(forgedBytes)),
            Ids.bytes(approver),SubmissionJson.canonical(forgedCommand.path("expected")),
            Ids.bytes(Ids.next()));
        int receiptsBeforeM6 = count(jdbc,"handover_command_receipt",caseId);
        String m6;
        try { execute(service,(ObjectNode)forgedCommand,requester,requesterName); m6="NO_ERROR"; }
        catch (BusinessException error) { m6=error.code(); }
        notes.append("M6 伪造回执重放 -> ").append(m6).append('\n');
        assertThat(m6).as("M6 a damaged stored receipt is an integrity failure, not a replay")
            .isEqualTo("HANDOVER_RECEIPT_INTEGRITY_FAILURE");
        assertThat(count(jdbc,"handover_command_receipt",caseId))
            .as("M6 the refused replay writes no new receipt").isEqualTo(receiptsBeforeM6);

        Files.writeString(attempt.resolve("operations.txt"),notes.toString());
    }

    /** The receipt contract's capability block, written out here because the codec keeps it package-private. */
    private static ObjectNode capabilities() {
        return SubmissionJson.object().put("writeConfirmationAvailable",false).put("releaseAllowed",false)
            .put("mayResumeScanning",false).put("stockReleased",false).put("localDeletionAllowed",false);
    }

    private static String sha256Hex(byte[] bytes) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    /** Appends the device-produced stop proof the release guard requires, and records the state it implies. */
    private static void recordStopProof(org.springframework.jdbc.core.JdbcTemplate jdbc, UUID caseId, UUID device) {
        jdbc.update("INSERT INTO handover_evidence(id,case_id,kind,device_id,payload_sha256,high_watermark,receipt_bytes,recorded_at) "
            + "VALUES(?,?,'STOP_PROOF',?,?,7,?,UTC_TIMESTAMP(3))",
            Ids.bytes(Ids.next()),Ids.bytes(caseId),Ids.bytes(device),
            java.util.HexFormat.of().parseHex("a".repeat(64)),"stop-proof-receipt".getBytes(StandardCharsets.UTF_8));
        jdbc.update("UPDATE handover_case SET stop_state='DEVICE_ACKED' WHERE id=?",Ids.bytes(caseId));
    }

    private static int count(org.springframework.jdbc.core.JdbcTemplate jdbc, String table, UUID caseId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE case_id=?",Integer.class,Ids.bytes(caseId));
    }

    private static int audits(org.springframework.jdbc.core.JdbcTemplate jdbc, UUID caseId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM operation_event WHERE entity_type='HANDOVER_COMMAND' "
            + "AND entity_id IN (SELECT id FROM handover_command_receipt WHERE case_id=?)",Integer.class,Ids.bytes(caseId));
    }

    private ObjectNode approve(HandoverStore store, UUID caseId, UUID warehouse, long caseVersion, String stop,
        String integrity, String originalResult, String physical, String highWatermark, String checklistState) {
        var facts = store.caseFacts(caseId);
        var command = SubmissionJson.object().put("commandVersion",1).put("commandType","APPROVE")
            .put("commandId",UUID.randomUUID().toString()).put("transportRecoveryEpoch","1").put("reason","主管逐包核对后批准")
            .put("warehouseId",warehouse.toString()).put("caseId",caseId.toString())
            .put("stopState",stop).put("integrityState",integrity).put("originalResultState",originalResult)
            .put("physicalState",physical).put("inputHighWatermark",highWatermark);
        var expected = SubmissionJson.object();
        for (String key : List.of("caseId","caseVersion","status")) expected.set(key,facts.get(key));
        expected.put("caseVersion",Long.toString(caseVersion));
        expected.put("userAuthVersion","1");
        command.set("expected",expected);
        command.putArray("checklist").addObject().put("baleId",Ids.next().toString())
            .put("deliveryCode","D1").put("state",checklistState).put("dispatchCycle","1");
        return (ObjectNode)command;
    }

    private UUID user(org.springframework.jdbc.core.JdbcTemplate jdbc, UUID warehouse, String permission, String username) {
        UUID id = Ids.next();
        jdbc.update("INSERT INTO app_user(id,username,display_name,password_hash,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(3))",
            Ids.bytes(id),username,"交接运维","x");
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(id),Ids.bytes(warehouse));
        jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code='WAREHOUSE_ADMIN'",Ids.bytes(id));
        if (permission != null) {
            // The vendor migrations may already grant this code to WAREHOUSE_ADMIN (V44 does), so the grant is
            // idempotent: what matters here is "the actor's role holds the permission", not "insert a row".
            jdbc.update("INSERT IGNORE INTO role_permission SELECT r.id,p.id FROM role r CROSS JOIN permission p "
                + "WHERE r.code='WAREHOUSE_ADMIN' AND p.code=?",permission);
        }
        return id;
    }

    private JsonNode execute(HandoverCommandService service, ObjectNode command, UUID actorId, String username) throws Exception {
        var actor = new SessionUser(actorId,username,"交接运维","x",1,true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,"unused",List.of()));
        try {
            byte[] response = service.execute(SubmissionJson.canonical(command).getBytes(StandardCharsets.UTF_8),"ops-layer-check");
            return SubmissionJson.parse(response);
        } finally { SecurityContextHolder.clearContext(); }
    }
}
