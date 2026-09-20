package com.acrylic.outbound.inventory;

import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class SubmissionArchiveIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    @Autowired OfflineSigner signer;@Autowired SubmissionArchiveProofs proofs;@Autowired SubmissionFiles files;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Value("${outbound.storage}") String storage;
    private SubmissionFixture f;private JsonNode receipt;
    @BeforeEach void fixture() throws Exception {
        f=new SubmissionFixture(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")),jdbc,false,false);
        receipt=f.submit();
    }
    @Test void durableCopiesProduceStableSignedConfirmationAndAndroidContract() throws Exception {
        var proof=confirm();proofs.verify(proof,"signedArchive","acrylic-pda-archive","archive");
        assertThat(confirm()).isEqualTo(proof);assertThat(count()).isEqualTo(1);
        assertThat(SubmissionJson.parse(files.read(proof.get("payloadSha256").asText()))).isEqualTo(f.body);
        assertThat(SubmissionJson.parse(files.read(proof.get("receiptSha256").asText()))).isEqualTo(receipt);
        assertThat(proof.get("localDeletionAllowed").asBoolean()).isFalse();
        assertThat(Instant.parse(proof.get("retainUntil").asText())).isEqualTo(
            Instant.parse(proof.get("archivedAt").asText()).atOffset(ZoneOffset.UTC).plusYears(5).toInstant());
        var fixture=json.createObjectNode();fixture.set("body",f.body);fixture.set("receipt",receipt);fixture.set("confirmation",proof);
        fixture.set("signingKey",json.valueToTree(signer.publicKey()));
        Files.writeString(Path.of("build/pda-archive-fixture.json"),json.writeValueAsString(fixture));
    }
    @Test void differentClientHashesCannotCreateOrReplaceConfirmation() throws Exception {
        for(String field:List.of("payloadSha256","receiptSha256"))
            f.pda.send(request(body().put(field,"0".repeat(64))),f.tokens).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ARCHIVE_CONTENT_MISMATCH"));
        assertThat(count()).isZero();var proof=confirm();
        f.pda.send(request(body().put("receiptSha256","f".repeat(64))),f.tokens).andExpect(status().isConflict());
        assertThat(confirm()).isEqualTo(proof);
    }
    @Test void missingOrCorruptPayloadNeverCreatesConfirmation() throws Exception {
        var path=path(SubmissionJson.sha(f.body));byte[] bytes=Files.readAllBytes(path);
        try {
            Files.delete(path);unavailable();assertThat(count()).isZero();
            Files.writeString(path,"corrupt");unavailable();assertThat(count()).isZero();
        } finally { Files.write(path,bytes); }
        assertThat(confirm().get("submissionId").asText()).isEqualTo(f.id());
    }
    @Test void lostReceiptFileIsNotSilentlyRecreatedOnRetry() throws Exception {
        var proof=confirm();var path=path(proof.get("receiptSha256").asText());byte[] bytes=Files.readAllBytes(path);
        try { Files.delete(path);unavailable();assertThat(count()).isEqualTo(1); }
        finally { Files.write(path,bytes); }
        assertThat(confirm()).isEqualTo(proof);
    }
    @Test void corruptReceiptTargetRollsBackConfirmation() throws Exception {
        var target=path(SubmissionJson.sha(receipt));Files.writeString(target,"invalid existing target");
        try { unavailable();assertThat(count()).isZero(); }
        finally { Files.delete(target); }
        assertThat(confirm()).isNotNull();
    }
    @Test @Transactional void unsignedDatabaseReceiptCannotBecomeTrustedArchive() throws Exception {
        var corrupt=receipt.deepCopy();((ObjectNode)corrupt).put("completedAt","2000-01-01T00:00:00Z");
        jdbc.update("UPDATE submission SET receipt_text=? WHERE id=UUID_TO_BIN(?)",corrupt.toString(),f.id());
        unavailable();assertThat(count()).isZero();
    }
    @Test @Transactional void changedStoredConfirmationIsRejectedEvenWhenFilesExist() throws Exception {
        var proof=(ObjectNode)confirm();proof.put("localDeletionAllowed",true);
        jdbc.update("UPDATE submission_archive SET confirmation_text=? WHERE submission_id=UUID_TO_BIN(?)",proof.toString(),f.id());
        unavailable();assertThat(count()).isEqualTo(1);
    }
    @Test void foreignDeviceCannotReadOrCreateAnArchive() throws Exception {
        var other=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));
        var pda=new PdaTestDriver(other);var tokens=pda.login(pda.register());
        pda.send(request(body()),tokens).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SUBMISSION_NOT_FOUND"));
        assertThat(count()).isZero();
    }
    @Test @Transactional void unresolvedStatesNeverReceiveConfirmation() throws Exception {
        for(String state:List.of("REGISTERED","RECEIVING","REVIEW_REQUIRED","REJECTED","ABORTED")) {
            jdbc.update("UPDATE submission SET status=? WHERE id=UUID_TO_BIN(?)",state,f.id());
            f.pda.send(request(body()),f.tokens).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ARCHIVE_NOT_READY"));
        }
        assertThat(count()).isZero();
    }
    @Test @Transactional void recoveryBarrierAndNewEpochDoNotReuseOldConfirmation() throws Exception {
        confirm();jdbc.update("UPDATE server_state SET service_mode='RECOVERY_PENDING' WHERE id=1");
        f.pda.send(request(body()),f.tokens).andExpect(status().isServiceUnavailable());
        jdbc.update("UPDATE server_state SET service_mode='NORMAL',recovery_epoch=2 WHERE id=1");
        f.pda.send(request(body()),f.tokens).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SERVER_RECOVERY_EPOCH_MISMATCH"));
        f.pda.send(post("/api/v1/pda/submissions/"+f.id()+"/archive-confirmation").header("X-Server-Recovery-Epoch","2")
            .contentType("application/json").content(body().toString()),f.tokens).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ORDER_RECOVERY_REQUIRED"));assertThat(count()).isEqualTo(1);
    }
    @Test void duplicateRequestsCommitOneIdenticalConfirmation() throws Exception {
        var start=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{start.await();return confirm();});var b=pool.submit(()->{start.await();return confirm();});
            start.countDown();assertThat(a.get(20,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(b.get(20,java.util.concurrent.TimeUnit.SECONDS));
        }
        assertThat(count()).isEqualTo(1);
    }
    @Test void malformedAndOversizeRequestsAreRejected() throws Exception {
        f.pda.send(request(body().put("deleteLocal",true)),f.tokens).andExpect(status().isBadRequest());
        f.pda.send(request(body().put("payloadSha256","bad")),f.tokens).andExpect(status().isBadRequest());
        f.pda.send(request(body().put("extra","x".repeat(1024))),f.tokens).andExpect(status().isPayloadTooLarge());
        assertThat(count()).isZero();
    }
    @Test void rolledBackConfirmationCanReuseDurableOrphanReceiptWithoutChangingBusinessResult() throws Exception {
        int dispatches=f.count("dispatch_record");
        var tx=new org.springframework.transaction.support.TransactionTemplate(transactions);
        assertThatThrownBy(()->tx.executeWithoutResult(status->{
            try { confirm(); } catch(Exception error) { throw new AssertionError(error); }
            throw new IllegalStateException("simulated commit failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(count()).isZero();assertThat(SubmissionJson.parse(files.read(SubmissionJson.sha(receipt)))).isEqualTo(receipt);
        assertThat(confirm().get("receiptSha256").asText()).isEqualTo(SubmissionJson.sha(receipt));
        assertThat(f.count("dispatch_record")).isEqualTo(dispatches);assertThat(f.submit()).isEqualTo(receipt);
    }
    @Test @Transactional void damagedArchiveIndexCannotMaskAValidSignedProof() throws Exception {
        confirm();jdbc.update("UPDATE submission_archive SET receipt_sha256=? WHERE submission_id=UUID_TO_BIN(?)","0".repeat(64),f.id());
        unavailable();
    }
    private ObjectNode body() { return json.createObjectNode().put("payloadSha256",SubmissionJson.sha(f.body)).put("receiptSha256",SubmissionJson.sha(receipt)); }
    private MockHttpServletRequestBuilder request(ObjectNode body) {
        return post("/api/v1/pda/submissions/"+f.id()+"/archive-confirmation").header("X-Server-Recovery-Epoch","1")
            .contentType("application/json").content(body.toString());
    }
    private JsonNode confirm() throws Exception { return f.pda.data(request(body()),f.tokens); }
    private void unavailable() throws Exception {
        f.pda.send(request(body()),f.tokens).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("ARCHIVE_EVIDENCE_UNAVAILABLE"));
    }
    private Path path(String hash) { return Path.of(storage,"submissions",hash+".json"); }
    private int count() { return jdbc.queryForObject("SELECT COUNT(*) FROM submission_archive WHERE submission_id=UUID_TO_BIN(?)",Integer.class,f.id()); }
}
