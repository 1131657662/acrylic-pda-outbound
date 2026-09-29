# R09 C2 B1-3 S3：请求根过滤器与真实 Session 覆盖（2026-09-29）

> 轮次身份：主树 `D:/acrylic-pda-outbound`、分支 `codex/development`；写者＝DSH 本会话；**同一会话实施＋自审＋运行，非独立第三方验收**。授权：用户 2026-09-29 指示继续 B1-3（[开工说明](R09_C2_B1_3_PLAN_2026-09-29.md) §1 的 S3 子切片）。边界：**只接线、不开任何生产开关**（`outbound.recovery.admission.enabled`、`effect-gate.enabled`、跨进程开关全部默认 false）；不接生产、不部署、不开放七类生产决定能力。起点 HEAD `47db883`（S2 收口），未提交项仅另一任务的两份文档（本会话未代提交）。

## 1. 实现（产品代码）

| 文件 | 作用 |
| --- | --- |
| `RecoveryRequestAdmissionFilter`（新） | **一请求一根**：首次分派建根并把根令牌写入请求属性；每次分派 `bindDispatch`／`finally` `unbindDispatch`，并在 SERVING→FINALIZING 时只做一次相位推进；覆写 `shouldNotFilter{Async,Error}Dispatch()` 为 `false` 且覆写 `doFilterNestedErrorDispatch`，使 ASYNC／ERROR／嵌套重分派**复用同一根并为当前线程补绑定**；根标记在场但线程未绑定 ⇒ `RECOVERY_OPERATION_CONTEXT_LOST`，两者皆无 ⇒ 交给主数据源按 `RECOVERY_OPERATION_REQUIRED` 拒绝。注册顺序 `Ordered.HIGHEST_PRECEDENCE`（必须早于会话过滤器 `-2147483598`），分派范围 `{REQUEST, ASYNC, ERROR}` |
| `RecoveryRequestRootListener`（新） | `ServletRequestListener`：容器最后边界——清该根的**全部线程**绑定（含泄漏在其它线程上的）并退役根，使"存活根数＝在飞请求数"成立 |
| `RecoveryOperationRegistry.admit(token)`（改） | 请求入口：在 P3 协调锁内建根（读权威＋身份比对＋取共享写者租约）。这是设计 §4.1 S0–S4 的**真子集**；P2 证据、P5 租约发布与前后探针属后续批次，不在本轮声明 |
| `RecoveryAdmissionConfiguration`（改） | 新增过滤器、注册 Bean、监听器三个 Bean；**仅在 `admission.enabled=true` 时装配**（默认关闭 ⇒ 应用行为不变） |

未改：旧连接层语义、迁移/契约/Android、开关默认值、Python 侧。

## 2. 验证

### 2.1 一级：单元／上下文测试（无数据库）

- 新增 `RecoveryRequestAdmissionFilterTest` **8 项**：一请求一根且分派期间绑定、链返回后同线程被拒、销毁时退役；嵌套 ERROR 复分派复用同根且不清外层作用域；链返回后的独立 ERROR 分派复用而非再接纳；ASYNC 分派自行绑定（根处于 FINALIZING 仍覆盖）；**请求被服务期间同一进程内无绑定线程被拒**（无线程绑定且无根标记⇒`RECOVERY_OPERATION_REQUIRED`；携带请求上下文⇒`RECOVERY_OPERATION_CONTEXT_LOST`，即设计 S7 ①/②）；销毁清理其它线程上的泄漏绑定；权威已推进时拒绝接纳且零根；**临界区内再次接纳⇒`CONTROL_COORDINATOR_BUSY`**（见 §3 发现 2）。
- 新增 `RecoverySessionFilterRegistrationTest` **2 项**：注册顺序／`{REQUEST,ASYNC,ERROR}`／监听器存在／关时零注册。
- 全量（`run10-unit-full`，`gradlew -p server test`，**不带 `--tests` 白名单**）：**125 类 / 2800 项 / 0 失败 / 0 错误 / 1 既有无关 skip**；`com.acrylic.outbound.recovery.*` **30 类 / 2162 项 / 0 失败 / 0 跳过**。

