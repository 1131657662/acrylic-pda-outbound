package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.ServerState;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.command.CommandContext;
import com.acrylic.outbound.temporary.command.CommandIdentity;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class SourceEvidenceIntakeReadService {
    private final SourceEvidenceIntakeIdentity identity;
    private final SourceEvidenceIntakeFacts facts;
    private final SourceEvidenceIntakeReadStore store;
    private final SourceEvidenceIntakeReadIntegrity integrity;
    private final ServerState state;

    public SourceEvidenceIntakeReadService(SourceEvidenceIntakeIdentity identity,
        SourceEvidenceIntakeFacts facts, SourceEvidenceIntakeReadStore store,
        SourceEvidenceIntakeReadIntegrity integrity, ServerState state) {
        this.identity = identity; this.facts = facts; this.store = store;
        this.integrity = integrity; this.state = state;
    }

    public View read(UUID warehouseId, UUID submissionId, long workRound, String originalKey) {
        var found = required(warehouseId, submissionId, workRound, originalKey);
        var result = found.result();
        return new View(new String(result.originalBytes(), StandardCharsets.UTF_8), result.sha256(),
            result.trustStatus(), currentEffect(found, state.current()));
    }

    public byte[] original(UUID warehouseId, UUID submissionId, long workRound, String originalKey) {
        var result = required(warehouseId, submissionId, workRound, originalKey).result();
        if (!"VERIFIED".equals(result.trustStatus()))
            throw new BusinessException(409, "E2A_RECEIPT_KEY_UNVERIFIABLE", "历史签名密钥不可核验");
        return result.originalBytes();
    }

    private Found required(UUID warehouseId, UUID submissionId,
        long workRound, String originalKey) {
        if (warehouseId == null || submissionId == null || workRound < 1)
            throw new BusinessException(400, "E2A_READ_INPUT_INVALID", "原来源读取参数无效");
        UUID key = key(originalKey);
        var actor = identity.current(warehouseId);
        identity.requireRecovery(actor);
        var source = facts.read(submissionId);
        requireOwner(actor, source, warehouseId, workRound);
        return new Found(integrity.check(store.find(actor.id(), key), source, key), source, actor);
    }

    private static void requireOwner(CommandIdentity.Actor actor, CommandContext source,
        UUID warehouseId, long workRound) {
        if (!actor.id().equals(source.id("originalOperatorId"))
            || !warehouseId.equals(source.id("warehouseId"))
            || !actor.pda().deviceId().equals(source.id("creatorDeviceId"))
            || workRound != source.version("workRound"))
            throw new BusinessException(404, "E2A_SOURCE_NOT_FOUND", "原来源不存在或无权访问");
    }

    private static UUID key(String value) {
        try {
            var parsed = UUID.fromString(value);
            if (parsed.toString().equals(value)) return parsed;
        } catch (RuntimeException invalid) { }
        throw new BusinessException(400, "E2A_ORIGINAL_KEY_INVALID", "原键须为规范UUID");
    }

    private static JsonNode currentEffect(Found found,
        ServerState.State server) {
        var effect = SubmissionJson.object().put("evaluatedAt", Instant.now().toString())
            .put("evaluation", "PARTIAL").put("applicable", false)
            .put("serverRecoveryEpoch", server.epoch()).put("serviceMode", server.mode());
        var reasons = effect.putArray("reasons");
        reasons.add("NO_TRUSTED_COMPLETE_AT_STOP");
        reasons.add("BUSINESS_DECISION_NOT_IMPLEMENTED");
        if (!"VERIFIED".equals(found.result().trustStatus())) reasons.add("HISTORICAL_KEY_UNVERIFIABLE");
        if (!"NORMAL".equals(server.mode())) reasons.add("SERVER_NOT_NORMAL");
        var original = found.result().receipt().path("originalContext");
        if (!found.actor().authVersion().equals(original.path("userAuthVersion").asText()))
            reasons.add("USER_AUTH_VERSION_CHANGED");
        if (!found.actor().pda().deviceVersion().equals(original.path("deviceVersion").asText()))
            reasons.add("DEVICE_VERSION_CHANGED");
        if (!found.actor().pda().sessionId().toString().equals(original.path("currentSessionId").asText()))
            reasons.add("SESSION_CHANGED");
        if (!found.source().text("submissionId").equals(found.source().text("currentSubmissionId")))
            reasons.add("SUBMISSION_NOT_CURRENT");
        if (found.source().version("scopeRound") != found.source().version("workRound"))
            reasons.add("SCOPE_ROUND_CHANGED");
        effect.set("capabilities", SourceEvidenceIntakeReceiptCodec.capabilities());
        return effect;
    }

    private record Found(SourceEvidenceIntakeReadIntegrity.Result result, CommandContext source,
        CommandIdentity.Actor actor) { }

    public record View(String originalResponseUtf8, String originalResponseSha256,
        String trustStatus, JsonNode currentEffect) { }
}
