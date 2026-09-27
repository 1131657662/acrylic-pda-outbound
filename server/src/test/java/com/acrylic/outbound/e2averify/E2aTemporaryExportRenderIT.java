package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.inventory.TemporaryCommandWebHttpChecks;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * R08 子项 C 的**渲染验证**：TEMP（R06 受控关联 + 确认后的正式单）真的把文件渲染出来并回读容器签名。
 *
 * <p>为什么用 e2a 的"自起真服"模式而不是 {@code @SpringBootTest}：TEMP 链的既有夹具走**真 HTTP 到
 * 127.0.0.1:19434**（{@code TemporaryAccountingHttp}），MockMvc 不监听该端口；而下载/作业又需要同一容器里的
 * worker bean。{@link E2aCrossSourceHttpIT#start} 恰好一次满足三者：同一 attempt 库（应用不自己迁移，
 * schema 由本 IT 的 {@code profile.migrate} 建）＋真服 19434 ＋ 可注入的 bean。
 *
 * <p>渲染子进程需要 {@code --outbound.exports.renderer-home} 与 {@code --outbound.exports.browsers}
 * （本 IT 显式传入；缺了导出 POST 会 503 EXPORT_UNAVAILABLE，属文档化前置）。
 */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aTemporaryExportRenderIT {
    @Test void temporaryLinkedOrderRendersXlsxScanXlsxAndPdf() throws Exception {
        var profile = new E2aCrossSourceProfile();
        var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("render-started.marker"));
        profile.migrate(jdbc, "34");
        E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        // 与 E2aTemporaryCommandWebHttpIT 完全一致：命令链需要 V35-V41 的临时来源/映射/槽位表，
        // 只迁到 34 会让 LINK 命令在建表缺位处 500（attempt 388 的实测教训）。
        profile.migrate(jdbc, "41");
        E2aCrossSourceHttpSetup.signing(profile.attempt);

        Path renderer = Path.of("../report-rendering/build/install/acrylic-report-rendering").toAbsolutePath().normalize();
        Path browsers = Path.of("D:/acrylic-pda-worktrees/r08-export/report-rendering/build/browsers");
        assertThat(Files.isDirectory(renderer)).as("渲染器安装目录存在").isTrue();
        assertThat(Files.isDirectory(browsers)).as("Chromium 目录存在").isTrue();

        var notes = new StringBuilder("R08 子项 C 渲染验证（TEMP 来源正式单）\n");
        // 顺序必须与同类 IT 一致：**先**把 schema/种子/签名准备好，**再**起真服——应用启动时会读 server_state
        // 等表，空库直接起会 500（attempt 387 的实测教训）。
        var context = E2aCrossSourceHttpIT.start(profile,
            "--outbound.temporary.commands.enabled=true",
            "--server.servlet.session.cookie.secure=false",
            "--outbound.exports.sample-data=true",
            "--outbound.exports.renderer-home=" + renderer,
            "--outbound.exports.browsers=" + browsers);
        try {
            var checks = TemporaryCommandWebHttpChecks.verifyAndReturn(
                (org.springframework.web.context.WebApplicationContext) context);
            for (String format : List.of("XLSX", "SCAN_XLSX", "PDF")) {
                var rendered = checks.renderExport(format);
                assertThat(rendered.bytes().length).as(format + " 文件非空").isPositive();
                switch (format) {
                    case "XLSX" -> {
                        assertThat(rendered.bytes()).as("XLSX 是 xlsx(zip) 容器").startsWith((byte) 'P', (byte) 'K');
                        assertThat(rendered.contentType()).contains("spreadsheetml");
                    }
                    case "SCAN_XLSX" -> {
                        assertThat(rendered.bytes()).as("SCAN_XLSX 是 xlsx(zip) 容器").startsWith((byte) 'P', (byte) 'K');
                        assertThat(rendered.contentType()).contains("spreadsheetml");
                    }
                    case "PDF" -> assertThat(new String(rendered.bytes(), 0, 5, java.nio.charset.StandardCharsets.US_ASCII))
                        .isEqualTo("%PDF-");
                    default -> throw new IllegalStateException();
                }
                Path out = profile.attempt.resolve("storage").resolve(
                    "temp-linked-" + format.toLowerCase(Locale.ROOT) + (format.equals("PDF") ? ".pdf" : ".xlsx"));
                Files.write(out, rendered.bytes());
                notes.append(format).append(" -> ").append(rendered.bytes().length).append(" bytes, ")
                    .append(rendered.contentType()).append(", ").append(out.getFileName()).append('\n');
                System.out.println("TEMP-RENDER " + format + " bytes=" + rendered.bytes().length
                    + " type=" + rendered.contentType() + " file=" + out);
            }
        } finally {
            context.close();
        }
        Files.writeString(profile.attempt.resolve("render-completed.marker"), notes.toString());
    }
}
