# R08 专用库方案与首子项建议（2026-09-27，指令 v13）

**性质**：接续准备评估，**未开工、未建库、未改代码**。本页只回答"R08 剩下的钱花在哪、要什么环境、第一子项是什么"。

## 一、R08 在主线的实际存量（先核事实，不看历史叙述）

R08 的源码**已并入 `codex/development`**（R08 独立分支 `codex/r08-export-rendering` 的成果已在主树）：

- 服务端：`server/src/main/java/com/acrylic/outbound/exports/` 的 `ExportCenterController` / `ExportRangeReader` /
  `ExportSummary`（+`Query`）/ `ExportRetry` / `ExportProducer` 等；
- Web：`web/src/exports/` 的 `ExportCenterContent.vue` / `ExportSummaryPanel.vue` / `useExportSummary.ts`；
- 渲染模块：`report-rendering/`（lib 已构建，`build/install/acrylic-report-rendering/` 存在）；
- IT：`server/src/test/java/com/acrylic/outbound/inventory/R08*.java` 共 **8 类 + 1 夹具**，标注测试点约 **39 项**：

| 类 | 行数 | 测试点 |
| --- | --- | --- |
| `R08ExportIT` | 191 | 14 |
| `R08RetryIT` | 76 | 7 |
| `R08SummaryIT` | 59 | 6 |
| `R08CenterPaginationIT` | 59 | 4 |
| `R08ExportFenceIT` | 87 | 4 |
| `R08AuthorizationRaceIT` | 83 | 2 |
| `R08ClockBoundaryIT` | 53 | 2 |
| `R08PreviewSeedIT` | 35 | 1（种子类，需 preview 库） |
| `R08CenterFixture` | 84 | 夹具（不单独跑） |

## 二、R08 剩余清单（按依赖状态分档）

| 子项 | 依赖状态 | 说明 |
| --- | --- | --- |
| **A. 主线上组合回归（8 类 IT 在 V48 上重跑）** | **依赖已就绪** | 源码在树、渲染模块已构建；**唯一缺的就是专用库**（见三） |
| B. 跨订单汇总 / 导出中心 | 源码 + 本机隔离验证已完成 | 缺"主线组合回归"（即 A）与正式契约整合；功能本身已验证过 |
| C. **TEMP 来源接入** | **R06 已收口 ✅（本会话）** | 现在具备接入前提；属**新开发** |
| D. **更正净结果** | **R07-3 已收口 ✅** | 具备前提；属**新开发**（净值/结果版本/期间口径） |
| E. 多设备来源（`VERIFIED_HANDOVER`） | **缺 R07-2/R07-5** | 交接的 TEMP 适配与故障分支未做 ⇒ **前置不存在**，应排在 C/D 之后 |
| F. 正式契约整合 / 版本 / 迁移编号 | 需主线协调 | 不属本任务 |
| G. 文件自动清理/配额、生产规模查询、现场打印/异地 | 未做 | 属运维/现场项 |

## 三、专用库方案（评估：**1 轮可完成**，不需要新建工程）

**为什么现在跑不了**：8 个 R08 IT 各自带**环境守卫**，只认两个库坐标：

- `R08CenterFixture:34`：`jdbc:mysql://127.0.0.1:23308/acrylic_r08_it`（或历史 wave1–4 的 `13316/acrylic_outbound_*`）
- `R08ExportIT:27`：`jdbc:mysql://127.0.0.1:23308/acrylic_r08_*`
- `R08ExportFenceIT:27`：同上；`R08PreviewSeedIT:21`：需 `23308/acrylic_r08_preview`

⇒ 在 e2a harness（19330 + `acrylic_e2a_cross_*`）下它们**按设计跳过**。这正是"R08 族需专用库"的实义。

**方案（复用已验证的构造器，零新工程、零守卫改动）**：

1. **入口**：把 `tools/e2a-provision-and-run-106.ps1` 的构造逻辑参数化为 R08 版本
   （`-Run`/`-Tests` 已有；需新增 `-Port`（23308）、`-DatabasePrefix`（`acrylic_r08_it`）两个开关，
   库名/用户名按该前缀生成）。**不改任何 R08 守卫、不改生产代码**。
