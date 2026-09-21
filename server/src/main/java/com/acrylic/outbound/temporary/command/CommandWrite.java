package com.acrylic.outbound.temporary.command;

import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;

public record CommandWrite(UUID requestId,UUID receiptId,UUID eventId,Instant recordedAt,
    CommandInput command,CommandContext context,JsonNode actor) {
    public CommandWrite { actor=actor.deepCopy(); }
    @Override public JsonNode actor() { return actor.deepCopy(); }
}
