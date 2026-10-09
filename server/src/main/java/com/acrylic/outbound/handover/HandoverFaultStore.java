package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.Ids;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Durable device exclusion and case-bound fault evidence. There is no release/re-enable operation. */
@Repository
class HandoverFaultStore {
    private final JdbcTemplate jdbc;
    private final HandoverFormalFaultStore formal;
    HandoverFaultStore(JdbcTemplate jdbc, HandoverFormalFaultStore formal) { this.jdbc = jdbc; this.formal = formal; }

    void insert(HandoverCommand.Write write, HandoverStopStore.Origin origin, long deviceVersion) {
        var input = HandoverFaultInput.read(write.command().body(), write.recordedAt());
        if (input.formalArchive() != null) { formal.insert(write, origin, deviceVersion, input); return; }
        jdbc.update("""
            INSERT INTO handover_fault_fence(case_id,warehouse_id,scope_id,submission_id,source_device_id,device_version,
              source_evidence_set_id,verification_id,command_key,command_sha256,input_high_watermark,recorded_by,recorded_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
            """, Ids.bytes(write.caseId()), Ids.bytes(origin.warehouse()), Ids.bytes(origin.scope()), Ids.bytes(origin.submission()),
            Ids.bytes(origin.device()), deviceVersion, Ids.bytes(input.archive().evidenceSet()), Ids.bytes(input.archive().verification()),
            Ids.bytes(UUID.fromString(write.command().key())), write.command().digest(), input.archive().inputHigh(),
            Ids.bytes(UUID.fromString(write.actor().path("principalId").asText())), Timestamp.from(write.recordedAt()));
    }

    Map<String, Object> command(UUID caseId) {
        var rows = jdbc.queryForList("""
            SELECT r.* FROM handover_fault_fence f JOIN handover_command_receipt r
              ON r.case_id=f.case_id AND r.original_key=f.command_key AND HEX(r.command_sha256)=UPPER(f.command_sha256)
              AND r.command_type='FAULT_FENCE' AND r.actor_id=f.recorded_by
            JOIN handover_case h ON h.id=f.case_id AND h.warehouse_id=f.warehouse_id AND h.source_kind='TEMP'
              AND h.scope_id=f.scope_id AND h.submission_id=f.submission_id AND h.input_high_watermark=f.input_high_watermark
            JOIN submission s ON s.id=f.submission_id AND s.freeze_device_id=f.source_device_id
            JOIN device d ON d.id=f.source_device_id AND d.warehouse_id=f.warehouse_id
              AND d.status='DISABLED' AND d.row_version>=f.device_version
            WHERE f.case_id=?
            """, Ids.bytes(caseId));
        if (rows.isEmpty()) return formal.command(caseId);
        if (rows.size() != 1) throw HandoverTemporaryRoots.changed();
        return rows.getFirst();
    }

    void requireArchiveCutoff(HandoverFaultInput input) {
        var rows = jdbc.queryForList("SELECT verified_at FROM source_evidence_verification WHERE id=? AND set_id=?",
            Timestamp.class, Ids.bytes(input.archive().verification()), Ids.bytes(input.archive().evidenceSet()));
        if (rows.size() != 1 || rows.getFirst().toInstant().isAfter(input.controlledAt())) throw HandoverTemporaryRoots.changed();
    }
}
