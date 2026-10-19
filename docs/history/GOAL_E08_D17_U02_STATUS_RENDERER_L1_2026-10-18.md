# E08/D17/U02 作业台状态语义修复 L1（2026-10-18）

本切片修复正式单与临时单作业台把单条待确认输入隐藏在“待处理 0/无提示”之后的问题。渲染器现在把待确认输入作为主状态行，把队列待落盘作为独立阻断行；持久失败仍显示原记录保留，且不把输入说成已保存。引擎、Room、HTTP、生产开关、取消范围和 `localDeletionAllowed=false` 均未改变。

## 路径与影响

| 路径 | 影响层 | 必跑 | 复用依据 |
| --- | --- | --- | --- |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/WorkRenderer.kt` | 正式单作业台状态文案/首屏高度 | `ScanFirstScreenTest`、`lintDebug`、Debug APK | 当前 WorkUi/Activity 入口；无状态模型或数据库改动 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/TemporaryWorkRenderer.kt` | TEMP 作业台状态文案/首屏高度 | `TemporaryWorkLayoutTest`、高频 TEMP instrumentation（前置可用时） | 当前 TemporaryWorkUi/Activity 入口；fail-closed gate 未触碰 |
| `android/app/src/test/java/com/acrylic/outbound/pda/ui/ScanFirstScreenTest.kt` | 正式单可观察状态断言 | 定向 Robolectric | 原有首屏、队列和持久失败测试 |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/HighFrequencyOfflineDeviceTest.kt` | TEMP 设备候选待确认文案断言 | 隔离 AVD instrumentation | run904 同一高频输入与产品入口 |

源码 SHA-256：`WorkRenderer.kt`=`1884DF75484945227BF03ED3EFBB323D0E745E14FD8702A44AB66015043B66C4`；`TemporaryWorkRenderer.kt`=`F027B69F4B792D8403CD73AA6F44770796B3A9459EF95D37220DE1138633B7BB`；`ScanFirstScreenTest.kt`=`ACFCE5868B68196F910999D55130A50FC2763AC9ADF2C354F9838F49EE64C73F`；`HighFrequencyOfflineDeviceTest.kt`=`639A6A8B778CD645AD9FE2F513957AA53D64FE8B9F4C453744DC8A3B7635A867`。完整输入、进程、监听、APK 与磁盘快照在 `.local/goal-20261018-resume/u02-status-renderer-run01/`。

## 实际验证

- `testDebugUnitTest --tests ...ScanFirstScreenTest --tests ...TemporaryWorkLayoutTest`：首次失败因新增状态行将“核对/异常”推过 509dp；第二次失败因阻断状态未同时保留待确认提示；两份日志和退出码保全。修正状态行合并后第三轮 `BUILD SUCCESSFUL`，32 个定向测试任务通过。
- `lintDebug --no-daemon`：`BUILD SUCCESSFUL`。
- `assembleDebug assembleDebugAndroidTest --no-daemon`：`BUILD SUCCESSFUL`。本轮 APK SHA-256：Debug `7C848DF111B273EB20998FA470BA82F08E2842811B7F8428D2409725387C9621`，AndroidTest `6204579730B0BE34007423327516F35A786BBB1DBA998A931FB5918270B55BEB`。
- 新隔离 `Pixel_6`/`emulator-5620` 安装成功，恢复 run904 可重建 Room tar 后执行 TEMP 高频测试；由于 tar 不包含 `noBackupFiles/session.enc`，`independentDraftSearchEditThenManualConfirmPauseAndReturnPreserveFacts` 在产品入口前以“请先登录”退出，未发生业务写入。首页导航测试同样因无会话导致 `navigation=GONE`，未形成设备通过。原 instrumentation/logcat/安装/ADB 记录在该切片证据目录，不能计 Android L1 业务通过。

## 失败、边界与后续

两次单测失败是本次改动发现的首屏回归，已修复并保留；设备失败归因于隔离 tar 缺少登录会话，未改测试前置、未连接共享服务、未操作 USB M71。当前结果只证明渲染器主机行为、首屏高度和构建/lint，不能替代真实登录绑定、高频 Android 业务 L1、真实 HTTP/MySQL、Chrome、Python、非空迁移、原模拟器或 D18 L3。D17/U02、E06 L2、第8节闸门和总目标继续未完成。

生产默认关闭、取消的换机承接/接收确认链、正式签名、部署、Release、原库/现网和 `localDeletionAllowed=false` 保持不变。后续需要新的隔离登录/准备输入后重跑高频 TEMP 设备断言，不能复用本轮无会话失败作为通过。
