# E08/D17 recovered-confirmation WIP 逐路径审计（takeover03，2026-10-18）

本次审计在接管后重新读取源码、调用点和现行范围，输入树为
`D:/acrylic-pda-outbound`，分支 `codex/development`，HEAD 为
`93710ec5cff979a567654084c80abe089e9a73b7`。工作树中的继承改动、未跟踪文件、旧
run03 失败原件和既有 WIP 审计均保留。本记录只新增审计事实，不恢复旧链、不覆盖继承文件。

## 范围依据

- R07 第1节把故障订单处理改为后台撤回、重新发布、新设备从头作业；原机进度承接、
  多跳和接收者准备/确认/激活链已取消。
- 总目标第4/5/7/8节要求 D06/D07 走撤回重发与新设备独立作业，D13/D14 只保留
  可信恢复、原ID/原件/回执和截点重建；旧确认表写入不能替代这些出口标准。
- R09 恢复契约允许恢复态的受控认证和只读读取，不能由摘要、fixture、模拟器、开发
  签名或合成身份推导确认、激活、首轮、扫描、释放或删除能力。
- 默认开关继续关闭：`RECOVERED_HANDOVER_DEVELOPMENT=false`；对话框、worker、fixture
  和 Java bridge 的历史测试不作为 D06/D07/D13/D14/D17 或第8节闸门通过。

## 逐路径清单

