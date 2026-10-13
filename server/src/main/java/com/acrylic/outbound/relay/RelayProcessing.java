package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.submissions.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Service
public class RelayProcessing {
    private final RelayEnabled enabled;private final RelayAccess access;private final RelayOriginals originals;private final RelayStore relays;
    private final SubmissionStore submissions;private final SubmissionService service;private final RelayReceipts receipts;private final RelayJournal journal;
    public RelayProcessing(RelayEnabled enabled,RelayAccess access,RelayOriginals originals,RelayStore relays,SubmissionStore submissions,
        SubmissionService service,RelayReceipts receipts,RelayJournal journal) {
        this.enabled=enabled;this.access=access;this.originals=originals;this.relays=relays;this.submissions=submissions;
        this.service=service;this.receipts=receipts;this.journal=journal;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public JsonNode process(RelayIntake.Context c,UUID id) {
        var selected=originals.read(c.warehouse(),id);var row=selected.row();var bundle=selected.bundle();
        if(bundle.control()==null)throw new BusinessException(409,"ORIGINAL_NOT_FROZEN","仅接收证据，不能补造冻结批次或自动记账");
        UUID operator=access.write(c.warehouse(),c.epoch(),bundle.original().user());
        var actor=access.original(bundle,c.epoch());var command=command(c,row,bundle,operator);
        UUID replay=journal.replay(command);if(replay!=null)return parse(relays.result(replay).text());
        // Inspect the original ID before deciding whether any business work is necessary.
        var prior=submissions.find(bundle.control().id());validate(prior,bundle);
        var head=submissions.lock(bundle.control(),actor);validate(head,bundle);JsonNode business;
        if(head.receipt()!=null)business=service.response(head);
        else { submissions.current(head);business=service.accept(head,bundle.body()); }
        var result=receipts.bind(row,bundle,business,operator);
        journal.record(command,result.id(),c.requestId());return parse(result.text());
    }
    @Transactional(readOnly=true)
    public JsonNode query(UUID warehouse,UUID id) {
        var selected=originals.read(warehouse,id);var bundle=selected.bundle();var result=object().put("relayId",id.toString()).put("localDeletionAllowed",false);
        if(bundle.control()==null) { result.put("state","EVIDENCE_ONLY");result.putNull("businessReceipt");return result; }
        if(!access.trusted(bundle,false))throw new BusinessException(409,"ORIGINAL_SOURCE_UNVERIFIED","原登记设备钥未核验，不得查询业务资料");
        var head=submissions.find(bundle.control().id());validate(head,bundle);
        result.put("state",head==null?"NOT_FOUND":head.status());result.set("businessReceipt",head==null?null:service.response(head));return result;
    }
    @Transactional(readOnly=true)
    public String result(UUID warehouse,UUID id) {
        enabled.require();access.read(warehouse);var result=relays.result(id);RelayIntake.scope(relays.get(result.relay()),warehouse);return result.text();
    }
    private void validate(SubmissionStore.Head head,RelayArchive.Bundle bundle) {
        if(head==null)return;equal(head.control(),bundle.control(),"原提交ID来源不符");equal(head.actor(),bundle.original().user(),"原提交ID采集者不符");
        String hash=submissions.payloadHash(head.control().id());
        if(hash!=null && !hash.equals(sha(bundle.body())))throw new BusinessException(409,"IDEMPOTENCY_CONTENT_MISMATCH","同一原提交ID正文不同，须保留原件核对");
        submissions.requireUploadHash(head.control().id(),sha(bundle.body()));
    }
    private RelayJournal.Command command(RelayIntake.Context c,RelayStore.Row row,RelayArchive.Bundle b,UUID operator) {
        return new RelayJournal.Command(new IdempotencyRequests.Key(operator,"relay.process",c.key()),
            Map.of("relayId",row.id().toString(),"archiveSha256",b.archiveSha256(),"submissionId",b.control().id().toString(),
                "collectorId",b.original().user().toString(),"epoch",c.epoch()));
    }
}
