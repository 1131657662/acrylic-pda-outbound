# R09 C2-G0 K1 模型更正轮（2026-09-28）

> 输入基线：`25469d94d4181c508869455270565513e542cccb`。**本轮只修复 G0-K1 测试模型、补齐已授权的验证缺口并更正证据口径**；仅改三个 `G0K1*Test.java` 与受影响文档。未改 `src/main`、生产 Python 工具、构建配置、迁移、契约、默认开关与既有回归断言；未接 HTTP/Session/Worker/文件/启动入口；未连接数据库、未启动服务、未执行真实 halt/kill；未分配 IT/SmokeRun/端口/数据库。
>
> **判定口径**：逐项区分 **代理特征实测 / 模型性质验证 / 静态（文档）分析 / 负控 / 仍未证明 / 未验**。**本轮结论：K1 仍未通过**（K1-1 规范仍有未证明项；K1-2 窗口 W；K1-3 依条件分支）。**“效果门尚未在 `src/main` 实现”属 B1 待实施，不作为 G0 设计失败的理由**——G0 的失败原因是下列规范与模型缺口。

## 1. 前置检查（修改之前采集）

| 项 | 实测 | 证据（`.local/r09-c2-g0-k1-model-correction-20260928-a/`） |
| --- | --- | --- |
| 分支 / HEAD / 实际远端 | `codex/development` / `25469d9` / `git ls-remote` 同 SHA | `00-pre-head.txt`、`00-pre-remote.txt` |
| **修改前**工作区 | status 为空、无 `index.lock`、`0/0`、`git diff --check` 退出 0 | `00-pre-status.txt`、`00-pre-checks.txt`、`00-pre-diff-check.txt` |
| 写者/环境 | 仅本会话引用本树/共享树；19330/19434/19437/23308/8080/8081/9090 无监听 | `03-pre-writers.txt`、`04-pre-listeners.txt` |
| 共享树 | detached `ea4d166`、30 修改＋67 未跟踪＝97；状态哈希与留档一致（前后各一次）；138 项哈希 **138/138 一致** | `01-pre-shared-tree-*.txt`、`09-post-*` |
| 查重 | 新增辅助模型都放在既有三个测试类内部（无新增文件）；证据目录唯一后缀 `-a`，未覆盖上一轮 | 本文与批次指针 |

## 2. 修复前问题 → 修复 → 轨迹/断言 → 负控 → 复验

### 2.1 围栏矩阵：历史动作计数 → 状态迁移（修复 1）

- **修复前问题**：`[3,2,2,1,2,1,1,0]` 由“历史确认成功的动作数”累加得出；在 F1′ 与 F5 都确认时同时列出“共享锁仍被持有”与“进程终止、全部锁释放”两条互相矛盾的依据；`local=true` 直接赋值再断言为 true，不构成本机停止的证明。
- **修复**：`ProcessState` 只保存**终态**（`alive`／`recordLockHeld`／`fenceLockHeld`／`leasesPresent`），迁移 `fenceSignal`／`releaseRecordLock`／`retireLeases`／`terminate`；**`terminate()` 释放该进程模型中的全部锁**；`Observer.basis()` 只按终态计算；终止后任何门动作抛 `IllegalStateException`；新增不变式 `requireTerminationReleasesAllLocks`。本机拒绝改由 `LocalGate` 的**调用入口＋委托轨迹**验证，删除 `local=true` 式证明。
- **实际轨迹/断言**：8 组合按迁移构造并逐项断言（`everyConfirmationCombinationYieldsAStateBasedOutcome`）；终态依据示例（F1′/F3/F5 全确认）＝仅“已确认终止且租约未退役 ⇒ 判孤儿”，**不再出现“共享锁仍被持有”**（`basisIsComputedFromTheFinalStateNotFromHistoricalSuccesses`）；本机拒绝轨迹＝`[F1, gate(c2) readFenced=true, refuse(c2)]`，且轨迹中**没有** `delegate`（`localRefusalIsVerifiedByCallEntryAndTraceNotByAFlag`）。
- **负控**：①`terminationInvariantDetectsARetainedLock` 用 `terminateWithoutReleasing()` 故意保留锁 ⇒ 不变式**检出** `AssertionError("…仍有锁被持有")`，恢复后通过；②`guardBypassIsDetectedByTheTrace` 关闭守卫 ⇒ 轨迹为 `[F1, gate(c2) readFenced=true, delegate(c2)]`（反例可达），恢复后为 `refuse(c2)`。
- **仍未证明**：真实 `FileLock` 的跨进程可见性、真实进程终止与 OS 释放全部锁、真实 `fenced.lock` 的共享/独占竞争——都属未验（需 B1 实现后的真机/真实文件系统证据）。

