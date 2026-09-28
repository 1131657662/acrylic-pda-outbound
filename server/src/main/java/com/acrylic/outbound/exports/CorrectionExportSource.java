package com.acrylic.outbound.exports;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.reports.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * D 片1：**更正净结果**（v&gt;1）的只读快照来源。
 *
 * <p>为什么需要它：`V46` 的 {@code ck_result_channel} 第三支规定 v&gt;1 的结果版本行
 * **没有提交来源**（{@code submission_id}/{@code pc_submission_id} 皆为 NULL，只有 {@code source_correction_id}），
 * 而 `PdaExportSource`/`PcExportSource`/`TemporaryExportSource` 三条既有读取器**全部以"提交"为锚**。
 * 所以更正净结果只能从更正侧的事实组装：{@code correction_document} + {@code correction_line}
 * + 该版本的 {@code order_effective_scan}，而**原始事实行/产品计划沿用原确认那一版（v1）的既有读取结果**。
 *
 * <p>**不改任何形状**：产出仍是 {@link ReportSnapshot}/{@link OrderSnapshot}（`OrderSnapshot` 本来就带
 * {@code resultVersion} 与 {@code adjustments}）。片1 不渲染，basis 用既有枚举
 * {@link ReportSnapshot.Basis#CURRENT_EFFECTIVE}——新增 `CORRECTED` 属跨模块形状改动，留片2。
 *
 * <p>**不做的事**：不渲染、不写业务表、不释放能力；只读 + 由调用方落库 JSON。
 */
@Component
public class CorrectionExportSource {
    private final JdbcTemplate jdbc;
    private final ExportFacts facts;
    private final PdaExportSource pda;
    private final TemporaryExportSource temporary;
    public CorrectionExportSource(JdbcTemplate jdbc, ExportFacts facts, PdaExportSource pda,
            TemporaryExportSource temporary) {
        this.jdbc=jdbc; this.facts=facts; this.pda=pda; this.temporary=temporary;
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

        // 原始事实行/产品计划沿用**原确认那一版（v1）**的既有读取器：
        // 与 ExportSnapshotReader 同一套选路判定——TEMP 受理正文没有正式 PDA 正文的 source.sourceOrderId，
        // 必须走 TemporaryExportSource，否则会在解析处直接失败。
        var origin = facts.head(scope.warehouse(), scope.order(), 1L);
        ExportFacts.check(origin.submission() != null, "原确认提交缺失");
        boolean tempOrigin = facts.tempOrigin(origin.submission());
        var source = tempOrigin ? temporary.read(origin, false, cutoff) : pda.read(origin, false);
        int revision = Math.toIntExact(source.revision());
        // 该版本的逐包有效值（含被归零的行——不能把缺失当作零）。
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

        var adjustments = adjustments(scope.order(), resultVersion);
        var order = new OrderSnapshot(scope.order().toString(), corrected.number(), revision, resultVersion, "CORRECTED",
            source.header(), source.header(), origin.confirmedAt(),
            new OrderSnapshot.SequenceProof(OrderSnapshot.SequenceKind.PERSISTED_ORDER_SEQUENCE,
                "dispatch_record.order_scan_seq / correction:" + document),
            source.products(), source.dispatches().stream().map(ConfirmedV1Input.Dispatch::line).toList(),
            effectiveLines, adjustments, source.exceptions());
        var day = cutoff.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
        var metadata = new ReportSnapshot.Metadata(UUID.randomUUID().toString(),
            "result:" + resultVersion + "/revision:" + revision, cutoff, cutoff, day, day,
            ReportSnapshot.Basis.CURRENT_EFFECTIVE, ReportSnapshot.Provenance.SOURCE_SNAPSHOT,
            "固定结果版本 " + resultVersion + " 的更正净结果（更正文档 " + document + "）",
            "单个指定订单；单内按持久化order_scan_seq递增");
        return new ReportSnapshot(metadata, List.of(order));
    }

    /** 该版本的逐包有效值（`order_effective_scan`：is_effective + 有效重量）。 */
    private List<Map<String, Object>> effectiveScan(UUID order, long version) {
        return jdbc.queryForList("""
            SELECT BIN_TO_UUID(s.dispatch_id) dispatch_id,s.is_effective,CAST(s.effective_weight_g AS CHAR) effective_weight_g
            FROM order_effective_scan s WHERE s.order_id=? AND s.result_version=? ORDER BY s.original_sequence
            """, Ids.bytes(order), version);
    }

    /**
     * 调整凭据（片1 语义判定①）：**带上**。依据＝R07-3 的"逐笔调整可追溯、净结果可追溯"原则——
     * 净结果若不是由凭据推出，就只剩一个合计数字，事后无法解释"哪一笔、谁批的、依据什么、何时发生"。
     * 来源＝该版本更正文档的 `correction_line`（before/target + 业务发生时刻）＋其 `correction_ledger`
     * 行（含 `reverses_entry_id` 反向引用链）。只输出**该版本实际写入且写了账**的更正行。
     */
    private List<Adjustment> adjustments(UUID order, long resultVersion) {
        var rows = jdbc.queryForList("""
            SELECT BIN_TO_UUID(c.id) line_id,BIN_TO_UUID(c.document_id) document_id,BIN_TO_UUID(c.dispatch_id) dispatch_id,
              c.before_count,c.before_weight_g,c.target_count,c.target_weight_g,c.business_occurred_at,
              d.type,d.reason,d.approved_by,
              (SELECT COUNT(*) FROM correction_ledger l WHERE l.correction_line_id=c.id) ledger_rows
            FROM correction_line c JOIN correction_document d ON d.id=c.document_id
            WHERE d.order_id=? AND d.effective_result_version=? AND d.status IN ('EFFECTIVE','WITHDRAW_PENDING')
            ORDER BY c.line_no
            """, Ids.bytes(order), resultVersion);
        var result = new ArrayList<Adjustment>();
        for (var row : rows) {
            if (((Number) row.get("ledger_rows")).longValue() == 0) continue; // 未写账的行不是净结果凭据
            String type = String.valueOf(row.get("type"));
            long beforeCount = ((Number) row.get("before_count")).longValue();
            long beforeWeight = ((Number) row.get("before_weight_g")).longValue();
            long targetCount = ((Number) row.get("target_count")).longValue();
            long targetWeight = ((Number) row.get("target_weight_g")).longValue();
            String lineId = String.valueOf(row.get("line_id"));
            // ── 边界层转换（业主 2026-09-28 决策④，选 C）────────────────────────────────────────
            // 引擎与渲染对"整行归零"用了不同表达：引擎写 `WEIGHT_CORRECTION + target_count=0`，
            // 而渲染侧 `AdjustmentValidator` 要求 WEIGHT_CORRECTION 必须 count=1。
            // 这里在**组装层**按实际效果归类（不改引擎、不改渲染），并且**只对已证明合法的两种形状做转换**：
            //   · 归零（target 0 包 0 克）        ⇒ 解释为"整行退出净结果"
            //   · WEIGHT_CORRECTION 保持 1 包    ⇒ 解释为"只改重量"
            // 其余组合一律拒绝，**绝不静默误解释**（业主明确要求：合法组合不能被当成取消）。
            ExportFacts.check(beforeCount == 1 && beforeWeight > 0 && targetWeight >= 0,
                "更正凭据的 before/after 形状非法");
            var kind = switch (type) {
                case "WEIGHT_CORRECTION" -> {
                    if (targetCount == 0 && targetWeight == 0) yield Adjustment.Kind.DISPATCH_CANCELLATION;
                    if (targetCount == 1 && targetWeight > 0) yield Adjustment.Kind.WEIGHT_CORRECTION;
                    throw new BusinessException(409, "CORRECTION_EXPORT_KIND_AMBIGUOUS",
                        "重量更正的包数/重量组合无法归类（既不归零也未保持 1 包）：不得静默解释为取消");
                }
                case "PHYSICAL_RETURN" -> {
                    requireZeroed(targetCount, targetWeight, "退货");
                    yield Adjustment.Kind.PHYSICAL_RETURN;
                }
                case "DISPATCH_CANCELLATION" -> {
                    requireZeroed(targetCount, targetWeight, "出库取消");
                    yield Adjustment.Kind.DISPATCH_CANCELLATION;
                }
                case "HEADER_CORRECTION" -> throw new BusinessException(409, "CORRECTION_EXPORT_KIND_AMBIGUOUS",
                    "抬头更正无出库行，需片2 专门处理（本轮未支持）");
                default -> throw new BusinessException(409, "EXPORT_SOURCE_UNAVAILABLE", "更正类型无法映射到调整凭据");
            };
            String dispatchId = row.get("dispatch_id") == null ? "" : String.valueOf(row.get("dispatch_id"));
            ExportFacts.check(!dispatchId.isEmpty(), "更正凭据缺少出库行引用");
            String documentId = String.valueOf(row.get("document_id"));
            result.add(new Adjustment(documentId, lineId, resultVersion, kind, dispatchId,
                (int) ((Number) row.get("before_count")).longValue(), (int) ((Number) row.get("target_count")).longValue(),
                Long.toString(((Number) row.get("before_weight_g")).longValue()),
                Long.toString(((Number) row.get("target_weight_g")).longValue()),
                "", "", "", aspect(row.get("business_occurred_at")), String.valueOf(row.get("reason")),
                row.get("approved_by") == null ? "" : String.valueOf(row.get("approved_by")),
                "correction_document:" + documentId + " / correction_line:" + lineId
                    + " / ledger_rows:" + row.get("ledger_rows")));
        }
        ExportFacts.check(!result.isEmpty(), "该结果版本没有可追溯的更正凭据行");
        return result;
    }

    /** 归零类的形状要求：0 包 0 克。非此形状一律拒绝（不得静默解释为取消）。 */
    private static void requireZeroed(long targetCount, long targetWeight, String label) {
        ExportFacts.check(targetCount == 0 && targetWeight == 0, label + "的更正形状非法（应归零）");
    }

    /** 业务发生时刻：驱动可能给 {@link java.sql.Timestamp} 或 {@link LocalDateTime}，两种都接。 */
    private static Instant aspect(Object value) {
        if (value instanceof java.sql.Timestamp stamp) return stamp.toInstant();
        if (value instanceof LocalDateTime local) return local.toInstant(ZoneOffset.UTC);
        if (value instanceof Instant instant) return instant;
        throw new BusinessException(409, "EXPORT_SOURCE_UNAVAILABLE", "更正凭据的业务发生时刻不可解析");
    }
}
