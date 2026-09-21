package com.acrylic.outbound.audit;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.pda.PdaSessions;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Repository
public class PdaAuditStore {
    private final JdbcTemplate jdbc;private final PdaAuditOrigins origins;
    public PdaAuditStore(JdbcTemplate jdbc,PdaAuditOrigins origins) { this.jdbc=jdbc;this.origins=origins; }
    public Set<UUID> users(PdaSessions.Principal actor,JsonNode events) {
        var users=new HashSet<UUID>();users.add(actor.userId());
        for(var row:events) users.add(UUID.fromString(origins.verify(actor.deviceId(),actor.warehouseId(),row).operator()));
        return users;
    }
    public void accept(PdaSessions.Principal actor,JsonNode row) {
        equal(uuid(row,"originDeviceId"),actor.deviceId(),"当前通道只接受本设备原审计，不可改写或代传其他设备来源");
        var origin=origins.verify(actor.deviceId(),actor.warehouseId(),row);origins.bind(actor.deviceId(),origin);
        PdaAuditFacts.verify(jdbc,row);
        jdbc.update("""
            INSERT INTO pda_audit_event(id,origin_device_id,local_seq,warehouse_id,assignment_id,operator_id,
                source_recovery_epoch,record_sha256,payload,uploaded_by,received_at,temporary_authorization_id,client_order_id)
            VALUES(?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(3),?,?)
            ON DUPLICATE KEY UPDATE id=id
            """,Ids.bytes(uuid(row,"id")),Ids.bytes(actor.deviceId()),number(row,"localSeq"),Ids.bytes(actor.warehouseId()),
            bytes(origin.assignment()),Ids.bytes(uuid(row,"operatorId")),origin.epoch(),sha(row),canonical(row),Ids.bytes(actor.userId()),
            bytes(origin.authorization()),bytes(origin.client()));
        // Another device can race on the globally unique event UUID; verify the winning row after the insert lock.
        PdaAuditFacts.verify(jdbc,row);
    }
    public long advance(String epoch,UUID device) {
        byte[] id=Ids.bytes(device);
        jdbc.update("INSERT INTO pda_audit_checkpoint VALUES(?,?,0) ON DUPLICATE KEY UPDATE through_seq=through_seq",epoch,id);
        long through=jdbc.queryForObject("SELECT through_seq FROM pda_audit_checkpoint WHERE recovery_epoch=? AND origin_device_id=? FOR UPDATE",Long.class,epoch,id);
        var sequences=jdbc.queryForList("SELECT local_seq FROM pda_audit_event WHERE origin_device_id=? AND local_seq>? ORDER BY local_seq LIMIT 2000",Long.class,id,through);
        for(long sequence:sequences) { if(sequence!=Math.addExact(through,1)) break;through=sequence; }
        jdbc.update("UPDATE pda_audit_checkpoint SET through_seq=? WHERE recovery_epoch=? AND origin_device_id=?",through,epoch,id);
        return through;
    }
    private static byte[] bytes(UUID id) { return id==null?null:Ids.bytes(id); }
}
