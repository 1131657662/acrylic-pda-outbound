package com.acrylic.outbound.correction;

import com.acrylic.outbound.common.BusinessException;
import java.io.IOException;
import java.io.InputStream;

/** Bounded opaque bytes; neither filename, media type nor a declared length is trusted. */
final class CorrectionAttachmentBytes {
    static final int MAX_BYTES = 10 * 1024 * 1024;
    static final long MAX_COMMAND_BYTES = 50L * 1024 * 1024;
    private CorrectionAttachmentBytes() { }

    static byte[] read(InputStream input, String declaredSha) throws IOException {
        requireHash(declaredSha);
        byte[] bytes = input.readNBytes(MAX_BYTES + 1);
        verify(bytes, declaredSha);
        return bytes;
    }

    static void verify(byte[] bytes, String declaredSha) {
        requireHash(declaredSha);
        if (bytes == null || bytes.length == 0) throw invalid("附件内容不能为空");
        if (bytes.length > MAX_BYTES) throw limit("单个附件不得超过 10MB");
        if (!CorrectionCommand.sha(bytes).equals(declaredSha))
            throw new BusinessException(400, "ATTACHMENT_HASH_MISMATCH", "附件实际字节与声明摘要不一致");
    }

    static void requireHash(String sha) {
        if (sha == null || !sha.matches("[a-f0-9]{64}")) throw invalid("附件摘要必须为小写 SHA-256");
    }

    static BusinessException invalid(String message) {
        return new BusinessException(400, "INVALID_CORRECTION_ATTACHMENT", message);
    }

    static BusinessException limit(String message) {
        return new BusinessException(400, "ATTACHMENT_LIMIT_EXCEEDED", message);
    }

    static BusinessException missing() {
        return new BusinessException(404, "CORRECTION_ATTACHMENT_NOT_FOUND", "附件不存在或无权访问");
    }
}
