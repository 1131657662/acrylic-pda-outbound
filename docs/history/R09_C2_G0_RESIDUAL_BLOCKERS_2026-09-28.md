# R09 C2-G0 残余阻断补正与设计复验（2026-09-28）

> 审查基线：`da4d56a812e433e299bb0f9088928b8c4bfe5ecf`（v2 定稿）。本轮只做设计补正与文档级复验：**零代码/迁移/契约/默认开关变更**，未编译、未跑单测/IT/HTTP/数据库、未启动应用或故障进程，未分配 IT/SmokeRun/端口/数据库。
> **判定口径**：R1–R5 **逐项**给出“已关闭（设计级）”或“保留阻断（含原因）”；**运行行为全部未验证**。v2 中被独立核对推翻的两处结论（P3 意图证明的适用范围、模式变更与框架写互斥的证明）在本轮**撤回并重写**，不保留原措辞。

## 1. 前置检查（修改文档之前采集）

| 项 | 实测 | 证据 |
| --- | --- | --- |
| 分支 / HEAD | `codex/development` / `da4d56a812e433e299bb0f9088928b8c4bfe5ecf` | `00-pre-head.txt` |
| 实际远端 | `git ls-remote origin refs/heads/codex/development` = `da4d56a…`（与本地 HEAD 一致） | `00-pre-remote.txt` |
| 工作区 / 锁 / 差异 | **修改前** `status` 为空、`index.lock` 不存在、`HEAD...origin` = `0 0`、`git diff --check` 退出 0 | `00-pre-status.txt`、`00-pre-status-count.txt`、`00-pre-index-lock.txt`、`00-pre-diff-check.txt`、`00-pre-left-right.txt` |
| 活动会话/写者 | 仅本会话进程引用本树；受控源码近 90 分钟 0 改动 | `03-pre-writers.txt` |
| 监听/进程 | 19330/19434/19437/23308/8080/8081/9090 无监听；19387＝DSH、3306＝系统 mysqld、Gradle daemon 未动 | `04-pre-listeners.txt` |
| 共享树 | detached `ea4d166`、30 修改＋67 未跟踪＝97；状态列表哈希与留档一致 | `01-pre-shared-tree-*.txt` |
| 138 项历史脏文件 | 逐项重算：**138/138 一致、mismatch 0、missing 0**（作业后复检见 `09-post-*`） | `02-pre-138-hash-verify.txt` |
| 证据目录 | `.local/r09-c2-g0-residual-20260928` 原不存在（未覆盖旧证据） | 批次指针文件 |

**对上一轮证据口径的纠正**：上一轮 `.local/r09-c2-g0-correction-20260928/01-git-status.txt` 的采集时点在**该轮文档修改之后**（内容含 2 个已修改文档与 1 个未跟踪文档），因此它**不能**支持“前置工作区干净”的表述。该轮真正的前置干净事实来自其首条只读命令的实时输出，但当时没有落盘为独立文件；本记录不补造该快照，只如实注明采集时点。本轮已在**修改任何文档之前**落盘 `00-pre-status.txt`（空），作为可核查的前置证据。

## 2. 依赖只读核对（本轮新增/复用的实际依赖事实）

`javap`（JDK 21.0.11）只读反编译，命令与原始输出留档。

