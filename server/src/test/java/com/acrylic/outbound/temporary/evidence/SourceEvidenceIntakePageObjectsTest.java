package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.Ids;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SourceEvidenceIntakePageObjectsTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final SourceEvidenceIntakePageObjects objects=new SourceEvidenceIntakePageObjects(jdbc);
    private final SourceEvidenceIntakePageObjects.Binding binding=new SourceEvidenceIntakePageObjects.Binding(
        UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),2,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
    private final SourceEvidenceIntakeFiles.Stored stored=new SourceEvidenceIntakeFiles.Stored(
        "source-evidence-intake/"+"a".repeat(64)+".json","a".repeat(64),12);

    @Test void stablePageIdentityDiffersAcrossOpenAndSequence() {
        var first=SourceEvidenceIntakePageObjects.originalId(binding.openKey(),1);

        assertThat(first).isEqualTo(SourceEvidenceIntakePageObjects.originalId(binding.openKey(),1));
        assertThat(first).isNotEqualTo(SourceEvidenceIntakePageObjects.originalId(binding.openKey(),2));
        assertThat(first).isNotEqualTo(SourceEvidenceIntakePageObjects.originalId(UUID.randomUUID(),1));
    }

    @Test void absentPageUsesV34NaturalKey() {
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of());

        assertThat(objects.locked(binding,1)).isNull();

        var sql=ArgumentCaptor.forClass(String.class);var args=ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForList(sql.capture(),args.capture());
        assertThat(sql.getValue()).contains("kind='MANIFEST_PAGE'").contains("original_object_id=?").contains("FOR UPDATE");
        assertThat((byte[])args.getValue()[3]).containsExactly(Ids.bytes(SourceEvidenceIntakePageObjects.originalId(binding.openKey(),1)));
    }

    @Test void existingObjectMustRemainBoundToOriginalDeviceAndOperator() {
        var row=row();row.put("origin_device_id",Ids.bytes(UUID.randomUUID()));
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(row));

        assertThatThrownBy(() -> objects.locked(binding,1))
            .isInstanceOfSatisfying(com.acrylic.outbound.common.BusinessException.class,
                error->assertThat(error.code()).isEqualTo("E2A_PAGE_OBJECT_INTEGRITY_FAILURE"));
    }

    @Test void existingPageReturnsItsImmutableFileReference() {
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(row()));

        var object=objects.locked(binding,1);

        assertThat(object.originalId()).isEqualTo(SourceEvidenceIntakePageObjects.originalId(binding.openKey(),1));
        assertThat(object.stored()).isEqualTo(stored);
    }

    @Test void alreadyLockedSourceReadDoesNotAcquireSameRowTwice() {
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(row()));

        assertThat(objects.findAfterSourceLock(binding,1)).isNotNull();

        var sql=ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(),any(Object[].class));
        assertThat(sql.getValue()).doesNotContain("FOR UPDATE");
    }

    @Test void insertOnlyAddsOnePageObjectWithoutSetOrItem() {
        when(jdbc.update(anyString(),any(Object[].class))).thenReturn(1);

        var object=objects.insert(binding,1,stored,Instant.EPOCH);

        var sql=ArgumentCaptor.forClass(String.class);var args=ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(),args.capture());
        assertThat(sql.getValue()).contains("INSERT INTO source_evidence_object").contains("'MANIFEST_PAGE'");
        assertThat(args.getValue()).hasSize(12);
        assertThat((byte[])args.getValue()[5]).containsExactly(Ids.bytes(object.originalId()));
        assertThat(args.getValue()[6]).isEqualTo(stored.sha256());
        assertThat(args.getValue()[7]).isEqualTo(stored.bytes());
    }

    private HashMap<String,Object> row() {
        var row=new HashMap<String,Object>();
        row.put("id",Ids.bytes(UUID.randomUUID()));row.put("warehouse_id",Ids.bytes(binding.warehouseId()));
        row.put("origin_device_id",Ids.bytes(binding.creatorDeviceId()));row.put("uploaded_by",Ids.bytes(binding.originalOperatorId()));
        row.put("bytes_sha256",stored.sha256());row.put("byte_count",stored.bytes());row.put("file_key",stored.key());
        return row;
    }
}
