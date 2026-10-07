package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SourceEvidenceIntakeBusinessFactsTest {
    private static final UUID WAREHOUSE=UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID SCOPE=UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID SUBMISSION=UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
    private static final UUID DEVICE=UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd");
    private static final UUID OPERATOR=UUID.fromString("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee");
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final SubmissionFiles files=mock(SubmissionFiles.class);
    private final SourceEvidenceIntakeBusinessFacts facts=new SourceEvidenceIntakeBusinessFacts(jdbc,files,unavailableLocal(),null);
    private final SourceEvidenceIntakePageObjects.Binding binding=new SourceEvidenceIntakePageObjects.Binding(
        WAREHOUSE,SCOPE,SUBMISSION,1,DEVICE,OPERATOR,UUID.randomUUID());

    @Test
    void captureFactsRequireStoredSourceRowAndOriginalDigest() {
        var capture=capture("COMMITTED",1);
        var body=payload(capture);
        stubPayload(body);
        when(jdbc.queryForObject(anyString(),eq(Integer.class),any(Object[].class))).thenReturn(1);

        facts.require(binding,List.of(item("CAPTURE",capture,"COMMITTED",1)),1);
    }

    @Test
    void changedCaptureDigestIsRejectedBeforeSetCreation() {
        var capture=capture("COMMITTED",1);
        stubPayload(payload(capture));
        when(jdbc.queryForObject(anyString(),eq(Integer.class),any(Object[].class))).thenReturn(1);
        var changed=new E2aPageItem("CAPTURE",SubmissionJson.uuid(capture,"id"),"CAPTURE_RECORDS",UUID.randomUUID(),
            "f".repeat(64),1,1,"f".repeat(64),"COMMITTED",null,null,null);

        assertThatThrownBy(() -> facts.require(binding,List.of(changed),1))
            .isInstanceOfSatisfying(BusinessException.class,error ->
                org.assertj.core.api.Assertions.assertThat(error.code()).isEqualTo("E2A_CAPTURE_FACT_CONFLICT"));
    }

    @Test
    void scanFactsMustMatchOriginalCaptureAndScanRecord() {
        var capture=capture("COMMITTED",1);
        var scan=scan(capture,"VOID",2);
        stubPayload(payload(capture,scan));
        when(jdbc.queryForObject(anyString(),eq(Integer.class),any(Object[].class))).thenReturn(1);
        var item=item("SCAN",scan,"VOID",2);

        facts.require(binding,List.of(item("CAPTURE",capture,"COMMITTED",1),item),1);
    }

    @Test
    void sourcePayloadMustBeAvailable() {
        var capture=capture("COMMITTED",1);
        when(jdbc.queryForList(anyString(),eq(String.class),any(Object[].class))).thenReturn(List.of());

        assertCode("E2A_SOURCE_PAYLOAD_UNAVAILABLE", () -> facts.require(binding,
            List.of(item("CAPTURE",capture,"COMMITTED",1)),1));
    }

    @Test
    void scanOmissionCannotCompleteSourceFacts() {
        var capture=capture("COMMITTED",1);
        stubPayload(payload(capture,scan(capture,"VOID",2)));
        when(jdbc.queryForObject(anyString(),eq(Integer.class),any(Object[].class))).thenReturn(1);

        assertCode("E2A_SCAN_FACT_COUNT_CONFLICT", () -> facts.require(binding,
            List.of(item("CAPTURE",capture,"COMMITTED",1)),1));
    }


    @Test
    void activeScanRequiresUnblockedStockAndMatchingSourceCommitment() throws Exception {
        var capture=capture("COMMITTED",1);
        var scan=scan(capture,"ACTIVE",2);
        stubPayload(payload(capture,scan));
        when(jdbc.queryForObject(anyString(),eq(Integer.class),any(Object[].class))).thenReturn(1);
        stubStock(SUBMISSION.toString(),"REVIEW_RESERVED",SCOPE.toString());

        facts.require(binding,List.of(item("CAPTURE",capture,"COMMITTED",1),item("SCAN",scan,"ACTIVE",2)),1);
    }

    @Test
    void uncommittedBaleRemainsOnlyAValidatedFact() throws Exception {
        var capture=capture("COMMITTED",1);
        var scan=scan(capture,"ACTIVE",2);
        stubPayload(payload(capture,scan));
        when(jdbc.queryForObject(anyString(),eq(Integer.class),any(Object[].class))).thenReturn(1);
        stubStock(null,null,null);

        facts.require(binding,List.of(item("CAPTURE",capture,"COMMITTED",1),item("SCAN",scan,"ACTIVE",2)),1);
        org.mockito.Mockito.verify(jdbc,org.mockito.Mockito.never()).update(anyString(),any(Object[].class));
    }

    @Test
    void anotherSourceCommitmentCannotBeAccepted() throws Exception {
        var capture=capture("COMMITTED",1);
        var scan=scan(capture,"ACTIVE",2);
        stubPayload(payload(capture,scan));
        when(jdbc.queryForObject(anyString(),eq(Integer.class),any(Object[].class))).thenReturn(1);
        stubStock(null,"SOURCE_RESERVED",SCOPE.toString());

        assertCode("E2A_BALE_OWNER_CONFLICT", () -> facts.require(binding,
            List.of(item("CAPTURE",capture,"COMMITTED",1),item("SCAN",scan,"ACTIVE",2)),1));
    }

    @Test
    void commitmentFromOtherScopeCannotBeAccepted() throws Exception {
        var capture=capture("COMMITTED",1);
        var scan=scan(capture,"ACTIVE",2);
        stubPayload(payload(capture,scan));
        when(jdbc.queryForObject(anyString(),eq(Integer.class),any(Object[].class))).thenReturn(1);
        stubStock(SUBMISSION.toString(),"REVIEW_RESERVED",UUID.randomUUID().toString());

        assertCode("E2A_BALE_OWNER_CONFLICT", () -> facts.require(binding,
            List.of(item("CAPTURE",capture,"COMMITTED",1),item("SCAN",scan,"ACTIVE",2)),1));
    }

    @Test
    void anotherSubmissionCommitmentCannotBeAccepted() throws Exception {
        var capture=capture("COMMITTED",1);
        var scan=scan(capture,"ACTIVE",2);
        stubPayload(payload(capture,scan));
        when(jdbc.queryForObject(anyString(),eq(Integer.class),any(Object[].class))).thenReturn(1);
        stubStock(UUID.randomUUID().toString(),"REVIEW_RESERVED",SCOPE.toString());

        assertCode("E2A_BALE_OWNER_CONFLICT", () -> facts.require(binding,
            List.of(item("CAPTURE",capture,"COMMITTED",1),item("SCAN",scan,"ACTIVE",2)),1));
    }

    private void stubStock(String owner,String state,String sourceScope) throws Exception {
        var result=mock(ResultSet.class);
        when(result.getString(1)).thenReturn(WAREHOUSE.toString());
        when(result.getLong(2)).thenReturn(1L);
        when(result.getBoolean(3)).thenReturn(true);
        when(result.getString(4)).thenReturn("NOT_DEDUCTED");
        when(result.getString(6)).thenReturn(owner);
        when(result.getLong(7)).thenReturn(1L);
        when(result.getString(8)).thenReturn(state);
        when(result.getString(9)).thenReturn(sourceScope);
        when(result.getString(10)).thenReturn(WAREHOUSE.toString());
        when(jdbc.query(anyString(),org.mockito.ArgumentMatchers.<RowMapper<Object>>any(),any(Object[].class)))
            .thenAnswer(invocation -> List.of(invocation.<RowMapper<?>>getArgument(1).mapRow(result,0)));
    }

    private void assertCode(String code,Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.code()).isEqualTo(code));
    }

    private void stubPayload(JsonNode body) {
        var bytes=SubmissionJson.canonical(body).getBytes(StandardCharsets.UTF_8);
        when(jdbc.queryForList(anyString(),eq(String.class),any(Object[].class))).thenReturn(List.of(SubmissionJson.sha(body)));
        when(files.read(SubmissionJson.sha(body))).thenReturn(bytes);
    }

    private JsonNode payload(JsonNode... values) {
        var result=SubmissionJson.object();
        var captures=result.putArray("captures");captures.add(values[0]);
        var scans=result.putArray("scans");for(int i=1;i<values.length;i++)scans.add(values[i]);
        return result;
    }

    private JsonNode capture(String state,long sequence) {
        return SubmissionJson.object().put("id",UUID.randomUUID().toString()).put("originDeviceId",DEVICE.toString())
            .put("deviceCaptureSeq",Long.toString(sequence)).put("orderScanSeq",Long.toString(sequence)).put("state",state);
    }

    private JsonNode scan(JsonNode capture,String status,long localSeq) {
        return SubmissionJson.object().put("captureId",capture.path("id").asText()).put("localSeq",Long.toString(localSeq))
            .put("orderScanSeq",capture.path("orderScanSeq").asText())
            .put("baleId",UUID.randomUUID().toString()).put("expectedDispatchCycle","1").put("status",status);
    }

    private E2aPageItem item(String kind,JsonNode record,String status,long sequence) {
        var id=kind.equals("CAPTURE")?SubmissionJson.uuid(record,"id"):SubmissionJson.uuid(record,"captureId");
        return new E2aPageItem(kind,id,"CAPTURE_RECORDS",UUID.randomUUID(),SubmissionJson.sha(record),
            SubmissionJson.canonical(record).getBytes(StandardCharsets.UTF_8).length,sequence,SubmissionJson.sha(record),
            status,kind.equals("SCAN")?SubmissionJson.uuid(record,"baleId"):null,
            kind.equals("SCAN")?1L:null,kind.equals("SCAN")?id:null);
    }
    private static SourceEvidenceIntakeLocalPayload unavailableLocal() {
        var local = mock(SourceEvidenceIntakeLocalPayload.class);
        org.mockito.Mockito.when(local.require(any(), any(), org.mockito.ArgumentMatchers.anyLong())).thenThrow(
            new com.acrylic.outbound.common.BusinessException(409, "E2A_SOURCE_PAYLOAD_UNAVAILABLE", "missing"));
        return local;
    }
}
