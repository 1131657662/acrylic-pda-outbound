package com.acrylic.outbound.temporary.draft;

import com.acrylic.outbound.submissions.SubmissionJson;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class DraftEvidenceVerifyService {
    private final DraftEvidenceAccess access;
    private final DraftEvidenceStore store;
    private final DraftEvidenceOpenService opens;
    private final DraftEvidenceReceipts receipts;
    private final DraftEvidenceIntegrity integrity;
    public DraftEvidenceVerifyService(DraftEvidenceAccess access,DraftEvidenceStore store,DraftEvidenceOpenService opens,
        DraftEvidenceReceipts receipts,DraftEvidenceIntegrity integrity) {
        this.access=access;this.store=store;this.opens=opens;this.receipts=receipts;this.integrity=integrity;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public byte[] verify(UUID evidence,UUID key,String epoch,byte[] request) {
        var body=DraftEvidenceWire.parse(request,Set.of("evidenceId","rootSha256"));
        DraftEvidenceWire.require(SubmissionJson.uuid(body,"evidenceId").equals(evidence));
        var actor=access.current();var header=store.required(evidence);var original=opens.checked(header,actor);
        DraftEvidenceWire.require(header.root().equals(body.path("rootSha256").textValue()));
        byte[] replay=receipts.existing(actor.userId(),key,evidence,"VERIFY",request);
        if(replay!=null) { integrity.verifiedReceipt(header,original,replay);return replay; }
        DraftEvidenceWire.require(access.lock(epoch).equals(actor));store.lock(evidence);
        header=store.required(evidence);original=opens.checked(header,actor);
        replay=receipts.existing(actor.userId(),key,evidence,"VERIFY",request);
        if(replay!=null) { integrity.verifiedReceipt(header,original,replay);return replay; }
        DraftEvidenceWire.require(!store.complete(evidence));
        var result=integrity.verify(header,original);
        var detail=SubmissionJson.object().put("recordCount",header.count()).put("byteCount",header.bytes())
            .put("ownCaptureCount",result.ownCaptureCount()).put("otherCaptureCount",result.otherCaptureCount()).put("archiveCount",result.archiveCount());
        byte[] response=receipts.insert(header,key,"VERIFY",request,detail);store.complete(evidence,actor.userId(),key);return response;
    }
}
