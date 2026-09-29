# R09 C2 B1-3 S29：树内 chromium 安装＋Android/夹具链根因定位（2026-09-29）

> 轮次身份：主树 `D:/acrylic-pda-outbound`、分支 `codex/development`；写者＝DSH 本会话；**同一会话实施＋自审＋运行，非独立第三方验收**。授权：用户 2026-09-29"继续"（推进不需新授权的项）。起点 HEAD `c9c4cd3`。**无产品代码改动**（环境安装＋harness 环境变量）。

## 1. 已完成：树内 Playwright chromium（去掉跨树引用）

`gradlew -p report-rendering installChromium --no-daemon` → **BUILD SUCCESSFUL in 37s**，`report-rendering/build/browsers` 现含 `chromium-1187`／`chromium_headless_shell-1187`／`ffmpeg-1011` ⇒ **S20 声明的"跨树回退到 r08-export"不再需要**（运行器本就"树内优先"，现在树内即为真）。

## 2. Android 两项（644／647）的阻塞链——已定位到最后一环

| 环节 | 事实 |
| --- | --- |
| ① 直接原因 | `:app:testDebugUnitTest`（`E2aTemporaryArchiveIT`／`E2aTemporarySuccessConsumerIT` 用 `cmd /c gradlew.bat -p android` 拉起）因 **输入文件不存在**被 Gradle 拒绝：`server/build/pda-contract-fixture.json`、`pda-temporary-*-fixture.json`、`pda-submission/-archive/-recall/-continuation-fixture.json`（`android/app/build.gradle.kts` L85–94 声明为 inputs） |
| ② 夹具由谁生成 | `server/src/test` 的 **8 个 inventory IT**：`PdaPreparationIT`／`SubmissionApiIT`／`SubmissionArchiveIT`／`RecallApiIT`／`ContinuationApiIT`／`TemporaryContractFixtureIT`／`TemporaryAuditContractFixtureIT`／`TemporarySubmissionContractFixtureIT`（`docs/ORDER_NUMBER_GUIDE.md` 也记过这个坑："首轮 Android 测试因后端 pda-contract-fixture 尚未生成被 Gradle 拒绝"） |
| ③ 这 8 个 IT 在本 harness 的失败 | 先 `OUTBOUND_DB_*` 缺失（已补，见 §3）→ 再 **`Failed to load ApplicationContext`**，根因链：`JobRecoveryFence` 构造失败 → `BadSqlGrammarException: SELECT recovery_epoch, service_mode FROM server_state WHERE id=1` → **`Table '…server_state' doesn't exist`** |
| ④ 最后一环 | 该 schema **没被迁移**：本 harness 沿用参考值导出 **`OUTBOUND_MIGRATE=false`**（参考 runner 的 IT 自己迁移），而这 8 个 IT 依赖**应用启动时自己跑迁移**。⇒ 下一步只需给该批 IT 用 `OUTBOUND_MIGRATE=true`（或加一个 `-AppMigrates` 开关），**无需改产品代码** |

## 3. 本轮 harness 改动（一处，已自审）

`tools/r09-e2a-entry-rerun.ps1`：补导出 `OUTBOUND_DB_URL`／`OUTBOUND_DB_USER`／`OUTBOUND_DB_PASSWORD`（与 `SPRING_DATASOURCE_*` 同值）——因为 inventory 类 IT 走 `FoundationEnvironment` 读的是这套旧名。**单靠它未能让 8 个夹具 IT 通过**（它们随后卡在 §2③ 的迁移问题），如实记录，不虚报收益。

## 4. 过程失败留档（未删）

- 我试图给运行器加 `-TestClasses`（多类一次跑）时**改坏了参数块**（3 个解析错误）⇒ 用 `git checkout -- tools/r09-e2a-entry-rerun.ps1` **恢复已提交版本**后重做，未把坏文件留在树里。
- run 664–671：8 个夹具 IT 因缺 `OUTBOUND_DB_*` 全失败；run 672–679：补变量后改为 Spring 上下文失败（根因见 §2③）。两批 attempt 与 datadir 均保留。

## 5. 状态

- **E2a 主树已验证 22 个**（本轮不变）：604、621、622、623、628、629、630、631、634、635、638、639、640、641、643、648、657、658、migration、devicekey、pairedkey、auditprefix；C1 侧 611 通过。
- 未验：**659**（待裁定：产品侧仪表化 or 改判不可验）、**V36 stopwriter／auditarchive**（待裁定私钥物料）、**644／647**（Android；阻塞已定位到 §2④，下一步是 `OUTBOUND_MIGRATE=true`）。
- 开关四项默认 false；未接生产、未部署、未合 main；K1 仍未通过（窗口 W、`UNCONFIRMED_NO_CROSS_PROCESS_BASIS` 原样保留）；`localDeletionAllowed=false`。
- 回归说明：无产品代码改动 ⇒ 恢复链 25 类批次沿用 S12 轮同产品 HEAD 证据（实例 19476：25/696/0/0）。

## 6. 证据位置（不入库，按约定保留）

`.local/e2a-cross-20260925-664…679`（两批夹具 IT 的失败轮，含 `TEST-*.xml` 与 datadir）、`.local/r09-c2-b1-3-s27-<item>/`（V36 记录副本）、`.local/s29-chromium.log`（安装日志）；`report-rendering/build/browsers` 为构建产物（不入库）。端口 19330 已释放。
