package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.independent.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Service
public class RelayIndependentReview {
    private final RelayOriginals originals;
    private final RelayAccess origins;private final IndependentCredentialHistory history;private final IndependentCredentialProof proof;
    public RelayIndependentReview(RelayOriginals originals,RelayAccess origins,IndependentCredentialHistory history,IndependentCredentialProof proof) {
        this.originals=originals;this.origins=origins;this.history=history;this.proof=proof;
    }
    @Transactional(readOnly=true)
    public JsonNode read(UUID warehouse,UUID id) {
        var selected=originals.read(warehouse,id);origins.reviewIndependent(warehouse);var row=selected.row();var bundle=selected.bundle();
        if(bundle.independent()==null)throw new BusinessException(409,"ORIGINAL_NOT_INDEPENDENT","该原件不是独立采集来源");
        var credential=bundle.independent().path("credential");boolean trusted=origins.trusted(bundle,false);
        var status=proof.verify(credential,history.find(credential));
        var result=object().put("relayId",id.toString()).put("exportId",bundle.exportId().toString()).put("archiveSha256",row.archiveSha256())
            .put("originalTaskId",bundle.original().localOrder().toString()).put("credentialId",text(credential,"credentialId"))
            .put("sourceTrust",trusted?"TRUSTED":"UNVERIFIED").put("authorizationState",status.state()).put("authorizationReason",status.reason())
            .put("currentSubjectState",history.currentSubjectState(credential)).put("pendingReconciliation",true)
            .put("completeBoundaryVerified",false).put("newCollectionAuthorized",false).put("businessProcessed",false).put("localDeletionAllowed",false);
        return result;
    }
}
