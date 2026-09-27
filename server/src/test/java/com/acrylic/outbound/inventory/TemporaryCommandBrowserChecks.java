package com.acrylic.outbound.inventory;

import com.acrylic.outbound.auth.SessionUser;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A3 (browser variant): the Web-session command chain is driven by a real browser through tools/e2a-web-command-flow.mjs
 * (login, facts, LINK, facts, NORMAL_CONFIRM all from the page context, so cookies and CSRF follow the SPA flow).
 * This class owns the state and the database landing assertions; the script owns the HTTP session.
 */
public final class TemporaryCommandBrowserChecks {
    private static final String PASSWORD = "Test-password-123";

    private TemporaryCommandBrowserChecks() { }

    public static void verify(WebApplicationContext context, Path attempt) throws Exception {
        var f = new TemporaryConfirmationFixture(context,false);
        SessionUser supervisor = f.supervisor();
        UUID role=Ids.next();
        f.jdbc.update("INSERT INTO role VALUES(?,?,?)",Ids.bytes(role),"BROWSER_"+role.toString().substring(20),"隔离Web命令权限");
        f.jdbc.update("INSERT INTO role_permission SELECT ?,id FROM permission WHERE code='temporary_order:link'",Ids.bytes(role));
        f.jdbc.update("INSERT INTO user_role VALUES(?,?)",Ids.bytes(supervisor.id()),Ids.bytes(role));
        String warehouse = f.source.f.web.identity().warehouse().toString();
        String submission = f.source.id();
        f.jdbc.update("UPDATE outbound_order SET created_at=DATE_SUB(created_at,INTERVAL 30 DAY) "
            + "WHERE source='WEB' AND status='READY' AND warehouse_id=UUID_TO_BIN(?)",warehouse);
        String temp = string(f,"SELECT BIN_TO_UUID(canonical_order_id) FROM submission_scope WHERE current_submission_id=UUID_TO_BIN(?)",submission);
        String target = webOrder(f,supervisor,warehouse,temp,"FH-BROWSER-0001");
        assertThat(candidateTotal(f,warehouse)).isEqualTo(1);

        Path output = attempt.resolve("browser-command-flow.json");
        Path log = attempt.resolve("browser-command-flow.log");
        var command = new ProcessBuilder("node",
            Path.of("D:/acrylic-pda-outbound/web/tools/e2a-web-command-flow.mjs").toString(),
            supervisor.username(), PASSWORD, warehouse, submission, output.toString())
            .directory(Path.of("D:/acrylic-pda-outbound/web").toFile())
            .redirectErrorStream(true).redirectOutput(log.toFile());
        var process = command.start();
        int exit = process.waitFor();
        String logText = Files.exists(log) ? Files.readString(log, StandardCharsets.UTF_8) : "";
        assertThat(Files.exists(output)).as("browser flow output missing; log=" + logText).isTrue();
        JsonNode flow = SubmissionJson.parse(Files.readAllBytes(output));
        assertThat(exit).as("browser flow exit; steps=" + flow.path("steps") + " log=" + logText).isZero();
        assertThat(flow.path("failed").isNull()).as("browser flow failed at " + flow.path("failed") + " steps=" + flow.path("steps")).isTrue();

        // A1 facts surface, as seen by the browser.
        var factsKeys = new TreeSet<String>();flow.path("factsKeys").forEach(node->factsKeys.add(node.asText()));
        assertThat(factsKeys).containsExactlyInAnyOrder("expected","warehouseId","submissionId","workRound","authorizationId",
            "freezeDeviceId","sourceRecoveryEpoch","source");
        assertThat(flow.path("expectedKeys").size()).isEqualTo(21);
        assertThat(flow.path("linkStatus").asInt()).isEqualTo(200);
        assertThat(flow.path("confirmStatus").asInt()).isEqualTo(200);
        assertThat(flow.at("/receipts/link/status").asText()).isEqualTo("RECORDED");
        assertThat(flow.at("/receipts/link/commandType").asText()).isEqualTo("LINK");
        assertThat(flow.at("/receipts/confirm/status").asText()).isEqualTo("RECORDED");
        assertThat(flow.at("/receipts/confirm/commandType").asText()).isEqualTo("NORMAL_CONFIRM");
        assertThat(flow.path("mappingVersionAfterLink").asText()).isEqualTo("2");
        assertThat(flow.path("canonicalOrderAfterLink").asText()).isEqualTo(target);
        for(String key:List.of("writeConfirmationAvailable","releaseAllowed","stockReleased","mayResumeScanning","localDeletionAllowed"))
            assertThat(flow.at("/receipts/confirm/capabilities/"+key).asBoolean()).isFalse();

        // Same landing points as the PDA-session attempt.
        assertThat(string(f,"SELECT BIN_TO_UUID(order_id) FROM order_result_version WHERE submission_id=UUID_TO_BIN(?)",submission)).isEqualTo(target);
        assertThat(longValue(f,"SELECT COUNT(*) FROM order_result_version WHERE submission_id=UUID_TO_BIN(?) AND order_id=UUID_TO_BIN(?)",submission,temp)).isZero();
        assertThat(longValue(f,"SELECT current_result_version FROM outbound_order WHERE id=UUID_TO_BIN(?)",target)).isEqualTo(1);
        assertThat(orderStatus(f,target)).isEqualTo("COMPLETED");
        assertThat(orderStatus(f,temp)).isEqualTo("LINKED");
        assertThat(string(f,"SELECT phase FROM order_submission_slot WHERE order_id=UUID_TO_BIN(?) AND current_submission_id=UUID_TO_BIN(?)",target,submission))
            .isEqualTo("COMPLETED");
        assertThat(longValue(f,"SELECT COUNT(*) FROM bale_commitment WHERE submission_id=UUID_TO_BIN(?) AND order_id=UUID_TO_BIN(?) AND state='DISPATCHED'",submission,target)).isPositive();
        assertThat(longValue(f,"SELECT COUNT(*) FROM bale_commitment WHERE submission_id=UUID_TO_BIN(?) AND order_id<>UUID_TO_BIN(?)",submission,target)).isZero();
        assertThat(longValue(f,"SELECT COUNT(*) FROM dispatch_record WHERE submission_id=UUID_TO_BIN(?) AND order_id=UUID_TO_BIN(?)",submission,target)).isPositive();
        assertThat(string(f,"SELECT status FROM submission WHERE id=UUID_TO_BIN(?)",submission)).isEqualTo("SUCCEEDED");
        assertThat(longValue(f,"SELECT d.decision_version FROM temporary_decision_state d JOIN submission s ON s.scope_id=d.scope_id WHERE s.id=UUID_TO_BIN(?)",submission)).isEqualTo(2);
        assertThat(longValue(f,"SELECT COUNT(*) FROM temporary_command_receipt WHERE submission_id=UUID_TO_BIN(?)",submission)).isEqualTo(2);
    }