### 2.2 确定性时序：增加真实命中的调度点（修复 2）

- **修复前问题**：第二段先完整执行 `enterEffect()`（委托已发生）再 `fence()`，证明的是“委托后围栏”，不是注释所称的“最后读取与委托之间围栏”；另一处围栏时点测试只断言两个手写布尔常量。
- **修复**：`LocalGate.enterEffect(callId, afterFirstRead, afterFinalRead)` 提供**两个显式调度点**；`FenceLock` 改为状态对象（`acquireShared()`／`observeFree(at)` 并记录轨迹），删除只断言常量的测试。
- **实际轨迹/断言**：①`最终读取未围栏 → F1 → 委托`（`windowAfterTheFinalReadIsRecordedAsInFlightNotAsAViolation`）：断言事件次序 `read2=false` < `F1` < `delegate`，并断定为**在途**、`violations()==0`；②`F1 → 新调用读取 → 拒绝`（`newCallEnteringAfterFenceIsRefusedWithTraceOrder`）：断言 `F1` < `read1=true` < `refuse`，且轨迹无 `delegate`；③重读窗口：`read1=false` < `F1` < `read2=true` < `refuseAfterRecheck`；④`crossProcessFenceObservationIsAStateTransitionNotAConstant`：`free=true → acquireShared → free=false`，且**早先的观察不被追溯改写**（只覆盖观察瞬间）。
- **负控**：`violationsAreCountedOnlyWhenTheGuardIsRemoved` 撤掉守卫 ⇒ `VIOLATION(c2) | delegate(c2)`（反例可达），恢复后 `refuse(c2)`。
- **仍未证明**：真实 JVM 调度与 `volatile` 可见性只能由产品实现验证；模型只证明规范自洽。

### 2.3 JDBC 测试侧信息流（修复 3）

- **修复前问题**：由调用者预填 `readOnly/commitClass` 布尔与 `…IN_TRANSACTION` 枚举，没有 prepare 绑定、批队列、`clearBatch`、执行时重检；CallableStatement 未覆盖；`executeLargeBatch` 未覆盖。这只验证“给定分类后的分支”，不能支持“冻结表完整覆盖”。
- **修复**：`CandidateGate` 按**实际调用形态**（方法名 + SQL 参数 + `StatementModel` 的 prepare 绑定/批队列 + `ConnState`）计算判定；SQL 分类改为明确标注的**有限样本分类器** `classifySql`（不可判定 ⇒ `UNKNOWN` ⇒ 委托前拒绝），并新增 `rowForSetAutoCommit(boolean)` 由**参数值**选行、`classifyMethod` 行映射唯一性检查。
- **实际轨迹/断言**（`G0K1JdbcBoundaryTest` 模型部分 7 项）：prepare 绑定存在/缺失（`NO_GATE`/`GATE`/`REFUSE_UNCLASSIFIED`）；批队列含已知写入 ⇒ `GATE`、含未知项 ⇒ `REFUSE`；`clearBatch` 后队列为空且**不复用旧分类**（`beforeClear=[WRITE]` → `afterClear=[]`）；**`addBatch` 后发生 F1，`executeBatch` 必须重新判定并拒绝**；手动事务内原始 `COMMIT`/`SET AUTOCOMMIT=1` 优先于免门；未知 ResultSet 方法（`moveToInsertRow`/`cancelRowUpdates`）委托前拒绝；同一实际调用不因选行不同而得到不同结论。
- **代理特征部分**（6 项）：连接级效果直接委托；`setAutoCommit(true)` 委托后被钩子记录（**只证明委托发生**）；`execute(sql)`/`addBatch(sql)` 可见 SQL、`prepareStatement`/`prepareCall` 只在 prepare 期可见、无参 `execute*/addBatch/executeBatch` 不可见；`ResultSet` 变更方法与**非白名单方法**（`moveToInsertRow`/`getWarnings`）都被委托；`autoCommit=false` 下原始 `COMMIT` 直接委托；CallableStatement 无参执行无 SQL。
- **负控**：①撤掉“执行批时重新判定围栏” ⇒ `lax=NO_GATE`，独立性质断言 `requireRecheckOnFence(...)==false` **检出**；②`clearBatch` 若未生效，`beforeClear` 的 `GATE` 与清空后的 `NO_GATE` 两断言合起来可检出。
- **仍未证明**：真实驱动对注释/多语句/`CALL`/DDL 的文本形态、真实隐式提交是否发生、真实 `executeLargeBatch`/驱动方言行为；SQL 分类器**不是**通用解析器，其覆盖边界需 B1 用真实 MySQL 语句样本验证。

