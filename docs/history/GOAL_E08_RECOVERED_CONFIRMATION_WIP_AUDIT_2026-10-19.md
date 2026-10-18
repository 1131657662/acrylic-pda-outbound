# E08/D17 recovered-confirmation WIP 逐路径审计（2026-10-19）

本次在 `D:/acrylic-pda-outbound`、`codex/development`、HEAD `b745d58c673c25dcee519487173276063352419a` 只读接管核实后完成。继承改动、失败证据、原资料、身份/Keystore、回执和 handoff-final01 快照均保留。当前唯一源码写者为本聊天；外部 CUA 服务、D:/jiachengfangzhi 的 Node/Vite 和既有隔离 MySQL 仅观察，不作为本轮业务输入。项目隔离 MySQL 13316/13317 仍监听，未连接、未停止。

## 政策判定

D06/D07 现行处理是坏机未完成订单由有权人员撤回，重新发布后由新设备从头作业。R07 已取消故障换机进度承接、多跳、接收者确认和激活链。D13/D14 仅保留原件、截点、恢复写入及只读核对边界。因此恢复确认/激活 WIP 只能保留源码和失败资料，不能作为产品入口、后台调度或验收链。

## 逐路径清单

| 路径 | 范围 | 实际调用事实（当前树） | 处置建议 | 直接受影响验证 |
| --- | --- | --- | --- | --- |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryHandoverConfirmationWorker.kt` | 已取消 TEMP 接收确认写链 | `doWork`/`restorePending` 在 DEBUG+TEMP 开发开关下可恢复 pending；`HandoverViewModel` 的显式 `TEMP_CONFIRM` 仍可调度 | 保留继承源码和隔离 fixture；移除现行同步 caller，不把开发开关当产品验收 | host 单测、编译、lint；默认启动/重启零 queue/POST |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryRecoveredConfirmationWorker.kt` | 已取消恢复接收确认/激活写链 | 仅自身测试引用；`doWork`/`restorePending` 在 DEBUG+recovered 开关下可 `resume`/排队 | 保留继承源码和失败证据；不新增 caller、不调度/POST | host 单测、编译、lint；默认启动/重启零 queue/POST |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverConfirmationDialog.kt` | 已取消接收确认入口 | 只有确认回调；当前 Activity 对 `RECOVERED_CONFIRM` 明确拒绝，不调用 dialog | 保留组件/历史单测；不新增 caller | caller 矩阵、构建/lint |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverConfirmationDisplay.kt` | R09 原件只读展示，仍有效 | 由 `RecoveredHandoverCardReader` 解析本机 stage/chunks；不联网、不写业务库 | 保留并与确认/激活解耦 | reader 单测、损坏/顺序/身份代次边界 |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/RecoveredConfirmationDeviceTest.kt` | 已取消链合成设备测试 | `@Ignore`；fixture 驱动确认页、丢响应、换 LOGIN、重启 | 保留测试和失败原件；仅编译，不计业务验收 | `assembleDebugAndroidTest`；不得计 D06/D07/D17 L1 |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/RecoveredConfirmationUiFixture.kt` | 已取消链合成 fixture | 写合成 touch/receipt，并转发 `/api/v1/pda/handovers/{caseId}/recovered-confirmation` POST | 保留输入/日志；不得冒充原 PDA、原身份、现场回执 | 仅编译；若政策重开须重新隔离审计 |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aRecoveredConfirmationAndroidBridgeMain.java` | 已取消链测试 bridge | 测试侧 Spring/HTTP/MySQL bridge，可写确认表；不属于现行只读恢复链 | 保留 bridge/日志；不启动、不接产品路径 | 静态范围核对；确认表不得被只读恢复改变 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/HandoverActivity.kt` | 普通交接有效；恢复确认取消 | `RECOVERED_READ` 只读；`TEMP_*` 先过 gate；`RECOVERED_CONFIRM` 仅显示取消错误 | 保留 fail-closed；不导航旧 dialog | Android UI/L1、lint |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/HandoverViewModel.kt` | 普通交接/只读恢复有效 | `RECOVERED_READ` 调只读 repository；`RECOVERED_CONFIRM` 抛取消错误；TEMP action 可调旧 worker/repository | 保留现行 guard；不恢复确认/激活调度 | gate 单测、默认入口、lint |
| `android/app/src/main/java/com/acrylic/outbound/pda/sync/SyncWorker.kt` | 现行同步有效；取消链不得进入 | 当前第24行调用 `TemporaryHandoverConfirmationWorker.restorePending(app)`，即使被开发开关保护仍存在后台接入 | **已移除该调用**；保留 E2a/草稿/现行 TEMP continuation 恢复 | 源码负向断言、unit/build/lint、默认启动/重启零 queue/POST |
| `android/app/src/main/java/com/acrylic/outbound/pda/PdaApplication.kt` | 依赖暴露，不等于调度 | 暴露历史 repository；无确认调度 | 保留依赖以保护 WIP；不在 `onCreate` 排队 | build/lint；无 WorkManager 任务 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverCardReader.kt` | R09 只读卡片 | 读取 stage 并返回 `RECOVERED_READ`；校验失败返回不可用卡片 | 保留只读；不连接扫描、确认、激活 | reader 单测、default-off/有无权 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/TemporaryHandoverCardReader.kt` | TEMP 现行入口与取消边界 | 默认关闭返回 `TEMP_DISABLED`；开发开关打开时可产生旧 `TEMP_CONFIRM/INSTALL/START` | 保留 fail-closed reader；不把旧 action 当 D06/D07 通过 | gate/reader 单测、默认 UI |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryRecoveredConfirmationRepository.kt` 及 `*Command/*ReceiptVerifier/*Migration` | 已取消确认持久化模型 | 可保存意图并 POST，但当前不应有产品 caller | 保留源码、迁移和失败资料；不接只读链 | 无 caller 矩阵、默认无 POST/写入 |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryHandoverActivationRepository.kt` 及 `Download/Work/Baseline` | 已取消 TEMP 激活/换机承接 | 仅开发开关链可达；不是普通 TEMP 作业政策 | 保留继承 WIP；不恢复 R07 范围 | gate、编译；不启动旧 run03 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/TemporaryOrdersActivity.kt`、`TemporaryDetailsActivity.kt`、`TemporaryWorkRenderer.kt` | 现行 TEMP 浏览/状态展示 | 无 recovered-confirmation caller；只显示现行记录和阻断状态 | 保留现行入口；不新增旧交接入口 | 受影响 Android L1、既有 U02 证据按路径复用 |

## 结论与边界

当前调用核对发现唯一需要修正的现行后台接入是 `SyncWorker` 第24行的 `TemporaryHandoverConfirmationWorker.restorePending`。移除后，取消链仍作为保全源码存在；显式 TEMP 开发开关下的历史交互仍只作隔离 WIP，不属于现行产品或验收入口。只读 `RECOVERED_READ` 保持。不得启动旧 run03、旧 bridge、取消链设备测试，不连接原库/现网，不以合成身份、fixture、DTO、模拟器或测试签名冒充原 PDA、现场事实或回执。

本审计和后续修复不构成 D06/D07、D13/D14、D17、E06 L2、D18 L3、第8节闸门或总目标通过。直接验证仅覆盖受影响 Android 源码、构建/lint、默认关闭与负向调用检查；真实 HTTP/MySQL、Chrome、Python、非空迁移、原 PDA、恢复全链仍须另行执行。
