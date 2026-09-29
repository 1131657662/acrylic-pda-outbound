package com.acrylic.outbound.recovery;

import java.util.Objects;
import java.util.UUID;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.require;

/**
 * The identity of one admitted operation root. It names the operation, the control authority record
 * it was admitted under and (for a request) the request it belongs to, so a token that survives its
 * root can never be mistaken for a live admission.
 */
public record RecoveryOperationToken(
        UUID operationId,
        RecoveryOperationKind kind,
        UUID installationId,
        String authorityRecordSha256,
        String requestSha256) {

    public RecoveryOperationToken {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(installationId, "installationId");
        require(isHash(authorityRecordSha256), "CONTROL_OPERATION_TOKEN_INVALID");
        require(requestSha256 == null || isHash(requestSha256), "CONTROL_OPERATION_TOKEN_INVALID");
    }

    private static boolean isHash(String value) {
        return value != null && value.matches("[a-f0-9]{64}");
    }
}
