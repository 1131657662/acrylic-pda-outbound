package com.acrylic.outbound.relay;

import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Binds a file result without replacing the original business receipt. */
@Component
public class RelayReceipts {
    private final OfflineSigner signer;private final RelayStore store;
    public RelayReceipts(OfflineSigner signer,RelayStore store) { this.signer=signer;this.store=store; }
    public RelayStore.Result bind(RelayStore.Row row,RelayArchive.Bundle b,JsonNode business,UUID actor) {
        String digest=SubmissionJson.sha(business);var old=store.matching(row.id(),digest);if(old!=null)return old;
        UUID id=Ids.next();var o=b.original();
        var receipt=SubmissionJson.object().put("purpose","PDA_ORIGINAL_RELAY_RESULT_V1").put("relayId",row.id().toString())
            .put("resultId",id.toString()).put("exportId",b.exportId().toString()).put("archiveSha256",b.archiveSha256())
            .put("deviceKeySha256",b.keySha256()).put("operatorId",o.user().toString()).put("deviceId",o.device().toString())
            .put("warehouseId",o.warehouse().toString()).put("localOrderId",o.localOrder().toString())
            .put("submissionId",b.control().id().toString()).put("payloadSha256",SubmissionJson.sha(b.body()))
            .put("sourceRecoveryEpoch",b.control().epoch()).put("relayOperatorId",actor.toString()).put("localDeletionAllowed",false);
        receipt.set("businessReceipt",business);
        var claims=SubmissionJson.object().put("iss","acrylic-outbound").put("aud","acrylic-pda-relay-result")
            .put("iat",Instant.now().getEpochSecond());claims.set("receipt",receipt);
        var result=new RelayStore.Result(id,row.id(),SubmissionJson.canonical(receipt.put("signedRelayReceipt",signer.sign(claims).token())));
        store.result(result,digest,actor);return result;
    }
}
