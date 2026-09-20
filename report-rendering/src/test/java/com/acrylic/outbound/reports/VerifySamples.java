package com.acrylic.outbound.reports;

import java.nio.file.*;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Read-back verification of actual generated PDFs; no business services involved. */
public final class VerifySamples {
    public static void main(String[] args) throws Exception {
        var results = new ArrayList<Map<String, Object>>();
        for (String name : List.of("empty", "corrected", "multipage", "confirmed-v1")) results.add(verify(name));
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(Path.of("build/samples/pdf-verification.json").toFile(), results);
    }
    private static Map<String, Object> verify(String name) throws Exception {
        try (var pdf = Loader.loadPDF(Path.of("build/samples/" + name + ".pdf").toFile())) {
            var stripper = new PDFTextStripper(); var text = stripper.getText(pdf);
            for (int i = 1; i <= pdf.getNumberOfPages(); i++) {
                stripper.setStartPage(i); stripper.setEndPage(i); String page = stripper.getText(pdf);
                Values.require(page.contains("测试样例"), "样例页缺少标识");
                Values.require(page.contains("第 " + i + " 页 / 共 " + pdf.getNumberOfPages() + " 页"), "页码缺失");
                if (page.contains("操作员签字")) Values.require(page.contains("合计") && page.contains("TEST-R08-"), "签字区不能脱离本单合计");
                Values.require(pdf.getPage(i - 1).getAnnotations().isEmpty(), "意外的PDF交互或链接");
                var resources = pdf.getPage(i - 1).getResources();
                for (var font : resources.getFontNames()) Values.require(resources.getFont(font).isEmbedded(), "字体未嵌入");
            }
            if (name.equals("corrected")) Values.require(text.contains("601.002") && text.contains("c2") && text.contains("0009"), "更正结果缺失");
            if (name.equals("multipage")) Values.require(text.contains("<script>") && text.contains("1234567890") && text.contains("18030.06"), "长文本/条码/合计缺失");
            if (name.equals("confirmed-v1")) Values.require(text.contains("TEST-R08-V1-PDA") && text.contains("TEST-R08-V1-PC")
                    && text.contains("1803.006") && !text.contains("SYNTHETIC_NOT_A_SIGNATURE"), "首版来源/合计错误或泄露回执原文");
            Files.writeString(Path.of("build/samples/" + name + ".txt"), text);
            return Map.of("sample", name, "pages", pdf.getNumberOfPages(), "characters", text.length(),
                    "embeddedFonts", true, "headersAndPageNumbers", true, "annotations", 0);
        }
    }
}
