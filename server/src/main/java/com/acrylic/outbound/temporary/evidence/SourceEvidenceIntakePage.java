package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import com.acrylic.outbound.temporary.protocol.E2aPageItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class SourceEvidenceIntakePage {
    private static final Set<String> FIELDS=Set.of("operation","openOriginalKey","pageCore","pageHash");
    private SourceEvidenceIntakePage() { }

    public static Parsed parse(byte[] requestBytes,UUID expectedOpenKey,SourceEvidenceIntakeOpen.Parsed open) {
        if(open==null || expectedOpenKey==null)throw new IllegalArgumentException("PAGE_OPEN_REQUIRED");
        var request=SourceEvidenceIntakeReplay.canonicalize(requestBytes);
        JsonNode body=SubmissionJson.parse(request.bytes());
        require(body.isObject(),"PAGE_REQUEST_OBJECT");
        var actual=new HashSet<String>();body.fieldNames().forEachRemaining(actual::add);
        require(actual.equals(FIELDS),"PAGE_REQUEST_FIELDS");
        require("PAGE".equals(body.path("operation").textValue()),"PAGE_OPERATION");
        UUID openKey=uuid(body.path("openOriginalKey"));
        require(openKey.equals(expectedOpenKey),"PAGE_OPEN_KEY_MISMATCH");
        require(body.path("pageCore") instanceof ObjectNode,"PAGE_CORE_OBJECT");
        var core=(ObjectNode)body.path("pageCore");
        String hash=E2aProtocol.pageHash(core);
        require(hash.equals(body.path("pageHash").textValue()),"PAGE_HASH_MISMATCH");
        require(open.manifestId().equals(core.path("manifestId").textValue())
            && open.manifestVersion()==core.path("manifestVersion").longValue()
            && open.snapshotId().equals(core.path("snapshotId").textValue()),"PAGE_OPEN_CONTEXT");
        byte[] coreBytes=E2aProtocol.canonical(core.toString().getBytes(StandardCharsets.UTF_8));
        return new Parsed(request.bytes(),request.sha256(),openKey,coreBytes,hash,
            core.path("pageNo").longValue(),core.path("pageCount").longValue(),
            core.path("prevPageHash").isNull()?null:core.path("prevPageHash").textValue(),
            E2aPageItem.parseAll(core.path("items"),core.path("byteCount").longValue()));
    }

    private static UUID uuid(JsonNode node) {
        require(node.isTextual(),"PAGE_OPEN_KEY");
        try {
            UUID value=UUID.fromString(node.textValue());
            require(value.toString().equals(node.textValue()),"PAGE_OPEN_KEY");
            return value;
        } catch(IllegalArgumentException invalid) { throw new IllegalArgumentException("PAGE_OPEN_KEY",invalid); }
    }

    private static void require(boolean valid,String code) { if(!valid)throw new IllegalArgumentException(code); }

    public record Parsed(byte[] canonicalBytes,String requestSha256,UUID openOriginalKey,byte[] pageCoreBytes,
                         String pageHash,long pageNo,long pageCount,String prevPageHash,List<E2aPageItem> items) {
        public Parsed { canonicalBytes=canonicalBytes.clone();pageCoreBytes=pageCoreBytes.clone();items=List.copyOf(items); }
        @Override public byte[] canonicalBytes() { return canonicalBytes.clone(); }
        @Override public byte[] pageCoreBytes() { return pageCoreBytes.clone(); }
    }
}
