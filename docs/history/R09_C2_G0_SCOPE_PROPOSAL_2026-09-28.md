# R09 C2 G0 范围与验收提案（2026-09-28）

> **状态：提案待授权。本轮只做只读核对与提案，未执行 G0 设计、未写产品/测试/工具代码、未新增迁移、未改契约或默认配置、未启动服务、未分配 IT 号。**结论口径只能写「C2 G0 范围与验收提案已提交，待授权」，不得写成 C2 已实现或 G0 已完成。源码版本事实见[看板生成事实块](../ORCHESTRATION_STATUS.md)，本文件不复述版本数字。

## 1. 输入、基线与授权

| 项目 | 本次实测结果 | 证据 |
| --- | --- | --- |
| 主树/分支 | `D:/acrylic-pda-outbound` / `codex/development` | `.local/r09-c2-g0-proposal-20260928/git-branch.txt` |
| HEAD | `56a38e2eed4514f260f1c3ad691987524917cf13`（= [交接文档](SESSION_HANDOFF_2026-09-28.md) 最后修改提交） | `git-head.txt`、`git-handoff-commit.txt` |
| 工作区 | 干净；`git status --porcelain --untracked-files=all` 为空 | `git-status-main.txt`、`git-clean.txt` |
| 远端同步 | `git rev-list --left-right --count HEAD...origin/codex/development` = `0 0`，origin 为 SSH | `git-left-right.txt`、`git-remotes.txt` |
| 看板事实块 | `tools/version_facts.ps1 -Check` 通过（代码派生事实一致；生成块的 date/head/dirty 字段按设计不做漂移校验） | `facts-check.txt` |
| 共享验证树 | `D:/acrylic-pda-worktrees/r06-e2a-migration` 仍 detached `ea4d166`，30 修改 + 67 未跟踪 = 97，与[工作树清点](DSH_SWITCH_WORKTREE_AUDIT_2026-09-28.md)一致 | `shared-tree-head.txt`、`shared-tree-status.txt`、`shared-tree-count.txt`、`shared-tree-status-sha256.txt` |
| 监听/进程 | 19330/19434/19437/23308/8080/8081/9090 无监听；19387 = DSH 应用（PID 23292）；3306 = 系统 mysqld（PID 5448）；既有 Gradle daemon（PID 20264）保留 | `listeners.txt`、`processes.txt` |
| 写权 | 主树无 `.git/index.lock`；主树干净且与远端 0/0；未观察到 C1 服务/IT 在途；`561/562/563` 无已用目录 | `writer-checks.txt`、`attempt-dup-check.txt` |

授权范围：用户仅要求**只读核对＋形成 G0 范围与验收提案**。C2 任何入口实现、F 类/七类生产决定、TEMP 生产归档、生产释放能力均未授权；`localDeletionAllowed=false`。本轮不重开 D 扩测、A3 补跑、526 专项或 R07 抬头撤销。

## 2. G0 目标与出口标准

**目标**：核对 C1 现有证据，确定「统一操作租约 ＋ 关闭后旧操作收尾 ＋ 启动顺序」的可实施方案，使 B1 之后的每批接线都有单一、可审查的租约与准入语义，而不是每个入口各自决定。

**G0 出口物（设计交付，不含实现）**：

1. 操作租约模型规格：身份边界、根/子操作关系、创建/传递/引用计数/最终释放、状态机（含排空与毒化）。
2. 关闭后行为规格：新根操作拒绝点；已接纳操作的受限收尾白名单；禁止借旧租约开展新业务的可检查判据；拒绝的错误码/响应形态。
3. C1 双侧核验接入点规格：核验时机、证据读取所用连接来源、与非准入连接的隔离约束、检查后竞态的处理方式。
4. UNKNOWN 规格：保留/毒化/隔离/排空判据；明确 rollback/close/finally 不清洗 UNKNOWN 的强制点。
5. 锁序与启动顺序规格：coordinator→writers→server_state→业务锁；迁移/初始化许可先于业务入口的强制点与竞争结果。
6. 框架边界清单：纳入/不纳入路径逐项列出，含 Session 最终保存、ERROR/ASYNC 分派、Session 过期清理、multipart 暂存、Worker 独立进程、渲染子进程。
7. B1 可直接执行的验收矩阵与文件清单（见 §5、§6）。
8. 失败/未决证据：凡方案依赖未验事实（跨进程、断电、Linux 持久性、现场原件）必须显式标为未验，并给出「未验时保持拒绝」的默认。

**非目标**：G0 不产出允许态验收、不改生产代码、不新增迁移、不改默认开关、不开放关闭能力与生产释放端点。C1 的补实现/真实复验若需要新增组件，须独立授权，不在 G0 内顺手完成。

## 3. 现状判断（源码只读核对）