### 2.2 现场判据（真实容器＋真实 Spring Session JDBC＋隔离 MySQL）

- 方式：新增 `tools/r09-c2-b1-3-s3-session.ps1`（按端口保有独立实例、逐 attempt 独立证据目录、可 `-Shutdown` 释放）＋ `RecoveryRequestRootHttpIT`（真 `SpringApplication` 启动、真 HTTP、`s3-probe` 探针配置）。数据库契约沿用既有守卫：回环、端口 19400–19999、schema＝账号、证据目录 `run<N>-...`、拒 3306。
- **最终证据（`run11-s3-session`，全新实例 19456、应用端口 19458、schema 全新、`migrate executed=49/version=49`）**：
  - `entryOrder=-2147483648`（`urls=[/*]`，`{REQUEST,ASYNC,ERROR}`）**先于**会话过滤器 `-2147483598`（`{REQUEST,ASYNC,ERROR}`）；
  - `/__s3/session` ⇒ **200**，`SPRING_SESSION` 行 1、`s3-session` 属性 1；
  - `/__s3/error` ⇒ **500**（错误响应），行 1、`s3-error` 属性 1 ⇒ **错误分派的 Session 写被同一根覆盖且未新建根**；
  - `/__s3/async` ⇒ **200**，轨迹出现 **ASYNC** 分派且带根标记；
  - `liveRootsDuringRequests=[1,1,1]`（**根数＝请求数**）；10 次框架 Session 保存**全部 `BOUND`（保存时会话线程处于存活根）**；轨迹 `REQUEST → ERROR → ASYNC`，每趟均 `rootMarker=true`、`liveRoots=1`；
  - 请求结束后 `liveRootsAfter=0`（根在请求销毁时退役、无泄漏）；`springSessionThreads=[]`（本运行清理已禁用且生效）。
- 逐轮失败保留：`run1`（**启动期发现**，见 §3 发现 1）、`run2`（迁移断言过严）、`run3`（编译签名）、`run4`（启动仍无根）、`run5`（注册 Bean 类型）、`run6`（ERROR 分派被安全链拦成 401）、`run7`（无 ASYNC 分派：`complete()` 不产生分派）、`run8`（异步线程借用的拒绝码是 `CONTEXT_LOST` 而非 `REQUIRED`）、`run9`/`run11`（绿，run11 为全新实例最终证据）。

### 2.3 二级：恢复链 25 类 IT 批次（回归）

`tools/r09-recovery-it-batch.ps1`，全新专用实例 **19457**、schema＝账号 `r09_batch_20260929`：**25 类 / 696 项 / 0 失败 / 0 错误 / 0 跳过 / 0 类失败**，`NOT-COVERED` 为空，TIME_WAIT 峰值 1445（节流回落），收尾 `exit=0`、`port_listening_after=0`。该批次同时构成 S1 后继与 S2 变更的持续回归证据（本切片改的是主代码接线，25 类全绿 ⇒ 未破坏恢复链）。

## 3. 本轮发现（未修，须记入后续批次）

