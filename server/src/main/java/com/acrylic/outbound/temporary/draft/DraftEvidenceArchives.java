package com.acrylic.outbound.temporary.draft;

import com.acrylic.outbound.audit.*;
import com.acrylic.outbound.common.JsonText;
import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeObjectFiles;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeAuditArchives;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Component;

/** An uploaded archive key is evidence only; authority comes from configured historical keys. */
@Component
public class DraftEvidenceArchives {
    private final AuditArchiveStore archives;
    private final AuditArchiveReader reader;
    private final OfflineSigner signer;
    private final JsonText json;
    private final DraftEvidenceStore store;
    private final SourceEvidenceIntakeObjectFiles files;
    public DraftEvidenceArchives(AuditArchiveStore archives,AuditArchiveReader reader,OfflineSigner signer,
        JsonText json,DraftEvidenceStore store,SourceEvidenceIntakeObjectFiles files) {
        this.archives=archives;this.reader=reader;this.signer=signer;this.json=json;this.store=store;this.files=files;
    }

    public void require(DraftEvidenceStore.Header header,DraftEvidenceStore.Item item,byte[] raw) {
        var wrapper=SubmissionJson.parse(raw);
        DraftEvidenceWire.require(wrapper.size()==2 && wrapper.path("originalProof").isTextual()
            && wrapper.path("originalSigningKey").isTextual());
        var proof=SubmissionJson.parse(wrapper.path("originalProof").textValue());
        var uploadedKey=SubmissionJson.parse(wrapper.path("originalSigningKey").textValue());
        long epoch=SubmissionJson.number(proof,"recoveryEpoch"),after=SubmissionJson.number(proof,"afterSeq");
        long through=SubmissionJson.number(proof,"throughSeq");
        DraftEvidenceWire.require("ARCHIVE".equals(item.kind()) && "ARCHIVED".equals(item.status())
            && epoch>0 && epoch<=header.epoch() && through==item.sequence()
            && item.record().equals(SourceEvidenceIntakeAuditArchives.recordId(header.device(),epoch,after)));
        String token=SubmissionJson.text(proof,"signedArchive");
        DraftEvidenceWire.require(token.length()<=1048576 && token.split("\\.",-1).length==3);
        var tokenHeader=SubmissionJson.parse(Base64.getUrlDecoder().decode(token.split("\\.",-1)[0]));
        var trusted=signer.publicKeyFor(SubmissionJson.text(tokenHeader,"kid"));
        DraftEvidenceWire.require(trusted!=null && uploadedKey.equals(SubmissionJson.parse(json.encode(trusted))));
        var stored=archives.find(Long.toString(epoch),header.device(),after);
        DraftEvidenceWire.require(stored!=null && stored.equals(proof));
        for(var event:reader.read(proof,header.device(),header.warehouse())) requireSnapshotEvent(header,event);
    }

    private void requireSnapshotEvent(DraftEvidenceStore.Header header,JsonNode event) {
        var item=store.audit(header.evidence(),SubmissionJson.number(event,"localSeq"));
        DraftEvidenceWire.require(item!=null && item.record().equals(SubmissionJson.uuid(event,"id")));
        try {
            byte[] raw=files.read(item.stored());
            DraftEvidenceWire.require(Arrays.equals(raw,SubmissionJson.canonical(event).getBytes(StandardCharsets.UTF_8)));
        } catch(java.io.IOException unavailable) { throw DraftEvidenceWire.unavailable(); }
    }
}
