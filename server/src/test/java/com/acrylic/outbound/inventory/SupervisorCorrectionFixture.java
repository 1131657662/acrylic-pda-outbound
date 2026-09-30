package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Fresh successful source and distinct actors using the migrated role memberships. */
final class SupervisorCorrectionFixture {
    final TemporaryConfirmationFixture source;
    final JdbcTemplate jdbc;
    final IdentityFixture checker;
    final String order, warehouse;
    final long originalCount, originalWeight;

    SupervisorCorrectionFixture(WebApplicationContext context) throws Exception {
        source = new TemporaryConfirmationFixture(context, false); jdbc = source.jdbc;
        source.execute(source.command("NORMAL_CONFIRM", source.operator()), source.operator());
        warehouse = source.source.f.web.identity().warehouse().toString();
        order = jdbc.queryForObject("SELECT BIN_TO_UUID(order_id) FROM order_result_version WHERE submission_id=UUID_TO_BIN(?)",
            String.class, source.source.id());
        var original = result(); originalCount = Long.parseLong(original.path("actualCount").asText());
        originalWeight = Long.parseLong(original.path("weightG").asText());
        checker = IdentityFixture.create(jdbc, context.getBean(PasswordEncoder.class), "SUPERVISOR");
        jdbc.update("INSERT INTO user_warehouse VALUES(?,UUID_TO_BIN(?))", Ids.bytes(checker.user().id()), warehouse);
        assertThat(permissions(source.operator().id())).contains("outbound:correct").doesNotContain("correction:approve");
        assertThat(permissions(checker.user().id())).contains("correction:approve").doesNotContain("outbound:correct");
        assertThat(originalCount).isPositive();
    }

    ObjectNode create() {
        var current = result();
        var expected = SubmissionJson.object().put("orderId", order).put("currentResultVersion", current.path("version").asText())
            .put("currentResultEffectiveCount", current.path("actualCount").asText())
            .put("currentResultEffectiveWeightG", current.path("weightG").asText()).put("userAuthVersion", "1");
        var body = base("CREATE").put("orderId", order).put("type", "WEIGHT_CORRECTION");
        body.putArray("lines").add(SubmissionJson.parse(jdbc.queryForObject(
            "SELECT JSON_OBJECT('dispatchId',BIN_TO_UUID(id),'targetCount','1','targetWeightG',CAST(weight_g+1 AS CHAR))"
                + " FROM dispatch_record WHERE order_id=UUID_TO_BIN(?) ORDER BY id LIMIT 1", String.class, order)));
        body.set("expected", expected); return body;
    }

    ObjectNode approve(String document) {
        var expected = SubmissionJson.parse(jdbc.queryForObject(
            "SELECT JSON_OBJECT('documentId',BIN_TO_UUID(id),'documentSha256',document_sha256,'status',status,"
                + "'expectedResultVersion',CAST(expected_result_version AS CHAR),'userAuthVersion','1')"
                + " FROM correction_document WHERE id=UUID_TO_BIN(?)", String.class, document));
        var body = base("APPROVE").put("documentId", document); body.set("expected", expected); return body;
    }

    ObjectNode result() {
        return (ObjectNode) SubmissionJson.parse(jdbc.queryForObject(
            "SELECT JSON_OBJECT('version',CAST(v.result_version AS CHAR),'actualCount',CAST(v.actual_count AS CHAR),"
                + "'weightG',CAST(v.weight_g AS CHAR)) FROM order_result_version v JOIN outbound_order o ON o.id=v.order_id"
                + " AND o.current_result_version=v.result_version WHERE o.id=UUID_TO_BIN(?)", String.class, order));
    }

    ObjectNode immutableSource() {
        var facts = new ReverseCandidateFacts(jdbc).businessState(); var result = SubmissionJson.object();
        for (String key : List.of("submissions", "scopes", "payloads", "mappings", "commitments", "dispatches", "ledger"))
            result.set(key, facts.get(key));
        result.put("originalResult", jdbc.queryForObject("SELECT JSON_OBJECT('submission',BIN_TO_UUID(submission_id),"
            + "'count',actual_count,'weight',weight_g,'header',header_snapshot) FROM order_result_version"
            + " WHERE order_id=UUID_TO_BIN(?) AND result_version=1", String.class, order));
        return result;
    }

    List<String> permissions(UUID actor) {
        return jdbc.queryForList("SELECT DISTINCT p.code FROM permission p JOIN role_permission rp ON rp.permission_id=p.id"
            + " JOIN user_role ur ON ur.role_id=rp.role_id WHERE ur.user_id=? ORDER BY p.code", String.class, Ids.bytes(actor));
    }

    void makerApprovalRole(boolean enabled) {
        String sql = enabled ? "INSERT INTO user_role SELECT ?,id FROM role WHERE code='SUPERVISOR'"
            : "DELETE ur FROM user_role ur JOIN role r ON r.id=ur.role_id WHERE ur.user_id=? AND r.code='SUPERVISOR'";
        assertThat(jdbc.update(sql, Ids.bytes(source.operator().id()))).isEqualTo(1);
    }

    private ObjectNode base(String type) {
        return SubmissionJson.object().put("commandVersion", 1).put("commandType", type).put("commandId", UUID.randomUUID().toString())
            .put("transportRecoveryEpoch", "1").put("warehouseId", warehouse).put("reason", "isolated supervisor signature check");
    }
}
