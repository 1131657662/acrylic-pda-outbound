package com.acrylic.outbound.handover;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Strict references preserve the exact order of at most five originals. */
final class HandoverAttachmentRefs {
    private HandoverAttachmentRefs() { }

    static List<Ref> read(JsonNode command) {
        var items = command.path("attachments");
        if (items.isMissingNode()) return List.of();
        if (!items.isArray() || items.isEmpty() || items.size() > 5)
            throw HandoverAttachmentBytes.limit("单条凭据须引用 1 至 5 个附件");
        var refs = new ArrayList<Ref>(); var seen = new HashSet<UUID>(); long total = 0;
        for (var item : items) {
            var ref = ref(item);
            if (!seen.add(ref.id())) throw HandoverAttachmentBytes.invalid("同一凭据不能重复引用附件");
            total = Math.addExact(total, ref.size()); refs.add(ref);
        }
        if (total > HandoverAttachmentBytes.MAX_COMMAND_BYTES)
            throw HandoverAttachmentBytes.limit("单次提交附件总量不得超过 50MiB");
        return List.copyOf(refs);
    }

    private static Ref ref(JsonNode item) {
        try {
            var fields = new HashSet<String>(); item.fieldNames().forEachRemaining(fields::add);
            if (!item.isObject() || !fields.equals(Set.of("attachmentId", "evidenceSha256", "sizeBytes"))) throw new IllegalArgumentException();
            var id = UUID.fromString(item.path("attachmentId").asText());
            if (!id.toString().equals(item.path("attachmentId").asText())) throw new IllegalArgumentException();
            var size = item.path("sizeBytes");
            if (!size.isTextual() || !size.asText().matches("[1-9][0-9]{0,7}")) throw new IllegalArgumentException();
            long length = Long.parseLong(size.asText());
            if (length > HandoverAttachmentBytes.MAX_BYTES) throw HandoverAttachmentBytes.limit("单个附件不得超过 10MiB");
            if (!item.path("evidenceSha256").isTextual()) throw new IllegalArgumentException();
            var sha = item.path("evidenceSha256").asText(); HandoverAttachmentBytes.hash(sha);
            return new Ref(id, sha, (int) length);
        } catch (IllegalArgumentException invalid) { throw HandoverAttachmentBytes.invalid("附件引用格式无效"); }
    }

    record Ref(UUID id, String sha, int size) { }
}
