package com.acrylic.outbound.recall;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.audit.*;
import com.acrylic.outbound.submissions.SubmissionFiles;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Component
public class RecoveryAudit {
    private final JdbcTemplate jdbc;private final SubmissionFiles files;private final AuditArchiveReader archives;
    public RecoveryAudit(JdbcTemplate jdbc,SubmissionFiles files,AuditArchiveReader archives) { this.jdbc=jdbc;this.files=files;this.archives=archives; }
    public long validate(UUID device,UUID warehouse,JsonNode body) {
        var coverage=array(body,"auditCoverage");require(coverage.size()==1,"当前恢复仅接受原设备审计");var c=coverage.get(0);
        equal(uuid(c,"originDeviceId"),device,"审计覆盖设备错误");
        var context=context(device,warehouse,body);long after=archives.prefix(body,device,warehouse,e->validateEvent(e,context));
        long next=after;var events=array(body,"auditEvents");require(!events.isEmpty(),"缺少停止审计");
        for(var e:events) { equal(number(e,"localSeq"),++next,"全设备审计存在缺口或乱序");validateEvent(e,context); }
        equal(number(c,"throughSeq"),next,"审计覆盖终点不一致");
        require(context.known().keySet().stream().mapToLong(Long::longValue).max().orElse(0)<=next,"审计终点早于已知记录");
        for(var e:array(body,"events"))equal(sha(e),context.all().get(text(e,"id")),"本单审计不在完整覆盖内");
        var last=events.get(events.size()-1);equal(text(last,"type"),"RECALL_STOPPED","覆盖终点必须为原机停止事件");
        equal(text(last,"assignmentId"),text(body,"assignmentId"),"停止审计原授权错误");require(last.path("captureId").isNull(),"停止审计不能关联输入");
        equal(text(last,"detail"),text(body,"recoveryPackageId"),"停止事件不属于当前恢复包");return next;
    }
    private Context context(UUID device,UUID warehouse,JsonNode body) {
        var owners=new HashMap<String,String>();jdbc.query("""
            SELECT BIN_TO_UUID(a.id),BIN_TO_UUID(a.operator_id) FROM order_assignment a
            INNER JOIN outbound_order o ON o.id=a.order_id WHERE a.device_id=? AND o.warehouse_id=?
            """,rs->{owners.put(rs.getString(1),rs.getString(2));},Ids.bytes(device),Ids.bytes(warehouse));
        var known=new HashMap<Long,String>();
        for(String table:List.of("submitted_event","pda_audit_event")) jdbc.query("SELECT local_seq,record_sha256 FROM "+table+" WHERE origin_device_id=?",
            rs->{remember(known,rs.getLong(1),rs.getString(2));},Ids.bytes(device));
        previousRecoveries(device,known);
        var current=new HashSet<String>();array(body,"events").forEach(e->current.add(text(e,"id")));
        return new Context(device,text(body,"assignmentId"),owners,known,current,new HashMap<>());
    }
    private void validateEvent(JsonNode e,Context c) {
        PdaAuditFacts.validate(e);PdaAuditFacts.verify(jdbc,e);
        equal(uuid(e,"originDeviceId"),c.device(),"审计来源设备错误");
        equal(text(e,"operatorId"),c.owners().get(text(e,"assignmentId")),"审计原授权或人员不匹配");
        require(c.all().put(text(e,"id"),sha(e))==null,"审计ID重复");long seq=number(e,"localSeq");
        if(c.known().containsKey(seq))equal(sha(e),c.known().get(seq),"已上传审计内容被改写");
        if(text(e,"assignmentId").equals(c.assignment()))require(c.current().contains(text(e,"id")),"本单审计被遗漏");
    }
    public void verifyExisting(UUID device,JsonNode events) {
        var known=new HashMap<Long,String>();previousRecoveries(device,known);
        for(var event:events) if(known.containsKey(number(event,"localSeq")))
            equal(sha(event),known.get(number(event,"localSeq")),"原恢复包审计被改写");
    }
    private void previousRecoveries(UUID device,Map<Long,String> known) {
        var rows=jdbc.query("""
            SELECT p.payload_sha256,BIN_TO_UUID(o.warehouse_id) FROM recovery_package p
            INNER JOIN order_change_request r ON r.id=p.request_id INNER JOIN order_assignment a ON a.id=r.assignment_id
            INNER JOIN outbound_order o ON o.id=a.order_id WHERE a.device_id=?
            """,(rs,n)->new Prior(rs.getString(1),UUID.fromString(rs.getString(2))),Ids.bytes(device));
        for(var row:rows) {
            var body=parse(files.read(row.hash()));
            archives.prefix(body,device,row.warehouse(),event->remember(known,number(event,"localSeq"),sha(event)));
            for(var event:array(body,"auditEvents")) remember(known,number(event,"localSeq"),sha(event));
        }
    }
    private static void remember(Map<Long,String> known,long sequence,String hash) {
        String old=known.putIfAbsent(sequence,hash);require(old==null || old.equals(hash),"服务器既有审计不一致，须恢复核对");
    }
    private record Prior(String hash,UUID warehouse) { }
    private record Context(UUID device,String assignment,Map<String,String> owners,Map<Long,String> known,Set<String> current,Map<String,String> all) { }
}
