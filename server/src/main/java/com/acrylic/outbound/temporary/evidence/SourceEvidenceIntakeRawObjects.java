package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class SourceEvidenceIntakeRawObjects {
    private static final Set<String> KINDS=Set.of("LOCAL_FROZEN_PAYLOAD","CAPTURE_RECORDS","AUDIT_RECORDS","AUDIT_ARCHIVE");
    private static final String FIND="""
        SELECT id,warehouse_id,origin_device_id,uploaded_by,bytes_sha256,byte_count,file_key
        FROM source_evidence_object
        WHERE scope_id=? AND submission_id=? AND work_round=? AND kind=? AND original_object_id=?
        """;
    private static final String INSERT="""
        INSERT INTO source_evidence_object
        (id,warehouse_id,scope_id,submission_id,work_round,kind,original_object_id,bytes_sha256,
         byte_count,file_key,origin_device_id,uploaded_by,received_at)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
        """;
    private final JdbcTemplate jdbc;

    public SourceEvidenceIntakeRawObjects(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @Transactional(propagation=Propagation.MANDATORY)
    public RawObject findAfterSourceLock(Binding binding) {
        if(binding==null)throw new IllegalArgumentException("RAW_OBJECT_BINDING");
        var rows=jdbc.queryForList(FIND,Ids.bytes(binding.scopeId()),Ids.bytes(binding.submissionId()),
            binding.workRound(),binding.kind(),Ids.bytes(binding.originalObjectId()));
        if(rows.isEmpty())return null;
        if(rows.size()!=1)throw corrupt();
        var row=rows.getFirst();
        if(!(row.get("id") instanceof byte[] id) || id.length!=16
            || !(row.get("warehouse_id") instanceof byte[] warehouse)
            || !(row.get("origin_device_id") instanceof byte[] device)
            || !(row.get("uploaded_by") instanceof byte[] operator)
            || !Arrays.equals(warehouse,Ids.bytes(binding.warehouseId()))
            || !Arrays.equals(device,Ids.bytes(binding.creatorDeviceId()))
            || !Arrays.equals(operator,Ids.bytes(binding.originalOperatorId()))
            || !(row.get("bytes_sha256") instanceof String hash) || !hash.matches("[0-9a-f]{64}")
            || !(row.get("byte_count") instanceof Number count) || count.longValue()<1 || count.longValue()>33554432
            || !(row.get("file_key") instanceof String key)
            || !key.equals("source-evidence-intake/objects/"+hash+".bin"))throw corrupt();
        var bytes=ByteBuffer.wrap(id);
        return new RawObject(new UUID(bytes.getLong(),bytes.getLong()),binding.originalObjectId(),
            new SourceEvidenceIntakeObjectFiles.Stored(key,hash,count.intValue()));
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public RawObject insert(Binding binding,SourceEvidenceIntakeObjectFiles.Stored stored,Instant receivedAt) {
        if(binding==null || stored==null || receivedAt==null)throw new IllegalArgumentException("RAW_OBJECT_INSERT");
        UUID id=Ids.next();
        if(jdbc.update(INSERT,Ids.bytes(id),Ids.bytes(binding.warehouseId()),Ids.bytes(binding.scopeId()),
            Ids.bytes(binding.submissionId()),binding.workRound(),binding.kind(),Ids.bytes(binding.originalObjectId()),
            stored.sha256(),stored.bytes(),stored.key(),Ids.bytes(binding.creatorDeviceId()),
            Ids.bytes(binding.originalOperatorId()),Timestamp.from(receivedAt))!=1)
            throw new IllegalStateException("RAW_OBJECT_INSERT_COUNT");
        return new RawObject(id,binding.originalObjectId(),stored);
    }

    private static BusinessException corrupt() {
        return new BusinessException(409,"E2A_RAW_OBJECT_INTEGRITY_FAILURE","原始对象来源或文件引用损坏");
    }

    public record Binding(UUID warehouseId,UUID scopeId,UUID submissionId,long workRound,UUID creatorDeviceId,
                          UUID originalOperatorId,String kind,UUID originalObjectId) {
        public Binding {
            if(warehouseId==null || scopeId==null || submissionId==null || creatorDeviceId==null
                || originalOperatorId==null || originalObjectId==null || workRound<1 || !KINDS.contains(kind))
                throw new IllegalArgumentException("RAW_OBJECT_BINDING");
        }
    }

    public record RawObject(UUID id,UUID originalObjectId,SourceEvidenceIntakeObjectFiles.Stored stored) { }
}
