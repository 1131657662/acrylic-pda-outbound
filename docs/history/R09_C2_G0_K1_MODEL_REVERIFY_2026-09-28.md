# R09 C2-G0 K1 测试侧模型复验（2026-09-28）

> **本记录已被[K1 模型更正轮](R09_C2_G0_K1_MODEL_CORRECTION_2026-09-28.md)部分更正，原文保留不改写。更正项：**
> 1. **“失败轮 XML 原样保留”不成立**：第 1 次运行的三个 XML 确实保留在 `failed-run-xml/`；**第 2 次运行的失败 XML 未保留**（多源 `Copy-Item` 塌缩成一个错名文件 `failed-run-xml/rerun2-`，其内容是第 2 次的 `G0K1OrderingModelTest.xml`（9 项全绿），不是那份 1 项失败的 `G0K1JdbcBoundaryTest.xml`）。第 2 次结论仅由保留日志 `22-gradle-rerun.log` 支持；未补造历史 XML。
> 2. **证据目录口径**：该目录为 **33 个顶层文件 / 递归 42 个文件**（报告只写了 33）；更正轮改为每轮独立子目录并附 `files.sha256`。
> 3. **四项反例的分类更正**：①②③（`ResultSet` 变更方法直接委托、无参/批处理 SQL 信息流缺口、`autoCommit==false` 免门漏原始提交）属**现有代理实测**；④（“无法归类”两种结果）是**文档静态矛盾**，不是代理实测。Mockito 特征测试只证明**代理把调用委托出去**，**不**证明真实驱动发生了隐式提交。
> 4. **围栏矩阵与“本机无条件成立”的证明方式**已在更正轮替换：原 `[3,2,2,1,2,1,1,0]` 是“历史动作计数”，会同时列出互相矛盾的依据（如“共享锁仍被持有”与“进程已终止、全部锁释放”）；`local=true` 式赋值不构成本机停止的证明。更正轮改为**状态迁移模型**（终态计算依据）＋**调用入口与委托轨迹**验证本机拒绝。
> 5. **时序用例**：原文“重读之后、委托之前围栏 ⇒ 拒绝”的轨迹实际未命中（那段先完成委托再围栏）；更正轮用显式调度点分别构造 `最终读取未围栏 → F1 → 委托`（在途）与 `F1 → 新调用读取 → 拒绝` 两条轨迹。

> 输入基线：`5c07c529ee960feb0b2acaa35aed97189e976bff`（v5）。本轮**仅授权测试侧可执行反例、规范核定与文档同步**：新增 3 个测试类并定向运行；**不**实施 B1、**不**修改 `src/main`、生产 Python 工具、构建配置、迁移、契约或默认开关；未连接数据库、未启动服务、未执行真实 halt/kill；未分配 IT/SmokeRun/端口/数据库。
> **判定口径**：逐项区分 **现有代理实测 / 模型反例 / 模型性质验证 / 静态证明 / 仍阻断 / 未验**，不笼统宣布通过。**本轮结论：K1 仍未通过**（K1-1 存在四项测试侧反例并被据此修订设计，但规则未在产品代码实现/验证；K1-2 命名窗口 W 保留；K1-3 一条依条件分支保留）。

## 1. 前置检查（修改之前采集）

| 项 | 实测 | 证据（`.local/r09-c2-g0-k1-model-20260928-a/`） |
| --- | --- | --- |
| 分支 / HEAD / 实际远端 | `codex/development` / `5c07c52` / `git ls-remote` 同 SHA | `00-pre-head.txt`、`00-pre-remote.txt` |
| **修改前**工作区 | status 为空、无 `index.lock`、`0/0`、`git diff --check` 退出 0 | `00-pre-status.txt`、`00-pre-checks.txt`、`00-pre-diff-check.txt` |
| 写者/环境 | 仅本会话引用本树/共享树；受控源码近 60 分钟 0 改动；19330/19434/19437/23308/8080/8081/9090 无监听 | `03-pre-writers.txt`、`04-pre-listeners.txt` |
| 共享树 | detached `ea4d166`、30 修改＋67 未跟踪＝97，状态哈希与留档一致（前后各一次）；138 项哈希 **138/138 一致** | `01-pre-shared-tree-*.txt`、`09-post-*` |
| 名称/证据查重 | 无既有 `G0K1*` 测试文件；证据目录加后缀 `-a`（无覆盖） | 本节与批次指针 |

## 2. 本轮新增测试（两类，分开报告）

