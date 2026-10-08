package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.Ids;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** The receiving origin is proved by its immutable TEMP handover registration, never the scope's root creator. */
final class SourceEvidenceHandoverFacts {
    private SourceEvidenceHandoverFacts() { }
    static List<String> read(JdbcTemplate jdbc,UUID submission) {
        var installed=jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?",
            Integer.class,"temporary_handover_submission");
        if(installed==null || installed!=1)return List.of();
        return jdbc.queryForList("""
            SELECT JSON_OBJECT('scopeId',BIN_TO_UUID(c.id),'warehouseId',BIN_TO_UUID(c.warehouse_id),
              'creatorDeviceId',BIN_TO_UUID(a.device_id),'scopeClientOrderId',BIN_TO_UUID(c.client_order_id),
              'clientOrderId',BIN_TO_UUID(n.new_client_order_id),'submissionId',BIN_TO_UUID(s.id),
              'workRound',CAST(s.work_round AS CHAR),'scopeRound',CAST(c.work_round AS CHAR),
              'originalOperatorId',BIN_TO_UUID(s.operator_id),'freezeDeviceId',BIN_TO_UUID(s.freeze_device_id),
              'sourceRecoveryEpoch',CAST(s.source_recovery_epoch AS CHAR),'authorizationId',BIN_TO_UUID(a.id),
              'canonicalOrderId',BIN_TO_UUID(c.canonical_order_id),'currentSubmissionId',BIN_TO_UUID(c.current_submission_id))
            FROM submission s JOIN submission_scope c ON c.id=s.scope_id
            JOIN temporary_handover_submission r ON r.submission_id=s.id AND r.scope_id=c.id
              AND r.to_work_round=s.work_round AND r.from_work_round=s.work_round-1
            JOIN temporary_handover_activation n ON n.ticket_id=r.ticket_id AND n.to_work_round=r.to_work_round
              AND n.from_work_round=r.from_work_round AND n.new_authorization_id=s.temporary_authorization_id
              AND n.target_device_id=s.freeze_device_id AND n.target_user_id=s.operator_id
            JOIN temporary_work_origin o ON o.creator_device_id=n.target_device_id
              AND o.client_order_id=n.new_client_order_id AND o.authorization_id=n.new_authorization_id
            JOIN temporary_authorization a ON a.id=s.temporary_authorization_id AND a.device_id=s.freeze_device_id
              AND a.user_id=s.operator_id AND a.warehouse_id=c.warehouse_id AND a.recovery_epoch=s.source_recovery_epoch
            WHERE s.id=? AND c.source_order_id IS NULL AND s.assignment_id IS NULL AND s.continuation_id IS NULL
            """,String.class,Ids.bytes(submission));
    }
}
