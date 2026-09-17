package com.acrylic.outbound.inventory.master;

import com.acrylic.outbound.auth.Access;
import com.acrylic.outbound.common.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {
    private final Access access;
    private final ApiResponses responses;
    private final InventoryQueries queries;
    private final SnapshotQueries snapshots;
    private final WarehouseBaseline baseline;
    public InventoryController(Access access, ApiResponses responses, InventoryQueries queries, SnapshotQueries snapshots, WarehouseBaseline baseline) {
        this.access = access; this.responses = responses; this.queries = queries; this.snapshots = snapshots; this.baseline = baseline;
    }
    @GetMapping("/current")
    public ApiEnvelope<?> current(@RequestParam UUID warehouseId, HttpServletRequest request) {
        access.require("inventory:read", warehouseId);
        return responses.ok(baseline.read(warehouseId, false), request);
    }
    @GetMapping("/snapshots")
    public ApiEnvelope<?> snapshots(@RequestParam UUID warehouseId, @RequestParam(defaultValue="9223372036854775807") long beforeVersion,
        @RequestParam(defaultValue="50") int limit, HttpServletRequest request) {
        access.require("inventory:read", warehouseId);
        return responses.ok(snapshots.list(warehouseId, beforeVersion, limit), request);
    }
    @GetMapping("/bales")
    public ApiEnvelope<?> bales(@RequestParam UUID warehouseId, @RequestParam(defaultValue="") String barcode,
        @RequestParam(defaultValue="") String deliveryCode, @RequestParam(defaultValue="all") String presence,
        @RequestParam(defaultValue="") String afterBarcode, @RequestParam(defaultValue="50") int limit, HttpServletRequest request) {
        access.require("inventory:read", warehouseId);
        return responses.ok(queries.bales(warehouseId, new InventoryQueries.Filter(barcode, deliveryCode, presence, afterBarcode, limit)), request);
    }
    @GetMapping("/discrepancies")
    public ApiEnvelope<?> discrepancies(@RequestParam UUID warehouseId, @RequestParam(required=false) UUID beforeId,
        @RequestParam(defaultValue="50") int limit, HttpServletRequest request) {
        access.require("inventory:read", warehouseId);
        return responses.ok(queries.discrepancies(warehouseId, beforeId, limit), request);
    }
}
