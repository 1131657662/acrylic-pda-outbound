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
