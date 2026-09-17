package com.acrylic.outbound.inventory.parser;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;

public class FixedWidthRowParser {
    private static final Charset ENCODING = Charset.forName("GB18030");

    public ImportRow parse(byte[] bytes, int lineNo) {
        String raw = new String(bytes, 0, Math.min(bytes.length, 1024), ENCODING);
        if (bytes.length != 57) return new ImportRow(lineNo, raw, null, "ROW_BYTE_LENGTH", "每行必须为57字节，不含换行");
        try {
            ParsedBale bale = fields(bytes);
            return new ImportRow(lineNo, raw, bale, null, null);
        } catch (CharacterCodingException error) {
            return new ImportRow(lineNo, raw, null, "INVALID_ENCODING", "字段不是有效GB18030编码或字符跨越列边界");
        } catch (IllegalArgumentException | ArithmeticException error) {
            return new ImportRow(lineNo, raw, null, "INVALID_FIELD", error.getMessage());
        }
    }

    private ParsedBale fields(byte[] bytes) throws CharacterCodingException {
        String model = field(bytes, 0, 20);
        String name = field(bytes, 20, 14);
        String barcode = field(bytes, 34, 13);
        String weight = field(bytes, 47, 10);
        if (model.isEmpty() || name.isEmpty()) throw new IllegalArgumentException("型号和品名不能为空");
        if (!barcode.matches("[A-Z0-9]{11}")) throw new IllegalArgumentException("条码必须为11位大写字母或数字");
        if (!weight.matches("[0-9]+(?:\\.[0-9]{1,3})?"))
            throw new IllegalArgumentException("重量必须为最多3位小数的正数，不能舍入或使用科学计数法");
        long grams = new BigDecimal(weight).movePointRight(3).longValueExact();
        if (grams <= 0) throw new IllegalArgumentException("重量必须大于0");
        return new ParsedBale(model, name, barcode, barcode.substring(0, 3), weight, Long.toString(grams));
    }

    private String field(byte[] bytes, int start, int length) throws CharacterCodingException {
        return ENCODING.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes, start, length)).toString().strip();
    }
}
