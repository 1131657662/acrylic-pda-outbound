package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.exports.*;
import com.acrylic.outbound.jobs.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * R08 子项 C 的**渲染验证**：TEMP（R06 受控关联 + 确认后的正式单）走完"申请 → worker 生成 → 受权下载"，
 * 把三种格式真的渲染出来并回读文件头/字节。
 *
 * <p>为什在这里做：C 只验到"入口受理 + 首版快照落地"，而**渲染是导出功能的另一半**。本类复用
 * {@link TemporaryCommandWebHttpChecks#verifyAndReturn} 把 TEMP 链跑完，再用与既有 R08 IT 相同的
 * {@code BackgroundJobWorker} + 受权下载路径把 XLSX / SCAN_XLSX / PDF 三种格式各生成一次。
 *
 * <p>守卫与 worker：由 {@code tools/r08-provision-and-run.ps1} 提供 R08 专用库坐标与
 * {@code -NonE2a} 环境（应用自迁移）；类内再用 assumption 复核库坐标，非 R08 库时整类跳过而非报红。
 * 渲染子进程需要 {@code OUTBOUND_EXPORTS_RENDERER_HOME} / {@code OUTBOUND_EXPORTS_BROWSERS}。
 */
@SpringBootTest @AutoConfigureMockMvc
@org.junit.jupiter.api.Disabled("""
    待接线（本轮实测定位，未通过）：本类用 @SpringBootTest + MockMvc，而 TEMP 链的既有夹具
    TemporaryCommandWebHttpChecks 走**真 HTTP 到 127.0.0.1:19434**（TemporaryAccountingChecks.http），
    MockMvc 不监听该端口 ⇒ ConnectException（R08-24）。三条路本轮都试过：
      a) R08 库（23308）+ MockMvc：链的第一步即 ConnectException；
      b) e2a harness（起真服 19434）不带 -NonE2a：@SpringBootTest 的数据源缺 OUTBOUND_DB_URL
         ⇒ "'url' must start with jdbc"（attempt 384）；
      c) e2a harness + -NonE2a：该开关同时把上下文数据源指到同一 attempt 库，而链夹具随后要
         profile.migrate(…, "34"/"42") —— 库已被应用迁到最新，Flyway target 低于当前版本会报错。
    可行接线（下一轮照做）：给 e2a runner 增加"仅导出库坐标、不预迁移"模式——`-NonE2a` 只设
    OUTBOUND_DB_URL/USER/PASSWORD 而 OUTBOUND_MIGRATE=false，让应用 context 与 E2aCrossSourceProfile
    共用同一 attempt 库（该库由 profile 自己 migrate）⇒ 真服 19434 + TEMP 链 + 渲染三者可同时成立。
    本轮按"1 轮为限、渲染验证不通则记录并转 D"的约定停在这里，不硬做。
    """)
class R08TemporaryExportRenderIT {

    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired PasswordEncoder passwords;
    @Autowired BackgroundJobWorker worker; @Autowired BackgroundJobStore jobs; @Autowired ExportJobQueries queries;
    @Autowired org.springframework.web.context.WebApplicationContext context;

    @Test void temporaryLinkedOrderRendersXlsxScanXlsxAndPdf() throws Exception {
        // 专用库守卫：只认 R08 专用库坐标（与既有 R08 IT 同口径）；其余环境整类跳过而非报红。
        org.junit.jupiter.api.Assumptions.assumeTrue(
            Objects.toString(System.getenv("OUTBOUND_DB_URL"), "").startsWith("jdbc:mysql://127.0.0.1:23308/acrylic_r08_it"),
            "R08 渲染验证需要 R08 专用库");
        var chain = TemporaryCommandWebHttpChecks.verifyAndReturn(context);
        var api = new PublicationDriver(mvc, json, new IdentityFixture(chain.supervisor(), chain.warehouse()));

        for (String format : List.of("XLSX", "SCAN_XLSX", "PDF")) {
            var created = api.data(post("/api/v1/orders/" + chain.targetOrder() + "/exports")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType("application/json")
                .content("{\"format\":\"" + format + "\",\"basis\":\"ORIGINAL_CONFIRMED\"}"));
            UUID jobId = UUID.fromString(created.get("id").asText());
            var job = generate(jobId);
            byte[] bytes = api.send(get("/api/v1/exports/" + job.id() + "/file"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
            assertThat(bytes.length).as(format + " 文件非空").isPositive();
            switch (format) {
                case "XLSX", "SCAN_XLSX" -> assertThat(bytes).as(format + " 是 zip 容器").startsWith((byte) 'P', (byte) 'K');
                case "PDF" -> assertThat(new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
                default -> throw new IllegalStateException();
            }
            Path out = Path.of(System.getenv("OUTBOUND_STORAGE"), "temp-linked-" + format.toLowerCase(Locale.ROOT)
                + (format.equals("XLSX") ? ".xlsx" : format.equals("SCAN_XLSX") ? ".scan.xlsx" : ".pdf"));
            Files.write(out, bytes);
            System.out.println("TEMP-RENDER " + format + " bytes=" + bytes.length + " file=" + out);
            // 该作业的固定范围就是本次 TEMP 目标单（先固定成员、后渲染）。
            assertThat(queries.payload(jobs.find(jobId).orElseThrow()).scope().order().toString())
                .as(format + " 作业归属").isEqualTo(chain.targetOrder());
        }
    }

    private BackgroundJob generate(UUID jobId) {
        for (int attempt = 0; attempt < 40; attempt++) {
            var current = jobs.find(jobId).orElseThrow();
            if (current.status() == BackgroundJob.Status.SUCCEEDED) return current;
            if (current.status() == BackgroundJob.Status.FAILED)
                throw new AssertionError("渲染失败: " + current.lastError());
            var outcome = worker.runOnce("r08-temp-render");
            assertThat(outcome).isPresent();
        }
        throw new AssertionError("导出未在尝试上限内完成");
    }
}
