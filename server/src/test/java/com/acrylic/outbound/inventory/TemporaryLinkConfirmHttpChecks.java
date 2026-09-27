package com.acrylic.outbound.inventory;

import com.acrylic.outbound.auth.SessionUser;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.devices.DeviceCrypto;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * M2b-4b real-HTTP verification on the isolated non-empty MySQL: the two POSTs under test
 * (LINK then NORMAL_CONFIRM) go through the running server on 127.0.0.1:19434 with a Bearer PDA session
 * minted exactly like {@code E2aCrossSourceHttpSetup.session()}. State preparation reuses
 * {@link TemporaryConfirmationFixture}; the failure paths stay in {@link TemporaryConfirmationChecks} and
 * {@link TemporaryLinkChecks} and are deliberately not re-implemented here.
 */
public final class TemporaryLinkConfirmHttpChecks {
    private static final String BASE = "http://127.0.0.1:19434/api/v1/pda/temporary-commands";
    private final WebApplicationContext context;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private TemporaryLinkConfirmHttpChecks(WebApplicationContext context) { this.context = context; }

    public static void verify(WebApplicationContext context) throws Exception { new TemporaryLinkConfirmHttpChecks(context).check(); }

    private void check() throws Exception {
        var f = new TemporaryConfirmationFixture(context, false);
        SessionUser actor = f.operator();
        String warehouse = f.source.f.web.identity().warehouse().toString();
        grantLink(f, actor);
        String token = session(f, actor);
        String tempOrder = string(f, "SELECT BIN_TO_UUID(canonical_order_id) FROM submission_scope WHERE current_submission_id=UUID_TO_BIN(?)", f.source.id());
        // Earlier scenarios reuse the same synthetic vehicle/products; keep their orders out of this window.
        f.jdbc.update("UPDATE outbound_order SET created_at=DATE_SUB(created_at,INTERVAL 30 DAY) WHERE source='WEB' AND status='READY' AND warehouse_id=UUID_TO_BIN(?)", warehouse);
        String target = webOrder(f, actor, warehouse, tempOrder, "FH-LINKCONFIRM-0001");
        assertThat(candidateTotal(f, warehouse)).as("reviewed candidate set is exactly the linked target").isEqualTo(1);

        // 1) LINK over real HTTP
        byte[] linkOriginal = post(token, f.command("LINK", actor).put("reason", "人工核对疑似重复正式单后确认受控关联"));
        var linkReceipt = SubmissionJson.parse(linkOriginal);
        assertThat(linkReceipt.path("status").asText()).isEqualTo("RECORDED");
        assertThat(linkReceipt.path("commandType").asText()).isEqualTo("LINK");
        assertThat(linkReceipt.at("/businessDecision/submissionReceipt/applyState").asText()).isEqualTo("APPLIED");
        assertThat(linkReceipt.at("/businessDecision/submissionReceipt/mappingVersion").asText()).isEqualTo("2");
        assertThat(linkReceipt.at("/businessDecision/submissionReceipt/targetOrderId").asText()).isEqualTo(target);
        assertThat(longValue(f, "SELECT mapping_version FROM submission_scope WHERE current_submission_id=UUID_TO_BIN(?)", f.source.id())).isEqualTo(2);
        assertThat(canonical(f)).isEqualTo(target);
        assertThat(orderStatus(f, tempOrder)).isEqualTo("LINKED");
        assertThat(orderStatus(f, target)).isEqualTo("READY");

        // 2) NORMAL_CONFIRM over real HTTP on the now linked source
        var confirm = f.command("NORMAL_CONFIRM", actor);
        byte[] confirmOriginal = post(token, confirm);
        var receipt = SubmissionJson.parse(confirmOriginal);
        assertThat(receipt.path("status").asText()).isEqualTo("RECORDED");
        assertThat(receipt.path("commandType").asText()).isEqualTo("NORMAL_CONFIRM");
        assertThat(receipt.path("businessDecision").isNull()).isFalse();
        assertThat(receipt.at("/businessDecision/submissionReceipt/orderId").asText()).isEqualTo(target);
        assertThat(receipt.at("/businessDecision/submissionReceipt/status").asText()).isEqualTo("SUCCEEDED");
        for (String key : List.of("writeConfirmationAvailable", "releaseAllowed", "stockReleased", "mayResumeScanning", "localDeletionAllowed"))
            assertThat(receipt.path("capabilities").path(key).asBoolean()).isFalse();

        // 3) The effects must land on the formal order, never on the superseded PDA_TEMP order.
        assertThat(string(f, "SELECT BIN_TO_UUID(order_id) FROM order_result_version WHERE submission_id=UUID_TO_BIN(?)", f.source.id()))
            .as("order_result_version.order_id").isEqualTo(target);
        assertThat(longValue(f, "SELECT COUNT(*) FROM order_result_version WHERE submission_id=UUID_TO_BIN(?) AND order_id=UUID_TO_BIN(?)", f.source.id(), tempOrder))
            .as("no result row on the superseded TEMP order").isZero();
        assertThat(longValue(f, "SELECT current_result_version FROM outbound_order WHERE id=UUID_TO_BIN(?)", target))
            .as("formal order current_result_version").isEqualTo(1);
        assertThat(orderStatus(f, target)).as("formal order status").isEqualTo("COMPLETED");
        assertThat(orderStatus(f, tempOrder)).as("superseded TEMP order keeps LINKED").isEqualTo("LINKED");
        assertThat(string(f, "SELECT phase FROM order_submission_slot WHERE order_id=UUID_TO_BIN(?) AND current_submission_id=UUID_TO_BIN(?)", target, f.source.id()))
            .as("slot on the formal order").isEqualTo("COMPLETED");
        assertThat(longValue(f, "SELECT COUNT(*) FROM bale_commitment WHERE submission_id=UUID_TO_BIN(?) AND order_id=UUID_TO_BIN(?) AND state='DISPATCHED'", f.source.id(), target))
            .as("dispatched commitments on the formal order").isPositive();
        assertThat(longValue(f, "SELECT COUNT(*) FROM bale_commitment WHERE submission_id=UUID_TO_BIN(?) AND order_id<>UUID_TO_BIN(?)", f.source.id(), target))
            .as("commitments outside the formal order").isZero();
        assertThat(longValue(f, "SELECT COUNT(*) FROM dispatch_record WHERE submission_id=UUID_TO_BIN(?) AND order_id=UUID_TO_BIN(?)", f.source.id(), target))
            .as("dispatch records on the formal order").isPositive();
        assertThat(string(f, "SELECT s.status FROM submission s WHERE s.id=UUID_TO_BIN(?)", f.source.id()))
            .as("submission status").isEqualTo("SUCCEEDED");
        assertThat(longValue(f, "SELECT d.decision_version FROM temporary_decision_state d JOIN submission s ON s.scope_id=d.scope_id WHERE s.id=UUID_TO_BIN(?)", f.source.id()))
            .as("decision version after link+confirm").isEqualTo(2);
        assertThat(longValue(f, "SELECT COUNT(*) FROM temporary_command_receipt WHERE submission_id=UUID_TO_BIN(?)", f.source.id()))
            .as("stored receipts").isEqualTo(2);

        // 4) Repeated confirmation: the same original key replays the stored bytes and adds no effect.
        assertThat(post(token, confirm)).as("replay returns the stored original receipt").isEqualTo(confirmOriginal);
        assertThat(longValue(f, "SELECT COUNT(*) FROM order_result_version WHERE submission_id=UUID_TO_BIN(?)", f.source.id())).isEqualTo(1);
        assertThat(longValue(f, "SELECT COUNT(*) FROM temporary_command_receipt WHERE submission_id=UUID_TO_BIN(?)", f.source.id())).isEqualTo(2);
        assertThat(longValue(f, "SELECT COUNT(*) FROM api_idempotency_request q JOIN temporary_command_receipt r ON r.api_request_id=q.id WHERE r.submission_id=UUID_TO_BIN(?)", f.source.id())).isEqualTo(2);
    }

