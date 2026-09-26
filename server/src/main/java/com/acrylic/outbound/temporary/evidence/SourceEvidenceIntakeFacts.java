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
        var rows=jdbc.queryForList("""
            SELECT JSON_OBJECT('scopeId',BIN_TO_UUID(c.id),'warehouseId',BIN_TO_UUID(c.warehouse_id),
              'creatorDeviceId',BIN_TO_UUID(c.creator_device_id),'clientOrderId',BIN_TO_UUID(c.client_order_id),
              'submissionId',BIN_TO_UUID(s.id),'workRound',CAST(s.work_round AS CHAR),'scopeRound',CAST(c.work_round AS CHAR),
              'originalOperatorId',BIN_TO_UUID(s.operator_id),'freezeDeviceId',BIN_TO_UUID(s.freeze_device_id),
              'sourceRecoveryEpoch',CAST(s.source_recovery_epoch AS CHAR),'authorizationId',BIN_TO_UUID(a.id),
              'canonicalOrderId',BIN_TO_UUID(c.canonical_order_id),'currentSubmissionId',BIN_TO_UUID(c.current_submission_id))
            FROM submission s JOIN submission_scope c ON c.id=s.scope_id
            JOIN temporary_work_origin t ON t.creator_device_id=c.creator_device_id AND t.client_order_id=c.client_order_id
            JOIN temporary_authorization a ON a.id=t.authorization_id AND a.id=s.temporary_authorization_id
              AND a.device_id=c.creator_device_id AND a.device_id=s.freeze_device_id AND a.user_id=s.operator_id
              AND a.warehouse_id=c.warehouse_id AND a.recovery_epoch=s.source_recovery_epoch
            WHERE s.id=? AND c.source_order_id IS NULL AND s.assignment_id IS NULL AND s.continuation_id IS NULL
            """,String.class,Ids.bytes(submission));
        if(rows.size()!=1)throw new BusinessException(404,"E2A_SOURCE_NOT_FOUND","原来源不存在或无权访问");
        return new CommandContext(SubmissionJson.parse(rows.getFirst()));
    }
}
