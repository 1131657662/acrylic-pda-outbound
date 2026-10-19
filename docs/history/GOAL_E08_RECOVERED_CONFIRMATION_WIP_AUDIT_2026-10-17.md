# E08/D17 recovered-confirmation WIP 逐路径范围审计（2026-10-17）

本单元完成接管后要求的有界审计。输入树为 `D:/acrylic-pda-outbound`，分支为 `codex/development`，审计时 HEAD/远端均为 `e3cebea70eb89576fc014c20128ea4940f3d273b`；`version_facts.ps1 -Write` 与 `-Check` 均通过。完整继承 dirty、未跟踪文件、原字节、进程/监听和失败资料继续由 `.local/goal-20261017-resume/handoff-final01/` 及本轮 `resume-20261017-124637/` 保全。本报告只做范围判定，不启动旧 run03，不连接共享 MySQL/原库，不修改业务数据库。

## 判定依据

- `docs/BUSINESS_POLICY_R07.md` 第1节：后台有权撤回坏机已领取但未成功订单，随后重新发布，新设备从头作业；自动换机交接、坏机进度承接、多跳和专用接收者准备/确认/激活不再开发。
- `docs/AUTONOMOUS_DEVELOPMENT_GOAL_2026-10-07.md` 第4、5、6、7、8节：D06/D07 必须验证撤回重发与新设备独立作业；D13/D14 只保留可信恢复、原ID/原件/回执与截点重建，不能把取消范围恢复为门槛；第8节全部闸门仍未满足。
- `docs/parallel/R09/RECOVERED_RECIPIENT_DOWNLOAD.md` 与 `RECOVERY_CONTROL_CONTRACT.md`：恢复态仅允许本人真实 LOGIN、已保存准入/资格/准备包下的原正文读取；不创建确认、激活、首轮、扫描、释放或删除能力，缺可信材料保持阻断。
- 服务端 `outbound.handover.recovered-confirmation.enabled` 默认关闭。隔离 fixture、开发签名和 MockWebServer 不能冒充原 PDA、原身份、现场回执或生产验收。

## 逐路径清单