### 2.4 证据与文档更正（修复 4）

| # | 问题 | 处理 |
| --- | --- | --- |
| 1 | 上一轮报告称“失败轮 XML 原样完整保留”，但第 2 次运行的失败 XML 缺失 | 定向查找：`failed-run-xml/` 仅含**第 1 次**三个 XML；`failed-run-xml/rerun2-` 是一个**错名单文件**（2234 字节，SHA-256 `E22CCD68EFF784…`），内容是第 2 次的 `G0K1OrderingModelTest.xml`（9 项全绿），**不是**那份 1 项失败的 `G0K1JdbcBoundaryTest.xml`（多源 `Copy-Item` 塌缩所致）。**明确登记缺失**，第 2 次结论改由保留日志 `22-gradle-rerun.log` 支持（`rawCommitOverrides… FAILED`＋`46 tests completed, 1 failed`＋`exit_code=1`），见 `40-run2-log-evidence.txt`；未补造历史 XML，新一轮重跑不冒充旧轮。旧目录 42 个文件逐一取 SHA-256 存 `41-legacy-evidence-map.txt` |
| 2 | 本轮新运行未按轮隔离 | 改为 `run1/`–`run4/` **每轮独立目录**，各含 `gradle.log`、`exit.txt`、全部 JUnit XML、`files.sha256`；并校验 XML 汇总与退出码一致 |
| 3 | 未知 `ResultSet` 方法行为两处不一致 | 设计 §4.2 该行由“两层通过；否则拒绝”改为**委托前拒绝**（不使用第二种结果） |
| 4 | “无冲突锁＝没有 FENCED 进程”过强 | 设计三处限定为 **F1′ 已确认取得且锁仍持有**；未取得围栏信号的进程**不在这条通道上**，其覆盖见 X-孤儿与 §5.5 |
| 5 | 验收记录开头/“未运行”仍写“零代码、未编译、未跑单测” | 改为：旧表述**仅对 v5 文档轮成立**；测试侧两轮**新增测试代码并运行了定向单测**；仍未运行单测全集/IT/HTTP/数据库/冒烟/真实 halt |
| 6 | 四项反例被一概称“现有代理实测” | 更正为：①②③＝现有代理实测，④＝**文档静态矛盾**；Mockito 只证明委托发生，不证明真实隐式提交（旧记录已加更正块） |
| 7 | 以“产品代码尚未实现”作为 G0 失败理由（循环） | 验收记录与本文分开记录 **G0 规范状态**（未通过原因＝规范/模型缺口）与 **B1 产品实现未验**（待实施） |
| 8 | 证据口径 | 上一轮目录为 **33 顶层 / 42 递归**；本轮逐轮子目录另计 |

## 3. 本轮运行记录（每轮独立目录）