2. **库坐标**：`127.0.0.1:23308` + `acrylic_r08_it`，账号沿用 harness 的按 attempt 生成 + 随机密码
   （写入 attempt 目录 `run-password.txt`，不入 Git）。
3. **应用侧**：用**已验证的 `-NonE2a` 通道**（导出 `OUTBOUND_DB_URL/USER/PASSWORD` + `OUTBOUND_MIGRATE=true`
   + `OUTBOUND_AUTH_SIGNING_*`（`.local/it-keys/`）+ `OUTBOUND_STORAGE`）⇒ 应用自迁移到 **V48**，
   无需手工迁移目标；R08 IT 是 `@SpringBootTest + MockMvc`，正好走这条通道。
4. **渲染依赖**：导出 IT 会起渲染子进程 ⇒ 需显式传
   `--outbound.exports.renderer-home=report-rendering/build/install/acrylic-report-rendering`
   与 `--outbound.exports.browsers=<已存在的 browsers 目录>`（本机 `D:\acrylic-pda-worktrees\r08-export\report-rendering\build\browsers`
   已存在；`E2aLatestSchemaRegressionIT` 用的就是这个派生方式）。若走环境变量，harness 需支持透传这两个值。
5. **attempt 编号**：**独立序列**（避免与 e2a 的 375+ 混号）⇒ 建议 `.local/r08-it-<Run>`，从 **R08-1** 起。
6. **一 attempt 一类**（沿用 `tools/e2a-batch-it.ps1` 的构造：每类一库一 attempt + XML 聚合 + 失败分类），
   `R08PreviewSeedIT` 单独用 `acrylic_r08_preview` 库跑（它是种子类，不是验收类）。

**工作量**：参数化 + 首库打通 ≈ 1 轮；随后 7 类跑批 ≈ 1 轮（合计 2 轮，符合"1-2 轮"的评估）。

## 四、建议的第一子项

**首选：A —— 用专用库把 8 类 R08 IT 在最新 schema（V48）上跑一遍（真库/MockMvc/真 HTTP 层），并记录失败与未验。**

理由：
1. **收益最高、风险最低**：源码与渲染都在树里，唯一缺的是库坐标；这是"把已有成果变成可复核证据"的一步，不是新功能。
2. **顺带回答一个真问题**：R08 的导出读取器是 `order_result_version` 的另一个消费者，而 V46/V47/V48 动过该表。
   目前**只有** `E2aLatestSchemaRegressionIT`（attempt 290）覆盖了"R08 导出读取器读最新 schema"的**一条**路径；
   8 类 IT 在 V48 上跑能把这条共享面风险从"一条路径"扩到"整套 R08 行为"。
3. **不动契约、不开开关**：全程默认关闭态即可（导出能力本身已是生产接线，R08 IT 不依赖 R06/R07 的开关）。

**次选（若 A 之后有余量）**：**C —— TEMP 来源接入 R08 导出**（R06 已收口，前提最实），
或 **D —— 更正净结果**（R07-3 已收口）。**E（多设备来源）应等 R07-2/R07-5**，前置不存在，不建议先做。

## 四之补：A 的执行实况与三个环境前提（2026-09-27 v14，已实施）

构造器已落地：`tools/r08-provision-and-run.ps1`（`-Port` / `-DatabasePrefix` / `-SampleData` 参数化 +
`-NonE2a` 通道 + 独立 attempt 序列 `.local/r08-it-<Run>`）。**未改任何 R08 守卫与生产代码。**
实测踩到三个**环境前提**（都不是产品缺陷，但缺一个就整类红）：

1. **渲染器必须配置**：未配 `outbound.exports.renderer-home` 时导出 POST 直接
   `503 EXPORT_UNAVAILABLE`（`ExportRendererProcess:25`）— 这是文档写明的 R08 前置。
   `@SpringBootTest` **不读** Gradle 程序参数，Gradle 自己的 `-D` 也只进 Gradle JVM ⇒ 必须用 Spring 的
   宽松绑定环境变量：`OUTBOUND_EXPORTS_RENDERER_HOME` / `OUTBOUND_EXPORTS_BROWSERS`（实测 R08-1 全红 → R08-2 修复）。