| 路径 | 范围判定 | 实际调用事实 | 处置建议 | 直接受影响验证 |
| --- | --- | --- | --- | --- |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryRecoveredConfirmationWorker.kt` | 已取消的接收者确认/激活链；不是 D06/D07 正向实现 | `doWork()` 在 DEBUG 且 `RECOVERED_HANDOVER_DEVELOPMENT` 时调用 `temporaryRecoveredConfirmations.resume()`；`restorePending()` 扫描 pending 行并按同一 LOGIN 调度唯一 Work | 保留继承 WIP 与失败证据，禁止现行入口调度；若未来需要 R09，仅改为只读事实校验 worker，不发送确认 POST | 默认构建、lint、`SyncWorker` 静态调用检查；新只读消费者应断言无队列/无 POST/无业务写入 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverConfirmationDialog.kt` | 已取消；“已核对，确认接收”直接触发旧确认 | AlertDialog 按钮执行 `confirm()`，文案等待接收确认，带 `FLAG_SECURE` | 只保全源码和单测，不接回 `HandoverActivity`；不把大字/滚动通过写成当前功能验收 | 编译/lint；确认入口不存在；若重构须验证只读显示、返回和原资料保全 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverConfirmationDisplay.kt` | 仍有效的 R09 只读恢复证据候选 | 从 `MANIFEST`/`PAYLOAD` 原字节读取车号、柜号、RETAINED 清单和扫描条码，保持原顺序，不写数据库 | 保留并与 dialog/worker 解耦；可作为只读恢复资料展示，不得产生资格或激活 | 原字节/顺序、非 RETAINED、损坏和跨 LOGIN 拒绝；Android 只读入口实际回读 |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/RecoveredConfirmationDeviceTest.kt` | 已取消链；不是原 PDA 或原身份现场证据 | 导入开发私钥，执行确认 POST、断首响应、重试、CONFIRMED、重启和换 LOGIN | 保留测试、输入和失败原件；不重跑旧 run03，不计 UI L1/L2/D14 | 仅编译/静态范围核对；未来新测试必须无确认 POST、无激活、无写入 |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/RecoveredConfirmationUiFixture.kt` | 已取消链的合成 fixture | MockWebServer 转发 `/api/v1/pda/handovers/{caseId}/recovered-confirmation`，导入开发证书并写 touch/receipt/exchange 证据 | 保留原 fixture 和私有输入，不作为业务输入或原回执；不得复用旧端口/旧数据库 | 旧 fixture 不运行；任何新只读 UI 需新隔离输入、新证据根和真实产品 GET |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aRecoveredConfirmationAndroidBridgeMain.java` | 已取消链；不能作为 D13/D14 正向恢复 | 启动真实 Spring/HTTP/MySQL 后等待 Android 首次确认 POST，并要求 `recovery_handover_confirmation` 从0变1 | 保留 bridge、快照和失败日志；不重启，不把“Android 首次确认”记成恢复通过；如需 R09 价值另建只读 bridge | 仅编译/静态范围核对；新 bridge 需真实 HTTP/MySQL、快照守恒、无确认表写入 |
| `android/app/src/main/java/com/acrylic/outbound/pda/PdaApplication.kt` | 取消链的遗留 repository 注入；只读恢复基础设施可保留 | 仍暴露 confirmation/activation/recovered repositories 以保护继承 WIP | 保留，不扩展产品入口；默认开关保持关闭 | Debug/staging 构建与 lint；无生产能力开放 |
| `android/app/src/main/java/com/acrylic/outbound/pda/sync/SyncWorker.kt` | 现行 E08/D17 保护边界 | 当前不再调用 `TemporaryRecoveredConfirmationWorker.restorePending`；仍恢复其他有效队列 | 保留当前隔离守卫；不得重新加入旧 confirmation 调度 | 编译/lint；默认启动、不同 LOGIN、重启均无 recovered-confirmation POST |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/HandoverViewModel.kt` | 普通交接 UI 有效；`RECOVERED_CONFIRM` 已取消 | `perform()` 对 `RECOVERED_CONFIRM` 直接失败；`RECOVERED_READ` 只调用 recovered repository 的 read；TEMP/FORMAL 动作仍按现行链路 | 保留只读拒绝和普通导航；不得把 recovered row 转为 D06 撤回重发/新机作业 | `RecoveredHandoverCardReaderTest`（如 Robolectric 环境可用）、构建/lint、实际只读卡片入口 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/HandoverActivity.kt` | 通用导航有效；旧 recovered dialog 入口取消 | 只对 TEMP/FORMAL/RECALL 进入产品页面；不再为 recovered row 打开确认 dialog | 保留通用导航和返回；不新增接收确认页面 | 现有 Handover/TEMP/RECALL 导航、窄屏/大字；不验确认成功 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverCardReader.kt` | 只读恢复展示有效；确认/激活取消 | 完成态统一 `RECOVERED_READ`；遗留 `RECOVERED_CONFIRM` 由 ViewModel 拒绝，保留资料错误默认阻断 | 保留原件读取；禁止恢复确认 POST、激活等待或旧接收者文案 | 原 manifest/payload 顺序、损坏保全、身份变化、零 recovered confirmation 写入 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/TemporaryOrdersActivity.kt` | E08/D17 有效，只读阻断入口 | `HANDOVER_BLOCKED` 文案为“旧冻结原件 · 只读核对”，导航到 `TemporaryDetailsActivity`；`CONTINUATION_BLOCKED` 保持待确认阻断 | 保留；不得改成取消/调整、开轮或接收确认 | `TemporaryDetailsReaderTest`、真实 Android blocked/details 入口、导出/分页/返回；前后 DB 字节守恒 |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryDetailsActivity.kt` 与 `TemporaryDetailsReader.kt` | `HANDOVER_BLOCKED`/`CONTINUATION_BLOCKED` 的只读资料核对属于 E08/D17/R09 候选 | 读取受身份与 baseline 原件校验约束，在事务快照中分页/筛选/定位；当前两状态隐藏取消/调整入口，保留导出、重读、返回 | 保留只读行为；不从此页连接 `start`、download-ack、confirmation 或 activation | 主机 `TemporaryDetailsReaderTest`、Debug/APK/lint、独立 AVD instrumentation；零业务写入 |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryHandoverBaseline.kt`、`TemporaryHandoverWork.kt`、`TemporaryHandoverDownload.kt`、`TemporaryHandoverConfirmationRepository.kt` | 旧交接确认/激活链；不属于 R07 坏机撤回重发 | baseline 读取既有 stage/confirmation/activation 原件；work/download/confirmation 受 DEBUG 开关保护，普通入口不调度 | 保留继承源码与测试，不从只读页面重连旧链 | 编译/lint；默认开关、无权/身份变化、尾 SQL/回滚路径按历史输入复用，不算 D06/D07 |
| `android/app/src/test/java/com/acrylic/outbound/pda/temporary/TemporaryRecoveredConfirmationRetryTest.kt`、`TemporaryRecoveredConfirmation*Test.kt` | 主要覆盖已取消确认/激活行为 | 断言 `CONFIRMED`、确认回执、重试和旧资格拒绝；仍会实际验证确认 repository | 作为历史 WIP 保全；不得改名包装为当前恢复验收 | 仅编译/定向静态核对；新增只读测试替代确认断言 |
| `android/app/src/test/java/com/acrylic/outbound/pda/ui/RecoveredHandoverCardReaderTest.kt`、`RecoveredHandoverConfirmationDialogTest.kt` | 前者可转只读候选；后者已取消 | 前者断言 `RECOVERED_READ`/无本地 confirmation；后者断言确认按钮与大字 dialog | 前者保留定向验证价值；后者保全不作为当前产品门 | Robolectric 若遇 `SQLITE_CANTOPEN` 记录运行器失败；通过必须无 POST/无写入 |
| `server/src/main/resources/application.yml` 与 `V82__recovery_handover_confirmation.sql` | 默认关闭和受限恢复证据表仍是历史兼容边界 | 开关默认 false；confirmation 表不能替代普通 authorization/activation 外键 | 不删除迁移、不开放开关；R09 只读消费者必须保持围栏 | 非空迁移、default-off、有权/无权和表守恒；不得以合成确认行当恢复通过 |

