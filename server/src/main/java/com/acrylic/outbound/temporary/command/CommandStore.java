package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.temporary.protocol.TemporaryProtocol;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CommandStore {
    private final JdbcTemplate jdbc;
    public CommandStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public CommandRecord find(UUID actor,String key) {
        var rows=jdbc.queryForList("""
            SELECT r.*,q.id request_id,q.principal_id principal_id,q.route_scope route_scope,q.idempotency_key original_key,
             q.request_sha256 request_sha256,q.resource_id resource_id
            FROM api_idempotency_request q LEFT JOIN temporary_command_receipt r ON r.api_request_id=q.id
            WHERE q.principal_id=? AND q.route_scope=? AND q.idempotency_key=?
            """,Ids.bytes(actor),TemporaryProtocol.ROUTE_SCOPE,key);
        return rows.isEmpty()?null:new CommandRecord(rows.getFirst());
    }
    public CommandRecord byReceipt(UUID receipt) {
        var row=jdbc.queryForMap("SELECT BIN_TO_UUID(q.principal_id) principal,q.idempotency_key FROM temporary_command_receipt r "
            + "JOIN api_idempotency_request q ON q.id=r.api_request_id WHERE r.id=?",Ids.bytes(receipt));
        return find(UUID.fromString(row.get("principal").toString()),row.get("idempotency_key").toString());
    }
    public void reserve(CommandWrite write) {
        jdbc.update("""
            INSERT INTO api_idempotency_request(id,principal_id,route_scope,idempotency_key,request_sha256,resource_id,created_at)
            VALUES(?,?,?,?,?,?,?)
            """,Ids.bytes(write.requestId()),Ids.bytes(UUID.fromString(write.actor().path("principalId").asText())),
            TemporaryProtocol.ROUTE_SCOPE,write.command().key(),write.command().digest(),Ids.bytes(write.receiptId()),Timestamp.from(write.recordedAt()));
    }
    public void advance(CommandWrite write) {
        int count=jdbc.update("UPDATE temporary_decision_state SET decision_version=decision_version+1 WHERE scope_id=? AND decision_version=?",
            Ids.bytes(write.context().id("scopeId")),write.context().version("decisionVersion"));
        if(count!=1)throw CommandErrors.stale();
    }
    public void receipt(CommandWrite w,CommandReceiptCodec.Receipt r) {
        var c=w.context();
        jdbc.update("""
            INSERT INTO temporary_command_receipt(id,api_request_id,warehouse_id,scope_id,submission_id,work_round,
             creator_device_id,client_order_id,temporary_authorization_id,original_operator_id,source_recovery_epoch,
             original_canonical_order_id,original_mapping_id,evidence_set_id,evidence_verification_id,
             command_version,command_type,canonical_command_bytes,command_sha256,receipt_version,
             original_response_bytes,original_response_sha256,unsigned_receipt_sha256,signature_key_id,signed_receipt,recorded_at)
             VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,CASE WHEN ? IN ('PHYSICAL_DISPOSITION','CLOSE_DISPOSITION','REOPEN_WORK','ACTIVATE_CONTINUATION') THEN ? ELSE NULL END,
             CASE WHEN ? IN ('PHYSICAL_DISPOSITION','CLOSE_DISPOSITION','REOPEN_WORK','ACTIVATE_CONTINUATION') THEN ? ELSE NULL END,1,?,?,?,1,?,?,?,?,?,?)
            """,Ids.bytes(w.receiptId()),Ids.bytes(w.requestId()),Ids.bytes(c.id("warehouseId")),Ids.bytes(c.id("scopeId")),
            Ids.bytes(c.id("submissionId")),c.version("workRound"),Ids.bytes(c.id("creatorDeviceId")),Ids.bytes(c.id("clientOrderId")),
            Ids.bytes(c.id("authorizationId")),Ids.bytes(c.id("originalOperatorId")),c.version("sourceRecoveryEpoch"),
            c.id("canonicalOrderId")==null?null:Ids.bytes(c.id("canonicalOrderId")),Ids.bytes(c.id("mappingId")),
            w.command().kind(),w.command().expected().path("evidenceSetId").isNull()?null:Ids.bytes(UUID.fromString(w.command().expected().path("evidenceSetId").asText())),
            w.command().kind(),w.command().expected().path("verificationId").isNull()?null:Ids.bytes(UUID.fromString(w.command().expected().path("verificationId").asText())),
            w.command().kind(),w.command().bytes(),w.command().digest(),r.original(),r.originalSha256(),r.unsignedSha256(),
            r.keyId(),r.token(),Timestamp.from(w.recordedAt()));
    }
    public void event(CommandWrite w,CommandReceiptCodec.Receipt r) {
        jdbc.update("""
            INSERT INTO temporary_decision_event(id,scope_id,resulting_decision_version,receipt_id,decision_kind,context_bytes,recorded_at)
            VALUES(?,?,?,?,?,?,?)
            """,Ids.bytes(w.eventId()),Ids.bytes(w.context().id("scopeId")),Math.addExact(w.context().version("decisionVersion"),1),
            Ids.bytes(w.receiptId()),CommandDecision.kind(com.acrylic.outbound.submissions.SubmissionJson.parse(r.unsigned())),r.unsigned(),Timestamp.from(w.recordedAt()));
    }
    public Map<String,Object> event(UUID receipt) {
        var rows=jdbc.queryForList("SELECT * FROM temporary_decision_event WHERE receipt_id=?",Ids.bytes(receipt));
        CommandErrors.integrity(rows.size()==1);return rows.getFirst();
    }
    public Map<String,Object> mapping(UUID id) {
        var rows=jdbc.queryForList("SELECT * FROM temporary_source_mapping WHERE id=?",Ids.bytes(id));
        CommandErrors.integrity(rows.size()==1);return rows.getFirst();
    }
    public List<Map<String,Object>> audits(UUID receipt) {
        return jdbc.queryForList("SELECT actor_id,details,event_type FROM operation_event WHERE entity_type='TEMPORARY_COMMAND' AND entity_id=?",
            Ids.bytes(receipt));
    }
}
