package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.JsonText;
import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.command.CommandContext;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** JDBC is the storage boundary; actual codec, signature, read-integrity and replay logic run here. */
class SourceEvidenceIntakeVerifyReceiptTest {
    @TempDir Path directory;
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final Map<String,Object> row = new HashMap<>();
    private final UUID submission = UUID.randomUUID(), authorization = UUID.randomUUID(), key = UUID.randomUUID();
    private final UUID setId = UUID.randomUUID(), verificationId = UUID.randomUUID();
    private final byte[] request = E2aProtocol.canonical("{\"operation\":\"VERIFY\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private OfflineSigner signer;
    private SourceEvidenceIntakeReceipts.OpenInsert context;

    @BeforeEach void setup() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        var pair = generator.generateKeyPair();
        Files.write(directory.resolve("private.pk8"), pair.getPrivate().getEncoded());
        Files.write(directory.resolve("public.der"), pair.getPublic().getEncoded());
        signer = new OfflineSigner(directory.resolve("private.pk8").toString(), directory.resolve("public.der").toString(),
            new JsonText(new ObjectMapper()));
        var actor = UUID.randomUUID();
        context = new SourceEvidenceIntakeReceipts.OpenInsert(actor,key,UUID.randomUUID(),UUID.randomUUID(),
            submission,2,UUID.randomUUID(),UUID.randomUUID(),authorization,actor,1,UUID.randomUUID(),"1","1",
            request,E2aProtocol.intakeSha256(request));
        when(jdbc.update(anyString(),any(Object[].class))).thenAnswer(invocation -> store((Object[])invocation.getRawArguments()[1]));
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(invocation -> List.of(row));
    }

    @ParameterizedTest @ValueSource(strings={"PARTIAL","VERIFIED_COMPLETE_AT_STOP"})
    void persistedStatusAndBothRecoveryPathsPreserveTheOriginal(String status) {
        var receipt = receipt(status);
        new SourceEvidenceIntakeVerifyReceipts(jdbc).insert(context,setId,verificationId,receipt);
        assertThat(row.get("receipt_status")).isEqualTo(status);
        var read = new SourceEvidenceIntakeReadIntegrity(signer).check(row,source(),key);
        assertThat(read.trustStatus()).isEqualTo("VERIFIED");
        assertThat(read.originalBytes()).containsExactly(receipt.originalResponseBytes());
        assertThat(read.receipt().path("status").asText()).isEqualTo(status);
        var replay = new SourceEvidenceIntakeVerifyReceipts(jdbc).existing(bound(),request);
        assertThat(replay.status()).isEqualTo(SourceEvidenceIntakeReplay.Status.REPLAY);
        assertThat(replay.originalResponseBytes()).containsExactly(receipt.originalResponseBytes());
        assertThat(read.receipt().path("capabilities")).isEqualTo(SourceEvidenceIntakeReceiptCodec.capabilities());
    }

    @ParameterizedTest @ValueSource(strings={"PARTIAL","VERIFIED_COMPLETE_AT_STOP"})
    void databaseStatusCannotRelabelTheSignedOriginal(String status) {
        new SourceEvidenceIntakeVerifyReceipts(jdbc).insert(context,setId,verificationId,receipt(status));
        row.put("receipt_status",status.equals("PARTIAL") ? "VERIFIED_COMPLETE_AT_STOP" : "PARTIAL");
        assertThatThrownBy(() -> new SourceEvidenceIntakeReadIntegrity(signer).check(row,source(),key))
            .isInstanceOf(com.acrylic.outbound.common.BusinessException.class);
        assertThat(new SourceEvidenceIntakeVerifyReceipts(jdbc).existing(bound(),request).status())
            .isEqualTo(SourceEvidenceIntakeReplay.Status.INTEGRITY_FAILURE);
    }

    @ParameterizedTest @ValueSource(strings={"RECEIVED","CONFLICT","UNVERIFIABLE",""})
    void unsupportedVerificationStatusCannotBePersisted(String status) {
        assertThatThrownBy(() -> new SourceEvidenceIntakeVerifyReceipts(jdbc)
            .insert(context,setId,verificationId,receipt(status))).isInstanceOf(IllegalArgumentException.class);
        assertThat(row).isEmpty();
    }

    @Test void missingEvidenceReferenceCannotBeReplayed() {
        new SourceEvidenceIntakeVerifyReceipts(jdbc).insert(context,setId,verificationId,receipt("VERIFIED_COMPLETE_AT_STOP"));
        row.put("evidence_verification_id",null);
        assertThatThrownBy(() -> new SourceEvidenceIntakeReadIntegrity(signer).check(row,source(),key))
            .isInstanceOf(com.acrylic.outbound.common.BusinessException.class);
        assertThat(new SourceEvidenceIntakeVerifyReceipts(jdbc).existing(bound(),request).status())
            .isEqualTo(SourceEvidenceIntakeReplay.Status.INTEGRITY_FAILURE);
    }

    private SourceEvidenceIntakeReceiptCodec.Receipt receipt(String status) {
        var open = SourceEvidenceIntakeOpen.parse(SubmissionJson.canonical(SubmissionJson.object()
            .put("operation","OPEN").put("manifestId","manifest").put("manifestVersion",1).put("snapshotId","snapshot")
            .put("source","TEMP").put("sourceId",authorization.toString()).put("submissionId",submission.toString())
            .put("workRound",2)).getBytes(java.nio.charset.StandardCharsets.UTF_8),submission,2);
        var verify = new SourceEvidenceIntakeVerify.Parsed(request,context.requestSha256(),UUID.randomUUID(),UUID.randomUUID(),
            "manifest",1,1,"a".repeat(64));
        return new SourceEvidenceIntakeReceiptCodec(signer).verify(UUID.randomUUID(),key,open,verify,context,
            setId,verificationId,status,Instant.now());
    }

    private int store(Object[] values) {
        String[] columns = ("id,receipt_version,principal_id,route_scope,original_key,current_session_id,warehouse_id,"
            +"scope_id,submission_id,work_round,creator_device_id,client_order_id,temporary_authorization_id,"
            +"original_operator_id,source_recovery_epoch,operation_kind,receipt_status,canonical_request_bytes,"
            +"request_sha256,original_response_bytes,original_response_sha256,signature_key_id,signed_receipt,"
            +"evidence_set_id,evidence_verification_id,recorded_at").split(",");
        assertThat(values).hasSize(columns.length);
        for(int i=0;i<columns.length;i++) row.put(columns[i],values[i]);
        row.put("object_id",null);
        return 1;
    }

    private CommandContext source() {
        return new CommandContext(SubmissionJson.object().put("warehouseId",context.warehouseId().toString())
            .put("scopeId",context.scopeId().toString()).put("submissionId",submission.toString()).put("workRound","2")
            .put("creatorDeviceId",context.creatorDeviceId().toString()).put("clientOrderId",context.clientOrderId().toString())
            .put("scopeClientOrderId",context.scopeClientOrderId().toString())
            .put("authorizationId",authorization.toString()).put("originalOperatorId",context.originalOperatorId().toString())
            .put("sourceRecoveryEpoch","1"));
    }

    private SourceEvidenceIntakeReceipts.BoundKey bound() {
        return new SourceEvidenceIntakeReceipts.BoundKey(context.principalId(),key,context.warehouseId(),submission,
            2,context.creatorDeviceId(),authorization);
    }
}
