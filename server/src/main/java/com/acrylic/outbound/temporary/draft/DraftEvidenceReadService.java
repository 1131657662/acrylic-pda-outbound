package com.acrylic.outbound.temporary.draft;

import com.acrylic.outbound.submissions.SubmissionJson;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class DraftEvidenceReadService {
    private final DraftEvidenceAccess access;
    private final DraftEvidenceStore store;
    private final DraftEvidenceOpenService opens;
    private final DraftEvidenceReceipts receipts;
    private final DraftEvidenceIntegrity integrity;
    public DraftEvidenceReadService(DraftEvidenceAccess access,DraftEvidenceStore store,DraftEvidenceOpenService opens,
        DraftEvidenceReceipts receipts,DraftEvidenceIntegrity integrity) {
        this.access=access;this.store=store;this.opens=opens;this.receipts=receipts;this.integrity=integrity;
    }
    @Transactional(readOnly=true,isolation=Isolation.READ_COMMITTED)
    public byte[] read(UUID evidence,UUID key) {
        var actor=access.current();var header=store.required(evidence);var original=opens.checked(header,actor);
        byte[] bytes=receipts.read(actor.userId(),key,evidence);var response=SubmissionJson.parse(bytes);
        if("VERIFY".equals(response.path("operationKind").textValue()))integrity.verifiedReceipt(header,original,bytes);
        if("ITEM".equals(response.path("operationKind").textValue()))integrity.item(header,response.path("detail").path("ordinal").longValue());
        return bytes;
    }
}
