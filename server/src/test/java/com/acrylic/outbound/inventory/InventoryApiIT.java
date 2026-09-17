package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class InventoryApiIT {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    private IdentityFixture identity;

    @BeforeEach void fixture() {
        identity = IdentityFixture.create(jdbc, passwords, "WAREHOUSE_ADMIN");
    }

    private MockHttpServletRequestBuilder upload(byte[] bytes, String key) {
        return multipart("/api/v1/inventory/imports").file(new MockMultipartFile("file", "sku.txt", "text/plain", bytes))
            .param("warehouseId", identity.warehouse().toString()).header("Idempotency-Key", key)
            .header("X-Server-Recovery-Epoch", "1");
    }

    @Test void unauthenticatedReadIsDenied() throws Exception {
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test void realFormLoginCreatesSessionAndCsrfIsRequired() throws Exception {
        mvc.perform(post("/api/v1/auth/web/login").param("username", identity.user().username())
            .param("password", "Test-password-123")).andExpect(status().isForbidden());
        var result = mvc.perform(post("/api/v1/auth/web/login").with(csrf()).param("username", identity.user().username())
            .param("password", "Test-password-123")).andExpect(status().isOk()).andReturn();
        mvc.perform(get("/api/v1/me").cookie(result.getResponse().getCookies())).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.username").value(identity.user().username()))
            .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test void sampleIsDurablyStoredWithoutPublishingStock() throws Exception {
        var result = mvc.perform(upload(Files.readAllBytes(Path.of("../docs/sku.txt")), UUID.randomUUID().toString())
            .with(user(identity.user())).with(csrf())).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.validRows").value(12639))
            .andExpect(jsonPath("$.data.totalWeightGrams").value("3801362000")).andReturn();
        String id = json.readTree(result.getResponse().getContentAsString()).at("/data/id").asText();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_import_row WHERE import_id=?", Integer.class,
            Ids.bytes(UUID.fromString(id)))).isEqualTo(12639);
        assertThat(jdbc.queryForObject("SELECT active_inventory_snapshot_id FROM warehouse WHERE id=?", byte[].class,
            Ids.bytes(identity.warehouse()))).isNull();
    }

    @Test void duplicateRetryReturnsOriginalImportButChangedContentConflicts() throws Exception {
        String key = UUID.randomUUID().toString();
        byte[] bytes = SkuFixture.row("003B0051601", "305.5");
        String first = mvc.perform(upload(bytes, key).with(user(identity.user())).with(csrf()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String retry = mvc.perform(upload(bytes, key).with(user(identity.user())).with(csrf()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(first).get("data")).isEqualTo(json.readTree(retry).get("data"));
        mvc.perform(upload(SkuFixture.row("003B0051601", "306"), key).with(user(identity.user())).with(csrf()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONTENT_MISMATCH"));
    }

    @Test void parallelIdenticalUploadsProduceOneImport() throws Exception {
        String key = UUID.randomUUID().toString();
        byte[] bytes = SkuFixture.row(String.format("003B%07d", Math.floorMod(System.nanoTime(), 10000000)), "305");
        try (var pool = Executors.newFixedThreadPool(2)) {
            var task = (java.util.concurrent.Callable<Integer>) () -> mvc.perform(upload(bytes, key)
                .with(user(identity.user())).with(csrf())).andReturn().getResponse().getStatus();
            var a = pool.submit(task); var b = pool.submit(task);
            assertThat(a.get()).isEqualTo(200); assertThat(b.get()).isEqualTo(200);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_import WHERE warehouse_id=?", Integer.class,
            Ids.bytes(identity.warehouse()))).isEqualTo(1);
    }

    @Test void warehouseBoundaryPreventsReadingOtherImports() throws Exception {
        var another = IdentityFixture.create(jdbc, passwords, "WAREHOUSE_ADMIN");
        mvc.perform(get("/api/v1/inventory/imports").param("warehouseId", another.warehouse().toString())
            .with(user(identity.user()))).andExpect(status().isNotFound());
    }

    @Test void viewerCannotUpload() throws Exception {
        identity = IdentityFixture.create(jdbc, passwords, "WAREHOUSE_VIEWER");
        mvc.perform(upload(SkuFixture.row("003B0051601", "305"), UUID.randomUUID().toString())
            .with(user(identity.user())).with(csrf())).andExpect(status().isForbidden());
    }

    @Test void revokedAuthorizationInvalidatesExistingSession() throws Exception {
        jdbc.update("UPDATE app_user SET auth_version=auth_version+1 WHERE id=?", Ids.bytes(identity.user().id()));
        mvc.perform(get("/api/v1/me").with(user(identity.user()))).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
    }

    @Test void oldRecoveryEpochCannotCreateImport() throws Exception {
        mvc.perform(upload(SkuFixture.row("003B0051601", "305"), UUID.randomUUID().toString())
            .with(req -> { req.removeHeader("X-Server-Recovery-Epoch"); req.addHeader("X-Server-Recovery-Epoch", "old"); return req; })
            .with(user(identity.user())).with(csrf()))
            .andExpect(status().isConflict());
    }

    @Test void invalidRowsRemainVisibleAndBlockWholeFile() throws Exception {
        mvc.perform(upload(SkuFixture.concat("bad\n".getBytes(), SkuFixture.row("003B0051601", "305")), UUID.randomUUID().toString())
            .with(user(identity.user())).with(csrf())).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("BLOCKED")).andExpect(jsonPath("$.data.errorRows").value(1));
    }
}
