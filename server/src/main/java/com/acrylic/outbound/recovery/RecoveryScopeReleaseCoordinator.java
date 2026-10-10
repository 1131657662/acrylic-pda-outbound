package com.acrylic.outbound.recovery;

import com.acrylic.outbound.handover.HandoverRecoveredOperationsReview;
import com.acrylic.outbound.handover.HandoverRecoveredPhysicalReview;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.require;

/** PREPARE -> closed DB commit -> committed readback -> COMMIT. No NORMAL or qualification side effect. */
public final class RecoveryScopeReleaseCoordinator {
    private final HandoverRecoveredOperationsReview review;
    public RecoveryScopeReleaseCoordinator(HandoverRecoveredOperationsReview review) { this.review=Objects.requireNonNull(review); }
    public Result release(RecoveryScopeReleaseStore store,HandoverRecoveredPhysicalReview.Request request) {
        outside(); store.prepare();
        review.project(store,request,false);
        review.project(store,request,true);
        store.approval().closed().read(); store.approval().requireMaterials();
        store.complete(store.intent().commitBytes());
        return verify(store,request);
    }
    public Result verify(RecoveryScopeReleaseStore store,HandoverRecoveredPhysicalReview.Request request) {
        outside(); store.approval().closed().read();
        require(store.inspect(),"RECOVERY_RELEASE_COMMIT_REQUIRED"); review.project(store,request,true);
        require(store.inspect(),"RECOVERY_RELEASE_COMMIT_REQUIRED"); store.approval().closed().read();
        return new Result(store.intent().commit());
    }
    private static void outside() {
        require(!TransactionSynchronizationManager.isActualTransactionActive()
            && !TransactionSynchronizationManager.isSynchronizationActive(),"RECOVERY_RELEASE_OUTSIDE_TRANSACTION_REQUIRED");
    }
    /** Evidence only; later qualification issuance must recheck both sides under its own transaction. */
    public static final class Result {
        private final JsonNode facts;
        private Result(JsonNode facts) { this.facts=facts.deepCopy(); }
        public JsonNode facts() { return facts.deepCopy(); }
        public boolean ordinaryWritesAllowed() { return false; }
        public boolean localDeletionAllowed() { return false; }
    }
}
