# E08/D17 recovered-confirmation WIP 逐路径审计（takeover04，2026-10-18）

本记录是本次接管的只读范围审计。输入树为 `D:/acrylic-pda-outbound`，分支为
`codex/development`，HEAD 与 `origin/codex/development` 均为
`93710ec5cff979a567654084c80abe089e9a73b7`。审计前已核对当前 `git status`、活动聊天、
目标监听、共享服务、Android 模拟器、ADB 和磁盘；继承源码、未跟踪文件、handoff-final01
及 run03 失败原件均保留。本记录不恢复旧链、不覆盖继承改动。

## 判定依据

- `docs/BUSINESS_POLICY_R07.md` 第1节及 `docs/history/R07_RECALL_REPUBLISH_SCOPE_2026-10-11.md`：故障订单走后台撤回、重新发布、新设备从头作业；坏机进度承接、多跳和专用接收者准备/确认/激活链已取消。
- 总目标第4、5、7、8节：D06/D07 必须验证撤回重发和新设备独立作业；D13/D14 只保留可信恢复、原 ID/原件/回执及截点重建；静态构建、fixture、模拟器或历史通过不能替代业务验收。
- 当前产品代码以 `RECOVERED_HANDOVER_DEVELOPMENT=false` 默认关闭；恢复读取须绑定真实登录、设备、仓库、会话、恢复代次及签名原件。确认/激活 POST 不得由只读恢复入口触发。

## 逐路径清单