## 处置结论

1. worker、dialog、device test、UI fixture、bridge 以及 confirmation repository 的核心确认 POST/确认队列/激活等待全部属于已取消范围，继承源码和失败证据保留但不进入当前候选，也不重启旧 run03。
2. `RecoveredHandoverConfirmationDisplay`、`RecoveredHandoverCardReader`、`TemporaryOrdersActivity` 和 `TemporaryDetails*` 的原件只读、顺序、身份校验、分页/定位、导出/返回具备 E08/D17 或 R09 只读复用价值；复用的硬门槛是零确认 POST、零激活、零业务写入和明确恢复围栏。
3. 本审计没有把任何构建、fixture、模拟器或合成身份写成业务通过。当前 D17 整体、E06 L2、D18 L3、第8节闸门和总目标均保持未完成。
4. 下一独立切片应从 E06/E07/E08 有效矩阵选取与取消链无关的真实 HTTP/MySQL、Android、Python、非空迁移组合；无 Web 影响时在验收单中说明 Chrome 不适用，不用旧确认链补齐闸门。

## 审计输入 SHA

| 路径 | SHA-256 |
| --- | --- |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryRecoveredConfirmationWorker.kt` | `B6ACB4260721CD4C9CC02D66DB4023002FEC0C399CEE07613CA4A8BA65AC3DB3` |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverConfirmationDialog.kt` | `90A4F08EF5A77A0C1A51089E5D8DDA69095F4368BA577FA7CF8AD26D44DF3EFE` |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverConfirmationDisplay.kt` | `D005A763E1D2B2224D3A1918D45FF71A77158C009B7995CFC0EACED86D3F2963` |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/RecoveredConfirmationDeviceTest.kt` | `598253C5474336C49C09F54313D223982241C9FB8D705AF6920F5DFD989D99EF` |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/RecoveredConfirmationUiFixture.kt` | `9550F4595B23E91688C0106C2BB01BE8643FB4E96CC670EEC7EE901AE56B36C2` |
| `android/app/src/test/java/com/acrylic/outbound/pda/temporary/TemporaryRecoveredConfirmationRetryTest.kt` | `F5D0E17146957A5B92794CAC60AFBB19A559F9E17A9349B1607C1F889EA5679E` |
| `android/app/src/test/java/com/acrylic/outbound/pda/ui/RecoveredHandoverCardReaderTest.kt` | `112DCE92A5AC65DA6F85AD59FB5DC49459151302F15773CF9D08235503F9CE9F` |
| `android/app/src/test/java/com/acrylic/outbound/pda/ui/RecoveredHandoverConfirmationDialogTest.kt` | `70FE50A48FF9934F52CBA37539FB8B0B0BCB429CAFA0AA6F7121DB58A1DA0B10` |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aRecoveredConfirmationAndroidBridgeMain.java` | `F768B60270716709AF8A19B2142497FD41D115C36CEA092E136C45F48C660402` |

## 直接验证与限制

- 本轮只读接管核对：分支/HEAD/远端、97 条实际 dirty、最终快照、写权、进程/监听、ADB 设备、共享 MySQL、磁盘余量；无共享数据库写入、无共享模拟器变更。
- 已有 `CONTINUATION_BLOCKED`/`HANDOVER_BLOCKED` 只读切片的主机测试、lint、Debug 构建和独立 AVD 证据继续绑定于既有报告；本报告不重复运行，不改写其结论。
- 交接中保留的 `build-failure01.log`、`mysqld*.log`、失败库/快照及 `dirty-final.*` 不删除、不覆盖；当前 Gradle/Kotlin daemon 只作环境观察，未发现业务重型任务。
