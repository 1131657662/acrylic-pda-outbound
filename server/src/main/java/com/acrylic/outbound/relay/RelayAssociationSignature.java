package com.acrylic.outbound.relay;

import com.acrylic.outbound.pda.OfflineSigner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Component
public class RelayAssociationSignature {
    private final OfflineSigner signer;
    public RelayAssociationSignature(OfflineSigner signer) { this.signer=signer; }
    public ObjectNode create(RelayAssociationStore.Row row) {
        var claims=claims(row);var signed=signer.sign(claims);return claims.put("signedProof",signed.token()).put("signingKeyId",signed.keyId());
    }
    private ObjectNode claims(RelayAssociationStore.Row row) {
        var s=row.scope();var original=row.context().path("original");
        return object().put("purpose","INDEPENDENT_ASSOCIATION_EVIDENCE_V1").put("associationId",row.id().toString())
            .put("warehouseId",s.warehouse().toString()).put("relayId",s.relay().toString()).put("deviceId",s.device().toString())
            .put("taskId",s.task().toString()).put("targetOrderId",s.target().toString()).put("supervisorId",s.actor().toString())
            .put("physicalCheckId",s.physical().toString()).put("archiveSha256",text(original,"archiveSha256"))
            .put("closureSha256",text(original,"closureSha256")).put("requestSha256",sha(row.request())).put("contextSha256",sha(row.context()))
            .put("recordedAt",row.at().toString()).put("associationRecorded",true).put("pendingReconciliation",true)
            .put("newCollectionAuthorized",false).put("stockAvailable",false).put("businessProcessed",false)
            .put("releaseAllowed",false).put("localDeletionAllowed",false);
    }
    public void verify(RelayAssociationStore.Row row) {
        try {
            var parts=text(row.receipt(),"signedProof").split("\\.",-1);if(parts.length!=3)throw RelayAssociationStore.corrupt();
            var header=parse(decode(parts[0]));RelayPhysicalPolicy.exact(header,"alg typ kid");
            if(!text(header,"alg").equals("RS256") || !text(header,"typ").equals("JWT")
                || !text(header,"kid").equals(text(row.receipt(),"signingKeyId")))throw RelayAssociationStore.corrupt();
            var key=signer.publicKeyFor(text(header,"kid"));if(key==null)throw RelayAssociationStore.corrupt();
            var verifier=Signature.getInstance("SHA256withRSA");
            verifier.initVerify(KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(key.publicKey()))));
            verifier.update((parts[0]+"."+parts[1]).getBytes(StandardCharsets.US_ASCII));
            var expected=claims(row);var actual=row.receipt().deepCopy();((ObjectNode)actual).remove(List.of("signedProof","signingKeyId"));
            if(!verifier.verify(decode(parts[2])) || !parse(decode(parts[1])).equals(expected) || !actual.equals(expected))throw RelayAssociationStore.corrupt();
        } catch(GeneralSecurityException|IllegalArgumentException error) { throw RelayAssociationStore.corrupt(); }
    }
    private byte[] decode(String value) {
        var raw=Base64.getUrlDecoder().decode(value);
        if(!Base64.getUrlEncoder().withoutPadding().encodeToString(raw).equals(value))throw RelayAssociationStore.corrupt();return raw;
    }
}
