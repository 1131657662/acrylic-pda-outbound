package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
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

class SourceEvidenceIntakePageReceiptsTest {
    private static final byte[] REQUEST="{\"operation\":\"PAGE\"}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] RESPONSE="{\"status\":\"PARTIAL\"}".getBytes(StandardCharsets.UTF_8);
    private final UUID operator=UUID.randomUUID(), pageKey=UUID.randomUUID(), warehouse=UUID.randomUUID();
    private final UUID submission=UUID.randomUUID(), device=UUID.randomUUID(), authorization=UUID.randomUUID();
    private final UUID objectId=UUID.randomUUID();
    private final SourceEvidenceIntakeReceipts.BoundKey key=new SourceEvidenceIntakeReceipts.BoundKey(
        operator,pageKey,warehouse,submission,3,device,authorization);
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final SourceEvidenceIntakePageReceipts receipts=new SourceEvidenceIntakePageReceipts(jdbc);

    @Test void sameKeyAndCanonicalRequestReplaysOriginalBytes() {
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(row()));

        var result=receipts.existing(key,REQUEST);

        assertThat(result.status()).isEqualTo(SourceEvidenceIntakeReplay.Status.REPLAY);
        assertThat(result.originalResponseBytes()).containsExactly(RESPONSE);
    }

    @Test void differentSourceCannotReplayPage() {
        var row=row();row.put("submission_id",Ids.bytes(UUID.randomUUID()));
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(row));

        assertThat(receipts.existing(key,REQUEST).status()).isEqualTo(SourceEvidenceIntakeReplay.Status.CONFLICT);
    }

    @Test void missingObjectReferenceIsIntegrityFailure() {
        var row=row();row.put("object_id",null);
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(row));

        assertThat(receipts.existing(key,REQUEST).status()).isEqualTo(SourceEvidenceIntakeReplay.Status.INTEGRITY_FAILURE);
    }

    @Test void lockedLookupUsesOriginalKeyAndForUpdate() {
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of());

        assertThat(receipts.existingLocked(key,REQUEST).status()).isEqualTo(SourceEvidenceIntakeReplay.Status.NOT_FOUND);

        var sql=ArgumentCaptor.forClass(String.class);var args=ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForList(sql.capture(),args.capture());
        assertThat(sql.getValue().trim()).endsWith("FOR UPDATE");
        assertThat((byte[])args.getValue()[2]).containsExactly(Ids.bytes(pageKey));
    }

    @Test void insertsOnlyPageObjectReceiptWithImmutableResponse() {
        when(jdbc.update(anyString(),any(Object[].class))).thenReturn(1);
        var context=new SourceEvidenceIntakeReceipts.OpenInsert(operator,pageKey,warehouse,UUID.randomUUID(),submission,3,
            device,UUID.randomUUID(),authorization,operator,4,UUID.randomUUID(),"8","2",REQUEST,E2aProtocol.intakeSha256(REQUEST));
        var receipt=new SourceEvidenceIntakeReceiptCodec.Receipt(UUID.randomUUID(),RESPONSE,ImportFiles.sha256(RESPONSE),
            "key","token",Instant.EPOCH);

        receipts.insert(new SourceEvidenceIntakePageReceipts.PageInsert(context,objectId),receipt);

        var sql=ArgumentCaptor.forClass(String.class);var args=ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(),args.capture());
        assertThat(sql.getValue()).contains("INSERT INTO source_evidence_intake_receipt")
            .contains("object_id,evidence_set_id,evidence_verification_id").contains("NULL,NULL");
        assertThat(sql.getValue().chars().filter(character->character=='?').count()).isEqualTo(25);
        assertThat(args.getValue()).hasSize(25);
        assertThat((byte[])args.getValue()[23]).containsExactly(Ids.bytes(objectId));
        assertThat(args.getValue()[15]).isEqualTo("PAGE");
        assertThat(args.getValue()[16]).isEqualTo("PARTIAL");
    }

    @Test void rollbackRecheckRequiresIndependentReadTransaction() throws Exception {
        var method=SourceEvidenceIntakePageReceipts.class.getMethod("recheckAfterRollback",
            SourceEvidenceIntakeReceipts.BoundKey.class,byte[].class);
        var transaction=new AnnotationTransactionAttributeSource().getTransactionAttribute(method,SourceEvidenceIntakePageReceipts.class);

        assertThat(transaction.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        assertThat(transaction.isReadOnly()).isTrue();
    }

    private HashMap<String,Object> row() {
        var row=new HashMap<String,Object>();
        row.put("receipt_version",1);row.put("canonical_request_bytes",REQUEST);
        row.put("request_sha256",E2aProtocol.intakeSha256(REQUEST));
        row.put("original_response_bytes",RESPONSE);row.put("original_response_sha256",ImportFiles.sha256(RESPONSE));
        row.put("warehouse_id",Ids.bytes(warehouse));row.put("submission_id",Ids.bytes(submission));row.put("work_round",3L);
        row.put("creator_device_id",Ids.bytes(device));row.put("temporary_authorization_id",Ids.bytes(authorization));
        row.put("original_operator_id",Ids.bytes(operator));row.put("operation_kind","PAGE");row.put("receipt_status","PARTIAL");
        row.put("object_id",Ids.bytes(objectId));
        return row;
    }
}
