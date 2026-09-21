# 第四轮整合记录

2026-09-21，执行者 W2 `/root/e2_source_model`。独立工作树 `D:/acrylic-pda-worktrees/fourth-wave-integration`，分支 `codex/fourth-wave-integration`，固定管理起点 `5558052725de4069dffa9f49507c251e4a46e02a`。主调度五份管理文件和主目录 `docs/out.xlsx` 不在本树修改范围；development 合回须另行派工。

## 已审来源与合入

| 批次 | 已审作者提交 | 独立复审 | 保留历史合并节点 / 范围 |
| --- | --- | --- | --- |
| L2 精确身份锁及 C04 | `b530784b7e0122458d87fae6e0e22e4b46976d79` | E2 执行者覆盖 27 生产、7 测试及指南，核对作者 XML、源码摘要和锁证据，无整合阻断；未重跑测试 | `f1d3eeb0b6705cf9db4835a12b3696a2b26e1592`，35 文件 |
| E2 来源/共同占用模型 | `a073c230e060b41cf84701ddc100ff3496ee4aec` | C1 执行者核对四份方案及源码接点，无文档整合阻断；未跑产品测试 | `f2b0d834654b1a7a39519136088443f294e2976e`，4 文档 |
| C1 命令台账/原回执方案 | `26b224f4134369cac3f3dd894758cb82bcf0500e` | L2 执行者核对四文档、引用和 E2/L2 接点，无文档整合阻断；未跑产品测试 | `745904ec205e5c61cf9d518489f5741e2b43c90f`，4 文档 |

三次均使用 `merge --no-ff --no-commit`，自动合并无冲突；提交前暂存路径与作者差异集相同，逐文件 Git blob 与已审提交一致。35/4/4 文件原字节 SHA-256 和 Git blob 保存在 ignored `.local/wave4-20260921/*-merge-manifest.json`。来源合入后的 43 文件另由 C1 执行者只读初审，主调度五份管理文件相对起点未变。

作者 L2 的 43 单元、532 个不同 IT 通过及 2 项输入缺失跳过属于作者证据；首轮 531 IT 中 6 项 R08 503 配置失败及后续 15 项补跑记录保留于 [L2 指南](R06_L2_IDENTITY_SET_GUIDE.md)。E2/C1 是方案文档，详见 [E2 交接](parallel/R06_E2/HANDOFF.md) 与 [C1 交接](parallel/R06_C1_LEDGER/HANDOFF.md)，不能写成生产 schema/台账/签名协议已实现。作者和 [第三轮](THIRD_WAVE_INTEGRATION_GUIDE.md) 的结果不计入本轮新跑。

## 本轮隔离环境与护栏

仅新建 `127.0.0.1:19322` 实例，datadir 为本树 `.local/mysql/data`；创建前核对端口无监听、目录不存在，连接后用 `--batch --raw` 精确核验 datadir，确认库和账号均不存在才创建。新库 `acrylic_outbound_wave4_20260921`、新账号 `outbound_wave4_20260921`，使用独立随机凭据、RSA 开发签名和私有目录。账号仅拥有新库权限及 `performance_schema.data_locks`、`data_lock_waits`、`threads` 三表 SELECT，没有 PROCESS 或旧库授权。

两处 fixture 只追加精确新 URL/账号配对，`IdentityLockFixture` 同时按账号校验实际 `DATABASE()`；既有 L1/L2/第三轮/R08 条件与断言保留。L2 执行者在组合 IT 前独立复审两文件的 7 增 2 删及 SHA-256，无阻断：

- `IdentityLockFixture.java`：`DE100E4EEA1378DC54EEA31F45AA1035B5F00F019A2205E2CBB4922D082E32D3`
- `R08CenterFixture.java`：`60B246E1BF4B697D7AE830EE7BDFD3CCB8DD689ED5980C08A2DF0F0726DAE890`

本树自行构建报表 `installDist` 并明确配置本树 `renderer-home`；没有复用旧树报表产物或弱化 R08 断言。`docs/sku.txt` 是原授权样本的本机副本，字体按既有固定 SHA 校验。`L1_LOCK_EVIDENCE` 和 `L2_LOCK_EVIDENCE` 都指向本轮 `.local/wave4-20260921/lock-evidence`，保存新 P_S 锁等待 JSON 及 C04 最终持久状态。

13316、19321 在开始时均无监听，未启动、连接或重置；原 R05/R06/R09 数据与恢复断点、AVD/M71、旧 B/C/D 任务、HTTP/代理及现网保持。配置、数据库、密钥、样本、日志和原始证据均在 ignored 路径，不进入 Git。

## 本轮组合结果

首次组合命令全部通过，耗时 1 分 51 秒，13 个 Gradle task 实际执行；没有重复运行扩大测试计数。原日志 `.local/wave4-20260921/server-first.log`，原始 XML 已复制到 `server-first-results/test` 与 `server-first-results/integrationTest`，逐套件数量和 XML SHA-256 固定在 `server-first-summary.json`。

