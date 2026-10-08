package com.acrylic.outbound.submissions;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.pda.OfflineSigner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Component
public class SubmissionReceipts {
    private final OfflineSigner signer;private final JdbcTemplate jdbc;
    public SubmissionReceipts(OfflineSigner signer,JdbcTemplate jdbc) { this.signer=signer;this.jdbc=jdbc; }
    public ObjectNode base(SubmissionStore.Head head,String status,String hash) {
        var c=head.control();var result=object().put("submissionId",c.id().toString()).put("orderId",head.order()==null?null:head.order().toString())
            .put("status",status).put("payloadSha256",hash).put("freezeDeviceId",c.device().toString()).put("workRound",Long.toString(c.round()))
            .put("issuedRecoveryEpoch",c.epoch()).put("mayResumeScanning",false).put("stockReleased",false);
        result.set("source",c.source());return result;
    }
    public ObjectNode success(SubmissionStore.Head head,JsonNode body,SubmissionSnapshot.Validated value,boolean deduct) {
        var result=base(head,"SUCCEEDED",sha(body));result.put("orderStatus","COMPLETED").put("actualCount",Long.toString(value.scans().size()))
            .put("actualWeightGrams",Long.toString(value.weight())).put("inventoryDeducted",deduct).put("resultVersion","1").put("receiptVersion",1)
            .put("captureHighWatermark",text(body,"captureHighWatermark")).put("firstOrderScanSeq",Long.toString(value.scans().getFirst().sequence()))
            .put("lastOrderScanSeq",Long.toString(value.scans().getLast().sequence())).put("completedAt",Instant.now().toString())
            .put("orderNo",jdbc.queryForObject("SELECT order_no FROM outbound_order WHERE id=?",String.class,Ids.bytes(head.order())));
        var items=result.putArray("items");value.products().forEach(p->items.add(object().put("deliveryCode",p.code())
            .put("actualCount",Long.toString(p.count())).put("actualWeightGrams",Long.toString(p.weight()))));
        if(head.control().temporary()!=null && head.control().round()>1)result.put("localDeletionAllowed",false);
        return sign(result);
    }
    public ObjectNode abort(SubmissionStore.Head head,String hash,String reason) {
        return sign(base(head,"ABORTED",hash).put("payloadPresent",hash!=null).put("reason",reason).put("abortedAt",Instant.now().toString()));
    }
    public ObjectNode continuationStop(SubmissionStore.Head head,String hash,JsonNode request) {
        var receipt=base(head,"ABORTED",hash).put("payloadPresent",true).put("reason",text(request,"reason"))
            .put("abortedAt",Instant.now().toString()).put("localDeletionAllowed",false).put("stopRequestSha256",sha(request));
        for(String field:java.util.List.of("sourceEvidenceSetId","verificationId","setSha256"))receipt.set(field,request.get(field));
        return sign(receipt);
    }
    public ObjectNode localFrozenHandoverStop(SubmissionStore.Head head,String localHash,JsonNode request) {
        var receipt=base(head,"ABORTED",null).put("payloadPresent",false).put("localFrozenPayloadSha256",localHash)
            .put("reason",text(request,"reason")).put("abortedAt",Instant.now().toString())
            .put("localDeletionAllowed",false).put("stopRequestSha256",sha(request));
        for(String field:java.util.List.of("sourceEvidenceSetId","verificationId","setSha256"))receipt.set(field,request.get(field));
        return sign(receipt);
    }
    private ObjectNode sign(ObjectNode receipt) {
        var claims=object().put("iss","acrylic-outbound").put("aud","acrylic-pda-receipt");claims.set("receipt",receipt.deepCopy());
        var signed=signer.sign(claims);receipt.put("signedReceipt",signed.token());return receipt;
    }
}
