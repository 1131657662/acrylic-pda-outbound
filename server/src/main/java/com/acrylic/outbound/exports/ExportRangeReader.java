package com.acrylic.outbound.exports;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.reports.*;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.exports.ExportModels.*;

/** Selects bounded, original confirmations in one caller-owned repeatable-read transaction. */
@Component
public class ExportRangeReader {
    private static final ZoneId SHANGHAI=ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate jdbc;private final ExportSnapshotReader reader;
    public ExportRangeReader(JdbcTemplate jdbc,ExportSnapshotReader reader) { this.jdbc=jdbc;this.reader=reader; }
    public static void validate(Range range) {
        if(range==null || range.fromDate()==null || range.throughDate()==null
                || range.fromDate().isAfter(range.throughDate())
                || ChronoUnit.DAYS.between(range.fromDate(),range.throughDate())>=31
                || range.fromDate().getYear()<2000 || range.throughDate().getYear()>9998)
            throw new BusinessException(400,"EXPORT_RANGE_INVALID","请选择不超过31天的有效原出库日期");
    }
    public ReportSnapshot read(Request request,Instant cutoff) {
        Range range=request.command().range();validate(range);
        if(request.command().format()==Format.SCAN_XLSX)
            throw new BusinessException(400,"EXPORT_FORMAT_UNSUPPORTED","跨订单汇总支持通用Excel或PDF，扫码记录请在单订单下载");
        var ids=members(request.scope().warehouse(),range,cutoff);
        if(ids.size()>100) throw new BusinessException(409,"EXPORT_LIMIT_EXCEEDED","单次汇总最多100单，请缩小日期范围");
        var snapshots=new ArrayList<ReportSnapshot>();long count=0;
        for(UUID id:ids) {
            var snapshot=reader.read(new Scope(request.scope().actor(),request.scope().warehouse(),id),request.command().format(),cutoff);
            count+=snapshot.orders().getFirst().originalLines().size();
            if(count>(request.command().format()==Format.PDF?1000:5000))
                throw new BusinessException(409,"EXPORT_LIMIT_EXCEEDED","汇总Excel最多5000包、PDF最多1000包，请缩小日期范围");
            snapshots.add(snapshot);
        }
        return combine(snapshots,range,cutoff);
    }
    private List<UUID> members(UUID warehouse,Range range,Instant cutoff) {
        return jdbc.query("""
            SELECT BIN_TO_UUID(o.id) FROM outbound_order o
              JOIN order_result_version r ON r.order_id=o.id AND r.result_version=1
            WHERE o.warehouse_id=? AND o.source='WEB' AND o.status='COMPLETED'
              AND r.created_at>=? AND r.created_at<? AND r.created_at<=?
            ORDER BY r.created_at,o.id LIMIT 101
            """,(rs,n)->UUID.fromString(rs.getString(1)),Ids.bytes(warehouse),
                Timestamp.from(range.fromDate().atStartOfDay(SHANGHAI).toInstant()),
                Timestamp.from(range.throughDate().plusDays(1).atStartOfDay(SHANGHAI).toInstant()),Timestamp.from(cutoff));
    }
    private ReportSnapshot combine(List<ReportSnapshot> snapshots,Range range,Instant cutoff) {
        var metadata=new ReportSnapshot.Metadata(UUID.randomUUID().toString(),"original-confirmed-v1",cutoff,cutoff,
                range.fromDate(),range.throughDate(),ReportSnapshot.Basis.ORIGINAL_CONFIRMED,reader.provenance(),
                "固定成员的正式订单原确认V1；不含后续更正、临时来源或未经核验的多设备来源",
                "原确认时刻升序、订单UUID升序；单内按持久化order_scan_seq递增");
        var snapshot=new ReportSnapshot(metadata,snapshots.stream().flatMap(s->s.orders().stream()).toList());
        try { SnapshotValidator.validate(snapshot); }
        catch(IllegalArgumentException error) {
            throw new BusinessException(409,"EXPORT_SOURCE_INCONSISTENT","跨订单原确认数据不一致，请核对来源");
        }
        return snapshot;
    }
}
