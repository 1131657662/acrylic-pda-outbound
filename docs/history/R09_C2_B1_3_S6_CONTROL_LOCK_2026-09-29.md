# R09 C2 B1-3 S6：控制锁按根可嵌套与有界等待（2026-09-29）

> 轮次身份：主树 `D:/acrylic-pda-outbound`、分支 `codex/development`；写者＝DSH 本会话；**同一会话实施＋自审＋运行，非独立第三方验收**。授权：用户 2026-09-29"继续推荐的下一步"＝**②锁的按根可嵌套＋有界等待（设计第 13 行）**。边界：仍**不开任何生产开关**，不接生产、不部署、不合 main。起点 HEAD `922a062`（S5 收口）。

## 1. 问题与设计依据

- 设计 §2.1（接口与前置条件）、§2.2（按根可嵌套与层号）、§9 第 13 行（`RecoveryControlLock`：按根信号量、深度/集合、层号），以及 R1 的锁序要求：**固定 P3(1)→P2(2)→P5(3)，逆序即 `CONTROL_LOCK_ORDER_VIOLATION`**。
- 原实现是**单 JVM 信号量＋单值 `ThreadLocal<Path>`**，两个后果：①同一线程不可能同时持有两个根 ⇒ 设计要求的"P3 锁内取 P2 锁"结构上做不到；②`tryAcquire` 无等待 ⇒ S3 现场之外的并发请求在接纳段直接 `CONTROL_COORDINATOR_BUSY`（S3 记录发现 2，已有确定性单测证明）。本轮把这两点按设计改掉。

## 2. 实现

| 文件 | 改动 |
| --- | --- |
| `RecoveryControlLock`（重写） | **按根信号量**（键＝`coordinator.lock` 规范路径）＋**线程级根集合**（根→层号）。层号 `AUTHORITY_LAYER=1`(P3)／`STORE_LAYER=2`(P2)／`LEASE_PROCESS_LAYER=3`(P5)。同根重入按**误用**拒绝：立即式仍 `CONTROL_COORDINATOR_BUSY`（语义不变），等待式 `CONTROL_COORDINATOR_REENTRANT`；层号不严格递增 ⇒ `CONTROL_LOCK_ORDER_VIOLATION`。新增 `lockedWaiting(root, timeoutMillis, read)`：预算范围 `1..10000` 校验（`CONTROL_LOCK_TIMEOUT_RANGE`），JVM 信号量与**跨进程文件锁**都在同一预算内重试，预算耗尽仍是拒绝（`CONTROL_COORDINATOR_BUSY`）。`requireOwner` 改为按当前线程根集合判定（语义与错误码不变） |
| `InitialNormalStore` | `locked(...)` 明确使用 `STORE_LAYER`（P2），使层号有真实第二层调用点（P3 内取 P2 由此成为可能，符合 R1） |
| `RecoveryOperationRegistry.admit` | 改用 `lockedWaiting(..., ADMISSION_WAIT_MILLIS = 2000ms)`：并发请求**排队**而不是被拒；预算耗尽仍失败关闭 |

新增 `RecoveryControlLockTest` 8 项：不同根按层嵌套且两把都在本线程名下；逆序 `CONTROL_LOCK_ORDER_VIOLATION` 且外层已释放；同根重入在两种入口下的不同码；等待式预算范围校验；等待者在持有者释放后被接纳（**排队而非被拒**）；预算耗尽仍 `CONTROL_COORDINATOR_BUSY` 且有界；不同根跨线程互不阻塞；非本线程根 `CONTROL_COORDINATOR_REQUIRED`。

## 3. 验证

### 3.1 一级

- 定向（`run1-s6-targeted`）：6 类 / **44 项 / 0 失败**（新锁 8 项、`InitialNormalStoreTest` 10、`RecoveryControlReaderTest` 5、`RecoveryWriterLeaseTest` 6、`RecoveryRequestAdmissionFilterTest` 9、`InitialNormalVerificationTest` 6）。
- 全量（`run2-unit-full`，`gradlew -p server test`，不带 `--tests` 白名单）：**128 类 / 2814 项 / 0 失败 / 0 错误 / 1 既有无关 skip**；`com.acrylic.outbound.recovery.*` **33 类 / 2176 项 / 0 失败**。
- **语义变更（唯一一处，且是设计明文要求的）**：同根重入在**等待式**入口的码由 `CONTROL_COORDINATOR_BUSY` 变为 `CONTROL_COORDINATOR_REENTRANT`（设计 §2.1 的规定）；立即式入口保持 `BUSY`，故 `RecoveryControlProcess.nested`、`InitialNormalStoreTest:89`、`RecoveryControlReaderTest:55` 三项既有断言**不改也全绿**；S3 期我自己写的“临界区内再接纳”单测按新语义更新为 `REENTRANT`（该用例原记录的是旧限制，非产品契约）。

