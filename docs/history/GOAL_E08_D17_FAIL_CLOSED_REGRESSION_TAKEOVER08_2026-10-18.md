# E08/D17 取消确认链 fail-closed 回归（takeover08，2026-10-18）

本次回归只验证 recovered-confirmation WIP 的默认边界和当前 TEMP reader 依赖，未启动
旧 run03、旧 bridge、隔离服务或数据库，也未安装或操作 USB PDA。输入树为
`codex/development`、HEAD `c5c8f9e5851746f71aacf8a856e3cf23bf365146`；工作树的
继承改动保持原样。

## 验证

在唯一写者聊天中执行：

```text
.\gradlew.bat -p android :app:testDebugUnitTest --tests com.acrylic.outbound.pda.ui.RecoveredHandoverCardReaderTest --tests com.acrylic.outbound.pda.temporary.TemporaryDetailsReaderTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --console=plain
```

结果：`BUILD SUCCESSFUL in 42s`；`RecoveredHandoverCardReaderTest` 2/0/0/0，
`TemporaryDetailsReaderTest` 6/0/0/0，Debug/AndroidTest 构建和 `lintDebug` 通过。
第一次使用裸 `gradlew.bat` 的 PowerShell 调用在 Gradle 启动前退出，随后用显式
`.\gradlew.bat` 重试；该命令解析错误不计产品或测试失败。

| 产物/报告 | SHA-256 |
| --- | --- |
| `android/app/build/outputs/apk/debug/app-debug.apk` | `8225CD581166F1F727FACE72025C6F3076B6244D8A1B80FFA3BA198B7C18CEE7` |
| `android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | `76EA705A2C485FF47E039844FA3D7578B50A31CBE18A7D7F51D20D319DDBCDD5` |
| `android/app/build/reports/lint-results-debug.xml` | `A011FFE509C56AB07398E1E149A74040AB8983D4CD8C8E6A22FDD3D942209D5F` |
| `TEST-com.acrylic.outbound.pda.ui.RecoveredHandoverCardReaderTest.xml` | `FCAFBDAE44D5517A24B516D0690DDF4B6C1527992291554153A06199D9BC878A` |

## 边界

这只是主机单测、构建和 lint 的直接回归。它不证明真实 Android 触摸、真实
HTTP/MySQL、Chrome、Python 消费、非空迁移、原 PDA、现场身份、外置保管或 D18 全链；
不计 D06/D07、D13/D14、D17 整体、E06 L2、D18 L3、第8节闸门或总目标通过。
高频首页/扫码链仍等待新的隔离准备、登录和可核验模拟器输入；三次旧启动失败原件继续保全。
