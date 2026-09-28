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
    private final TemporaryExportSource temporary;private final CorrectionExportSource correction;
    private final boolean sampleData;
    public ExportSnapshotReader(ExportFacts facts,PdaExportSource pda,PcExportSource pc,TemporaryExportSource temporary,
            CorrectionExportSource correction,
            @Value("${outbound.exports.sample-data:false}") boolean sampleData) {
        this.facts=facts;this.pda=pda;this.pc=pc;this.temporary=temporary;this.correction=correction;this.sampleData=sampleData;
    }
    /** 默认入口：结果版本 1（原确认）。既有调用点与行为一字不变。 */
    public ReportSnapshot read(ExportModels.Scope scope,ExportModels.Format format,Instant cutoff) {
        return read(scope,format,cutoff,1L);
    }
    /**
     * D 片1：按**指定结果版本**读快照。v==1 时与既有逻辑逐字相同（三条 WEB/PDA/TEMP 路径条件与顺序未动）；
     * v&gt;1 时该版本行**没有提交来源**（V46 `ck_result_channel` 第三支），故走 {@link CorrectionExportSource}
     * 从更正凭据组装——三条既有读取器不变，只是**多一条新分支**。
     */
    public ReportSnapshot read(ExportModels.Scope scope,ExportModels.Format format,Instant cutoff,long resultVersion) {
        if (resultVersion > 1) return correction.read(scope, resultVersion, cutoff);
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
