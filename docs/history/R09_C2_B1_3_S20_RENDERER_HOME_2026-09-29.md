# R09 C2 B1-3 S20：报表/渲染服务供给（646 通过、633 推进到权限前提）（2026-09-29）

> 轮次身份：主树 `D:/acrylic-pda-outbound`、分支 `codex/development`；写者＝DSH 本会话；**同一会话实施＋自审＋运行，非独立第三方验收**。授权：用户 2026-09-29"继续"＝**报表/渲染服务配置**（S19 列的下一步①）。边界：不开生产开关、不接生产、不部署、不合 main。起点 HEAD `68af980`。**无产品代码改动**（只改 harness 供给）。

## 1. 根因（读源码定位，非猜测）

`ExportRendererProcess.requireAvailable(format)` 的判定是：
`renderer-home` 为空或其中没有 `lib/` 目录 ⇒ `EXPORT_UNAVAILABLE`；`PDF` 还要求 `browsers` 目录存在。
渲染器本身是一个**子进程**：`java -cp <renderer-home>/lib/* com.acrylic.outbound.reports.ReportCli <snapshot> <format> <output>`。

而 E2a 侧只有**部分** IT 显式传 `--outbound.exports.renderer-home=../report-rendering/build/install/acrylic-report-rendering`（相对 CWD=`server`，即**树内**安装分发，主树里存在 ✔）；**没传的那些 IT**（646、633 …）就只能拿到 `EXPORT_UNAVAILABLE`——这就是 646 与 633 的根因：**harness 未供给渲染器**，不是产品缺服务。

## 2. 改动（仅 harness）

`tools/r09-e2a-entry-rerun.ps1` 增加两项环境供给（显式 `--outbound.exports.*` 参数仍然优先）：
`OUTBOUND_EXPORTS_RENDERER_HOME=<root>/report-rendering/build/install/acrylic-report-rendering`；
`OUTBOUND_EXPORTS_BROWSERS=` 树内 `report-rendering/build/browsers`，**不存在时回退到 `D:/acrylic-pda-worktrees/r08-export/report-rendering/build/browsers`**——这正是那些 IT **自己硬编码**的同一路径（PDF 用的 Playwright chromium 树；树内自建的正式做法是 `gradlew :report-rendering:installChromium`，需联网下载，本轮未跑）。已在此声明该跨树引用。

## 3. 结果

| run | IT | 结果 |
| --- | --- | --- |
| 648 | `E2aTemporaryCommandWebHttpIT` | **✔ 通过**（14.3s）——`EXPORT_UNAVAILABLE` 消失 |
| 649 | `E2aCorrectionChainIT` | 推进：失败点由「渲染必须成功（**XLSX**）」变为「渲染必须成功（**PDF**）」⇒ XLSX 已可渲染，缺的是 browsers |
| 650 | `E2aCorrectionChainIT`（补 browsers 后） | 再推进：失败点变为 **`[real Web login] expected: 200 but was: 403`** ⇒ 渲染阶段已过去，卡在**登录/权限**前提 |

⇒ **E2a 主树已验证清单增至 16 个**：604、621、622、623、628、629、630、631、634、635、638、639、640、641、643、**648**；C1 侧 611 通过。
⇒ **633 的定性收窄**：**不是**渲染/导出问题（XLSX＋PDF 两步在 650 里都已走过），而是与 642／645 同源的**权限/角色种子前提**（650 的 `real Web login 403`、645 的 `LINK 403 ACCESS_DENIED`、642 的登录失败）。633 仍判**未验**，但其阻塞项已归一到 S19 的②。

## 4. 未做与边界

- 下一步环境项（按价值排序）：①**权限/角色种子与迁移目标**（一次可能解开 633 剩余步骤、645、642）；②**Android 单测工具链**（644／647 与 Android/链接类）；③V36 家族的门＋供给；④树内 Playwright chromium 安装（`installChromium`，替代跨树引用）。
- 未做（产品侧）：Python 侧 v2 供给与 `closedAuthority` 身份（B9，需先定锚点政策）、生产级 provision、§5.6 终端语义、完整接纳门 S0–S4。
- 开关四项默认 false；未接生产、未部署、未合 main；K1 仍未通过（窗口 W、`UNCONFIRMED_NO_CROSS_PROCESS_BASIS` 原样保留）。
- **回归说明**：无产品代码改动 ⇒ 恢复链 25 类批次沿用 S12 轮同产品 HEAD 证据（实例 19476：25/696/0/0）。

## 5. 证据位置（不入库，按约定保留）

`.local/e2a-cross-20260925-648…650`（每轮含 `TEST-*.xml`、`gradle.log`、`exit.txt`、datadir；648 的 export 产物与 649／650 的渲染产物在各自 attempt 内）。每轮跑完即 `-Shutdown`，端口 19330 全程释放。

## 6. 下一步

①**权限/角色种子与迁移目标**（解 633 剩余步骤＋645＋642）；②Android 单测工具链（解 644／647）；③V36 门＋供给；④树内 chromium 安装；⑤**Python 侧 v2 供给与封闭权威身份（B9）**——需先定锚点保管/轮换政策。均需独立授权/验收，不改默认开关。
