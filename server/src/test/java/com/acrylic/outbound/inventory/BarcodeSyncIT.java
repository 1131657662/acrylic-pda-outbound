package com.acrylic.outbound.inventory;

import com.acrylic.outbound.sync.BarcodeChanges;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class BarcodeSyncIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    private PublicationDriver web;private PdaTestDriver pda;private JsonNode tokens;
    @BeforeEach void fixture() throws Exception {
        web=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));
        pda=new PdaTestDriver(web);tokens=pda.login(pda.register());
    }
    @Test void pcReservationVoidAndNonDeductedDispatchAllPublish() throws Exception {
        var f=new PcFixture(web,false,1,1);f.start();var first=sync("");
        assertThat(first.get("baseline")).hasSize(3);assertThat(first.get("baseline").findValuesAsText("status")).containsOnly("AVAILABLE");
        var entry=f.add(0);var reserved=sync(first.get("nextCursor").asText());
        assertThat(reserved.at("/events/0/status").asText()).isEqualTo("RESERVED");
        f.send("void",f.write("").put("entryId",entry.at("/result/entryId").asText()).put("reason","实物未装车"));
        var released=sync(reserved.get("nextCursor").asText());assertThat(released.at("/events/0/status").asText()).isEqualTo("AVAILABLE");
        for(int i=0;i<3;i++) f.add(i);f.submit("");
        var dispatch=sync(released.get("nextCursor").asText());assertThat(dispatch.get("events").findValuesAsText("status")).containsOnly("DISPATCHED");
        assertThat(sync(dispatch.get("nextCursor").asText()).get("events")).isEmpty();
    }
    @Test void fixedBaselineDoesNotChangeWhenLaterReservationArrives() throws Exception {
        var f=new PcFixture(web,false,1,1);f.start();var first=pda.data(query().param("limit","1"),tokens);
        assertThat(first.get("nextCursor").isNull()).isTrue();assertThat(first.get("baselineComplete").asBoolean()).isFalse();
        for(int i=0;i<3;i++) f.add(i);
        var page=first;
        while(!page.get("baselineComplete").asBoolean()) {
            page=pda.data(query().param("limit","1").param("baselineId",first.get("baselineId").asText())
                .param("baselineCursor",page.get("baselineNextCursor").asText()),tokens);
            assertThat(page.get("highWatermark")).isEqualTo(first.get("highWatermark"));
            assertThat(page.at("/baseline/0/status").asText()).isEqualTo("AVAILABLE");
        }
        assertThat(sync(page.get("nextCursor").asText()).get("events").findValuesAsText("status")).containsOnly("RESERVED");
    }
    @Test void physicalQuarantineAndReleasePublishWithoutExposingOtherOrder() throws Exception {
        var f=new PcExceptionFixture(web);var first=sync("");f.stop();
        f.propose(f.plan(web.identity().user().getUsername(),"RETAIN","RELEASE","QUARANTINE"));f.approve();f.apply();
        var delta=sync(first.get("nextCursor").asText());
        assertThat(delta.get("events").findValuesAsText("status")).containsExactlyInAnyOrder("AVAILABLE","QUARANTINED");
        assertThat(delta.toString()).doesNotContain("orderId","operatorId","weightGrams");
    }
    @Test void snapshotOmissionAndChangedCycleBecomeVersionedRestrictions() throws Exception {
        var f=new PcFixture(web,false,1,1);var first=sync("");
        web.publish(web.stage(SkuFixture.row(f.barcodes.get(0),"300.501")));
        var missing=sync(first.get("nextCursor").asText());
        assertThat(missing.get("events").findValuesAsText("status")).containsExactlyInAnyOrder("AVAILABLE","UNAVAILABLE","UNAVAILABLE");
        String bale=first.at("/baseline/0/baleId").asText();
        jdbc.update("UPDATE bale SET dispatch_cycle=2 WHERE id=UUID_TO_BIN(?)",bale);BarcodeChanges.bale(jdbc,UUID.fromString(bale));
        var cycle=sync(missing.get("nextCursor").asText());assertThat(cycle.at("/events/0/dispatchCycle").asText()).isEqualTo("2");
    }
    @Test void invalidNamespaceEpochAndExpiredBaselineAreRejected() throws Exception {
        new PcFixture(web,false,1,1);var first=sync("");
        String raw=new String(Base64.getUrlDecoder().decode(first.get("nextCursor").asText()),java.nio.charset.StandardCharsets.US_ASCII);
        for(String changed:List.of(raw.replace("BARCODE","ORDER"),raw.replace(web.identity().warehouse().toString(),UUID.randomUUID().toString()),raw+"99999")) {
            String cursor=Base64.getUrlEncoder().encodeToString(changed.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            pda.send(query().param("cursor",cursor),tokens).andExpect(status().isBadRequest());
        }
        pda.send(get("/api/v1/pda/sync/barcodes").header("X-Server-Recovery-Epoch","2"),tokens).andExpect(status().isConflict());
        jdbc.update("UPDATE barcode_sync_baseline SET created_at=TIMESTAMPADD(HOUR,-2,created_at),expires_at=TIMESTAMPADD(HOUR,-1,expires_at) WHERE id=UUID_TO_BIN(?)",first.get("baselineId").asText());
        pda.send(query().param("baselineId",first.get("baselineId").asText()),tokens).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SYNC_BASELINE_EXPIRED"));
    }
    @Test void warehouseWithMoreThanPublicationBatchResumesAsDeltaWithoutGaps() throws Exception {
        new PcFixture(web,false,1,80);var page=pda.data(query().param("limit","200"),tokens);
        assertThat(page.get("baseline")).hasSize(200);assertThat(page.get("highWatermark").asText()).isEqualTo("200");
        var delta=pda.data(query().param("cursor",page.get("nextCursor").asText()).param("limit","200"),tokens);
        assertThat(delta.get("events")).hasSize(40);assertThat(delta.at("/events/0/sequence").asText()).isEqualTo("201");
        assertThat(delta.get("highWatermark").asText()).isEqualTo("240");assertThat(delta.get("hasMore").asBoolean()).isFalse();
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder query() {
        return get("/api/v1/pda/sync/barcodes").header("X-Server-Recovery-Epoch","1");
    }
    private JsonNode sync(String cursor) throws Exception { return pda.data(query().param("cursor",cursor),tokens); }
}
