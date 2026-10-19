# E08/D17 UI 接续审计与只读验证（2026-10-18）

本轮接管固定树 `D:/acrylic-pda-outbound`，分支 `codex/development`，HEAD 与开发远端均为 `93710ec5cff979a567654084c80abe089e9a73b7`。唯一写者是当前聊天；继承工作树、`handoff-final01`、run03 构建失败和 recovered-confirmation 失败原件均保留。

## 范围判定

逐路径关系已在 [WIP 审计](GOAL_E08_RECOVERED_CONFIRMATION_WIP_AUDIT_2026-10-18.md) 固化，本轮没有恢复取消链：

| 路径 | 当前判定 | 本轮处置 | 直接验证 |
| --- | --- | --- | --- |
| `TemporaryRecoveredConfirmationWorker.kt` | 取消的接收确认/激活链 | 保留，不接入调度，不发送 POST | 主机编译；静态核对 `SyncWorker` 无 recovered worker 调用 |
| `RecoveredHandoverConfirmationDialog.kt` | 取消的接收确认入口 | 保留，不接回产品导航 | 主机编译；生产 `HandoverActivity` 无 dialog caller |
| `RecoveredHandoverConfirmationDisplay.kt` | R09 只读原件展示 | 保留，只允许原字节和身份围栏展示 | `RecoveredHandoverCardReaderTest` |
| `RecoveredConfirmationDeviceTest.kt` / `RecoveredConfirmationUiFixture.kt` | 取消链的合成 fixture/device test | 保留，不运行旧 run03 | 主机编译；不计产品验收 |
| `E2aRecoveredConfirmationAndroidBridgeMain.java` | 取消链 Android 首次确认 bridge | 保留，不启动，不写确认表 | 静态范围核对；不启动 bridge |
| `Handover*`、`Temporary*` 相关改动 | 普通作业与只读阻断页有效；旧 TEMP 确认/激活仍隔离 | 保留默认关闭和只读入口 | `TemporaryDetailsReaderTest`、默认构建/lint |

该判定依据 D06/D07 的撤回重发政策、D13/D14 的灾后只读恢复边界及 R07/R09 规定；不恢复换机进度承接、多跳、接收者确认或激活链。

## 实际验证

在子进程将 Java 临时目录指向 `.local/d17-ui-host-tmp` 后执行：

```text
gradlew.bat -p android :app:testDebugUnitTest --tests com.acrylic.outbound.pda.ui.RecoveredHandoverCardReaderTest --tests com.acrylic.outbound.pda.temporary.TemporaryDetailsReaderTest --no-daemon --console=plain
gradlew.bat -p android :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --console=plain
```

两条命令均退出 0。当前产物哈希为：

- `android/app/build/outputs/apk/debug/app-debug.apk`: `0D5BD1B5581C16EDF46900263280A67D01DC1BF8CF351F3FB7F0EF9A35B799ED`
- `android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`: `1724F25B2892D7B4F670EC66DFC1B8F1E740994FCD44C64040E3C4A7F84AE548`

`git diff --check` 和 `tools/version_facts.ps1 -Check` 通过。主机验证只覆盖只读恢复卡片和 TEMP 明细 reader；不替代 Android 客户端实际触摸、真实 HTTP/MySQL、Chrome、Python 消费或 D18 全链。

## 设备与失败边界

ADB 只读检查发现一台在线 USB 设备 `22D2910042`，身份、资料和授权均未确认，本轮未安装、未启动或操作 APK。独立 AVD 没有启动；此前 `emulator-5578` 的合成 `CONTINUATION_BLOCKED` 只读 L1 仍以 [既有报告](GOAL_E08_D17_CONTINUATION_BLOCKED_READONLY_L1_2026-10-17.md) 为准。`android-ui-run03/build-failure01.log`、recovered-confirmation run03 的 MySQL/pipe 失败日志及候选失败计数继续保留，旧 run03 不重启。

因此本轮不宣称 D17 UI L1 通过；D17 整体、E06 L2、D18 L3、第 8 节闸门和总目标继续未完成。下一步应为具备独立隔离输入和可审计设备身份后，实际只读 UI 入口复验；取消的确认/激活链仍不入队。
