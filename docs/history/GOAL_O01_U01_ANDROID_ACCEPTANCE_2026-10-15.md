# O01/U01 Android 交互定向验收（2026-10-15）

本记录收口 O01/U01 的一段 Android 主机定向验证：扫码首屏、已扫码包调整、临时产品搜索、提交下一动作，以及临时作业阻断态页面。它是开发切片证据，不代替真机、U01-U04 完整验收、E06 L2、D18 L3 或总目标完成。

## 输入与影响映射

- 工作目录：`D:/acrylic-pda-outbound`；分支：`codex/development`；验证输入基线：`6a9a51115277884b28502c8bbc9096e660584ac6`，工作树继承的其他未提交改动全部保留。
- 改动路径：`android/app/src/main/java/com/acrylic/outbound/pda/ui/TemporaryWorkRenderer.kt`。影响层为 Android 临时作业首屏和阻断态渲染；具体修复为空车号或无草稿时仍显示“临时作业”，并让候选/阻断状态保留确认/恢复入口可见。
- 直接受影响测试：`WorkAdjustmentTest`、`TemporaryAdjustmentTest`、`AdjustmentLayoutTest`、`ScanFirstScreenTest`、`SubmissionActionTest`、`TemporaryProductSearchTest`、`TemporaryWorkLayoutTest`、`WorkDetailsLayoutTest`。
- 证据目录：`.local/goal-20261015/o01-u01-android01/` 至 `o01-u01-android08/`。首次错误入口、首轮断言失败、临时目录未创建的运行器失败均保留；后续使用 D 盘 Java 临时目录完成复验。

## 实际结果

| 验证项 | 结果 | 证据 |
| --- | --- | --- |
| O01/U01 定向 Android 单测 | 26/26 通过，0 失败，0 错误 | `.local/goal-20261015/o01-u01-android07/run.log`、`result.json` |
| `TemporaryWorkLayoutTest` 修复后复验 | 3/3 通过 | `.local/goal-20261015/o01-u01-android06/run.log`、`result.json` |
| Android `lintDebug` | 通过 | `.local/goal-20261015/o01-u01-android08/lint.log` |
| Debug APK `assembleDebug` | 通过 | `.local/goal-20261015/o01-u01-android08/assemble.log` |
| APK | `android/app/build/outputs/apk/debug/app-debug.apk`，14,127,080 bytes | SHA-256 `F45FE0BEE5F555C9F9E45F109B6A56060799E70FB6AB36169AF01AF2D54583CB` |

定向运行期间未启动本项目服务，目标端口无监听；共享 MySQL 仅保持既有 `3306` 监听。执行后记录 C 盘约 72.04 GiB、D 盘约 10.59 GiB 可用。`git diff --check` 对本次源码路径通过。

## 限制与后续

本轮没有执行 instrumentation、真机导航、系统栏/实体键/键盘走查、长时扫码队列、真实 HTTP/MySQL 业务组合或 Chrome。原生崩溃/恢复、U01-U04 完整证据、E06 L2、D18 L3 及总目标仍未完成；生产决定能力和 `localDeletionAllowed=false` 保持关闭。下一步复用本轮输入继续受影响组合核对，随后推进剩余 D/O/U 闸门。
