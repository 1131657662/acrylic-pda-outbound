package com.acrylic.outbound.temporary;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.pda.PdaSessions;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TemporaryStore {
    private final JdbcTemplate jdbc;
    public TemporaryStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public long nextSequence(PdaSessions.Principal principal) {
        return Math.addExact(jdbc.queryForObject("SELECT COALESCE(MAX(authorization_epoch),0) FROM temporary_authorization WHERE device_id=? AND user_id=?",
            Long.class,Ids.bytes(principal.deviceId()),Ids.bytes(principal.userId())),1);
    }
    public void insert(TemporaryIssuer.Work work) {
        var b=work.binding();var p=b.principal();var base=work.baseline();var r=work.receipt();
        jdbc.update("""
            INSERT INTO temporary_authorization(id,bundle_id,warehouse_id,device_id,user_id,authorization_epoch,user_auth_version,
              device_version,recovery_epoch,inventory_snapshot_id,mapping_revision,policy_id,issued_at,expires_at,key_id,
              signed_token,manifest_text,manifest_sha256,signed_manifest) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,Ids.bytes(b.id()),Ids.bytes(b.bundle()),Ids.bytes(p.warehouseId()),Ids.bytes(p.deviceId()),Ids.bytes(p.userId()),b.sequence(),
            p.userAuthVersion(),p.deviceVersion(),b.recoveryEpoch(),Ids.bytes(UUID.fromString(base.inventorySnapshotId())),base.mappingRevision(),
            Ids.bytes(UUID.fromString(base.policy().id())),java.sql.Timestamp.from(work.issued()),java.sql.Timestamp.from(work.expires()),work.keyId(),
            r.offlineToken(),r.manifest(),r.manifestSha256(),r.signedManifest());
        for(var part:work.parts()) jdbc.update("INSERT INTO temporary_preparation_part(authorization_id,part_name,compressed) VALUES(?,?,?)",
            Ids.bytes(b.id()),part.descriptor().name(),part.compressed());
    }
    public Stored get(UUID id) {
        var rows=jdbc.query("""
            SELECT BIN_TO_UUID(id),BIN_TO_UUID(bundle_id),BIN_TO_UUID(warehouse_id),BIN_TO_UUID(device_id),BIN_TO_UUID(user_id),
              authorization_epoch,user_auth_version,device_version,recovery_epoch,issued_at,expires_at,signed_token,manifest_text,manifest_sha256,signed_manifest
            FROM temporary_authorization WHERE id=?
            """,(rs,n)->new Stored(UUID.fromString(rs.getString(1)),UUID.fromString(rs.getString(3)),UUID.fromString(rs.getString(4)),UUID.fromString(rs.getString(5)),
                rs.getString(6),rs.getString(7),rs.getString(8),rs.getString(9),rs.getTimestamp(10).toInstant(),rs.getTimestamp(11).toInstant(),
                new TemporaryCommands.Receipt(rs.getString(1),rs.getString(2),rs.getString(12),rs.getString(13),rs.getString(14),rs.getString(15))),Ids.bytes(id));
        if(rows.isEmpty()) throw missing();return rows.getFirst();
    }
    public byte[] content(UUID id,String part) {
        if(!Set.of("inventory","mapping","policy","rules","availability").contains(part)) throw missing();
        var rows=jdbc.queryForList("SELECT compressed FROM temporary_preparation_part WHERE authorization_id=? AND part_name=?",byte[].class,Ids.bytes(id),part);
        if(rows.isEmpty()) throw missing();return rows.getFirst();
    }
    /** A continuation credential can only enter its explicit next-round protocol. */
    public void requireInitialSubmission(UUID authorization) {
        Integer consumed = jdbc.queryForObject("SELECT COUNT(*) FROM temporary_source_continuation_activation WHERE new_authorization_id=?",
            Integer.class, Ids.bytes(authorization));
        if (consumed == null || consumed != 0)
            throw new BusinessException(409,"TEMP_CONTINUATION_INTAKE_UNAVAILABLE","续作资格不能用于临时首轮；请保留原票据等待专用续作协议");
        Integer handover = jdbc.queryForObject("SELECT COUNT(*) FROM temporary_handover_activation WHERE new_authorization_id=?",
            Integer.class, Ids.bytes(authorization));
        if (handover == null || handover != 0)
            throw new BusinessException(409,"TEMP_HANDOVER_INTAKE_UNAVAILABLE","交接资格只能用于专用交接轮次，不能创建临时首轮");
    }
    public TemporaryCommands.Acknowledgement ack(Stored row) {
        var rows=jdbc.query("SELECT activated_at,acknowledged_at,signed_ack FROM temporary_preparation_ack WHERE authorization_id=?",
            (rs,n)->new TemporaryCommands.Acknowledgement(row.id().toString(),row.receipt().bundleId(),rs.getTimestamp(1).toInstant().toString(),
                rs.getTimestamp(2).toInstant().toString(),rs.getString(3)),Ids.bytes(row.id()));
        return rows.isEmpty()?null:rows.getFirst();
    }
    public void requireAckDigest(UUID id,String digest) {
        String original=jdbc.queryForObject("SELECT command_sha256 FROM temporary_preparation_ack WHERE authorization_id=?",String.class,Ids.bytes(id));
        if(!digest.equals(original)) throw new BusinessException(409,"TEMPORARY_ACTIVATION_CONFLICT","已存在不同的临时准备激活证明，请恢复原请求");
    }
    public void acknowledge(Stored row,AckWrite input) {
        jdbc.update("INSERT INTO temporary_preparation_ack(authorization_id,command_sha256,activated_at,acknowledged_at,signed_ack) VALUES(?,?,?,?,?)",
            Ids.bytes(row.id()),input.digest(),java.sql.Timestamp.from(input.command().activatedAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)),
            java.sql.Timestamp.from(input.time()),input.signed());
    }
    public static BusinessException missing() { return new BusinessException(404,"TEMPORARY_PREPARATION_NOT_FOUND","临时准备资料不存在或不属于当前人员和设备"); }
    public record Stored(UUID id,UUID warehouseId,UUID deviceId,UUID userId,String authorizationEpoch,String userVersion,String deviceVersion,
        String recoveryEpoch,Instant issuedAt,Instant expiresAt,TemporaryCommands.Receipt receipt) { }
    public record AckWrite(String digest,TemporaryCommands.Ack command,Instant time,String signed) { }
}
