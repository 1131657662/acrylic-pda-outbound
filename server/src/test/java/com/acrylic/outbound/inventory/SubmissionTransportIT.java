package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.*;
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
class SubmissionTransportIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    private SubmissionFixture fixture() throws Exception { return new SubmissionFixture(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")),jdbc,true,false); }
    @Test void twoOrdersCompetingForSameBalesCreateOnlyOneDispatch() throws Exception {
        var first=fixture();var second=new SubmissionFixture(first);
        var results=compete(first::submit,second::submit);
        assertThat(results.stream().map(n->n.get("status").asText())).containsExactlyInAnyOrder("SUCCEEDED","REVIEW_REQUIRED");
        assertThat(first.count("dispatch_record")+second.count("dispatch_record")).isEqualTo(2);
        assertThat(first.count("inventory_ledger")+second.count("inventory_ledger")).isEqualTo(2);
    }
    @Test void concurrentIdenticalSubmissionsReturnOneImmutableReceipt() throws Exception {
        var f=fixture();var results=compete(f::submit,f::submit);assertThat(results.get(0)).isEqualTo(results.get(1));assertThat(f.count("dispatch_record")).isEqualTo(2);
    }
    @Test void abortAndCommitHaveOnlyOneDurableTerminalOutcome() throws Exception {
        var f=fixture();var results=compete(()->json.readTree(f.send(f.body).andReturn().getResponse().getContentAsString()),f::abort);
        var terminal=f.pda.data(get("/api/v1/pda/submissions/"+f.id()),f.tokens);String status=terminal.get("status").asText();
        assertThat(status).isIn("SUCCEEDED","ABORTED");assertThat(f.count("dispatch_record")).isEqualTo(status.equals("SUCCEEDED")?2:0);
        if(status.equals("SUCCEEDED")) assertThat(results.get(1)).isEqualTo(terminal);
        else f.send(f.body).andExpect(status().isConflict());
    }
    @Test void chunkRetryAndIncompleteFinalizeCannotDoubleApply() throws Exception {
        var f=fixture();byte[] bytes=SubmissionJson.canonical(f.body).getBytes(StandardCharsets.UTF_8);String base="/api/v1/pda/submissions/"+f.id();
        var control=f.body.deepCopy().put("payloadSha256",SubmissionJson.sha(f.body)).put("byteCount",Integer.toString(bytes.length)).put("chunkCount","1");
        f.pda.data(post(base+"/upload").header("X-Server-Recovery-Epoch","1").contentType("application/json").content(control.toString()),f.tokens);
        f.pda.send(post(base+"/finalize").header("X-Server-Recovery-Epoch","1"),f.tokens).andExpect(status().isBadRequest());
        for(int i=0;i<2;i++) f.pda.data(post(base+"/chunks/0").header("X-Server-Recovery-Epoch","1").contentType("application/octet-stream").content(bytes),f.tokens);
        byte[] changed=bytes.clone();changed[changed.length-1]=' ';
        f.pda.send(post(base+"/chunks/0").header("X-Server-Recovery-Epoch","1").contentType("application/octet-stream").content(changed),f.tokens).andExpect(status().isConflict());
        var receipt=f.pda.data(post(base+"/finalize").header("X-Server-Recovery-Epoch","1"),f.tokens);
        assertThat(f.pda.data(post(base+"/finalize").header("X-Server-Recovery-Epoch","1"),f.tokens)).isEqualTo(receipt);assertThat(f.count("dispatch_record")).isEqualTo(2);
    }
    private List<JsonNode> compete(Callable<JsonNode> first,Callable<JsonNode> second) throws Exception {
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{ready.countDown();assertThat(start.await(5,TimeUnit.SECONDS)).isTrue();return first.call();});
            var b=pool.submit(()->{ready.countDown();assertThat(start.await(5,TimeUnit.SECONDS)).isTrue();return second.call();});
            try { assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue(); } finally { start.countDown(); }
            return List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
        }
    }
    @Test void multiMegabyteEvidenceReassemblesOutOfOrderChunksWithoutLosingOriginalInputs() throws Exception {
        var f=fixture();f.addAcknowledgedFailures(1100);byte[] bytes=SubmissionJson.canonical(f.body).getBytes(StandardCharsets.UTF_8);
        assertThat(bytes.length).isGreaterThan(1024*1024);int chunkSize=262144,count=(bytes.length+chunkSize-1)/chunkSize;
        String base="/api/v1/pda/submissions/"+f.id();
        var control=f.body.deepCopy().put("payloadSha256",SubmissionJson.sha(f.body)).put("byteCount",Integer.toString(bytes.length)).put("chunkCount",Integer.toString(count));
        f.pda.data(post(base+"/upload").header("X-Server-Recovery-Epoch","1").contentType("application/json").content(control.toString()),f.tokens);
        for(int i=count-1;i>0;i--) upload(f,base,i,Arrays.copyOfRange(bytes,i*chunkSize,Math.min(bytes.length,(i+1)*chunkSize)));
        f.pda.send(post(base+"/finalize").header("X-Server-Recovery-Epoch","1"),f.tokens).andExpect(status().isBadRequest());
        upload(f,base,0,Arrays.copyOfRange(bytes,0,chunkSize));upload(f,base,0,Arrays.copyOfRange(bytes,0,chunkSize));
        var receipt=f.pda.data(post(base+"/finalize").header("X-Server-Recovery-Epoch","1"),f.tokens);
        assertThat(receipt.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(receipt.path("captureHighWatermark").asText()).isEqualTo("1102");assertThat(f.count("dispatch_record")).isEqualTo(2);
    }
    private void upload(SubmissionFixture f,String base,int index,byte[] bytes) throws Exception {
        f.pda.data(post(base+"/chunks/"+index).header("X-Server-Recovery-Epoch","1").contentType("application/octet-stream").content(bytes),f.tokens);
    }
}
