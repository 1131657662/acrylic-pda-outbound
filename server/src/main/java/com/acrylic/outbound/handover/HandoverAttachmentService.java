package com.acrylic.outbound.handover;

import com.acrylic.outbound.audit.AuditWriter;
import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.IdentityWriteGuard;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Original-key uploads recheck the caller and pending case inside the durable transaction. */
@Service
@ConditionalOnProperty(prefix = "outbound.handover", name = {"commands.enabled", "fault.enabled", "attachments.enabled"}, havingValue = "true")
public class HandoverAttachmentService {
    private final HandoverAttachmentStore store;
    private final HandoverAttachmentAccess access;
    private final IdentityWriteGuard guard;
    private final HandoverCommand.Locks locks;
    private final AuditWriter audit;

    HandoverAttachmentService(HandoverAttachmentStore store, HandoverAttachmentAccess access, IdentityWriteGuard guard,
            HandoverCommand.Locks locks, AuditWriter audit) {
        this.store = store; this.access = access; this.guard = guard; this.locks = locks; this.audit = audit;
    }

    void authorize(UUID kase, UUID warehouse) { access.upload(kase, access.current(warehouse)); }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Metadata upload(Upload input, byte[] bytes, String requestId) {
        if (requestId == null || !requestId.matches("[A-Za-z0-9._:-]{1,64}"))
            throw new BusinessException(400, "INVALID_REQUEST_ID", "请求追踪标识无效");
        HandoverAttachmentBytes.verify(bytes, input.sha()); guard.lock(input.epoch());
        var before = access.current(input.warehouse()); guard.users(List.of(before.id()));
        var actor = access.current(input.warehouse());
        locks.scope(uuid(access.caseFacts(input.kase(), input.warehouse()), "scopeId")); locks.kase(input.kase());
        access.upload(input.kase(), actor);
        var owner = new HandoverAttachmentStore.Owner(input.kase(), input.warehouse(), actor.id());
        var existing = store.byUpload(owner, input.key());
        if (existing != null) {
            existing.verify();
            if (!Arrays.equals(existing.bytes(), bytes)) throw HandoverCommand.conflict();
            return metadata(existing);
        }
        var stored = store.insert(owner, input.key(), bytes);
        var details = object().put("caseId", input.kase().toString()).put("sha256", stored.sha()).put("sizeBytes", stored.size())
            .put("warehouseId", input.warehouse().toString()).put("uploadKey", input.key().toString());
        audit.append(new AuditWriter.Event(actor.id(), "HANDOVER_ATTACHMENT_UPLOAD", "HANDOVER_ATTACHMENT", stored.id(), details.toString()), requestId);
        return metadata(stored);
    }

    private Metadata metadata(HandoverAttachmentStore.Stored stored) {
        return new Metadata(stored.id(), stored.sha(), Integer.toString(stored.size()));
    }

    public record Upload(UUID kase, UUID warehouse, UUID key, String epoch, String sha) { }
    public record Metadata(UUID attachmentId, String evidenceSha256, String sizeBytes) { }
}
