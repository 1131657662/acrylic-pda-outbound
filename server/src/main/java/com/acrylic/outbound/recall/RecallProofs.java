package com.acrylic.outbound.recall;

import com.acrylic.outbound.pda.OfflineSigner;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Component
public class RecallProofs {
    private final OfflineSigner signer;
    public RecallProofs(OfflineSigner signer) { this.signer=signer; }
    public ObjectNode fault(RecallStore.Head h,UUID actor,RecallFaultStock.Review stock) {
        var value=object().put("changeRequestId",h.id().toString()).put("orderId",h.order().toString())
            .put("assignmentId",h.assignment().toString()).put("revision",h.revision()).put("issuedRecoveryEpoch",h.epoch())
            .put("status","WITHDRAWN").put("orderStatus","DRAFT").put("reason",h.reason()).put("resolvedBy",actor.toString())
            .put("originalDeviceStopReceived",false).put("mayResumeScanning",false).put("stockReleased",!stock.items().isEmpty())
            .put("restartRevision",Math.addExact(h.revision(),1)).put("physicalEvidence",stock.physical())
            .put("confirmedAt",Instant.now().toString());
        value.set("releasedBales",stock.manifest());
        var claims=object().put("iss","acrylic-outbound").put("aud","acrylic-pda-fault-withdrawal");
        claims.set("receipt",value.deepCopy());
        return value.put("signedReceipt",signer.sign(claims).token());
    }
    public ObjectNode sign(RecallStore.Head h,RecallStore.Package p,String status) {
        var value=object().put("changeRequestId",h.id().toString()).put("orderId",h.order().toString()).put("assignmentId",h.assignment().toString())
            .put("recoveryPackageId",p.id().toString()).put("payloadSha256",p.hash()).put("status",status).put("issuedRecoveryEpoch",h.epoch())
            .put("actualCount",Long.toString(p.count())).put("actualWeightGrams",Long.toString(p.grams())).put("captureHighWatermark",Long.toString(p.captureHigh()))
            .put("auditThroughSeq",Long.toString(p.auditHigh())).put("mayResumeScanning",false).put("stockReleased",false).put("confirmedAt",Instant.now().toString());
        var claims=object().put("iss","acrylic-outbound").put("aud","acrylic-pda-recall");claims.set("receipt",value.deepCopy());
        return value.put("signedReceipt",signer.sign(claims).token());
    }
}
