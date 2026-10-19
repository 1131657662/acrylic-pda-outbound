# E08/D17 recovered-confirmation WIP 范围审计（2026-10-18）

本单元在接管后重新核对当前树、继承 WIP、调用关系和隔离候选。输入树为
`D:/acrylic-pda-outbound`，分支 `codex/development`，HEAD 与远端均为
`93710ec5cff979a567654084c80abe089e9a73b7`。本次唯一写者为聊天
`01a147c5-0495-7703-ba98-2e7637cbc619`；其他项目聊天 idle/notLoaded，未发现并行项目写者。
接管快照见 `.local/goal-20261018-resume/takeover01/`。候选 `1595` 已失败并保全，不能作为业务候选。

## 判定依据

- `docs/BUSINESS_POLICY_R07.md` 第1节：故障订单由后台撤回并重新发布，新设备从头作业；不开发坏机进度承接、多跳及专用接收者准备/确认/激活链。
- 总目标第4、5、7、8节：D06/D07验证撤回重发和新设备独立作业；D13/D14只保留可信恢复、原ID/原件/回执和截点重建；全部闸门仍未满足。
- `docs/parallel/R09/RECOVERY_CONTROL_CONTRACT.md` 与 `RECOVERED_RECIPIENT_DOWNLOAD.md`：恢复态只允许本人真实 LOGIN 下的既有原件读取；不创建确认、激活、首轮、扫描、释放或删除能力。
- 默认开关 `TEMPORARY_HANDOVER_DEVELOPMENT=false`、`RECOVERED_HANDOVER_DEVELOPMENT=false`，服务端 `recovered-confirmation` 默认关闭。开发签名、fixture、MockWebServer、模拟器和合成身份不能替代原 PDA、原身份或现场回执。

## 逐路径清单