| 文件 | 类别 | 内容 |
| --- | --- | --- |
| `server/src/test/java/com/acrylic/outbound/recovery/G0K1JdbcBoundaryTest.java` | ①现有代码特征（真实代理＋Mockito fake delegate）＋②候选规则模型 | 5 项特征测试（连接级效果直接委托、`setAutoCommit(true)` 委托内隐式提交、SQL 可见性/信息流、可更新 `ResultSet` 变更方法、`autoCommit=false` 下的原始 `COMMIT`）＋4 项候选规则表测试（冻结表逐行判定、提交类优先级、无参依赖记住的 prepare SQL、不可归类**统一拒绝**） |
| `server/src/test/java/com/acrylic/outbound/recovery/G0K1OrderingModelTest.java` | ②确定性调度模型 | 9 项：F1 之后新进入必拒；读标志在 F1 之前属**在途**；重读只移动线性化点；跨进程观察的时点语义；F1′ 未确认不构成 BLOCKED；属主在扫描前/窗口 W 内/扫描后死亡三种；负控（撤守卫可检出反例、恢复后消失） |
| `server/src/test/java/com/acrylic/outbound/recovery/G0K1FenceMatrixTest.java` | ②围栏确认矩阵模型 | 5 项：F1′/F3/F5 的 8 种确认组合（本机停止恒成立；跨进程依据 3/2/2/1/2/1/1/0 条；仅全未确认为 `UNCONFIRMED_NO_CROSS_PROCESS_BASIS`）、全未确认仍为阻断项、逐步可见证据、**模型内“已调用 halt”不算终止确认**、`fenced.lock` 成功只证明观察瞬间 |

## 3. 定向运行结果（含失败轮保留）

命令（与指令一致，四次运行均逐字使用同一过滤器）：

```powershell
.\gradlew.bat -p server test --tests 'com.acrylic.outbound.recovery.G0K1*Test' --tests 'com.acrylic.outbound.recovery.RecoveryAdmittedDataSourceTest' --tests 'com.acrylic.outbound.recovery.RecoveryWriterLeaseTest' --rerun-tasks --no-daemon --console=plain
```

| 轮次 | 命令/日志 | 退出码 | 结果 |
| --- | --- | --- | --- |
| 第 1 次 | `20-gradle-run.log`、`20-gradle-exit.txt` | **1** | 46 项 / **3 失败**（全部为**测试模型自身缺陷**：候选规则的“不可归类”用例覆盖了非 SQL 分类形态；手动事务用例误用 `STATEMENT_EXECUTE`；“隐式提交”用例断言了未发生的 `setAutoCommit(false)`） |
| 第 2 次 | `22-gradle-rerun.log`、`22-gradle-rerun-exit.txt` | **1** | 46 项 / **1 失败**（模型实现与设计表不一致：手动事务内非提交 SQL 被错误判为 GATE，应为“延迟到 commit 门”） |
| 第 3 次 | `23-gradle-rerun2.log`、`23-gradle-rerun2-exit.txt` | **0** | 46 项 / **0 失败**（`G0K1JdbcBoundaryTest` 9、`G0K1OrderingModelTest` 9、`G0K1FenceMatrixTest` 5、`RecoveryAdmittedDataSourceTest` 17、`RecoveryWriterLeaseTest` 6） |
| 第 4 次（`--info`，为抓取逐事件轨迹） | `27-gradle-traces.log`、`27-gradle-traces-exit.txt` | **0** | 同上全绿；轨迹汇入 `26-model-traces.txt` |

失败轮 XML 原样保留在 `failed-run-xml/`（含第 2 次），汇总见 `21-failed-run-summary.txt`；通过轮 XML 在 `passed-run-xml/`，汇总见 `25-passed-run-summary.txt`（TOTAL tests=46 failures=0 errors=0）。**测试全绿不等于协议全绿**：反例“成功找到”与“性质成立”分别统计，见 §4。

逐事件轨迹（节选，完整见 `26-model-traces.txt`）：

```text
[G0K1-order] in-flight vs new: gate(c1) readFenced=false | F1@inside-c1 | delegate(c1) | gate(c3) readFenced=true | refuse(c3)
[G0K1-order] GAP_W: scan1:eval(r1)@2 alive=true | scan1:eval(r2)@3 alive=true | scan1:scan_end@3
[G0K1-order] negative control (guard removed): F1@before-call | gate(c2) readFenced=true | VIOLATION(c2) | delegate(c2)
[G0K1-order] restored run (guard enabled): F1@before-call | gate(c2) readFenced=true | refuse(c2)
[G0K1-fence] 8 组合：本机停止全部成立；跨进程依据条数=[3, 2, 2, 1, 2, 1, 1, 0]
[G0K1-char]  autoCommit=false 时原始 COMMIT 仍直接委托 -> “autoCommit=false 整体免门”的规则会漏掉提交类 SQL
[G0K1-char]  无参 execute*/addBatch/executeBatch 本次调用不可见 -> 候选规则必须记住 prepare 期 SQL
[G0K1-char]  ResultSet.insertRow/updateRow/deleteRow 经代理委托 -> v5 表把 ResultSet.* 一概列为“无持久化”是错误的
```

