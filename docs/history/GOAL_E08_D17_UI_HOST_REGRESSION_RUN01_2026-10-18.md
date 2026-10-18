# E08/D17 UI 主机回归 run01

本批只验证当前工作树中已受影响的 Android UI 主机单测，确认继承 WIP 与 TEMP 明细页改动没有引入主机回归。未启动 HTTP、MySQL、模拟器或 USB PDA，也未修改产品源码。

## 范围与验证

| 路径 | 影响层 | 直接验证 | 处置 |
| --- | --- | --- | --- |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/HandoverActivity.kt` | 普通交接导航与取消确认入口隔离 | `OrderCardsTest`、恢复卡片测试通过 | 保留继承 WIP；不接回取消的确认/激活链 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/HandoverViewModel.kt` | 只读恢复动作与 TEMP 开始动作 | `RecoveredHandoverCardReaderTest`、对话框测试通过 | 默认关闭、只读边界保持 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverCardReader.kt` | 原资料只读展示与身份围栏 | `RecoveredHandoverCardReaderTest` 通过 | 仅保留只读展示，不发送确认 POST |
| `android/app/src/main/res/layout/activity_temporary_details.xml` | TEMP 明细页 IME/大字布局 | `TemporaryDetailsReaderTest` 通过；设备 L1 复用既有 run09/run10 报告 | 不重复运行旧设备组合 |

命令：

```text
gradlew.bat -p android :app:testDebugUnitTest --tests com.acrylic.outbound.pda.ui.OrderCardsTest --tests com.acrylic.outbound.pda.ui.RecoveredHandoverCardReaderTest --tests com.acrylic.outbound.pda.temporary.TemporaryDetailsReaderTest --tests com.acrylic.outbound.pda.ui.RecoveredHandoverConfirmationDialogTest --no-daemon --console=plain
```

结果：Gradle exit `0`，`BUILD SUCCESSFUL`，32 个任务均成功或为 up-to-date；原始日志位于 `.local/goal-20261018-resume/ui-host-run01/test.log`，SHA-256 `8AFEEA4EA64DC89596F9D92B387A0A206A2219DC08DE9F26CC70F709598D0B13`。

## 边界

该批是作者同一会话的主机验证，不包含真实 Android 设备行为、真实 HTTP/MySQL、Chrome、Python 消费、非空迁移、原模拟器恢复或现场身份。合成 recovered fixture 只验证取消链不会被重新接入；不能计 D17 整体、E06 L2、D18 L3、第 8 节闸门或总目标完成。高频首页的独立 AVD 登录前置仍按三次同根因失败规则暂缓。
