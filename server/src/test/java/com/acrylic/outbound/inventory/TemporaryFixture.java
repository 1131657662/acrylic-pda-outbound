package com.acrylic.outbound.inventory;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

final class TemporaryFixture {
    static final String BASE="/api/v1/pda/temporary-preparations";
    final PublicationDriver web;final PdaTestDriver pda;final JdbcTemplate jdbc;
    final PdaTestDriver.Device device;JsonNode tokens;
    TemporaryFixture(PublicationDriver web,JdbcTemplate jdbc) throws Exception {
        this.web=web;this.jdbc=jdbc;this.pda=new PdaTestDriver(web);device=pda.register();
        jdbc.update("INSERT INTO user_role SELECT UUID_TO_BIN(?),id FROM role WHERE code='TEMPORARY_ORDER_CREATOR'",web.identity().user().id().toString());
        admission("1",true);tokens=pda.login(device);
    }
    void stock() throws Exception {
        String suffix=UUID.randomUUID().toString().replace("-","").substring(0,7).toUpperCase();
        web.publish(web.stage(SkuFixture.concat(SkuFixture.row("003T"+suffix,"300.5"),SkuFixture.row("571T"+suffix,"301"))));
        new OrderTestDriver(web).policy("0",false);
    }
    void admission(String version,boolean enabled) throws Exception {
        var body=web.json().createObjectNode().put("expectedVersion",version).put("enabled",enabled).put("reason","核对临时作业测试准入");
        web.data(post("/api/v1/devices/"+device.id()+"/temporary-order").header("Idempotency-Key",UUID.randomUUID().toString())
            .contentType("application/json").content(body.toString()));
    }
    ObjectNode request() throws Exception {
        var b=pda.data(get(BASE+"/baseline").header("X-Server-Recovery-Epoch","1"),tokens);
        return web.json().createObjectNode().put("inventorySnapshotId",b.get("inventorySnapshotId").asText())
            .put("mappingRevision",b.get("mappingRevision").asText()).put("policyVersion",b.at("/policy/version").asText());
    }
    MockHttpServletRequestBuilder command(String path,JsonNode body,String key) {
        return post(BASE+path).header("X-Server-Recovery-Epoch","1").header("Idempotency-Key",key)
            .contentType("application/json").content(body.toString());
    }
    JsonNode prepare() throws Exception { return pda.data(command("",request(),UUID.randomUUID().toString()),tokens); }
    JsonNode manifest(JsonNode receipt) throws Exception { return web.json().readTree(receipt.get("manifest").asText()); }
    MockHttpServletRequestBuilder download(JsonNode receipt,String part) {
        return get(BASE+"/"+receipt.get("authorizationId").asText()+"/content/"+part)
            .param("bundleId",receipt.get("bundleId").asText()).header("X-Server-Recovery-Epoch","1");
    }
    JsonNode part(JsonNode receipt,String name) throws Exception {
        byte[] bytes=pda.send(download(receipt,name),tokens).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        return web.json().readTree(new GZIPInputStream(new ByteArrayInputStream(bytes)).readAllBytes());
    }
    ObjectNode ack(JsonNode receipt) {
        return web.json().createObjectNode().put("bundleId",receipt.get("bundleId").asText()).put("manifestSha256",receipt.get("manifestSha256").asText())
            .put("authorizationEpoch","1").put("activatedAt",Instant.now().toString());
    }
    MockHttpServletRequestBuilder acknowledge(JsonNode receipt,JsonNode body,String key) {
        return command("/"+receipt.get("authorizationId").asText()+"/download-ack",body,key);
    }
    JsonNode state(JsonNode receipt) throws Exception { return pda.data(get(BASE+"/"+receipt.get("authorizationId").asText()),tokens); }
    JsonNode verify(String token) throws Exception {
        String[] jwt=token.split("\\.");var key=pda.data(get("/api/v1/pda/signing-key"),tokens);var decoder=Base64.getUrlDecoder();
        var verifier=Signature.getInstance("SHA256withRSA");
        verifier.initVerify(KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(key.get("publicKey").asText()))));
        verifier.update((jwt[0]+"."+jwt[1]).getBytes(StandardCharsets.US_ASCII));assertThat(verifier.verify(decoder.decode(jwt[2]))).isTrue();
        var header=web.json().readTree(decoder.decode(jwt[0]));assertThat(header.get("alg").asText()).isEqualTo("RS256");
        assertThat(header.get("kid")).isEqualTo(key.get("keyId"));return web.json().readTree(decoder.decode(jwt[1]));
    }
    int authorizations() { return jdbc.queryForObject("SELECT COUNT(*) FROM temporary_authorization WHERE device_id=UUID_TO_BIN(?)",Integer.class,device.id()); }
    int auditCount(JsonNode receipt,String event) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM operation_event WHERE entity_id=UUID_TO_BIN(?) AND event_type=?",
            Integer.class,receipt.get("authorizationId").asText(),event);
    }
}
