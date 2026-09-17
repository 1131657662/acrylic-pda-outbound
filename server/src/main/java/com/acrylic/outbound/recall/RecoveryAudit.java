package com.acrylic.outbound.recall;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Component
public class RecoveryAudit {
    private final JdbcTemplate jdbc;private final com.acrylic.outbound.submissions.SubmissionFiles files;
    public RecoveryAudit(JdbcTemplate jdbc,com.acrylic.outbound.submissions.SubmissionFiles files) { this.jdbc=jdbc;this.files=files; }
    public long validate(UUID device,JsonNode body) {
        var coverage=array(body,"auditCoverage");require(coverage.size()==1,"当前恢复仅接受原设备完整审计");var c=coverage.get(0);
        equal(uuid(c,"originDeviceId"),device,"审计覆盖设备错误");equal(number(c,"afterSeq"),0L,"必须补齐从0开始的完整审计");
        var owners=new HashMap<String,String>();jdbc.query("SELECT BIN_TO_UUID(id),BIN_TO_UUID(operator_id) FROM order_assignment WHERE device_id=?",
            rs->{owners.put(rs.getString(1),rs.getString(2));},Ids.bytes(device));
        var known=new HashMap<Long,String>();jdbc.query("SELECT local_seq,record_sha256 FROM submitted_event WHERE origin_device_id=?",
            rs->{known.put(rs.getLong(1),rs.getString(2));},Ids.bytes(device));
        previousRecoveries(device,known);
        var all=new HashMap<String,String>();var current=new HashSet<String>();array(body,"events").forEach(e->current.add(text(e,"id")));
        long next=1;var events=array(body,"auditEvents");
        for(var e:events) {
            long seq=number(e,"localSeq");equal(seq,next++,"全设备审计存在缺口或乱序");equal(uuid(e,"originDeviceId"),device,"审计来源设备错误");
            equal(text(e,"operatorId"),owners.get(text(e,"assignmentId")),"审计原授权或人员不匹配");
            number(e,"wallMs");number(e,"elapsedMs");require(e.path("boot").isIntegralNumber(),"审计时钟格式错误");
            require(!text(e,"type").isBlank() && text(e,"type").length()<=64 && text(e,"detail").length()<=500,"审计字段无效");
            if(!e.path("captureId").isNull())uuid(e,"captureId");
            uuid(e,"id");require(all.put(text(e,"id"),sha(e))==null,"审计ID重复");
            if(known.containsKey(seq))equal(sha(e),known.get(seq),"已上传审计内容被改写");
            if(text(e,"assignmentId").equals(text(body,"assignmentId")))require(current.contains(text(e,"id")),"本单审计被遗漏");
        }
        equal(number(c,"throughSeq"),next-1,"审计覆盖终点不一致");require(next>1,"缺少停止审计");
        require(known.keySet().stream().mapToLong(Long::longValue).max().orElse(0)<=next-1,"审计终点早于已知记录");
        for(var e:array(body,"events"))equal(sha(e),all.get(text(e,"id")),"本单审计不在完整覆盖内");
        var last=events.get(events.size()-1);equal(text(last,"type"),"RECALL_STOPPED","覆盖终点必须为原机停止事件");
        equal(text(last,"assignmentId"),text(body,"assignmentId"),"停止审计原授权错误");require(last.path("captureId").isNull(),"停止审计不能关联输入");
        equal(text(last,"detail"),text(body,"recoveryPackageId"),"停止事件不属于当前恢复包");return next-1;
    }
    private void previousRecoveries(UUID device,Map<Long,String> known) {
        var hashes=jdbc.queryForList("""
            SELECT p.payload_sha256 FROM recovery_package p INNER JOIN order_change_request r ON r.id=p.request_id
            INNER JOIN order_assignment a ON a.id=r.assignment_id WHERE a.device_id=?
            """,String.class,Ids.bytes(device));
        for(String hash:hashes) for(var event:array(parse(files.read(hash)),"auditEvents")) {
            long seq=number(event,"localSeq");String digest=sha(event),old=known.putIfAbsent(seq,digest);
            require(old==null || old.equals(digest),"服务器既有审计不一致，须恢复核对");
        }
    }
}
