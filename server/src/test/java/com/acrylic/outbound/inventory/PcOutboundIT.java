package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class PcOutboundIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    private PublicationDriver api;
    @BeforeEach void identity() { api=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")); }
    private PcFixture fixture(boolean deduct,int plan,int stock) throws Exception { return new PcFixture(api,deduct,plan,stock); }
    @Test void fullPlanPostsTrustedWeightAndKeepsGlobalDispatchGuardWithoutDeduction() throws Exception {
        var f=fixture(false,1,1);f.start();for(int i=0;i<3;i++) f.add(i);
        var body=f.write("");String key=UUID.randomUUID().toString();var result=api.data(f.request("submit",body,key));
        assertThat(result.at("/result/status").asText()).isEqualTo("SUCCEEDED");
        assertThat(result.at("/result/weightGrams").asText()).isEqualTo("901503");
        assertThat(api.data(f.request("submit",body,key))).isEqualTo(result);
        assertThat(count("dispatch_record",f.order)).isEqualTo(3);
        assertThat(count("bale_commitment",f.order)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_ledger WHERE dispatch_id IN (SELECT id FROM dispatch_record WHERE order_id=?)",Integer.class,Ids.bytes(UUID.fromString(f.order)))).isZero();
        assertThat(f.orders.detail(f.order).at("/order/status").asText()).isEqualTo("COMPLETED");
    }
    @Test void shortShipmentRequiresApprovalAndDeductsExactlyOnce() throws Exception {
        var f=fixture(true,2,1);f.start();f.add(0);
        api.send(f.request("submit",f.write(""),UUID.randomUUID().toString())).andExpect(status().isBadRequest());
        f.submit("实物不足");assertThat(count("dispatch_record",f.order)).isZero();
        var body=f.reviewBody("APPROVE");String key=UUID.randomUUID().toString();var result=api.data(f.request("review",body,key));
        assertThat(result.at("/result/status").asText()).isEqualTo("SUCCEEDED");
        assertThat(api.data(f.request("review",body,key))).isEqualTo(result);
        assertThat(count("dispatch_record",f.order)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT SUM(weight_delta_g) FROM inventory_ledger WHERE dispatch_id IN (SELECT id FROM dispatch_record WHERE order_id=?)",Long.class,Ids.bytes(UUID.fromString(f.order)))).isEqualTo(-300501);
    }
    @Test void duplicateAddAndChangedBodyDoNotIncreaseQuantity() throws Exception {
        var f=fixture(false,1,1);var start=f.startBody("1");String startKey=UUID.randomUUID().toString();
        var first=api.data(f.request("start",start,startKey));assertThat(api.data(f.request("start",start,startKey))).isEqualTo(first);
        var body=f.write(f.barcodes.get(0));String key=UUID.randomUUID().toString();var result=api.data(f.request("entries",body,key));
        assertThat(api.data(f.request("entries",body,key))).isEqualTo(result);
        api.send(f.request("entries",body.deepCopy().put("barcode",f.barcodes.get(1)),key)).andExpect(status().isConflict());
        api.send(f.request("entries",f.write(f.barcodes.get(0)),UUID.randomUUID().toString())).andExpect(status().isConflict());
        assertThat(f.view().at("/products/0/actualCount").asInt()).isEqualTo(1);
    }
    @Test void voidRequiresPhysicalEvidenceAndPreservesSequenceHistory() throws Exception {
        var f=fixture(false,1,1);f.start();var entry=f.add(0).at("/result/entryId").asText();
        var body=f.write("").put("entryId",entry).put("reason","误选产品，实物未装车").put("physicalConfirmed",false);
        api.send(f.request("void",body,UUID.randomUUID().toString())).andExpect(status().isBadRequest());
        f.send("void",body.put("physicalConfirmed",true));assertThat(count("bale_commitment",f.order)).isZero();f.add(0);
        var records=api.data(get(f.path("entries")));assertThat(records.at("/items/0/sequence").asText()).isEqualTo("2");
        assertThat(records.at("/items/1/status").asText()).isEqualTo("VOIDED");
    }
    @Test void newWindowFencesOldWritesAndPreservesReceiptRecovery() throws Exception {
        var f=fixture(false,2,1);f.start();var old=f.write(f.barcodes.get(0));String key=UUID.randomUUID().toString();
        var receipt=api.data(f.request("entries",old,key));var stale=f.write(f.barcodes.get(1));f.writer=UUID.randomUUID().toString();
        f.send("writer",f.startBody(f.view().at("/work/version").asText()));
        api.send(f.request("entries",stale,UUID.randomUUID().toString())).andExpect(status().isConflict());
        assertThat(api.data(f.request("entries",old,key))).isEqualTo(receipt);f.add(1);
    }
    @Test void groupedCandidatePagesDoNotHideOtherProductsOrTreatExactMissAsFuzzy() throws Exception {
        var f=fixture(false,20,8);f.start();var found=api.data(get(f.path("candidates")).param("query",f.serial.toLowerCase()));
        assertThat(found.get("matchedCodes").asInt()).isEqualTo(3);assertThat(found.get("total").asInt()).isEqualTo(24);
        for(var group:found.get("groups")) { assertThat(group.get("items").size()).isEqualTo(5);assertThat(group.get("total").asInt()).isEqualTo(8); }
        var second=api.data(get(f.path("candidates")).param("query",f.serial).param("code","003").param("page","2"));
        assertThat(second.at("/groups/0/items").size()).isEqualTo(3);
        assertThat(api.data(get(f.path("candidates")).param("query","99900"+f.serial)).get("total").asInt()).isZero();
        api.send(get(f.path("candidates")).param("query","%_12")).andExpect(status().isBadRequest());
        api.send(get(f.path("candidates")).param("query","123")).andExpect(status().isBadRequest());
    }
    @Test void emptyOverPlanAndOutOfOrderProductsAreRejected() throws Exception {
        var f=fixture(false,1,2);f.start();api.send(f.request("submit",f.write(""),UUID.randomUUID().toString())).andExpect(status().isConflict());
        f.add(0);api.send(f.request("entries",f.write(f.barcodes.get(1)),UUID.randomUUID().toString())).andExpect(status().isConflict());
        api.send(f.request("entries",f.write("99900"+f.serial),UUID.randomUUID().toString())).andExpect(status().isConflict());
        assertThat(f.view().at("/products/0/actualCount").asInt()).isEqualTo(1);
    }
    @Test void rejectionRetainsStockAndExplicitSupervisorResumeCreatesNewRound() throws Exception {
        var f=fixture(false,2,1);f.start();f.add(0);f.submit("等待补装");f.send("review",f.reviewBody("REJECT"));
        String old=f.view().at("/submission/id").asText();assertThat(count("bale_commitment",f.order)).isEqualTo(1);
        api.send(f.request("entries",f.write(f.barcodes.get(1)),UUID.randomUUID().toString())).andExpect(status().isConflict());
        f.send("resume",json.createObjectNode().put("expectedVersion",f.view().at("/work/version").asText()).put("physicalEvidence","逐包核对全部原包仍在原车，无卸货减包"));
        f.send("writer",f.startBody(f.view().at("/work/version").asText()));f.add(1);f.submit("仍短装");
        assertThat(f.view().at("/submission/round").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT status FROM pc_submission WHERE id=?",String.class,Ids.bytes(UUID.fromString(old)))).isEqualTo("REJECTED");
    }
    @Test void inventoryConflictCannotBeApprovedOrReleaseReservations() throws Exception {
        var f=fixture(false,1,1);f.start();f.add(0);
        jdbc.update("UPDATE inventory_position p INNER JOIN bale b ON b.id=p.bale_id SET p.blocked_reason='核对差异' WHERE b.barcode=?",f.barcodes.get(0));
        f.submit("短装");api.send(f.request("review",f.reviewBody("APPROVE"),UUID.randomUUID().toString())).andExpect(status().isConflict());
        assertThat(count("dispatch_record",f.order)).isZero();assertThat(count("bale_commitment",f.order)).isEqualTo(1);
    }
    private int count(String table,String order) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE order_id=?",Integer.class,Ids.bytes(UUID.fromString(order))); }
}
