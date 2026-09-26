package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
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

class SourceEvidenceIntakeRawObjectsTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final SourceEvidenceIntakeRawObjects objects=new SourceEvidenceIntakeRawObjects(jdbc);
    private final SourceEvidenceIntakeRawObjects.Binding binding=new SourceEvidenceIntakeRawObjects.Binding(
        UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),3,UUID.randomUUID(),UUID.randomUUID(),
        "CAPTURE_RECORDS",UUID.randomUUID());
    private final SourceEvidenceIntakeObjectFiles.Stored stored=new SourceEvidenceIntakeObjectFiles.Stored(
        "source-evidence-intake/objects/"+"a".repeat(64)+".bin","a".repeat(64),7);

    @Test void rejectsKindsOutsideV34RawObjectAllowlist() {
        assertThatThrownBy(() -> new SourceEvidenceIntakeRawObjects.Binding(binding.warehouseId(),binding.scopeId(),
            binding.submissionId(),3,binding.creatorDeviceId(),binding.originalOperatorId(),"STOP_PROOF",
            binding.originalObjectId())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void findsOriginalObjectWithoutLockingEvidenceRowsTwice() {
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(row()));

        var found=objects.findAfterSourceLock(binding);

        assertThat(found.stored()).isEqualTo(stored);
        assertThat(found.originalObjectId()).isEqualTo(binding.originalObjectId());
        var sql=ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(),any(Object[].class));
        assertThat(sql.getValue()).contains("kind=?").contains("original_object_id=?").doesNotContain("FOR UPDATE");
    }

    @Test void rejectsForeignDeviceAndUnexpectedFileReference() {
        var foreign=row();foreign.put("origin_device_id",Ids.bytes(UUID.randomUUID()));
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(foreign));

        assertThatThrownBy(() -> objects.findAfterSourceLock(binding)).isInstanceOfSatisfying(BusinessException.class,
            error->assertThat(error.code()).isEqualTo("E2A_RAW_OBJECT_INTEGRITY_FAILURE"));
    }

    @Test void insertsOnlyOriginBoundRawObject() {
        when(jdbc.update(anyString(),any(Object[].class))).thenReturn(1);

        var inserted=objects.insert(binding,stored,Instant.EPOCH);

        var sql=ArgumentCaptor.forClass(String.class);var args=ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(),args.capture());
        assertThat(sql.getValue()).contains("INSERT INTO source_evidence_object").doesNotContain("source_evidence_item");
        assertThat(args.getValue()).hasSize(13);
        assertThat(args.getValue()[5]).isEqualTo("CAPTURE_RECORDS");
        assertThat((byte[])args.getValue()[6]).containsExactly(Ids.bytes(binding.originalObjectId()));
        assertThat(inserted.stored()).isEqualTo(stored);
    }

    private HashMap<String,Object> row() {
        var row=new HashMap<String,Object>();
        row.put("id",Ids.bytes(UUID.randomUUID()));row.put("warehouse_id",Ids.bytes(binding.warehouseId()));
        row.put("origin_device_id",Ids.bytes(binding.creatorDeviceId()));
        row.put("uploaded_by",Ids.bytes(binding.originalOperatorId()));
        row.put("bytes_sha256",stored.sha256());row.put("byte_count",stored.bytes());row.put("file_key",stored.key());
        return row;
    }
}
