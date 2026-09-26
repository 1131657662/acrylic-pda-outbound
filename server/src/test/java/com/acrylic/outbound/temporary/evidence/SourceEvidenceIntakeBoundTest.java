package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SourceEvidenceIntakeBoundTest {
    private static final byte[] REQUEST="{\"operation\":\"OPEN\"}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] RESPONSE="{\"status\":\"RECEIVED\"}".getBytes(StandardCharsets.UTF_8);
    private final SourceEvidenceIntakeReceipts.BoundKey key=new SourceEvidenceIntakeReceipts.BoundKey(
        UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),2,UUID.randomUUID(),UUID.randomUUID());
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final SourceEvidenceIntakeReceipts receipts=new SourceEvidenceIntakeReceipts(jdbc);

    @Test void matchingBoundKeyReturnsStoredBytes() {
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(row()));

        var result=receipts.existingBound(key,REQUEST);

        assertThat(result.status()).isEqualTo(SourceEvidenceIntakeReplay.Status.REPLAY);
        assertThat(result.originalResponseBytes()).containsExactly(RESPONSE);
        assertKeyQuery(false);
    }

    @Test void otherSourceCannotRetrieveSamePrincipalsOriginalReceipt() {
        var other=row();other.put("submission_id",Ids.bytes(UUID.randomUUID()));
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(other));

        var result=receipts.existingBound(key,REQUEST);

        assertThat(result.status()).isEqualTo(SourceEvidenceIntakeReplay.Status.CONFLICT);
        assertThat(result.originalResponseBytes()).isNull();
    }

    @Test void lockedLookupUsesUniqueKeyForUpdate() {
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of());

        assertThat(receipts.existingBoundLocked(key,REQUEST).status()).isEqualTo(SourceEvidenceIntakeReplay.Status.NOT_FOUND);

        assertKeyQuery(true);
    }

    @Test void nonOpenReceiptCannotReplayAsOpen() {
        var row=row();row.put("operation_kind","PAGE");
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(row));

        var result=receipts.existingBound(key,REQUEST);

        assertThat(result.status()).isEqualTo(SourceEvidenceIntakeReplay.Status.INTEGRITY_FAILURE);
        assertThat(result.originalResponseBytes()).isNull();
    }

    @Test void lockedLookupRequiresCallerTransactionAndRollbackReadRequiresNew() throws NoSuchMethodException {
        var source=new AnnotationTransactionAttributeSource();
        var locked=source.getTransactionAttribute(SourceEvidenceIntakeReceipts.class.getMethod("existingBoundLocked",
            SourceEvidenceIntakeReceipts.BoundKey.class,byte[].class),SourceEvidenceIntakeReceipts.class);
        var recheck=source.getTransactionAttribute(SourceEvidenceIntakeReceipts.class.getMethod("recheckBoundAfterRollback",
            SourceEvidenceIntakeReceipts.BoundKey.class,byte[].class),SourceEvidenceIntakeReceipts.class);

        assertThat(locked.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_MANDATORY);
        assertThat(recheck.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        assertThat(recheck.isReadOnly()).isTrue();
        assertThat(recheck.getIsolationLevel()).isEqualTo(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    private void assertKeyQuery(boolean locked) {
        var sql=ArgumentCaptor.forClass(String.class);var args=ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForList(sql.capture(),args.capture());
        assertThat(sql.getValue()).contains("WHERE principal_id=? AND route_scope=? AND original_key=?");
        assertThat(sql.getValue().trim().endsWith("FOR UPDATE")).isEqualTo(locked);
        Object[] values=args.getValue();assertThat(values).hasSize(3);
        assertThat((byte[])values[0]).containsExactly(Ids.bytes(key.principalId()));
        assertThat(values[1]).isEqualTo(E2aProtocol.ROUTE_SCOPE);
        assertThat((byte[])values[2]).containsExactly(Ids.bytes(key.originalKey()));
    }

    private Map<String,Object> row() {
        var row=new HashMap<String,Object>();
        row.put("receipt_version",1);row.put("canonical_request_bytes",REQUEST);
        row.put("request_sha256",E2aProtocol.intakeSha256(REQUEST));
        row.put("original_response_bytes",RESPONSE);row.put("original_response_sha256",ImportFiles.sha256(RESPONSE));
        row.put("warehouse_id",Ids.bytes(key.warehouseId()));row.put("submission_id",Ids.bytes(key.submissionId()));
        row.put("work_round",2L);row.put("creator_device_id",Ids.bytes(key.creatorDeviceId()));
        row.put("temporary_authorization_id",Ids.bytes(key.authorizationId()));
        row.put("original_operator_id",Ids.bytes(key.principalId()));
        row.put("operation_kind","OPEN");row.put("receipt_status","RECEIVED");
        return row;
    }
}
