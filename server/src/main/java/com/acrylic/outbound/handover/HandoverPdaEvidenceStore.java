package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.Ids;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Selects exactly one immutable command receipt for each handover milestone. */
@Repository
class HandoverPdaEvidenceStore {
    private final JdbcTemplate jdbc;

    HandoverPdaEvidenceStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    Map<String, Object> command(UUID caseId, String kind) {
        var rows = jdbc.queryForList("SELECT * FROM handover_command_receipt "
            + "WHERE case_id=? AND command_type=? LIMIT 2", Ids.bytes(caseId), kind);
        HandoverCommand.corruptUnless(rows.size() == 1);
        return rows.getFirst();
    }
}
