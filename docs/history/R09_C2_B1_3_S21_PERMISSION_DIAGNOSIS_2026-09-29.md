# R09 C2 B1-3 S21：权限/角色前提的第三次尝试与决定性诊断（2026-09-29）

> 轮次身份：主树 `D:/acrylic-pda-outbound`、分支 `codex/development`；写者＝DSH 本会话；**同一会话实施＋自审＋运行，非独立第三方验收**。授权：用户 2026-09-29"继续"＝**权限/角色种子与迁移目标**（S20 列的下一步①）。边界：不开生产开关、不接生产、不部署、不合 main。起点 HEAD `e6dc82f`。**无产品代码改动**（只改 harness 环境变量）。

## 1. 本轮做了什么

按参考 runner（`tools/e2a-cross-source-verify.ps1` L110–113）对照，我的 harness 少了它导出的四个环境变量，补上后复跑三个受阻 IT：
`OUTBOUND_MIGRATE=false`、`OUTBOUND_STORAGE=<attempt>/storage`、`OUTBOUND_AUTH_SIGNING_PRIVATE_KEY=<attempt>/private/server-private.der`、`OUTBOUND_AUTH_SIGNING_PUBLIC_KEY=<attempt>/private/server-public.der`（这三个文件由 IT 自己调用的 `E2aCrossSourceHttpSetup.signing(attempt)` 写入 ✔）。

## 2. 结果：**没有变化**（诚实记录）

| run | IT | 失败点（与补变量前一致） |
| --- | --- | --- |
| 651 | `E2aCorrectionChainIT` | `[real Web login]` |
| 652 | `E2aHandoverHttpLayerIT` | `[① switch off must be an absent route]`（status=-1，登录未成功） |
| 653 | `E2aTemporaryCommandBrowserIT` | `steps=[… "login 200 authenticated=true","facts#1 200","candidates 200","LINK 403 ACCESS_DENIED"]` |

⇒ **这四个环境变量不是这些 403 的原因**（至少不充分）；该结论本身是有效信息：把"缺参考环境变量"这一假设**排除**了。

## 3. 决定性诊断（对 653 保留的 datadir 离线查库）

我把 653 那次 attempt 的 MySQL datadir 单独挂到 19331 端口查（查完已 shutdown）：

```
permission 中 temporary 前缀行数 = 4
  temporary_order:confirm / temporary_order:create / temporary_order:link / temporary_order:manage
role_permission 中涉及 temporary% 的授予行数 = 7
user_role 行数 = 6
```

⇒ **权限行存在、且已被授予**（7 条授予）。因此 653 的 `LINK 403 ACCESS_DENIED` **不是"权限行缺失/未授予"**，而更可能是：
①**作用域/仓库绑定**（scope）不匹配该业务对象 ⇒ 门按 `ACCESS_DENIED` 拒；
②该步骤要求的其实是**另一个**权限码（例如 `temporary_order:manage`）而该 actor 没有；
③本 harness 启动应用时**少了某个 `outbound.temporary.*` 开关**，使默认路径与 IT 期望不同；
④（可能性较低）CSRF/epoch 相关，但轨迹显示 CSRF 步骤已过。

## 4. 价值与结论

- 本轮**排除**了两个假设（缺参考环境变量、缺权限行/授予），并把余下方向收窄到**作用域/权限码/临时模块开关**三选一；诊断所依据的事实（权限行与授予计数）已留证。
- **未解决**：633／642／645 仍判**未验**。停止继续盲试的理由：每轮都要一次性实例（约 1–2 分钟）且三者的期望各不相同，需要先**读它们自己的步骤日志/断言上下文**（它们的 `log=` 为空，说明测试 stdout 未被转发）——正确做法是给 harness 加"转发测试 stdout"的能力（Gradle `testLogging.showStandardStreams` 或 `--info`），再逐项比对。
- 未做（产品侧）：Python 侧 v2 供给与 `closedAuthority` 身份（B9，需先定锚点政策）、生产级 provision、§5.6 终端语义、完整接纳门 S0–S4。
- 开关四项默认 false；未接生产、未部署、未合 main；K1 仍未通过（窗口 W、`UNCONFIRMED_NO_CROSS_PROCESS_BASIS` 原样保留）。
- **回归说明**：无产品代码改动 ⇒ 恢复链 25 类批次沿用 S12 轮同产品 HEAD 证据（实例 19476：25/696/0/0）。

## 5. 证据位置（不入库，按约定保留）

`.local/e2a-cross-20260925-651…653`（含 `TEST-*.xml`、`gradle.log`、`exit.txt`、datadir）；653 的 datadir 被离线挂载查询后已正常 `mysqladmin shutdown`（诊断日志 `.local/mysql19331.log`）。端口 19330／19331 均已释放。

## 6. 下一步（明确、可执行）

①**给 harness 加测试 stdout 转发**（Gradle `testLogging.showStandardStreams=true` 或 `--info`），把 651／652／653 的 `log=` 与断言上下文取出来 ⇒ 逐项判定"作用域／权限码／临时模块开关"；
②接着比对 653 的 actor 与业务对象的作用域（warehouse）关系；
③Android 工具链（644／647）；④V36 门＋供给；⑤**Python 侧 v2 供给与封闭权威身份（B9）**——需先定锚点保管/轮换政策。均需独立授权/验收，不改默认开关。
