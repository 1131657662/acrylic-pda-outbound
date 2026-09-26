package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SourceEvidenceIntakePageItemsTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final SourceEvidenceIntakePageItems repository=new SourceEvidenceIntakePageItems(jdbc);
    private final SourceEvidenceIntakePageObjects.Binding binding=new SourceEvidenceIntakePageObjects.Binding(
        UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),3,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
    private final SourceEvidenceIntakePageItems.PageContext page=new SourceEvidenceIntakePageItems.PageContext(
        binding,2,UUID.randomUUID());
    private final SourceEvidenceIntakePageItems.Indexed item=new SourceEvidenceIntakePageItems.Indexed(0,"CAPTURE",
        UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),42);

    @Test void existingIndexIsBoundToOriginalOpenAndPage() {
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(row()));

        assertThat(repository.existing(page)).containsExactly(item);

        var sql=ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(),any(Object[].class));
        assertThat(sql.getValue()).contains("page_object_id=?").contains("ORDER BY item_ordinal").doesNotContain("FOR UPDATE");
    }

    @Test void corruptPageNumberCannotBeTreatedAsIdempotentIndex() {
        var corrupted=row();corrupted.put("page_no",3);
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(corrupted));

        assertThatThrownBy(() -> repository.existing(page)).isInstanceOfSatisfying(BusinessException.class,
            error->assertThat(error.code()).isEqualTo("E2A_PAGE_ITEM_INTEGRITY_FAILURE"));
    }

    @Test void totalsUseManifestOpenIdentityAndBoundedSource() {
        when(jdbc.queryForList(anyString(),any(Object[].class)))
            .thenReturn(List.of(Map.of("bytes",42L,"entries",1L)));

        assertThat(repository.totals(binding)).isEqualTo(new SourceEvidenceIntakePageItems.Totals(42,1));

        var sql=ArgumentCaptor.forClass(String.class);var args=ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForList(sql.capture(),args.capture());
        assertThat(sql.getValue()).contains("SUM(byte_count)").contains("open_original_key=?");
        assertThat((byte[])args.getValue()[3]).containsExactly(Ids.bytes(binding.openKey()));
    }

    @Test void insertOnlyAddsOneImmutableStageReference() {
        when(jdbc.update(anyString(),any(Object[].class))).thenReturn(1);

        repository.insert(page,item);

        var sql=ArgumentCaptor.forClass(String.class);var args=ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(),args.capture());
        assertThat(sql.getValue()).contains("INSERT INTO source_evidence_page_item_stage");
        assertThat(args.getValue()).hasSize(15);
        assertThat((byte[])args.getValue()[7]).containsExactly(Ids.bytes(page.pageObjectId()));
        assertThat((byte[])args.getValue()[13]).containsExactly(Ids.bytes(item.rawObjectId()));
    }

    @Test void duplicateNaturalKeyIsNotAnIntakeReceiptReplay() {
        when(jdbc.update(anyString(),any(Object[].class))).thenThrow(new DuplicateKeyException("duplicate"));

        assertThatThrownBy(() -> repository.insert(page,item)).isInstanceOfSatisfying(BusinessException.class,
            error->assertThat(error.code()).isEqualTo("E2A_PAGE_ITEM_DUPLICATE"));
    }

    private HashMap<String,Object> row() {
        var row=new HashMap<String,Object>();
        row.put("warehouse_id",Ids.bytes(binding.warehouseId()));
        row.put("principal_id",Ids.bytes(binding.originalOperatorId()));
        row.put("open_original_key",Ids.bytes(binding.openKey()));
        row.put("page_no",page.pageNo());row.put("item_ordinal",item.ordinal());row.put("item_kind",item.kind());
        row.put("original_record_id",Ids.bytes(item.recordId()));
        row.put("original_object_id",Ids.bytes(item.originalObjectId()));
        row.put("raw_object_id",Ids.bytes(item.rawObjectId()));row.put("byte_count",item.byteCount());
        return row;
    }
}
