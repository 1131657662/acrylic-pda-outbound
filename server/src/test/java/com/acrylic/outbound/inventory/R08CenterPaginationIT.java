package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class R08CenterPaginationIT extends R08CenterFixture {
    @Test void timestampTiesPaginateWithoutDuplicatesOrMissingRows() throws Exception {
        var expected=new HashSet<String>();
        for(int i=0;i<25;i++) {
            UUID id=id(create(UUID.randomUUID().toString(),"XLSX"));state(id,"FAILED");expected.add(id.toString());
            jdbc.update("UPDATE background_job SET created_at='2026-09-20 00:00:00.000' WHERE id=?",Ids.bytes(id));
        }
        var first=api.data(get("/api/v1/exports"));
        assertThat(first.path("items")).hasSize(20);String cursor=first.path("nextCursor").asText();
        var second=api.data(get("/api/v1/exports").param("before",cursor));assertThat(second.path("items")).hasSize(5);
        var actual=new ArrayList<String>();first.path("items").forEach(n->actual.add(n.path("id").asText()));
        second.path("items").forEach(n->actual.add(n.path("id").asText()));
        assertThat(actual).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(expected);
        assertThat(second.path("nextCursor").isNull()).isTrue();
    }
    @Test void formatAndEffectiveStatusFiltersApplyBeforePagination() throws Exception {
        UUID failed=id(create(UUID.randomUUID().toString(),"XLSX"));state(failed,"FAILED");
        UUID expired=id(create(UUID.randomUUID().toString(),"PDF"));expire(expired);
        UUID pending=id(create(UUID.randomUUID().toString(),"PDF"));
        assertThat(api.data(get("/api/v1/exports").param("status","FAILED")).path("items"))
                .extracting(n->n.path("id").asText()).containsExactly(failed.toString());
        assertThat(api.data(get("/api/v1/exports").param("format","PDF").param("status","EXPIRED")).path("items"))
                .extracting(n->n.path("id").asText()).containsExactly(expired.toString());
        assertThat(api.data(get("/api/v1/exports").param("status","PENDING")).path("items"))
                .extracting(n->n.path("id").asText()).containsExactly(pending.toString());
        jdbc.update("UPDATE background_job SET payload=JSON_SET(payload,'$.epoch','0') WHERE id=?",Ids.bytes(failed));
        assertThat(api.data(get("/api/v1/exports").param("status","INVALIDATED")).path("items")).hasSize(1);
        api.send(get("/api/v1/exports").param("status","UNKNOWN")).andExpect(status().isBadRequest());
    }
    @Test void foreignCursorAndRetryAreHiddenFromAnotherActor() throws Exception {
        UUID id=id(create(UUID.randomUUID().toString(),"XLSX"));state(id,"FAILED");
        var other=IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN");
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(other.user().id()),Ids.bytes(api.identity().warehouse()));
        var stranger=new PublicationDriver(mvc,json,new IdentityFixture(other.user(),api.identity().warehouse()));
        assertThat(stranger.data(get("/api/v1/exports")).path("items")).isEmpty();
        stranger.send(get("/api/v1/exports").param("before",id.toString())).andExpect(status().isNotFound());
        stranger.send(retry(id,UUID.randomUUID().toString())).andExpect(status().isNotFound());
        stranger.send(get("/api/v1/exports/"+id+"/file")).andExpect(status().isNotFound());
    }
    @Test void revokedWarehouseAndPermissionCannotReadSummaryOrCreateRetry() throws Exception {
        UUID id=id(create(UUID.randomUUID().toString(),"XLSX"));state(id,"FAILED");
        jdbc.update("DELETE FROM user_warehouse WHERE user_id=?",Ids.bytes(api.identity().user().id()));
        api.send(get("/api/v1/exports")).andExpect(status().isNotFound());
        jdbc.update("DELETE FROM user_role WHERE user_id=?",Ids.bytes(api.identity().user().id()));
        api.send(get("/api/v1/exports/summary").param("fromDate","2026-01-01").param("throughDate","2026-01-01"))
                .andExpect(status().isForbidden());
        api.send(retry(id,UUID.randomUUID().toString())).andExpect(status().isForbidden());
    }
}
