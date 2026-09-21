package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class CommandReadService {
    private final CommandIdentity identities;private final CommandReplay replay;private final CommandCurrentEffect effect;
    public CommandReadService(CommandIdentity identities,CommandReplay replay,CommandCurrentEffect effect) {
        this.identities=identities;this.replay=replay;this.effect=effect;
    }
    public View read(UUID warehouse,String key) {
        key(key);var actor=identities.current(warehouse);var row=required(actor,key);
        return new View(new String(row.bytes("original_response_bytes"),StandardCharsets.UTF_8),row.text("original_response_sha256"),effect.evaluate(row,actor));
    }
    public byte[] original(UUID warehouse,String key) {
        key(key);return required(identities.current(warehouse),key).bytes("original_response_bytes");
    }
    private CommandRecord required(CommandIdentity.Actor actor,String key) {
        var row=replay.find(actor,key);if(row==null)throw CommandErrors.missing();return row;
    }
    private void key(String key) {
        try { if(!UUID.fromString(key).toString().equals(key))throw new IllegalArgumentException(); }
        catch(IllegalArgumentException invalid) { throw new BusinessException(400,"INVALID_COMMAND_KEY","原键须为规范UUID"); }
    }
    public record View(String originalCommandReceiptUtf8,String originalResponseSha256,JsonNode currentEffect) { }
}
