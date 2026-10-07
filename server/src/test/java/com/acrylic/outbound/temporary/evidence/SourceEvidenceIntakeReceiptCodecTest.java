package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.JsonText;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.acrylic.outbound.temporary.command.CommandContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SourceEvidenceIntakeReceiptCodecTest {
    private static final UUID SUBMISSION=UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID RECEIPT=UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID ORIGINAL=UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
    private static final UUID AUTHORIZATION=UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd");
    private static final String REQUEST="""
        {"operation":"OPEN","manifestId":"manifest-1","manifestVersion":7,"snapshotId":"snapshot-1",
         "source":"TEMP","sourceId":"dddddddd-dddd-4ddd-8ddd-dddddddddddd","submissionId":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","workRound":2}
        """;
    @TempDir Path directory;
    private OfflineSigner signer;
    private SourceEvidenceIntakeReceiptCodec codec;

    @BeforeEach void setUp() throws Exception {
        var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);var pair=generator.generateKeyPair();
        Files.write(directory.resolve("private.pk8"),pair.getPrivate().getEncoded());
        Files.write(directory.resolve("public.der"),pair.getPublic().getEncoded());
        signer=new OfflineSigner(directory.resolve("private.pk8").toString(),directory.resolve("public.der").toString(),
            new JsonText(new ObjectMapper()));
        codec=new SourceEvidenceIntakeReceiptCodec(signer);
    }

    @Test void openReceiptContainsClosedCapabilitiesAndMillisecondTimestamp() {
        var open=SourceEvidenceIntakeOpen.parse(REQUEST.getBytes(StandardCharsets.UTF_8),SUBMISSION,2);
        var receipt=codec.open(RECEIPT,ORIGINAL,open,context(open),Instant.parse("2026-09-23T12:34:56.123456789Z"));
        JsonNode response=SubmissionJson.parse(receipt.originalResponseBytes());

        assertThat(response.path("receiptVersion").intValue()).isEqualTo(1);
        assertThat(response.path("operationKind").textValue()).isEqualTo("OPEN");
        assertThat(response.path("originalKey").textValue()).isEqualTo(ORIGINAL.toString());
        assertThat(response.path("receiptId").textValue()).isEqualTo(RECEIPT.toString());
        assertThat(response.path("recordedAt").textValue()).isEqualTo("2026-09-23T12:34:56.123Z");
        assertThat(response.path("status").textValue()).isEqualTo("RECEIVED");
        assertThat(response.path("originalContext").path("temporaryAuthorizationId").textValue()).isEqualTo(AUTHORIZATION.toString());
        assertThat(response.path("originalContext").path("userAuthVersion").textValue()).isEqualTo("7");
        assertThat(response.path("signedReceipt").textValue()).isEqualTo(receipt.signedReceipt());
        assertThat(receipt.originalResponseSha256()).isEqualTo(ImportFiles.sha256(receipt.originalResponseBytes()));
        for(String field: new String[]{"writeConfirmationAvailable","releaseAllowed","mayResumeScanning","stockReleased","localDeletionAllowed"})
            assertThat(response.path("capabilities").path(field).booleanValue()).isFalse();
    }

    @Test void signedClaimsCoverExactlyTheUnsignedOpenReceipt() {
        var open=SourceEvidenceIntakeOpen.parse(REQUEST.getBytes(StandardCharsets.UTF_8),SUBMISSION,2);
        var receipt=codec.open(RECEIPT,ORIGINAL,open,context(open),Instant.parse("2026-09-23T12:34:56.123456789Z"));
        JsonNode response=SubmissionJson.parse(receipt.originalResponseBytes());
        var unsigned=((com.fasterxml.jackson.databind.node.ObjectNode)response).deepCopy();unsigned.remove("signedReceipt");
        String[] parts=receipt.signedReceipt().split("\\.",-1);
        JsonNode claims=SubmissionJson.parse(Base64.getUrlDecoder().decode(parts[1]));

        assertThat(E2aProtocol.verifyRsaReceipt(response,signer.publicKey())).isEqualTo("VERIFIED");
        assertThat(claims.path("iss").textValue()).isEqualTo(E2aProtocol.ISSUER);
        assertThat(claims.path("aud").textValue()).isEqualTo(E2aProtocol.AUDIENCE);
        assertThat(claims.path("receipt")).isEqualTo(unsigned);
    }

    @Test void readIntegrityReturnsOriginalBytesOnlyAfterCheckingSourceAndSignature() {
        var open=SourceEvidenceIntakeOpen.parse(REQUEST.getBytes(StandardCharsets.UTF_8),SUBMISSION,2);
        var input=context(open);
        var receipt=codec.open(RECEIPT,ORIGINAL,open,input,Instant.now());

        var checked=new SourceEvidenceIntakeReadIntegrity(signer).check(row(input,receipt),source(input),ORIGINAL);

        assertThat(checked.trustStatus()).isEqualTo("VERIFIED");
        assertThat(checked.originalBytes()).isEqualTo(receipt.originalResponseBytes());
    }

    @Test void readIntegrityRejectsChangedSourceAndResponseBytes() {
        var open=SourceEvidenceIntakeOpen.parse(REQUEST.getBytes(StandardCharsets.UTF_8),SUBMISSION,2);
        var input=context(open);
        var receipt=codec.open(RECEIPT,ORIGINAL,open,input,Instant.now());
        var row=row(input,receipt);
        row.put("original_response_sha256","0".repeat(64));

        assertThatThrownBy(() -> new SourceEvidenceIntakeReadIntegrity(signer).check(row,source(input),ORIGINAL))
            .isInstanceOfSatisfying(com.acrylic.outbound.common.BusinessException.class,
                error -> assertThat(error.code()).isEqualTo("E2A_RECEIPT_INTEGRITY_FAILURE"));

        row.put("original_response_sha256",receipt.originalResponseSha256());
        row.put("scope_id",Ids.bytes(UUID.randomUUID()));
        assertThatThrownBy(() -> new SourceEvidenceIntakeReadIntegrity(signer).check(row,source(input),ORIGINAL))
            .isInstanceOfSatisfying(com.acrylic.outbound.common.BusinessException.class,
                error -> assertThat(error.code()).isEqualTo("E2A_RECEIPT_INTEGRITY_FAILURE"));
    }

    @Test void missingHistoricalKeyIsExplicitlyUnverifiable() {
        var open=SourceEvidenceIntakeOpen.parse(REQUEST.getBytes(StandardCharsets.UTF_8),SUBMISSION,2);
        var input=context(open);
        var receipt=codec.open(RECEIPT,ORIGINAL,open,input,Instant.now());
        var rotated=mock(OfflineSigner.class);
        when(rotated.publicKey()).thenReturn(new OfflineSigner.PublicKeyView(
            "offline-other", "RS256", signer.publicKey().publicKey()));

        var checked=new SourceEvidenceIntakeReadIntegrity(rotated).check(row(input,receipt),source(input),ORIGINAL);

        assertThat(checked.trustStatus()).isEqualTo("UNVERIFIABLE_KEY");
    }

    @Test void currentKeyWithInvalidSignatureIsNotAReadableReceipt() {
        var open=SourceEvidenceIntakeOpen.parse(REQUEST.getBytes(StandardCharsets.UTF_8),SUBMISSION,2);
        var input=context(open);
        var receipt=codec.open(RECEIPT,ORIGINAL,open,input,Instant.now());
        var response=(com.fasterxml.jackson.databind.node.ObjectNode)SubmissionJson.parse(receipt.originalResponseBytes());
        String token=receipt.signedReceipt();
        String changed=token.substring(0,token.length()-1)+(token.endsWith("A")?"B":"A");
        response.put("signedReceipt",changed);
        byte[] bytes=SubmissionJson.canonical(response).getBytes(StandardCharsets.UTF_8);
        var row=row(input,receipt);
        row.put("original_response_bytes",bytes);
        row.put("original_response_sha256",ImportFiles.sha256(bytes));
        row.put("signed_receipt",changed);

        assertThatThrownBy(() -> new SourceEvidenceIntakeReadIntegrity(signer).check(row,source(input),ORIGINAL))
            .isInstanceOfSatisfying(com.acrylic.outbound.common.BusinessException.class,
                error -> assertThat(error.code()).isEqualTo("E2A_RECEIPT_INTEGRITY_FAILURE"));
    }

    private Map<String,Object> row(SourceEvidenceIntakeReceipts.OpenInsert input,
        SourceEvidenceIntakeReceiptCodec.Receipt receipt) {
        var row=new HashMap<String,Object>();
        row.put("id",Ids.bytes(receipt.id()));row.put("receipt_version",1);
        row.put("principal_id",Ids.bytes(input.principalId()));row.put("route_scope",E2aProtocol.ROUTE_SCOPE);
        row.put("original_key",Ids.bytes(input.originalKey()));row.put("warehouse_id",Ids.bytes(input.warehouseId()));
        row.put("scope_id",Ids.bytes(input.scopeId()));row.put("submission_id",Ids.bytes(input.submissionId()));
        row.put("work_round",input.workRound());row.put("creator_device_id",Ids.bytes(input.creatorDeviceId()));
        row.put("client_order_id",Ids.bytes(input.clientOrderId()));
        row.put("temporary_authorization_id",Ids.bytes(input.authorizationId()));
        row.put("original_operator_id",Ids.bytes(input.originalOperatorId()));
        row.put("source_recovery_epoch",input.sourceRecoveryEpoch());row.put("operation_kind","OPEN");
        row.put("receipt_status","RECEIVED");row.put("canonical_request_bytes",input.requestBytes());
        row.put("request_sha256",input.requestSha256());row.put("original_response_bytes",receipt.originalResponseBytes());
        row.put("original_response_sha256",receipt.originalResponseSha256());
        row.put("signature_key_id",receipt.signatureKeyId());row.put("signed_receipt",receipt.signedReceipt());
        row.put("object_id",null);row.put("evidence_set_id",null);row.put("evidence_verification_id",null);
        return row;
    }

    private CommandContext source(SourceEvidenceIntakeReceipts.OpenInsert input) {
        var source=SubmissionJson.object().put("warehouseId",input.warehouseId().toString())
            .put("scopeId",input.scopeId().toString()).put("submissionId",input.submissionId().toString())
            .put("workRound",Long.toString(input.workRound()))
            .put("creatorDeviceId",input.creatorDeviceId().toString())
            .put("clientOrderId",input.clientOrderId().toString()).put("scopeClientOrderId",input.scopeClientOrderId().toString())
            .put("authorizationId",input.authorizationId().toString())
            .put("originalOperatorId",input.originalOperatorId().toString())
            .put("sourceRecoveryEpoch",Long.toString(input.sourceRecoveryEpoch()));
        return new CommandContext(source);
    }

    @Test void mismatchedOriginalKeyCannotBeSigned() {
        var open=SourceEvidenceIntakeOpen.parse(REQUEST.getBytes(StandardCharsets.UTF_8),SUBMISSION,2);

        assertThatThrownBy(() -> codec.open(RECEIPT,UUID.randomUUID(),open,context(open),Instant.EPOCH))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("OPEN_RECEIPT_INPUT");
    }

    private SourceEvidenceIntakeReceipts.OpenInsert context(SourceEvidenceIntakeOpen.Parsed open) {
        UUID principal=UUID.fromString("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee");
        return new SourceEvidenceIntakeReceipts.OpenInsert(principal,ORIGINAL,UUID.randomUUID(),UUID.randomUUID(),
            SUBMISSION,2,UUID.randomUUID(),UUID.randomUUID(),AUTHORIZATION,principal,3,UUID.randomUUID(),"7","1",
            open.canonicalBytes(),open.requestSha256());
    }
}
