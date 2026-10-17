package com.acrylic.outbound.exports;

import com.acrylic.outbound.audit.AuditWriter;
import com.acrylic.outbound.auth.Access;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.jobs.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.acrylic.outbound.exports.ExportModels.*;

@Service
public class ExportRetry {
    public record RetryRequest(Scope scope,UUID original,UUID key,String epoch) { }
    private final JdbcTemplate jdbc;private final ServerState state;private final ExportJobQueries queries;
    private final BackgroundJobStore jobs;private final ExportFiles files;private final JsonText json;
    private final ExportRendererProcess renderer;private final AuditWriter audit;
    private final Access access;
    public ExportRetry(JdbcTemplate jdbc,ServerState state,ExportJobQueries queries,BackgroundJobStore jobs,
            ExportFiles files,JsonText json,ExportRendererProcess renderer,AuditWriter audit,Access access) {
        this.jdbc=jdbc;this.state=state;this.queries=queries;this.jobs=jobs;this.files=files;this.json=json;this.renderer=renderer;this.audit=audit;this.access=access;
    }
    @Transactional
    public View create(RetryRequest request,String requestId) {
        state.lockWritable(request.epoch());
        jdbc.queryForList("SELECT id FROM app_user WHERE id=? FOR UPDATE",byte[].class,Ids.bytes(request.scope().actor()));
        access.require("order:read",request.scope().warehouse());
        var original=queries.owned(request.scope().actor(),request.scope().warehouse(),request.original());
        var p=queries.payload(original);String key="r08:"+request.scope().actor()+":"+request.key();
        var existing=jobs.findByDedupeKey(key);
        if(existing.isPresent()) return replay(existing.get(),request);
        validate(original,p,request.epoch());renderer.requireAvailable(p.format());queries.checkCapacity(request.scope().actor());
        Instant now=Instant.now();var payload=new Payload(p.scope(),p.format(),p.basis(),p.epoch(),p.snapshotHash(),p.snapshotId(),
                p.orderNo(),p.revision(),p.originalDay(),p.template(),now.plus(Duration.ofDays(7)),null,p.range(),p.orderCount(),original.id(),p.resultVersion(),p.members());
        var job=jobs.enqueue(new NewBackgroundJob(TYPE,key,json.encode(payload),3,now));
        audit.append(new AuditWriter.Event(p.scope().actor(),"REPORT_EXPORT_RETRIED","EXPORT",job.id(),
                json.encode(Map.of("retryOf",original.id(),"snapshotHash",p.snapshotHash()))),requestId);
        return queries.view(job);
    }
    private void validate(BackgroundJob original,Payload p,String epoch) {
        if(!p.epoch().equals(epoch) || !epoch.equals(original.recoveryEpoch()))
            throw new BusinessException(409,"EXPORT_GENERATION_CHANGED","恢复代次不同或原任务代次未知，请重新查询并生成文件");
        if(p.expiresAt().isAfter(Instant.now()) && original.status()!=BackgroundJob.Status.FAILED && original.status()!=BackgroundJob.Status.SUCCEEDED)
            throw new BusinessException(409,"EXPORT_NOT_RETRYABLE","原文件任务尚在处理，请等待结束后再试");
        if(!p.template().equals(p.format().template()))
            throw new BusinessException(409,"EXPORT_TEMPLATE_UNAVAILABLE","原模板已不可用，请重新查询并生成文件");
        try { files.verify(files.snapshot(p.snapshotHash()),p.snapshotHash()); }
        catch(java.io.IOException error) { throw new BusinessException(410,"EXPORT_SNAPSHOT_UNAVAILABLE","原快照缺失或校验失败，请重新查询并生成文件"); }
    }
    private View replay(BackgroundJob job,RetryRequest request) {
        var p=queries.payload(job);
        if(!job.jobType().equals(TYPE) || !request.original().equals(p.retryOf()) || !request.epoch().equals(p.epoch())
                || !request.scope().actor().equals(p.scope().actor()) || !request.scope().warehouse().equals(p.scope().warehouse()))
            throw new BusinessException(409,"IDEMPOTENCY_CONFLICT","幂等键已用于其他导出请求");
        return queries.view(job);
    }
}
