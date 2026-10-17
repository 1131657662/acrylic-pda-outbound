package com.acrylic.outbound.exports;

import com.acrylic.outbound.audit.AuditWriter;
import com.acrylic.outbound.auth.Access;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.jobs.*;
import com.acrylic.outbound.reports.ReportSnapshot;
import java.io.IOException;
import java.time.*;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.exports.ExportModels.*;

@Service
public class ExportProducer {
    private final JdbcTemplate jdbc;private final ServerState state;private final BackgroundJobStore jobs;
    private final ExportJobQueries queries;private final ExportSnapshotReader snapshots;private final ExportFiles files;
    private final JsonText json;private final AuditWriter audit;private final ExportRendererProcess renderer;private final ExportRangeReader ranges;
    private final Access access;
    private final ExportResultSelection selection;
    public ExportProducer(JdbcTemplate jdbc,ServerState state,BackgroundJobStore jobs,ExportJobQueries queries,
            ExportSnapshotReader snapshots,ExportFiles files,JsonText json,AuditWriter audit,ExportRendererProcess renderer,ExportRangeReader ranges,
            Access access,ExportResultSelection selection) {
        this.jdbc=jdbc;this.state=state;this.jobs=jobs;this.queries=queries;this.snapshots=snapshots;
        this.files=files;this.json=json;this.audit=audit;this.renderer=renderer;this.ranges=ranges;this.access=access;
        this.selection=selection;
    }
    @Transactional(isolation=Isolation.REPEATABLE_READ,rollbackFor=IOException.class)
    public View create(Request request,String requestId) throws IOException {
        if((request.scope().order()==null)!=(request.command().range()!=null))
            throw new BusinessException(400,"EXPORT_RANGE_INVALID","单订单与汇总范围不匹配");
        ExportResultSelection.validate(request);
        state.lockWritable(request.epoch());
        jdbc.queryForList("SELECT id FROM app_user WHERE id=? FOR UPDATE",byte[].class,Ids.bytes(request.scope().actor()));
        access.require("order:read",request.scope().warehouse());
        String key="r08:"+request.scope().actor()+":"+request.key();
        var existing=jobs.findByDedupeKey(key);
        if(existing.isPresent()) return replay(existing.get(),request);
        renderer.requireAvailable(request.command().format());queries.checkCapacity(request.scope().actor());
        Instant now=jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)",java.sql.Timestamp.class).toInstant();
        var snapshot=snapshot(request,now);String hash=files.store(snapshot);
        var p=payload(request,snapshot,hash,now);
        var job=jobs.enqueue(new NewBackgroundJob(TYPE,key,json.encode(p),3,now));
        audit.append(new AuditWriter.Event(request.scope().actor(),"REPORT_EXPORT_REQUESTED","ORDER",request.scope().order(),
                json.encode(Map.of("jobId",job.id(),"format",p.format(),"basis",p.basis(),"snapshotHash",hash))),requestId);
        return queries.view(job);
    }
    private ReportSnapshot snapshot(Request request,Instant now) {
        if(request.scope().order()==null) return ranges.read(request,now);
        long version=selection.version(request.scope().order(),request.command(),now);
        var snapshot=snapshots.read(request.scope(),request.command().format(),now,version);
        if(request.command().format()==Format.PDF && snapshot.orders().getFirst().originalLines().size()>1000)
            throw new BusinessException(409,"EXPORT_LIMIT_EXCEEDED","单次PDF最多1000包，请使用Excel");
        if(!"CURRENT_EFFECTIVE".equals(request.command().basis()))return snapshot;
        var m=snapshot.metadata();
        return new ReportSnapshot(new ReportSnapshot.Metadata(m.snapshotId(),m.dataVersion(),m.cutoff(),m.generatedAt(),m.fromDate(),m.throughDate(),
                ReportSnapshot.Basis.CURRENT_EFFECTIVE,m.provenance(),"截止时最新净值；已固定结果版本 "+version,m.orderSortDescription()),snapshot.orders());
    }
    private Payload payload(Request request,ReportSnapshot snapshot,String hash,Instant now) {
        var order=request.scope().order()==null?null:snapshot.orders().getFirst();
        String label="CURRENT_EFFECTIVE".equals(request.command().basis())?"跨订单最新净值汇总":"跨订单原确认汇总";
        return new Payload(request.scope(),request.command().format(),request.command().basis(),request.epoch(),hash,
                snapshot.metadata().snapshotId(),order==null?label:order.number(),order==null?0:order.revision(),snapshot.metadata().fromDate().toString(),
                request.command().format().template(),now.plus(Duration.ofDays(7)),null,request.command().range(),snapshot.orders().size(),null,
                order==null?request.command().version():order.resultVersion(),
                snapshot.orders().stream().map(o->new Member(java.util.UUID.fromString(o.id()),o.resultVersion())).toList());
    }
    private View replay(BackgroundJob job,Request request) {
        var p=queries.payload(job);
        if(!job.jobType().equals(TYPE) || !p.scope().equals(request.scope()) || p.format()!=request.command().format()
                || !p.basis().equals(request.command().basis()) || !p.epoch().equals(request.epoch())
                || (!"CURRENT_EFFECTIVE".equals(p.basis()) && p.version()!=request.command().version())
                || !java.util.Objects.equals(p.range(),request.command().range()) || p.retryOf()!=null)
            throw new BusinessException(409,"IDEMPOTENCY_CONFLICT","幂等键已用于其他导出请求");
        return queries.view(job);
    }
}
