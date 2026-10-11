package com.acrylic.outbound.recovery;

import com.acrylic.outbound.handover.*;
import java.nio.file.Path;
import java.util.*;
import javax.sql.DataSource;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Independently pinned server input for one named receiving case, reopening only existing signed audits. */
public final class RecoveryRecipientDownloadInput {
    private static final int ARCHIVE_LIMIT=33554432;
    private final RecoveryRecipientDownloadMaterial index;
    private final Descriptor input;
    public RecoveryRecipientDownloadInput(Path file,String independentSha256) {
        index=new RecoveryRecipientDownloadMaterial(file,independentSha256);
        var value=readCanonical(index.read(RecoveryControlJson.MAX_BYTES));
        fields(value,"formatVersion purpose caseId archive originalFault operationsIdentity operationsApproval scopeRoot scopeLockIdentity admission qualification session numberBegin numberEnd receiptPublicKeys");
        version(value); require(text(value,"purpose").equals("RECOVERED_TEMP_DOWNLOAD_INPUT"),"RECOVERY_DOWNLOAD_INPUT_PURPOSE");
        input=RecoveryRecipientDownloadArchive.descriptor(value,Descriptor.class); requireUnchanged();
        new RecoveryReceiptKeys(input.receiptPublicKeys());
    }
    public Context load(DataSource database,UUID caseId) {
        requireUnchanged();
        if(!input.caseId().equals(caseId)) throw new com.acrylic.outbound.common.BusinessException(404,"HANDOVER_NOT_FOUND","交接案件不可读取");
        var archive=RecoveryRecipientDownloadArchive.read(input.archive().read(ARCHIVE_LIMIT));
        var closed=new RecoveryClosedAuthority(archive.location(),archive.checkpoint(),archive.pins());
        for(var material:List.of(index,input.archive(),input.originalFault())) material.outside(closed);
        var keys=new RecoveryReceiptKeys(input.receiptPublicKeys());
        var consumer=new RecoveryReceivedTemporaryArchiveConsumer(Objects.requireNonNull(database),closed,keys);
        var binding=new HandoverRecoveredTemporaryBinding(consumer,archive.request(),archive.nativeFacts());
        var history=new RecoveryReleaseNumberHistory.Request(archive.request(),keys,input.numberBegin(),input.numberEnd());
        var request=new HandoverRecoveredPhysicalReview.Request(input.originalFault().read(RecoveryControlJson.MAX_BYTES),binding,
            new RecoveryReleaseReconciliation.Request(closed,history,keys));
        var authority=new RecoveryOperationsAuthority(closed,input.operationsIdentity().file(),input.operationsIdentity().independentSha256());
        var approval=new RecoveryOperationsApproval.Request(authority,input.operationsApproval().file(),input.operationsApproval().independentSha256());
        var recipient=RecoveryRecipientAdmission.reopen(new RecoveryRecipientAdmission.Request(authority,input.admission().file(),input.admission().independentSha256()),
            new RecoveryRecipientAdmission.RetainedRelease(approval,input.scopeRoot(),input.scopeLockIdentity()));
        var qualification=RecoveryTemporaryQualification.reopen(new RecoveryTemporaryQualification.Request(authority,
            input.qualification().file(),input.qualification().independentSha256()),recipient);
        var session=RecoveryRecipientSession.reopen(new RecoveryRecipientSession.Request(authority,input.session().file(),input.session().independentSha256()),qualification);
        require(uuid(session.facts(),"caseId").equals(caseId),"RECOVERY_DOWNLOAD_CASE_BINDING"); requireUnchanged();
        return new Context(session,request,this::requireUnchanged);
    }
    private void requireUnchanged() {
        index.read(RecoveryControlJson.MAX_BYTES); input.archive().read(ARCHIVE_LIMIT); input.originalFault().read(RecoveryControlJson.MAX_BYTES);
    }
    public record Context(RecoveryRecipientSession session,HandoverRecoveredPhysicalReview.Request request,Runnable requireUnchanged) {
        public Context { Objects.requireNonNull(session); Objects.requireNonNull(request); Objects.requireNonNull(requireUnchanged); }
    }
    record Descriptor(int formatVersion,String purpose,UUID caseId,RecoveryRecipientDownloadMaterial archive,RecoveryRecipientDownloadMaterial originalFault,
            RecoveryRecipientDownloadMaterial operationsIdentity,RecoveryRecipientDownloadMaterial operationsApproval,Path scopeRoot,String scopeLockIdentity,
            RecoveryRecipientDownloadMaterial admission,RecoveryRecipientDownloadMaterial qualification,RecoveryRecipientDownloadMaterial session,
            RecoveryReleaseNumberHistory.Snapshot numberBegin,RecoveryReleaseNumberHistory.Snapshot numberEnd,List<byte[]> receiptPublicKeys) {
        Descriptor {
            require(caseId!=null && archive!=null && originalFault!=null && operationsIdentity!=null && operationsApproval!=null && scopeRoot!=null
                && scopeLockIdentity!=null && admission!=null && qualification!=null && session!=null && numberBegin!=null && numberEnd!=null
                && receiptPublicKeys!=null,"RECOVERY_DOWNLOAD_DESCRIPTOR_REQUIRED"); receiptPublicKeys=receiptPublicKeys.stream().map(byte[]::clone).toList();
        }
        @Override public List<byte[]> receiptPublicKeys() { return receiptPublicKeys.stream().map(byte[]::clone).toList(); }
    }
}
