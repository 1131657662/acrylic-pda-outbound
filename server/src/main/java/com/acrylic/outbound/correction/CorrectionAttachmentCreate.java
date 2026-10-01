package com.acrylic.outbound.correction;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

/** CREATE's existing outer transaction also owns byte verification and evidence references. */
final class CorrectionAttachmentCreate implements CorrectionCommand.Operation {
    private final CorrectionCommand.Operation delegate;
    private final CorrectionAttachmentConsumer attachments;

    CorrectionAttachmentCreate(CorrectionCommand.Operation delegate, CorrectionAttachmentConsumer attachments) {
        this.delegate = delegate; this.attachments = attachments;
    }

    @Override public String commandType() { return "CREATE"; }

    @Override public JsonNode execute(CorrectionCommand.Write write) {
        var uses = attachments.validate(write);
        var result = delegate.execute(write);
        attachments.bind(UUID.fromString(result.path("documentId").asText()), uses);
        return result;
    }
}
