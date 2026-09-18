package com.acrylic.outbound.pc;

import com.acrylic.outbound.auth.Access;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.inventory.master.MutationAccess;
import com.acrylic.outbound.orders.OrderQueries;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/orders/{id}/pc/exception")
public class PcExceptionController {
    private final Access access;private final OrderQueries orders;private final PcStore works;private final PcExceptionStore store;
    private final PcExceptionRequests commands;private final PcExceptionHandover handover;private final ApiResponses responses;
    public PcExceptionController(Access access,OrderQueries orders,PcStore works,PcExceptionStore store,PcExceptionRequests commands,PcExceptionHandover handover,ApiResponses responses) {
        this.access=access;this.orders=orders;this.works=works;this.store=store;this.commands=commands;this.handover=handover;this.responses=responses;
    }
    @GetMapping @Transactional(readOnly=true)
    public ApiEnvelope<?> view(@PathVariable UUID id,@RequestParam UUID warehouseId,@RequestParam(required=false) UUID caseId,HttpServletRequest request) {
        access.require("order:read",warehouseId);orders.head(warehouseId,id);var work=works.required(id);var selected=store.find(work,caseId);
        return responses.ok(new View(work,selected,selected==null?List.of():store.plans(selected),selected==null?List.of():store.reviews(selected),store.history(work)),request);
    }
    @PostMapping("/stop")
    public ApiEnvelope<?> stop(@PathVariable UUID id,@RequestParam UUID warehouseId,@Valid @RequestBody PcExceptionCommands.Stop body,HttpServletRequest request) {
        return responses.ok(commands.stop(context(warehouseId,request),id,body),request);
    }
    @PostMapping("/plan")
    public ApiEnvelope<?> plan(@PathVariable UUID id,@RequestParam UUID warehouseId,@Valid @RequestBody PcExceptionCommands.Plan body,HttpServletRequest request) {
        return responses.ok(commands.propose(context(warehouseId,request),id,body),request);
    }
    @PostMapping("/review")
    public ApiEnvelope<?> review(@PathVariable UUID id,@RequestParam UUID warehouseId,@Valid @RequestBody PcExceptionCommands.Decision body,HttpServletRequest request) {
        return responses.ok(commands.review(context(warehouseId,request),id,body),request);
    }
    @PostMapping("/apply")
    public ApiEnvelope<?> apply(@PathVariable UUID id,@RequestParam UUID warehouseId,@Valid @RequestBody PcExceptionCommands.Apply body,HttpServletRequest request) {
        return responses.ok(handover.apply(context(warehouseId,request),id,body),request);
    }
    private MutationAccess.Context context(UUID warehouse,HttpServletRequest request) {
        String key=request.getHeader("Idempotency-Key"),epoch=request.getHeader("X-Server-Recovery-Epoch");
        if(key==null || epoch==null)throw new BusinessException(400,"INVALID_REQUEST","缺少原请求身份，请刷新后重新核对");
        return new MutationAccess.Context(warehouse,epoch,key,(String)request.getAttribute("requestId"));
    }
    private record View(PcStore.Work work,PcExceptionStore.Case stopped,List<PcExceptionStore.Plan> plans,List<PcExceptionStore.Review> reviews,List<PcExceptionStore.History> history) { }
}
