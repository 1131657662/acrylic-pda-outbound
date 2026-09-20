package com.acrylic.outbound.temporary;

import com.acrylic.outbound.audit.PdaAuditOrigins;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.submissions.*;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Component
public class TemporarySubmissionControls {
    private final JdbcTemplate jdbc;private final PdaAuditOrigins origins;
    public TemporarySubmissionControls(JdbcTemplate jdbc,PdaAuditOrigins origins) { this.jdbc=jdbc;this.origins=origins; }
    public void lock(SubmissionControl c,SubmissionAccess.Actor actor) {
        var t=c.temporary();origins.bind(t.creator(),new PdaAuditOrigins.Origin(null,t.authorization(),t.client(),actor.user().toString(),c.epoch()));
        var scopes=jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM submission_scope WHERE creator_device_id=? AND client_order_id=? FOR UPDATE",
            String.class,Ids.bytes(t.creator()),Ids.bytes(t.client()));
        UUID scope=scopes.isEmpty()?Ids.next():UUID.fromString(scopes.getFirst());
        if(scopes.isEmpty())jdbc.update("INSERT INTO submission_scope(id,warehouse_id,creator_device_id,client_order_id) VALUES(?,?,?,?)",
            Ids.bytes(scope),Ids.bytes(actor.warehouse()),Ids.bytes(t.creator()),Ids.bytes(t.client()));
        var target=jdbc.queryForObject("SELECT BIN_TO_UUID(canonical_order_id) FROM submission_scope WHERE id=?",String.class,Ids.bytes(scope));
        if(target!=null)jdbc.queryForList("SELECT id FROM outbound_order WHERE id=UUID_TO_BIN(?) FOR UPDATE",target);
        var existing=jdbc.queryForList("SELECT id FROM submission WHERE id=? FOR UPDATE",byte[].class,Ids.bytes(c.id()));
        if(!existing.isEmpty())return; // SubmissionStore compares the full immutable identity after this lock.
        var selected=jdbc.queryForObject("SELECT BIN_TO_UUID(current_submission_id) FROM submission_scope WHERE id=?",String.class,Ids.bytes(scope));
        if(selected!=null)throw new BusinessException(409,"CURRENT_SUBMISSION_EXISTS","临时作业已有批次，须恢复原批次");
        jdbc.update("""
            INSERT INTO submission(id,scope_id,freeze_device_id,operator_id,work_round,source_recovery_epoch,status,created_at,updated_at,temporary_authorization_id)
            VALUES(?,?,?,?,?,?,'REGISTERED',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),?)
            """,Ids.bytes(c.id()),Ids.bytes(scope),Ids.bytes(c.device()),Ids.bytes(actor.user()),c.round(),c.epoch(),Ids.bytes(t.authorization()));
        jdbc.update("UPDATE submission_scope SET current_submission_id=? WHERE id=?",Ids.bytes(c.id()),Ids.bytes(scope));
    }
}
