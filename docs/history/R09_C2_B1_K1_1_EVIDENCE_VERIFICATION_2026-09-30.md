# R09 C2 B1-3 S44：K1-1 证据核实（E1–E6 事实表重建；真实驱动形态缺口逐条定位）（2026-09-30）

> 轮次身份：主树 `D:/acrylic-pda-outbound`、分支 `codex/development`；写者＝DSH 本会话。
>
> 依据：用户 2026-09-30 裁定"按提案建议执行**已授权部分**"，[K1 通过判据提案](R09_C2_B1_K1_PASS_CRITERIA_PROPOSAL_2026-09-30.md) §6 明确 **E1–E6 的重建只需验证、不需新授权**。本批**只读＋复用本会话实测证据**，**未改代码、未跑新测试**（所有计数均取自本会话已落盘的 XML/记录）。
>
> 口径：每条证据给出**当次可核**的类名、项数（tests/failures/errors/skipped）与证据路径。**不引用历史通过**替代本次证据（下述计数全部来自 2026-09-30 本会话的 run）。

## 1. E1–E6 事实表（重建结果）

| # | 证据包 | 类 | 实测 | 证据路径 | 判定 |
| --- | --- | --- | --- | --- | --- |
| **E1** | 真实跨进程文件锁语义 | `G0K1RealFileLockIT` | **10 / 0 / 0 / 0** | `.local/r09-a03-20260930-pinned-b/run123-batch-g0k1realfilelockit/` | 成立（`REAL\|` 记录含子进程 PID/退出码/时间戳，见 S34 引用） |
| **E2** | 真实产品获取路径＋原生身份 | `G0K1WriterLeaseIdentityRealIT` | **5 / 0 / 0 / 0** | `…/run124-batch-g0k1writerleaseidentityrealit/` | 成立（`CONTROL_WRITER_LOCK_REPLACED`／`CONTROL_FILE_MISSING`，3/3） |
| **E3** | 真实 JDBC 边界 | `G0K1RealJdbcBoundaryIT` | **13 / 0 / 0 / 0** | `…/run125-batch-g0k1realjdbcboundaryit/` | 成立 |
| **E4** | 真实驱动 SQL 形态与拒绝 | `RecoveryEffectAdversarial*` 全家族（Batch/Callable/Cleanup/DefaultOff/Driver/Fence/Handles/Lob/Metadata/Pool/SqlSurface/View） | **合计 501 / 0 / 0 / 0**（逐类见 §2） | `.local/r09-a03-20260930-pinned-b/run105…run116/` | **部分成立**：真实驱动＋真实 MySQL 的逐形态断言存在，但**部分形态缺证据**（§3） |
| **E5** | 效果门接线真实路径 | `RecoveryEffectGateWiringRealIT`（＋同族 `RecoveryEffectSqlIT`／`CommitIT`／`CursorIT`／`BatchIT`） | `GateWiringRealIT` **1 / 0 / 0 / 0**（`…/r09-c2-b1-3-s11-20260929-a/run1-s11-real-authority/TEST-…RecoveryEffectGateWiringRealIT.xml`）；`SqlIT` 11、`CommitIT` 14、`CursorIT` 7、`BatchIT` 3（各 0/0/0，`…/run101–104`） | 同左 | 成立：`EffectRealEnvironment.requireEnvironment()` 强制 `jdbc:mysql://127.0.0.1:19xxx`，测试用 `DriverManagerDataSource(System.getenv("OUTBOUND_DB_URL"),…)` 走**真实 MySQL**；`registry.admit(...)` 走**生产注册表** |
| **E6** | 效果门默认关闭 | `RecoveryEffectAdversarialDefaultOffIT` | **15 / 0 / 0 / 0** | `…/run108-batch-recoveryeffectadversarialdefaultoffit/` | 成立（默认关闭下拒绝行为有断言） |

**证据强度声明（提案 §2 第 4 条）**：以上全部为**①真实驱动/真实跨进程实测**与**②模型/测试侧验证**的分类；没有任何一条以合成身份、MockMvc 或服务 DTO 充当 K1 依据。E1–E3、E5、E6 的说明已含"真实"标签；E4 见 §3 的缺口。

## 2. E4 明细（逐类计数，均 0 失败 / 0 错误 / 0 跳过）

| 类 | tests |
| --- | --- |
| `RecoveryEffectAdversarialBatchIT` | 12 |
| `RecoveryEffectAdversarialCallableIT` | 45 |
| `RecoveryEffectAdversarialCleanupIT` | 36 |
| `RecoveryEffectAdversarialDefaultOffIT` | 15 |
| `RecoveryEffectAdversarialDriverIT` | 21 |
| `RecoveryEffectAdversarialFenceIT` | 15 |
| `RecoveryEffectAdversarialHandlesIT` | 183 |
| `RecoveryEffectAdversarialLobIT` | 48 |
| `RecoveryEffectAdversarialMetadataIT` | 15 |
| `RecoveryEffectAdversarialPoolIT` | 3 |
| `RecoveryEffectAdversarialSqlSurfaceIT` | 102 |
| `RecoveryEffectAdversarialViewIT` | 6 |
| **合计** | **501** |

（连同 E5 的 `BatchIT` 3＋`CommitIT` 14＋`CursorIT` 7＋`SqlIT` 11＝35，恢复链 25 类总计 **696** 项，与批次汇总一致。）

## 3. K1-1 判据逐条核对：**仍有真实驱动形态缺口 ⇒ K1-1 不记通过**

按提案 §3 的三条判据逐条对照：

