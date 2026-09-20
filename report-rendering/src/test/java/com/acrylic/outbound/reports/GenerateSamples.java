package com.acrylic.outbound.reports;

import java.nio.file.*;
import java.util.*;

public final class GenerateSamples {
    public static void main(String[] args) throws Exception {
        Files.createDirectories(Path.of("build/samples"));
        var corrected = Samples.report(List.of(Samples.corrected()));
        SnapshotJson.write(corrected, Path.of("examples/corrected-test-snapshot.json"));
        render("corrected", corrected);
        render("empty", Samples.report(List.of()));
        var confirmed = ConfirmedV1Fixture.input(ConfirmedV1Fixture.order(ConfirmedV1Input.Channel.PDA),
                ConfirmedV1Fixture.order(ConfirmedV1Input.Channel.PC));
        SnapshotJson.writeConfirmedV1(confirmed, Path.of("examples/confirmed-v1-test-input.json"));
        render("confirmed-v1", ConfirmedV1Adapter.adapt(confirmed));
        var scanRecords=ConfirmedV1Adapter.adapt(ConfirmedV1Fixture.input(ConfirmedV1Fixture.order(ConfirmedV1Input.Channel.PDA)));
        SnapshotJson.write(scanRecords,Path.of("examples/scan-records-test-snapshot.json"));
        try(var out=Files.newOutputStream(Path.of("build/samples/scan-records-TEST-SAMPLE.xlsx"))) {
            new ScanRecordExcelRenderer().render(scanRecords,out);
        }
        var o = Samples.order(60); var rows = new ArrayList<>(o.originalLines());
        rows.set(0, Samples.changed(rows.get(0), "barcode", "0".repeat(150) + "1234567890"));
        rows.set(1, Samples.changed(rows.get(1), "remark", "长备注与恶意文本 <script>fetch('https://invalid.example')</script> & =1+1。".repeat(7)));
        var products = List.of(new OrderSnapshot.Product("003", "透明亚克力长名称".repeat(12), "超长型号ABC-1234-".repeat(12), 40), o.products().get(1));
        o = Samples.withLines(o, rows);
        var longOrder = new OrderSnapshot(o.id(), o.number(), o.revision(), o.resultVersion(), o.source(), o.originalHeader(),
                o.effectiveHeader(), o.confirmedAt(), o.sequenceProof(), products, o.originalLines(), o.effectiveLines(), o.adjustments(), o.exceptions());
        render("multipage", Samples.report(List.of(longOrder)));
        try (var out = Files.newOutputStream(Path.of("build/samples/large.xlsx"))) {
            new ExcelRenderer(10_000).render(Samples.report(List.of(Samples.order(50_000))), out);
        }
    }
    private static void render(String name, ReportSnapshot s) throws Exception {
        try (var out = Files.newOutputStream(Path.of("build/samples/" + name + ".xlsx"))) { new ExcelRenderer().render(s, out); }
        try (var out = Files.newOutputStream(Path.of("build/samples/" + name + ".pdf"))) { new PdfRenderer().render(s, out); }
    }
}
