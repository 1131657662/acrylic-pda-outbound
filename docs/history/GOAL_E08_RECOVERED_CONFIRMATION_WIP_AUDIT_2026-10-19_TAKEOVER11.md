# E08/D17 recovered-confirmation WIP 当前接管审计（takeover11，2026-10-19）

本报告在 `D:/acrylic-pda-outbound`、`codex/development` 当前输入上重新核对取消范围和调用矩阵。接管快照为 `.local/goal-20261019-resume/takeover-audit11/`；当前源码、继承 WIP、失败证据、原资料、身份/Keystore、回执和 handoff-final01 保全均未覆盖。共享 MySQL、ADB、Vite/Node、Chrome 仅观察，未连接或停止。

## 范围判定

D06/D07 现行政策是有权后台撤回坏机未完成订单、重新发布，由新设备从头作业；不迁移坏机进度。R07 已取消自动换机承接、多跳、接收者确认和激活链。D13/D14 只保留灾后原件、截点、恢复写入和只读核对边界。总目标第 4/5 节因此要求保留旧源码和失败资料，但不得让取消链成为现行产品入口或验收链。

## 逐路径清单

| 路径 | 仍有效或已取消范围 | 当前调用事实 | 处置建议 | 直接受影响验证 |
| --- | --- | --- | --- | --- |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryRecoveredConfirmationWorker.kt` | 已取消恢复接收确认/激活写链 | `doWork`、`restorePending`、`schedule` 仅在 Debug 且 `RECOVERED_HANDOVER_DEVELOPMENT` 开关下可用；当前树无产品 caller | 保留源码、测试和失败证据；不新增 caller、不调度、不 POST | 负向 caller 检查；既有 host gate/build/lint 结果复用 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverConfirmationDialog.kt` | 已取消接收确认入口 | 只有 `show` 和确认回调；当前 Activity/ViewModel 没有调用 | 保留组件和历史单测；不恢复导航 | caller 矩阵、构建/lint |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverConfirmationDisplay.kt` | R09 原件只读展示，仍有效 | 只由 `RecoveredHandoverCardReader` 读取本机 stage/chunks，解析清单和扫码资料；不联网、不写业务库 | 保留并与确认/激活彻底解耦 | reader 单测、损坏/乱序/代次边界 |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/RecoveredConfirmationDeviceTest.kt` | 已取消链的合成设备测试 | `@Ignore`；fixture 驱动确认页、丢响应、换 LOGIN、重启并断言确认状态 | 保留测试和失败原件；仅允许编译，不计业务验收 | `assembleDebugAndroidTest` 可复用；禁止启动旧 run03 |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/RecoveredConfirmationUiFixture.kt` | 已取消链合成 fixture | MockWebServer 转发 `/api/v1/pda/handovers/{caseId}/recovered-confirmation`，写合成 touch/receipt 和开发接收机钥 | 保留输入/日志；不得冒充原 PDA、现场身份或回执 | 仅编译；政策未重开前不跑 |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aRecoveredConfirmationAndroidBridgeMain.java` | 已取消链测试 bridge | 测试侧启动 Spring/HTTP/MySQL，等待 Android marker 后写确认表；不属于现行产品路径 | 保留 bridge、日志和失败库；不启动、不接产品 | 静态范围核对；确认表不得被只读恢复改变 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/HandoverActivity.kt` | 普通交接有效；恢复确认取消 | `TEMP_*` 在导航、对话框前先过 `TemporaryHandoverActionGate`；当前没有 `RECOVERED_CONFIRM` 对话框分支 | 保留 fail-closed；不导航旧 dialog | gate 单测、Android build/lint；真实触摸另列 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/HandoverViewModel.kt` | 普通交接和 R09 只读有效 | `perform` 先拒绝关闭的 `TEMP_*`；`RECOVERED_READ` 只读，`RECOVERED_CONFIRM` 明确抛取消错误；开发开关打开时的 TEMP confirm 仍是隔离 WIP | 保留 guard；不恢复接收确认/激活调度 | gate 单测、负向调用检查、默认入口 |
| `android/app/src/main/java/com/acrylic/outbound/pda/sync/SyncWorker.kt` | 现行同步有效；取消链不得进入 | 当前只调用 E2A、草稿、`TemporaryContinuationWorker`；无 `TemporaryHandoverConfirmationWorker.restorePending` 或 recovered worker caller | 保持本次 fail-closed 修复；不再修改 | 负向 `rg`、既有 gate/build/lint |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/TemporaryHandoverActionGate.kt` | 现行默认关闭边界 | `TEMP_*` 只有 Debug 且 `TEMPORARY_HANDOVER_DEVELOPMENT` 时可达，默认返回关闭消息 | 保留集中 gate；生产/普通 Debug 不放行 | gate unit test、默认 build/lint |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/TemporaryHandoverCardReader.kt` | TEMP 浏览和关闭提示有效；旧确认链取消 | 关闭时返回 `TEMP_DISABLED` 不可操作；显式开发开关才产生 `TEMP_CONFIRM/INSTALL/START` | 保留 fail-closed reader；不把开发 action 当 D06/D07 通过 | reader/gate 回归；历史 SQLite 环境失败继续保全 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverCardReader.kt` | R09 只读候选有效 | 需要 Debug recovered 开关；返回 `RECOVERED_READ`，文案明确接收确认和激活已取消 | 保留只读；不连接扫描、确认、激活 | reader 单测、默认关闭/有无权 |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryHandoverConfirmationWorker.kt` | 已取消 TEMP 接收确认写链 | worker 和 pending 恢复都受 Debug/TEMP 开关；只有显式开发 `TEMP_CONFIRM` 会排队 | 保留 WIP；不恢复后台同步 caller，不启动旧 run03 | gate/build/lint；无默认 WorkManager 任务 |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryHandoverConfirmationRepository.kt` | 已取消 TEMP 确认持久化/POST | `queue`/`resume` 写 Room 并调用 `temporary-confirmation`，均要求开发开关；当前默认入口不可达 | 保留源码、迁移和失败资料；不接只读链 | 默认无写/无 POST；开发链仅隔离测试 |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryRecoveredConfirmationRepository.kt` | 已取消恢复确认持久化/POST | `queue`/`resume` 可写 Room 并调用 `recovered-confirmation`，但当前无产品 caller，构造默认受 recovered 开关约束 | 保留源码、迁移和失败资料；不新增调用 | 静态 caller 矩阵；不启动 bridge |
| `android/app/src/main/java/com/acrylic/outbound/pda/PdaApplication.kt` | 依赖暴露，不等于调度 | 暴露两个确认 repository 和激活 repository；`onCreate` 只启动现行 `SyncScheduler` | 保留依赖以保护 WIP；不在初始化排队 | build/lint；无确认 WorkManager 任务 |
| `android/app/build.gradle.kts` | 默认关闭边界有效 | `TEMPORARY_HANDOVER_DEVELOPMENT`、`RECOVERED_HANDOVER_DEVELOPMENT` 默认均为 `false`，仅隔离参数可显式打开 | 保持 default-off；不把隔离 APK 当候选 | default-off build/lint |
| `server/src/main/resources/application.yml` | 服务端恢复确认保持关闭 | `outbound.handover.recovered-confirmation.enabled=false` | 保持关闭，不开放七类生产决定能力 | 配置静态检查；真实 HTTP 仅在政策重开后另行隔离 |
| `server/src/main/java/com/acrylic/outbound/handover/HandoverRecoveredConfirmationEndpoint.java` | 已取消产品写入口的保留实现 | 仅 `@ConditionalOnProperty(...enabled=true)` 装配；固定输入摘要后才调用确认 store | 保留实现和测试；不开配置、不接现行产品 | default-off 配置检查；不跑旧 bridge |
| `server/src/main/java/com/acrylic/outbound/handover/HandoverRecoveredConfirmationController.java` | 已取消产品写入口的保留实现 | controller 本身还受 commands 开关；endpoint 不存在时返回 disabled，不接受 HTTP 自带 authority | 保留实现；不新增客户端调用 | default-off/503 负向测试（既有范围） |

