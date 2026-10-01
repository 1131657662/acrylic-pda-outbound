package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.assertThat;

/** Synthetic FORMAL history; browser actor has separate supervisor and order-read roles. */
final class HandoverListFixture {
    final HandoverRollbackFixture f;
    final HandoverReadHttp maker, denied, anonymous;
    final UUID otherWarehouse = Ids.next();
    final Path attempt;
    final List<String> ids = new ArrayList<>();

    HandoverListFixture(ConfigurableApplicationContext app, Path attempt) throws Exception {
        this.attempt = attempt; var evidence = Files.createDirectory(attempt.resolve("list-http"));
        f = new HandoverRollbackFixture(app.getBean(JdbcTemplate.class));
        var password = app.getBean(PasswordEncoder.class).encode("Test-password-123");
        f.jdbc.update("UPDATE app_user SET password_hash=? WHERE id=?", password, Ids.bytes(f.requester.id()));
        // Keep the approval role unchanged; only this synthetic actor also has the existing order-reader role.
        assertThat(f.jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code='ORDER_VIEWER'",
            Ids.bytes(f.requester.id()))).isEqualTo(1);
        var permissions = app.getBean(com.acrylic.outbound.auth.IdentityStore.class).permissions(f.requester.id());
        assertThat(permissions).contains("order:read", "order:handover");
        Files.write(attempt.resolve("browser-actor-permissions.txt"), permissions);
        f.jdbc.update("INSERT INTO warehouse(id,code,name) VALUES(?,?,?)", Ids.bytes(otherWarehouse), "LIST-OTHER", "交接隔离仓");
        f.jdbc.update("INSERT INTO user_warehouse VALUES(?,?)", Ids.bytes(f.requester.id()), Ids.bytes(otherWarehouse));
        UUID user = Ids.next(); String name = "list-denied-" + user;
        f.jdbc.update("INSERT INTO app_user(id,username,display_name,password_hash,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(3))",
            Ids.bytes(user), name, "list denied", password);
        f.jdbc.update("INSERT INTO user_warehouse VALUES(?,?)", Ids.bytes(user), Ids.bytes(f.warehouse));
        maker = new HandoverReadHttp(f.requester.username(), evidence, "maker");
        denied = new HandoverReadHttp(name, evidence, "denied");
        anonymous = new HandoverReadHttp(null, evidence, "anonymous");
    }

    void append() throws Exception {
        if (!ids.isEmpty()) f.jdbc.update("UPDATE handover_case SET status='WITHDRAWN',open_flag=NULL WHERE id=?",
            Ids.bytes(UUID.fromString(ids.getLast())));
        var scope = SubmissionJson.parse(maker.get("/handovers/scopes/" + f.scope + "/context?warehouseId=" + f.warehouse, 200));
        var body = SubmissionJson.object().put("commandVersion", 1).put("commandType", "REQUEST")
            .put("commandId", UUID.randomUUID().toString()).put("warehouseId", f.warehouse.toString())
            .put("scopeId", f.scope.toString()).put("transportRecoveryEpoch", "1").put("reason", "合成交接查询验收");
        body.set("expected", scope.path("expected"));
        var response = SubmissionJson.parse(maker.command(body));
        String id = response.path("businessDecision").path("caseId").asText(); ids.add(id);
        f.jdbc.update("UPDATE handover_case SET requested_at='2026-10-01 01:02:03.123456' WHERE id=?", Ids.bytes(UUID.fromString(id)));
    }

    String path() { return "/handovers/cases?warehouseId=" + f.warehouse + "&orderId=" + f.order; }
    JsonNode page(int size) throws Exception { return SubmissionJson.parse(maker.get(path() + "&pageSize=" + size, 200)); }

    void browserFixture() throws Exception {
        var data = SubmissionJson.object().put("username", f.requester.username()).put("password", "Test-password-123")
            .put("warehouseId", f.warehouse.toString()).put("otherWarehouseId", otherWarehouse.toString())
            .put("orderId", f.order.toString()).put("orderNo", f.jdbc.queryForObject(
                "SELECT order_no FROM outbound_order WHERE id=?", String.class, Ids.bytes(f.order)));
        assertThat(ids).hasSize(21);
        Files.writeString(attempt.resolve("browser-fixture.json"), data.toPrettyString());
    }
}
