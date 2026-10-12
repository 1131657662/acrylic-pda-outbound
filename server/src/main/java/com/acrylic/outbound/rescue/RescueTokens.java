package com.acrylic.outbound.rescue;

import com.acrylic.outbound.pda.OfflineSigner;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RescueTokens {
    private final OfflineSigner signer;private final String issuer;
    public RescueTokens(OfflineSigner signer,@Value("${outbound.signing.issuer:acrylic-outbound}") String issuer) {
        this.signer=signer;this.issuer=issuer;
    }
    public String grant(Grant input,List<RescueCommands.Target> scope) {
        var claims=identity(input.request());claims.put("purpose","SUPERVISOR_READ_ONLY_RESCUE");
        claims.put("grantId",input.id());claims.put("issuerId",input.issuer());
        claims.put("orderScope",scope);claims.put("issuedAt",input.issued().toString());
        claims.put("expiresAt",input.expires()==null?null:input.expires().toString());
        claims.put("capabilities",Map.of("readOriginalAllowed",true,"mayResumeScanning",false,"maySubmit",false,
            "releaseAllowed",false,"stockReleased",false,"localDeletionAllowed",false));
        return signer.sign(claims).token();
    }
    public String revoke(Grant input,String authorizationSha256) {
        var claims=identity(input.request());claims.put("purpose","SUPERVISOR_READ_ONLY_RESCUE_REVOCATION");
        claims.put("grantId",input.id());claims.put("issuerId",input.issuer());
        claims.put("revokedAt",input.issued().toString());claims.put("authorizationSha256",authorizationSha256);
        return signer.sign(claims).token();
    }
    private Map<String,Object> identity(RescueStore.Request request) {
        var claims=new LinkedHashMap<String,Object>();claims.put("iss",issuer);claims.put("aud","acrylic-pda-readonly-rescue-v1");
        claims.put("rescuerId",request.rescuerId());claims.put("deviceId",request.deviceId());
        claims.put("operatorId",request.operatorId());claims.put("warehouseId",request.warehouseId());
        claims.put("deviceKeySha256",request.deviceKeySha256());return claims;
    }
    public record Grant(UUID id,RescueStore.Request request,UUID issuer,Instant issued,Instant expires) { }
}
