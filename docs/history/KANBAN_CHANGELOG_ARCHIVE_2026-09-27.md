# 看板变更日志归档（2026-09-27）

本文件保存从看板当前区移出的较早变更日志条目（原文，未改），以便当前区保持在 100 行 / 8000 字符上限内。

- 2026-09-27 修 `ExportRendererProcessTest` pid 握手 flake（`9480a21`）：只判 `exists` 就 `parseLong` 会命中 `Files.writeString` 的空文件窗口；改原子发布＋等非空内容（A/B 12998→0）。隔离/负载/全量 718 均 0 失败。
- 2026-09-27 IT 分批评估 attempt 179（`*E2aTemporaryArchive*IT` 3 类 / 1 attempt）：1 通过 2 红，均 `One-shot attempt already used`（结构性，非产品）；构造器 `tests` 计数与 `-Tests` 同名失真（复现 `0111`）→ 需先封装分批入口。
- 2026-09-27 分批入口封装（选 A）：`f34c886` 修构造器 `tests` 计数；新增 `tools/e2a-batch-it.ps1`（每类一 attempt + XML 聚合 + 分类 + `entryIssues`）；runs **181–184** 四类全绿；波次 IT 仍需专用库。

---

## 归档：看板变更日志（2026-09-26 ~ 2026-09-27，v10 压缩时移出，原文未改）

- 2026-09-26 文档收敛：本页改生成事实块 + 四节字段化（硬上限 100 行 / 8,000 字符；旧当前区归档至 [history](history/ORCHESTRATION_STATE_before_status_convergence_2026-09-26.md)）；[AGENTS.md](../AGENTS.md) 收敛为纯规则；新增 `tools/version_facts.ps1`（`-Write`/`-Check` 校验上限与漂移，已用篡改 Room 版本与超长当前区两例反证 exit 1）。
- 2026-09-26 `27729f7` 默认关闭的命令接线（3 个 operation + POST 路由，受 `outbound.temporary.commands.enabled`）+ 服务模式启动告警；服务端全量 718 项 0 失败。
- 2026-09-26 `be2048e` 单一基线合并：两树 16 个双侧改动逐条核对，无一方改动被静默丢弃。
- 2026-09-26 冻结两树：`3c677ad`（主树 10 提交）/ `d16836b`（服务端树 6 提交），两个 freeze tag 已推远端。
- 2026-09-27 发布身份链：生成正式 keystore（`.local/pda-signing/`，两份备份分别在 D: 与 E: 两块物理盘）、`signingConfigs` 接入 release、versionCode 20→21、`build_pda.ps1` 支持 `-BuildType` 并注入更新公钥、更新白名单补 `0.33.0`；release APK 经 `apksigner verify` 通过且签名摘要与 keystore 一致。
- 2026-09-27 工作树清点（只读，未删除）：34 个工作树、33 个分支、112.9 GB；发现 2 个**未注册检出**（`D:\acrylic-entry-verify`、`D:\acrylic-snapshots\…\workspace`）共用 `r06-e2a-migration` 的 gitdir。归档前须先取证，删除一律待批。
- 2026-09-27 唯一文件回收（选项 A）：32 个文件经 `git log --all` 与对象库 blob 双重确认从未入库，30 个提交进基线（datetime-display / R06 设计 / R07 设计），2 个归档到工作区外 `E:\acrylic-archive-unique-20260927`；清单见 [回收记录](history/UNIQUE_FILE_RESCUE_2026-09-27.md)。验证：Android 451 项 0 失败、web typecheck 通过。
- 2026-09-27 `D:\acrylic-entry-verify` 726 个缺失文件＝**刻意的部分副本，不是丢失**（集中在 android/web/demo/contracts/tools/deploy 等无关模块，server 仅 2 个）；726/726 均可从 Git 取回。
- 2026-09-27 合并三提交（选项 B）：`0b1094e`→`f196887`、`94305a7`→`1e98e44`、`f051535`→`8bb3e57`。验证：`R08AuthorizationRaceIT` 9 项全通过、`npm run build` 通过。两个环境发现：V35+ 建触发器需 `log_bin_trust_function_creators=1`（否则 Flyway 1419）；`renderer-home` 必须是当前源码同批 `installDist` 产物，否则作业 `EXPORT_RENDER_FAILED`。详见 [合并记录](history/MERGE_BATCH_2026-09-27.md)。
- 2026-09-27 选项 C 前置：`r08-export` / `r09-v33-recovery` / `sixth-wave-integration` 三棵树已合并且工作区干净，**可进入删除候选**；删除仍待业主逐项批准。
- 2026-09-27 TEMP 归档 attempt 174（`dd54c8a` 修 7 处 attempt 号守卫，仅测试/夹具）+ 看板 head 与 R07 口径修正（`00a17e9`）；attempt 172 的 closure 归因被证伪（把 `corruptIndex` 的故意反例日志当成根因）。详见 [R06 欠债清单](history/R06_DEBT_LIST_2026-09-27.md)。
- 2026-09-27 TEMP 归档收口（选 B＝新能力默认关闭）：`2a3acce` 加 `outbound.temporary.archive.enabled`（默认 false）+ `a91572e` 修关闭态 500（`IdentityWriteGuard` MANDATORY）；attempt 176 绿（验收达成）、178 绿（关/开两场景）；attempt 号守卫扫描无遗漏。
- 2026-09-27 Android LINK L4（选④）：`2bf2507` 三件套 → attempts **185/187** 双绿 `server=1/0/0/0`/`android=1/0/0/0`，L3→**L4**；`java.net.http` 不在 Android classpath→OkHttp；构造器补 `--rerun-tasks`。

