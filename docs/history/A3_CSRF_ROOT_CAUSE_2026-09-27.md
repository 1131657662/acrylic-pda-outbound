# A3 Web CSRF / Web 会话 POST 403 —— 根因定位与修复（2026-09-27，指令 v6）

**结论先行**：根因是**测试/运维层的 Cookie `Secure` 属性与明文 HTTP 冲突**（生产默认正确，未改），
外加两处**夹具探测方式**问题。**未改任何生产代码/配置默认值**。

## 一、症状沿革（先读旧记录，不从零开始）

| 时间/attempt | 现象 |
| --- | --- |
| attempt **150** | `E2aHandoverHttpLayerIT` 在**第 52 行**失败 —— `session.login(outsider)` 为假（`status=-1`），即 **Web 登录本身不成功**（本次实测该 attempt 目录仍在：`integration-test.log` 保存了同一行号） |
| R07-3 attempts **194-196** | 同一族：`GET /auth/csrf` 后 `POST /auth/web/login` 直接 **403 `ACCESS_DENIED`**（重取令牌再试仍 403）→ 当时判定"已知 A3 类问题"并改走 PDA 通道 |
| attempt **237**（批量回归） | 同上（`-1`） |
| attempt **263**（批量回归） | 服务层反例 IT 因夹具问题失败（与 CSRF 无关，见 R07-1 记录） |
| 真浏览器 | 文档记载亦为 403（`DEVELOPMENT_*` / 看板多处） |

**关键观察**：MockMvc 系测试（`with(csrf())`）从不复现该问题 —— 因为 MockMvc 直接注入令牌/会话，绕过了 Cookie 往返。
该问题**只在真 HTTP 上出现**。

## 二、根因 #1（主因）：会话 Cookie 默认 `Secure`，而 harness 走明文 HTTP

`server/src/main/resources/application.yml`：

```yaml
server:
  servlet:
    session:
      cookie:
        name: OUTBOUND_SESSION
        http-only: true
        secure: ${OUTBOUND_COOKIE_SECURE:true}   # ← 默认 true
        same-site: strict
```

- **默认 `Secure=true` 对生产（HTTPS）是正确的**，不应改动。
- 但 e2a harness 在 `http://127.0.0.1:19434` 上跑：**明文 HTTP 下 Secure Cookie 不会被回传** ⇒
  每个请求都落到一个新会话 ⇒ `/auth/csrf` 发的令牌绑定在"下一个 POST 无法出示的会话"上 ⇒
  **CsrfFilter 拒绝 → 403 `ACCESS_DENIED`**（不是 401，也不是凭据错误）✓ 与实测现象完全一致。
- 真浏览器在**非 localhost 的 http 地址**上同理（Chrome 把 `http://localhost` 视为安全上下文，这解释了为何"真浏览器也 403"
  与 Java 客户端都成立、而 MockMvc 不复现）。

**修复（测试侧，未动生产）**：`tools/e2a-provision-and-run-106.ps1` 在启动 IT 前显式
`$env:OUTBOUND_COOKIE_SECURE='false'`（该开关本就存在、生产默认仍为 `true`）✓ 提交 `0d14ba8`。

**证据**：`E2aHandoverHttpLayerIT` 从 attempt 150 起的"登录失败（`-1`）"变为
**①开关关闭+已登录会话 → 404 ✓**、**②匿名+令牌 → 401 ✓**（attempt **284/285**）⇒ 会话与登录链路已通 ✓✓

## 三、根因 #2：R07-1 `E2aHandoverHttpLayerIT` 的 ② 探测方式不成立

- 原 ② 直接向 `/handover-commands` 发**不带 CSRF 令牌**的匿名 POST，期望 401；实测 **403**。
  原因：Spring Security 的 **CsrfFilter 在认证之前**运行，无令牌的 POST 先被 CSRF 拒绝 ⇒ 401（认证入口）不可达。
- 修（只改探测构造，断言与语义不变）：②先 `GET /auth/csrf`（该端点 `permitAll`，会给匿名会话发令牌）再 POST ⇒ **401** ✓
  提交 `0d14ba8`。IT 自带的注释也早已写明"CSRF 失败与权限拒绝在 HTTP 层都是 403"。

## 四、仍未解的两处**夹具**问题（不属产品缺陷，记录为残余）
> **更新（指令 v11）**：本节的 **② `TemporaryCommandWebHttpChecks` 已收口** ✅，见文末「六」；
> ① R07-1 ③/④ 仍待专项。第 ②（原条目）保留原文如下，不删除失败与错误诊断记录。

