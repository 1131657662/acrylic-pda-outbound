# E08/D17 恢复确认 WIP 接管审计（takeover12，2026-10-19）

本次审计在 `D:/acrylic-pda-outbound`、`codex/development` 当前输入上完成。接管前只读核实分支、远端、202 个继承/未提交条目、进程/监听、ADB、隔离库和磁盘；未连接或停止任何共享服务，未运行旧 `run03`、bridge 或取消链设备测试。版本事实 `-Write/-Check` 均通过。

## 输入、写权与保全

- HEAD、`origin/codex/development` 和 `git ls-remote` 均为 `9c6feaffe39633206734accac7c945a4b4bfcc1f`；无暂存，202 个 tracked/untracked 条目逐文件哈希见 `.local/goal-20261019-resume/takeover-audit12/dirty-hashes-final.json`，完整状态、patch、worktree 和磁盘/监听/进程见同目录。
- 本会话已登记为唯一源码写者；未发现其他本项目源码写者。PID 22580 为空闲 Gradle daemon；本项目隔离 MySQL 13316/13317、共享 MySQL 3306、外部项目 33318/Vite 和 ADB 5037 仅观察，均保持原状。
- `handoff-final01`、原资料、原身份/Keystore、原回执、失败日志、最终 APK、旧 WIP 和高频 run925/run06 证据均保留。当前源文件绑定见 `wip-source-bindings.json`，本切片绑定见 `current-slice-bindings.json`。

## 现行政策判定

D06/D07 当前方案是后台有权撤回坏机已领取且未成功出库的订单，再发布新资格，由新设备独立从头作业；不迁移坏机进度。R07 已取消自动换机承接、多跳、接收者确认和激活链。D13/D14 只保留灾后原件、截点、恢复写入和只读核对边界。总目标第4/5节要求取消链保留源码和失败资料，但不得成为现行产品入口或验收链。

## 逐路径清单