## 归档：v7-v9 变更日志（v13 为腾出上限移出，原文未改）

- 2026-09-27 v7-v9：**V43-V48 未破坏共享面**（290）；R07-3 Web 路由已验（292）；**非 e2a 回归面解锁**（临时密钥 + `-NonE2a`）：已跑 **56 类**（53 通过 / 3 类需专用环境），并**发现+修复本会话真回归**（FORMAL 归档确认自调用绕过事务→500，`36d2368`／316 复验全绿）；v9 高风险 23 类 = 21 绿 / 2 环境守卫、**无新 bug**；A3 残余 ①②待专项（②诊断已锐化）。A3 CSRF 根因：会话 Cookie 默认 `secure=true` 与 harness 明文 HTTP 冲突（生产默认不改，harness 置 false）→ `E2aHandoverHttpLayerIT` ①② 转绿（284/285）；残余=两处夹具基础设施（待专项）。R07-1 夹具卡点解除（幂等探测改重放原 REQUEST）→ 反例①②③④+3 幂等已验／283。历史：A 组共享表回归（14 类：10 通过 / 4 历史失败，均在不应用 V46/V47 的库）+ **最新 schema 回归 IT**／**256**；B 组（窗口权威来源 / 附件三档限额 / HEADER 补丁）与 C 组（撤销两阶段 + V48 模型）／**255**；契约草案未发布、默认关闭、未部署。

## 归档：v13 变更日志（v14 为腾出上限移出，原文未改）

- 2026-09-27 v13：**A2 口径修复＝停下报告（须先改已声明契约）；R08 专用库方案已评估**。A2 三处阻碍：facts 面新增字段违背 `temporary-command-paths.yaml:47` 明文承诺且 `Facts` 为 `additionalProperties:false`；候选接口新增改动其已声明形状；`candidateSetSha256` 是**死字段**（`TemporaryDuplicateService.read:32` 实际传 `snapshot.fingerprint()`）、`contextSha256` 已被**游标绑定** ⇒ 命令口径摘要在 HTTP 面**无承载字段**，修它必须动契约 ⇒ 按硬边界**未改一行代码**（[A3 §6.5](history/A3_CSRF_ROOT_CAUSE_2026-09-27.md)）。R08：源码**已在主线**（`exports/`、`web/src/exports/`、`report-rendering/` 已构建），8 类 IT ≈ 39 测试点，**只缺专用库坐标**（守卫只认 `127.0.0.1:23308/acrylic_r08_it`）；方案＝构造器参数化 + `-NonE2a` 通道（自迁移 V48）+ 透传渲染器路径，**不改守卫/生产代码**，估 1 轮打通；**首子项建议＝在 V48 上跑 8 类**（[R08 专用库方案](history/R08_DEDICATED_DB_PLAN_2026-09-27.md)）。**均未开工。**

