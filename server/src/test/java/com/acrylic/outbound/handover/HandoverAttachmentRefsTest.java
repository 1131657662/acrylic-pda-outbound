package com.acrylic.outbound.handover;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

class HandoverAttachmentRefsTest {
    @Test void absentFieldPreservesOldCommandCompatibility() {
        assertThat(HandoverAttachmentRefs.read(object())).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings={"null", "[]", "{}", "\"file\""})
    void explicitInvalidCollectionIsRefused(String value) {
        assertThatThrownBy(() -> HandoverAttachmentRefs.read(parse("{\"attachments\":" + value + "}")))
            .hasMessageContaining("1 至 5");
    }

    @Test void fiveMaximumFilesReachExactlyCommandLimit() {
        var body = object(); var files = body.putArray("attachments");
        for (int i = 0; i < 5; i++) files.add(ref());
        assertThat(HandoverAttachmentRefs.read(body).stream().mapToLong(HandoverAttachmentRefs.Ref::size).sum())
            .isEqualTo(HandoverAttachmentBytes.MAX_COMMAND_BYTES);
    }

    @Test void sixthFileCannotBypassTheCountLimit() {
        var body = object(); var files = body.putArray("attachments");
        for (int i = 0; i < 6; i++) files.add(ref());
        assertThatThrownBy(() -> HandoverAttachmentRefs.read(body)).hasMessageContaining("1 至 5");
    }

    @Test void duplicateReferenceIsRefused() {
        var body = object(); var item = ref(); body.putArray("attachments").add(item).add(item.deepCopy());
        assertThatThrownBy(() -> HandoverAttachmentRefs.read(body)).hasMessageContaining("重复引用");
    }

    @Test void extraFieldsCannotSmuggleFilenameOrAuthority() {
        var body = object(); body.putArray("attachments").add(ref().put("releaseAllowed", true));
        assertThatThrownBy(() -> HandoverAttachmentRefs.read(body)).hasMessageContaining("格式无效");
    }

    @ParameterizedTest @ValueSource(strings={"0", "-1", "01", "10485761", "99999999999999999999"})
    void invalidOrOversizedByteCountIsRefused(String size) {
        var body = object(); body.putArray("attachments").add(ref().put("sizeBytes", size));
        assertThatThrownBy(() -> HandoverAttachmentRefs.read(body)).isInstanceOf(com.acrylic.outbound.common.BusinessException.class);
    }

    private com.fasterxml.jackson.databind.node.ObjectNode ref() {
        return object().put("attachmentId", UUID.randomUUID().toString()).put("evidenceSha256", "a".repeat(64))
            .put("sizeBytes", Integer.toString(HandoverAttachmentBytes.MAX_BYTES));
    }
}
