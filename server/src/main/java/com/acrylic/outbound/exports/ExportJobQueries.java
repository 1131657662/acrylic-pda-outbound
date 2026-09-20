package com.acrylic.outbound.exports;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.jobs.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.exports.ExportModels.*;

@Component
public class ExportJobQueries {
    private final JdbcTemplate jdbc;private final BackgroundJobStore jobs;private final ObjectMapper json;private final ServerState state;
    public ExportJobQueries(JdbcTemplate jdbc,BackgroundJobStore jobs,ObjectMapper json,ServerState state) {
        this.jdbc=jdbc;this.jobs=jobs;this.json=json;this.state=state;
    }
    public Payload payload(BackgroundJob job) {
        try { return json.readValue(job.payload(),Payload.class); }
        catch(JsonProcessingException error) { throw new IllegalStateException("Invalid export payload",error); }
    }
    public BackgroundJob owned(UUID actor,UUID warehouse,UUID id) {
        var job=jobs.find(id).filter(j->j.jobType().equals(TYPE)).orElseThrow(ExportJobQueries::missing);
        var scope=payload(job).scope();
        if(!scope.actor().equals(actor) || !scope.warehouse().equals(warehouse)) throw missing();return job;
    }
    public View view(BackgroundJob job) {
        var p=payload(job);var a=p.artifact();boolean expired=!p.expiresAt().isAfter(Instant.now());
        boolean old=!p.epoch().equals(state.current().epoch());
        return new View(job.id(),p.orderNo(),p.format(),p.basis(),1,p.revision(),old?"INVALIDATED":expired?"EXPIRED":job.status().name(),
                job.attemptCount(),job.lastError(),job.createdAt(),p.expiresAt(),p.originalDay(),p.snapshotId(),
                a==null?null:a.sha256(),a==null?null:a.bytes(),!old && !expired && a!=null && job.status()==BackgroundJob.Status.SUCCEEDED,
                p.scope().order(),p.range(),p.orderCount()==null?1:p.orderCount(),p.retryOf());
    }
    public ExportModels.Page list(Scope scope,UUID before) {
        return list(scope,new Filter(before,null,null));
    }
    public record Filter(UUID before,Format format,String status) { }
    public ExportModels.Page list(Scope scope,Filter filter) {
        var args=new ArrayList<Object>(List.of(TYPE,scope.actor().toString(),scope.warehouse().toString()));
        String sql=conditions(scope,filter,args);
        var ids=jdbc.query("""
            SELECT BIN_TO_UUID(id) FROM background_job WHERE job_type=? AND payload->>'$.scope.actor'=?
              AND payload->>'$.scope.warehouse'=?
            """+sql+" ORDER BY created_at DESC,id DESC LIMIT 21",(rs,n)->UUID.fromString(rs.getString(1)),args.toArray());
        return new ExportModels.Page(ids.stream().limit(20).map(id->view(jobs.find(id).orElseThrow())).toList(),ids.size()>20?ids.get(19):null);
    }
    private String conditions(Scope scope,Filter filter,List<Object> args) {
        String sql="";
        if(scope.order()!=null) { sql+=" AND payload->>'$.scope.order'=?";args.add(scope.order().toString()); }
        if(filter.format()!=null) { sql+=" AND payload->>'$.format'=?";args.add(filter.format().name()); }
        if(filter.status()!=null) {
            if(!Set.of("PENDING","RUNNING","RETRY","SUCCEEDED","FAILED","EXPIRED","INVALIDATED").contains(filter.status()))
                throw new BusinessException(400,"EXPORT_STATUS_INVALID","文件任务状态无效");
            sql+=" AND CASE WHEN payload->>'$.epoch'<>? THEN 'INVALIDATED' WHEN CAST(REPLACE(REPLACE(payload->>'$.expiresAt','T',' '),'Z','') AS DATETIME(6))<=UTC_TIMESTAMP(6) THEN 'EXPIRED' ELSE status END=?";
            args.add(state.current().epoch());args.add(filter.status());
        }
        if(filter.before()!=null) {
            var cursor=owned(scope.actor(),scope.warehouse(),filter.before());
            if(scope.order()!=null && !scope.order().equals(payload(cursor).scope().order())) throw missing();
            sql+=" AND (created_at<? OR (created_at=? AND id<?))";
            args.add(java.sql.Timestamp.from(cursor.createdAt()));args.add(java.sql.Timestamp.from(cursor.createdAt()));args.add(Ids.bytes(cursor.id()));
        }
        return sql;
    }
    public void checkCapacity(UUID actor) {
        Long active=jdbc.queryForObject("""
            SELECT COUNT(*) FROM background_job WHERE job_type=? AND payload->>'$.scope.actor'=?
              AND payload->>'$.epoch'=? AND created_at>UTC_TIMESTAMP(3)-INTERVAL 7 DAY AND status IN ('PENDING','RUNNING','RETRY')
            """,Long.class,TYPE,actor.toString(),state.current().epoch());
        if(active!=null && active>=5) throw new BusinessException(429,"EXPORT_QUEUE_FULL","最多同时生成5个文件，请等待已有任务结束");
    }
    static BusinessException missing() { return new BusinessException(404,"EXPORT_NOT_FOUND","文件任务不存在或无权访问"); }
}
