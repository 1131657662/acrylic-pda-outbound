package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.correction.CorrectionStore;
import com.acrylic.outbound.exports.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;
import static com.acrylic.outbound.inventory.CorrectionExportHttpClient.data;

/** FORMAL/PC creation uses existing setup fixtures; corrections and files cross real HTTP. */
final class ExportNetSourceChecks {
    private ExportNetSourceChecks() { }
    static void verify(WebApplicationContext context, Path attempt) throws Exception {
        verifySource(context, attempt, false); verifySource(context, attempt, true);
    }
    private static void verifySource(WebApplicationContext context, Path attempt, boolean pda) throws Exception {
        var jdbc = context.getBean(JdbcTemplate.class); var passwords = context.getBean(org.springframework.security.crypto.password.PasswordEncoder.class);
        var actor = IdentityFixture.create(jdbc, passwords, "WAREHOUSE_ADMIN");
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
            .addFilters(context.getBean(com.acrylic.outbound.common.RequestIdFilter.class))
            .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        var api = new PublicationDriver(mvc, context.getBean(com.fasterxml.jackson.databind.ObjectMapper.class), actor);
        String order;
        if (pda) { var formal = new SubmissionFixture(api, jdbc, false, false); formal.submit(); order = formal.order; }
        else { var pc = new PcFixture(api, false, 1, 1); pc.start(); for (int i = 0; i < 3; i++) pc.add(i); pc.submit(""); order = pc.order; }
        var scope = new ExportModels.Scope(actor.user().id(), actor.warehouse(), UUID.fromString(order));
        var consumer = new ExportNetJobConsumer(context, CorrectionExportHttpClient.login(context, scope),
            attempt.resolve(pda ? "e05-formal" : "e05-pc"));
        UUID original = consumer.create("/orders/" + order + "/exports",
            SubmissionJson.object().put("format", "XLSX").put("basis", "ORIGINAL_CONFIRMED"), UUID.randomUUID());
        var originalSnapshot = consumer.snapshot(original);
        assertThat(originalSnapshot.orders().getFirst().source()).isEqualTo(pda ? "FORMAL_PDA" : "FORMAL_PC");
        consumer.download(original, "original.xlsx", "original-v1-");
        headerCorrection(context, attempt, actor, order, consumer.evidence);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_effective_scan WHERE order_id=? AND result_version=2 AND original_scan_ref IS NOT NULL",
            Integer.class, Ids.bytes(scope.order()))).isEqualTo(originalSnapshot.orders().getFirst().originalLines().size());
        if (!pda) assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_result_version WHERE order_id=? AND result_version=2 AND original_submission_id IS NULL",
            Integer.class, Ids.bytes(scope.order()))).isEqualTo(1);
        UUID corrected = consumer.create("/orders/" + order + "/exports",
            SubmissionJson.object().put("format", "XLSX").put("basis", "CORRECTED").put("resultVersion", 2), UUID.randomUUID());
        assertThat(consumer.snapshot(corrected).orders().getFirst().originalLines()).isEqualTo(originalSnapshot.orders().getFirst().originalLines());
        assertThat(consumer.snapshot(corrected).orders().getFirst().effectiveHeader().vehicle()).isEqualTo("E05-source-net");
        consumer.download(corrected, "corrected.xlsx", "corrected-v2-");
        selectionBoundaries(jdbc, scope, consumer); legacyTask(jdbc, original, consumer);
    }
    private static void headerCorrection(WebApplicationContext context, Path attempt, IdentityFixture actor, String order, Path evidence) throws Exception {
        var jdbc = context.getBean(JdbcTemplate.class); var store = context.getBean(CorrectionStore.class);
        var supervisor = IdentityFixture.create(jdbc, context.getBean(org.springframework.security.crypto.password.PasswordEncoder.class), "SUPERVISOR");
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)", Ids.bytes(supervisor.user().id()), Ids.bytes(actor.warehouse()));
        var maker = new SupervisorCorrectionHttp(actor.user().username()); var checker = new SupervisorCorrectionHttp(supervisor.user().username());
        var expected = SubmissionJson.object(); var facts = store.orderFacts(UUID.fromString(order));
        for (String field : List.of("orderId", "currentResultVersion", "currentResultEffectiveCount", "currentResultEffectiveWeightG")) expected.set(field, facts.get(field));
        expected.put("userAuthVersion", "1"); var body = base(actor, "CREATE").put("orderId", order).put("type", "HEADER_CORRECTION");
        body.set("expected", expected); body.putObject("headerPatch").put("vehicle", "E05-source-net");
        byte[] created = maker.command(body, 200); Files.write(evidence.resolve("header-created.json"), created);
        var result = SupervisorCorrectionReceipt.verify(created, attempt.resolve("private/server-public.der"));
        String document = result.at("/businessDecision/documentId").asText(); facts = store.documentFacts(UUID.fromString(document));
        expected = SubmissionJson.object().put("userAuthVersion", "1");
        for (String field : List.of("documentId", "documentSha256", "status", "expectedResultVersion")) expected.set(field, facts.get(field));
        body = base(actor, "APPROVE").put("documentId", document); body.set("expected", expected);
        byte[] approved = checker.command(body, 200); SupervisorCorrectionReceipt.verify(approved, attempt.resolve("private/server-public.der"));
        Files.write(evidence.resolve("header-approved.json"), approved);
    }
    private static void selectionBoundaries(JdbcTemplate jdbc, ExportModels.Scope scope, ExportNetJobConsumer consumer) throws Exception {
        var originalTime = jdbc.queryForObject("SELECT created_at FROM order_result_version WHERE order_id=? AND result_version=1", Timestamp.class, Ids.bytes(scope.order()));
        var correctedTime = jdbc.queryForObject("SELECT created_at FROM order_result_version WHERE order_id=? AND result_version=2", Timestamp.class, Ids.bytes(scope.order()));
        try {
            jdbc.update("UPDATE order_result_version SET created_at=? WHERE order_id=? AND result_version=2", Timestamp.from(Instant.now().plusSeconds(60)), Ids.bytes(scope.order()));
            assertThat(ExportNetHttpChecks.summary(consumer).at("/orders/0/resultVersion").asLong()).isEqualTo(1);
        } finally { jdbc.update("UPDATE order_result_version SET created_at=? WHERE order_id=? AND result_version=2", correctedTime, Ids.bytes(scope.order())); }
        assertThat(ExportNetHttpChecks.summary(consumer).at("/orders/0/resultVersion").asLong()).isEqualTo(2);
        LocalDate day = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1);
        try {
            jdbc.update("UPDATE order_result_version SET created_at=? WHERE order_id=? AND result_version=1",
                Timestamp.from(day.atTime(23, 59, 59).atZone(ZoneId.of("Asia/Shanghai")).toInstant()), Ids.bytes(scope.order()));
            assertThat(ExportNetHttpChecks.summary(consumer).path("orderCount").asInt()).isZero();
            var previous = data(consumer.wire.get("/exports/summary?fromDate=" + day + "&throughDate=" + day), 200);
            assertThat(previous.at("/orders/0/resultVersion").asLong()).isEqualTo(2);
            assertThat(previous.at("/orders/0/originalDay").asText()).isEqualTo(day.toString());
        } finally { jdbc.update("UPDATE order_result_version SET created_at=? WHERE order_id=? AND result_version=1", originalTime, Ids.bytes(scope.order())); }
        UUID net = consumer.create("/exports", ExportNetHttpChecks.rangeCommand("XLSX", "CURRENT_EFFECTIVE"), UUID.randomUUID());
        consumer.download(net, "latest-net.xlsx", "latest-net-");
    }
    private static void legacyTask(JdbcTemplate jdbc, UUID original, ExportNetJobConsumer consumer) throws Exception {
        jdbc.update("UPDATE background_job SET payload=JSON_REMOVE(payload,'$.members','$.resultVersion','$.range','$.orderCount','$.retryOf') WHERE id=?", Ids.bytes(original));
        assertThat(data(consumer.wire.get("/exports/" + original), 200).path("resultVersion").asLong()).isEqualTo(1);
        UUID retry = consumer.create("/exports/" + original + "/retry", SubmissionJson.object(), UUID.randomUUID());
        assertThat(consumer.payload(retry).snapshotHash()).isEqualTo(consumer.payload(original).snapshotHash());
        assertThat(consumer.payload(retry).basis()).isEqualTo("ORIGINAL_CONFIRMED");
        consumer.download(retry, "legacy-regenerated.xlsx", "original-v1-");
    }
    private static ObjectNode base(IdentityFixture actor, String kind) {
        return SubmissionJson.object().put("commandVersion", 1).put("commandType", kind).put("commandId", UUID.randomUUID().toString())
            .put("warehouseId", actor.warehouse().toString()).put("transportRecoveryEpoch", "1").put("reason", "isolated E05 source consumer");
    }
}
