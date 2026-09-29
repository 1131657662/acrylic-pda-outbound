# R09 C2 B1-3 S8：入口 IT 重基到请求根语义（2026-09-29）

> 轮次身份：主树 `D:/acrylic-pda-outbound`、分支 `codex/development`；写者＝DSH 本会话；**同一会话实施＋自审＋运行，非独立第三方验收**。授权：用户 2026-09-29"同意"＝**④入口 IT 重基**。边界：仍**不开任何生产开关**，不接生产、不部署、不合 main。起点 HEAD `d414273`（S7 收口）。

## 1. 问题

`RecoveryAdmissionEntryChecks` 是三处入口验证（`E2aRecoveryAdmissionEntryIT`、`C1EntryRegression` 以及本轮的 `RecoveryAdmissionEntryRebaseIT`）共用的断言语料。它写于**旧适配器**时代，假定：主数据源是 `RecoveryAdmittedDataSource`；**任意线程**都可以直接借用（旧模型没有操作根）；关闭意图会立刻切断**同一条连接级**入口。B1-3 之后这三点全部不成立：主源是 `RecoveryRequestAdmittedDataSource`，借用必须解析到**活的操作根**，关闭意图只拒绝**新根**而不切断已接纳根的保留工作（设计 §6.4）。因此这三处验证在 B1-3 之后要么起不来、要么断言的是错语义——即看板长期单列的"入口 IT 重基"。

## 2. 实施（全部为测试侧与工具侧，无产品代码改动）

| 文件 | 改动 |
| --- | --- |
| `RecoveryAdmissionEntryChecks` | 三处检查改为**在入口接纳的操作根内**运行：`admit(context)` 走 `RecoveryOperationRegistry.admit`（有界等待的接纳段）并 `bindDispatch` 当前线程，`retire(root)` 收尾；主源断言改 `RecoveryRequestAdmittedDataSource`；`wiring` 新增**无根拒绝**证据（另起无线程借用 ⇒ `RECOVERY_OPERATION_REQUIRED`）；`fenceAndClosingIntent` 改为区分"**已接纳根继续服务**（再借用/再写＝allowed）"与"**新根被拒**（`CONTROL_CLOSING_INTENT`）"，并用独立写者仍被挡（`CONTROL_WRITERS_ACTIVE`）|
| `RecoveryEntryLostCommitConfiguration` | 故障输运（丢提交回执）改到**请求根数据源之下**：`RecoveryRequestAdmittedDataSource(new LostCommitDataSource(raw), registry)`；并补齐生产的启动覆盖（`recoveryOperationRegistry`／`recoveryInitializationWindow`／`recoveryStartupGuard`，前两者为 **static** 工厂，避免守卫的 `dependsOn=窗口` 把配置类自己卷成环）|
| `RecoveryAdmissionEntryRebaseIT`（新） | 可在**隔离实例**上运行的第三处调用者：迁移 → 造独立原稿（Python 写者）→ 依次启动 live/unknown/lost 三个上下文跑同一批检查 → 落证据 |
| `C1MainProfile`／`E2aCrossSourceHttpIT` | 启动参数补 `--spring.session.jdbc.cleanup-cron=-`：S5 守卫要求禁用框架会话过期写者，否则**这两个实验舱连启动都会被拒**（本轮发现的重基缺口）|
| `tools/r09-c2-b1-3-s3-session.ps1` | 隔离实例账号增补 `GRANT PROCESS ON *.*`（入口检查要读 `information_schema.INNODB_TRX`）；`-TestClass` 参数化；导出 `S3_INSTANCE_DIR`（夹具的恢复根＝该实例真实 datadir）与 `PYTHONUTF8/PYTHONIOENCODING=utf-8`（独立 Python 写者输出编码）|

## 3. 验证

### 3.1 一级

- 全量（`run10-unit-full`，不带 `--tests` 白名单）：**129 类 / 2818 项 / 0 失败 / 0 错误 / 1 既有无关 skip**（含重基后的入口语料与新增 IT 的编译）。
- 说明：`C1MainProfile`／`E2aCrossSourceHttpIT` 只做编译校验——它们是实验舱装配类，本环境无法运行（见 §5）。

### 3.2 二级 现场：入口重基（`run9-s8-entry-rebase`，全新实例 19468／应用 19458，1 用例 0 失败）

| 检查 | 现场证据 |
| --- | --- |
| 装配与无根拒绝 | `wiring: primary=RecoveryRequestAdmittedDataSource migration=recoveryMigrationDataSource JdbcTemplate/transactionManager=admitted unwrap=rejected rootRequired=RECOVERY_OPERATION_REQUIRED` |
| 根内往返 | `roundTrip: committed=1 rolledBack=1 after explicit rollback` |
| 关闭竞争 | `closeRace: closeWaitedMillis=2500 execution=ok innodbTrx=0` |
| 关闭意图 × 保留工作 | `fence: independentWriterBlocked=CONTROL_WRITERS_ACTIVE admittedRootStillServes=allowed newRootWithPendingIntent=CONTROL_CLOSING_INTENT` |
| 提交期连接丢失 | `observed=TransactionSystemException: JDBC rollback failed`、`counterAfterKilledTransaction=1`、`subsequentEntryWrite=CONTROL_WRITERS_ACTIVE`、`innodbTrx=0` |
| 丢提交回执 | `observed=TransactionSystemException: JDBC rollback failed`、`committedCounter=2`（真实提交发生、仅回执丢失）、`subsequentEntryWrite=CONTROL_WRITERS_ACTIVE` |
| 夹具身份 | `fixtureBase=…\.local\mysql19468-s3session restoreData=…\mysql19468-s3session\data instanceBound=true`（恢复根＝该隔离实例真实 datadir，非占位目录）|