## 归档：v12 变更日志（v15 为腾出上限移出，原文未改）

- 2026-09-27 v12：**A3 残余① 收口 ✅ + A2 口径定性**。① `E2aHandoverHttpLayerIT` attempt **374 绿**（`1/0/0/0`）：夹具两个原因＝请求体全零锚点（业务层先 404、权限门触达不到）＋只迁 V42 而 `order:handover` 由 V44 才注册（授予静默插 0 行，162 老陷阱）；修法＝真实 FORMAL 来源＋迁 V44＋完整 expected。实测 ①404 ②401 ③**403 PERMISSION_DENIED**（非 CSRF）④**200 案件建立** ⑤resume-only APPROVE→409（过 approval 门）⑥handover-only REQUEST→409（过权限门）。**A2 口径＝命令面完整性缺陷**（契约 `temporary-command-facts-schemas.yaml:26` 早写明"由候选接口提供"，但该接口给的是另一套摘要 ⇒ HTTP 客户端无可用来源）⇒ 可独立修、不需开关授权、不改字段类型，本轮**只定性未修**。**待裁决**：内核权限门接受 `order:handover||order:resume`，按命令拆分未实现。硬边界：生产代码/配置未改、开关未开、探针已清。

## 归档：v11 变更日志（v15 为腾出上限移出，原文未改）

- 2026-09-27 v11：**A3 残余② 收口 ✅**——`E2aTemporaryCommandWebHttpIT` attempts **363/364** 均 `1/0/0/0` 绿。诊断先落地（登录响应此前被后续 CSRF 刷新覆盖⇒从不进报告，改逐请求 `[wire]` + `SPRING_SESSION` 对拍）；**第一层根因＝`csrf()` 用 `HttpRequest.newBuilder` 绕过带 Cookie 的 `request(...)`⇒令牌请求无 Cookie⇒匿名会话顶掉登录轮换出的已认证会话（401）**；**第二层＝候选摘要两套口径**（复核页 `contextSha256` ≠ `TemporaryDecisionCandidates.snapshot` 命令口径）⇒LINK 稳定 409（后经 v12 定性为命令面缺陷）。硬边界：开关未开、生产代码/配置默认值未改、守卫星树探针已回滚（主树 `A3-DIAG` 0 条）。

## 归档：v10 变更日志（v15 为腾出上限移出，原文未改）

- 2026-09-27 v10 换会话固化：看板压缩 7990→5596 字符（13 条旧日志按日期归档，余量 2404）；**R06 开发+回归基本完成**（v7-v9 累计 95 类 IT 在 V48 上跑过，修复真 bug `36d2368`，共享面健康）；未完成＝A3 残余①②、约 80 个非 e2a 类、三族专用库、政策 P1-P7、七类开关授权。

## 归档：v14 变更日志（v16 为腾出上限移出，原文未改）

- 2026-09-27 v14：**A2 口径已修 ✅**（`dcaf7ad`，attempt 377 绿）：候选接口新增只读 `commandContextSha256`，Web 全线改用它 ⇒ 开关一开不再必然 409；facts 面仍 `null`、capabilities 仍恒 false；契约**草案** 0.36.0→0.37.0（未发布）+ typecheck 0。**R08 专用库构造器跑批 ✅**（`9e448d7`）：**8 类 47 项 = 46 通过 / 1 失败**（Export 14/14、PreviewSeed 1/1、ClockBoundary 2/2、CenterPagination 4/4、Retry 7/7、Summary 6/6、AuthorizationRace 9/9）。**待专项（未判定真 bug）**：`R08ExportFenceIT.recoveryGenerationBlocksPublicationAndDownload` 两次独立复现红（`BackgroundJobLeases.fail:76→JobRecoveryFence.lock:18→ServerState.validate:39` 抛 409 逃逸）；**已按指令停下报告，未改生产代码/断言**。环境前提三条已记档（renderer-home / sample-data / 库名逐字匹配守卫 ⇒ 一类一 attempt）。（[R08 方案](history/R08_DEDICATED_DB_PLAN_2026-09-27.md)）

## 归档：v15 变更日志（v17 为腾出上限移出，原文未改）

