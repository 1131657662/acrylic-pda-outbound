# R09 C2 B1-3 S31：夹具链——签名守卫的进程内证据与下一步（2026-09-29）

> 轮次身份：主树 `D:/acrylic-pda-outbound`、分支 `codex/development`；写者＝DSH 本会话；**同一会话实施＋自审＋运行，非独立第三方验收**。授权：用户 2026-09-29"继续"（查夹具 IT 是否覆盖/置空 `outbound.signing.*`）。起点 HEAD `2d7a644`。**无产品代码改动**（诊断探针已撤销）。

## 1. 两个决定性发现

### 1.1 应用侧绑定（推翻"属性空白"假设）

`server/src/main/resources/application.yml` L38–41：

```yaml
outbound:
  signing:
    private-key: ${OUTBOUND_AUTH_SIGNING_PRIVATE_KEY:}
    public-key: ${OUTBOUND_AUTH_SIGNING_PUBLIC_KEY:}
    historical-public-keys: ${OUTBOUND_AUTH_SIGNING_HISTORICAL_PUBLIC_KEYS:}
```

⇒ `outbound.signing.private-key/public-key` **就是从 `OUTBOUND_AUTH_SIGNING_*` 取值**，而这两个变量自 S21 起一直在导出。

### 1.2 进程内生效值（决定性，run 692）

在 `PdaPreparationIT` 临时打印后跑一次（探针随后已 `git checkout --` 撤销，树干净）：

```
S31-ENV OUTBOUND_AUTH_SIGNING_PRIVATE_KEY=D:\…\.local\e2a-cross-20260925-692\private\server-private.der exists=true
S31-ENV OUTBOUND_AUTH_SIGNING_PUBLIC_KEY=D:\…\private\server-public.der exists=true
S31-ENV OUTBOUND_MIGRATE=true
```

⇒ **环境变量确实到达测试 JVM，且两个密钥文件都存在**（此前实测：私钥 1191 B PKCS#8、公钥 294 B SPKI，首字节均 `0x30`）。
⇒ 因此 `OfflineSigner.keys()` 抛的是 **catch 分支**（L71）——即 `privatePath/publicPath` **非空**，是 **try 块内失败**：`PKCS8EncodedKeySpec`/`X509EncodedKeySpec` 载入、`RSAPublicKey` 模长 ≥2048、或 **SHA256withRSA 自签验签** 三者之一。**"属性空白"的解释已被证据排除。**

## 2. 下一步（唯一能继续收敛的动作，二选一）

①**测试侧（不改产品）**：在某个夹具 IT 里反射取 `OfflineSigner` bean 并调用其 `keys()`，`catch (Exception e)` 打印 **`e.getCause()` 链**——一次即可定位是载入、模长还是自签验签失败；
②若需要看应用内部判定细节，则须**授权对产品侧做一次带日志的仪表化运行**（改动产品代码，未授权则不做）。

在拿到①的结果前，**644／647 与 8 个夹具 IT 一律判未验**，且**不得**当作产品缺陷。

## 3. 本轮状态

- **E2a 主树已验证 22 个（不变）**：604、621、622、623、628、629、630、631、634、635、638、639、640、641、643、648、657、658、migration、devicekey、pairedkey、auditprefix；C1 侧 611 通过。
- 未验：**659**（待裁定）、**V36 stopwriter／auditarchive**（待裁定私钥物料）、**644／647＋8 个夹具 IT**（阻塞已收敛到 §1.2 的 try 块）。
- 开关四项默认 false；未接生产、未部署、未合 main；K1 仍未通过（窗口 W、`UNCONFIRMED_NO_CROSS_PROCESS_BASIS` 原样保留）；`localDeletionAllowed=false`。
- 回归说明：无产品代码改动 ⇒ 恢复链 25 类批次沿用 S12 轮同产品 HEAD 证据（实例 19476：25/696/0/0）；端口 19330 已释放；临时探针已撤销，工作树只余**另一会话的两个文件**与本次文档。

## 4. 证据位置（不入库，按约定保留）

`.local/e2a-cross-20260925-692`（含 `S31-ENV …` 打印与密钥文件的 datadir）、`-688`（MockMvc 完整响应体 `SIGNING_KEY_UNAVAILABLE`）、`-680…691` 全部失败轮；源码证据：`application.yml` L38–41、`pda/OfflineSigner.java` L61/L62–68/L71。
