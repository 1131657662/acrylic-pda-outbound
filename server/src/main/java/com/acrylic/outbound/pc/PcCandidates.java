package com.acrylic.outbound.pc;

import com.acrylic.outbound.common.*;
import com.github.benmanes.caffeine.cache.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PcCandidates {
    private final JdbcTemplate jdbc;
    private final Cache<UUID,AtomicInteger> attempts=Caffeine.newBuilder().maximumSize(10000).expireAfterWrite(Duration.ofMinutes(1)).build();
    public PcCandidates(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private static final String BASE="""
        WITH candidates AS (
        SELECT b.barcode,BIN_TO_UUID(b.id) baleId,s.delivery_code code,s.model_snapshot model,s.name_snapshot name,
          s.weight_g weight,CASE
          WHEN c.pc_work_id=? AND c.state='PC_RESERVED' THEN 'RECORDED'
          WHEN c.state='DISPATCHED' THEN 'DISPATCHED' WHEN c.state='QUARANTINED' THEN 'QUARANTINED'
          WHEN c.bale_id IS NOT NULL THEN 'RESERVED'
          WHEN ip.bale_id IS NULL OR ip.upstream_present=0 OR ip.book_status!='NOT_DEDUCTED' OR ip.blocked_reason IS NOT NULL THEN 'UNAVAILABLE'
          WHEN latest.delivery_code!=s.delivery_code OR latest.weight_g!=s.weight_g OR latest.model_snapshot!=s.model_snapshot OR latest.name_snapshot!=s.name_snapshot THEN 'CHANGED'
          WHEN COALESCE(e.cnt,0)>=l.planned_count THEN 'PLAN_FULL' ELSE 'AVAILABLE' END availability,
          IF(RIGHT(SUBSTRING(b.barcode,4),CHAR_LENGTH(?))=?,0,1) suffix_rank
        FROM order_revision r INNER JOIN order_line l ON l.revision_id=r.id
        INNER JOIN inventory_snapshot_bale s ON s.snapshot_id=r.inventory_snapshot_id AND s.delivery_code=l.delivery_code
        INNER JOIN bale b ON b.id=s.bale_id AND b.warehouse_id=r.warehouse_id
        LEFT JOIN inventory_position ip ON ip.bale_id=b.id
        LEFT JOIN inventory_snapshot_bale latest ON latest.snapshot_id=ip.last_seen_snapshot_id AND latest.bale_id=b.id
        LEFT JOIN bale_commitment c ON c.bale_id=b.id
        LEFT JOIN (SELECT delivery_code,COUNT(*) cnt FROM pc_entry WHERE work_id=? AND status='ACTIVE' GROUP BY delivery_code) e ON e.delivery_code=l.delivery_code
        WHERE r.order_id=? AND r.revision=? AND (?='' OR s.delivery_code=?) AND
          ((CHAR_LENGTH(?)=11 AND b.barcode=?) OR (CHAR_LENGTH(?)<11 AND SUBSTRING(b.barcode,4) LIKE ?)))
        """;
    public Result search(PcStore.Work work,UUID actor,Search input) {
        if(attempts.get(actor,key->new AtomicInteger()).incrementAndGet()>120) throw new BusinessException(429,"PC_SEARCH_RATE_LIMIT","查询过于频繁，请稍后再试");
        String query=input.query().strip().toUpperCase(Locale.ROOT);
        if(!query.matches("[A-Z0-9]{4,11}") || !input.code().matches("([A-Z0-9]{3})?") || input.page()<1 || input.page()>1_000_000 || (input.code().isEmpty() && input.page()!=1))
            throw new BusinessException(400,"INVALID_REQUEST","请输入4至10位条码片段，或完整11位条码");
        Object[] params={Ids.bytes(work.id()),query,query,Ids.bytes(work.id()),Ids.bytes(work.order()),work.revision(),input.code(),input.code(),query,query,query,"%"+query+"%"};
        var counts=jdbc.query(BASE+" SELECT code,COUNT(*) total FROM candidates GROUP BY code ORDER BY code",(rs,n)->new Count(rs.getString(1),rs.getLong(2)),params);
        int page=counts.size()==1?(int)Math.min(input.page(),Math.max(1,(counts.getFirst().total()+4)/5)):1;
        var args=new ArrayList<>(Arrays.asList(params));args.add((long)(page-1)*5+1);args.add((long)page*5);
        var rows=jdbc.query(BASE+", ranked AS (SELECT candidates.*,ROW_NUMBER() OVER(PARTITION BY code ORDER BY suffix_rank,barcode) rn FROM candidates) SELECT * FROM ranked WHERE rn BETWEEN ? AND ? ORDER BY code,rn",
            (rs,n)->new Candidate(rs.getString("baleId"),rs.getString("barcode"),rs.getString("code"),rs.getString("model"),rs.getString("name"),rs.getString("weight"),rs.getString("availability")),args.toArray());
        var groups=counts.stream().map(c->new Group(c.code(),c.total(),page,5,rows.stream().filter(row->row.deliveryCode().equals(c.code())).toList())).toList();
        return new Result(query,groups.size(),counts.stream().mapToLong(Count::total).sum(),groups);
    }
    public record Search(String query,String code,int page) { }
    public record Candidate(String baleId,String barcode,String deliveryCode,String model,String name,String weightGrams,String availability) { }
    public record Group(String deliveryCode,long total,int page,int pageSize,List<Candidate> items) { }
    public record Result(String query,int matchedCodes,long total,List<Group> groups) { }
    private record Count(String code,long total) { }
}