| 路径 | 范围判定 | 实际调用事实 | 处置建议 | 直接受影响验证 |
| --- | --- | --- | --- | --- |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryRecoveredConfirmationWorker.kt` | 已取消接收确认/激活链 | `doWork()` 仅在 DEBUG 且 recovered 开关打开时调用 `temporaryRecoveredConfirmations.resume()`；`restorePending()` 只在显式调用时排队同一 LOGIN 的 pending 行；当前 `SyncWorker` 未调用它 | 保留继承源码和失败证据；不得接入现行调度。若未来需要恢复，只能改成只读事实校验 worker，不能 POST | 默认构建/lint；静态断言 `SyncWorker` 无该 worker 调用；新只读消费者断言无队列、无 POST、无业务写入 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverConfirmationDialog.kt` | 已取消接收确认入口 | AlertDialog 的“已核对，确认接收”直接调用确认 repository；当前 `HandoverActivity` 没有该 dialog 的产品调用 | 保留原源码和单测，不接回导航；不把文案、滚动或大字显示当作当前功能验收 | 编译/lint；`rg` 确认无生产 caller；若重构只验证返回和原件保全 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverConfirmationDisplay.kt` | 仍有效的 R09 只读展示候选 | 由 `RecoveredHandoverCardReader` 从已保存 MANIFEST/PAYLOAD 原字节解析车号、柜号、RETAINED 清单和条码；不发网络请求、不写数据库 | 保留并与确认/激活代码解耦；仅允许原字节、顺序和身份围栏展示 | 原字节/顺序、损坏、非 RETAINED、跨 LOGIN 拒绝；Android 只读入口实跑 |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/RecoveredConfirmationDeviceTest.kt` | 已取消链；不是原 PDA/原身份现场证据 | `@Ignore`；fixture 导入开发私钥，执行确认 POST、丢首响应、重试、CONFIRMED、重启和换 LOGIN | 保留测试、输入和失败原件；不重跑旧 run03，不计 UI/D14 通过 | 只做编译和静态范围核对；新测试必须无确认 POST、无激活、无业务写入 |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/RecoveredConfirmationUiFixture.kt` | 已取消链的合成 fixture | MockWebServer 转发 recovered-confirmation POST，并保存 command/receipt/exchange/touch 文件；使用开发证书和合成输入 | 保留私有输入和失败原件，不作为产品输入或原回执；不重新启用测试 | 仅编译；禁止用 fixture 代替真实 HTTP/MySQL、原设备或现场 |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aRecoveredConfirmationAndroidBridgeMain.java` | 已取消链；不能作为 D13/D14 正向恢复 | 启动真实 Spring/HTTP/MySQL 后等待 Android 首次确认，并要求 `recovery_handover_confirmation` 从0变1 | 保留 bridge、快照和日志；不重启，不把确认表写入记为恢复通过 | 仅编译/静态核对；若另建 bridge，必须只读且断言确认表零写入 |
| `android/app/src/main/java/com/acrylic/outbound/pda/PdaApplication.kt` | 依赖注入遗留 | 暴露 recovered confirmation repository，同时保留旧 TEMP confirmation/activation repository；没有自行排队或 POST | 保留注入以保护继承 WIP；不新增产品入口或改变默认开关 | 默认构建/lint；应用启动无 recovered POST |
| `android/app/src/main/java/com/acrylic/outbound/pda/sync/SyncWorker.kt` | 现行同步边界；旧 TEMP 链仍为历史隔离能力 | 调用 `TemporaryHandoverConfirmationWorker.restorePending(app)`，但该 worker 以 DEBUG/TEMP 开关 fail-closed；不调用 recovered confirmation worker | 保留默认关闭行为；不得加入 recovered confirmation 调度，也不得在默认同步中恢复旧链 | 默认启动/重启、不同 LOGIN、无确认 POST；静态调用检查 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/HandoverViewModel.kt` | 普通交接和只读恢复有效；旧 TEMP 确认/激活已取消 | `RECOVERED_READ` 只读；`RECOVERED_CONFIRM` 明确抛错；`TEMP_CONFIRM` 仍 queue/schedule/resume，`TEMP_INSTALL` 仍 restore | 不恢复 recovered 确认；旧 TEMP 分支只保留继承代码并维持开发开关关闭 | 只读卡片、默认开关、权限/身份变化；旧确认链不作为验收 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/HandoverActivity.kt` | 普通导航有效；旧 TEMP dialog 属取消范围 | 没有 recovered dialog 分支；`TEMP_CONFIRM` 仍可在开发开关打开时弹确认 dialog | 保留普通导航和返回；不新增确认页面，不打开旧 TEMP 开关 | 默认 UI 构建/lint；无 recovered-confirmation 触摸入口 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverCardReader.kt` | R09 只读展示候选 | DEBUG 且 recovered 开关打开时读取 stage/display，返回 `RECOVERED_READ`；默认关闭或校验失败返回不可用只读卡片 | 保留只读行为；不得转为确认、激活、扫描或普通作业 | 主机单测、默认构建/lint、实际只读入口；零业务写入 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/TemporaryHandoverCardReader.kt` | 已取消旧 TEMP 接收确认/激活链 | 开发开关打开时返回 `TEMP_CONFIRM`/`TEMP_INSTALL`/`TEMP_START`；关闭时返回 `REFRESH` 并显示保持停止 | 保留旧 WIP 和历史证据，禁止重新打开开发开关；不把它计入 D06/D07 | 默认开关和静态动作矩阵；旧 UI fixture 仅历史 |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryHandoverConfirmationWorker.kt` | 已取消旧 TEMP 确认链 | `SyncWorker` 可发现 pending 行并调度，但 worker 在默认关闭时立即成功；打开开发开关后会调用确认 repository | 不删除继承源码；禁止接入现行产品或重跑旧确认候选 | 默认 build/lint；无默认队列和 POST |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryHandoverConfirmationRepository.kt` | 已取消接收确认 | `queue()` 持久化显式意图，`resume()` 发送确认并保存回执；受 DEBUG/TEMP 开关保护 | 保留原请求/回执模型，禁止扩展或包装为 R09 恢复 | 仅历史单测/编译；不作为当前门槛 |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryHandoverActivationRepository.kt`、`TemporaryHandoverDownload.kt`、`TemporaryHandoverWork.kt`、`TemporaryHandoverBaseline.kt` | 已取消激活及换机进度承接 | 依赖确认回执生成新轮、安装 retained 占用并开始扫描；均受 TEMP 开关围栏 | 保留源码和失败证据；不得从只读页面连接 `start`、download-ack、activation 或扫描 | 默认构建/lint；无产品入口启用 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/TemporaryOrdersActivity.kt` 与 `TemporaryDetailsActivity.kt`/`TemporaryDetailsReader.kt` | E08/D17/R09 有效只读边界 | `HANDOVER_BLOCKED` 改为进入详情只读页；详情读取原件、分页/定位和导出，不调用确认/激活/开轮 | 保留只读入口；不得连接旧 TEMP 确认链 | Android 默认行为、分页/返回、前后数据库字节守恒 |
| `server/src/main/resources/application.yml`、`HandoverRecoveredConfirmationEndpoint.java`、`V82__recovery_handover_confirmation.sql` | 默认关闭的历史兼容边界 | endpoint 仅显式配置时启用并写 append-only 确认表；迁移和保护触发器仍存在 | 不删除迁移、不打开配置；恢复只读路径不得写该表 | 非空迁移、default-off、有权/无权、表守恒；确认 endpoint 不计 D13/D14 |

