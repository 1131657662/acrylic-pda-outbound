package com.acrylic.outbound.reports;

import java.io.*;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class ExcelRendererTest {
    private XSSFWorkbook render(ReportSnapshot s, int rows) throws IOException {
        var bytes = new ByteArrayOutputStream(); new ExcelRenderer(rows).render(s, bytes);
        return new XSSFWorkbook(new ByteArrayInputStream(bytes.toByteArray()));
    }
    @Test void preservesMixedProductSequenceAcrossSheetSplits() throws Exception {
        try (var book = render(Samples.report(List.of(Samples.order(5))), 2)) {
            var first = book.getSheet("原始扫码明细"); var second = book.getSheet("原始扫码明细_2");
            assertEquals("003", first.getRow(3).getCell(4).getStringCellValue());
            assertEquals("007", first.getRow(4).getCell(4).getStringCellValue());
            assertEquals("3", second.getRow(3).getCell(2).getStringCellValue());
            assertEquals("003", second.getRow(3).getCell(4).getStringCellValue());
            assertEquals("5", book.getSheet("原始扫码明细_3").getRow(3).getCell(2).getStringCellValue());
        }
    }
    @Test void retainsLeadingZerosLongBarcodesAndRawWeight() throws Exception {
        var o = Samples.order(1); var barcode = "0".repeat(180) + "123456789";
        o = Samples.withLines(o, List.of(Samples.changed(o.originalLines().getFirst(), "barcode", barcode)));
        try (var book = render(Samples.report(List.of(o)), 100)) {
            var row = book.getSheet("原始扫码明细").getRow(3);
            assertEquals(CellType.STRING, row.getCell(3).getCellType()); assertEquals(barcode, row.getCell(3).getStringCellValue());
            assertEquals("300.501", row.getCell(6).getStringCellValue()); assertEquals("300.501", row.getCell(7).getStringCellValue());
            assertEquals("003", book.getSheet("订单汇总").getRow(3).getCell(7).getStringCellValue());
        }
    }
    @ParameterizedTest @ValueSource(strings = {"=1+1", "+cmd|' /C calc'!A0", "-1+2", "@SUM(A1)", "\t=HYPERLINK(\"https://evil\")", "<img src='https://evil'>"})
    void maliciousTextIsLiteralWithNoFormulaOrLink(String malicious) throws Exception {
        var o = Samples.order(1);
        o = Samples.withLines(o, List.of(Samples.changed(o.originalLines().getFirst(), "remark", malicious)));
        try (var book = render(Samples.report(List.of(o)), 100)) {
            var cell = book.getSheet("原始扫码明细").getRow(3).getCell(18);
            assertEquals(CellType.STRING, cell.getCellType()); assertEquals(malicious, cell.getStringCellValue()); assertNull(cell.getHyperlink());
            for (var sheet : book) for (var row : sheet) for (var c : row) assertNotEquals(CellType.FORMULA, c.getCellType());
        }
    }
    @Test void preservesUnsafeNumericWeightAsExactTextWithBlankNumericCell() throws Exception {
        var o = Samples.order(1);
        o = Samples.withLines(o, List.of(Samples.changed(o.originalLines().getFirst(), "weight", "900719925474099312345")));
        try (var book = render(Samples.report(List.of(o)), 100)) {
            var row = book.getSheet("原始扫码明细").getRow(3);
            assertEquals(CellType.BLANK, row.getCell(5).getCellType());
            assertEquals("900719925474099312.345", row.getCell(6).getStringCellValue());
        }
    }
    @Test void originalAndEffectiveDetailKeepTheirOwnRows() throws Exception {
        try (var book = render(Samples.report(List.of(Samples.corrected())), 100)) {
            assertEquals(5, book.getSheet("原始扫码明细").getLastRowNum());
            assertEquals(4, book.getSheet("版本有效明细").getLastRowNum());
            assertEquals("2", book.getSheet("版本有效明细").getRow(3).getCell(3).getStringCellValue());
            assertEquals("2026-09-18", book.getSheet("调整凭据").getRow(3).getCell(1).getLocalDateTimeCellValue().toLocalDate().toString());
            assertEquals("601.002", book.getSheet("订单汇总").getRow(3).getCell(17).getStringCellValue());
        }
    }
    @Test void emptyWorkbookIncludesZeroSummaryAndSampleNotice() throws Exception {
        try (var book = render(Samples.report(List.of()), 100)) {
            assertEquals(0, book.getSheet("订单汇总").getRow(3).getCell(9).getNumericCellValue());
            for (var sheet : book) assertTrue(sheet.getRow(1).getCell(0).getStringCellValue().contains("测试样例"));
        }
    }
}
