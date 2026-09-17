package com.acrylic.outbound.inventory.parser;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ImportConflicts {
    private ImportConflicts() { }

    static List<ImportRow> apply(List<ImportRow> rows) {
        Map<String, ParsedBale> barcodes = new HashMap<>();
        Map<String, List<String>> mappings = new HashMap<>();
        Set<String> barcodeConflicts = new HashSet<>();
        Set<String> mappingConflicts = new HashSet<>();
        for (ImportRow row : rows) collect(row, barcodes, mappings, barcodeConflicts, mappingConflicts);
        Set<String> seen = new HashSet<>();
        return rows.stream().map(row -> classify(row, seen, barcodeConflicts, mappingConflicts)).toList();
    }

    private static void collect(ImportRow row, Map<String, ParsedBale> barcodes,
                                Map<String, List<String>> mappings, Set<String> badCodes, Set<String> badMappings) {
        ParsedBale bale = row.parsedFields();
        if (bale == null) return;
        ParsedBale previous = barcodes.putIfAbsent(bale.barcode(), bale);
        if (previous != null && !previous.equals(bale)) badCodes.add(bale.barcode());
        List<String> mapping = List.of(bale.model(), bale.name());
        List<String> previousMapping = mappings.putIfAbsent(bale.deliveryCode(), mapping);
        if (previousMapping != null && !previousMapping.equals(mapping)) badMappings.add(bale.deliveryCode());
    }

    private static ImportRow classify(ImportRow row, Set<String> seen, Set<String> badCodes, Set<String> badMappings) {
        ParsedBale bale = row.parsedFields();
        if (bale == null) return row;
        if (badCodes.contains(bale.barcode())) return row.withError("DUPLICATE_CONFLICT", "同条码存在不同数据，相关行均阻断");
        if (badMappings.contains(bale.deliveryCode())) return row.withError("MAPPING_CONFLICT", "同发货代码对应多个型号或品名，相关行均阻断");
        if (!seen.add(bale.barcode())) return row.withError("DUPLICATE_IDENTICAL", "相同条码及字段重复，需要明确确认去重");
        return row;
    }
}