| 依赖 | SHA-256 | 关键原始输出 |
| --- | --- | --- |
| spring-session-core 3.5.7 | `AE4D07D034693821030FA913D822706CD13401AEF492635A3A73CAAC2ACA2ABD` | `SessionRepositoryFilter.DEFAULT_ORDER = -2147483598`；`commitSession` 在 `doFilterInternal` 体内（异常表 `38 48 56 any`）；`doFilterNestedErrorDispatch` → `doFilterInternal`；Spring Session 自有 `OncePerRequestFilter.doFilter` 对已过滤请求按 `DispatcherType` 分支，**无** `shouldNotFilter*` |
| spring-web 6.2.19 | `CA88E364ECEBEE61163E2260E8A20584138ECA74E28A050D6A2EF6302BAD2F2D` | `shouldNotFilterAsyncDispatch()`/`shouldNotFilterErrorDispatch()` 均 `iconst_1; ireturn`（默认 true，跳过）；`skipDispatch` 用 `isAsyncDispatch` 与 `jakarta.servlet.error.request_uri` |
| tomcat-embed-core 10.1.55（jakarta.servlet API） | `82473F841824095E03F2C938C18707E44E388E34FB1735B2B338BB4F22FD54F0` | `jakarta.servlet.FilterRegistration` 只有 `addMappingFor*`/`get*Mappings`，**没有 order API**；`ServletContext.getFilterRegistrations()` 返回 `Map<String, ? extends FilterRegistration>` ⇒ 不能用它断言顺序 |
| spring-boot-autoconfigure 3.5.16 | `0198331231E9BF872A135741D59C6D1F9E9007FF207A1CA910179C550BB12BFC` | `SessionRepositoryFilterConfiguration.sessionRepositoryFilterRegistration(...)` 用 `DelegatingFilterProxyRegistrationBean`，`setDispatcherTypes(getDispatcherTypes(props))`、`setOrder(props.getServlet().getFilterOrder())`；`SessionProperties$Servlet` 构造函数把 `filterOrder` 置 `-2147483598`、`filterDispatcherTypes` 置 `HashSet{ASYNC, ERROR, REQUEST}` |
| spring-session-jdbc 3.5.7 | `2CFECC01209CC6EB880E21EAFF70332237ACC863536D08D1BAFF7B26B4B37170` | `JdbcIndexedSessionRepository.DEFAULT_CLEANUP_CRON="0 * * * * *"`；`afterPropertiesSet` 在 `cleanupCron != "-"` 时建自有调度器（线程前缀 `spring-session-`）并调度 `cleanUpExpiredSessions()` |
| spring-boot 3.5.16 | 见 `07-filterOrder-init.txt` 记录 | `FilterRegistrationBean`/`AbstractFilterRegistrationBean` 提供 `getOrder()`/`getDispatcherTypes()`（用于冒烟的注册配置断言） |

命令（完整输出见证据目录）：

```text
javap -p -constants -classpath <core> org.springframework.session.web.http.SessionRepositoryFilter
javap -p -c         -classpath <core> org.springframework.session.web.http.OncePerRequestFilter
javap -p -c         -classpath <core> org.springframework.session.web.http.SessionRepositoryFilter
javap -p -c         -classpath <web>  org.springframework.web.filter.OncePerRequestFilter
javap -classpath    <servlet> jakarta.servlet.FilterRegistration
javap -classpath    <servlet> jakarta.servlet.ServletContext
javap -p -c         -classpath <auto> org.springframework.boot.autoconfigure.session.SessionRepositoryFilterConfiguration
javap -p -constants -classpath <auto> 'org.springframework.boot.autoconfigure.session.SessionProperties$Servlet'
javap -p -v -c      -classpath <jdbc> org.springframework.session.jdbc.JdbcIndexedSessionRepository
```

## 3. R1 真正串行化离线登记与首次接纳

