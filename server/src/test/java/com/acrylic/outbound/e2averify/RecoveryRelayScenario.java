package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Actual web ZIP intake and original download; no processing or fabricated submission. */
final class RecoveryRelayScenario {
    final E2aCrossSourceProfile profile;
    final JdbcTemplate raw;
    final RecoveryRelayInput input;
    final ConfigurableApplicationContext web;
    final RecoveryWorkerHttp http;
    final String mode;
    RecoveryRelayScenario(E2aCrossSourceProfile profile, JdbcTemplate raw, RecoveryRelayInput input,
            ConfigurableApplicationContext web, String mode) throws Exception {
        this.profile=profile;this.raw=raw;this.input=input;this.web=web;this.mode=mode;
        http=peer("main");
    }

    void positive() throws Exception {
        http.get("/original-relays/"+UUID.randomUUID()+"?warehouseId="+input.warehouse(),401);
        http.login(input.username());byte[] bytes=input.archive();String key=UUID.randomUUID().toString();
        var receipt=upload(http,bytes,key,200).path("data");
        assertThat(upload(http,bytes,key,200).path("data")).isEqualTo(receipt);
        assertThat(upload(http,input.archive(),key,409).path("code").asText()).isEqualTo("IDEMPOTENCY_CONTENT_MISMATCH");
        http.get("/original-relays/"+receipt.path("relayId").asText()+"?warehouseId="+UUID.randomUUID(),404);
        var viewer=peer("viewer");viewer.login(input.viewer());
        assertThat(upload(viewer,bytes,UUID.randomUUID().toString(),403).path("code").asText()).isEqualTo("PERMISSION_DENIED");
        preserve(receipt,bytes,mode+"-positive");
    }

    void trustRejections() throws Exception {
        byte[] bytes=input.unverifiedArchive();
        var receipt=upload(http,bytes,UUID.randomUUID().toString(),200).path("data");
        assertThat(receipt.path("sourceTrust").asText()).isEqualTo("UNVERIFIED");
        preserve(receipt,bytes,"unverified-content");
        String base="/original-relays/"+receipt.path("relayId").asText();
        assertThat(http.retry(base+"/process?warehouseId="+input.warehouse(),UUID.randomUUID().toString(),409)
            .path("code").asText()).isEqualTo("ORIGINAL_NOT_FROZEN");
        assertThat(http.get(base+"/submission?warehouseId="+input.warehouse(),200).at("/data/state").asText())
            .isEqualTo("EVIDENCE_ONLY");
        upload(http,Arrays.copyOf(bytes,bytes.length-22),UUID.randomUUID().toString(),400);
    }

    JsonNode upload(RecoveryWorkerHttp client,byte[] bytes,String key,int expected) throws Exception {
        return client.relay(input.warehouse(),bytes,key,expected);
    }

    void preserve(JsonNode receipt,byte[] bytes,String name) throws Exception {
        assertThat(receipt.path("sourceTrust").asText()).isEqualTo(name.equals("unverified-content")?"UNVERIFIED":"TRUSTED");
        assertThat(receipt.path("receptionState").asText()).isEqualTo("ORIGINALS_STORED");
        assertThat(receipt.path("businessProcessed").asBoolean()).isFalse();
        assertThat(receipt.path("localDeletionAllowed").asBoolean()).isFalse();
        assertThat(receipt.path("submissionId").isNull()).isTrue();
        String id=receipt.path("relayId").asText();
        assertThat(http.get("/original-relays/"+id+"?warehouseId="+input.warehouse(),200).path("data")).isEqualTo(receipt);
        assertThat(http.relayOriginal(input.warehouse(),id,profile.attempt.resolve(name+"-download.zip"))).isEqualTo(bytes);
        assertThat(Files.readAllBytes(file(bytes))).isEqualTo(bytes);
        Files.write(profile.attempt.resolve(name+".zip"),bytes,StandardOpenOption.CREATE_NEW);
        save(name,receipt);
    }

    Path file(byte[] bytes) { return profile.attempt.resolve("storage/original-relays/"+ImportFiles.sha256(bytes)+".zip"); }
    RecoveryWorkerHttp peer(String suffix) throws Exception {
        return new RecoveryWorkerHttp(profile.attempt.resolve("private/http-relay-"+mode+"-"+suffix));
    }
    void save(String name,JsonNode facts) throws Exception {
        Files.writeString(profile.attempt.resolve("relay-"+name+".json"),facts.toPrettyString(),StandardOpenOption.CREATE_NEW);
    }

    void probe(String expected,String label) throws Exception {
        var config=SubmissionJson.parse(Files.readString(profile.attempt.resolve("private/p5-retirement/config.json")));
        var command=List.of(E2aWorktree.ROOT.resolve(".local/r09-control-venv/Scripts/python.exe").toString(),
            E2aWorktree.ROOT.resolve("docs/parallel/R09/tests/d13_writer_probe.py").toString(),
            Path.of(config.path("root").asText()).resolve("writers.lock").toString(),"--expect",expected);
        Path out=profile.attempt.resolve("relay-writer-"+label+".stdout");
        var child=new ProcessBuilder(command).redirectOutput(out.toFile())
            .redirectError(out.resolveSibling(out.getFileName()+".stderr").toFile()).start();
        assertThat(child.waitFor(15,TimeUnit.SECONDS)).isTrue();assertThat(child.exitValue()).isZero();
        assertThat(SubmissionJson.parse(Files.readString(out)).path("status").asText()).isEqualTo(expected);
    }
}
