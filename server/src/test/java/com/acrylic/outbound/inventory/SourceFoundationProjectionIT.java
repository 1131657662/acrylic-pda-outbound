package com.acrylic.outbound.inventory;

import com.acrylic.outbound.pda.*;
import com.acrylic.outbound.temporary.TemporaryAvailability;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class SourceFoundationProjectionIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    @Autowired TemporaryAvailability availability;@Autowired PreparationPayloads preparations;@Autowired AssignmentStore assignments;
    TemporarySubmissionFixture t;
    @BeforeEach void create() throws Exception {
        t=new TemporarySubmissionFixture(new SubmissionFixture(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")),jdbc,false,false));
        t.abort();
    }
    @Test void sourceOwnerRemainsReservedInAllProjectionsAfterInventoryRepublish() throws Exception {
        var baseline=t.f.pda.data(get("/api/v1/pda/sync/barcodes").header("X-Server-Recovery-Epoch","1"),t.f.tokens);
        String set=SourceFoundationFixture.reserve(t,null),bale=t.body.at("/scans/0/baleId").asText();
        var delta=t.f.pda.data(get("/api/v1/pda/sync/barcodes").header("X-Server-Recovery-Epoch","1").param("cursor",baseline.get("nextCursor").asText()),t.f.tokens);
        assertThat((Iterable<JsonNode>)delta.get("events")).filteredOn(row->bale.equals(row.path("baleId").asText())).singleElement()
            .satisfies(row->assertThat(row.path("status").asText()).isEqualTo("RESERVED"));
        var data=preparations.read(assignments.get(UUID.fromString(t.f.assignment.get("id").asText())));
        assertThat((Iterable<JsonNode>)json.readTree(data.availability())).filteredOn(row->bale.equals(row.path("baleId").asText())).singleElement()
            .satisfies(row->assertThat(row.get("available")).isEqualTo(json.getNodeFactory().booleanNode(false)));
        String barcode=t.body.at("/scans/0/barcode").asText();
        t.f.web.publish(t.f.web.stage(SkuFixture.concat(SkuFixture.row(barcode,"300.5"),SkuFixture.row("003"+UUID.randomUUID().toString().replace("-","").substring(0,8).toUpperCase(),"200"))));
        assertThat(availability.read(t.f.web.identity().warehouse(),"1").items()).anySatisfy(item->{
            assertThat(item.baleId()).isEqualTo(bale);assertThat(item.status()).isEqualTo("RESERVED");assertThat(item.dispatchCycle()).isEqualTo("1");
        });
        assertThat(jdbc.queryForObject("SELECT BIN_TO_UUID(source_evidence_set_id) FROM bale_commitment WHERE bale_id=UUID_TO_BIN(?)",String.class,bale)).isEqualTo(set);
    }
    @Test void pcCandidatesDisplaySourceReservationAndRecordingRejectsIt() throws Exception {
        SourceFoundationFixture.reserve(t,null);var orders=new OrderTestDriver(t.f.web);
        String order=orders.create(orders.plan()).get("orderId").asText(),path="/api/v1/orders/"+order+"/pc",writer=UUID.randomUUID().toString();
        var start=json.createObjectNode().put("writerId",writer).put("expectedVersion","1").put("policyVersion","1").put("reason","合成来源占用兼容");
        t.f.web.data(post(path+"/start").header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json").content(start.toString()));
        String barcode=t.body.at("/scans/0/barcode").asText();
        assertThat(t.f.web.data(get(path+"/candidates").param("query",barcode)).at("/groups/0/items/0/availability").asText()).isEqualTo("RESERVED");
        var work=t.f.web.data(get(path)).get("work");
        var command=json.createObjectNode().put("writerId",writer).put("fence",work.get("fence").asText()).put("expectedVersion",work.get("version").asText())
            .put("barcode",barcode).putNull("entryId").put("reason","").put("physicalConfirmed",true);
        t.f.web.send(post(path+"/entries").header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json").content(command.toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PC_BALE_UNAVAILABLE"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pc_entry e JOIN pc_work w ON w.id=e.work_id WHERE w.order_id=UUID_TO_BIN(?)",Integer.class,order)).isZero();
    }
}
