package com.acrylic.outbound.correction;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CorrectionStoreCycleReopeningTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CorrectionStore store = new CorrectionStore(jdbc);
    private final UUID order = UUID.randomUUID(), bale = UUID.randomUUID();

    @Test void matchingDispatchedOwnerIsDeletedAndBaleCycleAdvances() {
        when(jdbc.queryForObject(startsWith("SELECT dispatch_cycle"), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbc.queryForList(contains("FROM bale_commitment"), any(Object[].class)))
            .thenReturn(List.of(Map.of("order_id", order.toString(), "state", "DISPATCHED", "dispatch_cycle", 1L)));
        when(jdbc.update(startsWith("DELETE FROM bale_commitment"), any(Object[].class))).thenReturn(1);
        when(jdbc.update(startsWith("UPDATE bale SET dispatch_cycle"), any(Object[].class))).thenReturn(1);

        store.reopenDispatchOccupancy(order, bale, 1, 2);

        verify(jdbc).update(startsWith("DELETE FROM bale_commitment"), any(Object[].class));
        verify(jdbc).update(startsWith("UPDATE bale SET dispatch_cycle"), any(Object[].class));
    }

    @Test void anOwnerMismatchFailsClosedBeforeAnyMutation() {
        when(jdbc.queryForObject(startsWith("SELECT dispatch_cycle"), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbc.queryForList(contains("FROM bale_commitment"), any(Object[].class)))
            .thenReturn(List.of(Map.of("order_id", UUID.randomUUID().toString(), "state", "DISPATCHED", "dispatch_cycle", 1L)));

        assertThatThrownBy(() -> store.reopenDispatchOccupancy(order, bale, 1, 2))
            .isInstanceOfSatisfying(com.acrylic.outbound.common.BusinessException.class,
                error -> assertThat(error.code()).isEqualTo("CORRECTION_LEDGER_CONFLICT"));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
}
