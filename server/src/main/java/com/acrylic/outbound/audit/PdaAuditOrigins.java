package com.acrylic.outbound.audit;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Evidence keeps the original authorization even after expiry or permission changes. */
@Component
public class PdaAuditOrigins {
    private final JdbcTemplate jdbc;
    public PdaAuditOrigins(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Origin verify(UUID device,UUID warehouse,JsonNode row) {
        equal(uuid(row,"originDeviceId"),device,"审计原设备不匹配");
        return row.has("source")?temporary(device,warehouse,row):formal(device,warehouse,row);
    }
    private Origin formal(UUID device,UUID warehouse,JsonNode row) {
        var id=uuid(row,"assignmentId");
        var origins=jdbc.query("""
            SELECT BIN_TO_UUID(a.operator_id),a.recovery_epoch FROM order_assignment a
            INNER JOIN outbound_order o ON o.id=a.order_id WHERE a.id=? AND a.device_id=? AND o.warehouse_id=?
            """,(rs,n)->new Origin(id,null,null,rs.getString(1),rs.getString(2)),Ids.bytes(id),Ids.bytes(device),Ids.bytes(warehouse));
        require(origins.size()==1,"审计原授权不属于当前设备和仓库");
        var origin=origins.getFirst();equal(text(row,"operatorId"),origin.operator(),"审计原操作员与授权不一致");return origin;
    }
    private Origin temporary(UUID device,UUID warehouse,JsonNode row) {
        var source=row.get("source");equal(uuid(source,"creatorDeviceId"),device,"临时审计创建设备不匹配");
        UUID id=uuid(row,"authorizationId"),client=uuid(source,"clientOrderId");
        var origins=jdbc.query("""
            SELECT BIN_TO_UUID(a.user_id),a.recovery_epoch FROM temporary_authorization a
            WHERE a.id=? AND a.device_id=? AND a.warehouse_id=?
            """,(rs,n)->new Origin(null,id,client,rs.getString(1),rs.getString(2)),Ids.bytes(id),Ids.bytes(device),Ids.bytes(warehouse));
        require(origins.size()==1,"临时审计原资格不属于当前设备和仓库");
        var origin=origins.getFirst();equal(text(row,"operatorId"),origin.operator(),"临时审计原操作员与资格不一致");
        var old=jdbc.queryForList("SELECT BIN_TO_UUID(authorization_id) FROM temporary_work_origin WHERE creator_device_id=? AND client_order_id=?",
            String.class,Ids.bytes(device),Ids.bytes(client));
        require(old.isEmpty() || old.getFirst().equals(id.toString()),"临时作业不得替换原资格来源");return origin;
    }
    public void bind(UUID device,Origin origin) {
        if(origin.authorization()==null)return;
        jdbc.update("INSERT INTO temporary_work_origin VALUES(?,?,?) ON DUPLICATE KEY UPDATE authorization_id=authorization_id",
            Ids.bytes(device),Ids.bytes(origin.client()),Ids.bytes(origin.authorization()));
        equal(jdbc.queryForObject("SELECT BIN_TO_UUID(authorization_id) FROM temporary_work_origin WHERE creator_device_id=? AND client_order_id=?",
            String.class,Ids.bytes(device),Ids.bytes(origin.client())),origin.authorization().toString(),"临时作业原资格来源冲突");
    }
    public record Origin(UUID assignment,UUID authorization,UUID client,String operator,String epoch) { }
}
