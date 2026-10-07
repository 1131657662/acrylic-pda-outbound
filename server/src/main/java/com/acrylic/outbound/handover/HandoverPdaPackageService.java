package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionFiles;
import java.io.UncheckedIOException;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Delivers only the original recovery bytes proved by the receiving PDA manifest. */
@Service
@ConditionalOnProperty(prefix="outbound.handover.commands", name="enabled", havingValue="true")
@Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
public class HandoverPdaPackageService {
    private final HandoverPdaEvidenceService evidence;
    private final SubmissionFiles files;

    HandoverPdaPackageService(HandoverPdaEvidenceService evidence, SubmissionFiles files) {
        this.evidence = evidence;
        this.files = files;
    }

    public byte[] download(UUID caseId) {
        var verified = evidence.read(caseId);
        var manifest = verified.manifest();
        if ("TEMP".equals(manifest.path("sourceKind").asText()))
            return HandoverReceiptCodec.bytes(verified.body());
        try { return files.read(manifest.path("recoveryPackageSha256").asText()); }
        catch (UncheckedIOException unavailable) {
            throw new BusinessException(409, "HANDOVER_STOP_FILE_UNAVAILABLE", "原恢复包不可读取或摘要不符，请保留资料");
        }
    }
}
