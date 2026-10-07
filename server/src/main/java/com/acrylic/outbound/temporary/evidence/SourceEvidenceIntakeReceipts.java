package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import java.util.*;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Repository
public class SourceEvidenceIntakeReceipts {
    private static final String FIND_SQL="""
        SELECT receipt_version,canonical_request_bytes,request_sha256,original_response_bytes,original_response_sha256
        FROM source_evidence_intake_receipt
        WHERE principal_id=? AND route_scope=? AND original_key=?
        """;
    private static final String FIND_BOUND_SQL="""
        SELECT receipt_version,canonical_request_bytes,request_sha256,original_response_bytes,original_response_sha256,
          warehouse_id,submission_id,work_round,creator_device_id,temporary_authorization_id,
          original_operator_id,operation_kind,receipt_status
        FROM source_evidence_intake_receipt
        WHERE principal_id=? AND route_scope=? AND original_key=?
        """;
    private static final String FIND_BOUND_LOCK_SQL=FIND_BOUND_SQL+" FOR UPDATE";
    private final JdbcTemplate jdbc;

    public SourceEvidenceIntakeReceipts(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public record OriginalKey(UUID principalId,UUID originalKey) { }
    public record BoundKey(UUID principalId,UUID originalKey,UUID warehouseId,UUID submissionId,long workRound,
                           UUID creatorDeviceId,UUID authorizationId) { }

    @Transactional(readOnly=true,isolation=Isolation.READ_COMMITTED)
    public SourceEvidenceIntakeReplay.Decision existing(UUID principalId,UUID originalKey,
                                                         byte[] requestBytes) {
        var request=SourceEvidenceIntakeReplay.canonicalize(requestBytes);
        if(principalId==null || originalKey==null) throw new IllegalArgumentException("INTAKE_IDENTITY_REQUIRED");
        var rows=jdbc.queryForList(FIND_SQL,Ids.bytes(principalId),E2aProtocol.ROUTE_SCOPE,Ids.bytes(originalKey));
        if(rows.isEmpty()) return SourceEvidenceIntakeReplay.evaluate(request,null);
        if(rows.size()!=1) return new SourceEvidenceIntakeReplay.Decision(
            SourceEvidenceIntakeReplay.Status.INTEGRITY_FAILURE,null,SourceEvidenceIntakeReplay.TrustStatus.NOT_EVALUATED);
        return SourceEvidenceIntakeReplay.evaluate(request,stored(rows.getFirst()));
    }

    @Transactional(readOnly=true,isolation=Isolation.READ_COMMITTED,propagation=Propagation.REQUIRES_NEW)
    public SourceEvidenceIntakeReplay.Decision recheckAfterRollback(OriginalKey key,byte[] requestBytes) {
        if(key==null) throw new IllegalArgumentException("INTAKE_IDENTITY_REQUIRED");
        return existing(key.principalId(),key.originalKey(),requestBytes);
    }

    @Transactional(readOnly=true,isolation=Isolation.READ_COMMITTED)
    public SourceEvidenceIntakeReplay.Decision existingBound(BoundKey key,byte[] requestBytes) {
        if(key==null)throw new IllegalArgumentException("INTAKE_IDENTITY_REQUIRED");
        var request=SourceEvidenceIntakeReplay.canonicalize(requestBytes);
        var rows=jdbc.queryForList(FIND_BOUND_SQL,Ids.bytes(key.principalId()),E2aProtocol.ROUTE_SCOPE,Ids.bytes(key.originalKey()));
        return boundDecision(key,request,rows);
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public SourceEvidenceIntakeReplay.Decision existingBoundLocked(BoundKey key,byte[] requestBytes) {
        if(key==null)throw new IllegalArgumentException("INTAKE_IDENTITY_REQUIRED");
        var request=SourceEvidenceIntakeReplay.canonicalize(requestBytes);
        var rows=jdbc.queryForList(FIND_BOUND_LOCK_SQL,Ids.bytes(key.principalId()),E2aProtocol.ROUTE_SCOPE,Ids.bytes(key.originalKey()));
        return boundDecision(key,request,rows);
    }

    @Transactional(readOnly=true,isolation=Isolation.READ_COMMITTED)
    public SourceEvidenceIntakeOpen.Parsed openBound(BoundKey key) {
        if(key==null)throw new IllegalArgumentException("INTAKE_IDENTITY_REQUIRED");
        var rows=jdbc.queryForList(FIND_BOUND_SQL,Ids.bytes(key.principalId()),E2aProtocol.ROUTE_SCOPE,Ids.bytes(key.originalKey()));
        return parsedOpen(key,rows);
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public SourceEvidenceIntakeOpen.Parsed openBoundLocked(BoundKey key) {
        if(key==null)throw new IllegalArgumentException("INTAKE_IDENTITY_REQUIRED");
        var rows=jdbc.queryForList(FIND_BOUND_LOCK_SQL,Ids.bytes(key.principalId()),E2aProtocol.ROUTE_SCOPE,Ids.bytes(key.originalKey()));
        return parsedOpen(key,rows);
    }

    private SourceEvidenceIntakeOpen.Parsed parsedOpen(BoundKey key,List<Map<String,Object>> rows) {
        if(rows.isEmpty())throw new BusinessException(404,"E2A_OPEN_NOT_FOUND","原OPEN不存在或无权访问");
        if(rows.size()!=1)throw new BusinessException(409,"E2A_OPEN_INTEGRITY_FAILURE","原OPEN回执损坏");
        var row=rows.getFirst();
        if(!Arrays.equals(bytes(row,"warehouse_id"),Ids.bytes(key.warehouseId()))
            || !Arrays.equals(bytes(row,"submission_id"),Ids.bytes(key.submissionId()))
            || !(row.get("work_round") instanceof Number round) || round.longValue()!=key.workRound()
            || !Arrays.equals(bytes(row,"creator_device_id"),Ids.bytes(key.creatorDeviceId()))
            || !Arrays.equals(bytes(row,"temporary_authorization_id"),Ids.bytes(key.authorizationId())))
            throw new BusinessException(404,"E2A_OPEN_NOT_FOUND","原OPEN不存在或无权访问");
        try {
            byte[] request=bytes(row,"canonical_request_bytes");
            var canonical=SourceEvidenceIntakeReplay.canonicalize(request);
            if(boundDecision(key,canonical,rows).status()!=SourceEvidenceIntakeReplay.Status.REPLAY)
                throw new IllegalArgumentException("OPEN_RECEIPT_INTEGRITY");
            var open=SourceEvidenceIntakeOpen.parse(request,key.submissionId(),key.workRound());
            if(!open.sourceId().equals(key.authorizationId().toString()))throw new IllegalArgumentException("OPEN_SOURCE");
            return open;
        } catch(RuntimeException invalid) {
            throw new BusinessException(409,"E2A_OPEN_INTEGRITY_FAILURE","原OPEN回执损坏");
        }
    }

    private SourceEvidenceIntakeReplay.Decision boundDecision(BoundKey key,SourceEvidenceIntakeReplay.CanonicalRequest request,
                                                               List<Map<String,Object>> rows) {
        if(rows.isEmpty())return SourceEvidenceIntakeReplay.evaluate(request,null);
        if(rows.size()!=1)return invalid();
        var row=rows.getFirst();
        if(!Arrays.equals((byte[])row.get("original_operator_id"),Ids.bytes(key.principalId()))
            || !"OPEN".equals(row.get("operation_kind")) || !"RECEIVED".equals(row.get("receipt_status")))return invalid();
        if(!Arrays.equals((byte[])row.get("warehouse_id"),Ids.bytes(key.warehouseId()))
            || !Arrays.equals((byte[])row.get("submission_id"),Ids.bytes(key.submissionId()))
            || !(row.get("work_round") instanceof Number round) || round.longValue()!=key.workRound()
            || !Arrays.equals((byte[])row.get("creator_device_id"),Ids.bytes(key.creatorDeviceId()))
            || !Arrays.equals((byte[])row.get("temporary_authorization_id"),Ids.bytes(key.authorizationId())))
            return new SourceEvidenceIntakeReplay.Decision(SourceEvidenceIntakeReplay.Status.CONFLICT,null,
                SourceEvidenceIntakeReplay.TrustStatus.NOT_EVALUATED);
        return SourceEvidenceIntakeReplay.evaluate(request,stored(row));
    }

    @Transactional(readOnly=true,isolation=Isolation.READ_COMMITTED,propagation=Propagation.REQUIRES_NEW)
    public SourceEvidenceIntakeReplay.Decision recheckBoundAfterRollback(BoundKey key,byte[] requestBytes) {
        return existingBound(key,requestBytes);
    }

    private SourceEvidenceIntakeReplay.Decision invalid() {
        return new SourceEvidenceIntakeReplay.Decision(SourceEvidenceIntakeReplay.Status.INTEGRITY_FAILURE,null,
            SourceEvidenceIntakeReplay.TrustStatus.NOT_EVALUATED);
    }

    private static final String INSERT_SQL="""
        INSERT INTO source_evidence_intake_receipt
        (id,receipt_version,principal_id,route_scope,original_key,current_session_id,warehouse_id,scope_id,submission_id,
         work_round,creator_device_id,client_order_id,temporary_authorization_id,original_operator_id,source_recovery_epoch,
         operation_kind,receipt_status,canonical_request_bytes,request_sha256,original_response_bytes,original_response_sha256,
         signature_key_id,signed_receipt,object_id,evidence_set_id,evidence_verification_id,recorded_at)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NULL,NULL,NULL,?)
        """;

    @Transactional(propagation=Propagation.MANDATORY)
    public void insertOpen(OpenInsert insert,SourceEvidenceIntakeReceiptCodec.Receipt receipt) {
        if(insert==null || receipt==null)throw new IllegalArgumentException("OPEN_INSERT_REQUIRED");
        if(!insert.principalId().equals(insert.originalOperatorId()))throw new IllegalArgumentException("OPEN_PRINCIPAL_OPERATOR");
        if(jdbc.update(INSERT_SQL,Ids.bytes(receipt.id()),1,Ids.bytes(insert.principalId()),E2aProtocol.ROUTE_SCOPE,
            Ids.bytes(insert.originalKey()),Ids.bytes(insert.currentSessionId()),Ids.bytes(insert.warehouseId()),Ids.bytes(insert.scopeId()),Ids.bytes(insert.submissionId()),
            insert.workRound(),Ids.bytes(insert.creatorDeviceId()),Ids.bytes(insert.scopeClientOrderId()),Ids.bytes(insert.authorizationId()),
            Ids.bytes(insert.originalOperatorId()),insert.sourceRecoveryEpoch(),"OPEN","RECEIVED",insert.requestBytes(),insert.requestSha256(),
            receipt.originalResponseBytes(),receipt.originalResponseSha256(),receipt.signatureKeyId(),receipt.signedReceipt(),Timestamp.from(receipt.recordedAt()))!=1)
            throw new IllegalStateException("OPEN_RECEIPT_INSERT_COUNT");
    }

    private SourceEvidenceIntakeReplay.StoredReceipt stored(Map<String,Object> row) {
        return new SourceEvidenceIntakeReplay.StoredReceipt(number(row.get("receipt_version")),bytes(row,"canonical_request_bytes"),
            text(row,"request_sha256"),bytes(row,"original_response_bytes"),text(row,"original_response_sha256"));
    }

    private int number(Object value) { return value instanceof Number number?number.intValue():-1; }
    private byte[] bytes(Map<String,Object> row,String key) { return row.get(key) instanceof byte[] value?value:null; }
    private String text(Map<String,Object> row,String key) { return row.get(key)==null?null:row.get(key).toString(); }

    /** clientOrderId binds the signed current round; scopeClientOrderId preserves the root scope foreign key. */
    public record OpenInsert(UUID principalId,UUID originalKey,UUID warehouseId,UUID scopeId,UUID submissionId,long workRound,
                             UUID creatorDeviceId,UUID clientOrderId,UUID authorizationId,UUID originalOperatorId,
                             long sourceRecoveryEpoch,UUID currentSessionId,String userAuthVersion,String deviceVersion,
                             byte[] requestBytes,String requestSha256,UUID scopeClientOrderId) {
        public OpenInsert(UUID principalId,UUID originalKey,UUID warehouseId,UUID scopeId,UUID submissionId,long workRound,
            UUID creatorDeviceId,UUID clientOrderId,UUID authorizationId,UUID originalOperatorId,long sourceRecoveryEpoch,
            UUID currentSessionId,String userAuthVersion,String deviceVersion,byte[] requestBytes,String requestSha256) {
            this(principalId,originalKey,warehouseId,scopeId,submissionId,workRound,creatorDeviceId,clientOrderId,
                authorizationId,originalOperatorId,sourceRecoveryEpoch,currentSessionId,userAuthVersion,deviceVersion,
                requestBytes,requestSha256,clientOrderId);
        }
        public OpenInsert {
            if(principalId==null || originalKey==null || warehouseId==null || scopeId==null || submissionId==null
                || creatorDeviceId==null || clientOrderId==null || scopeClientOrderId==null || authorizationId==null || originalOperatorId==null
                || currentSessionId==null || userAuthVersion==null || userAuthVersion.isBlank()
                || deviceVersion==null || deviceVersion.isBlank() || workRound<1 || sourceRecoveryEpoch<0
                || requestBytes==null || requestSha256==null)throw new IllegalArgumentException("OPEN_INSERT_CONTEXT");
            requestBytes=requestBytes.clone();
        }
        @Override public byte[] requestBytes() { return requestBytes.clone(); }
    }
}