- 2026-09-27 v15：**R08ExportFenceIT 判定＝测试期望过时 ✅（run 18 = 4/4；R08 8 类 47/47）**：代次由**测试自己**在 job handler 里 +1；`BackgroundJobWorker.failed→BackgroundJobLeases.fail:76→lockOwner:97→JobRecoveryFence.lock:18→ServerState.validate:39` **先抛 409** ⇒ `lockOwner` 的"不匹配即不认领"永不返回 false、`fail()` 的 STALE 分支不可达，连 `recoverExpired` 也抛 ⇒ 契约本就是"绝不静默接受被恢复的代次"，处置＝重启进程；已按实测语义改断言（**未改生产代码**，`8de3223`）。**子项 C 阻塞点已定位**（TEMP 单导出走 `PdaExportSource` 报 400 `INVALID_SUBMISSION`，attempts 378/379）。

## 归档：v16 变更日志（v17 为腾出上限移出，原文未改）

- 2026-09-27 v16：**R08 子项 C 已实现 ✅**（`exports` 包三处，既有路径未动）：①`ExportFacts.tempOrigin`（`submission_scope.source_order_id IS NULL` ⇒ 提交源自 TEMP）；②`ExportSnapshotReader` 选路新增"结果版本行的提交源自 TEMP ⇒ 走 `TemporaryExportSource`"（PDA_TEMP / 正式 PDA / PC 三路原样）；③`TemporaryExportEvidence` 映射版本 `==1` 放宽为 `∈{1,2}`（R06 受控关联后的确认必为 v2，旧断言把合法导出挡在门外）。**先探针后实现**：探针 attempt 382 即返回 200 ⇒ 证明缺口恰为此两处；正式实现 **attempt 383** `1/0/0/0`，TEMP 链尾导出得 `format=XLSX basis=ORIGINAL_CONFIRMED resultVersion=1 snapshotId=f913d65c…`。**不破坏既有**：R08 导出族回归 run 19-22 = **Export 14/14、ExportFence 4/4、Summary 6/6、Retry 7/7 全绿**。（[R08 方案 §6](history/R08_DEDICATED_DB_PLAN_2026-09-27.md)）

## 归档：v17 变更日志（v19 为腾出上限移出，原文未改）

- 2026-09-27 v17：**C 的渲染验证未接通线（如实记录，未硬做）**：新类 `R08TemporaryExportRenderIT`（`@Disabled` 附原因）三条路实测均不通——a) R08 库+MockMvc：TEMP 链夹具走**真 HTTP 19434** ⇒ ConnectException（R08-24）；b) e2a harness 不带 `-NonE2a`：数据源缺 URL ⇒ `url must start with jdbc`（384）；c) 带 `-NonE2a`：数据源被指到同一 attempt 库而链夹具随后 `profile.migrate(…,34/42)`，Flyway target 低于当前版本报错。**可执行接法**：给 e2a runner 加"只导出库坐标、`OUTBOUND_MIGRATE=false`"模式，让应用 context 与 `E2aCrossSourceProfile` 共用同一 attempt 库。⇒ C 证据边界＝**入口受理+首版快照（383）＋既有路径回归绿（19-22）**，**渲染内容正确仍未验**。**「断言假设过时」扫描**：同类仅 `TemporaryExportEvidence:46`（已修）；`ExportFacts:29/:55`、`ExportRangeReader:45`、`CommandBusinessIntegrity:81` 的 `result_version=1` 属设计，但与**子项 D 直接相关**（D 前先定"更正后导出取哪个结果版本"）。

## 归档：v18 变更日志（v20 为腾出上限移出，原文未改）