    private byte[] post(String token, ObjectNode command) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(BASE))
            .timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + token)
            .POST(HttpRequest.BodyPublishers.ofByteArray(SubmissionJson.canonical(command).getBytes(StandardCharsets.UTF_8)))
            .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).as("POST " + command.get("commandType").asText() + ": " + new String(response.body(), StandardCharsets.UTF_8))
            .isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
        return response.body();
    }

    private void grantLink(TemporaryConfirmationFixture f, SessionUser actor) {
        UUID role = Ids.next();
        f.jdbc.update("INSERT INTO role VALUES(?,?,?)", Ids.bytes(role), "LINK_" + role.toString().substring(20), "隔离关联权限");
        f.jdbc.update("INSERT INTO role_permission SELECT ?,id FROM permission WHERE code='temporary_order:link'", Ids.bytes(role));
        f.jdbc.update("INSERT INTO user_role VALUES(?,?)", Ids.bytes(actor.id()), Ids.bytes(role));
    }

    /** Mint a PDA session for the fixture's own device/user, exactly like E2aCrossSourceHttpSetup does. */
    private String session(TemporaryConfirmationFixture f, SessionUser actor) {
        String token = DeviceCrypto.secret();
        f.jdbc.update("""
            INSERT INTO pda_session(id,device_id,user_id,user_auth_version,device_version,access_hash,refresh_hash,
              access_expires_at,expires_at,created_at)
            VALUES(?,?,?,1,1,?,?,DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 1 DAY),
              DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 2 DAY),UTC_TIMESTAMP(3))
            """, Ids.bytes(UUID.randomUUID()), Ids.bytes(UUID.fromString(f.source.a.device())), Ids.bytes(actor.id()),
            DeviceCrypto.sha(token), DeviceCrypto.sha(DeviceCrypto.secret()));
        return token;
    }

    private String webOrder(TemporaryConfirmationFixture f, SessionUser actor, String warehouse, String tempOrder, String orderNo) {
        UUID order = Ids.next(), revision = Ids.next();
        f.jdbc.update("INSERT INTO outbound_order(id,warehouse_id,order_no,source,status,current_revision,created_by,created_at,updated_at) "
            + "VALUES(?,?,?,'WEB','READY',NULL,?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))", Ids.bytes(order), Ids.bytes(UUID.fromString(warehouse)),
            orderNo, Ids.bytes(actor.id()));
        f.jdbc.update("""
            INSERT INTO order_revision(id,order_id,warehouse_id,revision,inventory_snapshot_id,mapping_revision,vehicle_no,container_no,
             notes,planned_total,reason,created_by,created_at)
            SELECT ?,?,?,1,inventory_snapshot_id,mapping_revision,vehicle_no,container_no,notes,planned_total,reason,created_by,created_at
            FROM order_revision WHERE order_id=UUID_TO_BIN(?) AND revision=1
            """, Ids.bytes(revision), Ids.bytes(order), Ids.bytes(UUID.fromString(warehouse)), tempOrder);
        f.jdbc.update("INSERT INTO order_line(revision_id,delivery_code,mapping_id,product_id,model_snapshot,name_snapshot,planned_count) "
            + "SELECT ?,delivery_code,mapping_id,product_id,model_snapshot,name_snapshot,planned_count FROM order_line "
            + "WHERE revision_id=(SELECT id FROM order_revision WHERE order_id=UUID_TO_BIN(?) AND revision=1)", Ids.bytes(revision), tempOrder);
        f.jdbc.update("UPDATE outbound_order SET current_revision=1 WHERE id=?", Ids.bytes(order));
        return order.toString();
    }

    private int candidateTotal(TemporaryConfirmationFixture f, String warehouse) {
        return f.bean(com.acrylic.outbound.temporary.review.TemporaryDecisionCandidates.class)
            .snapshot(UUID.fromString(warehouse), UUID.fromString(f.source.id()), f.source.body).path("total").asInt();
    }

    private String canonical(TemporaryConfirmationFixture f) {
        return string(f, "SELECT BIN_TO_UUID(canonical_order_id) FROM submission_scope WHERE current_submission_id=UUID_TO_BIN(?)", f.source.id());
    }

    private String orderStatus(TemporaryConfirmationFixture f, String order) {
        return string(f, "SELECT status FROM outbound_order WHERE id=UUID_TO_BIN(?)", order);
    }

    private static String string(TemporaryConfirmationFixture f, String sql, Object... args) {
        return f.jdbc.queryForObject(sql, String.class, args);
    }

    private static long longValue(TemporaryConfirmationFixture f, String sql, Object... args) {
        return f.jdbc.queryForObject(sql, Long.class, args);
    }
}
