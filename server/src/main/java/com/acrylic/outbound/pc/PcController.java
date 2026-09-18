package com.acrylic.outbound.pc;

import com.acrylic.outbound.auth.Access;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.inventory.master.MutationAccess;
import com.acrylic.outbound.orders.OrderQueries;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/orders/{id}/pc")
public class PcController {
    private final Access access;private final OrderQueries orders;private final PcStore store;private final PcEntries entries;
    private final PcCandidates candidates;private final PcSubmissions submissions;private final ApiResponses responses;
    private final PcWorkService work;private final PcRecording recording;private final PcFinalization finalization;
    public PcController(Access access,OrderQueries orders,PcStore store,PcEntries entries,PcCandidates candidates,PcSubmissions submissions,
        ApiResponses responses,PcWorkService work,PcRecording recording,PcFinalization finalization) {
        this.access=access;this.orders=orders;this.store=store;this.entries=entries;this.candidates=candidates;this.submissions=submissions;
        this.responses=responses;this.work=work;this.recording=recording;this.finalization=finalization;
    }
    @GetMapping @Transactional(readOnly=true)
    public ApiEnvelope<?> view(@PathVariable UUID id,@RequestParam UUID warehouseId,HttpServletRequest request) {
        access.require("order:read",warehouseId);orders.head(warehouseId,id);var found=store.find(id);
        return responses.ok(new View(found,found==null?java.util.List.of():entries.products(found),found==null?null:submissions.current(found)),request);
    }
    @GetMapping("/candidates") @Transactional(readOnly=true)
    public ApiEnvelope<?> candidates(@PathVariable UUID id,@RequestParam UUID warehouseId,@RequestParam String query,
        @RequestParam(defaultValue="") String code,@RequestParam(defaultValue="1") int page,HttpServletRequest request) {
        var actor=access.require("pc:operate",warehouseId);orders.head(warehouseId,id);var found=store.required(id);
        return responses.ok(candidates.search(found,actor.id(),new PcCandidates.Search(query,code,page)),request);
    }
    @GetMapping("/entries") @Transactional(readOnly=true)
    public ApiEnvelope<?> entries(@PathVariable UUID id,@RequestParam UUID warehouseId,@RequestParam(defaultValue="") String search,
        @RequestParam(defaultValue="") String code,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,HttpServletRequest request) {
        access.require("order:read",warehouseId);orders.head(warehouseId,id);
        return responses.ok(entries.page(store.required(id),new PcEntries.Filter(search,code,page,pageSize)),request);
    }
    @PostMapping("/start")
    public ApiEnvelope<?> start(@PathVariable UUID id,@RequestParam UUID warehouseId,@Valid @RequestBody PcCommands.Start body,HttpServletRequest request) {
        return responses.ok(work.start(context(warehouseId,request),id,body),request);
    }
    @PostMapping("/writer")
    public ApiEnvelope<?> writer(@PathVariable UUID id,@RequestParam UUID warehouseId,@Valid @RequestBody PcCommands.Start body,HttpServletRequest request) {
        return responses.ok(work.writer(context(warehouseId,request),id,body),request);
    }
    @PostMapping("/{action:entries|void|submit}")
    public ApiEnvelope<?> write(@PathVariable UUID id,@PathVariable String action,@RequestParam UUID warehouseId,@Valid @RequestBody PcCommands.Write body,HttpServletRequest request) {
        var context=context(warehouseId,request);
        return responses.ok(action.equals("submit")?finalization.freeze(context,id,body):recording.record(context,id,new PcRecording.Input(action.equals("entries")?"ADD":"VOID",body)),request);
    }
    @PostMapping("/review")
    public ApiEnvelope<?> review(@PathVariable UUID id,@RequestParam UUID warehouseId,@Valid @RequestBody PcCommands.Review body,HttpServletRequest request) {
        return responses.ok(finalization.review(context(warehouseId,request),id,body),request);
    }
    @PostMapping("/resume")
    public ApiEnvelope<?> resume(@PathVariable UUID id,@RequestParam UUID warehouseId,@Valid @RequestBody PcCommands.Resume body,HttpServletRequest request) {
        return responses.ok(finalization.resume(context(warehouseId,request),id,body),request);
    }
    private MutationAccess.Context context(UUID warehouse,HttpServletRequest request) {
        String key=request.getHeader("Idempotency-Key"),epoch=request.getHeader("X-Server-Recovery-Epoch");
        if(key==null || epoch==null) throw new BusinessException(400,"INVALID_REQUEST","缺少原请求身份，请刷新后重新核对");
        return new MutationAccess.Context(warehouse,epoch,key,(String)request.getAttribute("requestId"));
    }
    private record View(PcStore.Work work,java.util.List<PcEntries.Product> products,PcSubmissions.Submission submission) { }
}
