# R09 C2-G0 K1 测试模型最终复验（2026-09-28）

> 输入基线：`30b64201489a78c2b4ab1d579043e20c5ac6d06a`。本轮**只处理测试源码、测试证据与对应文档**：完成 G0/K1 测试模型的最终审查与证据闭合。未实施 B1、未改 `src/main`、生产 Python 工具、构建配置、迁移、契约、默认开关与 HTTP/Session/Worker 接线；未连接数据库、未启动服务、未执行真实 halt/kill；未分配 IT/SmokeRun/端口/数据库。
>
> **判定口径（五类必须分开）**：代理行为实测 / 测试模型验证（含负控）/ 静态规范修订 / 真实驱动与真实锁协议**未验** / B1 产品实现**待授权**。
> **本轮结论：K1 仍未通过**——K1-1（规范与模型已收口，真实驱动行为未验）、K1-2（窗口 W）、K1-3（F1′/F3/F5 全未确认分支）继续保留。**“B1 未实现”不是 G0 规范失败的理由**，两者分开记录。

## 1. 前置检查（修改之前采集）

| 项 | 实测 | 证据（`.local/r09-c2-g0-k1-model-final-20260928-a/`） |
| --- | --- | --- |
| 分支 / HEAD / 实际远端 | `codex/development` / `30b6420` / `git ls-remote` 同 SHA | `00-pre-head.txt`、`00-pre-remote.txt` |
| **修改前**工作区 | status 为空、无 `index.lock`、`0/0`、`git diff --check` 退出 0 | `00-pre-status.txt`、`00-pre-checks.txt`、`00-pre-diff-check.txt` |
| 写者/环境 | 仅本会话引用本树/共享树；19330/19434/19437/23308/8080/8081/9090 无监听 | `03-pre-writers.txt`、`04-pre-listeners.txt` |
| 共享树 | detached `ea4d166`、30 修改＋67 未跟踪＝97；状态哈希与留档一致（前后各一次）；138 项哈希 **138/138 一致** | `01-pre-shared-tree-*.txt`、`09-post-*` |
| 测试名/证据目录查重 | 修改前 30 个测试方法、**0 重复**；证据目录唯一后缀 `-a`，未覆盖前两轮 | `05-pre-test-names.txt` |

## 2. 必须完成项与结果

### 2.1 围栏矩阵按“观察时状态”（已完成）

- 状态：进程存活、进程记录锁、`fenced.lock` 共享锁、租约记录、本机 FENCED（`ProcessState`）。
- `terminate()` 释放该进程**全部锁**并标记不可继续动作（此后 `fenceSignal`/`releaseRecordLock`/`retireLeases` 抛 `IllegalStateException`）；不变式 `requireTerminationReleasesAllLocks`。
- **观察者按当前终态计算依据，不累计历史动作**；每条依据带观察时前提（`Observer.FENCE_PRECONDITION ＝ "观察时仍存活且已确认取得 fenced.lock 共享锁并仍持有"`）。F5 已确认后不再并列围栏通道依据。
- 四种状态**分别验证**（各输出本机结果、跨进程依据、后续唯一结果）：
  | 状态 | 本机结果 | 跨进程依据（观察时） | 后续唯一结果 |
  | --- | --- | --- | --- |
  | F1′ 已确认且仍存活 | FENCED 已置：F1 之后**新进入**必拒（效力由轨迹用例证明，不用赋值断言） | 1 条：仍存活且共享锁仍持有 ⇒ 门独占 tryLock 必失败 | `BLOCKED` |
  | F3 已确认 | 同上 | 1 条：记录锁已释放且租约未退役 ⇒ 判孤儿 | `BLOCKED` |
  | F5 已确认（此前 F1′ 也曾确认） | 同上 | 1 条：已确认终止且租约未退役 ⇒ 判孤儿（全部锁已释放，**不再并列围栏通道依据**） | `BLOCKED` |
  | 三者均未确认 | 同上 | **0 条** | `UNCONFIRMED_NO_CROSS_PROCESS_BASIS`（须外部隔离，非已实现能力） |
