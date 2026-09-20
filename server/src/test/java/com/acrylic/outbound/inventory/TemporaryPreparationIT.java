package com.acrylic.outbound.inventory;

import com.acrylic.outbound.pda.PreparationArchive;
import com.fasterxml.jackson.databind.*;
import java.io.*;
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
class TemporaryPreparationIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    private TemporaryFixture f;
    @BeforeEach void fixture() throws Exception {
        f=new TemporaryFixture(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")),jdbc);f.stock();
    }
    @Test void noOfficeOrderNeededAndTokenBindsFixed48HourContext() throws Exception {
        var receipt=f.prepare();var manifest=f.manifest(receipt);var claims=f.verify(receipt.get("offlineToken").asText());
        assertThat(claims.get("aud").asText()).isEqualTo("acrylic-pda-temporary-work");
        assertThat(claims.get("purpose").asText()).isEqualTo("TEMP_WORK");
        assertThat(claims.get("scope").asText()).isEqualTo("temporary_order:create pda:scan pda:submit");
        assertThat(claims.get("exp").asLong()-claims.get("iat").asLong()).isEqualTo(172800);
        assertThat(claims.get("identity")).isEqualTo(manifest.get("identity"));
        for(String field:List.of("userId","deviceId","warehouseId","userAuthVersion","deviceVersion"))
            assertThat(claims.at("/identity/"+field)).isEqualTo(f.tokens.get(field));
        assertThat(claims.at("/identity/authorizationId")).isEqualTo(receipt.get("authorizationId"));
        assertThat(claims.at("/identity/serverRecoveryEpoch").asText()).isEqualTo("1");
        assertThat(claims.get("manifestSha256")).isEqualTo(receipt.get("manifestSha256"));
        assertThat(PdaTestDriver.sha(receipt.get("manifest").asText())).isEqualTo(receipt.get("manifestSha256").asText());
        var proof=f.verify(receipt.get("signedManifest").asText());
        assertThat(proof.get("aud").asText()).isEqualTo("acrylic-pda-temporary-preparation");
        assertThat(proof.get("manifestSha256")).isEqualTo(receipt.get("manifestSha256"));
        for(String table:List.of("outbound_order","order_assignment"))
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE warehouse_id=UUID_TO_BIN(?)",Integer.class,f.tokens.get("warehouseId").asText())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment c INNER JOIN bale b ON b.id=c.bale_id WHERE b.warehouse_id=UUID_TO_BIN(?)",
            Integer.class,f.tokens.get("warehouseId").asText())).isZero();
    }
    @Test void fiveCompressedPartsMatchManifestDigestsLengthsAndRows() throws Exception {
        var receipt=f.prepare();var manifest=f.manifest(receipt);
        assertThat(manifest.get("parts").findValuesAsText("name")).containsExactly("inventory","mapping","policy","rules","availability");
        for(var part:manifest.get("parts")) {
            byte[] compressed=f.pda.send(f.download(receipt,part.get("name").asText()),f.tokens).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsByteArray();
            byte[] raw=new GZIPInputStream(new ByteArrayInputStream(compressed)).readAllBytes();
            assertThat(compressed.length).isEqualTo(part.get("compressedBytes").asInt());
            assertThat(PreparationArchive.sha(compressed)).isEqualTo(part.get("compressedSha256").asText());
            assertThat(raw.length).isEqualTo(part.get("bytes").asInt());assertThat(PreparationArchive.sha(raw)).isEqualTo(part.get("sha256").asText());
            var data=json.readTree(raw);int rows=data.isArray()?data.size():data.has("items")?data.get("items").size():1;
            assertThat(rows).isEqualTo(part.get("rows").asInt());
        }
        assertThat(f.part(receipt,"rules").get("barcodePattern").asText()).isEqualTo("[A-Z0-9]{11}");
    }
    @Test void replayRetainsOriginalBundleAfterMappingAndPolicyChanges() throws Exception {
        String key=UUID.randomUUID().toString();var request=f.request();var receipt=f.pda.data(f.command("",request,key),f.tokens);
        var oldMapping=f.part(receipt,"mapping");f.web.revise("003","1","修订型号",false);new OrderTestDriver(f.web).policy("1",true);
        assertThat(f.pda.data(f.command("",request,key),f.tokens)).isEqualTo(receipt);
        assertThat(f.part(receipt,"mapping")).isEqualTo(oldMapping);
        f.pda.send(f.command("",request,UUID.randomUUID().toString()),f.tokens).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("TEMPORARY_BASELINE_CHANGED"));
        var newer=f.prepare();assertThat(f.manifest(newer).at("/identity/authorizationEpoch").asText()).isEqualTo("2");
        assertThat(f.part(newer,"mapping").get(0).get("enabled").asBoolean()).isFalse();
        assertThat(f.part(newer,"policy").get("autoDeductInventory").asBoolean()).isTrue();
        assertThat(f.authorizations()).isEqualTo(2);assertThat(f.auditCount(receipt,"TEMPORARY_PREPARATION_ISSUED")).isEqualTo(1);
    }
    @Test void changedIdempotentCommandCannotCreateAnotherAuthorization() throws Exception {
        String key=UUID.randomUUID().toString();var body=f.request();f.pda.data(f.command("",body,key),f.tokens);
        f.pda.send(f.command("",body.put("policyVersion","2"),key),f.tokens).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONTENT_MISMATCH"));assertThat(f.authorizations()).isEqualTo(1);
    }
    @Test void staleRecoveryAndInvalidPartsCannotDownload() throws Exception {
        var receipt=f.prepare();f.pda.send(f.download(receipt,"unrecognized"),f.tokens).andExpect(status().isNotFound());
        f.pda.send(f.download(receipt,"inventory").with(r->{r.removeHeader("X-Server-Recovery-Epoch");r.addHeader("X-Server-Recovery-Epoch","2");return r;}),f.tokens)
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SERVER_RECOVERY_EPOCH_MISMATCH"));
        f.pda.send(get(TemporaryFixture.BASE+"/"+receipt.get("authorizationId").asText()+"/content/inventory")
            .param("bundleId",UUID.randomUUID().toString()).header("X-Server-Recovery-Epoch","1"),f.tokens).andExpect(status().isNotFound());
    }
}
