package com.acrylic.outbound.exports;

import com.acrylic.outbound.audit.AuditWriter;
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
    private final JsonText json;private final AuditWriter audit;private final ExportRendererProcess renderer;
    public ExportProducer(JdbcTemplate jdbc,ServerState state,BackgroundJobStore jobs,ExportJobQueries queries,
            ExportSnapshotReader snapshots,ExportFiles files,JsonText json,AuditWriter audit,ExportRendererProcess renderer) {
        this.jdbc=jdbc;this.state=state;this.jobs=jobs;this.queries=queries;this.snapshots=snapshots;
        this.files=files;this.json=json;this.audit=audit;this.renderer=renderer;
    }
    @Transactional(isolation=Isolation.REPEATABLE_READ,rollbackFor=IOException.class)
    public View create(Request request,String requestId) throws IOException {
        state.lockWritable(request.epoch());
        jdbc.queryForList("SELECT id FROM app_user WHERE id=? FOR UPDATE",Ids.bytes(request.scope().actor()));
        String key="r08:"+request.scope().actor()+":"+request.key();
        var existing=jobs.findByDedupeKey(key);
        if(existing.isPresent()) return replay(existing.get(),request);
        renderer.requireAvailable(request.command().format());queries.checkCapacity(request.scope().actor());
        Instant now=Instant.now();var snapshot=snapshots.read(request.scope(),request.command().format(),now);
        var order=snapshot.orders().getFirst();String hash=files.store(snapshot);
        var p=new Payload(request.scope(),request.command().format(),request.command().basis(),request.epoch(),hash,
                snapshot.metadata().snapshotId(),order.number(),order.revision(),snapshot.metadata().fromDate().toString(),
                request.command().format().template(),now.plus(Duration.ofDays(7)),null);
        var job=jobs.enqueue(new NewBackgroundJob(TYPE,key,json.encode(p),3,now));
        audit.append(new AuditWriter.Event(request.scope().actor(),"REPORT_EXPORT_REQUESTED","ORDER",request.scope().order(),
                json.encode(Map.of("jobId",job.id(),"format",p.format(),"basis",p.basis(),"snapshotHash",hash))),requestId);
        return queries.view(job);
    }
    private View replay(BackgroundJob job,Request request) {
        var p=queries.payload(job);
        if(!job.jobType().equals(TYPE) || !p.scope().equals(request.scope()) || p.format()!=request.command().format()
                || !p.basis().equals(request.command().basis()) || !p.epoch().equals(request.epoch()))
            throw new BusinessException(409,"IDEMPOTENCY_CONFLICT","幂等键已用于其他导出请求");
        return queries.view(job);
    }
}
