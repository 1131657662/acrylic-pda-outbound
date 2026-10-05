package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.devices.DeviceCrypto;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.UUID;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** A fresh isolated source, matching formal target and PDA HTTP identity for linked vectors. */
final class TemporaryLinkedFixture {
    final TemporaryConfirmationFixture source;
    final String originalOrder;
    final String target;
    final UUID sessionId = Ids.next();
    private final String token = DeviceCrypto.secret();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    TemporaryLinkedFixture(WebApplicationContext context) throws Exception {
        source = new TemporaryConfirmationFixture(context, false);
        originalOrder = source.jdbc.queryForObject("SELECT BIN_TO_UUID(canonical_order_id) FROM submission_scope "
            + "WHERE current_submission_id=UUID_TO_BIN(?)", String.class, source.source.id());
        grantLink();
        target = createTarget();
        createSession();
    }

    private void grantLink() {
        UUID role = Ids.next();
        source.jdbc.update("INSERT INTO role VALUES(?,?,?)", Ids.bytes(role),
            "LV_" + role.toString().substring(20), "隔离关联向量权限");
        source.jdbc.update("INSERT INTO role_permission SELECT ?,id FROM permission WHERE code='temporary_order:link'", Ids.bytes(role));
        source.jdbc.update("INSERT INTO user_role VALUES(?,?)", Ids.bytes(source.operator().id()), Ids.bytes(role));
    }

    String createTarget() {
        UUID order = Ids.next(), revision = Ids.next();
        var warehouse = source.source.f.web.identity().warehouse();
        source.jdbc.update("INSERT INTO outbound_order(id,warehouse_id,order_no,source,status,current_revision,created_by,created_at,updated_at) "
            + "VALUES(?,?,?,'WEB','READY',NULL,?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))", Ids.bytes(order), Ids.bytes(warehouse),
            "FH-LINK-VECTOR-" + order.toString().substring(24), Ids.bytes(source.operator().id()));
        source.jdbc.update("""
            INSERT INTO order_revision(id,order_id,warehouse_id,revision,inventory_snapshot_id,mapping_revision,vehicle_no,container_no,
              notes,planned_total,reason,created_by,created_at)
            SELECT ?,?,?,1,inventory_snapshot_id,mapping_revision,vehicle_no,container_no,notes,planned_total,reason,created_by,created_at
            FROM order_revision WHERE order_id=UUID_TO_BIN(?) AND revision=1
            """, Ids.bytes(revision), Ids.bytes(order), Ids.bytes(warehouse), originalOrder);
        source.jdbc.update("INSERT INTO order_line(revision_id,delivery_code,mapping_id,product_id,model_snapshot,name_snapshot,planned_count) "
            + "SELECT ?,delivery_code,mapping_id,product_id,model_snapshot,name_snapshot,planned_count FROM order_line "
            + "WHERE revision_id=(SELECT id FROM order_revision WHERE order_id=UUID_TO_BIN(?) AND revision=1)", Ids.bytes(revision), originalOrder);
        source.jdbc.update("UPDATE outbound_order SET current_revision=1 WHERE id=?", Ids.bytes(order));
        return order.toString();
    }

    private void createSession() {
        source.jdbc.update("""
            INSERT INTO pda_session(id,device_id,user_id,user_auth_version,device_version,access_hash,refresh_hash,
              access_expires_at,expires_at,created_at)
            VALUES(?,?,?,1,1,?,?,DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 1 DAY),
              DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 2 DAY),UTC_TIMESTAMP(3))
            """, Ids.bytes(sessionId), Ids.bytes(UUID.fromString(source.source.a.device())), Ids.bytes(source.operator().id()),
            DeviceCrypto.sha(token), DeviceCrypto.sha(DeviceCrypto.secret()));
    }

    byte[] post(ObjectNode command) throws Exception {
        var response = send(command);
        assertThat(response.statusCode()).as("command HTTP status; code=%s", SubmissionJson.parse(response.body()).path("code")).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
        return response.body();
    }

    HttpResponse<byte[]> send(ObjectNode command) throws Exception {
        var request = request("/pda/temporary-commands").header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(TemporaryConfirmationFixture.bytes(command))).build();
        return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    com.fasterxml.jackson.databind.JsonNode detail(String commandId) throws Exception {
        var path = "/pda/temporary-command-receipts/" + commandId + "?warehouseId=" + source.source.f.web.identity().warehouse();
        var response = client.send(request(path).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        return SubmissionJson.parse(response.body()).path("data");
    }

    String success() throws Exception {
        var response = client.send(request("/pda/submissions/" + source.source.id()).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1" + path))
            .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + token);
    }
}
