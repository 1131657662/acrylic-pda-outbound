package com.acrylic.outbound.temporary;

import com.acrylic.outbound.common.Ids;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class TemporaryPreparationService {
    private final TemporaryAccess access;private final TemporaryBaseline baseline;private final TemporaryPayloads payloads;
    private final TemporaryIssuer issuer;private final TemporaryStore store;private final TemporaryEvents events;
    public TemporaryPreparationService(TemporaryAccess access,TemporaryBaseline baseline,TemporaryPayloads payloads,
        TemporaryIssuer issuer,TemporaryStore store,TemporaryEvents events) {
        this.access=access;this.baseline=baseline;this.payloads=payloads;this.issuer=issuer;this.store=store;this.events=events;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public TemporaryBaseline.View baseline(String epoch) { return baseline.read(access.lock(epoch).warehouseId()); }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public TemporaryCommands.Receipt prepare(TemporaryCommands.Context context,TemporaryCommands.Prepare command) {
        var principal=access.lock(context.epoch());
        var ticket=events.prepare(principal,context,new TemporaryEvents.Command("prepare",null,command));
        if(ticket.replay()!=null) { var row=store.get(ticket.replay());access.owned(row,principal);return row.receipt(); }
        var fixed=baseline.require(principal.warehouseId(),command);
        var parts=payloads.build(fixed,context.epoch());
        var binding=new TemporaryIssuer.Binding(Ids.next(),Ids.next(),store.nextSequence(principal),context.epoch(),principal);
        var work=issuer.issue(binding,fixed,parts);store.insert(work);events.save(ticket,binding.id());
        events.audit(store.get(binding.id()),"TEMPORARY_PREPARATION_ISSUED",context.requestId());return work.receipt();
    }
}
