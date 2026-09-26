package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.temporary.command.CommandContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SourceEvidenceIntakeEvidenceLocks {
    private final JdbcTemplate jdbc;

    public SourceEvidenceIntakeEvidenceLocks(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @Transactional(propagation=Propagation.MANDATORY)
    public void source(CommandContext context) {
        byte[] scope=Ids.bytes(context.id("scopeId"));
        byte[] submission=Ids.bytes(context.id("submissionId"));
        long round=context.version("workRound");
        lock("SELECT id FROM source_evidence_set WHERE scope_id=? AND submission_id=? AND work_round=? ORDER BY id FOR UPDATE",
            scope,submission,round);
        lock("SELECT id FROM source_evidence_object WHERE scope_id=? AND submission_id=? AND work_round=? ORDER BY id FOR UPDATE",
            scope,submission,round);
        lock("SELECT set_id,item_kind,original_record_id FROM source_evidence_item "
            +"WHERE scope_id=? AND submission_id=? AND work_round=? ORDER BY set_id,item_kind,original_record_id FOR UPDATE",
            scope,submission,round);
        lock("SELECT id FROM source_evidence_verification WHERE scope_id=? AND submission_id=? AND work_round=? ORDER BY id FOR UPDATE",
            scope,submission,round);
        lock("SELECT id FROM source_evidence_intake_receipt WHERE scope_id=? AND submission_id=? AND work_round=? ORDER BY id FOR UPDATE",
            scope,submission,round);
        lock("SELECT item_kind,original_record_id FROM source_evidence_page_item_stage "
            +"WHERE scope_id=? AND submission_id=? AND work_round=? ORDER BY item_kind,original_record_id FOR UPDATE",
            scope,submission,round);
    }

    private void lock(String sql,byte[] scope,byte[] submission,long round) {
        jdbc.query(sql,(RowCallbackHandler) row -> { },scope,submission,round);
    }
}
