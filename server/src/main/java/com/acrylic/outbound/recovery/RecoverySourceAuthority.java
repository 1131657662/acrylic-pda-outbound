package com.acrylic.outbound.recovery;

import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.draft.DraftPreparationProof;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Historical qualification proof only. An expired/revoked original credential never becomes a new work permit. */
public final class RecoverySourceAuthority {
    private final RecoveryReceiptKeys keys;

    public RecoverySourceAuthority(RecoveryReceiptKeys keys) { this.keys = Objects.requireNonNull(keys); }

    public JsonNode verify(RecoveryPinnedSource source) {
        var claims = verifyCredential(source);
        try {
            var payload = SubmissionJson.parse(source.original("payload"));
            requirePayload(payload, source.identity());
            RecoverySourceSavedTimes.verify(payload, Map.of(uuid(claims, "jti"),
                new RecoverySourceSavedTimes.Credential(source.identity(), claims)));
            return claims.deepCopy();
        } catch (RecoveryControlException refusal) { throw refusal; }
        catch (RuntimeException invalid) { throw new RecoveryControlException("RECOVERY_SOURCE_ORIGIN_INVALID", invalid); }
    }

    /** Only proves the retained credential, never a later-round activation or a new work permit. */
    public JsonNode verifyCredential(RecoveryPinnedSource source) {
        require(source != null, "RECOVERY_SOURCE_IDENTITY");
        try {
            var origin = SubmissionJson.parse(source.original("origin"));
            String token = text(origin, "offlineToken");
            var identity = source.identity();
            require(identity.workRound() == 1, "RECOVERY_SOURCE_ORIGIN_ROUND_UNSUPPORTED");
            String audience = identity.sourceKind().equals("FORMAL") ? "acrylic-pda-offline" : "acrylic-pda-temporary-work";
            var claims = DraftPreparationProof.verify(token, key(token), audience);
            RecoverySourceSavedTimes.window(claims);
            if (identity.sourceKind().equals("FORMAL")) formal(claims, identity);
            else temporary(claims, identity);
            return claims.deepCopy();
        } catch (RecoveryControlException refusal) { throw refusal; }
        catch (RuntimeException invalid) { throw new RecoveryControlException("RECOVERY_SOURCE_ORIGIN_INVALID", invalid); }
    }

    private OfflineSigner.PublicKeyView key(String token) {
        require(token.length() <= 131072, "RECOVERY_SOURCE_ORIGIN_INVALID");
        String[] parts = token.split("\\.", -1);
        require(parts.length == 3, "RECOVERY_SOURCE_ORIGIN_INVALID");
        var header = SubmissionJson.parse(Base64.getUrlDecoder().decode(parts[0]));
        String id = text(header, "kid"); var trusted = keys.requireKey(id);
        return new OfflineSigner.PublicKeyView(id, "RS256", Base64.getEncoder().encodeToString(trusted.getEncoded()));
    }

    static void requirePayload(JsonNode payload, RecoverySourceIdentity identity) {
        String qualification = identity.sourceKind().equals("FORMAL") ? "assignmentId" : "authorizationId";
        require(uuid(payload, "freezeDeviceId").equals(identity.deviceId())
            && revision(payload, "workRound") == identity.workRound()
            && uuid(payload, qualification).equals(identity.qualificationId())
            && text(payload, "sourceRecoveryEpoch").equals(identity.sourceRecoveryEpoch())
            && text(payload.path("source"), "kind").equals(identity.sourceKind()), "RECOVERY_SOURCE_ORIGIN_PAYLOAD");
    }

    private void formal(JsonNode claims, RecoverySourceIdentity identity) {
        require("pda:scan".equals(text(claims, "scope"))
            && uuid(claims, "assignmentId").equals(identity.qualificationId())
            && uuid(claims, "orderId").equals(identity.canonicalOrderId())
            && uuid(claims, "sub").equals(identity.operatorId())
            && uuid(claims, "deviceId").equals(identity.deviceId())
            && uuid(claims, "warehouseId").equals(identity.warehouseId())
            && text(claims, "serverRecoveryEpoch").equals(identity.sourceRecoveryEpoch()), "RECOVERY_SOURCE_ORIGIN_MISMATCH");
        uuid(claims, "jti");
    }

    private void temporary(JsonNode claims, RecoverySourceIdentity expected) {
        var identity = claims.path("identity");
        require("TEMP_WORK".equals(text(claims, "purpose"))
            && "temporary_order:create pda:scan pda:submit".equals(text(claims, "scope"))
            && uuid(claims, "jti").equals(expected.qualificationId())
            && uuid(identity, "authorizationId").equals(expected.qualificationId())
            && uuid(identity, "userId").equals(expected.operatorId())
            && uuid(identity, "deviceId").equals(expected.deviceId())
            && uuid(identity, "warehouseId").equals(expected.warehouseId())
            && text(identity, "serverRecoveryEpoch").equals(expected.sourceRecoveryEpoch()), "RECOVERY_SOURCE_ORIGIN_MISMATCH");
        hashField(claims, "manifestSha256"); uuid(identity, "bundleId");
    }
}
