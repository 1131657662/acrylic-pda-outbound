# R09 C2-G0 定向纠错与设计复验（2026-09-28）

> **后续状态（2026-09-28 晚，本条为纠错后追加的导航）**：本记录的 v2 修订经独立核对后，有**两处结论被推翻**（P2 空租约检查不构成互斥、模式变更必先完成独占控制推进的证明不成立），另有 P5、上下文回退与验收规格问题；已在[残余阻断补正与设计复验](R09_C2_G0_RESIDUAL_BLOCKERS_2026-09-28.md)中逐项补正，设计定稿更新为 v3。本记录中与本条冲突的表述以 v3 与残余阻断记录为准。
> **一处事实更正**：本记录 §4 曾把 v1 描述为“未覆写/依赖自动覆盖两个 `shouldNotFilter*`”，与 v1 原文不符——v1 已明确写为覆写为 `false`。v1 的真实缺陷是：把 Spring Session 自有 `OncePerRequestFilter` 与 Spring Web 同名类混同、未处理嵌套 ERROR 分派重跑、重分派时根复用语义未定义、S4a 屏障未落在真实保存路径。已在[残余阻断记录](R09_C2_G0_RESIDUAL_BLOCKERS_2026-09-28.md) §7 如实更正。

> 审查基线：`40d3702575aa25c5b70fc87beb76355421a27a1b`（v1 定稿）。本轮只做设计纠错与文档级复验：**零代码/迁移/契约/默认开关变更**，未编译、未跑单测/IT/HTTP/数据库、未启动应用，未分配 IT attempt / SmokeRun / 数据库 / 端口。产出见[设计定稿 v2](R09_C2_G0_DESIGN_2026-09-28.md)与[验收与阻断记录](R09_C2_G0_ACCEPTANCE_2026-09-28.md)。
> **判定口径**：B01–B06 的**设计反例已关闭**（设计级，可审查、可测试）；**运行行为全部未验证**。凡涉及“已通过/已生效”的表述只指设计一致性。

## 1. 前置检查结果

| 项 | 实测 | 证据 |
| --- | --- | --- |
| 分支/HEAD | `codex/development` / `40d3702575aa25c5b70fc87beb76355421a27a1b`（= 审查基线，无后续提交需解释） | `01-git-head.txt` |
| 工作区/远端 | `git status --porcelain -uall` 为空；`HEAD...origin` = `0 0`；无 `.git/index.lock`；`git diff --check` 退出 0 | `01-git-*.txt` |
| 活动会话/写者 | `Win32_Process` 过滤本树/共享树仅本会话自身；受控源码近 90 分钟 0 改动；单写者确认（不以端口空闲为唯一依据） | `04-writers.txt` |
| 监听/进程 | 19330/19434/19437/23308/8080/8081/9090 无监听；19387＝DSH、3306＝系统 mysqld、Gradle daemon 未动 | `05-listeners.txt`、`05-processes.txt` |
| 共享树 | `r06-e2a-migration` detached `ea4d166`；30 修改 + 67 未跟踪 = 97；状态列表哈希与本轮前快照一致 | `03-shared-tree-*.txt` |
| 138 项历史脏文件 | 对 `dsh-handoff-20260928-ec4be3b/dirty-file-hashes-initial.json` 逐项重算 SHA-256：**138/138 一致，mismatch 0，missing 0**（本轮作业后再核仍 0 变化） | `02-shared-tree-hash-verify.txt`、`17-shared-tree-post-status.txt` |
| 证据目录查重 | `.local/r09-c2-g0-correction-20260928` 原不存在（未覆盖任何旧证据） | `00-batch-dir.txt` |
| 编号 | 本轮不分配 attempt/SmokeRun/端口/数据库；`561` 只是历史候选 | 同上前置文件 |

## 2. 依赖只读核对（B02/B04 的事实来源）

`javap`（JDK 21.0.11，`C:\Program Files\Java\jdk-21.0.11\bin\javap.exe`）只读反编译，未启动应用、未写入依赖。

