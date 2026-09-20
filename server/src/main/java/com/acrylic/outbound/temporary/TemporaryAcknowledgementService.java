package com.acrylic.outbound.temporary;

import com.acrylic.outbound.common.BusinessException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class TemporaryAcknowledgementService {
    private final TemporaryAccess access;private final TemporaryStore store;private final TemporaryEvents events;private final TemporaryIssuer issuer;
    public TemporaryAcknowledgementService(TemporaryAccess access,TemporaryStore store,TemporaryEvents events,TemporaryIssuer issuer) {
        this.access=access;this.store=store;this.events=events;this.issuer=issuer;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public TemporaryCommands.Acknowledgement acknowledge(UUID id,TemporaryCommands.Context context,TemporaryCommands.Ack command) {
        var principal=access.lock(context.epoch());var row=store.get(id);access.usable(row,principal,context.epoch());
        var ticket=events.prepare(principal,context,new TemporaryEvents.Command("ack",id,command));
        if(ticket.replay()!=null) return store.ack(row);
        validate(row,command);String digest=events.digest(command);var previous=store.ack(row);
        if(previous==null) {
            // A delayed receipt records the original activation; it never grants or extends work authority.
            Instant now=Instant.now().truncatedTo(ChronoUnit.MILLIS);
            store.acknowledge(row,new TemporaryStore.AckWrite(digest,command,now,issuer.signAck(row,command,now)));
            events.audit(row,"TEMPORARY_PREPARATION_ACKNOWLEDGED",context.requestId());
        } else store.requireAckDigest(id,digest);
        events.save(ticket,id);return store.ack(row);
    }
    private void validate(TemporaryStore.Stored row,TemporaryCommands.Ack command) {
        if(!row.receipt().bundleId().equals(command.bundleId().toString()) || !row.receipt().manifestSha256().equals(command.manifestSha256())
            || !row.authorizationEpoch().equals(command.authorizationEpoch()))
            throw new BusinessException(409,"TEMPORARY_ACTIVATION_MISMATCH","激活确认与原临时准备资料不一致");
        if(command.activatedAt().isBefore(row.issuedAt()) || !command.activatedAt().isBefore(row.expiresAt())
            || command.activatedAt().isAfter(Instant.now().plusSeconds(300)))
            throw new BusinessException(400,"INVALID_ACTIVATION_TIME","临时资料激活时间不在授权范围，请联网核对");
    }
}
