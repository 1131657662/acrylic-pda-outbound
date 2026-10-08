package com.acrylic.outbound.submissions;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Fixed source policy only. This does not authorize a TEMP decision or resolve review issues. */
final class SubmissionAccountingPolicy {
    private SubmissionAccountingPolicy() { }

    static boolean require(JdbcTemplate jdbc, SubmissionStore.Head head, JsonNode body) {
        if (head.control().temporary() == null) {
            return jdbc.queryForObject("SELECT p.auto_deduct_inventory FROM order_publication p "
                + "INNER JOIN order_assignment a ON a.publication_id=p.id WHERE a.id=?",
                Boolean.class, Ids.bytes(head.control().assignment()));
        }
        var control=head.control().round()==1?SubmissionControl.read(uuid(body,"submissionId"),body)
            :body.has("temporaryHandover")?com.acrylic.outbound.handover.TemporaryHandoverControl.read(body).control()
            :com.acrylic.outbound.temporary.continuation.TemporaryRoundControl.read(body).control();
        equal(control, head.control(), "记账原控制身份不符");
        var policy = temporary(jdbc, head);
        equal(policy.payload(), sha(body), "记账正文与原受理摘要不符");
        equal(policy.version(), text(body, "policyVersion"), "记账原政策版本不符");
        SubmissionJson.require(body.path("autoDeductInventory").isBoolean(), "固定扣账模式无效");
        equal(policy.deduct(), body.path("autoDeductInventory").booleanValue(), "记账固定扣账模式不符");
        return policy.deduct();
    }

    private static final String TEMPORARY_QUERY="""
            SELECT p.version,p.auto_deduct_inventory,v.payload_sha256
            FROM submission s JOIN submission_scope c ON c.id=s.scope_id
            LEFT JOIN temporary_work_origin t ON t.creator_device_id=c.creator_device_id AND t.client_order_id=c.client_order_id
            LEFT JOIN temporary_source_continuation_submission r ON r.submission_id=s.id AND r.scope_id=c.id AND r.to_work_round=s.work_round
            LEFT JOIN temporary_source_continuation_activation n ON n.ticket_id=r.ticket_id AND n.scope_id=r.scope_id AND n.from_work_round=r.from_work_round
              AND n.new_authorization_id=s.temporary_authorization_id AND n.target_device_id=s.freeze_device_id AND n.target_operator_id=s.operator_id
            LEFT JOIN temporary_work_origin co ON co.creator_device_id=n.target_device_id AND co.client_order_id=n.new_client_order_id
              AND co.authorization_id=n.new_authorization_id
            LEFT JOIN temporary_handover_submission hr ON hr.submission_id=s.id AND hr.scope_id=c.id AND hr.to_work_round=s.work_round
            LEFT JOIN temporary_handover_activation ha ON ha.ticket_id=hr.ticket_id AND ha.new_authorization_id=s.temporary_authorization_id
              AND ha.from_work_round=hr.from_work_round AND ha.to_work_round=hr.to_work_round
              AND ha.target_device_id=s.freeze_device_id AND ha.target_user_id=s.operator_id
            LEFT JOIN temporary_work_origin ho ON ho.creator_device_id=ha.target_device_id AND ho.client_order_id=ha.new_client_order_id
              AND ho.authorization_id=ha.new_authorization_id
            JOIN temporary_authorization a ON a.id=s.temporary_authorization_id
              AND ((s.work_round=1 AND a.id=t.authorization_id) OR (s.work_round>1 AND r.from_work_round=s.work_round-1
                AND a.id=n.new_authorization_id AND co.authorization_id=a.id
                AND n.new_client_order_id=CAST(? AS BINARY(16)) AND n.ticket_id=r.ticket_id) OR
                (s.work_round>1 AND hr.from_work_round=s.work_round-1 AND ho.authorization_id=a.id))
            JOIN policy_version p ON p.id=a.policy_id AND p.warehouse_id=a.warehouse_id
            JOIN submission_payload v ON v.submission_id=s.id
            JOIN outbound_order o ON o.id=c.canonical_order_id AND o.warehouse_id=c.warehouse_id
            WHERE s.id=CAST(? AS BINARY(16)) AND s.status='REVIEW_REQUIRED' AND c.work_round=s.work_round
              AND c.current_submission_id=s.id AND c.source_order_id IS NULL
              AND o.id=CAST(? AS BINARY(16)) AND o.current_result_version IS NULL
              AND ((c.mapping_version=1 AND o.source='PDA_TEMP' AND o.status='REVIEW_REQUIRED')
                OR (c.mapping_version>1 AND o.source='WEB' AND o.status IN ('READY','REVIEW_REQUIRED')
                    AND EXISTS(SELECT 1 FROM temporary_source_mapping m WHERE m.scope_id=c.id
                      AND m.mapping_version=c.mapping_version AND m.canonical_order_id=o.id
                      AND m.origin='CONTROLLED_LINK')))
              AND a.id=CAST(? AS BINARY(16)) AND a.warehouse_id=c.warehouse_id AND a.warehouse_id=CAST(? AS BINARY(16))
              AND a.device_id=COALESCE(ha.target_device_id,n.target_device_id,c.creator_device_id) AND a.device_id=s.freeze_device_id AND a.device_id=CAST(? AS BINARY(16))
              AND a.user_id=s.operator_id AND a.user_id=CAST(? AS BINARY(16)) AND a.recovery_epoch=s.source_recovery_epoch
              AND COALESCE(ha.target_device_id,n.target_device_id,c.creator_device_id)=CAST(? AS BINARY(16))
              AND COALESCE(ha.new_client_order_id,n.new_client_order_id,c.client_order_id)=CAST(? AS BINARY(16))
              AND ((s.work_round=1 AND n.ticket_id IS NULL AND ha.ticket_id IS NULL)
                OR (s.work_round>1 AND ((n.ticket_id IS NOT NULL AND ha.ticket_id IS NULL)
                  OR (n.ticket_id IS NULL AND ha.ticket_id IS NOT NULL))))
            """;
    private static Policy temporary(JdbcTemplate jdbc, SubmissionStore.Head head) {
        // Explicit binary types avoid coercion of client-prepared parameters across nullable origin joins.
        var rows = jdbc.query(TEMPORARY_QUERY, (rs, n) -> new Policy(rs.getString(1), rs.getBoolean(2), rs.getString(3)),
            Ids.bytes(head.control().temporary().client()),Ids.bytes(head.control().id()), Ids.bytes(head.order()), Ids.bytes(head.control().temporary().authorization()),
            Ids.bytes(head.warehouse()), Ids.bytes(head.control().device()), Ids.bytes(head.actor()),
            Ids.bytes(head.control().temporary().creator()), Ids.bytes(head.control().temporary().client()));
        if (rows.size() != 1) throw new BusinessException(409, "TEMPORARY_ACCOUNTING_CONTEXT_CHANGED", "临时记账原来源或当前目标已变化");
        return rows.getFirst();
    }

    private record Policy(String version, boolean deduct, String payload) { }
}
