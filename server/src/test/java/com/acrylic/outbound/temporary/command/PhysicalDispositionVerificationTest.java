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

    @org.junit.jupiter.api.BeforeEach void noHandoverPrefixByDefault() {
        when(jdbc.query(contains("temporary_handover_activation_owner"), any(org.springframework.jdbc.core.RowMapper.class),
            any(Object[].class))).thenReturn(List.of());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
        "[{\"code\":\"SUPERVISOR_REJECTED\",\"reason\":\"unload\"}]",
        "{\"issues\":[{\"code\":\"SUPERVISOR_REJECTED\",\"reason\":\"legacy\"}]}"
    })
    void readsCompleteVerificationAndReservations(String issues) {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
            row("REJECTED", issues, "VERIFIED_COMPLETE_AT_STOP")));
        when(jdbc.query(argThat((String sql)->!sql.contains("temporary_handover_activation_owner")), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
            .thenReturn(List.of(new PhysicalDispositionVerification.Reservation(bale, 7)));
        var result = reader.read(warehouse, scope, submission, 1, set, verification, "a".repeat(64), 1, 1);
        assertThat(result.activeBales()).containsExactly(bale);
    }

    @Test void returnsReservationsInManifestUuidOrder() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
            row("REJECTED", "{\"issues\":[{\"code\":\"SUPERVISOR_REJECTED\"}]}", "VERIFIED_COMPLETE_AT_STOP")));
        when(jdbc.query(argThat((String sql)->!sql.contains("temporary_handover_activation_owner")), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
            .thenReturn(List.of(new PhysicalDispositionVerification.Reservation(second, 7),
                new PhysicalDispositionVerification.Reservation(first, 3)));
        assertThat(reader.read(warehouse, scope, submission, 1, set, verification, "a".repeat(64), 1, 1).activeBales())
            .containsExactly(first, second);
    }

    @Test void rejectsPartialVerification() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
            row("REJECTED", "{\"issues\":[{\"code\":\"SUPERVISOR_REJECTED\"}]}", "PARTIAL")));
        assertThatThrownBy(() -> reader.read(warehouse, scope, submission, 1, set, verification, "a".repeat(64), 1, 1))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("DISPOSITION_VERIFICATION_NOT_COMPLETE"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"different-cycle", "other-bale", "extra-bale", "duplicate-evidence"})
    void rejectsReservationsThatDoNotExactlyMatchTrustedActiveScans(String mismatch) {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
            row("REJECTED", "[{\"code\":\"SUPERVISOR_REJECTED\"}]", "VERIFIED_COMPLETE_AT_STOP")));
        var original = new PhysicalDispositionVerification.Reservation(bale, 7);
        var foreign = new PhysicalDispositionVerification.Reservation(UUID.randomUUID(), 7);
        var expected = mismatch.equals("duplicate-evidence") ? List.of(original, original) : List.of(original);
        var actual = switch (mismatch) {
            case "different-cycle" -> List.of(new PhysicalDispositionVerification.Reservation(bale, 8));
            case "other-bale" -> List.of(foreign);
            case "extra-bale" -> List.of(original, foreign);
            default -> List.of(original, original);
        };
        when(jdbc.query(argThat((String sql)->!sql.contains("temporary_handover_activation_owner")), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
            .thenReturn(expected, actual);
        assertThatThrownBy(() -> reader.read(warehouse, scope, submission, 1, set, verification, "a".repeat(64), 1, 1))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.code()).isEqualTo("DISPOSITION_RESERVATION_CORRUPT"));
    }

    @Test void rejectsDispositionBeforeSupervisorRejection() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
            row("REVIEW_REQUIRED", "{\"issues\":[]}", "VERIFIED_COMPLETE_AT_STOP")));
        assertThatThrownBy(() -> reader.read(warehouse, scope, submission, 1, set, verification, "a".repeat(64), 1, 1))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("DISPOSITION_REJECTION_REQUIRED"));
    }

    @Test void permitsAbortedSourceOnlyForCloseDisposition() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
            row("ABORTED", "{\"issues\":[]}", "VERIFIED_COMPLETE_AT_STOP")));
        when(jdbc.query(argThat((String sql)->!sql.contains("temporary_handover_activation_owner")), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
            .thenReturn(List.of(new PhysicalDispositionVerification.Reservation(bale, 1)));
        var result = reader.readForClose(warehouse, scope, submission, 1, set, verification, "a".repeat(64), 1, 1);
        assertThat(result.activeBales()).containsExactly(bale);
    }

    @Test void permitsCompleteAbortedSourceForContinuation() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
            row("ABORTED", "{\"issues\":[]}", "VERIFIED_COMPLETE_AT_STOP")));
        when(jdbc.query(argThat((String sql)->!sql.contains("temporary_handover_activation_owner")), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
            .thenReturn(List.of(new PhysicalDispositionVerification.Reservation(bale, 1)));

        var result = reader.readForContinuation(warehouse, scope, submission, 1, set, verification, "a".repeat(64), 1, 1);

        assertThat(result.status()).isEqualTo("ABORTED");
        assertThat(result.activeBales()).containsExactly(bale);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"REJECTED", "REVIEW_REQUIRED", "SUCCEEDED", "REGISTERED", "RECEIVING"})
    void rejectsNonAbortedSourceForContinuation(String status) {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
            row(status, "{\"issues\":[{\"code\":\"SUPERVISOR_REJECTED\"}]}", "VERIFIED_COMPLETE_AT_STOP")));

        assertThatThrownBy(() -> reader.readForContinuation(warehouse, scope, submission, 1, set, verification,
            "a".repeat(64), 1, 1))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.code()).isEqualTo("CONTINUATION_ABORT_REQUIRED"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"PARTIAL", "CONFLICT", "UNVERIFIABLE"})
    void abortedSourceStillRequiresCompleteStopVerification(String verificationResult) {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
            row("ABORTED", "[]", verificationResult)));

        assertThatThrownBy(() -> reader.readForContinuation(warehouse, scope, submission, 1, set, verification,
            "a".repeat(64), 1, 1))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.code()).isEqualTo("DISPOSITION_VERIFICATION_NOT_COMPLETE"));
    }

    @Test void rejectsAbortedSourceForOrdinaryDisposition() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
            row("ABORTED", "{\"issues\":[]}", "VERIFIED_COMPLETE_AT_STOP")));
        assertThatThrownBy(() -> reader.read(warehouse, scope, submission, 1, set, verification, "a".repeat(64), 1, 1))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("DISPOSITION_REJECTION_REQUIRED"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"valid","missingInheritedOwner","wrongInheritedCycle","duplicateInherited"})
    void matchesCurrentSuffixWithImmutableHandoverOwners(String mutation) {
        var old=new PhysicalDispositionVerification.Reservation(UUID.randomUUID(),3);
        var own=new PhysicalDispositionVerification.Reservation(bale,7);
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(row("ABORTED","[]","VERIFIED_COMPLETE_AT_STOP")));
        when(jdbc.query(contains("FROM source_evidence_item"),any(org.springframework.jdbc.core.RowMapper.class),any(Object[].class)))
            .thenReturn(List.of(own));
        when(jdbc.query(contains("temporary_handover_activation_owner"),any(org.springframework.jdbc.core.RowMapper.class),any(Object[].class)))
            .thenReturn(mutation.equals("duplicateInherited")?List.of(old,old):List.of(old));
        var actual=mutation.equals("missingInheritedOwner")?List.of(own):List.of(own,
            new PhysicalDispositionVerification.Reservation(old.bale(),mutation.equals("wrongInheritedCycle")?4:3));
        when(jdbc.query(contains("FROM bale_commitment"),any(org.springframework.jdbc.core.RowMapper.class),any(Object[].class))).thenReturn(actual);
        if(mutation.equals("valid")) assertThat(reader.readForContinuation(warehouse,scope,submission,2,set,verification,"a".repeat(64),1,1)
            .activeBales()).containsExactlyInAnyOrder(bale,old.bale());
        else assertThatThrownBy(()->reader.readForContinuation(warehouse,scope,submission,2,set,verification,"a".repeat(64),1,1))
            .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.code()).isEqualTo("DISPOSITION_RESERVATION_CORRUPT"));
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