三个上下文（live／unknown／lost）**在同一轮内**全部跑完，收尾 `innodbTrx=0`，并写入 `real-records.tsv` 的 `S8|REAL|entry-rebase` 记录。

### 3.3 产品代码回归

本轮**未改任何产品代码**（`git diff` 仅测试与工具），故恢复链 25 类批次仍以 S7 轮同产品 HEAD 的 `it-batch-25`（25 类 / 696 项 / 0 失败 / 0 跳过，实例 19467）为准，不重复消耗证据。

## 4. 语义澄清：旧检查钉住的是旧模型

- 旧 `fenceAndClosingIntent` 断言"关闭意图生效后，入口的**下一次借用**即 `CONTROL_CLOSING_INTENT`"。新模型下这是**错的**：根一旦被接纳，其后继借用在关闭意图下仍然有效（设计 §6.4 的"不切断保留工作"）。重基后固化为两条：**保留根继续服务＝allowed**、**新根＝`CONTROL_CLOSING_INTENT`**。
- 旧检查隐含"任意线程可借用"。新模型下这是**错的**：无根线程必须被拒。重基后把它变成显式证据 `rootRequired=RECOVERY_OPERATION_REQUIRED`。

## 5. 未做与边界

- **C1/E2a 实验舱本轮未运行**（`E2A_CROSS_VERIFY`、`C1MainProfile` 需要各自专属环境：19330/19434 实例、binlog 与 V34 种子、签名密钥等）。重基已把它们需要的启动参数与故障装配补齐，但"实验舱重跑并出具证据"仍留在未验清单（IT-DISCOVERY／IT-EVIDENCE-AUDIT 继续单列）。
- 未做（后续）：阶段 0 安全前置（设计第 8–12 行：`leaseRoot` 三方一致、P5 进程记录与锁、孤儿租约、FENCED 残留；需 `outbound.recovery.lease-root`／锚点秘密／`closedAuthority` 配置与 provision 协议）、效果门接线、§5.6 退役协议、完整接纳门 S0–S4 与 P5 前后探针/租约发布。
- 未验：PDA 客户端对 503 envelope 的消费、并发拒绝计数、跨进程并发接纳、Linux/其他文件系统、断电/硬复位、现场组合。
- 仍是"只接线、不开开关"：`admission.enabled`、`effect-gate.enabled`、跨进程开关默认 false；未接生产、未部署、未合 main；K1 仍未通过。

## 6. 逐轮失败保留（未删）

| 轮 | 现象 | 处置 |
| --- | --- | --- |
| `run1-s8-entry-rebase` | Python 原稿夹具拒绝：`ValueError: Actual isolated database/storage restore roots required` | 夹具根改到隔离实例目录下（父目录即含真实 `data/`），并导出 `S3_INSTANCE_DIR` |
| `run2` | `BadSqlGrammarException: SELECT COUNT(*) FROM information_schema.INNODB_TRX` | 隔离账号增补 `GRANT PROCESS ON *.*`（并写进 runner 的 init.sql 供后续实例复用） |
| `run3` | `java.nio.charset.MalformedInputException`（独立 Python 写者输出编码） | runner 导出 `PYTHONUTF8=1`／`PYTHONIOENCODING=utf-8` |
| `run4` | 夹具根已存在（`exist_ok=False`） | 夹具目录加时间戳，逐轮独立、失败轮保留 |
| `run5` | 丢提交上下文主源仍是 `HikariDataSource` | `@TestConfiguration` 须作为**显式 source** 安装（与 E2a/C1 一致） |
| `run6` | 该上下文启动失败：`JobRecoveryFence` 构造期读库被拒 | 故障入口配置补齐生产启动覆盖（初始化窗口＋守卫） |
| `run7` | `CONTROL_STARTUP_WINDOW_MISSING`（守卫按固定 bean 名找窗口） | 故障配置的窗口/注册表改用生产 bean 名 |
| `run8` | `BeanCurrentlyInCreationException`（守卫给所有应用 Bean 加 `dependsOn=窗口`，配置类自身被卷入） | 窗口与注册表工厂方法改 **static** |
| `run9` | **绿**（1 用例，三个上下文全过） | 本轮二级现场证据 |

## 7. 证据位置（不入库，按约定保留）

`.local/r09-c2-b1-3-s8-20260929-a/`：`run1..run8-s8-entry-rebase`（失败轮，含 XML 与 `gradle.log`）、`run9-s8-entry-rebase`（含 `entry-rebase.txt`、`admission-entry-checks.txt`、`admission-entry-close-race.txt`、`admission-entry-fence.txt`、`admission-entry-unknown-commit-checks.txt`、`admission-entry-unknown-commit.txt`、`admission-entry-lost-commit.txt`、`real-records.tsv`）、`run10-unit-full`；夹具目录（`entry-*-<stamp>`）保留在 `.local/mysql19468-s3session/` 下，实例 19468 已正常 shutdown、端口释放，datadir 保留。

## 8. 下一步

① **阶段 0 安全前置（设计第 8–12 行）**——需新增 `outbound.recovery.lease-root`／锚点秘密／`closedAuthority` 配置与 provision 协议（独立批次、需独立授权）；② C1/E2a 实验舱重跑（环境相关，需协调）；③ 效果门接线与 §5.6 退役协议。均不改默认开关。
