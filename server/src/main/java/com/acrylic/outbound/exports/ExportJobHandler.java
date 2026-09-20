package com.acrylic.outbound.exports;

import com.acrylic.outbound.audit.AuditWriter;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.jobs.*;
import com.acrylic.outbound.reports.ReportSnapshot;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.exports.ExportModels.*;

@Component
public class ExportJobHandler implements BackgroundJobHandler {
    private final ExportJobQueries queries;private final ExportFiles files;private final ExportRendererProcess renderer;
    private final ServerState state;private final JdbcTemplate jdbc;private final JsonText json;private final AuditWriter audit;
    public ExportJobHandler(ExportJobQueries queries,ExportFiles files,ExportRendererProcess renderer,ServerState state,
            JdbcTemplate jdbc,JsonText json,AuditWriter audit) {
        this.queries=queries;this.files=files;this.renderer=renderer;this.state=state;this.jdbc=jdbc;this.json=json;this.audit=audit;
    }
    @Override public String jobType() { return TYPE; }
    @Override public Runnable execute(BackgroundJobExecution execution) throws Exception {
        var job=execution.job();var p=queries.payload(job);validate(p);execution.renew();
        var snapshot=files.snapshot(p.snapshotHash());
        try { files.verify(snapshot,p.snapshotHash()); }
        catch(java.io.IOException error) { throw new JobFailure("EXPORT_SNAPSHOT_UNAVAILABLE",false); }
        var output=files.artifact(job.id(),job.leaseToken(),p.format());
        renderer.render(execution,snapshot,output,p.format());execution.renew();
        files.force(output);var artifact=new Artifact(job.leaseToken(),files.digest(output),Files.size(output));
        return ()->publish(job,p,artifact);
    }
    private void validate(Payload p) throws JobFailure {
        if(!p.expiresAt().isAfter(Instant.now())) throw new JobFailure("EXPORT_EXPIRED",false);
        if(!p.format().template().equals(p.template())) throw new JobFailure("EXPORT_TEMPLATE_UNAVAILABLE",false);
        if(!p.epoch().equals(state.current().epoch())) throw new JobFailure("EXPORT_GENERATION_CHANGED",false);
        if(!"NORMAL".equals(state.current().mode())) throw new JobFailure("EXPORT_RECOVERY_IN_PROGRESS",true);
    }
    private void publish(BackgroundJob job,Payload p,Artifact artifact) {
        state.lockWritable(p.epoch());
        if(!p.expiresAt().isAfter(Instant.now())) throw new BusinessException(409,"EXPORT_EXPIRED","文件任务已过期");
        int changed=jdbc.update("UPDATE background_job SET payload=JSON_SET(payload,'$.artifact',CAST(? AS JSON)) WHERE id=? AND status='SUCCEEDED'",
                json.encode(artifact),Ids.bytes(job.id()));
        if(changed!=1) throw new IllegalStateException("Export publication lost");
        audit.append(new AuditWriter.Event(p.scope().actor(),"REPORT_EXPORT_GENERATED","ORDER",p.scope().order(),
                json.encode(Map.of("jobId",job.id(),"sha256",artifact.sha256(),"bytes",artifact.bytes()))),"r08:"+job.id());
    }
}
