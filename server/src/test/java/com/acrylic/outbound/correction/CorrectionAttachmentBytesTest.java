package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.BusinessException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CorrectionAttachmentBytesTest {
    @Test void acceptsExactlyTenMebibytes() throws Exception {
        byte[] bytes = new byte[CorrectionAttachmentBytes.MAX_BYTES];
        assertThat(CorrectionAttachmentBytes.read(new ByteArrayInputStream(bytes), CorrectionCommand.sha(bytes)))
            .isEqualTo(bytes);
    }

    @Test void readsAtMostLimitPlusOneFromAnUnboundedStream() {
        var count = new AtomicInteger();
        var input = new InputStream() {
            @Override public int read() { count.incrementAndGet(); return 1; }
        };
        assertThatThrownBy(() -> CorrectionAttachmentBytes.read(input, "0".repeat(64)))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("ATTACHMENT_LIMIT_EXCEEDED"));
        assertThat(count.get()).isEqualTo(CorrectionAttachmentBytes.MAX_BYTES + 1);
    }

    @Test void rejectsWrongDigestOfActualBytes() {
        assertThatThrownBy(() -> CorrectionAttachmentBytes.verify(new byte[]{1}, "0".repeat(64)))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("ATTACHMENT_HASH_MISMATCH"));
    }

    @Test void rejectsEmptyContentEvenWithMatchingDigest() {
        assertThatThrownBy(() -> CorrectionAttachmentBytes.verify(new byte[0], CorrectionCommand.sha(new byte[0])))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("INVALID_CORRECTION_ATTACHMENT"));
    }

    @Test void rejectsNoncanonicalDigestBeforeReading() {
        assertThatThrownBy(() -> CorrectionAttachmentBytes.read(InputStream.nullInputStream(), "ABC"))
            .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("INVALID_CORRECTION_ATTACHMENT"));
    }
}