- **具体反例（v2 成立）**：A 完成 S2（读 P2 证据、释放 P2 锁）→ 离线登记者取 P2 锁、检查 P5 为空（A 尚未进 S3，无租约）→ 登记出现未确认结果并发布 P2 UNKNOWN → A 取 P3 锁进入 S3 并登记根。双方都遵守 v2 设计，没有绕过工具或直接改文件。
- **涉及的状态与锁**：P2 证据（写者＝离线登记，锁＝P2 `coordinator.lock`）；P5 租约集合（写者＝接纳，v2 只在 P3 锁内写）；P3 权威（写者＝控制推进，锁＝P3 `coordinator.lock`）。v2 的缺陷是**接纳的 S2 与 S3 分属两把不同的锁**，登记只与 P2 侧互斥。
- **修订后的强制点**：①接纳的 S2 与 S3 合入**同一把 P3 协调锁**（S1 仍在锁外做 DB 证据读）；②离线登记的全部步骤（P2 读、DB 事务、P5 无在途检查、完成/UNKNOWN 发布）也在**同一把 P3 协调锁**内（**写侧改造**：`InitialNormalRegistration` 新增 P3 位置与 `leaseRoot` 参数，先取 P3、锁内再取 P2）；③锁序固定 P3(1)→P2(2)→P5 进程文件(3，瞬时)，`RecoveryControlLock` 改为按根信号量＋按线程根集合＋层号逆序拒止（`CONTROL_LOCK_ORDER_VIOLATION`）。
- **为何该交错被拒绝/合法串行化**：P3 `coordinator.lock` 是跨进程独占 OS 锁 ⇒ 接纳与登记全序。登记在前 ⇒ 其完成/UNKNOWN 已发布，接纳 S2 必读 UNKNOWN 而拒绝；接纳在前 ⇒ 其租约已在 S3-h 发布，登记锁内检查 P5 非空而拒绝（`INITIAL_NORMAL_APPLICATION_ACTIVE`）。线性化点：接纳＝后探针（S3-k）完成；登记＝完成/UNKNOWN 发布。
- **失败恢复**：登记中途死亡 → `.pending`/UNKNOWN 遗留 → 接纳拒绝；接纳在 h 与 k 之间死亡 → 属主进程锁由 OS 释放 → 后续接纳判孤儿拒绝。
- **未来测试屏障与唯一预期**：S13-①（用真实控制工具 + 阻塞探针制造两侧并发）——唯一预期：二者全序，登记先→接纳 `RECOVERY_EVIDENCE_MISSING`/`INITIAL_NORMAL_UNKNOWN` 且零根登记；接纳先→登记 `INITIAL_NORMAL_APPLICATION_ACTIVE` 且 `current.json`/P2 未被改动。
- **本轮设计判定**：**已关闭（设计级）**。运行未验证。
- **实施清单要求**：包含承担新守卫的**登记写侧**（设计 §3.6 第 11 项、§9 第 11 行），不是只改读取侧。

## 4. R2 封闭初次权威与恢复权威的消费者边界

- **具体反例（v2 成立）**：`RecoveryClosedProjection.register()` 在 DB 绑定为空时接受 `revision=1 / QUIESCING / previousRecordSha256=ZERO_HASH`（`RecoveryClosedProjection.java:47-48`），写入 `recovery_installation_binding`＋epoch 映射＋投影并把 `server_state` 置 `RECOVERY_PENDING`（`RecoveryProjectionRows.java:45-65`）；其事务只持**共享**写者租约（`RecoveryLeasedTransaction.java:44-51` → `RecoveryWriterLease.java:39`）。该形状与拟议初次权威**完全同形**，且不需要独占控制推进。⇒ v2 “模式变更必先完成独占控制推进”的证明**不成立**。
- **涉及的状态与锁**：P3 `authorityKind`（写者＝供给）；P4 `server_state`/`recovery_installation_binding`（写者＝恢复链投影事务，锁＝DB 事务 + `FOR UPDATE`）；L2 共享写者租约。
- **修订后的强制点**：`authorityKind` 校验**下沉到权威加载唯一入口** `RecoveryControlHistory.load(location, checkpoint, purpose)`（Python 同构），`purpose` 为**必填**参数；固定 `authorityKind × consumer` 允许表（设计 §5.3）：接纳门/离线初次登记只接受 `INITIAL_NORMAL_INSTALL`；投影、`RecoveryNormalRegistration`、`RecoveryLeasedTransaction`、`RecoveryControlReader`、`RecoveryAdmissionVerifier.of/verify`、Python `control_store.inspect/apply` 等只接受 `RECOVERY`；不符即 `CONTROL_AUTHORITY_KIND_FORBIDDEN`，在事务/租约之前拒绝。
- **为何该交错被拒绝/合法串行化**：初次权威**不能被**任何恢复链消费者加载 ⇒ 初次权威上不可能出现 `RECOVERY_PENDING` 类模式变更；恢复权威**不能**被初次接纳门加载（`RECOVERY_INITIAL_AUTHORITY_REQUIRED`）且其存在会破坏初次证据（`INITIAL_NORMAL_CLOSED_HISTORY`）⇒ 同一权威身份上“已接纳初始根”与“恢复链模式变更”不可能并存。跨身份切换需要另一套 `identity.json` 与配置变更＋重启；就地替换身份被 `startup.equals(current)` 捕获（`CONTROL_INSTANCE_STALE`）；真实关闭权威条目的出现使接纳 S2 直接拒绝（`INITIAL_NORMAL_CLOSED_AUTHORITY`）。
- **真实关闭权威创建/切换的共同约束**：必须在 P3 协调锁内且无在途租约（与登记同一协议）——列为 §3.6 第 24 项，B9 实施；本轮只冻结规则，不改恢复链工具。
- **未来测试屏障与唯一预期**：S11——用 INITIAL 权威调用 `RecoveryClosedProjection.project()`，唯一预期 `CONTROL_AUTHORITY_KIND_FORBIDDEN`，且 `recovery_installation_binding` 无行、`service_mode` 未变（DB 前后摘要一致、事务回滚证据）；S10——恢复型权威请求接纳的唯一预期 `RECOVERY_INITIAL_AUTHORITY_REQUIRED`/`_INVALID`。
- **本轮设计判定**：**已关闭（设计级）**；v2 的错误证明已撤回并在设计 §5.3 重写。运行未验证。

