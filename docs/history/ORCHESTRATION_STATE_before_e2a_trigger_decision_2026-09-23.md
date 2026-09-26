# E2a 触发器授权决策卡前的当前区原文快照

<!-- CURRENT_STATE_START -->
## 当前有效状态（唯一接续入口）
### 2026-09-23 R06 E2a 隔离迁移续作：V35 触发器前置核查已写，attempt6 阻断仍在

- Git：主树 `D:/acrylic-pda-outbound` / `codex/development` @ `23ae8c492acf47e89875852d2573392fcf379f80`；原有 Android 18 源/资源与 11 测试、server 3 文件等在途改动不碰，原 `docs/out.xlsx`、`docs/PDA_ORDER_FLOW_ISSUES_2026-09-23.md`、新增 `docs/PDA_TEST_RELEASE_CODE22_2026-09-23.md`、前一版历史快照及 `docs/superpowers/` 等未跟踪仍保留。本任务主树看板、`docs/history/README.md` 索引及本次历史快照未提交。迁移原树 `D:/acrylic-pda-worktrees/r06-e2a-migration` / `codex/r06-e2a-migration` @ `568683c1a1f8257ad0596a654c6b3558c767ed95`，原验收器未提交改动保持，新增 `E2aMysqlTriggerPreflight.java` / `E2aMysqlTriggerPreflightTest.java`，修改 runner 与两份专项文档；完整路径以两树 `git status --short` 为准。未提交、推送、合并或部署。
- 写权/重型槽：本任务仅持迁移原树 E2a 验收代码/测试/专项文档与主树看板/历史快照写权；本轮 Gradle 聚焦测试已结束，19329 无监听，未启动 MySQL。其他树、Android/服务端在途改动、现网、原库、test/live 部署和既有执行者写权未接管，旧代理 ID 不当活跃身份。
- 作者静态验证：`server` 中 `test --tests com.acrylic.outbound.e2averify.E2a*Test` **35/0失败/0错误/0跳过**，含 7 项触发器预检测试；`compileTestJava` 实际执行。初次从根工程调用不存在的 `:server:test`、从 server 调不存在的包装器均仅为命令定位失误，正确包装器 `../gradlew.bat -p .` 后通过，不是产品测试失败。完整单测/构建、独立消费者、组合验收、APK/模拟器/真机/HTTP/部署均未在本断点通过。
- 新增验收行为：专用库连接和进程身份核实后、任何 V34 Flyway/seed 前，仅读 `DATABASE()`、`CURRENT_USER()`、MySQL 版本、global binlog/trust 值及专用账号的 schema TRIGGER/global SUPER 权限；写 `CREATE_NEW` 的本次 `trigger-preflight.json`。要求 binlog 保持开启、无 SUPER、TRIGGER 权限和经批准的 trust 策略；不自动改全局配置/账号授权，`preconditions-met` 仅代表技术条件、不代表部署批准。本轮未分配或运行 attempt7。
- 本轮静态证据：迁移树 `server/build/test-results/test/TEST-com.acrylic.outbound.e2averify.E2a*Test.xml` 共 8 份，35/0/0/0；新预检 XML SHA-256 `F882188383FC8B5FE9259D1C224102007214A6BE5E65E2F289A689DB2C29E8BA`。动态 attempt6 `before-v35.json` 与失败 XML、日志、marker 沿原路径保留，不是本轮通过。
- 动态作者 one-shot：attempt1–3 保留旧失败；attempt4 全新 profile 在 V34 seed 的循环 FK 插入失败（已修）；attempt5 全新 profile 在 evidence set 的 SQL 绑定数失败（已修且新增三模式测试）；attempt6 全新 profile 完成非空 V34 seed 和 `before-v35.json`，V35 `CREATE TRIGGER` 第 60 行因本机 MySQL 1419（binlog 开启、专用账号无 SUPER）失败。attempt4/5/6 每个 marker 均 `failed` 后 `stopped`，经鉴权停止、19329 零监听；各数据目录、日志、私有凭据、stdout/XML 均不重用、不删除、不 repair。
- 证据：迁移树 `.local/evidence-e2a-attempt4/integrationTest-result.xml` SHA `A4E0CE88…`，attempt5 `F7727947…`，attempt6 `97F79156…`；对应 `.local/e2a-dynamic-attempt{4,5,6}-20260923.stdout.txt`、marker 与 datadir。attempt6 `before-v35.json` SHA `89353792…`，无 after/V35 负向结果、无 target marker。旧 attempt1 XML 曾被覆盖；attempt2/3 XML 和 marker 继续保留，详情见专项 `ACCEPTANCE_STAGES.md`。
- 限制与下一步：attempt6 V35 MySQL 1419 仍阻断，原 profile/证据不重用；不关闭 binlog、不授予 SUPER、不修改固定 V35 SHA 或绕过审批以造通过。先由迁移/运维权限持有者确认生产兼容的 `log_bin_trust_function_creators`/触发器迁移授权方案，再明确分配全新 attempt7，继续 V35、负向矩阵与 before/after。PDA 问题用户称已解决，未在本 E2a 任务复核；R05 整体未验、Android code20/Room17 历史限制、现场/R09 和七类生产写入/决定/放行限制仍单列。作者聚焦测试不能冒充独立或组合交付。
- 被替换的完整当前区原文归档：`docs/history/ORCHESTRATION_STATE_before_e2a_trigger_preflight_2026-09-23.md`，其上一版在 `docs/history/ORCHESTRATION_STATE_before_e2a_attempt6_2026-09-23.md`；下方看板历史区保留。
<!-- CURRENT_STATE_END -->
