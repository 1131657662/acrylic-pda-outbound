package com.acrylic.outbound.recovery;

import com.acrylic.outbound.auth.IdentityStore;
import com.acrylic.outbound.devices.DeviceStore;
import com.acrylic.outbound.pda.PdaSessions;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Independently pinned audit of an actual receiving login; never manufactures a PDA identity. */
public final class RecoveryRecipientSession {
    private final Request request;
    private final RecoveryTemporaryQualification qualification;
    private final byte[] signed;
    private final JsonNode facts;
    private final boolean retainedOnly;
    private RecoveryRecipientSession(Request request,RecoveryTemporaryQualification qualification,boolean retainedOnly) {
        this.request=Objects.requireNonNull(request); this.qualification=Objects.requireNonNull(qualification); this.retainedOnly=retainedOnly;
        qualification.materials();
        require(request.authority().sha256().equals(text(qualification.recipient().facts(),"authoritySha256")),"RECOVERY_SESSION_AUTHORITY");
        signed=read(); facts=RecoveryOperationsSignature.verify(signed,request.authority()); RecoveryRecipientSessionFormat.verify(facts,qualification);
    }
    public static RecoveryRecipientSession verify(Request request,RecoveryTemporaryQualification qualification) {
        require(!TransactionSynchronizationManager.isActualTransactionActive(),"RECOVERY_SESSION_PREFLIGHT_OUTSIDE_TRANSACTION");
        return new RecoveryRecipientSession(request,qualification,false);
    }
    public static RecoveryRecipientSession reopen(Request request,RecoveryTemporaryQualification qualification) {
        require(!TransactionSynchronizationManager.isActualTransactionActive(),"RECOVERY_SESSION_PREFLIGHT_OUTSIDE_TRANSACTION");
        return new RecoveryRecipientSession(request,qualification,true);
    }
    public RecoveryTemporaryQualification qualification() { return qualification; }
    public JsonNode facts() { return facts.deepCopy(); }
    public String sha256() { return request.independentSha256(); }
    byte[] signedBytes() { return signed.clone(); }
    boolean retainedOnly() { return retainedOnly; }
    public RecoveryRecipientAdmission.IdentityLocks lockIdentity(DataSource database) {
        materials(); var identity=qualification.recipient().lockReceivingIdentity(database); requirePrincipal(database);
        TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
            @Override public void beforeCommit(boolean readOnly) { materials(); requirePrincipal(database); }
        }); return identity;
    }
    public PdaSessions.Principal requirePrincipal(DataSource database) {
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        require(authentication!=null && authentication.getPrincipal() instanceof PdaSessions.Principal,"RECOVERY_SESSION_PDA_REQUIRED");
        var jdbc=new JdbcTemplate(database); var principal=new PdaSessions(jdbc,new IdentityStore(jdbc),new DeviceStore(jdbc))
            .recheck((PdaSessions.Principal)authentication.getPrincipal());
        var recipient=facts.path("recipient");
        require(principal.sessionId().toString().equals(text(facts.path("login"),"sessionId"))
            && principal.userId().toString().equals(text(recipient,"userId")) && principal.deviceId().toString().equals(text(recipient,"deviceId"))
            && principal.warehouseId().toString().equals(text(facts,"warehouseId"))
            && principal.username().equals(text(facts.path("login"),"username"))
            && principal.userAuthVersion().equals(text(recipient,"userAuthVersion"))
            && principal.deviceVersion().equals(text(recipient,"deviceVersion")),"RECOVERY_SESSION_ACTOR"); return principal;
    }
    RecoveryRecipientSessionEffects effects(DataSource database) { return new RecoveryRecipientSessionEffects(this,new JdbcTemplate(database)); }
    void materials() {
        qualification.materials(); request.authority().requireUnchanged();
        require(java.util.Arrays.equals(signed,read()),"RECOVERY_SESSION_MATERIAL_CHANGED"); RecoveryRecipientSessionFormat.verify(facts,qualification);
    }
    private byte[] read() {
        require(request.independentSha256()!=null && request.independentSha256().matches("[a-f0-9]{64}")
            && !ZERO_HASH.equals(request.independentSha256()),"RECOVERY_SESSION_PIN_REQUIRED");
        byte[] bytes=request.authority().material(request.file());
        require(bytes.length<=49152 && RecoveryControlFormat.sha256(bytes).equals(request.independentSha256()),"RECOVERY_SESSION_PIN_MISMATCH"); return bytes;
    }
    public record Request(RecoveryOperationsAuthority authority,Path file,String independentSha256) {
        public Request { Objects.requireNonNull(authority); Objects.requireNonNull(file); }
    }
}
