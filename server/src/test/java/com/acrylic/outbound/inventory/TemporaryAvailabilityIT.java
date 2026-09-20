package com.acrylic.outbound.inventory;

import com.fasterxml.jackson.databind.*;
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
class TemporaryAvailabilityIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    private TemporaryFixture f;
    @BeforeEach void fixture() throws Exception {
        f=new TemporaryFixture(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")),jdbc);
    }
    @Test void temporaryOperatorWithoutAssignCanResumeFromFixedAvailabilityCursor() throws Exception {
        var pc=new PcFixture(f.web,false,1,1);pc.start();pc.add(0);var receipt=f.prepare();var fixed=f.part(receipt,"availability");
        assertThat(fixed.get("items").findValuesAsText("status")).containsExactlyInAnyOrder("RESERVED","AVAILABLE","AVAILABLE");
        jdbc.update("DELETE rp FROM role_permission rp INNER JOIN role r ON r.id=rp.role_id INNER JOIN permission p ON p.id=rp.permission_id WHERE r.code='WAREHOUSE_ADMIN' AND p.code='pda:assign'");
        pc.add(1);pc.add(2);pc.submit("");
        var delta=f.pda.data(get("/api/v1/pda/sync/barcodes").header("X-Server-Recovery-Epoch","1").param("cursor",fixed.get("nextCursor").asText()),f.tokens);
        assertThat(delta.get("events").findValuesAsText("status")).containsOnly("DISPATCHED");assertThat(delta.get("events")).hasSize(3);
        assertThat(f.part(receipt,"availability")).isEqualTo(fixed);
        f.admission("2",false);f.tokens=f.pda.login(f.device);
        f.pda.send(get("/api/v1/pda/sync/barcodes").header("X-Server-Recovery-Epoch","1"),f.tokens).andExpect(status().isForbidden());
    }
    @Test void omittedAndQuarantinedBalesRemainInWarehouseSnapshot() throws Exception {
        var pc=new PcExceptionFixture(f.web);pc.stop();
        pc.propose(pc.plan(f.web.identity().user().getUsername(),"RETAIN","RELEASE","QUARANTINE"));pc.approve();pc.apply();
        var receipt=f.prepare();var states=f.part(receipt,"availability");
        assertThat(states.get("items").findValuesAsText("status")).contains("QUARANTINED","RESERVED","AVAILABLE");
        var available=states.get("items").findValuesAsText("barcode");
        f.web.publish(f.web.stage(SkuFixture.row(available.getFirst(),"300.501")));
        var next=f.part(f.prepare(),"availability");
        assertThat(next.get("items")).hasSize(12);assertThat(next.get("items").findValuesAsText("status")).contains("QUARANTINED","RESERVED","UNAVAILABLE");
    }
    @Test void fullWarehousePackageIsCompleteBeforeStreamPublicationBatchFinishes() throws Exception {
        new PcFixture(f.web,false,1,80);var receipt=f.prepare();var fixed=f.part(receipt,"availability");
        assertThat(fixed.get("items")).hasSize(240);assertThat(fixed.get("highWatermark").asText()).isEqualTo("0");
        var first=f.pda.data(get("/api/v1/pda/sync/barcodes").header("X-Server-Recovery-Epoch","1")
            .param("cursor",fixed.get("nextCursor").asText()).param("limit","200"),f.tokens);
        assertThat(first.get("events")).hasSize(200);assertThat(first.get("hasMore").asBoolean()).isTrue();
        var second=f.pda.data(get("/api/v1/pda/sync/barcodes").header("X-Server-Recovery-Epoch","1")
            .param("cursor",first.get("nextCursor").asText()).param("limit","200"),f.tokens);
        assertThat(second.get("events")).hasSize(40);assertThat(second.get("highWatermark").asText()).isEqualTo("240");
    }
    @Test void removedStockIsUnavailableEvenWhenAbsentFromFixedInventory() throws Exception {
        f.stock();var first=f.prepare();var inventory=f.part(first,"inventory");
        f.web.publish(f.web.stage(SkuFixture.row(inventory.get(0).get("barcode").asText(),"300.5")));
        var second=f.prepare();assertThat(f.part(second,"inventory")).hasSize(1);
        assertThat(f.part(second,"availability").get("items").findValuesAsText("status")).containsExactlyInAnyOrder("AVAILABLE","UNAVAILABLE");
    }
    @Test void legacyStockWithoutPendingVersionStillAdvancesAfterFirstPackage() throws Exception {
        var pc=new PcFixture(f.web,false,1,1);pc.start();
        jdbc.update("DELETE FROM barcode_sync_pending WHERE warehouse_id=UUID_TO_BIN(?)",f.tokens.get("warehouseId").asText());
        var fixed=f.part(f.prepare(),"availability");pc.add(0);
        var delta=f.pda.data(get("/api/v1/pda/sync/barcodes").header("X-Server-Recovery-Epoch","1")
            .param("cursor",fixed.get("nextCursor").asText()),f.tokens);
        var changed=new ArrayList<JsonNode>();delta.get("events").forEach(e->{if(e.get("status").asText().equals("RESERVED")) changed.add(e);});
        assertThat(changed).hasSize(1);assertThat(changed.getFirst().get("entityVersion").asInt()).isEqualTo(2);
    }
}
