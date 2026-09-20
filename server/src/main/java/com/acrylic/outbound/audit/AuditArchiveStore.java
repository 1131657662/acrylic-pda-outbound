package com.acrylic.outbound.audit;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.submissions.SubmissionArchiveProofs;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Repository
public class AuditArchiveStore {
    private final JdbcTemplate jdbc;
    public AuditArchiveStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public JsonNode find(String epoch,UUID device,long after) {
        var rows=jdbc.query("SELECT through_seq,payload_sha256,proof_text FROM pda_audit_archive WHERE recovery_epoch=? AND origin_device_id=? AND after_seq=?",
            (rs,n)->{
                try {
                    var proof=parse(rs.getString(3));equal(number(proof,"throughSeq"),rs.getLong(1),"归档索引终点错误");
                    equal(text(proof,"payloadSha256"),rs.getString(2),"归档索引摘要错误");equal(text(proof,"recoveryEpoch"),epoch,"归档索引代次错误");
                    equal(uuid(proof,"originDeviceId"),device,"归档索引设备错误");equal(number(proof,"afterSeq"),after,"归档索引起点错误");return proof;
                } catch(RuntimeException error) { throw SubmissionArchiveProofs.unavailable(); }
            },epoch,Ids.bytes(device),after);
        return rows.isEmpty()?null:rows.getFirst();
    }
    public void requireNext(String epoch,UUID device,long after) {
        var rows=jdbc.query("SELECT after_seq,through_seq,proof_text FROM pda_audit_archive WHERE recovery_epoch=? AND origin_device_id=? ORDER BY after_seq LIMIT 1025",
            (rs,n)->{
                try {
                    var proof=parse(rs.getString(3));
                    equal(number(proof,"afterSeq"),rs.getLong(1),"归档索引起点错误");
                    equal(number(proof,"throughSeq"),rs.getLong(2),"归档索引终点错误");
                    return new long[]{rs.getLong(1),rs.getLong(2)};
                } catch(RuntimeException error) { throw SubmissionArchiveProofs.unavailable(); }
            },epoch,Ids.bytes(device));
        require(rows.size()<1024,"审计归档段超过当前资源限额，请保留原记录联系管理员");
        long next=0;
        for(var row:rows) {
            if(row[0]!=next || row[1]<=row[0] || row[1]-row[0]>200) throw SubmissionArchiveProofs.unavailable();
            next=row[1];
        }
        if(after!=next) throw new BusinessException(409,"AUDIT_ARCHIVE_GAP","须先确认此前全部连续审计段");
    }
    public ObjectNode payload(UUID device,UUID warehouse,JsonNode request) {
        long after=number(request,"afterSeq"),through=number(request,"throughSeq");
        var body=object().put("originDeviceId",device.toString()).put("afterSeq",Long.toString(after)).put("throughSeq",Long.toString(through));
        var events=body.putArray("events");jdbc.query("""
            SELECT payload,record_sha256,BIN_TO_UUID(warehouse_id) FROM pda_audit_event
            WHERE origin_device_id=? AND local_seq>? AND local_seq<=? ORDER BY local_seq
            """,rs->{
                var row=parse(rs.getString(1));equal(sha(row),rs.getString(2),"原审计摘要损坏");
                equal(rs.getString(3),warehouse.toString(),"原审计仓库错误");events.add(row);
            },Ids.bytes(device),after,through);
        AuditArchiveReader.validateEvents(events,device,after,through);return body;
    }
    public void insert(String epoch,UUID device,JsonNode proof) {
        jdbc.update("INSERT INTO pda_audit_archive VALUES(?,?,?,?,?,?)",epoch,Ids.bytes(device),number(proof,"afterSeq"),
            number(proof,"throughSeq"),text(proof,"payloadSha256"),canonical(proof));
    }
}