| 主题 | 源码坐标 | 现状事实 | 对 G0 的含义 |
| --- | --- | --- | --- |
| 固定身份 | [RecoveryControlLocation](../../server/src/main/java/com/acrylic/outbound/recovery/RecoveryControlLocation.java)、[RecoveryAdmissionConfiguration](../../server/src/main/java/com/acrylic/outbound/recovery/RecoveryAdmissionConfiguration.java) | 身份＝`root`＋`installationId`＋`restoreRoots`＋`checkpoint`，全部来自显式配置，无自动 bootstrap；`checked()` 还要求权威根与恢复集不重叠 | 统一身份已有载体，可直接复用；G0 不需新造身份 |
| 实例新鲜度 | [RecoveryAdmittedDataSource](../../server/src/main/java/com/acrylic/outbound/recovery/RecoveryAdmittedDataSource.java):69–81 | 每次取连接都在 coordinator 锁内重读权威并与构造期 `startup` 快照比对，不一致即 `CONTROL_INSTANCE_STALE` | 已有「检查后竞态」缓解手段，但代价是每次借连接都做一次文件历史校验 |
| 连接级租约 | [RecoveryWriterLease](../../server/src/main/java/com/acrylic/outbound/recovery/RecoveryWriterLease.java):18–33 | 每个 JVM/文件身份一个 `Held`（1 字节 `writers.lock` 上的 OS FileLock），`references++` 共享；`close()` 引用归零才释放 | 已有引用计数可支撑根/子租约，但当前计数单位是「连接」不是「操作」 |
| 关闭意图位置 | [RecoveryWriterLease](../../server/src/main/java/com/acrylic/outbound/recovery/RecoveryWriterLease.java):21、[RecoveryControlIntents](../../server/src/main/java/com/acrylic/outbound/recovery/RecoveryControlIntents.java):35–46 | `requireAdmission` 在 `HELD` 命中与 `references++` **之前**无条件执行；待决关闭意图（`targetRevision > revision`）或意图序列不完整即抛 `CONTROL_CLOSING_INTENT`／`CONTROL_INTENT_INCOMPLETE` | 关闭意图一旦落盘，**已接纳操作的后继连接同样被拒**（Session 尾保存、Worker 独立续租）；这是 G0 的核心待解冲突 |
| 关闭相位 | [RecoveryControlCommand](../../server/src/main/java/com/acrylic/outbound/recovery/RecoveryControlCommand.java):9–10 | 只接受 `QUIESCING→RECOVERING→VERIFYING→RELEASE_READY` 四相；`requireAdmission` 并不因 `RELEASE_READY` 本身拒绝，只按 revision 关系判断 | 「关闭后」不等于「某相位后」；哪些权威形态必须拒绝新根操作需要在 G0 明确 |
| UNKNOWN 毒化 | [RecoveryConnectionHandler](../../server/src/main/java/com/acrylic/outbound/recovery/RecoveryConnectionHandler.java):93–115、[RecoveryWriterLease](../../server/src/main/java/com/acrylic/outbound/recovery/RecoveryWriterLease.java):51–63、[RecoveryTransactionLease](../../server/src/main/java/com/acrylic/outbound/recovery/RecoveryTransactionLease.java):31–38 | 事务未确认/回滚失败/关闭失败/`abort`/提交类歧义失败均置 `poisoned`；毒化后 `close()` 不释放锁、不 `HELD.remove`，本 JVM 内后续 `acquire` 一律 `CONTROL_WRITERS_ACTIVE`；`RecoveryLeasedTransaction` 的 `uncertainCompletion` 亦永久置位 | 已有「保留而不清洗」雏形，但没有落盘状态：进程退出后 OS 锁随之释放、毒化记忆丢失，跨进程/断电语义缺失 |
| 启动隔离 | [RecoveryAdmissionConfiguration](../../server/src/main/java/com/acrylic/outbound/recovery/RecoveryAdmissionConfiguration.java):39–50 | 迁移专用原池以 `@FlywayDataSource` 限定符保留，`RecoveryAdmittedDataSource` 为其上唯一 `@Primary` | 「借 Flyway 原池绕过准入」的唯一合法出口已知，必须限定为内部证据/迁移使用并防扩散 |
| 初始化顺序冲突 | [BootstrapAdmin](../../server/src/main/java/com/acrylic/outbound/auth/BootstrapAdmin.java):16–31、[application.yml](../../server/src/main/resources/application.yml):23–25 | `BootstrapAdmin` 是显式开关的 `CommandLineRunner`，用普通 `JdbcTemplate`（准入启用时即 `@Primary` 准入源）在事务中建仓库/管理员/授权/审计；Spring Boot 的 Web 容器在 `CommandLineRunner` 之前已开始监听 | 「空身份库初始化」与「受准入保护的普通写」直接冲突；且 runners 执行期间端口已开放，G0 必须给出启动顺序的强制点与窗口期行为 |
| 锁序 | [RecoveryControlLock](../../server/src/main/java/com/acrylic/outbound/recovery/RecoveryControlLock.java):16–39、[IdentityWriteGuard](../../server/src/main/java/com/acrylic/outbound/common/IdentityWriteGuard.java):23、[RecoveryProjectionRows](../../server/src/main/java/com/acrylic/outbound/recovery/RecoveryProjectionRows.java):14 | 现序为 JVM 信号量＋`coordinator.lock` → `writers.lock` → `server_state`（`FOR SHARE`/`FOR UPDATE`）→ 业务锁；`RecoveryControlLock` 的 ThreadLocal `OWNER` 只在其临界区有效 | 锁序已有实现，G0 需要的是「不得反序」的可检查约束与启动期互斥，而不是重写 |
| C1 双侧核验 | [RecoveryAdmissionVerifier](../../server/src/main/java/com/acrylic/outbound/recovery/RecoveryAdmissionVerifier.java):16–17、[InitialNormalVerification](../../server/src/main/java/com/acrylic/outbound/recovery/InitialNormalVerification.java):12–29、[InitialNormalStore](../../server/src/main/java/com/acrylic/outbound/recovery/InitialNormalStore.java):39–47、[RecoveryAdmissionQueries](../../server/src/main/java/com/acrylic/outbound/recovery/RecoveryAdmissionQueries.java):13 | `verifyInitial` 在 `store.locked(...)`（持 coordinator 锁）内读 DB 证据；证据类由调用方传入 `DataSource` | **递归借连接陷阱已在源码成立**：若证据走准入源，`acquire()` 会重入 `RecoveryControlLock.locked` → 同 JVM 信号量重入失败或 `OverlappingFileLockException`，结果为 `CONTROL_COORDINATOR_BUSY`。现有唯一接线（测试探针，[C1HttpController](../../server/src/test/java/com/acrylic/c1probe/C1HttpController.java):21–30）注入的是测试源，从未走过准入源，故该陷阱**无动态失败证据** |
| 运行期核验未接 | [RecoveryAdmissionVerifier](../../server/src/main/java/com/acrylic/outbound/recovery/RecoveryAdmissionVerifier.java):92–109 | `of(...)` 提供配置化 Verifier 工厂；生产源码中未见被应用入口调用 | 「接入点」是 G0 待设计项，不是已存在接线 |
| Session 与请求层 | [RequestIdFilter](../../server/src/main/java/com/acrylic/outbound/common/RequestIdFilter.java):14–24、[SecurityConfiguration](../../server/src/main/java/com/acrylic/outbound/auth/SecurityConfiguration.java) | 无准入过滤器；`RequestIdFilter` 为 `@Order(HIGHEST_PRECEDENCE)`，早于 Spring Session 的 `SessionRepositoryFilter`（后者顺序更晚，需在实现批中实测确认具体值） | 「在 Session 过滤器之前接纳」的插入位已存在；租约必须覆盖 `SessionRepositoryFilter` 之后的最终保存 |
| Worker 独立进程 | [BackgroundWorkerProcess](../../server/src/main/java/com/acrylic/outbound/jobs/BackgroundWorkerProcess.java):10–13、[BackgroundJobLeases](../../server/src/main/java/com/acrylic/outbound/jobs/BackgroundJobLeases.java):11 | worker 是同一 JAR 的独立非 Web 进程（`@Profile("worker")`）；续租用 `REQUIRES_NEW`，即独立事务与独立连接 | 跨进程语义必须由 `writers.lock` 的 OS 锁承担；关闭意图下「独立续租」与「连接级准入」直接冲突 |
| 渲染子进程 | [ExportRendererProcess](../../server/src/main/java/com/acrylic/outbound/exports/ExportRendererProcess.java)、[ExportJobHandler](../../server/src/main/java/com/acrylic/outbound/exports/ExportJobHandler.java):24–29 | 已有 Windows Job/子进程排空/定时 `renew`，但无操作租约传递 | 父租约须覆盖到进程树退出确认；Windows 证据不外推 Linux |
| 框架未纳入面 | [application.yml](../../server/src/main/resources/application.yml):26–32、`server/build.gradle`:18 | Spring Session JDBC 在类路径且 `initialize-schema: never`；multipart 上限已配置但未配置落盘位置 | Session 清理线程与 multipart 暂存归属未验，属 G0 必须逐项登记的实现前必查项 |

