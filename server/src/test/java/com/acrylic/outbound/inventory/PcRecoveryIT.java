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
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class PcRecoveryIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    private PublicationDriver api;
    @BeforeEach void identity() { api=new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")); }
    @Test void receiptRemainsReadableAfterOperatePermissionIsRemoved() throws Exception {
        var f=new PcFixture(api,false,2,1);f.start();String key=UUID.randomUUID().toString();
        var body=f.write(f.barcodes.get(0));var receipt=api.data(f.request("entries",body,key));
        jdbc.update("DELETE FROM user_role WHERE user_id=?",Ids.bytes(api.identity().user().id()));
        jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code='ORDER_VIEWER'",Ids.bytes(api.identity().user().id()));
        assertThat(api.data(get(f.path("requests/"+key))).get("receipt")).isEqualTo(receipt);
        api.send(f.request("entries",body,key)).andExpect(status().isForbidden());
    }
    @Test void recordsUseRealPaginationAndPreserveOriginalSequenceAndWeight() throws Exception {
        var f=new PcFixture(api,false,8,8);f.start();for(int i=0;i<24;i++) f.add(i);
        var first=api.data(get(f.path("entries")));var second=api.data(get(f.path("entries")).param("page","2"));
        assertThat(first.get("total").asInt()).isEqualTo(24);assertThat(first.get("items").size()).isEqualTo(20);
        assertThat(second.get("items").size()).isEqualTo(4);assertThat(second.at("/items/0/sequence").asText()).isEqualTo("4");
        assertThat(second.at("/items/3/weightGrams").asText()).isEqualTo("300501");
        var filtered=api.data(get(f.path("entries")).param("code","003").param("page","99"));
        assertThat(filtered.get("page").asInt()).isEqualTo(1);assertThat(filtered.get("total").asInt()).isEqualTo(8);
    }
    @Test void unconfirmedPhysicalActionsCannotRecordOrSubmit() throws Exception {
        var f=new PcFixture(api,false,1,1);f.start();
        api.send(f.request("entries",f.write(f.barcodes.get(0)).put("physicalConfirmed",false),UUID.randomUUID().toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PC_PHYSICAL_CHECK_REQUIRED"));
        assertThat(f.view().at("/products/0/actualCount").asInt()).isZero();f.add(0);
        api.send(f.request("submit",f.write("").put("reason","短装").put("physicalConfirmed",false),UUID.randomUUID().toString())).andExpect(status().isBadRequest());
        assertThat(f.view().get("submission").isNull()).isTrue();
    }
    @Test void postingFailureRollsBackEveryFactAndOriginalRequestCanRetry() throws Exception {
        var f=new PcFixture(api,true,1,1);f.start();for(int i=0;i<3;i++) f.add(i);
        byte[] order=Ids.bytes(UUID.fromString(f.order));var body=f.write("");String key=UUID.randomUUID().toString();
        long next=jdbc.queryForObject("SELECT row_version+1 FROM outbound_order WHERE id=?",Long.class,order);UUID fault=Ids.next();
        jdbc.update("INSERT INTO order_sync_outbox VALUES(?,1,?,?,?,'TEST_FAULT','{}',NULL,UTC_TIMESTAMP(3))",Ids.bytes(fault),Ids.bytes(api.identity().warehouse()),order,next);
        try {
            api.send(f.request("submit",body,key)).andExpect(status().isConflict());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_record WHERE order_id=?",Integer.class,order)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pc_submission s INNER JOIN pc_work w ON w.id=s.work_id WHERE w.order_id=?",Integer.class,order)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bale_commitment WHERE order_id=? AND state='PC_RESERVED'",Integer.class,order)).isEqualTo(3);
            assertThat(f.view().at("/work/status").asText()).isEqualTo("ACTIVE");
        } finally { jdbc.update("DELETE FROM order_sync_outbox WHERE id=?",Ids.bytes(fault)); }
        assertThat(api.data(f.request("submit",body,key)).at("/result/status").asText()).isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_ledger l INNER JOIN dispatch_record d ON d.id=l.dispatch_id WHERE d.order_id=?",Integer.class,order)).isEqualTo(3);
    }
}
