package com.acrylic.outbound.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class TemporaryDuplicatePaginationIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    private TemporaryDuplicateFixture f;
    @BeforeEach void setup() throws Exception { f=new TemporaryDuplicateFixture(jdbc,mvc,json,passwords); }
    @Test void equalCreatedTimesHaveStableRealPagesAndCompleteCount() throws Exception {
        var expected=new HashSet<String>();for(int i=0;i<4;i++)expected.add(f.create(true));
        for(String id:expected)jdbc.update("UPDATE outbound_order SET created_at=DATE(UTC_TIMESTAMP()) WHERE id=UUID_TO_BIN(?)",id);
        var found=new HashSet<String>();String cursor="";String fingerprint=null;
        do {
            var page=f.page(cursor);assertThat(page.path("items")).hasSize(1);assertThat(page.path("total").asInt()).isEqualTo(4);
            assertThat(found.add(page.at("/items/0/order/id").asText())).isTrue();
            if(fingerprint==null)fingerprint=page.path("candidateSetSha256").asText();
            assertThat(page.path("candidateSetSha256").asText()).isEqualTo(fingerprint);
            cursor=page.path("nextCursor").isNull()?null:page.path("nextCursor").asText();
        } while(cursor!=null);
        assertThat(found).isEqualTo(expected);
    }
    @ParameterizedTest @ValueSource(strings={"new-order","publish","source","range"})
    void changedContextRequiresRestartRatherThanSkippingOrReusingOldComparison(String change) throws Exception {
        f.create(true);String draft=f.create(false);var first=f.page("");String cursor=first.path("nextCursor").asText();
        var request=f.request().param("limit","1").param("cursor",cursor);
        switch(change) {
            case "new-order"->f.create(true);
            case "publish"->f.publish(draft);
            case "source"->f.review.temporary.abort();
            default->request.param("fromDate","2026-01-01").param("throughDate","2026-12-31");
        }
        f.review.reviewer.send(request).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TEMPORARY_DUPLICATE_CONTEXT_CHANGED"));
        if(change.equals("new-order"))assertThat(f.page().path("total").asInt()).isEqualTo(3);
        if(change.equals("publish"))assertThat(f.candidate(f.page(),draft).at("/order/status").asText()).isEqualTo("READY");
    }
    @ParameterizedTest @ValueSource(strings={"invalid","eA","xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"})
    void malformedCursorsAreRejected(String cursor) throws Exception {
        f.review.reviewer.send(f.request().param("cursor",cursor)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
    }
    @Test void aForgedBoundaryCannotClaimAnEmptyPage() throws Exception {
        f.create(true);f.create(true);String cursor=f.page("").path("nextCursor").asText();
        var parts=new String(Base64.getUrlDecoder().decode(cursor),java.nio.charset.StandardCharsets.US_ASCII).split("\\|");parts[3]=UUID.randomUUID().toString();
        String forged=Base64.getUrlEncoder().withoutPadding().encodeToString(String.join("|",parts).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        f.review.reviewer.send(f.request().param("cursor",forged)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
    }
}
