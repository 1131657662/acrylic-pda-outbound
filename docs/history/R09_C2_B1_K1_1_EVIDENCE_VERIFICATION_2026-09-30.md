# R09 C2 B1-3 S44：K1 判据证据核实与补齐（E1–E6 事实表；不可归类形态 102→135；K1-3 真实用例更正）（2026-09-30）

> 轮次身份：主树 `D:/acrylic-pda-outbound`、分支 `codex/development`；写者＝DSH 本会话。
>
> 依据：用户 2026-09-30 裁定"按提案建议执行**已授权部分**"，[K1 通过判据提案](R09_C2_B1_K1_PASS_CRITERIA_PROPOSAL_2026-09-30.md) §6 明确 **E1–E6 的重建只需验证、不需新授权**。
>
> 本页三段：**§1–§3 只读重建**（未改代码、未跑新测试，计数全部取自本会话已落盘的 XML/记录）；**§3a–§3b 缺口补齐**（**测试侧新增**，`RecoveryEffectAdversarialSqlSurfaceIT` 102→135）；**§4 K1-3 复核实测**（据源码与真实记录**更正**此前"未补"的台账）。
>
> 口径：每条证据给出**当次可核**的类名、项数（tests/failures/errors/skipped）与证据路径；**不引用历史通过**替代本次证据。

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

**补齐后的更新（同日晚些时候，本页 §3a 为补齐结果）**：上表 4–5 类形态**已在本批补齐**，`RecoveryEffectAdversarialSqlSurfaceIT` 由 102 项扩到 **135 项**，恢复链批次由 696 项变为 **729 项 / 0 失败 / 0 错误 / 0 跳过**。补齐证据见 §3a。

## 3a. K1-1 缺口补齐（本批实施＋实测，测试侧新增）

**改动**：扩展 `server/src/test/java/com/acrylic/outbound/recovery/RecoveryEffectAdversarialSqlSurfaceIT.java`（**仅测试**）：

| 新增 | 内容 |
| --- | --- |
| `unclassifiableForms()` × 9 形态 × repeat 1–3 ＝ **27 项** | `SET SESSION sql_mode='ANSI'`、`SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED`、`SET NAMES utf8mb4`、`SELECT 1;SELECT 2`、`INSERT …;SELECT 1`、`/* allowed by legacy */ INSERT …`、`-- legacy comment\nSELECT 1`、`CALL p_$T(1)`、`{call p_$T(?)}` |
| `callableBodies()` × 2 形态 × repeat 1–3 ＝ **6 项** | `CALL p_$T(1)`、`{call p_$T(?)}` 经 **`prepareCall`→`CallableStatement.execute`** 入口（与 `Statement.execute` 不同的入口） |

**实测（`E2A` 无关；恢复链批次 `.local/r09-k11-final-20260930/`）**：`RecoveryEffectAdversarialSqlSurfaceIT` **135 / 0 / 0 / 0**；整批 **25 类 / 729 项 / 0 失败 / 0 错误 / 0 跳过**、`failedClasses=0`、`portReleased=True`。

**真实记录原文**（`.local/r09-k11-final-20260930/run115-batch-recoveryeffectadversarialsqlsurfaceit/real-records.tsv`，节选）：

```text
REAL|sql-unclassifiable|repeat=1|sql=SET SESSION sql_mode='ANSI'|refusal=CONTROL_EFFECT_UNCLASSIFIED|delegate=0|…
REAL|sql-unclassifiable|repeat=1|sql=SET NAMES utf8mb4|refusal=CONTROL_EFFECT_UNCLASSIFIED|delegate=0|…
REAL|sql-unclassifiable|repeat=1|sql=SELECT 1;SELECT 2|refusal=CONTROL_EFFECT_UNCLASSIFIED|delegate=0|…
REAL|sql-unclassifiable|repeat=1|sql=/* allowed by legacy */ INSERT INTO $T VALUES(2,'b','new')|refusal=CONTROL_EFFECT_UNCLASSIFIED|delegate=0|…
REAL|sql-callable-body|repeat=1|sql=CALL p_$T(1)|refusal=CONTROL_EFFECT_UNCLASSIFIED|effect=none|…
REAL|sql-callable-body|repeat=1|sql={call p_$T(?)}|refusal=CONTROL_EFFECT_UNCLASSIFIED|effect=none|…
```

