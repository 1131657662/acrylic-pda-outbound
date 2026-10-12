package com.acrylic.outbound.continuation;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Original physical approvals are retained in the same transaction as their evidence and idempotency key. */
@Component
public final class DispositionReceipts {
    private final JdbcTemplate jdbc;
    public DispositionReceipts(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public void record(UUID batch,UUID packageId,UUID actor,String requestHash,JsonNode command,JsonNode response) {
        byte[] commandBytes=SubmissionJson.canonical(command).getBytes(StandardCharsets.UTF_8);
        byte[] responseBytes=SubmissionJson.canonical(response).getBytes(StandardCharsets.UTF_8);
        jdbc.update("""
            INSERT INTO formal_disposition_receipt(approval_batch_id,recovery_package_id,principal_id,request_sha256,
              command_bytes,command_sha256,response_bytes,response_sha256,created_at) VALUES(?,?,?,?,?,?,?,?,UTC_TIMESTAMP(3))
            """,Ids.bytes(batch),Ids.bytes(packageId),Ids.bytes(actor),requestHash,commandBytes,
            com.acrylic.outbound.inventory.ImportFiles.sha256(commandBytes),responseBytes,
            com.acrylic.outbound.inventory.ImportFiles.sha256(responseBytes));
    }
}
