# E08 / D17 `CONTINUATION_BLOCKED` 只读明细 L1（2026-10-17）

本切片修复 TEMP 前驱记录页面在 `CONTINUATION_BLOCKED` 状态仍显示“取消本机零作业草稿”和“调整不可用”的入口问题，并在独立 Android 模拟器上复验页面行为。取消的接收者确认、激活、多跳和换机进度承接链没有恢复。

## 改动与范围

| 路径 | 影响层 | 必跑清单 | 复用依据 |
| --- | --- | --- | --- |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/TemporaryDetailsActivity.kt` | TEMP 明细页只读状态与入口可见性 | `TemporaryDetailsReaderTest`；`lintDebug`；`assembleDebug`；`assembleDebugAndroidTest`；独立 instrumentation | 既有 `HANDOVER_BLOCKED` 只读实现；`CONTINUATION_BLOCKED` 由 `TemporaryDraftCancellation` 拒绝，前驱输入为 `.local/goal-20261016-resume/temporary-details01/host04-prefix-input/` |
| 本报告、进度索引、看板当前区 | 验收事实、失败和接续 | 文档核对、版本事实检查 | 设备证据目录 `.local/goal-20261017-resume/e08-ui-l1-20261017-01/` |

`render` 现在把 `HANDOVER_BLOCKED` 与 `CONTINUATION_BLOCKED` 都映射为只读。两种状态继续提供原记录读取、筛选、分页、定位、重读、应急导出和返回；取消、调整和新作业入口隐藏。普通 `DRAFT`/`SCANNING` 状态的既有资格判定未改变。

## 主机验证

以下命令在同一轮执行并退出 0：

```text
.\gradlew.bat -p android :app:testDebugUnitTest --tests com.acrylic.outbound.pda.temporary.TemporaryDetailsReaderTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

完整输出保存在 `.local/goal-20261017-resume/e08-ui-l1-20261017-01/readonly-guard-build.log`。本轮 Debug 产品 APK `android/app/build/outputs/apk/debug/app-debug.apk` 的 SHA-256 为 `0D5BD1B5581C16EDF46900263280A67D01DC1BF8CF351F3FB7F0EF9A35B799ED`，测试 APK `android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` 的 SHA-256 为 `1724F25B2892D7B4F670EC66DFC1B8F1E740994FCD44C64040E3C4A7F84AE548`；均为 Debug 签名，不是正式签名或 Release。

## 独立 Android L1

独立 AVD 为 `emulator-5578`，数据目录为 `.local/goal-20261017-resume/e08-ui-l1-20261017-01/pixel6-data`。输入来自既有 `host04-prefix-input` 合成数据库、合成会话和 `syntheticOnly` 身份，不代表原 PDA、原身份或现场回执。测试 APK 覆盖安装后运行：

```text
com.acrylic.outbound.pda.TemporaryDetailsPrefixDeviceTest#blockedPredecessorRemainsReadOnlyThroughFilterLocateAndIdentityChange
```

`blocked-readonly-fix-03` 返回 `OK (1 test)`。它实际读取前驱页面，完成筛选、完整条码定位、跨身份拒绝、恢复身份、返回和安全区文本检查。回拉证据为：

- `readonly-guard-instrument-03.log`
- `readonly-guard-evidence-blocked-readonly-fix-03.tar`
- `readonly-guard-db-blocked-readonly-fix-03.tar`
- 解包后的 `blocked-prefix.txt` 与 `blocked-prefix.png`

修复后页面文本不再包含“取消本机零作业草稿”或“调整不可用”；修复前的 `prefix-current-200b/blocked-prefix.txt` 同时包含两项，原件保留。复验前后 `outbound-pda.db` SHA-256 均为 `DCA73BCD052673CA3292980AF2DB8A70C4AF654772536702DEAB7388900A07F9`，没有业务数据库字节变化。

本轮还保留了两次运行器/环境失败：首次 instrumentation 的 `failed to attach` 在 `readonly-guard-instrument.log`，第二次因独立 AVD Launcher ANR 导致 Espresso 无焦点在 `readonly-guard-instrument-02.log`；它们没有被改写为产品失败，也没有清除既有失败证据。处理 ANR 后第三次测试才通过。

测试完成后已停止 `emulator-5578`。`environment-before-stop.json`、`environment-after-stop.json` 和 `emulator-5578-stop.log` 记录了目标 AVD 的启动/停止、共享 MySQL、共享 ADB、原模拟器监听和 C/D/E 盘余量；共享 `emulator-5564`、真机、MySQL 与 ADB 服务未停止或重置。

## 边界与未验

这是合成输入上的 Android UI L1 开发备份，只证明该状态的页面入口语义和只读导航。没有连接共享 MySQL、真实 HTTP、Chrome、原模拟器或正式身份，没有执行非空迁移组合、Python 消费或恢复全链。D17 整体、E06 L2、D18 L3、第 8 节全部闸门和总目标仍未完成。
