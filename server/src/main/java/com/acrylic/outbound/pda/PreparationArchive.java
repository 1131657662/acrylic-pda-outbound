package com.acrylic.outbound.pda;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import com.acrylic.outbound.common.BusinessException;

public record PreparationArchive(byte[] compressed,Part descriptor) {
    public static PreparationArchive pack(String name,String text,int rows) {
        byte[] raw=text.getBytes(StandardCharsets.UTF_8);
        if(raw.length>16*1024*1024) throw new BusinessException(413,"PREPARATION_TOO_LARGE","准备包超过当前支持大小，请联系管理员");
        try {
            var bytes=new ByteArrayOutputStream();
            try(var gzip=new GZIPOutputStream(bytes)) { gzip.write(raw); }
            byte[] compressed=bytes.toByteArray();
            return new PreparationArchive(compressed,new Part(name,compressed.length,raw.length,sha(compressed),sha(raw),rows));
        } catch(IOException error) { throw new UncheckedIOException(error); }
    }
    public static String sha(byte[] data) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
        catch(GeneralSecurityException error) { throw new IllegalStateException(error); }
    }
    public record Part(String name,int compressedBytes,int bytes,String compressedSha256,String sha256,int rows) { }
}
