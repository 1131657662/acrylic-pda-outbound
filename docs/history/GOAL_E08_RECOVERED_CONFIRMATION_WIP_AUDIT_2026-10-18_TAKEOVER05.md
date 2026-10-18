# E08/D17 recovered-confirmation WIP 逐路径审计（takeover05，2026-10-18）

本次接管在树 `D:/acrylic-pda-outbound`、分支 `codex/development`、HEAD
`2cf6f894e60523e76e28a0e354c07dff9fc07de3` 上完成。工作树继承改动、handoff-final01、run03
失败原件和前序审计均未还原、覆盖或重排。现行范围以 R07 撤回→重新发布→新设备从头作业、
D13/D14 可信原件只读恢复为准；已取消的换机进度承接、多跳、接收者确认/激活链不恢复。

## 审计事实

- `RECOVERED_HANDOVER_DEVELOPMENT` 与 `TEMPORARY_HANDOVER_DEVELOPMENT` 的默认构建值均为
  `false`；服务端 recovered-confirmation 默认关闭。
- 当前调用检索显示没有生产导航调用 `RecoveredHandoverConfirmationDialog`，没有
  `SyncWorker` 调用 `TemporaryRecoveredConfirmationWorker`，只读入口只返回
  `RECOVERED_READ`；`HandoverViewModel` 对 `RECOVERED_CONFIRM` 明确拒绝。
- `RecoveredConfirmationDeviceTest` 标为 `@Ignore`，`RecoveredConfirmationUiFixture` 使用
  合成开发签名/MockWebServer；`E2aRecoveredConfirmationAndroidBridgeMain` 只属于旧确认链。
  这些不能冒充原 PDA、原身份、真实 HTTP/MySQL、现场回执或 D06/D07 正向验收。
- 直接验证依据：`rg` 调用矩阵、默认构建/lint、只读页面 L1 报告和
  `GOAL_E08_RECOVERED_CONFIRMATION_WIP_AUDIT_2026-10-18_TAKEOVER04.md`；未启动旧 run03。

## 逐路径清单

