package com.acrylic.outbound.reports;

import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import javax.xml.stream.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LargeWorkbookTest {
    @TempDir Path directory;
    @Test void streamsFiftyThousandRowsWithExactTotalsAcrossFiveSheets() throws Exception {
        var snapshot = Samples.report(List.of(Samples.order(50_000)));
        var target = directory.resolve("large.xlsx");
        try (var out = Files.newOutputStream(target)) { new ExcelRenderer(10_000).render(snapshot, out); }
        assertEquals("15025050", Values.kg(ReportTotals.all(snapshot).netGrams()));
        try (var zip = new ZipFile(target.toFile())) {
            var sheets = new String(zip.getInputStream(zip.getEntry("xl/workbook.xml")).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(sheets.contains("原始扫码明细_5"));
            assertTrue(sheets.contains("版本有效明细_5"));
            assertFalse(sheets.contains("原始扫码明细_6"));
            // Order summary and products occupy sheets 1/2; raw detail sheets 3,7,9,11,13.
            List<Integer> rawSheets = rawSheetIndexes(sheets);
            int next = 1;
            for (int index : rawSheets) next = verifySequence(zip, index, next);
            assertEquals(50_001, next);
        }
    }
    private static List<Integer> rawSheetIndexes(String xml) throws Exception {
        var factory = XMLInputFactory.newFactory(); factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        var reader = factory.createXMLStreamReader(new java.io.StringReader(xml));
        var indexes = new ArrayList<Integer>(); int index = 0;
        while (reader.hasNext()) {
            if (reader.next() == XMLStreamConstants.START_ELEMENT && reader.getLocalName().equals("sheet")) {
                index++;
                if (reader.getAttributeValue(null, "name").startsWith("原始扫码明细")) indexes.add(index);
            }
        }
        reader.close(); return indexes;
    }
    private static int verifySequence(ZipFile zip, int sheet, int next) throws Exception {
        var factory = XMLInputFactory.newFactory(); factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        try (var input = zip.getInputStream(zip.getEntry("xl/worksheets/sheet" + sheet + ".xml"))) {
            var reader = factory.createXMLStreamReader(input); String reference = "";
            while (reader.hasNext()) {
                if (reader.next() != XMLStreamConstants.START_ELEMENT) continue;
                assertNotEquals("f", reader.getLocalName(), "不允许公式");
                if (reader.getLocalName().equals("c")) reference = reader.getAttributeValue(null, "r");
                if (reader.getLocalName().equals("t") && reference.matches("C[0-9]+") && Integer.parseInt(reference.substring(1)) >= 4)
                    assertEquals(Integer.toString(next++), reader.getElementText());
            }
            reader.close(); return next;
        }
    }
}
