package com.acrylic.outbound.temporary.draft;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeObjectFiles;
import java.io.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class DraftEvidenceItemService {
    private final DraftEvidenceAccess access;
    private final DraftEvidenceStore store;
    private final DraftEvidenceOpenService opens;
    private final DraftEvidenceReceipts receipts;
    private final SourceEvidenceIntakeObjectFiles files;
    public DraftEvidenceItemService(DraftEvidenceAccess access,DraftEvidenceStore store,DraftEvidenceOpenService opens,
        DraftEvidenceReceipts receipts,SourceEvidenceIntakeObjectFiles files) {
        this.access=access;this.store=store;this.opens=opens;this.receipts=receipts;this.files=files;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public byte[] item(DraftEvidenceItemRequest request,String epoch) {
        var actor=access.current();var header=store.required(request.evidence());
        var original=opens.checked(header,actor);byte[] descriptor=request.descriptor();
        byte[] replay=receipts.existing(actor.userId(),request.key(),request.evidence(),"ITEM",descriptor);
        if(replay!=null) { verifyStored(request);return replay; }
        DraftEvidenceWire.require(access.lock(epoch).equals(actor));store.lock(request.evidence());
        header=store.required(request.evidence());original=opens.checked(header,actor);
        replay=receipts.existing(actor.userId(),request.key(),request.evidence(),"ITEM",descriptor);
        if(replay!=null) { verifyStored(request);return replay; }
        DraftEvidenceWire.require(!store.complete(request.evidence()) && request.ordinal()<=header.count() && store.item(request.evidence(),request.ordinal())==null);
        long[] totals=store.totals(request.evidence());
        DraftEvidenceWire.require(totals[0]==totals[2] && request.ordinal()==totals[2]+1 && totals[0]<header.count()
            && totals[1]+request.length()+DraftEvidenceWire.bytes(original.value().path("metadata")).length<=header.bytes());
        try {
            var stored=files.store(new ByteArrayInputStream(request.raw()),request.length(),request.sha());
            store.insertItem(request.evidence(),new DraftEvidenceStore.Item(request.ordinal(),request.kind(),request.record(),request.sequence(),request.status(),stored));
        } catch(IOException invalid) { throw DraftEvidenceWire.unavailable(); }
        return receipts.insert(header,request.key(),"ITEM",descriptor,SubmissionJson.object().put("ordinal",request.ordinal()).put("bytesSha256",request.sha()));
    }
    private void verifyStored(DraftEvidenceItemRequest request) {
        var item=store.item(request.evidence(),request.ordinal());
        DraftEvidenceWire.require(item!=null && item.kind().equals(request.kind()) && item.record().equals(request.record())
            && item.sequence()==request.sequence() && item.status().equals(request.status()) && item.stored().sha256().equals(request.sha())
            && item.stored().bytes()==request.length());
        try { files.verify(item.stored()); } catch(IOException invalid) { throw DraftEvidenceWire.unavailable(); }
    }
}
