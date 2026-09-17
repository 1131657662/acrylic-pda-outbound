package com.acrylic.outbound.inventory;

import com.acrylic.outbound.auth.Access;
import com.acrylic.outbound.common.ApiEnvelope;
import com.acrylic.outbound.common.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/inventory/imports")
public class ImportController {
    private final ImportService service;
    private final ImportQueries queries;
    private final ImportRowStore rows;
    private final Access access;
    private final ApiResponses responses;
    public ImportController(ImportService service, ImportQueries queries, ImportRowStore rows,
                            Access access, ApiResponses responses) {
        this.service = service; this.queries = queries; this.rows = rows; this.access = access; this.responses = responses;
    }

    @PostMapping
    public ApiEnvelope<ImportSummary> upload(@RequestParam UUID warehouseId, @RequestPart MultipartFile file,
        @RequestHeader("Idempotency-Key") String key, @RequestHeader("X-Server-Recovery-Epoch") String epoch,
        HttpServletRequest request) throws IOException {
        UUID id = service.upload(new ImportService.Upload(warehouseId, key, epoch,
            (String) request.getAttribute("requestId")), file);
        return responses.ok(queries.detail(warehouseId, id), request);
    }

    @GetMapping
    public ApiEnvelope<?> list(@RequestParam UUID warehouseId, @RequestParam(required=false) String cursor,
                              @RequestParam(defaultValue="50") int limit, HttpServletRequest request) {
        access.require("inventory:read", warehouseId);
        return responses.ok(queries.list(warehouseId, cursor, limit), request);
    }

    @GetMapping("/{id}")
    public ApiEnvelope<?> detail(@PathVariable UUID id, @RequestParam UUID warehouseId, HttpServletRequest request) {
        access.require("inventory:read", warehouseId);
        return responses.ok(queries.detail(warehouseId, id), request);
    }

    @GetMapping("/{id}/rows")
    public ApiEnvelope<?> rows(@PathVariable UUID id, @RequestParam UUID warehouseId,
        @RequestParam(defaultValue="0") int afterLine, @RequestParam(defaultValue="false") boolean errorsOnly,
        @RequestParam(defaultValue="50") int limit, HttpServletRequest request) {
        access.require("inventory:read", warehouseId);
        queries.detail(warehouseId, id);
        return responses.ok(rows.list(id, new ImportRowStore.RowPage(afterLine, errorsOnly, limit)), request);
    }
}
