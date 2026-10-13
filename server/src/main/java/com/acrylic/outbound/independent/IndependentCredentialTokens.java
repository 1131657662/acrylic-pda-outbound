package com.acrylic.outbound.independent;

import com.acrylic.outbound.pda.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class IndependentCredentialTokens {
    private final OfflineSigner signer;
    public IndependentCredentialTokens(OfflineSigner signer) { this.signer=signer; }
    public String sign(Input input) {
        var actor=input.actor();var claims=new LinkedHashMap<String,Object>();
        claims.put("iss","acrylic-outbound");claims.put("aud","acrylic-pda-independent-v1");
        claims.put("purpose","INDEPENDENT_CAPTURE");claims.put("scope","pda:independent");
        claims.put("credentialId",input.id().toString());claims.put("operatorId",actor.userId().toString());
        claims.put("deviceId",actor.deviceId().toString());claims.put("warehouseId",actor.warehouseId().toString());
        claims.put("deviceKeySha256",input.keySha256());claims.put("serverRecoveryEpoch",input.epoch());
        claims.put("userAuthVersion",actor.userAuthVersion());claims.put("deviceVersion",actor.deviceVersion());
        claims.put("issuedAt",input.issued().toString());claims.put("expiresAt",input.issued().plusSeconds(48*3600).toString());
        claims.put("capabilities",Map.of("mayCollectIndependent",true,"maySubmit",false,"mayConfirm",false,
            "stockAvailable",false,"localDeletionAllowed",false));
        return signer.sign(claims).token();
    }
    public record Input(UUID id,PdaSessions.Principal actor,String epoch,String keySha256,Instant issued) { }
}
