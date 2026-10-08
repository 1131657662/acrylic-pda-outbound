package com.acrylic.outbound.temporary;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.pda.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class TemporaryIssuer {
    private final OfflineSigner signer;private final JsonText json;
    public TemporaryIssuer(OfflineSigner signer,JsonText json) { this.signer=signer;this.json=json; }
    public Work issue(Binding binding,TemporaryBaseline.View baseline,List<PreparationArchive> parts) {
        Instant issued=Instant.now().truncatedTo(ChronoUnit.SECONDS),expires=issued.plusSeconds(172800);
        if (binding.expiresAtLimit()!=null && binding.expiresAtLimit().isBefore(expires))
            expires=binding.expiresAtLimit().truncatedTo(ChronoUnit.SECONDS);
        if (!expires.isAfter(issued)) throw new BusinessException(409,"TEMPORARY_AUTH_EXPIRED","临时授权期限已到，请保留资料");
        var identity=new Identity(binding.id(),binding.bundle(),binding.principal().userId(),binding.principal().deviceId(),binding.principal().warehouseId(),
            Long.toString(binding.sequence()),binding.principal().userAuthVersion(),binding.principal().deviceVersion(),binding.recoveryEpoch());
        var manifest=new Manifest(1,"TEMP_WORK",identity,baseline.inventorySnapshotId(),baseline.inventoryVersion(),baseline.mappingRevision(),
            baseline.policy().version(),"1",issued.toString(),expires.toString(),parts.stream().map(PreparationArchive::descriptor).toList());
        String text=json.encode(manifest),sha=json.digest(manifest);
        var claims=new Claims("acrylic-outbound","acrylic-pda-temporary-work",binding.id().toString(),"TEMP_WORK",identity,sha,
            "temporary_order:create pda:scan pda:submit",issued.getEpochSecond(),expires.getEpochSecond());
        var token=signer.sign(claims);
        var signedManifest=signer.sign(new ManifestProof("acrylic-outbound","acrylic-pda-temporary-preparation",binding.id().toString(),
            binding.bundle().toString(),sha,issued.getEpochSecond(),expires.getEpochSecond()));
        var receipt=new TemporaryCommands.Receipt(binding.id().toString(),binding.bundle().toString(),token.token(),text,sha,signedManifest.token());
        return new Work(binding,baseline,issued,expires,token.keyId(),receipt,parts);
    }
    public String signAck(TemporaryStore.Stored row,TemporaryCommands.Ack command,Instant time) {
        return signer.sign(new AckProof("acrylic-outbound","acrylic-pda-temporary-activation",row.id().toString(),row.receipt().bundleId(),
            row.receipt().manifestSha256(),json.digest(command),row.recoveryEpoch(),row.userId().toString(),row.deviceId().toString(),
            command.activatedAt().truncatedTo(ChronoUnit.MILLIS).toString(),time.toString())).token();
    }
    public record Binding(UUID id,UUID bundle,long sequence,String recoveryEpoch,PdaSessions.Principal principal,Instant expiresAtLimit) {
        public Binding(UUID id,UUID bundle,long sequence,String recoveryEpoch,PdaSessions.Principal principal) {
            this(id,bundle,sequence,recoveryEpoch,principal,null);
        }
    }
    public record Work(Binding binding,TemporaryBaseline.View baseline,Instant issued,Instant expires,String keyId,
        TemporaryCommands.Receipt receipt,List<PreparationArchive> parts) { }
    public record Identity(UUID authorizationId,UUID bundleId,UUID userId,UUID deviceId,UUID warehouseId,String authorizationEpoch,
        String userAuthVersion,String deviceVersion,String serverRecoveryEpoch) { }
    public record Manifest(int schemaVersion,String purpose,Identity identity,String inventorySnapshotId,String inventoryVersion,
        String mappingRevision,String policyVersion,String barcodeRuleVersion,String issuedAt,String expiresAt,List<PreparationArchive.Part> parts) { }
    private record Claims(String iss,String aud,String jti,String purpose,Identity identity,String manifestSha256,String scope,long iat,long exp) { }
    private record ManifestProof(String iss,String aud,String authorizationId,String bundleId,String manifestSha256,long iat,long exp) { }
    private record AckProof(String iss,String aud,String authorizationId,String bundleId,String manifestSha256,String commandSha256,
        String serverRecoveryEpoch,String userId,String deviceId,String activatedAt,String acknowledgedAt) { }
}