## 4. K1-1：JDBC 判定闭合（**四项测试侧反例，设计已修订，判定未通过**）

| # | 反例（现有代理实测） | 证据 | 设计修订 | 未来断言 |
| --- | --- | --- | --- | --- |
| 1 | `ResultSet.insertRow/updateRow/deleteRow` 经代理**直接委托**，v5 表把 `ResultSet.*` 一概列为“无持久化”是错的 | `G0K1JdbcBoundaryTest.updatableResultSetMutatorsAreWrappedAndDelegated`（`verify(result).insertRow()` 等 3 项） | §4.2 表拆分为 `ResultSet` 变更方法（GATE）／只读白名单（NO_GATE）／其余（委托前拒绝） | S20：三类各给唯一结果；S20-u：其余 ⇒ `CONTROL_EFFECT_UNCLASSIFIED` |
| 2 | 无参 `PreparedStatement.execute*/executeQuery/executeBatch` 与无参批处理的 **SQL 在本次调用不可见**（`sql(args)` 只读首个字符串参数；`prepareStatement(sql)` 的 SQL 只在 prepare 期可见） | `sqlTextIsVisibleOnExecuteButNotOnNoArgPreparedExecutionOrBatch`（verify 6 项委托形态） | §4.2 改为**依赖 prepare 期记住的 SQL**：只读 ⇒ 放行；写入 ⇒ 过门；**无记住的 SQL ⇒ 拒绝**；批处理在 `addBatch` 本身判定 | S20：无参两向 + 批处理累积；缺 SQL ⇒ `CONTROL_EFFECT_UNCLASSIFIED` |
| 3 | `autoCommit==false` 时原始 `COMMIT` 仍直接委托 ⇒ “整体免门”规则会漏掉提交类 SQL | `rawCommitSqlIsDelegatedEvenWhenAutoCommitIsDisabled` | §4.2 规则句与表：**提交类（显式 commit／隐式提交／原始 `COMMIT`／`SET AUTOCOMMIT=1`／`START TRANSACTION`）优先级最高**，覆盖免门与只读白名单 | S20：手动事务内提交类 SQL ⇒ GATE；非提交类 ⇒ NO_GATE |
| 4 | “无法归类”在 v5 表（“仅当两层通过”）与规则句/S20（“委托前拒绝”）之间**有两种结果** | 设计 §4.2 原文自相矛盾（静态） | §4.2 统一为**委托前拒绝**，并明确“不存在过门即可执行的第二种结果” | **S20-u**：无法归类 ⇒ `CONTROL_EFFECT_UNCLASSIFIED`，且代理特征测试证明 delegate **未**收到该调用 |

**候选规则模型**（测试源码内，非产品实现）对上述四项给出可回放判定：`sqlClassifiedApisRefuseUnclassifiableInputsInsteadOfGateThenAllow`、`rawCommitOverridesTheAutoCommitFalseExemptionAtEveryLevel`、`noArgPreparedExecutionDependsOnRememberedPrepareSql`、`candidateRulesMapEveryFrozenTableRowToADecision`、`rawCommitOverridesTheAutoCommitFalseExemptionAtEveryLevel` 全绿。

**判定：未通过。** 规则虽已在模型内自洽，但（a）产品代码尚未实现效果门；（b）SQL 归类只验证了“模型判定”，未验证真实驱动的语句文本形态（注释/多语句/`CALL`/DDL 等仍需真实 MySQL 证据，属未验）。

## 5. K1-2：本机与跨进程判定时序（模型性质已验证，窗口 W 保留）

