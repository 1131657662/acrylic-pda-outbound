package com.acrylic.outbound.temporary.review;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TemporaryDecisionCandidatesTest {
    @Test void canonicalRoundTripPreservesDigestButWrongSourceWarehouseDatesAndBodyFail() {
        var jdbc=mock(JdbcTemplate.class);var queries=mock(TemporaryDuplicateQueries.class);
        var warehouse=UUID.randomUUID();var submission=UUID.randomUUID();
        when(jdbc.queryForObject(anyString(),eq(Timestamp.class),any())).thenReturn(Timestamp.from(Instant.parse("2026-09-25T01:00:00Z")));
        when(queries.snapshot(any())).thenReturn(new TemporaryDuplicateModels.Snapshot(0,"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"));
        var candidates=new TemporaryDecisionCandidates(queries,jdbc);var body=body();
        var snapshot=candidates.snapshot(warehouse,submission,body);
        var stored=(ObjectNode)SubmissionJson.parse(SubmissionJson.canonical(snapshot));
        assertThat(candidates.authentic(warehouse,submission,body,stored)).isTrue();
        assertThat(candidates.authentic(UUID.randomUUID(),submission,body,stored)).isFalse();
        assertThat(candidates.authentic(warehouse,UUID.randomUUID(),body,stored)).isFalse();
        assertThat(candidates.authentic(warehouse,submission,body.deepCopy().put("payloadChanged",true),stored)).isFalse();
        assertThat(candidates.authentic(warehouse,submission,body,stored.deepCopy().put("throughDate","2026-09-25"))).isFalse();
        assertThat(snapshot.path("fromDate").asText()).isEqualTo("2026-09-18");
        assertThat(snapshot.path("throughDate").asText()).isEqualTo("2026-10-02");
    }
    private ObjectNode body() {
        var body=SubmissionJson.object();body.putArray("scans");var plan=body.putObject("temporaryOrder").put("vehicleNo","合成车");
        plan.putArray("lines").addObject().put("deliveryCode","003").put("productId",UUID.randomUUID().toString())
            .put("model","X").put("name","Y").put("plannedCount","1");return body;
    }
}
