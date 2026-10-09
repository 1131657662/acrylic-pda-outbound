package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.JsonText;
import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.TemporaryCommands;
import com.acrylic.outbound.temporary.draft.DraftPreparationProof;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** The issuer hashes its native Ack record in declaration order, not a sorted JSON object. */
final class RecoveryTemporaryAckSignature {
    private final RecoveryReceiptKeys keys;
    RecoveryTemporaryAckSignature(RecoveryReceiptKeys keys) { this.keys = keys; }

    JsonNode verify(JsonNode receipt, Context context) {
        fields(receipt, "authorizationId bundleId activatedAt acknowledgedAt signedAck");
        var command = context.command(); var manifest = SubmissionJson.parse(text(context.preparationReceipt(), "manifest")); var source = context.source();
        fields(command, "bundleId manifestSha256 authorizationEpoch activatedAt");
        require(uuid(command, "bundleId").equals(uuid(manifest.path("identity"), "bundleId"))
            && text(command, "manifestSha256").equals(text(context.preparationReceipt(), "manifestSha256"))
            && text(command, "authorizationEpoch").equals(text(manifest.path("identity"), "authorizationEpoch")),
            "RECOVERY_TEMP_ACTIVATION_COMMAND");
        String token = text(receipt, "signedAck"); var proof = signature(token);
        fields(proof, "iss aud authorizationId bundleId manifestSha256 commandSha256 serverRecoveryEpoch userId deviceId activatedAt acknowledgedAt");
        require(uuid(proof, "authorizationId").equals(source.qualificationId()) && uuid(proof, "userId").equals(source.operatorId())
            && uuid(proof, "deviceId").equals(source.deviceId()) && text(proof, "serverRecoveryEpoch").equals(source.sourceRecoveryEpoch())
            && proof.path("bundleId").equals(command.path("bundleId")) && proof.path("manifestSha256").equals(command.path("manifestSha256")),
            "RECOVERY_TEMP_ACTIVATION_IDENTITY");
        for (String field : List.of("authorizationId", "bundleId", "activatedAt", "acknowledgedAt"))
            require(receipt.path(field).equals(proof.path(field)), "RECOVERY_TEMP_ACTIVATION_RECEIPT");
        var nativeCommand = new TemporaryCommands.Ack(uuid(command, "bundleId"), text(command, "manifestSha256"),
            text(command, "authorizationEpoch"), RecoveryPreparationBinding.time(command, "activatedAt"));
        var json = new JsonText(new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
        require(json.digest(nativeCommand).equals(text(proof, "commandSha256")), "RECOVERY_TEMP_ACTIVATION_DIGEST");
        return proof;
    }

    private JsonNode signature(String token) {
        require(token.length() <= 131072, "RECOVERY_TEMP_ACTIVATION_SIGNATURE");
        String[] parts = token.split("\\.", -1); require(parts.length == 3, "RECOVERY_TEMP_ACTIVATION_SIGNATURE");
        var header = SubmissionJson.parse(Base64.getUrlDecoder().decode(parts[0]));
        String kid = text(header, "kid"); var key = keys.requireKey(kid);
        return DraftPreparationProof.verify(token, new OfflineSigner.PublicKeyView(kid, "RS256",
            Base64.getEncoder().encodeToString(key.getEncoded())), "acrylic-pda-temporary-activation");
    }

    record Context(RecoverySourceIdentity source, JsonNode preparationReceipt, JsonNode command) { }
}
