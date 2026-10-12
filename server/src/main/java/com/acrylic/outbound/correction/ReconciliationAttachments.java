package com.acrylic.outbound.correction;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Verifies actual controlled bytes under the original submitter's scope before submission or approval. */
@Component
final class ReconciliationAttachments {
    private final CorrectionAttachmentStore store;
    ReconciliationAttachments(CorrectionAttachmentStore store) { this.store = store; }

    void verify(JsonNode evidence, JsonNode actor) {
        var attachments = evidence.path("attachments");
        if (!attachments.isArray() || attachments.isEmpty() || attachments.size() > 5) throw CorrectionCommand.evidenceRequired();
        var owner = new CorrectionAttachmentStore.Owner(UUID.fromString(actor.path("warehouseId").asText()),
            UUID.fromString(actor.path("principalId").asText()));
        var seen = new HashSet<UUID>(); long total = 0;
        for (var reference : attachments) {
            var names=new HashSet<String>(); reference.fieldNames().forEachRemaining(names::add);
            if (!reference.isObject() || !names.equals(java.util.Set.of("attachmentId","evidenceSha256","sizeBytes")))
                throw CorrectionCommand.evidenceRequired();
            for (String field : java.util.Set.of("attachmentId", "evidenceSha256", "sizeBytes"))
                if (!reference.path(field).isTextual()) throw CorrectionCommand.evidenceRequired();
            UUID id;
            try { id = UUID.fromString(reference.path("attachmentId").asText()); }
            catch (RuntimeException failure) { throw CorrectionCommand.evidenceRequired(); }
            if (!seen.add(id)) throw CorrectionCommand.evidenceRequired();
            var stored = store.owned(owner, id); stored.verify();
            if (!stored.sha().equals(reference.path("evidenceSha256").asText())
                || !Integer.toString(stored.size()).equals(reference.path("sizeBytes").asText())) throw CorrectionCommand.evidenceRequired();
            total = Math.addExact(total, stored.size());
            if (total > CorrectionAttachmentBytes.MAX_COMMAND_BYTES) throw CorrectionCommand.attachmentLimit();
        }
    }
}
