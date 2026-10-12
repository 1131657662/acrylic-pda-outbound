package com.acrylic.outbound.rescue;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class RescueCommands {
    private RescueCommands() { }
    public record Request(@NotBlank @Size(max=32768) String originalText,
        @NotBlank @Size(max=256) String signature) { }
    public record Approve(@com.fasterxml.jackson.annotation.JsonProperty(value="expiresAt",required=true) Instant expiresAt,
        @NotBlank @Size(max=1000) String reason) { }
    public record Revoke(@NotBlank @Size(max=1000) String reason) { }
    public record Target(String source,UUID localOrderId) { }
    public record Document(String purpose,String requestKey,UUID deviceId,UUID operatorId,UUID warehouseId,
        UUID rescuerId,List<Target> orderScope,String reason) { }
    public record RequestReceipt(String requestId,String requestSha256) { }
    public record GrantReceipt(String grantId,String signedAuthorization) { }
    public record RevocationReceipt(String grantId,String signedRevocation) { }
    public record Material(RequestReceipt request,String originalText,String deviceSignature,List<Target> orderScope,
        String status,GrantReceipt authorization,RevocationReceipt revocation,boolean localDeletionAllowed) { }
}
