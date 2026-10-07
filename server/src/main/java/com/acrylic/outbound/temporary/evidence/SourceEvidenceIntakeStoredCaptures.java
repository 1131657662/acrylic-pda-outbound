package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.temporary.protocol.E2aPageItem;
import org.springframework.jdbc.core.JdbcTemplate;

/** Cumulative accepted originals can reference immutable captures stored by an earlier round of the same scope. */
final class SourceEvidenceIntakeStoredCaptures {
    private SourceEvidenceIntakeStoredCaptures() { }
    static void require(JdbcTemplate jdbc,SourceEvidenceIntakePageObjects.Binding binding,E2aPageItem item) {
        Integer count=binding.workRound()==1?jdbc.queryForObject("""
            SELECT COUNT(*) FROM submitted_capture
            WHERE id=? AND submission_id=? AND origin_device_id=? AND device_capture_seq=? AND record_sha256=?
            """,Integer.class,Ids.bytes(item.originalRecordId()),Ids.bytes(binding.submissionId()),
            Ids.bytes(binding.creatorDeviceId()),item.originalDeviceSeq(),item.recordSha256()):earlier(jdbc,binding,item);
        if(count==null || count!=1)throw new BusinessException(409,"E2A_CAPTURE_FACT_NOT_STORED","原机采集事实未完整保存在本来源");
    }
    private static Integer earlier(JdbcTemplate jdbc,SourceEvidenceIntakePageObjects.Binding binding,E2aPageItem item) {
        return jdbc.queryForObject("""
            SELECT COUNT(*) FROM submitted_capture r JOIN submission s ON s.id=r.submission_id
            JOIN submission_scope c ON c.id=s.scope_id AND c.warehouse_id=? AND c.creator_device_id=?
            WHERE r.id=? AND s.scope_id=? AND s.work_round<=? AND r.origin_device_id=?
              AND r.device_capture_seq=? AND r.record_sha256=?
            """,Integer.class,Ids.bytes(binding.warehouseId()),Ids.bytes(binding.creatorDeviceId()),
            Ids.bytes(item.originalRecordId()),Ids.bytes(binding.scopeId()),binding.workRound(),
            Ids.bytes(binding.creatorDeviceId()),item.originalDeviceSeq(),item.recordSha256());
    }
}
