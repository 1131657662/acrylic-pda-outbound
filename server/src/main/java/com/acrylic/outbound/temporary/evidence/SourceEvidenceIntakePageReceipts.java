package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class SourceEvidenceIntakePageReceipts {
    private static final String FIND="""
        SELECT receipt_version,canonical_request_bytes,request_sha256,original_response_bytes,original_response_sha256,
          warehouse_id,submission_id,work_round,creator_device_id,temporary_authorization_id,
          original_operator_id,operation_kind,receipt_status,object_id
        FROM source_evidence_intake_receipt
        WHERE principal_id=? AND route_scope=? AND original_key=?
        """;
    private static final String INSERT="""
        INSERT INTO source_evidence_intake_receipt
        (id,receipt_version,principal_id,route_scope,original_key,current_session_id,warehouse_id,scope_id,submission_id,
         work_round,creator_device_id,client_order_id,temporary_authorization_id,original_operator_id,source_recovery_epoch,
         operation_kind,receipt_status,canonical_request_bytes,request_sha256,original_response_bytes,original_response_sha256,
         signature_key_id,signed_receipt,object_id,evidence_set_id,evidence_verification_id,recorded_at)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NULL,NULL,?)
        """;
    private final JdbcTemplate jdbc;

    public SourceEvidenceIntakePageReceipts(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @Transactional(readOnly=true,isolation=Isolation.READ_COMMITTED)
    public SourceEvidenceIntakeReplay.Decision existing(SourceEvidenceIntakeReceipts.BoundKey key,byte[] requestBytes) {
        return lookup(key,requestBytes,false);
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public SourceEvidenceIntakeReplay.Decision existingLocked(SourceEvidenceIntakeReceipts.BoundKey key,byte[] requestBytes) {
        return lookup(key,requestBytes,true);
    }

    @Transactional(readOnly=true,isolation=Isolation.READ_COMMITTED,propagation=Propagation.REQUIRES_NEW)
    public SourceEvidenceIntakeReplay.Decision recheckAfterRollback(SourceEvidenceIntakeReceipts.BoundKey key,byte[] requestBytes) {
        return lookup(key,requestBytes,false);
    }

    private SourceEvidenceIntakeReplay.Decision lookup(SourceEvidenceIntakeReceipts.BoundKey key,byte[] bytes,boolean locked) {
        if(key==null)throw new IllegalArgumentException("PAGE_RECEIPT_KEY");
        var request=SourceEvidenceIntakeReplay.canonicalize(bytes);
        String sql=locked?FIND+" FOR UPDATE":FIND;
        var rows=jdbc.queryForList(sql,Ids.bytes(key.principalId()),E2aProtocol.ROUTE_SCOPE,Ids.bytes(key.originalKey()));
        if(rows.isEmpty())return SourceEvidenceIntakeReplay.evaluate(request,null);
        if(rows.size()!=1)return invalid();
        return decision(key,request,rows.getFirst());
    }

    private SourceEvidenceIntakeReplay.Decision decision(SourceEvidenceIntakeReceipts.BoundKey key,
        SourceEvidenceIntakeReplay.CanonicalRequest request,Map<String,Object> row) {
        if(!Arrays.equals((byte[])row.get("original_operator_id"),Ids.bytes(key.principalId()))
            || !"PAGE".equals(row.get("operation_kind")) || !"PARTIAL".equals(row.get("receipt_status"))
            || !(row.get("object_id") instanceof byte[] object) || object.length!=16)return invalid();
        if(!Arrays.equals((byte[])row.get("warehouse_id"),Ids.bytes(key.warehouseId()))
            || !Arrays.equals((byte[])row.get("submission_id"),Ids.bytes(key.submissionId()))
            || !(row.get("work_round") instanceof Number round) || round.longValue()!=key.workRound()
            || !Arrays.equals((byte[])row.get("creator_device_id"),Ids.bytes(key.creatorDeviceId()))
            || !Arrays.equals((byte[])row.get("temporary_authorization_id"),Ids.bytes(key.authorizationId())))
            return new SourceEvidenceIntakeReplay.Decision(SourceEvidenceIntakeReplay.Status.CONFLICT,null,
                SourceEvidenceIntakeReplay.TrustStatus.NOT_EVALUATED);
        var stored=new SourceEvidenceIntakeReplay.StoredReceipt(version(row.get("receipt_version")),
            value(row,"canonical_request_bytes"),text(row,"request_sha256"),value(row,"original_response_bytes"),
            text(row,"original_response_sha256"));
        return SourceEvidenceIntakeReplay.evaluate(request,stored);
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public void insert(PageInsert input,SourceEvidenceIntakeReceiptCodec.Receipt receipt) {
        if(input==null || receipt==null)throw new IllegalArgumentException("PAGE_RECEIPT_INSERT");
        var context=input.context();
        if(!context.principalId().equals(context.originalOperatorId()))throw new IllegalArgumentException("PAGE_PRINCIPAL_OPERATOR");
        if(jdbc.update(INSERT,Ids.bytes(receipt.id()),1,Ids.bytes(context.principalId()),E2aProtocol.ROUTE_SCOPE,
            Ids.bytes(context.originalKey()),Ids.bytes(context.currentSessionId()),Ids.bytes(context.warehouseId()),
            Ids.bytes(context.scopeId()),Ids.bytes(context.submissionId()),context.workRound(),Ids.bytes(context.creatorDeviceId()),
            Ids.bytes(context.scopeClientOrderId()),Ids.bytes(context.authorizationId()),Ids.bytes(context.originalOperatorId()),
            context.sourceRecoveryEpoch(),"PAGE","PARTIAL",context.requestBytes(),context.requestSha256(),
            receipt.originalResponseBytes(),receipt.originalResponseSha256(),receipt.signatureKeyId(),receipt.signedReceipt(),
            Ids.bytes(input.objectId()),Timestamp.from(receipt.recordedAt()))!=1)throw new IllegalStateException("PAGE_RECEIPT_INSERT_COUNT");
    }

    private SourceEvidenceIntakeReplay.Decision invalid() {
        return new SourceEvidenceIntakeReplay.Decision(SourceEvidenceIntakeReplay.Status.INTEGRITY_FAILURE,null,
            SourceEvidenceIntakeReplay.TrustStatus.NOT_EVALUATED);
    }
    private static int version(Object value) { return value instanceof Number number?number.intValue():-1; }
    private static byte[] value(Map<String,Object> row,String key) { return row.get(key) instanceof byte[] bytes?bytes:null; }
    private static String text(Map<String,Object> row,String key) { return row.get(key) instanceof String text?text:null; }

    public record PageInsert(SourceEvidenceIntakeReceipts.OpenInsert context,UUID objectId) {
        public PageInsert { if(context==null || objectId==null)throw new IllegalArgumentException("PAGE_RECEIPT_CONTEXT"); }
    }
}
