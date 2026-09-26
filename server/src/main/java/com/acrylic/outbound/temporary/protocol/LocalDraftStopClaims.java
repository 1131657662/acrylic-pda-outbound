package com.acrylic.outbound.temporary.protocol;

import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import static com.acrylic.outbound.temporary.protocol.ProtocolJson.require;

/** Structural and source binding only; preparation receipts and raw closure require their own verification. */
final class LocalDraftStopClaims {
    private static final Set<String> CORE = Set.of("kind", "evidenceId", "clientOrderId", "creatorDeviceId",
        "authorizationId", "warehouseId", "originalOperatorId", "snapshotId", "stopEventId", "stopTransactionVersion",
        "metadataSha256", "rootSha256", "recordCount", "byteCount", "sourceRecoveryEpoch", "captureHighWatermark", "auditThroughSeq");
    private static final Set<String> IDS = Set.of("evidenceId", "clientOrderId", "creatorDeviceId", "authorizationId",
        "warehouseId", "originalOperatorId", "snapshotId", "stopEventId");
    private LocalDraftStopClaims() { }

    static void validate(JsonNode core, JsonNode meta, byte[] metadata, LocalDraftStopProtocol.Expected expected) {
        fields(core, CORE);
        for (String name : IDS) SubmissionJson.uuid(core, name);
        equal(core, "kind", "LOCAL_DRAFT_STOP");
        require(number(core, "stopTransactionVersion", 1, 1) == 1, "DRAFT_STOP_VERSION");
        number(core, "recordCount", 1, 250000); number(core, "byteCount", 1, 134217728);
        number(core, "sourceRecoveryEpoch", 1, 9007199254740991L);
        number(core, "captureHighWatermark", 0, 9007199254740991L);
        number(core, "auditThroughSeq", 1, 9007199254740991L);
        require(text(core, "rootSha256").matches("[0-9a-f]{64}"), "DRAFT_STOP_ROOT");
        equal(core, "metadataSha256", ImportFiles.sha256(metadata));
        require(core.path("byteCount").longValue() >= metadata.length, "DRAFT_STOP_BYTES");
        expected(core, expected); metadata(core, meta);
    }

    private static void expected(JsonNode core, LocalDraftStopProtocol.Expected expected) {
        equal(core, "evidenceId", expected.evidenceId().toString());
        equal(core, "clientOrderId", expected.clientOrderId().toString());
        equal(core, "creatorDeviceId", expected.creatorDeviceId().toString());
        equal(core, "authorizationId", expected.authorizationId().toString());
        equal(core, "warehouseId", expected.warehouseId().toString());
        equal(core, "originalOperatorId", expected.originalOperatorId().toString());
        equal(core, "snapshotId", expected.snapshotId().toString());
        require(core.path("sourceRecoveryEpoch").longValue() == expected.sourceRecoveryEpoch(), "DRAFT_STOP_EPOCH");
    }

    private static void metadata(JsonNode core, JsonNode meta) {
        fields(meta, Set.of("schemaVersion", "kind", "evidenceId", "draft", "stopEventId", "captureHighWatermark",
            "auditThroughSeq", "sourceRecoveryEpoch", "snapshotId", "preparationRequestKey", "preparationRequest",
            "preparationReceipt", "preparationSigningKey", "manifestSha256", "localDeletionAllowed"));
        number(meta, "schemaVersion", 1, 1);
        for (String name : Set.of("kind", "evidenceId", "stopEventId", "snapshotId")) equal(meta, name, text(core, name));
        for (String name : Set.of("captureHighWatermark", "auditThroughSeq", "sourceRecoveryEpoch"))
            equal(meta, name, Long.toString(core.path(name).longValue()));
        require(meta.path("localDeletionAllowed").isBoolean() && !meta.path("localDeletionAllowed").booleanValue(), "DRAFT_STOP_DELETION");
        JsonNode draft = meta.path("draft");
        require(draft.isObject() && Set.of("DRAFT", "SCANNING").contains(text(draft, "state")), "DRAFT_STOP_STATE");
        for (String name : Set.of("clientOrderId", "creatorDeviceId", "authorizationId", "warehouseId"))
            equal(draft, name, text(core, name));
        equal(draft, "operatorId", text(core, "originalOperatorId"));
        SubmissionJson.uuid(meta, "preparationRequestKey");
        for (String name : Set.of("preparationRequest", "preparationReceipt", "preparationSigningKey"))
            require(!text(meta, name).isEmpty(), "DRAFT_STOP_PREPARATION");
        require(text(meta, "manifestSha256").matches("[0-9a-f]{64}"), "DRAFT_STOP_MANIFEST");
    }

    static void fields(JsonNode node, Set<String> allowed) {
        require(node != null && node.isObject() && node.size() == allowed.size(), "DRAFT_STOP_FIELDS");
        node.fieldNames().forEachRemaining(name -> require(allowed.contains(name), "DRAFT_STOP_FIELD"));
    }
    static long number(JsonNode node, String name, long min, long max) {
        JsonNode value = node.path(name);
        require(value.isIntegralNumber() && value.canConvertToLong() && value.longValue() >= min && value.longValue() <= max,
            "DRAFT_STOP_NUMBER");
        return value.longValue();
    }
    static String text(JsonNode node, String name) {
        require(node.path(name).isTextual(), "DRAFT_STOP_TEXT"); return node.path(name).textValue();
    }
    static void equal(JsonNode node, String name, String expected) { require(text(node, name).equals(expected), "DRAFT_STOP_BINDING"); }
}