| 依赖 | 路径 | SHA-256 |
| --- | --- | --- |
| spring-session-core 3.5.7 | `C:\Users\qq113\.gradle\caches\modules-2\files-2.1\org.springframework.session\spring-session-core\3.5.7\83beec2585d07d22fbbeec80288a72629551039\spring-session-core-3.5.7.jar` | `AE4D07D034693821030FA913D822706CD13401AEF492635A3A73CAAC2ACA2ABD` |
| spring-session-jdbc 3.5.7 | `…\org.springframework.session\spring-session-jdbc\3.5.7\2d894eb8c91741d19f2efda4a5095dd353b977d3\spring-session-jdbc-3.5.7.jar` | `2CFECC01209CC6EB880E21EAFF70332237ACC863536D08D1BAFF7B26B4B37170` |
| spring-web 6.2.19 | `…\org.springframework\spring-web\6.2.19\f1dc7b238611aeaf9e256a230aa089057cf0e5b3\spring-web-6.2.19.jar` | `CA88E364ECEBEE61163E2260E8A20584138ECA74E28A050D6A2EF6302BAD2F2D` |
| spring-boot-autoconfigure 3.5.16 | `…\org.springframework.boot\spring-boot-autoconfigure\3.5.16\87db09240595b49b530b230252311836abd21df1\spring-boot-autoconfigure-3.5.16.jar` | `0198331231E9BF872A135741D59C6D1F9E9007FF207A1CA910179C550BB12BFC` |

命令与原始输出（保留在证据目录）：

```text
javap -p -constants -classpath <core>  org.springframework.session.web.http.SessionRepositoryFilter
javap -p -c         -classpath <core>  org.springframework.session.web.http.OncePerRequestFilter
javap -p -c         -classpath <core>  org.springframework.session.web.http.SessionRepositoryFilter
javap -p -c         -classpath <web>   org.springframework.web.filter.OncePerRequestFilter
javap -p -v         -classpath <jdbc>  org.springframework.session.jdbc.config.annotation.web.http.JdbcHttpSessionConfiguration
javap -p -v -c      -classpath <jdbc>  org.springframework.session.jdbc.JdbcIndexedSessionRepository
javap -p -v         -classpath <auto>  org.springframework.boot.autoconfigure.session.JdbcSessionProperties
```

关键原始输出（逐条摘录，非转述）：

1. `SessionRepositoryFilter`：`extends org.springframework.session.web.http.OncePerRequestFilter`；`public static final int DEFAULT_ORDER = -2147483598;`；方法含 `doFilterInternal`、`doFilterNestedErrorDispatch`。
2. `SessionRepositoryFilter.doFilterInternal` 字节码：`… invokeinterface FilterChain.doFilter → aload_0…invokevirtual SessionRepositoryRequestWrapper.commitSession` 两次（正常路径与异常表 `38 48 56 any` / `56 58 56 any` 分支），即 **链返回后与异常路径都提交 Session**，提交发生在过滤器体内。
3. `SessionRepositoryFilter.doFilterNestedErrorDispatch`：直接 `invokevirtual doFilterInternal`（嵌套 ERROR 分派重跑并提交）。
4. Spring Session `OncePerRequestFilter.doFilter`：属性名 `getClass().getName()+".FILTERED"`；`已过滤 && DispatcherType==ERROR → doFilterNestedErrorDispatch`；`已过滤 && 非 ERROR → chain.doFilter`；未过滤 → `setAttribute → doFilterInternal → removeAttribute(finally)`。**无 `shouldNotFilter*` 方法**。
5. Spring Web `OncePerRequestFilter`：`shouldNotFilterAsyncDispatch()` → `iconst_1; ireturn`（默认 true）；`shouldNotFilterErrorDispatch()` → `iconst_1; ireturn`（默认 true）；`skipDispatch` 用 `isAsyncDispatch` 与 `jakarta.servlet.error.request_uri`；`doFilterNestedErrorDispatch` 默认仅 `chain.doFilter`。
6. `JdbcHttpSessionConfiguration`：字段 `private String cleanupCron;`，`sessionRepository()` 内 `invokevirtual JdbcIndexedSessionRepository.setCleanupCron`。
7. `JdbcIndexedSessionRepository`：`public static final String DEFAULT_CLEANUP_CRON = "0 * * * * *";`；`afterPropertiesSet`：`if (!"-".equals(cleanupCron)) { createTaskScheduler()（线程名前缀 "spring-session-"）→ initialize() → schedule(runnable, new CronTrigger(cleanupCron)) }`；存在 `cleanUpExpiredSessions()`。
8. Boot `JdbcSessionProperties`：`DEFAULT_CLEANUP_CRON = "0 * * * * *"`（`ConstantValue`）。
9. Python 侧只读核对：`ops/control_history.py:71` 与 `ops/control_paths.py:61-63` 对 `identity.json` 使用**精确字段集** `{formatVersion installationId restoreRoots initialCommandSha256}`；`ops/control_model.py:6-8` 对 command 使用精确字段集且状态机规定首记录 `phase=QUIESCING`；`ops/control_store.py:38-53` 的 `apply()` 顺序为 `coordinator 锁 → prepare(意图+admission) → exclusive(writers) → 提交历史/指针`。

