package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.BusinessException;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PhysicalDispositionStoreTest {
    @Test void rejectsManifestOrderBeforeWriting() {
        var jdbc = mock(JdbcTemplate.class);
        var bales = List.of(UUID.randomUUID(), UUID.randomUUID());
        var error = catchThrowable(() -> new PhysicalDispositionStore(jdbc)
            .apply(verified(bales), "b".repeat(64), 1, bales.reversed(), Instant.now()));
        assertThat(error).isInstanceOf(BusinessException.class);
        verifyNoInteractions(jdbc);
    }

    @Test void rejectsMalformedManifestHashBeforeWriting() {
        var jdbc = mock(JdbcTemplate.class);
        var bales = List.of(UUID.randomUUID());
        assertThatThrownBy(() -> new PhysicalDispositionStore(jdbc)
            .apply(verified(bales), "not-a-sha256", 1, bales, Instant.now()))
            .isInstanceOf(BusinessException.class);
        verifyNoInteractions(jdbc);
    }

    @Test void persistsTheDispositionHashIndependentlyOfTheEvidenceSetHash() {
        var jdbc = mock(JdbcTemplate.class);
        var bales = List.of(UUID.randomUUID());
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            UUID id = new PhysicalDispositionStore(jdbc).apply(verified(bales), "b".repeat(64), 1, bales, Instant.now());
            assertThat(id).isNotNull();
            var headers = mockingDetails(jdbc).getInvocations().stream()
                .filter(call -> call.getArgument(0).toString().contains("INSERT INTO temporary_physical_disposition\n"))
                .toList();
            assertThat(headers).hasSize(1);
            assertThat(Arrays.asList(headers.getFirst().getArguments())).contains("b".repeat(64));
        } finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
    }

    private PhysicalDispositionVerification.Verified verified(List<UUID> bales) {
        return new PhysicalDispositionVerification.Verified(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1,
            UUID.randomUUID(), UUID.randomUUID(), "a".repeat(64), 1, 1, bales);
    }
}