## 4. 设计要点、待决问题与候选

每条给出：现状判断 → 待决问题 → 候选与取舍 → **建议方向（供用户拍板，非已定案）**。

### A. 统一身份与租约边界

**现状**：固定实例身份已有单一载体（§3 第 1 行）；但租约计数单位是 JDBC 连接，文件操作与子进程完全在租约之外，请求/作业/文件三类操作没有共同的「操作」载体。

**待决问题**

1. 「操作」的粒度：HTTP 请求？JDBC 事务？一次作业（claim→完成）？一次文件写？一个子进程生命周期？
2. 根/子操作的创建与传递方式：显式对象沿调用链传递，还是作用域查找？若用作用域，异步/独立线程如何不丢？
3. 同一请求内 JdbcTemplate 反复借还连接是否共用一个根租约（否则引用计数无意义，且关闭意图后必然被拒）。
4. 文件/进程操作的租约入口放在框架/外围层，还是必须改 R05/R06/R07 已验业务类（后者默认排除）。

**候选与取舍**

- (A1) **显式根租约 + DataSource 复用**：Servlet 过滤器/Worker 调度/进程启动点创建 `OperationLease`（内部 `RecoveryWriterLease`），`RecoveryAdmittedDataSource` 在该作用域内 `borrow()` 复用根租约而非重新 `requireAdmission`。
  - 优点：语义明确、可审计、可测；不依赖 ThreadLocal 就能覆盖「同线程多次借连接」。
  - 代价：需要把租约对象送入 DataSource 的取连接路径（新增上下文载体），并重新跑 105 连接层回归。