| 判据 | 结论 | 依据 |
| --- | --- | --- |
| ①形态分类：`CallableStatement` 调用体、多语句、带注释语句、会话级 `SET`、厂商方言、DDL 变体、`addBatch/executeBatch/executeLargeBatch/clearBatch`、无参 `PreparedStatement` 依赖 prepare 记忆、手动事务内原始提交优先 —— **每形态须有真实驱动的具名断言** | **不满足**（多数形态有，四类无**门开启模式**下的证据） | 见下表 |
| ②隐式提交事实：`autoCommit=false` 下 DDL 隐式提交且其后 `rollback()` 无法撤销，须由**真实驱动调用**观测 | **满足** | `G0K1RealJdbcBoundaryIT:381` 的 `record("DRIVER","ddl-implicit-commit","Statement.execute(\"CREATE TABLE …\")",… "observation_after_ddl=" + after + " observation_after_later_rollback=" + afterRollback)`；同类 `:134` 另有 `setAutoCommitTrueImplicitlyCommitsAPendingTransaction`。两例的观察值来自**真实 MySQL** |
| ③不可归类即拒绝：真实驱动下无法归类的语句在**委托前**被拒绝 | **部分满足**（默认关闭模式有证据；门开启模式仅对 SQL 黑名单形态有证据） | `RecoveryEffectAdversarialDefaultOffIT`（`path=comment/multi/session/autoCommit/ddl` × repeat 1–3）在**默认关闭**下断言**直连可执行**（`f.authority.checks` 为 0、观察值含真实写入），即"门关时放行"的基线，**不是**拒绝证据；`SqlSurfaceIT`（102 例）逐条断言 `fenced <SQL> deny=<bool>`，含 `SET AUTOCOMMIT=1`／`CREATE/ALTER/DROP/TRUNCATE`／`COMMIT/BEGIN/START TRANSACTION` 等**拒绝** |

**缺证据的真实驱动形态（逐条定位）**：

| 形态 | 现状 | 依据 |
| --- | --- | --- |
| `CallableStatement` **调用体**（`{call p_x(?)}` 的语句文本分类） | **缺**：`CallableIT` 的 45 项实际是**参数 setter**（`Blob/Clob/NClob/SQLXML/Array/Ref/Object` × named/unnamed × repeat 1–3），**不是** 调用体分类；`SqlSurfaceIT` 的 102 例中**没有** `CALL` 形态（其名单为 SELECT/SHOW/ROLLBACK/SELECT FROM T/DESC/EXPLAIN/INSERT/REPLACE/MERGE/DELETE/UPDATE/CREATE/ALTER/DROP/TRUNCATE/COMMIT/BEGIN/START TRANSACTION/SET AUTOCOMMIT） | 本批逐名核对 `.local/r09-a03-20260930-pinned-b/run106…/TEST-*.xml` 与 `run115…/TEST-*.xml` |
| **多语句**（`allowMultiQueries`）在**门开启**下的拒绝 | **缺**：只有默认关闭下的直连基线（`DefaultOffIT:24`），没有门开启的拒绝断言 | 同上 |
| **带注释语句**在**门开启**下的拒绝 | **缺**：只有默认关闭下的直连基线（`DefaultOffIT:23`） | 同上 |
| **会话级 `SET`**（如 `SET SESSION sql_mode`） | **缺**（门开启/关闭两种模式都没有 `SET SESSION …`；`SqlSurfaceIT` 只覆盖 `SET AUTOCOMMIT=1/ =1`） | 同上 |
| 厂商方言、DDL 变体 | **缺**（无区分性用例） | 同上 |

**⇒ K1-1 判定：仍不通过。** 说明：**这不是"规范/模型没做"**（分类与 fail-closed 规范已在产品与模型内），而是**判据①/③要求的"真实驱动、门开启模式下逐形态具名断言"缺 4–5 类形态**。按提案 §2 第 5 条与 §3 的判定规则，缺一条即**不得记通过**。

**补齐成本（供后续裁定）**：为这 4–5 类形态各加一条**门开启模式**的真实驱动用例（在既有 `RecoveryEffectAdversarial*` 家族内扩展即可，复用 `EffectRealEnvironment` 与 `EffectTestAuthority`），属**测试侧新增**、不改产品代码。这是"补做到通过"的**剩余最小集**。

## 4. 与 K1-2／K1-3 的关系（不因本批改变）

- **K1-2**：已按 W-2 记"**已接受为有界已知边界（附 W-2-d 缺口）**"，见 [K1-2 验收页](R09_C2_B1_K1_2_WINDOW_W_ACCEPTANCE_2026-09-30.md)。
- **K1-3**：`UNCONFIRMED_NO_CROSS_PROCESS_BASIS` **原样保留**；真实跨进程用例**未补**（本批未做）。
- 因此 **K1 整体仍未通过**；本批把 K1-1 的剩余缺口从"待核实"收敛为**可枚举的 4–5 类形态**。

## 5. 边界声明

- 本批**未改任何代码**；未部署、不接原库/现网、未合 main、未打标签/Release；`localDeletionAllowed=false`。
- 开关全默认 **false**（含 `effect-gate.enabled`／`cross-process-enabled`／`lease.enabled`／`admission.enabled`）。
- 证据路径均在 `.local/`（不入库）；失败轮与跳过轮未删未改。

## 6. 下一步

1. **补 K1-1 的 4–5 类形态**（测试侧，不需新授权）：门开启模式下 `CALL` 调用体、多语句、带注释语句、`SET SESSION …`、方言/DDL 变体各一条真实驱动用例。
2. **K1-3**：补一条真实跨进程用例（隔离测试，不需产品改动）。
3. 待授权项不变：I3（共享树处置）、B4 实施级（⇒ A4.3/B9）、A2.1 主管↔权限码映射、W-2-d 未决孤儿计数。
