package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class SourceEvidenceIntakeStopLookup {
    private static final String FIND = """
        SELECT r.canonical_request_bytes,r.object_id,r.operation_kind,r.receipt_status,r.warehouse_id,r.submission_id,
          r.work_round,r.creator_device_id,r.temporary_authorization_id,r.original_operator_id,
          o.bytes_sha256,o.byte_count,o.file_key,o.kind
        FROM source_evidence_intake_receipt r JOIN source_evidence_object o ON o.id=r.object_id
        WHERE r.principal_id=? AND r.route_scope=? AND r.original_key=?
        """;
    private final JdbcTemplate jdbc;

    public SourceEvidenceIntakeStopLookup(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public StopRecord read(SourceEvidenceIntakeReceipts.BoundKey key) { return find(key, false); }

    @Transactional(propagation = Propagation.MANDATORY)
    public StopRecord locked(SourceEvidenceIntakeReceipts.BoundKey key) { return find(key, true); }

    private StopRecord find(SourceEvidenceIntakeReceipts.BoundKey key, boolean lock) {
        if (key == null) throw new IllegalArgumentException("STOP_LOOKUP_KEY");
        var rows = jdbc.queryForList(lock ? FIND + " FOR UPDATE" : FIND, Ids.bytes(key.principalId()),
            E2aProtocol.ROUTE_SCOPE, Ids.bytes(key.originalKey()));
        if (rows.isEmpty()) throw new BusinessException(409, "E2A_STOP_NOT_RECEIVED", "尚未收到原机停止证明");
        if (rows.size() != 1) throw conflict();
        var row = rows.getFirst();
        if (!(row.get("canonical_request_bytes") instanceof byte[] request)
            || !(row.get("object_id") instanceof byte[] object) || object.length != 16
            || !"STOP".equals(row.get("operation_kind")) || !"PARTIAL".equals(row.get("receipt_status"))
            || !"STOP_PROOF".equals(row.get("kind"))
            || !java.util.Arrays.equals(value(row, "warehouse_id"), Ids.bytes(key.warehouseId()))
            || !java.util.Arrays.equals(value(row, "submission_id"), Ids.bytes(key.submissionId()))
            || !(row.get("work_round") instanceof Number round) || round.longValue() != key.workRound()
            || !java.util.Arrays.equals(value(row, "creator_device_id"), Ids.bytes(key.creatorDeviceId()))
            || !java.util.Arrays.equals(value(row, "temporary_authorization_id"), Ids.bytes(key.authorizationId()))
            || !java.util.Arrays.equals(value(row, "original_operator_id"), Ids.bytes(key.principalId()))) throw conflict();
        var stored = new SourceEvidenceIntakeObjectFiles.Stored(text(row, "file_key"), text(row, "bytes_sha256"),
            number(row, "byte_count"));
        var buffer = ByteBuffer.wrap(object);
        return new StopRecord(request, new UUID(buffer.getLong(), buffer.getLong()), stored);
    }

    private static String text(Map<String, Object> row, String key) {
        if (!(row.get(key) instanceof String value) || value.isBlank()) throw conflict(); return value;
    }

    private static int number(Map<String, Object> row, String key) {
        if (!(row.get(key) instanceof Number value) || value.longValue() < 1 || value.longValue() > 33554432)
            throw conflict(); return value.intValue();
    }

    private static byte[] value(Map<String, Object> row, String key) {
        if (!(row.get(key) instanceof byte[] value) || value.length != 16) throw conflict(); return value;
    }

    private static BusinessException conflict() {
        return new BusinessException(409, "E2A_STOP_RECEIPT_INTEGRITY_FAILURE", "原停止回执或停止对象不可核验");
    }

    public record StopRecord(byte[] requestBytes, UUID objectId, SourceEvidenceIntakeObjectFiles.Stored stored) {
        public StopRecord { requestBytes = requestBytes.clone(); }
        @Override public byte[] requestBytes() { return requestBytes.clone(); }
    }
}
