# E08/D17 TEMP fail-closed 修复（failclosed-fix-run01，2026-10-18）

本切片承接 `GOAL_E08_D17_FAIL_CLOSED_REGRESSION_TAKEOVER08_2026-10-18.md`，只处理
取消的 TEMP 接收/激活 WIP 在默认关闭状态下仍可能被过期 action 触发的缺口。R07 已取消的
换机进度承接、多跳、接收者确认/激活链没有恢复；本切片不启动旧 run03，不安装或操作 USB PDA，
不连接原库或现网。

## 输入与写权

- 树：`D:/acrylic-pda-outbound`，分支 `codex/development`。
- 起始 HEAD 与远端均为 `c5c8f9e5851746f71aacf8a856e3cf23bf365146`；继承的未提交/未跟踪改动保持原样。
- 唯一写者为当前聊天；共享 MySQL、Vite/Node、Chrome、ADB server 只观察未停止。
- 起始全量路径与 SHA 快照：`.local/goal-20261018-resume/failclosed-fix-run01/input-snapshot.json`。
- 运行清单和旧产物基线：`.local/goal-20261018-resume/failclosed-fix-run01/run-manifest.json`；旧 Debug/AndroidTest APK 与 lint XML 的 SHA 先行保全。
- 收口快照：`.local/goal-20261018-resume/failclosed-fix-run01/final-state.json`（含逐路径 SHA、监听、ADB、进程和 C/D/E 余量）。

## 改动路径与范围

| 路径 | 影响层 | 变化 | 直接验证 |
| --- | --- | --- | --- |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/TemporaryHandoverActionGate.kt` | Android UI policy | 集中判断 Debug 开发开关；默认关闭时拦截全部 `TEMP_*` action | `TemporaryHandoverActionGateTest`、lint |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/TemporaryHandoverCardReader.kt` | Android UI reader | 默认关闭返回 `TEMP_DISABLED` 不可操作卡片，避免 `REFRESH` 误入正式接收路径 | lint；reader 既有回归受 SQLite 环境失败影响 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/HandoverViewModel.kt` | Android UI command | 在异步调度和副作用前拒绝关闭状态的 TEMP action；保留 `RECOVERED_CONFIRM` 明确拒绝 | `TemporaryHandoverActionGateTest`、lint |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/HandoverActivity.kt` | Android UI navigation | 对话框和 TEMP 导航前再次拦截过期 action | lint；未执行设备触摸 |
| `android/app/src/test/java/com/acrylic/outbound/pda/ui/TemporaryHandoverActionGateTest.kt` | Android host unit test | 覆盖默认关闭的 stale action、无 busy 调度、无 WorkManager 确认任务及开发开关矩阵 | `rerun02` 通过 |

## 验证结果

1. `.\gradlew.bat -p android :app:testDebugUnitTest --tests com.acrylic.outbound.pda.ui.TemporaryHandoverActionGateTest --no-daemon --console=plain`：`rerun02`，`BUILD SUCCESSFUL`，测试通过；日志为 `.local/goal-20261018-resume/failclosed-fix-run01/rerun02/logs/unit-gate.log`。
2. `.\gradlew.bat -p android :app:lintDebug --no-daemon --console=plain`：`rerun03-lint`，`BUILD SUCCESSFUL`；日志为 `.local/goal-20261018-resume/failclosed-fix-run01/rerun03-lint/logs/lint.log`。
3. `git diff --check`：通过。
4. 首次组合命令同时运行新测试、两个既有 reader 测试和 lint 时，10 项中 6 项失败：5 项既有 reader 用例命中 `SQLiteCantOpenDatabaseException`，新 gate 用例的首版断言在主线程读取 Room。首轮完整 XML/HTML/lint 与日志保全于 `.local/goal-20261018-resume/failclosed-fix-run01/failed-test-output/`；首轮日志为 `logs/gradle-test-and-lint.log`。
5. 修正新测试为 IO 上下文且移除无关 Room 读取后，gate 测试通过；两个既有 reader 类串行 `rerun04-readers` 仍全部命中同一 `FullSyncOpenHelper`/Robolectric `SQLiteCantOpenDatabaseException`，日志保全于 `.local/goal-20261018-resume/failclosed-fix-run01/rerun04-readers/logs/readers.log`。按既有同根因失败规则暂缓，不归因于本次 fail-closed 代码，不再机械重跑。

## 环境与未验

- ADB 只读枚举到 USB M71 `22D2910042`，没有 emulator；未执行安装、instrumentation、Android 触摸或真实 HTTP/MySQL。
- 既有 Debug APK、AndroidTest APK 和 lint 基线保持原 SHA；本切片没有运行 assemble，避免覆盖继承 APK。
- 未执行 Chrome、Python 消费、非空迁移、原 PDA/现场身份、外置介质、业务回执、D18 全链；不计 D17 整体、E06 L2、D18 L3、第8节闸门或总目标完成。
- 共享监听和隔离数据库未停止或连接；七类生产决定、TEMP 生产归档、`localDeletionAllowed=false`、正式签名、部署、Release 和 `main` 边界保持关闭。
- 本切片结束后尝试删除已结束的 run-local Gradle/Robolectric `tmp`/`java-tmp` 目录；Windows 执行策略拒绝递归删除，未用替代 shell 绕过，临时目录保留并在收口快照中登记。日志、失败 XML/HTML、输入快照、APK 基线和源码均未清理。

## 复用依据与下一步

- 取消范围、调用事实和只读保留边界复用 `history/GOAL_E08_RECOVERED_CONFIRMATION_WIP_AUDIT_2026-10-18_TAKEOVER08.md` 及其 SHA/调用矩阵。
- 既有 reader 的历史通过不能覆盖本次 SQLite 环境失败；`GOAL_E08_D17_FAIL_CLOSED_REGRESSION_TAKEOVER08_2026-10-18.md` 只作为前序范围依据，不替代本次定向 gate/lint 结果。
- 下一独立切片仍需新的隔离高频 UI 输入、登录绑定和可核验模拟器；具备真实 PDA/现场身份时才补 D06/D07，具备真实外控介质/保管回执时才补 O05。不得恢复取消链或重复旧 run03。
