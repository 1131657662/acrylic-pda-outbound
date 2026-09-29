# R09 C2 B1-3 S5：启动守卫与初始化窗口（2026-09-29）

> 轮次身份：主树 `D:/acrylic-pda-outbound`、分支 `codex/development`；写者＝DSH 本会话；**同一会话实施＋自审＋运行，非独立第三方验收**。授权：用户 2026-09-29 选择"继续推荐的下一步"＝**①启动期无根 DB 访问能力（设计第 19/22 行）**。边界：仍**不开任何生产开关**（`admission.enabled`、`effect-gate.enabled`、跨进程开关保持默认 false），不接生产、不部署、不合 main。起点 HEAD `f75b9ca`（S4 收口）。

## 1. 为什么需要这一步（S3 实测发现）

`admission.enabled=true` 时应用**起不来**：`JobRecoveryFence`（构造期读 `server_state`，经 `BackgroundJobStore`→`ExportProducer`→`ExportCenterController`）走主数据源（要求本线程有效根）⇒ `RECOVERY_OPERATION_REQUIRED`。同类启动期消费者还有 `ServiceModeStartupNotice`（`ApplicationRunner` 读 `server_state`）与 `BootstrapAdmin`（`CommandLineRunner`，默认关闭）。设计对此有两处要求：第 19 行"启动门与清理守卫"（S9 用例），第 22 行"无根 DB 访问的合法能力清单（仅清点＋守卫）"。本轮按此实现**显式初始化窗口**（而不是给某个 Bean 开旁路），并保留清单守卫。

## 2. 实现（产品代码）

| 文件 | 作用 |
| --- | --- |
| `RecoveryStartupGuard`（新，`BeanFactoryPostProcessor`） | 在任何应用 Bean 之前运行：①**清理守卫**——`spring.session.jdbc.cleanup-cron` 不等于 `-`（含未配置）⇒ 启动失败 `CONTROL_SESSION_CLEANUP_UNCOVERED`（该框架写者跑在自有线程上、不属于任何根，开着就等于有一个未覆盖写者）；②**显式排序**——把每个应用 Bean（`com.acrylic.outbound.*`，排除配置属性、BP/ B FPP 与自身）的 `dependsOn` 合并加入 `recoveryInitializationWindow`，使启动期 DB 访问落在显式根内，而不依赖声明顺序 |
| `RecoveryInitializationWindow`（新） | 初始化窗口：协调锁内 `admit` 一个 `INITIALIZATION` 根并绑定到**刷新上下文的线程**；监听 `ApplicationReadyEvent`（在框架启动 runner 之后）时清绑定并退役；实现 `AutoCloseable`（异常关闭也会退役）。窗口期正是设计 §8.1 的"显式许可 → 迁移 → 初始化 → 业务入口"里的"初始化"段 |
| `RecoveryAdmissionConfiguration`（改） | 装配 `recoveryStartupGuard`（静态 `@Bean`，仅 `admission.enabled=true` 生效）与 `recoveryInitializationWindow` |

**未改**：`JobRecoveryFence`/`ServerState`/`ServiceModeStartupNotice`/`BootstrapAdmin` 等启动消费者——它们仍走主数据源，只是现在被显式初始化根覆盖。设计第 8–12 行的阶段 0 安全前置（`leaseRoot` 三方一致、`lease-root.json`、P5 进程记录与锁、孤儿租约、FENCED 残留）**未实现**：它们需要新的 `leaseRoot`/锚点/`closedAuthority` 配置与 provision 协议（B1-2 只留下 `RecoveryLeaseRoots` 等基座，未接入应用），属后续批次，见 §5。

## 3. 验证

### 3.1 一级：单元／上下文（`run4-unit-full`）

