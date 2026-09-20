package com.acrylic.outbound.exports;

import com.acrylic.outbound.auth.Access;
import com.acrylic.outbound.common.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import static com.acrylic.outbound.exports.ExportModels.*;

@RestController @RequestMapping("/api/v1/exports")
public class ExportCenterController {
    private final Access access;private final ApiResponses responses;private final ExportProducer producer;
    private final ExportJobQueries queries;private final ExportSummaryQuery summaries;private final ExportRetry retry;
    public ExportCenterController(Access access,ApiResponses responses,ExportProducer producer,
            ExportJobQueries queries,ExportSummaryQuery summaries,ExportRetry retry) {
        this.access=access;this.responses=responses;this.producer=producer;this.queries=queries;this.summaries=summaries;this.retry=retry;
    }
    @GetMapping("/summary")
    public ApiEnvelope<?> summary(@RequestParam UUID warehouseId,@RequestParam LocalDate fromDate,
            @RequestParam LocalDate throughDate,HttpServletRequest request) {
        var actor=access.require("order:read",warehouseId);
        return responses.ok(summaries.read(new Scope(actor.id(),warehouseId,null),new Range(fromDate,throughDate)),request);
    }
    @GetMapping
    public ApiEnvelope<?> list(@RequestParam UUID warehouseId,@RequestParam(required=false) UUID before,
            @RequestParam(required=false) Format format,@RequestParam(required=false) String status,HttpServletRequest request) {
        var actor=access.require("order:read",warehouseId);
        return responses.ok(queries.list(new Scope(actor.id(),warehouseId,null),new ExportJobQueries.Filter(before,format,status)),request);
    }
    @PostMapping
    public ApiEnvelope<?> create(@RequestParam UUID warehouseId,@RequestHeader("Idempotency-Key") UUID key,
            @RequestHeader("X-Server-Recovery-Epoch") String epoch,@Valid @RequestBody Command command,HttpServletRequest request) throws IOException {
        var actor=access.require("order:read",warehouseId);
        return responses.ok(producer.create(new Request(new Scope(actor.id(),warehouseId,null),key,epoch,command),requestId(request)),request);
    }
    @PostMapping("/{id}/retry")
    public ApiEnvelope<?> retry(@PathVariable UUID id,@RequestParam UUID warehouseId,@RequestHeader("Idempotency-Key") UUID key,
            @RequestHeader("X-Server-Recovery-Epoch") String epoch,HttpServletRequest request) {
        var actor=access.require("order:read",warehouseId);
        return responses.ok(retry.create(new ExportRetry.RetryRequest(new Scope(actor.id(),warehouseId,null),id,key,epoch),requestId(request)),request);
    }
    private String requestId(HttpServletRequest request) { return (String)request.getAttribute("requestId"); }
}
