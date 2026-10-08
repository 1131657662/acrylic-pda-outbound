package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Component
final class HandoverTemporaryRoundStore {
    private final JdbcTemplate jdbc;
    HandoverTemporaryRoundStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    void advance(JsonNode facts) {
        var order = uuid(facts, "canonicalOrderId"); var scope = uuid(facts, "scopeId"); var previous = uuid(facts, "submissionId");
        long from = number(facts, "fromWorkRound"), to = number(facts, "toWorkRound");
        int slot = jdbc.update("""
            UPDATE order_submission_slot SET work_round=?,current_submission_id=NULL,phase='CONTINUING',slot_version=slot_version+1
            WHERE order_id=? AND scope_id=? AND work_round=? AND current_submission_id=?
            """, to, Ids.bytes(order), Ids.bytes(scope), from, Ids.bytes(previous));
        if (slot != 1) throw changed();
        int current = jdbc.update("UPDATE submission_scope SET work_round=?,current_submission_id=NULL WHERE id=? AND work_round=? AND current_submission_id=?",
            to, Ids.bytes(scope), from, Ids.bytes(previous));
        if (current != 1 || jdbc.update("UPDATE outbound_order SET status='CONTINUING' WHERE id=?", Ids.bytes(order)) != 1) throw changed();
    }

    void closeCase(UUID caseId) {
        if (jdbc.update("UPDATE handover_case SET open_flag=NULL WHERE id=? AND source_kind='TEMP' AND status='ACTIVATED' AND open_flag=1",
                Ids.bytes(caseId)) != 1) throw changed();
    }

    private static BusinessException changed() {
        return new BusinessException(409, "HANDOVER_ROUND_CHANGED", "临时来源活动轮次或交接开案槽已变化");
    }
}
