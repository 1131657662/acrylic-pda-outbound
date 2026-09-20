package com.acrylic.outbound.inventory;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class R08SummaryIT extends R08CenterFixture {
    @Test void shanghaiInclusiveDateRangeUsesOriginalConfirmationAndExactTotals() throws Exception {
        completedAt("2026-09-17T15:59:59.999Z");
        var first=completedAt("2026-09-17T16:00:00Z");
        var second=completedAt("2026-09-18T15:59:59.999Z");
        completedAt("2026-09-18T16:00:00Z");
        var summary=api.data(get("/api/v1/exports/summary").param("fromDate","2026-09-18").param("throughDate","2026-09-18"));
        assertThat(summary.path("orderCount").asInt()).isEqualTo(2);
        assertThat(summary.path("actualCount").asInt()).isEqualTo(6);
        assertThat(summary.path("weightGrams").asText()).isEqualTo("1803006");
        assertThat(summary.path("orders")).extracting(n->n.path("id").asText()).containsExactly(first.order,second.order);
        assertThat(summary.path("products")).allSatisfy(p->{
            assertThat(p.path("actualCount").asInt()).isEqualTo(2);assertThat(p.path("weightGrams").asText()).isEqualTo("601002");
        });
    }
    @Test void rangeRequestFreezesMembersAndReplaysOriginalSnapshotAfterNewConfirmation() throws Exception {
        var first=completed();String key=UUID.randomUUID().toString();var accepted=create(key,"XLSX");
        completed();var replay=create(key,"XLSX");
        assertThat(replay.path("id")).isEqualTo(accepted.path("id"));
        assertThat(snapshot(id(replay)).orders()).extracting(o->o.id()).containsExactly(first.order);
        assertThat(replay.path("orderCount").asInt()).isEqualTo(1);
        generate(id(replay));download(id(replay),"center-frozen.xlsx");
    }
    @Test void crossOrderExcelAndPdfUseSameOrderedOriginalFacts() throws Exception {
        var first=completed();var second=completed();
        for(String format:java.util.List.of("XLSX","PDF")) {
            UUID id=id(create(UUID.randomUUID().toString(),format));
            assertThat(snapshot(id).orders()).extracting(o->o.id()).containsExactly(first.order,second.order);
            generate(id);download(id,"center-summary."+format.toLowerCase());
        }
    }
    @Test void emptyRangeReturnsZeroSummaryAndRenderableEmptyFile() throws Exception {
        var result=api.data(get("/api/v1/exports/summary").param("fromDate","2026-01-01").param("throughDate","2026-01-01"));
        assertThat(result.path("orderCount").asInt()).isZero();assertThat(result.path("weightGrams").asText()).isEqualTo("0");
        UUID id=id(api.data(request(UUID.randomUUID().toString(),"XLSX","2026-01-01","2026-01-01")));
        generate(id);download(id,"center-empty.xlsx");
    }
    @Test void rejectsOversizedReversedOrWrongScopeRanges() throws Exception {
        for(String[] range:java.util.List.of(new String[]{"2026-01-01","2026-02-01"},new String[]{"2026-02-01","2026-01-01"}))
            api.send(request(UUID.randomUUID().toString(),"XLSX",range[0],range[1])).andExpect(status().isBadRequest());
        api.send(request(UUID.randomUUID().toString(),"SCAN_XLSX","2026-01-01","2026-01-01")).andExpect(status().isBadRequest());
        api.send(post("/api/v1/exports").header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json")
                .content("{\"format\":\"XLSX\",\"basis\":\"ORIGINAL_CONFIRMED\"}")).andExpect(status().isBadRequest());
    }
    @Test void changingRangeWithOriginalKeyRejectsReplay() throws Exception {
        String key=UUID.randomUUID().toString();api.data(request(key,"XLSX","2026-01-01","2026-01-01"));
        api.send(request(key,"XLSX","2026-01-01","2026-01-02")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }
}
