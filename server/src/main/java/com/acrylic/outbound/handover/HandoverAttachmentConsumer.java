package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Fault writes bind originals in the existing transaction; reads always re-prove them even with writes closed. */
@Component
final class HandoverAttachmentConsumer {
    private final HandoverAttachmentStore store;
    private final boolean enabled;
    HandoverAttachmentConsumer(HandoverAttachmentStore store, @Value("${outbound.handover.attachments.enabled:false}") boolean enabled) {
        this.store = store; this.enabled = enabled;
    }

    void consume(HandoverCommand.Write write, com.fasterxml.jackson.databind.node.ObjectNode decision) {
        var command = write.command().body();
        if (!command.has("attachments")) return;
        if (!enabled) throw new BusinessException(409, "HANDOVER_ATTACHMENTS_NOT_OPEN", "交接附件写入尚未开放");
        var owner = new HandoverAttachmentStore.Owner(write.caseId(), uuid(write.actor(), "warehouseId"), uuid(write.actor(), "principalId"));
        var refs = HandoverAttachmentRefs.read(command); int ordinal = 0;
        for (var ref : refs) { verify(owner, ref); store.bind(write, owner, ++ordinal, ref.id()); }
        decision.set("attachments", command.path("attachments"));
    }

    void verify(HandoverReadReceipt.Verified recorded) {
        try { verifyRecorded(recorded); }
        catch (BusinessException missingOrDamaged) { throw HandoverCommand.corrupt(); }
    }

    private void verifyRecorded(HandoverReadReceipt.Verified recorded) {
        var command = recorded.command(); var receipt = recorded.receipt();
        var refs = HandoverAttachmentRefs.read(command);
        var decision = receipt.path("businessDecision");
        HandoverCommand.corruptUnless(command.path("attachments").equals(decision.path("attachments")));
        var owner = new HandoverAttachmentStore.Owner(uuid(command, "caseId"), uuid(command, "warehouseId"), uuid(receipt.path("actor"), "principalId"));
        var uses = store.uses(uuid(receipt, "receiptId"), owner, text(command, "commandId"), sha(command));
        HandoverCommand.corruptUnless(uses.equals(refs.stream().map(HandoverAttachmentRefs.Ref::id).toList()));
        for (var ref : refs) verify(owner, ref);
    }

    private void verify(HandoverAttachmentStore.Owner owner, HandoverAttachmentRefs.Ref ref) {
        var stored = store.owned(owner, ref.id()); stored.verify();
        if (!stored.sha().equals(ref.sha()) || stored.size() != ref.size())
            throw HandoverAttachmentBytes.invalid("附件声明与受控原件不一致");
    }
}
