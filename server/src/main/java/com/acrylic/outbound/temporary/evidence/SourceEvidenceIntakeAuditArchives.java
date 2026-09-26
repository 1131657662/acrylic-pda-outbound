package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.audit.AuditArchiveReader;
import com.acrylic.outbound.audit.AuditArchiveStore;
import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.E2aPageItem;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SourceEvidenceIntakeAuditArchives {
    private final AuditArchiveStore store;
    private final AuditArchiveReader reader;
    private final SourceEvidenceIntakeAuditPrefix prefix;

    public SourceEvidenceIntakeAuditArchives(AuditArchiveStore store, AuditArchiveReader reader,
        SourceEvidenceIntakeAuditPrefix prefix) {
        this.store = store; this.reader = reader; this.prefix = prefix;
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public void requireItem(E2aPageItem item, byte[] original, Context context) {
        if (item == null || original == null || context == null)
            throw new IllegalArgumentException("E2A_AUDIT_ARCHIVE_BINDING");
        if (!"ARCHIVE".equals(item.itemKind()) || !"AUDIT_ARCHIVE".equals(item.objectKind()))
            throw conflict();
        try {
            var proof = SubmissionJson.parse(original);
            long after = SubmissionJson.number(proof, "afterSeq");
            long through = SubmissionJson.number(proof, "throughSeq");
            long epoch = SubmissionJson.number(proof, "recoveryEpoch");
            String digest = SubmissionJson.sha(proof);
            if (epoch < 1 || epoch > context.recoveryEpoch() || through > context.auditThroughSeq()
                || through != item.originalDeviceSeq() || original.length != item.byteCount()
                || !Arrays.equals(original, SubmissionJson.canonical(proof).getBytes(StandardCharsets.UTF_8))
                || !item.itemBytesSha256().equals(ImportFiles.sha256(original))
                || !item.recordSha256().equals(digest)
                || !item.originalRecordId().equals(recordId(context.device(), epoch, after)))
                throw conflict();
            var stored = store.find(Long.toString(epoch), context.device(), after);
            if (stored == null || !proof.equals(stored)) throw conflict();
            var events = reader.read(stored, context.device(), context.warehouse());
            if (events.size() != through - after) throw conflict();
            for (var event : events)
                prefix.requireArchiveEvent(event, context.device(), context.warehouse(), context.recoveryEpoch());
        } catch (BusinessException unavailable) {
            if ("ARCHIVE_EVIDENCE_UNAVAILABLE".equals(unavailable.code())) throw unavailable;
            throw conflict();
        } catch (RuntimeException invalid) { throw conflict(); }
    }

    public static UUID recordId(UUID device, long epoch, long after) {
        if (device == null || epoch < 1 || after < 0)
            throw new IllegalArgumentException("E2A_AUDIT_ARCHIVE_KEY");
        String key = "acrylic-pda-audit-archive/" + device + "/" + epoch + "/" + after;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private static BusinessException conflict() {
        return new BusinessException(409, "E2A_AUDIT_ARCHIVE_UNVERIFIABLE",
            "原审计归档与签名、原文件或设备审计前缀不一致");
    }

    public record Context(UUID device, UUID warehouse, long recoveryEpoch, long auditThroughSeq) {
        public Context {
            if (device == null || warehouse == null || recoveryEpoch < 1 || auditThroughSeq < 1)
                throw new IllegalArgumentException("E2A_AUDIT_ARCHIVE_CONTEXT");
        }
    }
}
