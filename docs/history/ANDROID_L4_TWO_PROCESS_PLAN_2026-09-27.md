# Android LINK 映射安装 L4：两进程编排方案（2026-09-27，第 1 轮产出）

## 目标与缺口
- **已有**：Android 侧 L3（`SourceBindingRepositoryTest`：拉取→安装→本地一致 / `installRequired=false` 不安装 / 重复拉取幂等 / 迁移 27→28 数据保留，四条全绿）；服务端半边 L4（attempt 153：真服 + 真 HTTP + PDA Bearer 返回设备载荷）。
- **缺口**：Android **代码本身**还没打过真服——真服目前由服务端 IT 在 19434 起、**IT 结束即关**；Android 单测在另一个 gradle 进程。

## 可行性已核实（本轮实测，非推测）
| 前提 | 证据 | 结论 |
| --- | --- | --- |
| 允许本地明文 HTTP | `android/app/src/main/java/com/acrylic/outbound/pda/network/ConnectionSettings.kt:40`：`check(url.scheme=="https" \|\| (BuildConfig.DEBUG && url.scheme=="http" && url.host in setOf("127.0.0.1","localhost","10.0.2.2")))` | ✅ **现成测试缝**（Robolectric 单测走 debug 变体） |
| base URL 可注入 | `PdaHttp.kt:27` `class PdaNetwork(baseUrl: String, …)`；`Retrofit.Builder().baseUrl(baseUrl)` | ✅ 构造参数 |
| 免会话持久化的捷径 | `SourceBindingRepository` 有 `internal constructor(db, current: () -> JSONObject, request: (String,String,JSONObject)->JSONObject)` | ✅ **测试内自建 transport lambda 即可，无需触碰生产代码、无需模拟配对/Keystore** |

## 编排设计（三件套）
1. **服务端"常驻"IT**（新增，gated `E2A_CROSS_VERIFY`）：`E2aServeBindingForAndroidIT`
   - 复用 R06 已验证机制产出 TEMP+LINK 状态（`TemporaryLinkConfirmHttpChecks.verify`）；
   - 用 SQL 定位已关联 scope 的 device/operator → 铸一枚 PDA Bearer（`DeviceCrypto.sha`）并授 `PDA_OPERATOR`（attempt 153 已验证的做法）；
   - 起真服（19434）后**写握手文件** `android-binding-handshake.json`：`{baseUrl, token, warehouseId, scopeId, canonicalOrderId, mappingVersion, orderNo, serverRecoveryEpoch}`；
   - **轮询 stop 文件**（上限 N 分钟）→ 出现后正常退出并触发脚本 teardown（真服全程常驻）。
2. **Android 真服测试**（新增，JVM/Robolectric）：`RealServerSourceBindingTest`
   - 从环境变量读握手文件路径；用 `TestInfrastructure.memoryDatabase()` 建库；
   - `request` 用 **java.net.http** 真打到 `baseUrl + "/api/v1/pda/temporary-source-bindings"`（`Authorization: Bearer <token>`）；`current()` 返回握手里的 `deviceId/serverRecoveryEpoch`；
   - 调 `SourceBindingRepository(db, current, request).installBindings(...)` → 断言 `db.sourceBindings().get(scopeId)` 的 `canonicalOrderId/mappingVersion/orderNo/phase` **等于握手值**（即"设备本地状态与服务端关联结果一致"）；并断言重复调用幂等（行数仍 1）。
3. **编排脚本**：`provision` → 后台跑常驻 IT → 等握手文件 → 跑 `gradlew -p android :app:testDebugUnitTest --tests '*RealServerSourceBindingTest'`（带环境变量）→ 写 stop 文件 → 汇总两侧 XML。

## 风险与未决（第 2 轮解决）
- 握手文件轮询与 stop 的**超时**必须设死（否则服务端 IT 会挂住 attempt）；
- Android 测试需要**可解析的握手 JSON**（字段名两侧对齐）；
- 真服在常驻 IT 内启动，脚本必须在**同一 attempt** 内完成两侧动作（否则 DB 被 teardown 停掉）。

