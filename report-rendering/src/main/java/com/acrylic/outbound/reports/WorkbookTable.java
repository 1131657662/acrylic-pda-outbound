package com.acrylic.outbound.reports;

import java.math.BigInteger;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.*;

/** One logical table, split without changing row order at the XLSX sheet limit. */
final class WorkbookTable {
    record Weight(BigInteger grams) { }
    private final SXSSFWorkbook book;
    private final String name, notice;
    private final String[] headings;
    private final int limit;
    private final CellStyle body, number, header, date;
    private SXSSFSheet sheet;
    private int rowIndex, part;

    WorkbookTable(SXSSFWorkbook book, String[] description, int limit) {
        this.book = book; this.name = description[0]; this.notice = description[1]; this.limit = limit;
        this.headings = Arrays.copyOfRange(description, 2, description.length);
        body = style(false); number = style(false); header = style(true); date = style(false);
        body.setDataFormat(book.createDataFormat().getFormat("@"));
        number.setDataFormat(book.createDataFormat().getFormat("0.###"));
        date.setDataFormat(book.createDataFormat().getFormat("yyyy-mm-dd"));
        nextSheet();
    }
    void add(Object... values) {
        if (rowIndex == limit + 3) { finish(); nextSheet(); }
        var row = sheet.createRow(rowIndex++);
        int column = 0, lines = 1;
        for (Object value : values) {
            if (value instanceof Weight weight) {
                var cell = row.createCell(column++); cell.setCellStyle(number);
                if (Values.excelExact(weight.grams())) cell.setCellValue(Double.parseDouble(Values.kg(weight.grams())));
                value = Values.kg(weight.grams());
            }
            var cell = row.createCell(column++); cell.setCellStyle(body);
            write(cell, value);
            lines = Math.max(lines, displayLines(String.valueOf(value), column - 1));
        }
        Values.require(column == headings.length, "表格列数错误: " + name);
        Values.require(lines <= 30, "单元格换行超过可读行高限制: " + name);
        row.setHeightInPoints(Math.max(23, lines * 13 + 6));
    }
    void finish() {
        sheet.setAutoFilter(new CellRangeAddress(2, Math.max(2, rowIndex - 1), 0, headings.length - 1));
    }
    private void nextSheet() {
        sheet = book.createSheet(part++ == 0 ? name : name + "_" + part);
        sheet.setDisplayGridlines(false);
        rowIndex = 3;
        for (int c = 0; c < headings.length; c++) sheet.setColumnWidth(c, width(headings[c]) * 256);
        var title = sheet.createRow(0); title.setHeightInPoints(28);
        title.createCell(0).setCellValue(name); title.getCell(0).setCellStyle(header);
        var meta = sheet.createRow(1); meta.setHeightInPoints(32);
        meta.createCell(0).setCellValue(notice); meta.getCell(0).setCellStyle(body);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, headings.length - 1));
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, headings.length - 1));
        var head = sheet.createRow(2); head.setHeightInPoints(34);
        for (int c = 0; c < headings.length; c++) {
            head.createCell(c).setCellValue(headings[c]); head.getCell(c).setCellStyle(header);
        }
        sheet.createFreezePane(2, 3);
        sheet.setRepeatingRows(new CellRangeAddress(0, 2, -1, -1));
        sheet.getPrintSetup().setLandscape(true); sheet.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);
        sheet.getFooter().setRight("第 &P 页 / 共 &N 页");
    }
    private CellStyle style(boolean strong) {
        var style = book.createCellStyle(); var font = book.createFont();
        font.setFontName("等线"); font.setFontHeightInPoints((short) 10); font.setBold(strong);
        style.setFont(font); style.setWrapText(true); style.setVerticalAlignment(VerticalAlignment.TOP);
        if (strong) {
            style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        return style;
    }
    private void write(Cell cell, Object value) {
        if (value == null) { cell.setBlank(); return; }
        if (value instanceof java.time.LocalDate day) {
            cell.setCellValue(day.atStartOfDay()); cell.setCellStyle(date); return;
        }
        if (value instanceof Number n && n.toString().replace("-", "").length() <= 15) {
            cell.setCellValue(n.doubleValue()); return;
        }
        cell.setCellValue(value.toString()); // STRING, never a formula, even when beginning = + - @.
    }
    private static int width(String heading) {
        if (heading.matches(".*(备注|说明|依据|原因|名称|型号|前文本|后文本|值).*")) return 80;
        if (heading.contains("条码") || heading.contains("ID") || heading.contains("时间")) return 36;
        return 24;
    }
    private int displayLines(String text, int column) {
        int result = 0;
        for (var line : text.split("\n", -1)) {
            int units = line.codePoints().map(c -> c > 255 ? 2 : 1).sum();
            result += Math.max(1, (int) Math.ceil((double) units / (width(headings[column]) - 2)));
        }
        return result;
    }
}
