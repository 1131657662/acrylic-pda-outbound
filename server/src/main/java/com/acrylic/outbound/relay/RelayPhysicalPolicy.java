package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.inventory.ImportFiles;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Physical observations are complete evidence; they never decide stock availability. */
final class RelayPhysicalPolicy {
    static final int MAX_BYTES=10*1024*1024;
    private RelayPhysicalPolicy() { }
    static void bytes(byte[] bytes,String digest) {
        if(bytes.length<1 || bytes.length>MAX_BYTES)throw invalid("单附件须为1字节至10MiB");
        if(digest==null || !digest.matches("[0-9a-f]{64}") || !ImportFiles.sha256(bytes).equals(digest))
            throw invalid("附件实际字节与摘要不符");
    }
    static void validate(JsonNode body) {
        exact(body,"contextSha256 checkedAt area reason packages attachments");
        if(!text(body,"contextSha256").matches("[0-9a-f]{64}"))throw invalid("核对摘要无效");
        sentence(body,"area",200);sentence(body,"reason",500);
        var packages=body.path("packages");var attachments=body.path("attachments");
        if(!packages.isArray() || packages.size()>10000 || !attachments.isArray() || attachments.isEmpty() || attachments.size()>5)
            throw invalid("实物包集合或附件数量无效");
        var ids=new HashSet<UUID>();
        for(var row:packages) {
            exact(row,"captureId status note");if(!ids.add(identifier(row,"captureId")))throw invalid("实物包重复");
            if(!Set.of("LOADED","UNLOADED","NOT_FOUND").contains(text(row,"status")))throw invalid("实物观察状态无效");
            if(!row.path("note").isTextual() || text(row,"note").length()>500
                || !text(row,"status").equals("LOADED") && text(row,"note").isBlank())throw invalid("缺少异常实物说明");
        }
        var seen=new HashSet<UUID>();long total=0;
        for(var row:attachments) {
            exact(row,"attachmentId evidenceSha256 sizeBytes");if(!seen.add(identifier(row,"attachmentId")))throw invalid("附件重复");
            if(!text(row,"evidenceSha256").matches("[0-9a-f]{64}") || !text(row,"sizeBytes").matches("[1-9][0-9]{0,7}"))throw invalid("附件引用无效");
            long size=Long.parseLong(text(row,"sizeBytes"));if(size>MAX_BYTES || (total+=size)>50L*1024*1024)throw invalid("附件容量超限");
        }
    }
    static void complete(JsonNode body,JsonNode context,UUID actor,Instant now) {
        if(text(context.path("original"),"operatorId").equals(actor.toString()))
            throw new BusinessException(403,"INDEPENDENT_PHYSICAL_SELF_CHECK","原采集者不得独自登记本任务的主管实物核对");
        if(!sha(context).equals(text(body,"contextSha256")))throw new BusinessException(409,"INDEPENDENT_REVIEW_CONTEXT_CHANGED","完整核对依据已变化，请重新核对");
        var expected=new HashSet<String>();context.path("inventory").forEach(row->expected.add(text(row,"captureId")));
        var actual=new HashSet<String>();body.path("packages").forEach(row->actual.add(text(row,"captureId")));
        if(!actual.equals(expected))throw invalid("实物检查须恰好覆盖全部原有效包，VOID不得混入");
        Instant checked;
        try { checked=Instant.parse(text(body,"checkedAt")); }
        catch(DateTimeException error) { throw invalid("实物检查时间无效"); }
        if(checked.isBefore(now.minus(Duration.ofDays(1))) || checked.isAfter(now.plusSeconds(120)))throw invalid("实物检查时间已过期或超未来");
    }
    static UUID identifier(JsonNode row,String field) {
        try { var id=UUID.fromString(text(row,field));if(!id.toString().equals(text(row,field)))throw invalid("标识格式无效");return id; }
        catch(IllegalArgumentException error) { throw invalid("标识格式无效"); }
    }
    static void exact(JsonNode row,String fields) {
        var names=new HashSet<String>();row.fieldNames().forEachRemaining(names::add);
        if(!row.isObject() || !names.equals(Set.of(fields.split(" "))))throw invalid("实物证据字段不符");
    }
    private static void sentence(JsonNode row,String field,int maximum) {
        if(!row.path(field).isTextual() || text(row,field).isBlank() || text(row,field).length()>maximum)throw invalid("检查责任区及说明不能为空或超长");
    }
    static BusinessException invalid(String message) { return new BusinessException(400,"INVALID_INDEPENDENT_PHYSICAL_EVIDENCE",message); }
}
