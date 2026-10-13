package com.acrylic.outbound.inventory;

import com.acrylic.outbound.exports.ExportModels;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;

/** Real PC commands prepare a bounded synthetic 1001-row source, separate from the export assertions. */
final class ExportNetLargePcFixture {
    final WebApplicationContext context;
    final PublicationDriver api;
    final String order;
    final Path evidence;
    final ExportNetJobConsumer consumer;
    ExportNetLargePcFixture(WebApplicationContext context, Path attempt) throws Exception {
        this.context = context; evidence = attempt.resolve("export-capacity"); Files.createDirectories(evidence);
        var jdbc = context.getBean(JdbcTemplate.class);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
            .addFilters(context.getBean(com.acrylic.outbound.common.RequestIdFilter.class))
            .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        api = new PublicationDriver(mvc, context.getBean(com.fasterxml.jackson.databind.ObjectMapper.class),
            IdentityFixture.create(jdbc, context.getBean(org.springframework.security.crypto.password.PasswordEncoder.class), "WAREHOUSE_ADMIN"));
        String serial = UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT);
        var rows = new ArrayList<byte[]>(); var barcodes = new ArrayList<String>();
        for (int i = 0; i < 1001; i++) { String code = "003" + serial + String.format(Locale.ROOT, "%04d", i);
            barcodes.add(code); rows.add(SkuFixture.row(code, "300.501")); }
        api.publish(api.stage(SkuFixture.concat(rows.toArray(byte[][]::new))));
        var orders = new OrderTestDriver(api); orders.policy("0", false);
        var plan = orders.plan(); plan.putArray("lines").addObject().put("deliveryCode", "003").put("plannedCount", 1001);
        order = orders.create(plan).path("orderId").asText();
        consumer = new ExportNetJobConsumer(context, CorrectionExportHttpClient.login(context,
            new ExportModels.Scope(api.identity().user().id(), api.identity().warehouse(), UUID.fromString(order))), evidence);
        prepare(barcodes);
    }

    private void prepare(List<String> barcodes) throws Exception {
        String writer = UUID.randomUUID().toString(), path = "/orders/" + order + "/pc/";
        var start = SubmissionJson.object().put("expectedVersion", "1").put("writerId", writer)
            .put("policyVersion", "1").put("reason", "Isolated E05 capacity source");
        JsonNode work = CorrectionExportHttpClient.data(consumer.wire.post(path + "start", start, UUID.randomUUID()), 200).path("result");
        for (String barcode : barcodes) {
            var body = entry(writer, work).put("barcode", barcode);
            work = CorrectionExportHttpClient.data(consumer.wire.post(path + "entries", body, UUID.randomUUID()), 200).path("result");
        }
        var submitted = consumer.wire.post(path + "submit", entry(writer, work).put("barcode", ""), UUID.randomUUID());
        CorrectionExportHttpClient.data(submitted, 200); Files.write(evidence.resolve("pc-source-submission.json"), submitted.body());
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode entry(String writer, JsonNode work) {
        return SubmissionJson.object().put("writerId", writer).put("fence", work.path("fence").asText())
            .put("expectedVersion", work.path("version").asText()).putNull("entryId").put("reason", "").put("physicalConfirmed", true);
    }
}