### 3.2 二级 现场：并发接纳（`run1-s6-concurrency`，全新实例 19464／应用 19458，`migrate executed=49`）

- `concurrent requests=4 codes=[200,200,200,200]`：**四个重叠请求全部被接纳**（旧语义下会是接纳段 `CONTROL_COORDINATOR_BUSY`）。
- `concurrentRoots=[4,3,2,1]`：在每个请求观测的那一刻，注册表里分别有 4/3/2/1 个存活根 ⇒ **真的重叠**，不是被串行成一次一个。
- 会话保存在并发窗口内出现 `liveRoots=4,4,3,3,2,2,1,1` ⇒ 重叠请求各自在自己的根内保存；轨迹 `/__s3/concurrent` 四趟依次 `liveRoots=1,2,3,4` ⇒ **接纳段串行化、每趟恰好一个新根**。
- 收尾：`liveRootsAfter=0`（无泄漏）、`springSessionThreads=[]`；同轮 S3/S4 判据（session/error/async/multipart）与清理守卫真实入口全部复绿（**2 个用例 / 0 失败**）。

### 3.3 二级 回归：恢复链 25 类 IT 批次

`tools/r09-recovery-it-batch.ps1`，全新专用实例 **19465**、schema＝账号：**25 类 / 696 项 / 0 失败 / 0 错误 / 0 跳过 / 0 类失败**，`NOT-COVERED` 为空，TIME_WAIT 峰值 1393（节流回落），收尾 `exit=0`、`port_listening_after=0`。锁语义是本轮唯一改动的产品面，而恢复链 IT（`RecoveryFence*`、`G0K1*`）正是重锁路径的消费者，因此该批次是必要的回归证据：按根信号量、层号与有界等待没有破坏围栏/租约/跨进程语义。

## 4. 未做与边界

- 仍是"只接线、不开开关"：`admission.enabled`、`effect-gate.enabled`、跨进程开关默认 false；未接生产、未部署、未合 main；K1 仍未通过。
- **P5 层暂无取锁调用点**：`LEASE_PROCESS_LAYER` 已定义，但 P5 进程文件仍由 `RecoveryLeaseProcess` 自己的文件锁承担（B1-2 基座未接入应用的启动路径）；阶段 0 安全前置（设计第 8–12 行）因此仍未做。
- **未强制"P2 必须持 P3"**：设计 §2.1 未列该前置条件，本轮只做"逆序拒止"；若后续 K4 需要，应作为独立约束补上并配用例。
- 未验：**跨进程并发**（两个 JVM 同根并发接纳 ⇒ 文件锁重试路径）、生产负载下的等待时间分布、Linux/其他文件系统上的重试语义、进程内多根长时间并存的内存增长（信号量表按根保留，条目极小但**不回收**，已在实现注释说明）。
- 未做（后续）：拒绝码 HTTP 映射（§4.4/第 20 行）、入口 IT 重基（`E2aRecoveryAdmissionEntryIT`、`C1EntryRegression`）、效果门接线、§5.6 退役协议；S4 记录的 multipart 超限 413 偏差仍在。

## 5. 逐轮失败保留（未删）

| 轮 | 现象 | 处置 |
| --- | --- | --- |
| `run1-s6-targeted`（首版） | 编译失败：`CountDownLatch.await` 在 `Read<T>` lambda 内抛 `InterruptedException`（该函数式接口只允许 `IOException`） | 增加 `await(latch)` 辅助方法把中断转成 `IOException`，三处调用点改用之 |
| `run1-s6-targeted` | **绿**（44 项 0 失败） | 一级定向证据 |
| `run1-s6-concurrency` | **绿**（2 项；含 4 路并发） | 二级现场证据 |
| `run2-unit-full` | **绿**（2814 项 0 失败） | 一级全量证据 |

## 6. 证据位置（不入库，按约定保留）

`.local/r09-c2-b1-3-s6-20260929-a/`：`run1-s6-targeted`、`run1-s6-concurrency`（含 `s3-session-root.txt`、`s5-cleanup-guard.txt`、`real-records.tsv`、`gradle.log`、`exit.txt`、XML、`coordinates.txt`）、`run2-unit-full`、`it-batch-25/`（逐类证据＋`batch-summary.json`）、`s6-compile*.log`。实例 19464 已正常 shutdown、端口释放；datadir 与批次 datadir 保留。

## 7. 下一步

S3 记录里的"并发 HTTP 未验"由此闭合。接下来：**③拒绝码 HTTP 映射（§4.4/第 20 行，含 multipart 超限 413 与 S4 记录的 500 偏差）**→**④入口 IT 重基**（启动阻塞已在 S5 解除）；阶段 0 安全前置（第 8–12 行，需要 `leaseRoot`／锚点秘密／`closedAuthority` 配置与 provision 协议）建议作为独立批次。均需独立授权与验收，不改默认开关。
