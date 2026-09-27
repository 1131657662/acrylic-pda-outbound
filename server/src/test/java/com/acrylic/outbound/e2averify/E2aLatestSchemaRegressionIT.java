package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.correction.CorrectionCommandController;
import com.acrylic.outbound.inventory.TemporaryLinkConfirmHttpChecks;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A 组回归：把**已验证的 R06 链**放到**最新 schema**（V46/V47/V48 的共享表调整之后）上重跑一次。
 *
 * <p>为什么需要它：其余 e2a IT 各自把数据库钉在 V40/V42/V45 上，因此它们**根本不会应用** V46/V47
 * （手工迁移到目标版本、应用内 Flyway 关闭）——它们通过并不能证明共享表没被改坏，失败也不能归因于本批。
 * 本类把目标抬到 V48，让 R06 的确认链在"加了列、放宽了 CHECK、多了 order_effective_scan"的真实表上再走一遍，
 * 并逐条核对共享表的关键不变量。
 */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aLatestSchemaRegressionIT {
    @Test void verifiedR06ChainStillRunsOnTheLatestSchema() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        profile.migrate(jdbc, "42"); E2aCrossSourceHttpSetup.signing(profile.attempt);
        profile.migrate(jdbc, "48");
        var notes = new StringBuilder("A 组回归：R06 链 + R08 导出读取器在最新 schema 上重跑\n");
        // The export reader (ExportFacts / ExportRangeReader) is the other consumer of order_result_version
        // (header_snapshot); the export fixtures need the renderer and its browsers, exactly as E2aTemporaryExportIT
        // passes them. cookie.secure=false is repeated here so the IT is self-contained outside this harness.
        java.nio.file.Path renderer=java.nio.file.Path.of("../report-rendering/build/install/acrylic-report-rendering")
            .toAbsolutePath().normalize();
        try (var context = E2aCrossSourceHttpIT.start(profile, "--outbound.temporary.commands.enabled=true",
                "--outbound.exports.sample-data=true", "--server.servlet.session.cookie.secure=false",
                "--outbound.exports.renderer-home="+renderer,
                "--outbound.exports.browsers=D:/acrylic-pda-worktrees/r08-export/report-rendering/build/browsers")) {
            // The correction capability must still be absent with its own default switch off.
            assertThat(context.getBeanNamesForType(CorrectionCommandController.class))
                .as("更正路由默认不装配").isEmpty();
            TemporaryLinkConfirmHttpChecks.verify((WebApplicationContext) context);
            notes.append("①已验证的 R06 关联+确认链在 V48 上重跑完成\n");
            // (1) The channel CHECK still admits a PDA submission, and the correction channel stays unused.
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_result_version v"
                + " WHERE v.result_version=1 AND v.submission_id IS NOT NULL AND v.pc_submission_id IS NULL"
                + " AND v.source_correction_id IS NULL", Integer.class))
                .as("v1 keeps its submission channel").isPositive();
            // V46's backfill can only cover rows that existed when it ran. A v1 row written afterwards by the R05/R06
            // writers leaves original_submission_id NULL - those writers are verified surfaces this batch must not
            // touch - so this service fills it with COALESCE(...channel) for the versions it writes itself. Recorded
            // as an open item rather than silently assumed.
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_result_version WHERE result_version=1"
                + " AND original_submission_id IS NULL", Integer.class))
                .as("a post-V46 writer leaves original_submission_id NULL (recorded gap)").isPositive();
            // (2) The order's own pointer names a real version. V43's extra projection table is written only by the
            // correction path: an order confirmed by R05/R06 has no row there, and filling it would mean changing those
            // verified writers - recorded as an open item instead of assumed.
            assertThat(jdbc.queryForObject("SELECT CAST(current_result_version AS CHAR) FROM outbound_order"
                + " WHERE current_result_version IS NOT NULL LIMIT 1", String.class))
                .as("order pointer names a real version").isEqualTo("1");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_current_result", Integer.class))
                .as("order_current_result stays empty for R05/R06-confirmed orders (recorded gap)").isZero();
            // (3) The new scan table is empty until a correction exists, and so is the correction receipt table.
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_effective_scan", Integer.class))
                .as("no correction ran, so no scan snapshot was written").isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM correction_command_receipt", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM correction_document", Integer.class)).isZero();
            // (4) The relaxed columns are nullable for the reason V46/V47 documented, and nothing else moved.
            assertThat(jdbc.queryForObject("SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()"
                + " AND TABLE_NAME='order_result_version' AND COLUMN_NAME='submission_id'", String.class))
                .isEqualTo("YES");
            assertThat(jdbc.queryForObject("SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()"
                + " AND TABLE_NAME='correction_command_receipt' AND COLUMN_NAME='order_id'", String.class))
                .isEqualTo("YES");
            // (5) The widened channel CHECK still demands SOME channel: only a correction-driven version may have
            // neither a PDA nor a PC submission, and that branch requires source_correction_id to be set.
            assertThatThrownBy(() -> jdbc.update("INSERT INTO order_result_version(order_id,result_version,submission_id,"
                + "pc_submission_id,actual_count,weight_g,inventory_deducted,header_snapshot,created_at)"
                + " SELECT order_id,99,NULL,NULL,0,0,1,header_snapshot,UTC_TIMESTAMP(3)"
                + " FROM order_result_version LIMIT 1"))
                .as("a channel-less version that is not correction-driven is still refused")
                .isInstanceOf(org.springframework.dao.DataAccessException.class)
                .hasMessageContaining("ck_result_channel");
            notes.append("②共享表不变量逐条核对完成：v1 渠道/回填、双指针一致、scan 与回执表空、放宽列可空、渠道 CHECK 仍生效\n");
        }
        // (6) R08 导出读取器（读 order_result_version.header_snapshot）在新 schema 上跑一遍——这是
        //     "读 order_result_version 的类"里唯一能在 V48 上验证的通道（其余 e2a IT 都把库钉在 V44 及以前）。
        //     它要求命令面为默认关闭态（fixture 断言无 CommandOperation bean），故用第二个上下文，DB 仍是最新 schema。
        try (var exportContext = E2aCrossSourceHttpIT.start(profile, "--outbound.exports.sample-data=true",
                "--server.servlet.session.cookie.secure=false",
                "--outbound.exports.renderer-home="+renderer,
                "--outbound.exports.browsers=D:/acrylic-pda-worktrees/r08-export/report-rendering/build/browsers")) {
            assertThat(exportContext.getBeanNamesForType(CorrectionCommandController.class))
                .as("导出上下文仍是默认关闭态").isEmpty();
            new com.acrylic.outbound.inventory.TemporaryExportChecks((WebApplicationContext) exportContext)
                .verify(profile.attempt.resolve("exports-verified"));
            assertThat(java.nio.file.Files.list(profile.attempt.resolve("exports-verified")).count())
                .as("导出链在新 schema 上产出了产物").isPositive();
            notes.append("③R08 导出读取器在 V48 上跑通并产出产物\n");
        }
        Files.writeString(profile.attempt.resolve("latest-schema-regression.txt"), notes.toString());
        Files.writeString(profile.attempt.resolve("test-completed.marker"), notes.toString());
    }
}
