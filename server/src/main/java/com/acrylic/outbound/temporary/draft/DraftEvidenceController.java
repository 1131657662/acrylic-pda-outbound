package com.acrylic.outbound.temporary.draft;

import java.util.UUID;
import java.io.IOException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pda/local-draft-evidence")
public class DraftEvidenceController {
    private final DraftEvidenceOpenService opens;
    private final DraftEvidenceItemService items;
    private final DraftEvidenceVerifyService verifies;
    private final DraftEvidenceReadService reads;
    private final DraftEvidenceAccess access;
    public DraftEvidenceController(DraftEvidenceOpenService opens,DraftEvidenceItemService items,DraftEvidenceVerifyService verifies,DraftEvidenceReadService reads,DraftEvidenceAccess access) {
        this.opens=opens;this.items=items;this.verifies=verifies;this.reads=reads;this.access=access;
    }
    @PostMapping(value="/{evidence}/open",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> open(@PathVariable UUID evidence,@RequestParam UUID originalKey,
        @RequestHeader("X-Server-Recovery-Epoch") String epoch,HttpServletRequest request) throws IOException {
        return json(opens.open(evidence,originalKey,epoch,body(request,262144,-1)));
    }
    @PostMapping(value="/{evidence}/item",consumes=MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> item(@PathVariable UUID evidence,@RequestParam UUID originalKey,@RequestParam long ordinal,
        @RequestParam String kind,@RequestParam UUID recordId,@RequestParam long deviceSeq,@RequestParam String recordStatus,
        @RequestParam int byteCount,@RequestParam String bytesSha256,@RequestHeader("X-Server-Recovery-Epoch") String epoch,HttpServletRequest request) throws IOException {
        return json(items.item(new DraftEvidenceItemRequest(evidence,originalKey,ordinal,kind,recordId,deviceSeq,recordStatus,byteCount,bytesSha256,
            body(request,33554432,byteCount)),epoch));
    }
    @PostMapping(value="/{evidence}/verify",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> verify(@PathVariable UUID evidence,@RequestParam UUID originalKey,
        @RequestHeader("X-Server-Recovery-Epoch") String epoch,HttpServletRequest request) throws IOException {
        return json(verifies.verify(evidence,originalKey,epoch,body(request,262144,-1)));
    }
    @GetMapping("/{evidence}/receipts/{originalKey}")
    public ResponseEntity<byte[]> read(@PathVariable UUID evidence,@PathVariable UUID originalKey) { return json(reads.read(evidence,originalKey)); }
    private byte[] body(HttpServletRequest request,int maximum,int expected) throws IOException {
        access.current();long length=request.getContentLengthLong();
        DraftEvidenceWire.require(length<=maximum && (expected==-1 || expected>0 && expected<=maximum && (length<0 || length==expected)));
        byte[] bytes=request.getInputStream().readNBytes((expected==-1?maximum:expected)+1);
        DraftEvidenceWire.require(bytes.length>0 && bytes.length<=maximum && (expected==-1 || bytes.length==expected));
        return bytes;
    }
    private ResponseEntity<byte[]> json(byte[] body) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).contentLength(body.length).cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options","nosniff").body(body);
    }
}
