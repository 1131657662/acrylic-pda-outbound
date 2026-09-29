# R09 C2 B1-3 S2：请求根数据源（`RecoveryRequestAdmittedDataSource`）实现与验证（2026-09-29）

> 轮次身份：主树 `D:/acrylic-pda-outbound`、分支 `codex/development`；写者＝DSH 本会话；**同一会话实施＋自审＋运行，非独立第三方验收**。授权：用户 2026-09-29 指示"继续 S2（读 §5 后一次做完并跑两级验证）"。边界：**只接线、不开任何生产开关**（`outbound.recovery.effect-gate.enabled`、跨进程开关、`outbound.recovery.admission.enabled` 均保持默认 false），不接生产、不部署、不开放七类生产决定能力。

## 1. 起点与前置检查

- 起点 HEAD `90c924da581f25e8ccae77b220b5a7dc8dcd6319`（`codex/development`）；工作区未提交项中有**两项非本会话**的改动，本会话未代为提交：`docs/PDA_OFFLINE_CONTINUITY_ANALYSIS_2026-09-29.md`（另一任务的离线连续性分析补充）与未跟踪 `docs/PDA_UI_UX_REVIEW_2026-09-29.md`；`docs/ORCHESTRATION_STATUS.md` 当时只有生成事实块滞后。
- 规格来源：[开工说明 §5](R09_C2_B1_3_PLAN_2026-09-29.md)、[G0 设计 §6.1/§6.2/§6.3/§4.4](R09_C2_G0_DESIGN_2026-09-28.md)、[残余阻断 R3](R09_C2_G0_RESIDUAL_BLOCKERS_2026-09-28.md)（主数据源必须解析到本线程的有效根；解析失败**永不回退**）。
- 环境：系统 MySQL 3306/33060 未动；19387＝DSH 监听；证据根 `.local/r09-c2-b1-3-s2-20260929-a/`（不入库）。

## 2. 实现（产品代码，`recovery` 包）

新增：

| 文件 | 作用 |
| --- | --- |
| `RecoverySubOperation` | 五种被登记的子操作（设计 §6.1 同名枚举） |
| `RecoveryOperationKind` | 根的种类**及其子操作白名单**（越集即拒；映射集中在一处，便于复核） |
| `RecoveryOperationPhase` | `SERVING/FINALIZING/RELEASING/RELEASED/FENCED`；仅前两者接受新子操作 |
| `RecoveryOperationToken` | 根身份（operationId／kind／installationId／authorityRecordSha256／requestSha256，哈希格式校验） |
| `RecoverySubLease` | 单个子操作的句柄；关闭幂等 |
| `RecoveryOperationLease` | 根租约：相位机、子操作登记与计数、分派绑定深度、持有一份共享写者租约、跨线程解绑 |
| `RecoveryOperationRegistry` | 内存权威：登记根（重读权威＋身份比对＋取共享写者租约，须在协调锁内）、线程绑定、环境根观察、根退役 |
| `RecoveryRequestAdmittedDataSource` | `@Primary` 数据源：每次 `getConnection` 解析到本线程的根，六码各自独立拒绝 |

修改：

| 文件 | 改动 |
| --- | --- |
| `RecoveryAdmissionConfiguration` | `@Primary` 由旧连接层改为 `RecoveryRequestAdmittedDataSource`；新增 `RecoveryOperationRegistry` bean（权威缺失或不可读 ⇒ 启动失败，不降级为无守卫连接池）；旧类不再是应用 bean |
| `RecoveryWriterLease` | 新增包内 `reference()`：同一已持描述符的独立引用（引用计数），供"根跨多个连接持有共享租约" |
| `RecoveryConnectionHandler` | 新增**可选**子租约：连接到达终态时释放该根的 `CONNECTION` 子操作；旧连接层路径传 `null`，行为逐字节不变 |

### 2.1 六码与条件（逐条实现，各配按码断言的单测）

