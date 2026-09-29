# R09 C2 B1-3 S25：659 的 403 根因修复（测试侧）＋推进到业务级 409（2026-09-29）

> 轮次身份：主树 `D:/acrylic-pda-outbound`、分支 `codex/development`；写者＝DSH 本会话；**同一会话实施＋自审＋运行，非独立第三方验收**。授权：用户 2026-09-29"继续"＝**659 的 CSRF 轮换/驱动令牌判定**（S24 的二选一）。边界：不开生产开关、不接生产、不部署、不合 main。起点 HEAD `5286596`。**改动仅在 Web 测试驱动**（无产品代码改动）。

## 1. 根因（代码级确认）

`web/tools/e2a-web-command-flow.mjs`（IT 用 `ProcessBuilder("node", …)` 拉起的 SPA 驱动）**只在登录前取一次** `/api/v1/auth/csrf`（旧 L25–29），随后 `POST /api/v1/temporary-commands`（L60–62）仍复用同一个 `csrfHeader/csrfToken`。Spring Security **认证后会轮换 CSRF 令牌** ⇒ 旧令牌在**业务之前**被拒 ⇒ 403 `ACCESS_DENIED`「权限不足或安全凭据失效，请刷新后重试」，且**不留审计行**（与 S24 的库证据一致 ✔）。

## 2. 改动（仅测试驱动）

`web/tools/e2a-web-command-flow.mjs`：把取 CSRF 抽成 `readCsrf(label)`，登录前调用一次；**登录成功后再次调用**（`csrf (after login)`）。`node --check` 通过。

## 3. 结果：403 消失，阻塞前移到业务级 409

run 661：`steps=["csrf X-CSRF-TOKEN epoch=1","login 200 authenticated=true","csrf (after login) X-CSRF-TOKEN epoch=1","facts#1 200","candidates 200","LINK 409 COMMAND_CONTEXT_CHANGED"]`，响应体 `code=COMMAND_CONTEXT_CHANGED`、`message="原上下文已变化，请核对原意图"`、`serverRecoveryEpoch=1`、`NORMAL`。

⇒ **安全层问题已彻底关闭**（403 不再出现）；现在卡在**业务级乐观并发守卫**：`facts#1` 之后、`LINK` 之前，驱动还调了 `/temporary-reviews/{submissionId}/duplicate-candidates`，该「候选」读取很可能推进了上下文（如 `candidateContextSha256`/`scopeRound`），使 `facts#1` 的快照过期，于是 LINK 报 `COMMAND_CONTEXT_CHANGED`。**这是产品守卫按设计工作**，不是缺陷。

## 4. 下一步（最小）

在驱动里把「候选摘要」取回后**重新读一次 facts**（或用最新 facts 的 `expected` 构造 LINK 命令），再发 LINK；若通过则 659 转已验（将是第 19 个）。这与 S22/S24/S25 一脉相承：**先把安全/凭据类问题清零，再判业务守卫**。

## 5. 状态

- E2a 主树已验证 **18 个**（604、621、622、623、628、629、630、631、634、635、638、639、640、641、643、648、657、658）＋C1 611；659 **仍未验**但阻塞已从"安全层"前移到"上下文新鲜度"。
- 未验：659（业务级 409）、644／647（Android 工具链）、V36 家族（门＋供给）。
- 开关四项默认 false；未接生产、未部署、未合 main；K1 仍未通过（窗口 W、`UNCONFIRMED_NO_CROSS_PROCESS_BASIS` 原样保留）。
- 回归说明：改动仅在 Web 测试驱动（该文件不被产品运行时引用）⇒ 恢复链 25 类批次沿用 S12 轮同产品 HEAD 证据（实例 19476：25/696/0/0）。

## 6. 证据位置（不入库，按约定保留）

`.local/e2a-cross-20260925-661/browser-command-flow.json`（`csrf (after login)` ＋ `LINK 409 COMMAND_CONTEXT_CHANGED`）、`-661` 的 `TEST-*.xml` 与 datadir；端口 19330 已释放。
