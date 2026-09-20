package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class TemporarySubmissionTransportIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    private TemporarySubmissionFixture t;
    @BeforeEach void setup() throws Exception {
        t=new TemporarySubmissionFixture(new SubmissionFixture(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")),jdbc,false,false));
    }
    private byte[] begin() throws Exception {
        byte[] bytes=SubmissionJson.canonical(t.body).getBytes(StandardCharsets.UTF_8);
        var control=t.control().put("payloadSha256",SubmissionJson.sha(t.body)).put("byteCount",Integer.toString(bytes.length))
            .put("chunkCount",Integer.toString((bytes.length+262143)/262144));
        t.f.pda.data(t.request(t.base()+"/upload",control),t.f.tokens);return bytes;
    }
    private void chunk(int index,byte[] bytes) throws Exception {
        t.f.pda.data(post(t.base()+"/chunks/"+index).header("X-Server-Recovery-Epoch","1").contentType("application/octet-stream").content(bytes),t.f.tokens);
    }
    private JsonNode finish() throws Exception { return t.f.pda.data(t.request(t.base()+"/finalize",SubmissionJson.object()),t.f.tokens); }
    @Test void incompleteChunkRetryAndFinalizationRecoverOneOrder() throws Exception {
        var bytes=begin();t.f.pda.send(t.request(t.base()+"/finalize",SubmissionJson.object()),t.f.tokens).andExpect(status().isBadRequest());
        chunk(0,bytes);chunk(0,bytes);var result=finish();assertThat(finish()).isEqualTo(result);assertThat(t.submit()).isEqualTo(result);
        assertThat(t.orders()).isEqualTo(1);
    }
    @Test void largeOriginalEvidenceReassemblesOutOfOrderChunks() throws Exception {
        var f=new SubmissionFixture(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")),jdbc,false,false);
        f.addAcknowledgedFailures(1100);t=new TemporarySubmissionFixture(f);byte[] bytes=begin();assertThat(bytes.length).isGreaterThan(1048576);
        int count=(bytes.length+262143)/262144;
        for(int i=count-1;i>=0;i--)chunk(i,Arrays.copyOfRange(bytes,i*262144,Math.min(bytes.length,(i+1)*262144)));
        var result=finish();assertThat(result.get("status").asText()).isEqualTo("REVIEW_REQUIRED");assertThat(finish()).isEqualTo(result);
        assertThat(t.orders()).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submitted_capture WHERE submission_id=UUID_TO_BIN(?)",Integer.class,t.id())).isEqualTo(1102);
    }
    @Test void directUploadCannotReplaceOriginalChunkManifest() throws Exception {
        begin();t.body.put("temporaryNo","changed");
        t.f.pda.send(t.request("/api/v1/pda/temporary-orders/submissions",t.body),t.f.tokens).andExpect(status().isConflict());
        assertThat(t.read().get("status").asText()).isEqualTo("RECEIVING");assertThat(t.orders()).isZero();
    }
    @Test void abortedChunkSessionCannotBeFinalized() throws Exception {
        var bytes=begin();chunk(0,bytes);t.abort();
        t.f.pda.send(t.request(t.base()+"/finalize",SubmissionJson.object()),t.f.tokens).andExpect(status().isConflict());
        assertThat(t.orders()).isZero();assertThat(t.read().get("payloadSha256").isNull()).isTrue();
    }
    @Test void concurrentAbortAndUploadLeaveOneTerminalAbortWithoutDispatch() throws Exception {
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var upload=pool.submit(()->{start.await();return t.f.pda.send(t.request("/api/v1/pda/temporary-orders/submissions",t.body),t.f.tokens).andReturn().getResponse().getStatus();});
            var abort=pool.submit(()->{start.await();return t.abort();});start.countDown();
            assertThat(upload.get(30,TimeUnit.SECONDS)).isIn(200,409);assertThat(abort.get(30,TimeUnit.SECONDS).get("status").asText()).isEqualTo("ABORTED");
        }
        assertThat(t.read().get("status").asText()).isEqualTo("ABORTED");assertThat(t.submissions()).isEqualTo(1);assertThat(t.orders()).isBetween(0,1);
    }
    @Test void concurrentSameSourceHasOneFormalization() throws Exception {
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{start.await();return t.submit();});var b=pool.submit(()->{start.await();return t.submit();});start.countDown();
            assertThat(a.get(30,TimeUnit.SECONDS)).isEqualTo(b.get(30,TimeUnit.SECONDS));
        }
        assertThat(t.orders()).isEqualTo(1);
    }
    @Test void existingAuditConflictRollsBackFormalizationAndReservations() throws Exception {
        var event=((ObjectNode)t.body.at("/events/0")).deepCopy().put("detail","original cloud fact");t.a.send(t.a.batch(event));
        t.f.pda.send(t.request("/api/v1/pda/temporary-orders/submissions",t.body),t.f.tokens).andExpect(status().isConflict());
        assertThat(t.orders()).isZero();assertThat(t.submissions()).isZero();
        assertThat(t.a.count("pda_audit_event")).isEqualTo(1);
    }
    @Test void reusedDeviceCaptureSequenceIsConflictAndRollsBackTemporaryOrder() throws Exception {
        t.f.submit();t.body.put("submissionId",UUID.randomUUID().toString());
        ((ObjectNode)t.body.withArray("events").get(t.body.withArray("events").size()-1)).put("detail",t.id());
        t.f.pda.send(t.request("/api/v1/pda/temporary-orders/submissions",t.body),t.f.tokens).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CAPTURE_CONTENT_CONFLICT"));
        assertThat(t.orders()).isZero();assertThat(t.submissions()).isZero();
    }
    @Test void sameBalesInSecondTemporaryOrderRequireConflictReview() throws Exception {
        var first=t.submit();t.body.put("submissionId",UUID.randomUUID().toString());String client=UUID.randomUUID().toString();
        ((ObjectNode)t.body.get("source")).put("clientOrderId",client);((ObjectNode)t.body.get("temporaryOrder")).put("clientOrderId",client);
        t.body.put("planSha256",SubmissionJson.sha(t.body.get("temporaryOrder")));
        t.body.withArray("captures").forEach(c->((ObjectNode)c).put("clientOrderId",client).put("deviceCaptureSeq",Integer.toString(c.get("deviceCaptureSeq").asInt()+2)));
        var ids=new HashMap<String,String>();t.body.withArray("captures").forEach(c->{String old=c.get("id").asText();String id=UUID.randomUUID().toString();ids.put(old,id);((ObjectNode)c).put("id",id);});
        t.body.withArray("scans").forEach(s->((ObjectNode)s).put("captureId",ids.get(s.get("captureId").asText())).put("localSeq",Integer.toString(s.get("localSeq").asInt()+7)));
        t.body.withArray("events").forEach(e->{var row=(ObjectNode)e;row.put("id",UUID.randomUUID().toString()).put("localSeq",Integer.toString(e.get("localSeq").asInt()+7));
            ((ObjectNode)row.get("source")).put("clientOrderId",client);if(!row.get("captureId").isNull())row.put("captureId",ids.get(row.get("captureId").asText()));
            if(row.get("type").asText().equals("FROZEN"))row.put("detail",t.id());});
        var second=t.submit();assertThat(second.get("status").asText()).isEqualTo("REVIEW_REQUIRED");assertThat(second.get("issues").toString()).contains("BALE_ALREADY_COMMITTED");
        assertThat(second.get("orderId")).isNotEqualTo(first.get("orderId"));assertThat(t.orders()).isEqualTo(2);
    }
}
