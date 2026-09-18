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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class PcAccessIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    private PublicationDriver api;private PcFixture f;
    @BeforeEach void fixture() throws Exception {
        api=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));f=new PcFixture(api,false,2,1);
    }
    private PublicationDriver sameWarehouse(String role) {
        var identity=IdentityFixture.create(jdbc,passwords,role);
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(identity.user().id()),Ids.bytes(api.identity().warehouse()));
        return new PublicationDriver(mvc,json,new IdentityFixture(identity.user(),api.identity().warehouse()));
    }
    @Test void viewerCanReadButCannotClaimSearchOrRecord() throws Exception {
        var viewer=sameWarehouse("ORDER_VIEWER");viewer.send(f.request("start",f.startBody("1"),UUID.randomUUID().toString())).andExpect(status().isForbidden());
        f.start();assertThat(viewer.data(get(f.path(""))).at("/work/status").asText()).isEqualTo("ACTIVE");
        viewer.send(get(f.path("candidates")).param("query",f.serial)).andExpect(status().isForbidden());
        viewer.send(f.request("entries",f.write(f.barcodes.get(0)),UUID.randomUUID().toString())).andExpect(status().isForbidden());
    }
    @Test void otherOperatorCannotTakeOverOrRecoverPrivateRequest() throws Exception {
        f.start();var other=sameWarehouse("WAREHOUSE_ADMIN");String key=UUID.randomUUID().toString();api.data(f.request("entries",f.write(f.barcodes.get(0)),key));
        other.send(f.request("writer",f.startBody(f.view().at("/work/version").asText()),UUID.randomUUID().toString())).andExpect(status().isForbidden());
        assertThat(other.data(get(f.path("requests/"+key))).get("receipt").isNull()).isTrue();
        assertThat(api.data(get(f.path("requests/"+key))).at("/receipt/action").asText()).isEqualTo("ADD");
    }
    @Test void crossWarehouseIdsAndMissingCsrfAreRejected() throws Exception {
        f.start();var other=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));
        other.send(get(f.path(""))).andExpect(status().isNotFound());other.send(f.request("entries",f.write(f.barcodes.get(0)),UUID.randomUUID().toString())).andExpect(status().isNotFound());
        mvc.perform(f.request("entries",f.write(f.barcodes.get(0)),UUID.randomUUID().toString()).param("warehouseId",api.identity().warehouse().toString()).with(user(api.identity().user())).header("X-Server-Recovery-Epoch","1")).andExpect(status().isForbidden());
    }
    @Test void pdaAndPcCannotHoldSameOrderInEitherDirection() throws Exception {
        f.start();var pda=new PdaTestDriver(api);var tokens=pda.login(pda.register());
        pda.send(pda.claim(f.order,UUID.randomUUID().toString()),tokens).andExpect(status().isConflict());
        var other=new PcFixture(f);api.data(other.orders.command("/"+other.order+"/publish",other.orders.release("1","1"),UUID.randomUUID().toString()));
        pda.data(pda.claim(other.order,UUID.randomUUID().toString()),tokens);
        api.send(other.request("start",other.startBody("3"),UUID.randomUUID().toString())).andExpect(status().isConflict());
    }
    @Test void revokedOriginalOperatorCannotBeApprovedBySupervisor() throws Exception {
        f.start();f.add(0);f.submit("短装");var body=f.reviewBody("APPROVE");var supervisor=sameWarehouse("WAREHOUSE_ADMIN");
        jdbc.update("UPDATE app_user SET status='DISABLED',auth_version=auth_version+1 WHERE id=?",Ids.bytes(api.identity().user().id()));
        supervisor.send(f.request("review",body,UUID.randomUUID().toString())).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_record WHERE order_id=?",Integer.class,Ids.bytes(UUID.fromString(f.order)))).isZero();
    }
    @Test void frozenEntryCannotBeVoidedAfterSupervisorResumesOriginalWork() throws Exception {
        f.start();String entry=f.add(0).at("/result/entryId").asText();f.submit("短装");f.send("review",f.reviewBody("REJECT"));
        f.send("resume",json.createObjectNode().put("expectedVersion",f.view().at("/work/version").asText()).put("physicalEvidence","全部原包在原车"));
        f.send("writer",f.startBody(f.view().at("/work/version").asText()));
        api.send(f.request("void",f.write("").put("entryId",entry).put("reason","不能以撤销卸货").put("physicalConfirmed",true),UUID.randomUUID().toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PC_FROZEN_ENTRY"));
    }
}
