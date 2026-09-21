package com.acrylic.outbound.temporary.command;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

/** Server facts only; callers receive copies, never the mutable internal snapshot. */
public final class CommandContext {
    private final JsonNode facts;
    public CommandContext(JsonNode facts) { this.facts=facts.deepCopy(); }
    public JsonNode json() { return facts.deepCopy(); }
    public String text(String key) { var value=facts.get(key);return value==null || value.isNull()?null:value.asText(); }
    public UUID id(String key) { String value=text(key);return value==null?null:UUID.fromString(value); }
    public long version(String key) { return Long.parseLong(text(key)); }
}
