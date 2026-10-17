# E08 recovered-confirmation WIP 范围审计（2026-10-17）

## 目的与输入

本单元只审计接管时保留的 Android recovered-confirmation worker/dialog/fixture/bridge 与现行范围的关系，不执行旧 run03、不启动服务/数据库/模拟器，也不修改业务源码。输入树为 `D:/acrylic-pda-outbound`，分支为 `codex/development`，接管时 HEAD 为 `16f6911e73dba039c4f2d12aca0c6c479ba03c12`。完整只读接管快照、状态、工作树、环境、监听和改动哈希保存在 `.local/goal-20261017-resume/resume-20261017-1014/`；看板旧结论保存在 `board-before-resume.md`，新写者登记为 `writer-takeover.json`。

对照依据：

- `docs/BUSINESS_POLICY_R07.md`、`docs/history/R07_RECALL_REPUBLISH_SCOPE_2026-10-11.md`：取消原机换机交接、故障换机进度承接、多跳及旧接收者准备/确认/激活，改为后台撤回坏机已领取未成功订单后重新发布，新设备从头作业。
- `docs/AUTONOMOUS_DEVELOPMENT_GOAL_2026-10-07.md` 第4、5、6、7、8节：D06/D07/D13/D14 的退出标准、R09灾后可信恢复边界、不得把取消范围恢复成开发门槛。
- `server/src/main/resources/application.yml`：`outbound.handover.recovered-confirmation.enabled=false` 默认关闭。
- `server/src/main/resources/db/migration/V82__recovery_handover_confirmation.sql`：该表是受限恢复证据，不能满足普通 authorization/activation 外键；这不改变 R07 对旧接收者确认/激活链的取消决定。

## 路径审计

