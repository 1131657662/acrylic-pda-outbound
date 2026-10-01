package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.Ids;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Selects the immutable REQUEST evidence for a case; ambiguous evidence fails closed. */
@Repository
class HandoverReadStore {
    private final JdbcTemplate jdbc;
    HandoverReadStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    Map<String, Object> originalRequest(UUID caseId) {
        var rows = jdbc.queryForList("SELECT * FROM handover_command_receipt "
            + "WHERE case_id=? AND command_type='REQUEST' LIMIT 2", Ids.bytes(caseId));
        HandoverCommand.corruptUnless(rows.size() == 1);
        return rows.getFirst();
    }
}