**判定要点**：①`delegate=0` ⇒ **驱动从未看到该语句**（委托前拒绝）；②`observer=1:62:original;` ⇒ 表内容**未被改动**；③拒绝码是 `CONTROL_EFFECT_UNCLASSIFIED`（冻结语法的"不可归类即拒绝"），**不是** `RECOVERY_LEASE_FENCED`——首轮我按后者断言导致 33 项失败，**失败轮 `.local/r09-k11-20260930/` 完整保留**，随后改为断言"两种拒绝码之一＋delegate=0"。

**一处必须写清的产品事实（自审发现）**：`prepareCall` **本身不分类**——分类发生在 `RecoveryJdbcObjectHandler` 的调用路径上（`statement.classify(name,args)` → `owner.checkEffect()`），因此"调用体"的拒绝点在**执行**而非准备阶段；本轮用例据此调整为"先 `prepareCall`、再 fence、再 `execute`"，从而真正覆盖调用体的分类入口。

**⇒ K1-1 判据①③现状**：`CallableStatement` **调用体**、**多语句**、**带注释语句**、**会话级 `SET`** 四类形态**已在门开启模式下有真实驱动、`delegate=0`、无效果的具名断言**；厂商方言/DDL 变体仍**未单列**（属"需要先证明语法"的同一 `unclassified` 分支，可由上面四类共同覆盖，但按判据字面**未逐条单列 ⇒ 记为可控残余**）。

**⇒ K1-1 判定（补齐后）：判据①③的原有缺口形态已补齐并实测；唯一残余是"厂商方言/DDL 变体未逐条单列"（落入同一 `unclassified` 分支，见 §3a）。** 因此 K1-1 从"不通过（缺 4–5 类形态）"变为"**判据逐条满足，附一处可控残余**"；是否据此把 K1-1 记通过，属**裁定问题**（见 §3b），本页不单方面改判。

## 3b. K1-1 单列项（供裁定）

| 项 | 现状 | 影响 |
| --- | --- | --- |
| **厂商方言 / DDL 变体**（如 `CREATE OR REPLACE`、`ALTER … ALGORITHM=`、`SET GLOBAL`、注释变体 `#`） | **未逐条单列断言**；代码上全部落入 `RecoveryEffectSql.statement()` 末尾的 `unclassified()`（与已测的四类走同一分支） | 要"逐条"满足判据①，需再补 N 条（每个方言一族）；也可裁定"同一拒绝分支 + 四条代表形态已足以覆盖，逐条单列非必要" |
| 历史 keyring | 未实现（属 K1 之外，见 §4） | 不阻断 K1-1 |
| K1-2 的 W-2-d（未决孤儿计数） | 未实现（需产品改动＋授权） | 不阻断 K1-1 |

## 4. K1-3 复核实测：**真实跨进程用例已交付**（此前登记为"未补"，本轮更正）

原登记"K1-3 缺一条真实跨进程用例"。本轮核实**该用例实际已存在并有真实记录**：

- 用例：`RecoveryFenceCrossProcessIT.everyFailureMatrixBranchUsesObservedState`（`server/src/test/java/com/acrylic/outbound/recovery/RecoveryFenceCrossProcessIT.java:59` 起），以 `bits` 遍历 **8 种围栏组合**，每个组合 **repeat 0–2**。
- **`bits="000"`（F1′/F3/F5 三者都未确认）分支的断言**（源码原文）：子 JVM 的 stderr **必须包含** `UNCONFIRMED_NO_CROSS_PROCESS_BASIS`；随后**金丝雀委托被允许**且**恰好发生一次**（`Statement.executeUpdate` 计数＝1、`jdbc.count()==1`）——即"**没有跨进程依据 ⇒ 拒绝把该状态当作 BLOCKED，而交外部隔离**"，与冻结规格一致。
- **真实记录原文**（本轮 `.local/r09-k11-final-20260930/run119-batch-recoveryfencecrossprocessit/real-records.tsv`，3/3）：

