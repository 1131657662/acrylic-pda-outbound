package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.correction.CorrectionStore;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;
import static com.acrylic.outbound.inventory.CorrectionExportHttpClient.data;

final class ExportNetCapacityChecks {
    private ExportNetCapacityChecks() { }
    static void verify(WebApplicationContext context, Path attempt) throws Exception {
        var f = new ExportNetLargePcFixture(context, attempt); reduceNet(f, attempt);
        var summary = ExportNetHttpChecks.summary(f.consumer);
        assertThat(summary.path("originalCount").asInt()).isEqualTo(1001);
        assertThat(summary.path("actualCount").asInt()).isEqualTo(801);
        var jdbc = context.getBean(JdbcTemplate.class);
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM background_job", Integer.class);
        for (String path : List.of("/exports", "/orders/" + f.order + "/exports")) {
            var body = path.equals("/exports") ? ExportNetHttpChecks.rangeCommand("PDF", "CURRENT_EFFECTIVE")
                : SubmissionJson.object().put("format", "PDF").put("basis", "CURRENT_EFFECTIVE");
            var response = f.consumer.wire.post(path, body, UUID.randomUUID()); data(response, 409);
            assertThat(SubmissionJson.parse(response.body()).path("code").asText()).isEqualTo("EXPORT_LIMIT_EXCEEDED");
            Files.write(f.evidence.resolve(path.equals("/exports") ? "range-capacity.json" : "single-capacity.json"), response.body());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM background_job", Integer.class)).isEqualTo(before);
        UUID excel = f.consumer.create("/exports", ExportNetHttpChecks.rangeCommand("XLSX", "CURRENT_EFFECTIVE"), UUID.randomUUID());
        assertThat(f.consumer.snapshot(excel).orders().getFirst().originalLines()).hasSize(1001);
        f.consumer.download(excel, "retained1001-net801.xlsx", "latest-net-");
        Files.writeString(f.evidence.resolve("result.json"), summary.toPrettyString());
    }

    private static void reduceNet(ExportNetLargePcFixture f, Path attempt) throws Exception {
        var jdbc = f.context.getBean(JdbcTemplate.class); var store = f.context.getBean(CorrectionStore.class);
        var supervisor = IdentityFixture.create(jdbc, f.context.getBean(org.springframework.security.crypto.password.PasswordEncoder.class), "SUPERVISOR");
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)", Ids.bytes(supervisor.user().id()), Ids.bytes(f.api.identity().warehouse()));
        var maker = new SupervisorCorrectionHttp(f.api.identity().user().username()); var checker = new SupervisorCorrectionHttp(supervisor.user().username());
        var create = base(f, "CREATE").put("orderId", f.order).put("type", "DISPATCH_CANCELLATION");
        var facts = store.orderFacts(UUID.fromString(f.order)); var expected = SubmissionJson.object().put("userAuthVersion", "1");
        for (String name : List.of("orderId", "currentResultVersion", "currentResultEffectiveCount", "currentResultEffectiveWeightG")) expected.set(name, facts.get(name));
        create.set("expected", expected); var lines = create.putArray("lines");
        for (String id : jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM dispatch_record WHERE order_id=UUID_TO_BIN(?) ORDER BY order_scan_seq LIMIT 200", String.class, f.order))
            lines.addObject().put("dispatchId", id).put("targetCount", "0").put("targetWeightG", "0")
                .put("evidenceId", UUID.randomUUID().toString()).put("evidenceSha256", "c".repeat(64));
        byte[] original = maker.command(create, 200); Files.write(f.evidence.resolve("capacity-correction-create.json"), original);
        String document = SupervisorCorrectionReceipt.verify(original, attempt.resolve("private/server-public.der")).at("/businessDecision/documentId").asText();
        facts = store.documentFacts(UUID.fromString(document)); expected = SubmissionJson.object().put("userAuthVersion", "1");
        for (String name : List.of("documentId", "documentSha256", "status", "expectedResultVersion")) expected.set(name, facts.get(name));
        var approve = base(f, "APPROVE").put("documentId", document); approve.set("expected", expected);
        byte[] result = checker.command(approve, 200); SupervisorCorrectionReceipt.verify(result, attempt.resolve("private/server-public.der"));
        Files.write(f.evidence.resolve("capacity-correction-approved.json"), result);
    }

    private static ObjectNode base(ExportNetLargePcFixture f, String kind) {
        return SubmissionJson.object().put("commandVersion", 1).put("commandType", kind).put("commandId", UUID.randomUUID().toString())
            .put("warehouseId", f.api.identity().warehouse().toString()).put("transportRecoveryEpoch", "1").put("reason", "Isolated retained-row capacity fixture");
    }
}
