package com.acrylic.outbound.temporary;

import com.acrylic.outbound.pda.PdaAccess;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service @Transactional(readOnly=true)
public class TemporaryQueries {
    private final PdaAccess pda;private final TemporaryAccess access;private final TemporaryStore store;
    public TemporaryQueries(PdaAccess pda,TemporaryAccess access,TemporaryStore store) { this.pda=pda;this.access=access;this.store=store; }
    public State get(UUID id) {
        var principal=pda.current();var row=store.get(id);access.owned(row,principal);
        return new State(row.receipt(),store.ack(row));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public byte[] content(Content query,String epoch) {
        var principal=access.lock(epoch);var row=store.get(query.id());access.usable(row,principal,epoch);access.unexpired(row);
        if(!row.receipt().bundleId().equals(query.bundleId().toString())) throw TemporaryStore.missing();
        return store.content(query.id(),query.part());
    }
    public record Content(UUID id,UUID bundleId,String part) { }
    public record State(TemporaryCommands.Receipt preparation,TemporaryCommands.Acknowledgement acknowledgement) { }
}
