# R09 C2 B1-3 S30：夹具链推进——上下文可起、503=签名密钥守卫（2026-09-29）

> 轮次身份：主树 `D:/acrylic-pda-outbound`、分支 `codex/development`；写者＝DSH 本会话；**同一会话实施＋自审＋运行，非独立第三方验收**。授权：用户 2026-09-29"继续"（推进不需新授权的 `OUTBOUND_MIGRATE=true` 重跑）。起点 HEAD `8b00e2a`。**无产品代码改动**（harness 工具与运行环境）。

## 1. 结论（一句话）

给 harness 加 `-AppMigrates`（`OUTBOUND_MIGRATE=true`）后，8 个库存夹具 IT **能起 Spring 上下文了**（此前 `server_state` 不存在），但统一拿到 **503 `SIGNING_KEY_UNAVAILABLE`**；harness 已能生成合法密钥对并导出命名空间，守卫却仍报不可用 ⇒ **下一处待查是"IT 是否覆盖了 `outbound.signing.*`（或被显式置空）"**，非产品改动。

## 2. 本轮改动（仅 harness）

`tools/r09-e2a-entry-rerun.ps1`：
- 新增 **`-AppMigrates`** 开关：`OUTBOUND_MIGRATE` 由硬编码 `false` 变为按开关取 `true`（库存夹具 IT 依赖应用启动时自跑迁移）。
- 新增 **离线签名密钥自动生成**：若 `<attempt>/private/server-private.der` 不存在，则用 Git 自带 `openssl` 生成 PKCS#8 私钥＋SPKI 公钥（与 `E2aCrossSourceHttpSetup.signing` 的编码一致）；调用处用局部 `Continue` ＋ `*> $null`（openssl 的进度点走 stderr，在 `Stop` 下会变成终止错误——本轮实测坑）。
- 新增 `OUTBOUND_SIGNING_PRIVATE_KEY/PUBLIC_KEY` 导出（`OfflineSigner` 读的是 `outbound.signing.*`，此前只导出了 auth 命名空间）。

## 3. 运行证据（全部保留）

| run | 现象 |
| --- | --- |
| 680–687 | `-AppMigrates` 前：`Failed to load ApplicationContext` → `Table '…server_state' doesn't exist` |
| 680–687 | `-AppMigrates` 后：上下文可起，8 个 IT 全部 **503** |
| 688（`-GradleInfo`） | MockMvc 打印出失败响应体：**`{"code":"SIGNING_KEY_UNAVAILABLE","message":"离线签名密钥不可用，暂不能领取订单"}`**（`Request URI = /api/v1/inventory/imports`）；其前的步骤都是 `code:OK` ⇒ 不是全局拦截 |
| 689/690/691 | 补 `OUTBOUND_SIGNING_*`、再补 openssl 自动生成后仍 503（8/8 失败） |

**守卫源码**（`pda/OfflineSigner.keys()`）：`privatePath.isBlank() || publicPath.isBlank()` ⇒ 抛该 503；否则尝试 `PKCS8EncodedKeySpec`/`X509EncodedKeySpec` 载入并自签验签，失败同样抛该码。
**密钥实测**：run 691 的 `private/server-private.der` **1191 B、首字节 0x30**；`server-public.der` **294 B、首字节 0x30** ⇒ 文件与编码都正常，**故最可能是属性为空白**（IT 覆盖/置空，或 `@Value` 取值路径与我的环境变量名未对上）。

## 4. 下一步（最小、明确）

在其中一个夹具 IT 上确认**生效值**：查该 IT 是否用 `@SpringBootTest(properties=…)`／`@TestPropertySource` 覆盖了 `outbound.signing.*`（若是，harness 需按该 IT 的期望提供，而非改产品）；否则读 `OfflineSigner` 构造入参的实际取值（`-GradleInfo` 已可转发 stdout，可临时打印）。**这一步之前，644／647 与 8 个夹具 IT 一律判未验。**

## 5. 状态

- **E2a 主树已验证 22 个**（本轮不变）：604、621、622、623、628、629、630、631、634、635、638、639、640、641、643、648、657、658、migration、devicekey、pairedkey、auditprefix；C1 侧 611 通过。
- 未验：**659**（待裁定）、**V36 stopwriter／auditarchive**（待裁定私钥物料）、**644／647＋8 个夹具 IT**（阻塞已到 §3 的签名守卫）。
- 开关四项默认 false；未接生产、未部署、未合 main；K1 仍未通过（窗口 W、`UNCONFIRMED_NO_CROSS_PROCESS_BASIS` 原样保留）；`localDeletionAllowed=false`。
- 回归说明：无产品代码改动 ⇒ 恢复链 25 类批次沿用 S12 轮同产品 HEAD 证据（实例 19476：25/696/0/0）。端口 19330 已释放（本轮另遇"端口被占"⇒ 用 `Stop-Process` 清理了遗留 mysqld，属一次性实验实例）。

## 6. 证据位置（不入库，按约定保留）

`.local/e2a-cross-20260925-680…691`（含 688 的 MockMvc 完整响应体打印、691 的密钥文件与 datadir）；`.local/s29-chromium.log`。
