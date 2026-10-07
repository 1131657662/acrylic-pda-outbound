package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.io.IOException;
import java.util.Arrays;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SourceEvidenceIntakeVerifyPreflight {
    private final SourceEvidenceIntakeStopReceipts receipts;
    private final SourceEvidenceIntakeReceipts opens;
    private final SourceEvidenceIntakeStopObjects stops;
    private final SourceEvidenceIntakeObjectFiles files;
    private final SourceEvidenceIntakePageClosure pages;
    private final SourceEvidenceIntakeDeviceKeys deviceKeys;
    private final SourceEvidenceIntakeAuditPrefix audits;

    public SourceEvidenceIntakeVerifyPreflight(SourceEvidenceIntakeStopReceipts receipts,
        SourceEvidenceIntakeReceipts opens,
        SourceEvidenceIntakeStopObjects stops, SourceEvidenceIntakeObjectFiles files,
        SourceEvidenceIntakePageClosure pages, SourceEvidenceIntakeDeviceKeys deviceKeys,
        SourceEvidenceIntakeAuditPrefix audits) {
        this.receipts = receipts; this.opens = opens; this.stops = stops; this.files = files; this.pages = pages;
        this.deviceKeys = deviceKeys;
        this.audits = audits;
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public void requireStoredRoots(byte[] originalStopBytes, SourceEvidenceIntakeStop.Expected expected,
        SourceEvidenceIntakeReceipts.BoundKey key, SourceEvidenceIntakeStopObjects.Binding stopBinding,
        SourceEvidenceIntakePageObjects.Binding pageBinding) {
        requireBinding(expected, key, stopBinding, pageBinding);
        var openKey = new SourceEvidenceIntakeReceipts.BoundKey(key.principalId(), expected.openKey(),
            key.warehouseId(), key.submissionId(), key.workRound(), key.creatorDeviceId(), key.authorizationId());
        var storedOpen = opens.openBound(openKey);
        if (storedOpen == null || !Arrays.equals(storedOpen.canonicalBytes(), expected.open().canonicalBytes()))
            throw conflict("E2A_OPEN_CHANGED");
        var registeredKey = deviceKeys.trusted(expected.warehouseId(), expected.deviceId());
        if (registeredKey == null || !Arrays.equals(registeredKey.getEncoded(), expected.trustedKey().getEncoded()))
            throw conflict("E2A_STOP_KEY_UNVERIFIABLE");
        var trusted = new SourceEvidenceIntakeStop.Expected(expected.openKey(), storedOpen,
            expected.warehouseId(), expected.operatorId(), expected.deviceId(), expected.recoveryEpoch(), registeredKey);
        SourceEvidenceIntakeStop.Parsed stop;
        try { stop = SourceEvidenceIntakeStop.parse(originalStopBytes, trusted); }
        catch (RuntimeException invalid) { throw conflict("E2A_STOP_PROOF_UNVERIFIABLE"); }
        var receiptObjectId = org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly()
            ? receipts.objectIdForRead(key, stop.canonicalBytes()) : receipts.objectIdForLockedReplay(key, stop.canonicalBytes());
        var object = stops.findAfterSourceLock(stopBinding, stop.stopEventId());
        if (object == null || !receiptObjectId.equals(object.id())
            || !ImportFiles.sha256(stop.canonicalStopProofBytes()).equals(object.stored().sha256())
            || stop.canonicalStopProofBytes().length != object.stored().bytes())
            throw conflict("E2A_STOP_OBJECT_INTEGRITY_FAILURE");
        try { files.verify(object.stored()); }
        catch (IOException failure) { throw conflict("E2A_STOP_FILE_UNAVAILABLE"); }
        var assessment = pages.inspect(stop, pageBinding);
        if (!assessment.missingPageNumbers().isEmpty())
            throw conflict("E2A_PAGE_INCOMPLETE");
        if (!assessment.auditCovered()) throw conflict("E2A_AUDIT_COVERAGE_INCOMPLETE");
        if (!assessment.captureCovered()) throw conflict("E2A_CAPTURE_WATERMARK_INCOMPLETE");
        long through = SubmissionJson.parse(stop.canonicalStopProofBytes()).path("core").path("auditThroughSeq").longValue();
        audits.require(expected.deviceId(), expected.warehouseId(), expected.recoveryEpoch(), through);
        audits.requireStopEvent(expected.deviceId(), expected.warehouseId(), expected.operatorId(),
            key.authorizationId(), expected.recoveryEpoch(), through, stop.stopEventId(), key.submissionId());
    }

    private static void requireBinding(SourceEvidenceIntakeStop.Expected expected,
        SourceEvidenceIntakeReceipts.BoundKey key, SourceEvidenceIntakeStopObjects.Binding stopBinding,
        SourceEvidenceIntakePageObjects.Binding pageBinding) {
        if (expected == null || key == null || stopBinding == null || pageBinding == null
            || !expected.openKey().equals(stopBinding.openKey())
            || !expected.openKey().equals(pageBinding.openKey())
            || !expected.warehouseId().equals(stopBinding.warehouseId())
            || !expected.operatorId().equals(stopBinding.operatorId())
            || !expected.deviceId().equals(stopBinding.deviceId())
            || !expected.open().submissionId().equals(stopBinding.submissionId())
            || expected.open().workRound() != stopBinding.workRound()
            || !expected.open().sourceId().equals(key.authorizationId().toString())
            || !stopBinding.warehouseId().equals(pageBinding.warehouseId())
            || !stopBinding.scopeId().equals(pageBinding.scopeId())
            || !stopBinding.submissionId().equals(pageBinding.submissionId())
            || stopBinding.workRound() != pageBinding.workRound()
            || !stopBinding.deviceId().equals(pageBinding.creatorDeviceId())
            || !stopBinding.operatorId().equals(pageBinding.originalOperatorId())
            || !key.warehouseId().equals(stopBinding.warehouseId())
            || !key.submissionId().equals(stopBinding.submissionId())
            || key.workRound() != stopBinding.workRound()
            || !key.creatorDeviceId().equals(stopBinding.deviceId())
            || !key.principalId().equals(stopBinding.operatorId()))
            throw new IllegalArgumentException("E2A_VERIFY_PREFLIGHT_BINDING");
    }

    private static BusinessException conflict(String code) {
        return new BusinessException(409, code, "停止与分页根尚不可完整核验");
    }
}
