package com.acrylic.outbound.pc;

import com.acrylic.outbound.common.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PcEntries {
    private final JdbcTemplate jdbc;
    public PcEntries(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private static final String SELECT="""
        SELECT BIN_TO_UUID(e.id),BIN_TO_UUID(e.bale_id),b.barcode,e.sequence_no,e.dispatch_cycle,e.status,e.delivery_code,
          e.weight_g,e.raw_weight,e.model_snapshot,e.name_snapshot,e.operator_name,e.created_at,e.void_reason
        FROM pc_entry e INNER JOIN bale b ON b.id=e.bale_id
        """;
    public List<Entry> all(PcStore.Work work) { return jdbc.query(SELECT+" WHERE e.work_id=? ORDER BY e.sequence_no",this::map,Ids.bytes(work.id())); }
    public List<Product> products(PcStore.Work work) {
        return jdbc.query("""
            SELECT l.delivery_code,l.model_snapshot,l.name_snapshot,l.planned_count,COALESCE(e.cnt,0),COALESCE(e.weight,0)
            FROM order_line l INNER JOIN order_revision r ON r.id=l.revision_id
            LEFT JOIN (SELECT delivery_code,COUNT(*) cnt,SUM(weight_g) weight FROM pc_entry WHERE work_id=? AND status='ACTIVE' GROUP BY delivery_code) e ON e.delivery_code=l.delivery_code
            WHERE r.order_id=? AND r.revision=? ORDER BY l.delivery_code
            """,(rs,n)->new Product(rs.getString(1),rs.getString(2),rs.getString(3),rs.getLong(4),rs.getLong(5),rs.getString(6)),Ids.bytes(work.id()),Ids.bytes(work.order()),work.revision());
    }
    public Entry required(PcStore.Work work,UUID id) {
        if(id==null) throw new BusinessException(400,"INVALID_REQUEST","请选择需要撤销的原记录");
        var rows=jdbc.query(SELECT+" WHERE e.work_id=? AND e.id=?",this::map,Ids.bytes(work.id()),Ids.bytes(id));
        if(rows.isEmpty()) throw new BusinessException(404,"PC_ENTRY_NOT_FOUND","本单没有此录入记录");return rows.getFirst();
    }
    public EntryPage page(PcStore.Work work,Filter filter) {
        if(filter.page()<1 || filter.page()>1_000_000 || !Set.of(20,50,100).contains(filter.pageSize()) || !filter.search().matches("[A-Za-z0-9]{0,11}") || !filter.code().matches("[A-Z0-9]{0,3}"))
            throw new BusinessException(400,"INVALID_REQUEST","录入记录筛选条件无效");
        String from=" FROM pc_entry e INNER JOIN bale b ON b.id=e.bale_id WHERE e.work_id=? AND (?='' OR b.barcode LIKE ?) AND (?='' OR e.delivery_code=?)";
        Object[] args={Ids.bytes(work.id()),filter.search(),"%"+filter.search().toUpperCase(Locale.ROOT)+"%",filter.code(),filter.code()};
        long total=jdbc.queryForObject("SELECT COUNT(*)"+from,Long.class,args);int page=(int)Math.min(filter.page(),Math.max(1,(total+filter.pageSize()-1)/filter.pageSize()));
        var params=new ArrayList<>(Arrays.asList(args));params.add(filter.pageSize());params.add((long)(page-1)*filter.pageSize());
        var rows=jdbc.query(SELECT+from.substring(from.indexOf(" WHERE"))+" ORDER BY e.sequence_no DESC LIMIT ? OFFSET ?",this::map,params.toArray());
        return new EntryPage(rows,total,page,filter.pageSize());
    }
    private Entry map(java.sql.ResultSet rs,int n) throws java.sql.SQLException {
        return new Entry(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getLong(5),rs.getString(6),rs.getString(7),
            rs.getString(8),rs.getString(9),rs.getString(10),rs.getString(11),rs.getString(12),rs.getTimestamp(13).toInstant().toString(),rs.getString(14));
    }
    public record Entry(String id,String baleId,String barcode,String sequence,long cycle,String status,String deliveryCode,String weightGrams,
        String rawWeight,String model,String name,String operatorName,String createdAt,String voidReason) { }
    public record Product(String deliveryCode,String model,String name,long plannedCount,long actualCount,String weightGrams) { }
    public record Filter(String search,String code,int page,int pageSize) { }
    public record EntryPage(List<Entry> items,long total,int page,int pageSize) { }
}
