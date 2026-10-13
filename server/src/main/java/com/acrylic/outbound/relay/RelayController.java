package com.acrylic.outbound.relay;

import com.acrylic.outbound.common.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/original-relays")
public class RelayController {
    private final RelayIntake intake;private final RelayProcessing processing;private final ApiResponses responses;
    public RelayController(RelayIntake intake,RelayProcessing processing,ApiResponses responses) {
        this.intake=intake;this.processing=processing;this.responses=responses;
    }
    @PostMapping(consumes="application/zip")
    public ResponseEntity<ApiEnvelope<?>> receive(@RequestParam UUID warehouseId,
        @RequestHeader("X-Server-Recovery-Epoch") String epoch,@RequestHeader("Idempotency-Key") String key,HttpServletRequest request) throws IOException {
        byte[] bytes=request.getInputStream().readNBytes(RelayArchive.MAX_BYTES+1);
        return result(intake.receive(context(warehouseId,epoch,key,request),bytes),request);
    }
    @GetMapping("/{id}")
    public ResponseEntity<ApiEnvelope<?>> read(@PathVariable UUID id,@RequestParam UUID warehouseId,HttpServletRequest request) {
        return result(intake.read(warehouseId,id),request);
    }
    @GetMapping("/{id}/submission")
    public ResponseEntity<ApiEnvelope<?>> query(@PathVariable UUID id,@RequestParam UUID warehouseId,HttpServletRequest request) {
        return result(processing.query(warehouseId,id),request);
    }
    @PostMapping("/{id}/process")
    public ResponseEntity<ApiEnvelope<?>> process(@PathVariable UUID id,@RequestParam UUID warehouseId,
        @RequestHeader("X-Server-Recovery-Epoch") String epoch,@RequestHeader("Idempotency-Key") String key,HttpServletRequest request) {
        return result(processing.process(context(warehouseId,epoch,key,request),id),request);
    }
    @GetMapping("/{id}/original")
    public ResponseEntity<byte[]> original(@PathVariable UUID id,@RequestParam UUID warehouseId) {
        return file(intake.original(warehouseId,id),"application/zip",id+".zip");
    }
    @GetMapping("/results/{id}")
    public ResponseEntity<byte[]> receipt(@PathVariable UUID id,@RequestParam UUID warehouseId) {
        return file(processing.result(warehouseId,id).getBytes(java.nio.charset.StandardCharsets.UTF_8),"application/json",id+".pda-receipt.json");
    }
    private ResponseEntity<byte[]> file(byte[] bytes,String type,String name) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
            .header("Content-Disposition","attachment; filename=\""+name+"\"").contentType(MediaType.parseMediaType(type)).body(bytes);
    }
    private ResponseEntity<ApiEnvelope<?>> result(Object data,HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(responses.ok(data,request));
    }
    private RelayIntake.Context context(UUID warehouse,String epoch,String key,HttpServletRequest request) {
        return new RelayIntake.Context(warehouse,epoch,key,(String)request.getAttribute("requestId"));
    }
}
