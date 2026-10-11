package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Pinned, scope-specific enrollment evidence. Never a public ordinary-write or qualification permit. */
public final class RecoveryRecipientAdmission {
    private final Request request;
    private final RecoveryScopeReleaseStore store;
    private final byte[] signed;
    private final JsonNode body;
    private RecoveryRecipientAdmission(Request request,RecoveryScopeReleaseStore store) {
        this.request=request; this.store=store; signed=read(request); body=RecoveryOperationsSignature.verify(signed,request.authority());
        RecoveryRecipientAdmissionFormat.verify(body,request.authority(),store);
    }
    public static RecoveryRecipientAdmission verify(Request request,RecoveryScopeReleaseStore store) {
        require(!TransactionSynchronizationManager.isActualTransactionActive()
            && !TransactionSynchronizationManager.isSynchronizationActive(),"RECOVERY_RECIPIENT_PREFLIGHT_OUTSIDE_TRANSACTION");
        Objects.requireNonNull(request); Objects.requireNonNull(store);
        request.authority().closed().read();
        require(store.inspect(),"RECOVERY_RELEASE_COMMIT_REQUIRED"); return new RecoveryRecipientAdmission(request,store);
    }
    /** Reload original pinned statements after restart; missing DB enrollment is refused, never recreated. */
    public static RecoveryRecipientAdmission reopen(Request request,RetainedRelease release) {
        var approval=RecoveryOperationsApproval.retainedEvidence(release.approval());
        var store=new RecoveryScopeReleaseStore(approval,release.root(),release.independentLockIdentity());
        var value=verify(request,store); value.requireRetained=true; return value;
    }
    private boolean requireRetained;
    public IdentityLocks lockIdentity(DataSource database) {
        transaction(database); materials();
        RecoveryClosedOperationContext.requireCurrent(database,request.authority().closed());
        new RecoveryClosedWriteProjection(new JdbcTemplate(database)).requireMatches(request.authority().closed().control());
        var identity=new RecoveryRecipientIdentity(new JdbcTemplate(database),body,store); identity.lock();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void beforeCommit(boolean readOnly) { identity.requireCurrent(); materials(); }
        });
        return new IdentityLocks(database,identity,this,TransactionSynchronizationManager.getResource(database));
    }
    public Effects project(IdentityLocks locks,ProjectionContext context) {
        require(locks!=null && locks.admission==this,"RECOVERY_RECIPIENT_IDENTITY_REQUIRED"); transaction(locks.database);
        require(locks.transaction==TransactionSynchronizationManager.getResource(locks.database),"RECOVERY_RECIPIENT_IDENTITY_TRANSACTION");
        locks.identity.requireCurrent(); materials();
        var projection=new RecoveryRecipientProjection(locks.database,this); projection.requireRelease();
        if(requireRetained) require(projection.effects()!=null,"RECOVERY_RECIPIENT_RETAINED_REQUIRED");
        else projection.insertIfAbsent();
        var effects=projection.effects();
        var inventory=new RecoveryReleaseInventoryCapture(locks.database).capture(context.archive(),context.faultReceipt(),effects);
        var physical=RecoveryReleasePhysicalReview.inspect(locks.database,inventory,context.prepared(),effects);
        new RecoveryScopeReleaseProjection(locks.database,store,true).apply(new RecoveryOperationsApproval.Context(physical,inventory),request.authority().closed());
        return effects;
    }
    /** Reading a stored, exact signed effect is allowed; missing audit never licenses a device insertion. */
    public Effects readEffects(DataSource database) {
        transaction(database); materials(); var projection=new RecoveryRecipientProjection(database,this);
        projection.requireRelease(); return projection.effects();
    }
    void materials() {
        request.authority().requireUnchanged();
        require(java.util.Arrays.equals(signed,read(request)),"RECOVERY_RECIPIENT_MATERIAL_CHANGED");
        RecoveryRecipientAdmissionFormat.verify(body,request.authority(),store);
        require(store.inspect(),"RECOVERY_RELEASE_COMMIT_REQUIRED");
    }
    private static byte[] read(Request request) {
        require(request.independentSha256()!=null && request.independentSha256().matches("[a-f0-9]{64}")
            && !ZERO_HASH.equals(request.independentSha256()),"RECOVERY_RECIPIENT_PIN_REQUIRED");
        byte[] bytes=request.authority().material(request.file());
        require(RecoveryControlFormat.sha256(bytes).equals(request.independentSha256()),"RECOVERY_RECIPIENT_PIN_MISMATCH"); return bytes;
    }
    static void transaction(DataSource database) {
        require(database!=null && TransactionSynchronizationManager.isActualTransactionActive()
            && TransactionSynchronizationManager.isSynchronizationActive() && TransactionSynchronizationManager.hasResource(database),
            "RECOVERY_RECIPIENT_TRANSACTION_REQUIRED");
    }
    JsonNode body() { return body.deepCopy(); }
    byte[] signed() { return signed.clone(); }
    RecoveryScopeReleaseStore store() { return store; }
    public JsonNode facts() { return body.deepCopy(); }
    public String sha256() { return request.independentSha256(); }
    public boolean ordinaryWritesAllowed() { return false; }
    public boolean localDeletionAllowed() { return false; }
    public record Request(RecoveryOperationsAuthority authority,Path file,String independentSha256) {
        public Request { Objects.requireNonNull(authority); Objects.requireNonNull(file); }
    }
    public record RetainedRelease(RecoveryOperationsApproval.Request approval,Path root,String independentLockIdentity) {
        public RetainedRelease { Objects.requireNonNull(approval); Objects.requireNonNull(root); Objects.requireNonNull(independentLockIdentity); }
    }
    public record ProjectionContext(RecoveryRecoveredTemporaryArchive archive,JsonNode faultReceipt,RecoveryReleaseReconciliation.Prepared prepared) {
        public ProjectionContext { Objects.requireNonNull(archive); Objects.requireNonNull(prepared); faultReceipt=faultReceipt.deepCopy(); }
        @Override public JsonNode faultReceipt() { return faultReceipt.deepCopy(); }
    }
    public static final class IdentityLocks {
        private final DataSource database;
        private final RecoveryRecipientIdentity identity;
        private final RecoveryRecipientAdmission admission;
        private final Object transaction;
        private IdentityLocks(DataSource database,RecoveryRecipientIdentity identity,RecoveryRecipientAdmission admission,Object transaction) {
            this.database=database; this.identity=identity; this.admission=admission; this.transaction=transaction;
        }
    }
    /** Only a verified projection can create this; it removes one fully checked device row from a copy. */
    public static final class Effects {
        private final RecoveryRecipientProjection projection;
        Effects(RecoveryRecipientProjection projection) { this.projection=projection; }
        public JsonNode before(JsonNode tables) { return projection.before(tables); }
    }
}
