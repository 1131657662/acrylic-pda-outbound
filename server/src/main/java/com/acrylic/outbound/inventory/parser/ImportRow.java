package com.acrylic.outbound.inventory.parser;

public record ImportRow(int lineNo, String rawText, ParsedBale parsedFields,
                        String errorCode, String errorDetail) {
    public ImportRow withError(String code, String detail) {
        return new ImportRow(lineNo, rawText, parsedFields, code, detail);
    }
    public boolean blocking() { return errorCode != null && !errorCode.equals("DUPLICATE_IDENTICAL"); }
}
