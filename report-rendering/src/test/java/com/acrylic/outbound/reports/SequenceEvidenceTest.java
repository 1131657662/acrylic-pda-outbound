package com.acrylic.outbound.reports;

import java.io.*;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SequenceEvidenceTest {
    @Test void verifiedHandoverKeepsOrderSequenceWhenDeviceLocalSequencesCollide() throws Exception {
        var o = Samples.order(3); var rows = new ArrayList<>(o.originalLines()); var r = rows.get(2);
        rows.set(2, new ReportLine(r.dispatchId(), r.captureId(), 3, "device-B", 1, r.operatorName(), r.inputSource(),
                r.barcode(), r.dispatchCycle(), r.productCode(), r.weightGrams(), r.rawWeight(), r.receivedAt(), r.savedAt(), r.timeQuality(), r.remark()));
        var handover = new OrderSnapshot(o.id(), o.number(), o.revision(), 1, o.source(), o.originalHeader(), o.effectiveHeader(),
                o.confirmedAt(), new OrderSnapshot.SequenceProof(OrderSnapshot.SequenceKind.VERIFIED_HANDOVER, "TEST-HANDOVER-A-B"),
                o.products(), rows, o.effectiveLines(), List.of(), List.of());
        var output = new ByteArrayOutputStream(); new ExcelRenderer().render(Samples.report(List.of(handover)), output);
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
            var sheet = book.getSheet("原始扫码明细");
            assertEquals("3", sheet.getRow(5).getCell(2).getStringCellValue());
            assertEquals("device-B", sheet.getRow(5).getCell(8).getStringCellValue());
            assertEquals("1", sheet.getRow(5).getCell(9).getStringCellValue());
        }
    }
    @Test void multipleOrdersUseFrozenMemberOrderNotDeviceOrTimestampSort() throws Exception {
        var first = Samples.order(1); var secondBase = Samples.order(2); var secondRow = secondBase.originalLines().getLast();
        var second = new OrderSnapshot("order-2", "TEST-FIRST", 1, 1, secondBase.source(), first.originalHeader(), first.effectiveHeader(),
                first.confirmedAt(), first.sequenceProof(), first.products(), List.of(secondRow),
                List.of(new OrderSnapshot.EffectiveLine(secondRow.dispatchId(), 1, secondRow.weightGrams())), List.of(), List.of());
        var output = new ByteArrayOutputStream(); new ExcelRenderer().render(Samples.report(List.of(second, first)), output);
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
            assertEquals("TEST-FIRST", book.getSheet("原始扫码明细").getRow(3).getCell(0).getStringCellValue());
            assertEquals("TEST-R08-001", book.getSheet("原始扫码明细").getRow(4).getCell(0).getStringCellValue());
        }
    }
}
