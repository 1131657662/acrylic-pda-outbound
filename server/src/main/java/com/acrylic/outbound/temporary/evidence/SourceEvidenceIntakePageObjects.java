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
public class SourceEvidenceIntakePageObjects {
    private static final String FIND="""
        SELECT id,warehouse_id,origin_device_id,uploaded_by,bytes_sha256,byte_count,file_key
        FROM source_evidence_object
        WHERE scope_id=? AND submission_id=? AND work_round=? AND kind='MANIFEST_PAGE' AND original_object_id=?
        """;
    private static final String INSERT="""
        INSERT INTO source_evidence_object
        (id,warehouse_id,scope_id,submission_id,work_round,kind,original_object_id,bytes_sha256,
         byte_count,file_key,origin_device_id,uploaded_by,received_at)
        VALUES (?,?,?,?,?,'MANIFEST_PAGE',?,?,?,?,?,?,?)
        """;
    private final JdbcTemplate jdbc;

    public SourceEvidenceIntakePageObjects(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public static UUID originalId(UUID openKey,long pageNo) {
        if(openKey==null || pageNo<1 || pageNo>4096)throw new IllegalArgumentException("PAGE_OBJECT_ID");
        String name="acrylic-r06-e2a-page\u0000"+openKey+"\u0000"+pageNo;
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public PageObject locked(Binding binding,long pageNo) {
        return read(binding,pageNo,true);
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public PageObject findAfterSourceLock(Binding binding,long pageNo) {
        return read(binding,pageNo,false);
    }

    private PageObject read(Binding binding,long pageNo,boolean lock) {
        if(binding==null)throw new IllegalArgumentException("PAGE_OBJECT_BINDING");
        UUID original=originalId(binding.openKey(),pageNo);
        var rows=jdbc.queryForList(lock?FIND+" FOR UPDATE":FIND,Ids.bytes(binding.scopeId()),
            Ids.bytes(binding.submissionId()),binding.workRound(),Ids.bytes(original));
        if(rows.isEmpty())return null;
        if(rows.size()!=1)throw corrupt();
        var row=rows.getFirst();
        if(!Arrays.equals((byte[])row.get("warehouse_id"),Ids.bytes(binding.warehouseId()))
            || !Arrays.equals((byte[])row.get("origin_device_id"),Ids.bytes(binding.creatorDeviceId()))
            || !Arrays.equals((byte[])row.get("uploaded_by"),Ids.bytes(binding.originalOperatorId()))
            || !(row.get("id") instanceof byte[] rawId) || !(row.get("byte_count") instanceof Number count)
            || !(row.get("bytes_sha256") instanceof String sha) || !(row.get("file_key") instanceof String fileKey)
            || rawId.length!=16 || count.longValue()<1 || count.longValue()>262144
            || !sha.matches("[0-9a-f]{64}")
            || !fileKey.equals("source-evidence-intake/"+sha+".json"))throw corrupt();
        var stored=new SourceEvidenceIntakeFiles.Stored(fileKey,sha,count.intValue());
        var buffer=ByteBuffer.wrap(rawId);
        return new PageObject(new UUID(buffer.getLong(),buffer.getLong()),original,stored);
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public PageObject insert(Binding binding,long pageNo,SourceEvidenceIntakeFiles.Stored stored,Instant receivedAt) {
        if(binding==null || stored==null || receivedAt==null)throw new IllegalArgumentException("PAGE_OBJECT_INSERT");
        UUID original=originalId(binding.openKey(),pageNo), objectId=Ids.next();
        if(jdbc.update(INSERT,Ids.bytes(objectId),Ids.bytes(binding.warehouseId()),Ids.bytes(binding.scopeId()),
            Ids.bytes(binding.submissionId()),binding.workRound(),Ids.bytes(original),stored.sha256(),stored.bytes(),
            stored.key(),Ids.bytes(binding.creatorDeviceId()),Ids.bytes(binding.originalOperatorId()),Timestamp.from(receivedAt))!=1)
            throw new IllegalStateException("PAGE_OBJECT_INSERT_COUNT");
        return new PageObject(objectId,original,stored);
    }

    private static BusinessException corrupt() {
        return new BusinessException(409,"E2A_PAGE_OBJECT_INTEGRITY_FAILURE","已存页对象来源或文件引用损坏");
    }

    public record Binding(UUID warehouseId,UUID scopeId,UUID submissionId,long workRound,UUID creatorDeviceId,
                          UUID originalOperatorId,UUID openKey) {
        public Binding {
            if(warehouseId==null || scopeId==null || submissionId==null || creatorDeviceId==null
                || originalOperatorId==null || openKey==null || workRound<1)throw new IllegalArgumentException("PAGE_OBJECT_BINDING");
        }
    }

    public record PageObject(UUID id,UUID originalId,SourceEvidenceIntakeFiles.Stored stored) { }
}
