package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.submissions.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.web.context.WebApplicationContext;

final class TemporaryArchiveVectors {
    private TemporaryArchiveVectors() { }
    static void write(WebApplicationContext context,Path attempt,JsonNode proof) throws java.io.IOException {
        var result=SubmissionJson.object();var signer=context.getBean(SubmissionArchiveProofs.class);
        var changes=SubmissionJson.object().put("warehouseId",UUID.randomUUID().toString()).put("operatorId",UUID.randomUUID().toString())
            .put("authorizationId",UUID.randomUUID().toString()).put("freezeDeviceId",UUID.randomUUID().toString()).put("workRound","2")
            .put("orderId",UUID.randomUUID().toString()).put("payloadSha256","f".repeat(64)).put("receiptSha256","f".repeat(64))
            .put("payloadBytes","1").put("receiptBytes","1").put("recoveryEpoch","2").put("sourceRecoveryEpoch","2")
            .put("mappingVersion","2").put("localDeletionAllowed",true).put("archiveVersion",1)
            .put("retainUntil","2000-01-01T00:00:00Z").put("commandResponseSha256","bad").put("commandBytes","0");
        changes.fields().forEachRemaining(field->{
            var changed=(ObjectNode)proof.deepCopy();changed.remove("signedArchive");changed.set(field.getKey(),field.getValue());
            var envelope=SubmissionJson.object().put("code","OK");envelope.set("data",signer.sign(changed));
            result.put(field.getKey(),SubmissionJson.canonical(envelope));
        });
        Files.writeString(attempt.resolve("archive-negative-vectors.json"),SubmissionJson.canonical(result));
    }
}
