package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.temporary.protocol.TemporaryProtocol;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public final class CommandInput {
    private final byte[] bytes;
    private final JsonNode body;
    private final String digest;
    private CommandInput(byte[] bytes) {
        this.bytes=bytes.clone();body=SubmissionJson.parse(bytes);digest=TemporaryProtocol.commandSha256(bytes);
    }
    public static CommandInput parse(byte[] bytes) {
        try { return new CommandInput(TemporaryProtocol.command(bytes)); }
        catch(RuntimeException malformed) { throw new BusinessException(400,"INVALID_TEMPORARY_COMMAND","命令格式或资源大小无效"); }
    }
    public byte[] bytes() { return bytes.clone(); }
    public JsonNode body() { return body.deepCopy(); }
    public JsonNode expected() { return body.get("expected").deepCopy(); }
    public String text(String field) { return body.get(field).textValue(); }
    public UUID uuid(String field) { return UUID.fromString(text(field)); }
    public String kind() { return text("commandType"); }
    public String key() { return text("commandId"); }
    public String digest() { return digest; }
}
