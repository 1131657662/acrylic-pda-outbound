# E08/D17 recovered-confirmation WIP 接管增量审计（2026-10-18）

本增量审计接续 `GOAL_E08_RECOVERED_CONFIRMATION_WIP_AUDIT_2026-10-18.md`，输入为
`D:/acrylic-pda-outbound` 的 `codex/development`、HEAD
`93710ec5cff979a567654084c80abe089e9a73b7`。本次接管快照为
`.local/goal-20261018-resume/takeover02/`。R07 第1节已取消原机交接、故障换机进度承接、多跳和接收者准备/确认/激活；D13/D14 只保留可信灾后读取、原件/原 ID/回执和截点重建，不得用本 WIP 恢复旧链。

## 逐路径清单

| 路径 | 范围判定 | 实际调用事实 | 处置建议 | 直接受影响验证 |
| --- | --- | --- | --- | --- |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryRecoveredConfirmationWorker.kt` | 已取消接收确认/激活链 | `doWork()` 仅 DEBUG 且 `RECOVERED_HANDOVER_DEVELOPMENT` 打开时调用 `temporaryRecoveredConfirmations.resume()`；`restorePending()` 只按同一 LOGIN 排队，当前 `SyncWorker` 没有调用 | 保留 WIP 和失败原件，不接入现行调度；只读恢复不能 POST | 默认构建/lint；静态确认无 `SyncWorker` caller；零队列/零 POST |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverConfirmationDialog.kt` | 已取消接收确认入口 | `show()` 渲染原清单，并将“已核对，确认接收”直接连接确认回调；当前产品导航无 caller | 保留源码/单测，不恢复入口；文案和大字显示不计当前功能 | 默认构建/lint；`rg` 无生产 caller；只读页面无确认触摸点 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverConfirmationDisplay.kt` | R09 仍有效的只读展示候选 | 从已保存 `MANIFEST`/`PAYLOAD` 原字节解析车号、柜号、`RETAINED` 清单和原顺序条码，不联网、不写库 | 保留并与确认/激活解耦；仅展示可信原件和身份围栏内事实 | 原字节/顺序、损坏、非 `RETAINED`、跨 LOGIN 拒绝；Android 只读入口 |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/RecoveredConfirmationDeviceTest.kt` | 已取消链，不能作为原 PDA/现场证据 | `@Ignore`；通过 fixture 覆盖确认 POST、丢首响应、重试、`CONFIRMED`、重启和换 LOGIN | 保留测试、输入和失败证据；不重跑旧 run03，不计 UI/D14 | 仅编译/静态范围核对；新测试必须无确认 POST、无激活、无业务写入 |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/RecoveredConfirmationUiFixture.kt` | 已取消链的合成 fixture | 使用导入开发私钥和 MockWebServer 转发 `recovered-confirmation` POST，保存 command/receipt/exchange/touch 文件 | 保留私有输入和失败原件，不作为产品输入、原 PDA 或原回执；不重新启用 | 仅编译；真实 HTTP/MySQL、原设备和现场证据不能由 fixture 替代 |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aRecoveredConfirmationAndroidBridgeMain.java` | 已取消链，不能作为 D13/D14 正向恢复 | 启动真实隔离 Spring/HTTP/MySQL，等待 Android 首次确认，并断言 `recovery_handover_confirmation` 从 0 写成 1 | 保留 bridge、快照和日志，不重启；若新增 bridge 必须只读且断言确认表无写入 | 仅编译/静态核对；确认表写入不得计灾后恢复 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverCardReader.kt` | R09 只读展示候选 | DEBUG 且恢复开发开关打开时读取 stage/display，默认关闭或校验失败返回不可用只读卡片 | 保留只读行为，不连接确认、激活、扫描或普通作业 | 默认开关、主机单测、只读产品入口；零业务写入 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/HandoverViewModel.kt` | 只读恢复有效；确认/激活分支取消 | `RECOVERED_READ` 只读；`RECOVERED_CONFIRM` 明确抛错；`TEMP_CONFIRM`/`TEMP_INSTALL` 是旧开发链 | 不恢复 recovered 确认；旧 TEMP 分支只保留并保持开关关闭 | 只读卡片、默认开关、权限/身份变化；旧确认不作验收 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/HandoverActivity.kt` | 普通导航有效；旧 TEMP 对话框属取消范围 | 没有 recovered dialog 分支；旧 TEMP 确认仅在开发开关打开时可达 | 保留普通导航和返回，不新增确认页、不打开旧开关 | 默认 UI 构建/lint；无 recovered-confirmation 触摸入口 |
| `android/app/src/main/java/com/acrylic/outbound/pda/sync/SyncWorker.kt` | 现行同步边界 | 只发现旧 `TemporaryHandoverConfirmationWorker`；不调 recovered worker，且旧 worker 默认 fail-closed | 保持默认关闭，不把确认恢复塞入同步 | 默认启动/重启、不同 LOGIN、无确认 POST |
| `android/app/src/main/java/com/acrylic/outbound/pda/PdaApplication.kt` | 依赖注入遗留 | 暴露 recovered confirmation repository，同时保留旧 TEMP repository；自身不排队、不 POST | 保留注入以保护继承 WIP，不新增入口或放宽开关 | 默认构建/lint；应用启动无 recovered POST |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryHandoverConfirmationWorker.kt`、`TemporaryHandoverConfirmationRepository.kt` | 已取消 TEMP 接收确认链 | pending 意图可持久化，resume 可写确认回执；均受 TEMP 开关保护 | 保留历史代码和报告，不接现行产品或 R09 恢复 | 默认构建；不执行旧确认候选 |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryHandoverActivationRepository.kt`、`TemporaryHandoverDownload.kt`、`TemporaryHandoverWork.kt`、`TemporaryHandoverBaseline.kt` | 已取消激活及换机进度承接 | 依赖确认回执生成新轮、安装 retained 占用并开始扫描 | 不从只读页面连接 `start`、download-ack、activation 或扫描 | 默认构建/lint；入口矩阵保持关闭 |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryOrdersActivity.kt`、`TemporaryDetailsActivity.kt` | E08/D17/R09 有效只读边界 | `HANDOVER_BLOCKED` 进入详情只读页，读取原件并支持分页/定位/导出，不确认、不激活、不开始新轮 | 保留只读入口，核数据库字节守恒 | Android 默认行为、分页/返回、前后事实一致 |

源码 SHA、开关和调用核对以本次接管树为准；关键文件 SHA 见接管快照的 `dirty-hashes-before.json`，旧审计中的历史 SHA 不覆盖本次输入。

## 结论

本次审计不恢复任何已取消的换机承接、接收者确认或激活链。可继续推进的最小范围是 R09 可信原件只读展示及其身份/损坏围栏；其余 WIP 保留为默认关闭开发备份。任何 Android/HTTP/MySQL 业务验收仍需新隔离输入，不能重启旧 run03 或把构建、fixture、模拟器和局部 L1 当作 D17、D14、E06 L2、D18 L3 或总目标通过。
