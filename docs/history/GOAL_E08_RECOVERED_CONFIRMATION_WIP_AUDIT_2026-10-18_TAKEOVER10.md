# E08/D17 recovered-confirmation WIP 逐路径审计（takeover10，2026-10-18）

本次审计在 `D:/acrylic-pda-outbound`、`codex/development`、HEAD
`c5c8f9e5851746f71aacf8a856e3cf23bf365146` 完成。接管前已只读核对工作树、远端、handoff-final01、进程/监听、ADB 和 C/D/E 余量；USB M71 `22D2910042` 只读枚举，未安装、未触摸。共享 MySQL、外部 `D:/jiachengfangzhi` 的隔离 MySQL/Vite、Gradle daemon 均未停止或连接。

## 现行范围判定

D06/D07 现行政策是坏机未完成订单由有权人员撤回，随后重新发布并由新设备从头作业。R07 已取消故障换机进度承接、多跳、接收者确认和激活链；D13/D14 灾后范围只保留原件、截点、恢复写入和只读核对等可验证边界。下列 `recovered-confirmation` 写入/激活 WIP 因此继续保留以保护历史源码和失败证据，但不再作为产品入口、调度链或验收范围。

## 逐路径清单

| 路径 | 范围 | 实际调用事实 | 处置建议 | 直接受影响验证 |
| --- | --- | --- | --- | --- |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryRecoveredConfirmationWorker.kt` | 已取消的恢复接收确认/激活写链 | 仅在 `DEBUG && RECOVERED_HANDOVER_DEVELOPMENT` 下读取 pending 行并排队 `WorkManager`；当前 `SyncWorker`、启动代码无调用 | 保留继承 WIP；不接调度、不 POST、不恢复旧 run03 | 默认开关单测、lint；默认启动/重启应无确认 WorkManager 任务 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverConfirmationDialog.kt` | 已取消的接收确认入口 | 组件只有确认回调；`HandoverActivity` 对 `RECOVERED_CONFIRM` 走明确拒绝，不导航该 dialog | 保留组件和历史单测；不新增 caller | `rg` 生产 caller 矩阵；默认 UI/lint |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverConfirmationDisplay.kt` | R09 原件只读展示，仍有效 | 从本机恢复 stage/chunks 解析原顺序和保留清单，不联网、不写业务库 | 保留；与确认/激活解耦 | `RecoveredHandoverCardReaderTest`、损坏/顺序/身份代次边界 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverCardReader.kt` | R09 只读卡片候选，仍有效 | 仅在开发隔离入口读取 stage；校验失败返回不可用卡片；默认开关不产生写动作 | 保留只读；不连接扫描、确认或激活 | reader 单测；default-off/有无权矩阵 |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/RecoveredConfirmationDeviceTest.kt` | 已取消链的合成设备测试 | 通过 fixture 驱动确认页面、丢响应、换 LOGIN 和重启；不是原 PDA/现场回执，当前不重跑 | 保留测试和既有失败原件；仅编译，不计业务验收 | `assembleDebugAndroidTest`；不得计 D06/D07/D17 L1 |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/RecoveredConfirmationUiFixture.kt` | 已取消链合成 fixture | 写合成 touch/receipt，并转发 `/api/v1/pda/handovers/{caseId}/recovered-confirmation` POST | 保留输入和失败日志；不得冒充原身份、原回执或灾后候选 | 仅编译；若未来恢复范围先重新政策审计和新隔离输入 |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aRecoveredConfirmationAndroidBridgeMain.java` | 已取消链测试 bridge | 测试侧 Spring/HTTP/MySQL bridge，可写确认表；不属于现行只读恢复链 | 保留 bridge/日志；不启动、不接产品路径 | 静态范围核对；确认表不应被只读恢复改变 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/HandoverActivity.kt` | 普通交接有效；恢复确认分支取消 | `RECOVERED_READ` 可只读；`RECOVERED_CONFIRM` 明确拒绝；TEMP action 先经 fail-closed gate | 保留 fail-closed；不新增确认导航 | 默认 UI/L1、lint；只读入口矩阵 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/HandoverViewModel.kt` | 普通交接/只读恢复有效；确认写入取消 | `RECOVERED_READ` 调只读 repository；`RECOVERED_CONFIRM` 抛出取消错误；TEMP action gated | 保留；不恢复 `confirm`/`activate` 调度 | gate 单测、默认启动/重启、lint |
| `android/app/src/main/java/com/acrylic/outbound/pda/PdaApplication.kt` | 依赖暴露，不等于调度 | 暴露 recovered/temporary repository；未在 `onCreate` 或 worker 链排队确认 | 保留依赖注入；不添加启动排队 | 默认构建/lint；无 POST/WorkManager 任务 |
| `android/app/src/main/java/com/acrylic/outbound/pda/sync/SyncWorker.kt` | 同步链有效；取消链不得进入 | 仅处理现行同步/恢复任务，未调用 `TemporaryRecoveredConfirmationWorker` | 保留现行同步；禁止接入取消 worker | 默认启动、重启、无权/无开关测试 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/TemporaryHandoverCardReader.kt` | TEMP 只读/现行作业边界；确认和激活取消 | 默认关闭返回 `TEMP_DISABLED`；旧 `TEMP_CONFIRM` 仅在开发开关可达 | 保留 fail-closed reader；不把旧 action 当 D06/D07 | gate/reader 单测、默认 UI |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/TemporaryOrdersActivity.kt` | TEMP 订单浏览仍有效 | 只读分页/创建入口；无 recovered-confirmation caller | 保留现行订单页；不接取消确认 | 既有页面 L1，按直接改动复验 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/TemporaryDetailsActivity.kt` | TEMP 详情现行范围 | 展示现行记录并受 action gate；无恢复确认 POST caller | 保留；不新增旧交接入口 | `TemporaryDetails` 主机/Android 入口（有有效登录输入时） |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/TemporaryWorkRenderer.kt` | TEMP 状态展示仍有效 | 仅渲染待确认输入/队列等状态，不决定确认或激活 | 保留语义修复；按 U02 证据复用并补受影响入口 | Robolectric、Android 客户端行为 |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryRecoveredConfirmationRepository.kt` 及其 `*Command/*ReceiptVerifier/*Migration` | 已取消确认持久化模型 | 可保存意图并 POST，但当前产品无 caller；不属于 D06/D07 新政策 | 保留源码、迁移和失败证据；不接只读链 | default-off/无 caller 静态矩阵；不计 L2/L3 |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryHandoverActivationRepository.kt` 及相关 `Download/Work/Baseline` | 已取消 TEMP 激活/换机承接 | 只在开发开关链可达；现行普通 TEMP 作业不应自动激活 | 保留继承 WIP；不恢复 R07 取消范围 | 默认 gate、无 POST/队列、编译 |

## 结论

逐路径调用核对没有发现生产导航调用 recovered confirmation dialog，也没有 `SyncWorker` 调用 recovered confirmation worker；只读入口保持 `RECOVERED_READ`。本审计不删除或覆盖继承源码、原资料、失败日志、原回执或 handoff-final01 快照，不启动旧 run03，不连接原库/现网，不扩大 D06/D07 或 D13/D14 范围。它只完成范围和 fail-closed 自审，不构成 D06/D07、D13/D14、D17、E06 L2、D18 L3、第8节闸门或总目标通过。

直接验证仍按当前输入执行：默认关闭属性的 gate/lint/构建；有效登录和隔离 HTTP/MySQL 输入具备后，再补现行 TEMP/只读恢复产品入口。取消链不得以合成 fixture、DTO、模拟器或测试签名冒充原 PDA、原身份、现场事实或回执。
