package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.temporary.command.CommandContext;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Allows evidence transport for one activated, manifest-bound pending round without claiming it is current. */
final class SourceEvidencePendingHandover {
    private static final String PENDING="""
            SELECT COUNT(*) FROM submission s
            JOIN submission_scope c ON c.id=s.scope_id AND c.current_submission_id IS NULL AND c.work_round=s.work_round
            JOIN order_submission_slot l ON l.scope_id=c.id AND l.order_id=c.canonical_order_id
              AND l.work_round=c.work_round AND l.current_submission_id IS NULL AND l.phase='CONTINUING'
            JOIN outbound_order o ON o.id=l.order_id AND o.status='CONTINUING' AND o.current_result_version IS NULL
            JOIN temporary_handover_submission r ON r.submission_id=s.id AND r.scope_id=c.id AND r.to_work_round=s.work_round
            JOIN temporary_handover_activation_ticket t ON t.id=r.ticket_id AND t.scope_id=c.id
              AND t.canonical_order_id=o.id AND t.from_work_round=r.from_work_round AND t.to_work_round=r.to_work_round
            JOIN handover_case h ON h.id=t.case_id AND h.scope_id=c.id AND h.status='ACTIVATED' AND h.source_kind='TEMP'
            JOIN temporary_handover_activation a ON a.ticket_id=t.id AND a.to_work_round=s.work_round
              AND a.target_device_id=s.freeze_device_id AND a.target_user_id=s.operator_id
              AND a.new_authorization_id=s.temporary_authorization_id
            JOIN temporary_work_origin w ON w.creator_device_id=a.target_device_id AND w.client_order_id=a.new_client_order_id
              AND w.authorization_id=a.new_authorization_id
            JOIN temporary_authorization q ON q.id=w.authorization_id AND q.warehouse_id=c.warehouse_id
              AND q.device_id=s.freeze_device_id AND q.user_id=s.operator_id AND q.recovery_epoch=s.source_recovery_epoch
            JOIN submission_upload u ON u.submission_id=s.id AND u.byte_count>0
              AND u.chunk_count=CEILING(u.byte_count/262144.0)
            WHERE s.id=? AND c.id=? AND c.warehouse_id=? AND s.work_round=? AND s.status='RECEIVING'
              AND c.source_order_id IS NULL AND s.assignment_id IS NULL AND s.continuation_id IS NULL
              AND NOT EXISTS(SELECT 1 FROM submission_payload p WHERE p.submission_id=s.id)
              AND NOT EXISTS(SELECT 1 FROM temporary_source_continuation_submission n WHERE n.submission_id=s.id)
            """;
    static boolean matches(JdbcTemplate jdbc,CommandContext context,UUID submission) {
        if(!submission.equals(context.id("submissionId")))return false;
        if(submission.equals(context.id("currentSubmissionId")))return true;
        if(context.id("currentSubmissionId")!=null || context.id("canonicalOrderId")==null)return false;
        return jdbc.queryForObject(PENDING,Integer.class,Ids.bytes(submission),Ids.bytes(context.id("scopeId")),
            Ids.bytes(context.id("warehouseId")),context.version("workRound"))==1;
    }
}
