package com.acrylic.outbound.inventory;

import com.acrylic.outbound.exports.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;
import static com.acrylic.outbound.inventory.CorrectionExportHttpClient.data;

/** Synthetic signed source setup, followed by actual authenticated HTTP export/correction consumers. */
public final class ExportNetHttpChecks {
    private ExportNetHttpChecks() { }
    public static void verify(WebApplicationContext context, Path attempt) throws Exception {
        var f = new CorrectionAccountingHttpFixture(context, attempt, false, "e05-net");
        var wire = CorrectionExportHttpClient.login(context, new ExportModels.Scope(f.makerIdentity.user().id(),
            UUID.fromString(f.warehouse), UUID.fromString(f.order)));
        var consumer = new ExportNetJobConsumer(context, wire, f.evidence);
        fixedAcrossChanges(f, consumer);
        new ExportNetFailureChecks(f, consumer).verify();
        ExportNetSourceChecks.verify(context, attempt);
    }
    private static void fixedAcrossChanges(CorrectionAccountingHttpFixture f, ExportNetJobConsumer consumer) throws Exception {
        var original = f.original();
        assertThat(summary(consumer).path("actualCount").asLong()).isEqualTo(2);
        f.approve(1, f.originalDispatchWeight + 17);
        UUID key = UUID.randomUUID(); var command = rangeCommand("XLSX", "CURRENT_EFFECTIVE");
        UUID fixed = consumer.create("/exports", command, key); var frozen = consumer.snapshot(fixed);
        assertThat(frozen.orders().getFirst().resultVersion()).isEqualTo(2);
        assertThat(consumer.payload(fixed).members()).containsExactly(new ExportModels.Member(UUID.fromString(f.order), 2));
        consumer.download(fixed, "fixed-v2.xlsx", "latest-net-");
        zeroAndRestoreHeader(f);
        var latest = summary(consumer); Files.writeString(f.evidence.resolve("summary-net.json"), latest.toPrettyString());
        assertThat(latest.path("basis").asText()).isEqualTo("CURRENT_EFFECTIVE");
        assertThat(latest.path("actualCount").asLong()).isZero();
        assertThat(latest.path("weightGrams").asText()).isEqualTo("0");
        assertThat(latest.path("originalCount").asLong()).isEqualTo(2);
        assertThat(latest.path("originalWeightGrams").asText()).isEqualTo(Long.toString(f.originalWeight));
        assertThat(latest.path("adjustedCount").asLong()).isEqualTo(-2);
        assertThat(latest.path("adjustedWeightGrams").asText()).isEqualTo(Long.toString(-f.originalWeight));
        assertThat(consumer.create("/exports", command, key)).isEqualTo(fixed);
        assertThat(consumer.snapshot(fixed)).isEqualTo(frozen);
        UUID retry = consumer.create("/exports/" + fixed + "/retry", SubmissionJson.object(), UUID.randomUUID());
        assertThat(consumer.payload(retry).snapshotHash()).isEqualTo(consumer.payload(fixed).snapshotHash());
        assertThat(consumer.payload(retry).members()).isEqualTo(consumer.payload(fixed).members());
        consumer.download(retry, "regenerated-v2.xlsx", "latest-net-");
        zeroDownloads(f, consumer); assertThat(f.original()).isEqualTo(original);
        saveBrowserAndRestart(f, fixed, retry, consumer);
    }
    private static void zeroAndRestoreHeader(CorrectionAccountingHttpFixture f) throws Exception {
        var before = HeaderCompensationHttpChecks.header(f);
        String header = HeaderCompensationHttpChecks.approve(f, SubmissionJson.object().put("vehicle", "E05-changed"));
        f.withdraw(header); assertThat(HeaderCompensationHttpChecks.header(f)).isEqualTo(before);
        for (String dispatch : f.dispatches()) {
            var body = f.cancellation(0); ((ObjectNode) body.path("lines").get(0)).put("dispatchId", dispatch);
            String document = f.signed(f.maker, body).path("documentId").asText();
            f.signed(f.checker, f.command(document, "APPROVE"));
        }
    }
    private static void zeroDownloads(CorrectionAccountingHttpFixture f, ExportNetJobConsumer consumer) throws Exception {
        for (String format : List.of("XLSX", "PDF")) {
            UUID id = consumer.create("/exports", rangeCommand(format, "CURRENT_EFFECTIVE"), UUID.randomUUID());
            var order = consumer.snapshot(id).orders().getFirst();
            assertThat(order.resultVersion()).isEqualTo(f.result().path("version").asLong());
            assertThat(order.originalLines()).hasSize(2);
            assertThat(order.effectiveLines()).allSatisfy(line -> {
                assertThat(line.count()).isZero(); assertThat(line.weightGrams()).isEqualTo("0");
            });
            assertThat(order.originalLines()).extracting(line -> line.dispatchId()).containsExactlyElementsOf(f.dispatches());
            consumer.download(id, "zero-net." + format.toLowerCase(Locale.ROOT), "latest-net-");
        }
        UUID original = consumer.create("/orders/" + f.order + "/exports",
            SubmissionJson.object().put("format", "SCAN_XLSX").put("basis", "ORIGINAL_CONFIRMED"), UUID.randomUUID());
        consumer.download(original, "original-scans.xlsx", "scan-records-v1-");
        var old = data(consumer.wire.get(summaryPath() + "&basis=ORIGINAL_CONFIRMED"), 200);
        assertThat(old.path("actualCount").asLong()).isEqualTo(2);
        assertThat(old.at("/orders/0/resultVersion").asLong()).isEqualTo(1);
    }
    private static void saveBrowserAndRestart(CorrectionAccountingHttpFixture f, UUID fixed, UUID retry,
        ExportNetJobConsumer consumer) throws Exception {
        var record = SubmissionJson.object().put("username", f.makerIdentity.user().username()).put("warehouse", f.warehouse)
            .put("order", f.order).put("fixedJob", fixed.toString()).put("retryJob", retry.toString())
            .put("snapshotHash", consumer.payload(fixed).snapshotHash()).put("latestVersion", f.result().path("version").asLong());
        Files.writeString(f.evidence.resolve("restart-input.json"), record.toPrettyString());
    }
    static JsonNode summary(ExportNetJobConsumer consumer) throws Exception { return data(consumer.wire.get(summaryPath()), 200); }
    static String summaryPath() {
        String day = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        return "/exports/summary?fromDate=" + day + "&throughDate=" + day;
    }
    static ObjectNode rangeCommand(String format, String basis) {
        String day = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        var command = SubmissionJson.object().put("format", format).put("basis", basis);
        command.putObject("range").put("fromDate", day).put("throughDate", day); return command;
    }
}
