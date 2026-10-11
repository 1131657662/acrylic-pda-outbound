package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** A new scoped preparation qualification. It never becomes a general TEMP_WORK credential. */
public final class RecoveryTemporaryQualification {
    private final Request request;
    private final RecoveryRecipientAdmission recipient;
    private final byte[] signed;
    private final JsonNode facts;
    private boolean retainedOnly;
    private RecoveryTemporaryQualification(Request request,RecoveryRecipientAdmission recipient) {
        this.request=request; this.recipient=recipient; signed=read();
        facts=RecoveryOperationsSignature.verify(signed,request.authority());
        RecoveryTemporaryQualificationFormat.verify(facts,recipient);
    }
    public static RecoveryTemporaryQualification verify(Request request,RecoveryRecipientAdmission recipient) {
        require(!TransactionSynchronizationManager.isActualTransactionActive()
            && !TransactionSynchronizationManager.isSynchronizationActive(),"RECOVERY_QUALIFICATION_PREFLIGHT_OUTSIDE_TRANSACTION");
        Objects.requireNonNull(request); Objects.requireNonNull(recipient);
        request.authority().closed().read(); recipient.materials();
        require(request.authority().sha256().equals(text(recipient.facts(),"authoritySha256")),"RECOVERY_QUALIFICATION_AUTHORITY");
        return new RecoveryTemporaryQualification(request,recipient);
    }
    public static RecoveryTemporaryQualification reopen(Request request,RecoveryRecipientAdmission recipient) {
        var value=verify(request,recipient); value.retainedOnly=true; return value;
    }
    public RecoveryRecipientAdmission recipient() { return recipient; }
    public JsonNode facts() { return facts.deepCopy(); }
    public String sha256() { return request.independentSha256(); }
    public byte[] signedBytes() { return signed.clone(); }
    public RecoveryRecipientAdmission.IdentityLocks lockPreparationIdentity(DataSource database) {
        return recipient.lockPreparationIdentity(database);
    }
    public void requirePreparationUse(Context context) {
        RecoveryTemporaryQualificationFormat.bindArchive(facts,context.projection().archive());
        var projection=new RecoveryTemporaryQualificationProjection(context.database(),this);
        projection.requireBaseline(); projection.issue(true); projection.requireUnexpired();
    }
    public RecoveryRecipientAdmission.Effects preparationEffects(DataSource database,RecoveryReceiptKeys keys) {
        var projection=new RecoveryTemporaryQualificationProjection(database,this);
        projection.requireBaseline(); projection.issue(true);
        var effects=recipient.readEffects(database);
        require(effects!=null,"RECOVERY_QUALIFICATION_ENROLLMENT_REQUIRED");
        return effects.withPreparation(new RecoveryPreparedTemporaryEffects(this,keys,database));
    }
    public void verifyPreparation(RecoveryRecipientAdmission.IdentityLocks identity,Context context) {
        requirePreparationUse(context);
        recipient.project(identity,context.projection(),new RecoveryPreparedTemporaryEffects(this,
            context.projection().prepared().request().targetKeys(),context.database()));
    }
    public JsonNode preparationDescriptor() {
        var result=com.acrylic.outbound.submissions.SubmissionJson.object().put("purpose","RECOVERED_TEMP_HANDOVER_QUALIFICATION")
            .put("qualificationSha256",sha256());
        for(String field:java.util.List.of("qualificationId","caseId","scopeId","warehouseId","recipient","source","control","baseline","expiresAt","capabilities"))
            result.set(field,facts.path(field));
        return result;
    }
    public Result issue(RecoveryRecipientAdmission.IdentityLocks identity,Context context) {
        require(recipient.readEffects(context.database())!=null,"RECOVERY_QUALIFICATION_ENROLLMENT_REQUIRED");
        recipient.project(identity,context.projection()); materials();
        RecoveryTemporaryQualificationFormat.bindArchive(facts,context.projection().archive());
        var projection=new RecoveryTemporaryQualificationProjection(context.database(),this);
        projection.requireBaseline(); projection.issue(retainedOnly); return new Result(facts,sha256(),signed);
    }
    void materials() {
        request.authority().requireUnchanged(); recipient.materials();
        require(java.util.Arrays.equals(signed,read()),"RECOVERY_QUALIFICATION_MATERIAL_CHANGED");
        RecoveryTemporaryQualificationFormat.verify(facts,recipient);
    }
    private byte[] read() {
        require(request.independentSha256()!=null && request.independentSha256().matches("[a-f0-9]{64}")
            && !ZERO_HASH.equals(request.independentSha256()),"RECOVERY_QUALIFICATION_PIN_REQUIRED");
        byte[] bytes=request.authority().material(request.file());
        require(RecoveryControlFormat.sha256(bytes).equals(request.independentSha256()),"RECOVERY_QUALIFICATION_PIN_MISMATCH"); return bytes;
    }
    public record Request(RecoveryOperationsAuthority authority,Path file,String independentSha256) {
        public Request { Objects.requireNonNull(authority); Objects.requireNonNull(file); }
    }
    public record Context(DataSource database,RecoveryRecipientAdmission.ProjectionContext projection) {
        public Context { Objects.requireNonNull(database); Objects.requireNonNull(projection); }
    }
    public record Result(JsonNode facts,String signedSha256,byte[] signedBytes) {
        public Result { facts=facts.deepCopy(); signedBytes=signedBytes.clone(); }
        @Override public JsonNode facts() { return facts.deepCopy(); }
        @Override public byte[] signedBytes() { return signedBytes.clone(); }
        public boolean ordinaryWritesAllowed() { return false; }
        public boolean localDeletionAllowed() { return false; }
        public boolean firstRoundAllowed() { return false; }
        public boolean mayResumeScanning() { return false; }
    }
}
