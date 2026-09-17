package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.UUID;
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
class PublicationApiIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    private PublicationDriver api;
    @BeforeEach void fixture() { api = new PublicationDriver(mvc, json, IdentityFixture.create(jdbc, passwords, "WAREHOUSE_ADMIN")); }

    @Test void fullSamplePublishesAndPagesWithoutLosingLeadingZerosOrWeight() throws Exception {
        String id = api.stage(Files.readAllBytes(Path.of("../docs/sku.txt")));
        assertThat(api.preview(id).get("added").asInt()).isEqualTo(12639);
        var receipt = api.publish(id);
        assertThat(receipt.get("totalWeightGrams").asText()).isEqualTo("3801362000");
        assertThat(receipt.get("rowCount").asInt()).isEqualTo(12639);
        assertThat(api.data(get("/api/v1/inventory/current")).get("activeSnapshotId")).isEqualTo(receipt.get("id"));
        var page = api.data(get("/api/v1/inventory/bales").param("limit", "1"));
        assertThat(page.at("/items/0/barcode").asText()).startsWith("003");
        assertThat(page.get("hasMore").asBoolean()).isTrue();
        assertThat(api.data(get("/api/v1/inventory/bales").param("afterBarcode", page.get("nextCursor").asText()).param("limit", "1"))
            .at("/items/0/barcode").asText()).isGreaterThan(page.at("/items/0/barcode").asText());
        assertThat(api.data(get("/api/v1/inventory/imports/" + id)).get("publishedSnapshotId")).isEqualTo(receipt.get("id"));
    }
    @Test void erroneousFileCannotPublishValidSubset() throws Exception {
        String id = api.stage(SkuFixture.concat("bad\n".getBytes(), SkuFixture.row("003B0051601", "305")));
        api.send(api.publication(id, api.decisions(id), UUID.randomUUID().toString())).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("IMPORT_BLOCKED"));
        assertThat(api.data(get("/api/v1/inventory/current")).get("activeSnapshotId").isNull()).isTrue();
    }
    @Test void identicalDuplicatesAndCandidateMappingsRequireAcknowledgment() throws Exception {
        byte[] row = SkuFixture.row("003B0051601", "305");
        String id = api.stage(SkuFixture.concat(row, row));
        var body = api.decisions(id).put("acceptIdenticalDuplicates", false);
        api.send(api.publication(id, body, UUID.randomUUID().toString())).andExpect(jsonPath("$.code").value("DEDUP_CONFIRMATION_REQUIRED"));
        body.put("acceptIdenticalDuplicates", true).put("acceptMappingChanges", false);
        api.send(api.publication(id, body, UUID.randomUUID().toString())).andExpect(jsonPath("$.code").value("MAPPING_CONFIRMATION_REQUIRED"));
        assertThat(api.publish(id).get("rowCount").asInt()).isEqualTo(1);
    }
    @Test void retryReturnsReceiptAndOldImportCannotReactivateOldSnapshot() throws Exception {
        String first = api.stage(SkuFixture.row("003B0051601", "305"));
        String key = UUID.randomUUID().toString(); var body = api.decisions(first);
        var receipt = api.data(api.publication(first, body, key));
        assertThat(api.data(api.publication(first, body, key))).isEqualTo(receipt);
        api.send(api.publication(first, body.deepCopy().put("reason", "changed"), key))
            .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONTENT_MISMATCH"));
        String second = api.stage(SkuFixture.row("003B0051602", "300.5"));
        var next = api.publish(second);
        assertThat(api.data(api.publication(first, body, UUID.randomUUID().toString()))).isEqualTo(receipt);
        assertThat(api.data(get("/api/v1/inventory/current")).get("activeSnapshotId")).isEqualTo(next.get("id"));
    }
    @Test void mappingEditInvalidatesPreviewAndHistoryIsVersioned() throws Exception {
        String id = api.stage(SkuFixture.row("003B0051601", "305")); var stale = api.decisions(id);
        api.revise("003", "0", "人工型号", false);
        api.send(api.publication(id, stale, UUID.randomUUID().toString())).andExpect(jsonPath("$.code").value("PUBLICATION_BASELINE_CHANGED"));
        api.publish(id);
        var current = api.data(get("/api/v1/delivery-mappings")).at("/items/0");
        assertThat(current.get("enabled").asBoolean()).isFalse();
        assertThat(current.get("version").asText()).isEqualTo("2");
        var history = api.data(get("/api/v1/delivery-mappings/003/history").param("limit", "1"));
        assertThat(history.get("nextCursor").asText()).isEqualTo("2");
        assertThat(api.data(get("/api/v1/delivery-mappings/003/history").param("beforeVersion", "2")).at("/items/0/model").asText()).isEqualTo("人工型号");
    }
    @Test void missingReappearingAndChangedBalePreserveBookBlockCycleAndOriginalSnapshot() throws Exception {
        var first = api.publish(api.stage(SkuFixture.row("003B0051601", "305.5")));
        jdbc.update("UPDATE inventory_position p INNER JOIN bale b ON b.id=p.bale_id SET p.book_status='DEDUCTED',p.blocked_reason='核实实物' WHERE b.barcode='003B0051601'");
        jdbc.update("UPDATE bale SET dispatch_cycle=7 WHERE barcode='003B0051601'");
        String missing = api.stage(SkuFixture.row("003B0051602", "300"));
        api.send(api.publication(missing, api.decisions(missing).put("confirmedRemovedCount", 0), UUID.randomUUID().toString()))
            .andExpect(jsonPath("$.code").value("REMOVAL_CONFIRMATION_REQUIRED"));
        api.publish(missing);
        var absent = api.data(get("/api/v1/inventory/bales").param("presence", "missing")).at("/items/0");
        assertThat(absent.get("barcode").asText()).isEqualTo("003B0051601");
        assertThat(absent.get("weightGrams").asText()).isEqualTo("305500");
        api.publish(api.stage(SkuFixture.row("003B0051601", "306")));
        var returned = api.data(get("/api/v1/inventory/bales").param("barcode", "003B0051601")).at("/items/0");
        assertThat(returned.get("dispatchCycle").asText()).isEqualTo("7");
        assertThat(returned.get("bookStatus").asText()).isEqualTo("DEDUCTED");
        assertThat(returned.get("blockedReason").asText()).isEqualTo("核实实物");
        assertThat(api.data(get("/api/v1/inventory/discrepancies")).toString()).contains("UPSTREAM_BOOK_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT weight_g FROM inventory_snapshot_bale WHERE snapshot_id=?", Long.class,
            Ids.bytes(UUID.fromString(first.get("id").asText())))).isEqualTo(305500L);
    }
    @Test @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void crossWarehouseBarcodeRollsBackMappingsAndPublication() throws Exception {
        String barcode = String.format("998B%07d", Math.floorMod(System.nanoTime(), 10000000));
        api.publish(api.stage(SkuFixture.row(barcode, "305")));
        var other = new PublicationDriver(mvc, json, IdentityFixture.create(jdbc, passwords, "WAREHOUSE_ADMIN"));
        String id = other.stage(SkuFixture.row("新型号", barcode, "300"));
        other.send(other.publication(id, other.decisions(id), UUID.randomUUID().toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BARCODE_WAREHOUSE_CONFLICT"));
        assertThat(other.data(get("/api/v1/inventory/current")).get("activeSnapshotId").isNull()).isTrue();
        assertThat(other.data(get("/api/v1/delivery-mappings")).get("items").isEmpty()).isTrue();
    }
    @Test void readOnlyUserCannotPublishOrReviseAndMalformedBodyIs400() throws Exception {
        var viewer = new PublicationDriver(mvc, json, IdentityFixture.create(jdbc, passwords, "WAREHOUSE_VIEWER"));
        String id = api.stage(SkuFixture.row("003B0051601", "305"));
        viewer.send(viewer.publication(id, api.decisions(id), UUID.randomUUID().toString())).andExpect(status().isForbidden());
        api.send(post("/api/v1/inventory/imports/" + id + "/publish").header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType("application/json").content("{}" )).andExpect(status().isBadRequest());
        viewer.send(get("/api/v1/inventory/bales").param("warehouseId", api.identity().warehouse().toString())).andExpect(status().isNotFound());
    }
    @Test void staleMappingEditIsRejectedAndSameKeyReplaysOriginalVersion() throws Exception {
        var body = json.createObjectNode().put("deliveryCode", "003").put("expectedVersion", "0").put("model", "M")
            .put("name", "N").put("enabled", true).put("reason", "核对代码");
        String key = UUID.randomUUID().toString();
        var request = post("/api/v1/delivery-mappings/revisions").contentType("application/json")
            .header("Idempotency-Key", key).content(json.writeValueAsBytes(body));
        var first = api.data(request);
        assertThat(api.data(request)).isEqualTo(first);
        api.send(post("/api/v1/delivery-mappings/revisions").contentType("application/json")
            .header("Idempotency-Key", UUID.randomUUID().toString()).content(json.writeValueAsBytes(body)))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MAPPING_VERSION_CONFLICT"));
        api.send(post("/api/v1/delivery-mappings/revisions").contentType("application/json")
            .header("Idempotency-Key", key).content(json.writeValueAsBytes(body.put("enabled", false))))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONTENT_MISMATCH"));
    }
    @Test void pagedPreviewRejectsChangedBaseline() throws Exception {
        String id = api.stage(SkuFixture.concat(SkuFixture.row("003B0051601", "305"), SkuFixture.row("003B0051602", "306")));
        var request = get("/api/v1/inventory/imports/" + id + "/diff/changes").param("limit", "1")
            .param("expectedSnapshot", "NONE").param("expectedMappingRevision", "0");
        assertThat(api.data(request).get("nextCursor").asText()).isEqualTo("003B0051601");
        api.revise("003", "0", "人工型号", true);
        api.send(request).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PUBLICATION_BASELINE_CHANGED"));
    }
}
