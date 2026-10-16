# D13 恢复受影响组合 L2 验收（2026-10-17）

## 范围与输入绑定

本报告收口 D13 当前受影响组合：真实 HTTP/MySQL、非空 Flyway 迁移、原提交占用、未提交占用、恢复写入事务、SQL/事务失败回滚、错库/事务内/水位/所有者拒绝、renderer/XLSX/PDF/导出重放及对应 Chrome 成功路径。它不宣称 D13 全部、Android、完整外部 Python 消费、原模拟器、现场身份或 D18 L3 通过。

固定树为 `D:/acrylic-pda-outbound`、分支为 `codex/development`。接手后全量输入快照为 `.local/goal-20261017-resume/resume-20261017-0752/input.json`，SHA-256 `bd5e5c02413b35dcae3052b693471ef0c4636da598733748f212352ffdc0a425`；对应 HEAD、远端、102 条工作区状态、33 个 worktree、进程/监听和 C/D 余量见同目录快照。当前唯一写者由接管复核确认，清理聊天 idle 且无未交接写者。

| 改动路径 | 影响层与必跑 | 本批输入 SHA-256 |
| --- | --- | --- |
| `tools/e2a-cross-source-verify.ps1` | 真实隔离 runner 的原提交/未提交占用及恢复事务入口 | `90d9ed04ca36b87052c159e3b71824f445dc74e0b756cf24a7750a96dc53c864` |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aRecoveryWriterTransactionIT.java` | 恢复事务、完整 Flyway、renderer/export 和浏览器固定树绑定 | `af6aa304b7d2e961336a8f0bd7767295e9244feebd7c1f4d8a7178871ca01ea6` |
| `server/src/test/java/com/acrylic/outbound/inventory/TemporaryAccountingChecks.java` | 动态隔离 HTTP 端口、TEMP 账务正向/拒绝检查 | `9037645f8336729a259fef3aa1dc65d7fb4e663f56841f718a48642fcccdfdaf` |
| `server/src/test/java/com/acrylic/outbound/inventory/TemporaryCandidateHttp.java` | 动态隔离 HTTP 端口、候选/错库/水位拒绝 | `7a5da1a1c9b7b0f185176226d176985edea3f1fc7b619520f0e85238ace120e2` |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aTemporaryExportBrowser.java` | Chrome runner 使用当前隔离 HTTP 端口 | `7fc5a004b7c76ca6d9b6ac8b646a7dbd4548b752d908ea7f43418a3a287c88c6` |
| `web/tests/temporary-export.spec.ts` | 导出/跨订单最新净值汇总 Chrome 入口 | `c1dda9581658ab8067ff1030f8afaf9dcf93494a1fa9c30fd01f5c52080648ff` |

## 定向与组合验证

1. 工作树定向单测批次 `d13-combination04-unit` 的 `RecoveryInitialRecordsTest`、`RecoveryInitialSourceTest`、`RecoverySourceBindingTest`、`RecoverySourceOccupancyTest` 合计 114/0/0/0。
2. 原提交真实 MySQL 占用批次 run 805（隔离 DB 19493、HTTP 19503）1 test、0 failures、0 errors、0 skipped；覆盖错库、事务内拒绝、`RECOVERY_PENDING`、所有者/库存变化拒绝和全表/文件守恒。
3. 未提交 TEMP 占用批次 run 806（隔离 DB 19494、HTTP 19504）1 test、0 failures、0 errors、0 skipped；覆盖 V99 非空迁移、`ABORTED`/`SOURCE_RESERVED`、错库/事务/水位/所有者拒绝和全表/文件守恒。
4. 最终恢复事务批次 run 812（隔离 DB 19500、HTTP 19520）执行 `E2aRecoveryWriterTransactionIT` 1/0/0/0。`RecoveryTransactionChecks` 覆盖 commit、afterCommit、rollback、末条 SQL 失败、outer/nested、stale、unknown commit；随后完整 Flyway 迁移并执行 HTTP 导出、renderer、XLSX/PDF 与导出相关 Python/Java 只读路径。
5. 同一批次 Chrome `web/tests/temporary-export.spec.ts` 1 passed；浏览器日志、导出账号绑定及 `temporary-export-browser.json` 均保留。MySQL 19500 在 `server.err.log` 记录认证启动和正常停机，19500/19520 在收尾后无监听，datadir、binlog、日志和失败批次不清理。

关键结果证据：

| 证据 | 字节 | SHA-256 |
| --- | ---: | --- |
| `.local/goal-20261017-resume/d13-combination09-transaction/integration-result.xml` | 144502 | `60552b932f2d447e4d69aac3d9a7dbe95bc2ff97f877596a296c7615799d3f07` |
| `.local/goal-20261017-resume/d13-combination09-transaction/integration.log` | 1237 | `1873a30b6403695d01d2add639ffa06df60692d8d5a1de2735fc3af212e7d866` |
| `.local/goal-20261017-resume/d13-combination09-transaction/private/browser.log` | 532 | `c4f21b25015c429257c6e2d85b9436b65ffab6532486ead69e4432957a745897` |
| `.local/goal-20261017-resume/d13-combination09-transaction/private/server.err.log` | 1295 | `3370e50798b23e15c4fb0a9be463c09a232b6afc89aff07c06c54819844209f2` |
| `.local/goal-20261017-resume/d13-combination09-transaction/private/temporary-export-browser.json` | 373 | `902df71bd36095cc9d674b56f92507b37f0861733260a2407dc26aceeba77ee9` |
| `.local/goal-20261017-resume/d13-combination09-transaction/test-completed.marker` | 104 | `7b54e0cb2542ffaca425f72ede143327404fe5b8fbf4ffc1cd8d759ba225466b` |

## 结论与未覆盖

本批满足 D13 受影响组合的 L2 证据要求：直接受影响 runner/测试夹具已按路径绑定，真实 HTTP/MySQL、非空迁移、事务与回滚反例、隔离拒绝路径、导出/renderer 和 Chrome 成功入口均有当次结果；原提交、未提交和失败数据库/日志继续保全。该结论不扩展为 D13 整体、D14–D18、E06/E07/E08、O/U、Android、完整 Python 外部消费者、原模拟器/恢复组合或 D18 L3。生产默认开关、七类生产决定、`localDeletionAllowed=false`、原库/现网、部署和发布边界保持关闭。

下一步执行受影响的 D14–D18、E06–E09、O/U 组合，完成候选输入后再按 D18 L3 要求执行 Android、真实 HTTP/MySQL、Chrome、Python、非空迁移、原模拟器和恢复全链回归。
