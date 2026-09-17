package com.acrylic.outbound.inventory.parser;

import com.acrylic.outbound.common.BusinessException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SkuParser {
    public static final String VERSION = "sku-gb18030-fixed57-v1";
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    private final FixedWidthRowParser parser = new FixedWidthRowParser();

    public ImportPreview parse(byte[] file) {
        if (file.length == 0) throw new BusinessException(400, "EMPTY_FILE", "文件为空");
        if (file.length > MAX_BYTES) throw new BusinessException(413, "FILE_TOO_LARGE", "文件不得超过2MiB");
        boolean bom = file.length >= 3 && file[0] == (byte) 0xef && file[1] == (byte) 0xbb && file[2] == (byte) 0xbf;
        List<ImportRow> rows = new ArrayList<>();
        int start = bom ? 3 : 0;
        int line = 0;
        int ignored = 0;
        for (int i = start; i <= file.length; i++) {
            if (i < file.length && file[i] != '\n') continue;
            if (i == file.length && start == i) break;
            byte[] bytes = Arrays.copyOfRange(file, start, i > start && file[i - 1] == '\r' ? i - 1 : i);
            line++;
            if (line > 40000) throw new BusinessException(400, "ROW_LIMIT_EXCEEDED", "文件超过40000行，请核对文件格式");
            if (bytes.length == 0) ignored++; else rows.add(parser.parse(bytes, line));
            start = i + 1;
        }
        if (rows.isEmpty()) throw new BusinessException(400, "EMPTY_FILE", "文件没有有效数据行");
        return summarize(ImportConflicts.apply(rows), ignored, bom);
    }

    private ImportPreview summarize(List<ImportRow> rows, int ignored, boolean bom) {
        var valid = rows.stream().filter(row -> row.errorCode() == null).toList();
        int errors = (int) rows.stream().filter(ImportRow::blocking).count();
        int duplicates = rows.size() - valid.size() - errors;
        String status = errors > 0 ? "BLOCKED" : duplicates > 0 ? "NEEDS_DEDUP_CONFIRMATION" : "READY_FOR_REVIEW";
        long grams = 0;
        for (ImportRow row : valid) grams = Math.addExact(grams, Long.parseLong(row.parsedFields().weightGrams()));
        int products = (int) valid.stream().map(row -> row.parsedFields().deliveryCode()).distinct().count();
        return new ImportPreview(VERSION, status, rows.size(), valid.size(), errors, duplicates, ignored, products,
            Long.toString(grams), bom, rows);
    }
}
