package com.acrylic.outbound.audit;

import com.acrylic.outbound.common.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Original UUID and device sequence identify the same immutable event across all upload paths. */
public final class PdaAuditFacts {
    private PdaAuditFacts() { }
    public static void validate(JsonNode row) {
        var fields=new java.util.HashSet<>(java.util.Set.of("id","captureId","originDeviceId","operatorId","localSeq","type","detail","wallMs","elapsedMs","boot"));
        if(row.has("source")) { fields.add("source");fields.add("authorizationId");temporary(row); }
        else { fields.add("assignmentId");uuid(row,"assignmentId"); }
        require(row.isObject() && row.size()==fields.size(),"审计字段不完整或包含未知字段");
        row.fieldNames().forEachRemaining(field->require(fields.contains(field),"审计字段无效"));
        uuid(row,"id");uuid(row,"originDeviceId");uuid(row,"operatorId");
        require(number(row,"localSeq")>0,"审计序号必须为正数");number(row,"wallMs");number(row,"elapsedMs");
        require(row.path("boot").isIntegralNumber() && row.path("boot").canConvertToInt(),"审计启动标识无效");
        require(text(row,"type").matches("[A-Z][A-Z0-9_]{0,63}") && text(row,"detail").length()<=500,"审计类型或详情无效");
        if(!row.path("captureId").isNull()) uuid(row,"captureId");
    }
    private static void temporary(JsonNode row) {
        var source=row.path("source");require(source.isObject() && source.size()==3,"临时审计来源字段无效");
        equal(text(source,"kind"),"TEMP","审计来源类型无效");
        equal(uuid(source,"creatorDeviceId"),uuid(row,"originDeviceId"),"临时审计原设备不匹配");
        uuid(source,"clientOrderId");uuid(row,"authorizationId");
    }
    public static void verify(JdbcTemplate jdbc,JsonNode row) {
        String digest=sha(row);
        for(String table:java.util.List.of("pda_audit_event","submitted_event")) {
            var hashes=jdbc.queryForList("SELECT record_sha256 FROM "+table+" WHERE id=? UNION ALL SELECT record_sha256 FROM "+table+" WHERE origin_device_id=? AND local_seq=?",
                String.class,Ids.bytes(uuid(row,"id")),Ids.bytes(uuid(row,"originDeviceId")),number(row,"localSeq"));
            if(hashes.stream().anyMatch(hash->!hash.equals(digest)))
                throw new BusinessException(409,"AUDIT_CONTENT_CONFLICT","原审计ID或设备序号对应的内容不同，请保留原记录并核对");
        }
    }
}
