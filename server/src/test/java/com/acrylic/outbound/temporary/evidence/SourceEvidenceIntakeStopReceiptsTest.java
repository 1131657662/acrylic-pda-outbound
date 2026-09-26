package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class SourceEvidenceIntakeStopReceiptsTest {
    private static final byte[] REQUEST = "{\"operation\":\"STOP\"}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] RESPONSE = "{\"status\":\"PARTIAL\"}".getBytes(StandardCharsets.UTF_8);
    private final UUID warehouse = UUID.randomUUID(), operator = UUID.randomUUID(), device = UUID.randomUUID();
    private final UUID submission = UUID.randomUUID(), authorization = UUID.randomUUID();
    private final UUID stopKey = UUID.randomUUID(), objectId = UUID.randomUUID();
    private final SourceEvidenceIntakeReceipts.BoundKey key = new SourceEvidenceIntakeReceipts.BoundKey(
        operator, stopKey, warehouse, submission, 3, device, authorization);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final SourceEvidenceIntakeStopReceipts receipts = new SourceEvidenceIntakeStopReceipts(jdbc);

    @Test void originalStopReplaysOnlyItsImmutableResponse() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row()));
        var decision = receipts.existing(key, REQUEST);
        assertThat(decision.status()).isEqualTo(SourceEvidenceIntakeReplay.Status.REPLAY);
        assertThat(decision.originalResponseBytes()).containsExactly(RESPONSE);
    }

    @Test void foreignSourceOrWrongOperationCannotReplay() {
        var foreign = row(); foreign.put("submission_id", Ids.bytes(UUID.randomUUID()));
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(foreign));
        assertThat(receipts.existing(key, REQUEST).status()).isEqualTo(SourceEvidenceIntakeReplay.Status.CONFLICT);
        var wrong = row(); wrong.put("operation_kind", "PAGE");
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(wrong));
        assertThat(receipts.existing(key, REQUEST).status()).isEqualTo(SourceEvidenceIntakeReplay.Status.INTEGRITY_FAILURE);
    }

    @Test void missingStopObjectFailsIntegrityAndLockedLookupUsesForUpdate() {
        var missing = row(); missing.put("object_id", null);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(missing));
        assertThat(receipts.existing(key, REQUEST).status()).isEqualTo(SourceEvidenceIntakeReplay.Status.INTEGRITY_FAILURE);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        assertThat(receipts.existingLocked(key, REQUEST).status()).isEqualTo(SourceEvidenceIntakeReplay.Status.NOT_FOUND);
        var sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, org.mockito.Mockito.atLeastOnce()).queryForList(sql.capture(), any(Object[].class));
        assertThat(sql.getValue().trim()).endsWith("FOR UPDATE");
    }

    @Test void stopReceiptCannotReplayIfItClaimsPrematureClosure() {
        var altered = row(); altered.put("evidence_set_id", Ids.bytes(UUID.randomUUID()));
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(altered));
        assertThat(receipts.existing(key, REQUEST).status())
            .isEqualTo(SourceEvidenceIntakeReplay.Status.INTEGRITY_FAILURE);
    }

    @Test void stopReceiptCannotReplayIfObjectIsAPage() {
        var altered = row(); altered.put("object_kind", "MANIFEST_PAGE");
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(altered));
        assertThat(receipts.existing(key, REQUEST).status())
            .isEqualTo(SourceEvidenceIntakeReplay.Status.INTEGRITY_FAILURE);
    }

    @Test void lockedReplayReturnsTheReceiptObjectReferenceOnlyWhenValid() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row()));
        assertThat(receipts.objectIdForLockedReplay(key, REQUEST)).isEqualTo(objectId);
        var corrupt = row(); corrupt.put("object_id", new byte[15]);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(corrupt));
        assertThatThrownBy(() -> receipts.objectIdForLockedReplay(key, REQUEST))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.code()).isEqualTo("E2A_STOP_RECEIPT_INTEGRITY_FAILURE"));
    }

    @Test void insertPreservesStopObjectReferenceButNoSetOrVerification() throws Exception {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        var context = new SourceEvidenceIntakeReceipts.OpenInsert(operator, stopKey, warehouse, UUID.randomUUID(),
            submission, 3, device, UUID.randomUUID(), authorization, operator, 4, UUID.randomUUID(),
            "7", "2", REQUEST, E2aProtocol.intakeSha256(REQUEST));
        var receipt = new SourceEvidenceIntakeReceiptCodec.Receipt(UUID.randomUUID(), RESPONSE,
            ImportFiles.sha256(RESPONSE), "kid", "token", Instant.EPOCH);

        receipts.insert(context, objectId, receipt);

        var sql = ArgumentCaptor.forClass(String.class);
        var arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), arguments.capture());
        assertThat(sql.getValue()).contains("object_id,evidence_set_id,evidence_verification_id")
            .contains("NULL,NULL");
        assertThat(arguments.getValue()).hasSize(25);
        assertThat(arguments.getValue()[15]).isEqualTo("STOP");
        assertThat(arguments.getValue()[16]).isEqualTo("PARTIAL");
        assertThat((byte[]) arguments.getValue()[23]).containsExactly(Ids.bytes(objectId));
        var method = SourceEvidenceIntakeStopReceipts.class.getMethod("recheckAfterRollback",
            SourceEvidenceIntakeReceipts.BoundKey.class, byte[].class);
        var transaction = new AnnotationTransactionAttributeSource().getTransactionAttribute(method,
            SourceEvidenceIntakeStopReceipts.class);
        assertThat(transaction.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        assertThat(transaction.isReadOnly()).isTrue();
    }

    private HashMap<String, Object> row() {
        var row = new HashMap<String, Object>();
        row.put("receipt_version", 1); row.put("canonical_request_bytes", REQUEST);
        row.put("request_sha256", E2aProtocol.intakeSha256(REQUEST));
        row.put("original_response_bytes", RESPONSE);
        row.put("original_response_sha256", ImportFiles.sha256(RESPONSE));
        row.put("warehouse_id", Ids.bytes(warehouse)); row.put("submission_id", Ids.bytes(submission));
        row.put("work_round", 3L); row.put("creator_device_id", Ids.bytes(device));
        row.put("temporary_authorization_id", Ids.bytes(authorization));
        row.put("original_operator_id", Ids.bytes(operator)); row.put("operation_kind", "STOP");
        row.put("receipt_status", "PARTIAL"); row.put("object_id", Ids.bytes(objectId));
        row.put("object_kind", "STOP_PROOF");
        return row;
    }
}
