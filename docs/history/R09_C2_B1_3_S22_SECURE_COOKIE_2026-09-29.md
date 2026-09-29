# R09 C2 B1-3 S22：Secure cookie／CSRF 根因——633 与 642 转为已验（2026-09-29）

> 轮次身份：主树 `D:/acrylic-pda-outbound`、分支 `codex/development`；写者＝DSH 本会话；**同一会话实施＋自审＋运行，非独立第三方验收**。授权：用户 2026-09-29"继续"＝**给 harness 加测试 stdout 转发后判定 651／652／653**（S21 列的下一步①）。边界：不开生产开关、不接生产、不部署、不合 main。起点 HEAD `12bf3ae`。**无产品代码改动**。

## 1. 做法与根因

1. 给 harness 加 `-GradleInfo`（Gradle `-i`）⇒ 测试 JVM 的 stdout/stderr 进入 harness 日志（654–656 复跑）。日志显示迁移正常（V44/V47 都跑到），失败仍是那三处断言。
2. 读源码看登录流程：`CorrectionExportHttpClient.authenticate(...)` 走 `GET /api/v1/auth/csrf` → `POST /api/v1/auth/web/login`（form: `username/password/_csrf`），断言 **200**。
3. **决定性诊断**：把 655 的保留 datadir 离线挂到 19332 查库 —— **没有任何 `WEB_LOGIN_FAILED` 审计行**（事件类型里只有 `PDA_LOGIN` 等）⇒ 那个 **403 根本没走到登录逻辑**，是**安全过滤器（CSRF）**在认证之前拦下的。
4. 对照通过的 `E2aTemporaryExportIT`：它显式传 `--server.servlet.session.cookie.secure=false`，而失败的 IT 没传 ⇒ **Secure 会话/CSRF cookie 在明文 HTTP 下不会被 JDK HttpClient 回送** ⇒ CSRF 校验失败 ⇒ 403。

## 2. 改动（仅 harness）

`tools/r09-e2a-entry-rerun.ps1`：新增 `-GradleInfo` 开关（转发测试 stdout/stderr），并新增环境变量
`SERVER_SERVLET_SESSION_COOKIE_SECURE=false`（显式 `--server.servlet.*` 参数仍优先）。
其余沿用既有配方（`OUTBOUND_MIGRATE/STORAGE/AUTH_SIGNING_*`、renderer-home、browsers 回退、schema 族、binlog/PROCESS、`integrationTest`）。

## 3. 结果

| run | IT | 结果 |
| --- | --- | --- |
| 657 | `E2aCorrectionChainIT` | **✔ 通过**（43.0s）——**633 由未验转已验** |
| 658 | `E2aHandoverHttpLayerIT` | **✔ 通过**（16.8s）——**642 由未验转已验** |
| 659 | `E2aTemporaryCommandBrowserIT` | ✗ 仍失败，但**性质已澄清**（见 §4） |

⇒ **E2a 主树已验证清单增至 18 个**：604、621、622、623、628、629、630、631、634、635、638、639、640、641、643、648、**657、658**；C1 侧 611 通过。

## 4. 659（645 的同源项）剩余问题已澄清

它 **登录成功**（`login 200 authenticated=true`）后在 `LINK` 步骤被 **403 ACCESS_DENIED**；结合 S21 的库诊断（`temporary_order:{confirm,create,link,manage}` 权限行 4、授予 7 均在），CSRF/Secure-cookie 这一类已被本轮排除 ⇒ 余下就是**作用域（warehouse）绑定**或**该步骤实际要求另一个权限码**（不是 cookie、不是渲染、不是权限行缺失）。仍判**未验**，且不再属于"harness 环境未供给"的模糊类别。

## 5. 价值

- 一举把两个长期未验项（633、642）转为**已验**，并把"403 类"失败从"权限/环境不明"收敛为一个**具体 harness 配置根因**（Secure cookie + 明文 HTTP + CSRF）。
- 本轮的 `-GradleInfo` 能力对后续所有 IT 排障都有用（IT 自身的 `log=` 可读）。

## 6. 未做与边界

- 659 的"作用域／权限码"判定（需读该 IT 的 LINK 请求构造与所授权限的作用域，或加响应体日志）。
- Android 单测工具链（644／647 与 Android/链接类）；V36 家族门＋供给（6 个门）；树内 Playwright chromium 安装（替代跨树引用）；其余门控 IT。
- 产品侧：Python 侧 v2 供给与 `closedAuthority` 身份（B9，需先定锚点政策）、生产级 provision、§5.6 终端语义、完整接纳门 S0–S4。
- 开关四项默认 false；未接生产、未部署、未合 main；K1 仍未通过（窗口 W、`UNCONFIRMED_NO_CROSS_PROCESS_BASIS` 原样保留）。
- **回归说明**：无产品代码改动 ⇒ 恢复链 25 类批次沿用 S12 轮同产品 HEAD 证据（实例 19476：25/696/0/0）。

## 7. 证据位置（不入库，按约定保留）

`.local/e2a-cross-20260925-654…659`（654–656 为 `-i` 复跑的失败轮，657／658 通过，659 失败并已定性）；655 的 datadir 被离线挂载查询后已正常 shutdown（日志 `.local/mysql19332.log`）。端口 19330／19332 均已释放。

## 8. 下一步

①**659 的作用域／权限码判定**（读其 LINK 构造与授予作用域，必要时加响应体日志）；②Android 单测工具链（644／647）；③V36 门＋供给；④树内 chromium 安装；⑤**Python 侧 v2 供给与封闭权威身份（B9）**——需先定锚点保管/轮换政策。均需独立授权/验收，不改默认开关。
