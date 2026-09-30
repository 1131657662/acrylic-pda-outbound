# R06 A1.1 第 2 步：单测缺口 G2/G3 补齐（子步记录，非 A1.1 验收）（2026-09-30）

> **性质**：这是 A1.1 的**子步收口**，**不是** A1.1 验收。A1.1 的五条标准中 ①②③⑤ 的 **device 层取证仍缺**（G1/G4 留第 3 步），故 `docs/history/R06_A1_1_<日期>.md` 验收记录**不在此轮产生**。
>
> 依据：用户 2026-09-30 授权"以补取证为主，第 2 步做 G2/G3（单测，不需 AVD）"。

## 1. 交付的测试

| 文件 | 测试名 | 覆盖标准 |
| --- | --- | --- |
| `android/app/src/test/.../evidence/LocalDraftStopWriterTest.kt`（新增） | `inputReceivedBeforeTheStopIsDurablyPreservedAndSignedExactlyOnce` | ① 已接收输入耐久保存 |
| 同上 | `stopCommittedFirstRefusesLaterInputWithoutCreatingScanFacts` | ① 先拒新输入 |
| 同上 | `refusedStopLeavesNoSignatureNoStopRowAndNoQueueAdmission` | ① **失败不签停止**（补齐既有 rollback 测试未断言的两点：**无签名行**、**未入队**；并验证无中毒状态、稍后可成功停止） |
| 同上 | `parallelStopAttemptsYieldExactlyOneStopEvidenceAndOneSignature` | ① 并发下的单停止证据（**串行化/幂等探针**） |
| `android/app/src/test/.../evidence/LocalDraftSourceBindingMigrationTest.kt`（新增） | `nonemptyRoomTwentySevenRetainsOriginalKeysRequestsReceiptsAndDoesNotInventBindings` | ④ 非空迁移保留原键/请求/回执 |

## 2. 验证结果

```text
LocalDraftStopWriterTest              tests=4 skipped=0 failures=0 errors=0
LocalDraftSourceBindingMigrationTest  tests=1 skipped=0 failures=0 errors=0
BUILD SUCCESSFUL
```

运行方式：`gradlew.bat -p android :app:testDebugUnitTest --tests *LocalDraftStopWriterTest --tests *LocalDraftSourceBindingMigrationTest`（**未跑 AVD**）。日志：`.local/r06-a11-step2{,b,c,d,e}.log`（**失败轮全部保留**）。

## 3. G3 覆盖的版本段（先说清为什么是 27→28）

建立了 v1..v28 迁移覆盖图：既有 `*MigrationTest` 已逐一覆盖 1→2、2→3、3→4、4→5、8→9、12→13、13→14、14→15、15→16、16→17、18→19、19→20、20→21、21→22、22→23、23→24、24→25、25→26、26→27。**未核段**为 6→7、7→8、9→10、10→11、11→12、17→18、**27→28**；其中**只有 27→28（`LocalSourceBindingMigration`）落在 LocalDraft 时代**——低版本段尚不存在原键/请求/回执，无法承载本条验收语义。

手法沿用既有 `downgrade`：关闭 DB → 读 `27.json` 的 `identityHash` → 裸库 `DROP TABLE local_source_binding` → `db.version=27` → `reopen()` 让 Room 跑**真实迁移**。

断言要点：版本达 28；**stop 行/签名/plan/requests/receipt（state+signingKeyJson+responseBytes）/scan events 逐项相等**；**故意把队列置为暂停后迁移必须保留该暂停、不得复活自动续传**；私有密钥设为 unavailable ⇒ **不得追补签名**；新表建出来**为空**（不臆造绑定）；`PRAGMA foreign_key_check` 干净。

## 4. 并发级别与局限（如实标注）

- 前 3 条 G2 用例是**顺序交织模拟**：把调度器可能产生的两种顺序各钉一遍（停止先 / 输入先），**不验证竞态**。
- 第 4 条用**真实 4 线程** + `CountDownLatch` 屏障，但 SQLite 一次只允许一个写者 ⇒ 它验证的是**串行化/幂等**，**不是**"扫码写"与"停止写"之间的真实竞态。
- **真实竞态**与真机重启属第 3 步设备层（G1/G4），本轮**未验**。

## 5. "6 事件 vs 4 尝试"的**最终定性：判据误用，非产品缺陷、非装置重试**

三处独立证据（全部读自产品/测试源码）：

| 事实 | 位置 | 推论 |
| --- | --- | --- |
| `LocalDraftStopDao.count(id)` = `SELECT COUNT(*) FROM local_draft_stop_item WHERE clientOrderId=:id` | `LocalDraftStopDao.kt` | 它数的是**停止明细项**（每扫描记录一条），**与停止行数、事件数都无关** ⇒ "6" 是 **6 条 stop item**，正常 |
| `local_draft_stop` 的 **PK = `clientOrderId`** | `LocalDraftStop.kt` 实体 + 23→24 DDL | 每草稿**结构上最多一行**停止 ⇒ 不可能有 6 条停止行 |
| `TemporaryWorkStore.event(...)` 首行即 `check(db.inTransaction())` | `TemporaryWorkStore.kt:46-47` | 事件追加**必须**发生在调用方事务内 ⇒ 与停止插入**同原子**，落败的并发尝试**不可能**留下事件 |

**决定性验证**：把事件断言恢复为**严格** `LOCAL_EVIDENCE_STOP 事件 == 1`，在 **4 并发尝试**下**通过** ⇒ 多余事件从未存在，此前的"异常"是我把 `count(id)`（明细项）当成停止行的**判据错误**。因此**不存在需要单列阻断的产品缺陷**。

**我此前的两个判据错误（已修正并写入代码注释）**：
1. 用 `localDraftStops().count(id)` 断言"停止行数" ⇒ 改为一律断言**该行存在**（`get(id) != null`）。
2. 用 `stopSigner.calls` 断言"只签一次" ⇒ 该计数器在 `E2aTestDeviceSigner` 的 **L19 与 L30 两处**自增（≥2 种签名操作），不是停止签名次数的判据；"只签一次"的正确判据是**结构性**的：`local_draft_stop_signature` **PK = clientOrderId** ＋ `LocalDraftStopSignatures.create()` 的 `check(get(id) == null)` ⇒ 第二次签名会**抛错而非覆盖**，故不可能存在两条。

## 6. 边界

**未改任何产品代码**（本轮仅新增测试与注释）；**未跑 AVD**；未删除任何 `.local` 证据；失败轮 `.local/r06-a11-step2{,b,c,d}.log` 保留。