| 本轮新跑 | 实际结果 |
| --- | --- |
| 后端单元 | 7 类、43 项通过，0 失败/错误/跳过 |
| L1 `IdentityLock*` | 5 类、26 项通过 |
| L2 `IdentitySet*` | 4 类、9 项通过 |
| E1 `TemporarySourceEvidence*` | 3 类、26 项通过 |
| `PublicationConcurrencyIT` | 2 项通过 |
| `R08AuthorizationRaceIT` | 6 项通过 |
| 集成汇总 | 14 类、69 项通过，0 失败/错误/跳过 |
| 构建 | `compileJava`、`compileTestJava`、`bootJar` 和报表 `installDist` 成功 |

JAR 为本树 `server/build/libs/acrylic-outbound-server-0.1.0.jar`，SHA-256 `6F41CD4B2231499AE7020C714B8BB6AD98AA7110926F8AAE37326EB088774DF6`。测试库为本轮空库迁移至 V33，实际 93 表；这不是旧库升级或灾后恢复验收。不重跑依赖真实 Android 冻结输入、另建编号升级库、完整 R09 恢复或无关设备/报表/Web 套件。

新锁目录共 25 份 JSON：18 份锁等待/事务证据，7 份 C04 最终状态，逐件摘要在 `lock-manifest.json`。C04 七组均有实际 G 等待；同键交接回放两端提交并返回 OK，其余原人/目标/批准人变化使等待方回滚，分别返回 PC/CONTINUATION 身份变化码。七份最终状态均 `dispatchCount=0`，保留 PC/REVIEW 占用；续作保持 REQUESTED 且无新 assignment。C03 refresh 与高低 UUID 场景保留等待 U 而未取得后续 W/D/H 的证据。这里仅声明本轮所跑场景，不推导完整 H/所有 DML 锁图已证明。

已完成 `npm ci --ignore-scripts --no-audit --no-fund`；`web` 范围 `npm run check:contract` 新跑通过，OpenAPI 3.1 共 116 paths。日志为 `.local/wave4-20260921/npm-ci.log`、`contract-first.log`。C0 本轮未改，未重复其离线测试，不把第三轮 153 项计成本轮结果。

## 失败记录与环境收尾

本轮新环境初始化、组合测试、依赖安装和契约检查没有失败或跳过；编译只出现已有 deprecated API 和 JVM class-sharing 提示。作者 L2 的历史环境及 R08 六项失败仍按原指南保留，没有改写成首次成功。

只读准备时曾误用不存在的 `backend`/`scripts` 路径、Windows 不支持的通配路径，以及一次 PowerShell `foreach` 后直接管道表达式；均修正后完成核对，未触及业务数据，不计作业务测试失败。Git 对两 fixture 的 LF/CRLF 提示不属于差异检查失败，已审原字节 SHA 保留。

2026-09-21 09:26（Asia/Shanghai）再次核对 19322 的 datadir 后通过 `mysqladmin shutdown` 正常停止自己的实例，退出码 0；`shutdown-status.json` 确认 13316/19321/19322 无监听、没有本树 Java/MySQL 进程、数据目录仍在。其它 MySQL 进程未操作。本轮库、账号、签名、私有文件和证据保持；没有启动网络 HTTP、Worker 守护、设备或现网服务。

最终 10 份交付/整合文档严格 UTF-8/NUL 与 72 处本地链接检查通过，0 缺失；主调度五份管理文件相对起点逐项 Git blob 不变。两 fixture 仍与复审 SHA 相同，其余作者文件与已审来源一致；差异检查通过。完整 44 文件的 Git blob/原字节摘要保存为 `final-source-manifest.json`，不把本机证据放入 Git。

## 实施边界与下一动作

L2 只收口本次精确身份集合与 C04；完整 H 批量锁图、C09 LINK、C15 全量反向包序/批量 DML、真实 HTTP/设备/长时负载及 R09 fence 仍待后续。PC review/resume 和续作原人变化三例是受控字段变化夹具，不代表新改派业务已实现。

E2 的 nullable order/共同占用、完整原机证据及非空迁移，C1 的生产命令台账/不可变原回执/签名原子，均只交付设计。未来 C1 必须区分当前身份/同仓只读恢复与新命令写权检查；不能直接复用当前 `MutationAccess.lock` 先做写鉴权的顺序实现原键恢复。`RecoveryStock.reserve` 的旧 null-order 消费风险须在引入 E2 新数据的同一批修复，不写成当前 V33 已有生产故障。

后续由主调度指定唯一协议/Flyway/共同 K 执行者，核对最新迁移后再分配编号；不能各自创建竞争迁移，也不直接采用 C0 提案常量作为生产协议。普通有权人员正常直接确认目标保持，不新增普通确认必填理由；TEMP 尚无确认/关联/拒绝处置/成功放行入口，`localDeletionAllowed=false` 保持。

Flyway V33、契约 0.29/116 路径、Android code20/Room17 不变。本轮只在整合短分支提交推送；最终固定 SHA 交独立复审，再等明确 development 快进派工。不合 main、不打标签、不发布或部署。