| 路径 | 仍有效范围或已取消范围 | 实际调用事实 | 处置建议 | 直接受影响验证 |
| --- | --- | --- | --- | --- |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryRecoveredConfirmationWorker.kt` | 已取消接收确认/激活链 | `doWork()` 仅在 DEBUG 且 `RECOVERED_HANDOVER_DEVELOPMENT` 打开时调用 `temporaryRecoveredConfirmations.resume()`；`restorePending()` 只按同一 LOGIN 为 pending 行排队；当前 `SyncWorker` 没有调用它 | 保留源码、哈希和失败证据；不接入现行调度，不改为确认 worker | 默认构建/lint；静态无生产 caller；默认启动、重启、不同 LOGIN 下零排队、零 POST、零业务写入 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverConfirmationDialog.kt` | 已取消接收确认入口 | 对话框把“已核对，确认接收”回调接到确认操作；当前产品导航没有该 dialog caller | 保留 WIP 与单测，不接回导航，不把大字/滚动显示计作当前功能 | 默认构建/lint；`rg` 无产品 caller；只读恢复页无确认触摸点 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverConfirmationDisplay.kt` | R09 仍有效的可信原件只读候选 | 从本机保存的 MANIFEST/PAYLOAD 原字节解析车号、柜号、`RETAINED` 清单、条码和原顺序；本身不联网、不写库 | 保留并与确认/激活解耦，只展示已验证原件事实 | 原字节/顺序、损坏、非 `RETAINED`、跨 LOGIN/代次拒绝；Android 只读入口实跑 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverCardReader.kt` | R09 只读恢复候选 | 仅在 DEBUG 且 recovered 开关打开时读取 stage/display 并返回 `RECOVERED_READ`；默认关闭或校验失败返回不可用只读卡片 | 保留只读行为；不得连接确认、激活、扫描、普通 TEMP 作业 | 主机边界单测、默认构建/lint、实际只读入口；数据库确认表与业务表零写入 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/HandoverViewModel.kt` | 普通交接和只读恢复有效；确认/激活分支已取消 | `stage()` 在 `RECONCILING` 时只调用恢复 stage；`perform()` 的 `RECOVERED_READ` 只读，`RECOVERED_CONFIRM` 明确抛错；旧 `TEMP_CONFIRM`/`TEMP_INSTALL`/`TEMP_START` 仍受另一个开发开关保护 | 不恢复 recovered 确认；旧 TEMP 分支作为继承 WIP 保留，保持默认关闭 | 只读卡片、默认开关、权限/身份变化；旧确认链不计 D06/D07/D13/D14/D17 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/HandoverActivity.kt` | 普通交接有效；旧 TEMP 确认入口属取消范围 | 当前 `act()` 只处理 `TEMP_*`、普通 `CONTINUE` 和导航；没有 recovered dialog 分支 | 保留普通返回/导航；不新增 recovered 确认页面 | 默认 UI 构建/lint；无 recovered-confirmation 产品触摸入口 |
| `android/app/src/main/java/com/acrylic/outbound/pda/sync/SyncWorker.kt` | 现行同步边界 | 只调用旧 `TemporaryHandoverConfirmationWorker.restorePending()`；未调用 recovered confirmation worker，且旧 worker 默认 fail-closed | 保持默认关闭；不得把取消链塞入恢复同步 | 默认启动/重启、不同 LOGIN、无 recovered confirmation POST |
| `android/app/src/main/java/com/acrylic/outbound/pda/PdaApplication.kt` | 依赖注入遗留 | 暴露 `temporaryRecoveredConfirmations`，但应用启动不排队、不 POST | 保留注入保护继承 WIP；不新增入口或放宽开关 | 默认构建/lint；应用启动无 recovered POST |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/RecoveredConfirmationDeviceTest.kt` | 已取消链，不是原 PDA/原身份现场证据 | `@Ignore`；测试覆盖确认 POST、首响应丢失、重试、`CONFIRMED`、重启和换 LOGIN | 保留测试、输入和失败原件；不重跑旧 run03，不计 UI/D14 | 只编译和静态范围核对；任何新产品测试必须无确认 POST/激活/业务写入 |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/RecoveredConfirmationUiFixture.kt` | 已取消链的合成 fixture | 导入开发私钥，用 MockWebServer 转发 `recovered-confirmation` POST，保存 command/receipt/exchange/touch 文件 | 保留私有输入与失败原件；不能冒充原 PDA、原身份、原回执或真实 HTTP/MySQL | 仅编译；禁止替代真实 HTTP/MySQL、原设备、现场事实 |
| `android/app/src/test/java/com/acrylic/outbound/pda/ui/RecoveredHandoverConfirmationDialogTest.kt` | 已取消链主机单测 | 直接调用 dialog 并点击确认/返回回调；不证明产品导航可达 | 保留为历史边界测试，不扩展为产品验收 | 定向编译/单测可复用；结果只证明组件行为 |
| `android/app/src/test/java/com/acrylic/outbound/pda/temporary/TemporaryRecoveredConfirmationRetryTest.kt` | 已取消链的 LOGIN 围栏单测 | 只验证同 LOGIN 允许、变更 LOGIN 拒绝，不发 HTTP | 保留作 fail-closed 边界证据，不计 D06/D07 | 定向主机单测可复用；注明无真实 HTTP/MySQL |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aRecoveredConfirmationAndroidBridgeMain.java` | 已取消链，不能作为 D13/D14 正向恢复 | 启动真实 Spring/HTTP/MySQL，等待 Android 首次确认，并要求 `recovery_handover_confirmation` 从0变1 | 保留 bridge、快照和日志；不重启，不把确认表写入记为恢复通过 | 仅编译/静态核对；新增 bridge 必须只读并断言确认表零写入 |
| `server/src/main/java/com/acrylic/outbound/handover/HandoverRecoveredConfirmationController.java`、`HandoverRecoveredConfirmationEndpoint.java`、`HandoverRecoveredConfirmationStore.java` | 已取消的服务端确认写入口 | 配置开启时接收 `POST /api/v1/pda/handovers/{caseId}/recovered-confirmation` 并写 append-only 确认表 | 保留默认关闭实现和历史测试；不得因内部通过开放生产或只读恢复调用 | default-off 有/无权、非空迁移、确认表守恒；不计现行 R07/D13 正向 |
| `server/src/main/resources/application.yml`、`server/src/main/resources/db/migration/V82__recovery_handover_confirmation.sql` | 默认关闭的历史兼容边界 | 配置默认关闭；迁移建立确认表及不可更新/删除约束 | 不删除迁移、不打开配置；只读恢复不得写该表 | 非空迁移、default-off、有权/无权、表守恒 |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryHandoverConfirmationWorker.kt`、`TemporaryHandoverConfirmationRepository.kt` | 已取消旧 TEMP 接收确认链 | 旧 worker 可发现 pending 意图并在开发开关打开时发送确认、保存回执；`TEMP_CONFIRM` 会 queue 后 resume | 保留继承源码和历史报告；不接入 R07 新方案或 R09 只读路径 | 默认构建/lint；不执行旧确认候选 |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryHandoverActivationRepository.kt`、`TemporaryHandoverDownload.kt`、`TemporaryHandoverWork.kt`、`TemporaryHandoverBaseline.kt` | 已取消激活及换机进度承接 | 依赖确认回执生成新轮、安装 retained 占用并开始扫描 | 保留源码与失败证据；不得从只读页连接 download-ack、activation 或扫描 | 默认构建/lint；入口矩阵保持关闭 |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryRecoveredHandoverRepository.kt`、`TemporaryRecoveredHandoverAccess.kt`、`TemporaryRecoveredHandoverVerifier.kt`、`TemporaryRecoveredHandoverPayload.kt`、`TemporaryRecoveredHandoverMigration.kt` | R09 可信恢复原件读取仍有效；其中 confirmation 后续动作已取消 | `stage/read` 下载并校验原 manifest/payload，绑定身份、恢复代次和本机持钥后只写本机原件；migration 仅建立恢复暂存表/块表 | 保留只读存储与校验；不得以其结果创建 TEMP preparation、confirmation、activation 或扫描授权 | 非空 Room 迁移、原字节/签名/身份/代次/损坏/重复读取守恒；真实 HTTP/MySQL 恢复读取 |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryRecoveredConfirmationRepository.kt`、`TemporaryRecoveredConfirmation.kt`、`TemporaryRecoveredConfirmationCommand.kt`、`TemporaryRecoveredConfirmationReceiptVerifier.kt`、`TemporaryRecoveredConfirmationMigration.kt` | 已取消接收确认写链 | `queue/resume` 持久化显式确认意图并向 recovered-confirmation POST，保存回执；当前 UI 不调用其确认分支 | 保留模型、迁移、失败日志和哈希；不接入 R09 只读流程，不删除唯一证据 | 仅默认关闭/静态无 caller；历史写链不计 D06/D07/D13/D14/D18 |
| `android/app/src/test/java/com/acrylic/outbound/pda/temporary/TemporaryRecoveredConfirmation*`、`android/app/src/androidTest/java/com/acrylic/outbound/pda/RecoveredConfirmation*` | 已取消链的测试/fixture集合 | 测试使用合成身份、开发签名、MockWebServer 或模拟器，覆盖确认和恢复重试；不能证明原设备或现场 | 保留失败 XML/日志/输入；不重启旧 run03，不以测试通过替代业务候选 | 只做编译、静态调用和默认关闭边界；恢复只读新增测试另行建立 |
| `android/app/src/main/java/com/acrylic/outbound/pda/sync/*`、`android/app/src/main/java/com/acrylic/outbound/pda/temporary/*` 中与 `Sync`/`Handover`/`Temporary` 相关继承改动 | 混合范围：普通同步、R06/R07 现行作业和已取消旧链并存 | 由调用点和开关决定行为；旧 TEMP 确认/激活仍有代码但默认关闭，R07 撤回重发走独立后台/新设备链 | 逐文件保留，先按调用事实分层；不得因名称相近恢复取消功能；有效 R07/D13/D14 改动单独建立新候选 | 受影响路径跑真实 HTTP/MySQL、Android、Chrome、Python、非空迁移；取消链只做静态/默认关闭核对 |

## 结论与下一步

1. 本审计不恢复换机进度承接、多跳、接收者确认或激活链；上述 worker、dialog、fixture、device test、bridge 及旧 TEMP 代码继续作为默认关闭开发备份和失败证据保全。
2. 可继续推进的最小有效范围是 `RecoveredHandoverCardReader`/`RecoveredHandoverConfirmationDisplay` 的可信原件只读展示、身份/损坏围栏，以及 R07 撤回→重新发布→新设备从头作业。只读代码不得包装成确认能力。
3. 旧 recovered-confirmation UI run03 不重启；已有同根因失败按规则暂缓。下一业务切片使用全新隔离编号，在当前源码候选上补 Android、真实 HTTP/MySQL、Python 消费和非空迁移组合，并单独绑定 Chrome 复用关系；不把 run739 Chrome L1 自动升级为 L2。
4. 本审计文件是范围和调用事实记录，不是 D17、D14、D06/D07、E06 L2、D18 L3 或总目标通过证据；总目标继续保持 active。

## 本次输入与保全

- 交接全量快照：`.local/goal-20261017-resume/handoff-final01/input-final.json`、`dirty-final.json`、`dirty-final.zip`、`tracked-final.patch`、`inherited-protection-final.json`。
- 本次只读 status 快照：`.local/goal-20261018-resume/readonly-status.txt`，创建后保留，不替代 handoff 全量清单。
- 失败原件继续保留：`.local/goal-20261017-resume/android-ui-run03/build-failure01.log`、`.local/goal-20261017-resume/recovered-confirmation-ui-run03/private/mysqld.log`、`mysqld-skip-grants.log`、`mysqld-skip-pipe.log`。
- 共享 MySQL、既有 Android 模拟器、ADB 和其他项目监听只观察未停止；未连接原库/现网、未使用正式签名、未改变生产默认或 `localDeletionAllowed=false`。
