package com.acrylic.outbound.relay;

import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Synthetic package builder; actual Android/Keystore consumption is a separate gate. */
public final class RelayBundleFixture {
    private RelayBundleFixture() { }
    public static Map<String,byte[]> files(JsonNode order,JsonNode body) {
        var files=new LinkedHashMap<String,byte[]>();files.put("order.json",bytes(order.toString()));
        for(String name:List.of("captures.ndjson","scans.ndjson","events.ndjson","lines.ndjson","dispositions.ndjson"))files.put(name,new byte[0]);
        for(String name:List.of("scan-details.tsv","product-summary.tsv","readme.txt"))files.put(name,bytes("synthetic\n"));
        if(body!=null) {
            byte[] payload=bytes(SubmissionJson.canonical(body));files.put("submission.json",payload);
            files.put("submission-info.json",bytes(SubmissionJson.object().put("id",body.path("submissionId").asText())
                .put("sha256",ImportFiles.sha256(payload)).toString()));
        }
        return files;
    }
    public static byte[] build(Map<String,byte[]> input,KeyPair key) throws Exception { return build(input,key,UUID.randomUUID(),null); }
    public static byte[] build(Map<String,byte[]> input,KeyPair key,UUID export,String manifestOverride) throws Exception {
        var files=new LinkedHashMap<>(input);var manifest=SubmissionJson.object().put("purpose",RelayArchive.PURPOSE)
            .put("exportId",export.toString()).put("deviceKeySha256",ImportFiles.sha256(key.getPublic().getEncoded()));
        manifest.putObject("summary");var facts=manifest.putObject("files");
        files.forEach((name,value)->facts.putObject(name).put("bytes",Integer.toString(value.length)).put("sha256",ImportFiles.sha256(value)));
        String text=manifestOverride==null?manifest.toString():manifestOverride;
        var signature=Signature.getInstance("SHA256withECDSA");signature.initSign(key.getPrivate());
        signature.update(bytes(RelayArchive.PURPOSE+"\n"+text));
        files.put("manifest.json",bytes(text));files.put("manifest.sig",signature.sign());files.put("device-public-key.der",key.getPublic().getEncoded());
        return zip(files);
    }
    public static byte[] zip(Map<String,byte[]> files) throws IOException {
        var output=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(output)) {
            for(var entry:files.entrySet()) { zip.putNextEntry(new ZipEntry(entry.getKey()));zip.write(entry.getValue());zip.closeEntry(); }
        }
        return output.toByteArray();
    }
    public static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
}
