package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.Ids;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** A formal fault excludes the source qualification without creating its missing STOP/ACK. */
@Repository
class HandoverFormalFaultStore {
    private final JdbcTemplate jdbc;
    HandoverFormalFaultStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    void insert(HandoverCommand.Write write, HandoverStopStore.Origin origin, long version, HandoverFaultInput input) {
        var archive = input.formalArchive();
        if (archive == null || origin.assignment() == null || origin.temporary() != null) throw HandoverFormalArchive.changed();
        int revoked = jdbc.update("UPDATE order_assignment SET status='REVOKED' WHERE id=? AND status='RECALL_PENDING'",
            Ids.bytes(origin.assignment()));
        if (revoked != 1) throw HandoverFormalArchive.changed();
        jdbc.update("UPDATE offline_authorization SET revoked_at=COALESCE(revoked_at,UTC_TIMESTAMP(3)) WHERE assignment_id=?",
            Ids.bytes(origin.assignment()));
        jdbc.update("""
            INSERT INTO handover_formal_fault_fence(case_id,warehouse_id,scope_id,submission_id,assignment_id,source_device_id,
              device_version,change_request_id,recovery_package_id,payload_sha256,capture_high_watermark,audit_through_seq,
              input_high_watermark,controlled_at,command_key,command_sha256,recorded_by,recorded_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """, Ids.bytes(write.caseId()), Ids.bytes(origin.warehouse()), Ids.bytes(origin.scope()), Ids.bytes(origin.submission()),
            Ids.bytes(origin.assignment()), Ids.bytes(origin.device()), version, Ids.bytes(archive.recall()), Ids.bytes(archive.recovery()),
            archive.payloadHash(), archive.captureHigh(), archive.auditHigh(), archive.inputHigh(), Timestamp.from(input.controlledAt()),
            Ids.bytes(UUID.fromString(write.command().key())), write.command().digest(),
            Ids.bytes(UUID.fromString(write.actor().path("principalId").asText())), Timestamp.from(write.recordedAt()));
    }

    Map<String, Object> command(UUID kase) {
        var rows = jdbc.queryForList("""
            SELECT r.* FROM handover_formal_fault_fence f JOIN handover_command_receipt r
              ON r.case_id=f.case_id AND r.original_key=f.command_key AND HEX(r.command_sha256)=UPPER(f.command_sha256)
              AND r.command_type='FAULT_FENCE' AND r.actor_id=f.recorded_by
            JOIN handover_case h ON h.id=f.case_id AND h.warehouse_id=f.warehouse_id AND h.source_kind='FORMAL'
              AND h.scope_id=f.scope_id AND h.submission_id=f.submission_id AND h.input_high_watermark=f.input_high_watermark
            JOIN submission s ON s.id=f.submission_id AND s.assignment_id=f.assignment_id AND s.freeze_device_id=f.source_device_id
            JOIN device d ON d.id=f.source_device_id AND d.warehouse_id=f.warehouse_id
              AND d.status='DISABLED' AND d.row_version>=f.device_version
            JOIN recovery_package p ON p.id=f.recovery_package_id AND p.request_id=f.change_request_id
              AND BINARY p.payload_sha256=BINARY f.payload_sha256
              AND p.capture_high_watermark=f.capture_high_watermark AND p.audit_through_seq=f.audit_through_seq
            WHERE f.case_id=?
            """, Ids.bytes(kase));
        if (rows.size() != 1) throw HandoverFormalArchive.changed();
        return rows.getFirst();
    }
}
