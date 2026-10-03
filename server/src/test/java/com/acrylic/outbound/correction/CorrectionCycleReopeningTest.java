package com.acrylic.outbound.correction;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CorrectionCycleReopeningTest {
    private final UUID order = UUID.randomUUID(), dispatch = UUID.randomUUID(), bale = UUID.randomUUID(), lineId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-02T00:00:00Z");

    @Test void reopeningReleasesTheCurrentDispatchOwnerBeforePostingAccounting() {
        var store = mock(CorrectionStore.class); var accounting = mock(CorrectionAccounting.class);
        when(store.cycleReopeningExists(dispatch)).thenReturn(false);
        when(store.dispatchFacts(order, dispatch)).thenReturn(facts());
        when(store.recoveryEpoch()).thenReturn(7L);
        when(accounting.approve(anyMap(), eq(bale), eq(now))).thenReturn(List.of("CORR:" + lineId));
        var result = new CorrectionApprovalEffects(store, accounting).apply(write(), 4, List.of(line()));

        assertThat(result.path("reopenedCycles")).hasSize(1);
        var calls = inOrder(store, accounting);
        calls.verify(store).reopenDispatchOccupancy(order, bale, 1, 2);
        calls.verify(store).insertCycleReopening(any(), eq(dispatch), eq(lineId), eq(1), eq(2), eq("a".repeat(64)), eq(4L), eq(7L), eq(now));
        calls.verify(accounting).approve(anyMap(), eq(bale), eq(now));
    }

    @Test void duplicateReopeningStopsBeforeAccountingOrOccupancyMutation() {
        var store = mock(CorrectionStore.class); var accounting = mock(CorrectionAccounting.class);
        when(store.cycleReopeningExists(dispatch)).thenReturn(true);
        when(store.dispatchFacts(order, dispatch)).thenReturn(facts());

        assertThatThrownBy(() -> new CorrectionApprovalEffects(store, accounting).apply(write(), 4, List.of(line())))
            .isInstanceOfSatisfying(com.acrylic.outbound.common.BusinessException.class,
                error -> assertThat(error.code()).isEqualTo("CYCLE_REOPENING_EXISTS"));
        verifyNoInteractions(accounting);
        verify(store, never()).reopenDispatchOccupancy(any(), any(), anyInt(), anyInt());
        verify(store, never()).insertCycleReopening(any(), any(), any(), anyInt(), anyInt(), anyString(), anyLong(), anyLong(), any());
    }

    @Test void physicalReturnAlsoReopensTheControlledDispatchCycle() {
        var store = mock(CorrectionStore.class); var accounting = mock(CorrectionAccounting.class);
        when(store.cycleReopeningExists(dispatch)).thenReturn(false);
        when(store.dispatchFacts(order, dispatch)).thenReturn(facts());
        when(store.recoveryEpoch()).thenReturn(7L);
        when(accounting.approve(anyMap(), eq(bale), eq(now))).thenReturn(List.of());

        var result = new CorrectionApprovalEffects(store, accounting).apply(write("PHYSICAL_RETURN"), 4, List.of(line()));

        assertThat(result.path("reopenedCycles")).hasSize(1);
        verify(store).reopenDispatchOccupancy(order, bale, 1, 2);
        verify(store).insertCycleReopening(any(), eq(dispatch), eq(lineId), eq(1), eq(2), eq("a".repeat(64)),
            eq(4L), eq(7L), eq(now));
    }

    private JsonNode facts() {
        return SubmissionJson.object().put("baleId", bale.toString()).put("dispatchCycle", "1");
    }

    private Map<String,Object> line() {
        return new HashMap<>(Map.of("line_id", lineId, "dispatch_id", dispatch, "before_count", 1L,
            "before_weight_g", 100L, "target_count", 0L, "target_weight_g", 0L,
            "business_occurred_at", now, "evidence_sha256", "a".repeat(64)));
    }

    private CorrectionCommand.Write write() {
        return write("DISPATCH_CANCELLATION");
    }

    private CorrectionCommand.Write write(String type) {
        var body = SubmissionJson.object().put("commandVersion", 1).put("commandType", "APPROVE")
            .put("commandId", UUID.randomUUID().toString()).put("warehouseId", UUID.randomUUID().toString())
            .put("documentId", UUID.randomUUID().toString());
        var actor = SubmissionJson.object().put("principalId", UUID.randomUUID().toString());
        var anchor = SubmissionJson.object().put("expectedResultVersion", 3).put("type", type);
        return new CorrectionCommand.Write(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), order, now,
            new CorrectionCommand.Input(SubmissionJson.canonical(body).getBytes(java.nio.charset.StandardCharsets.UTF_8)), anchor, actor);
    }
}