## 3. B01 闭合合法的初次租约权威

- **原断言（v1）**：“接纳＝DB 证据 + P2 文件证据 + P3 协调锁内身份/协议/毒化/关闭意图校验”，并称“登记责任只在离线协调器”；把 P3 仅当作租约权威，未区分初次安装与恢复，且 v1 §4.1 S2 **没有** `requireUnclosed`，关闭权威路径是可自由传入的参数（`C1HttpController` 传入 `base/closed-authority`）。
- **源码/依赖证据**：`InitialNormalStore.requireUnclosed` 只做 `!Files.exists(closed)` 与重叠校验（`InitialNormalStore.java:84-93`）⇒ 传不存在的目录即“通过”；`RecoveryControlHistory.load` 要求 `history/` 非空且 `identity.initialCommandSha256 == records[0].commandSha256`（`RecoveryControlHistory.java:17-18`），而状态机强制首记录 `phase=QUIESCING`（`RecoveryControlCommand.java:45-46`、`control_model.py:44-46`）⇒ **任何 P3 权威的引导记录必然是 QUIESCING 恢复语义**，v1 无法区分“初次允许态”与“恢复会话”；`RecoveryControlRecords.identity`/`control_history.load` 都是精确字段集 ⇒ 新增绑定必须升版。
- **反例时序（v1 下成立）**：(1) 攻击者/误配以任意恢复型权威启动；(2) 传 `closedAuthority = <不存在路径>`；(3) 接纳 S2 通过（无 requireUnclosed），S3 只查协议/意图；(4) 得到根租约并在“关闭权威其实存在”的现场写入 → **关闭事实被绕过**。
- **修订规则（v2 §3.2–§3.5）**：`identity.json` 升 v2，新增不可变 `authorityKind ∈ {INITIAL_NORMAL_INSTALL, RECOVERY}` 与 `closedAuthority`；初次接纳额外要求：kind=INITIAL_NORMAL_INSTALL、历史**恰好 1 条** QUIESCING 记录、锚点=1、空意图（`targetRevision=1` 且 `ZERO_HASH`）、且该记录 command 的 installationId/commandId/epochId/evidenceSha256/manifestSha256/snapshotId/recoverySessionId/warehouses 哨兵/零损失窗口/requestedAt 逐字段绑定可信原件 P1；`closedAuthority` 必须与身份逐字一致、**父目录必须存在**、条目及其 `.pending` 变体必须不存在（`requireUnclosed` 在登记与接纳两条路径都强制）；v1 identity 不得用于初次接纳。登记路径额外要求 P5 `leases/` 为空（禁止应用在跑时登记）。
- **未来测试及唯一预期**：见设计 §11 场景 10/11 与 §10.2 S2——恢复型权威 → `RECOVERY_INITIAL_AUTHORITY_REQUIRED`／多记录 → `_INVALID`；`closedAuthority` 不符或父目录缺失 → `RECOVERY_CLOSED_BINDING_UNTRUSTED`；条目存在 → `INITIAL_NORMAL_CLOSED_AUTHORITY`；四种情形均**零业务写、零根登记、零 P5 记录**。
- **本轮判定**：**设计反例已关闭**（结构上不可能用恢复型权威或不存在路径满足初次接纳）；运行行为未验证。

