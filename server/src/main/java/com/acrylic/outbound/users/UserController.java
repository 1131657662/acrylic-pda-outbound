package com.acrylic.outbound.users;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.inventory.master.MutationAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/users")
public class UserController {
    private final UserAccess access;private final UserStore store;private final UserService service;private final UserReceipts receipts;private final ApiResponses responses;
    public UserController(UserAccess access,UserStore store,UserService service,UserReceipts receipts,ApiResponses responses) {
        this.access=access;this.store=store;this.service=service;this.receipts=receipts;this.responses=responses;
    }
    @GetMapping public ApiEnvelope<?> list(@RequestParam UUID warehouseId,@RequestParam(defaultValue="") String username,
        @RequestParam(defaultValue="") String status,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,HttpServletRequest request) {
        return responses.ok(store.list(access.read(warehouseId),warehouseId,new UserCommands.Query(username,status,page,pageSize)),request);
    }
    @GetMapping("/options") public ApiEnvelope<?> options(@RequestParam UUID warehouseId,HttpServletRequest request) {
        var scope=access.read(warehouseId);
        return responses.ok(Map.of("roles",store.roles().stream().filter(r->scope.permissions().containsAll(r.permissions())).toList(),"warehouses",scope.warehouses()),request);
    }
    @GetMapping("/{id}") public ApiEnvelope<?> detail(@RequestParam UUID warehouseId,@PathVariable UUID id,HttpServletRequest request) {
        var scope=access.read(warehouseId);var item=store.find(id);access.target(scope,item,warehouseId);return responses.ok(item,request);
    }
    @GetMapping("/{id}/history") public ApiEnvelope<?> history(@RequestParam UUID warehouseId,@PathVariable UUID id,
        @RequestParam(defaultValue="") String beforeId,HttpServletRequest request) {
        var scope=access.read(warehouseId);access.target(scope,store.find(id),warehouseId);return responses.ok(receipts.history(id,beforeId),request);
    }
    @GetMapping("/requests/{key}") public ApiEnvelope<?> lookup(@RequestParam UUID warehouseId,@PathVariable String key,HttpServletRequest request) {
        return responses.ok(receipts.lookup(access.read(warehouseId).actor(),warehouseId,key),request);
    }
    @PostMapping public ApiEnvelope<?> create(@RequestParam UUID warehouseId,@Valid @RequestBody UserCommands.Create body,HttpServletRequest request) {
        return responses.ok(service.create(context(warehouseId,request),body),request);
    }
    @PostMapping("/{id}") public ApiEnvelope<?> update(@RequestParam UUID warehouseId,@PathVariable UUID id,
        @Valid @RequestBody UserCommands.Update body,HttpServletRequest request) {
        return responses.ok(service.update(context(warehouseId,request),id,body),request);
    }
    @PostMapping("/{id}/password") public ApiEnvelope<?> password(@RequestParam UUID warehouseId,@PathVariable UUID id,
        @Valid @RequestBody UserCommands.Password body,HttpServletRequest request) {
        return responses.ok(service.password(context(warehouseId,request),id,body),request);
    }
    private MutationAccess.Context context(UUID warehouse,HttpServletRequest request) {
        String key=request.getHeader("Idempotency-Key"),epoch=request.getHeader("X-Server-Recovery-Epoch");
        if(key==null || epoch==null) throw new BusinessException(400,"INVALID_REQUEST","缺少原请求标识或恢复代次");
        return new MutationAccess.Context(warehouse,epoch,key,(String)request.getAttribute("requestId"));
    }
}