- (A2) **只靠 ThreadLocal 作用域**：实现最省。
  - 缺点：Worker 调度线程、Session 清理线程、渲染子进程线程、`REQUIRES_NEW` 路径均不可靠；交接文档已明确「不能仅靠 ThreadLocal」。**不建议**。
- (A3) **维持连接级租约，只加强拒绝语义**：零改动。
  - 缺点：关闭意图后已接纳请求无法收尾（Session 尾保存必失败），无法满足「关闭后旧操作收尾」。**不足以作为 G0 方案**。

**建议方向**：以 A1 为主体，并把「文件/子进程租约」定义为对根租约的显式借用（不新建独立锁），未纳入的调用路径逐条列入 §4G 清单。

### B. 关闭后行为

**现状**：`requireAdmission` 是唯一拒绝点（§3 第 4 行），当前对「新操作」和「已接纳操作的子连接」不加区分；也没有「收尾」概念。

**待决问题**

1. 「关闭后」以哪个权威形态为准（待决关闭意图？四相中的某相？`RELEASE_READY`？）
2. 已接纳操作允许哪些收尾动作：提交/回滚当前事务？Session 最终保存？作业完成/失败登记？审计写？渲染产物落盘？——需要一个**可枚举的白名单**，并说明白名单之外一律拒绝。
3. 拒绝的表现：HTTP 状态码与错误码（现有 `SERVER_RECOVERY_REQUIRED` 503 语义是否复用）、Worker/文件的退出形态（作业标记失败还是保持 UNKNOWN）。
4. 如何证明「不得借旧租约开展新业务」：仅靠白名单代码路径，还是需要运行时可审计的证据（如按操作类型计数/日志）？

**候选与取舍**

- (B1) **根租约 check-once ＋ 收尾白名单**：根创建时做完整准入（含 C1 双侧证据）；进入收尾后只允许白名单子操作。新根操作始终重新检查。
- (B2) **每次子连接都重查关闭意图，但允许「同一根租约的已登记子操作」通过**：需要引入「已接纳但未完成」的登记（类似作业台账），复杂度更高，但拒绝更细。
- (B3) **关闭意图后全部拒绝，包括收尾**：语义最严，但会把已接纳请求留在 UNKNOWN，需接受「必须人工/进程重启处理」。

**建议方向**：B1 为主，并把 B3 作为「无法确认收尾」时的兜底（保持毒化、保留租约、不宣称排空）。

### C. 嵌套连接（Session 尾保存、Worker 独立续租/完成）

**现状**：Spring Session JDBC 的保存走 `@Primary` 准入源，发生在 `SessionRepositoryFilter` 退出前的 finally 阶段，属「业务已完成、操作未结束」的典型嵌套；`BackgroundJobLeases` 的 `REQUIRES_NEW` 会在 handler 执行中途开新事务/新连接。二者在关闭意图后都会撞 `CONTROL_CLOSING_INTENT`。守卫不能删（105 已验语义），也不能仅靠 ThreadLocal（§4A）。

**待决问题**

1. 根租约在哪些边界结束：请求（含 Session 保存）、作业（含完成/失败登记）、渲染进程（含进程树退出）。
2. Worker 的根租约由谁创建：`runOnce` 入口？还是 claim 事务内？后者会与 `REQUIRES_NEW` 冲突。
3. Session 保存失败（因关闭收尾被拒）时，用户可见行为是什么（响应当前请求 vs 会话未持久化）？是否需要「保存前探测」以避免半提交？
4. 嵌套子租约的上限与饥饿：一次请求内连接借还次数、`REQUIRES_NEW` 次数是否要计数/限流。

**候选与取舍**

- (C1) **根租约对象贯穿 + 子租约借用**：请求/作业/进程各建一个根，子连接只 `borrow()`；Session 保存与 `REQUIRES_NEW` 借同一根。
- (C2) **让 Session 保存与续租走「内部收尾通道」**：绕开准入但有白名单与审计。风险是被误用为普通旁路（与「不得借 Flyway 原池绕行」同性质）。
- (C3) **把 Session 保存移出租约覆盖范围**：等于承认最终保存不受保护，直接违背「持操作租约至最终 Session 保存结束」的目标。**排除**。

