package com.acrylic.outbound.exports;

import com.acrylic.outbound.reports.*;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;

/** Original-confirmed totals only; never interprets ledger movements or correction effects. */
public record ExportSummary(String basis,ExportModels.Range range,Instant readAt,int orderCount,
        long actualCount,String weightGrams,List<OrderRow> orders,List<ProductRow> products) {
    public record OrderRow(String id,String orderNo,long revision,String originalDay,String vehicle,
            long actualCount,String weightGrams) { }
    public record ProductRow(String code,String name,String model,long plannedCount,long actualCount,String weightGrams) { }
    private record ProductKey(String code,String name,String model) { }
    public static ExportSummary from(ReportSnapshot snapshot) {
        var orders=snapshot.orders().stream().map(ExportSummary::order).toList();
        var products=products(snapshot);var m=snapshot.metadata();
        return new ExportSummary("ORIGINAL_CONFIRMED",new ExportModels.Range(m.fromDate(),m.throughDate()),m.cutoff(),orders.size(),
                orders.stream().mapToLong(OrderRow::actualCount).sum(),
                orders.stream().map(o->new BigInteger(o.weightGrams())).reduce(BigInteger.ZERO,BigInteger::add).toString(),orders,products);
    }
    private static OrderRow order(OrderSnapshot o) {
        return new OrderRow(o.id(),o.number(),o.revision(),o.confirmedAt().atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate().toString(),
                o.originalHeader().vehicle(),o.originalLines().size(),
                o.originalLines().stream().map(l->new BigInteger(l.weightGrams())).reduce(BigInteger.ZERO,BigInteger::add).toString());
    }
    private static List<ProductRow> products(ReportSnapshot snapshot) {
        var rows=new LinkedHashMap<ProductKey,ProductRow>();
        for(var order:snapshot.orders()) for(var product:order.products()) {
            var key=new ProductKey(product.code(),product.name(),product.model());
            var previous=rows.getOrDefault(key,new ProductRow(key.code(),key.name(),key.model(),0,0,"0"));
            var lines=order.originalLines().stream().filter(l->l.productCode().equals(product.code())).toList();
            var weight=lines.stream().map(l->new BigInteger(l.weightGrams())).reduce(new BigInteger(previous.weightGrams()),BigInteger::add);
            rows.put(key,new ProductRow(key.code(),key.name(),key.model(),previous.plannedCount()+product.plannedCount(),
                    previous.actualCount()+lines.size(),weight.toString()));
        }
        return List.copyOf(rows.values());
    }
}
