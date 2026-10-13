package com.acrylic.outbound.exports;

import com.acrylic.outbound.reports.*;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;

/** Projects validated result snapshots into exact original, adjustment and net totals. */
public record ExportSummary(String basis,ExportModels.Range range,Instant readAt,int orderCount,
        long actualCount,String weightGrams,long originalCount,String originalWeightGrams,
        long adjustedCount,String adjustedWeightGrams,List<OrderRow> orders,List<ProductRow> products) {
    public record OrderRow(String id,String orderNo,long revision,long resultVersion,String originalDay,String vehicle,
            long actualCount,String weightGrams,long originalCount,String originalWeightGrams,long adjustedCount,String adjustedWeightGrams) { }
    public record ProductRow(String code,String name,String model,long plannedCount,long actualCount,String weightGrams,
            long originalCount,String originalWeightGrams,long adjustedCount,String adjustedWeightGrams) { }
    private record ProductKey(String code,String name,String model) { }
    private record Total(long original,BigInteger originalGrams,long net,BigInteger netGrams) {
        static final Total ZERO=new Total(0,BigInteger.ZERO,0,BigInteger.ZERO);
        Total plus(Total other) {
            return new Total(original+other.original,originalGrams.add(other.originalGrams),net+other.net,netGrams.add(other.netGrams));
        }
        long adjusted() { return net-original; }
        String adjustedGrams() { return netGrams.subtract(originalGrams).toString(); }
    }
    public static ExportSummary from(ReportSnapshot snapshot) {
        SnapshotValidator.validate(snapshot);
        var orders=snapshot.orders().stream().map(ExportSummary::order).toList();
        var m=snapshot.metadata();var all=snapshot.orders().stream().map(o->total(o,null)).reduce(Total.ZERO,Total::plus);
        return new ExportSummary(m.basis().name(),new ExportModels.Range(m.fromDate(),m.throughDate()),m.cutoff(),orders.size(),
                all.net,all.netGrams.toString(),all.original,all.originalGrams.toString(),all.adjusted(),all.adjustedGrams(),orders,products(snapshot));
    }
    private static OrderRow order(OrderSnapshot o) {
        var t=total(o,null);
        return new OrderRow(o.id(),o.number(),o.revision(),o.resultVersion(),
                o.confirmedAt().atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate().toString(),
                o.effectiveHeader().vehicle(),t.net,t.netGrams.toString(),t.original,t.originalGrams.toString(),t.adjusted(),t.adjustedGrams());
    }
    private static Total total(OrderSnapshot order,String product) {
        var effective=new HashMap<String,OrderSnapshot.EffectiveLine>();
        order.effectiveLines().forEach(line->effective.put(line.dispatchId(),line));
        var total=Total.ZERO;
        for(var line:order.originalLines()) {
            if(product!=null && !line.productCode().equals(product))continue;
            var net=effective.get(line.dispatchId());
            total=total.plus(new Total(1,new BigInteger(line.weightGrams()),net.count(),new BigInteger(net.weightGrams())));
        }
        return total;
    }
    private static List<ProductRow> products(ReportSnapshot snapshot) {
        var rows=new LinkedHashMap<ProductKey,ProductRow>();
        for(var order:snapshot.orders()) for(var p:order.products()) {
            var key=new ProductKey(p.code(),p.name(),p.model());var previous=rows.get(key);
            var old=previous==null?Total.ZERO:new Total(previous.originalCount(),new BigInteger(previous.originalWeightGrams()),
                    previous.actualCount(),new BigInteger(previous.weightGrams()));
            var t=old.plus(total(order,p.code()));long planned=p.plannedCount()+(previous==null?0:previous.plannedCount());
            rows.put(key,new ProductRow(key.code(),key.name(),key.model(),planned,t.net,t.netGrams.toString(),
                    t.original,t.originalGrams.toString(),t.adjusted(),t.adjustedGrams()));
        }
        return List.copyOf(rows.values());
    }
}
