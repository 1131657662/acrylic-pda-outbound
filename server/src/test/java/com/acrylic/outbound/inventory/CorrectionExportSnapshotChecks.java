package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.exports.*;
import com.acrylic.outbound.reports.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * D 片1 定向检查：**更正净结果（v&gt;1）快照能被正确组装**（服务端只读，不渲染）。
 *
 * <p>价值是"证明能组装出与库内 v&gt;1 事实逐条一致的快照"，不是"能导出文件"——渲染（片2）
 * 与请求面（片3）分开做。
 *
 * <p>断言口径（全部对照数据库事实，不看生成侧的自述）：
 * ① 版本号＝库内 `order_result_version` 的实际版本；② `CURRENT_EFFECTIVE` 口径 +
 * `result:&lt;v&gt;/revision:&lt;r&gt;`；③ 逐包有效行与 `order_effective_scan` **逐条一致**；
 * ④ 合计与 `order_result_version.actual_count/weight_g` 一致；
 * ⑤ 调整凭据行与 `correction_line`（有账行者）逐条一致，且带文档/行/账行数的溯源引用；
 * ⑥ 默认 v1 路径在**同一订单**上仍产出 v1 快照（默认行为不变）。
 */
public final class CorrectionExportSnapshotChecks {
    private CorrectionExportSnapshotChecks() { }

    public static void verify(WebApplicationContext context, String orderId, long correctedVersion) throws Exception {
        var jdbc = context.getBean(JdbcTemplate.class);
        var reader = context.getBean(ExportSnapshotReader.class);
        var files = context.getBean(ExportFiles.class);
        UUID order = UUID.fromString(orderId);

        // ② 默认 v1 路径不变（同一订单 + 同一 reader）。
        var v1 = reader.read(new ExportModels.Scope(UUID.randomUUID(), warehouse(jdbc, order), order),
            ExportModels.Format.XLSX, java.time.Instant.now());
        assertThat(v1.metadata().basis()).isEqualTo(ReportSnapshot.Basis.ORIGINAL_CONFIRMED);
        assertThat(v1.orders().getFirst().resultVersion()).as("默认取 v1").isEqualTo(1);

        // ①③④⑤ v>1：走更正来源。
        var corrected = reader.read(new ExportModels.Scope(UUID.randomUUID(), warehouse(jdbc, order), order),
            ExportModels.Format.XLSX, java.time.Instant.now(), correctedVersion);
        assertThat(corrected.metadata().basis()).isEqualTo(ReportSnapshot.Basis.CURRENT_EFFECTIVE);
        assertThat(corrected.metadata().dataVersion()).isEqualTo("result:" + correctedVersion + "/revision:"
            + jdbc.queryForObject("SELECT current_revision FROM outbound_order WHERE id=?", Integer.class, Ids.bytes(order)));
        var snap = corrected.orders().getFirst();
        assertThat(snap.resultVersion()).as("快照版本号＝库内实际版本").isEqualTo(correctedVersion);

        // ③ 逐包有效行 vs order_effective_scan 逐条一致。
        var expected = new LinkedHashMap<String, String[]>();
        jdbc.query("SELECT BIN_TO_UUID(dispatch_id),is_effective,CAST(effective_weight_g AS CHAR) FROM order_effective_scan "
            + "WHERE order_id=? AND result_version=? ORDER BY original_sequence", (rs, n) -> {
                int count = rs.getBoolean(2) ? 1 : 0;
                expected.put(rs.getString(1), new String[]{Integer.toString(count), count == 0 ? "0" : rs.getString(3)});
                return null;
            }, Ids.bytes(order), correctedVersion);
        assertThat(snap.effectiveLines()).hasSize(expected.size());
        long countSum = 0, weightSum = 0;
        for (var line : snap.effectiveLines()) {
            var want = expected.get(line.dispatchId());
            assertThat(want).as("有效行必须对应库内一包: " + line.dispatchId()).isNotNull();
            assertThat(line.count()).as("包数 vs is_effective: " + line.dispatchId()).isEqualTo(Integer.parseInt(want[0]));
            assertThat(line.weightGrams()).as("有效重量 vs order_effective_scan: " + line.dispatchId()).isEqualTo(want[1]);
            countSum += line.count();
            weightSum += Long.parseLong(line.weightGrams());
        }

        // ④ 合计 vs order_result_version。
        var totals = jdbc.queryForMap("SELECT CAST(actual_count AS CHAR) c,CAST(weight_g AS CHAR) w "
            + "FROM order_result_version WHERE order_id=? AND result_version=?", Ids.bytes(order), correctedVersion);
        assertThat(Long.toString(countSum)).isEqualTo(String.valueOf(totals.get("c")));
        assertThat(Long.toString(weightSum)).isEqualTo(String.valueOf(totals.get("w")));

        // ⑤ 调整凭据行 vs correction_line（只算写了账的行），并要求溯源引用非空。
        Integer ledgerLines = jdbc.queryForObject("""
            SELECT COUNT(*) FROM correction_line c JOIN correction_document d ON d.id=c.document_id
            WHERE d.order_id=? AND d.effective_result_version=?
              AND EXISTS(SELECT 1 FROM correction_ledger l WHERE l.correction_line_id=c.id)
            """, Integer.class, Ids.bytes(order), correctedVersion);
        assertThat(snap.adjustments()).as("调整凭据行数 vs correction_line(有账行)").hasSize(ledgerLines);
        for (var a : snap.adjustments()) {
            assertThat(a.correctionId()).isNotEmpty();
            assertThat(a.lineId()).isNotEmpty();
            assertThat(a.resultVersion()).isEqualTo(correctedVersion);
            assertThat(a.evidenceReference()).contains("correction_document").contains("correction_line");
            assertThat(a.reason()).isNotEmpty();
        }

        // 落库 JSON（片1 的产出就是"能组装并固定成快照"；渲染留片2）。
        String hash = files.store(corrected);
        JsonNode stored = SubmissionJson.parse(Files.readString(files.snapshot(hash)));
        assertThat(stored.at("/metadata/basis").asText()).isEqualTo("CURRENT_EFFECTIVE");
        assertThat(stored.at("/orders/0/resultVersion").asLong()).isEqualTo(correctedVersion);
        assertThat(stored.at("/orders/0/adjustments").size()).isEqualTo(ledgerLines);
        System.out.println("D-SLICE1 snapshot hash=" + hash + " version=" + correctedVersion
            + " lines=" + snap.effectiveLines().size() + " adjustments=" + snap.adjustments().size()
            + " count=" + countSum + " weightG=" + weightSum);
    }

    private static UUID warehouse(JdbcTemplate jdbc, UUID order) {
        return UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(warehouse_id) FROM outbound_order WHERE id=?",
            String.class, Ids.bytes(order)));
    }
}