    private static String webOrder(TemporaryConfirmationFixture f,SessionUser actor,String warehouse,String template,String orderNo) {
        UUID order=Ids.next(),revision=Ids.next();
        f.jdbc.update("INSERT INTO outbound_order(id,warehouse_id,order_no,source,status,current_revision,created_by,created_at,updated_at) "
            +"VALUES(?,?,?,'WEB','READY',NULL,?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",Ids.bytes(order),Ids.bytes(UUID.fromString(warehouse)),
            orderNo,Ids.bytes(actor.id()));
        f.jdbc.update("""
            INSERT INTO order_revision(id,order_id,warehouse_id,revision,inventory_snapshot_id,mapping_revision,vehicle_no,container_no,
             notes,planned_total,reason,created_by,created_at)
            SELECT ?,?,?,1,inventory_snapshot_id,mapping_revision,vehicle_no,container_no,notes,planned_total,reason,created_by,created_at
            FROM order_revision WHERE order_id=UUID_TO_BIN(?) AND revision=1
            """,Ids.bytes(revision),Ids.bytes(order),Ids.bytes(UUID.fromString(warehouse)),template);
        f.jdbc.update("INSERT INTO order_line(revision_id,delivery_code,mapping_id,product_id,model_snapshot,name_snapshot,planned_count) "
            +"SELECT ?,delivery_code,mapping_id,product_id,model_snapshot,name_snapshot,planned_count FROM order_line "
            +"WHERE revision_id=(SELECT id FROM order_revision WHERE order_id=UUID_TO_BIN(?) AND revision=1)",Ids.bytes(revision),template);
        f.jdbc.update("UPDATE outbound_order SET current_revision=1 WHERE id=?",Ids.bytes(order));
        return order.toString();
    }
    private static int candidateTotal(TemporaryConfirmationFixture f,String warehouse) {
        return f.bean(com.acrylic.outbound.temporary.review.TemporaryDecisionCandidates.class)
            .snapshot(UUID.fromString(warehouse),UUID.fromString(f.source.id()),f.source.body).path("total").asInt();
    }
    private static String orderStatus(TemporaryConfirmationFixture f,String order) {
        return string(f,"SELECT status FROM outbound_order WHERE id=UUID_TO_BIN(?)",order);
    }
    private static String string(TemporaryConfirmationFixture f,String sql,Object... args) { return f.jdbc.queryForObject(sql,String.class,args); }
    private static long longValue(TemporaryConfirmationFixture f,String sql,Object... args) { return f.jdbc.queryForObject(sql,Long.class,args); }
}
