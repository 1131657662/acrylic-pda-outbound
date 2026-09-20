package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.temporary.*;
import com.acrylic.outbound.pda.OfflineSigner;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class TemporaryAtomicityIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    @MockitoSpyBean OfflineSigner signer;
    private TemporaryFixture f;
    @BeforeEach void fixture() throws Exception {
        f=new TemporaryFixture(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")),jdbc);f.stock();
    }
    @Test void databaseAuditFailureRollsBackAuthorizationPartsAndIdempotency() throws Exception {
        String key=UUID.randomUUID().toString();var body=f.request();failAudit("TEMPORARY_PREPARATION_ISSUED");
        try { f.pda.send(f.command("",body,key),f.tokens).andExpect(status().isInternalServerError()); }
        finally { jdbc.execute("ALTER TABLE operation_event DROP CHECK r06b_fail_audit"); }
        assertThat(f.authorizations()).isZero();assertThat(idempotency(key)).isZero();
        var receipt=f.pda.data(f.command("",body,key),f.tokens);assertThat(f.manifest(receipt).at("/identity/authorizationEpoch").asText()).isEqualTo("1");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM temporary_preparation_part WHERE authorization_id=UUID_TO_BIN(?)",
            Integer.class,receipt.get("authorizationId").asText())).isEqualTo(5);
    }
    @Test void signingFailureDoesNotPersistAnIncompletePreparation() throws Exception {
        String key=UUID.randomUUID().toString();var body=f.request();
        doThrow(new BusinessException(503,"SIGNING_KEY_UNAVAILABLE","injected signing outage")).when(signer).sign(any());
        try { f.pda.send(f.command("",body,key),f.tokens).andExpect(status().isServiceUnavailable()); }
        finally { doCallRealMethod().when(signer).sign(any()); }
        assertThat(f.authorizations()).isZero();assertThat(idempotency(key)).isZero();
        f.pda.data(f.command("",body,key),f.tokens);assertThat(f.authorizations()).isEqualTo(1);
    }
    @Test void acknowledgementAuditFailureRollsBackProofAndCanRetryOriginalBody() throws Exception {
        var receipt=f.prepare();var body=f.ack(receipt);String key=UUID.randomUUID().toString();failAudit("TEMPORARY_PREPARATION_ACKNOWLEDGED");
        try { f.pda.send(f.acknowledge(receipt,body,key),f.tokens).andExpect(status().isInternalServerError()); }
        finally { jdbc.execute("ALTER TABLE operation_event DROP CHECK r06b_fail_audit"); }
        assertThat(f.state(receipt).get("acknowledgement").isNull()).isTrue();assertThat(idempotency(key)).isZero();
        f.pda.data(f.acknowledge(receipt,body,key),f.tokens);assertThat(f.auditCount(receipt,"TEMPORARY_PREPARATION_ACKNOWLEDGED")).isEqualTo(1);
    }
    @Test void concurrentSameKeyReturnsExactlyOneAuthorization() throws Exception {
        String key=UUID.randomUUID().toString();var body=f.request();var receipts=parallel(body,key,key);
        assertThat(receipts.get(0)).isEqualTo(receipts.get(1));assertThat(f.authorizations()).isEqualTo(1);
        assertThat(f.auditCount(receipts.get(0),"TEMPORARY_PREPARATION_ISSUED")).isEqualTo(1);
    }
    @Test void concurrentDifferentKeysHaveDistinctSequencesWithoutReplacingOriginal() throws Exception {
        var receipts=parallel(f.request(),UUID.randomUUID().toString(),UUID.randomUUID().toString());
        assertThat(List.of(f.manifest(receipts.get(0)).at("/identity/authorizationEpoch").asText(),
            f.manifest(receipts.get(1)).at("/identity/authorizationEpoch").asText())).containsExactlyInAnyOrder("1","2");
        assertThat(f.authorizations()).isEqualTo(2);
        for(var receipt:receipts) assertThat(f.state(receipt).get("preparation")).isEqualTo(receipt);
    }
    private List<JsonNode> parallel(JsonNode body,String oneKey,String twoKey) throws Exception {
        var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var one=pool.submit(()->{gate.await();return f.pda.data(f.command("",body,oneKey),f.tokens);});
            var two=pool.submit(()->{gate.await();return f.pda.data(f.command("",body,twoKey),f.tokens);});gate.countDown();
            return List.of(one.get(30,TimeUnit.SECONDS),two.get(30,TimeUnit.SECONDS));
        }
    }
    private int idempotency(String key) { return jdbc.queryForObject("SELECT COUNT(*) FROM api_idempotency_request WHERE principal_id=UUID_TO_BIN(?) AND idempotency_key=?",
        Integer.class,f.tokens.get("userId").asText(),key); }
    private void failAudit(String event) {
        if(!Set.of("TEMPORARY_PREPARATION_ISSUED","TEMPORARY_PREPARATION_ACKNOWLEDGED").contains(event)) throw new IllegalArgumentException(event);
        String actor=HexFormat.of().formatHex(Ids.bytes(UUID.fromString(f.tokens.get("userId").asText())));
        jdbc.execute("ALTER TABLE operation_event ADD CONSTRAINT r06b_fail_audit CHECK(NOT(actor_id=X'"+actor+"' AND event_type='"+event+"'))");
    }
}
