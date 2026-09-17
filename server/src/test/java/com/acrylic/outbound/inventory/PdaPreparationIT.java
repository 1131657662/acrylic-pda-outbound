package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.pda.PreparationArchive;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.time.Instant;
import java.util.*;
import java.util.zip.GZIPInputStream;
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
class PdaPreparationIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    private PdaTestDriver pda;private PdaTestDriver.Device device;private JsonNode tokens;private JsonNode assignment;private String base;
    @BeforeEach void fixture() throws Exception {
        pda=new PdaTestDriver(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")));
        String order=pda.readyOrder();device=pda.register();tokens=pda.login(device);assignment=pda.data(pda.claim(order,UUID.randomUUID().toString()),tokens);
        base="/api/v1/pda/assignments/"+assignment.get("id").asText();
    }
    @Test void fixedBundleHashesMatchOriginalAuthorizationAndRepeatedPreparation() throws Exception {
        var receipt=prepare();var manifest=json.readTree(receipt.get("manifest").asText());
        assertThat(PdaTestDriver.sha(receipt.get("manifest").asText())).isEqualTo(receipt.get("manifestSha256").asText());
        for(var part:manifest.get("parts")) {
            byte[] compressed=pda.send(get(base+"/content/"+part.get("name").asText()).param("bundleId",receipt.get("bundleId").asText()),tokens)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
            assertThat(compressed.length).isEqualTo(part.get("compressedBytes").asInt());
            assertThat(PreparationArchive.sha(compressed)).isEqualTo(part.get("compressedSha256").asText());
            byte[] raw=new GZIPInputStream(new ByteArrayInputStream(compressed)).readAllBytes();
            assertThat(raw.length).isEqualTo(part.get("bytes").asInt());assertThat(PreparationArchive.sha(raw)).isEqualTo(part.get("sha256").asText());
        }
        assertThat(manifest.at("/parts/0/sha256")).isEqualTo(assignment.get("planSha256"));
        assertThat(manifest.at("/parts/1/sha256")).isEqualTo(assignment.get("inventorySha256"));
        pda.web().revise("003","1","修改映射",true);assertThat(prepare()).isEqualTo(receipt);
    }
    @Test void exportSignedPreparationForAndroidContractVerification() throws Exception {
        var receipt=prepare();var manifest=json.readTree(receipt.get("manifest").asText());var fixture=json.createObjectNode();
        fixture.set("assignment",assignment);fixture.set("receipt",receipt);
        fixture.set("signingKey",pda.data(get("/api/v1/pda/signing-key"),tokens));
        fixture.putObject("session").put("userId",tokens.get("userId").asText()).put("deviceId",tokens.get("deviceId").asText())
            .put("warehouseId",tokens.get("warehouseId").asText()).put("serverRecoveryEpoch","1")
            .put("userAuthVersion",tokens.get("userAuthVersion").asText()).put("deviceVersion",tokens.get("deviceVersion").asText());
        var payloads=fixture.putObject("gzip");
        for(var part:manifest.get("parts")) {
            byte[] content=pda.send(get(base+"/content/"+part.get("name").asText()).param("bundleId",receipt.get("bundleId").asText()),tokens)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
            payloads.put(part.get("name").asText(),Base64.getEncoder().encodeToString(content));
        }
        var path=java.nio.file.Path.of("build","pda-contract-fixture.json");java.nio.file.Files.createDirectories(path.getParent());
        java.nio.file.Files.writeString(path,json.writeValueAsString(fixture));
    }
    @Test void wrongDigestCannotAcknowledgeAndValidRepeatHasSameReceipt() throws Exception {
        var receipt=prepare();var body=ack(receipt);
        pda.send(post(base+"/download-ack").header("X-Server-Recovery-Epoch","1").contentType("application/json")
            .content(json.writeValueAsBytes(body.deepCopy().put("manifestSha256","0".repeat(64)))),tokens).andExpect(status().isConflict());
        assertThat(currentAck()).isNull();var first=sendAck(body);assertThat(sendAck(body)).isEqualTo(first);
        assertThat(currentAck()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_event WHERE entity_id=? AND event_type='ORDER_DOWNLOAD_ACK'",Integer.class,
            Ids.bytes(UUID.fromString(assignment.get("orderId").asText())))).isEqualTo(1);
    }
    @Test void otherDeviceCannotDownloadOrAcknowledge() throws Exception {
        var receipt=prepare();var other=pda.login(pda.register());
        pda.send(post(base+"/preparation").header("X-Server-Recovery-Epoch","1"),other).andExpect(status().isNotFound());
        pda.send(get(base+"/content/inventory").param("bundleId",receipt.get("bundleId").asText()),other).andExpect(status().isNotFound());
        pda.send(post(base+"/download-ack").header("X-Server-Recovery-Epoch","1").contentType("application/json")
            .content(json.writeValueAsBytes(ack(receipt))),other).andExpect(status().isNotFound());assertThat(currentAck()).isNull();
    }
    @Test void expiredAuthorizationCannotDownloadButStillHoldsOrder() throws Exception {
        var receipt=prepare();byte[] id=Ids.bytes(UUID.fromString(assignment.get("id").asText()));
        jdbc.update("UPDATE order_assignment SET issued_at=TIMESTAMPADD(HOUR,-49,UTC_TIMESTAMP(3)),expires_at=TIMESTAMPADD(HOUR,-1,UTC_TIMESTAMP(3)) WHERE id=?",id);
        pda.send(get(base+"/content/inventory").param("bundleId",receipt.get("bundleId").asText()),tokens).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT status FROM order_assignment WHERE id=?",String.class,id)).isEqualTo("ACTIVE");
    }
    @Test void incorrectBundleOrRecoveryEpochCannotBeUsed() throws Exception {
        prepare();pda.send(get(base+"/content/inventory").param("bundleId",UUID.randomUUID().toString()),tokens).andExpect(status().isNotFound());
        pda.send(post(base+"/preparation").header("X-Server-Recovery-Epoch","2"),tokens).andExpect(status().isConflict());
        assertThat(currentAck()).isNull();
    }
    @Test void revokedCredentialCannotBePrepared() throws Exception {
        jdbc.update("UPDATE offline_authorization SET revoked_at=UTC_TIMESTAMP(3) WHERE assignment_id=?",
            Ids.bytes(UUID.fromString(assignment.get("id").asText())));
        pda.send(post(base+"/preparation").header("X-Server-Recovery-Epoch","1"),tokens).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("OFFLINE_AUTH_EXPIRED"));assertThat(currentAck()).isNull();
    }
    @Test void newLoginAfterPermissionVersionChangeCannotReuseOldOfflineAuthorization() throws Exception {
        var receipt=prepare();
        jdbc.update("UPDATE app_user SET auth_version=auth_version+1 WHERE id=?",Ids.bytes(pda.web().identity().user().id()));
        tokens=pda.login(device);
        pda.send(post(base+"/preparation").header("X-Server-Recovery-Epoch","1"),tokens).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("OFFLINE_AUTH_REAUTHORIZATION_REQUIRED"));
        pda.send(get(base+"/content/inventory").param("bundleId",receipt.get("bundleId").asText()),tokens).andExpect(status().isConflict());
        pda.send(post(base+"/download-ack").header("X-Server-Recovery-Epoch","1").contentType("application/json")
            .content(json.writeValueAsBytes(ack(receipt))),tokens).andExpect(status().isConflict());
        assertThat(currentAck()).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM order_assignment WHERE id=?",String.class,
            Ids.bytes(UUID.fromString(assignment.get("id").asText())))).isEqualTo("ACTIVE");
    }
    private JsonNode prepare() throws Exception { return pda.data(post(base+"/preparation").header("X-Server-Recovery-Epoch","1"),tokens); }
    private ObjectNode ack(JsonNode receipt) {
        return json.createObjectNode().put("bundleId",receipt.get("bundleId").asText()).put("manifestSha256",receipt.get("manifestSha256").asText())
            .put("assignmentEpoch",assignment.get("epoch").asText()).put("inventorySnapshotId",assignment.get("inventorySnapshotId").asText())
            .put("activatedAt",Instant.now().toString());
    }
    private JsonNode sendAck(ObjectNode body) throws Exception {
        return pda.data(post(base+"/download-ack").header("X-Server-Recovery-Epoch","1").contentType("application/json").content(json.writeValueAsBytes(body)),tokens);
    }
    private String currentAck() { return jdbc.queryForObject("SELECT download_ack_at FROM order_assignment WHERE id=?",String.class,
        Ids.bytes(UUID.fromString(assignment.get("id").asText()))); }
}
