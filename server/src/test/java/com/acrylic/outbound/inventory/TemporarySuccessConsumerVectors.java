package com.acrylic.outbound.inventory;

import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Synthetic test identities; positive receipts come from real internal decisions and HTTP GET. */
public final class TemporarySuccessConsumerVectors {
    private final WebApplicationContext context;
    public TemporarySuccessConsumerVectors(WebApplicationContext context) { this.context=context; }

    public void write(Path attempt) throws Exception {
        var normal=new TemporaryConfirmationFixture(context,false);
        var zero=new TemporaryConfirmationFixture(context,true,TemporarySuccessConsumerVectors::voidSecondProduct);
        var supervisor=zero.supervisor();zero.execute(zero.command("SHORT_APPROVE",supervisor),supervisor);
        var vectors=SubmissionJson.object();vectors.set("normal",success(normal));vectors.set("zeroProduct",success(zero));
        var path=Path.of("D:/acrylic-pda-outbound/server/build/pda-temporary-success-fixture.json");
        Files.writeString(path,SubmissionJson.canonical(vectors));
        Files.writeString(attempt.resolve("success-vectors.json"),SubmissionJson.canonical(vectors));
        var input=SubmissionJson.object().put("baseUrl","http://127.0.0.1:19434/api/v1/")
            .put("token",normal.source.f.tokens.path("accessToken").asText()).put("attempt",attempt.toString());
        input.set("vector",vectors.get("normal"));
        Files.writeString(attempt.resolve("private/android-success-input.json"),SubmissionJson.canonical(input));
    }

    private ObjectNode success(TemporaryConfirmationFixture f) throws Exception {
        var pending=f.transport.http(f.source,"/pda/submissions/"+f.source.id(),null);
        f.execute(f.command("NORMAL_CONFIRM",f.operator()),f.operator());
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1/pda/submissions/"+f.source.id()))
            .header("Authorization","Bearer "+f.source.f.tokens.path("accessToken").asText()).GET().build();
        var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        var receipt=(ObjectNode)SubmissionJson.parse(response.body()).path("data");
        assertThat(receipt.path("status").asText()).isEqualTo("SUCCEEDED");
        var result=SubmissionJson.object().put("payloadUtf8",SubmissionJson.canonical(f.source.body))
            .put("responseUtf8",response.body());result.set("pendingReceipt",pending);
        result.set("signingKey",new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(f.bean(OfflineSigner.class).publicKey()));
        result.set("negativeReceipts",negatives(receipt));
        result.set("conflictingSuccessReceipt",sign(receipt.deepCopy().put("orderNo","DIFFERENT-SIGNED-HISTORY")));return result;
    }

    private JsonNode negatives(ObjectNode original) {
        var result=SubmissionJson.object();
        Map<String,JsonNode> changes=new LinkedHashMap<>();
        var values=SubmissionJson.object().put("submissionId",UUID.randomUUID().toString()).put("freezeDeviceId",UUID.randomUUID().toString())
            .put("orderId",UUID.randomUUID().toString()).put("workRound","2").put("issuedRecoveryEpoch","2")
            .put("payloadSha256","0".repeat(64)).put("actualCount","99").put("actualWeightGrams","1")
            .put("captureHighWatermark","99").put("firstOrderScanSeq","99").put("lastOrderScanSeq","99")
            .put("inventoryDeducted",false).put("mayResumeScanning",true).put("stockReleased",true)
            .put("orderStatus","OPEN").put("resultVersion","2").put("receiptVersion",2).put("localDeletionAllowed",true);
        values.fields().forEachRemaining(entry->changes.put(entry.getKey(),entry.getValue()));
        changes.forEach((name,value)->{var changed=original.deepCopy();changed.set(name,value);result.set(name,sign(changed));});
        var source=original.deepCopy();((ObjectNode)source.path("source")).put("clientOrderId",UUID.randomUUID().toString());
        result.set("source",sign(source));
        var duplicate=original.deepCopy();duplicate.withArray("items").add(duplicate.path("items").get(0).deepCopy());
        result.set("duplicateProduct",sign(duplicate));
        var missing=original.deepCopy();missing.withArray("items").remove(missing.withArray("items").size()-1);
        result.set("missingProduct",sign(missing));
        var type=original.deepCopy().put("actualCount",original.path("actualCount").asLong());result.set("numberInsteadOfString",sign(type));
        return result;
    }

    private ObjectNode sign(ObjectNode receipt) {
        receipt.remove("signedReceipt");var claims=SubmissionJson.object().put("iss","acrylic-outbound").put("aud","acrylic-pda-receipt");
        claims.set("receipt",receipt.deepCopy());receipt.put("signedReceipt",context.getBean(OfflineSigner.class).sign(claims).token());return receipt;
    }

    private static void voidSecondProduct(TemporarySubmissionFixture f) {
        var body=f.body;var scan=(ObjectNode)body.withArray("scans").get(1);scan.put("status","VOID");
        var events=body.withArray("events");var frozen=(ObjectNode)events.remove(events.size()-1);
        events.add(frozen.deepCopy().put("id",UUID.randomUUID().toString()).put("localSeq","7")
            .put("type","VOID").put("captureId",scan.path("captureId").asText()).put("detail","卸回核对"));
        events.add(frozen.put("localSeq","8"));body.put("expectedCount","1").put("expectedWeightGrams",body.at("/scans/0/weightGrams").asText());
        ((ObjectNode)body.path("captureSummary")).put("saved","1").put("voided","1");
    }
}
