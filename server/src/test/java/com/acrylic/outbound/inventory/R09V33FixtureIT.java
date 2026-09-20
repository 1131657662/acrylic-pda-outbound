package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Retained, synthetic V33 recovery snapshots; never runs against an existing task database. */
@SpringBootTest @AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="R09_V33_FIXTURE",matches="true")
class R09V33FixtureIT {
    static {
        String url=System.getenv("OUTBOUND_DB_URL");
        if(url==null || !url.startsWith("jdbc:mysql://127.0.0.1:19309/r09_ops_source?")
                || !"r09_source".equals(System.getenv("OUTBOUND_DB_USER")))
            throw new IllegalStateException("R09 V33 fixtures require their isolated source database");
    }
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;

    private TemporarySubmissionFixture fixture(boolean large) throws Exception {
        var f=new SubmissionFixture(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")),jdbc,false,false);
        if(large) f.addAcknowledgedFailures(400);
        return new TemporarySubmissionFixture(f);
    }

    @Test void abortBeforeBodyRetainsOnlyOriginalSourceControlAndSignedReceipt() throws Exception {
        var t=fixture(false);var result=t.abort();
        assertThat(result.get("orderId").isNull()).isTrue();assertThat(result.get("payloadSha256").isNull()).isTrue();
        assertThat(result.get("stockReleased").asBoolean()).isFalse();assertThat(result.get("signedReceipt").asText()).isNotBlank();
        assertThat(t.orders()).isZero();assertThat(t.read()).isEqualTo(result);
    }

    @Test void manifestWithoutChunksRemainsReceivingWithoutFormalization() throws Exception {
        var t=fixture(false);begin(t);
        assertThat(t.read().get("status").asText()).isEqualTo("RECEIVING");assertThat(t.orders()).isZero();
    }

    @Test void partialChunksRetainOriginalManifestWithoutAcceptedBody() throws Exception {
        var t=fixture(true);var bytes=begin(t);assertThat(bytes.length).isGreaterThan(262144);
        chunk(t,0,Arrays.copyOfRange(bytes,0,262144));
        assertThat(t.read().get("payloadSha256").isNull()).isTrue();assertThat(t.orders()).isZero();
    }

    @Test void directBodyCreatesExactlyOnePendingTargetAndRetainedReservations() throws Exception {
        var t=fixture(false);var accepted=t.submit();assertThat(t.submit()).isEqualTo(accepted);
        pending(t);assertThat(t.read()).isEqualTo(accepted);
    }

    @Test void completeChunksCreateExactlyOnePendingTarget() throws Exception {
        var t=fixture(true);var bytes=begin(t);int count=(bytes.length+262143)/262144;
        for(int index=count-1;index>=0;index--)
            chunk(t,index,Arrays.copyOfRange(bytes,index*262144,Math.min(bytes.length,(index+1)*262144)));
        t.f.pda.data(t.request(t.base()+"/finalize",SubmissionJson.object()),t.f.tokens);pending(t);
    }

    @Test void abortAfterBodyKeepsTargetAndPhysicalReservations() throws Exception {
        var t=fixture(false);t.submit();var result=t.abort();
        assertThat(result.get("payloadPresent").asBoolean()).isTrue();assertThat(result.get("stockReleased").asBoolean()).isFalse();
        assertThat(t.orders()).isEqualTo(1);assertThat(commitments(t)).isEqualTo(2);
    }

    @Test void allChunksWithoutFinalizeRemainReceivingWithoutAcceptedBody() throws Exception {
        var t=fixture(false);var bytes=begin(t);chunk(t,0,bytes);
        assertThat(t.read().get("status").asText()).isEqualTo("RECEIVING");assertThat(t.orders()).isZero();
        assertThat(t.read().get("payloadSha256").isNull()).isTrue();
    }

    @Test void abortAfterPartialUploadKeepsDeclarationSeparateFromAcceptedBody() throws Exception {
        var t=fixture(true);var bytes=begin(t);chunk(t,0,Arrays.copyOfRange(bytes,0,262144));
        var result=t.abort();assertThat(result.get("payloadPresent").asBoolean()).isFalse();
        assertThat(result.get("payloadSha256").isNull()).isTrue();assertThat(t.orders()).isZero();
    }

    @Test void acceptedConflictRetainsEvidenceWithoutInventingReservation() throws Exception {
        var t=fixture(false);
        block(t);
        assertThat(t.submit().get("status").asText()).isEqualTo("REVIEW_REQUIRED");
        assertThat(t.orders()).isEqualTo(1);assertThat(commitments(t)).isZero();
    }

    @Test void abortAfterConflictingBodyDoesNotInventMissingReservations() throws Exception {
        var t=fixture(false);block(t);t.submit();t.abort();
        assertThat(t.orders()).isEqualTo(1);assertThat(commitments(t)).isZero();
    }

    private void block(TemporarySubmissionFixture t) {
        jdbc.update("UPDATE inventory_position SET blocked_reason='R09 retained synthetic conflict' WHERE last_seen_snapshot_id=UUID_TO_BIN(?)",t.body.get("inventorySnapshotId").asText());
    }

    private byte[] begin(TemporarySubmissionFixture t) throws Exception {
        byte[] bytes=SubmissionJson.canonical(t.body).getBytes(StandardCharsets.UTF_8);
        var control=t.control().put("payloadSha256",SubmissionJson.sha(t.body)).put("byteCount",Integer.toString(bytes.length))
                .put("chunkCount",Integer.toString((bytes.length+262143)/262144));
        t.f.pda.data(t.request(t.base()+"/upload",control),t.f.tokens);return bytes;
    }

    private void chunk(TemporarySubmissionFixture t,int index,byte[] bytes) throws Exception {
        t.f.pda.data(post(t.base()+"/chunks/"+index).header("X-Server-Recovery-Epoch","1")
                .contentType("application/octet-stream").content(bytes),t.f.tokens);
    }

    private void pending(TemporarySubmissionFixture t) throws Exception {
        assertThat(t.read().get("status").asText()).isEqualTo("REVIEW_REQUIRED");assertThat(t.orders()).isEqualTo(1);
        assertThat(commitments(t)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_record WHERE submission_id=UUID_TO_BIN(?)",Integer.class,t.id())).isZero();
    }

    private int commitments(TemporarySubmissionFixture t) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE submission_id=UUID_TO_BIN(?)",Integer.class,t.id());
    }
}
