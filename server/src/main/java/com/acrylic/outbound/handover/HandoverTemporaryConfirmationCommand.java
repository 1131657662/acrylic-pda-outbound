package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** A recipient's durable intent binds the original prepared evidence and an explicit physical confirmation. */
record HandoverTemporaryConfirmationCommand(UUID caseId, String epoch, UUID key, UUID authorization,
        long caseVersion, JsonNode bound, byte[] bytes, String digest) {
    static final int MAX_BYTES = 65536;
    HandoverTemporaryConfirmationCommand {
        bound = bound.deepCopy(); bytes = bytes.clone();
    }
    @Override public JsonNode bound() { return bound.deepCopy(); }
    @Override public byte[] bytes() { return bytes.clone(); }
    static HandoverTemporaryConfirmationCommand parse(UUID caseId, String epoch, byte[] bytes) {
        try {
            if (caseId == null || epoch == null || !epoch.matches("[1-9][0-9]{0,17}")
                    || bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) throw invalid();
            var body = com.acrylic.outbound.submissions.SubmissionJson.parse(bytes);
            var fields = new HashSet<String>(); body.fieldNames().forEachRemaining(fields::add);
            if (!body.isObject() || !fields.equals(Set.of("commandVersion", "commandId", "expectedCaseVersion", "targetAuthorizationId",
                    "preparedTicketSha256", "payloadSha256", "checklistSha256", "physicalConfirmed"))) throw invalid();
            if (!body.path("commandVersion").isIntegralNumber() || !body.path("commandVersion").canConvertToInt() || body.path("commandVersion").intValue() != 1
                    || !body.path("physicalConfirmed").isBoolean() || !body.path("physicalConfirmed").booleanValue()) throw invalid();
            for (String field : List.of("preparedTicketSha256", "payloadSha256", "checklistSha256"))
                if (!body.path(field).isTextual() || !body.path(field).asText().matches("[a-f0-9]{64}")) throw invalid();
            String version = text(body, "expectedCaseVersion");
            if (!version.matches("[1-9][0-9]{0,17}")) throw invalid();
            var bound = object().put("caseId", caseId.toString()).put("transportRecoveryEpoch", epoch); bound.set("command", body);
            return new HandoverTemporaryConfirmationCommand(caseId, epoch, uuid(body, "commandId"), uuid(body, "targetAuthorizationId"),
                Long.parseLong(version), bound, canonical(bound).getBytes(StandardCharsets.UTF_8), sha(bound));
        } catch (RuntimeException malformed) { throw invalid(); }
    }
    static BusinessException invalid() { return new BusinessException(400, "INVALID_TEMP_HANDOVER_CONFIRMATION", "请明确确认实物与原交接资料，并保留原请求"); }
}