## 与既有教训的关系
- 这是**新验证基础设施**，有重演 FORMAL 夹具（R03 五轮未成）的风险 ⇒ 业主已设**2 轮硬边界**：2 轮内做不出即记录为"独立验证基础设施任务"，不硬做。
- 第 1 轮**未发现深坑**（三个前提全部正面），故不触发"提前转 flake 修复"的备选路径。
## 第 2 轮结果：**未实现**——按 2 轮硬边界收口为「独立验证基础设施任务」

**attempt 号更正（业主指正）**：
- **Android LINK L4 本轮 → attempt 171**（本项）；
- **TEMP 归档（选 A 已授权、待排期）→ attempt 172+**（此前报告里误写为 171，特此更正）。

**第 2 轮实际状态**：三件套（服务端常驻 IT / Android 真服测试 `RealServerSourceBindingTest` / 编排脚本）**均未写出**，因此**attempt 171 未运行**，两侧 XML 证据**不存在**。

**为什么停在这里（如实）**：本轮可用余量不足以同时写出三个相互依赖的新文件并完成"编译 → 起常驻 IT → Android 测试打真服 → 汇总"的闭环；而按既教训（上一批我提交过会破坏 Spring 上下文启动的接线），**宁可不动手，也不留下不可编译/不可运行的半成品**。这符合业主设定的边界："2 轮内做不出 → 立即停，记录为独立验证基础设施任务，不硬做。"

**本项已固化的资产（下一任务可直接用，不需重做）**：
1. 三个可行前提已实测核实：`ConnectionSettings.kt:40` 的 127.0.0.1 明文缝；`PdaNetwork(baseUrl)` 可注入；`SourceBindingRepository(db, current, request)` 免会话捷径；
2. 编排三件套的接口设计（握手文件字段、stop 轮询、同 attempt 闭环）；
3. 三个风险点的处理要求：stop/超时设死、两侧字段名对齐、同 attempt 内完成两侧动作。

**定性**：Android LINK 映射安装**维持 L3**（Robolectric + MockWebServer 四条断言），通道服务端半边维持 **L4**（attempt 153）；「Android 代码打到真服」这一 L4 证据**尚未取得**，列为**独立验证基础设施任务**（建议独立时间盒，勿在业务子项内消耗）。

## 第 3 轮结果：**已实现 → L4 达成**（2026-09-27，业主重启授权、按本方案执行不重新设计）

三件套全部落地（提交 `2bf2507`；工具修正 `516dfd7`、`eda91b9`）：
1. `server/.../e2averify/E2aServeBindingForAndroidIT.java`（常驻：R06 链产出 LINK → 铸 PDA Bearer → 真 HTTP 自证 → 握手文件 → 轮询 stop，15 min 上限）；
2. `android/.../pda/temporary/RealServerSourceBindingTest.kt`（OkHttp 真打 19434 → `installBindings` → 本地行 == 服务端关联结果 + 幂等）；
3. `tools/e2a-android-link-l4.ps1`（同 attempt：后台常驻 IT → 等握手 → 跑 Android → 写 stop → teardown → 双侧 XML 汇总 + L4 判定）。

**结果**：attempt **185** 与 **187** 各 `server=1/0/0/0 android=1/0/0/0 level=L4`（两次独立可复现；均双标记完成、19330 释放）。

**两处细节更正（三前提仍成立）**：
- `java.net.http` **不在 Android 测试 classpath**（`android.jar` 无此包，编译即失败）⇒ 改用 **OkHttp 4.12**；本 transport 不经 `PdaNetwork`，故明文缝与生产 `PdaSession` 传输未行使；
- **Gradle UP-TO-DATE 陷阱**：同一 `--tests` 重复跑会静默不执行（attempt 186 实测 `NOT-EXECUTED`）⇒ 构造器加 `--rerun-tasks`。

**未验**：单类/单路径；多设备、并发、断网重连、仪器化、真机现场均未验。