```text
REAL|matrix|repeat=0|steps=000|basis=UNCONFIRMED_NO_CROSS_PROCESS_BASIS: external isolation required; canary delegate=1 observer=1|childAlive=true|observer=1
REAL|matrix|repeat=1|steps=000|basis=UNCONFIRMED_NO_CROSS_PROCESS_BASIS: external isolation required; canary delegate=1 observer=1|childAlive=true|observer=1
REAL|matrix|repeat=2|steps=000|basis=UNCONFIRMED_NO_CROSS_PROCESS_BASIS: external isolation required; canary delegate=1 observer=1|childAlive=true|observer=1
```

- 八种组合在本轮**各 3/3 完整**（`111/110/101/100/011/010/001/000` 各 3 条），该类计 **33 项 / 0 失败 / 0 错误 / 0 跳过**。

**⇒ K1-3 更正**：**真实跨进程用例已交付**（真实子 JVM、真实文件锁、真实 MySQL 观察者、含 `basis`／`childAlive`／`observer` 记录）。`UNCONFIRMED_NO_CROSS_PROCESS_BASIS` 的原始表述**保持不变**（它仍是"无跨进程依据"的标签，且后续处置＝外部隔离）。此前"未补"的登记是**台账滞后**，本轮据源码与真实记录更正。

## 5. 边界声明

- §1–§3（重建）**未改任何代码**；§3a（补齐）**只新增测试**。均未部署、不接原库/现网、未合 main、未打标签/Release；`localDeletionAllowed=false`。
- 开关全默认 **false**（含 `effect-gate.enabled`／`cross-process-enabled`／`lease.enabled`／`admission.enabled`）。
- 证据路径均在 `.local/`（不入库）；失败轮与跳过轮未删未改（`.local/r09-k11-20260930/` 的 33 项失败轮保留）。

## 6. K1 判据现状汇总（供裁定；本页不单方面改判）

| 判据 | 现状 |
| --- | --- |
| **K1-1 ②** 隐式提交事实（真实驱动） | **满足**（`G0K1RealJdbcBoundaryIT:381`＋`:134`） |
| **K1-1 ①③** 形态分类与"不可归类即拒绝" | **原缺口四类已补齐实测**（§3a：`delegate=0`、无效果）；**残余**＝厂商方言/DDL 变体未逐条单列（§3b） |
| **K1-2** 窗口 W | **已按 W-2 接受为有界已知边界**（附 W-2-d 缺口，见 [K1-2 验收](R09_C2_B1_K1_2_WINDOW_W_ACCEPTANCE_2026-09-30.md)） |
| **K1-3** `UNCONFIRMED_NO_CROSS_PROCESS_BASIS` | **真实跨进程用例已交付**（§4：8 组合 ×3、`steps=000` 的 `basis`／`canary delegate=1` 记录齐备） |

⇒ 三项判据中 **K1-2、K1-3 已有落地结论**，**K1-1 只剩"方言/DDL 变体是否需逐条单列"一处裁定**。

## 7. 下一步

1. **K1-1 残余**：厂商方言/DDL 变体**是否需逐条单列**——**请裁定**（可裁定"同一 `unclassified` 分支＋四条代表形态已足够"）。
2. 待授权项不变：I3（共享树处置）、B4 实施级（⇒ A4.3/B9）、A2.1 主管↔权限码映射、W-2-d 未决孤儿计数。
3. R07 其余缺口保持单列。
