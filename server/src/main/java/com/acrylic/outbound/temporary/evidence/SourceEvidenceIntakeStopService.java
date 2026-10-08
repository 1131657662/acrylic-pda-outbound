package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.common.ServerState;
import com.acrylic.outbound.devices.DeviceStore;
import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.temporary.command.CommandContext;
import com.acrylic.outbound.temporary.command.CommandIdentity;
import com.acrylic.outbound.temporary.command.CommandLocks;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class SourceEvidenceIntakeStopService {
    private final SourceEvidenceIntakeIdentity identity;
    private final SourceEvidenceIntakeTransactions transactions;
    private final SourceEvidenceIntakeReceipts opens;
    private final SourceEvidenceIntakeStopReceipts stops;
    private final SourceEvidenceIntakeRecovery recovery;
    private final SourceEvidenceIntakeReceiptCodec codec;
    private final SourceEvidenceIntakeFacts facts;
    private final CommandLocks locks;
    private final SourceEvidenceIntakeEvidenceLocks evidenceLocks;
    private final SourceEvidenceIntakeStopObjects objects;
    private final SourceEvidenceIntakeObjectFiles files;
    private final DeviceStore devices;
    private final SourceEvidenceIntakeDeviceKeys deviceKeys;
    private final ServerState serverState;

    public SourceEvidenceIntakeStopService(SourceEvidenceIntakeIdentity identity,
        SourceEvidenceIntakeTransactions transactions, SourceEvidenceIntakeReceipts opens,
        SourceEvidenceIntakeStopReceipts stops, SourceEvidenceIntakeRecovery recovery,
        SourceEvidenceIntakeReceiptCodec codec, SourceEvidenceIntakeFacts facts, CommandLocks locks,
        SourceEvidenceIntakeEvidenceLocks evidenceLocks, SourceEvidenceIntakeStopObjects objects,
        SourceEvidenceIntakeObjectFiles files, DeviceStore devices,
        SourceEvidenceIntakeDeviceKeys deviceKeys, ServerState serverState) {
        this.identity = identity; this.transactions = transactions; this.opens = opens; this.stops = stops;
        this.recovery = recovery; this.codec = codec; this.facts = facts; this.locks = locks;
        this.evidenceLocks = evidenceLocks; this.objects = objects; this.files = files;
        this.devices = devices; this.deviceKeys = deviceKeys; this.serverState = serverState;
    }

    public byte[] stop(StopRequest request) {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("INTAKE_MUST_OWN_TRANSACTION_BOUNDARY");
        var actor = identity.current(request.warehouseId());
        var hint = facts.read(request.submissionId());
        var key = bound(request.originalKey(), request, actor, hint);
        var first = transactions.read(status -> stops.existing(key, request.requestBytes()));
        if (first.status() != SourceEvidenceIntakeReplay.Status.NOT_FOUND) return result(first);
        var openKey = bound(request.openOriginalKey(), request, actor, hint);
        var open = transactions.read(status -> opens.openBound(openKey));
        validate(request, actor, hint, open);
        var parsed = transactions.read(status -> parse(request, actor, hint, open));
        identity.requireRecovery(actor);
        serverState.requireWritable(request.transportRecoveryEpoch());
        SourceEvidenceIntakeObjectFiles.Stored stored;
        byte[] proof = parsed.canonicalStopProofBytes();
        try { stored = files.store(new ByteArrayInputStream(proof), proof.length, ImportFiles.sha256(proof)); }
        catch (IOException failure) { throw unavailable(); }
        try {
            return transactions.write(status -> write(request, actor, hint, key, openKey, open, parsed, stored));
        } catch (DuplicateKeyException collision) {
            if (!recovery.originalKeyCollision(collision)) throw collision;
            var current = bound(request.originalKey(), request, identity.current(request.warehouseId()),
                facts.read(request.submissionId()));
            return result(stops.recheckAfterRollback(current, parsed.canonicalBytes()));
        }
    }

    private byte[] write(StopRequest request, CommandIdentity.Actor initial, CommandContext hint,
        SourceEvidenceIntakeReceipts.BoundKey key, SourceEvidenceIntakeReceipts.BoundKey openKey,
        SourceEvidenceIntakeOpen.Parsed original, SourceEvidenceIntakeStop.Parsed parsed,
        SourceEvidenceIntakeObjectFiles.Stored stored) {
        locks.identity(initial, hint); locks.source(hint);
        var locked = facts.read(request.submissionId()); locks.sameHint(hint, locked);
        var actor = identity.current(request.warehouseId());
        require(actor.id().equals(initial.id()) && bound(request.originalKey(), request, actor, locked).equals(key),
            "E2A_SOURCE_CONTEXT_CHANGED");
        evidenceLocks.source(locked);
        var second = stops.existingLocked(key, parsed.canonicalBytes());
        if (second.status() != SourceEvidenceIntakeReplay.Status.NOT_FOUND) return result(second);
        var open = opens.openBound(openKey);
        require(Arrays.equals(original.canonicalBytes(), open.canonicalBytes()), "E2A_OPEN_CHANGED");
        var context = facts.read(request.submissionId()); locks.sameHint(hint, context);
        validate(request, actor, context, open);
        var current = parse(request, actor, context, open);
        require(Arrays.equals(current.canonicalBytes(), parsed.canonicalBytes()), "E2A_STOP_CHANGED");
        serverState.requireWritable(request.transportRecoveryEpoch()); identity.requireRecovery(actor);
        var binding = binding(request, actor, context);
        var existing = objects.findAfterSourceLock(binding, parsed.stopEventId());
        var object = existing == null ? objects.insert(binding, parsed.stopEventId(), stored, Instant.now())
            : sameObject(existing, stored);
        var insertion = insert(context, actor, parsed, request.originalKey());
        var receipt = codec.stop(Ids.next(), request.originalKey(), open, parsed, insertion, object, Instant.now());
        stops.insert(insertion, object.id(), receipt);
        return receipt.originalResponseBytes();
    }

    private SourceEvidenceIntakeStopObjects.StopObject sameObject(SourceEvidenceIntakeStopObjects.StopObject existing,
        SourceEvidenceIntakeObjectFiles.Stored stored) {
        require(existing.stored().equals(stored), "E2A_STOP_OBJECT_CONFLICT");
        try { files.verify(existing.stored()); }
        catch (IOException failure) { throw unavailable(); }
        return existing;
    }

    private SourceEvidenceIntakeStop.Parsed parse(StopRequest request, CommandIdentity.Actor actor,
        CommandContext context, SourceEvidenceIntakeOpen.Parsed open) {
        var expected = new SourceEvidenceIntakeStop.Expected(request.openOriginalKey(), open,
            request.warehouseId(), actor.id(), context.id("freezeDeviceId"),
            Long.parseLong(context.text("sourceRecoveryEpoch")), trustedKey(context));
        return SourceEvidenceIntakeStop.parse(request.requestBytes(), expected);
    }

    private PublicKey trustedKey(CommandContext context) {
        var device = devices.find(context.id("freezeDeviceId"), false);
        require(device.id().equals(context.id("freezeDeviceId"))
            && device.warehouseId().equals(context.id("warehouseId")), "E2A_STOP_DEVICE_KEY_CHANGED");
        device.requireActive();
        var trusted = deviceKeys.trusted(context.id("warehouseId"), context.id("freezeDeviceId"));
        require(Base64.getEncoder().encodeToString(trusted.getEncoded()).equals(device.publicKey()),
            "E2A_STOP_DEVICE_KEY_CHANGED");
        return trusted;
    }

    private void validate(StopRequest request, CommandIdentity.Actor actor, CommandContext context,
        SourceEvidenceIntakeOpen.Parsed open) {
        require(actor.id().equals(context.id("originalOperatorId"))
            && actor.warehouse().equals(context.id("warehouseId"))
            && actor.pda().deviceId().equals(context.id("creatorDeviceId"))
            && actor.pda().deviceId().equals(context.id("freezeDeviceId"))
            && open.sourceId().equals(context.text("authorizationId"))
            && open.submissionId().equals(request.submissionId()) && open.workRound() == request.workRound()
            && context.version("workRound") == request.workRound()
            && context.version("scopeRound") == request.workRound()
            && (request.submissionId().equals(context.id("currentSubmissionId")) || facts.currentOrPendingHandover(context,request.submissionId())), "E2A_SOURCE_CONTEXT_CHANGED");
    }

    private SourceEvidenceIntakeReceipts.BoundKey bound(UUID originalKey, StopRequest request,
        CommandIdentity.Actor actor, CommandContext context) {
        require(actor.id().equals(context.id("originalOperatorId"))
            && actor.warehouse().equals(context.id("warehouseId"))
            && actor.pda().deviceId().equals(context.id("creatorDeviceId")), "E2A_SOURCE_NOT_OWNER");
        return new SourceEvidenceIntakeReceipts.BoundKey(actor.id(), originalKey, request.warehouseId(),
            request.submissionId(), request.workRound(), context.id("creatorDeviceId"), context.id("authorizationId"));
    }

    private SourceEvidenceIntakeStopObjects.Binding binding(StopRequest request, CommandIdentity.Actor actor,
        CommandContext context) {
        return new SourceEvidenceIntakeStopObjects.Binding(request.warehouseId(), context.id("scopeId"),
            request.submissionId(), request.workRound(), context.id("freezeDeviceId"), actor.id(),
            request.openOriginalKey());
    }

    private SourceEvidenceIntakeReceipts.OpenInsert insert(CommandContext context, CommandIdentity.Actor actor,
        SourceEvidenceIntakeStop.Parsed stop, UUID key) {
        return new SourceEvidenceIntakeReceipts.OpenInsert(actor.id(), key, context.id("warehouseId"),
            context.id("scopeId"), context.id("submissionId"), context.version("workRound"),
            context.id("creatorDeviceId"), context.id("clientOrderId"), context.id("authorizationId"),
            context.id("originalOperatorId"), Long.parseLong(context.text("sourceRecoveryEpoch")),
            actor.pda().sessionId(), actor.authVersion(), actor.pda().deviceVersion(),
            stop.canonicalBytes(), stop.requestSha256(), context.id("scopeClientOrderId"));
    }

    private static byte[] result(SourceEvidenceIntakeReplay.Decision decision) {
        return switch (decision.status()) {
            case REPLAY -> decision.originalResponseBytes();
            case CONFLICT -> throw new BusinessException(409, "E2A_INTAKE_CONFLICT", "同一原键对应不同STOP请求");
            case INTEGRITY_FAILURE -> throw new BusinessException(409, "E2A_INTAKE_INTEGRITY_FAILURE", "原STOP回执损坏");
            case UNKNOWN, NOT_FOUND -> throw new BusinessException(409, "E2A_INTAKE_RESULT_UNKNOWN", "STOP结果不可确认");
        };
    }

    private static void require(boolean condition, String code) {
        if (!condition) throw new BusinessException(409, code, "停止证明与原来源上下文不一致");
    }

    private static BusinessException unavailable() {
        return new BusinessException(409, "E2A_STOP_FILE_UNAVAILABLE", "停止证明文件缺失、损坏或不可持久保存");
    }

    public record StopRequest(UUID warehouseId, UUID originalKey, UUID openOriginalKey, UUID submissionId,
        long workRound, String transportRecoveryEpoch, byte[] requestBytes) {
        public StopRequest {
            if (warehouseId == null || originalKey == null || openOriginalKey == null
                || originalKey.equals(openOriginalKey) || submissionId == null || workRound < 1
                || transportRecoveryEpoch == null || !transportRecoveryEpoch.matches("0|[1-9][0-9]{0,18}")
                || requestBytes == null) throw new IllegalArgumentException("E2A_STOP_REQUEST");
            requestBytes = requestBytes.clone();
        }
        @Override public byte[] requestBytes() { return requestBytes.clone(); }
    }
}
