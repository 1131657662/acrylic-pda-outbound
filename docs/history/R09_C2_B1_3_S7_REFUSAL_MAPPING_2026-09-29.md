# R09 C2 B1-3 S7：拒绝码 HTTP 映射与无状态拒绝写出口（2026-09-29）

> 轮次身份：主树 `D:/acrylic-pda-outbound`、分支 `codex/development`；写者＝DSH 本会话；**同一会话实施＋自审＋运行，非独立第三方验收**。授权：用户 2026-09-29"继续推荐的下一步"＝**③拒绝码 HTTP 映射（设计 §4.4／第 20 行）**。边界：仍**不开任何生产开关**，不接生产、不部署、不合 main。起点 HEAD `7d0ae5f`（S6 收口）。

## 1. 问题

设计 §4.4 要求"关闭意图／孤儿／围栏／无根一律 503"，第 20 行要求 `ApiResponses.writeWithoutState`——**拒绝写出口不读 DB**。原实现两点都不满足：

1. 接纳阶段的 `RecoveryControlException` 从过滤器冒出到容器 ⇒ 客户端拿到 Tomcat 的 **500 HTML**（实测 S3/S5 现场）；服务中的恢复拒绝走通用 `unexpected` ⇒ **500 JSON**。
2. 错误出口本身会读库（`ApiResponses.body` → `ServerState.current()` → 主数据源）；在被围栏/无根的实例上，这次读**又会被拒**，把一次拒绝变成"错误处理器自己失败"⇒ 500。现场 run1 实测到该二次失败。

## 2. 实现（产品代码）

| 文件 | 改动 |
| --- | --- |
| `ApiResponses`（common） | 新增 `writeWithoutState(request, response, status, code, message)`：同一 envelope 形状，`serverRecoveryEpoch="unknown"`、`serviceMode="UNAVAILABLE"`，**不调用 `ServerState`**。另把 `body(...)` 的状态读取 catch 从 `DataAccessException` 放宽到 `RuntimeException`——报告状态不得把一次失败变成另一次失败（这是行为变更，见 §5） |
| `RecoveryRefusalAdvice`（recovery，新） | `@RestControllerAdvice @Order(HIGHEST_PRECEDENCE)`：请求处理中抛出的 `RecoveryControlException` ⇒ **503 ＋ 原拒绝码**，经无状态写出口。**排序是承重的**：否则通用 `ApiErrors.unexpected`（读库）先匹配，在被围栏的库上二次失败 ⇒ 500（run1 实测） |
| `RecoveryRequestAdmissionFilter`（recovery） | 接纳发生在框架之前，故由过滤器自己写 **503**（同一无状态写出口）并直接返回，不再把拒绝冒到容器；构造参数新增 `ApiResponses` |
| `ApiErrors`（common） | `tooLarge` 改为无状态 413；新增 `MultipartException` 处理器：cause 链里是尺寸限制（`MaxUploadSizeExceededException` 或 `*SizeLimitExceededException`）⇒ **413**，其他解析失败 ⇒ **400 `INVALID_REQUEST`**；两者都不读库 |
| `RecoveryAdmissionConfiguration` | 装配 `RecoveryRefusalAdvice`，并把 `ApiResponses` 注入过滤器（仍仅 `admission.enabled=true` 生效） |

新增 `ApiResponsesTest` 4 项（设计第 20 行点名的测试）：无状态拒绝不读库（`verifyNoInteractions(state)`）、正常 envelope 仍带状态、413 与 400 两条无状态映射。`RecoveryRequestAdmissionFilterTest` 的接纳拒绝用例改为断言 **503 ＋ `CONTROL_INSTANCE_STALE` ＋ 不读库**。

## 3. 验证

### 3.1 一级

- 定向（`run3-s7-targeted`）：6 类 / **24 项 / 0 失败**（`ApiResponsesTest` 4、过滤器 9、注册 2、启动门 3、入口配置 4、无根能力白名单 2）。
- 全量（`run5-unit-full`，不带 `--tests` 白名单）：**129 类 / 2818 项 / 0 失败 / 0 错误 / 1 既有无关 skip**；`recovery` 包 ＋ `ApiResponsesTest` 合计 **34 类 / 2180 项 / 0 失败**。

### 3.2 二级 现场（`run4-s7-refusals`，全新实例 19466／应用 19458，`migrate executed=49`，2 用例 0 失败）

| 场景 | 观测 |
| --- | --- |
| 服务中围栏（`/__s3/fenced`：先围栏当前根再借用） | **503** `RECOVERY_LEASE_FENCED`，body `serverRecoveryEpoch=unknown`／`serviceMode=UNAVAILABLE` |
| 部件超限（2.5MiB，> 2MB 部件上限） | **413**（此前 500） |
| 畸形 multipart（有 boundary、无结束分隔符） | **400** `INVALID_REQUEST`，无状态 |
| 权威过期后的**接纳**（把权威推进一格后请求） | **503** `CONTROL_INSTANCE_STALE`，无状态，**零根登记** |
| 并发与既有判据 | 4 路并发全部 200、`liveRootsDuringRequests=[1,…]`、三次解析全 `BOUND`、`liveRootsAfter=0`、`springSessionThreads=[]` |

解析期观测（探针）进一步给出真实异常类型：`parses=[Parse[bound=BOUND, stagedFiles=1, outcome=OK], Parse[…, outcome=MaxUploadSizeExceededException], Parse[…, outcome=MultipartException]]`。

### 3.3 二级 回归：恢复链 25 类 IT 批次

`tools/r09-recovery-it-batch.ps1`，全新专用实例 **19467**、schema＝账号：**25 类 / 696 项 / 0 失败 / 0 错误 / 0 跳过 / 0 类失败**，`NOT-COVERED` 为空，TIME_WAIT 峰值 1423（节流回落），收尾 `exit=0`、`port_listening_after=0`。本轮改了错误出口与拒绝映射（产品代码），该批次确认恢复链未受影响。