- 2026-09-28 v18 自主运行：**A1 收口 ✅**（TEMP 渲染验证接通：e2a harness 新增 `-CoordinatesOnly`（只给库坐标不预迁移）＋渲染器透传；新 IT `E2aTemporaryExportRenderIT` 用"同一 attempt 库＋真服 19434＋可注入 bean"跑通 **attempt 390**：XLSX 13965B / SCAN_XLSX 4400B / PDF 360092B 真生成，openpyxl 回读订单汇总 `FH-WEB-0001`/来源 `TEMP_PDA`/结果版本1/2.0 包/**601.5kg**、产品 003→300.5 + 571→301.0、扫码明细 2 行、扫码 Excel 单表 2 行 ⇒ **内容正确**）。**A2 收口 ✅**：重启后新代次接管**已由既有** `JobFenceChecks.restartedOldJob` 覆盖，跑出绿证据 **attempt 401**（`E2aJobFenceHttpIT` 1/0/0/0）；取证前修掉 harness 两处断言级缺陷（piped SQL 静默截断致 GRANT 丢失、隔离账号无权读 `performance_schema`，另统一 CRLF）。**A3 完成 3 批**（attempts 431-463）：新增 **19 个非 e2a 类 / 241 项**（240 通过 / 1 条件 skip / 0 产品失败）；2 个 `IdentityLock*` 为**环境守卫**（需 19321 专用库）、`ReminderListRows` 系我误列夹具。**D2**：看板清掉 5 条冗余"已归档"占位（→61 行 / 6954 字符）。**环境阻塞已处置**：`D:` 曾 0GB 可用致 MySQL 无法 init（attempts 416-424 NOT-EXECUTED）；删 501 个 attempt 的 **datadir**（91.4GB）腾出 92.7GB，**日志/marker/storage/密钥全部保留**。**未开任何开关、未发布契约、未改 R05/R06/R07 业务逻辑。**

## 归档：v21 变更日志（v22 为腾出上限移出，原文未改）

- 2026-09-28 v21：**G7 已修 ✅**（`4b8ab5b`，只改描述）：`export-center-paths.yaml:4` 明确"限 WEB 正式已完成订单原确认 V1（**含**由 TEMP 批次经 R06 受控关联并确认后正式化的订单；**未关联的 TEMP_PDA 临时单不在汇总内**）；不含更正净结果、未核验多设备来源"；`IMPLEMENTED_API.md:24` 属 2026-09-20 历史段 ⇒ 就地加更正注记（不改写历史）；`SummaryResponse` 与 `web/` 无同批需改文案。**版本不单独递增（0.38.0 留下次实质变更）**。**D 方案 C 第一步＝触发停止条件，停下报告**（`aa4c8ed`）：`ConfirmedV1Adapter:11` 对非原确认 basis **硬拒**、`:29/:36` **版本写死 1**，且请求面 `ExportModels.Command:11` 是 `@Pattern("ORIGINAL_CONFIRMED")` 硬编码 ⇒ 渲染更正净结果需**新写更正适配器（跨模块新代码）**＋改请求面正则（契约面）⇒ **未写任何 v>1 代码**；建议拆三片（服务端只读快照→渲染适配器→请求面），并待裁 3 个语义（调整凭据行/汇总口径/命令完整性口径）。

## 归档：v20 变更日志（v22 为腾出上限移出，原文未改）

- 2026-09-28 v20：**G7 判定＝描述精度问题（实现不改）**：契约"不含 TEMP"在设计语境指**未关联的 TEMP_PDA 临时单** ⇒ **子项 C 不越界**（次日已按授权改描述，见 v21）。**D 开工前实测＝面超授权**：`V46` 的 `ck_result_channel` 第三支使 **v>1 行无提交来源**，三条读取器全以"提交"为锚 ⇒ 需新写更正来源读取器＋跨模块 `Basis.CORRECTED`＋契约面（[D 范围报告](history/D_CORRECTED_EXPORT_SCOPE_2026-09-28.md)）。

## 归档：v19 变更日志（v22 为腾出上限移出，原文未改）

- 2026-09-28 v19：**磁盘例行策略落地**（`cleanup-old-attempt-datadirs.ps1`：只删旧 attempt 的 datadir、保留最近 50，日志/marker/storage/签名材料一律保留；两 harness 收尾自动接线，冒烟 464 绿）。**B 组确认 ✅**：跨订单汇总与完整导出中心**已具备**（源码＋已进正式契约＋`Summary 6/6 / CenterPagination 4/4 / Retry 7/7 / AuthorizationRace 9/9 / ClockBoundary 2/2` 全绿）⇒ 无功能缺口，缺口清单 G1-G7 均政策/规模/现场/运维项。**待 D 口径**（[B 组确认](history/R08_B_GROUP_CONFIRMATION_2026-09-28.md)）。
