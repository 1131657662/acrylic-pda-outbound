package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import com.acrylic.outbound.temporary.protocol.TemporaryProtocol;
import java.util.Arrays;

public final class SourceEvidenceIntakeReplay {
    private SourceEvidenceIntakeReplay() { }

    public static CanonicalRequest canonicalize(byte[] requestBytes) {
        try {
            byte[] canonical=canonicalObject(requestBytes);
            return new CanonicalRequest(canonical,E2aProtocol.intakeSha256(canonical));
        } catch(RuntimeException invalid) {
            throw new BusinessException(400,"E2A_INTAKE_REQUEST_INVALID","接收正文或摘要无效");
        }
    }

    private static byte[] canonicalObject(byte[] bytes) {
        byte[] canonical=E2aProtocol.canonical(bytes);
        if(!SubmissionJson.parse(canonical).isObject()) throw new IllegalArgumentException("INTAKE_OBJECT_REQUIRED");
        return canonical;
    }

    public static Decision evaluate(CanonicalRequest request,StoredReceipt stored) {
        if(request==null) throw new IllegalArgumentException("INTAKE_REQUEST_REQUIRED");
        if(stored==null) return new Decision(Status.NOT_FOUND,null,TrustStatus.NOT_EVALUATED);
        if(!stored.isIntact()) return new Decision(Status.INTEGRITY_FAILURE,null,TrustStatus.NOT_EVALUATED);
        if(!Arrays.equals(request.bytes(),stored.canonicalRequestBytes()) || !request.sha256().equals(stored.requestSha256()))
            return new Decision(Status.CONFLICT,null,TrustStatus.NOT_EVALUATED);
        return new Decision(Status.REPLAY,stored.originalResponseBytes(),TrustStatus.NOT_EVALUATED);
    }

    public enum Status { NOT_FOUND, REPLAY, CONFLICT, INTEGRITY_FAILURE, UNKNOWN }
    public enum TrustStatus { NOT_EVALUATED }

    public record CanonicalRequest(byte[] bytes,String sha256) {
        public CanonicalRequest {
            byte[] canonical=canonicalObject(bytes);
            if(!Arrays.equals(canonical,bytes) || !E2aProtocol.intakeSha256(canonical).equals(sha256))
                throw new IllegalArgumentException("INTAKE_REQUEST_DIGEST");
            bytes=copy(bytes);
        }
        @Override public byte[] bytes() { return copy(bytes); }
    }

    public record Decision(Status status,byte[] originalResponseBytes,TrustStatus trustStatus) {
        public Decision { originalResponseBytes=copy(originalResponseBytes); }
        @Override public byte[] originalResponseBytes() { return copy(originalResponseBytes); }
    }

    public record StoredReceipt(int receiptVersion,byte[] canonicalRequestBytes,String requestSha256,
                                byte[] originalResponseBytes,String originalResponseSha256) {
        public StoredReceipt {
            canonicalRequestBytes=copy(canonicalRequestBytes);
            originalResponseBytes=copy(originalResponseBytes);
        }
        @Override public byte[] canonicalRequestBytes() { return copy(canonicalRequestBytes); }
        @Override public byte[] originalResponseBytes() { return copy(originalResponseBytes); }

        private boolean isIntact() {
            try {
                byte[] canonical=canonicalObject(canonicalRequestBytes);
                return receiptVersion==1 && Arrays.equals(canonical,canonicalRequestBytes)
                    && E2aProtocol.intakeSha256(canonical).equals(requestSha256)
                    && TemporaryProtocol.originalResponseSha256(originalResponseBytes).equals(originalResponseSha256);
            } catch(RuntimeException invalid) {
                return false;
            }
        }
    }

    private static byte[] copy(byte[] value) { return value==null?null:value.clone(); }
}
