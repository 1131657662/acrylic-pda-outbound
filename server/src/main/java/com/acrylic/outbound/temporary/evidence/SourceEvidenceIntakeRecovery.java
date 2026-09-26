package com.acrylic.outbound.temporary.evidence;

import java.sql.SQLException;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeReplay.*;

@Service
public class SourceEvidenceIntakeRecovery {
    private static final Pattern ORIGINAL_KEY=Pattern.compile(
        "(?i)\\bfor key ['\"`](?:[a-z0-9_]+\\.)?uq_evidence_intake_original['\"`]\\s*$");
    private final SourceEvidenceIntakeReceipts receipts;

    public SourceEvidenceIntakeRecovery(SourceEvidenceIntakeReceipts receipts) { this.receipts=receipts; }

    public Decision resolveAfterRollback(DuplicateKeyException collision,
                                         SourceEvidenceIntakeReceipts.OriginalKey key,byte[] requestBytes) {
        if(!originalKeyCollision(collision)) throw collision;
        if(TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("INTAKE_WRITE_TRANSACTION_NOT_ROLLED_BACK");
        var result=receipts.recheckAfterRollback(key,requestBytes);
        if(result.status()!=Status.NOT_FOUND) return result;
        return new Decision(Status.UNKNOWN,null,TrustStatus.NOT_EVALUATED);
    }

    public Decision resolveBoundAfterRollback(DuplicateKeyException collision,
                                              SourceEvidenceIntakeReceipts.BoundKey key,byte[] requestBytes) {
        if(!originalKeyCollision(collision))throw collision;
        if(TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("INTAKE_WRITE_TRANSACTION_NOT_ROLLED_BACK");
        var result=receipts.recheckBoundAfterRollback(key,requestBytes);
        if(result.status()!=Status.NOT_FOUND)return result;
        return new Decision(Status.UNKNOWN,null,TrustStatus.NOT_EVALUATED);
    }

    boolean originalKeyCollision(DuplicateKeyException collision) {
        if(collision==null) throw new IllegalArgumentException("INTAKE_COLLISION_REQUIRED");
        for(Throwable cause=collision.getCause();cause!=null;cause=cause.getCause()) {
            if(cause instanceof SQLException sql && sql.getErrorCode()==1062
                && "23000".equals(sql.getSQLState()) && sql.getMessage()!=null
                && ORIGINAL_KEY.matcher(sql.getMessage()).find()) return true;
        }
        return false;
    }
}
