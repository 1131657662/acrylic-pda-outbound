package com.acrylic.outbound.reports;

import java.io.*;
import java.time.LocalDateTime;
import java.util.*;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class ScanRecordExcelRendererTest {
    private ReportSnapshot original(List<OrderSnapshot> orders) {
        var m=Samples.report(orders).metadata();
        return new ReportSnapshot(new ReportSnapshot.Metadata(m.snapshotId(),m.dataVersion(),m.cutoff(),m.generatedAt(),
                m.fromDate(),m.throughDate(),ReportSnapshot.Basis.ORIGINAL_CONFIRMED,m.provenance(),m.sourceDescription(),m.orderSortDescription()),orders);
    }
    private XSSFWorkbook render(ReportSnapshot snapshot) throws Exception {
        var bytes=new ByteArrayOutputStream();new ScanRecordExcelRenderer().render(snapshot,bytes);
        return new XSSFWorkbook(new ByteArrayInputStream(bytes.toByteArray()));
    }
    private ReportLine changed(ReportLine r,long sequence,String operator,String received) {
        return new ReportLine(r.dispatchId(),r.captureId(),sequence,r.originDeviceId(),r.deviceCaptureSeq(),operator,r.inputSource(),
                r.barcode(),r.dispatchCycle(),r.productCode(),r.weightGrams(),r.rawWeight(),received,r.savedAt(),r.timeQuality(),r.remark());
    }
    @Test void matchesTemplateWithNoHeadersAndChinaTimesInScanOrder() throws Exception {
        var order=Samples.order(3);
        var rows=new ArrayList<>(order.originalLines());
        rows.set(0,changed(rows.get(0),2,"刘  颖","2026-09-17T16:00:00.123Z"));
        rows.set(1,changed(rows.get(1),5,"李明","2026-09-17T15:59:00Z"));
        rows.set(2,changed(rows.get(2),9,"张华","2026-09-17T16:01:00Z"));
        try(var book=render(original(List.of(Samples.withLines(order,rows))))) {
            assertEquals(1,book.getNumberOfSheets());var sheet=book.getSheet("out");
            assertEquals("宋体",book.getFontAt(0).getFontName());assertEquals(11,book.getFontAt(0).getFontHeightInPoints());
            assertEquals(3,sheet.getPhysicalNumberOfRows());assertEquals(0,sheet.getNumMergedRegions());
            double[] widths={6.625,22.625,15.625,12.625,9.5,19.875};
            for(int i=0;i<6;i++) assertEquals((int)(widths[i]*256),sheet.getColumnWidth(i));
            for(int i=0;i<3;i++) {
                var row=sheet.getRow(i);assertEquals(6,row.getLastCellNum());assertEquals(i+1,row.getCell(0).getNumericCellValue());
                assertEquals(rows.get(i).barcode(),row.getCell(2).getStringCellValue());
                assertEquals(300.501,row.getCell(3).getNumericCellValue(),0.0000001);
            }
            assertEquals(order.products().getFirst().model(),sheet.getRow(0).getCell(1).getStringCellValue());
            assertEquals(order.products().get(1).model(),sheet.getRow(1).getCell(1).getStringCellValue());
            assertEquals("刘  颖",sheet.getRow(0).getCell(4).getStringCellValue());
            assertEquals(LocalDateTime.parse("2026-09-18T00:00:00.123"),sheet.getRow(0).getCell(5).getLocalDateTimeCellValue());
            assertEquals("m/d/yy h:mm",sheet.getRow(0).getCell(5).getCellStyle().getDataFormatString());
        }
    }
    @ParameterizedTest @ValueSource(strings={"=1+1","+cmd|' /C calc'!A0","@SUM(A1)","-1+2","\t=HYPERLINK(\"https://evil\")"})
    void neverExecutesMaliciousBarcodeText(String barcode) throws Exception {
        var order=Samples.order(1);order=Samples.withLines(order,List.of(Samples.changed(order.originalLines().getFirst(),"barcode",barcode)));
        try(var book=render(original(List.of(order)))) {
            var cell=book.getSheetAt(0).getRow(0).getCell(2);
            assertEquals(CellType.STRING,cell.getCellType());assertEquals(barcode,cell.getStringCellValue());assertNull(cell.getHyperlink());
            for(var row:book.getSheetAt(0)) for(var c:row) assertNotEquals(CellType.FORMULA,c.getCellType());
        }
    }
    @Test void longBarcodeAndUnsafeWeightArePreservedWithoutChangingWidths() throws Exception {
        var order=Samples.order(1);String barcode="0".repeat(200);
        var line=Samples.changed(Samples.changed(order.originalLines().getFirst(),"barcode",barcode),"weight","900719925474099312345");
        try(var book=render(original(List.of(Samples.withLines(order,List.of(line)))))) {
            var row=book.getSheetAt(0).getRow(0);
            assertEquals(barcode,row.getCell(2).getStringCellValue());assertTrue(row.getHeightInPoints()>13.5f);
            assertEquals("900719925474099312.345",row.getCell(3).getStringCellValue());
            assertEquals(4000,book.getSheetAt(0).getColumnWidth(2));
        }
    }
    @Test void emptyInputHasOnlyAnEmptyTemplateSheet() throws Exception {
        try(var book=render(original(List.of()))) {
            assertEquals(1,book.getNumberOfSheets());assertEquals(0,book.getSheetAt(0).getPhysicalNumberOfRows());
        }
    }
    @Test void rejectsUnclearBasisPlaceholderNamesAndTimes() {
        assertThrows(IllegalArgumentException.class,()->ScanRecordExcelRenderer.validate(Samples.report(List.of(Samples.corrected()))));
        var order=Samples.order(1);var line=order.originalLines().getFirst();
        for(String name:List.of("原人员ID:123","","   ","5052f470-906c-44e3-9e7a-af0e08ff93f7","5052F470906C44E39E7AAF0E08FF93F7"))
            assertThrows(IllegalArgumentException.class,()->ScanRecordExcelRenderer.validate(original(List.of(
                    Samples.withLines(order,List.of(changed(line,1,name,line.receivedAt())))))));
        for(String name:List.of("operator-123","Liu Ying","张 三","test_pda01"))
            ScanRecordExcelRenderer.validate(original(List.of(Samples.withLines(order,List.of(changed(line,1,name,line.receivedAt()))))));
        assertThrows(IllegalArgumentException.class,()->ScanRecordExcelRenderer.validate(original(List.of(
                Samples.withLines(order,List.of(changed(line,1,"中文名","")))))));
    }
    @Test void largeInputHasContinuousDisplayNumbersAndNoExtraRows() throws Exception {
        try(var book=render(original(List.of(Samples.order(50_000))))) {
            var sheet=book.getSheetAt(0);assertEquals(50_000,sheet.getPhysicalNumberOfRows());
            assertEquals(50_000,sheet.getRow(49_999).getCell(0).getNumericCellValue());
            assertEquals("00000050000",sheet.getRow(49_999).getCell(2).getStringCellValue());
        }
    }
}
