package com.acrylic.outbound.exports;

import com.acrylic.outbound.auth.Access;
import com.acrylic.outbound.common.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import static com.acrylic.outbound.exports.ExportModels.*;

@RestController @RequestMapping("/api/v1")
public class ExportController {
    private final Access access;private final ApiResponses responses;private final ExportProducer producer;
    private final ExportJobQueries queries;private final ExportDownloads downloads;
    public ExportController(Access access,ApiResponses responses,ExportProducer producer,ExportJobQueries queries,ExportDownloads downloads) {
        this.access=access;this.responses=responses;this.producer=producer;this.queries=queries;this.downloads=downloads;
    }
    @PostMapping("/orders/{order}/exports")
    public ApiEnvelope<?> create(@PathVariable UUID order,@RequestParam UUID warehouseId,
            @RequestHeader("Idempotency-Key") UUID key,@RequestHeader("X-Server-Recovery-Epoch") String epoch,
            @Valid @RequestBody Command command,HttpServletRequest request) throws IOException {
        var actor=access.require("order:read",warehouseId);
        return responses.ok(producer.create(new Request(new Scope(actor.id(),warehouseId,order),key,epoch,command),requestId(request)),request);
    }
    @GetMapping("/orders/{order}/exports")
    public ApiEnvelope<?> list(@PathVariable UUID order,@RequestParam UUID warehouseId,
            @RequestParam(required=false) UUID before,HttpServletRequest request) {
        var actor=access.require("order:read",warehouseId);
        return responses.ok(queries.list(new Scope(actor.id(),warehouseId,order),before),request);
    }
    @GetMapping("/exports/{id}")
    public ApiEnvelope<?> status(@PathVariable UUID id,@RequestParam UUID warehouseId,HttpServletRequest request) {
        var actor=access.require("order:read",warehouseId);
        return responses.ok(queries.view(queries.owned(actor.id(),warehouseId,id)),request);
    }
    @GetMapping("/exports/{id}/file")
    public ResponseEntity<byte[]> download(@PathVariable UUID id,@RequestParam UUID warehouseId,HttpServletRequest request) {
        var actor=access.require("order:read",warehouseId);
        var result=downloads.read(queries.owned(actor.id(),warehouseId,id),requestId(request));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma","no-cache")
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(result.filename()).build().toString())
                .header("X-Content-Type-Options","nosniff").contentType(MediaType.parseMediaType(result.format().contentType))
                .contentLength(result.bytes().length).body(result.bytes());
    }
    private String requestId(HttpServletRequest request) { return (String)request.getAttribute("requestId"); }
}
