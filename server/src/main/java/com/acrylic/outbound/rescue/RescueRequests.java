package com.acrylic.outbound.rescue;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.devices.DeviceStore;
import java.security.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class RescueRequests {
    private final RescueAccess access;private final RescueRequestProof proof;private final RescueStore store;
    private final RescueJournal journal;private final RescueEnabled enabled;
    public RescueRequests(RescueAccess access,RescueRequestProof proof,RescueStore store,RescueJournal journal,RescueEnabled enabled) {
        this.access=access;this.proof=proof;this.store=store;this.journal=journal;this.enabled=enabled;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public RescueCommands.RequestReceipt create(RescueAccess.Context context,RescueCommands.Request input) {
        enabled.require();var actor=access.pda();
        var document=proof.verify(input,actor,access.device(actor.deviceId()),context.key());
        var device=access.request(actor,document.rescuerId(),context.epoch());
        document=proof.verify(input,actor,device,context.key());
        var command=new RescueJournal.Command(new IdempotencyRequests.Key(actor.userId(),"rescue.request",context.key()),input);
        var replay=journal.replay(command);
        if(replay!=null) return store.request(replay,false).receipt();
        store.requireRescuer(document.rescuerId(),document.warehouseId());store.requireFormalTargets(document);
        UUID id=Ids.next();store.request(id,document,input,keyDigest(device));journal.record(command,id,context.requestId());
        return store.request(id,false).receipt();
    }
    static String keyDigest(DeviceStore.Device device) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Base64.getDecoder().decode(device.publicKey()))); }
        catch(GeneralSecurityException error) { throw new IllegalStateException(error); }
    }
}
