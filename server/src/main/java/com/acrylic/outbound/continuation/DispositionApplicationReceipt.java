package com.acrylic.outbound.continuation;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Records the actual physical application in the same transaction as its stock effects. */
@Component
public class DispositionApplicationReceipt {
    private final JdbcTemplate jdbc;
    private final OfflineSigner signer;
    private final boolean enabled;
    public DispositionApplicationReceipt(JdbcTemplate jdbc,OfflineSigner signer,
        @org.springframework.beans.factory.annotation.Value("${outbound.corrections.commands.enabled:false}") boolean enabled) {
        this.jdbc=jdbc;this.signer=signer;this.enabled=enabled;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void record(SubmissionStore.Head head,JsonNode body,JsonNode proofs) {
        if(!enabled) return;
        var ids=jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM physical_disposition_evidence WHERE applied_submission_id=? ORDER BY id",
            String.class,Ids.bytes(head.control().id()));
        if(ids.isEmpty()) return;
        String scope=jdbc.queryForObject("SELECT BIN_TO_UUID(scope_id) FROM submission WHERE id=?",String.class,Ids.bytes(head.control().id()));
        var now=Instant.now().truncatedTo(ChronoUnit.MILLIS);
        var receipt=object().put("applicationVersion",1).put("status","DISPOSITIONS_APPLIED")
            .put("warehouseId",head.warehouse().toString()).put("scopeId",scope).put("orderId",head.order().toString())
            .put("operatorId",head.actor().toString()).put("issuedRecoveryEpoch",head.control().epoch())
            .put("payloadSha256",sha(body)).put("dispositionsSha256",sha(proofs)).put("appliedAt",now.toString())
            .put("localDeletionAllowed",false).put("mayResumeScanning",false).put("stockReleased",false);
        for(String field:java.util.List.of("submissionId","assignmentId","freezeDeviceId","continuationId","workRound")) receipt.set(field,body.path(field));
        var applied=receipt.putArray("appliedEvidenceIds");ids.forEach(applied::add);
        var claims=object().put("iss","acrylic-outbound").put("aud","acrylic-pda-disposition-application");claims.set("application",receipt.deepCopy());
        receipt.put("signedApplication",signer.sign(claims).token());
        byte[] bytes=canonical(receipt).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        jdbc.update("INSERT INTO formal_disposition_application VALUES(?,?,?,?,?,?)",Ids.bytes(head.control().id()),
            sha(body),sha(proofs),bytes,com.acrylic.outbound.inventory.ImportFiles.sha256(bytes),java.sql.Timestamp.from(now));
    }
}