| # | 条件 | 结果 | 判据位置 | 单测 |
| --- | --- | --- | --- | --- |
| 1 | 线程绑定有效且根存活 | 借用连接并登记 `CONNECTION` | `requireRoot()`＋`registerSubOperation(CONNECTION)` | `boundLiveRootBorrowsAndRegistersOneConnectionSubOperation` |
| 2 | 有请求根标记但线程未绑定 | `RECOVERY_OPERATION_CONTEXT_LOST` | `registry.ambient()`（请求属性 `RecoveryOperationRegistry.REQUEST_ROOT_ATTRIBUTE`） | `requestRootWithoutAThreadBindingIsRefusedAsLostContext` |
| 3 | 无线程绑定且无任何根标识 | `RECOVERY_OPERATION_REQUIRED`（拒绝，不回退） | 同上，未命中即拒 | `threadWithoutAnyRootMarkerIsRefusedInsteadOfFallingBack` |
| 4 | 令牌不再解析到根 | `CONTROL_OPERATION_RELEASED` | `registry.live(token)` 为空 | `borrowThroughAReleasedRootIsRefusedWithTheReleasedCode` |
| 5 | 越集子操作（种类白名单或已进入释放相位） | `CONTROL_SUBOPERATION_FORBIDDEN` | `registerSubOperation` | `subOperationOutsideTheRootSetIsRefused`、`rootThatStartedReleasingRefusesNewSubOperations` |
| 6 | 根处于 FENCED | `RECOVERY_LEASE_FENCED` | `requireRoot()` 相位判定 | `fencedRootIsRefusedWithTheFenceCode` |

增强覆盖：`eachBorrowTakesAndReleasesItsOwnWriterReference`（根自己的租约引用跨两次借还仍存活 ⇒ 逐连接引用计数成立）、`finalizingRootStillAcceptsRetainedWork`（FINALIZING 仍覆盖链返回后的保留写）、`nestedDispatchBindingKeepsTheOuterScopeUntilItEnds`（嵌套分派深度 +1/−1，外层作用域不被提前清除）、`failedBorrowReleasesTheRootsRetainedWorkAndKeepsItUsable`（借出失败不留残余子操作、根仍可用）。

### 2.2 新增拒绝码（须回填设计码表）

除上表六码外，本切片为**生命周期误用**新增（一律失败关闭，均不是回退路径）：`CONTROL_OPERATION_TOKEN_INVALID`、`CONTROL_OPERATION_DUPLICATE`、`CONTROL_OPERATION_PHASE`、`CONTROL_OPERATION_BINDING_MISMATCH`、`CONTROL_OPERATION_BINDING_MISSING`、`CONTROL_SUBLEASE_NOT_HELD`、`CONTROL_SUBLEASE_RELEASE_UNCONFIRMED`；`reference()` 沿用 `CONTROL_WRITER_LOCK_REPLACED`／`CONTROL_WRITERS_ACTIVE`。设计 §4.4 的码表尚未含这些内部码 ⇒ 列为**待回填项**（不改变六码语义）。

### 2.3 `@Primary` 唯一性

- 应用主代码中 `@Primary` 只有一处（`RecoveryAdmissionConfiguration`，且在 `RecoveryRequestAdmittedDataSource` 的 bean 方法上）；旧 `RecoveryAdmittedDataSource` 在主代码中除定义处外**零引用**，也不再是 bean ⇒ 请求路径无法到达旧连接层。
- 测试侧 `RecoveryEntryLostCommitConfiguration:35` 的 `@Primary` **保留**：它是"丢失提交回执"的故障注入装置，其上下文只在该装置自己的开关（`outbound.recovery.test.lost-commit-entry=true`）下装配，且**不开启** `outbound.recovery.admission.enabled` ⇒ 与主入口互斥，同一上下文不会出现两个 primary。**这是本切片对设计"旧类非 `@Primary`"的一处明确限定**，并已由静态白名单测试冻结（测试侧新增第二个 `@Primary` 即失败）。

