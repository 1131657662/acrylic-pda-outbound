package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SourceEvidenceIntakeSetWriterTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final SourceEvidenceIntakePageClosure closure=mock(SourceEvidenceIntakePageClosure.class);
    private final SourceEvidenceIntakePageObjects pages=mock(SourceEvidenceIntakePageObjects.class);
    private final SourceEvidenceIntakePageItems staged=mock(SourceEvidenceIntakePageItems.class);
    private final SourceEvidenceIntakeFiles files=mock(SourceEvidenceIntakeFiles.class);
    private final SourceEvidenceIntakeBusinessFacts facts=mock(SourceEvidenceIntakeBusinessFacts.class);
    private final SourceEvidenceIntakeReservations reservations=mock(SourceEvidenceIntakeReservations.class);
    private final SourceEvidenceIntakeSetWriter writer=new SourceEvidenceIntakeSetWriter(
        jdbc,closure,pages,staged,files,facts,reservations);
    private final SourceEvidenceIntakePageObjects.Binding binding=new SourceEvidenceIntakePageObjects.Binding(
        UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),2,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
    private final UUID stopObjectId=UUID.randomUUID(), recordId=UUID.randomUUID(), originalObjectId=UUID.randomUUID();
    private final UUID rawObjectId=UUID.randomUUID(), pageObjectId=UUID.randomUUID();
    private SourceEvidenceIntakeStop.Parsed stop;

    @BeforeEach void setUp() throws Exception {
        var manifest=SubmissionJson.object().put("pageCount",1);
        var proof=SubmissionJson.object();
        proof.set("core",SubmissionJson.object().put("captureHighWatermark",1).put("auditThroughSeq",2));
        stop=new SourceEvidenceIntakeStop.Parsed(new byte[]{1},"a".repeat(64),bytes(manifest),
            "b".repeat(64),"c".repeat(64),bytes(proof),binding.openKey(),"event");
        var item=SubmissionJson.object().put("itemKind","CAPTURE")
            .put("originalRecordId",recordId.toString()).put("objectKind","CAPTURE_RECORDS")
            .put("originalObjectId",originalObjectId.toString()).put("itemBytesSha256","d".repeat(64))
            .put("byteCount",1).put("originalDeviceSeq",1).put("recordSha256","d".repeat(64))
            .put("recordStatus","COMMITTED").putNull("baleId").putNull("originalCycle").putNull("captureId");
        var page=SubmissionJson.object().put("byteCount",1);
        page.putArray("items").add(item);
        var stored=new SourceEvidenceIntakeFiles.Stored("page","d".repeat(64),bytes(page).length);
        when(pages.findAfterSourceLock(binding,1)).thenReturn(new SourceEvidenceIntakePageObjects.PageObject(
            pageObjectId,UUID.randomUUID(),stored));
        when(files.readPage(stored)).thenReturn(bytes(page));
        when(staged.existing(new SourceEvidenceIntakePageItems.PageContext(binding,1,pageObjectId)))
            .thenReturn(List.of(new SourceEvidenceIntakePageItems.Indexed(0,"CAPTURE",recordId,
                originalObjectId,rawObjectId,1)));
        when(closure.inspect(stop,binding)).thenReturn(new SourceEvidenceIntakePageClosure.Assessment(
            List.of(),1,1,true,true));
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of());
        when(jdbc.update(anyString(),any(Object[].class))).thenReturn(1);
    }

    @Test void missingPageNeverCreatesASet() {
        when(closure.inspect(stop,binding)).thenReturn(new SourceEvidenceIntakePageClosure.Assessment(
            List.of(1L),0,0,false,false));

        assertThat(writer.createIfComplete(stop,binding,stopObjectId)).isNull();

        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }

    @Test void businessFactFailureDoesNotInsertAnySetOrItem() {
        doThrow(new BusinessException(409,"E2A_SOURCE_PAYLOAD_UNAVAILABLE","missing"))
            .when(facts).requireTrusted(eq(binding),any(),eq(1L),eq(null),eq(2L));

        assertThatThrownBy(() -> writer.createIfComplete(stop,binding,stopObjectId))
            .isInstanceOf(BusinessException.class);

        verify(jdbc,never()).update(anyString(),any(Object[].class));
        verify(reservations,never()).reserve(any(),any(),any());
    }

    @Test void trustedFactsPrecedeSetAndItemInsertion() {
        when(facts.requireTrusted(eq(binding),any(),eq(1L),eq(null),eq(2L)))
            .thenReturn(new SourceEvidenceIntakeBusinessFacts.TrustedFacts(List.of(),7L));
        var link=writer.createIfComplete(stop,binding,stopObjectId);

        assertThat(link.itemCount()).isEqualTo(1);
        var order=inOrder(facts,jdbc,reservations);
        order.verify(facts).requireTrusted(eq(binding),any(),eq(1L),eq(null),eq(2L));
        var arguments=ArgumentCaptor.forClass(Object[].class);
        order.verify(jdbc).update(contains("INSERT INTO source_evidence_set"),arguments.capture());
        assertThat(arguments.getValue()[9]).isEqualTo(7L);
        order.verify(jdbc).update(contains("INSERT INTO source_evidence_item"),any(Object[].class));
        order.verify(reservations).reserve(eq(binding),any(),eq(List.of()));
    }

    @Test void existingSetRejectsAlteredItemBeforeAnyNewReservation() {
        UUID setId=existingSet();
        when(facts.requireTrusted(eq(binding),any(),eq(1L),eq(setId),eq(2L)))
            .thenReturn(new SourceEvidenceIntakeBusinessFacts.TrustedFacts(List.of(),1L));
        when(jdbc.queryForObject(contains("COUNT(*) FROM source_evidence_item"),eq(Integer.class),any(Object[].class)))
            .thenReturn(0);

        assertThatThrownBy(() -> writer.createIfComplete(stop,binding,stopObjectId))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.code()).isEqualTo("E2A_SOURCE_ITEM_CONFLICT"));
        verify(reservations,never()).reserve(eq(binding),eq(setId),any());
    }

    @Test void existingSetRechecksFactsAndReservationsWithoutReinsertion() {
        UUID setId=existingSet();
        when(facts.requireTrusted(eq(binding),any(),eq(1L),eq(setId),eq(2L)))
            .thenReturn(new SourceEvidenceIntakeBusinessFacts.TrustedFacts(List.of(),1L));
        when(jdbc.queryForObject(contains("COUNT(*) FROM source_evidence_item"),eq(Integer.class),any(Object[].class)))
            .thenReturn(1);

        assertThat(writer.createIfComplete(stop,binding,stopObjectId).id()).isEqualTo(setId);

        verify(facts).requireTrusted(eq(binding),any(),eq(1L),eq(setId),eq(2L));
        verify(reservations).reserve(eq(binding),eq(setId),eq(List.of()));
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }

    private UUID existingSet() {
        UUID setId=UUID.randomUUID();
        when(jdbc.queryForList(contains("FROM source_evidence_set"),any(Object[].class))).thenReturn(List.of(Map.of(
            "id",Ids.bytes(setId),"stop_object_id",Ids.bytes(stopObjectId),"manifest_bytes",stop.manifestBytes(),
            "set_sha256",stop.setHash(),"capture_high_watermark",1L,"order_scan_high_watermark",1L,
            "audit_through_seq",2L,"item_count",1L)));
        when(jdbc.queryForObject(contains("SELECT COUNT(*) FROM source_evidence_item WHERE set_id"),eq(Long.class),
            any(Object[].class))).thenReturn(1L);
        return setId;
    }

    private static byte[] bytes(com.fasterxml.jackson.databind.JsonNode value) {
        return SubmissionJson.canonical(value).getBytes(StandardCharsets.UTF_8);
    }
}
