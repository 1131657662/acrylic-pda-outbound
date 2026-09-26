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

class SourceEvidenceIntakeStopObjectsTest {
    private final UUID warehouse = UUID.randomUUID(), scope = UUID.randomUUID(), submission = UUID.randomUUID();
    private final UUID device = UUID.randomUUID(), operator = UUID.randomUUID(), openKey = UUID.randomUUID();
    private final SourceEvidenceIntakeStopObjects.Binding binding = new SourceEvidenceIntakeStopObjects.Binding(
        warehouse, scope, submission, 3, device, operator, openKey);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final SourceEvidenceIntakeStopObjects objects = new SourceEvidenceIntakeStopObjects(jdbc);
    private final String hash = "a".repeat(64);
    private final SourceEvidenceIntakeObjectFiles.Stored stored = new SourceEvidenceIntakeObjectFiles.Stored(
        "source-evidence-intake/objects/" + hash + ".bin", hash, 100);

    @Test void originalIdIsStableWithinOpenAndDistinctAcrossOpens() {
        var original = SourceEvidenceIntakeStopObjects.originalId(openKey, "stop-event");
        assertThat(original).isEqualTo(SourceEvidenceIntakeStopObjects.originalId(openKey, "stop-event"));
        assertThat(original).isNotEqualTo(SourceEvidenceIntakeStopObjects.originalId(UUID.randomUUID(), "stop-event"));
    }

    @Test void insertBindsStopKindAndOriginalEventToSource() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        var object = objects.insert(binding, "stop-event", stored, Instant.EPOCH);

        assertThat(object.originalId()).isEqualTo(SourceEvidenceIntakeStopObjects.originalId(openKey, "stop-event"));
        var sql = ArgumentCaptor.forClass(String.class);
        var args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), args.capture());
        assertThat(sql.getValue()).contains("'STOP_PROOF'");
        assertThat((byte[]) args.getValue()[2]).containsExactly(Ids.bytes(scope));
        assertThat((byte[]) args.getValue()[3]).containsExactly(Ids.bytes(submission));
        assertThat(args.getValue()[4]).isEqualTo(3L);
        assertThat((byte[]) args.getValue()[5]).containsExactly(Ids.bytes(object.originalId()));
    }

    @Test void existingSameSourceReturnsImmutableStoredReference() {
        var id = UUID.randomUUID();
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row(id)));

        var found = objects.findAfterSourceLock(binding, "stop-event");

        assertThat(found.id()).isEqualTo(id);
        assertThat(found.stored()).isEqualTo(stored);
    }

    @Test void foreignDeviceOrMismatchedFileFailsClosed() {
        var wrongDevice = row(UUID.randomUUID());
        wrongDevice.put("origin_device_id", Ids.bytes(UUID.randomUUID()));
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(wrongDevice));
        assertThatThrownBy(() -> objects.findAfterSourceLock(binding, "stop-event"))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.code()).isEqualTo("E2A_STOP_OBJECT_INTEGRITY_FAILURE"));
        var wrongPath = row(UUID.randomUUID());
        wrongPath.put("file_key", "elsewhere/" + hash);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(wrongPath));
        assertThatThrownBy(() -> objects.findAfterSourceLock(binding, "stop-event"))
            .isInstanceOf(BusinessException.class);
    }

    @Test void malformedStoredIdentityFailsWithIntegrityError() {
        var malformed = row(UUID.randomUUID());
        malformed.put("warehouse_id", "corrupt");
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(malformed));

        assertThatThrownBy(() -> objects.findAfterSourceLock(binding, "stop-event"))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.code()).isEqualTo("E2A_STOP_OBJECT_INTEGRITY_FAILURE"));
    }

    private HashMap<String, Object> row(UUID id) {
        var row = new HashMap<String, Object>();
        row.put("id", Ids.bytes(id)); row.put("warehouse_id", Ids.bytes(warehouse));
        row.put("origin_device_id", Ids.bytes(device)); row.put("uploaded_by", Ids.bytes(operator));
        row.put("bytes_sha256", hash); row.put("byte_count", 100L); row.put("file_key", stored.key());
        return row;
    }
}