**建议方向**：C1 为默认；若某框架路径确实无法传递根租约（如容器级清理线程），则在 §4G 明确列为「不纳入」，而不是用 C2 悄悄放开。

### D. C1 双侧核验接入点

**现状**：`verifyInitial` 全程持 coordinator 锁，并在锁内读 DB 证据（§3 第 11 行）；证据源由调用方注入。若注入准入源 → 递归 `CONTROL_COORDINATOR_BUSY`。另有一个语义差异历史：登记组件允许无外部意图并标记 `UNRECORDED_EXTERNAL_INTENT`，而运行时 Verifier 缺意图即拒绝（已在历史验收记录，本轮不修）。

**待决问题**

1. 核验时机与频率：每请求一次？根租约创建时一次？周期性？缓存与失效条件（revision/epoch 变化如何触发重核）？
2. 证据连接来源：迁移专用原池？独立的只读连接/只读账号？若是原池，如何保证不被普通入口复用（编译期/静态检查/包可见性/独立 Bean 名）。
3. 核验与业务写的顺序：核验发生在租约创建内（先核验后借连接）还是在业务事务开始后（容易形成「持业务锁再核验」的反序）？
4. 核验读 DB 是否需要在同一「受保护生命周期」内，且不得与 `server_state` 的 `FOR SHARE`/`FOR UPDATE` 形成反向等待（需明确读核是否参与业务锁序）。
5. 允许态验收的 C1 前提：据交接逐项，C1 已由隔离 fixture＋测试域真 HTTP 通过，但**首次插入提交丢回执、跨进程、真实网络中断未验**。G0 需明确这些未验项在允许态验收中如何表达（拒绝或标注）。

**候选与取舍**

- (D1) **核验＝根租约创建的前置条件**：先核验（用不经过准入的证据连接），通过后才 `requireAdmission`＋取根租约；此后子连接复用根租约，不再重复核验。
  - 优点：无递归、无每连接重复文件校验；与锁序一致（先权威后业务）。
  - 代价：核验与业务写之间仍有窗口，需靠「根租约创建后权威不可被重写」的既有 `startup`/`CONTROL_INSTANCE_STALE` 机制压缩。
- (D2) **每次子连接重核**：最保守，但会命中 §4A 的递归与每连接文件历史校验代价，且关闭意图下必然拒收尾。**不可行**。
- (D3) **把 Verifier 降级为启动期一次性检查**：实现最简，但等于放弃运行期双侧证据，且与「受保护生命周期内读核双侧证据」要求不符。**不建议**。

**建议方向**：D1＋明确的证据连接隔离约定；核验失败一律「拒绝且不借连接」，并保留失败证据。

### E. UNKNOWN 处理

**现状**：`poisoned`＋`HELD` 保留＋`uncertainCompletion` 已实现「本 JVM 内不再接纳」，且 `close()`/`finally` 不会清洗（§3 第 6 行）。但毒化状态不落盘：进程退出后 OS 文件锁随进程释放，新进程可重新接纳同一 `writers.lock`。

**待决问题**

1. 毒化作用域：JVM？进程？实例（跨重启）？共享树/多进程场景下的判定依据是什么？
2. 排空判据：如何证明「已排空」——`INNODB_TRX` 为空？`writers.lock` 无持有者？子进程树已退出？产物未入 DB 引用的活动如何判定？
3. 解除/隔离流程归属：谁有权解除？是否需要新的控制权威记录（会触及七类生产决定边界）？在生产未授权前是否只允许「重启进程但保持关闭」？
4. 是否需要持久化 UNKNOWN 标记（C1 的 `initial-normal-unknown.json` 是唯一先例），以及它与 `localDeletionAllowed=false`、不得清理失败证据的关系。
5. rollback/close/finally 不得清洗 UNKNOWN 的**强制点**如何写死（现有为内存标志；是否需要在文件层加不可删标记，由哪个包负责）。

**建议方向**：G0 先定义「排空判据＋毒化作用域」并给出未验项清单；持久化毒化标记是否必要、是否落在控制权威内，作为用户决策点单独列出，不在 G0 内自行扩大到生产释放能力。

### F. 锁序与启动顺序

**现状**：锁序实现已有（§3 第 9 行）；冲突集中在启动：迁移走原池属显式独立许可，`BootstrapAdmin` 走准入源且是 `CommandLineRunner`，而 Web 容器在 runners 之前已监听（§3 第 8 行）。

**待决问题**

1. 迁移/初始化许可的载体：控制权威中的显式记录？独立许可文件？还是仅靠「默认关闭＋运维顺序」？
2. `BootstrapAdmin` 的连接来源：改用迁移专用原池（等于给它一个「非业务写」的合法通道）还是专用初始化 Bean？两种都会引入新的旁路，必须限定与审计。
3. 启动窗口内（runners 执行中、`server_state` 未就绪）到达的 HTTP 请求应得到什么响应（拒绝并审计，而不是 500 泄漏）；Worker 的 poll 在许可完成前如何不抢跑。
4. 两进程竞争「迁移 vs 业务启动」的互斥协议：谁先取锁、失败方等待还是退出、失败后是否允许自动重试。
5. 启动顺序与应用默认配置的关系：是否需要在准入启用且许可未满足时**启动失败**（现有 `RecoveryAdmittedDataSource` 构造期即要求权威可用，可作为先例）。

