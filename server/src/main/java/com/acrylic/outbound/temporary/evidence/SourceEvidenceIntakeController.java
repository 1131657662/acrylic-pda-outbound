package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.pda.PdaAccess;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pda/temporary-source-evidence")
public class SourceEvidenceIntakeController {
    private final PdaAccess access;
    private final SourceEvidenceIntakeOpenService opens;
    private final SourceEvidenceIntakePageService pages;
    private final SourceEvidenceIntakeStopService stops;
    private final SourceEvidenceIntakeVerifyService verifies;

    public SourceEvidenceIntakeController(PdaAccess access, SourceEvidenceIntakeOpenService opens,
        SourceEvidenceIntakePageService pages, SourceEvidenceIntakeStopService stops,
        SourceEvidenceIntakeVerifyService verifies) {
        this.access = access; this.opens = opens; this.pages = pages; this.stops = stops; this.verifies = verifies;
    }

    @PostMapping(value = "/open", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> open(@RequestParam UUID warehouseId, @RequestParam UUID submissionId,
        @RequestParam long workRound, @RequestParam UUID originalKey,
        @RequestHeader("X-Server-Recovery-Epoch") String epoch, @RequestBody byte[] body) {
        access.require("pda:recover");
        return json(opens.open(new SourceEvidenceIntakeOpenService.OpenRequest(warehouseId, originalKey,
            submissionId, workRound, epoch, body)));
    }

    @PostMapping(value = "/page", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> page(@RequestParam UUID warehouseId, @RequestParam UUID submissionId,
        @RequestParam long workRound, @RequestParam UUID originalKey, @RequestParam UUID openOriginalKey,
        @RequestHeader("X-Server-Recovery-Epoch") String epoch, @RequestBody byte[] body) {
        access.require("pda:recover");
        return json(pages.page(new SourceEvidenceIntakePageService.PageRequest(warehouseId, originalKey,
            openOriginalKey, submissionId, workRound, epoch, body)));
    }

    @PostMapping(value = "/stop", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> stop(@RequestParam UUID warehouseId, @RequestParam UUID submissionId,
        @RequestParam long workRound, @RequestParam UUID originalKey, @RequestParam UUID openOriginalKey,
        @RequestHeader("X-Server-Recovery-Epoch") String epoch, @RequestBody byte[] body) {
        access.require("pda:recover");
        return json(stops.stop(new SourceEvidenceIntakeStopService.StopRequest(warehouseId, originalKey,
            openOriginalKey, submissionId, workRound, epoch, body)));
    }

    @PostMapping(value = "/verify", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> verify(@RequestParam UUID warehouseId, @RequestParam UUID submissionId,
        @RequestParam long workRound, @RequestParam UUID originalKey, @RequestParam UUID openOriginalKey,
        @RequestParam UUID stopOriginalKey, @RequestHeader("X-Server-Recovery-Epoch") String epoch,
        @RequestBody byte[] body) {
        access.require("pda:recover");
        return json(verifies.verify(new SourceEvidenceIntakeVerifyService.VerifyRequest(warehouseId, originalKey,
            openOriginalKey, stopOriginalKey, submissionId, workRound, epoch, body)));
    }

    private ResponseEntity<byte[]> json(byte[] body) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).contentLength(body.length)
            .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff").body(body);
    }
}
