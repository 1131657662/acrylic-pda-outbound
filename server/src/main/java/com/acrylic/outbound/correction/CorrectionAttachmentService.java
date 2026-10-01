package com.acrylic.outbound.correction;

import com.acrylic.outbound.audit.AuditWriter;
import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.IdentityWriteGuard;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Authorizes durable, original-key uploads; no attachment grants an approval or deletion capability. */
@Service
@ConditionalOnProperty(prefix = "outbound.corrections", name = {"commands.enabled", "attachments.enabled"}, havingValue = "true")
public class CorrectionAttachmentService {
    private final CorrectionAttachmentStore store;
    private final CorrectionCommand.Identity identity;
    private final IdentityWriteGuard guard;
    private final AuditWriter audit;

    CorrectionAttachmentService(CorrectionAttachmentStore store, CorrectionCommand.Identity identity,
            IdentityWriteGuard guard, AuditWriter audit) {
        this.store = store; this.identity = identity; this.guard = guard; this.audit = audit;
    }

    void authorizeUpload(UUID warehouse) { identity.current(warehouse, false); }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Metadata upload(UUID warehouse, UUID key, String epoch, String sha, byte[] bytes, String requestId) {
        if (requestId == null || !requestId.matches("[A-Za-z0-9._:-]{1,64}"))
            throw new BusinessException(400, "INVALID_REQUEST_ID", "请求追踪标识无效");
        CorrectionAttachmentBytes.verify(bytes, sha);
        guard.lock(epoch);
        var actor = identity.current(warehouse, false);
        guard.users(List.of(actor.id()));
        actor = identity.current(warehouse, false);
        var owner = new CorrectionAttachmentStore.Owner(warehouse, actor.id());
        var existing = store.byUpload(owner, key);
        if (existing != null) {
            existing.verify();
            if (!Arrays.equals(existing.bytes(), bytes)) throw CorrectionCommand.conflict();
            return metadata(existing);
        }
        var stored = store.insert(owner, key, bytes);
        var details = SubmissionJson.object().put("sha256", stored.sha()).put("sizeBytes", stored.size())
            .put("warehouseId", warehouse.toString()).put("uploadKey", key.toString());
        audit.append(new AuditWriter.Event(actor.id(), "CORRECTION_ATTACHMENT_UPLOAD", "CORRECTION_ATTACHMENT",
            stored.id(), details.toString()), requestId);
        return metadata(stored);
    }

    @Transactional(readOnly = true)
    public byte[] read(UUID warehouse, UUID attachment, boolean review) {
        var actor = identity.current(warehouse, review);
        var stored = review ? store.forReview(warehouse, attachment)
            : store.owned(new CorrectionAttachmentStore.Owner(warehouse, actor.id()), attachment);
        stored.verify();
        return stored.bytes();
    }

    private Metadata metadata(CorrectionAttachmentStore.Stored stored) {
        return new Metadata(stored.id(), stored.sha(), Integer.toString(stored.size()));
    }

    public record Metadata(UUID attachmentId, String evidenceSha256, String sizeBytes) { }
}