**建议方向**：G0 给出「显式许可 → 迁移 → 初始化 → 业务入口」的强制点清单与竞争结果表；许可缺失时业务入口一律拒绝，迁移与业务不得并行；不改默认开关。

### G. 框架边界（纳入/不纳入逐项）

| 路径 | 现状 | G0 需判定的归属 |
| --- | --- | --- |
| Session 最终保存 | 走准入源，位于 `SessionRepositoryFilter` 退出的 finally | 纳入根租约，租约覆盖至保存结束 |
| ERROR/ASYNC 分派 | 生产源码未见 `@Async` 控制器/执行器（仅渲染停止线程与 Worker 调度） | 过滤器若用 `OncePerRequestFilter` 需明确分派语义；无业务异步也要给出容器级证据或显式标为不纳入 |
| Session 过期清理 | `spring.session.jdbc.initialize-schema: never`；清理定时开关未配置 | 需实测是否有清理线程/独立连接；无法传递根租约则列「不纳入」并说明后果 |
| multipart 暂存 | 上限已配置，落盘位置未配置 | 解析发生在 `DispatcherServlet` 内（过滤器之后）→ 理论上被根租约覆盖，须实测确认；暂存文件清理归属需写明 |
| Worker poll/提醒 sweep | 独立非 Web 进程，`REQUIRES_NEW` 续租 | 独立根租约；跨进程关闭语义单列 |
| 渲染子进程 | 已有 Job/drain/renew，无租约 | 父租约覆盖到进程树退出确认；Linux 隔离另验 |
| 导入/提交/导出/来源证据文件 | 直接文件系统写，无租约 | 外围接线优先；不得改已验业务语义 |
| Flyway / BootstrapAdmin | 见 §4F | 独立许可，不并入普通入口 |
| 审计归档 / TEMP 归档 / 下载 GET 审计写 | 均为受保护写，未接 | 随对应入口批次排期，本提案不承诺 |
| Android 客户端 / demo 模块 | 不在服务端准入面 | 不纳入 |

## 5. B1 验收矩阵草案

执行顺序固定为：**完整应用冒烟 → 定向单测 → 真 HTTP/真 MySQL IT**。冒烟未通过不得进入 IT（沿用 C1 的停报规则）。DB 使用按隔离许可准备的**非空**库；应用 Flyway/Bootstrap 保持关闭；沿用既有专用端口与账号隔离方式，不新建原库连接。

### 5.1 用例组

| 组 | 前置 | 关键步骤 | 通过判据 | 证据形式 |
| --- | --- | --- | --- | --- |
| S0 完整应用冒烟 | 独立空 schema、专用端口 | 复用 [r09-c1-smoke.ps1](../../tools/r09-c1-smoke.ps1) 模式的**新轮次坐标**启动完整 Boot/Tomcat 与准入接线 | 启动无异常、无重复映射、上下文 active、正常关闭、端口释放 | 应用日志、runner 结果、marker |
| S1 允许态（双侧满足） | 非空隔离库＋可信原件满足 C1 双侧 | 真 HTTP：登录→普通 GET→CSRF 取用→受保护写→退出 | 全部成功；Session 最终保存落库；无新增放行 | HTTP 记录、DB 前后摘要、Session 行 |
| S2 缺侧拒绝 | 分别移走可信原件/库外完成/DB 记录 | 同 S1 请求序列 | 一律拒绝且不借连接、不写 Session、不产生半条业务记录 | 拒绝路径日志、DB 摘要不变 |
| S3 GET/CSRF/登录/退出异常 Session 写 | 非空隔离库 | GET 触发 Session 写；CSRF 端点；登录成功/失败；退出；构造错误响应 | 每类写入均受门禁；拒绝时不留半条会话；错误响应不泄漏内部结构 | HTTP 记录、Session 表核对 |
| S4 最终 Session 保存 × 关闭竞争（可控屏障） | 在最终保存前设屏障 | 卡住 Session 尾部再写入关闭意图；放行 | 已接纳请求完成收尾（或按 B3 兜底保持毒化，并如实记录）；新请求在 Session 写之前被拒 | 屏障日志、时序证据、两请求结果 |
| S5 嵌套连接/异常/UNKNOWN | 隔离库＋故障注入 | 请求内多连接借还；`REQUIRES_NEW` 路径；提交后丢回执注入；异常回滚 | 复用根租约不递归；UNKNOWN 保留且 later 成功 close/错误码不能洗掉；不宣称已释放 | 原始异常、锁状态、DB 摘要 |
| S6 关闭后新操作拒绝 | 写入关闭意图 | 新 HTTP 请求；Worker poll；文件写入口 | 新根操作一律拒绝；已接纳者按白名单收尾 | 拒绝码、计数、审计 |
| S7 回归 | 同构建 | 既有 Recovery 连接层断言（105 面）＋已验业务回归 | 原断言全部保持；无新增失败 | 单测/IT XML、摘要比对 |

