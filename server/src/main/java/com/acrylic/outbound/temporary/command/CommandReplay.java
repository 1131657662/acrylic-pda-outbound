package com.acrylic.outbound.temporary.command;

import java.util.Arrays;
import org.springframework.stereotype.Component;

@Component
public class CommandReplay {
    private final CommandStore store;private final CommandReceiptIntegrity integrity;
    public CommandReplay(CommandStore store,CommandReceiptIntegrity integrity) { this.store=store;this.integrity=integrity; }
    public CommandRecord find(CommandIdentity.Actor actor,String key) {
        var row=store.find(actor.id(),key);if(row==null)return null;
        // A partial request is corruption, not permission to execute again.
        if(row.id("id")==null)throw CommandErrors.corrupt();
        if(!actor.warehouse().equals(row.id("warehouse_id")) || !CommandPermissions.mayRead(actor.permissions(),row.text("command_type")))
            throw CommandErrors.missing();
        integrity.verify(row);return row;
    }
    public byte[] existing(CommandIdentity.Actor actor,CommandInput input) {
        var row=find(actor,input.key());if(row==null)return null;
        if(!input.digest().equals(row.text("command_sha256")) || !Arrays.equals(input.bytes(),row.bytes("canonical_command_bytes")))
            throw CommandErrors.conflict();
        return row.bytes("original_response_bytes");
    }
}
