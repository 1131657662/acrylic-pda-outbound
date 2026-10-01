package com.acrylic.outbound.correction;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Rechecks actual bytes before CREATE; keeps only immutable identifiers while counting every reference. */
@Component
@ConditionalOnProperty(prefix = "outbound.corrections", name = {"commands.enabled", "attachments.enabled"}, havingValue = "true")
class CorrectionAttachmentConsumer {
    private final CorrectionAttachmentStore store;
    CorrectionAttachmentConsumer(CorrectionAttachmentStore store) { this.store = store; }

    List<CorrectionAttachmentStore.Use> validate(CorrectionCommand.Write write) {
        var owner = new CorrectionAttachmentStore.Owner(UUID.fromString(write.actor().path("warehouseId").asText()),
            UUID.fromString(write.actor().path("principalId").asText()));
        var body = write.command().body();
        var uses = new ArrayList<CorrectionAttachmentStore.Use>();
        long total = 0; int lineNumber = 0;
        for (var line : body.path("lines")) {
            total += line(owner, line, ++lineNumber, body.path("type").asText(), uses);
            if (total > CorrectionAttachmentBytes.MAX_COMMAND_BYTES)
                throw CorrectionAttachmentBytes.limit("单次提交附件总量不得超过 50MB");
        }
        return List.copyOf(uses);
    }

    private long line(CorrectionAttachmentStore.Owner owner, JsonNode line, int number, String type,
            List<CorrectionAttachmentStore.Use> uses) {
        var attachments = line.path("attachments");
        if (!attachments.isMissingNode() && !attachments.isNull()
                && (!attachments.isArray() || attachments.isEmpty() || attachments.size() > 5))
            throw CorrectionAttachmentBytes.limit("单条凭据最多 5 个附件");
        boolean referenced = false; long total = 0; var seen = new HashSet<UUID>();
        for (var item : attachments) {
            UUID id = id(item.path("attachmentId"));
            if (!seen.add(id)) throw CorrectionAttachmentBytes.invalid("同一明细不能重复引用附件");
            var stored = store.owned(owner, id); stored.verify();
            if (!stored.sha().equals(item.path("evidenceSha256").asText()) || size(item.path("sizeBytes")) != stored.size())
                throw CorrectionAttachmentBytes.invalid("附件声明与受控原件不一致");
            total += stored.size(); uses.add(new CorrectionAttachmentStore.Use(number, id));
            if (id.toString().equals(line.path("evidenceId").asText())
                    && stored.sha().equals(line.path("evidenceSha256").asText())) referenced = true;
        }
        boolean physical = Set.of("PHYSICAL_RETURN", "DISPATCH_CANCELLATION").contains(type) && line.hasNonNull("dispatchId");
        if ((physical || line.hasNonNull("evidenceId") || line.hasNonNull("evidenceSha256")) && !referenced)
            throw CorrectionCommand.evidenceRequired();
        return total;
    }

    void bind(UUID document, List<CorrectionAttachmentStore.Use> uses) {
        uses.forEach(use -> store.bind(document, use));
    }

    private UUID id(JsonNode value) {
        try {
            UUID id = UUID.fromString(value.asText());
            if (!id.toString().equals(value.asText())) throw new IllegalArgumentException();
            return id;
        } catch (IllegalArgumentException error) { throw CorrectionAttachmentBytes.invalid("必须引用受控附件 ID"); }
    }

    private long size(JsonNode value) {
        if ((!value.isTextual() && !value.isIntegralNumber()) || !value.asText().matches("[1-9][0-9]{0,7}"))
            throw CorrectionAttachmentBytes.invalid("附件大小必须为正整数字节数");
        return Long.parseLong(value.asText());
    }
}