## 4. B02 按实际依赖纠正请求生命周期

- **原断言（v1）**：“过滤器在 `finally` 内 `enterFinalizing()`，`requestDestroyed` 释放根”，并把 S4a 屏障放在“SERVING→FINALIZING 后首次子操作登记处”，称其代表最终 Session 保存；还声称 Spring Session 与我们的过滤器都属 Spring Web 的 `OncePerRequestFilter` 语义。
- **源码/依赖证据**：§2 第 1–5 条。关键差异三处：①`SessionRepositoryFilter` 用的是 **Spring Session 自己的** `OncePerRequestFilter`（无 `shouldNotFilter*`，且覆写了嵌套 ERROR 分派）；②Session 提交在**其方法体内、链返回之后与异常路径**（不是容器回调，也不在我们过滤器的 finally 之后）；③**我们自己的**过滤器若延用 Spring Web 默认（两个 `shouldNotFilter*` 返回 `true`）就会被跳过——v2 明确覆写为 `false`，但 v1 的缺陷不在此（v1 也写了 `false`），而在于①把两类同名 `OncePerRequestFilter` 混同、②未处理嵌套 ERROR 分派重跑、③重分派时根复用语义未定义。
- **反例时序（v1 下成立）**：(1) 请求进入我们的过滤器并登记根；(2) 链内 `response.sendError`；(3) 容器发起 ERROR 分派，Spring Web 默认 `shouldNotFilterErrorDispatch()==true` 使我们的过滤器**不再进入**（若异步/独立线程执行该分派，则线程未绑定）；(4) 该分派内 `SessionRepositoryFilter.doFilterNestedErrorDispatch → doFilterInternal → commitSession()` 执行 DB 写；(5) v1 的屏障/断言放在外层 finally 之后的连接借用上，**永远观察不到这一步**，S4a 会把“非真实尾保存”当成通过。
- **修订规则（v2 §6.3）**：过滤器显式覆写 `shouldNotFilterErrorDispatch()`/`shouldNotFilterAsyncDispatch()` 为 `false`；根令牌写进**请求属性且释放前不移除**，重分派（ERROR/ASYNC/nested）**复用同一根**并为当前线程补 `bindThread`（保证 ERROR 分派内的 Session 提交仍被覆盖）；释放仍只在 `requestDestroyed`；`finally` 只 `enterFinalizing()`；S4a 屏障改为**测试专用 `SessionRepository` 装饰器**在 `save()` 阻塞（真实保存路径），S4a（正常收尾成功）与 S4b（新根拒绝）保持两个独立用例。
- **未来测试及唯一预期**：§10.2 S4a 期望“请求 200 + `SPRING_SESSION` 行在场 + 控制推进 `CONTROL_WRITERS_ACTIVE` + 意图在场且指针未推进”；S8 期望“错误分派 Session 写覆盖且根数不增（复用）”“异步未绑定线程 503 `RECOVERY_OPERATION_FORBIDDEN`”。
- **本轮判定**：**设计反例已关闭**（生命周期依实际依赖重写，屏障落在真实路径）；运行行为未验证。

## 5. B03 消除无绑定回退造成的歧义

