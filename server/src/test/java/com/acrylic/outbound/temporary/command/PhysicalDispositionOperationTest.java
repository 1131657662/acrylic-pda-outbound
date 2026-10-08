package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises the operation with real validation/store logic and only JDBC replaced. */
class PhysicalDispositionOperationTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final UUID bale = UUID.randomUUID();
    private final PhysicalDispositionOperation operation = new PhysicalDispositionOperation(
        new PhysicalDispositionVerification(jdbc), new PhysicalDispositionStore(jdbc));

    @BeforeEach void beginTransactionContext() { TransactionSynchronizationManager.setActualTransactionActive(true); }
    @AfterEach void endTransactionContext() { TransactionSynchronizationManager.setActualTransactionActive(false); }

    @Test void abortedRetentionRecordsAValidDecisionWithoutReleasingStock() {
        facts("ABORTED", "VERIFIED_COMPLETE_AT_STOP");

        var decision = operation.execute(write("ABORTED", "RETAIN"));

        assertThat(decision.path("products").get(0).path("disposition").asText()).isEqualTo("RETAIN");
        ObjectNode receipt = SubmissionJson.object().put("status", "RECORDED").put("commandType", "PHYSICAL_DISPOSITION");
        receipt.set("businessDecision", decision);
        CommandDecision.validate(receipt);
        assertThat(mockingDetails(jdbc).getInvocations()).noneMatch(call -> {
            String sql = call.getArgument(0).toString();
            return sql.contains("DELETE FROM bale_commitment") || sql.contains("UPDATE bale_commitment")
                || sql.contains("barcode_sync_pending");
        });
    }

    @Test void rejectedSourceCannotRetainEvenWithCompleteStopEvidence() {
        facts("REJECTED", "VERIFIED_COMPLETE_AT_STOP");

        assertThatThrownBy(() -> operation.execute(write("REJECTED", "RETAIN")))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.code()).isEqualTo("DISPOSITION_MANIFEST_VERSION_CONFLICT"));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void rejectedSourceCanStillReleaseWithCompleteStopEvidence() {
        facts("REJECTED", "VERIFIED_COMPLETE_AT_STOP");

        var decision = operation.execute(write("REJECTED", "RELEASE"));

        assertThat(decision.path("products").get(0).path("disposition").asText()).isEqualTo("RELEASE");
        verify(jdbc).update(contains("DELETE FROM bale_commitment"), any(Object[].class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PARTIAL", "CONFLICT", "UNVERIFIABLE"})
    void incompleteStopCannotWriteDisposition(String result) {
        facts("ABORTED", result);

        assertThatThrownBy(() -> operation.execute(write("ABORTED", "RETAIN")))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.code()).isEqualTo("DISPOSITION_VERIFICATION_NOT_COMPLETE"));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"REVIEW_REQUIRED", "SUCCEEDED", "RECEIVING"})
    void nonterminalOrSuccessfulSourcesCannotBeDisposed(String status) {
        facts(status, "VERIFIED_COMPLETE_AT_STOP");

        assertThatThrownBy(() -> operation.execute(write(status, "RETAIN")))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.code()).isEqualTo("DISPOSITION_REJECTION_REQUIRED"));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    private void facts(String status, String result) {
        when(jdbc.queryForList(contains("FROM source_evidence_verification"), any(Object[].class)))
            .thenReturn(List.of(Map.of("submission_status", status, "issues_json", "[{\"code\":\"SUPERVISOR_REJECTED\"}]",
                "result", result, "set_sha256", "a".repeat(64), "verification_version", 1L, "algorithm_version", 1L)));
        when(jdbc.query(contains("FROM source_evidence_item"), any(RowMapper.class), any(Object[].class)))
            .thenReturn(List.of(new PhysicalDispositionVerification.Reservation(bale, 7)));
        when(jdbc.query(contains("FROM bale_commitment"), any(RowMapper.class), any(Object[].class)))
            .thenReturn(List.of(new PhysicalDispositionVerification.Reservation(bale, 7)));
        when(jdbc.queryForList(contains("SELECT k.bale_id"), any(Object[].class)))
            .thenReturn(List.of(Map.of("bale_id", Ids.bytes(bale))));
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(7L);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
    }

    private CommandWrite write(String status, String action) {
        CommandWrite base = CommandReceiptFixture.write();
        ObjectNode body = (ObjectNode) base.command().body();
        body.put("commandType", "PHYSICAL_DISPOSITION").put("reason", "physical inventory checked");
        var expected = (ObjectNode) body.path("expected");
        expected.put("evidenceSetId", UUID.randomUUID().toString()).put("evidenceSetSha256", "a".repeat(64))
            .put("verificationId", UUID.randomUUID().toString()).put("verificationVersion", "1")
            .put("verificationAlgorithmVersion", "1");
        ObjectNode manifest = body.putObject("dispositionManifest").put("version", 1);
        manifest.putArray("items").addObject().put("baleId", bale.toString()).put("disposition", action);
        expected.put("dispositionManifestSha256", SubmissionJson.sha(manifest)).put("dispositionManifestVersion", "1");
        var command = CommandInput.parse(SubmissionJson.canonical(body).getBytes(StandardCharsets.UTF_8));
        var context = new CommandContext(((ObjectNode) base.context().json()).put("status", status));
        return new CommandWrite(base.requestId(), base.receiptId(), base.eventId(), base.recordedAt(), command, context, base.actor());
    }
}