| 路径 | 仍有效范围或已取消范围 | 调用事实 | 处置建议 | 直接受影响验证 |
| --- | --- | --- | --- | --- |
| `android/app/src/main/java/com/acrylic/outbound/pda/PdaApplication.kt`、`android/app/src/main/java/com/acrylic/outbound/pda/sync/SyncWorker.kt` | 已取消链；仅有条件地可为 R09 只读恢复保留基础设施 | `PdaApplication` 仍保留历史 repository 注入以保护继承 WIP；`SyncWorker` 不再调用 `TemporaryRecoveredConfirmationWorker.restorePending`，因此后台同步不会恢复旧确认任务 | 保留继承 WIP，不把它当 D06/D07/D13 通过；若继续 R09，只允许改造成读取/核验已保存事实，不得自动恢复接收确认请求 | 直接检查 `SyncWorker` 调用点；Debug/AndroidTest/lint/build；新增负向测试证明默认关闭、不同 LOGIN 不调度、不产生 POST |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/TemporaryDetailsActivity.kt`、`android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryDetailsReader.kt`、`android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryDetailsPrefix.kt` | `HANDOVER_BLOCKED` 的原件核对属于 E08/D17/R09 只读展示；旧交接确认/激活仍是取消范围 | `TemporaryOrdersActivity` 将 `HANDOVER_BLOCKED` 导航到明细；明细读取经过 `TemporaryHandoverBaseline.read` 只做身份/原件/保留条码校验，不新增作业事实；当前 UI 已将该状态设为只读并隐藏取消/调整入口，保留导出、分页和原序号/条码定位 | 保留只读原件读取与失败保全；禁止把明细页改成开轮、确认接收、激活或新设备续作入口；若恢复证据模型变化，另建只读消费者而不是复活旧 POST | `TemporaryDetailsReaderTest` 的原序号、筛选、定位、身份变化拒绝和零写入；Android UI 入口验证 `HANDOVER_BLOCKED` 只读摘要、无取消/调整动作、导出/返回可达 |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryHandoverBaseline.kt`、`TemporaryHandoverWork.kt`、`TemporaryHandoverDownload.kt`、`TemporaryHandoverConfirmationRepository.kt` | 旧接收者确认/激活链；不属于当前 R07 坏机撤回重发 | `TemporaryDetailsPrefix` 通过 baseline 读取已保存 stage/confirmation/activation 以核对历史原件；`TemporaryHandoverWork`/`Download` 仍受 DEBUG 开关保护，当前产品入口未调度 | 保留继承源码与失败证据，默认关闭；不从只读明细重新连接 `start`、download-ack、confirmation 或 activation | Debug/staging 构建与 lint；默认开关无权/无入口检查；旧链测试只作历史保全，不计 D06/D07/D13/D14 正向通过 |
| `android/app/src/test/java/com/acrylic/outbound/pda/temporary/TemporaryDetailsReaderTest.kt`、`android/app/src/test/java/com/acrylic/outbound/pda/temporary/TemporaryHandover*Test.kt` | 前者为现行只读明细消费者；后者主要覆盖已取消旧交接链 | 明细测试验证分页、筛选、定位、身份变化和无写入；交接测试验证旧 activation/confirmation 状态机，不能证明当前产品范围 | 保留测试和输入；只读明细定向测试可复用，旧交接确认/激活测试不重跑、不改写成当前验收 | `testDebugUnitTest --tests ...TemporaryDetailsReaderTest`；旧链测试仅编译/lint，明确不计当前业务验收 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/HandoverViewModel.kt` | `RECOVERED_CONFIRM` 属已取消链；普通交接列表和冻结状态属于 E08/D17 有效 UI | `perform("RECOVERED_CONFIRM")` 立即失败并不写队列、不调度 Work；`load()` 将 recovered stage 混入交接卡片，卡片统一返回 `RECOVERED_READ` | 保留 recovered stage 的只读展示能力；不能把 recovery row 当 D06 撤回重发或 D14 正向作业 | 只读卡片读取、回登录阻断、无网络/重启持久性；确认分支不得出现在最终候选入口 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverCardReader.kt` | 已取消旧接收者确认/激活链 | 文案包含“确认接收”和“等待主管独立激活”；完成态返回 `RECOVERED_READ`，但前一步会实际提交确认 | 保留原资料读取逻辑作为候选 R09 证据消费者；删除或隔离确认动作，不能继续补激活 | 固定 manifest/payload 顺序与摘要读取；断言无 `temporaryRecoveredConfirmations` 写入、无 HTTP POST、恢复围栏仍为 `RECONCILING` |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverConfirmationDisplay.kt` | 可复用的 R09 只读恢复证据，不能带确认语义 | 从本机保存的 `MANIFEST`/`PAYLOAD` 读取 retained checklist、车号、柜号和原顺序，不直接写业务状态 | 可保留并重命名为只读 recovery evidence display；与接收确认 dialog/worker 解耦 | 单测覆盖原字节、顺序、非 `RETAINED` 拒绝、损坏保全；不运行旧确认链 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/HandoverActivity.kt`、`RecoveredHandoverConfirmationDialog.kt` | dialog 的“已核对，确认接收”属已取消链；`navigate` 重构属于 E08/D17 通用导航 | Activity 已移除 `RECOVERED_CONFIRM` 专用 dialog 分支；历史 dialog 类和测试仍保全，但没有产品入口；未知/旧动作落到 ViewModel 的 fail-closed 分支 | 保留通用 `navigate` 改动，隔离历史 dialog WIP；不新增接收者确认页面验收 | 运行既有 TEMP/FORMAL/RECALL 导航与 lint；对 recovered 卡片只验证只读/返回保留资料，不验证确认成功 |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryRecoveredConfirmationWorker.kt` | 已取消链 | 仅 Debug/开发开关运行；恢复同一 LOGIN 的 pending intent，失败时重试，唯一 work 名为 `temporary-recovered-confirmation:<id>` | 保留源码和失败证据，禁止继续调度旧确认；若 R09 需要恢复，只迁移为只读事实校验 worker | 单测覆盖 user/device/warehouse/session/版本/epoch 任一变化均不调度；默认关闭时 `doWork` 无副作用 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/TemporaryOrdersActivity.kt` | E08/D17 有效 UI，和取消的接收者确认链无直接关系 | `HANDOVER_BLOCKED` 改为“旧冻结原件 · 只读核对”，跳转 `TemporaryDetailsActivity`，不再进入旧 Handover 页面 | 保留；它符合已取消范围的只读阻断语义 | 运行 Temporary Orders blocked/details 导航、窄屏/大字和 lint；不把它计为 D06/D07 验收 |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/RecoveredConfirmationDeviceTest.kt`、`RecoveredConfirmationUiFixture.kt` | 已取消链；不是原 PDA/原身份现场证据 | fixture 导入开发私钥证书，绑定原 LOGIN，MockWebServer 将确认 POST 转发到 `/handovers/{caseId}/recovered-confirmation`，首个响应断开后重试；设备测试检查确认回执和重启/换 LOGIN | 保留原测试、输入和失败原件但不重跑、不计 UI L1/L2；不能用开发钥、MockWebServer 或模拟器替代原设备事实 | 若未来改成只读恢复 UI，重新建立新输入和新证据目录，验证无确认 POST、无激活、原资料可回读；旧 run03 不复用 |
| `android/app/src/test/java/com/acrylic/outbound/pda/temporary/TemporaryRecoveredConfirmationRetryTest.kt`、`RecoveredHandoverCardReaderTest.kt`、`RecoveredHandoverConfirmationDialogTest.kt` | 测试目标主要是已取消链 | 测试断言 `CONFIRMED`、`等待主管独立激活`、确认按钮和原请求重试；还断言没有 activation row，但仍实际验证确认 | 作为历史 WIP 保全，不作为当前 D14/R09 通过；改造后只保留 read-only/reconciliation fence/身份切换反例 | 新测试需覆盖只读恢复、默认关闭、损坏与跨 LOGIN 拒绝；不得复用旧确认断言 |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aRecoveredConfirmationAndroidBridgeMain.java` | 已取消链；不能作为 D13/D14 合法正向候选 | 启动真实 Spring/HTTP/MySQL，等待 Android 首次产品 POST，比较快照并要求 `recovery_handover_confirmation` 从 0 变 1；`httpPort()` 仅是 harness 修复 | 保留 bridge、输入、日志和快照；不继续运行或把“Android 首次确认”记为恢复通过。若要保留 D13 价值，另建只读恢复事实消费者 | 仅编译/静态范围核对；改成只读入口后，重新执行真实 HTTP/MySQL、末 SQL、并发、重启和快照守恒，不能复用旧结果 |

