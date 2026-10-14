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
public class RelayConflictSignature {
    private final OfflineSigner signer;
    public RelayConflictSignature(OfflineSigner signer) { this.signer=signer; }
    public ObjectNode create(RelayConflictStore.Row row) {
        var claims=claims(row);var signed=signer.sign(claims);return claims.put("signedProof",signed.token()).put("signingKeyId",signed.keyId());
    }
    private ObjectNode claims(RelayConflictStore.Row row) {
        var s=row.scope();var original=row.context().path("selected").path("context").path("original");
        var result=object().put("purpose","INDEPENDENT_CONFLICT_EVIDENCE_V1").put("decisionId",row.id().toString())
            .put("warehouseId",s.warehouse().toString()).put("relayId",s.relay().toString()).put("deviceId",s.device().toString())
            .put("taskId",s.task().toString()).put("supervisorId",s.actor().toString()).put("decision","ASSIGN_TO_SELECTED_TASK")
            .put("archiveSha256",text(original,"archiveSha256")).put("closureSha256",text(original,"closureSha256"))
            .put("requestSha256",sha(row.request())).put("contextSha256",sha(row.context())).put("recordedAt",row.at().toString())
            .put("conflictDecisionRecorded",true).put("pendingReconciliation",true).put("associationAuthorized",false)
            .put("newCollectionAuthorized",false).put("stockAvailable",false).put("businessProcessed",false)
            .put("releaseAllowed",false).put("localDeletionAllowed",false);
        if(row.request().has("predecessorDecisionId"))result.put("predecessorDecisionId",text(row.request(),"predecessorDecisionId"))
            .put("predecessorReceiptSha256",sha(row.context().path("predecessorReceipt")));return result;
    }
    public void verify(RelayConflictStore.Row row) {
        try {
            var parts=text(row.receipt(),"signedProof").split("\\.",-1);if(parts.length!=3)throw RelayConflictStore.corrupt();
            var header=parse(decode(parts[0]));RelayPhysicalPolicy.exact(header,"alg typ kid");
            if(!text(header,"alg").equals("RS256") || !text(header,"typ").equals("JWT")
                || !text(header,"kid").equals(text(row.receipt(),"signingKeyId")))throw RelayConflictStore.corrupt();
            var key=signer.publicKeyFor(text(header,"kid"));if(key==null)throw RelayConflictStore.corrupt();
            var verifier=Signature.getInstance("SHA256withRSA");
            verifier.initVerify(KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(key.publicKey()))));
            verifier.update((parts[0]+"."+parts[1]).getBytes(StandardCharsets.US_ASCII));
            var expected=claims(row);var actual=row.receipt().deepCopy();((ObjectNode)actual).remove(List.of("signedProof","signingKeyId"));
            if(!verifier.verify(decode(parts[2])) || !parse(decode(parts[1])).equals(expected) || !actual.equals(expected))throw RelayConflictStore.corrupt();
        } catch(GeneralSecurityException|IllegalArgumentException error) { throw RelayConflictStore.corrupt(); }
    }
    private byte[] decode(String value) {
        var raw=Base64.getUrlDecoder().decode(value);
        if(!Base64.getUrlEncoder().withoutPadding().encodeToString(raw).equals(value))throw RelayConflictStore.corrupt();return raw;
    }
}
