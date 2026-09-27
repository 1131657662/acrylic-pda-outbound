package com.acrylic.outbound.exports;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.reports.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ExportSnapshotReader {
    private final ExportFacts facts;private final PdaExportSource pda;private final PcExportSource pc;
    private final TemporaryExportSource temporary;
    private final boolean sampleData;
    public ExportSnapshotReader(ExportFacts facts,PdaExportSource pda,PcExportSource pc,TemporaryExportSource temporary,
            @Value("${outbound.exports.sample-data:false}") boolean sampleData) {
        this.facts=facts;this.pda=pda;this.pc=pc;this.temporary=temporary;this.sampleData=sampleData;
    }
    public ReportSnapshot read(ExportModels.Scope scope,ExportModels.Format format,Instant cutoff) {
        var head=facts.head(scope.warehouse(),scope.order());
        if(format==ExportModels.Format.PDF && Long.parseLong(head.count())>1000)
            throw new BusinessException(409,"EXPORT_LIMIT_EXCEEDED","单次PDF最多1000包，请使用Excel");
        boolean scanRecords=format==ExportModels.Format.SCAN_XLSX;
        // R08 子项 C：既有两条路径一字不动，只新增一种情况 —— 结果版本行的提交其实源自 TEMP
        // （source='WEB' 但提交是 TEMP 受理正文，例如 R06 受控关联 + 确认后的正式单）。TEMP 正文与正式 PDA
        // 正文字段形状不同，走 PdaExportSource 只会得到 INVALID_SUBMISSION（缺少 source.sourceOrderId）。
        var order=head.source().equals("PDA_TEMP")?temporary.read(head,scanRecords,cutoff):
                head.pdaSubmission()!=null&&facts.tempOrigin(head.pdaSubmission())?temporary.read(head,scanRecords,cutoff):
                head.pdaSubmission()!=null?pda.read(head,scanRecords):pc.read(head);
        var day=head.confirmedAt().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
        var metadata=new ReportSnapshot.Metadata(UUID.randomUUID().toString(),"result:1/revision:"+order.revision(),cutoff,cutoff,
                day,day,ReportSnapshot.Basis.ORIGINAL_CONFIRMED,sampleData?ReportSnapshot.Provenance.TEST_SAMPLE:ReportSnapshot.Provenance.SOURCE_SNAPSHOT,
                "已固定的订单首版确认事实、原提交正文及产品计划", "单个指定订单；单内按持久化order_scan_seq递增");
        ReportSnapshot snapshot;
        try { snapshot=ConfirmedV1Adapter.adapt(new ConfirmedV1Input(metadata,List.of(order))); }
        catch(IllegalArgumentException error) { throw new BusinessException(409,"EXPORT_SOURCE_INCONSISTENT","原确认数据不一致，无法生成文件，请联系管理员核对"); }
        if(scanRecords) {
            try { ScanRecordExcelRenderer.validate(snapshot); }
            catch(IllegalArgumentException error) { throw new BusinessException(409,"EXPORT_SCAN_SOURCE_UNAVAILABLE",error.getMessage()); }
        }
        return snapshot;
    }
    ReportSnapshot.Provenance provenance() {
        return sampleData?ReportSnapshot.Provenance.TEST_SAMPLE:ReportSnapshot.Provenance.SOURCE_SNAPSHOT;
    }
}
