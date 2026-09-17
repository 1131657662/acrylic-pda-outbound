package com.acrylic.outbound.orders;

import com.acrylic.outbound.auth.Access;
import com.acrylic.outbound.common.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/orders") @Transactional(readOnly=true)
public class OrderController {
    private final Access access;
    private final ApiResponses responses;
    private final OrderQueries queries;
    private final OrderStock stock;
    private final OrderPreflight preflight;
    public OrderController(Access access,ApiResponses responses,OrderQueries queries,OrderStock stock,OrderPreflight preflight) {
        this.access=access;this.responses=responses;this.queries=queries;this.stock=stock;this.preflight=preflight;
    }
    @GetMapping
    public ApiEnvelope<?> list(@RequestParam UUID warehouseId,@RequestParam(defaultValue="") String status,
        @RequestParam(defaultValue="") String search,@RequestParam(defaultValue="") String cursor,
        @RequestParam(defaultValue="50") int limit,@RequestParam(required=false) Integer page,
        @RequestParam(defaultValue="20") int pageSize,HttpServletRequest request) {
        access.require("order:read",warehouseId);
        if(page!=null) return responses.ok(queries.numbered(warehouseId,new OrderQueries.Filter(status,search,cursor,pageSize),page),request);
        return responses.ok(queries.list(warehouseId,new OrderQueries.Filter(status,search,cursor,limit)),request);
    }
    @GetMapping("/products")
    public ApiEnvelope<?> products(@RequestParam UUID warehouseId,@RequestParam(defaultValue="") String search,
        @RequestParam(defaultValue="") String afterCode,@RequestParam(defaultValue="50") int limit,HttpServletRequest request) {
        access.require("order:read",warehouseId);
        return responses.ok(stock.options(warehouseId,new OrderStock.Search(search,afterCode,limit)),request);
    }
    @GetMapping("/{id}")
    public ApiEnvelope<?> detail(@RequestParam UUID warehouseId,@PathVariable UUID id,
        @RequestParam(required=false) Integer revision,HttpServletRequest request) {
        access.require("order:read",warehouseId);return responses.ok(queries.detail(warehouseId,id,revision),request);
    }
    @GetMapping("/{id}/revisions")
    public ApiEnvelope<?> history(@RequestParam UUID warehouseId,@PathVariable UUID id,
        @RequestParam(defaultValue="2147483648") long beforeRevision,@RequestParam(defaultValue="50") int limit,HttpServletRequest request) {
        access.require("order:read",warehouseId);
        return responses.ok(queries.history(warehouseId,id,new OrderQueries.History(beforeRevision,limit)),request);
    }
    @GetMapping("/{id}/events")
    public ApiEnvelope<?> events(@RequestParam UUID warehouseId,@PathVariable UUID id,
        @RequestParam(defaultValue="9223372036854775807") long beforeId,@RequestParam(defaultValue="50") int limit,HttpServletRequest request) {
        access.require("order:read",warehouseId);
        return responses.ok(queries.events(warehouseId,id,new OrderQueries.History(beforeId,limit)),request);
    }
    @GetMapping("/{id}/preflight")
    public ApiEnvelope<?> preflight(@RequestParam UUID warehouseId,@PathVariable UUID id,HttpServletRequest request) {
        access.require("order:read",warehouseId);return responses.ok(preflight.check(warehouseId,id),request);
    }
}