| 路径 | 范围判定 | 实际调用事实 | 处置建议 | 直接受影响验证 |
| --- | --- | --- | --- | --- |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryRecoveredConfirmationWorker.kt` | 已取消接收确认/激活写链 | `doWork`、`restorePending`、`schedule` 只在 Debug 且 `RECOVERED_HANDOVER_DEVELOPMENT` 下可用；当前无产品 caller | 保留源码、测试和失败证据；不新增 caller、不调度、不 POST | `rg` 负向 caller；既有 gate 单测、Debug/AndroidTest build、lint |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverConfirmationDialog.kt` | 已取消接收确认入口 | 仅 `show` 和确认回调；当前 Activity/ViewModel 无调用 | 保留组件与历史单测；不恢复导航 | caller 矩阵、build/lint |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverConfirmationDisplay.kt` | R09 原件只读展示，仍有效 | 只解析本机 stage/chunks 和扫码资料；不联网、不写业务库 | 保留并与确认/激活解耦 | reader 单测、损坏/乱序/代次边界 |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/RecoveredConfirmationDeviceTest.kt` | 已取消链的合成设备测试 | `@Ignore`；fixture 驱动确认页、丢响应、换 LOGIN 和重启 | 仅编译保留；不得启动旧 run03 | `assembleDebugAndroidTest` 可复用，不计业务验收 |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/RecoveredConfirmationUiFixture.kt` | 已取消链合成 fixture | MockWebServer `/recovered-confirmation`，写合成 touch/receipt 和开发接收机钥 | 不得冒充原 PDA、现场身份或回执 | 仅静态审计/编译 |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aRecoveredConfirmationAndroidBridgeMain.java` | 已取消链测试 bridge | 测试侧启动 HTTP/MySQL，等待 marker 后写确认表；非现行产品路径 | 保留 bridge/失败资料；不启动、不接产品 | 静态范围核对；确认表不得被只读恢复改变 |
| `android/app/src/main/java/com/acrylic/outbound/pda/sync/SyncWorker.kt` | 现行同步有效，取消链不得进入 | 当前调用 E2A、草稿和 `TemporaryContinuationWorker`；无 `TemporaryRecoveredConfirmationWorker.restorePending` | 保持 fail-closed 修复；不恢复后台 caller | 负向 `rg`、gate 单测、build/lint |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/HandoverActivity.kt` | 普通交接有效，恢复确认取消 | TEMP 导航/对话框前通过 `TemporaryHandoverActionGate`；无 `RECOVERED_CONFIRM` 分支 | 保留 guard；不导航旧 dialog | gate 单测、Android build/lint；触摸另列 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/HandoverViewModel.kt` | 普通交接和 R09 只读有效 | `perform` 拒绝关闭 TEMP action；`RECOVERED_READ` 只读；`RECOVERED_CONFIRM` 明确抛取消错误 | 保留拒绝逻辑；不恢复确认/激活调度 | gate 单测、默认入口负向检查 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverCardReader.kt` | R09 只读有效 | Debug recovered 开关下返回 `RECOVERED_READ`，文案明确确认/激活取消 | 保留只读；不接扫码/确认/激活 | reader 单测、默认关闭/有无权 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/TemporaryHandoverActionGate.kt` | 默认关闭边界有效 | TEMP action 仅 Debug 且显式 `TEMPORARY_HANDOVER_DEVELOPMENT` 可达 | 保持 default-off；隔离开关不能当产品候选 | gate unit test、default-off build/lint |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/TemporaryHandoverCardReader.kt` | TEMP 浏览和关闭提示有效，确认链取消 | 关闭时返回 `TEMP_DISABLED`；显式开发开关才产生历史 action | 保留 fail-closed reader；不把开发 action 当 D06/D07 | reader/gate 回归；既有 SQLite 环境失败继续保全 |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryHandoverConfirmationWorker.kt` | 已取消 TEMP 接收确认写链 | 只有显式 Debug TEMP action 排队；默认无 WorkManager 任务 | 保留 WIP；不恢复同步 caller、不启动 run03 | default-off 无写/无 POST 检查 |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryHandoverConfirmationRepository.kt` | 已取消 TEMP 确认持久化/POST | `queue`/`resume` 写 Room 并调用 `temporary-confirmation`，受开发开关约束 | 保留源码、迁移、失败资料；不接只读链 | 默认无写/无 POST；隔离测试仅历史依据 |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryRecoveredConfirmationRepository.kt` | 已取消恢复确认持久化/POST | `queue`/`resume` 可写 Room 并调用 `recovered-confirmation`；当前无产品 caller | 保留源码和失败资料；不新增调用 | 静态 caller 矩阵；不启动 bridge |
| `android/app/src/main/java/com/acrylic/outbound/pda/PdaApplication.kt` | 依赖暴露不等于调度 | 暴露 repository；`onCreate` 只启动现行 `SyncScheduler` | 保留依赖，禁止初始化排队 | build/lint、无确认 WorkManager 任务 |
| `android/app/build.gradle.kts` | 默认关闭边界有效 | TEMP/recovered 两个开发 flag 默认 `false` | 保持 default-off；不把隔离 APK 当候选 | default-off build/lint |
| `server/src/main/resources/application.yml` | 服务端恢复确认保持关闭 | `outbound.handover.recovered-confirmation.enabled=false` | 不开放七类生产决定能力 | 配置静态检查、disabled HTTP 负向复用 |
| `server/src/main/java/com/acrylic/outbound/handover/HandoverRecoveredConfirmationEndpoint.java` | 已取消产品写入口的保留实现 | 仅 property 开启时装配并调用 store | 保留实现，不开配置、不接客户端 | default-off 配置检查 |
| `server/src/main/java/com/acrylic/outbound/handover/HandoverRecoveredConfirmationController.java` | 已取消产品写入口的保留实现 | commands 开关关闭时不接受自带 authority；endpoint 不存在时 disabled | 保留实现，不新增入口 | disabled/default-off 负向检查 |

## 结论与下一步

本次输入仍满足 fail-closed 处置：取消链无 `SyncWorker` 后台接入，Android/服务端默认关闭，现行恢复卡片只读；旧 worker/dialog/fixture/bridge 仅保留源码、失败证据和编译边界。不得恢复取消链，不得直接重启旧 run03，不得把隔离 fixture、测试签名、模拟器或合成身份当作 D06/D07/D13/D14 业务事实。

本轮可复用验证依据是 `docs/history/GOAL_E08_D17_FAIL_CLOSED_FIX_RUN01_2026-10-18.md` 及其 gate/build/lint 失败保全，输入字节绑定见 takeover12 快照。下一步选择新的有效 U02/U04 或 E07/E08 组合；优先补真实 Chrome/Python/非空迁移消费或新的隔离候选，必须建立新输入和影响清单。当前只计 U04 高频 Android/Python L1，不计 D17、E06 L2、D18 L3、第8节闸门或总目标完成。
