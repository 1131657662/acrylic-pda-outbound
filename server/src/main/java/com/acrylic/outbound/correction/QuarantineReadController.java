package com.acrylic.outbound.correction;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/v1/corrections/quarantine","/api/v1/pda/corrections/quarantine"})
@ConditionalOnProperty(prefix="outbound.corrections.commands",name="enabled",havingValue="true")
class QuarantineReadController {
    private final QuarantineReadService service;
    QuarantineReadController(QuarantineReadService service) { this.service=service; }
    @GetMapping ResponseEntity<byte[]> list(@RequestParam UUID warehouseId,@RequestParam(defaultValue="false") boolean review,
        @RequestParam(required=false) UUID before,@RequestParam(required=false) UUID beforeCase) {
        return response(service.list(warehouseId,review,new QuarantineReadService.Cursors(before,beforeCase)));
    }
    @GetMapping("/context") ResponseEntity<byte[]> context(@RequestParam UUID warehouseId,@RequestParam(required=false) UUID baleId,
        @RequestParam(required=false) UUID caseId,@RequestParam String commandType) { return response(service.context(warehouseId,baleId,caseId,commandType)); }
    @GetMapping("/original-receipt") ResponseEntity<byte[]> original(@RequestParam UUID warehouseId,@RequestParam(required=false) UUID baleId,
        @RequestParam(required=false) UUID caseId,@RequestParam String commandType) {
        byte[] bytes=service.original(warehouseId,baleId,caseId,commandType);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).contentLength(bytes.length).cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options","nosniff").header("Content-Disposition","attachment; filename=\"quarantine-source.json\"").body(bytes);
    }
    private ResponseEntity<byte[]> response(JsonNode value) {
        byte[] bytes=CorrectionReceiptCodec.bytes(value);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).contentLength(bytes.length).cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options","nosniff").body(bytes);
    }
}
