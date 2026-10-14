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

/** Compact server proof binds all original observations; it contains no processing capability. */
@Component
public class RelayPhysicalSignature {
    private final OfflineSigner signer;
    public RelayPhysicalSignature(OfflineSigner signer) { this.signer=signer; }
    public ObjectNode create(UUID id,RelayPhysicalStore.Scope scope,JsonNode request,JsonNode context,java.time.Instant at) {
        var receipt=claims(id,scope,request,context,at);var signed=signer.sign(receipt);
        return receipt.put("signedProof",signed.token()).put("signingKeyId",signed.keyId());
    }
    private ObjectNode claims(UUID id,RelayPhysicalStore.Scope scope,JsonNode request,JsonNode context,java.time.Instant at) {
        var original=context.path("original");return object().put("purpose","INDEPENDENT_PHYSICAL_EVIDENCE_V1").put("receiptId",id.toString())
            .put("warehouseId",scope.warehouse().toString()).put("relayId",scope.relay().toString()).put("supervisorId",scope.actor().toString())
            .put("taskId",text(original,"taskId")).put("archiveSha256",text(original,"archiveSha256")).put("closureSha256",text(original,"closureSha256"))
            .put("requestSha256",sha(request)).put("contextSha256",sha(context)).put("recordedAt",at.toString()).put("physicalCheckRecorded",true)
            .put("pendingReconciliation",true).put("associationAuthorized",false).put("stockAvailable",false).put("businessProcessed",false)
            .put("releaseAllowed",false).put("localDeletionAllowed",false);
    }
    public void verify(RelayPhysicalStore.Check row) {
        try {
            var parts=text(row.receipt(),"signedProof").split("\\.",-1);if(parts.length!=3)throw RelayPhysicalStore.corrupt();
            var header=parse(decode(parts[0]));RelayPhysicalPolicy.exact(header,"alg typ kid");
            if(!text(header,"alg").equals("RS256") || !text(header,"typ").equals("JWT")
                || !text(header,"kid").equals(text(row.receipt(),"signingKeyId")))throw RelayPhysicalStore.corrupt();
            var key=signer.publicKeyFor(text(header,"kid"));if(key==null)throw RelayPhysicalStore.corrupt();
            var verifier=Signature.getInstance("SHA256withRSA");
            verifier.initVerify(KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(key.publicKey()))));
            verifier.update((parts[0]+"."+parts[1]).getBytes(StandardCharsets.US_ASCII));
            var expected=claims(row.id(),row.scope(),row.request(),row.context(),row.at());var actual=row.receipt().deepCopy();
            ((ObjectNode)actual).remove(List.of("signedProof","signingKeyId"));
            if(!verifier.verify(decode(parts[2])) || !parse(decode(parts[1])).equals(expected) || !actual.equals(expected))throw RelayPhysicalStore.corrupt();
        } catch(GeneralSecurityException|IllegalArgumentException error) { throw RelayPhysicalStore.corrupt(); }
    }
    private byte[] decode(String value) {
        var raw=Base64.getUrlDecoder().decode(value);
        if(!Base64.getUrlEncoder().withoutPadding().encodeToString(raw).equals(value))throw RelayPhysicalStore.corrupt();return raw;
    }
}
