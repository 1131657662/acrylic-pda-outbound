package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.time.*;
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
class PdaAssignmentIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    private PublicationDriver web;private PdaTestDriver pda;private PdaTestDriver.Device device;private JsonNode tokens;private String order;
    @BeforeEach void fixture() throws Exception {
        web=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));pda=new PdaTestDriver(web);
        order=pda.readyOrder();device=pda.register();tokens=pda.login(device);
    }
    @Test void claimPreviewAndAssignmentUseThePublishedOrderIdentity() throws Exception {
        var preview=pda.data(get("/api/v1/pda/orders/"+order+"/claim-preview"),tokens);
        var assignment=pda.data(pda.claim(order,UUID.randomUUID().toString()),tokens);
        assertThat(preview.get("status").asText()).isEqualTo("READY");
        assertThat(assignment.get("orderNo")).isEqualTo(preview.get("orderNo"));
        assertThat(assignment.get("vehicleNo")).isEqualTo(preview.get("vehicleNo"));
        assertThat(assignment.get("plannedTotal")).isEqualTo(preview.get("plannedTotal"));
        pda.send(get("/api/v1/pda/orders/"+order+"/claim-preview"),tokens)
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_STATE_CONFLICT"));
    }
    @Test void foreignWarehouseCannotReadClaimPreview() throws Exception {
        var outsider=new PdaTestDriver(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")));
        var foreign=outsider.login(outsider.register());
        outsider.send(get("/api/v1/pda/orders/"+order+"/claim-preview"),foreign).andExpect(status().isNotFound());
    }
    @Test void lostClaimResponseRecoversIdenticalCredentialWithoutExtending48Hours() throws Exception {
        String key=UUID.randomUUID().toString();var original=pda.data(pda.claim(order,key),tokens);var relogin=pda.login(device);
        assertThat(pda.data(pda.claim(order,key),relogin)).isEqualTo(original);
        assertThat(pda.data(get("/api/v1/pda/assignments"),relogin).get("items").get(0)).isEqualTo(original);
        assertThat(Duration.between(Instant.parse(original.get("issuedAt").asText()),Instant.parse(original.get("expiresAt").asText())))
            .isEqualTo(Duration.ofHours(48));
        pda.send(pda.claim(order,UUID.randomUUID().toString()),relogin).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_ASSIGNED"));
        var changed=pda.claimBody().put("areaNote","改为另一堆");
        pda.send(pda.command("/orders/"+order+"/assignments",changed).header("Idempotency-Key",key)
            .header("X-Server-Recovery-Epoch","1"),relogin).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONTENT_MISMATCH"));
        assertThat(count("order_assignment")).isEqualTo(1);assertThat(count("order_sync_outbox")).isEqualTo(2);
    }
    @Test void offlineCredentialVerifiesWithPublishedKeyAndBindsImmutableContext() throws Exception {
        var assignment=pda.data(pda.claim(order,UUID.randomUUID().toString()),tokens);
        String[] jwt=assignment.get("offlineToken").asText().split("\\.");
        var key=pda.data(get("/api/v1/pda/signing-key"),tokens);var decoder=Base64.getUrlDecoder();
        var verifier=Signature.getInstance("SHA256withRSA");
        verifier.initVerify(KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(key.get("publicKey").asText()))));
        verifier.update((jwt[0]+"."+jwt[1]).getBytes(StandardCharsets.US_ASCII));assertThat(verifier.verify(decoder.decode(jwt[2]))).isTrue();
        var header=json.readTree(decoder.decode(jwt[0]));var claims=json.readTree(decoder.decode(jwt[1]));
        assertThat(header.get("alg").asText()).isEqualTo("RS256");assertThat(header.get("kid")).isEqualTo(key.get("keyId"));
        assertThat(claims.get("exp").asLong()-claims.get("iat").asLong()).isEqualTo(48*3600);
        assertThat(claims.get("sub").asText()).isEqualTo(web.identity().user().id().toString());
        assertThat(claims.get("deviceId").asText()).isEqualTo(device.id());assertThat(claims.get("orderId").asText()).isEqualTo(order);
        assertThat(claims.get("assignmentId")).isEqualTo(assignment.get("id"));assertThat(claims.get("serverRecoveryEpoch").asText()).isEqualTo("1");
        assertThat(claims.get("inventorySnapshotId")).isEqualTo(assignment.get("inventorySnapshotId"));
        assertThat(claims.get("planSha256")).isEqualTo(assignment.get("planSha256"));assertThat(claims.get("scope").asText()).isEqualTo("pda:scan");
        new OrderTestDriver(web).policy("1",true);assertThat(claims.get("autoDeductInventory").asBoolean()).isFalse();
    }
    @Test void otherDeviceCannotReplayOrReadCredentialEvenForSameUser() throws Exception {
        String key=UUID.randomUUID().toString();pda.data(pda.claim(order,key),tokens);var other=pda.login(pda.register());
        pda.send(pda.claim(order,key),other).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONTENT_MISMATCH"));
        pda.send(pda.claim(order,UUID.randomUUID().toString()),other).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_ASSIGNED"));
        assertThat(pda.data(get("/api/v1/pda/assignments"),other).get("items")).isEmpty();
        assertThat(web.data(get("/api/v1/orders/"+order+"/assignment")).get("assignment").has("offlineToken")).isFalse();
    }
    @Test void foreignWarehouseCannotSeeOrClaimOrder() throws Exception {
        var outsider=new PdaTestDriver(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")));
        var foreign=outsider.login(outsider.register());
        outsider.send(outsider.claim(order,UUID.randomUUID().toString()),foreign).andExpect(status().isNotFound());
        assertThat(outsider.data(get("/api/v1/pda/orders"),foreign).get("items")).isEmpty();
        outsider.web().send(get("/api/v1/orders/"+order+"/assignment")).andExpect(status().isNotFound());
        assertThat(count("order_assignment")).isZero();
    }
    @Test void changedInventoryBaselinePreventsClaim() throws Exception {
        web.revise("003","1","新版型号",true);
        pda.send(pda.claim(order,UUID.randomUUID().toString()),tokens).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ORDER_BASELINE_CHANGED"));
        assertThat(count("order_assignment")).isZero();
    }
    private int count(String table) {
        if(!Set.of("order_assignment","order_sync_outbox").contains(table)) throw new IllegalArgumentException(table);
        return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE order_id=?",Integer.class,Ids.bytes(UUID.fromString(order)));
    }
}
