package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionStore;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Immutable origin and recorded stop lookup; all write serialization is owned by the command transaction. */
@Repository
class HandoverStopStore {
    private final JdbcTemplate jdbc;
    private final SubmissionStore submissions;
    HandoverStopStore(JdbcTemplate jdbc, SubmissionStore submissions) { this.jdbc = jdbc; this.submissions = submissions; }

    Origin origin(UUID caseId) {
        var rows = jdbc.query("""
            SELECT BIN_TO_UUID(h.warehouse_id),BIN_TO_UUID(h.scope_id),BIN_TO_UUID(h.canonical_order_id),
              BIN_TO_UUID(h.submission_id),BIN_TO_UUID(s.assignment_id),BIN_TO_UUID(s.freeze_device_id),
              BIN_TO_UUID(s.operator_id),CAST(s.source_recovery_epoch AS CHAR),s.work_round,h.source_kind
            FROM handover_case h INNER JOIN submission s ON s.id=h.submission_id AND s.scope_id=h.scope_id
            WHERE h.id=? AND ((h.source_kind='FORMAL' AND s.assignment_id IS NOT NULL)
              OR (h.source_kind='TEMP' AND s.temporary_authorization_id IS NOT NULL AND s.assignment_id IS NULL))
            """, (rs, n) -> new Origin(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)),
                UUID.fromString(rs.getString(3)), UUID.fromString(rs.getString(4)), nullable(rs.getString(5)),
                UUID.fromString(rs.getString(6)), UUID.fromString(rs.getString(7)), rs.getString(8), rs.getLong(9),
                rs.getString(10).equals("TEMP") ? temporary(UUID.fromString(rs.getString(4))) : null),
            Ids.bytes(caseId));
        if (rows.size() != 1) throw HandoverCommand.missing();
        return rows.getFirst();
    }

    private Temporary temporary(UUID submission) {
        var control = submissions.required(submission).control().temporary();
        if (control == null) throw HandoverCommand.missing();
        return new Temporary(control.authorization(), control.creator(), control.client());
    }

    private static UUID nullable(String id) { return id == null ? null : UUID.fromString(id); }

    Map<String, Object> original(UUID caseId) { return evidence(caseId, "STOP_PROOF"); }
    Map<String, Object> recovery(UUID caseId) { return evidence(caseId, "RECOVERY_PACKAGE"); }

    private Map<String, Object> evidence(UUID caseId, String kind) {
        var rows = jdbc.queryForList("SELECT * FROM handover_evidence WHERE case_id=? AND kind=?", Ids.bytes(caseId), kind);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    record Origin(UUID warehouse, UUID scope, UUID order, UUID submission, UUID assignment,
            UUID device, UUID operator, String epoch, long round, Temporary temporary) {
        Origin(UUID warehouse, UUID scope, UUID order, UUID submission, UUID assignment,
                UUID device, UUID operator, String epoch, long round) {
            this(warehouse, scope, order, submission, assignment, device, operator, epoch, round, null);
        }
    }
    record Temporary(UUID authorization, UUID creator, UUID client) { }
}