1. **开关打开时应用起不来（fail closed，符合设计意图但需后续能力）**：`run1` 实测 `JobRecoveryFence`（构造期读 `server_state`，经 `BackgroundJobStore`→`ExportProducer`→`ExportCenterController`）走主数据源 ⇒ `RECOVERY_OPERATION_REQUIRED`，上下文启动失败。这正是设计第 19 行（启动门）与第 22 行（无根 DB 访问的合法能力清单）未实现的部分。本轮现场运行**没有替换任何产品 Bean**，而是由探针配置提供一条**显式 `INITIALIZATION` 根**并让它排在启动消费者之前，作为缺失能力的临时替身；产品侧在 `admission.enabled=true` 时仍会 fail closed。
2. **并发接纳受限**：现有 `RecoveryControlLock` 既非可嵌套、又是 `tryAcquire` 无等待 ⇒ 同一时刻只能有一个请求取得根；已用确定性单测证明"临界区内再次接纳 ⇒ `CONTROL_COORDINATOR_BUSY`"。设计第 13 行（按根可嵌套＋有界等待）未实现 ⇒ **并发 HTTP 未验**（本次现场运行是顺序请求）。
3. **拒绝码到 HTTP 的映射未实现**（设计 §4.4 的 503 映射／第 20 行）：拒绝目前表现为容器错误响应；本轮的绿色路径未触发拒绝。
4. 文档更正：`FilterRegistrationBean`/`AbstractFilterRegistrationBean` 的分派范围 getter 是 **`determineDispatcherTypes()`**（G0 残余阻断记录写作 `getDispatcherTypes()`）；`sessionRepositoryFilterRegistration` 的实际类型是 **`DelegatingFilterProxyRegistrationBean`**（同属 `AbstractFilterRegistrationBean`）。
5. 测试装配细节（不影响产品结论）：错误分派会以错误页路径重入安全链，探针配置必须放行 `/error` 才能观察到 500 而非 401。

## 4. 保留边界与未验

- 开关仍全部默认 false；未接生产、未部署、未合 main、未操作原库/现网；七类生产决定、TEMP 生产归档、生产释放继续关闭，`localDeletionAllowed=false`。
- **S4 未开工**：multipart 暂存纳入请求根范围、错误分派提交的进一步断言（设计 §8.4/§10.2 S4a/S4b 的"新根拒绝"用例）未做；S4a 的 `SessionRepository` 阻塞屏障未做（本轮只用保存期观测）。
- 未做（后续批次）：完整接纳门 S0–S4 与 P5 前后探针/租约发布/孤儿与围栏通道、效果门接线、启动门与 Session 清理守卫、§5.6 退役协议（含退役失败即隔离＋halt）、锁的按根嵌套与有界等待、拒绝码 HTTP 映射、无根合法能力清单。
- **入口 IT 重基保留项**：`E2aRecoveryAdmissionEntryIT` 与 `C1EntryRegression` 的 live/unknown 检查仍按 S2 语义过时（primary 现为请求根数据源；它们未带 `s3-probe` 也不走过滤器绑定），本轮**未运行**，待专门重基；lost-commit 检查仍走测试配置的旧类。
- 未验：并发/多请求交叉、跨进程、Session 过期清理的真实生产路径（自有根）、Worker/文件/渲染入口、Linux/其它文件系统、断电/硬复位、现场组合。
- K1 仍未通过；窗口 W、`DEBT-R09-C2-LEASE-ANCHOR`、`UNCONFIRMED_NO_CROSS_PROCESS_BASIS`、IT-DISCOVERY、IT-EVIDENCE-AUDIT、TZ-GMT 继续单列。

## 5. 证据位置（不入库，按约定保留）

`.local/r09-c2-b1-3-s3-20260929-a/`：`run10-unit-full/`（全量单测）、`run1-s3-session`…`run9-s3-session`、`run11-s3-session`（真实 HTTP 逐轮，含 `s3-session-root.txt`、`real-records.tsv`、`gradle.log`、`exit.txt`、XML、`files.sha256`、`coordinates.txt`）、`it-batch-25/`（25 类回归逐类证据与 `batch-summary.json`）、`s3-compile*.log`、`commit-message*.txt`。实例 datadir 保留：`.local/mysql19454-s3session`、`.local/mysql19456-s3session`、`.local/mysql19457-r09batch`；端口均已释放、实例正常 shutdown。

## 6. 下一步

**S4**：multipart 暂存与错误分派范围的收尾判据（同 S3 判据），并按需补 S4a/S4b 的保存期屏障与新根拒绝用例；随后处理 §3 的三项发现（至少把"并发接纳"与"启动期无根能力"排入后续批次）。