## 结论与下一步

1. 该 WIP 的核心行为是“灾后接收者确认并等待主管独立激活”，不是 R07 当前的“后台撤回→重新发布→新设备从头作业”。因此它不属于 D06/D07 的候选实现，也不能作为 D13/D14 的正向恢复通过。
2. `RecoveredHandoverConfirmationDisplay`、recovered stage 的原字节/顺序读取，以及 `TemporaryOrdersActivity` 的旧冻结只读跳转，存在 E08/D17 或 R09 只读复用价值；复用前必须切断确认 POST、确认队列、激活等待和旧接收者文案。
3. 本审计未修改源码、数据库或生产开关。已有 run02/run03 build/lint 事实仍仅是构建证据，未转化为 UI L1、D17、D14、E06 L2 或 D18 L3 通过。
4. 下一可执行项是从有效 E06/E07/E08 矩阵选一个不依赖该取消链的最小受影响组合；若继续 UI，先完成只读恢复范围的代码处置和新的“路径→影响层→必跑清单→输入 SHA”登记，再运行定向测试。

## 只读守卫实施回执（2026-10-17）

本次接管后已完成最小产品守卫，范围仍限于取消链的隔离，不构成 D06、D07、D13、D14、D17 或 D18 通过：

- `RecoveredHandoverCardReader` 无论读取成功、旧待处理行存在、身份变化或开发开关关闭，均返回 `RECOVERED_READ`；成功展示只读原始清单，错误保留原资料提示并禁用动作。
- `HandoverViewModel.perform` 对遗留 `RECOVERED_CONFIRM` 直接拒绝；不写确认队列、不调度确认 worker。
- `SyncWorker` 不调用 recovered-confirmation 的 `restorePending`；旧 `TemporaryRecoveredConfirmationWorker`、dialog、fixture、bridge 和历史测试仅保全，不进入现行产品入口。
- `TemporaryOrdersActivity` 的旧冻结入口改为只读核对语义，继续走普通详情页。

验证回执保存于 `.local/goal-20261017-resume/e08-readonly-guard-01/`：`lintDebug`、`assembleDebug`、`assembleDebugAndroidTest` 均返回 0。定向 `RecoveredHandoverCardReaderTest` 为 2/2 失败，失败发生在 fixture 创建时的 Robolectric native SQLite `SQLITE_CANTOPEN`，未到达断言；同一 retained temp/native SQLite 环境下，独立 `WorkRecoveryTest` 也出现 1 个相同失败，故本回执不把单测写成通过。APK 仅为 Debug 构建，未安装、未跑 instrumentation 或真实 HTTP/MySQL。

该结果只证明构建与入口隔离，不证明旧恢复确认功能可用，也不恢复其验收范围。下一步应选择与该取消链无关的 E06/E07/E08 最小组合，并为只读恢复若要继续保留另建新的输入与证据目录。
