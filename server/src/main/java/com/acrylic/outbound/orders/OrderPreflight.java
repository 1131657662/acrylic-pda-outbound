package com.acrylic.outbound.orders;

import com.acrylic.outbound.inventory.master.WarehouseBaseline;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class OrderPreflight {
    private final OrderQueries queries;
    private final OrderStock stock;
    public OrderPreflight(OrderQueries queries,OrderStock stock) { this.queries=queries;this.stock=stock; }
    public Result check(UUID warehouse,UUID order) {
        var detail=queries.detail(warehouse,order,null);var baseline=stock.baseline(warehouse);
        var issues=new ArrayList<Issue>();
        if(!detail.order().status().equals("DRAFT")) issues.add(new Issue("ORDER_NOT_DRAFT","仅草稿支持此下发前检查"));
        if(!Objects.equals(detail.plan().inventorySnapshotId(),baseline.activeSnapshotId()))
            issues.add(new Issue("INVENTORY_SNAPSHOT_CHANGED","库存版本已变化，请修订草稿并重新核对"));
        if(!detail.plan().mappingRevision().equals(baseline.mappingRevision()))
            issues.add(new Issue("MAPPING_REVISION_CHANGED","代码映射版本已变化，请修订草稿并重新核对"));
        var products=new HashMap<String,OrderStock.Product>();
        stock.products(warehouse,baseline.activeSnapshotId()).forEach(p->products.put(p.deliveryCode(),p));
        var lines=detail.lines().stream().map(line->lineCheck(line,products.get(line.deliveryCode()))).toList();
        if(lines.stream().anyMatch(line->!line.mappingValid())) issues.add(new Issue("ORDER_PRODUCT_UNAVAILABLE","部分产品已停用或映射已变化"));
        return new Result(detail.order().rowVersion(),baseline,issues.isEmpty(),false,issues,lines,Instant.now().toString());
    }
    private LineCheck lineCheck(OrderViews.Line line,OrderStock.Product product) {
        boolean valid=product!=null && product.enabled() && product.mappingId().equals(line.mappingId());
        int candidates=valid?product.candidateCount():0;
        return new LineCheck(line.deliveryCode(),line.plannedCount(),valid,product==null?0:product.snapshotCount(),
            candidates,Math.max(0L,(long)line.plannedCount()-candidates));
    }
    public record Issue(String code,String message) { }
    public record LineCheck(String deliveryCode,int plannedCount,boolean mappingValid,int snapshotCount,int candidateCount,long shortage) { }
    public record Result(String orderVersion,WarehouseBaseline.Baseline baseline,boolean checksPassed,boolean dispatchAvailable,
        List<Issue> blockers,List<LineCheck> lines,String checkedAt) { }
}
