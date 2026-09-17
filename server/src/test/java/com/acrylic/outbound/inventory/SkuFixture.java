package com.acrylic.outbound.inventory;

import java.nio.charset.Charset;
import java.util.Arrays;

public final class SkuFixture {
    private static final Charset ENCODING = Charset.forName("GB18030");
    private SkuFixture() { }

    public static byte[] row(String barcode, String weight) { return row("H815B 1.7 RE 38 300", barcode, weight); }

    public static byte[] row(String model, String barcode, String weight) {
        byte[] line = new byte[59];
        Arrays.fill(line, (byte) ' ');
        put(line, 0, model);
        put(line, 20, "有光短纤");
        put(line, 34, barcode);
        put(line, 47, weight);
        line[57] = '\r'; line[58] = '\n';
        return line;
    }

    public static byte[] concat(byte[]... rows) {
        var output = new java.io.ByteArrayOutputStream();
        for (byte[] row : rows) output.writeBytes(row);
        return output.toByteArray();
    }
    private static void put(byte[] row, int position, String value) {
        byte[] bytes = value.getBytes(ENCODING);
        System.arraycopy(bytes, 0, row, position, bytes.length);
    }
}
