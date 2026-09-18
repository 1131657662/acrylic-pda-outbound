package com.acrylic.outbound.pc;

import com.acrylic.outbound.common.*;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

@Component
public class PcStock {
    private final JdbcTemplate jdbc;
    public PcStock(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Bale fixed(PcStore.Work work,String input) {
        String barcode=input.strip().toUpperCase(Locale.ROOT);
        if(!barcode.matches("[A-Z0-9]{11}")) throw new BusinessException(400,"INVALID_BARCODE","本包未计入，条码应为11位数字或英文字母");
        var rows=jdbc.query("""
            SELECT BIN_TO_UUID(b.id),b.barcode,s.delivery_code,s.weight_g,s.raw_weight,s.model_snapshot,s.name_snapshot,b.dispatch_cycle
            FROM inventory_snapshot_bale s INNER JOIN bale b ON b.id=s.bale_id
            INNER JOIN order_revision r ON r.order_id=? AND r.revision=?
            INNER JOIN order_line l ON l.revision_id=r.id AND l.delivery_code=s.delivery_code
            WHERE s.snapshot_id=? AND b.barcode=? AND b.warehouse_id=?
            """,(rs,n)->new Bale(UUID.fromString(rs.getString(1)),rs.getString(2),rs.getString(3),rs.getLong(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getLong(8)),
            Ids.bytes(work.order()),work.revision(),Ids.bytes(work.snapshot()),barcode,Ids.bytes(work.warehouse()));
        if(rows.isEmpty()) throw new BusinessException(409,"PC_BARCODE_OUTSIDE_PLAN","本包未计入，本单固定库存和产品计划中没有此条码");return rows.getFirst();
    }
    public String conflict(PcStore.Work work,Bale bale,boolean owned) {
        long cycle=jdbc.queryForObject("SELECT dispatch_cycle FROM bale WHERE id=? FOR UPDATE",Long.class,Ids.bytes(bale.id()));
        var positions=jdbc.query("""
            SELECT p.upstream_present,p.book_status,p.blocked_reason,s.delivery_code,s.weight_g,s.model_snapshot,s.name_snapshot
            FROM inventory_position p INNER JOIN inventory_snapshot_bale s ON s.snapshot_id=p.last_seen_snapshot_id AND s.bale_id=p.bale_id WHERE p.bale_id=? FOR UPDATE
            """,(rs,n)->new Position(rs.getBoolean(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getLong(5),rs.getString(6),rs.getString(7)),Ids.bytes(bale.id()));
        var owners=jdbc.query("SELECT BIN_TO_UUID(pc_work_id),state,dispatch_cycle FROM bale_commitment WHERE bale_id=? FOR UPDATE",
            (rs,n)->new Owner(rs.getString(1),rs.getString(2),rs.getLong(3)),Ids.bytes(bale.id()));
        if(cycle!=bale.cycle()) return "出库轮次已变化";
        if(owned && (owners.isEmpty() || !work.id().toString().equals(owners.getFirst().id()) || !owners.getFirst().state().equals("PC_RESERVED") || owners.getFirst().cycle()!=cycle)) return "本单原占用不一致";
        if(!owned && !owners.isEmpty()) return owners.getFirst().state().equals("QUARANTINED")?"实物已隔离":owners.getFirst().state().equals("DISPATCHED")?"已经确认出库":"已有作业占用";
        if(positions.isEmpty() || !positions.getFirst().present() || !positions.getFirst().book().equals("NOT_DEDUCTED") || positions.getFirst().blocked()!=null) return "当前库存不可用";
        var p=positions.getFirst();if(!p.code().equals(bale.code()) || p.weight()!=bale.weight() || !p.model().equals(bale.model()) || !p.name().equals(bale.name())) return "库存资料已变化";
        return null;
    }
    public ArrayNode review(PcStore.Work work,List<PcEntries.Entry> entries) {
        var issues=object().putArray("issues");
        for(var entry:entries.stream().filter(e->e.status().equals("ACTIVE")).sorted(Comparator.comparing(PcEntries.Entry::baleId)).toList()) {
            var bale=new Bale(UUID.fromString(entry.baleId()),entry.barcode(),entry.deliveryCode(),Long.parseLong(entry.weightGrams()),entry.rawWeight(),entry.model(),entry.name(),entry.cycle());
            String conflict=conflict(work,bale,true);if(conflict!=null) issues.add(object().put("code","PC_STOCK_CONFLICT").put("barcode",entry.barcode()).put("reason",conflict));
        }
        return issues;
    }
    public record Bale(UUID id,String barcode,String code,long weight,String rawWeight,String model,String name,long cycle) { }
    private record Position(boolean present,String book,String blocked,String code,long weight,String model,String name) { }
    private record Owner(String id,String state,long cycle) { }
}
