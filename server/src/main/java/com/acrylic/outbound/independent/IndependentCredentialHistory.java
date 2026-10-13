package com.acrylic.outbound.independent;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Scoped server originals remain private. Only the proof status is sent to a supervisor. */
@Repository
public class IndependentCredentialHistory {
    private final JdbcTemplate jdbc;
    public IndependentCredentialHistory(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Record find(JsonNode original) {
        var rows=jdbc.query("""
            SELECT BIN_TO_UUID(id),BIN_TO_UUID(operator_id),BIN_TO_UUID(device_id),BIN_TO_UUID(warehouse_id),
                device_key_sha256,recovery_epoch,user_auth_version,device_version,issued_at,expires_at,credential_sha256,signed_credential
            FROM independent_capture_credential WHERE id=? AND operator_id=? AND device_id=? AND warehouse_id=?
            """,(rs,n)->new Record(object().put("credentialId",rs.getString(1)).put("operatorId",rs.getString(2))
                .put("deviceId",rs.getString(3)).put("warehouseId",rs.getString(4)).put("deviceKeySha256",rs.getString(5))
                .put("serverRecoveryEpoch",rs.getString(6)).put("userAuthVersion",rs.getString(7)).put("deviceVersion",rs.getString(8))
                .put("issuedAt",rs.getTimestamp(9).toInstant().toString()).put("expiresAt",rs.getTimestamp(10).toInstant().toString())
                .put("credentialSha256",rs.getString(11)),rs.getString(12)),Ids.bytes(uuid(original,"credentialId")),
                Ids.bytes(uuid(original,"operatorId")),Ids.bytes(uuid(original,"deviceId")),Ids.bytes(uuid(original,"warehouseId")));
        return rows.isEmpty()?null:rows.getFirst();
    }
    public String currentSubjectState(JsonNode original) {
        int count=jdbc.queryForObject("""
            SELECT COUNT(*) FROM app_user u INNER JOIN device d ON d.id=?
            WHERE u.id=? AND u.status='ACTIVE' AND u.auth_version=? AND d.status='ACTIVE' AND d.row_version=?
              AND d.warehouse_id=? AND EXISTS(SELECT 1 FROM user_warehouse w WHERE w.user_id=u.id AND w.warehouse_id=d.warehouse_id)
              AND EXISTS(SELECT 1 FROM server_state r WHERE r.id=1 AND r.recovery_epoch=? AND r.service_mode='NORMAL')
              AND EXISTS(SELECT 1 FROM user_role ur INNER JOIN role_permission rp ON rp.role_id=ur.role_id
                INNER JOIN permission p ON p.id=rp.permission_id WHERE ur.user_id=u.id AND p.code='pda:independent')
              AND EXISTS(SELECT 1 FROM user_role ur INNER JOIN role_permission rp ON rp.role_id=ur.role_id
                INNER JOIN permission p ON p.id=rp.permission_id WHERE ur.user_id=u.id AND p.code='pda:scan')
            """,Integer.class,Ids.bytes(uuid(original,"deviceId")),Ids.bytes(uuid(original,"operatorId")),
                text(original,"userAuthVersion"),text(original,"deviceVersion"),Ids.bytes(uuid(original,"warehouseId")),text(original,"serverRecoveryEpoch"));
        return count==1?"UNCHANGED":"CHANGED_OR_UNAVAILABLE";
    }
    public record Record(JsonNode publicClaims,String token) { }
}
