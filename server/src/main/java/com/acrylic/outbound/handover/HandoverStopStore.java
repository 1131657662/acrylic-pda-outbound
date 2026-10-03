package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.Ids;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Immutable origin and recorded stop lookup; all write serialization is owned by the command transaction. */
@Repository
class HandoverStopStore {
    private final JdbcTemplate jdbc;
    HandoverStopStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    Origin origin(UUID caseId) {
        var rows = jdbc.query("""
            SELECT BIN_TO_UUID(h.warehouse_id),BIN_TO_UUID(h.scope_id),BIN_TO_UUID(h.canonical_order_id),
              BIN_TO_UUID(h.submission_id),BIN_TO_UUID(s.assignment_id),BIN_TO_UUID(s.freeze_device_id),
              BIN_TO_UUID(s.operator_id),CAST(s.source_recovery_epoch AS CHAR),s.work_round
            FROM handover_case h INNER JOIN submission s ON s.id=h.submission_id AND s.scope_id=h.scope_id
            WHERE h.id=? AND h.source_kind='FORMAL' AND s.assignment_id IS NOT NULL
            """, (rs, n) -> new Origin(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)),
                UUID.fromString(rs.getString(3)), UUID.fromString(rs.getString(4)), UUID.fromString(rs.getString(5)),
                UUID.fromString(rs.getString(6)), UUID.fromString(rs.getString(7)), rs.getString(8), rs.getLong(9)),
            Ids.bytes(caseId));
        if (rows.size() != 1) throw HandoverCommand.missing();
        return rows.getFirst();
    }

    Map<String, Object> original(UUID caseId) { return evidence(caseId, "STOP_PROOF"); }
    Map<String, Object> recovery(UUID caseId) { return evidence(caseId, "RECOVERY_PACKAGE"); }

    private Map<String, Object> evidence(UUID caseId, String kind) {
        var rows = jdbc.queryForList("SELECT * FROM handover_evidence WHERE case_id=? AND kind=?", Ids.bytes(caseId), kind);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    record Origin(UUID warehouse, UUID scope, UUID order, UUID submission, UUID assignment,
            UUID device, UUID operator, String epoch, long round) { }
}