| 轮 | 目录 | 退出码 | 结果 |
| --- | --- | --- | --- |
| 1 | `run1/` | **1** | 53 项 / **1 失败**：`oneActualCallCannotGetDifferentDecisionsFromDifferentEnums`——行名 `close` 同时出现在两个映射分支（我的模型缺陷） |
| 2 | `run2/` | **1** | 53 项 / **1 失败**：同一用例——`close` 漏映射为 `UNCLASSIFIABLE`（模型缺陷） |
| 3 | `run3/` | **0** | **53 项 / 0 失败**（G0K1FenceMatrix 8、G0K1JdbcBoundary 13、G0K1OrderingModel 9、RecoveryAdmittedDataSource 17、RecoveryWriterLease 6）；含 `--info` 轨迹 |
| 4 | `run4/` | **0** | **53 项 / 0 失败 / 53 个唯一用例名**（最终复验，`--info`）：正常通过输出 `normal-pass-output.txt`，负控输出 `negative-control-output.txt` |

命令（四轮逐字一致，仅 `run4` 追加 `--info` 以抓取轨迹）：

```powershell
.\gradlew.bat -p server test --tests 'com.acrylic.outbound.recovery.G0K1*Test' --tests 'com.acrylic.outbound.recovery.RecoveryAdmittedDataSourceTest' --tests 'com.acrylic.outbound.recovery.RecoveryWriterLeaseTest' --rerun-tasks --no-daemon --console=plain
```

负控输出（`run4/negative-control-output.txt`，节选）：

```text
[G0K1-fence] negative control: [F1, gate(c2) readFenced=true, delegate(c2)] | restored: [F1, gate(c2) readFenced=true, refuse(c2)]
[G0K1-fence] termination invariant: retained-lock detected, restored run passes
[G0K1-flow]  negative control (missing re-check at executeBatch): lax=NO_GATE propertyRequiresFenceRefusal=false
[G0K1-order] negative control: F1@before-call | gate(c2) read1=true | VIOLATION(c2) | delegate(c2) | restored: … refuse(c2)
```

**测试全绿不等于协议全绿**：上述只覆盖代理特征与测试侧模型。

## 4. 逐项判定

| 项 | 判定 |
| --- | --- |
| 修复 1 围栏状态迁移 | 模型性质**已验证**（终态依据、终止释放全部锁、终止后不再执行门、本机拒绝由轨迹证明）；真实跨进程文件锁与真实终止**未验** |
| 修复 2 时序调度点 | 两条要求轨迹**已实际命中并由独立次序断言验证**；真实调度/可见性**未验** |
| 修复 3 JDBC 信息流 | prepare 绑定、批队列/清空/执行时重检、CallableStatement、`executeLargeBatch`、未知 ResultSet 拒绝、行映射唯一性**已在模型内验证**；真实驱动语句形态与真实隐式提交**未验** |
| 修复 4 证据与文档 | 8 项已处理（含**明确登记第 2 次失败 XML 缺失**，不补造） |
| **K1-1** | **未通过（规范）**：入口/信息流/优先级规则已按反例修订，但 SQL 分类边界与真实驱动证据仍缺；产品实现属 B1 待实施 |
| **K1-2** | **未通过**：命名窗口 W 仍在（本轮复现反例，未消除） |
| **K1-3** | **未通过**：F1′/F3/F5 三者都未确认的依条件分支仍在（须外部隔离） |
| K1-4 | 验收规格已分层，新增 S20-u；无新增缺口 |
| K2/K3/K4 | 保留既有设计结论，本轮不扩展 |
| 运行级 | 定向单测已运行（53 项）；单测全集/IT/HTTP/数据库/冒烟/真实 halt **未运行**；未发现新的真实产品缺陷 |

## 5. 未验边界与停止点

本轮仅验证**代理特征**与**测试模型**，不构成真实 JDBC 效果、跨进程文件锁协议或 HTTP/Session 接线验收。生产代码、迁移、契约、默认配置及其他入口在本轮范围之外；未连接数据库、未启动服务、未执行真实 halt/kill。

继续单列：**W**、**围栏全部动作未确认分支**、生产默认 Session 清理、孤儿解除、Java/Python `normal-admission.json` 允许集差异、C1 历史未验项（首次插入丢回执、独立进程重启、真实网络故障、断电、Linux 持久性、现场原资料组合）与 C2 全部动态接线。七类生产决定、TEMP 生产归档与生产释放不开放，`localDeletionAllowed=false`。

**本轮完成测试模型修复与记录后停止，不自动进入 B1。**
