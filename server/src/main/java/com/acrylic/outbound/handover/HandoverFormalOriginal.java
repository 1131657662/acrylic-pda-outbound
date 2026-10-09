package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.pda.AssignmentStore;
import com.acrylic.outbound.recall.*;
import com.acrylic.outbound.submissions.SubmissionFiles;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.util.Arrays;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Read-only revalidation of an original-device package uploaded before supervisor custody. */
@Component
final class HandoverFormalOriginal {
    private final RecallStore recalls;
    private final AssignmentStore assignments;
    private final RecoveryEvidence evidence;
    private final SubmissionFiles files;
    private final JdbcTemplate jdbc;

    HandoverFormalOriginal(RecallStore recalls, AssignmentStore assignments, RecoveryEvidence evidence,
            SubmissionFiles files, JdbcTemplate jdbc) {
        this.recalls = recalls; this.assignments = assignments; this.evidence = evidence; this.files = files; this.jdbc = jdbc;
    }

    JsonNode read(HandoverFaultInput input, HandoverStopStore.Origin origin) {
        try { return original(input, origin); }
        catch (BusinessException | java.io.UncheckedIOException | org.springframework.dao.EmptyResultDataAccessException changed) {
            throw HandoverFormalArchive.changed();
        }
    }

    private JsonNode original(HandoverFaultInput input, HandoverStopStore.Origin origin) {
        var archive = input.formalArchive();
        require(archive != null && origin.temporary() == null && origin.assignment() != null);
        var recall = recalls.get(archive.recall()); var pack = recalls.recovery(recall.id());
        requireRecall(origin, recall);
        byte[] bytes = requirePackage(input, pack, archive, recall); var body = parse(bytes);
        require(Arrays.equals(bytes, HandoverReceiptCodec.bytes(body)) && sha(body).equals(archive.payloadHash()));
        require(uuid(body, "recoveryPackageId").equals(pack.id()) && number(body, "workRound") == origin.round()
            && number(body, "captureHighWatermark") == archive.captureHigh() && uuid(body, "freezeDeviceId").equals(origin.device()));
        var assignment = assignments.get(origin.assignment());
        require(assignment.operatorId().equals(origin.operator().toString()) && assignment.deviceId().equals(origin.device().toString())
            && assignment.orderId().equals(origin.order().toString()) && assignment.revision() == recall.revision());
        var validated = evidence.validate(recall, assignment, body);
        require(validated.auditHigh() == archive.auditHigh() && validated.stock().scans().size() == pack.count()
            && validated.stock().weight() == pack.grams());
        requireInputHigh(body, archive.inputHigh());
        return body;
    }

    private void requireRecall(HandoverStopStore.Origin origin, RecallStore.Head recall) {
        require(recall.warehouse().equals(origin.warehouse()) && recall.order().equals(origin.order())
            && recall.assignment().equals(origin.assignment()) && recall.epoch().equals(origin.epoch())
            && "REQUESTED".equals(recall.status()) && recall.stop() == null && recall.resolution() == null);
    }

    private byte[] requirePackage(HandoverFaultInput input, RecallStore.Package pack, HandoverFormalArchive archive,
            RecallStore.Head recall) {
        require(pack != null && pack.id().equals(archive.recovery()) && pack.hash().equals(archive.payloadHash())
            && pack.captureHigh() == archive.captureHigh() && pack.auditHigh() == archive.auditHigh() && parse(pack.issues()).isEmpty());
        var row = jdbc.queryForMap("SELECT byte_count,received_at FROM recovery_package WHERE id=? AND request_id=?",
            Ids.bytes(pack.id()), Ids.bytes(recall.id()));
        require(!receivedAt(row.get("received_at")).isAfter(input.controlledAt()));
        byte[] bytes = files.read(pack.hash());
        require(bytes.length == ((Number) row.get("byte_count")).intValue());
        return bytes;
    }

    private void requireInputHigh(JsonNode body, long high) {
        long previous = 0;
        for (var capture : array(body, "captures")) {
            long sequence = number(capture, "orderScanSeq");
            require(sequence == Math.addExact(previous, 1)); previous = sequence;
        }
        require(previous == high);
    }

    private java.time.Instant receivedAt(Object value) {
        if (value instanceof Timestamp stamp) return stamp.toInstant();
        if (value instanceof java.time.LocalDateTime time) return time.toInstant(java.time.ZoneOffset.UTC);
        throw HandoverFormalArchive.changed();
    }

    private static void require(boolean valid) { if (!valid) throw HandoverFormalArchive.changed(); }
}
