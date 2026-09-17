package com.acrylic.outbound.inventory.parser;

import java.util.List;

public record ImportPreview(String parserVersion, String status, int totalRows, int validRows,
                            int errorRows, int duplicateRows, int ignoredRows, int productCount,
                            String totalWeightGrams, boolean bomRemoved, List<ImportRow> rows) { }
