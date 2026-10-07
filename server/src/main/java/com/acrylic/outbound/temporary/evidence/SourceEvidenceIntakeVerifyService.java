package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.common.ServerState;
import com.acrylic.outbound.devices.DeviceStore;
import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.command.CommandContext;
import com.acrylic.outbound.temporary.command.CommandIdentity;
import com.acrylic.outbound.temporary.command.CommandLocks;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class SourceEvidenceIntakeVerifyService {
    private final SourceEvidenceIntakeIdentity identity; private final SourceEvidenceIntakeTransactions transactions;
    private final SourceEvidenceIntakeReceipts opens; private final SourceEvidenceIntakeVerifyReceipts receipts;
    private final SourceEvidenceIntakeStopLookup stops; private final SourceEvidenceIntakeStopReceipts stopReceipts;
    private final SourceEvidenceIntakeRecovery recovery; private final SourceEvidenceIntakeReceiptCodec codec;
    private final SourceEvidenceIntakeFacts facts; private final CommandLocks locks;
    private final SourceEvidenceIntakeEvidenceLocks evidenceLocks; private final SourceEvidenceIntakeSetWriter sets;
    private final SourceEvidenceIntakeVerificationStore verifications; private final SourceEvidenceIntakeVerifyPreflight preflight;
    private final SourceEvidenceIntakeObjectFiles files; private final DeviceStore devices;
    private final SourceEvidenceIntakeDeviceKeys deviceKeys; private final ServerState serverState;

    public SourceEvidenceIntakeVerifyService(SourceEvidenceIntakeIdentity identity, SourceEvidenceIntakeTransactions transactions,
        SourceEvidenceIntakeReceipts opens, SourceEvidenceIntakeVerifyReceipts receipts, SourceEvidenceIntakeStopLookup stops,
        SourceEvidenceIntakeStopReceipts stopReceipts, SourceEvidenceIntakeRecovery recovery, SourceEvidenceIntakeReceiptCodec codec,
        SourceEvidenceIntakeFacts facts, CommandLocks locks, SourceEvidenceIntakeEvidenceLocks evidenceLocks,
        SourceEvidenceIntakeSetWriter sets, SourceEvidenceIntakeVerificationStore verifications,
        SourceEvidenceIntakeVerifyPreflight preflight, SourceEvidenceIntakeObjectFiles files, DeviceStore devices,
        SourceEvidenceIntakeDeviceKeys deviceKeys, ServerState serverState) {
        this.identity = identity; this.transactions = transactions; this.opens = opens; this.receipts = receipts;
        this.stops = stops; this.stopReceipts = stopReceipts; this.recovery = recovery; this.codec = codec;
        this.facts = facts; this.locks = locks; this.evidenceLocks = evidenceLocks; this.sets = sets;
        this.verifications = verifications; this.preflight = preflight; this.files = files; this.devices = devices;
        this.deviceKeys = deviceKeys; this.serverState = serverState;
    }

    public byte[] verify(VerifyRequest request) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("INTAKE_MUST_OWN_TRANSACTION_BOUNDARY");
        var actor = identity.current(request.warehouseId()); var hint = facts.read(request.submissionId());
        var key = bound(request.originalKey(), request, actor, hint);
        var first = transactions.read(status -> receipts.existing(key, request.requestBytes()));
        if (first.status() != SourceEvidenceIntakeReplay.Status.NOT_FOUND) return result(first);
        var openKey = bound(request.openOriginalKey(), request, actor, hint);
        var open = transactions.read(status -> opens.openBound(openKey));
        var stopKey = bound(request.stopOriginalKey(), request, actor, hint);
        var stopRecord = transactions.read(status -> stops.read(stopKey));
        var expected = transactions.read(status -> expected(request, actor, hint, open));
        var stop = parseStop(stopRecord, expected);
        var parsed = SourceEvidenceIntakeVerify.parse(request.requestBytes(), request.openOriginalKey(),
            request.stopOriginalKey(), open, stop);
        identity.requireRecovery(actor); serverState.requireWritable(request.transportRecoveryEpoch());
        try { return transactions.write(status -> write(request, actor, hint, key, openKey, stopKey, open, stopRecord, expected, stop, parsed)); }
        catch (DuplicateKeyException collision) {
            if (!recovery.originalKeyCollision(collision)) throw collision;
            var current = bound(request.originalKey(), request, identity.current(request.warehouseId()), facts.read(request.submissionId()));
            return result(receipts.recheckAfterRollback(current, parsed.canonicalBytes()));
        }
    }

    private byte[] write(VerifyRequest request, CommandIdentity.Actor initial, CommandContext hint,
        SourceEvidenceIntakeReceipts.BoundKey key, SourceEvidenceIntakeReceipts.BoundKey openKey,
        SourceEvidenceIntakeReceipts.BoundKey stopKey, SourceEvidenceIntakeOpen.Parsed originalOpen,
        SourceEvidenceIntakeStopLookup.StopRecord originalRecord, SourceEvidenceIntakeStop.Expected expected,
        SourceEvidenceIntakeStop.Parsed originalStop, SourceEvidenceIntakeVerify.Parsed originalVerify) {
        locks.identity(initial, hint); locks.source(hint); var context = facts.read(request.submissionId()); locks.sameHint(hint, context);
        var actor = identity.current(request.warehouseId()); require(actor.id().equals(initial.id()), "E2A_SOURCE_CONTEXT_CHANGED");
        require(bound(request.originalKey(), request, actor, context).equals(key), "E2A_SOURCE_CONTEXT_CHANGED");
        evidenceLocks.source(context); var second = receipts.existingLocked(key, originalVerify.canonicalBytes());
        if (second.status() != SourceEvidenceIntakeReplay.Status.NOT_FOUND) return result(second);
        var open = opens.openBoundLocked(openKey); require(Arrays.equals(open.canonicalBytes(), originalOpen.canonicalBytes()), "E2A_OPEN_CHANGED");
        var record = stops.locked(stopKey); var stop = parseStop(record, expected);
        require(Arrays.equals(stop.canonicalBytes(), originalStop.canonicalBytes()), "E2A_STOP_CHANGED");
        var verify = SourceEvidenceIntakeVerify.parse(originalVerify.canonicalBytes(), request.openOriginalKey(), request.stopOriginalKey(), open, stop);
        var pageBinding = pageBinding(request, actor, context);
        var stopBinding = new SourceEvidenceIntakeStopObjects.Binding(request.warehouseId(), context.id("scopeId"),
            request.submissionId(), request.workRound(), context.id("freezeDeviceId"), actor.id(), request.openOriginalKey());
        preflight.requireStoredRoots(record.requestBytes(), expected, stopKey, stopBinding, pageBinding);
        var link = sets.createIfComplete(stop, pageBinding, record.objectId());
        if (link == null) throw conflict("E2A_PAGE_INCOMPLETE");
        serverState.requireWritable(request.transportRecoveryEpoch()); identity.requireRecovery(actor);
        byte[] factsBytes = E2aProtocol.canonical(context.json().toString().getBytes(StandardCharsets.UTF_8));
        byte[] findings = findings(stop, link.itemCount());
        UUID verificationId = verifications.insert(pageBinding, link.id(), verify.setHash(), verify.verificationVersion(),
            verify.algorithmVersion(), "VERIFIED_COMPLETE_AT_STOP", factsBytes, findings, Instant.now());
        var insert = insert(context, actor, verify, request.originalKey());
        var receipt = codec.verify(Ids.next(), request.originalKey(), open, verify, insert, link.id(), verificationId,
            "VERIFIED_COMPLETE_AT_STOP", Instant.now());
        receipts.insert(insert, link.id(), verificationId, receipt); return receipt.originalResponseBytes();
    }

    private SourceEvidenceIntakeStop.Parsed parseStop(SourceEvidenceIntakeStopLookup.StopRecord record,
        SourceEvidenceIntakeStop.Expected expected) {
        try { return SourceEvidenceIntakeStop.parse(record.requestBytes(), expected); }
        catch (RuntimeException invalid) { throw conflict("E2A_STOP_PROOF_UNVERIFIABLE"); }
    }

    private SourceEvidenceIntakeStop.Expected expected(VerifyRequest request, CommandIdentity.Actor actor,
        CommandContext context, SourceEvidenceIntakeOpen.Parsed open) {
        var device = devices.find(context.id("freezeDeviceId"), false); device.requireActive();
        var trusted = deviceKeys.trusted(request.warehouseId(), context.id("freezeDeviceId"));
        require(device.id().equals(context.id("freezeDeviceId")) && device.warehouseId().equals(request.warehouseId())
            && Base64.getEncoder().encodeToString(trusted.getEncoded()).equals(device.publicKey()), "E2A_STOP_DEVICE_KEY_CHANGED");
        return new SourceEvidenceIntakeStop.Expected(request.openOriginalKey(), open, request.warehouseId(), actor.id(),
            context.id("freezeDeviceId"), Long.parseLong(context.text("sourceRecoveryEpoch")), trusted);
    }

    private SourceEvidenceIntakePageObjects.Binding pageBinding(VerifyRequest request, CommandIdentity.Actor actor, CommandContext context) {
        return new SourceEvidenceIntakePageObjects.Binding(request.warehouseId(), context.id("scopeId"), request.submissionId(),
            request.workRound(), context.id("freezeDeviceId"), actor.id(), request.openOriginalKey());
    }

    private SourceEvidenceIntakeReceipts.BoundKey bound(UUID originalKey, VerifyRequest request,
        CommandIdentity.Actor actor, CommandContext context) {
        require(actor.id().equals(context.id("originalOperatorId")) && actor.warehouse().equals(context.id("warehouseId"))
            && actor.pda().deviceId().equals(context.id("creatorDeviceId")), "E2A_SOURCE_NOT_OWNER");
        return new SourceEvidenceIntakeReceipts.BoundKey(actor.id(), originalKey, request.warehouseId(), request.submissionId(),
            request.workRound(), context.id("creatorDeviceId"), context.id("authorizationId"));
    }

    private SourceEvidenceIntakeReceipts.OpenInsert insert(CommandContext context, CommandIdentity.Actor actor,
        SourceEvidenceIntakeVerify.Parsed verify, UUID key) {
        return new SourceEvidenceIntakeReceipts.OpenInsert(actor.id(), key, context.id("warehouseId"), context.id("scopeId"),
            context.id("submissionId"), context.version("workRound"), context.id("creatorDeviceId"), context.id("clientOrderId"),
            context.id("authorizationId"), context.id("originalOperatorId"), Long.parseLong(context.text("sourceRecoveryEpoch")),
            actor.pda().sessionId(), actor.authVersion(), actor.pda().deviceVersion(), verify.canonicalBytes(), verify.requestSha256(), context.id("scopeClientOrderId"));
    }

    private byte[] findings(SourceEvidenceIntakeStop.Parsed stop, long items) {
        var proof = SubmissionJson.parse(stop.canonicalStopProofBytes()).path("core");
        ObjectNode node = SubmissionJson.object().put("result", "VERIFIED_COMPLETE_AT_STOP")
            .put("reason", "SERVER_ROOTS_RECHECKED")
            .put("captureHighWatermark", proof.path("captureHighWatermark").longValue())
            .put("auditThroughSeq", proof.path("auditThroughSeq").longValue()).put("itemCount", items);
        return E2aProtocol.canonical(node.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] result(SourceEvidenceIntakeReplay.Decision decision) {
        return switch (decision.status()) {
            case REPLAY -> decision.originalResponseBytes();
            case CONFLICT -> throw conflict("E2A_INTAKE_CONFLICT");
            case INTEGRITY_FAILURE -> throw conflict("E2A_INTAKE_INTEGRITY_FAILURE");
            case UNKNOWN, NOT_FOUND -> throw conflict("E2A_INTAKE_RESULT_UNKNOWN");
        };
    }

    private static void require(boolean valid, String code) { if (!valid) throw conflict(code); }
    private static BusinessException conflict(String code) { return new BusinessException(409, code, "原机证据验证失败"); }

    public record VerifyRequest(UUID warehouseId, UUID originalKey, UUID openOriginalKey, UUID stopOriginalKey,
        UUID submissionId, long workRound, String transportRecoveryEpoch, byte[] requestBytes) {
        public VerifyRequest {
            if (warehouseId == null || originalKey == null || openOriginalKey == null || stopOriginalKey == null
                || originalKey.equals(openOriginalKey) || originalKey.equals(stopOriginalKey) || submissionId == null
                || workRound < 1 || transportRecoveryEpoch == null || !transportRecoveryEpoch.matches("0|[1-9][0-9]{0,18}")
                || requestBytes == null) throw new IllegalArgumentException("VERIFY_REQUEST");
            requestBytes = requestBytes.clone();
        }
        @Override public byte[] requestBytes() { return requestBytes.clone(); }
    }
}
