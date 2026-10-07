package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.ServerState;
import com.acrylic.outbound.devices.DeviceStore;
import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.pda.PdaSessions;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.command.CommandContext;
import com.acrylic.outbound.temporary.command.CommandIdentity;
import com.acrylic.outbound.temporary.command.CommandLocks;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionCallback;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SourceEvidenceIntakeStopServiceTest {
    private final UUID warehouse = UUID.randomUUID(), operator = UUID.randomUUID(), device = UUID.randomUUID();
    private final UUID submission = UUID.randomUUID(), scope = UUID.randomUUID(), authorization = UUID.randomUUID();
    private final UUID openKey = UUID.randomUUID(), stopKey = UUID.randomUUID(), clientOrder = UUID.randomUUID();
    @TempDir Path directory;
    private final SourceEvidenceIntakeIdentity identity = mock(SourceEvidenceIntakeIdentity.class);
    private final SourceEvidenceIntakeTransactions transactions = mock(SourceEvidenceIntakeTransactions.class);
    private final SourceEvidenceIntakeReceipts opens = mock(SourceEvidenceIntakeReceipts.class);
    private final SourceEvidenceIntakeStopReceipts stops = mock(SourceEvidenceIntakeStopReceipts.class);
    private final SourceEvidenceIntakeRecovery recovery = mock(SourceEvidenceIntakeRecovery.class);
    private final SourceEvidenceIntakeReceiptCodec codec = mock(SourceEvidenceIntakeReceiptCodec.class);
    private final SourceEvidenceIntakeFacts facts = mock(SourceEvidenceIntakeFacts.class);
    private final CommandLocks locks = mock(CommandLocks.class);
    private final SourceEvidenceIntakeEvidenceLocks evidenceLocks = mock(SourceEvidenceIntakeEvidenceLocks.class);
    private final SourceEvidenceIntakeStopObjects objects = mock(SourceEvidenceIntakeStopObjects.class);
    private final DeviceStore devices = mock(DeviceStore.class);
    private final SourceEvidenceIntakeDeviceKeys deviceKeys = mock(SourceEvidenceIntakeDeviceKeys.class);
    private final ServerState state = mock(ServerState.class);
    private SourceEvidenceIntakeObjectFiles files;
    private CommandIdentity.Actor actor;
    private CommandContext context;
    private SourceEvidenceIntakeOpen.Parsed open;
    private SourceEvidenceIntakeReceiptCodec.Receipt receipt;
    private KeyPair keys;
    private SourceEvidenceIntakeStopService service;

    @BeforeEach void setUp() throws Exception {
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        keys = generator.generateKeyPair();
        var session = new PdaSessions.Principal(UUID.randomUUID(), operator, "operator", device, warehouse, "7", "2");
        actor = new CommandIdentity.Actor(operator, warehouse, "7", session, List.of("pda:recover"),
            List.of(warehouse.toString()));
        var data = SubmissionJson.object().put("scopeId", scope.toString()).put("warehouseId", warehouse.toString())
            .put("creatorDeviceId", device.toString()).put("freezeDeviceId", device.toString())
            .put("clientOrderId", clientOrder.toString()).put("scopeClientOrderId", clientOrder.toString()).put("submissionId", submission.toString())
            .put("currentSubmissionId", submission.toString()).put("workRound", "3").put("scopeRound", "3")
            .put("originalOperatorId", operator.toString()).put("authorizationId", authorization.toString())
            .put("sourceRecoveryEpoch", "4");
        context = new CommandContext(data);
        open = SourceEvidenceIntakeOpen.parse(bytes(SubmissionJson.object().put("operation", "OPEN")
            .put("manifestId", "manifest").put("manifestVersion", 2).put("snapshotId", "snapshot")
            .put("source", "TEMP").put("sourceId", authorization.toString())
            .put("submissionId", submission.toString()).put("workRound", 3)), submission, 3);
        files = spy(new SourceEvidenceIntakeObjectFiles(directory.toString()));
        service = new SourceEvidenceIntakeStopService(identity, transactions, opens, stops, recovery, codec,
            facts, locks, evidenceLocks, objects, files, devices, deviceKeys, state);
        when(identity.current(warehouse)).thenReturn(actor);
        when(facts.read(submission)).thenReturn(context);
        when(opens.openBound(bound(openKey))).thenReturn(open);
        when(stops.existing(any(), any())).thenReturn(decision(SourceEvidenceIntakeReplay.Status.NOT_FOUND, null));
        when(stops.existingLocked(any(), any())).thenReturn(decision(SourceEvidenceIntakeReplay.Status.NOT_FOUND, null));
        when(devices.find(device, false)).thenReturn(new DeviceStore.Device(device, warehouse, "D", "device",
            Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()), "hash", "ACTIVE", "1", "", null, true));
        when(deviceKeys.trusted(warehouse, device)).thenReturn(keys.getPublic());
        byte[] response = "{\"operationKind\":\"STOP\",\"status\":\"PARTIAL\"}".getBytes(StandardCharsets.UTF_8);
        receipt = new SourceEvidenceIntakeReceiptCodec.Receipt(UUID.randomUUID(), response,
            ImportFiles.sha256(response), "kid", "token", Instant.EPOCH);
        when(codec.stop(any(), eq(stopKey), eq(open), any(), any(), any(), any())).thenReturn(receipt);
        when(objects.insert(any(), any(), any(), any())).thenAnswer(call -> {
            var binding = (SourceEvidenceIntakeStopObjects.Binding) call.getArgument(0);
            var eventId = (String) call.getArgument(1);
            var stored = (SourceEvidenceIntakeObjectFiles.Stored) call.getArgument(2);
            return new SourceEvidenceIntakeStopObjects.StopObject(UUID.randomUUID(),
                SourceEvidenceIntakeStopObjects.originalId(binding.openKey(), eventId), stored);
        });
        doAnswer(call -> ((TransactionCallback<?>) call.getArgument(0)).doInTransaction(null))
            .when(transactions).read(any());
        doAnswer(call -> ((TransactionCallback<?>) call.getArgument(0)).doInTransaction(null))
            .when(transactions).write(any());
    }

    @Test void acceptedStopStoresProofBeforeTransactionalObjectAndPartialReceipt() throws Exception {
        assertThat(service.stop(request())).containsExactly(receipt.originalResponseBytes());
        var order = inOrder(files, transactions, locks, evidenceLocks, objects, codec, stops);
        order.verify(files).store(any(), any(Integer.class), any());
        order.verify(transactions).write(any());
        order.verify(locks).identity(actor, context);
        order.verify(evidenceLocks).source(context);
        order.verify(objects).insert(any(), any(), any(), any());
        order.verify(stops).insert(any(), any(), eq(receipt));
    }

    @Test void originalKeyReplayReturnsOriginalResponseWithoutWritingFile() throws Exception {
        when(stops.existing(any(), any())).thenReturn(decision(SourceEvidenceIntakeReplay.Status.REPLAY,
            receipt.originalResponseBytes()));
        assertThat(service.stop(request())).containsExactly(receipt.originalResponseBytes());
        verify(transactions, never()).write(any());
        verify(objects, never()).insert(any(), any(), any(), any());
        verify(opens, never()).openBound(any());
    }

    @Test void originalKeyCollisionRechecksAfterFileIsDurable() throws Exception {
        var collision = new DuplicateKeyException("duplicate", new SQLException(
            "Duplicate entry for key 'uq_evidence_intake_original'", "23000", 1062));
        doThrow(collision).when(stops).insert(any(), any(), any());
        when(recovery.originalKeyCollision(collision)).thenReturn(true);
        when(stops.recheckAfterRollback(any(), any())).thenReturn(decision(
            SourceEvidenceIntakeReplay.Status.REPLAY, receipt.originalResponseBytes()));
        assertThat(service.stop(request())).containsExactly(receipt.originalResponseBytes());
        var saved = ArgumentCaptor.forClass(SourceEvidenceIntakeObjectFiles.Stored.class);
        verify(objects).insert(any(), any(), saved.capture(), any());
        files.verify(saved.getValue());
        verify(stops).recheckAfterRollback(eq(bound(stopKey)), any());
    }

    @Test void changedDeviceKeyRejectsBeforeAnyNewObjectOrReceipt() throws Exception {
        var other = KeyPairGenerator.getInstance("EC");
        other.initialize(new ECGenParameterSpec("secp256r1"));
        when(devices.find(device, false)).thenReturn(new DeviceStore.Device(device, warehouse, "D", "device",
            Base64.getEncoder().encodeToString(other.generateKeyPair().getPublic().getEncoded()),
            "hash", "ACTIVE", "2", "", null, true));

        assertThatThrownBy(() -> service.stop(request())).isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.code()).isEqualTo("E2A_STOP_DEVICE_KEY_CHANGED"));
        verify(transactions, never()).write(any());
        verify(objects, never()).insert(any(), any(), any(), any());
    }

    @Test void untrustedServerKeyRejectsBeforeAnyNewObjectOrReceipt() {
        when(deviceKeys.trusted(warehouse, device))
            .thenThrow(new BusinessException(409, "E2A_STOP_KEY_UNVERIFIABLE", "untrusted"));

        assertThatThrownBy(() -> service.stop(request())).isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.code()).isEqualTo("E2A_STOP_KEY_UNVERIFIABLE"));
        verify(transactions, never()).write(any());
        verify(objects, never()).insert(any(), any(), any(), any());
    }

    @Test void revokedRecoveryPermissionRejectsBeforeStoringProof() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(403, "E2A_RECOVERY_PERMISSION_DENIED", "revoked"))
            .when(identity).requireRecovery(actor);

        assertThatThrownBy(() -> service.stop(request())).isInstanceOfSatisfying(BusinessException.class,
            error -> assertThat(error.code()).isEqualTo("E2A_RECOVERY_PERMISSION_DENIED"));
        verify(transactions, never()).write(any());
        verify(objects, never()).insert(any(), any(), any(), any());
    }

    private SourceEvidenceIntakeStopService.StopRequest request() {
        var page = SubmissionJson.object().put("manifestId", "manifest").put("manifestVersion", 2)
            .put("snapshotId", "snapshot").put("pageNo", 1).put("pageCount", 1).put("isFinal", true)
            .put("itemCount", 0).put("byteCount", 0).putNull("prevPageHash");
        page.putArray("items");
        var manifest = SubmissionJson.object().put("schemaVersion", 1).put("manifestId", "manifest")
            .put("manifestVersion", 2).put("snapshotId", "snapshot").put("source", "TEMP")
            .put("sourceId", authorization.toString()).put("submissionId", submission.toString())
            .put("workRound", 3).put("pageCount", 1).put("entryCount", 0).put("byteCount", 0).put("endPageNo", 1);
        manifest.putArray("pages").add(SubmissionJson.object().put("pageNo", 1)
            .put("pageHash", E2aProtocol.pageHash(page)));
        var hashes = E2aProtocol.manifestHashes(manifest);
        var proof = SubmissionJson.object().put("warehouseId", warehouse.toString())
            .put("sourceId", authorization.toString()).put("submissionId", submission.toString())
            .put("workRound", 3).put("originalOperatorId", operator.toString())
            .put("freezeDeviceId", device.toString()).put("sourceRecoveryEpoch", 4)
            .put("stopEventId", "stop-event").put("captureHighWatermark", 0).put("auditThroughSeq", 1)
            .put("stopTransactionVersion", 1).put("manifestId", "manifest").put("manifestVersion", 2)
            .put("manifestBytesHash", hashes.manifestBytesHash()).put("setHash", hashes.setHash());
        var body = SubmissionJson.object().put("operation", "STOP").put("openOriginalKey", openKey.toString())
            .put("manifestBytesHash", hashes.manifestBytesHash()).put("setHash", hashes.setHash());
        body.set("manifestCore", manifest);
        body.set("stopProof", E2aProtocol.signStop(proof, keys.getPrivate(),
            ImportFiles.sha256(keys.getPublic().getEncoded())));
        return new SourceEvidenceIntakeStopService.StopRequest(warehouse, stopKey, openKey, submission, 3,
            "4", bytes(body));
    }

    private SourceEvidenceIntakeReceipts.BoundKey bound(UUID originalKey) {
        return new SourceEvidenceIntakeReceipts.BoundKey(operator, originalKey, warehouse, submission, 3,
            device, authorization);
    }

    private static byte[] bytes(ObjectNode node) { return node.toString().getBytes(StandardCharsets.UTF_8); }
    private static SourceEvidenceIntakeReplay.Decision decision(SourceEvidenceIntakeReplay.Status status,
        byte[] response) {
        return new SourceEvidenceIntakeReplay.Decision(status, response,
            SourceEvidenceIntakeReplay.TrustStatus.NOT_EVALUATED);
    }
}
