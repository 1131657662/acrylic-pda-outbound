package com.acrylic.outbound.recovery;

import com.acrylic.outbound.audit.AuditArchiveReader;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Verifies retained original audit segments with independent public keys, never today's signing service. */
public final class RecoveryAuditArchiveProof {
    private final RecoveryReceiptKeys keys;

    public RecoveryAuditArchiveProof(RecoveryReceiptKeys keys) { this.keys = Objects.requireNonNull(keys); }

    public ArrayNode read(Segment segment, RecoverySourceIdentity source) {
        require(segment != null && source != null, "RECOVERY_AUDIT_ARCHIVE_REQUIRED");
        try { return checked(segment, source); }
        catch (RecoveryControlException refusal) { throw refusal; }
        catch (GeneralSecurityException | RuntimeException invalid) {
            throw new RecoveryControlException("RECOVERY_AUDIT_ARCHIVE_INVALID", invalid);
        }
    }

    private ArrayNode checked(Segment segment, RecoverySourceIdentity source) throws GeneralSecurityException {
        var proof = signed(segment.proof()); byte[] bytes = segment.payload();
        require(bytes.length > 0 && bytes.length <= 512 * 1024 && bytes.length == revision(proof, "byteCount")
            && sha256(bytes).equals(text(proof, "payloadSha256")), "RECOVERY_AUDIT_ARCHIVE_CONTENT");
        require(uuid(proof, "originDeviceId").equals(source.deviceId()) && uuid(proof, "warehouseId").equals(source.warehouseId())
            && text(proof, "recoveryEpoch").equals(source.sourceRecoveryEpoch()), "RECOVERY_AUDIT_ARCHIVE_SOURCE");
        require(proof.path("archiveVersion").isIntegralNumber() && proof.path("archiveVersion").canConvertToInt()
            && proof.path("archiveVersion").intValue() == 1
            && "SERVER_DURABLE_FILES_V1".equals(text(proof, "storagePolicy")), "RECOVERY_AUDIT_ARCHIVE_POLICY");
        require(proof.path("localDeletionAllowed").isBoolean() && !proof.path("localDeletionAllowed").booleanValue(),
            "RECOVERY_AUDIT_ARCHIVE_RELEASE");
        var archived = Instant.parse(text(proof, "archivedAt"));
        require(!Instant.parse(text(proof, "retainUntil")).isBefore(archived.atOffset(ZoneOffset.UTC).plusYears(5).toInstant()),
            "RECOVERY_AUDIT_ARCHIVE_POLICY");
        var body = SubmissionJson.parse(bytes); long after = revision(proof, "afterSeq"), through = revision(proof, "throughSeq");
        require(uuid(body, "originDeviceId").equals(source.deviceId()) && revision(body, "afterSeq") == after
            && revision(body, "throughSeq") == through, "RECOVERY_AUDIT_ARCHIVE_CONTENT");
        AuditArchiveReader.range(proof); var events = SubmissionJson.array(body, "events");
        AuditArchiveReader.validateEvents(events, source.deviceId(), after, through); return events.deepCopy();
    }

    private JsonNode signed(JsonNode proof) throws GeneralSecurityException {
        require(proof != null && proof.isObject(), "RECOVERY_AUDIT_ARCHIVE_REQUIRED");
        String token = text(proof, "signedArchive");
        require(token.length() <= 1048576, "RECOVERY_AUDIT_ARCHIVE_SIZE");
        String[] parts = token.split("\\.", -1); require(parts.length == 3, "RECOVERY_AUDIT_ARCHIVE_TOKEN");
        var header = SubmissionJson.parse(decode(parts[0])); fields(header, "alg typ kid");
        require("RS256".equals(text(header, "alg")) && "JWT".equals(text(header, "typ")), "RECOVERY_AUDIT_ARCHIVE_ALGORITHM");
        var verifier = Signature.getInstance("SHA256withRSA"); verifier.initVerify(keys.requireKey(text(header, "kid")));
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        require(verifier.verify(decode(parts[2])), "RECOVERY_AUDIT_ARCHIVE_SIGNATURE");
        var claims = SubmissionJson.parse(decode(parts[1])); fields(claims, "iss aud archive");
        require("acrylic-outbound".equals(text(claims, "iss")) && "acrylic-pda-audit-archive".equals(text(claims, "aud")),
            "RECOVERY_AUDIT_ARCHIVE_PURPOSE");
        var unsigned = (com.fasterxml.jackson.databind.node.ObjectNode) proof.deepCopy(); unsigned.remove("signedArchive");
        require(unsigned.equals(claims.path("archive")), "RECOVERY_AUDIT_ARCHIVE_CONTENT"); return proof;
    }

    private byte[] decode(String value) {
        require(value.matches("[A-Za-z0-9_-]+"), "RECOVERY_AUDIT_ARCHIVE_TOKEN");
        byte[] bytes = Base64.getUrlDecoder().decode(value);
        require(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(value), "RECOVERY_AUDIT_ARCHIVE_TOKEN");
        return bytes;
    }

    public record Segment(JsonNode proof, byte[] payload) {
        public Segment {
            require(proof != null && payload != null, "RECOVERY_AUDIT_ARCHIVE_REQUIRED");
            proof = proof.deepCopy(); payload = payload.clone();
        }
        @Override public JsonNode proof() { return proof.deepCopy(); }
        @Override public byte[] payload() { return payload.clone(); }
    }
}
