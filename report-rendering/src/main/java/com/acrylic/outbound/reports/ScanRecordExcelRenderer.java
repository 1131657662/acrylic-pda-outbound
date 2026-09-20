package com.acrylic.outbound.reports;

import java.io.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

/** The six-column out.xlsx layout. No title, header, summary, or extra worksheets. */
public final class ScanRecordExcelRenderer {
    public static final String TEMPLATE = "r08-scan-records-1";
    private static final double[] WIDTHS = {6.625, 22.625, 15.625, 12.625, 9.5, 19.875};

    public static void validate(ReportSnapshot snapshot) {
        SnapshotValidator.validate(snapshot);
        Values.require(snapshot.metadata().basis() == ReportSnapshot.Basis.ORIGINAL_CONFIRMED,
                "扫码记录仅支持原确认版本，不支持更正净结果");
        Values.require(snapshot.orders().size() <= 1, "无表头扫码记录每份只能包含一个订单");
        for (var order : snapshot.orders()) {
            Values.require(order.originalLines().size() <= 1_048_576, "扫码记录超过单表行数上限");
            for (var product : order.products()) Values.required(product.model());
            for (var line : order.originalLines()) {
                Values.require(line.operatorName().codePoints().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN)
                        && !line.operatorName().startsWith("原人员ID:"), "扫码人缺少中文姓名，请完善人员资料后重新申请导出");
                var time = Instant.parse(Values.required(line.receivedAt())).atZone(Values.ZONE).toLocalDateTime();
                Values.require(time.getYear() >= 1900 && time.getYear() <= 9999, "扫码时间超出Excel日期范围");
            }
        }
    }

    public void render(ReportSnapshot snapshot, OutputStream output) throws IOException {
        validate(snapshot);
        try (var book = new SXSSFWorkbook(100)) {
            book.setCompressTempFiles(true);
            templateDefaultStyle(book);
            book.getXSSFWorkbook().getProperties().getCoreProperties().setDescription(ReportText.identity(snapshot));
            book.getXSSFWorkbook().getProperties().getCustomProperties().addProperty("layout", TEMPLATE);
            var sheet = book.createSheet("out");
            sheet.setDefaultRowHeightInPoints(13.5f);
            sheet.setDefaultColumnWidth(9);
            for (int i = 0; i < WIDTHS.length; i++) sheet.setColumnWidth(i, (int) (WIDTHS[i] * 256));
            var style = styles(book);
            if (!snapshot.orders().isEmpty()) write(sheet, snapshot.orders().getFirst(), style);
            book.write(output);
        }
    }

    private void templateDefaultStyle(SXSSFWorkbook book) {
        // Column-width units use the Normal font; match the reference workbook, including WPS.
        var font=book.getFontAt(0);font.setFontName("宋体");font.setFontHeightInPoints((short)11);
        try(var cursor=book.getXSSFWorkbook().getStylesSource().getCTStylesheet().newCursor()) {
            cursor.toEndToken();cursor.beginElement("cellStyles","http://schemas.openxmlformats.org/spreadsheetml/2006/main");
            cursor.insertAttributeWithValue("count","1");
            cursor.beginElement("cellStyle","http://schemas.openxmlformats.org/spreadsheetml/2006/main");
            cursor.insertAttributeWithValue("name","Normal");cursor.insertAttributeWithValue("xfId","0");
            cursor.insertAttributeWithValue("builtinId","0");
        }
    }

    private CellStyle[] styles(SXSSFWorkbook book) {
        var font = book.createFont(); font.setFontName("宋体"); font.setFontHeightInPoints((short) 11);
        var base = book.createCellStyle(); base.setFont(font);
        base.setAlignment(HorizontalAlignment.CENTER); base.setVerticalAlignment(VerticalAlignment.CENTER);
        base.setWrapText(true);
        var barcode = book.createCellStyle(); barcode.cloneStyleFrom(base);
        barcode.setDataFormat(book.createDataFormat().getFormat("@"));
        var time = book.createCellStyle(); time.cloneStyleFrom(base); time.setWrapText(false);
        time.setDataFormat(book.createDataFormat().getFormat("m/d/yy h:mm"));
        return new CellStyle[]{base, barcode, time};
    }

    private void write(Sheet sheet, OrderSnapshot order, CellStyle[] styles) {
        var models = new HashMap<String, String>();
        order.products().forEach(p -> models.put(p.code(), p.model()));
        int index = 0;
        for (var line : order.originalLines()) {
            var row = sheet.createRow(index++);
            for (int i = 0; i < 6; i++) row.createCell(i).setCellStyle(styles[i == 2 ? 1 : i == 5 ? 2 : 0]);
            row.getCell(0).setCellValue(index);
            row.getCell(1).setCellValue(models.get(line.productCode()));
            row.getCell(2).setCellValue(line.barcode());
            var grams = Values.grams(line.weightGrams());
            if (Values.excelExact(grams)) row.getCell(3).setCellValue(new BigDecimal(grams, 3).doubleValue());
            else row.getCell(3).setCellValue(Values.kg(grams));
            row.getCell(4).setCellValue(line.operatorName());
            row.getCell(5).setCellValue(Instant.parse(line.receivedAt()).atZone(Values.ZONE).toLocalDateTime());
            fitHeight(row);
        }
    }

    private void fitHeight(Row row) {
        int lines = 1;
        for (int i = 1; i < 5; i++) {
            var cell = row.getCell(i);
            if (cell.getCellType() != CellType.STRING) continue;
            int count = 0;
            for (String segment : cell.getStringCellValue().split("\n", -1)) {
                int width = segment.codePoints().map(c -> c > 255 ? 2 : 1).sum();
                count += Math.max(1, (int) Math.ceil(width / (WIDTHS[i] - 1)));
            }
            lines = Math.max(lines, count);
        }
        if (lines > 1) row.setHeightInPoints(Math.min(409, lines * 15f));
    }
}
