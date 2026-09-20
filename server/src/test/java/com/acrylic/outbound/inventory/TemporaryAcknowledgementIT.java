package com.acrylic.outbound.inventory;

import com.fasterxml.jackson.databind.*;
import java.time.Instant;
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
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class TemporaryAcknowledgementIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    private TemporaryFixture f;private JsonNode receipt;
    @BeforeEach void fixture() throws Exception {
        f=new TemporaryFixture(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")),jdbc);f.stock();receipt=f.prepare();
    }
    @Test void originalActivationReceiptSurvivesLostResponseAndNewKeyReplay() throws Exception {
        String key=UUID.randomUUID().toString();var body=f.ack(receipt);
        var first=f.pda.data(f.acknowledge(receipt,body,key),f.tokens);
        assertThat(f.pda.data(f.acknowledge(receipt,body,key),f.tokens)).isEqualTo(first);
        assertThat(f.pda.data(f.acknowledge(receipt,body,UUID.randomUUID().toString()),f.tokens)).isEqualTo(first);
        assertThat(f.state(receipt).get("acknowledgement")).isEqualTo(first);
        var claims=f.verify(first.get("signedAck").asText());
        assertThat(claims.get("aud").asText()).isEqualTo("acrylic-pda-temporary-activation");
        assertThat(claims.get("manifestSha256")).isEqualTo(receipt.get("manifestSha256"));
        assertThat(claims.get("activatedAt")).isEqualTo(first.get("activatedAt"));
        assertThat(claims.get("acknowledgedAt")).isEqualTo(first.get("acknowledgedAt"));
        assertThat(f.auditCount(receipt,"TEMPORARY_PREPARATION_ACKNOWLEDGED")).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"bundleId","manifestSha256","authorizationEpoch"})
    void wrongBindingCannotAcknowledge(String field) throws Exception {
        var body=f.ack(receipt).put(field,field.equals("bundleId")?UUID.randomUUID().toString():field.equals("manifestSha256")?"0".repeat(64):"2");
        f.pda.send(f.acknowledge(receipt,body,UUID.randomUUID().toString()),f.tokens).andExpect(status().isConflict());
        assertThat(f.state(receipt).get("acknowledgement").isNull()).isTrue();
    }
    @ParameterizedTest @ValueSource(longs={-172900,3600,172900})
    void activationOutsideOriginalWindowOrInFutureIsRejected(long seconds) throws Exception {
        var body=f.ack(receipt).put("activatedAt",Instant.now().plusSeconds(seconds).toString());
        f.pda.send(f.acknowledge(receipt,body,UUID.randomUUID().toString()),f.tokens).andExpect(status().isBadRequest());
    }
    @Test void differentActivationCannotOverwriteDurableReceipt() throws Exception {
        var body=f.ack(receipt);String key=UUID.randomUUID().toString();var first=f.pda.data(f.acknowledge(receipt,body,key),f.tokens);
        var changed=body.deepCopy().put("activatedAt",Instant.parse(body.get("activatedAt").asText()).plusSeconds(1).toString());
        f.pda.send(f.acknowledge(receipt,changed,key),f.tokens).andExpect(status().isConflict());
        f.pda.send(f.acknowledge(receipt,changed,UUID.randomUUID().toString()),f.tokens).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("TEMPORARY_ACTIVATION_CONFLICT"));
        assertThat(f.state(receipt).get("acknowledgement")).isEqualTo(first);
    }
    @Test void delayedActivationEvidenceIsAcceptedWithoutAllowingNewDownloads() throws Exception {
        // Synthetic database-time boundary, not a real 48-hour offline test; token integrity is tested separately.
        jdbc.update("UPDATE temporary_authorization SET issued_at=TIMESTAMPADD(HOUR,-49,issued_at),expires_at=TIMESTAMPADD(HOUR,-49,expires_at) WHERE id=UUID_TO_BIN(?)",
            receipt.get("authorizationId").asText());
        var body=f.ack(receipt).put("activatedAt",Instant.now().minusSeconds(7200).toString());String key=UUID.randomUUID().toString();
        var proof=f.pda.data(f.acknowledge(receipt,body,key),f.tokens);
        assertThat(f.pda.data(f.acknowledge(receipt,body,key),f.tokens)).isEqualTo(proof);
        f.pda.send(f.download(receipt,"inventory"),f.tokens).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TEMPORARY_AUTH_EXPIRED"));
        assertThat(f.state(receipt).get("preparation")).isEqualTo(receipt);assertThat(f.authorizations()).isEqualTo(1);
    }
}