- **原断言（v1）**：“无令牌时保持现有连接级租约路径”，未区分“本来就无上下文”与“令牌失效/上下文丢失”。
- **源码/依赖证据**：`RecoveryAdmittedDataSource.getConnection()` 目前只有一条“取租约”路径（`RecoveryAdmittedDataSource.java:35-48,69-81`）；`RecoveryWriterLease.acquire` 每次执行 `requireAdmission`（`RecoveryWriterLease.java:21`）。若在其前面加“有令牌则复用、无令牌则走旧路径”，失效令牌（被释放/身份不符）就会**静默降级**为新建连接级租约——在关闭意图下它会失败，但在正常态下它会让“已释放的令牌仍能取得写权限”。
- **反例时序（v1 下成立）**：(1) 请求根释放（或令牌被错误持有）；(2) 框架/异步代码仍持令牌取连接；(3) 判定“令牌查不到 → 无绑定 → 走旧路径”；(4) 连接级 `requireAdmission` 在无关闭意图时**通过** ⇒ 出现一个不属于任何已接纳根的新写者，违反“禁止借旧令牌开展新业务/新根拒绝”的目标。
- **修订规则（v2 §6.2）**：给出 A–F 六行判定表；分支 A（旧连接级路径）**仅当**“线程无绑定 **且** `RequestContextHolder` 无可观察请求”时进入；C 失效令牌、D 请求上下文丢失（有请求且该请求带根令牌但线程未绑定）、E 未绑定异步线程一律**拒绝**（`CONTROL_OPERATION_RELEASED` / `RECOVERY_OPERATION_CONTEXT_LOST`），禁止回退；F 越集子操作拒绝。105 语义保留：A 的判定比 v1 更严，因此不会把 D/E 误判成 A。
- **未来测试及唯一预期**：S7-③/④ 期望唯一码且**零业务写**，并用探针断言“未走分支 A”（避免回退被静默掩盖）；105 回归 `RecoveryAdmissionEntryChecks` 全绿证明分支 A 语义未变。
- **本轮判定**：**设计反例已关闭**；运行行为未验证。

## 6. B04 纠正 Session 清理边界

- **原断言（v1）**：“`spring.session.jdbc.cleanup-cron` 未配置 ⇒ 本配置下无清理任务”，“不纳入 B1”。
- **源码/依赖证据**：§2 第 6–8 条。清理**默认开启**：`DEFAULT_CLEANUP_CRON = "0 * * * * *"`（Spring Session 与 Boot 两侧同值），`afterPropertiesSet()` 在 `cleanupCron != "-"` 时创建自有 `spring-session-` 线程并按 cron 调度 `cleanUpExpiredSessions()`；该写路径不经 `ServerState` 守卫。v1 的“不存在”结论**事实错误**。
- **反例时序**：(1) 准入启用、清理保持默认；(2) 每分钟 `spring-session-*` 线程写 `SPRING_SESSION`；(3) 该写不属于任何已接纳根、不受关闭意图约束、也不受租约覆盖；(4) 关闭/排空语义被这条隐形写者破坏。
- **修订规则（v2 §8.3）**：承认默认存在；**生产默认配置本轮不改**；B1 隔离测试**必须**显式 `spring.session.jdbc.cleanup-cron=-` 并**验证实际生效**（无 `spring-session-*` 线程、无该仓库调度任务）；新增 B1 启动守卫：准入启用且清理未禁用 ⇒ 启动失败 `CONTROL_SESSION_CLEANUP_UNCOVERED`（防止未覆盖写者随生产配置悄然开启）；生产默认清理路径单列为**未覆盖入口**，后续批次须为其建立自有根（kind=`SESSION_CLEANUP`、子操作 `SESSION_WRITE`、显式 `bindThread`），本轮不补实现。
- **未来测试及唯一预期**：S9——准入启用 + 清理未禁用 ⇒ **启动失败**；准入启用 + `-` ⇒ 启动成功且无 `spring-session-*` 线程（冒烟断言⑥）。
- **本轮判定**：**设计反例已关闭**（不再以“未配置 cron”宣布路径不存在）；运行行为未验证。

## 7. B05 闭合 UNKNOWN 无标记分支