2. **`outbound.exports.sample-data` 必须为 true**：`ExportSnapshotReader:28` 用它在 `TEST_SAMPLE` 与
   `SOURCE_SNAPSHOT` 之间二选一，而 IT 断言 `TEST_SAMPLE`（e2a 导出夹具也是显式传这个开关）
   ⇒ 环境变量 `OUTBOUND_EXPORTS_SAMPLE_DATA=true`（实测 R08-2 该项红 → R08-3 修复）。
3. **库名与 URL 必须逐字匹配守卫**：`R08CenterFixture:34` / `R08ExportIT:27` 比对的是
   `jdbc:mysql://127.0.0.1:23308/acrylic_r08_it?`（**带 `?`，且不接受任何后缀**）
   ⇒ URL 必须带查询参数（`?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC`），
   库名必须**恰好**是 `acrylic_r08_it`。因此**不能靠改库名做 per-class 隔离**，
   必须**一类一 attempt**（每次 DROP+CREATE 同名库）——这正是"每类一 attempt"这条既有约定的由来。
   实测：批量跑 8 类共用一个库时 `R08CenterFixture.<clinit>` 抛
   `R08 center tests require the dedicated integration database`（29 项红），
   改用**每次全新库 + 一类一跑**后该错误消失。

**已取得的实测结果（V48，真库 + MockMvc + 渲染子进程）**：

| run | 类 | 结果 |
| --- | --- | --- |
| R08-2 | `R08ExportIT` | **14/14 通过**（XLSX/PDF/SCAN_XLSX 落地、幂等回放、越权、篡改、并发同键） |
| R08-4 | `R08PreviewSeedIT` | **1/1 通过**（合成正式单种子链可用） |
| R08-10 / 16 | `R08ExportFenceIT` | **3/4**：`recoveryGenerationBlocksPublicationAndDownload` **两次独立复现红**（见下） |
| R08-11 | `R08ClockBoundaryIT` | **2/2 通过** |
| R08-12 | `R08CenterPaginationIT` | **4/4 通过** |
| R08-13 | `R08RetryIT` | **7/7 通过** |
| R08-14 | `R08SummaryIT` | **6/6 通过** |
| R08-15 | `R08AuthorizationRaceIT` | **9/9 通过** |

合计：**8 类 47 项 = 46 通过 / 1 失败 / 0 错误**（R08-4 那次批量 29 项红已由"一类一库"消除，属环境构造问题）。

### 待专项：`R08ExportFenceIT.recoveryGenerationBlocksPublicationAndDownload`（**已确认可复现，未判定为产品缺陷**）

- **现象**：`com.acrylic.outbound.common.BusinessException: 服务器恢复代次已变化，请刷新后重试`（`ServerState:39`）
  从 `assertThat(run(...))` 的辅助方法**逃逸**（该测试 helper 未捕获），发生在 `run(job->UPDATE server_state SET
  recovery_epoch=recovery_epoch+1 ...)` 之后的失败记录路径上。
- **代码路径**：`BackgroundJobLeases.fail:76` → `lockOwner:97` → `JobRecoveryFence.lock:18` →
  `ServerState.lockWritable:22` → `validate:39`。即：数据库代次已被本次 job 自己 +1 后，
  **失败结算（`fail`）走栅栏校验时抛 409**，而不是像 `lockOwner` 的返回值语义那样"不匹配即 STALE"。
- **判定状态：未定**（不排除这是"栅栏按设计拒绝"、而测试期望沿用了旧实现）。**本轮按指令停在这里报告**，
  未改任何生产代码、未弱化任何断言、未改 R08 守卫。
- **下一步建议**：由熟悉 R05 任务内核的一方裁定 `fail()` 在代次不匹配时应"返回 STALE"还是"抛 409"；
  若判为测试期望过时，则修测试；若判为缺口，则属**真 bug**（job 可能卡在 RUNNING、失败无法结算）。
  两次失败日志与 XML 均保留（R08-10 / R08-16 attempt 目录）。



## 五、未验与边界

- 本页为**评估**：未建库、未跑任何 R08 IT、未改代码/配置/守卫；"1 轮可完成"是**估时**，不是实测结论。
- 未核：`R08PreviewSeedIT` 的 preview 库种子流程细节、渲染子进程在本机的实际耗时、
  Web 侧浏览器 8 项（需独立 Web+API+Worker，属另一步）。
- 不改边界：不合 main、不发布契约、不动 R08 守卫与生产代码；不操作原库/现网。
