package com.acrylic.outbound.temporary.protocol;

import com.acrylic.outbound.inventory.ImportFiles;
import java.nio.charset.StandardCharsets;

/** Production byte/domain contract only. No route, permission or business decision is enabled. */
public final class TemporaryProtocol {
    public static final String ROUTE_SCOPE="r06.command.v1";
    public static final int COMMAND_VERSION=1, RECEIPT_VERSION=1;
    public static final String RECEIPT_AUDIENCE="acrylic-r06-command-receipt-v1";
    public static final String ISSUER="acrylic-outbound";
    private TemporaryProtocol() { }
    public static byte[] canonical(byte[] utf8) { return ProtocolJson.canonical(ProtocolJson.parse(utf8)); }
    public static byte[] command(byte[] utf8) {
        var value=ProtocolJson.parse(utf8);
        TemporaryCommandShape.validate(value);
        return ProtocolJson.canonical(value);
    }
    public static String commandSha256(byte[] utf8) { return domain("COMMAND",command(utf8)); }
    public static String unsignedReceiptSha256(byte[] utf8) { return domain("RECEIPT",object(utf8)); }
    public static String derivedControlSha256(byte[] utf8) { return domain("DERIVED-CONTROL",object(utf8)); }
    public static String originalResponseSha256(byte[] utf8) {
        ProtocolJson.utf8(utf8);
        return ImportFiles.sha256(utf8);
    }
    private static byte[] object(byte[] utf8) {
        var value=ProtocolJson.parse(utf8);
        ProtocolJson.require(value.isObject(),"OBJECT_REQUIRED");
        return ProtocolJson.canonical(value);
    }
    private static String domain(String kind,byte[] bytes) {
        byte[] prefix=("ACRYLIC-R06/"+kind+"/1\n").getBytes(StandardCharsets.UTF_8);
        byte[] input=new byte[prefix.length+bytes.length];
        System.arraycopy(prefix,0,input,0,prefix.length);
        System.arraycopy(bytes,0,input,prefix.length,bytes.length);
        return ImportFiles.sha256(input);
    }
}