## 候选与环境事实

- `1595`：`.local/e2a-cross-20260925-1595/` 完整保留。真实 MySQL 已正常停机，`test-result.json` 为 `exitCode=1, completed=false`；Gradle 日志显示测试执行器连接重置并退出70，不能解释为业务成功。
- 当前快照 `.local/goal-20261018-resume/takeover01/` 记录 120 条 dirty/tracked-or-untracked 输入、精确 SHA、进程、监听、磁盘和写权。共享 MySQL、另项目 MySQL、另项目 uvicorn/node 与 Gradle daemon 仅观察，未连接、停止或复用。
- 版本事实已执行 `tools/version_facts.ps1 -Write` 与 `-Check`，看板事实块按实际树刷新；总目标、D17、E06 L2、D18 L3 和第8节闸门仍未完成。

## 接管后边界补验

在同一工作树和唯一写者约束下，使用 D 盘临时目录运行：

```text
$env:TEMP='D:\\acrylic-pda-outbound\\.local\\rtmp-wip-boundary'; $env:TMP=$env:TEMP
gradlew.bat -p android testDebugUnitTest --tests com.acrylic.outbound.pda.ui.RecoveredHandoverCardReaderTest --tests com.acrylic.outbound.pda.ui.RecoveredHandoverConfirmationDialogTest --tests com.acrylic.outbound.pda.temporary.TemporaryRecoveredConfirmationRetryTest --no-daemon --console=plain
```

输入 HEAD 为 `93710ec5cff979a567654084c80abe089e9a73b7`，源码未因本次补验改变。Gradle 退出 0，`BUILD SUCCESSFUL in 1m 35s`；三个定向类零失败、零错误、零跳过。该结果只证明读取展示、对话框单测和同 LOGIN 围栏的主机边界，自审确认默认 `RECOVERED_HANDOVER_DEVELOPMENT=false`，没有启动 HTTP/MySQL、模拟器或设备，也没有生成业务确认 POST；不计 D17、D14、E06 L2、D18 L3 或总目标。

## 结论与下一步

1. 本审计不恢复任何取消范围；explicit worker/dialog/device fixture/bridge、旧 TEMP confirmation/activation/scan 链和 `1595` 失败证据继续保全。
2. 只读价值集中在 `RecoveredHandoverConfirmationDisplay`、`RecoveredHandoverCardReader`、`TemporaryOrdersActivity` 与 `TemporaryDetails*`；下一步只可围绕零 POST、零激活、零业务写入的真实恢复读取或独立 D13 修复推进。
3. `1595` 与此前候选失败计数保留；不得直接重启旧 run03，也不得把构建、fixture、模拟器或局部 L1 当作业务验收。