| 路径 | 范围判定 | 实际调用事实 | 处置建议 | 直接受影响验证 |
| --- | --- | --- | --- | --- |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryRecoveredConfirmationWorker.kt` | 已取消的接收确认/激活链 | `doWork()` 仅在 DEBUG 且 recovered 开关打开时调用 `temporaryRecoveredConfirmations.resume()`；`restorePending()` 只按同一 LOGIN 排队；当前 `SyncWorker` 没有调用它 | 保留源码和失败证据；不得接入现行调度。未来若保留恢复消费者，只能做原件/身份只读检查，不得 POST | 默认构建/lint；静态确认无生产 caller；默认启动、重启、不同 LOGIN 下零队列/零 POST |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverConfirmationDialog.kt` | 已取消的接收确认入口 | `show()` 渲染清单并把“已核对，确认接收”直接连接确认回调；当前产品导航没有 caller | 保留 WIP 和单测，不接回导航，不把文案、大字或滚动当成当前功能 | 默认构建/lint；`rg` 无生产 caller；只读页面无确认触摸点 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverConfirmationDisplay.kt` | R09 仍有效的只读展示候选 | 从本机保存的 MANIFEST/PAYLOAD 原字节解析车号、柜号、 RETAINED 清单和原顺序条码；不联网、不写库 | 保留并与确认/激活解耦，只展示身份围栏内的可信原件事实 | 原字节/顺序、损坏、非 RETAINED、跨 LOGIN 拒绝；Android 只读入口 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverCardReader.kt` | R09 只读展示候选 | recovered 开关打开时读取 stage/display 并返回 `RECOVERED_READ`；默认关闭或校验失败返回不可用只读卡片 | 保留只读行为；不得连接确认、激活、扫描或普通作业 | 主机边界单测、默认构建/lint、实际只读入口；零业务写入 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/HandoverViewModel.kt` | 普通交接和只读恢复有效；确认/激活分支已取消 | `RECOVERED_READ` 只读；`RECOVERED_CONFIRM` 明确抛错；旧 `TEMP_CONFIRM`/`TEMP_INSTALL`/`TEMP_START` 仍可在旧开发开关打开时排队、恢复、安装和开始 | 不恢复 recovered 确认；旧 TEMP 分支只保留继承代码并维持开关关闭 | 只读卡片、默认开关、权限/身份变化；旧确认链不计当前验收 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/HandoverActivity.kt` | 普通导航有效；旧 TEMP 确认入口属取消范围 | 只对 `TEMP_START`/`TEMP_CONFIRM` 处理旧链，没有 recovered dialog 分支 | 保留普通导航和返回；不新增 recovered 确认页，不打开旧 TEMP 开关 | 默认 UI 构建/lint；无 recovered-confirmation 触摸入口 |
| `android/app/src/main/java/com/acrylic/outbound/pda/sync/SyncWorker.kt` | 现行同步边界 | 调用旧 `TemporaryHandoverConfirmationWorker.restorePending()`，不调用 recovered worker；旧 worker 自身默认 fail-closed | 保持默认关闭，不把 recovered 确认塞入同步 | 默认启动/重启、不同 LOGIN、无 recovered 确认 POST |
| `android/app/src/main/java/com/acrylic/outbound/pda/PdaApplication.kt` | 依赖注入遗留 | 暴露 recovered confirmation repository，同时保留旧 TEMP repository；自身不排队、不 POST | 保留注入以保护继承 WIP，不新增入口或放宽开关 | 默认构建/lint；应用启动无 recovered POST |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/RecoveredConfirmationDeviceTest.kt` | 已取消链，不能作为原 PDA/现场证据 | `@Ignore`；fixture 覆盖确认 POST、丢首响应、重试、`CONFIRMED`、重启和换 LOGIN | 保留测试、输入和失败原件；不重跑旧 run03，不计 UI/D14 | 仅编译和静态范围核对；新增测试必须无确认 POST、无激活、无业务写入 |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/RecoveredConfirmationUiFixture.kt` | 已取消链的合成 fixture | 导入开发私钥，MockWebServer 转发 `recovered-confirmation` POST，保存 command/receipt/exchange/touch 文件 | 保留私有输入和失败证据；不能作为原 PDA、原身份、原回执或真实 HTTP/MySQL 验收 | 仅编译；禁止用 fixture 替代真实 HTTP/MySQL、原设备或现场事实 |
| `android/app/src/test/java/com/acrylic/outbound/pda/ui/RecoveredHandoverConfirmationDialogTest.kt` | 已取消链的主机单测 | 直接调用 dialog 并点击确认/返回回调；不代表产品导航可达 | 保留为历史边界测试；不扩展为产品验收 | 可定向编译/单测；结果只证明 dialog 组件 |
| `android/app/src/test/java/com/acrylic/outbound/pda/temporary/TemporaryRecoveredConfirmationRetryTest.kt` | 已取消链的同 LOGIN 围栏单测 | 验证同 LOGIN 允许、变更 LOGIN 拒绝；不发 HTTP | 保留作 fail-closed 边界证据；不计 D06/D07 | 定向主机单测可复用，需注明无真实 HTTP/MySQL |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aRecoveredConfirmationAndroidBridgeMain.java` | 已取消链，不能作为 D13/D14 正向恢复 | 启动真实 Spring/HTTP/MySQL，等待 Android 首次确认，并要求 `recovery_handover_confirmation` 从 0 写成 1 | 保留 bridge、快照和日志；不重启。若新增 bridge，必须只读且断言确认表零写入 | 仅编译/静态核对；确认表写入不得计灾后恢复 |
| `server/src/main/java/com/acrylic/outbound/handover/HandoverRecoveredConfirmationController.java`、`HandoverRecoveredConfirmationEndpoint.java`、`HandoverRecoveredConfirmationStore.java` | 已取消的服务端确认写入口 | controller 接收 `POST /api/v1/pda/handovers/{caseId}/recovered-confirmation`；endpoint 受配置条件控制并写 append-only 确认表 | 保留默认关闭实现和历史测试；不得因内部通过开放生产或只读恢复调用 | default-off 有/无权、非空迁移和确认表守恒；不计当前 R07/D13 正向 |
| `server/src/main/resources/application.yml`、`server/src/main/resources/db/migration/V82__recovery_handover_confirmation.sql` | 默认关闭的历史兼容边界 | 配置项默认关闭；迁移建确认表及不可更新/删除触发器 | 不删除迁移、不打开配置；只读恢复不得写该表 | 非空迁移、default-off、有权/无权、表守恒 |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryHandoverConfirmationWorker.kt`、`TemporaryHandoverConfirmationRepository.kt` | 已取消旧 TEMP 接收确认链 | 旧 worker 可发现 pending 意图并在开发开关打开时发送确认、保存回执；`HandoverViewModel` 的 `TEMP_CONFIRM` 会排队后立即恢复 | 保留继承源码和历史报告；不接入 R07 新方案或 R09 只读路径 | 默认构建/lint；不执行旧确认候选 |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryHandoverActivationRepository.kt`、`TemporaryHandoverDownload.kt`、`TemporaryHandoverWork.kt`、`TemporaryHandoverBaseline.kt` | 已取消激活及换机进度承接 | 依赖确认回执生成新轮、安装 retained 占用并开始扫描；旧 UI 可在开关打开时连接 `TEMP_INSTALL`/`TEMP_START` | 保留源码和失败证据；不得从只读页面连接 download-ack、activation 或扫描 | 默认构建/lint；入口矩阵保持关闭 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/TemporaryOrdersActivity.kt`、`TemporaryDetailsActivity.kt`、`TemporaryDetailsDeviceUi.kt`、`activity_temporary_details.xml` | E08/D17/R09 有效只读边界 | `HANDOVER_BLOCKED` 进入详情只读页，读取原件并支持分页/定位/导出，不确认、不激活、不开始新轮 | 保留只读入口；继续补实际 Android 行为和数据库字节守恒，不连接已取消链 | Android 默认行为、分页/返回、前后事实一致；UI L1 仍未通过 |
| `server/src/main/java/com/acrylic/outbound/recovery/RecoveryAuthorityLeaseChannel.java`、`RecoveryEffectGate.java`、`RecoveryFenceAuthority.java`、`RecoverySelectSql.java` | D13/R09 恢复围栏有效范围 | 提供恢复代次、锁/租约和只读选择；不应被 recovered-confirmation fixture 当作接收者激活许可 | 保留并按 D13/D14 受影响范围验证；不为取消的确认链添加旁路 | 真实 HTTP/MySQL、并发、撤权、重启及非空迁移组合 |
| `server/src/main/java/com/acrylic/outbound/temporary/TemporaryStore.java` 与 `temporary/evidence/SourceEvidence*` | D04/D13/D14 共享证据边界 | 读写 TEMP 来源/证据与原件摘要；不是 recovered 接收确认授权 | 保留并与 R07 撤回重发、R09 可信来源分别验证；不混用取消的确认链 | 非空迁移、原件/身份/高水位拒绝、真实 HTTP/MySQL |

