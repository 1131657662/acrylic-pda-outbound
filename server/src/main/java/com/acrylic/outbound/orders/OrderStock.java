package com.acrylic.outbound.orders;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.inventory.master.WarehouseBaseline;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrderStock {
    private final JdbcTemplate jdbc;
    private final WarehouseBaseline baselines;
    public OrderStock(JdbcTemplate jdbc, WarehouseBaseline baselines) { this.jdbc = jdbc; this.baselines = baselines; }
    public WarehouseBaseline.Baseline baseline(UUID warehouse) { return baselines.read(warehouse, false); }
    public List<Product> products(UUID warehouse, String snapshot) {
        return jdbc.query("""
            SELECT m.delivery_code,BIN_TO_UUID(m.id),BIN_TO_UUID(m.product_id),p.model,p.name,m.enabled,m.version,
              COALESCE(s.snapshot_count,0),COALESCE(s.candidate_count,0)
            FROM delivery_mapping m INNER JOIN product p ON p.id=m.product_id
            LEFT JOIN (
              SELECT sb.delivery_code,sb.product_id,COUNT(*) snapshot_count,
                SUM(IF(ip.upstream_present=1 AND ip.book_status='NOT_DEDUCTED' AND ip.blocked_reason IS NULL,1,0)) candidate_count
              FROM inventory_snapshot_bale sb INNER JOIN inventory_position ip ON ip.bale_id=sb.bale_id
              WHERE sb.snapshot_id=? GROUP BY sb.delivery_code,sb.product_id
            ) s ON s.delivery_code=m.delivery_code AND s.product_id=m.product_id
            WHERE m.warehouse_id=? AND m.is_active=1 ORDER BY m.delivery_code
            """, (rs,n) -> new Product(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),
                rs.getBoolean(6),rs.getString(7),rs.getInt(8),rs.getInt(9)),
            snapshot == null ? null : Ids.bytes(UUID.fromString(snapshot)),Ids.bytes(warehouse));
    }
    public Options options(UUID warehouse, Search search) {
        int limit = Page.checkedLimit(search.limit());
        if (search.search().length()>100 || !search.afterCode().matches("[A-Z0-9]{0,3}"))
            throw new BusinessException(400,"INVALID_REQUEST","产品查询条件无效");
        var baseline = baseline(warehouse);
        var rows = products(warehouse,baseline.activeSnapshotId()).stream().filter(Product::enabled)
            .filter(p -> p.deliveryCode().compareTo(search.afterCode())>0)
            .filter(p -> p.deliveryCode().contains(search.search()) || p.model().contains(search.search()) || p.name().contains(search.search()))
            .limit(limit+1L).toList();
        return new Options(baseline,Page.from(rows,limit,Product::deliveryCode));
    }
    public List<Product> validate(UUID warehouse, WarehouseBaseline.Baseline baseline, OrderCommands.Plan plan) {
        if (baseline.activeSnapshotId()==null)
            throw new BusinessException(409,"ORDER_INVENTORY_REQUIRED","请先发布库存版本再创建订单");
        if (!baseline.activeSnapshotId().equals(plan.expectedActiveSnapshot().toString()) ||
            !baseline.mappingRevision().equals(plan.expectedMappingRevision()))
            throw new BusinessException(409,"ORDER_BASELINE_CHANGED","库存或映射已变化，请重新加载并核对计划");
        var products = products(warehouse,baseline.activeSnapshotId());
        for (var line : plan.lines()) {
            if (products.stream().noneMatch(p -> p.deliveryCode().equals(line.deliveryCode()) && p.enabled()))
                throw new BusinessException(409,"ORDER_PRODUCT_UNAVAILABLE","产品映射不存在或已停用："+line.deliveryCode());
        }
        return products;
    }
    public record Product(String deliveryCode,String mappingId,String productId,String model,String name,
        boolean enabled,String mappingVersion,int snapshotCount,int candidateCount) { }
    public record Search(String search,String afterCode,int limit) { }
    public record Options(WarehouseBaseline.Baseline baseline,Page<Product> products) { }
}
