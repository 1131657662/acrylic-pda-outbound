package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Separate physical application does not turn a pending batch into final dispatch. */
public final class OpenFormalQuarantineHttpChecks {
    private final FormalQuarantineHttpSource source;
    private final CorrectionAttachmentHttp maker,checker;
    public OpenFormalQuarantineHttpChecks(FormalQuarantineHttpSource source) throws Exception {
        this.source=source;maker=new CorrectionAttachmentHttp(source.makerUser,source.evidence.resolve("maker-open.log"));
        checker=new CorrectionAttachmentHttp(source.checkerUser,source.evidence.resolve("checker-open.log"));
    }
    public void rejectsUnrelatedSignedTerminal(com.acrylic.outbound.pda.OfflineSigner signer) throws Exception {
        String id=source.appliedBody.path("submissionId").asText();
        var saved=source.jdbc.queryForMap("SELECT status,receipt_text FROM submission WHERE id=UUID_TO_BIN(?)",id);
        String target="/corrections/quarantine/context?warehouseId="+source.warehouse+"&baleId="+source.bale+"&commandType=RECONCILE_QUARANTINE_DISCOVER";
        try {
            for(String field:java.util.List.of("submissionId","orderId","payloadSha256")) {
                var receipt=SubmissionJson.object().put("status","SUCCEEDED").put("submissionId",id).put("orderId",source.order)
                    .put("payloadSha256",SubmissionJson.sha(source.appliedBody));
                receipt.put(field,field.equals("payloadSha256")?"0".repeat(64):UUID.randomUUID().toString());
                var claims=SubmissionJson.object().put("iss","acrylic-outbound").put("aud","acrylic-pda-receipt");claims.set("receipt",receipt.deepCopy());
                receipt.put("signedReceipt",signer.sign(claims).token());
                source.jdbc.update("UPDATE submission SET status='SUCCEEDED',receipt_text=? WHERE id=UUID_TO_BIN(?)",SubmissionJson.canonical(receipt),id);
                maker.get(target,409);
            }
        } finally { source.jdbc.update("UPDATE submission SET status=?,receipt_text=? WHERE id=UUID_TO_BIN(?)",saved.get("status"),saved.get("receipt_text"),id); }
    }
    public void verify(Path attempt) throws Exception {
        String target="/corrections/quarantine/context?warehouseId="+source.warehouse+"&baleId="+source.bale+"&commandType=RECONCILE_QUARANTINE_DISCOVER";
        var original=SubmissionJson.parse(maker.get(target,200)).at("/context/facts/origin/originalReceipt");
        assertThat(original.at("/application/receipt/status").asText()).isEqualTo("DISPOSITIONS_APPLIED");
        assertThat(original.at("/application/receipt/localDeletionAllowed").asBoolean()).isFalse();
        assertThat(source.jdbc.queryForObject("SELECT status FROM submission WHERE id=UUID_TO_BIN(?)",String.class,source.appliedBody.path("submissionId").asText())).isEqualTo("REVIEW_REQUIRED");
        immutability();drift(target);new FormalQuarantineHttpChecks(source,attempt).verify();
        var approval=SubmissionJson.object().put("decision","APPROVE").put("reason","ISOLATED later independent remaining-bale review")
            .put("payloadSha256",SubmissionJson.sha(source.appliedBody));
        checker.post("/submissions/"+source.appliedBody.path("submissionId").asText()+"/review",UUID.randomUUID().toString(),approval,200);
        java.util.List<Path> files;
        try(var stream=Files.list(source.evidence)) { files=stream.filter(path->path.getFileName().toString().endsWith("-command.json")).toList(); }
        var command=files.stream().map(path->{try{return SubmissionJson.parse(Files.readAllBytes(path));}catch(Exception e){throw new RuntimeException(e);}})
            .filter(row->row.path("commandType").asText().equals("RECONCILE_QUARANTINE_APPROVE")).findFirst().orElseThrow();
        byte[] proof=checker.get("/corrections/quarantine/original-receipt?warehouseId="+source.warehouse+"&caseId="+command.path("caseId").asText()+"&commandType=RECONCILE_QUARANTINE_APPROVE",200);
        assertThat(proof).containsExactly(Files.readAllBytes(source.evidence.resolve("original-formal-proof.json")));
        assertThat(source.jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_record WHERE order_id=UUID_TO_BIN(?)",Integer.class,source.order)).isEqualTo(1);
    }
    private void immutability() {
        assertThatThrownBy(()->source.jdbc.update("UPDATE formal_disposition_application SET receipt_sha256=REPEAT('0',64)"))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->source.jdbc.update("DELETE FROM formal_disposition_application")).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    private void drift(String path) throws Exception {
        String id=source.appliedBody.path("submissionId").asText();
        source.jdbc.update("UPDATE submission_payload SET payload_sha256=REPEAT('0',64) WHERE submission_id=UUID_TO_BIN(?)",id);
        try { maker.get(path,409); }
        finally { source.jdbc.update("UPDATE submission_payload SET payload_sha256=? WHERE submission_id=UUID_TO_BIN(?)",SubmissionJson.sha(source.appliedBody),id); }
    }
}
