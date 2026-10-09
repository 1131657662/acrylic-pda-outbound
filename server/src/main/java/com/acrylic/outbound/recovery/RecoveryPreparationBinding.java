package com.acrylic.outbound.recovery;

import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.temporary.draft.DraftPreparationProof;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static com.acrylic.outbound.recovery.RecoveryPreparationParts.integer;

/** Binds the native receipt/manifest to the separately verified original offline credential. */
final class RecoveryPreparationBinding {
    private final RecoveryReceiptKeys keys;
    RecoveryPreparationBinding(RecoveryReceiptKeys keys) { this.keys = Objects.requireNonNull(keys); }

    JsonNode verify(JsonNode receipt, JsonNode claims, RecoverySourceIdentity source) {
        boolean temporary = source.sourceKind().equals("TEMP");
        fields(receipt, temporary ? "authorizationId bundleId offlineToken manifest manifestSha256 signedManifest"
            : "bundleId manifest manifestSha256 signedManifest");
        String raw = text(receipt, "manifest"), digest = sha256(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        require(digest.equals(text(receipt, "manifestSha256")), "RECOVERY_SOURCE_PREPARATION_MANIFEST_HASH");
        JsonNode manifest = com.acrylic.outbound.submissions.SubmissionJson.parse(raw);
        JsonNode proof = signature(text(receipt, "signedManifest"), temporary ? "acrylic-pda-temporary-preparation" : "acrylic-pda-preparation");
        require(digest.equals(text(proof, "manifestSha256")), "RECOVERY_SOURCE_PREPARATION_SIGNATURE_BINDING");
        require(temporary || uuid(receipt, "bundleId").equals(uuid(manifest, "bundleId")),
            "RECOVERY_SOURCE_PREPARATION_IDENTITY");
        if (temporary) temporary(manifest, new Context(receipt, claims, proof), source);
        else formal(manifest, new Context(receipt, claims, proof), source);
        return manifest;
    }

    private void formal(JsonNode value, Context context, RecoverySourceIdentity source) {
        fields(value, "schemaVersion bundleId assignmentId orderId deviceId operatorId assignmentEpoch serverRecoveryEpoch "
            + "inventorySnapshotId revision stateCapturedAt expiresAt parts");
        require(integer(value, "schemaVersion") == 1, "RECOVERY_SOURCE_PREPARATION_FORMAT");
        require(uuid(value, "assignmentId").equals(source.qualificationId()) && uuid(value, "orderId").equals(source.canonicalOrderId())
            && uuid(value, "deviceId").equals(source.deviceId()) && uuid(value, "operatorId").equals(source.operatorId())
            && text(value, "serverRecoveryEpoch").equals(source.sourceRecoveryEpoch()), "RECOVERY_SOURCE_PREPARATION_IDENTITY");
        var claims = context.claims(); var proof = context.proof();
        require(!claims.hasNonNull("renewedFromAuthorizationId") && !claims.hasNonNull("authorizationSequence"),
            "RECOVERY_SOURCE_PREPARATION_RENEWAL_UNSUPPORTED");
        require(uuid(proof, "assignmentId").equals(source.qualificationId())
            && uuid(value, "inventorySnapshotId").equals(uuid(claims, "inventorySnapshotId"))
            && text(value, "assignmentEpoch").equals(text(claims, "assignmentEpoch"))
            && integer(value, "revision") > 0 && value.path("revision").equals(claims.path("revision")),
            "RECOVERY_SOURCE_PREPARATION_BASELINE");
        var window = RecoverySourceSavedTimes.window(claims);
        require(time(value, "expiresAt").equals(window.expires()) && integer(proof, "exp") == window.expires().getEpochSecond()
            && !time(value, "stateCapturedAt").isBefore(window.issued())
            && time(value, "stateCapturedAt").isBefore(window.expires()), "RECOVERY_SOURCE_PREPARATION_WINDOW");
        descriptorHash(value, "plan", text(claims, "planSha256")); descriptorHash(value, "inventory", text(claims, "inventorySha256"));
    }

    private void temporary(JsonNode value, Context context, RecoverySourceIdentity source) {
        fields(value, "schemaVersion purpose identity inventorySnapshotId inventoryVersion mappingRevision policyVersion "
            + "barcodeRuleVersion issuedAt expiresAt parts");
        fields(value.path("identity"), "authorizationId bundleId userId deviceId warehouseId authorizationEpoch userAuthVersion deviceVersion serverRecoveryEpoch");
        require(integer(value, "schemaVersion") == 1 && "TEMP_WORK".equals(text(value, "purpose"))
            && "1".equals(text(value, "barcodeRuleVersion")), "RECOVERY_SOURCE_PREPARATION_FORMAT");
        var receipt = context.receipt(); var claims = context.claims(); var proof = context.proof();
        require(value.path("identity").equals(claims.path("identity"))
            && uuid(receipt, "authorizationId").equals(source.qualificationId())
            && uuid(receipt, "bundleId").equals(uuid(value.path("identity"), "bundleId"))
            && uuid(proof, "authorizationId").equals(source.qualificationId())
            && uuid(proof, "bundleId").equals(uuid(receipt, "bundleId"))
            && text(receipt, "manifestSha256").equals(text(claims, "manifestSha256")), "RECOVERY_SOURCE_PREPARATION_IDENTITY");
        uuid(value, "inventorySnapshotId"); revision(value, "inventoryVersion"); revision(value, "mappingRevision"); revision(value, "policyVersion");
        var window = RecoverySourceSavedTimes.window(claims);
        require(time(value, "issuedAt").equals(window.issued()) && time(value, "expiresAt").equals(window.expires())
            && proof.path("iat").equals(claims.path("iat")) && proof.path("exp").equals(claims.path("exp")),
            "RECOVERY_SOURCE_PREPARATION_WINDOW");
    }

    private void descriptorHash(JsonNode manifest, String name, String expected) {
        var matches = new ArrayList<JsonNode>();
        for (var part : com.acrylic.outbound.submissions.SubmissionJson.array(manifest, "parts"))
            if (name.equals(text(part, "name"))) matches.add(part);
        require(matches.size() == 1 && expected.equals(text(matches.getFirst(), "sha256")), "RECOVERY_SOURCE_PREPARATION_BASELINE");
    }

    private JsonNode signature(String token, String audience) {
        require(token.length() <= 131072, "RECOVERY_SOURCE_PREPARATION_SIGNATURE");
        String[] parts = token.split("\\.", -1); require(parts.length == 3, "RECOVERY_SOURCE_PREPARATION_SIGNATURE");
        var header = com.acrylic.outbound.submissions.SubmissionJson.parse(Base64.getUrlDecoder().decode(parts[0]));
        String kid = text(header, "kid"); var key = keys.requireKey(kid);
        return DraftPreparationProof.verify(token, new OfflineSigner.PublicKeyView(kid, "RS256",
            Base64.getEncoder().encodeToString(key.getEncoded())), audience);
    }

    static java.time.Instant time(JsonNode value, String key) {
        String raw = text(value, key);
        require(raw.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,9})?Z"),
            "RECOVERY_SOURCE_PREPARATION_TIME");
        return java.time.Instant.parse(raw);
    }

    private record Context(JsonNode receipt, JsonNode claims, JsonNode proof) { }
}