1. **`E2aHandoverHttpLayerIT` ③/④**：③期望"登录但无权限 → 403"，④期望"有权限 → 到达业务层（400/404）"。
   实测 ③ 得 **404** ⇒ 因为其请求体用的是**全零 scope**，业务层在**查不到来源**时就先返回 404，**权限门根本没被触达**；
   且该 IT 的权限假设（REQUEST 需 `order:handover`）**早于内核的角色分离**（内核：REQUEST 需 `order:resume`、APPROVE 需 `order:handover`，
   见 `E2aFormalRegisteredCounterexamplesIT` 的注释）。要让它成立需**真实 scope 夹具 + 正确权限拆分**（约 15-30 行，属 R07-1 夹具扩展）。
2. **`TemporaryCommandWebHttpChecks`**（R06 Web 命令夹具）：登录成功后发出命令仍 403/401。
   attempt 287 的诊断输出显示 `login={"code":"OK",...,"authenticated":true}` 但随后 GET 事实面返回 **401** ⇒
   **登录响应轮换出的会话 Cookie 未被后续请求采用**（会话固定保护轮换 id；该夹具用手写 Cookie 头往返，且 `loggedIn` 标志从未置位）。
   修法：登录后**先取到轮换后的 Cookie 再刷新 CSRF 令牌**（我试过只加"登录后刷新令牌"，实测反而不够 ⇒ 已**回退**，不留半成品；
   该 IT 仍是红，属**待专项**）。

## 五、状态与边界

- **A3 CSRF 本身（"Web 会话 POST 403"）**：**根因已定位、测试侧已修复、有 attempt 284/285 证据** ⇒ 可从"待排期"改为
  **已定位并修复（测试侧）**；生产配置默认值**未改**（`Secure=true` 仍为默认，生产走 HTTPS 不受影响）。
- **仍未收口**：R07-1 ③/④ 的夹具、`TemporaryCommandWebHttpChecks` 的 Cookie 轮换采用、以及
  **R07-3 `/api/v1/corrections` 的 Web 路由真 HTTP 验证**（本轮 2-3 轮上限内未做，属"顺带项"）。
  这三项都**不是产品缺陷**，都需要**夹具基础设施**投入 ⇒ 记为**待专项**。
- **未改**：任何生产代码/配置默认值、R06/R07 已验证业务代码、TEMP 归档、Android LINK、FORMAL 夹具语义。
- **attempt**：284（诊断，红→定位）、285（①② 通过、③ 暴露夹具问题）、286/287（Web 命令夹具，未通过，已记录）。

---

## 六、A3 残余② 收口：`TemporaryCommandWebHttpChecks`（2026-09-27，指令 v11，attempts 355-364）

**结论**：R06 Web 命令夹具在**真 HTTP + Web 会话**下跑通 —— `E2aTemporaryCommandWebHttpIT`
**attempt 363（探针回滚后定版）与 364（诊断精简后）均 `tests=1 failures=0 errors=0 skipped=0`**，
`test-completed.marker` 已写。**仍未开放开关、未改任何生产代码/配置默认值、未改 R05/R06/R07 已验证业务代码。**

### 6.1 第一层根因（夹具）：CSRF 取令牌不带 Cookie ⇒ 匿名会话顶掉已认证会话

- **诊断先落地**（本轮先做的事）：登录响应此前只写进 `loginBody`，随后又紧跟一次 CSRF 刷新**覆盖了它**，
  所以那份"发布已认证会话"的响应从未进报告。改为**逐请求打印**（`[wire]`：方法/路径/`sentCookie`/状态/`Set-Cookie`）
  + 把登录交换存进不被覆盖的 `loginExchange`，并落 `SPRING_SESSION`（`SESSION_ID/PRINCIPAL_NAME`）对拍。
- **实测（attempt 356）**：
  - `GET /auth/csrf` → `sentCookie=(none)` → 服务器发**新会话**；`POST /auth/web/login` 带该会话 → 200
    且 `Set-Cookie: OUTBOUND_SESSION=N2Jk…`（会话固定保护**轮换** id）；`SPRING_SESSION` 中该行 `PRINCIPAL_NAME=test-…`（**已认证**）。
  - 登录后 `GET /auth/csrf` → **`sentCookie=(none)`** → 服务器再发一个新 id（`PRINCIPAL_NAME=null`），`send()` 把它写进 jar
    ⇒ **登录轮换出的已认证会话被匿名会话顶掉**；此后 `/facts` 必回 **401 UNAUTHENTICATED**，刷新令牌多少次都一样。
  - **不是"捕获不到 Cookie"**：`csrf()` 当时用 `HttpRequest.newBuilder(...)` 构造请求，**绕过了带 Cookie 的 `request(...)`**；
    `keep/restore` 那套补救因此永远拿到 `null`。
- **修法**：`csrf()` 改用 `request("/auth/csrf")`，令牌请求与业务请求携带同一会话。提交见下。