## 5. R3 取消以“上下文缺席”推断兼容权限

- **具体反例（v2 成立）**：v2 分支 A 的条件是“无线程绑定 **且** `RequestContextHolder` 无请求”。一个未传播上下文的异步执行器线程同时满足该条件 ⇒ 它会**静默取得旧连接级写权限**。“有上下文但没绑定”会被拒绝，推不出“上下文丢失也拒绝”。
- **涉及的状态与锁**：线程↔根绑定（注册表，内存）；`RequestContextHolder` 的线程本地性；数据源取连接的准入判定。
- **修订后的强制点**：**主数据源（`@Primary`）改为 `RecoveryRequestAdmittedDataSource`，每个 `getConnection()` 必须解析到本线程有效根**；解析失败一律拒绝且**永不回退**：无根标识 → `RECOVERY_OPERATION_REQUIRED`；有请求根但线程未绑定 → `RECOVERY_OPERATION_CONTEXT_LOST`；令牌失效 → `CONTROL_OPERATION_RELEASED`；越集子操作 → `CONTROL_SUBOPERATION_FORBIDDEN`。旧连接级路径改为**显式命名、非 `@Primary`** 的独立能力（`RecoveryAdmittedDataSource` 原语义逐字节保留），使用集合冻结为 {测试源、离线/运维工具、定义处}，并用静态测试禁止请求路径注入；需要无根 DB 访问的合法路径改用显式能力（迁移池、初始化能力、只读证据代理、只读容错提示），B1 实施清单逐项列出。
- **为何该交错被拒绝/合法串行化**：兼容路径不再是“默认分支”而是“显式能力”，因此“缺少上下文”不再携带任何权限；异步生产者线程即使无上下文，也只能拿到主数据源，必然被拒。绑定清理规则：过滤器 `finally` 在 `!isAsyncStarted()` 时解绑，`requestDestroyed` 清理该根全部绑定，退役前确认无残留 ⇒ 容器线程复用不会继承权限。
- **未来测试屏障与唯一预期**：S7 四负例——①有请求上下文无绑定→`RECOVERY_OPERATION_CONTEXT_LOST`；②两者都无（普通执行器线程）→`RECOVERY_OPERATION_REQUIRED`；③已释放令牌→`CONTROL_OPERATION_RELEASED`；④同线程连续两请求，第二次借用被拒（绑定已清理）。四者均零业务写，且轨迹证明未走旧连接层；105 回归证明旧连接层语义未变。
- **本轮设计判定**：**已关闭（设计级）**。运行未验证。

## 6. R4 补齐 P5 的身份、状态与失败协议