- 新增 `RecoveryStartupGateTest` **3 项**：清理未禁用（含显式 cron 与**未配置**两种）⇒ 启动失败且码为 `CONTROL_SESSION_CLEANUP_UNCOVERED`；清理 `-` ⇒ 启动成功、**应用 Bean 在初始化根内被创建**（测试消费者构造时读到 `liveRoots==1`）、`startupConsumer` 的 `dependsOn` 含 `recoveryInitializationWindow`、发布 `ApplicationReadyEvent` 后窗口关闭（`root()==null`、`liveRoots==0`）。
- 新增 `RecoveryNoRootCapabilityTest` **2 项**（第 22 行清点＋守卫）：`MIGRATION_DATASOURCE` 只允许出现在冻结集合（当前仅 `RecoveryAdmissionConfiguration.java`，新读取者必须显式加入）；`@FlywayDataSource` 全仓仅 1 处；启动覆盖必须是显式窗口（断言守卫码与窗口类存在）。
- 既有用例按新守卫更新：`RecoveryAdmissionConfigurationTest`（新增 `cleanup-cron=-`；无根借用断言改在 `ApplicationReadyEvent` 之后，与"就绪才接客"一致）、`RecoverySessionFilterRegistrationTest`（同上）。定向 `run3-s5-targeted`：**5 类 / 20 项 / 0 失败**。
- 全量（`gradlew -p server test`，不带 `--tests` 白名单）：**127 类 / 2806 项 / 0 失败 / 0 错误 / 1 既有无关 skip**；`com.acrylic.outbound.recovery.*` **32 类 / 2168 项 / 0 失败**。

### 3.2 二级：现场判据（真容器＋真实 Spring Session JDBC＋隔离 MySQL，`run3-s5-startup-window`，全新实例 19462／应用 19458，`migrate executed=49`）

- **本次现场运行不装任何替身**：S3/S4 轮里探针自带的 `s3StartupRoot` 与排序 BFPP 已**删除**，启动期 DB 访问完全由产品侧 `RecoveryInitializationWindow` 覆盖 ⇒ 应用在 `admission.enabled=true` 下**真的起来了**（先前 `run1` 在此处 fail closed）。
- S3/S4 判据全部复现：`/__s3/session` 200＋Session 行/属性；`/__s3/error` 500＋行/属性（ERROR 趟同根、不新建根）；`/__s3/async` 200 且轨迹含 ASYNC；`/__s3/multipart` 4096B ⇒ 200＋行/属性、解析 `[BOUND(staged=1), BOUND(staged=0)]`、请求后暂存 `0`；`liveRootsDuringRequests=[1,1,1,1]`（**初始化窗口在就绪时已关闭 ⇒ 不干扰"根数＝请求数"**）；14 次保存全 `BOUND`；`liveRootsAfter=0`；`springSessionThreads=[]`。
- **守卫的真实入口证据**（同一次运行的第 2 个用例）：清理保持默认（`0 * * * * *`）⇒ 启动失败 `CONTROL_SESSION_CLEANUP_UNCOVERED`、端口未被占用（`portFree=true`，证据 `s5-cleanup-guard.txt`）。
- 两个用例合计 **2 项 / 0 失败**；实例正常 shutdown、端口释放。

### 3.3 二级回归：恢复链 25 类 IT 批次

`tools/r09-recovery-it-batch.ps1`，全新专用实例 **19463**、schema＝账号：**25 类 / 696 项 / 0 失败 / 0 错误 / 0 跳过 / 0 类失败**，`NOT-COVERED` 为空，TIME_WAIT 峰值 1435（节流回落），收尾 `exit=0`、`port_listening_after=0`。本轮改了产品代码（启动守卫＋初始化窗口＋配置装配），因此该批次是必要的回归证据：恢复链未因启动排序与守卫而改变。

## 4. 本轮发现与说明

1. **初始化窗口绑定的是"刷新上下文的线程"**：这是刻意的——它让 bean 构造与框架启动 runner 都在根内；`ApplicationReadyEvent` 时 `unbindAll()` 清掉（含该线程）绑定后退役，业务流量从"无存活根"开始。单元测试里同一线程启动上下文，因此无根借用断言必须放在就绪事件之后（已按此更新既有用例，语义比原来更贴近生产）。
2. **排序注入的选择性**：只对 `com.acrylic.outbound.*` 的应用 Bean 注入依赖，跳过配置属性、`BeanPostProcessor`/`BeanFactoryPostProcessor` 与框架自动配置 Bean。理由：窗口本身由配置属性构造（注入会成环，第一次实现即撞上 `BeanCurrentlyInCreationException`，已在 §6 记录并修正）；而框架自动配置 Bean 在"清理已禁用"前提下不在启动期查库。若将来出现自动配置层的启动期读库，需要单独处理并在记录中说明。
3. **仍不宣称可用**：本轮只解决"起得来"。开关仍默认 false；真实业务入口、并发、跨进程、生产门都未验。

