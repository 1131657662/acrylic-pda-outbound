# R09 C2 B1-3 S32：夹具链与 Android 打通（10 个库存 IT ＋ Android 两项）（2026-09-29）

> 轮次身份：主树 `D:/acrylic-pda-outbound`、分支 `codex/development`；写者＝DSH 本会话；**同一会话实施＋自审＋运行，非独立第三方验收**。授权：用户 2026-09-29"继续"。起点 HEAD `b3cc9fd`。**改动仅在 harness 工具与测试侧**（无产品代码改动）。

## 1. 根因（一次定死）

用**测试侧探针**复刻 `OfflineSigner.keys()` 的载入逻辑（同一组环境路径），得到
`java.security.spec.InvalidKeySpecException :: java.security.InvalidKeyException: Unable to decode key`
⇒ **此前用 openssl 生成的那份 DER 不是 JVM 能解码的 PKCS#8/SPKI**（尽管文件存在、大小合理、首字节 0x30）。改用 **JDK 自己生成**（`getPrivate().getEncoded()` / `getPublic().getEncoded()`，与 `E2aCrossSourceHttpSetup.signing` 完全同编码）后，`SIGNING_KEY_UNAVAILABLE` 消失、夹具 IT 直接通过。

## 2. 改动（仅 harness 工具与测试侧）

| 文件 | 改动 |
| --- | --- |
| `tools/GenSigningKey.java`（新） | 用 JDK 生成隔离 harness 的 RSA-2048 密钥对（PKCS#8 私钥＋SPKI 公钥），单文件运行 |
| `tools/r09-e2a-entry-rerun.ps1` | ①新增 **`-AppMigrates`**（`OUTBOUND_MIGRATE=true`，库存夹具 IT 需要应用自跑迁移）；②缺密钥且 `-AppMigrates` 时用 `GenSigningKey` 生成（**openssl 方案已废弃并注明原因**）；③补导出 `OUTBOUND_DB_*` 与 `OUTBOUND_SIGNING_*` |
| `android/app/src/test/**` 4 个文件 | 去掉硬编码旧工作树路径的守卫（`E2aRealHttpRecoveryTest`／`LocalDraftEvidenceHttpTest`／`TemporaryArchiveHttpTest`／`TemporarySuccessHttpTest`），改为跨树可匹配——与 S17 修 Java 侧同一思路 |

## 3. 结果

**服务器侧（库存家族，全部带 `-AppMigrates`）**

| run | IT | 结果 |
| --- | --- | --- |
| 694 | `PdaPreparationIT` | **✔ 8/8** |
| 695 | `SubmissionApiIT` | **✔ 12/12** |
| 696 | `SubmissionArchiveIT` | **✔ 14/14** |
| 697 | `RecallApiIT` | **✔ 11/11** |
| 698 | `ContinuationApiIT` | **✔ 12/12** |
| 699–701 | `TemporaryContractFixtureIT`／`TemporaryAuditContractFixtureIT`／`TemporarySubmissionContractFixtureIT` | **✔ 各 1/1** |
| 706 | `AuditArchiveIT` | **✔ 15/15** |
| 707 | `DispositionApiIT` | **✔ 11/11** |

⇒ 共 **10 个库存 IT 新通过**，并生成 **11 个 PDA 夹具**（`pda-{contract,submission,archive,recall,continuation,disposition,audit-archive,temporary-contract,temporary-audit,temporary-submission,temporary-success}-fixture.json`）。

**Android 两项（644／647，曾被夹具缺失挡住）**

| run | IT | 结果 |
| --- | --- | --- |
| 710 | `E2aTemporarySuccessConsumerIT` | **✔ 通过**（48.1s；Android 套件 0 失败） |
| 711 | `E2aTemporaryArchiveIT` | **✔ 通过**（43.1s；Android 套件 0 失败） |

⇒ **E2a 主树已验证清单 22 → 24 个**（新增 `E2aTemporarySuccessConsumerIT`、`E2aTemporaryArchiveIT`）；C1 侧 611 通过。**Android 线（644／647）至此关闭。**

## 4. 过程失败留档（未删）

680–687（迁移前上下文失败）、688（拿到 `SIGNING_KEY_UNAVAILABLE` 响应体）、689/690/691（补变量后仍失败）、692（S31 环境探针）、693（**关键探针**：`Unable to decode key`）、702/703（我引入的 `FileAlreadyExistsException` ⇒ 把预生成限定到 `-AppMigrates` 后修复）、704/705（Android 仍缺 `pda-audit-archive`／`pda-disposition` 夹具）、708/709（Android 已能跑：14 tests, 1 failed → 命中 Android 侧硬编码工作树守卫）、710/711 **绿**。全部 attempt 与 datadir 保留。

## 5. 状态与边界

- **E2a 主树已验证 24 个**：604、621、622、623、628、629、630、631、634、635、638、639、640、641、643、648、657、658、migration、devicekey、pairedkey、auditprefix、temporary-success-consumer、temporary-archive；C1 侧 611 通过。
- 未验：**659**（待裁定）、**V36 stopwriter／auditarchive**（待裁定私钥物料）、其余门控 IT。
- 开关四项默认 false；未接生产、未部署、未合 main；K1 仍未通过（窗口 W、`UNCONFIRMED_NO_CROSS_PROCESS_BASIS` 原样保留）；`localDeletionAllowed=false`。
- 回归说明：无产品代码改动（第 2 节三处均为 harness/测试侧）⇒ 恢复链 25 类批次沿用 S12 轮同产品 HEAD 证据（实例 19476：25/696/0/0）；端口 19330 已释放；生成的密钥与夹具均在 `.local`／`server/build`（不入库）。

## 6. 证据位置（不入库，按约定保留）

`.local/e2a-cross-20260925-680…711`（含 693 的 `Unable to decode key` 探针输出、704/708 的 Android 子进程日志与 `android/app/build/test-results/**` 的 XML）；夹具在 `server/build/pda-*-fixture.json`。