## 处置结论

1. 本审计不恢复换机进度承接、多跳、接收者确认或激活链；上述 worker/dialog/fixture/device test/bridge 和旧 TEMP 代码继续作为默认关闭开发备份与失败证据保全。
2. 可继续推进的最小范围是 `RecoveredHandoverCardReader`/`RecoveredHandoverConfirmationDisplay` 只读原件读取、身份/损坏围栏，以及 R07 撤回→重新发布→新设备从头作业；不得把只读代码包装成确认能力。
3. 当前主机边界复验已有三次同根因 SQLite fixture 失败，按规则暂缓，不重启旧 run03。run735 只证明隔离 Android/HTTP/MySQL 的局部撤回消费，不改变上述范围判断。
4. 直接验证必须按影响层执行：默认 Android 构建/lint 与只读入口；有效 R07 路径跑真实 HTTP/MySQL、Android、Web/Chrome 和迁移组合；取消链不得以 fixture、MockWebServer、开发签名、模拟器或确认表写入计入 D06/D07/D13/D14/D17/D18。

## 本次环境保全

- run734 隔离 MySQL `19344` 已先精确保存 PID/命令行，随后在缺少 `SHUTDOWN` 权限时保全失败输出；通过 shared-memory 的临时 `skip-grant-tables` 实例授予单项 `SHUTDOWN` 后，以 run734 隔离账号认证停机退出码 0。
- 最终身份、认证停机和端口/PID 消失记录见 `.local/e2a-cross-20260925-734/run734-authenticated-shutdown-final-20261018.txt`；外部 `3306`/`33318` 实例未连接、未停止。
- 本记录不删除 `.local/goal-20261018-resume/handoff-final01/`、run03 失败原件、run735 证据或任何继承源码/未跟踪文件。
