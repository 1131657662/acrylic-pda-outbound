package com.acrylic.outbound.recovery;

import com.acrylic.outbound.submissions.SubmissionJson;
import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;

/** Read-only per-save credential proof. The independent pins are prerequisites, not recovered-data assertions. */
public final class RecoverySourceQualifications {
    private final RecoverySourceAuthority authority;

    public RecoverySourceQualifications(RecoveryReceiptKeys keys) { authority = new RecoverySourceAuthority(keys); }

    public void verify(RecoveryPinnedSource current, Map<UUID, RecoveryPinnedSource> originals) {
        require(current != null && originals != null && !originals.isEmpty() && originals.size() <= 1024,
            "RECOVERY_SOURCE_QUALIFICATIONS_REQUIRED");
        require(current.control() != null, "RECOVERY_SOURCE_CREDENTIAL_CONTROL");
        try {
            var credentials = new HashMap<UUID, RecoverySourceSavedTimes.Credential>(); boolean currentFound = false;
            for (var entry : Map.copyOf(originals).entrySet()) {
                var source = entry.getValue(); var claims = authority.verifyCredential(source);
                require(entry.getKey().equals(uuid(claims, "jti")), "RECOVERY_SOURCE_CREDENTIAL_ID");
                require(source.identity().warehouseId().equals(current.identity().warehouseId()), "RECOVERY_SOURCE_CREDENTIAL_WAREHOUSE");
                require(source.control().equals(current.control()), "RECOVERY_SOURCE_CREDENTIAL_CONTROL");
                credentials.put(entry.getKey(), new RecoverySourceSavedTimes.Credential(source.identity(), claims));
                currentFound |= source.identity().equals(current.identity())
                    && source.manifestSha256().equals(current.manifestSha256())
                    && Arrays.equals(source.original("origin"), current.original("origin"));
            }
            require(currentFound, "RECOVERY_SOURCE_CREDENTIAL_CURRENT_MISSING");
            var body = SubmissionJson.parse(current.original("payload"));
            RecoverySourceAuthority.requirePayload(body, current.identity());
            RecoverySourceSavedTimes.verify(body, credentials);
        } catch (RecoveryControlException refusal) { throw refusal; }
        catch (RuntimeException invalid) { throw new RecoveryControlException("RECOVERY_SOURCE_ORIGIN_INVALID", invalid); }
    }
}