- 8 组合观察时依据条数：`[111=1, 110=2, 101=1, 100=1, 011=1, 010=1, 001=1, 000=0]`（`110` 同时具备“共享锁仍持有”与“记录锁已释放+租约未退役”，二者在观察时**并不矛盾**）。
- **负控**：①`terminateWithoutReleasing()` ⇒ 不变式报告“已确认终止但仍有锁被持有”；②撤去本机门 ⇒ 轨迹出现 `delegate(c2)`（`[F1, gate(c2) readFenced=true, delegate(c2)]`），恢复后为 `refuse(c2)`。
- **仍未证明**：真实 `FileLock` 的跨进程可见性、真实进程终止与 OS 释放、其他 JVM 的观察行为——**全部未验**。

### 2.2 时序测试命中真实调度点（已完成）

`LocalGate.enterEffect(callId, afterFirstRead, afterFinalRead)` 提供两个显式调度点，三条次序全部由**事件轨迹**严格断言（`ordered(...)` 要求前缀存在且严格递增），并集中由 `requiredSchedulingOrdersAreAssertedFromEventTracesOnly` 复核：

```text
① gate(c1) read1=false | gate(c1) read2=false | F1@after-final-read | delegate(c1)        （在途）
② F1@before-call | gate(c2) read1=true | refuse(c2)                                        （F1 后新进入必拒）
③ gate(c3) read1=false | F1@after-read1 | gate(c3) read2=true | refuseAfterRecheck(c3)     （重读关闭首读窗口）
```

此外 `crossProcessFenceObservationIsAStateTransitionNotAConstant` 以状态迁移＋轨迹验证 `free=true → acquireShared → free=false`，且早先观察不被追溯改写；已删除只断言预置布尔值的测试。**仍未证明**：真实 JVM 调度与 `volatile` 可见性（需产品实现）。

### 2.3 JDBC 覆盖矩阵与模型输入对齐（已完成）

- `RuleRow`（20 条规则行/并发性质）↔ 测试方法**唯一映射** `COVERAGE`，并由 `coverageMatrixMapsEveryRuleRowAndEveryTestMethod` **双向校验**：每条规则至少一个覆盖测试、每个测试方法至少映射一条规则、方法名唯一（本次输出 `rules=20 testMethods=17 allMapped=true`）。
- 覆盖的规则行含：`Connection.commit`、`setAutoCommit(true/false)`、Statement/PreparedStatement/CallableStatement 的有参（可见 SQL）与无参（依赖 prepare 记忆）、`addBatch`／`executeBatch`／`executeLargeBatch`／`clearBatch`、prepare 记忆存在与缺失、手动事务内原始提交优先、`ResultSet` 变更方法／只读白名单／未知方法（`moveToInsertRow`、`cancelRowUpdates` 委托前拒绝）、执行批次前再次检查围栏。
- **输入限定**：`CandidateGate` 只接受“方法名 + SQL 文本 + 语句/连接状态”，SQL 分类由模型内部的**有限样本分类器**完成（`classifierInputsAreSqlSamplesNotPrecomputedBooleans` 断言 `SELECT`/`WITH`/`INSERT`/`COMMIT`/`SET AUTOCOMMIT=1` 的判定，并断言同一输入结论唯一），**不存在**调用方预填“已分类布尔值”。
- **声明未验**：`classifierDeclaresWhichFormsRemainUnverified` 明确列出并断言这些形态在样本分类器下一律 `UNKNOWN` ⇒ 委托前拒绝：`CallableStatement` 调用体、多语句、带注释语句、会话级 `SET`、厂商方言与 DDL 变体、驱动在 `autoCommit` 切换时的**真实隐式提交时机**——**真实语义未验**。
- **仍未证明**：真实驱动对上述形态的行为、`executeLargeBatch` 的驱动方言差异、真实隐式提交是否发生。模型中的 `UNKNOWN → 拒绝` 是**安全规格选择**，不是生产效果门已实现。

### 2.4 证据与文档（已完成）

