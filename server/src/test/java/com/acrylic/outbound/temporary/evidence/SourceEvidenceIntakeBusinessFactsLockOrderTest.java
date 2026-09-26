package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionFiles;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.E2aPageItem;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SourceEvidenceIntakeBusinessFactsLockOrderTest {
    private static final UUID WAREHOUSE=UUID.randomUUID(), SCOPE=UUID.randomUUID();
    private static final UUID SUBMISSION=UUID.randomUUID(), DEVICE=UUID.randomUUID();
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final SubmissionFiles files=mock(SubmissionFiles.class);
    private final SourceEvidenceIntakeBusinessFacts facts=new SourceEvidenceIntakeBusinessFacts(jdbc,files,unavailableLocal(),null);
    private final SourceEvidenceIntakePageObjects.Binding binding=new SourceEvidenceIntakePageObjects.Binding(
        WAREHOUSE,SCOPE,SUBMISSION,1,DEVICE,UUID.randomUUID(),UUID.randomUUID());

    @Test void activeBalesLockInStableIdentityOrderRegardlessOfPageOrder() throws Exception {
        var first=capture(1);
        var second=capture(2);
        UUID lowBale=UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID highBale=UUID.fromString("00000000-0000-4000-8000-000000000002");
        var low=scan(first,lowBale,3);
        var high=scan(second,highBale,4);
        var payload=SubmissionJson.object();
        payload.putArray("captures").add(first).add(second);
        payload.putArray("scans").add(low).add(high);
        byte[] bytes=SubmissionJson.canonical(payload).getBytes(StandardCharsets.UTF_8);
        when(jdbc.queryForList(anyString(),eq(String.class),any(Object[].class)))
            .thenReturn(List.of("a".repeat(64)));
        when(files.read("a".repeat(64))).thenReturn(bytes);
        when(jdbc.queryForObject(anyString(),eq(Integer.class),any(Object[].class))).thenReturn(1);
        var stock=mock(ResultSet.class);
        when(stock.getString(1)).thenReturn(WAREHOUSE.toString());
        when(stock.getLong(2)).thenReturn(1L);
        when(stock.getBoolean(3)).thenReturn(true);
        when(stock.getString(4)).thenReturn("NOT_DEDUCTED");
        when(jdbc.query(anyString(),org.mockito.ArgumentMatchers.<RowMapper<Object>>any(),any(Object[].class)))
            .thenAnswer(invocation -> List.of(invocation.<RowMapper<?>>getArgument(1).mapRow(stock,0)));

        facts.require(binding,List.of(captureItem(first),captureItem(second),item(high,4),item(low,3)),2);

        var locks=inOrder(jdbc);
        locks.verify(jdbc).query(anyString(),org.mockito.ArgumentMatchers.<RowMapper<Object>>any(),eq(Ids.bytes(lowBale)));
        locks.verify(jdbc).query(anyString(),org.mockito.ArgumentMatchers.<RowMapper<Object>>any(),eq(Ids.bytes(highBale)));
    }

    @Test void sourceReservationRequiresTheExactExistingEvidenceSet() throws Exception {
        var capture=capture(1);
        var scan=scan(capture,UUID.randomUUID(),2);
        var payload=SubmissionJson.object();
        payload.putArray("captures").add(capture);
        payload.putArray("scans").add(scan);
        when(jdbc.queryForList(anyString(),eq(String.class),any(Object[].class)))
            .thenReturn(List.of("a".repeat(64)));
        when(files.read("a".repeat(64))).thenReturn(SubmissionJson.canonical(payload).getBytes(StandardCharsets.UTF_8));
        when(jdbc.queryForObject(anyString(),eq(Integer.class),any(Object[].class))).thenReturn(1);
        UUID setId=UUID.randomUUID();
        var stock=mock(ResultSet.class);
        when(stock.getString(1)).thenReturn(WAREHOUSE.toString());
        when(stock.getLong(2)).thenReturn(1L);
        when(stock.getBoolean(3)).thenReturn(true);
        when(stock.getString(4)).thenReturn("NOT_DEDUCTED");
        when(stock.getLong(7)).thenReturn(1L);
        when(stock.getString(8)).thenReturn("SOURCE_RESERVED");
        when(stock.getString(9)).thenReturn(SCOPE.toString());
        when(stock.getString(10)).thenReturn(WAREHOUSE.toString());
        when(stock.getString(11)).thenReturn(setId.toString());
        when(jdbc.query(anyString(),org.mockito.ArgumentMatchers.<RowMapper<Object>>any(),any(Object[].class)))
            .thenAnswer(invocation -> List.of(invocation.<RowMapper<?>>getArgument(1).mapRow(stock,0)));

        facts.require(binding,List.of(captureItem(capture),item(scan,2)),1,setId);
        assertThatThrownBy(() -> facts.require(binding,List.of(captureItem(capture),item(scan,2)),1,UUID.randomUUID()))
            .isInstanceOfSatisfying(com.acrylic.outbound.common.BusinessException.class,
                error -> assertThat(error.code()).isEqualTo("E2A_BALE_OWNER_CONFLICT"));
    }

    private JsonNode capture(long sequence) {
        return SubmissionJson.object().put("id",UUID.randomUUID().toString())
            .put("originDeviceId",DEVICE.toString()).put("deviceCaptureSeq",Long.toString(sequence))
            .put("state","COMMITTED");
    }

    private JsonNode scan(JsonNode capture,UUID bale,long sequence) {
        return SubmissionJson.object().put("captureId",capture.path("id").asText())
            .put("localSeq",Long.toString(sequence)).put("baleId",bale.toString())
            .put("expectedDispatchCycle","1").put("status","ACTIVE");
    }

    private E2aPageItem item(JsonNode scan,long sequence) {
        var capture=SubmissionJson.uuid(scan,"captureId");
        var digest=SubmissionJson.sha(scan);
        return new E2aPageItem("SCAN",capture,"CAPTURE_RECORDS",UUID.randomUUID(),digest,
            SubmissionJson.canonical(scan).getBytes(StandardCharsets.UTF_8).length,
            sequence,digest,"ACTIVE",SubmissionJson.uuid(scan,"baleId"),1L,capture);
    }

    private E2aPageItem captureItem(JsonNode capture) {
        var digest=SubmissionJson.sha(capture);
        return new E2aPageItem("CAPTURE",SubmissionJson.uuid(capture,"id"),"CAPTURE_RECORDS",UUID.randomUUID(),
            digest,SubmissionJson.canonical(capture).getBytes(StandardCharsets.UTF_8).length,
            SubmissionJson.number(capture,"deviceCaptureSeq"),digest,"COMMITTED",null,null,null);
    }
    private static SourceEvidenceIntakeLocalPayload unavailableLocal() {
        var local = mock(SourceEvidenceIntakeLocalPayload.class);
        org.mockito.Mockito.when(local.require(any(), any(), org.mockito.ArgumentMatchers.anyLong())).thenThrow(
            new com.acrylic.outbound.common.BusinessException(409, "E2A_SOURCE_PAYLOAD_UNAVAILABLE", "missing"));
        return local;
    }
}