- **原断言（v1）**：UNKNOWN 时“尽力发布 P3 `writers.poison.json`”；若 `pending` 写入失败则“进程 fail-stop，残余缺口需人工隔离”。这等于把**跨进程拒绝保证**建立在“故障时写入成功”上。
- **源码/依赖证据**：L2 `writers.lock` 是**共享**锁（`RecoveryWriterLease.java:39`，`tryLock(0,1,true)`）⇒ 进程死亡即释放，不能作为耐久阻断；L1 `poisoned` 仅本 JVM 内存（`RecoveryWriterLease.java:11,63`）⇒ 崩溃后丢失；P3 追加文件会让 Python 独立消费者看到未知产物（`control_history.py:50-66` 允许集固定），v1 方案还额外引入双侧协议改动与“Java 写了新文件、Python 判为未知产物”的不一致。
- **反例时序（v1 下成立）**：(1) 事务提交结果未知；(2) 磁盘满/权限不足使 `writers.poison.json.pending` 写入失败；(3) 本进程 fail-stop；(4) 另一进程仍在服务、新进程可启动 ⇒ **无任何跨进程阻断依据**，污染写者与新写者并存。
- **修订规则（v2 §7.2/§7.3）**：改为**故障前**建立依据——P5 操作租约目录：进程启动即创建 `processes/<processIdentity>.json` 并持**进程生命周期独占锁**；每次接纳在 P3 临界区内创建 `leases/<operationId>.json`（创建失败即拒绝接纳）；UNKNOWN 时**不退役**该租约并 fail-stop（`Runtime.halt`），OS 释放进程锁 ⇒ 该租约对所有其他进程与新进程成为**孤儿**，孤儿判定在接纳 S3-g 与启动阶段 0 内执行（探活仅靠文件锁持有性，不依赖时间）。阻断依据因此**不依赖任何故障时写入成功**；故障时的 `unknown.json` 仅为审计。阻断范围覆盖要求四种情形：标记无法创建（阻断依据已预先存在）、进程死亡（孤儿）、其他进程仍运行（其新根被拒）、新进程启动（启动失败）。**阻断保证与解除权限分开**：孤儿记录的清除属生产释放能力，未授权、不实现、不新增解除端点，但“未授权”的是解除，不是阻断。
- **未来测试及唯一预期**：S5/S6——UNKNOWN 后同 JVM 新请求拒绝、第二 JVM **启动失败**、孤儿判定码 `RECOVERY_LEASE_OUTSTANDING`；另测“退役失败保留记录 → 后续拒绝”的方向正确性。
- **本轮判定**：**设计反例已关闭**（不再存在“无标记即无阻断”的分支）；孤儿清除的运行流程未验证、未授权。

## 8. B06 补齐接纳线性化证明

- **原断言（v1）**：以“意图与登记同处协调锁临界区”“再次读取一次权威”“窗口较小”说明无检查后窗口，未列出事实可变性与框架写范围。
- **源码/依赖证据**：意图写者与控制推进的锁路径（`control_store.py:38-53`、`control_admission.py:14-31`）；P2 写者与锁（`InitialNormalStore.java:39-47,72-82`）；模式写者与事务内守卫（`RecoveryProjectionRows.java:14,64`、`ServerState.java:21-33`）；**框架写不经该守卫**（§2 第 6–8 条，`JdbcIndexedSessionRepository` 自持 `JdbcTemplate`）。
- **反例时序（v1 下成立）**：(1) 接纳读模式=NORMAL；(2) 恢复链投影把 `service_mode` 置为非 NORMAL；(3) 本请求随后的 Session 保存**绕过** `ServerState` 守卫而提交；(4) v1 仅以“再读一次/业务写守卫”作答，无法排除该情形。
- **修订规则（v2 §5）**：给出事实表（可变性/写者/所持锁/失效方式/登记时校验位置）＋四段证明：①P3 意图与登记同临界区（互斥，非窗口）；②P2 UNKNOWN 与接纳的跨根互斥（登记要求 P5 `leases/` 为空，故应用在服务时不可能产生 UNKNOWN）；③模式变化与框架写用“推进必须独占写者锁 + 根租约覆盖最终 Session 保存 + 推进后初次接纳条件永久失效”三段式，并把框架写纳入证明范围；④孤儿租约与登记（故障前建立 + 同临界区检查 + fail-stop 使孤儿对所有后续接纳可见）。同时列出显式前提（禁止直接改文件/SQL 改状态，属项目既有口径）与唯一非阻断情形（临界区先于孤儿完成的“故障前已接纳”操作）。
- **未来测试及唯一预期**：§5.6 五项并发反例（推进 vs 接纳、P2 UNKNOWN vs 接纳、模式翻转 vs 框架写、UNKNOWN vs 后续接纳/新进程、令牌校验 vs 释放），每项给出唯一预期。
- **本轮判定**：**设计证明已补齐**（不再使用“窗口小/再读一次/仅引用业务守卫”）；运行行为未验证。