## 当前源文件绑定

以下 SHA 是本次接管输入的逐路径绑定，完整工作树哈希在 `.local/goal-20261019-resume/takeover-audit11/dirty-hashes.json`：

| 路径 | SHA-256 |
| --- | --- |
| `android/app/src/main/java/com/acrylic/outbound/pda/sync/SyncWorker.kt` | `421b9dc78aa22a32ef084a56cf16c0ebe94d7bad8a2426ab813b26d8f2ac3f42` |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryRecoveredConfirmationWorker.kt` | `b6acb4260721cd4c9cc02d66db4023002fec0c399cee07613ca4a8ba65ac3db3` |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverConfirmationDialog.kt` | `90a4f08ef5a77a0c1a51089e5d8dda69095f4368ba577fa7cf8ad26d44df3efe` |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverConfirmationDisplay.kt` | `d005a763e1d2b2224d3a1918d45ff71a77158c009b7995cfc0eaced86d3f2963` |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/RecoveredConfirmationDeviceTest.kt` | `598253c5474336c49c09f54313d223982241c9fb8d705af6920f5dfd989d99ef` |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/RecoveredConfirmationUiFixture.kt` | `9550f4595b23e91688c0106c2bb01be8643fb4e96cc670eec7ee901ae56b36c2` |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aRecoveredConfirmationAndroidBridgeMain.java` | `f768b60270716709af8a19b2142497fd41d115c36cea092e136c45f48c660402` |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/HandoverActivity.kt` | `14f241c414ce05077e5a056e02eb4344848b253ce9a1d007d56fd1a195e0d397` |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/HandoverViewModel.kt` | `2de1c877af814a6267834bdbb4be093b47c91c7069cf324d5e3a2f0098d3bcd7` |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverCardReader.kt` | `3e800f2c9e05647f287c79c11b95abe7d763d584164fb3a09df08b60834bc2ba` |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/TemporaryHandoverCardReader.kt` | `f4647ab8e076a310002eb15ec9e0f0c5030788aaa3bcb8151da7c24c17baa26cc` |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryHandoverConfirmationWorker.kt` | `7704eed5b546fa50ab91a1eb2c044667d0c40e2a6feab1b94e01f18121e2b597` |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryHandoverConfirmationRepository.kt` | `ab8a55334253490f42932614cd4d031567f78417c06052901830a584858c4360` |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryRecoveredConfirmationRepository.kt` | `925626170530fbed5725741c26bb52356817692d8232446326f314fd8846a9eb` |
| `android/app/src/main/java/com/acrylic/outbound/pda/PdaApplication.kt` | `8ce9a0023449a737641f528afe3ea603b49a3e7133d32d23eb2f2a1801147799` |
| `android/app/build.gradle.kts` | `597edec0dfa7312db371079e23a942654c3035389c217e458730212eb1160db7` |
| `server/src/main/resources/application.yml` | `ee582399700506698f3436b1cc6b8e18c47752661441dbaef0665bd855aaa2bc` |
| `server/src/main/java/com/acrylic/outbound/handover/HandoverRecoveredConfirmationEndpoint.java` | `0cb79271e9ed2947d1690af4e03449f5ae72a59825a1fbd00580990ae7a0c9d4` |
| `server/src/main/java/com/acrylic/outbound/handover/HandoverRecoveredConfirmationController.java` | `4b5ff974a6fd57e72d5a2add015e00081b7c75b3e84f8f7c16d51eeac74b8016` |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/TemporaryHandoverActionGate.kt` | `1aa677d8ef2c1f04081ed76c7a519a055276ebb0b9f35c0677554e632e52222c` |

## 结论与下一步

当前树中的取消链已经满足本次 fail-closed 处置：无 `SyncWorker` 后台接入，默认 Android/服务端均关闭，现行恢复卡片只读，旧 dialog/fixture/bridge 仅保留。`TEMP_*` 在显式 Debug 开关下仍可执行历史 WIP，这不是产品入口，也不构成 D06/D07、D13/D14、D17、E06 L2、D18 L3 或第 8 节闸门证据；不得启动旧 run03、旧 bridge 或取消链设备测试，不得连接原库/现网。

直接验证复用 `GOAL_E08_D17_FAIL_CLOSED_FIX_RUN01_2026-10-18.md` 的 gate 单测、构建、lint 和失败保全，并以当前 SHA 做负向 caller 检查。下一独立工作应选择仍有效的 U02/U04 或 E07/E08 组合，另建隔离输入和影响清单；取消范围不重新入队。总目标继续 active，只有第 8 节全部闸门在实际统一候选上满足时才可 complete。
