package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Service
public class RelayIntake {
    private final RelayEnabled enabled;private final RelayAccess access;private final RelayFiles files;
    private final RelayStore store;private final RelayJournal journal;
    public RelayIntake(RelayEnabled enabled,RelayAccess access,RelayFiles files,RelayStore store,RelayJournal journal) {
        this.enabled=enabled;this.access=access;this.files=files;this.store=store;this.journal=journal;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public JsonNode receive(Context c,byte[] bytes) {
        enabled.require();access.read(c.warehouse());var bundle=RelayArchive.read(bytes);
        equal(c.warehouse(),bundle.original().warehouse(),"原包仓库与中转范围不符");
        UUID actor=access.write(c.warehouse(),c.epoch(),null);
        var command=new RelayJournal.Command(new IdempotencyRequests.Key(actor,"relay.receive",c.key()),
            Map.of("archiveSha256",bundle.archiveSha256(),"warehouseId",c.warehouse().toString()));
        UUID replay=journal.replay(command);if(replay!=null)return receipt(store.get(replay));
        var existing=store.byExport(bundle.exportId());
        if(existing!=null && !existing.archiveSha256().equals(bundle.archiveSha256()))
            throw new BusinessException(409,"IDEMPOTENCY_CONTENT_MISMATCH","同一原导出ID出现不同字节，须保留原包核对");
        files.store(bytes);
        if(existing==null) {
            UUID id=Ids.next();store.insert(id,bundle,actor,access.trusted(bundle,true));existing=store.get(id);
        }
        journal.record(command,existing.id(),c.requestId());return receipt(existing);
    }
    @Transactional(readOnly=true)
    public JsonNode read(UUID warehouse,UUID id) {
        enabled.require();access.read(warehouse);var row=store.get(id);scope(row,warehouse);return receipt(row);
    }
    @Transactional(readOnly=true)
    public byte[] original(UUID warehouse,UUID id) {
        enabled.require();access.read(warehouse);var row=store.get(id);scope(row,warehouse);return files.read(row.archiveSha256());
    }
    private JsonNode receipt(RelayStore.Row row) {
        var bundle=RelayArchive.read(files.read(row.archiveSha256()));var receipt=object().put("relayId",row.id().toString())
            .put("exportId",bundle.exportId().toString()).put("archiveSha256",row.archiveSha256()).put("sourceTrust",row.trust())
            .put("receptionState","ORIGINALS_STORED").put("businessProcessed",false).put("localDeletionAllowed",false);
        if(bundle.control()==null)receipt.putNull("submissionId");else receipt.put("submissionId",bundle.control().id().toString());return receipt;
    }
    public static void scope(RelayStore.Row row,UUID warehouse) {
        if(!row.warehouse().equals(warehouse))throw new BusinessException(404,"ORIGINAL_RELAY_NOT_FOUND","原件中转不存在或无权访问");
    }
    public record Context(UUID warehouse,String epoch,String key,String requestId) { }
}
