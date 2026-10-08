package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Closing attachment writes does not bypass or disable proof of recorded original evidence. */
@Service
@ConditionalOnProperty(prefix = "outbound.handover.commands", name = "enabled", havingValue = "true")
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class HandoverAttachmentDownload {
    private final HandoverAttachmentAccess access;
    private final HandoverAttachmentStore store;
    private final HandoverFaultArchive archives;
    private final HandoverStopStore origins;
    HandoverAttachmentDownload(HandoverAttachmentAccess access, HandoverAttachmentStore store,
            HandoverFaultArchive archives, HandoverStopStore origins) {
        this.access = access; this.store = store; this.archives = archives; this.origins = origins;
    }

    public byte[] read(UUID kase, UUID warehouse, UUID id) {
        var actor = access.current(warehouse); var facts = access.caseFacts(kase, warehouse);
        if ("FAULT_FENCED".equals(text(facts, "stopState"))) archives.verify(facts, origins.origin(kase), number(facts, "inputHighWatermark"));
        HandoverAttachmentStore.Stored stored;
        try { stored = store.owned(new HandoverAttachmentStore.Owner(kase, warehouse, actor.id()), id); }
        catch (BusinessException missing) { stored = store.forReview(kase, warehouse, id); }
        stored.verify(); return stored.bytes();
    }
}
