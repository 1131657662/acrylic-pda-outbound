package com.acrylic.outbound.temporary.evidence;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SourceEvidenceIntakeRecoveryTest {
    private static final byte[] REQUEST="{\"operation\":\"OPEN\"}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] RESPONSE="{\"status\":\"RECEIVED\"}".getBytes(StandardCharsets.UTF_8);
    private final SourceEvidenceIntakeReceipts receipts=mock(SourceEvidenceIntakeReceipts.class);
    private final SourceEvidenceIntakeRecovery recovery=new SourceEvidenceIntakeRecovery(receipts);
    private final SourceEvidenceIntakeReceipts.OriginalKey key=new SourceEvidenceIntakeReceipts.OriginalKey(
        UUID.randomUUID(),UUID.randomUUID());
    private final SourceEvidenceIntakeReceipts.BoundKey bound=new SourceEvidenceIntakeReceipts.BoundKey(
        key.principalId(),key.originalKey(),UUID.randomUUID(),UUID.randomUUID(),2,UUID.randomUUID(),UUID.randomUUID());

    @Test void exactNaturalKeyCollisionReplaysStoredResponse() {
        var replay=decision(SourceEvidenceIntakeReplay.Status.REPLAY,RESPONSE);
        when(receipts.recheckAfterRollback(key,REQUEST)).thenReturn(replay);

        var result=recovery.resolveAfterRollback(duplicate("uq_evidence_intake_original","23000",1062),key,REQUEST);

        assertThat(result.status()).isEqualTo(SourceEvidenceIntakeReplay.Status.REPLAY);
        assertThat(result.originalResponseBytes()).isEqualTo(RESPONSE);
        verify(receipts).recheckAfterRollback(key,REQUEST);
    }

    @Test void sameKeyDifferentRequestRemainsConflict() {
        when(receipts.recheckAfterRollback(key,REQUEST))
            .thenReturn(decision(SourceEvidenceIntakeReplay.Status.CONFLICT,null));

        var result=recovery.resolveAfterRollback(duplicate("source_evidence_intake_receipt.uq_evidence_intake_original",
            "23000",1062),key,REQUEST);

        assertThat(result.status()).isEqualTo(SourceEvidenceIntakeReplay.Status.CONFLICT);
        assertThat(result.originalResponseBytes()).isNull();
    }

    @Test void missingRowAfterRollbackReturnsUnknownNotSuccess() {
        when(receipts.recheckAfterRollback(key,REQUEST))
            .thenReturn(decision(SourceEvidenceIntakeReplay.Status.NOT_FOUND,null));

        var result=recovery.resolveAfterRollback(duplicate("uq_evidence_intake_original","23000",1062),key,REQUEST);

        assertThat(result.status()).isEqualTo(SourceEvidenceIntakeReplay.Status.UNKNOWN);
        assertThat(result.originalResponseBytes()).isNull();
    }

    @Test void corruptedStoredReceiptFailsClosed() {
        when(receipts.recheckAfterRollback(key,REQUEST))
            .thenReturn(decision(SourceEvidenceIntakeReplay.Status.INTEGRITY_FAILURE,null));

        var result=recovery.resolveAfterRollback(duplicate("uq_evidence_intake_original","23000",1062),key,REQUEST);

        assertThat(result.status()).isEqualTo(SourceEvidenceIntakeReplay.Status.INTEGRITY_FAILURE);
    }

    @Test void unrelatedDatabaseErrorsNeverStartRecovery() {
        for(var failure:new DuplicateKeyException[] {
            duplicate("uq_other_original","23000",1062),
            duplicate("uq_evidence_intake_original","23000",1452),
            duplicate("uq_evidence_intake_original","HY000",1062),
            new DuplicateKeyException("insert failed",new SQLException(
                "Duplicate entry 'for key 'uq_evidence_intake_original'' for key 'uq_other_original'",
                "23000",1062)),
            new DuplicateKeyException("duplicate without JDBC identity")
        }) {
            assertThatThrownBy(()->recovery.resolveAfterRollback(failure,key,REQUEST)).isSameAs(failure);
        }
        verifyNoInteractions(receipts);
    }

    @Test void liveWriteTransactionCannotBeMistakenForRolledBack() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(()->recovery.resolveAfterRollback(
                duplicate("uq_evidence_intake_original","23000",1062),key,REQUEST))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("INTAKE_WRITE_TRANSACTION_NOT_ROLLED_BACK");
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(receipts);
    }

    @Test void recheckDatabaseFailurePropagates() {
        var failure=new IllegalStateException("read failed");
        when(receipts.recheckAfterRollback(key,REQUEST)).thenThrow(failure);

        assertThatThrownBy(()->recovery.resolveAfterRollback(
            duplicate("uq_evidence_intake_original","23000",1062),key,REQUEST)).isSameAs(failure);
    }

    @Test void boundRecoveryUsesIndependentBoundedLookup() {
        when(receipts.recheckBoundAfterRollback(bound,REQUEST))
            .thenReturn(decision(SourceEvidenceIntakeReplay.Status.REPLAY,RESPONSE));

        var result=recovery.resolveBoundAfterRollback(duplicate("uq_evidence_intake_original","23000",1062),bound,REQUEST);

        assertThat(result.status()).isEqualTo(SourceEvidenceIntakeReplay.Status.REPLAY);
        assertThat(result.originalResponseBytes()).containsExactly(RESPONSE);
        verify(receipts).recheckBoundAfterRollback(bound,REQUEST);
        verify(receipts,never()).recheckAfterRollback(any(),any());
    }

    @Test void boundRecoveryDoesNotMaskUnrelatedDuplicate() {
        var failure=duplicate("uq_other_original","23000",1062);

        assertThatThrownBy(() -> recovery.resolveBoundAfterRollback(failure,bound,REQUEST)).isSameAs(failure);
        verifyNoInteractions(receipts);
    }

    private SourceEvidenceIntakeReplay.Decision decision(SourceEvidenceIntakeReplay.Status status,byte[] response) {
        return new SourceEvidenceIntakeReplay.Decision(status,response,
            SourceEvidenceIntakeReplay.TrustStatus.NOT_EVALUATED);
    }

    private DuplicateKeyException duplicate(String constraint,String state,int code) {
        var sql=new SQLException("Duplicate entry 'value' for key '"+constraint+"'",state,code);
        return new DuplicateKeyException("insert failed",sql);
    }
}
