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
                    || sequence.longValue() > throughSeq)
                    throw conflict();
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
            var clients = jdbc.queryForList("""
                SELECT BIN_TO_UUID(c.client_order_id) FROM submission s
                JOIN submission_scope c ON c.id=s.scope_id WHERE s.id=?
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
