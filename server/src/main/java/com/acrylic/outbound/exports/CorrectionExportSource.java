package com.acrylic.outbound.exports;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.reports.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * D 片1/片2：**更正净结果**（v&gt;1）的只读快照来源。
 *
 * <p>为什么需要它：`V46` 的 {@code ck_result_channel} 第三支规定 v&gt;1 的结果版本行
 * **没有提交来源**（{@code submission_id}/{@code pc_submission_id} 皆为 NULL，只有 {@code source_correction_id}），
 * 而 `PdaExportSource`/`PcExportSource`/`TemporaryExportSource` 三条既有读取器**全部以"提交"为锚**。
 * 所以更正净结果只能从更正侧的事实组装：该版本的 {@code order_effective_scan}（逐包净贡献）
 * ＋{@link CorrectionAdjustmentSource}（可追溯的更正凭据），而**原始事实行/产品计划沿用原确认那一版的既有读取结果**。
 *
 * <p>**形状零变更**：产出仍是 {@link ReportSnapshot}/{@link OrderSnapshot}（后者本就带
 * {@code resultVersion}/{@code adjustments}）；片2 只把口径从 `CURRENT_EFFECTIVE` 改为语义更准的
 * {@link ReportSnapshot.Basis#CORRECTED}（枚举加值，不动任何字段）。
 *
 * <p>**不做的事**：不写业务表、不释放能力、不碰请求面（片3）；只读 + 由调用方落库 JSON。
 */
@Component
public class CorrectionExportSource {
    private final JdbcTemplate jdbc;
    private final ExportFacts facts;
    private final PdaExportSource pda;
    private final TemporaryExportSource temporary;
    private final CorrectionAdjustmentSource adjustments;
    public CorrectionExportSource(JdbcTemplate jdbc, ExportFacts facts, PdaExportSource pda,
            TemporaryExportSource temporary, CorrectionAdjustmentSource adjustments) {
        this.jdbc=jdbc; this.facts=facts; this.pda=pda; this.temporary=temporary; this.adjustments=adjustments;
    }

    /** 一个 v&gt;1 快照所需的事实，已按"缺一即拒"的纪律读出。 */
    public ReportSnapshot read(ExportModels.Scope scope, long resultVersion, Instant cutoff) {
        try { return checked(scope, resultVersion, cutoff); }
        catch (BusinessException error) { throw error; }
        catch (RuntimeException damaged) {
            throw new BusinessException(409, "EXPORT_SOURCE_UNAVAILABLE", "更正净结果凭据不完整或不一致，无法生成文件");
        }
    }

    private ReportSnapshot checked(ExportModels.Scope scope, long resultVersion, Instant cutoff) {
        ExportFacts.check(resultVersion > 1, "更正净结果只适用于结果版本大于 1");
        var corrected = facts.head(scope.warehouse(), scope.order(), resultVersion);
        ExportFacts.check(corrected.resultVersion().equals(Long.toString(resultVersion)), "结果版本行缺失");
        var document = facts.correctionDocumentFor(scope.order(), resultVersion);
        ExportFacts.check(document != null, "该结果版本没有对应的更正文档");

        // 原始事实行/产品计划沿用**原确认那一版（v1）**的既有读取器，并与 ExportSnapshotReader 同一套选路判定：
        // TEMP 受理正文没有正式 PDA 正文的 source.sourceOrderId，走错读取器会在解析处直接失败。
        var origin = facts.head(scope.warehouse(), scope.order(), 1L);
        ExportFacts.check(origin.submission() != null, "原确认提交缺失");
        var source = facts.tempOrigin(origin.submission())
            ? temporary.read(origin, false, cutoff) : pda.read(origin, false);
        int revision = Math.toIntExact(source.revision());

        var effective = effectiveScan(scope.order(), resultVersion);
        ExportFacts.check(!effective.isEmpty(), "该结果版本没有逐包有效快照");
        var effectiveLines = new ArrayList<OrderSnapshot.EffectiveLine>();
        long countSum = 0, weightSum = 0;
        for (var row : effective) {
            boolean active = Boolean.TRUE.equals(row.get("is_effective"));
            long weight = Long.parseLong(String.valueOf(row.get("effective_weight_g")));
            effectiveLines.add(new OrderSnapshot.EffectiveLine(String.valueOf(row.get("dispatch_id")),
                active ? 1 : 0, Long.toString(active ? weight : 0)));
            if (active) { countSum++; weightSum += weight; }
        }
        ExportFacts.check(Long.toString(countSum).equals(corrected.count())
            && Long.toString(weightSum).equals(corrected.weight()), "逐包有效快照与结果版本合计不一致");

        var evidence=adjustments.read(scope.order(),resultVersion);
        var order = new OrderSnapshot(scope.order().toString(), source.number(), revision, resultVersion, "CORRECTED",
            source.header(), effectiveHeader(source.header(),evidence), origin.confirmedAt(),
            new OrderSnapshot.SequenceProof(OrderSnapshot.SequenceKind.PERSISTED_ORDER_SEQUENCE,
                "dispatch_record.order_scan_seq / correction:" + document),
            source.products(), source.dispatches().stream().map(ConfirmedV1Input.Dispatch::line).toList(),
            effectiveLines, evidence, source.exceptions());
        var day = cutoff.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
        var metadata = new ReportSnapshot.Metadata(UUID.randomUUID().toString(),
            "result:" + resultVersion + "/revision:" + revision, cutoff, cutoff, day, day,
            ReportSnapshot.Basis.CORRECTED, ReportSnapshot.Provenance.SOURCE_SNAPSHOT,
            "固定结果版本 " + resultVersion + " 的更正净结果（更正文档 " + document + "）",
            "单个指定订单；单内按持久化order_scan_seq递增");
        return new ReportSnapshot(metadata, List.of(order));
    }

    /** Project checked scalar changes into the existing display fields. */
    private static OrderSnapshot.Header effectiveHeader(OrderSnapshot.Header original,List<Adjustment> evidence) {
        String vehicle=original.vehicle(),container=original.container(),remark=original.remark();
        for(var adjustment:evidence) {
            if(adjustment.kind()!=Adjustment.Kind.HEADER_CORRECTION)continue;
            switch(adjustment.headerField()) {
                case "vehicle" -> vehicle=adjustment.afterText();
                case "container" -> container=adjustment.afterText();
                case "remark" -> remark=adjustment.afterText();
                default -> { }
            }
        }
        return new OrderSnapshot.Header(vehicle,container,remark);
    }

    /** 该版本的逐包有效值（`order_effective_scan`：is_effective + 有效重量，含被归零的行）。 */
    private List<Map<String, Object>> effectiveScan(UUID order, long version) {
        return jdbc.queryForList("""
            SELECT BIN_TO_UUID(s.dispatch_id) dispatch_id,s.is_effective,CAST(s.effective_weight_g AS CHAR) effective_weight_g
            FROM order_effective_scan s WHERE s.order_id=? AND s.result_version=? ORDER BY s.original_sequence
            """, Ids.bytes(order), version);
    }
}
