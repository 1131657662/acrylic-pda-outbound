package com.acrylic.outbound.pairing;

import com.acrylic.outbound.devices.DeviceCrypto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;

public final class PairingCommands {
    private PairingCommands() { }
    public record Payload(@NotNull UUID applicationId,
        @NotNull @Pattern(regexp="[A-Za-z0-9_-]{43}") String credential,
        @NotBlank @Size(max=200) String publicKey,
        @NotBlank @Pattern(regexp="[^\\r\\n]{1,100}") String model,
        @NotNull @Pattern(regexp="([0-9a-f]{64})?") String approvalSha256) {
        public String binding() {
            return applicationId+"\n"+publicKey+"\n"+credential+"\n"+model+"\n"+approvalSha256;
        }
        public String originalHash() {
            return DeviceCrypto.sha(applicationId+"\n"+publicKey+"\n"+credential+"\n"+model);
        }
    }
    public record Command(@NotNull @Valid Payload payload,@NotNull UUID challengeId,
        @NotBlank @Size(max=200) String signature) { }
    public record ChallengeRequest(@NotNull UUID applicationId,@NotBlank @Size(max=200) String publicKey,
        @NotNull @Pattern(regexp="CREATE|STATUS|CONFIRM|CANCEL") String purpose,
        @NotNull @Pattern(regexp="[0-9a-f]{64}") String bindingSha256) { }
    public record Challenge(String challengeId,String signedText,String expiresAt) { }
    public record ApprovalRequest(@NotNull @Pattern(regexp="[A-Za-z0-9_-]{1,40}") String deviceCode,
        @NotBlank @Size(max=100) String name) { }
    public record Approval(UUID applicationId,UUID warehouseId,String warehouseName,String deviceCode,
        String name,String keySha256,String serverRecoveryEpoch) {
        public String binding() {
            return applicationId+"\n"+warehouseId+"\n"+keySha256+"\n"+serverRecoveryEpoch+"\n"+
                DeviceCrypto.sha(warehouseName)+"\n"+DeviceCrypto.sha(deviceCode)+"\n"+DeviceCrypto.sha(name);
        }
    }
    public record ApprovalReceipt(UUID applicationId,String approvalSha256,Approval approval) { }
    public record View(UUID applicationId,String status,String shortCode,String model,String createdAt,String expiresAt,
        String keySha256,String serverRecoveryEpoch,String approvalSha256,Approval approval,UUID deviceId) { }
}
