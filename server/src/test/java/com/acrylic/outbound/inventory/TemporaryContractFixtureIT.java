package com.acrylic.outbound.inventory;

import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class TemporaryContractFixtureIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    @Test void exportRealSignedContractForSubsequentAndroidVerification() throws Exception {
        var f=new TemporaryFixture(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")),jdbc);f.stock();
        var fixture=exportPreparation(f);
        fixture.set("nextPreparation",exportPreparation(f));
        Files.writeString(Path.of("build","pda-temporary-contract-fixture.json"),json.writeValueAsString(fixture));
    }
    private com.fasterxml.jackson.databind.node.ObjectNode exportPreparation(TemporaryFixture f) throws Exception {
        var receipt=f.prepare();var fixture=json.createObjectNode();fixture.set("receipt",receipt);
        fixture.set("signingKey",f.pda.data(get("/api/v1/pda/signing-key"),f.tokens));
        var session=fixture.putObject("session").put("serverRecoveryEpoch","1");
        session.set("capabilities",f.tokens.get("capabilities"));
        for(String key:List.of("userId","deviceId","warehouseId","userAuthVersion","deviceVersion")) session.set(key,f.tokens.get(key));
        var payloads=fixture.putObject("gzip");
        for(String part:List.of("inventory","mapping","policy","rules","availability")) {
            byte[] content=f.pda.send(f.download(receipt,part),f.tokens).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
            payloads.put(part,Base64.getEncoder().encodeToString(content));
        }
        var command=f.ack(receipt).put("activatedAt",java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS).toString())
            .put("authorizationEpoch",f.manifest(receipt).at("/identity/authorizationEpoch").asText());
        fixture.set("activationCommand",command);
        fixture.set("activationReceipt",f.pda.data(f.acknowledge(receipt,command,UUID.randomUUID().toString()),f.tokens));
        return fixture;
    }
}