### 5.2 现有单测与受影响回归（基线清单）

- 需保持绿的既有 Recovery 单测：`RecoveryWriterLeaseTest`、`RecoveryAdmittedDataSourceTest`、`RecoveryLeasedTransactionTest`、`RecoveryAdmissionVerifierTest`、`RecoveryAdmissionConfigurationTest`、`RecoveryControlIntentTest`、`RecoveryControlIntegrityTest`、`RecoveryControlJsonTest`、`RecoveryControlPathsTest`、`RecoveryControlReaderTest`、`RecoveryNormalIntentTest`、`InitialNormalEvidenceTest`、`InitialNormalStoreTest`、`InitialNormalVerificationTest`。
- 受影响回归面：`RecoveryAdmissionEntryChecks`（105 连接接纳/关闭竞争/UNKNOWN 面，C1 已复用过）、`C1InitialNormalHttpIT`（C1 真 HTTP 面）、认证/Session 面（`SecurityConfiguration` 过滤器顺序变化）、Worker 作业面（`BackgroundJob*IT`）、渲染进程面（`ExportRendererProcess*`）。
- 断言计数与历史红证据一律保留，不用新成功覆盖旧失败。

### 5.3 构建与运行命令（沿用既有工具，不新增编排器）

```text
# 定向单测（不含 *IT）
gradlew.bat -p server test --tests '*recovery*' --rerun-tasks --console=plain

# 隔离库上的真实 IT（OUTBOUND_DB_* 指向隔离测试库）
gradlew.bat -p server integrationTest --tests '*B1*IT' --rerun-tasks --console=plain

# 完整应用冒烟（新轮次坐标，先于 IT）
pwsh -NoProfile -File tools/r09-c1-smoke.ps1 -SmokeRun <未被占用的新轮次>
```

沿用既有约束：`--rerun-tasks` 必带（相同 `--tests` 过滤会 UP-TO-DATE）；PowerShell 特殊字符参数一律加引号或字符串数组；多行提交正文用 UTF-8 文件＋`--file`。

### 5.4 新增 IT 建议（仅建议，未创建）

- `B1SessionAdmissionIT`：S1–S4 的真 HTTP＋真 MySQL 用例组，含可控屏障（建议以测试域专用探针/`RecoveryAdmissionProbe` 的既有 seam 实现，而非改产品业务类）。
- `B1NestedLeaseIT`：S5 嵌套连接/`REQUIRES_NEW`/UNKNOWN 用例组。
- 断言口径：不得把单测、迁移或冒烟本身算作验收；一个组合 IT 不得按内部断言数量虚报成多项测试。

## 6. 拟新增/修改文件清单（B1 候选，未经授权不创建）

G0 本轮只新增本提案文档。下表是 B1 授权后**候选**改动面，逐条列出接口/调用关系/必要性/影响，实际以 G0 设计定案为准。

| 路径 | 类型 | 接口签名（建议） | 调用关系 | 必要性 | 影响范围 |
| --- | --- | --- | --- | --- | --- |
| `server/src/main/java/com/acrylic/outbound/recovery/OperationLease.java` | 新增 | `public final class OperationLease implements AutoCloseable { static OperationLease open(RecoveryControlLocation, Path checkpoint) throws ...; Connection borrow(...); void draining(); boolean closed(); }` | 由 `RecoveryRequestAdmissionFilter`/Worker 入口创建；由 `RecoveryAdmittedDataSource` 借用 | 提供根/子租约与收尾语义 | 新组件；被 DS/过滤器/Worker 引用 |
| `.../recovery/RecoveryAdmittedDataSource.java` | 修改 | 新增作用域感知取连接：作用域内 `borrow()` 复用根租约，作用域外保持现有 `acquire()` | `getConnection()` 分支 | 解决嵌套连接与递归 | 影响 105 连接层回归，需全量复跑 |
| `.../common/RecoveryRequestAdmissionFilter.java`（或置于 recovery 包） | 新增 | `extends OncePerRequestFilter`，顺序晚于 `RequestIdFilter`、早于 `SessionRepositoryFilter` | 请求进入时创建根租约并核验；响应前保证 Session 保存完成后释放 | 实现「Session 过滤器之前接纳」 | 过滤器链顺序变化，波及认证/Session 全部请求 |
| `.../recovery/RecoveryAdmissionGate.java` | 新增（可选） | `public final class RecoveryAdmissionGate { Result check(); }` | 统一封装 C1 双侧核验＋准入判定 | 避免各入口重复实现核验 | 依赖证据连接隔离约定 |
| `.../recovery/RecoveryEvidenceDataSource` 或限定符明确的证据 Bean | 新增（可选） | 只读、专用、不经准入 | 供 `RecoveryAdmissionQueries` 使用 | 消除递归与「借原池」扩散 | 与迁移原池隔离，须静态防误用 |
| `.../jobs/*`（Worker 根租约） | 修改 | `runOnce` 外层开根租约；`BackgroundJobLeases.renew` 借同一根 | Worker 调度 → 作业执行 | 关闭意图下续租/完成可收尾 | 影响作业面回归与跨进程语义 |
| `server/src/test/java/com/acrylic/outbound/recovery/B1SessionAdmissionIT.java` | 新增 | JUnit IT，见 §5.4 | 真实 HTTP＋真 MySQL | 承载 B1 验收 | 新测试面，不改产品测试守卫 |
| `docs/history/R09_C2_G0_DESIGN_<日期>.md` | 新增 | G0 设计定稿 | 本提案的下一份产出 | 记录定案与未决 | 文档面 |