- 旧轮失败证据保留：第 1 次失败 XML 在上一轮目录 `failed-run-xml/`；**第 2 次缺失 XML 的说明保留、不补造**（见 [K1 模型更正](R09_C2_G0_K1_MODEL_CORRECTION_2026-09-28.md) §2.4 与 `41-legacy-evidence-map.txt`）。
- 本轮 `run1/`（失败，含 `run1-failed-xml/` 副本）、`run2/`（通过）：各含 `gradle.log`、`exit.txt`、全部 JUnit XML、`files.sha256`；负控与正常通过输出分存（`negative-control-output.txt` / `normal-pass-output.txt`）。
- 设计同步：§5.5 失败矩阵每行改为**按观察时状态**表述（F5 确认后不再并列共享锁依据），并明确“不得以本轮曾确认过计数”；§4.2 未知 `ResultSet` 行与提交类优先等已在更正轮统一。
- 验收记录：新增“**验证类别（五类必须分开）**”表与覆盖率映射说明；G0 规范状态与 B1 待实施分开。

## 3. 运行记录

命令（两轮逐字一致，均追加 `--info` 以抓取轨迹）：

```powershell
.\gradlew.bat -p server test --tests 'com.acrylic.outbound.recovery.G0K1*Test' --tests 'com.acrylic.outbound.recovery.RecoveryAdmittedDataSourceTest' --tests 'com.acrylic.outbound.recovery.RecoveryWriterLeaseTest' --rerun-tasks --no-daemon --console=plain
```

| 轮 | 目录 | 退出码 | 结果 |
| --- | --- | --- | --- |
| 1 | `run1/`、`run1-failed-xml/` | **1** | 60 项 / **2 失败**，均在 `G0K1FenceMatrixTest`：终止依据文案里含“共享锁”一词，被我自己的断言（禁止出现该词）误判——**测试自身缺陷**，非产品缺陷 |
| 2 | `run2/` | **0** | **60 项 / 0 失败**；G0K1 测试方法 **37 个且 37 个唯一名**（围栏矩阵 10、JDBC 边界 17、时序 10）；回归 `RecoveryAdmittedDataSourceTest` 17、`RecoveryWriterLeaseTest` 6 全绿 |

汇总：`60-run-summary.txt`。**测试全绿不等于协议全绿**：模型只覆盖测试侧状态与信息流。

## 4. 逐项判定

| 项 | 判定 |
| --- | --- |
| 围栏矩阵（观察时状态） | **测试模型验证成立**；真实锁协议/真实终止**未验** |
| 时序（三条调度次序） | **测试模型验证成立**（轨迹严格断言）；真实调度与可见性**未验** |
| JDBC 覆盖矩阵与输入对齐 | **测试模型验证成立**（20 规则行全覆盖、17 方法全映射、方法名唯一）；真实驱动语义**未验** |
| 证据与文档 | 已完成；旧轮缺失证据**登记而非补造** |
| **K1-1** | **未通过**：规范已收口、模型自洽，但真实驱动 SQL 分类与真实隐式提交未验 |
| **K1-2** | **未通过**：命名窗口 W 仍在（本轮复现反例，未消除） |
| **K1-3** | **未通过**：F1′/F3/F5 三者都未确认的依条件分支仍在（须外部隔离） |
| K1-4 | 验收断言分层，沿用（S20-u 已入设计） |
| K2/K3/K4 | 保留既有设计级状态，本轮不扩展 |
| 真实驱动效果门 / 真实 JDBC 隐式提交 / 真实跨进程文件锁与进程终止 / HTTP·Session 与完整 C2 组合 | **全部未验** |
| B1 生产实现 | **未授权、未实施**（不作为 G0 失败理由） |

## 5. 未验边界与停止点

本轮不构成：真实数据库效果门验证、真实 JDBC 驱动隐式提交验证、真实跨进程文件锁与进程终止验证、HTTP/Session 与完整 C2 组合验收、B1 生产实现授权。

继续单列：**W**、**F1′/F3/F5 全未确认分支**、生产默认 Session 清理、孤儿解除、Java/Python `normal-admission.json` 允许集差异、C1 历史未验项（首次插入丢回执、独立进程重启、真实网络故障、断电、Linux 持久性、现场原资料组合）。七类生产决定、TEMP 生产归档与生产释放保持关闭，`localDeletionAllowed=false`。未发现新的真实产品缺陷。

**完成本轮最终模型审查与记录后停止，不自动进入 B1。**
