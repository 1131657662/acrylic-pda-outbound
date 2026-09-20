package com.acrylic.outbound.reports;

import java.io.*;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PdfRendererTest {
    @Test void rendersChineseMultiplePagesRepeatedHeadersTotalsAndPageNumbers() throws Exception {
        var snapshot = Samples.report(List.of(Samples.order(50)));
        var out = new ByteArrayOutputStream(); new PdfRenderer().render(snapshot, out);
        try (var pdf = Loader.loadPDF(out.toByteArray())) {
            assertTrue(pdf.getNumberOfPages() >= 3);
            var stripper = new PDFTextStripper(); var text = stripper.getText(pdf);
            assertTrue(text.contains("装车单")); assertTrue(text.contains("15025.05"));
            assertTrue(text.contains("原始扫码明细")); assertTrue(text.contains("版本有效明细"));
            assertEquals("SHIPMENT_DATE_NET", pdf.getDocumentInformation().getCustomMetadataValue("PeriodBasis"));
            assertTrue(text.split("采集时间信息", -1).length > 2, "原始表头应跨页重复");
            for (int i = 1; i <= pdf.getNumberOfPages(); i++) {
                stripper.setStartPage(i); stripper.setEndPage(i); var page = stripper.getText(pdf);
                assertTrue(page.contains("测试样例"), "每页标注样例");
                assertTrue(page.contains("第 " + i + " 页 / 共 " + pdf.getNumberOfPages() + " 页"));
                assertEquals(595.28, pdf.getPage(i - 1).getMediaBox().getWidth(), 1.0);
                assertEquals(841.89, pdf.getPage(i - 1).getMediaBox().getHeight(), 1.0);
            }
        }
    }
    @Test void allInputTextIsEscapedAndNoUserHtmlBecomesMarkup() throws Exception {
        var o = Samples.order(1); String payload = "<script>alert(1)</script><img src='file:///secret'><a href='https://evil'>链接</a>";
        o = Samples.withLines(o, List.of(Samples.changed(o.originalLines().getFirst(), "remark", payload)));
        var html = PdfHtml.document(Samples.report(List.of(o)));
        assertFalse(html.contains("<script>")); assertFalse(html.contains("<img")); assertFalse(html.contains("<a href"));
        assertTrue(html.contains("&lt;script&gt;")); assertTrue(html.contains("&amp;") || html.contains("&#39;"));
    }
    @Test void rejectsOversizedPdfBeforeStartingBrowser() {
        var snapshot = Samples.report(List.of(Samples.order(10_000)));
        assertThrows(IllegalArgumentException.class, () -> new PdfRenderer().render(snapshot, new ByteArrayOutputStream()));
    }
}
