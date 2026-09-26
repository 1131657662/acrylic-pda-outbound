package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SourceEvidenceIntakeReceiptCodec {
    private final OfflineSigner signer;
    public SourceEvidenceIntakeReceiptCodec(OfflineSigner signer) { this.signer=signer; }

    public Receipt open(UUID receiptId,UUID originalKey,SourceEvidenceIntakeOpen.Parsed open,
                        SourceEvidenceIntakeReceipts.OpenInsert context,Instant recordedAt) {
        if(receiptId==null || originalKey==null || open==null || context==null || recordedAt==null
            || !originalKey.equals(context.originalKey()) || !open.submissionId().equals(context.submissionId())
            || open.workRound()!=context.workRound() || !open.sourceId().equals(context.authorizationId().toString())
            || !open.requestSha256().equals(context.requestSha256())
            || !java.util.Arrays.equals(open.canonicalBytes(),context.requestBytes()))throw new IllegalArgumentException("OPEN_RECEIPT_INPUT");
        var at=recordedAt.truncatedTo(ChronoUnit.MILLIS);var unsigned=unsigned(receiptId,originalKey,open,context,at);
        var signed=signer.sign(Map.of("iss",E2aProtocol.ISSUER,"aud",E2aProtocol.AUDIENCE,"receipt",unsigned));
        var response=((ObjectNode)unsigned).deepCopy();response.put("signedReceipt",signed.token());byte[] bytes=bytes(response);
        return new Receipt(receiptId,bytes,ImportFiles.sha256(bytes),signed.keyId(),signed.token(),at);
    }

    public Receipt page(UUID receiptId,UUID originalKey,SourceEvidenceIntakeOpen.Parsed open,
                        SourceEvidenceIntakePage.Parsed page,SourceEvidenceIntakePageReceipts.PageInsert input,
                        SourceEvidenceIntakePageObjects.PageObject object,Instant recordedAt) {
        if(receiptId==null || originalKey==null || open==null || page==null || input==null || object==null || recordedAt==null)
            throw new IllegalArgumentException("PAGE_RECEIPT_INPUT");
        var context=input.context();
        if(!originalKey.equals(context.originalKey()) || !object.id().equals(input.objectId())
            || !open.submissionId().equals(context.submissionId()) || open.workRound()!=context.workRound()
            || !open.sourceId().equals(context.authorizationId().toString())
            || !java.util.Arrays.equals(page.canonicalBytes(),context.requestBytes())
            || !page.requestSha256().equals(context.requestSha256())
            || !ImportFiles.sha256(page.pageCoreBytes()).equals(object.stored().sha256())
            || page.pageCoreBytes().length!=object.stored().bytes())throw new IllegalArgumentException("PAGE_RECEIPT_INPUT");
        var at=recordedAt.truncatedTo(ChronoUnit.MILLIS);
        var unsigned=SubmissionJson.object().put("receiptVersion",1).put("receiptId",receiptId.toString())
            .put("operationKind","PAGE").put("originalKey",originalKey.toString()).put("recordedAt",at.toString())
            .put("status","PARTIAL").put("openOriginalKey",page.openOriginalKey().toString())
            .put("manifestId",open.manifestId()).put("manifestVersion",open.manifestVersion())
            .put("snapshotId",open.snapshotId()).put("pageNo",page.pageNo()).put("pageCount",page.pageCount())
            .put("pageHash",page.pageHash()).put("objectId",object.id().toString())
            .put("objectBytesSha256",object.stored().sha256()).put("objectByteCount",object.stored().bytes())
            .put("source","TEMP").put("sourceId",open.sourceId()).put("submissionId",open.submissionId().toString())
            .put("workRound",open.workRound());
        unsigned.set("originalContext",originalContext(context));unsigned.set("capabilities",capabilities());
        var signed=signer.sign(Map.of("iss",E2aProtocol.ISSUER,"aud",E2aProtocol.AUDIENCE,"receipt",unsigned));
        var response=unsigned.deepCopy();response.put("signedReceipt",signed.token());byte[] bytes=bytes(response);
        return new Receipt(receiptId,bytes,ImportFiles.sha256(bytes),signed.keyId(),signed.token(),at);
    }

    public Receipt stop(UUID receiptId, UUID originalKey, SourceEvidenceIntakeOpen.Parsed open,
        SourceEvidenceIntakeStop.Parsed stop, SourceEvidenceIntakeReceipts.OpenInsert context,
        SourceEvidenceIntakeStopObjects.StopObject object, Instant recordedAt) {
        if (receiptId == null || originalKey == null || open == null || stop == null || context == null
            || object == null || recordedAt == null || !originalKey.equals(context.originalKey())
            || !open.submissionId().equals(context.submissionId()) || open.workRound() != context.workRound()
            || !open.sourceId().equals(context.authorizationId().toString())
            || !java.util.Arrays.equals(stop.canonicalBytes(), context.requestBytes())
            || !stop.requestSha256().equals(context.requestSha256())
            || !object.originalId().equals(SourceEvidenceIntakeStopObjects.originalId(
                stop.openOriginalKey(), stop.stopEventId()))
            || !ImportFiles.sha256(stop.canonicalStopProofBytes()).equals(object.stored().sha256())
            || stop.canonicalStopProofBytes().length != object.stored().bytes())
            throw new IllegalArgumentException("STOP_RECEIPT_INPUT");
        var at = recordedAt.truncatedTo(ChronoUnit.MILLIS);
        var unsigned = SubmissionJson.object().put("receiptVersion", 1).put("receiptId", receiptId.toString())
            .put("operationKind", "STOP").put("originalKey", originalKey.toString()).put("recordedAt", at.toString())
            .put("status", "PARTIAL").put("openOriginalKey", stop.openOriginalKey().toString())
            .put("manifestId", open.manifestId()).put("manifestVersion", open.manifestVersion())
            .put("manifestBytesHash", stop.manifestBytesHash()).put("setHash", stop.setHash())
            .put("stopEventId", stop.stopEventId()).put("objectId", object.id().toString())
            .put("objectBytesSha256", object.stored().sha256()).put("objectByteCount", object.stored().bytes())
            .put("source", "TEMP").put("sourceId", open.sourceId())
            .put("submissionId", open.submissionId().toString()).put("workRound", open.workRound());
        unsigned.set("originalContext", originalContext(context));
        unsigned.set("capabilities", capabilities());
        var signed = signer.sign(Map.of("iss", E2aProtocol.ISSUER, "aud", E2aProtocol.AUDIENCE, "receipt", unsigned));
        var response = unsigned.deepCopy();
        response.put("signedReceipt", signed.token());
        byte[] bytes = bytes(response);
        return new Receipt(receiptId, bytes, ImportFiles.sha256(bytes), signed.keyId(), signed.token(), at);
    }

    public Receipt verify(UUID receiptId, UUID originalKey, SourceEvidenceIntakeOpen.Parsed open,
        SourceEvidenceIntakeVerify.Parsed verify, SourceEvidenceIntakeReceipts.OpenInsert context,
        UUID setId, UUID verificationId, String result, Instant recordedAt) {
        if (receiptId == null || originalKey == null || open == null || verify == null || context == null
            || setId == null || verificationId == null || result == null || recordedAt == null
            || !originalKey.equals(context.originalKey()) || !open.submissionId().equals(context.submissionId())
            || open.workRound() != context.workRound() || !verify.requestSha256().equals(context.requestSha256()))
            throw new IllegalArgumentException("VERIFY_RECEIPT_INPUT");
        var at = recordedAt.truncatedTo(ChronoUnit.MILLIS);
        var unsigned = SubmissionJson.object().put("receiptVersion", 1).put("receiptId", receiptId.toString())
            .put("operationKind", "VERIFY").put("originalKey", originalKey.toString()).put("recordedAt", at.toString())
            .put("status", result).put("openOriginalKey", verify.openOriginalKey().toString())
            .put("stopOriginalKey", verify.stopOriginalKey().toString()).put("manifestId", verify.manifestId())
            .put("verificationVersion", verify.verificationVersion()).put("algorithmVersion", verify.algorithmVersion())
            .put("setHash", verify.setHash()).put("evidenceSetId", setId.toString())
            .put("evidenceVerificationId", verificationId.toString()).put("source", "TEMP")
            .put("sourceId", context.authorizationId().toString()).put("submissionId", context.submissionId().toString())
            .put("workRound", context.workRound());
        unsigned.set("originalContext", originalContext(context));
        unsigned.set("capabilities", capabilities());
        var signed = signer.sign(Map.of("iss", E2aProtocol.ISSUER, "aud", E2aProtocol.AUDIENCE, "receipt", unsigned));
        var response = unsigned.deepCopy(); response.put("signedReceipt", signed.token());
        byte[] bytes = bytes(response);
        return new Receipt(receiptId, bytes, ImportFiles.sha256(bytes), signed.keyId(), signed.token(), at);
    }

    private JsonNode unsigned(UUID receiptId,UUID originalKey,SourceEvidenceIntakeOpen.Parsed open,
                              SourceEvidenceIntakeReceipts.OpenInsert context,Instant recordedAt) {
        var node=SubmissionJson.object().put("receiptVersion",1).put("receiptId",receiptId.toString())
            .put("operationKind","OPEN").put("originalKey",originalKey.toString()).put("recordedAt",recordedAt.toString())
            .put("status","RECEIVED").put("manifestId",open.manifestId()).put("manifestVersion",open.manifestVersion())
            .put("snapshotId",open.snapshotId()).put("source", "TEMP").put("sourceId",open.sourceId())
            .put("submissionId",open.submissionId().toString()).put("workRound",open.workRound());
        node.set("originalContext",originalContext(context));
        node.set("capabilities",capabilities());return node;
    }
    private ObjectNode originalContext(SourceEvidenceIntakeReceipts.OpenInsert context) {
        return SubmissionJson.object().put("principalId",context.principalId().toString())
            .put("warehouseId",context.warehouseId().toString()).put("scopeId",context.scopeId().toString())
            .put("creatorDeviceId",context.creatorDeviceId().toString()).put("clientOrderId",context.clientOrderId().toString())
            .put("temporaryAuthorizationId",context.authorizationId().toString())
            .put("originalOperatorId",context.originalOperatorId().toString())
            .put("sourceRecoveryEpoch",context.sourceRecoveryEpoch()).put("currentSessionId",context.currentSessionId().toString())
            .put("userAuthVersion",context.userAuthVersion()).put("deviceVersion",context.deviceVersion());
    }
    public static JsonNode capabilities() { return SubmissionJson.object().put("writeConfirmationAvailable",false)
        .put("releaseAllowed",false).put("mayResumeScanning",false).put("stockReleased",false).put("localDeletionAllowed",false); }
    static byte[] bytes(JsonNode node) { return E2aProtocol.canonical(node.toString().getBytes(StandardCharsets.UTF_8)); }
    public record Receipt(UUID id,byte[] originalResponseBytes,String originalResponseSha256,String signatureKeyId,
                          String signedReceipt,Instant recordedAt) {
        public Receipt { originalResponseBytes=originalResponseBytes.clone(); }
        @Override public byte[] originalResponseBytes() { return originalResponseBytes.clone(); }
    }
}