| 路径 | 范围判定 | 实际调用事实 | 处置建议 | 直接受影响验证 |
| --- | --- | --- | --- | --- |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryRecoveredConfirmationWorker.kt` | 已取消接收确认/激活链 | `doWork`/`restorePending` 仅在 DEBUG 且 recovered 开关打开时排队同 LOGIN pending 行；当前 `SyncWorker` 未调用 | 保留 WIP 与哈希；不接入调度、不改为确认 worker | 默认构建/lint；默认启动/重启、不同 LOGIN 零排队/零 POST/零业务写入 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverConfirmationDialog.kt` | 已取消接收确认入口 | 仅把确认按钮回调交给调用者；当前产品导航无 caller | 保留组件和历史单测；不接回导航 | `rg` 无产品 caller；只读恢复页无确认触点 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverConfirmationDisplay.kt` | R09 可信原件只读范围仍有效 | 解析 MANIFEST/PAYLOAD 原字节、车号/柜号/RETAINED 清单和原顺序，不联网不写库 | 保留并与确认/激活解耦 | 原字节、顺序、损坏、非 RETAINED、身份/代次边界的只读测试 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverCardReader.kt` | R09 只读恢复候选 | 仅 DEBUG+开关开启时读取恢复 stage；默认关闭或校验失败返回不可用只读卡片 | 保留只读行为；不连接扫描/确认/激活 | 主机边界单测、默认构建、实际只读入口；业务表与确认表零写 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/HandoverViewModel.kt` | 普通交接有效；确认分支取消 | `RECOVERED_READ` 只读；`RECOVERED_CONFIRM` 抛错；旧 `TEMP_*` 受另一开发开关保护 | 不恢复 recovered-confirmation；保留普通交接 | 只读卡片、身份/权限变化、默认开关矩阵 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/HandoverActivity.kt` | 普通交接有效；旧 TEMP 入口属取消范围 | `act` 只处理 TEMP、普通 CONTINUE 和导航，无 recovered dialog 分支 | 不新增 recovered 确认页面 | 默认 UI 构建/lint；无 recovered-confirmation 触摸入口 |
| `android/app/src/main/java/com/acrylic/outbound/pda/PdaApplication.kt` | 依赖注入遗留 | 暴露 recovered repository/worker 依赖，启动不排队、不 POST | 保留注入保护，不新增入口 | 默认构建；启动后无 recovered POST |
| `android/app/src/main/java/com/acrylic/outbound/pda/sync/SyncWorker.kt` | 现行同步边界 | 仅旧 TEMP worker restorePending；未调用 recovered worker，且旧链默认关闭 | 保持 fail-closed；不塞入取消链 | 默认启动/重启、不同 LOGIN、无 recovered POST |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/RecoveredConfirmationDeviceTest.kt` | 已取消链的合成测试 | 覆盖确认 POST、丢响应、重试、CONFIRMED、重启和换 LOGIN；标记 `@Ignore` | 保留测试/失败 XML；不重跑旧 run03 | 只编译和静态范围核对；不得作为 D06/D07/UI 正向证据 |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/RecoveredConfirmationUiFixture.kt` | 已取消链 fixture | MockWebServer 转发 `recovered-confirmation` POST，导入开发私钥并写 touch/receipt 文件 | 保留私有输入/失败原件；不冒充真实 HTTP/原 PDA | 仅编译；确认表/业务表写入不得计现行范围 |
| `android/app/src/test/java/com/acrylic/outbound/pda/ui/RecoveredHandoverConfirmationDialogTest.kt` | 已取消链主机单测 | 直接 show/click 对话框回调，不证明产品导航可达 | 保留为组件边界历史证据 | 定向单测可复用；不计业务验收 |
| `android/app/src/test/java/com/acrylic/outbound/pda/temporary/TemporaryRecoveredConfirmationRetryTest.kt` | 已取消链 LOGIN 围栏单测 | 仅验证同 LOGIN 允许、变更 LOGIN 拒绝，不发 HTTP | 保留 fail-closed 证据 | 定向主机单测；不计真实 HTTP/MySQL |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aRecoveredConfirmationAndroidBridgeMain.java` | 已取消链 bridge | 启动隔离 Spring/HTTP/MySQL 并要求确认表从 0 变 1 | 保留 bridge/日志；不重启、不把确认表写入记为恢复通过 | 仅编译/静态核对；新恢复验证必须只读且确认表守恒 |
| `server/src/main/java/com/acrylic/outbound/handover/HandoverRecoveredConfirmationController.java` 与 `Endpoint.java` | 已取消服务端确认写入口 | 配置开启才接受 `POST .../recovered-confirmation`；默认关闭/无目标返回拒绝 | 保留实现与默认关闭测试；生产不开放 | default-off 有权/无权、非空迁移、确认表守恒 |
| `server/src/main/resources/application.yml`、`V82__recovery_handover_confirmation.sql` | 默认关闭兼容边界 | 配置默认 false；迁移只建立确认表及不可更新/删除约束 | 不删除迁移、不打开配置 | 非空迁移、default-off、表守恒 |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryRecoveredHandoverRepository.kt` 及 `*Access/Verifier/Payload/Migration` | R09 原件读取有效；确认后续取消 | 读取/校验 manifest/payload，绑定身份/代次/本机持钥，只写本机恢复暂存 | 保留只读存储；不创建 TEMP preparation/confirmation/activation/扫描授权 | 非空 Room、原字节/签名/身份/代次/损坏/重复读取守恒；真实 HTTP/MySQL 只读恢复 |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryRecoveredConfirmationRepository.kt`、`TemporaryRecoveredConfirmation.kt`、`*Command/ReceiptVerifier/Migration` | 已取消接收确认写链 | `queue/resume` 可持久化确认意图并 POST，当前 UI 不调用确认分支 | 保留模型/迁移/失败日志；不接入只读流程 | 默认关闭/静态无 caller；历史写链不计 D06/D07/D13/D14/D18 |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryHandoverConfirmationWorker.kt`、`TemporaryHandoverActivationRepository.kt` 及相关 `Download/Work/Baseline` | 已取消旧 TEMP 接收确认、激活和换机进度承接 | 旧链可在开发开关下确认、安装 retained 占用并开始扫描 | 保留继承源码/失败证据；不接入 R07/R09 新路径 | 默认构建/lint；入口矩阵关闭；不执行旧确认候选 |

## 结论

本审计只确认范围和调用事实，不恢复已取消链，也不把合成测试、模拟器、MockWebServer、开发签名或历史报告升级为 D06/D07/D13/D14/D17、E06 L2、D18 L3 或总目标通过。下一独立工作使用全新编号执行 R07 撤回→重新发布→新设备从头作业的隔离组合，继续补齐真实 Android、HTTP/MySQL、Chrome、Python、非空迁移和原模拟器缺口；恢复确认路径按三次同根因失败继续暂缓，除非出现新证据。

## 保全绑定

- `.local/goal-20261017-resume/handoff-final01/dirty-final.json`
- `.local/goal-20261017-resume/handoff-final01/dirty-final.zip`
- `.local/goal-20261017-resume/handoff-final01/tracked-final.patch`
- `.local/goal-20261017-resume/handoff-final01/inherited-protection-final.json`
- `.local/goal-20261017-resume/android-ui-run03/build-failure01.log`
- `.local/goal-20261017-resume/recovered-confirmation-ui-run03/private/mysqld.log`
- `.local/goal-20261017-resume/recovered-confirmation-ui-run03/private/mysqld-skip-grants.log`
- `.local/goal-20261017-resume/recovered-confirmation-ui-run03/private/mysqld-skip-pipe.log`
