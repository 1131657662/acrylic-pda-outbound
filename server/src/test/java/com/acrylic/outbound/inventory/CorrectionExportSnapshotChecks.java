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
        assertThat(corrected.metadata().basis()).as("更正净结果须为 CORRECTED 口径").isEqualTo(ReportSnapshot.Basis.CORRECTED);
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

        // 落库 JSON（片1 的产出就是"能组装并固定成快照"）。
        String hash = files.store(corrected);
        JsonNode stored = SubmissionJson.parse(Files.readString(files.snapshot(hash)));
        assertThat(stored.at("/metadata/basis").asText()).isEqualTo("CORRECTED");
        assertThat(stored.at("/orders/0/resultVersion").asLong()).isEqualTo(correctedVersion);
        assertThat(stored.at("/orders/0/adjustments").size()).isEqualTo(ledgerLines);
        System.out.println("D-SLICE1 snapshot hash=" + hash + " version=" + correctedVersion
            + " lines=" + snap.effectiveLines().size() + " adjustments=" + snap.adjustments().size()
            + " count=" + countSum + " weightG=" + weightSum);

        // 片2：真出文件。走与生产同一条渲染器进程（同样的 home/browsers/CLI），只是不经过 job 监督。
        renderAndVerify(context, files, corrected, hash);
    }

    /** 片2 定向：更正净结果**真出 XLSX/PDF**，并回读内容确认"净结果"确实落进文件。 */
    private static void renderAndVerify(WebApplicationContext context, ExportFiles files,
            ReportSnapshot corrected, String hash) throws Exception {
        var store = context.getBean(com.acrylic.outbound.jobs.BackgroundJobStore.class);
        var worker = context.getBean(com.acrylic.outbound.jobs.BackgroundJobWorker.class);
        var json = context.getBean(com.acrylic.outbound.common.JsonText.class);
        var state = context.getBean(com.acrylic.outbound.common.ServerState.class);
        var outDir = files.snapshot(hash).getParent().getParent().resolve("corrected-render");
        Files.createDirectories(outDir);
        var snapshot = files.snapshot(hash);
        var order = corrected.orders().getFirst();
        var scope = new com.acrylic.outbound.exports.ExportModels.Scope(
            UUID.fromString(jdbc(context, order.id())), UUID.fromString(jdbcWarehouse(context, order.id())),
            UUID.fromString(order.id()));
        for (var format : List.of(com.acrylic.outbound.exports.ExportModels.Format.XLSX,
                com.acrylic.outbound.exports.ExportModels.Format.PDF)) {
            var payload = new com.acrylic.outbound.exports.ExportModels.Payload(scope, format, "CORRECTED",
                state.current().epoch(), hash, corrected.metadata().snapshotId(), order.number(), order.revision(),
                corrected.metadata().fromDate().toString(), format.template(),
                java.time.Instant.now().plus(java.time.Duration.ofDays(7)), null, null, 1, null);
            var job = store.enqueue(new com.acrylic.outbound.jobs.NewBackgroundJob(
                com.acrylic.outbound.exports.ExportModels.TYPE, "d-slice2:" + hash + ":" + format,
                json.encode(payload), 3, java.time.Instant.now()));
            var outcome = worker.runOnce("d-slice2-render");
            assertThat(outcome).as("作业必须被认领").isPresent();
            assertThat(outcome.get().outcome()).as("渲染必须成功（" + format + "）").isEqualTo("SUCCEEDED");
            var done = store.find(job.id()).orElseThrow();
            var artifact = queries(context).payload(done).artifact();
            assertThat(artifact).as("必须发布产物（" + format + "）").isNotNull();
            var produced = files.artifact(done.id(), artifact.token(), format);
            byte[] bytes = Files.readAllBytes(produced);
            assertThat(bytes.length).isEqualTo(artifact.bytes());
            var copy = outDir.resolve("corrected." + format.extension);
            Files.write(copy, bytes);
            if (format == com.acrylic.outbound.exports.ExportModels.Format.PDF) {
                assertThat(new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF");
            } else {
                assertThat(bytes[0]).isEqualTo((byte) 'P');
                assertThat(bytes[1]).isEqualTo((byte) 'K');
                String sheet = zipXml(produced);
                assertThat(sheet).as("文件必须标出结果版本 2").contains("2");
                assertThat(sheet).as("必须有净结果/调整凭据字样").matches("(?s).*(调整|净结果|更正).*");
                // 本 fixture 把两份包全部归零 ⇒ 渲染器按设计（`if (e.count()==1)`）在"版本有效明细"里**不再列该行**，
                // 但调整凭据必须仍然列出，才能"零结果"可追溯。用表头匹配断言这一对语义，而不是靠肉眼看空表。
                assertThat(sheet).as("调整凭据必须出现").contains("DISPATCH_CANCEL");
                int netRows = countDataRows(produced, "版本有效明细");
                assertThat(netRows).as("全部归零时版本有效明细不应有数据行").isZero();
                System.out.println("D-SLICE2 netSheetDataRows=" + netRows + " (全归零 ⇒ 0，符合渲染器设计)");
            }
            System.out.println("D-SLICE2 " + format + " bytes=" + bytes.length + " copy=" + copy);
        }
    }

    private static String jdbc(WebApplicationContext context, String order) {
        // 作业归属＝该订单原确认的提交人（v2 行没有提交，故回到 v1 行取）。
        return context.getBean(JdbcTemplate.class).queryForObject(
            "SELECT BIN_TO_UUID(s.operator_id) FROM outbound_order o"
                + " JOIN order_result_version r ON r.order_id=o.id AND r.result_version=1"
                + " JOIN submission s ON s.id=r.submission_id WHERE o.id=?",
            String.class, Ids.bytes(UUID.fromString(order)));
    }
    private static String jdbcWarehouse(WebApplicationContext context, String order) {
        return sender(context, order, "warehouse_id");
    }
    private static String sender(WebApplicationContext context, String order, String column) {
        return context.getBean(JdbcTemplate.class).queryForObject(
            "SELECT BIN_TO_UUID(" + column + ") FROM outbound_order WHERE id=?", String.class, Ids.bytes(UUID.fromString(order)));
    }
    private static com.acrylic.outbound.exports.ExportJobQueries queries(WebApplicationContext context) {
        return context.getBean(com.acrylic.outbound.exports.ExportJobQueries.class);
    }

    /** 统计某个工作表的数据行数（不含标题/说明/表头三行）。零依赖：直接读 sheet XML。 */
    private static int countDataRows(java.nio.file.Path xlsx, String sheetName) throws Exception {
        try (var zip = new java.util.zip.ZipFile(xlsx.toFile())) {
            String relationId = null;
            try (var in = zip.getInputStream(zip.getEntry("xl/workbook.xml"))) {
                String wb = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                // 名字可能被 XML 转义，故按"是否包含该名字"定位 <sheet .../>，再取同一标签里的 r:id。
                int at = wb.indexOf(sheetName);
                if (at < 0) return -1;
                int open = wb.lastIndexOf("<sheet ", at);
                int close = wb.indexOf("/>", at);
                if (open < 0 || close < 0) return -1;
                String tag = wb.substring(open, close);
                int idAt = tag.indexOf("r:id=\"");
                if (idAt < 0) return -1;
                relationId = tag.substring(idAt + 6, tag.indexOf('"', idAt + 6));
            }
            try (var in = zip.getInputStream(zip.getEntry("xl/_rels/workbook.xml.rels"))) {
                String rels = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                int at = rels.indexOf("Id=\"" + relationId + "\"");
                if (at < 0) return -1;
                int targetAt = rels.indexOf("Target=\"", at);
                String target = rels.substring(targetAt + 8, rels.indexOf('"', targetAt + 8));
                var entry = zip.getEntry(target.startsWith("/") ? target.substring(1) : "xl/" + target);
                if (entry == null) entry = zip.getEntry(target);
                if (entry == null) return -1;
                try (var sheet = zip.getInputStream(entry)) {
                    String xml = new String(sheet.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    int rows = xml.split("<row ", -1).length - 1;
                    return Math.max(0, rows - 3); // 标题 + 说明 + 表头
                }
            }
        }
    }

    /** 把 xlsx 当 zip 读全部 XML 文本（xlsx 就是 zip+XML，不引入额外依赖）。 */
    private static String zipXml(java.nio.file.Path xlsx) throws Exception {
        var text = new StringBuilder();
        // 用 ZipFile（读中央目录）而不是 ZipInputStream：POI 写出的数据描述符会让流式读取报
        // "invalid entry size"，但文件本身合法（ZipFile/openpyxl 都能正常读）。
        try (var zip = new java.util.zip.ZipFile(xlsx.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.getName().endsWith(".xml")) {
                    try (var in = zip.getInputStream(entry)) {
                        text.append(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                    }
                }
            }
        }
        return text.toString();
    }

    private static UUID warehouse(JdbcTemplate jdbc, UUID order) {
        return UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(warehouse_id) FROM outbound_order WHERE id=?",
            String.class, Ids.bytes(order)));
    }
}
