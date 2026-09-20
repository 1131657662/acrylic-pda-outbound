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
    private final JdbcTemplate jdbc;
    public PdaAuditStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public void accept(PdaSessions.Principal actor,JsonNode row) {
        equal(uuid(row,"originDeviceId"),actor.deviceId(),"当前通道只接受本设备原审计，不可改写或代传其他设备来源");
        var origins=jdbc.query("""
            SELECT BIN_TO_UUID(a.operator_id),a.recovery_epoch FROM order_assignment a
            INNER JOIN outbound_order o ON o.id=a.order_id WHERE a.id=? AND a.device_id=? AND o.warehouse_id=?
            """,(rs,n)->new Origin(rs.getString(1),rs.getString(2)),Ids.bytes(uuid(row,"assignmentId")),Ids.bytes(actor.deviceId()),Ids.bytes(actor.warehouseId()));
        require(origins.size()==1,"审计原授权不属于当前设备和仓库");
        var origin=origins.getFirst();equal(text(row,"operatorId"),origin.operator(),"审计原操作员与授权不一致");
        PdaAuditFacts.verify(jdbc,row);
        jdbc.update("""
            INSERT INTO pda_audit_event VALUES(?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(3))
            ON DUPLICATE KEY UPDATE id=id
            """,Ids.bytes(uuid(row,"id")),Ids.bytes(actor.deviceId()),number(row,"localSeq"),Ids.bytes(actor.warehouseId()),
            Ids.bytes(uuid(row,"assignmentId")),Ids.bytes(uuid(row,"operatorId")),origin.epoch(),sha(row),canonical(row),Ids.bytes(actor.userId()));
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
    private record Origin(String operator,String epoch) { }
}
