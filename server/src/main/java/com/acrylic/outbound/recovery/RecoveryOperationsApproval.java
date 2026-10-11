package com.acrylic.outbound.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Verified external operations decision; it is not a release permit or ordinary-write admission. */
public final class RecoveryOperationsApproval {
    private final Request request;
    private final JsonNode approval;
    private final String sha256;
    private RecoveryOperationsApproval(Request request,JsonNode approval) {
        this.request=request; this.approval=approval.deepCopy(); this.sha256=request.independentSha256();
    }
    public static RecoveryOperationsApproval verify(Request request,Context context) {
        require(!TransactionSynchronizationManager.isActualTransactionActive(),"RECOVERY_OPERATIONS_PREFLIGHT_OUTSIDE_TRANSACTION");
        Objects.requireNonNull(request); Objects.requireNonNull(context);
        request.authority().closed().read(); request.authority().requireUnchanged();
        var value=new RecoveryOperationsApproval(request,read(request)); value.requireCurrent(context); return value;
    }
    /** Bootstrap only for retained recipient replay. The caller exposes no release creator; DB context is revalidated before commit. */
    static RecoveryOperationsApproval retainedEvidence(Request request) {
        require(!TransactionSynchronizationManager.isActualTransactionActive(),"RECOVERY_OPERATIONS_PREFLIGHT_OUTSIDE_TRANSACTION");
        request.authority().closed().read(); request.authority().requireUnchanged();
        var value=new RecoveryOperationsApproval(request,read(request)); var facts=value.approval;
        fields(facts,"formatVersion purpose commandId authoritySha256 operatorId requestedAt recoverySessionId control caseId supervisorId physicalReview");
        version(facts); uuid(facts,"commandId"); uuid(facts,"caseId"); uuid(facts,"supervisorId");
        require(text(facts,"purpose").equals("RECOVERED_TEMP_NEW_QUALIFICATION")
            && text(facts,"authoritySha256").equals(request.authority().sha256())
            && uuid(facts,"operatorId").equals(uuid(request.authority().identity(),"operatorId")),"RECOVERY_OPERATIONS_AUTHORITY_BINDING");
        var closed=request.authority().closed(); var control=facts.path("control");
        require(uuid(facts,"recoverySessionId").equals(closed.control().recoverySessionId())
            && uuid(control,"installationId").equals(closed.control().installationId())
            && uuid(control,"epochId").equals(closed.control().epochId())
            && text(control,"controlRevision").equals(closed.control().revision())
            && text(control,"controlRecordSha256").equals(closed.control().recordSha256())
            && !instant(facts,"requestedAt").isAfter(Instant.now()),"RECOVERY_OPERATIONS_CONTROL_BINDING");
        return value;
    }
    /** Rebind to freshly inspected DB facts, and retain file checks until the owning transaction commits. */
    public void revalidate(Context current,RecoveryClosedAuthority closed) {
        require(TransactionSynchronizationManager.isActualTransactionActive()
            && TransactionSynchronizationManager.isSynchronizationActive(),"RECOVERY_OPERATIONS_TRANSACTION_REQUIRED");
        var supplied=request.authority().closed();
        require(supplied.authorityRoot().equals(closed.authorityRoot()) && supplied.control().equals(closed.control())
            && supplied.sourceIdentity().equals(closed.sourceIdentity()) && supplied.sourcePinSha256().equals(closed.sourcePinSha256()),
            "RECOVERY_OPERATIONS_CLOSED_BINDING");
        requireCurrent(current);
        TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
            @Override public void beforeCommit(boolean readOnly) { requireCurrent(current); }
        });
    }
    /** Called again inside the closed DB transaction, whose authority already owns the journal token. */
    void requireCurrent(Context context) {
        request.authority().requireUnchanged(); require(approval.equals(read(request)),"RECOVERY_OPERATIONS_APPROVAL_CHANGED");
        fields(approval,"formatVersion purpose commandId authoritySha256 operatorId requestedAt recoverySessionId control caseId supervisorId physicalReview");
        version(approval); uuid(approval,"commandId");
        require(text(approval,"purpose").equals("RECOVERED_TEMP_NEW_QUALIFICATION"),"RECOVERY_OPERATIONS_PURPOSE");
        require(text(approval,"authoritySha256").equals(request.authority().sha256()),"RECOVERY_OPERATIONS_AUTHORITY_BINDING");
        require(uuid(approval,"operatorId").equals(uuid(request.authority().identity(),"operatorId")),"RECOVERY_OPERATIONS_OPERATOR");
        require(!instant(approval,"requestedAt").isAfter(Instant.now()),"RECOVERY_OPERATIONS_FUTURE");
        bindControl(context.inventory()); bindSource(context.inventory()); bindPhysical(context);
    }
    private void bindSource(RecoveryReleaseInventory inventory) {
        var authority=request.authority().closed(); var source=authority.sourceIdentity(); var facts=inventory.facts();
        require(text(facts,"sourceManifestSha256").equals(authority.sourcePinSha256())
            && uuid(facts,"warehouseId").equals(source.warehouseId()) && uuid(facts,"scopeId").equals(source.scopeId())
            && uuid(facts,"submissionId").equals(source.submissionId()) && uuid(facts,"deviceId").equals(source.deviceId()),
            "RECOVERY_OPERATIONS_SOURCE_BINDING");
    }
    private void bindControl(RecoveryReleaseInventory inventory) {
        var control=request.authority().closed().control(); var observed=inventory.facts().path("control");
        require(approval.path("control").equals(observed),"RECOVERY_OPERATIONS_CONTROL_BINDING");
        require(uuid(approval,"recoverySessionId").equals(control.recoverySessionId())
            && uuid(observed,"installationId").equals(control.installationId()) && uuid(observed,"epochId").equals(control.epochId())
            && text(observed,"controlRevision").equals(control.revision())
            && text(observed,"controlRecordSha256").equals(control.recordSha256()),"RECOVERY_OPERATIONS_CONTROL_BINDING");
        String warehouse=text(inventory.facts(),"warehouseId");
        require(control.affectedWarehouseIds().contains(warehouse),"RECOVERY_OPERATIONS_CONTROL_SCOPE");
        boolean permitted=false;
        for(var value:request.authority().identity().path("warehouseIds")) if(value.asText().equals(warehouse)) permitted=true;
        require(permitted,"RECOVERY_OPERATIONS_WAREHOUSE_FORBIDDEN");
    }
    private void bindPhysical(Context context) {
        var physical=context.physical(); var inventory=context.inventory().facts(); var facts=physical.facts();
        require(approval.path("physicalReview").equals(facts) && text(facts,"physicalState").equals("MATCHED")
            && text(facts,"inventorySha256").equals(context.inventory().sha256()),"RECOVERY_OPERATIONS_PHYSICAL_BINDING");
        var fault=inventory.path("faultReceipt"); String caseId=text(fault.path("businessDecision"),"caseId");
        require(uuid(approval,"caseId").toString().equals(caseId),"RECOVERY_OPERATIONS_CASE_BINDING");
        var rows=RecoveryReleaseRows.decode(inventory.path("tables").path("handover_command_receipt"));
        var signed=rows.stream().filter(row->RecoveryReleaseRows.text(row,"command_type").equals("APPROVE")
            && Objects.equals(row.get("case_id"),RecoveryReleaseRows.id(caseId))).toList();
        require(signed.size()==1,"RECOVERY_OPERATIONS_APPROVAL_REQUIRED");
        byte[] receipt=RecoveryReleaseRows.bytes(signed.getFirst().get("response_bytes"));
        require(RecoveryControlFormat.sha256(receipt).equals(text(facts,"approvalReceiptSha256")),"RECOVERY_OPERATIONS_APPROVAL_HASH");
        var original=com.acrylic.outbound.submissions.SubmissionJson.parse(receipt);
        require(text(approval,"supervisorId").equals(text(original.path("actor"),"principalId")),"RECOVERY_OPERATIONS_SUPERVISOR_BINDING");
        require(!instant(approval,"requestedAt").isBefore(Instant.parse(text(original,"recordedAt"))),"RECOVERY_OPERATIONS_BEFORE_SUPERVISOR");
    }
    private static JsonNode read(Request request) {
        require(request.independentSha256()!=null && request.independentSha256().matches("[a-f0-9]{64}")
            && !ZERO_HASH.equals(request.independentSha256()),"RECOVERY_OPERATIONS_APPROVAL_PIN_REQUIRED");
        byte[] bytes=request.authority().material(request.file());
        require(RecoveryControlFormat.sha256(bytes).equals(request.independentSha256()),"RECOVERY_OPERATIONS_APPROVAL_PIN_MISMATCH");
        return RecoveryOperationsSignature.verify(bytes,request.authority());
    }
    public JsonNode facts() { return approval.deepCopy(); }
    RecoveryClosedAuthority closed() { return request.authority().closed(); }
    void requireMaterials() {
        request.authority().requireUnchanged(); require(approval.equals(read(request)),"RECOVERY_OPERATIONS_APPROVAL_CHANGED");
    }
    void requireOutside(Path root) {
        require(!RecoveryControlPaths.overlap(root,request.file())
            && !RecoveryControlPaths.overlap(root,request.authority().identityFile()),"RECOVERY_RELEASE_STORE_MATERIAL_OVERLAP");
    }
    public String sha256() { return sha256; }
    public boolean releaseAllowed() { return false; }
    public boolean ordinaryWritesAllowed() { return false; }
    public boolean localDeletionAllowed() { return false; }
    public record Request(RecoveryOperationsAuthority authority,Path file,String independentSha256) {
        public Request { Objects.requireNonNull(authority); Objects.requireNonNull(file); }
    }
    public record Context(RecoveryReleasePhysicalReview physical,RecoveryReleaseInventory inventory) {
        public Context { Objects.requireNonNull(physical); Objects.requireNonNull(inventory); }
    }
}
