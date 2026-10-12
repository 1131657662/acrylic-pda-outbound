package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Restores a reopened dispatch owner in the current reopened cycle when no later occupancy exists. */
final class CorrectionOccupancyRestoration {
    private static final Set<String> REOPENING_TYPES = Set.of("DISPATCH_CANCELLATION", "PHYSICAL_RETURN");
    private final JdbcTemplate jdbc;

    CorrectionOccupancyRestoration(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    ArrayNode restore(CorrectionCommand.Write write, List<Map<String, Object>> lines) {
        var restored = SubmissionJson.object().putArray("restoredOccupancies");
        if (!REOPENING_TYPES.contains(write.anchor().path("type").asText())) return restored;
        var orderedLines = new ArrayList<>(lines);
        orderedLines.sort(Comparator.comparing(line -> Objects.toString(line.get("dispatch_id"), "")));
        for (var line : orderedLines) restoreLine(write, line, restored);
        return restored;
    }

    private void restoreLine(CorrectionCommand.Write write, Map<String, Object> line, ArrayNode restored) {
        if (line.get("dispatch_id") == null) throw CorrectionCommand.ledgerConflict();
        UUID lineId = UUID.fromString(line.get("line_id").toString());
        UUID dispatchId = UUID.fromString(line.get("dispatch_id").toString());
        Reopening reopening = reopening(dispatchId, lineId);
        UUID baleId = originalBale(dispatchId, reopening.cycleBefore());
        requireUnchangedBale(dispatchId, baleId, reopening.cycleAfter());
        restoreCommitment(dispatchId, baleId, reopening.cycleAfter(), write.recordedAt());
        restored.add(receipt(dispatchId, baleId, reopening));
    }

    void record(CorrectionCommand.Write write, JsonNode decision) {
        for (var restored : decision.path("restoredOccupancies")) {
            UUID dispatchId = UUID.fromString(restored.path("dispatchId").asText());
            UUID lineId = lineId(write.documentId(), dispatchId);
            record(write, lineId, dispatchId, new Reopening(restored.path("cycleBefore").asInt(),
                restored.path("cycleAfter").asInt()), UUID.fromString(write.actor().path("principalId").asText()));
        }
    }

    private UUID lineId(UUID documentId, UUID dispatchId) {
        var rows = jdbc.queryForList("SELECT BIN_TO_UUID(id) line_id FROM correction_line WHERE document_id=? AND dispatch_id=?",
            Ids.bytes(documentId), Ids.bytes(dispatchId));
        if (rows.size() != 1) throw CorrectionCommand.ledgerConflict();
        return UUID.fromString(String.valueOf(rows.getFirst().get("line_id")));
    }

    private Reopening reopening(UUID dispatchId, UUID lineId) {
        var rows = jdbc.queryForList("""
            SELECT BIN_TO_UUID(correction_line_id) correction_line_id,cycle_before,cycle_after
            FROM cycle_reopening WHERE original_dispatch_id=? FOR UPDATE
            """, Ids.bytes(dispatchId));
        if (rows.size() != 1 || !lineId.toString().equals(String.valueOf(rows.getFirst().get("correction_line_id"))))
            throw CorrectionCommand.ledgerConflict();
        return new Reopening(number(rows.getFirst(), "cycle_before"), number(rows.getFirst(), "cycle_after"));
    }

    private UUID originalBale(UUID dispatchId, int cycleBefore) {
        var rows = jdbc.queryForList("SELECT BIN_TO_UUID(bale_id) bale_id,dispatch_cycle FROM dispatch_record"
            + " WHERE id=? FOR UPDATE", Ids.bytes(dispatchId));
        if (rows.size() != 1 || number(rows.getFirst(), "dispatch_cycle") != cycleBefore) throw CorrectionCommand.ledgerConflict();
        return UUID.fromString(String.valueOf(rows.getFirst().get("bale_id")));
    }

    private void requireUnchangedBale(UUID dispatchId, UUID baleId, int cycleAfter) {
        Integer releases = jdbc.queryForObject("SELECT COUNT(*) FROM returned_stock_release WHERE original_dispatch_id=?",
            Integer.class, Ids.bytes(dispatchId));
        if (releases!=null && releases>0) throw CorrectionCommand.ledgerConflict();
        Long cycle = jdbc.queryForObject("SELECT dispatch_cycle FROM bale WHERE id=? FOR UPDATE", Long.class, Ids.bytes(baleId));
        if (cycle == null || cycle != cycleAfter) throw CorrectionCommand.ledgerConflict();
        var owners = jdbc.queryForList("SELECT bale_id FROM bale_commitment WHERE bale_id=? FOR UPDATE", Ids.bytes(baleId));
        if (!owners.isEmpty()) throw CorrectionCommand.ledgerConflict();
    }

    private void restoreCommitment(UUID dispatchId, UUID baleId, int restoredCycle, Instant now) {
        int inserted = jdbc.update("""
            INSERT INTO bale_commitment(bale_id,order_id,submission_id,dispatch_cycle,state,created_at,pc_work_id,
              source_scope_id,source_warehouse_id)
            SELECT d.bale_id,d.order_id,d.submission_id,?,'DISPATCHED',?,p.work_id,
              IF(s.temporary_authorization_id IS NULL,NULL,s.scope_id),
              IF(s.temporary_authorization_id IS NULL,NULL,scope.warehouse_id)
            FROM dispatch_record d
            LEFT JOIN submission s ON s.id=d.submission_id
            LEFT JOIN submission_scope scope ON scope.id=s.scope_id
            LEFT JOIN pc_submission p ON p.id=d.pc_submission_id
            WHERE d.id=? AND d.bale_id=?
              AND ((d.submission_id IS NOT NULL AND p.work_id IS NULL)
                OR (d.submission_id IS NULL AND p.work_id IS NOT NULL))
            """, restoredCycle, Timestamp.from(now), Ids.bytes(dispatchId), Ids.bytes(baleId));
        if (inserted != 1) throw CorrectionCommand.ledgerConflict();
    }

    private void record(CorrectionCommand.Write write, UUID lineId, UUID dispatchId, Reopening reopening, UUID actor) {
        jdbc.update("""
            INSERT INTO correction_occupancy_restoration(id,original_dispatch_id,correction_line_id,
              withdrawal_document_id,withdrawal_receipt_id,cycle_before,cycle_after,restored_by,restored_at)
            VALUES(?,?,?,?,?,?,?,?,?)
            """, Ids.bytes(Ids.next()), Ids.bytes(dispatchId), Ids.bytes(lineId), Ids.bytes(write.documentId()),
            Ids.bytes(write.receiptId()), reopening.cycleBefore(), reopening.cycleAfter(), Ids.bytes(actor),
            Timestamp.from(write.recordedAt()));
    }

    private ObjectNode receipt(UUID dispatchId, UUID baleId, Reopening reopening) {
        return SubmissionJson.object().put("dispatchId", dispatchId.toString()).put("baleId", baleId.toString())
            .put("cycleBefore", Integer.toString(reopening.cycleBefore()))
            .put("cycleAfter", Integer.toString(reopening.cycleAfter()));
    }

    private static int number(Map<String, Object> row, String column) {
        Object value = row.get(column);
        if (!(value instanceof Number number)) throw CorrectionCommand.ledgerConflict();
        return Math.toIntExact(number.longValue());
    }

    private record Reopening(int cycleBefore, int cycleAfter) { }
}
