package com.acrylic.outbound.temporary.draft;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DraftEvidenceReceipts {
    public static final String AUDIENCE="acrylic-r06-local-draft-evidence-v1";
    private final JdbcTemplate jdbc;
    private final OfflineSigner signer;
    private final DraftEvidenceStore store;
    public DraftEvidenceReceipts(JdbcTemplate jdbc,OfflineSigner signer,DraftEvidenceStore store) { this.jdbc=jdbc;this.signer=signer;this.store=store; }

    public byte[] existing(UUID principal,UUID key,UUID evidence,String operation,byte[] request) {
        var rows=jdbc.queryForList("SELECT * FROM local_draft_evidence_receipt WHERE principal_id=? AND original_key=?",Ids.bytes(principal),Ids.bytes(key));
        if(rows.isEmpty())return null;
        var row=rows.getFirst();
        DraftEvidenceWire.require(rows.size()==1 && Arrays.equals((byte[])row.get("evidence_id"),Ids.bytes(evidence))
            && operation.equals(row.get("operation_kind")) && Arrays.equals((byte[])row.get("request_bytes"),request)
            && DraftEvidenceWire.sha(request).equals(row.get("request_sha256")));
        byte[] response=(byte[])row.get("response_bytes");
        DraftEvidenceWire.require(DraftEvidenceWire.sha(response).equals(row.get("response_sha256")));
        verifyResponse(response,principal,key,evidence,operation,request);return response;
    }
    public byte[] read(UUID principal,UUID key,UUID evidence) {
        var rows=jdbc.queryForList("SELECT operation_kind,request_bytes FROM local_draft_evidence_receipt WHERE principal_id=? AND original_key=? AND evidence_id=?",
            Ids.bytes(principal),Ids.bytes(key),Ids.bytes(evidence));
        if(rows.isEmpty())throw DraftEvidenceWire.missing();
        return existing(principal,key,evidence,(String)rows.getFirst().get("operation_kind"),(byte[])rows.getFirst().get("request_bytes"));
    }
    public byte[] insert(DraftEvidenceStore.Header header,UUID key,String operation,byte[] request,JsonNode detail) {
        ObjectNode receipt=SubmissionJson.object().put("receiptVersion",1).put("operationKind",operation).put("originalKey",key.toString())
            .put("evidenceId",header.evidence().toString()).put("clientOrderId",header.client().toString())
            .put("warehouseId",header.warehouse().toString()).put("creatorDeviceId",header.device().toString()).put("originalOperatorId",header.operator().toString())
            .put("rootSha256",header.root()).put("requestSha256",DraftEvidenceWire.sha(request)).put("recordedAt",Instant.now().toString())
            .put("status",operation.equals("VERIFY")?"ORIGINALS_STORED":"RECEIVED").put("businessFactsStatus","NOT_EVALUATED")
            .put("localDeletionAllowed",false).put("releaseAllowed",false).put("mayResumeScanning",false).put("writeConfirmationAvailable",false);
        receipt.set("detail",detail);
        var claims=SubmissionJson.object().put("iss","acrylic-outbound").put("aud",AUDIENCE).set("receipt",receipt.deepCopy());
        receipt.put("signedReceipt",signer.sign(claims).token());byte[] response=DraftEvidenceWire.bytes(receipt);
        jdbc.update("""
            INSERT INTO local_draft_evidence_receipt(principal_id,original_key,evidence_id,creator_device_id,warehouse_id,operation_kind,
              request_bytes,request_sha256,response_bytes,response_sha256,recorded_at) VALUES(?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(3))
            """,Ids.bytes(header.operator()),Ids.bytes(key),Ids.bytes(header.evidence()),Ids.bytes(header.device()),Ids.bytes(header.warehouse()),operation,
            request,DraftEvidenceWire.sha(request),response,DraftEvidenceWire.sha(response));
        return response;
    }
    private void verifyResponse(byte[] response,UUID principal,UUID key,UUID evidence,String operation,byte[] request) {
        try {
            var value=(ObjectNode)SubmissionJson.parse(response);
            DraftEvidenceWire.require(Arrays.equals(response,DraftEvidenceWire.bytes(value)));
            String token=SubmissionJson.text(value,"signedReceipt");
            String kid=SubmissionJson.parse(Base64.getUrlDecoder().decode(token.split("\\.",-1)[0])).path("kid").asText();
            var claims=DraftPreparationProof.verify(token,signer.publicKeyFor(kid),AUDIENCE);
            var unsigned=value.deepCopy();unsigned.remove("signedReceipt");
            DraftEvidenceWire.require(claims.size()==3 && unsigned.equals(claims.path("receipt")));
            var header=store.required(evidence);DraftEvidenceWire.require(header.operator().equals(principal));
            DraftEvidenceReceiptShape.check(unsigned,header,key,operation,request,store);
        } catch(RuntimeException invalid) { throw DraftEvidenceWire.unavailable(); }
    }
}
