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
