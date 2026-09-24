package com.acrylic.outbound.exports;

import com.acrylic.outbound.audit.AuditWriter;
import com.acrylic.outbound.auth.Access;
import com.acrylic.outbound.common.*;
import com.acrylic.outbound.jobs.BackgroundJob;
import java.io.IOException;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExportDownloads {
    private final ExportJobQueries queries;private final ExportFiles files;private final ServerState state;
    private final AuditWriter audit;private final JsonText json;private final JdbcTemplate jdbc;private final Access access;
    public ExportDownloads(ExportJobQueries queries,ExportFiles files,ServerState state,AuditWriter audit,JsonText json,
            JdbcTemplate jdbc,Access access) {
        this.queries=queries;this.files=files;this.state=state;this.audit=audit;this.json=json;this.jdbc=jdbc;this.access=access;
    }
    @Transactional
    public Download read(BackgroundJob job,String requestId) {
        var original=queries.payload(job);state.lockWritable(original.epoch());
        jdbc.queryForList("SELECT id FROM app_user WHERE id=? FOR UPDATE",Ids.bytes(original.scope().actor()));
        var actor=access.require("order:read",original.scope().warehouse());
        var current=queries.owned(actor.id(),original.scope().warehouse(),job.id());
        var p=queries.payload(current);
        if(!queries.view(current).downloadable()) throw new BusinessException(409,"EXPORT_NOT_READY","文件尚未生成或已过期，请重新生成");
        try {
            var a=p.artifact();byte[] bytes=files.verified(files.artifact(job.id(),a.token(),p.format()),a.sha256(),a.bytes());
            audit.append(new AuditWriter.Event(p.scope().actor(),"REPORT_EXPORT_DOWNLOADED","ORDER",p.scope().order(),
                    json.encode(Map.of("jobId",job.id(),"sha256",a.sha256()))),requestId);
            String prefix=p.format()==ExportModels.Format.SCAN_XLSX?"scan-records-v1-":"original-v1-";
            return new Download(bytes,p.format(),prefix+job.id()+"."+p.format().extension);
        } catch(IOException error) { throw new BusinessException(410,"EXPORT_FILE_UNAVAILABLE","文件缺失或校验失败，请重新生成"); }
    }
    public record Download(byte[] bytes,ExportModels.Format format,String filename) { }
}
