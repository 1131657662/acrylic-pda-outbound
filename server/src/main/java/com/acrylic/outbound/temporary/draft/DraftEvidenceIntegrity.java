package com.acrylic.outbound.temporary.draft;

import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeObjectFiles;
import com.acrylic.outbound.temporary.protocol.LocalDraftStopSnapshotProtocol;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.io.IOException;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class DraftEvidenceIntegrity {
    private final DraftEvidenceStore store;
    private final SourceEvidenceIntakeObjectFiles files;
    private final DraftEvidenceArchives archives;
    public DraftEvidenceIntegrity(DraftEvidenceStore store,SourceEvidenceIntakeObjectFiles files,DraftEvidenceArchives archives) {
        this.store=store;this.files=files;this.archives=archives;
    }
    public LocalDraftStopSnapshotProtocol.SnapshotIntegrity verify(DraftEvidenceStore.Header header,DraftEvidenceOpenService.Open original) {
        try {
            return LocalDraftStopSnapshotProtocol.verify(DraftEvidenceWire.bytes(original.value().path("proof")),
                DraftEvidenceWire.bytes(original.value().path("metadata")),original.expected(),()->new Originals(header));
        } catch(RuntimeException invalid) { throw DraftEvidenceWire.unavailable(); }
    }
    public void item(DraftEvidenceStore.Header header,long ordinal) {
        var row=store.item(header.evidence(),ordinal);DraftEvidenceWire.require(row!=null);
        try { files.verify(row.stored()); } catch(IOException invalid) { throw DraftEvidenceWire.unavailable(); }
    }
    public void verifiedReceipt(DraftEvidenceStore.Header header,DraftEvidenceOpenService.Open original,byte[] receipt) {
        var result=verify(header,original);var detail=SubmissionJson.parse(receipt).path("detail");
        DraftEvidenceWire.require(detail.path("ownCaptureCount").longValue()==result.ownCaptureCount()
            && detail.path("otherCaptureCount").longValue()==result.otherCaptureCount() && detail.path("archiveCount").longValue()==result.archiveCount());
    }
    private final class Originals implements Iterator<LocalDraftStopSnapshotProtocol.Item> {
        private final DraftEvidenceStore.Header header;
        private long after;
        private Iterator<DraftEvidenceStore.Item> page=List.<DraftEvidenceStore.Item>of().iterator();
        private boolean ended;
        Originals(DraftEvidenceStore.Header header) { this.header=header; }
        @Override public boolean hasNext() {
            if(!page.hasNext() && !ended) { var rows=store.page(header.evidence(),after);page=rows.iterator();ended=rows.isEmpty(); }
            return page.hasNext();
        }
        @Override public LocalDraftStopSnapshotProtocol.Item next() {
            if(!hasNext())throw new NoSuchElementException();var item=page.next();after=item.ordinal();
            try {
                byte[] raw=files.read(item.stored());
                if("ARCHIVE".equals(item.kind()))archives.require(header,item,raw);
                return item.wire(raw);
            }
            catch(IOException invalid) { throw DraftEvidenceWire.unavailable(); }
        }
    }
}