## 3. 验证（两级）

### 3.1 一级：单元测试（无数据库）

- 定向（`run1-unit-targeted`，自审前）：**6 类 / 45 项 / 0 失败 / 0 错误 / 0 跳过**；自审补一项失败借用路径单测后复跑（`run5-unit-targeted-postreview`）为 **6 类 / 47 项 / 0 失败 / 0 跳过**。其中 `RecoveryAdmittedDataSourceTest` **17/17 仍绿**（验收①基线）、`RecoveryRequestAdmittedDataSourceTest` **11**（含六码与失败借用路径）、`RecoveryLegacyPathIsolationTest` 3（验收③静态白名单）、`RecoveryAdmissionConfigurationTest` 4、`RecoveryAuthorityPurposeTest` 6、`RecoveryWriterLeaseTest` 6。
- 全量（`run3-unit-full-final` 自审前；`run4-unit-full-postreview` 自审后）：`gradlew -p server test`，**不带 `--tests` 白名单**（因此不存在"新测试类被白名单静默漏跑"的旧口径问题）：**123 类 / 2790 项 / 0 失败 / 0 错误 / 1 既有 skip**（`com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeFilesTest`，与本切片无关）；其中 `com.acrylic.outbound.recovery.*` **28 类 / 2152 项 / 0 失败 / 0 跳过**。
- 验收对照：①旧类 17 项仍绿 ✅；②六码各自的按码断言单测 ✅；③`@Primary` 唯一性静态测试 ✅。
- 自审发现与修复（**发现者＝本会话自审，非独立第三方**）：`admit` 的 javadoc 声称"未真正到达连接的借用不会留下该根的保留工作"，但**没有对应断言** ⇒ 补 `failedBorrowReleasesTheRootsRetainedWorkAndKeepsItUsable`（借出抛错后子操作计数归零、根仍 SERVING、随后借用仍成功）。该单测首版因 Mockito 重复 `when(...)` 触发上一次 `thenThrow` 而失败（**测试自身缺陷，非产品缺陷**），改为连续打桩后通过；失败轮 `run4` 保留。
- 该自审改动**仅测试代码**（产品代码零改动）⇒ 上述恢复链 IT 批次证据仍然对应同一产品代码；单元级证据以 `run4`/`run5` 为准。

### 3.2 二级：恢复链 25 类 IT 批次（含 S1 后继增量的 IT 复验）

运行方式：`tools/r09-recovery-it-batch.ps1 -Port 19453`，**全新专用实例**（`pinned=False`、schema＝账号 `r09_batch_20260929`、`timeWaitBefore=73`），逐类独立 attempt 目录与独立证据。

结果：**25 类 / 696 项 / 0 失败 / 0 错误 / 0 跳过 / 0 类失败**，`NOT-COVERED` 为空；TIME_WAIT 峰值 1521（节流后回落），收尾 `exit=0`、`port_listening_after=0`（正常 shutdown、端口释放）。逐类（`run101`–`run125`）：`RecoveryEffectBatchIT` 3、`RecoveryEffectCommitIT` 14、`RecoveryEffectCursorIT` 7、`RecoveryEffectSqlIT` 11、`RecoveryEffectAdversarial{Batch 12, Callable 45, Cleanup 36, DefaultOff 15, Driver 21, Fence 15, Handles 183, Lob 48, Metadata 15, Pool 3, SqlSurface 102, View 6}`、`RecoveryFence{AdversarialDurable 27, Connection 12, CrossProcess 33, Integrity 36, Startup 21, Unknown 3}`、`G0K1RealFileLockIT` 10、`G0K1WriterLeaseIdentityRealIT` 5、`G0K1RealJdbcBoundaryIT` 13；24 类为历史既有类，验证的是**本切片未破坏恢复链**（其中 `RecoveryEffectAdversarialPoolIT` 直接构造旧 `RecoveryAdmittedDataSource`，行为不变）。

