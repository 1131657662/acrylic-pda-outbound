package com.acrylic.outbound.temporary;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionStore;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.submissions.SubmissionJson.require;

/** New-mechanism baselines are observed facts, never retroactive business commands. */
final class TemporarySourceBaseline {
    private TemporarySourceBaseline() { }
    static void register(JdbcTemplate jdbc,UUID scope,UUID warehouse) {
        jdbc.update("INSERT INTO temporary_decision_state VALUES(?,0,'SOURCE_REGISTERED',UTC_TIMESTAMP(3))",Ids.bytes(scope));
        jdbc.update("INSERT INTO temporary_source_mapping VALUES(?,?,?,0,NULL,'SOURCE_REGISTERED',UTC_TIMESTAMP(3))",
            Ids.bytes(Ids.next()),Ids.bytes(warehouse),Ids.bytes(scope));
    }
    static void accept(JdbcTemplate jdbc,SubmissionStore.Head head,UUID order) {
        int changed=jdbc.update("UPDATE submission_scope SET canonical_order_id=?,mapping_version=1 WHERE current_submission_id=? AND canonical_order_id IS NULL AND mapping_version=0",
            Ids.bytes(order),Ids.bytes(head.control().id()));
        require(changed==1,"临时来源目标或映射版本已变化");
        jdbc.update("""
            INSERT INTO temporary_source_mapping(id,warehouse_id,scope_id,mapping_version,canonical_order_id,origin,observed_at)
            SELECT ?,warehouse_id,id,1,canonical_order_id,'INITIAL_ACCEPTANCE',UTC_TIMESTAMP(3) FROM submission_scope WHERE current_submission_id=?
            """,Ids.bytes(Ids.next()),Ids.bytes(head.control().id()));
    }
}