## 4. 口径更正：撤回 S4 记录的"multipart 超限 500"定性

[S4 记录 §2](R09_C2_B1_3_S4_MULTIPART_2026-09-29.md) 曾把"超限部件 ⇒ 500、应用 413 映射不适用"记为**既有错误映射问题**。本轮查明其真实成因是**测试仪表**：S4 轮引入的 `RecoveryMultipartParseRecorder` 用 JDK 动态代理包住容器 `multipartResolver`，而代理里 `method.invoke` 抛出的 `java.lang.reflect.InvocationTargetException` **未被解包**，代理于是把真实异常包装成 `java.lang.reflect.UndeclaredThrowableException`（现场 run2 的栈可证），使应用的 413 处理器不匹配、落到通用 500 处理器，而后者读库成功（因此当时看到的是"正常状态 + INTERNAL_ERROR"，掩盖了真因）。

修正探针（catch `InvocationTargetException` 后抛出其 cause）后，解析失败的类名就是 **`MaxUploadSizeExceededException`** ⇒ **413**。因此：

- **撤回** S4 记录中"产品侧超限映射不适用"的定性；产品侧 `MaxUploadSizeExceededException ⇒ 413` 在 S4 之前即已生效。
- 本轮的真实产品改动是：超限处理器改为**无状态**、新增**非超限 multipart 解析失败 ⇒ 400**、以及恢复拒绝一律 **503 且不读库**。
- 教训入档：**测试侧的观测代理必须透明地转抛原始异常**，否则会把仪表缺陷记成产品缺陷。

## 5. 行为变更与已知限制

- 行为变更：`ApiResponses.body(...)` 在读取 `server_state` 失败时（现在包含 `RecoveryControlException` 等 `RuntimeException`）报告 `unknown/UNAVAILABLE`，而不是让错误响应二次失败。正常可读时行为不变（envelope 仍带真实 epoch/mode）。
- 已知限制：**接纳拒绝的 `requestId` 可能为 `null`**——我们的请求根过滤器为 `HIGHEST_PRECEDENCE`，在 `RequestIdFilter` 取号之前就可能拒绝。拒绝码与状态不受影响；如需始终带号，应单独调整过滤器顺序（本轮未动其他组件顺序）。
- 未改：`BusinessException`／校验 400／404／409 等既有处理器仍按原样带状态（它们只在请求已被解析、实例仍在服务时到达；库不可读时按 §5 第 1 条降级）。

## 6. 未做与边界

- 仍是"只接线、不开开关"：开关默认 false；未接生产、未部署、未合 main；K1 仍未通过。
- 未做：阶段 0 安全前置（设计第 8–12 行，需 `leaseRoot`／锚点秘密／`closedAuthority` 配置与 provision 协议）、**入口 IT 重基**（`E2aRecoveryAdmissionEntryIT`／`C1EntryRegression`；启动阻塞已在 S5 解除）、效果门接线、§5.6 退役协议、完整接纳门 S0–S4 与 P5 前后探针/租约发布。
- 未验：并发拒绝（多请求同时被拒的计数/日志）、PDA 客户端对 503 envelope 的消费、跨进程、Linux/其他文件系统、断电/硬复位、现场组合。

## 7. 逐轮失败保留（未删）

| 轮 | 现象 | 处置 |
| --- | --- | --- |
| `run1-s7-refusals` | `/__s3/fenced` 返回 **500 HTML**：通用 `ApiErrors.unexpected` 先匹配并读库，被围栏的库拒绝 ⇒ 错误处理器二次失败 | 给 `RecoveryRefusalAdvice` 加 `@Order(HIGHEST_PRECEDENCE)`；并放宽 `body(...)` 的状态读取 catch |
| `run2-s7-refusals` | 超限仍 500：探针代理把 `MaxUploadSizeExceededException` 包成 `UndeclaredThrowableException` | 探针解包 `InvocationTargetException` 的 cause（见 §4） |
| `run3-s7-refusals` | 断言"2 次解析"实际 3 次（畸形步也经过解析） | 断言改 3 次并新增"尺寸失败被记为 `*Size*`"的断言 |
| `run4-s7-refusals` | **绿**（2 用例） | 本轮二级现场证据 |
| `run1-s7-targeted` | 既有上下文用例缺 `ApiResponses` bean（过滤器新依赖） | 三个 `ApplicationContextRunner` 补该 bean（mock `ServerState`） |
| `run2-s7-targeted` | 我插入 `withBean` 时误置分号，`compileTestJava` 失败而 XML 仍显示旧结果 | 修正语句并复跑（`run3-s7-targeted` 24/0） |

## 8. 证据位置（不入库，按约定保留）

`.local/r09-c2-b1-3-s7-20260929-a/`：`run1/run2/run3-s7-targeted`、`run1..run4-s7-refusals`（含 `s3-session-root.txt`、`s5-cleanup-guard.txt`、`real-records.tsv`、`gradle.log`、`exit.txt`、XML）、`run5-unit-full`、`it-batch-25/`、`s7-compile*.log`。实例 19466 已正常 shutdown、端口释放；datadir 与批次 datadir 保留。

## 9. 下一步

按优先级：**④入口 IT 重基**（`E2aRecoveryAdmissionEntryIT`、`C1EntryRegression` live/unknown；启动阻塞已解除，语义务必按"请求根 + 503 拒绝"重写并保留失败证据）→**阶段 0 安全前置（第 8–12 行）**作为独立批次（需 `leaseRoot`／锚点配置与 provision 协议）。均需独立授权与验收，不改默认开关。
