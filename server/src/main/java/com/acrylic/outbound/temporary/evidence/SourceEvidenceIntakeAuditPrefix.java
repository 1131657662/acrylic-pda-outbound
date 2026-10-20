package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.audit.PdaAuditFacts;
import com.acrylic.outbound.audit.PdaAuditOrigins;
import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.E2aPageItem;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class SourceEvidenceIntakeAuditPrefix {
    private static final String PAGE = """
        SELECT id,origin_device_id,local_seq,warehouse_id,operator_id,source_recovery_epoch,
          record_sha256,payload,assignment_id,temporary_authorization_id,client_order_id
        FROM pda_audit_event WHERE origin_device_id=? AND local_seq>? AND local_seq<=?
        ORDER BY local_seq LIMIT 512
        """;
    private final JdbcTemplate jdbc;
    private final PdaAuditOrigins origins;

    public SourceEvidenceIntakeAuditPrefix(JdbcTemplate jdbc, PdaAuditOrigins origins) {
        this.jdbc = jdbc; this.origins = origins;
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public void require(UUID device, UUID warehouse, long recoveryEpoch, long throughSeq) {
        if (device == null || warehouse == null || recoveryEpoch < 1 || throughSeq < 1
            || throughSeq > 9007199254740991L) throw new IllegalArgumentException("E2A_AUDIT_PREFIX_BINDING");
        long cursor = 0;
        while (cursor < throughSeq) {
            var rows = jdbc.queryForList(PAGE, Ids.bytes(device), cursor, throughSeq);
            if (rows.isEmpty()) throw conflict();
            for (var row : rows) {
                if (!(row.get("local_seq") instanceof Number sequence) || sequence.longValue() != cursor + 1
                    || sequence.longValue() > throughSeq) throw conflict();
                validate(row, device, warehouse, recoveryEpoch, cursor + 1);
                cursor++;
            }
        }
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public void requireItem(E2aPageItem item, byte[] original, UUID device, UUID warehouse, long epoch) {
        if (item == null || original == null || device == null || warehouse == null || epoch < 1)
            throw new IllegalArgumentException("E2A_AUDIT_ITEM_BINDING");
        if (!"AUDIT".equals(item.itemKind()) || !"AUDIT_RECORDS".equals(item.objectKind())) throw conflict();
        var rows = jdbc.queryForList(PAGE, Ids.bytes(device), item.originalDeviceSeq() - 1,
            item.originalDeviceSeq());
        if (rows.size() != 1) throw conflict();
        var row = rows.getFirst();
        validate(row, device, warehouse, epoch, item.originalDeviceSeq());
        try {
            var event = SubmissionJson.parse(original);
            if (original.length != item.byteCount()
                || !item.itemBytesSha256().equals(ImportFiles.sha256(original))
                || !Arrays.equals(original, SubmissionJson.canonical(event).getBytes(StandardCharsets.UTF_8))
                || !item.originalRecordId().equals(SubmissionJson.uuid(event, "id"))
                || !item.recordSha256().equals(SubmissionJson.sha(event))
                || !SubmissionJson.canonical(event).equals(SubmissionJson.canonical(
                    SubmissionJson.parse((String) row.get("payload"))))) throw conflict();
        } catch (RuntimeException invalid) { throw conflict(); }
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public void requireStopEvent(UUID device, UUID warehouse, UUID operator, UUID authorization,
        long epoch, long throughSeq, String stopEventId, UUID submission) {
        if (device == null || warehouse == null || operator == null || authorization == null
            || epoch < 1 || throughSeq < 1 || stopEventId == null || submission == null)
            throw new IllegalArgumentException("E2A_STOP_AUDIT_BINDING");
        var rows = jdbc.queryForList(PAGE, Ids.bytes(device), throughSeq - 1, throughSeq);
        if (rows.size() != 1) throw conflict();
        var row = rows.getFirst();
        validate(row, device, warehouse, epoch, throughSeq);
        try {
            var payload = SubmissionJson.parse((String) row.get("payload"));
            if (!"STOP".equals(SubmissionJson.text(payload, "type"))
                || !stopEventId.equals(SubmissionJson.uuid(payload, "id").toString())
                || !operator.equals(SubmissionJson.uuid(payload, "operatorId"))
                || !"TEMP".equals(SubmissionJson.text(payload.path("source"), "kind"))
                || !authorization.equals(SubmissionJson.uuid(payload, "authorizationId"))
                || !submission.toString().equals(SubmissionJson.text(payload, "detail"))) throw conflict();
            var handover=SourceEvidenceHandoverFacts.read(jdbc,submission);
            var clients = !handover.isEmpty() ? handover.stream()
                .map(value->SubmissionJson.text(SubmissionJson.parse(value),"clientOrderId")).toList()
                : continuationTablesInstalled() ? jdbc.queryForList("""
                SELECT BIN_TO_UUID(CASE WHEN s.work_round=1 THEN c.client_order_id ELSE n.new_client_order_id END)
                FROM submission s JOIN submission_scope c ON c.id=s.scope_id
                LEFT JOIN temporary_source_continuation_submission r ON r.submission_id=s.id AND r.scope_id=c.id
                  AND r.to_work_round=s.work_round AND r.from_work_round=s.work_round-1
                LEFT JOIN temporary_source_continuation_activation n ON n.ticket_id=r.ticket_id AND n.scope_id=c.id
                  AND n.from_work_round=r.from_work_round AND n.new_authorization_id=s.temporary_authorization_id
                  AND n.target_device_id=s.freeze_device_id AND n.target_operator_id=s.operator_id
                WHERE s.id=? AND (s.work_round<>1 OR (
                  c.work_round=1 AND s.assignment_id IS NULL AND s.continuation_id IS NULL
                  AND EXISTS (
                    SELECT 1 FROM temporary_work_origin t
                    JOIN temporary_authorization a ON a.id=t.authorization_id
                    WHERE t.creator_device_id=c.creator_device_id AND t.client_order_id=c.client_order_id
                      AND a.id=s.temporary_authorization_id AND a.device_id=c.creator_device_id
                      AND a.device_id=s.freeze_device_id AND a.user_id=s.operator_id
                      AND a.warehouse_id=c.warehouse_id AND a.recovery_epoch=s.source_recovery_epoch)))
                """, String.class, Ids.bytes(submission)):
                jdbc.queryForList("""
                SELECT BIN_TO_UUID(c.client_order_id)
                FROM submission s JOIN submission_scope c ON c.id=s.scope_id
                JOIN temporary_work_origin t ON t.creator_device_id=c.creator_device_id AND t.client_order_id=c.client_order_id
                JOIN temporary_authorization a ON a.id=s.temporary_authorization_id
                  AND a.id=t.authorization_id AND a.device_id=c.creator_device_id AND a.device_id=s.freeze_device_id
                  AND a.user_id=s.operator_id AND a.warehouse_id=c.warehouse_id AND a.recovery_epoch=s.source_recovery_epoch
                WHERE s.id=? AND s.work_round=1 AND c.work_round=1 AND s.assignment_id IS NULL AND s.continuation_id IS NULL
                """, String.class, Ids.bytes(submission));
            if (clients.size() != 1 || !clients.getFirst().equals(
                SubmissionJson.text(payload.path("source"), "clientOrderId"))) throw conflict();
        } catch (RuntimeException invalid) { throw conflict(); }
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public void requireArchiveEvent(JsonNode event, UUID device, UUID warehouse, long epoch) {
        if (event == null || device == null || warehouse == null || epoch < 1)
            throw new IllegalArgumentException("E2A_AUDIT_ARCHIVE_EVENT_BINDING");
        try {
            long sequence = SubmissionJson.number(event, "localSeq");
            if (sequence < 1) throw conflict();
            var rows = jdbc.queryForList(PAGE, Ids.bytes(device), sequence - 1, sequence);
            if (rows.size() != 1) throw conflict();
            var row = rows.getFirst();
            validate(row, device, warehouse, epoch, sequence);
            if (!SubmissionJson.canonical(event).equals(SubmissionJson.canonical(
                SubmissionJson.parse((String) row.get("payload"))))) throw conflict();
        } catch (RuntimeException invalid) { throw conflict(); }
    }

    private void validate(Map<String, Object> row, UUID device, UUID warehouse, long epoch, long sequence) {
        try {
            var payload = SubmissionJson.parse((String) row.get("payload"));
            PdaAuditFacts.validate(payload);
            if (!(row.get("source_recovery_epoch") instanceof Number sourceEpoch)
                || sourceEpoch.longValue() < 1 || sourceEpoch.longValue() > epoch
                || !same(row, "id", SubmissionJson.uuid(payload, "id"))
                || !same(row, "origin_device_id", device) || !same(row, "warehouse_id", warehouse)
                || !same(row, "operator_id", SubmissionJson.uuid(payload, "operatorId"))
                || !device.equals(SubmissionJson.uuid(payload, "originDeviceId"))
                || SubmissionJson.number(payload, "localSeq") != sequence
                || !SubmissionJson.sha(payload).equals(row.get("record_sha256"))
                || !sourceMatches(row, payload)) throw conflict();
            if (!Long.toString(sourceEpoch.longValue()).equals(origins.verify(device, warehouse, payload).epoch()))
                throw conflict();
            PdaAuditFacts.verify(jdbc, payload);
        } catch (RuntimeException invalid) { throw conflict(); }
    }

    private boolean continuationTablesInstalled() {
        Integer count=jdbc.queryForObject("""
            SELECT COUNT(*) FROM information_schema.tables
            WHERE table_schema=DATABASE() AND table_name IN (?,?)
            """,Integer.class,"temporary_source_continuation_submission","temporary_source_continuation_activation");
        return count!=null && count==2;
    }

    private boolean sourceMatches(Map<String, Object> row, JsonNode payload) {
        if (payload.has("source")) {
            return row.get("assignment_id") == null
                && same(row, "temporary_authorization_id", SubmissionJson.uuid(payload, "authorizationId"))
                && same(row, "client_order_id", SubmissionJson.uuid(payload.path("source"), "clientOrderId"));
        }
        return row.get("temporary_authorization_id") == null && row.get("client_order_id") == null
            && same(row, "assignment_id", SubmissionJson.uuid(payload, "assignmentId"));
    }

    private boolean same(Map<String, Object> row, String field, UUID expected) {
        return row.get(field) instanceof byte[] stored && Arrays.equals(stored, Ids.bytes(expected));
    }

    private static BusinessException conflict() {
        return new BusinessException(409, "E2A_AUDIT_PREFIX_UNVERIFIABLE", "原设备审计前缀存在缺口或内容不可核验");
    }
}