## 9. 顺带修正的描述性错误

| # | v1 错误 | v2 修正 |
| --- | --- | --- |
| 1 | 把 `writers.lock` 称为“写者独占锁” | §2 三层锁表：L1 JVM 引用、L2 跨进程**共享**租约、L3 控制推进**独占**（`coordinator.lock` ＋ 控制推进对 `writers.lock` 的独占尝试） |
| 2 | 锁接口描述含糊（未写前置条件） | §2 表格：`locked` / `lockedWaiting` / `acquire` 的签名、前置条件、误用结果（v2 曾列 `acquireLocked`，v3 已删除：`acquire` 本身就是“调用方须先持协调锁”，两者语义相同） |
| 3 | P2 允许集只说“保持不变” | §3.3 逐名列出 7 个允许名（含三种 `.pending`），并明确 B1 不得增删 |
| 4 | Python 文件清单缺项 | §3.6 第 9–16 项：`control_paths.py`、`control_history.py`、`control_model.py`、`control_store.py`、新增 `control_initial.py`/`control_cli.py`、夹具与既有 3 个测试文件、R09 协议文档 |
| 5 | 章节引用错位/内容重复 | v2 重排为 §0–§14，交叉引用逐处校正；原 v1 段落与 Git 历史可对照 |
| 6 | 称“P3 新增 `writers.poison.json`” | 删除该设计（P5 取代），P3 允许集与 Python `layout` 均**无需**改动 |
| 7 | 称 `InitialNormalDisk` 需泛化为通用发布器 | 取消：P5 使用独立发布器，`InitialNormalDisk` 不改（保住 `InitialNormalStoreTest` 既有可观察行为） |
| 8 | 观察到的跨语言不一致（未修，仅登记） | Python `control_history.layout` 允许集**不含** `normal-admission.json`，而 Java `RecoveryControlHistory.layout` 含 `RecoveryNormalIntent.FILE`：若 P3 出现该文件，Python 独立消费者会判为未知产物。属既有不一致，本轮不改代码；已列入 B1 前需核对项（初次路径不使用该文件） |

## 10. 本轮实际运行 / 未运行

**实际运行**：§1 前置检查全部项、§2 依赖 `javap` 只读核对（命令与原始输出留档）、Python 协议文件只读阅读、文档链接检查（正文与原文归档分别报告，见 `08-doc-links.txt`）、`git diff --check`、`tools/version_facts.ps1 -Write` 后 `-Check`（看板当前区在本轮更新后为 **45 行 / 3234 字符**，在 100 行/8000 字符上限内；此前“40 行/2310 字符”是定稿 v1 时的旧值，已作废）。

**证据时点更正**：本记录所在证据目录的 `01-git-status.txt` 采集于**该轮文档修改之后**（内容含 2 个已修改文档与 1 个未跟踪文档），不能作为“前置工作区干净”的证据；该轮真正的前置干净事实来自其首条只读命令的实时输出，当时未落盘为独立文件。此更正不补造历史快照，详见[残余阻断记录](R09_C2_G0_RESIDUAL_BLOCKERS_2026-09-28.md) §1。

**未运行**：编译、单测、IT、真实 HTTP、数据库供给、完整应用冒烟、跨进程与断电实验、Linux 持久性、现场原资料组合。§10 的全部 B1 用例与 §5.6 并发反例均为**待 B1 授权后执行**，不计入本轮通过项。

## 11. 结论

**B01–B06 的设计反例已关闭，G0 设计定稿为 v2（内部一致、可约束 B1 实现）；运行行为全部未验证，B1 尚未实施、待独立授权。** 保留的边界：生产默认 Session 清理路径为未覆盖入口（后续批次）、孤儿租约的解除属生产释放能力（未授权）、C1 首次插入提交丢回执／独立进程重启／真实网络故障／断电／Linux 持久性／现场原资料组合继续单列未验。未发现新的真实产品缺陷（本轮未做动态验证）。