- **具体反例（v2 成立，共四个）**：
  1. **不同目录逃逸**：P5 来自自由配置，仅有不重叠检查；相同 P3、不同 P5 的两进程互相看不见孤儿。
  2. **退役失败不立即成孤儿**：v2 称“删除失败保留记录后下次判孤儿拒绝”，但判定算法跳过本进程记录、并把仍持进程锁的其他进程记录视为存活 ⇒ 进程仍存活时该结论不成立。
  3. **孤儿产生不受 P3 锁串行化**：进程 A 可在 B 完成 S3-g 之后、S3-j 之前死亡；OS 释放文件锁不需要 P3 锁，故“扫描与登记同处临界区”不能证明该交错不存在。
  4. **halt 语义未区分**：`Runtime.halt(70)` 会终止同 JVM 的其他在途操作，v2 “未涉 UNKNOWN 的存量操作仍可收尾”未区分同/跨 JVM。
- **涉及的状态与锁**：P5 目录身份（配置）、进程记录＋生命周期独占锁、租约记录（含 `.pending`）、退役删除、P3 协调锁、OS 文件锁释放。
- **修订后的强制点（逐项对应）**：
  1. `leaseRoot` 写入 P3 identity v2，且 `<leaseRoot>/lease-root.json` 绑定 {installationId, authorityRoot, leaseRoot, bindingSha256}；启动与每次接纳要求“配置 == identity == lease-root.json”，否则 `RECOVERY_LEASE_ROOT_UNBOUND` ⇒ 同一安装的所有进程被强制使用同一 P5，空目录/另一目录被拒。
  2. 退役删除失败（或无法读回确认）**不再依赖“下次判孤儿”**：立即进入 quarantined（本进程拒绝所有新根）并 **halt**，其租约随即成为其他进程可见的孤儿 ⇒ 不变式“存活进程不存在已结束但未退役的租约”。退役协议顺序：RELEASING（禁新子操作）→ 子租约全关＋连接终态确认 → 事务/Session 结果确认 → 删除并读回确认 → 最后释放 L1 引用。
  3. 接纳改为**前后两次探针**（S3-g 前探针、S3-k 后探针），线性化点＝S3-k 完成；若 S3-g 后、S3-k 前目标进程死亡，S3-k 发现孤儿 → 退役刚建记录（失败即 halt）并拒绝，业务写尚未开始；若死亡发生在 S3-k 之后，属**接纳后故障**（允许收尾）。该边界不再依赖“OS 释放锁也要取 P3 锁”。
  4. halt 作用域明确：终止**整个 JVM** ⇒ 同进程在途操作一并终止且不退役（结果未知，租约成孤儿，方向保守）；**其他进程**在途操作可继续收尾但不能开新根；所有进程与新进程此后拒绝。设计不主张同进程其他操作仍能收尾。
- **状态与失败表**（设计 §5.4）：`.pending` → 孤儿拒绝；损坏租约记录 → 孤儿拒绝；属主进程记录缺失/不可解析 → 孤儿拒绝；属主锁可取得 → 孤儿拒绝；属主锁被其他存活进程持有 → 不阻断；本进程在途租约 → 跳过；`lease-root.json` 缺失/不匹配 → 启动失败或拒绝。
- **未来测试屏障与唯一预期**：S13-②（用只读句柄/权限制造删除失败）→ 该进程拒绝新根并 halt、租约成孤儿，且**不出现**“存活进程留未退役租约”；S13-③（探针间隙 kill 目标进程）→ 后探针发现孤儿、退役并拒绝、零业务写；S12（相同 P3／不同 P5）→ 第二进程启动失败 `RECOVERY_LEASE_ROOT_UNBOUND`；S5/S6（UNKNOWN 与新进程）唯一预期不变。
- **本轮设计判定**：**已关闭（设计级）**，四个反例分别由身份绑定、退役即隔离、前后探针、作用域声明覆盖。**保留残余**：①授权操作者直接改写文件/库不在威胁模型内（与项目对 `server_state` 的既有口径一致）；②`halt` 理论不可执行的极端情形退化为“租约仍在 ⇒ 仍拒绝”，方向不变。运行未验证。

## 7. R5 修正验收规格与文档事实

