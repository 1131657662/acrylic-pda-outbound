package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.sync.BarcodeChanges;
import com.acrylic.outbound.temporary.protocol.E2aPageItem;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SourceEvidenceIntakeReservationsTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final SourceEvidenceIntakeReservations reservations = new SourceEvidenceIntakeReservations(jdbc);
    private final SourceEvidenceIntakePageObjects.Binding binding = new SourceEvidenceIntakePageObjects.Binding(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    private final UUID setId = UUID.randomUUID();

    @Test void missingCommitmentIsInsertedOnlyForActiveScanAndNotified() {
        UUID bale = UUID.randomUUID();
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        when(jdbc.update(contains("INSERT INTO bale_commitment"), any(Object[].class))).thenReturn(1);

        try (var changes = mockStatic(BarcodeChanges.class)) {
            reservations.reserve(binding, setId, List.of(scan(bale, "ACTIVE"), scan(UUID.randomUUID(), "VOID")));
            changes.verify(() -> BarcodeChanges.bale(jdbc, bale));
        }

        var parameters = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(contains("INSERT INTO bale_commitment"), parameters.capture());
        assertThat((byte[])parameters.getValue()[0]).containsExactly(Ids.bytes(bale));
        assertThat(parameters.getValue()[1]).isEqualTo(2L);
        assertThat((byte[])parameters.getValue()[2]).containsExactly(Ids.bytes(binding.scopeId()));
        assertThat((byte[])parameters.getValue()[3]).containsExactly(Ids.bytes(binding.warehouseId()));
        assertThat((byte[])parameters.getValue()[4]).containsExactly(Ids.bytes(setId));
    }

    @Test void exactExistingSourceReservationRemainsUntouched() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row("SOURCE_RESERVED",
            null, setId.toString())));

        reservations.reserve(binding, setId, List.of(scan(UUID.randomUUID(), "ACTIVE")));

        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void sameSourceReviewReservationRemainsUntouched() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row("REVIEW_RESERVED",
            binding.submissionId().toString(), null)));

        reservations.reserve(binding, setId, List.of(scan(UUID.randomUUID(), "ACTIVE")));

        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void foreignSourceAndDuplicateActiveBaleFailBeforeAnyMutation() {
        UUID bale = UUID.randomUUID();
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row("SOURCE_RESERVED",
            null, UUID.randomUUID().toString())));

        assertThatThrownBy(() -> reservations.reserve(binding, setId, List.of(scan(bale, "ACTIVE"))))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.code()).isEqualTo("E2A_BALE_OWNER_CONFLICT"));
        assertThatThrownBy(() -> reservations.reserve(binding, setId,
            List.of(scan(bale, "ACTIVE"), scan(bale, "ACTIVE"))))
            .isInstanceOf(BusinessException.class);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void baleLocksAreRequestedInStableIdOrder() {
        UUID first = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID second = UUID.fromString("00000000-0000-4000-8000-000000000002");
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row("REVIEW_RESERVED",
            binding.submissionId().toString(), null)));

        reservations.reserve(binding, setId, List.of(scan(second, "ACTIVE"), scan(first, "ACTIVE")));

        var parameters = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, times(2)).queryForList(contains("FROM bale_commitment"), parameters.capture());
        assertThat((byte[])parameters.getAllValues().get(0)[0]).containsExactly(Ids.bytes(first));
        assertThat((byte[])parameters.getAllValues().get(1)[0]).containsExactly(Ids.bytes(second));
    }

    private E2aPageItem scan(UUID bale, String status) {
        return new E2aPageItem("SCAN",UUID.randomUUID(),"CAPTURE_RECORDS",UUID.randomUUID(),"a".repeat(64),
            1,1,"b".repeat(64),status,bale,2L,UUID.randomUUID());
    }

    private Map<String,Object> row(String state, String owner, String evidenceSet) {
        var row = new HashMap<String,Object>();
        row.put("state",state); row.put("dispatch_cycle",2L); row.put("owner",owner);
        row.put("source_scope",binding.scopeId().toString());
        row.put("source_warehouse",binding.warehouseId().toString()); row.put("evidence_set",evidenceSet);
        return row;
    }
}
