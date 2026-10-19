# E08/D17 recovered-confirmation WIP 逐路径审计（takeover08，2026-10-18）

本次审计在 `D:/acrylic-pda-outbound`、分支 `codex/development`、HEAD
`c5c8f9e5851746f71aacf8a856e3cf23bf365146` 完成。继承工作树、handoff-final01、run03
失败原件和旧报告均未还原、覆盖或重排。本清单按 D06/D07 撤回重发和 D13/D14
灾后恢复边界核对；R07 已取消的换机进度承接、多跳、接收者确认/激活链不恢复。

## 判定与处置清单

| 路径 | 范围 | 实际调用事实 | 处置建议 | 直接受影响验证 |
| --- | --- | --- | --- | --- |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryRecoveredConfirmationWorker.kt` | 取消的接收确认/激活 | 仅 DEBUG+开关开启时从同 LOGIN pending 行排队；`SyncWorker` 无调用 | 保留继承 WIP，不接入调度，不 POST | 默认构建/lint；默认启动重启零排队/零 POST |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverConfirmationDialog.kt` | 取消的确认入口 | 只有组件回调，生产导航无 caller | 保留组件和历史单测，不接回导航 | `rg` 无产品 caller；只读页无确认触点 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverConfirmationDisplay.kt` | R09 原件只读 | 解析原 MANIFEST/PAYLOAD、顺序和 RETAINED 清单，不联网不写库 | 保留，只读展示与确认解耦 | 原字节/顺序/损坏/身份代次边界测试 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverCardReader.kt` | R09 只读候选 | DEBUG+开关开启才读取恢复 stage；校验失败返回不可用卡片 | 保留只读，不连接扫描、确认或激活 | `RecoveredHandoverCardReaderTest`；默认开关矩阵 |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/RecoveredConfirmationDeviceTest.kt` | 取消链合成设备测试 | `@Ignore`；MockWebServer/开发签名覆盖确认 POST 与换 LOGIN | 保留测试和失败原件，不重跑旧 run03 | 仅编译；不计 PDA/HTTP/MySQL/现场验收 |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/RecoveredConfirmationUiFixture.kt` | 取消链 fixture | 写合成 touch/receipt，转发 `recovered-confirmation` POST | 保留输入和失败日志，不冒充真实回执 | 仅编译；确认表和业务表守恒 |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aRecoveredConfirmationAndroidBridgeMain.java` | 取消链 bridge | 隔离 Spring/HTTP/MySQL 写确认表；不属于现行恢复只读链 | 保留 bridge/日志，不启动 | 静态范围核对；确认表不应被只读恢复改变 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/HandoverActivity.kt`、`HandoverViewModel.kt` | 普通交接有效，确认分支取消 | `RECOVERED_READ` 只读；`RECOVERED_CONFIRM` 明确拒绝；无 dialog 导航 | 保留 fail-closed，不新增确认入口 | 默认 UI 构建/lint；只读入口矩阵 |
| `android/app/src/main/java/com/acrylic/outbound/pda/PdaApplication.kt`、`sync/SyncWorker.kt` | 依赖注入/同步边界 | 暴露依赖但启动不排队 recovered worker；旧 TEMP 仍受开关保护 | 保留保护，不塞入取消链 | 默认启动/重启；不同 LOGIN 零业务写入 |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryRecoveredHandoverRepository.kt` 及 `*Access/*Verifier/*Payload/*Migration` | R09 原件读取 | 绑定原字节、身份、代次、本机持钥，只写本机恢复暂存 | 保留只读读取；不创建 preparation/confirmation/activation | 非空 Room、签名/身份/代次/损坏/重复读取守恒 |
| `TemporaryRecoveredConfirmationRepository.kt`、`TemporaryRecoveredConfirmation.kt`、`*Command/*ReceiptVerifier/*Migration` | 取消的确认写链 | 可持久化意图并 POST，但当前 UI 不调用 | 保留模型/迁移/失败证据，不接只读流程 | default-off/静态无 caller；不计 D06/D07/D13/D14 |
| `TemporaryHandoverConfirmationWorker.kt`、`TemporaryHandoverActivationRepository.kt`、相关 `Download/Work/Baseline` | 取消的 TEMP 确认/激活/换机承接 | 仅开发开关可确认、安装 retained 并开始扫描 | 保留继承源码与失败证据，不接 R07/R09 | 默认构建/lint；入口矩阵保持关闭 |

## 结论与证据

- 当前 `rg` 调用矩阵确认没有生产导航调用 confirmation dialog，也没有 `SyncWorker`
  调用 recovered confirmation worker；只读入口仅产生 `RECOVERED_READ`。
- 取消链不恢复、不重启旧 run03；`.local/goal-20261017-resume/handoff-final01/`
  的 `dirty-final.json`、`dirty-final.zip`、`tracked-final.patch`、
  `inherited-protection-final.json` 及旧 MySQL/pipe/build 失败原件继续保全。
- 只读验证可复用 `RecoveredHandoverCardReaderTest`、`TemporaryDetailsReaderTest`、
  默认 Debug 构建/lint；这些不能替代原 PDA、真实 HTTP/MySQL、现场回执或 D18 L3。
- 该审计不构成 D06/D07、D13/D14、D17、E06 L2、D18 L3、第8节闸门或总目标通过。