| 项 | v2 的问题 | v3 修订 |
| --- | --- | --- |
| Session 保存断言 | 要求普通请求在 FINALIZING 相位登记子操作，与已纠正的时序冲突 | 改为：保存期间根存活、保存所用连接登记该根子操作、结束后正常退役、`leases/` 清空；取消 FINALIZING 子操作条件 |
| 过滤器顺序 | 用 `ServletContext.getFilterRegistrations()` 断言 order（该接口无 order 属性，javap 已核实） | 改为：Spring 上下文的注册顺序值与 `dispatcherTypes`（会话过滤器默认 `-2147483598`／`{ASYNC,ERROR,REQUEST}`）＋**测试期运行轨迹**（实际进入的 (过滤器, DispatcherType) 序列与根事件），并明确 REQUEST/ERROR/ASYNC 注册范围 |
| `acquire` 前置条件 | 写成“自取协调锁”（错） | 修正为“调用线程必须已持该根协调锁（`requireOwner`），由调用方用 `locked/lockedWaiting` 包装”；删除 `acquireLocked` 增量项 |
| P2 文件语义 | 只列允许集 | 区分三类：正常必备/正常发布（+`.pending` 表示未完成 → 拒绝）、**UNKNOWN 拒绝文件**（出现即永久拒绝，入允许集只为精确诊断） |
| v1↔v2 差异 | 纠错记录把 v1 描述为“未覆写两个 `shouldNotFilter*`”，与 v1 原文（已写为 false）不符 | 如实更正：v1 已写明覆写为 false；真实缺陷是①把 Spring Session 自有 `OncePerRequestFilter` 与 Spring Web 的同名类混同、②未处理嵌套 ERROR 分派重跑、③重分派时根复用语义未定义、④S4a 屏障未落在真实保存路径 |
| 看板规模 | 记录沿用“40 行/2310 字符”（上一轮值） | 更正为实际值（上一轮 45/3234；本轮更新后取值见验收记录） |
| 章节指向 | 存在“编号存在但指向错误语义”的引用 | 全文交叉引用按 v3 结构校正，并以脚本校验“引用编号可解析”，另人工核对语义 |
| 前置证据时点 | 上一轮 `01-git-status.txt` 采集于修改之后 | 本记录 §1 如实注明，并在本轮修改前落盘 `00-pre-status.txt`；不补造历史快照 |
| 实施清单 | 分散在多处增量引用 | 合并为设计 §9 的完整清单（强制点 → 类/方法 → 测试），共 21 行 |
| `normal-admission.json` 允许集差异 | v2 已登记 | 继续作为**恢复链互操作的独立欠债**单列，本轮不修、不宣称线上故障 |

- **本轮设计判定**：**已关闭（设计级）**；无保留阻断。

## 8. 本轮实际运行 / 未运行

**实际运行**：§1 前置快照（修改前）、共享树状态与 138 项哈希（前后各一次）、§2 依赖 `javap` 只读核对（命令/路径/哈希/原始输出留档）、Python 协议只读阅读、正文与归档链接检查、章节语义与跨文档状态一致性检查、`git diff --check`、`tools/version_facts.ps1 -Write` 后 `-Check`、看板当前区上限校验、暂存清单与提交编码复核。

**未运行**（全部标为待 B1 授权后执行）：编译、单测、IT、真实 HTTP、数据库供给与读写、完整应用冒烟、跨进程与故障进程实验、断电/Linux 持久性、现场组合。§3–§7 的“未来测试”与设计 §10/§11 的用例**不计入本轮通过项**。

## 9. 结论

R1–R5 的设计反例**已闭合或已明确保留**（R4 两项残余已具名，且不构成阻断缺口）；v2 中被推翻的两处证明已撤回重写；实施清单已合并为单一完整表。**运行行为全部未验证；B1 未实施、未授权。**保留边界：生产默认 Session 清理路径为未覆盖入口；孤儿解除、七类生产决定、TEMP 生产归档、生产释放未授权（`localDeletionAllowed=false`）；C1 首次插入提交丢回执、独立进程重启、真实网络故障、断电、Linux 持久性、现场原资料组合同样未验。未发现新的真实产品缺陷（本轮未做动态验证）。
