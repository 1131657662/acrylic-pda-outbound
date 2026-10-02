package com.acrylic.outbound.temporary.command;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.acrylic.outbound.common.BusinessException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PhysicalDispositionVerificationTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PhysicalDispositionVerification reader = new PhysicalDispositionVerification(jdbc);
    private final UUID warehouse = UUID.randomUUID(), scope = UUID.randomUUID(), submission = UUID.randomUUID();
    private final UUID set = UUID.randomUUID(), verification = UUID.randomUUID(), bale = UUID.randomUUID();

    @Test void readsCompleteVerificationAndReservations() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
            row("REJECTED", "{\"issues\":[{\"code\":\"SUPERVISOR_REJECTED\"}]}", "VERIFIED_COMPLETE_AT_STOP")));
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
            .thenReturn(List.of(bale));
        var result = reader.read(warehouse, scope, submission, 1, set, verification, "a".repeat(64), 1, 1);
        assertThat(result.activeBales()).containsExactly(bale);
    }

    @Test void rejectsPartialVerification() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
            row("REJECTED", "{\"issues\":[{\"code\":\"SUPERVISOR_REJECTED\"}]}", "PARTIAL")));
        assertThatThrownBy(() -> reader.read(warehouse, scope, submission, 1, set, verification, "a".repeat(64), 1, 1))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("DISPOSITION_VERIFICATION_NOT_COMPLETE"));
    }

    @Test void rejectsDispositionBeforeSupervisorRejection() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
            row("REVIEW_REQUIRED", "{\"issues\":[]}", "VERIFIED_COMPLETE_AT_STOP")));
        assertThatThrownBy(() -> reader.read(warehouse, scope, submission, 1, set, verification, "a".repeat(64), 1, 1))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("DISPOSITION_REJECTION_REQUIRED"));
    }

    @Test void rejectsSubstringWithoutSupervisorIssueCode() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
            row("REJECTED", "{\"issues\":[{\"code\":\"OTHER\",\"reason\":\"SUPERVISOR_REJECTED\"}]}", "VERIFIED_COMPLETE_AT_STOP")));
        assertThatThrownBy(() -> reader.read(warehouse, scope, submission, 1, set, verification, "a".repeat(64), 1, 1))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("DISPOSITION_REJECTION_REQUIRED"));
    }

    private Map<String, Object> row(String status, String issues, String result) {
        return Map.of("submission_status", status, "issues_json", issues, "result", result,
            "set_sha256", "a".repeat(64), "verification_version", 1L, "algorithm_version", 1L);
    }
}