S1 后继增量（kind 校验集中到同一 purpose 规则、错误码不变）的 IT 级复验在本批次内完成（该增量此前只在 19450 批次内做过 IT 复验）。

中止轮（原样保留、未删除）：`it-batch-25b-aborted-javadoc-edit/` —— 首类运行期间我做了一处**仅注释**的源码改动；为保证"批次证据逐字对应最终树"，主动中止并在源码冻结后重跑（新实例 19453）。原实例 19452 随进程终止（`listen19452=0`），datadir `.local/mysql19452-r09batch` 保留。

## 4. 边界、未验与保留项

- 两个效果门开关与 `admission.enabled` 全部保持默认 false；未接生产、未部署、未合 main、未操作原库/现网；七类生产决定、TEMP 生产归档、生产释放继续关闭，`localDeletionAllowed=false`。
- **S3/S4 未开工**：尚无请求接纳过滤器，生产入口**没有**创建根、绑定分派或发布请求根标记的代码 ⇒ 即使现在打开 `admission.enabled`，一次真实 HTTP 请求也会按 §2.1 第 3 行被拒（`RECOVERY_OPERATION_REQUIRED`）。这是切片顺序，不是本切片宣称可用。
- 未做（属后续批次，设计已登记）：完整接纳门 S0–S4 与 P5 前后探针/租约发布/孤儿与围栏通道（设计 §9 第 8–12、14 行）、效果门接线（第 11 行）、离线登记写侧（第 15 行）、启动门与清理守卫（第 19 行）、§5.6 退役协议（含退役失败即隔离＋halt）与本切片 `openSubOperations()` 的**强制**使用（当前只是观察值）。
- **入口 IT 重基保留项**：`E2aRecoveryAdmissionEntryIT` 与 `C1EntryRegression` 的 live/unknown 两个检查按 S2 语义**已过时**（primary 现为请求根数据源，而"绑定根"的过滤器属 S3）⇒ 本轮**未运行这两项**（环境门且不在批次内），须在 S3 完成后重基复验；其中的 lost-commit 检查仍走测试配置的旧类，语义不受影响，但本轮同样未运行。
- 未改 Python 侧、迁移版本、契约、Android；未新增迁移。K1 仍未通过；窗口 W、`DEBT-R09-C2-LEASE-ANCHOR`、`UNCONFIRMED_NO_CROSS_PROCESS_BASIS`、IT-DISCOVERY、IT-EVIDENCE-AUDIT、TZ-GMT 等继续单列。

## 5. 证据位置（不入库，按约定保留）

`.local/r09-c2-b1-3-s2-20260929-a/`：`pre-head.txt`、`pre-status.txt`、`run1-unit-targeted/`、`run3-unit-full-final/`、`run4-unit-full-postreview/`（含自审首版失败轮）、`run5-unit-targeted-postreview/`（各含 `gradle.log`、`exit.txt`、全部 `TEST-*.xml`）、`it-batch-25/`（`preflight.txt`、`instance-identity.txt`、逐类 `run101–run125` 目录含 `command.txt`／`gradle.log`／`exit.txt`／XML／`files.sha256`、`batch-summary.json`、`db-shutdown.txt`）、`it-batch-25b-aborted-javadoc-edit/`（中断轮原样保留）。

## 6. 下一步

S3：Session 过滤器（覆盖 `{ASYNC,ERROR,REQUEST}`、顺序 `-2147483598`、请求根覆盖、每类 Session 写在同一请求根内），并在其中把 `RecoveryOperationRegistry.REQUEST_ROOT_ATTRIBUTE` 的发布与 `bindDispatch/unbindDispatch`（含嵌套 ERROR/ASYNC 重分派的根复用与深度成对）接到真实分派生命周期；S3 完成后重基入口 IT。
