package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.submissions.SubmissionFiles;
import com.acrylic.outbound.temporary.command.*;
import com.acrylic.outbound.temporary.review.TemporaryDecisionCandidates;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

/** Synthetic stopped source followed by real ticket, intake, acknowledgement and confirmation HTTP. */
final class TemporaryRoundConfirmationHttpFixture implements AutoCloseable {
    final ContinuationReceiptHttpFixture http;
    final ObjectNode body,reopen,activate;
    final byte[] issued,activated;
    final JsonNode original;
    TemporaryRoundConfirmationHttpFixture(boolean shortShipment) throws Exception {
        this(shortShipment,true);
    }
    TemporaryRoundConfirmationHttpFixture(boolean shortShipment,boolean accept) throws Exception {
        http=new ContinuationReceiptHttpFixture("66",true,false);
        http.database.jdbc.update("INSERT IGNORE INTO user_role SELECT UUID_TO_BIN(?),id FROM role WHERE code IN ('TEMPORARY_ORDER_CREATOR','TEMPORARY_ORDER_CONFIRMER')",
            http.database.seed.get("operatorId"));
        original=parse(http.app.getBean(SubmissionFiles.class).read(http.database.seed.get("intakePredecessorHash")));
        var commands=new ContinuationReceiptHttpCommands(http);reopen=commands.request(null);
        issued=http.accepted(reopen,"confirmation-ticket");
        activate=commands.request(uuid(parse(issued).path("businessDecision"),"ticketId"));
        activated=http.accepted(activate,"confirmation-activation");
        body=new TemporaryRoundIntakeBody(http.database).next(original,parse(activated).path("businessDecision"));
        if(shortShipment)retainedOnly();
        if(accept) {
            var accepted=http.continuation(body,http.token,"confirmation-intake");
            assertThat(accepted.statusCode()).as(new String(accepted.body())).isEqualTo(200);
            assertThat(parse(accepted.body()).path("data").path("status").asText()).isEqualTo("REVIEW_REQUIRED");
        }
    }
    ObjectNode command(String kind) {
        var context=http.app.getBean(CommandFacts.class).read(uuid(body,"submissionId"));
        var result=object().put("commandVersion",1).put("commandType",kind).put("commandId",UUID.randomUUID().toString())
            .put("transportRecoveryEpoch","1").put("reason",kind.equals("NORMAL_CONFIRM")?"":"synthetic independent short approval");
        for(String key:List.of("warehouseId","submissionId","workRound","authorizationId","freezeDeviceId","sourceRecoveryEpoch"))
            result.set(key,context.json().get(key));
        result.putObject("source").put("kind","TEMP").put("creatorDeviceId",context.text("creatorDeviceId"))
            .put("clientOrderId",context.text("clientOrderId"));
        var candidates=http.app.getBean(TemporaryDecisionCandidates.class).snapshot(http.database.id("warehouseId"),uuid(body,"submissionId"),body);
        var expected=CommandExpected.current(context,"1");expected.set("candidateContextSha256",candidates.get("contextSha256"));
        result.set("expected",expected);return result;
    }
    void acknowledge() throws Exception {
        var auth=parse(activated).path("businessDecision").path("authorization");
        var ack=object().put("bundleId",text(auth,"bundleId")).put("manifestSha256",text(auth,"manifestSha256"))
            .put("authorizationEpoch",text(body,"authorizationEpoch")).put("activatedAt",Instant.now().toString());
        var path=http.database.profile.attempt.resolve("http");
        Files.writeString(path.resolve("new-authorization-ack-request.json"),canonical(ack),StandardOpenOption.CREATE_NEW);
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+http.app.getWebServer().getPort()
            +"/api/v1/pda/temporary-preparations/"+text(auth,"authorizationId")+"/download-ack"))
            .timeout(Duration.ofSeconds(30)).header("Authorization","Bearer "+http.token).header("Content-Type","application/json")
            .header("X-Server-Recovery-Epoch","1").header("Idempotency-Key",UUID.randomUUID().toString())
            .POST(HttpRequest.BodyPublishers.ofString(canonical(ack))).build();
        var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofByteArray());
        Files.write(path.resolve("new-authorization-ack-response.json"),response.body(),StandardOpenOption.CREATE_NEW);
        Files.writeString(path.resolve("new-authorization-ack-status.txt"),Integer.toString(response.statusCode()));
        assertThat(response.statusCode()).as(new String(response.body())).isEqualTo(200);
    }
    void reject(ObjectNode command,String code,String label) throws Exception {
        reject(command,http.token,code,label);
    }
    void reject(ObjectNode command,String token,String code,String label) throws Exception {
        var response=http.post(command,token,label);
        assertThat(response.statusCode()).as(label+": "+new String(response.body())).isIn(403,409);
        assertThat(parse(response.body()).path("code").asText()).as(label).isEqualTo(code);
    }
    private void retainedOnly() {
        array(body,"captures").remove(4);array(body,"scans").remove(3);
        for(int i=0;i<3;i++)array(body,"events").remove(13);
        ((ObjectNode)array(body,"events").get(13)).put("localSeq","14");
        body.put("captureHighWatermark","4").put("expectedCount","3").put("expectedWeightGrams","3000")
            .put("shortShipmentReason","synthetic retained only");
        ((ObjectNode)body.path("captureSummary")).put("received","4").put("saved","3");
    }
    @Override public void close() { http.close(); }
}
