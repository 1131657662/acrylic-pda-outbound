# E07/D06-D07 Android、真实 HTTP、Chrome 与 Python 候选（run1614，2026-10-19）

## 范围与结论

本轮在全新隔离输入上执行 D06/D07 故障订单撤回重发消费者组合：非空 Flyway 迁移、真实隔离 HTTP/MySQL、当前源码 Debug APK、独立 AVD、Android 四条 instrumentation、只读 Python 消费和 Chrome 两项。全部实际测试通过，隔离数据库认证停机通过，AVD 源与目标四个可写镜像哈希保持不变。

这是合成隔离身份、开发签名和非现场模拟器上的局部候选，只计该组合开发证据，不计 D06/D07 L2、E07 整体、D17、E06 L2、D18 L3、第 8 节或总目标。七类生产决定、TEMP 生产归档、localDeletionAllowed=false、原库/现网和发布边界保持关闭。

## 输入、改动与运行器修复

- 工作树输入 HEAD：`9c6feaffe39633206734accac7c945a4b4bfcc1f`，分支 `codex/development`；本轮改动路径为 `tools/e07-d06-d07-emulator-run.ps1`，只修复运行器 AVD 根目录表达式、PowerShell `$HOME` 参数冲突和目标 ini 路径绑定。
- run1612/1613 的运行器失败原件与修复记录保存在 `.local/goal-20261019-resume/e07-run1612-preflight/`：先后为内联 `if` 解析失败、`$home` 只读变量冲突，以及复制 ini 仍指向默认 AVD 导致源镜像被写。run1613 业务证据保留但因源变更 fail-closed，不与本轮混合。
- run1614 使用 run1613 目标目录中仍为原始哈希的 AVD 副本作为只读源，端口为 MySQL `19364`、HTTP `19464`、Web `5176`、模拟器 `5574`；目标副本 ini 已重写为目标目录。
- 输入与结果根：`.local/goal-20261011/fault-restart-consumer-1614/`。输入 `private/input.json` SHA-256 为 `7EAF65067A44894459F9C4F9EE8A6FAFD4C3F849DEEF238C38E17694C86536E2`；最终 dump SHA-256 为 `81217287C122DFCAB8CED67A35DA4AF383876A6F0582629A7C67DF5BFB75863B`。

## 实际验证

- `FaultRestartConsumerFixtureIT`：1/0/0/0，真实非空迁移至 V99 并生成三消费者输入；JUnit XML、fixture 日志和数据库身份保全。
- Python `fault-restart-input-consumer.py`：只读消费通过，输入 manifest 未漂移，`consumerCount=3`、`scanCount=2`，无数据库或网络连接。
- Android：`newDeviceClaimsDownloadsScansFreezesAndVerifiesOwnSuccess`、`forceStoppedProcessRetainsVerifiedResultAndOriginalFrozenBytes`、`scanningDeviceConsumesSignedWithdrawalAndRetainsItsLocalOrder`、`retainedWithdrawalReplaysIntoStoppedStateAfterNewProductProcess` 均为 `OK (1 test)`；`android-result.json` 为 `SYNCED`，`localDeletionAllowed=false`。
- Chrome/真实 HTTP：Playwright `fault-restart.spec.ts` 2/0/0，覆盖后台撤回、丢响应后刷新重试、原键/原正文/恢复代次一致、重新发布和浏览器存储失败不发送撤回 POST；报告 `playwright.json` 的 `expected=2, skipped=0, unexpected=0`。
- 停机：目标模拟器、HTTP、Web、MySQL 均清零；`emulator-source-after.json` 四个源镜像 `unchanged=true`；最终 `result.json` 为 `stage=complete, exit=0`。

## 清理与边界

run1613/1614 的已认证停机 AVD 副本与 datadir 在清理前登记于 `.local/goal-20261019-resume/e07-run1614-cleanup-before.json`，清理后回执为 `e07-run1614-cleanup-after.json`。共释放约 9.9 GB；保留输入、最终 dump、APK、XML、Playwright 报告、环境/进程快照、停机回执和失败摘要。默认 AVD 在 run1613 被运行器事故写入，未回滚；原始/变更哈希和根因已单列，后续候选使用隔离副本。

## 复用与下一步

复用依据为 `GOAL_E07_D06_D07_WEB_CONSUMER_RUN739_2026-10-18.md`、`GOAL_E07_D06_D07_ANDROID_HTTP_WEB_RUN1606_2026-10-18.md` 和 `GOAL_E07_D06_D07_FAIL_CLOSED_COMBINATION_RUN913_2026-10-18.md` 的同一撤回重发政策与消费者契约；本轮输入、APK、HTTP、数据库和设备均为新 attempt。仍需真实现场身份、原模拟器、独立外部 Python、完整并发/末 SQL 矩阵和最终候选组合，不能由本轮局部通过替代 L2/L3。
