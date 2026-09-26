package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SourceEvidenceIntakeReadStore {
    private static final String FIND = """
        SELECT id,receipt_version,principal_id,route_scope,original_key,warehouse_id,scope_id,submission_id,
          work_round,creator_device_id,client_order_id,temporary_authorization_id,original_operator_id,
          source_recovery_epoch,operation_kind,receipt_status,canonical_request_bytes,request_sha256,
          original_response_bytes,original_response_sha256,signature_key_id,signed_receipt,
          object_id,evidence_set_id,evidence_verification_id
        FROM source_evidence_intake_receipt
        WHERE principal_id=? AND route_scope=? AND original_key=?
        """;
    private final JdbcTemplate jdbc;

    public SourceEvidenceIntakeReadStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<String, Object> find(UUID principalId, UUID originalKey) {
        var rows = jdbc.queryForList(FIND, Ids.bytes(principalId), E2aProtocol.ROUTE_SCOPE, Ids.bytes(originalKey));
        if (rows.isEmpty()) throw new BusinessException(404, "E2A_RECEIPT_NOT_FOUND", "原机接收回执不存在或无权访问");
        if (rows.size() != 1) throw new BusinessException(409, "E2A_RECEIPT_INTEGRITY_FAILURE", "原机接收回执不可核验");
        return java.util.Collections.unmodifiableMap(new java.util.HashMap<>(rows.getFirst()));
    }
}
