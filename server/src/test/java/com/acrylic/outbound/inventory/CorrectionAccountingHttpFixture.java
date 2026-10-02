package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.correction.CorrectionStore;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.command.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Test-only full TEMP confirmation; correction commands use real separated Web sessions. */
final class CorrectionAccountingHttpFixture {
    final WebApplicationContext context;
    final JdbcTemplate jdbc;
    final String order, warehouse, dispatch;
    final long originalWeight, originalDispatchWeight;
    final IdentityFixture makerIdentity;
    final SupervisorCorrectionHttp maker, checker;
    final Path evidence, publicKey;
    private final CorrectionStore store;

    CorrectionAccountingHttpFixture(WebApplicationContext context, Path attempt, boolean deduct, String name) throws Exception {
        this.context = context; jdbc = context.getBean(JdbcTemplate.class); store = context.getBean(CorrectionStore.class);
        var confirmation = new TemporaryConfirmationFixture(context, new TemporaryConfirmationFixture.Setup(false, deduct, source -> {}));
        var source = confirmation.source;
        makerIdentity = source.f.web.identity(); warehouse = makerIdentity.warehouse().toString();
        publicKey = attempt.resolve("private/server-public.der"); evidence = attempt.resolve("accounting/" + name);
        Files.createDirectories(evidence); confirm(confirmation, deduct);
        order = jdbc.queryForObject("SELECT BIN_TO_UUID(order_id) FROM order_result_version WHERE submission_id=UUID_TO_BIN(?)",
            String.class, source.id());
        dispatch = dispatches().getFirst();
        originalWeight = result().path("weightG").asLong(); originalDispatchWeight = facts(dispatch).path("weightG").asLong();
        var supervisor = IdentityFixture.create(jdbc, context.getBean(PasswordEncoder.class), "SUPERVISOR");
        jdbc.update("INSERT INTO user_warehouse VALUES(?,UUID_TO_BIN(?))", Ids.bytes(supervisor.user().id()), warehouse);
        maker = new SupervisorCorrectionHttp(makerIdentity.user().username()); checker = new SupervisorCorrectionHttp(supervisor.user().username());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_ledger WHERE dispatch_id=UUID_TO_BIN(?)",
            Integer.class, dispatch)).isEqualTo(deduct ? 1 : 0);
    }

    private void confirm(TemporaryConfirmationFixture f, boolean deduct) throws Exception {
        assertThat(context.getBeansOfType(CommandOperation.class)).isEmpty();
        var command = f.command("NORMAL_CONFIRM", f.operator()); byte[] bytes = f.execute(command, f.operator());
        var receipt = (ObjectNode) SubmissionJson.parse(bytes);
        var record = context.getBean(CommandStore.class).byReceipt(UUID.fromString(receipt.path("receiptId").asText()));
        var unsigned = receipt.deepCopy(); unsigned.remove("signedReceipt");
        assertThat(context.getBean(CommandReceiptIntegrity.class).verify(record)).isEqualTo(unsigned);
        assertThat(receipt.path("status").asText()).isEqualTo("RECORDED");
        assertThat(receipt.at("/businessDecision/submissionReceipt/status").asText()).isEqualTo("SUCCEEDED");
        assertThat(receipt.at("/businessDecision/submissionReceipt/inventoryDeducted").asBoolean()).isEqualTo(deduct);
        assertThat(f.execute(command, f.operator())).containsExactly(bytes);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM temporary_decision_event e JOIN temporary_command_receipt r"
            + " ON r.id=e.receipt_id WHERE r.submission_id=UUID_TO_BIN(?) AND e.decision_kind='NORMAL_CONFIRM'",
            Integer.class, f.source.id())).isEqualTo(1);
        Files.write(evidence.resolve("temporary-confirmation.json"), bytes);
    }

    List<String> dispatches() {
        return jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM dispatch_record WHERE order_id=UUID_TO_BIN(?) ORDER BY order_scan_seq",
            String.class, order);
    }

    ObjectNode create(String selected, long count, long weight) {
        var current = store.orderFacts(UUID.fromString(order)); var expected = SubmissionJson.object();
        for (String key : List.of("orderId", "currentResultVersion", "currentResultEffectiveCount", "currentResultEffectiveWeightG"))
            expected.set(key, current.get(key));
        expected.put("userAuthVersion", "1");
        var body = base("CREATE").put("orderId", order).put("type", "WEIGHT_CORRECTION");
        body.set("expected", expected);
        body.putArray("lines").add(SubmissionJson.object().put("dispatchId", selected).put("targetCount", Long.toString(count))
            .put("targetWeightG", Long.toString(weight)));
        return body;
    }

    ObjectNode cancellation(long targetWeight) {
        var body = create(dispatch, 0, targetWeight).put("type", "DISPATCH_CANCELLATION");
        var line = (ObjectNode) body.path("lines").get(0);
        line.put("evidenceId", UUID.randomUUID().toString()).put("evidenceSha256", "c".repeat(64));
        return body;
    }

    ObjectNode headerCreate() {
        var current = store.orderFacts(UUID.fromString(order)); var expected = SubmissionJson.object();
        for (String key : List.of("orderId", "currentResultVersion", "currentResultEffectiveCount", "currentResultEffectiveWeightG"))
            expected.set(key, current.get(key));
        expected.put("userAuthVersion", "1");
        String snapshot = jdbc.queryForObject("SELECT CAST(header_snapshot AS CHAR) FROM order_result_version"
            + " WHERE order_id=UUID_TO_BIN(?) ORDER BY result_version DESC LIMIT 1", String.class, order);
        String orderNo = SubmissionJson.parse(snapshot).path("orderNo").asText();
        var body = base("CREATE").put("orderId", order).put("type", "HEADER_CORRECTION"); body.set("expected", expected);
        body.set("headerPatch", SubmissionJson.object().put("orderNo", orderNo + "-withdraw-guard"));
        return body;
    }

    ObjectNode command(String document, String kind) {
        var current = store.documentFacts(UUID.fromString(document)); var expected = SubmissionJson.object();
        for (String key : List.of("documentId", "documentSha256", "status", "expectedResultVersion")) expected.set(key, current.get(key));
        expected.put("userAuthVersion", "1");
        var body = base(kind).put("documentId", document); body.set("expected", expected); return body;
    }

    String approve(long count, long weight) throws Exception { return approve(dispatch, count, weight); }
    String approve(String selected, long count, long weight) throws Exception {
        var created = signed(maker, create(selected, count, weight)); String document = created.path("documentId").asText();
        var body = command(document, "APPROVE"); byte[] approved = checker.command(body, 200);
        SupervisorCorrectionReceipt.verify(approved, publicKey); Files.write(evidence.resolve(document + "-approve.json"), approved);
        assertThat(checker.command(body, 200)).containsExactly(approved);
        return document;
    }

    void withdraw(String document) throws Exception {
        signed(checker, command(document, "WITHDRAW")); var body = command(document, "WITHDRAW_CONFIRM");
        byte[] receipt = maker.command(body, 200); SupervisorCorrectionReceipt.verify(receipt, publicKey);
        Files.write(evidence.resolve(document + "-withdraw.json"), receipt);
        assertThat(maker.command(body, 200)).containsExactly(receipt);
    }

    JsonNode signed(SupervisorCorrectionHttp actor, ObjectNode body) throws Exception {
        byte[] bytes = actor.command(body, 200); var result = SupervisorCorrectionReceipt.verify(bytes, publicKey);
        Files.write(evidence.resolve(body.path("commandId").asText() + ".json"), bytes); return result.path("businessDecision");
    }

    JsonNode facts(String selected) { return store.dispatchFacts(UUID.fromString(order), UUID.fromString(selected)); }
    JsonNode result() {
        return SubmissionJson.parse(jdbc.queryForObject("SELECT JSON_OBJECT('version',CAST(v.result_version AS CHAR),"
            + "'count',CAST(v.actual_count AS CHAR),'weightG',CAST(v.weight_g AS CHAR)) FROM order_result_version v"
            + " JOIN outbound_order o ON o.id=v.order_id AND o.current_result_version=v.result_version WHERE o.id=UUID_TO_BIN(?)",
            String.class, order));
    }

    JsonNode original() {
        return SubmissionJson.parse(jdbc.queryForObject("SELECT JSON_OBJECT('count',actual_count,'weight',weight_g,'header',header_snapshot,"
            + "'dispatches',(SELECT JSON_ARRAYAGG(JSON_OBJECT('id',BIN_TO_UUID(id),'weight',weight_g)) FROM dispatch_record WHERE order_id=r.order_id),"
            + "'ledger',(SELECT JSON_ARRAYAGG(JSON_OBJECT('id',BIN_TO_UUID(l.id),'q',l.quantity_delta,'w',l.weight_delta_g))"
            + " FROM inventory_ledger l JOIN dispatch_record d ON d.id=l.dispatch_id WHERE d.order_id=r.order_id))"
            + " FROM order_result_version r WHERE order_id=UUID_TO_BIN(?) AND result_version=1", String.class, order));
    }

    long correctionWeight() {
        return jdbc.queryForObject("SELECT IFNULL(SUM(weight_delta_g),0) FROM correction_ledger WHERE dispatch_id=UUID_TO_BIN(?)",
            Long.class, dispatch);
    }
    int postings() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM correction_ledger WHERE dispatch_id=UUID_TO_BIN(?)", Integer.class, dispatch);
    }

    private ObjectNode base(String kind) {
        return SubmissionJson.object().put("commandVersion", 1).put("commandType", kind).put("commandId", UUID.randomUUID().toString())
            .put("warehouseId", warehouse).put("transportRecoveryEpoch", "1").put("reason", "isolated accounting regression");
    }
}
