package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Native FORMAL ACK is unsigned: only independently pinned original audit and assignment facts can bind it. */
public final class RecoverySourceFormalActivation {
    private final RecoverySourcePreparation preparation;
    private final RecoverySourceAuthority credentials;

    public RecoverySourceFormalActivation(RecoveryReceiptKeys keys) {
        preparation = new RecoverySourcePreparation(Objects.requireNonNull(keys)); credentials = new RecoverySourceAuthority(keys);
    }

    public Proof verify(RecoveryPinnedSource source) {
        require(source != null && source.identity().sourceKind().equals("FORMAL"), "RECOVERY_FORMAL_ACTIVATION_SOURCE");
        require(source.control() != null && source.manifestSha256() != null
            && source.manifestSha256().matches("[a-f0-9]{64}") && !ZERO_HASH.equals(source.manifestSha256()),
            "RECOVERY_FORMAL_ACTIVATION_PIN_REQUIRED");
        var proven = preparation.verify(source);
        try {
            var origin = SubmissionJson.parse(source.original("origin")); var activation = origin.path("activation");
            require(activation.isObject(), "RECOVERY_FORMAL_ACTIVATION_REQUIRED");
            fields(activation, "command receipt assignment operationEvent");
            var manifest = SubmissionJson.parse(text(origin.path("preparationReceipt"), "manifest"));
            var context = new Context(source.identity(), manifest, credentials.verify(source));
            var command = SubmissionJson.parse(text(activation, "command")); command(command, context, proven.manifestSha256());
            var assignment = activation.path("assignment"); assignment(assignment, context);
            var receipt = SubmissionJson.parse(text(activation, "receipt")); receipt(receipt, assignment, manifest);
            var event = activation.path("operationEvent"); event(event, command, context);
            Instant activated = time(command, "activatedAt"), acknowledged = time(receipt, "downloadAckAt");
            chronology(new Times(activated, acknowledged, time(event, "serverTime")), context.claims());
            saves(SubmissionJson.parse(source.original("payload")), activated);
            return new Proof(proven, text(event, "id"), SubmissionJson.sha(event), activated, acknowledged);
        } catch (RecoveryControlException refusal) { throw refusal; }
        catch (RuntimeException invalid) { throw new RecoveryControlException("RECOVERY_FORMAL_ACTIVATION_INVALID", invalid); }
    }

    private void command(JsonNode value, Context context, String manifestSha256) {
        fields(value, "bundleId manifestSha256 assignmentEpoch inventorySnapshotId activatedAt");
        require(uuid(value, "bundleId").equals(uuid(context.manifest(), "bundleId"))
            && manifestSha256.equals(text(value, "manifestSha256"))
            && value.path("assignmentEpoch").equals(context.manifest().path("assignmentEpoch"))
            && uuid(value, "inventorySnapshotId").equals(uuid(context.manifest(), "inventorySnapshotId")),
            "RECOVERY_FORMAL_ACTIVATION_COMMAND");
    }

    private void assignment(JsonNode value, Context context) {
        fields(value, "id orderId warehouseId deviceId operatorId epoch serverRecoveryEpoch inventorySnapshotId revision "
            + "offlineAuthorizationId authorizationSequence issuedAt expiresAt downloadAckAt");
        var source = context.source(); var claims = context.claims(); var manifest = context.manifest();
        require(uuid(value, "id").equals(source.qualificationId()) && uuid(value, "orderId").equals(source.canonicalOrderId())
            && uuid(value, "warehouseId").equals(source.warehouseId()) && uuid(value, "deviceId").equals(source.deviceId())
            && uuid(value, "operatorId").equals(source.operatorId())
            && text(value, "serverRecoveryEpoch").equals(source.sourceRecoveryEpoch()), "RECOVERY_FORMAL_ACTIVATION_ASSIGNMENT");
        require(value.path("epoch").equals(manifest.path("assignmentEpoch"))
            && value.path("revision").equals(manifest.path("revision"))
            && uuid(value, "inventorySnapshotId").equals(uuid(manifest, "inventorySnapshotId"))
            && uuid(value, "offlineAuthorizationId").equals(uuid(claims, "jti"))
            && revision(value, "authorizationSequence") == 0, "RECOVERY_FORMAL_ACTIVATION_BASELINE");
        var window = RecoverySourceSavedTimes.window(claims);
        require(time(value, "issuedAt").equals(window.issued()) && time(value, "expiresAt").equals(window.expires()),
            "RECOVERY_FORMAL_ACTIVATION_WINDOW");
    }

    private void receipt(JsonNode value, JsonNode assignment, JsonNode manifest) {
        fields(value, "assignmentId bundleId downloadAckAt");
        require(uuid(value, "assignmentId").equals(uuid(assignment, "id"))
            && uuid(value, "bundleId").equals(uuid(manifest, "bundleId"))
            && value.path("downloadAckAt").equals(assignment.path("downloadAckAt")), "RECOVERY_FORMAL_ACTIVATION_RECEIPT");
    }

    private void event(JsonNode value, JsonNode command, Context context) {
        fields(value, "id actorId eventType entityType entityId details serverTime requestId");
        require(revision(value, "id") > 0 && !text(value, "requestId").isBlank()
            && text(value, "requestId").length() <= 64, "RECOVERY_FORMAL_ACTIVATION_EVENT");
        require(uuid(value, "actorId").equals(context.source().operatorId())
            && uuid(value, "entityId").equals(context.source().canonicalOrderId())
            && "ORDER".equals(text(value, "entityType")) && "ORDER_DOWNLOAD_ACK".equals(text(value, "eventType")),
            "RECOVERY_FORMAL_ACTIVATION_EVENT");
        require(SubmissionJson.parse(text(value, "details")).equals(command), "RECOVERY_FORMAL_ACTIVATION_EVENT_COMMAND");
    }

    private void chronology(Times times, JsonNode claims) {
        var window = RecoverySourceSavedTimes.window(claims);
        for (Instant instant : java.util.List.of(times.activated(), times.acknowledged(), times.audit()))
            require(!instant.isBefore(window.issued()) && instant.isBefore(window.expires()), "RECOVERY_FORMAL_ACTIVATION_WINDOW");
        require(times.audit().equals(times.audit().truncatedTo(ChronoUnit.MILLIS))
            && times.acknowledged().equals(times.acknowledged().truncatedTo(ChronoUnit.MILLIS))
            && !times.audit().isAfter(times.acknowledged())
            // DATETIME(3) discarded the audit clock's sub-millisecond fraction.
            && times.activated().isBefore(times.audit().plusSeconds(300).plusMillis(1)), "RECOVERY_FORMAL_ACTIVATION_TIME");
    }

    private void saves(JsonNode payload, Instant activated) {
        for (var capture : SubmissionJson.array(payload, "captures"))
            if (capture.hasNonNull("confirmedAt")) require(!time(capture, "confirmedAt").isBefore(activated), "RECOVERY_FORMAL_ACTIVATION_SAVE");
        for (var scan : SubmissionJson.array(payload, "scans"))
            require(!time(scan, "savedAt").isBefore(activated), "RECOVERY_FORMAL_ACTIVATION_SAVE");
    }

    private static Instant time(JsonNode value, String field) { return RecoveryPreparationBinding.time(value, field); }
    private record Context(RecoverySourceIdentity source, JsonNode manifest, JsonNode claims) { }
    private record Times(Instant activated, Instant acknowledged, Instant audit) { }
    public record Proof(RecoverySourcePreparation.Proof preparation, String operationEventId, String operationEventSha256,
            Instant activatedAt, Instant acknowledgedAt) { }
}