## 5. 未做（后续批次，按设计行号）

- 设计第 8–12 行**阶段 0 安全前置**：`leaseRoot` 三处一致与 `lease-root.json` 校验（`RECOVERY_LEASE_ROOT_UNBOUND`）、P5 进程记录建立并持锁、孤儿租约扫描（`RECOVERY_LEASE_OUTSTANDING`）、FENCED 残留；需要新增 `outbound.recovery.lease-root`／锚点秘密／`closedAuthority` 配置与 provision 协议。
- 第 13 行锁的按根嵌套与有界等待（并发接纳受限）；§4.4/第 20 行拒绝码 HTTP 映射（含 multipart 超限 413 与 S4 记录的 500 偏差）。
- **入口 IT 重基**（`E2aRecoveryAdmissionEntryIT`、`C1EntryRegression` live/unknown）：本轮已解除其启动阻塞（应用能带 admission 起来），但重基本身未做。
- 未验：并发、跨进程、Session 过期清理自有根、Worker/文件/渲染入口、Linux/其他文件系统、断电/硬复位、现场组合；K1 仍未通过；窗口 W、`DEBT-R09-C2-LEASE-ANCHOR`、`UNCONFIRMED_NO_CROSS_PROCESS_BASIS`、IT-DISCOVERY、IT-EVIDENCE-AUDIT、TZ-GMT 继续单列。

## 6. 逐轮失败保留（未删）

| 轮 | 现象 | 处置 |
| --- | --- | --- |
| `run1-s5-targeted` | 6 项失败：`recoveryInitializationWindow` ↔ `recoveryOperationRegistry`(`Settings`) `BeanCurrentlyInCreationException` 环；另在失败上下文上做 bean 名查询触发 `IllegalStateException` | 守卫跳过配置属性（按名字前缀与 `@ConfigurationProperties` 注解）与 BP/BFPP；失败用例不再查询失败上下文的 Bean |
| `run2-s5-targeted` | 1 项失败：`borrowWithoutAnOperationRoot…` 得到 "Communications link failure"——初始化窗口仍绑定测试线程 | 该断言改在发布 `ApplicationReadyEvent`（窗口关闭）之后，与"就绪才接客"一致 |
| `run3-s5-targeted` | **绿**（20 项 0 失败） | — |
| `run1-s5-startup-window` | **绿**（1 项）：删替身后真实启动成功＋S3/S4 判据全绿 | 本轮主证据 |
| `run2-s5-startup-window` | 编译失败：`catchThrowable` 的 lambda 误写 `return null` | 改为块 lambda（无返回值） |
| `run3-s5-startup-window` | **绿**（2 项）：＋清理守卫真实入口证据 | 本轮主证据 |

## 7. 证据位置（不入库，按约定保留）

`.local/r09-c2-b1-3-s5-20260929-a/`：`run1/run2/run3-s5-targeted`、`run1/run3-s5-startup-window`（含 `s3-session-root.txt`、`s5-cleanup-guard.txt`、`real-records.tsv`、`gradle.log`、`exit.txt`、XML、`coordinates.txt`、`upload-*.bin`）、`run4-unit-full`、`it-batch-25/`（逐类证据＋`batch-summary.json`）、`s5-compile*.log`。实例 19462 已正常 shutdown、端口释放；datadir `.local/mysql19462-s3session` 与批次 datadir 保留。

## 8. 下一步

①已完成（启动期能力）；接下来按优先级：**②锁的按根可嵌套＋有界等待（第 13 行）**（解除并发接纳受限与"并发 HTTP 未验"）→**③拒绝码 HTTP 映射（§4.4/第 20 行）**→**入口 IT 重基**；阶段 0 安全前置（第 8–12 行）需要 `leaseRoot`/锚点配置，建议与②或独立批次一起排。