### 6.2 第二层根因（**接口口径不一致**，非夹具笔误）

会话通了以后，LINK 稳定 **409 `COMMAND_CONTEXT_CHANGED`**（定位＝`TemporaryLinkOperation:43` 的候选集复检）。
对拍两个值（attempt 361）：服务端复检 `digest=1439f4df…`，夹具送出的 `expected.candidateContextSha256=70f9e17b…`。

**两套 `contextSha256` 口径并存，且不可互换**：

| | 产出方 | 摘要输入 |
| --- | --- | --- |
| 复核页 `contextSha256` | `TemporaryDuplicateService.read`（`json.digest(List.of(warehouse,context,range))`，context 冗长：含 `receivedAt/status/orderRevision/containerNo/reviewerPermissionVersion/serverRecoveryEpoch`…） | 与命令侧**完全不同** |
| 命令 `candidateContextSha256` | `TemporaryDecisionCandidates.snapshot`（`sha(warehouseId+context+candidates)`，context 只含 `products/vehicleNo/submissionId/payloadSha256`） | 命令侧**唯一可接受**值 |

`CommandExpected.validate`（`CommandExpected.java:25-30`，`candidateContext=true` 时把该值并入期望）与
`TemporaryLinkOperation:41` 的锁定后复检，都只认**后者**；而夹具当时取的是**前者**。

**修法（测试侧，与既有驱动一致）**：`candidateDigest(...)` 改为调
`TemporaryDecisionCandidates.snapshot(...)` 取摘要（复核页返回值只留作 `total==1` 校验）。
`TemporaryArchiveDriver:44` 早就是这么做的 —— 这是本仓库既有的正确用法。

### 6.3 ⚠️ 影响面：**Web 复核页写操作（A2）取的是错口径**（本会话静态确认，未修、未实测）

- `web/src/temporary-review/TemporaryDuplicateCandidate.vue:5,30`：把**复核页返回的** `contextSha256`
  作为 `candidate-context-sha256` 传给 `TemporaryCommandAction` → `useTemporaryCommand.execute` → 写进 `expected.candidateContextSha256`。
- `web/src/temporary-review/TemporaryReviewPage.vue:73`（`NORMAL_CONFIRM`）**不传**该 prop ⇒ 走
  `useTemporaryCommand.ts:50-57` 的 fetch 分支，取的仍是
  `/temporary-reviews/{id}/duplicate-candidates` 的 `page.contextSha256` ⇒ **同一个错口径**（两条路都不对）。
- **后果**：开关一旦打开，A2 的 LINK 与 NORMAL_CONFIRM 都会**稳定 409 `COMMAND_CONTEXT_CHANGED`**
  （"原上下文已变化，请核对原意图"）—— 与 A3 残余② 的夹具症状同源、同一根因。
- **状态**：**待修**（记为接口一致性缺陷 / 行动项）；本轮**未改 web 代码**，也**未**开开关做真 HTTP 实测，
  故 A2 这条属**静态确认**，不是实测结论。
- **建议修法**：Web 复核页发起 LINK/NORMAL_CONFIRM 时，取**服务端候选集摘要**
  （`TemporaryDecisionCandidates` 口径，即把命令事实面 `/temporary-commands/facts` 补上该值，
  或由复核页接口另发一个命令口径字段），**不要**复用复核页的 `contextSha256`。
  最稳的修法是**让服务端把命令口径的摘要发出来**，避免再出现"页面口径 vs 命令口径"这类客户端可自行猜错的接口。

### 6.4 本轮证据与边界

- **attempt 台账**：355（401，诊断落地）→ 356（401 + `SPRING_SESSION` 对拍取证）→ 357/358/359（409，body/facts 对拍）
  → 360/361（守卫星树**临时探针**定位 `stale` 行号，**已回滚**）→ **362 绿**（探针仍在）
  → **363 绿（探针回滚后定版）** → **364 绿（诊断精简后）**。
- **探针回滚核对**：`CommandErrors.java` +12/−1、`TemporaryLinkOperation.java` +8/−0 两处**仅存在于守卫星树**
  `r06-e2a-migration`；回滚后与主树**逐字节相同**，主树 `git grep A3-DIAG` 为 0 条。
- **未验边界**：① Web 复核页真浏览器 UI 路径未跑（仍受"开关默认关闭"约束）；② A2 错口径**未实测**（见 6.3，属静态确认）；
  ③ 未做并发/多标签重放；④ 未跑全量 IT；⑤ 本 IT 只在隔离库 + 明文 HTTP（`OUTBOUND_COOKIE_SECURE=false`，harness 专用）下通过，
  **生产仍为 `Secure=true` 默认、未动**。

