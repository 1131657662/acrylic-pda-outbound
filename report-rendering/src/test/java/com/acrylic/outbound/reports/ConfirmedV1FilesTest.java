package com.acrylic.outbound.reports;

import java.nio.file.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static com.acrylic.outbound.reports.ConfirmedV1Fixture.*;

class ConfirmedV1FilesTest {
    @TempDir Path directory;

    @Test void cliRendersBothSourcesAndPreservesExcelText() throws Exception {
        var source = input(order(ConfirmedV1Input.Channel.PDA), order(ConfirmedV1Input.Channel.PC));
        var json = directory.resolve("source.json"); var excel = directory.resolve("confirmed.xlsx");
        SnapshotJson.writeConfirmedV1(source, json);
        ReportCli.main(new String[]{"--confirmed-v1", json.toString(), "xlsx", excel.toString()});
        try (var book = new XSSFWorkbook(excel.toFile())) {
            var raw = book.getSheet("原始扫码明细");
            assertEquals("00000000001", raw.getRow(3).getCell(3).getStringCellValue());
            assertEquals("5", raw.getRow(5).getCell(2).getStringCellValue());
            assertEquals("300.501", raw.getRow(3).getCell(6).getStringCellValue());
            assertEquals("pc-work:work-PC", raw.getRow(6).getCell(8).getStringCellValue());
            assertEquals(CellType.STRING, raw.getRow(3).getCell(18).getCellType());
            assertTrue(raw.getRow(3).getCell(18).getStringCellValue().startsWith("=HYPERLINK"));
            assertNull(raw.getRow(3).getCell(18).getHyperlink());
            for (var sheet : book) for (var row : sheet) for (var cell : row) assertNotEquals(CellType.FORMULA, cell.getCellType());
        }
    }

    @Test void cliRendersPdfWithOriginalDayAndExplicitSampleMarking() throws Exception {
        var json = directory.resolve("source.json"); var path = directory.resolve("confirmed.pdf");
        SnapshotJson.writeConfirmedV1(input(order(ConfirmedV1Input.Channel.PDA), order(ConfirmedV1Input.Channel.PC)), json);
        ReportCli.main(new String[]{"--confirmed-v1", json.toString(), "pdf", path.toString()});
        try (var pdf = Loader.loadPDF(path.toFile())) {
            var stripper = new PDFTextStripper(); String text = stripper.getText(pdf);
            assertTrue(text.contains("TEST-R08-V1-PDA")); assertTrue(text.contains("TEST-R08-V1-PC"));
            assertTrue(text.contains("901.503")); assertTrue(text.contains("2026-09-18"));
            assertTrue(text.contains("不含后续更正")); assertFalse(text.contains("当前净结果"));
            assertEquals("SHIPMENT_DATE_NET", pdf.getDocumentInformation().getCustomMetadataValue("PeriodBasis"));
            assertFalse(text.contains("SYNTHETIC_NOT_A_SIGNATURE"));
            for (int i = 1; i <= pdf.getNumberOfPages(); i++) {
                stripper.setStartPage(i); stripper.setEndPage(i);
                assertTrue(stripper.getText(pdf).contains("测试样例"));
                assertTrue(pdf.getPage(i - 1).getAnnotations().isEmpty());
            }
        }
    }

    @Test void rejectsUnknownSourceFieldsWithoutCreatingAnArtifact() throws Exception {
        var json = directory.resolve("unknown.json"); var output = directory.resolve("rejected.xlsx");
        SnapshotJson.writeConfirmedV1(input(order(ConfirmedV1Input.Channel.PDA)), json);
        Files.writeString(json, Files.readString(json).replace("\"dispatches\"", "\"unrecognized\":true,\"dispatches\""));
        assertThrows(java.io.IOException.class, () -> ReportCli.main(new String[]{"--confirmed-v1", json.toString(), "xlsx", output.toString()}));
        assertFalse(Files.exists(output));
        try (var files = Files.list(directory)) { assertTrue(files.noneMatch(p -> p.toString().endsWith(".part"))); }
    }

    @Test void acceptsOpaqueReceiptSignatureWithoutClaimingToVerifyIt() throws Exception {
        var source = order(ConfirmedV1Input.Channel.PDA); var receipt = (ObjectNode) source.successReceipt();
        receipt.put("signedReceipt", "SYNTHETIC".repeat(400));
        var json = directory.resolve("opaque.json");
        SnapshotJson.writeConfirmedV1(input(withReceipt(source, receipt)), json);
        assertEquals("order-PDA", SnapshotJson.readConfirmedV1(json).orders().getFirst().id());
    }

    @Test void confirmedCliStillRefusesToOverwrite() throws Exception {
        var json = directory.resolve("source.json"); var output = directory.resolve("existing.xlsx");
        SnapshotJson.writeConfirmedV1(input(order(ConfirmedV1Input.Channel.PC)), json);
        Files.writeString(output, "preserve-existing");
        assertThrows(IllegalArgumentException.class, () -> ReportCli.main(new String[]{"--confirmed-v1", json.toString(), "xlsx", output.toString()}));
        assertEquals("preserve-existing", Files.readString(output));
    }

    @Test void rejectsDuplicateFieldsInsideReceipt() throws Exception {
        var json = directory.resolve("duplicate-receipt.json");
        SnapshotJson.writeConfirmedV1(input(order(ConfirmedV1Input.Channel.PDA)), json);
        Files.writeString(json, Files.readString(json).replace("\"status\" : \"SUCCEEDED\"", "\"status\":\"SUCCEEDED\",\"status\":\"ABORTED\""));
        assertThrows(java.io.IOException.class, () -> SnapshotJson.readConfirmedV1(json));
    }

    @Test void rejectsNumericProjectedWeightInsteadOfCoercing() throws Exception {
        var json = directory.resolve("numeric-grams.json");
        SnapshotJson.writeConfirmedV1(input(order(ConfirmedV1Input.Channel.PDA)), json);
        Files.writeString(json, Files.readString(json).replace("\"weightGrams\" : \"300501\"", "\"weightGrams\":300501"));
        assertThrows(java.io.IOException.class, () -> SnapshotJson.readConfirmedV1(json));
    }
}
