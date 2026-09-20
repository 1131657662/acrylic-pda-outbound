package com.acrylic.outbound.inventory;

import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class AuditArchiveIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    @Autowired OfflineSigner signer;@Autowired SubmissionArchiveProofs signatures;
    @Value("${outbound.storage}") String storage;
    private SubmissionFixture f;private AuditArchiveFixture a;
    @BeforeEach void fixture() throws Exception {
        f=new SubmissionFixture(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")),jdbc,false,false);
        a=new AuditArchiveFixture(f);a.upload();
    }
    @Test void stableSignedSegmentsProduceAndroidContract() throws Exception {
        var first=a.confirm(0,3);var second=a.confirm(3,7);assertThat(a.confirm(0,3)).isEqualTo(first);
        signatures.verify(first,"signedArchive","acrylic-pda-audit-archive","archive");
        assertThat(count("pda_audit_archive")).isEqualTo(2);assertThat(count("pda_audit_event")).isEqualTo(7);
        var fixture=json.createObjectNode();fixture.set("first",first);fixture.set("second",second);fixture.set("payload",a.payload(0,3));
        fixture.set("signingKey",json.valueToTree(signer.publicKey()));
        Files.writeString(Path.of("build/pda-audit-archive-fixture.json"),json.writeValueAsString(fixture));
    }
    @Test void gapDigestMismatchAndChangedBoundaryCannotConfirm() throws Exception {
        f.pda.send(a.request(a.body(3,7)),f.tokens).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AUDIT_ARCHIVE_GAP"));
        f.pda.send(a.request(a.body(0,3).put("payloadSha256","0".repeat(64))),f.tokens).andExpect(status().isBadRequest());
        assertThat(count("pda_audit_archive")).isZero();a.confirm(0,3);
        f.pda.send(a.request(a.body(0,7)),f.tokens).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AUDIT_ARCHIVE_CONTENT_MISMATCH"));
    }
    @Test @Transactional void damagedArchiveIndexFailsClosedBeforeAppending() throws Exception {
        a.confirm(0,3);
        jdbc.update("UPDATE pda_audit_archive SET through_seq=2 WHERE origin_device_id=UUID_TO_BIN(?)",f.tokens.get("deviceId").asText());
        f.pda.send(a.request(a.body(3,7)),f.tokens).andExpect(status().isServiceUnavailable());
    }
    @Test void compactRecoveryRehydratesArchivedEventsAndKeepsOriginalOrderEvidence() throws Exception {
        var proof=a.confirm(0,7);var recall=new RecallFixture(f);recall.start(false);recall.body=a.compact(recall,proof);
        assertThat(recall.body.withArray("auditEvents").size()).isEqualTo(1);recall.upload();recall.stop();
        assertThat(count("pda_audit_event")).isEqualTo(7);
        var changed=f.body.deepCopy();((ObjectNode)changed.at("/events/0")).put("detail","changed");
        f.send(changed).andExpect(status().isConflict());
    }
    @Test void compactRecoveryCannotOmitOriginalOrderEvents() throws Exception {
        var proof=a.confirm(0,3);var recall=new RecallFixture(f);recall.start(false);recall.body=a.compact(recall,proof);
        recall.body.withArray("events").remove(0);
        f.pda.send(recall.upload(recall.body),f.tokens).andExpect(status().isBadRequest());
    }
    @Test void missingAndOverlappingPrefixCannotSkipSequences() throws Exception {
        var one=a.confirm(0,3);var two=a.confirm(3,7);var recall=new RecallFixture(f);recall.start(false);
        for(var body:java.util.List.of(a.compact(recall,two),a.compact(recall,one,one),a.compact(recall,two,one)))
            f.pda.send(recall.upload(body),f.tokens).andExpect(status().isBadRequest());
    }
    @Test void lostArchiveFileBlocksConfirmRestoreAndRecall() throws Exception {
        var proof=a.confirm(0,7);var path=Path.of(storage,"submissions",proof.get("payloadSha256").asText()+".json");byte[] bytes=Files.readAllBytes(path);
        var recall=new RecallFixture(f);recall.start(false);
        try {
            Files.delete(path);
            f.pda.send(a.request(a.body(0,7)),f.tokens).andExpect(status().isServiceUnavailable());
            f.pda.send(a.restoring(proof,"1"),f.tokens).andExpect(status().isServiceUnavailable());
            f.pda.send(recall.upload(a.compact(recall,proof)),f.tokens).andExpect(status().isServiceUnavailable());
        } finally { Files.write(path,bytes); }
    }
    @Test @Transactional void restoredEpochRebuildsOriginalEventsFromFilesWithoutOldDatabaseIndex() throws Exception {
        var one=a.confirm(0,3);var two=a.confirm(3,7);
        jdbc.update("DELETE FROM pda_audit_archive WHERE origin_device_id=UUID_TO_BIN(?)",f.tokens.get("deviceId").asText());
        jdbc.update("DELETE FROM pda_audit_event WHERE origin_device_id=UUID_TO_BIN(?)",f.tokens.get("deviceId").asText());
        jdbc.update("UPDATE server_state SET recovery_epoch=2 WHERE id=1");
        var restored=f.pda.data(a.restoring(one,"2"),f.tokens);f.pda.data(a.restoring(two,"2"),f.tokens);
        assertThat(restored.get("recoveryEpoch").asText()).isEqualTo("2");assertThat(restored.get("payloadSha256")).isEqualTo(one.get("payloadSha256"));
        assertThat(f.pda.data(a.restoring(one,"2"),f.tokens)).isEqualTo(restored);assertThat(count("pda_audit_event")).isEqualTo(7);
        assertThat(jdbc.queryForList("SELECT source_recovery_epoch FROM pda_audit_event WHERE origin_device_id=UUID_TO_BIN(?)",Long.class,f.tokens.get("deviceId").asText())).containsOnly(1L);
    }
    @Test @Transactional void recoveredFileCannotOverwriteKnownConflictingAudit() throws Exception {
        var proof=a.confirm(0,3);jdbc.update("UPDATE server_state SET recovery_epoch=2 WHERE id=1");
        jdbc.update("UPDATE pda_audit_event SET record_sha256=? WHERE origin_device_id=UUID_TO_BIN(?) AND local_seq=1","0".repeat(64),f.tokens.get("deviceId").asText());
        f.pda.send(a.restoring(proof,"2"),f.tokens).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AUDIT_CONTENT_CONFLICT"));
    }
    @Test void outOfOrderRestoreRollsBackWithoutLeavingAnyPartialAudit() throws Exception {
        var first=a.confirm(0,3);var second=a.confirm(3,7);String device=f.tokens.get("deviceId").asText();
        jdbc.update("DELETE FROM pda_audit_archive WHERE origin_device_id=UUID_TO_BIN(?)",device);
        jdbc.update("DELETE FROM pda_audit_event WHERE origin_device_id=UUID_TO_BIN(?)",device);
        jdbc.update("UPDATE pda_audit_checkpoint SET through_seq=0 WHERE origin_device_id=UUID_TO_BIN(?)",device);
        f.pda.send(a.restoring(second,"1"),f.tokens).andExpect(status().isBadRequest());
        assertThat(count("pda_audit_event")).isZero();assertThat(count("pda_audit_archive")).isZero();
        f.pda.data(a.restoring(first,"1"),f.tokens);f.pda.data(a.restoring(second,"1"),f.tokens);
        assertThat(count("pda_audit_event")).isEqualTo(7);
    }
    @Test void lastRestoredEventConflictRollsBackEarlierInsertsAtRequestBoundary() throws Exception {
        var proof=a.confirm(0,7);String device=f.tokens.get("deviceId").asText();
        jdbc.update("DELETE FROM pda_audit_event WHERE origin_device_id=UUID_TO_BIN(?) AND local_seq<7",device);
        jdbc.update("UPDATE pda_audit_event SET record_sha256=? WHERE origin_device_id=UUID_TO_BIN(?)","0".repeat(64),device);
        f.pda.send(a.restoring(proof,"1"),f.tokens).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AUDIT_CONTENT_CONFLICT"));
        assertThat(count("pda_audit_event")).isEqualTo(1);assertThat(count("pda_audit_archive")).isEqualTo(1);
    }
    @Test void corruptedArchiveCannotRestoreOrShortenRecallEvidence() throws Exception {
        var proof=a.confirm(0,7);var path=Path.of(storage,"submissions",proof.get("payloadSha256").asText()+".json");
        byte[] bytes=Files.readAllBytes(path);var recall=new RecallFixture(f);recall.start(false);
        try {
            Files.writeString(path,"{\"tampered\":true}");
            f.pda.send(a.restoring(proof,"1"),f.tokens).andExpect(status().isServiceUnavailable());
            f.pda.send(recall.upload(a.compact(recall,proof)),f.tokens).andExpect(status().isServiceUnavailable());
        } finally { Files.write(path,bytes); }
    }
    @Test @Transactional void recoveryModeAndStaleEpochBlockArchiveWrites() throws Exception {
        var proof=a.confirm(0,3);jdbc.update("UPDATE server_state SET service_mode='RECOVERY_PENDING' WHERE id=1");
        f.pda.send(a.restoring(proof,"1"),f.tokens).andExpect(status().isServiceUnavailable());
        jdbc.update("UPDATE server_state SET service_mode='NORMAL',recovery_epoch=2 WHERE id=1");
        f.pda.send(a.request(a.body(0,3)),f.tokens).andExpect(status().isConflict());
    }
    @Test void forgedProofAndDifferentDeviceNeverRestore() throws Exception {
        var proof=(ObjectNode)a.confirm(0,3);var forged=proof.deepCopy().put("throughSeq","7");
        f.pda.send(a.restoring(forged,"1"),f.tokens).andExpect(status().isServiceUnavailable());
        var other=f.pda.login(f.pda.register());f.pda.send(a.restoring(proof,"1"),other).andExpect(status().isBadRequest());
    }
    @Test void concurrentRequestsKeepOneOriginalProof() throws Exception {
        var start=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->{start.await();return a.confirm(0,3);});var second=pool.submit(()->{start.await();return a.confirm(0,3);});
            start.countDown();assertThat(first.get(20,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(second.get(20,java.util.concurrent.TimeUnit.SECONDS));
        }
        assertThat(count("pda_audit_archive")).isEqualTo(1);
    }
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE origin_device_id=UUID_TO_BIN(?)",Integer.class,f.tokens.get("deviceId").asText()); }
}
