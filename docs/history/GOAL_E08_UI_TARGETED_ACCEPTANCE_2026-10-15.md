# E08 UI 定向验收记录（2026-10-15）

本页固定记录 `e08-ui-targeted-05` 的一次定向 Android UI 开发验证。它补充 E08 页面开发备份的最新输入和可复核结果，不把主机测试或 lint 结果扩写为 U01-U04、真机、E06 L2、D18 L3 或总目标通过。

## 输入与影响映射

- 工作目录为 `D:/acrylic-pda-outbound`，分支 `codex/development`，被测 HEAD 为 `9f1d473652a422d516d0d327039f43b6f6d98c2a`。结果中的 `sourceHash=82F23A9996329F088E45F722A1CE0D8A2AAD9E12AD82A6E734C43DDB74C5F12D` 来自 `validation-summary.json`；继承的完整工作树未被清理或重排。
- 影响路径为 `android/app/src/main/java/com/acrylic/outbound/pda/ui/` 的 Work、Submission、Temporary、Handover 页面及恢复确认/搜索逻辑，对应 `android/app/src/test/java/com/acrylic/outbound/pda/ui/` 和 `android/app/src/test/java/com/acrylic/outbound/pda/temporary/` 的六个定向测试类。
- 证据目录为 `.local/goal-20261015/e08-ui-targeted-05/`。初次 D 盘 Gradle 缓存不完整的失败、使用共享 C 盘 Gradle 缓存的重试记录、日志、输入摘要、退出码和前后监听/磁盘记录全部保留。

## 实际结果

| 验证项 | 结果 | 证据 |
| --- | --- | --- |
| `ScanFirstScreenTest` | 5/5，通过 | `validation-summary.json` |
| `SubmissionActionTest` | 2/2，通过 | `validation-summary.json` |
| `TemporaryProductSearchTest` | 1/1，通过 | `validation-summary.json` |
| `RecoveredHandoverCardReaderTest` | 2/2，通过 | `validation-summary.json` |
| `RecoveredHandoverConfirmationDialogTest` | 2/2，通过 | `validation-summary.json` |
| `TemporaryRecoveredConfirmationRetryTest` | 1/1，通过 | `validation-summary.json` |
| 合计 | 13 tests，0 failures，0 errors，0 skipped | `validation-summary.json`，`gradleExit=0` |
| Android lint | 通过 | `gradle-default-cache.log`，`BUILD SUCCESSFUL` |

`gradle-default-cache.log` 还记录了既有 Kotlin/deprecation 警告；没有产品服务启动、业务 HTTP/MySQL 写入或目标端口监听变化。执行前后仅保留共享 MySQL `3306`（PID `5288`）监听，前后磁盘记录见 `input.json`、`environment-after-default-cache.json` 和 `environment-after.json`。

## 限制与结论

本轮只验证主机上的 UI 状态选择、首屏/下一动作、Temporary 搜索和恢复确认读取/重试。没有执行 instrumentation、真实 PDA 导航/系统栏/实体键/键盘走查、连续扫码性能或长时队列，也没有获得 1540 Chrome 入口。因此 E08 仍是开发定向证据，U01-U04、E06 L2、D18 L3 及总目标继续未完成；生产开关和 `localDeletionAllowed=false` 保持原边界。
