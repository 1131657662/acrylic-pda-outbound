package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CommandFacts {
    private final JdbcTemplate jdbc;private final SubmissionFiles files;
    public CommandFacts(JdbcTemplate jdbc,SubmissionFiles files) { this.jdbc=jdbc;this.files=files; }
    public CommandContext hint(UUID submission) {
        var rows=jdbc.queryForList("""
            SELECT JSON_OBJECT('scopeId',BIN_TO_UUID(c.id),'warehouseId',BIN_TO_UUID(c.warehouse_id),
             'creatorDeviceId',BIN_TO_UUID(c.creator_device_id),'clientOrderId',BIN_TO_UUID(c.client_order_id),
             'submissionId',BIN_TO_UUID(s.id),'originalOperatorId',BIN_TO_UUID(s.operator_id),
             'authorizationId',BIN_TO_UUID(s.temporary_authorization_id),'canonicalOrderId',BIN_TO_UUID(c.canonical_order_id))
            FROM submission s JOIN submission_scope c ON c.id=s.scope_id
            WHERE s.id=? AND c.source_order_id IS NULL AND s.temporary_authorization_id IS NOT NULL
            """,String.class,Ids.bytes(submission));
        if(rows.size()!=1)throw CommandErrors.missing();
        return new CommandContext(SubmissionJson.parse(rows.getFirst()));
    }
    public CommandContext read(UUID submission) {
        var rows=jdbc.queryForList("""
            SELECT JSON_OBJECT('scopeId',BIN_TO_UUID(c.id),'warehouseId',BIN_TO_UUID(c.warehouse_id),
             'creatorDeviceId',BIN_TO_UUID(c.creator_device_id),'clientOrderId',BIN_TO_UUID(c.client_order_id),
             'submissionId',BIN_TO_UUID(s.id),'workRound',CAST(s.work_round AS CHAR),'status',s.status,
             'authorizationId',BIN_TO_UUID(a.id),'authorizationEpoch',CAST(a.authorization_epoch AS CHAR),
             'authorizationUserVersion',CAST(a.user_auth_version AS CHAR),'authorizationDeviceVersion',CAST(a.device_version AS CHAR),
             'freezeDeviceId',BIN_TO_UUID(s.freeze_device_id),'originalOperatorId',BIN_TO_UUID(s.operator_id),
             'sourceRecoveryEpoch',CAST(s.source_recovery_epoch AS CHAR),'canonicalOrderId',BIN_TO_UUID(c.canonical_order_id),
             'mappingId',BIN_TO_UUID(m.id),'mappingVersion',CAST(c.mapping_version AS CHAR),'mappingOrderId',BIN_TO_UUID(m.canonical_order_id),
             'currentSubmissionId',BIN_TO_UUID(c.current_submission_id),'scopeRound',CAST(c.work_round AS CHAR),
             'decisionVersion',CAST(d.decision_version AS CHAR),'slotVersion',CAST(l.slot_version AS CHAR),
             'slotScopeId',BIN_TO_UUID(l.scope_id),'slotSubmissionId',BIN_TO_UUID(l.current_submission_id),
             'slotRound',CAST(l.work_round AS CHAR),'targetRevision',CAST(o.current_revision AS CHAR),
             'receivedPayloadSha256',p.payload_sha256,'declaredUploadSha256',u.payload_sha256,
             'evidenceSetId',BIN_TO_UUID(es.id),'evidenceSetSha256',es.set_sha256,
             'verificationId',BIN_TO_UUID(ev.id),'verificationVersion',CAST(ev.verification_version AS CHAR),
             'verificationAlgorithmVersion',CAST(ev.algorithm_version AS CHAR),'dispositionManifestSha256',NULL,
             'dispositionManifestVersion',NULL)
            FROM submission s JOIN submission_scope c ON c.id=s.scope_id
            JOIN temporary_work_origin t ON t.creator_device_id=c.creator_device_id AND t.client_order_id=c.client_order_id
            JOIN temporary_authorization a ON a.id=t.authorization_id AND a.id=s.temporary_authorization_id
             AND a.device_id=c.creator_device_id AND a.device_id=s.freeze_device_id AND a.user_id=s.operator_id
             AND a.warehouse_id=c.warehouse_id AND a.recovery_epoch=s.source_recovery_epoch
            LEFT JOIN temporary_source_mapping m ON m.scope_id=c.id AND m.mapping_version=c.mapping_version
            JOIN temporary_decision_state d ON d.scope_id=c.id
            LEFT JOIN outbound_order o ON o.id=c.canonical_order_id AND o.warehouse_id=c.warehouse_id
            LEFT JOIN order_submission_slot l ON l.order_id=o.id
            LEFT JOIN submission_payload p ON p.submission_id=s.id LEFT JOIN submission_upload u ON u.submission_id=s.id
            LEFT JOIN source_evidence_set es ON es.scope_id=c.id AND es.submission_id=s.id AND es.work_round=s.work_round
            LEFT JOIN source_evidence_verification ev ON ev.set_id=es.id AND ev.scope_id=es.scope_id
            WHERE s.id=? AND c.source_order_id IS NULL AND s.assignment_id IS NULL AND s.continuation_id IS NULL
            """,String.class,Ids.bytes(submission));
        if(rows.size()!=1)throw CommandErrors.missing();
        var node=(ObjectNode)SubmissionJson.parse(rows.getFirst());return complete(node);
    }
    private CommandContext complete(ObjectNode node) {
        if (node.get("canonicalOrderId") != null && !node.get("canonicalOrderId").isNull())
            CommandErrors.integrity(node.get("canonicalOrderId").equals(node.get("mappingOrderId")));
        String hash=node.path("receivedPayloadSha256").isNull()?null:node.path("receivedPayloadSha256").asText();
        node.putNull("planSha256");
        if(hash!=null)node.set("planSha256",SubmissionJson.parse(files.read(hash)).get("planSha256"));
        return new CommandContext(node);
    }
}
