package com.acrylic.outbound.handover;

import java.io.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class HandoverAttachmentBytesTest {
    @Test void actualBytesSurviveIncludingNonTextContent() throws Exception {
        byte[] bytes = {0, -1, 1, 7, 0};
        assertThat(HandoverAttachmentBytes.read(new ByteArrayInputStream(bytes), HandoverCommand.sha(bytes))).isEqualTo(bytes);
    }

    @Test void maximumFileIsAccepted() {
        byte[] bytes = new byte[HandoverAttachmentBytes.MAX_BYTES];
        assertThatCode(() -> HandoverAttachmentBytes.verify(bytes, HandoverCommand.sha(bytes))).doesNotThrowAnyException();
    }

    @Test void oversizedStreamReadsOnlyOneBytePastLimit() {
        var input = new ByteArrayInputStream(new byte[HandoverAttachmentBytes.MAX_BYTES + 100]);
        assertThatThrownBy(() -> HandoverAttachmentBytes.read(input, "0".repeat(64))).hasMessageContaining("10MiB");
        assertThat(input.available()).isEqualTo(99);
    }

    @Test void wrongHashIsRefused() {
        assertThatThrownBy(() -> HandoverAttachmentBytes.verify(new byte[]{1}, "0".repeat(64))).hasMessageContaining("摘要不一致");
    }

    @Test void emptyBytesAreRefused() {
        assertThatThrownBy(() -> HandoverAttachmentBytes.verify(new byte[0], HandoverCommand.sha(new byte[0]))).hasMessageContaining("不能为空");
    }
}
