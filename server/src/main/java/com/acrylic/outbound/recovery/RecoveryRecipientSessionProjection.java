package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Immutable audit of the independently pinned actual login; original credentials are never stored here. */
final class RecoveryRecipientSessionProjection {
    private final JdbcTemplate jdbc;
    private final RecoveryRecipientSession session;
    RecoveryRecipientSessionProjection(DataSource database,RecoveryRecipientSession session) {
        RecoveryRecipientAdmission.transaction(database); jdbc=new JdbcTemplate(database); this.session=session;
    }
    void issue() {
        session.materials(); var records=records(); var facts=session.facts();
        if(records.isEmpty()) {
            require(!session.retainedOnly(),"RECOVERY_SESSION_RETAINED_REQUIRED");
            Instant now=now();
            require(!instant(facts,"requestedAt").isAfter(now) && !instant(facts,"requestedAt").isBefore(now.minusSeconds(300))
                && instant(facts,"expiresAt").isAfter(now),"RECOVERY_SESSION_EXPIRED");
            var login=facts.path("login");
            require(jdbc.update("""
                INSERT INTO recovery_recipient_session(command_id,qualification_id,session_id,challenge_id,case_id,request_sha256,request_bytes,recorded_at)
                VALUES(?,?,?,?,?,?,?,UTC_TIMESTAMP(3))
                """,Ids.bytes(uuid(facts,"commandId")),Ids.bytes(uuid(facts,"qualificationId")),Ids.bytes(uuid(login,"sessionId")),
                Ids.bytes(uuid(login,"challengeId")),Ids.bytes(uuid(facts,"caseId")),bytes(session.sha256()),session.signedBytes())==1,"RECOVERY_SESSION_INSERT");
        }
        var retained=records(); match(retained);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void beforeCommit(boolean readOnly) {
                session.materials(); match(records()); require(retained.equals(records()),"RECOVERY_SESSION_AUDIT_CHANGED");
            }
        });
    }
    void requireRecorded() {
        session.materials(); match(records());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void beforeCommit(boolean readOnly) { session.materials(); match(records()); }
        });
    }
    private List<Map<String,String>> records() {
        var table=com.acrylic.outbound.submissions.SubmissionJson.object(); var columns=table.putArray("columns"); var rows=table.putArray("rows");
        jdbc.query("SELECT * FROM recovery_recipient_session ORDER BY command_id LIMIT 2 FOR UPDATE",rs->{
            var metadata=rs.getMetaData(); for(int i=1;i<=metadata.getColumnCount();i++) columns.add(metadata.getColumnLabel(i));
            while(rs.next()) { var row=rows.addArray(); for(int i=1;i<=metadata.getColumnCount();i++) {
                byte[] value=rs.getBytes(i); if(value==null) row.addNull(); else row.add(Base64.getEncoder().encodeToString(value));
            }} return null;
        }); return RecoveryReleaseRows.decode(table);
    }
    private void match(List<Map<String,String>> rows) {
        require(rows.size()==1,"RECOVERY_SESSION_UNVERIFIED_ADDITIONS"); var row=rows.getFirst(); var facts=session.facts();
        require(row.keySet().equals(Set.of("command_id","qualification_id","session_id","challenge_id","case_id","request_sha256","request_bytes","recorded_at")),"RECOVERY_SESSION_AUDIT_COLUMNS");
        for(String field:List.of("commandId","qualificationId","caseId"))
            RecoveryReleaseRows.uuid(row,field.replaceAll("([a-z])([A-Z])","$1_$2").toLowerCase(Locale.ROOT),text(facts,field));
        RecoveryReleaseRows.uuid(row,"session_id",text(facts.path("login"),"sessionId"));
        RecoveryReleaseRows.uuid(row,"challenge_id",text(facts.path("login"),"challengeId"));
        require(Objects.equals(row.get("request_sha256"),RecoveryReleaseRows.hashBytes(session.sha256()))
            && Arrays.equals(RecoveryReleaseRows.bytes(row.get("request_bytes")),session.signedBytes()),"RECOVERY_SESSION_IDEMPOTENCY_CONFLICT");
        Instant recorded=RecoveryReleaseRows.time(row,"recorded_at");
        require(!recorded.isBefore(instant(facts,"requestedAt")) && recorded.isBefore(instant(facts,"expiresAt")) && !recorded.isAfter(now()),"RECOVERY_SESSION_AUDIT_TIME");
    }
    private Instant now() { return jdbc.queryForObject("SELECT UTC_TIMESTAMP(3)",java.sql.Timestamp.class).toInstant(); }
}
