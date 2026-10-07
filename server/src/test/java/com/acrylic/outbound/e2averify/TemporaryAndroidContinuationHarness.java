package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.temporary.command.*;
import com.acrylic.outbound.temporary.review.TemporaryDecisionCandidates;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

/** Standalone test process so Android's Gradle build never overlaps a server Gradle build. */
public final class TemporaryAndroidContinuationHarness {
    private TemporaryAndroidContinuationHarness() { }
    public static void main(String[] args) throws Exception {
        assertThat(System.getenv("B4_RETENTION_VERIFY")).isEqualTo("true");
        try(var http=new ContinuationReceiptHttpFixture("66",true,false,true)) {
            if(Boolean.parseBoolean(System.getenv("D04_BINDINGS_VERIFY")))new TemporaryBindingHttpChecks(http).prepare();
            http.database.jdbc.update("INSERT IGNORE INTO user_role SELECT UUID_TO_BIN(?),id FROM role WHERE code='TEMPORARY_ORDER_CREATOR'",http.database.seed.get("operatorId"));
            var commands=new ContinuationReceiptHttpCommands(http);
            var issued=http.accepted(commands.request(null),"android-ticket");
            var activated=http.accepted(commands.request(uuid(parse(issued).path("businessDecision"),"ticketId")),"android-activation");
            Path attempt=http.database.profile.attempt;
            export(http,activated);
            if(Boolean.parseBoolean(System.getenv("B4_CONTINUATION_HIGHER")))
                activated=TemporaryAndroidHigherRounds.advance(http);
            await(attempt.resolve("android-uploaded.json"));
            var upload=parse(Files.readString(attempt.resolve("android-uploaded.json")));
            TemporaryRoundLinkedFixture linked=null;
            if(Boolean.parseBoolean(System.getenv("B4_CONTINUATION_LINK")) && !Boolean.parseBoolean(System.getenv("B4_CONTINUATION_LINK_BEFORE_STOP"))) {
                var original=parse(http.app.getBean(com.acrylic.outbound.submissions.SubmissionFiles.class).read(text(upload,"payloadSha256")));
                linked=new TemporaryRoundLinkedFixture(http,original);
                http.accepted(confirmation(http,uuid(upload,"submissionId"),"LINK"),"android-human-link");
            }
            var command=confirmation(http,uuid(upload,"submissionId"),"NORMAL_CONFIRM");
            var response=http.post(command,http.otherToken,"android-independent-confirm");
            assertThat(response.statusCode()).as(new String(response.body())).isEqualTo(200);
            verify(http,upload,activated);
            if(linked!=null)linked.effects();
            assertThat(http.post(command,http.otherToken,"android-independent-replay").body()).isEqualTo(response.body());
            Files.writeString(attempt.resolve("android-server-confirmed.marker"),"confirmed",StandardOpenOption.CREATE_NEW);
            await(attempt.resolve("android-completed.json"));
            Files.writeString(attempt.resolve("test-completed.marker"),"Android Room + actual HTTP/MySQL, independent confirmation and signed success. Synthetic stopped source and identities.\n",StandardOpenOption.CREATE_NEW);
        }
    }

    private static void export(ContinuationReceiptHttpFixture http,byte[] activated) throws Exception {
        Path attempt=http.database.profile.attempt;
        var input=object().put("baseUrl","http://127.0.0.1:"+http.app.getWebServer().getPort()+"/api/v1/")
            .put("token",http.token).put("activationUtf8",new String(activated,java.nio.charset.StandardCharsets.UTF_8))
            .put("chunked",Boolean.parseBoolean(System.getenv("B4_CONTINUATION_CHUNKS")))
            .put("higher",Boolean.parseBoolean(System.getenv("B4_CONTINUATION_HIGHER")))
            .put("linked",Boolean.parseBoolean(System.getenv("B4_CONTINUATION_LINK")))
            .put("linkBeforeStop",Boolean.parseBoolean(System.getenv("B4_CONTINUATION_LINK_BEFORE_STOP")));
        if(input.path("higher").asBoolean())TemporaryAndroidHigherRounds.device(http,input);
        input.set("vector",parse(Files.readString(attempt.resolve("private/android-source.json"))));
        Files.writeString(attempt.resolve("private/android-input.json"),canonical(input),StandardOpenOption.CREATE_NEW);
        Files.writeString(attempt.resolve("android-ready.marker"),Instant.now().toString(),StandardOpenOption.CREATE_NEW);
    }

    static ObjectNode confirmation(ContinuationReceiptHttpFixture http,UUID submission,String kind) {
        var context=http.app.getBean(CommandFacts.class).read(submission);
        var command=object().put("commandVersion",1).put("commandType",kind)
            .put("commandId",UUID.randomUUID().toString()).put("transportRecoveryEpoch","1")
            .put("reason",kind.equals("NORMAL_CONFIRM")?"":"Synthetic reviewed formal target");
        for(String key:List.of("warehouseId","submissionId","workRound","authorizationId","freezeDeviceId","sourceRecoveryEpoch"))command.set(key,context.json().get(key));
        command.putObject("source").put("kind","TEMP").put("creatorDeviceId",context.text("creatorDeviceId")).put("clientOrderId",context.text("clientOrderId"));
        var body=parse(http.app.getBean(com.acrylic.outbound.submissions.SubmissionFiles.class).read(context.text("receivedPayloadSha256")));
        var candidates=http.app.getBean(TemporaryDecisionCandidates.class).snapshot(http.database.id("warehouseId"),submission,body);
        var expected=CommandExpected.current(context,"1");expected.set("candidateContextSha256",candidates.get("contextSha256"));
        command.set("expected",expected);return command;
    }

    private static void verify(ContinuationReceiptHttpFixture http,JsonNode upload,byte[] activation) {
        var jdbc=http.database.jdbc;String submission=text(upload,"submissionId");
        assertThat(jdbc.queryForObject("SELECT status FROM submission WHERE id=UUID_TO_BIN(?)",String.class,submission)).isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT payload_sha256 FROM submission_payload WHERE submission_id=UUID_TO_BIN(?)",String.class,submission)).isEqualTo(text(upload,"payloadSha256"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE submission_id=UUID_TO_BIN(?) AND state='DISPATCHED'",Integer.class,submission)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT status FROM temporary_source_continuation WHERE id=UUID_TO_BIN(?)",String.class,text(parse(activation).path("businessDecision"),"ticketId"))).isEqualTo("COMPLETED");
    }

    static void await(Path marker) throws Exception {
        long end=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(15);
        while(!Files.exists(marker) && System.nanoTime()<end) {
            assertThat(Files.exists(marker.getParent().resolve("android-failed.marker"))).as("Android consumer failed; preserve this attempt").isFalse();
            Thread.sleep(200);
        }
        assertThat(Files.exists(marker)).as("Missing consumer checkpoint: "+marker.getFileName()).isTrue();
    }
}
