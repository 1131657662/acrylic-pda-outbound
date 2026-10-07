package com.acrylic.outbound.handover;

import com.acrylic.outbound.audit.AuditWriter;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.temporary.TemporaryAccess;
import com.acrylic.outbound.pda.PdaSessions;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Receipt of a named recipient's confirmation does not activate a round or change any stock owner. */
@Service
@ConditionalOnProperty(prefix="outbound.handover.commands", name="enabled", havingValue="true")
public class HandoverTemporaryConfirmationService {
    private final TemporaryAccess access;
    private final HandoverCommand.Locks locks;
    private final HandoverStore cases;
    private final HandoverSourceGuard sources;
    private final HandoverPdaEvidenceService evidence;
    private final HandoverTemporaryConfirmationStore store;
    private final HandoverTemporaryConfirmationReceipt receipts;
    private final AuditWriter audit;

    HandoverTemporaryConfirmationService(TemporaryAccess access, HandoverCommand.Locks locks, HandoverStore cases,
            HandoverSourceGuard sources, HandoverPdaEvidenceService evidence, HandoverTemporaryConfirmationStore store,
            HandoverTemporaryConfirmationReceipt receipts, AuditWriter audit) {
        this.access = access; this.locks = locks; this.cases = cases; this.sources = sources;
        this.evidence = evidence; this.store = store; this.receipts = receipts; this.audit = audit;
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public byte[] confirm(HandoverTemporaryConfirmationCommand command, String requestId) {
        if (requestId == null || !requestId.matches("[A-Za-z0-9._:-]{1,64}")) throw HandoverTemporaryConfirmationCommand.invalid();
        var actor = access.lock(command.epoch());
        var facts = cases.caseFacts(command.caseId()); requireTarget(facts, actor);
        locks.scope(uuid(facts, "scopeId")); locks.kase(command.caseId());
        facts = cases.caseFacts(command.caseId()); requireTarget(facts, actor);
        var existing = store.get(command.caseId());
        if (existing != null) return receipts.replay(existing, command, actor);
        require("TARGET_PREPARED".equals(text(facts, "status")) && number(facts, "caseVersion") == command.caseVersion());
        require("ABORTED".equals(sources.lock(facts).path("status").asText()));
        var manifest = evidence.manifest(command.caseId()); requireBinding(command, manifest);
        var receipt = receipts.create(command, manifest, actor);
        store.insert(command, receipt, actor);
        audit.append(new AuditWriter.Event(actor.userId(), "HANDOVER_TEMP_TARGET_CONFIRMED", "HANDOVER_CASE", command.caseId(),
            canonical(object().put("receiptId", receipt.id().toString()).put("commandSha256", command.digest()))), requestId);
        return receipt.bytes();
    }

    private void requireTarget(JsonNode facts, PdaSessions.Principal actor) {
        if (!"TEMP".equals(facts.path("sourceKind").asText()) || !actor.warehouseId().toString().equals(facts.path("warehouseId").asText())
                || !actor.deviceId().toString().equals(facts.path("targetDeviceId").asText())
                || !actor.userId().toString().equals(facts.path("targetUserId").asText())) throw HandoverCommand.missing();
    }

    private void requireBinding(HandoverTemporaryConfirmationCommand command, JsonNode manifest) {
        var body = command.bound().path("command");
        require(command.authorization().toString().equals(manifest.path("preparedTicket").path("temporaryQualification").path("authorizationId").asText()));
        require(body.path("preparedTicketSha256").equals(manifest.path("preparedTicketSha256"))
            && body.path("payloadSha256").equals(manifest.path("recoveryPackageSha256"))
            && text(body, "checklistSha256").equals(sha(manifest.path("checklist"))));
    }

    private void require(boolean valid) {
        if (!valid) throw new BusinessException(409, "HANDOVER_CONFIRMATION_CHANGED", "交接准备或确认内容已变化，请保留原意图并核对");
    }
}
