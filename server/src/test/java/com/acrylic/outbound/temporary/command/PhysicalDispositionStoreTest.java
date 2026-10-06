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
            .apply(verified(bales), "b".repeat(64), 1, items(bales.reversed(), "RELEASE"), Instant.now()));
        assertThat(error).isInstanceOf(BusinessException.class);
        verifyNoInteractions(jdbc);
    }

    @Test void rejectsMalformedManifestHashBeforeWriting() {
        var jdbc = mock(JdbcTemplate.class);
        var bales = List.of(UUID.randomUUID());
        assertThatThrownBy(() -> new PhysicalDispositionStore(jdbc)
            .apply(verified(bales), "not-a-sha256", 1, items(bales, "RELEASE"), Instant.now()))
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
            UUID id = new PhysicalDispositionStore(jdbc).apply(verified(bales), "b".repeat(64), 1,
                items(bales, "QUARANTINE"), Instant.now());
            assertThat(id).isNotNull();
            var headers = mockingDetails(jdbc).getInvocations().stream()
                .filter(call -> call.getArgument(0).toString().contains("INSERT INTO temporary_physical_disposition\n"))
                .toList();
            assertThat(headers).hasSize(1);
            assertThat(Arrays.asList(headers.getFirst().getArguments())).contains("b".repeat(64));
        } finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
    }

    @Test void releaseDeletesOnlyTheVerifiedSourceReservation() {
        var jdbc = mock(JdbcTemplate.class);
        var bale = UUID.randomUUID();
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(7L);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            new PhysicalDispositionStore(jdbc).apply(verified(List.of(bale)), "b".repeat(64), 1,
                items(List.of(bale), "RELEASE"), Instant.now());
            assertThat(mockingDetails(jdbc).getInvocations()).anyMatch(call -> call.getArgument(0).toString().contains("DELETE FROM bale_commitment"));
            assertThat(mockingDetails(jdbc).getInvocations()).anyMatch(call -> call.getArgument(0).toString().contains("temporary_physical_disposition_item") && Arrays.asList(call.getArguments()).contains("RELEASE"));
        } finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
    }

    @Test void retainRecordsAnAbortedDispositionWithoutChangingTheReservation() {
        var jdbc = mock(JdbcTemplate.class);
        var bale = UUID.randomUUID();
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(7L);
        when(jdbc.queryForList(contains("SELECT k.bale_id"), any(Object[].class)))
            .thenReturn(List.of(Map.of("bale_id", com.acrylic.outbound.common.Ids.bytes(bale))));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            new PhysicalDispositionStore(jdbc).apply(verified("ABORTED", List.of(bale)), "b".repeat(64), 1,
                items(List.of(bale), "RETAIN"), Instant.now());

            assertThat(mockingDetails(jdbc).getInvocations()).anyMatch(call ->
                call.getArgument(0).toString().contains("temporary_physical_disposition_item")
                    && Arrays.asList(call.getArguments()).contains("RETAIN"));
            assertThat(mockingDetails(jdbc).getInvocations()).noneMatch(call -> {
                String sql = call.getArgument(0).toString();
                return sql.contains("DELETE FROM bale_commitment") || sql.contains("UPDATE bale_commitment");
            });
        } finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
    }

    @Test void retainFailsWhenExactReservationNoLongerExists() {
        var jdbc = mock(JdbcTemplate.class);
        var bale = UUID.randomUUID();
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(7L);

        assertThatThrownBy(() -> new PhysicalDispositionStore(jdbc).apply(verified("ABORTED", List.of(bale)),
            "b".repeat(64), 1, items(List.of(bale), "RETAIN"), Instant.now()))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.code()).isEqualTo("DISPOSITION_RESERVATION_CHANGED"));
    }

    @Test void retainIsRejectedForRejectedSourcesBeforeWriting() {
        var jdbc = mock(JdbcTemplate.class);
        var bale = UUID.randomUUID();
        assertThatThrownBy(() -> new PhysicalDispositionStore(jdbc).apply(verified(List.of(bale)), "b".repeat(64), 1,
            items(List.of(bale), "RETAIN"), Instant.now()))
            .isInstanceOf(BusinessException.class);
        verifyNoInteractions(jdbc);
    }

    @Test void closeCannotRetainPackages() {
        var jdbc = mock(JdbcTemplate.class);
        var bale = UUID.randomUUID();
        assertThatThrownBy(() -> new PhysicalDispositionStore(jdbc).apply(verified("ABORTED", List.of(bale)), "b".repeat(64), 1,
            items(List.of(bale), "RETAIN"), Instant.now(), true, "close"))
            .isInstanceOf(BusinessException.class);
        verifyNoInteractions(jdbc);
    }

    @Test void closeRequiresAHumanReasonBeforeAnyWrite() {
        var jdbc = mock(JdbcTemplate.class);
        var bale = UUID.randomUUID();
        assertThatThrownBy(() -> new PhysicalDispositionStore(jdbc).apply(verified(List.of(bale)), "b".repeat(64), 1,
            items(List.of(bale), "RELEASE"), Instant.now(), true, " "))
            .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("CLOSE_REASON_REQUIRED");
        verifyNoInteractions(jdbc);
    }

    @Test void closeClearsTheSourceAndRecordsCloseMode() {
        var jdbc = mock(JdbcTemplate.class);
        var bale = UUID.randomUUID();
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(7L);
        when(jdbc.queryForList(anyString(), eq(byte[].class), any(Object[].class))).thenReturn(Collections.singletonList(null));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            new PhysicalDispositionStore(jdbc).apply(verified(List.of(bale)), "b".repeat(64), 1,
                items(List.of(bale), "RELEASE"), Instant.now(), true, "operator close");
            var calls = mockingDetails(jdbc).getInvocations();
            assertThat(calls).anyMatch(call -> call.getArgument(0).toString().contains("close_mode"));
            assertThat(calls).anyMatch(call -> call.getArgument(0).toString().contains("current_submission_id=NULL"));
        } finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
    }

    private static List<PhysicalDispositionManifest.Item> items(List<UUID> bales, String action) {
        return bales.stream().map(bale -> new PhysicalDispositionManifest.Item(bale, action)).toList();
    }

    private PhysicalDispositionVerification.Verified verified(List<UUID> bales) {
        return verified("REJECTED", bales);
    }

    private PhysicalDispositionVerification.Verified verified(String status, List<UUID> bales) {
        return new PhysicalDispositionVerification.Verified(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1,
            UUID.randomUUID(), UUID.randomUUID(), status, "a".repeat(64), 1, 1, bales);
    }
}
