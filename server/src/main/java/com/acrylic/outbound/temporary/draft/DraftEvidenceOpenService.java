package com.acrylic.outbound.temporary.draft;

import com.acrylic.outbound.pda.PdaSessions;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.LocalDraftStopProtocol;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class DraftEvidenceOpenService {
    private final DraftEvidenceAccess access;
    private final DraftEvidencePreparation preparation;
    private final DraftEvidenceStore store;
    private final DraftEvidenceReceipts receipts;
    public DraftEvidenceOpenService(DraftEvidenceAccess access,DraftEvidencePreparation preparation,DraftEvidenceStore store,DraftEvidenceReceipts receipts) {
        this.access=access;this.preparation=preparation;this.store=store;this.receipts=receipts;
    }
    public record Open(JsonNode value,LocalDraftStopProtocol.Expected expected,LocalDraftStopProtocol.VerifiedSignature signed) { }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public byte[] open(UUID evidence,UUID key,String epoch,byte[] bytes) {
        var actor=access.current();var original=checked(bytes,actor);
        DraftEvidenceWire.require(evidence.equals(original.expected().evidenceId()));
        var replay=receipts.existing(actor.userId(),key,evidence,"OPEN",bytes);
        if(replay!=null) { checked(store.required(evidence),actor);return replay; }
        var locked=access.lock(epoch);DraftEvidenceWire.require(locked.equals(actor));
        original=checked(bytes,locked);
        replay=receipts.existing(actor.userId(),key,evidence,"OPEN",bytes);
        if(replay!=null) { checked(store.required(evidence),locked);return replay; }
        DraftEvidenceWire.require(!store.sourceExists(actor.deviceId(),original.expected().clientOrderId()) && store.find(evidence)==null);
        store.insert(original.expected(),original.signed(),key,bytes);
        return receipts.insert(store.required(evidence),key,"OPEN",bytes,SubmissionJson.object());
    }
    Open checked(byte[] bytes,PdaSessions.Principal actor) {
        var value=DraftEvidenceWire.parse(bytes,Set.of("metadata","proof"));
        try {
            var expected=preparation.require(value.path("metadata"),actor);
            var signed=LocalDraftStopProtocol.verify(DraftEvidenceWire.bytes(value.path("proof")),DraftEvidenceWire.bytes(value.path("metadata")),expected);
            return new Open(value,expected,signed);
        } catch(IllegalArgumentException invalid) { throw DraftEvidenceWire.invalid(); }
    }
    Open checked(DraftEvidenceStore.Header header,PdaSessions.Principal actor) {
        DraftEvidenceAccess.owner(actor,header);DraftEvidenceWire.require(DraftEvidenceWire.sha(header.openBytes()).equals(header.openSha()));
        Open original=checked(header.openBytes(),actor);var expected=original.expected();var signed=original.signed();
        DraftEvidenceWire.require(expected.evidenceId().equals(header.evidence()) && expected.clientOrderId().equals(header.client())
            && expected.authorizationId().equals(header.authorization()) && expected.sourceRecoveryEpoch()==header.epoch()
            && signed.rootSha256().equals(header.root()) && signed.recordCount()==header.count() && signed.byteCount()==header.bytes());
        return original;
    }
}