| 轨迹 | 唯一结果 | 依据 |
| --- | --- | --- |
| 旧连接/Statement 在 F1 之后开始调用 | **拒绝**（`delegate` 未发生） | `callEnteringTheGateAfterFenceIsRefusedAndNeverDelegates` |
| 调用已读到未围栏 → F1 → 再委托 | **允许**（在途调用），并明确**不是**“新进入” | `inFlightCallThatReadBeforeFenceDelegatesAndIsLabelledInFlight`（轨迹含读—F1—委托顺序） |
| 读标志后、重读前发生 F1 | **拒绝**（`refuseAfterRecheck`） | `recheckNarrowsTheWindowButDoesNotRemoveTheLinearizationPoint`；证明“重读只移动线性化点” |
| `fenced.lock` 观察成功之后才发生围栏 | 该次观察的判定**不被追溯**；下一次门的观察必失败 | `crossProcessObservationBeforeFenceIsNotInvalidatedForTheLaterGate` |
| F1 已发生、F1′ 未确认 | 本机停止成立；**跨进程依据为空**，标签为 `UNCONFIRMED_NO_CROSS_PROCESS_BASIS`，**不得写成 BLOCKED** | `unconfirmedFenceLockYieldsNoCrossProcessBasisAndIsNotCalledBlocked` |
| 属主在扫描前死亡 | 判孤儿 ⇒ 拒绝 | `ownerDeathBeforeItsRecordEvaluationIsDetectedAsOrphan` |
| 属主在窗口 W 内死亡 | 该次扫描 **CLEAR**（命名缺口 W，轨迹含 `eval(r1)@2 alive=true` 与 `scan_end@3`）；**后一次扫描判孤儿** | `ownerDeathInsideWindowWIsMissedByThatScanAndCaughtByTheNext` |
| 属主在扫描结束后死亡 | 本次 CLEAR（判定后类）；后一次扫描判孤儿 | `ownerDeathAfterScanEndIsPostDecisionAndCaughtByTheNextScan` |
| 负控（撤去守卫） | 模型**检出反例**（`VIOLATION(c2)` + `delegate(c2)`）；恢复守卫后为 `refuse(c2)` | `guardRemovedModelDetectsTheCounterexampleAndRestoringItRemovesIt` |

**不变式与前提（有限交错之外仍需成立）**：①本机停止的线性化点＝**本调用对 volatile 标志的一次读取**；F1 写在之后开始的读取必然可见（`volatile` happens-before）⇒ “F1 之后新进入”必拒，读早于 F1 写者为在途调用；②跨进程“围栏”态的原子通道＝`fenced.lock` 共享/独占配对，其判定只覆盖**观察瞬间**；③孤儿扫描只给出**逐记录命题**（属主在其记录判定时刻或之前已死 ⇒ 检出），**不**给出“t_n 时刻无孤儿”；④W 为本机制结构边界（v5 §5.4 机制级论证），本轮模型复现了它的可回放反例，**未消除**。

**判定：逐记录命题与三条观察通道在模型内成立；“t_n 无孤儿”仍不成立 ⇒ W 保留为阻断。**

## 6. K1-3：围栏确认矩阵（模型性质已验证，一条分支保留）

八种组合的跨进程依据条数＝`[3,2,2,1,2,1,1,0]`；本机效果停止在**全部八种**组合下恒成立（内存标志，与 F1′/F3/F5 是否确认无关）；仅“F1′、F3、F5 三者都未确认”为 `UNCONFIRMED_NO_CROSS_PROCESS_BASIS`，其后续处置＝**外部隔离**，并明确写成“这不是已实现能力”。模型内**“已调用 halt”不产生终止确认**（`callingHaltInTheModelIsNotTreatedAsTerminationConfirmation`）。

**判定：五条分支成立；一条保留阻断分支（需外部隔离），未消除。**

## 7. K1-4 与文档纠正

- K1-4 验收规格已在 v5 分层，本轮补充 **S20-u**（无法归类唯一结果）并保持 S4a/S4b 独立。
- 文档纠正：§4.2 效果入口表按四项反例重写；无法归类规则统一为“委托前拒绝”；§4.1 接纳“线性化点”改为逐记录命题并注明“不主张 t_n 无孤儿”；S13 每个输入对应唯一预期（①a 正常完成 / ①b UNKNOWN）；§11 标题改为 v5、28 项；验收记录的历史证据归属逐轮对号（Python 笔记＝v3 轮、`01-git-status` 时点＝correction 轮、章节检查失败＝v4 轮）；[C2 初始立项文档](R09_C2_PROJECT_SCOPE_2026-09-28.md) 第 30 行加“立项时点历史”标识并导向 C1 复验与看板。
- K2/K3/K4 保留既有设计结论，本轮未扩展其实现或验收。

## 8. 未验边界与停止点

本轮结果仅覆盖**现有代理特征**与**测试侧模型**；**不构成**真实数据库效果、跨进程文件锁协议、HTTP/Session 接线或 C2 组合验收。未运行完整应用冒烟、C1／105 真库 IT 与 B1 的 S1–S21；未执行真实 halt/kill 或跨进程实验。

保留：**K1-1 未通过**（规则已修订、未在产品代码实现/验证；SQL 归类缺真实驱动证据）、**K1-2 命名窗口 W**、**K1-3 依条件分支**、生产默认 Session 清理未覆盖、孤儿解除与生产释放未授权、Java/Python `normal-admission.json` 允许集差异（独立欠债，本轮不修）、C1 首次插入丢回执/独立进程重启/真实网络故障/断电/Linux 持久性/现场原资料组合、C2 全部动态接线与组合验收。

**本轮止于测试侧验证与记录；不自动进入 B1。**未发现新的真实产品缺陷（特征测试只记录现有委托行为，未发现产品缺陷）。
