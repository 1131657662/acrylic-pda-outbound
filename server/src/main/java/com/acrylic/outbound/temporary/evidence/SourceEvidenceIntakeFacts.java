package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.command.CommandContext;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SourceEvidenceIntakeFacts {
    private final JdbcTemplate jdbc;

    public SourceEvidenceIntakeFacts(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public CommandContext read(UUID submission) {
        var handover=SourceEvidenceHandoverFacts.read(jdbc,submission);
        if(!handover.isEmpty()) {
            if(handover.size()!=1)throw new BusinessException(409,"E2A_HANDOVER_ORIGIN_CONFLICT","原交接来源绑定冲突");
            return new CommandContext(SubmissionJson.parse(handover.getFirst()));
        }
        var rows=continuationTablesInstalled() ? readWithContinuations(submission) : readInitialOnly(submission);
        if(rows.size()!=1)throw new BusinessException(404,"E2A_SOURCE_NOT_FOUND","原来源不存在或无权访问");
        return new CommandContext(SubmissionJson.parse(rows.getFirst()));
    }

    private boolean continuationTablesInstalled() {
        Integer count=jdbc.queryForObject("""
            SELECT COUNT(*) FROM information_schema.tables
            WHERE table_schema=DATABASE() AND table_name IN (?,?)
            """,Integer.class,"temporary_source_continuation_submission","temporary_source_continuation_activation");
        return count!=null && count==2;
    }

    private java.util.List<String> readInitialOnly(UUID submission) {
        return jdbc.queryForList("""
            SELECT JSON_OBJECT('scopeId',BIN_TO_UUID(c.id),'warehouseId',BIN_TO_UUID(c.warehouse_id),
              'creatorDeviceId',BIN_TO_UUID(c.creator_device_id),'scopeClientOrderId',BIN_TO_UUID(c.client_order_id),
              'clientOrderId',BIN_TO_UUID(c.client_order_id),
              'submissionId',BIN_TO_UUID(s.id),'workRound',CAST(s.work_round AS CHAR),'scopeRound',CAST(c.work_round AS CHAR),
              'originalOperatorId',BIN_TO_UUID(s.operator_id),'freezeDeviceId',BIN_TO_UUID(s.freeze_device_id),
              'sourceRecoveryEpoch',CAST(s.source_recovery_epoch AS CHAR),'authorizationId',BIN_TO_UUID(a.id),
              'canonicalOrderId',BIN_TO_UUID(c.canonical_order_id),'currentSubmissionId',BIN_TO_UUID(c.current_submission_id))
            FROM submission s JOIN submission_scope c ON c.id=s.scope_id AND s.work_round=1 AND c.work_round=1
            JOIN temporary_work_origin t ON t.creator_device_id=c.creator_device_id AND t.client_order_id=c.client_order_id
            JOIN temporary_authorization a ON a.id=s.temporary_authorization_id AND a.id=t.authorization_id
              AND a.device_id=c.creator_device_id AND a.device_id=s.freeze_device_id AND a.user_id=s.operator_id
              AND a.warehouse_id=c.warehouse_id AND a.recovery_epoch=s.source_recovery_epoch
            WHERE s.id=? AND c.source_order_id IS NULL AND s.assignment_id IS NULL AND s.continuation_id IS NULL
            """,String.class,Ids.bytes(submission));
    }

    private java.util.List<String> readWithContinuations(UUID submission) {
        return jdbc.queryForList("""
            SELECT JSON_OBJECT('scopeId',BIN_TO_UUID(c.id),'warehouseId',BIN_TO_UUID(c.warehouse_id),
              'creatorDeviceId',BIN_TO_UUID(COALESCE(n.target_device_id,c.creator_device_id)),'scopeClientOrderId',BIN_TO_UUID(c.client_order_id),
              'clientOrderId',BIN_TO_UUID(CASE WHEN s.work_round=1 THEN c.client_order_id ELSE n.new_client_order_id END),
              'submissionId',BIN_TO_UUID(s.id),'workRound',CAST(s.work_round AS CHAR),'scopeRound',CAST(c.work_round AS CHAR),
              'originalOperatorId',BIN_TO_UUID(s.operator_id),'freezeDeviceId',BIN_TO_UUID(s.freeze_device_id),
              'sourceRecoveryEpoch',CAST(s.source_recovery_epoch AS CHAR),'authorizationId',BIN_TO_UUID(a.id),
              'canonicalOrderId',BIN_TO_UUID(c.canonical_order_id),'currentSubmissionId',BIN_TO_UUID(c.current_submission_id))
            FROM submission s JOIN submission_scope c ON c.id=s.scope_id
            JOIN temporary_work_origin t ON t.creator_device_id=c.creator_device_id AND t.client_order_id=c.client_order_id
            LEFT JOIN temporary_source_continuation_submission r ON r.submission_id=s.id AND r.scope_id=c.id
              AND r.to_work_round=s.work_round AND r.from_work_round=s.work_round-1
            LEFT JOIN temporary_source_continuation_activation n ON n.ticket_id=r.ticket_id AND n.scope_id=c.id
              AND n.from_work_round=r.from_work_round
            LEFT JOIN temporary_work_origin fresh ON fresh.creator_device_id=n.target_device_id
              AND fresh.client_order_id=n.new_client_order_id AND fresh.authorization_id=n.new_authorization_id
            JOIN temporary_authorization a ON a.id=s.temporary_authorization_id
              AND ((s.work_round=1 AND a.id=t.authorization_id) OR (s.work_round>1 AND a.id=n.new_authorization_id
                AND fresh.authorization_id=a.id AND n.target_operator_id=s.operator_id AND n.target_device_id=s.freeze_device_id))
              AND a.device_id=COALESCE(n.target_device_id,c.creator_device_id) AND a.device_id=s.freeze_device_id AND a.user_id=s.operator_id
              AND a.warehouse_id=c.warehouse_id AND a.recovery_epoch=s.source_recovery_epoch
            WHERE s.id=? AND c.source_order_id IS NULL AND s.assignment_id IS NULL AND s.continuation_id IS NULL
            """,String.class,Ids.bytes(submission));
    }
}
