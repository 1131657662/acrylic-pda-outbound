package com.acrylic.outbound.inventory;

import java.nio.charset.Charset;
import java.util.*;

/** Synthetic byte inputs; expected results are produced by the actual server parser. */
final class SkuParityVectors {
    private SkuParityVectors() { }
    static Map<String,byte[]> inputs() {
        var values=new LinkedHashMap<String,byte[]>();byte[] row=SkuFixture.row("003B0051601","305.125");
        values.put("crlf",row);values.put("lf",concat(Arrays.copyOf(row,57),new byte[]{10}));
        values.put("no-final-newline",Arrays.copyOf(row,57));values.put("cr-only",concat(row,row,new byte[]{13}));
        values.put("bom-and-blanks",concat(new byte[]{(byte)0xef,(byte)0xbb,(byte)0xbf,13,10,10},row));
        values.put("blank-middle",concat(row,new byte[]{13,10},SkuFixture.row("003B0051602","0.001")));
        values.put("empty",new byte[0]);values.put("blank-only",new byte[]{13,10,10});
        values.put("bom-only",new byte[]{(byte)0xef,(byte)0xbb,(byte)0xbf});
        values.put("utf16-bom",concat(new byte[]{(byte)0xff,(byte)0xfe},row));
        values.put("identical",concat(row,row));values.put("same-grams-different-text",concat(row,SkuFixture.row("003B0051601","305.1250")));
        values.put("barcode-conflict",concat(row,SkuFixture.row("003B0051601","306")));
        values.put("mapping-conflict",concat(row,SkuFixture.row("other model","003B0051602","305")));
        values.put("conflict-precedence",concat(row,SkuFixture.row("other model","003B0051601","305"),SkuFixture.row("003B0051602","305")));
        fields(values);boundaries(values,row);return values;
    }
    private static void fields(Map<String,byte[]> values) {
        for(String weight:List.of("15.5","389.5","0.001","300.500","9999999999","0","-1","300.5001","1e3","NaN","300,5",".5","1.","+1"))
            values.put("weight-"+weight,SkuFixture.row("003B0051601",weight));
        for(String barcode:List.of("003b0051601","003B005160","003B00516012","AB3B0051601"))
            values.put("barcode-"+barcode,SkuFixture.row(barcode,"305"));
        values.put("empty-model",SkuFixture.row("","003B0051601","305"));
        values.put("unicode-strip",SkuFixture.row("\u2003M\u2003","003B0051601","305"));
        values.put("non-whitespace-nbsp",SkuFixture.row("\u00a0","003B0051601","305"));
    }
    private static void boundaries(Map<String,byte[]> values,byte[] row) {
        byte[] invalid=row.clone();invalid[33]=(byte)0xff;values.put("invalid-encoding",invalid);
        byte[] crossing=row.clone();crossing[19]=(byte)0xd3;crossing[20]=(byte)0xd0;values.put("cross-column-character",crossing);
        values.put("valid-subset-must-block",concat("bad\r\n".getBytes(Charset.forName("GB18030")),row));
        values.put("short-line",Arrays.copyOf(row,56));values.put("long-line",concat(Arrays.copyOf(row,57),new byte[]{32}));
        values.put("bounded-raw-preview",filled(100000,(byte)'A'));
        values.put("exact-file-limit",filled(2*1024*1024,(byte)'A'));values.put("over-file-limit",new byte[2*1024*1024+1]);
        values.put("exact-line-limit",concat(filled(39999,(byte)10),row));values.put("over-line-limit",concat(filled(40000,(byte)10),row));
    }
    private static byte[] concat(byte[]... chunks) { return SkuFixture.concat(chunks); }
    private static byte[] filled(int size,byte value) { byte[] bytes=new byte[size];Arrays.fill(bytes,value);return bytes; }
}
