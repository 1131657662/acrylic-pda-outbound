package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** New device plus one immutable signed enrollment record in the archive's owning transaction. */
final class RecoveryRecipientProjection {
    private final DataSource database;
    private final JdbcTemplate jdbc;
    private final RecoveryRecipientAdmission admission;
    RecoveryRecipientProjection(DataSource database,RecoveryRecipientAdmission admission) {
        this.database=database; jdbc=new JdbcTemplate(database); this.admission=admission;
    }
    void requireRelease() {
        RecoveryRecipientAdmission.transaction(database); admission.materials();
        RecoveryClosedOperationContext.requireCurrent(database,admission.store().approval().closed());
        new RecoveryScopeReleaseProjection(database,admission.store(),true).requireCommitted();
    }
    void insertIfAbsent() {
        var rows=records(); if(!rows.isEmpty()) { match(rows); return; }
        var body=admission.body(); Instant now=now();
        require(!instant(body,"requestedAt").isAfter(now) && instant(body,"expiresAt").isAfter(now),"RECOVERY_RECIPIENT_EXPIRED");
        absent(); var recipient=body.path("recipient");
        require(jdbc.update("""
            INSERT INTO device(id,warehouse_id,device_code,name,public_key,key_sha256,status,row_version,created_by,created_at,temporary_order_enabled)
            VALUES(?,?,?,?,?,?,'ACTIVE',1,?,?,FALSE)
            """,Ids.bytes(uuid(recipient,"deviceId")),Ids.bytes(uuid(body,"warehouseId")),text(recipient,"deviceCode"),text(recipient,"name"),
            text(recipient,"publicKeyDer"),text(recipient,"keySha256"),Ids.bytes(uuid(body,"registrarId")),
            java.sql.Timestamp.from(instant(body,"requestedAt")))==1,"RECOVERY_RECIPIENT_DEVICE_INSERT");
        require(jdbc.update("""
            INSERT INTO recovery_recipient_admission(command_id,release_command_id,device_id,user_id,request_sha256,request_bytes,recorded_at)
            VALUES(?,?,?,?,?,?,UTC_TIMESTAMP(3))
            """,Ids.bytes(uuid(body,"commandId")),Ids.bytes(uuid(admission.store().intent().commit(),"commandId")),
            Ids.bytes(uuid(recipient,"deviceId")),Ids.bytes(uuid(recipient,"userId")),bytes(admission.sha256()),admission.signed())==1,
            "RECOVERY_RECIPIENT_AUDIT_INSERT");
    }
    RecoveryRecipientAdmission.Effects effects() {
        return effects(null);
    }
    RecoveryRecipientAdmission.Effects effects(RecoveryRecipientSessionEffects session) {
        var records=records();
        if(records.isEmpty()) { absent(); return null; }
        match(records); var devices=new RecoveryReleaseDatabaseRows(jdbc).read();
        RecoveryRecipientDeviceEffect.before(session==null?devices:session.before(devices),admission.body());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void beforeCommit(boolean readOnly) {
                requireRelease(); require(records.equals(records()),"RECOVERY_RECIPIENT_AUDIT_CHANGED");
                var current=new RecoveryReleaseDatabaseRows(jdbc).read();
                RecoveryRecipientDeviceEffect.before(session==null?current:session.before(current),admission.body());
            }
        });
        return new RecoveryRecipientAdmission.Effects(this,session);
    }
    JsonNode before(JsonNode tables) {
        requireRelease(); match(records()); return RecoveryRecipientDeviceEffect.before(tables,admission.body());
    }
    private void absent() {
        var recipient=admission.body().path("recipient");
        require(jdbc.queryForList("SELECT id FROM device WHERE id=? OR device_code=? OR key_sha256=? FOR UPDATE",
            Ids.bytes(uuid(recipient,"deviceId")),text(recipient,"deviceCode"),text(recipient,"keySha256")).isEmpty(),"RECOVERY_RECIPIENT_DEVICE_ALREADY_EXISTS");
    }
    private List<Map<String,String>> records() {
        var table=com.acrylic.outbound.submissions.SubmissionJson.object(); var columns=table.putArray("columns"); var rows=table.putArray("rows");
        jdbc.query("SELECT * FROM recovery_recipient_admission ORDER BY command_id LIMIT 2 FOR UPDATE",rs->{
            var metadata=rs.getMetaData(); for(int i=1;i<=metadata.getColumnCount();i++) columns.add(metadata.getColumnLabel(i));
            while(rs.next()) {
                var row=rows.addArray();
                for(int i=1;i<=metadata.getColumnCount();i++) {
                    byte[] value=rs.getBytes(i); if(value==null) row.addNull(); else row.add(Base64.getEncoder().encodeToString(value));
                }
            } return null;
        }); return RecoveryReleaseRows.decode(table);
    }
    private void match(List<Map<String,String>> records) {
        require(records.size()==1,"RECOVERY_RECIPIENT_UNVERIFIED_ADMISSIONS"); var row=records.getFirst(); var body=admission.body();
        require(row.keySet().equals(Set.of("command_id","release_command_id","device_id","user_id","request_sha256","request_bytes","recorded_at")),
            "RECOVERY_RECIPIENT_AUDIT_COLUMNS");
        RecoveryReleaseRows.uuid(row,"command_id",text(body,"commandId"));
        RecoveryReleaseRows.uuid(row,"release_command_id",text(admission.store().intent().commit(),"commandId"));
        RecoveryReleaseRows.uuid(row,"device_id",text(body.path("recipient"),"deviceId"));
        RecoveryReleaseRows.uuid(row,"user_id",text(body.path("recipient"),"userId"));
        require(Objects.equals(row.get("request_sha256"),RecoveryReleaseRows.hashBytes(admission.sha256()))
            && Arrays.equals(RecoveryReleaseRows.bytes(row.get("request_bytes")),admission.signed()),"RECOVERY_RECIPIENT_IDEMPOTENCY_CONFLICT");
        Instant recorded=RecoveryReleaseRows.time(row,"recorded_at");
        require(!recorded.isBefore(instant(body,"requestedAt")) && recorded.isBefore(instant(body,"expiresAt"))
            && !recorded.isAfter(now()),"RECOVERY_RECIPIENT_AUDIT_TIME");
    }
    private Instant now() { return jdbc.queryForObject("SELECT UTC_TIMESTAMP(3)",java.sql.Timestamp.class).toInstant(); }
}
