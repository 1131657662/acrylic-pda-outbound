package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.command.CommandContext;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SourceEvidenceIntakeReadIntegrity {
    private final OfflineSigner signer;

    public SourceEvidenceIntakeReadIntegrity(OfflineSigner signer) { this.signer = signer; }

    public Result check(Map<String, Object> row, CommandContext source, UUID originalKey) {
        try { return checked(row, source, originalKey); }
        catch (BusinessException unavailable) {
            if (unavailable.status() == 503) throw unavailable;
            throw corrupt();
        }
        catch (RuntimeException damaged) { throw corrupt(); }
    }

    private Result checked(Map<String, Object> row, CommandContext source, UUID originalKey) {
        require(number(row, "receipt_version") == 1 && originalKey.equals(id(row, "original_key"))
            && E2aProtocol.ROUTE_SCOPE.equals(text(row, "route_scope")));
        require(id(row, "principal_id").equals(id(row, "original_operator_id"))
            && id(row, "warehouse_id").equals(source.id("warehouseId"))
            && id(row, "scope_id").equals(source.id("scopeId"))
            && id(row, "submission_id").equals(source.id("submissionId"))
            && number(row, "work_round") == source.version("workRound")
            && id(row, "creator_device_id").equals(source.id("creatorDeviceId"))
            && id(row, "client_order_id").equals(source.id("clientOrderId"))
            && id(row, "temporary_authorization_id").equals(source.id("authorizationId"))
            && id(row, "original_operator_id").equals(source.id("originalOperatorId"))
            && number(row, "source_recovery_epoch") == source.version("sourceRecoveryEpoch"));
        byte[] request = bytes(row, "canonical_request_bytes");
        require(Arrays.equals(request, E2aProtocol.canonical(request))
            && E2aProtocol.intakeSha256(request).equals(text(row, "request_sha256")));
        byte[] response = bytes(row, "original_response_bytes");
        require(Arrays.equals(response, E2aProtocol.canonical(response))
            && ImportFiles.sha256(response).equals(text(row, "original_response_sha256")));
        var receipt = SubmissionJson.parse(response);
        require(receipt.isObject() && receipt.path("receiptVersion").asInt() == 1
            && id(row, "id").toString().equals(receipt.path("receiptId").asText())
            && originalKey.toString().equals(receipt.path("originalKey").asText())
            && text(row, "operation_kind").equals(receipt.path("operationKind").asText())
            && text(row, "receipt_status").equals(receipt.path("status").asText())
            && source.id("submissionId").toString().equals(receipt.path("submissionId").asText())
            && source.version("workRound") == receipt.path("workRound").asLong()
            && source.id("authorizationId").toString().equals(receipt.path("sourceId").asText())
            && "TEMP".equals(receipt.path("source").asText())
            && SourceEvidenceIntakeReceiptCodec.capabilities().equals(receipt.path("capabilities")));
        context(row, receipt.path("originalContext"));
        references(row, receipt);
        String token = text(row, "signed_receipt");
        require(token.equals(receipt.path("signedReceipt").asText())
            && text(row, "signature_key_id").equals(tokenKid(token)));
        var trustedKey = signer.publicKeyFor(tokenKid(token));
        String trust = trustedKey == null ? "UNVERIFIABLE_KEY" : E2aProtocol.verifyRsaReceipt(receipt, trustedKey);
        require(Set.of("VERIFIED", "UNVERIFIABLE_KEY").contains(trust));
        return new Result(response, text(row, "original_response_sha256"), trust, receipt);
    }

    private static void context(Map<String, Object> row, JsonNode context) {
        require(context.isObject());
        for (var entry : Map.of("principalId", "principal_id", "warehouseId", "warehouse_id",
            "scopeId", "scope_id", "creatorDeviceId", "creator_device_id", "clientOrderId", "client_order_id",
            "temporaryAuthorizationId", "temporary_authorization_id", "originalOperatorId", "original_operator_id").entrySet())
            require(id(row, entry.getValue()).toString().equals(context.path(entry.getKey()).asText()));
        require(number(row, "source_recovery_epoch") == context.path("sourceRecoveryEpoch").asLong());
    }

    private static void references(Map<String, Object> row, JsonNode receipt) {
        String operation = text(row, "operation_kind");
        require(Set.of("OPEN", "PAGE", "STOP", "VERIFY").contains(operation)
            && operation.equals(SubmissionJson.text(SubmissionJson.parse(bytes(row, "canonical_request_bytes")), "operation")));
        if ("OPEN".equals(operation)) {
            require("RECEIVED".equals(text(row, "receipt_status")) && absent(row, "object_id")
                && absent(row, "evidence_set_id") && absent(row, "evidence_verification_id"));
        } else if ("VERIFY".equals(operation)) {
            require(Set.of("PARTIAL", "VERIFIED_COMPLETE_AT_STOP").contains(text(row, "receipt_status")) && absent(row, "object_id")
                && id(row, "evidence_set_id").toString().equals(receipt.path("evidenceSetId").asText())
                && id(row, "evidence_verification_id").toString().equals(receipt.path("evidenceVerificationId").asText()));
        } else {
            require("PARTIAL".equals(text(row, "receipt_status"))
                && id(row, "object_id").toString().equals(receipt.path("objectId").asText())
                && absent(row, "evidence_set_id") && absent(row, "evidence_verification_id"));
        }
    }

    private static String tokenKid(String token) {
        var parts = token.split("\\.", -1);
        require(parts.length == 3 && parts[0].matches("[A-Za-z0-9_-]+"));
        var header = SubmissionJson.parse(Base64.getUrlDecoder().decode(parts[0]));
        return SubmissionJson.text(header, "kid");
    }

    private static boolean absent(Map<String, Object> row, String field) { return row.get(field) == null; }
    private static String text(Map<String, Object> row, String field) { return (String) row.get(field); }
    private static long number(Map<String, Object> row, String field) { return ((Number) row.get(field)).longValue(); }
    private static byte[] bytes(Map<String, Object> row, String field) { return (byte[]) row.get(field); }
    private static UUID id(Map<String, Object> row, String field) {
        var value = bytes(row, field);
        require(value != null && value.length == 16);
        var buffer = java.nio.ByteBuffer.wrap(value);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
    private static void require(boolean valid) { if (!valid) throw corrupt(); }
    private static BusinessException corrupt() {
        return new BusinessException(409, "E2A_RECEIPT_INTEGRITY_FAILURE", "原机接收回执不可核验");
    }

    public record Result(byte[] originalBytes, String sha256, String trustStatus, JsonNode receipt) {
        public Result { originalBytes = originalBytes.clone(); receipt = receipt.deepCopy(); }
        @Override public byte[] originalBytes() { return originalBytes.clone(); }
        @Override public JsonNode receipt() { return receipt.deepCopy(); }
    }
}
