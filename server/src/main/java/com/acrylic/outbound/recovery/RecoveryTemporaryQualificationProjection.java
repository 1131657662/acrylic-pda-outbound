package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.inventory.master.WarehouseBaseline;
import com.acrylic.outbound.policies.PolicyStore;
import com.acrylic.outbound.temporary.TemporaryBaseline;
import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Only one exact pinned qualification is added; all original business rows remain untouched. */
final class RecoveryTemporaryQualificationProjection {
    private final DataSource database;
    private final JdbcTemplate jdbc;
    private final RecoveryTemporaryQualification qualification;
    RecoveryTemporaryQualificationProjection(DataSource database,RecoveryTemporaryQualification qualification) {
        this.database=database; jdbc=new JdbcTemplate(database); this.qualification=qualification;
    }
    void requireBaseline() {
        RecoveryRecipientAdmission.transaction(database);
        RecoveryClosedOperationContext.requireCurrent(database,qualification.recipient().store().approval().closed());
        qualification.materials(); var facts=qualification.facts();
        var current=new TemporaryBaseline(new WarehouseBaseline(jdbc),new PolicyStore(jdbc),jdbc).read(uuid(facts,"warehouseId"));
        var expected=com.acrylic.outbound.submissions.SubmissionJson.object().put("inventorySnapshotId",current.inventorySnapshotId())
            .put("inventoryVersion",current.inventoryVersion()).put("inventorySha256",current.inventorySha256())
            .put("mappingRevision",current.mappingRevision()).put("policyId",current.policy().id()).put("policyVersion",current.policy().version());
        require(expected.equals(facts.path("baseline")),"RECOVERY_QUALIFICATION_BASELINE_CHANGED");
    }
    void issue(boolean retainedOnly) {
        var records=records();
        if(records.isEmpty()) {
            require(!retainedOnly,"RECOVERY_QUALIFICATION_RETAINED_REQUIRED"); insert();
        }
        var retained=records(); match(retained);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void beforeCommit(boolean readOnly) {
                requireBaseline(); require(retained.equals(records()),"RECOVERY_QUALIFICATION_AUDIT_CHANGED"); match(records());
            }
        });
    }
    private void insert() {
        var facts=qualification.facts(); Instant now=now(),issued=instant(facts,"requestedAt");
        require(!issued.isAfter(now) && !issued.isBefore(now.minusSeconds(300)) && instant(facts,"expiresAt").isAfter(now),
            "RECOVERY_QUALIFICATION_EXPIRED");
        require(jdbc.update("""
            INSERT INTO recovery_recipient_qualification(command_id,qualification_id,admission_command_id,warehouse_id,scope_id,case_id,
              source_recovery_epoch,target_recovery_epoch,request_sha256,request_bytes,issued_at,expires_at,recorded_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(3))
            """,Ids.bytes(uuid(facts,"commandId")),Ids.bytes(uuid(facts,"qualificationId")),
            Ids.bytes(uuid(qualification.recipient().facts(),"commandId")),Ids.bytes(uuid(facts,"warehouseId")),
            Ids.bytes(uuid(facts,"scopeId")),Ids.bytes(uuid(facts,"caseId")),revision(facts.path("source"),"sourceRecoveryEpoch"),
            revision(facts.path("control"),"transportRecoveryEpoch"),bytes(qualification.sha256()),qualification.signedBytes(),
            java.sql.Timestamp.from(issued),java.sql.Timestamp.from(instant(facts,"expiresAt")))==1,"RECOVERY_QUALIFICATION_INSERT");
    }
    private List<Map<String,String>> records() {
        var table=com.acrylic.outbound.submissions.SubmissionJson.object(); var columns=table.putArray("columns"); var rows=table.putArray("rows");
        jdbc.query("SELECT * FROM recovery_recipient_qualification ORDER BY command_id LIMIT 2 FOR UPDATE",rs->{
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
        require(records.size()==1,"RECOVERY_QUALIFICATION_UNVERIFIED_ADDITIONS"); var row=records.getFirst(); var facts=qualification.facts();
        require(row.keySet().equals(Set.of("command_id","qualification_id","admission_command_id","warehouse_id","scope_id","case_id",
            "source_recovery_epoch","target_recovery_epoch","request_sha256","request_bytes","issued_at","expires_at","recorded_at")),
            "RECOVERY_QUALIFICATION_AUDIT_COLUMNS");
        for(var field:Map.of("command_id","commandId","qualification_id","qualificationId","warehouse_id","warehouseId","scope_id","scopeId","case_id","caseId").entrySet())
            RecoveryReleaseRows.uuid(row,field.getKey(),text(facts,field.getValue()));
        RecoveryReleaseRows.uuid(row,"admission_command_id",text(qualification.recipient().facts(),"commandId"));
        RecoveryReleaseRows.eq(row,"source_recovery_epoch",text(facts.path("source"),"sourceRecoveryEpoch"));
        RecoveryReleaseRows.eq(row,"target_recovery_epoch",text(facts.path("control"),"transportRecoveryEpoch"));
        require(Objects.equals(row.get("request_sha256"),RecoveryReleaseRows.hashBytes(qualification.sha256()))
            && Arrays.equals(RecoveryReleaseRows.bytes(row.get("request_bytes")),qualification.signedBytes()),"RECOVERY_QUALIFICATION_IDEMPOTENCY_CONFLICT");
        require(RecoveryReleaseRows.time(row,"issued_at").equals(instant(facts,"requestedAt"))
            && RecoveryReleaseRows.time(row,"expires_at").equals(instant(facts,"expiresAt")),"RECOVERY_QUALIFICATION_AUDIT_WINDOW");
        Instant recorded=RecoveryReleaseRows.time(row,"recorded_at");
        require(!recorded.isBefore(instant(facts,"requestedAt")) && recorded.isBefore(instant(facts,"expiresAt"))
            && !recorded.isAfter(now()),"RECOVERY_QUALIFICATION_AUDIT_TIME");
    }
    private Instant now() { return jdbc.queryForObject("SELECT UTC_TIMESTAMP(3)",java.sql.Timestamp.class).toInstant(); }
}
