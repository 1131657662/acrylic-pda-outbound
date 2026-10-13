package com.acrylic.outbound.relay;

import com.acrylic.outbound.devices.DeviceCrypto;
import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.submissions.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Integrity is separate from registration trust. Never extracts client paths to disk. */
public final class RelayArchive {
    public static final int MAX_BYTES=32*1024*1024;
    public static final String PURPOSE="PDA_READONLY_RESCUE_V1";
    private static final Set<String> REQUIRED=Set.of("order.json","captures.ndjson","scans.ndjson","events.ndjson",
        "lines.ndjson","dispositions.ndjson","scan-details.tsv","product-summary.tsv","readme.txt");
    private static final Set<String> OPTIONAL=Set.of("submission.json","submission-info.json","receipt.json","source-evidence.ndjson",
        "independent-sku.bin","independent-source.json","independent-voids.ndjson","independent-state.json");
    private static final Set<String> SEAL=Set.of("manifest.json","manifest.sig","device-public-key.der");
    private RelayArchive() { }

    public static Bundle read(byte[] bytes) {
        var files=unpack(bytes);var manifest=json(files.get("manifest.json"));
        require(files.get("manifest.json").length<=65536,"原清单过大");
        equal(fields(manifest),Set.of("purpose","exportId","deviceKeySha256","summary","files"),"原清单字段不符");
        equal(text(manifest,"purpose"),PURPOSE,"原包用途不符");UUID export=uuid(manifest,"exportId");
        var names=new HashSet<>(files.keySet());names.removeAll(SEAL);
        equal(fields(manifest.get("files")),names,"原清单成员不符");
        for(String name:names) digest(files.get(name),manifest.get("files").get(name));
        String key=Base64.getEncoder().encodeToString(files.get("device-public-key.der"));
        equal(text(manifest,"deviceKeySha256"),ImportFiles.sha256(files.get("device-public-key.der")),"原设备钥摘要不符");
        require(files.get("manifest.sig").length<=128,"原签名过大");
        DeviceCrypto.verify(key,PURPOSE+"\n"+utf8(files.get("manifest.json")),Base64.getEncoder().encodeToString(files.get("manifest.sig")));
        var order=json(files.get("order.json"));var identity=identity(order);
        var independent=RelayIndependentOriginal.read(files,order,text(manifest,"deviceKeySha256"));var body=frozen(files);
        if(body!=null) bind(identity,order,body);
        return new Bundle(export,identity,key,text(manifest,"deviceKeySha256"),ImportFiles.sha256(bytes),
            body,body==null?null:SubmissionControl.read(uuid(body,"submissionId"),body),independent);
    }
    private static Map<String,byte[]> unpack(byte[] bytes) {
        require(bytes.length>0 && bytes.length<=MAX_BYTES,"原包大小无效");var files=new LinkedHashMap<String,byte[]>();
        int total=0;Path temporary=null;
        try {
            temporary=Files.createTempFile("relay-verify-",".zip");Files.write(temporary,bytes);
            try(var zip=new ZipFile(temporary.toFile(),StandardCharsets.UTF_8)) {
                require(zip.size()<=REQUIRED.size()+OPTIONAL.size()+SEAL.size(),"原包成员过多");var entries=zip.entries();
                while(entries.hasMoreElements()) {
                ZipEntry entry=entries.nextElement();
                String name=entry.getName();require(REQUIRED.contains(name)||OPTIONAL.contains(name)||SEAL.contains(name),"原包含未知路径");
                require(!entry.isDirectory() && !files.containsKey(name),"原包成员重复");
                byte[] content;
                try(var stream=zip.getInputStream(entry)) { content=stream.readNBytes(MAX_BYTES-total+1); }
                total+=content.length;require(total<=MAX_BYTES,"原包展开过大");
                var crc=new CRC32();crc.update(content);equal(entry.getCrc(),crc.getValue(),"原ZIP校验不符");
                equal(entry.getSize(),(long)content.length,"原ZIP长度不符");files.put(name,content);
                }
            }
        } catch(IOException|IllegalArgumentException error) { throw bad("原ZIP格式无效"); }
        finally { if(temporary!=null)try { Files.deleteIfExists(temporary); }catch(IOException error) { throw new UncheckedIOException(error); } }
        require(files.keySet().containsAll(REQUIRED)&&files.keySet().containsAll(SEAL),"原包缺少成员");return files;
    }
    private static void digest(byte[] bytes,JsonNode fact) {
        equal(fields(fact),Set.of("bytes","sha256"),"原文件摘要字段不符");
        equal(text(fact,"bytes"),Integer.toString(bytes.length),"原文件长度不符");
        equal(text(fact,"sha256"),ImportFiles.sha256(bytes),"原文件摘要不符");
    }
    private static JsonNode frozen(Map<String,byte[]> files) {
        byte[] bytes=files.get("submission.json");
        if(bytes==null) { require(!files.containsKey("submission-info.json")&&!files.containsKey("receipt.json"),"无冻结正文不能声明原批次");return null; }
        require(files.containsKey("submission-info.json"),"缺少原冻结摘要");var info=json(files.get("submission-info.json"));var body=json(bytes);
        equal(fields(info),Set.of("id","sha256"),"原冻结摘要字段不符");equal(uuid(info,"id"),uuid(body,"submissionId"),"原批次ID不符");
        equal(text(info,"sha256"),ImportFiles.sha256(bytes),"原冻结字节摘要不符");
        equal(sha(body),ImportFiles.sha256(bytes),"须提供逐字原规范冻结正文");return body;
    }
    private static Identity identity(JsonNode order) {
        String source=text(order,"source");require(Set.of("FORMAL","TEMP","INDEPENDENT").contains(source),"原来源无效");
        equal(text(order,"timeTrust"),"UNCONFIRMED_EXPORT_TIME","原导出时间信任不符");
        return new Identity(uuid(order,"operatorId"),uuid(order,"deviceId"),uuid(order,"warehouseId"),source,uuid(order,"localOrderId"));
    }
    private static void bind(Identity original,JsonNode order,JsonNode body) {
        var c=SubmissionControl.read(uuid(body,"submissionId"),body);
        equal(c.device(),original.device(),"冻结原设备不符");equal(text(body.path("source"),"kind"),original.source(),"冻结来源不符");
        equal(original.localOrder(),c.temporary()==null?c.assignment():c.temporary().client(),"原本地订单不符");
        equal(uuid(order,"orderId"),c.temporary()==null?c.order():c.temporary().client(),"原订单不符");
    }
    private static Set<String> fields(JsonNode value) {
        require(value!=null && value.isObject(),"原件必须为JSON对象");var result=new HashSet<String>();value.fieldNames().forEachRemaining(result::add);return result;
    }
    private static JsonNode json(byte[] bytes) { return parse(utf8(bytes)); }
    private static String utf8(byte[] bytes) {
        try { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch(CharacterCodingException error) { throw bad("原件UTF-8无效"); }
    }
    public record Identity(UUID user,UUID device,UUID warehouse,String source,UUID localOrder) { }
    public record Bundle(UUID exportId,Identity original,String publicKey,String keySha256,String archiveSha256,
        JsonNode body,SubmissionControl control,JsonNode independent) { }
}
