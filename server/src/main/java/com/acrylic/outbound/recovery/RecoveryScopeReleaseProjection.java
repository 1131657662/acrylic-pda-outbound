package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** One immutable scope projection; callers supply a freshly inspected physical context in the same transaction. */
public final class RecoveryScopeReleaseProjection {
    private final DataSource database;
    private final JdbcTemplate jdbc;
    private final RecoveryScopeReleaseStore store;
    private final boolean requireExisting;
    public RecoveryScopeReleaseProjection(DataSource database,RecoveryScopeReleaseStore store,boolean requireExisting) {
        this.database=Objects.requireNonNull(database); jdbc=new JdbcTemplate(database);
        this.store=Objects.requireNonNull(store); this.requireExisting=requireExisting;
    }
    public void apply(RecoveryOperationsApproval.Context current,RecoveryClosedAuthority closed) {
        require(TransactionSynchronizationManager.isActualTransactionActive() && TransactionSynchronizationManager.hasResource(database),
            "RECOVERY_RELEASE_TRANSACTION_REQUIRED");
        var approval=store.approval(); approval.revalidate(current,closed);
        new RecoveryClosedWriteProjection(jdbc).requireMatches(closed.control());
        boolean externalCommitted=store.inspect(); var expected=store.intent().commit(); var rows=read(expected);
        require(rows.size()<=1,"RECOVERY_RELEASE_PROJECTION_CONFLICT");
        if(rows.isEmpty()) {
            require(!externalCommitted && !requireExisting,"RECOVERY_RELEASE_PROJECTION_MISSING"); insert(expected);
        }
        match(expected,read(expected)); var all=all();
        require(all.size()==1,"RECOVERY_RELEASE_UNVERIFIED_PROJECTIONS");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void beforeCommit(boolean readOnly) {
                store.inspect(); match(expected,read(expected)); require(all.equals(all()),"RECOVERY_RELEASE_PROJECTION_CHANGED");
            }
        });
    }
    private List<Map<String,Object>> read(JsonNode expected) {
        return jdbc.queryForList("SELECT * FROM recovery_scope_release WHERE command_id=? OR (installation_id=? AND epoch_id=? AND scope_id=?) FOR UPDATE",
            Ids.bytes(uuid(expected,"commandId")),Ids.bytes(uuid(expected.path("control"),"installationId")),
            Ids.bytes(uuid(expected.path("control"),"epochId")),Ids.bytes(uuid(expected,"scopeId")));
    }
    private void insert(JsonNode value) {
        var control=value.path("control"); var intent=store.intent();
        require(jdbc.update("""
            INSERT INTO recovery_scope_release(command_id,installation_id,control_revision,control_sha256,epoch_id,numeric_epoch,
                warehouse_id,scope_id,case_id,authority_sha256,approval_sha256,prepare_sha256,commit_sha256,commit_bytes)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,Ids.bytes(uuid(value,"commandId")),Ids.bytes(uuid(control,"installationId")),revision(control,"controlRevision"),
            bytes(text(control,"controlRecordSha256")),Ids.bytes(uuid(control,"epochId")),revision(control,"transportRecoveryEpoch"),
            Ids.bytes(uuid(value,"warehouseId")),Ids.bytes(uuid(value,"scopeId")),Ids.bytes(uuid(value,"caseId")),
            bytes(text(value,"authoritySha256")),bytes(text(value,"approvalSha256")),bytes(intent.prepareSha256()),
            bytes(intent.commitSha256()),intent.commitBytes())==1,"RECOVERY_RELEASE_INSERT_FAILED");
    }
    private void match(JsonNode expected,List<Map<String,Object>> rows) {
        require(rows.size()==1,"RECOVERY_RELEASE_PROJECTION_MISSING"); var row=rows.getFirst(); var control=expected.path("control");
        for(String field:List.of("commandId","warehouseId","scopeId","caseId"))
            binary(row,snake(field),Ids.bytes(uuid(expected,field)));
        binary(row,"installation_id",Ids.bytes(uuid(control,"installationId"))); binary(row,"epoch_id",Ids.bytes(uuid(control,"epochId")));
        require(((Number)row.get("control_revision")).longValue()==revision(control,"controlRevision")
            && ((Number)row.get("numeric_epoch")).longValue()==revision(control,"transportRecoveryEpoch"),"RECOVERY_RELEASE_PROJECTION_CONFLICT");
        binary(row,"control_sha256",bytes(text(control,"controlRecordSha256")));
        for(String field:List.of("authoritySha256","approvalSha256","prepareSha256")) binary(row,snake(field),bytes(text(expected,field)));
        binary(row,"commit_sha256",bytes(store.intent().commitSha256())); binary(row,"commit_bytes",store.intent().commitBytes());
        require(row.get("recorded_at")!=null,"RECOVERY_RELEASE_PROJECTION_CONFLICT");
    }
    private JsonNode all() {
        var result=com.acrylic.outbound.submissions.SubmissionJson.object().putArray("rows"); long[] total={0};
        jdbc.query("SELECT * FROM recovery_scope_release ORDER BY command_id LIMIT 65537 FOR UPDATE",rs->{
            while(rs.next()) {
                require(result.size()<65536,"RECOVERY_RELEASE_PROJECTION_LIMIT"); var row=result.addArray();
                for(int i=1;i<=rs.getMetaData().getColumnCount();i++) {
                    var bytes=rs.getBytes(i); if(bytes==null) row.addNull(); else {
                        total[0]+=bytes.length; require(total[0]<=16777216,"RECOVERY_RELEASE_PROJECTION_BYTES");
                        row.add(Base64.getEncoder().encodeToString(bytes));
                    }
                }
            } return null;
        }); return result;
    }
    private static String snake(String field) { return field.replaceAll("([A-Z])","_$1").toLowerCase(Locale.ROOT); }
    private static void binary(Map<String,Object> row,String field,byte[] expected) {
        require(row.get(field) instanceof byte[] && Arrays.equals((byte[])row.get(field),expected),"RECOVERY_RELEASE_PROJECTION_CONFLICT");
    }
}
