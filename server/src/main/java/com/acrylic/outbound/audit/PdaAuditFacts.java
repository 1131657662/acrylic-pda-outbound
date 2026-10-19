package com.acrylic.outbound.audit;

import com.acrylic.outbound.common.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
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
        verifyAll(jdbc,List.of(row));
    }
    public static void verifyAll(JdbcTemplate jdbc,Iterable<JsonNode> input) {
        var rows=new ArrayList<JsonNode>();input.forEach(rows::add);if(rows.isEmpty())return;
        var byId=new HashMap<String,String>();var byOrigin=new HashMap<String,String>();
        for(var row:rows) {
            String digest=sha(row),id=uuid(row,"id").toString(),origin=uuid(row,"originDeviceId").toString();
            byId.put(id,digest);byOrigin.put(originKey(origin,number(row,"localSeq")),digest);
        }
        var origins=new LinkedHashMap<String,List<JsonNode>>();
        for(var row:rows) origins.computeIfAbsent(uuid(row,"originDeviceId").toString(),key->new ArrayList<>()).add(row);
        for(String table:List.of("pda_audit_event","submitted_event")) { verifyIds(jdbc,table,rows,byId);verifyOrigins(jdbc,table,origins,byOrigin); }
    }
    private static void verifyIds(JdbcTemplate jdbc,String table,List<JsonNode> rows,Map<String,String> byId) {
        for(int start=0;start<rows.size();start+=200) {
            int end=Math.min(start+200,rows.size());var sql="SELECT BIN_TO_UUID(id),record_sha256 FROM "+table+" WHERE id IN ("+marks(end-start)+")";var params=new ArrayList<Object>();
            for(int i=start;i<end;i++)params.add(Ids.bytes(uuid(rows.get(i),"id")));
            jdbc.query(sql,(org.springframework.jdbc.core.RowCallbackHandler)rs->match(byId.get(rs.getString(1)),rs.getString(2)),params.toArray());
        }
    }
    private static void verifyOrigins(JdbcTemplate jdbc,String table,Map<String,List<JsonNode>> origins,Map<String,String> byOrigin) {
        for(var group:origins.entrySet()) for(int start=0;start<group.getValue().size();start+=200) {
            var rows=group.getValue();int end=Math.min(start+200,rows.size());var sql="SELECT local_seq,record_sha256 FROM "+table+" WHERE origin_device_id=? AND local_seq IN ("+marks(end-start)+")";var params=new ArrayList<Object>();params.add(Ids.bytes(UUID.fromString(group.getKey())));
            for(int i=start;i<end;i++)params.add(number(rows.get(i),"localSeq"));
            jdbc.query(sql,(org.springframework.jdbc.core.RowCallbackHandler)rs->match(byOrigin.get(originKey(group.getKey(),rs.getLong(1))),rs.getString(2)),params.toArray());
        }
    }
    private static String marks(int count) { return String.join(",",Collections.nCopies(count,"?")); }
    private static void match(String expected,String actual) {
        if(expected!=null&&!expected.equals(actual))throw new BusinessException(409,"AUDIT_CONTENT_CONFLICT","原审计ID或设备序号对应的内容不同，请保留原记录并核对");
    }
    private static String originKey(String origin,long sequence) { return origin+":"+sequence; }
}