**明确不改**：R05/R06/R07 已验业务类语义、`ConfirmedV1Adapter`、既有迁移与迁移版本、契约草案、默认开关（含七类生产决定与 TEMP 归档）、旧验证树与失败证据。

## 7. 前置检查与风险

本次已完成的只读前置（证据见 `.local/r09-c2-g0-proposal-20260928/`）：主树干净且与远端 0/0；无 `.git/index.lock`；19330/19434/19437/23308 无监听；无 C1 服务/IT 在途；`561/562/563` 无已用目录（561 为候选号，执行前仍需再次全树查重）；共享树 `r06-e2a-migration` 保持 detached `ea4d166` 与 97 条脏改动，状态列表 SHA-256 已留档，本轮**未**覆盖/同步/清理/reset/提交/合并。

| 风险 | 说明 | 缓解 |
| --- | --- | --- |
| 递归/重入死锁 | 核验或嵌套连接重入 coordinator 锁 | 证据连接不经准入；根租约创建前置核验（§4D） |
| 收尾被误当新业务 | 白名单过宽会变相放行 | 白名单可枚举、可审计；默认拒绝 |
| 单写者约束 | `writers.lock` 使同 JVM/跨进程只有一个持有者，测试并发场景需谨慎 | 每轮独立库/端口/坐标；执行前查重监听与写者 |
| 关闭能力未授权 | 关闭意图只能由测试注入构造 | G0 验收中明确「注入型关闭意图」性质，不外推线上关闭流程 |
| 文档/事实漂移 | 生成块的 git 字段是生成时快照，不做漂移校验 | 以 Git 实测为准；本提案已记录实测 SHA |
| 共享树污染 | 旧守卫绑定旧路径/库名/端口 | 不改旧树；如需同源验证先取前像并核对独占写权 |
| 跨进程/断电 | 毒化与锁状态不落盘 | 明确未验并保持拒绝；不作为允许态证据 |

## 8. 未验边界声明

1. C1 首次插入事务 commit 丢回执、跨进程 UNKNOWN、人工解除、真实网络中断、断电、Linux 持久性、现场原安装/原资料组合**均未验**；重启仅为同一 JVM 内完整上下文重建。
2. 本提案全部结论来自**只读源码核对与文档对照**：无 C2 实现、无单测/IT/真实 HTTP/新 attempt、无独立审查、未启动服务或数据库。
3. 递归借连接冲突为**源码级判断**（`verifyInitial` 持 coordinator 锁内读 DB ＋ 准入源 `acquire()` 重入），无动态失败证据；实际表现需在 B1 中以前置失败用例确认。
4. 过滤器精确顺序、Spring Session 清理线程是否存在、multipart 暂存落盘与清理归属、容器 ERROR/ASYNC 分派行为**均未实测**；本提案按「实现前必查」登记，不作为已验事实。
5. 跨进程语义（HTTP 与 Worker 两个 JVM 竞争同一 `writers.lock`）、Windows Job 之外 Linux 进程隔离、渲染子进程未知退出、Session 尾保存与关闭竞争的**组合**未验。
6. A3 剩余 21 类、526 专项、R07 抬头撤销、D 范围外组合、P1–P7、真机/Keystore/现场恢复与生产释放保持独立未验，不因本提案推进。

## 9. 边界与下一步

- 本轮严格守住：不改生产/测试/工具代码，不新增迁移，不改契约或默认配置；不操作原库/现网，不部署/发布/打标签/合 main；不开放七类生产决定、TEMP 生产归档与恢复释放能力（`localDeletionAllowed=false`）；不重开 D 扩测、A3 补跑、526 或 R07 抬头撤销专项。
- **完成本提案即收口**。等待用户对 G0 授权（范围、时间盒、是否允许新增组件与测试）后再执行 G0 设计；不自动进入 B1，不自行接线。
- 建议用户在授权 G0 时一并裁决：§4B 收尾白名单范围、§4E 毒化作用域与是否需要持久化标记、§4F 初始化许可载体与 `BootstrapAdmin` 连接来源、§4G 未纳入路径清单是否接受。
