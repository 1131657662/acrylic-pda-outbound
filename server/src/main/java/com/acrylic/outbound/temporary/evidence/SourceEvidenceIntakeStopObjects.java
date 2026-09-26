package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class SourceEvidenceIntakeStopObjects {
    private static final String FIND = """
        SELECT id,warehouse_id,origin_device_id,uploaded_by,bytes_sha256,byte_count,file_key
        FROM source_evidence_object
        WHERE scope_id=? AND submission_id=? AND work_round=? AND kind='STOP_PROOF' AND original_object_id=?
        """;
    private static final String INSERT = """
        INSERT INTO source_evidence_object
        (id,warehouse_id,scope_id,submission_id,work_round,kind,original_object_id,bytes_sha256,
         byte_count,file_key,origin_device_id,uploaded_by,received_at)
        VALUES (?,?,?,?,?,'STOP_PROOF',?,?,?,?,?,?,?)
        """;
    private final JdbcTemplate jdbc;

    public SourceEvidenceIntakeStopObjects(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public static UUID originalId(UUID openKey, String stopEventId) {
        if (openKey == null || stopEventId == null || stopEventId.isBlank())
            throw new IllegalArgumentException("STOP_OBJECT_ID");
        String name = "acrylic-r06-e2a-stop\u0000" + openKey + "\u0000" + stopEventId;
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public StopObject findAfterSourceLock(Binding binding, String eventId) {
        UUID original = originalId(binding.openKey(), eventId);
        var rows = jdbc.queryForList(FIND, Ids.bytes(binding.scopeId()), Ids.bytes(binding.submissionId()),
            binding.workRound(), Ids.bytes(original));
        if (rows.isEmpty()) return null;
        if (rows.size() != 1) throw corrupt();
        var row = rows.getFirst();
        if (!(row.get("id") instanceof byte[] id) || id.length != 16
            || !(row.get("warehouse_id") instanceof byte[] warehouse)
            || !Arrays.equals(warehouse, Ids.bytes(binding.warehouseId()))
            || !(row.get("origin_device_id") instanceof byte[] device)
            || !Arrays.equals(device, Ids.bytes(binding.deviceId()))
            || !(row.get("uploaded_by") instanceof byte[] uploader)
            || !Arrays.equals(uploader, Ids.bytes(binding.operatorId()))
            || !(row.get("bytes_sha256") instanceof String hash) || !hash.matches("[0-9a-f]{64}")
            || !(row.get("byte_count") instanceof Number count) || count.longValue() < 1
            || count.longValue() > 33554432 || !(row.get("file_key") instanceof String fileKey)
            || !fileKey.equals("source-evidence-intake/objects/" + hash + ".bin")) throw corrupt();
        var bytes = ByteBuffer.wrap(id);
        var stored = new SourceEvidenceIntakeObjectFiles.Stored(fileKey, hash, count.intValue());
        return new StopObject(new UUID(bytes.getLong(), bytes.getLong()), original, stored);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public StopObject insert(Binding binding, String eventId, SourceEvidenceIntakeObjectFiles.Stored stored,
        Instant receivedAt) {
        if (binding == null || stored == null || receivedAt == null)
            throw new IllegalArgumentException("STOP_OBJECT_INSERT");
        UUID original = originalId(binding.openKey(), eventId);
        UUID id = Ids.next();
        if (jdbc.update(INSERT, Ids.bytes(id), Ids.bytes(binding.warehouseId()), Ids.bytes(binding.scopeId()),
            Ids.bytes(binding.submissionId()), binding.workRound(), Ids.bytes(original), stored.sha256(),
            stored.bytes(), stored.key(), Ids.bytes(binding.deviceId()), Ids.bytes(binding.operatorId()),
            Timestamp.from(receivedAt)) != 1) throw new IllegalStateException("STOP_OBJECT_INSERT_COUNT");
        return new StopObject(id, original, stored);
    }

    private static BusinessException corrupt() {
        return new BusinessException(409, "E2A_STOP_OBJECT_INTEGRITY_FAILURE", "停止证明对象来源或文件引用损坏");
    }

    public record Binding(UUID warehouseId, UUID scopeId, UUID submissionId, long workRound,
        UUID deviceId, UUID operatorId, UUID openKey) {
        public Binding {
            if (warehouseId == null || scopeId == null || submissionId == null || deviceId == null
                || operatorId == null || openKey == null || workRound < 1)
                throw new IllegalArgumentException("STOP_OBJECT_BINDING");
        }
    }

    public record StopObject(UUID id, UUID originalId, SourceEvidenceIntakeObjectFiles.Stored stored) { }
}
