# E08/D17 recovered-confirmation 主机边界复验失败保全（2026-10-18）

本记录补充 [WIP 范围审计](GOAL_E08_RECOVERED_CONFIRMATION_WIP_AUDIT_2026-10-18.md) 的当前环境复验结果。输入树为 `D:/acrylic-pda-outbound`，分支 `codex/development`，HEAD 为 `93710ec5cff979a567654084c80abe089e9a73b7`。本次没有修改产品源码、没有启动 HTTP/MySQL、没有启动模拟器或 ADB 业务测试，也没有发生产品数据库写入。

## 执行与结果

三次尝试均使用 D 盘临时目录、`--no-daemon`，保留完整命令和日志：

1. `wip-audit-verification01`：三个定向类共 5 项，3 项在 fixture 建库阶段失败，退出码 1。
2. `wip-audit-verification02`：显式设置 `JAVA_TOOL_OPTIONS`/`GRADLE_OPTS` 的 `java.io.tmpdir` 后重试同三类，仍为同一失败，退出码 1。
3. `wip-audit-verification03`：仅运行 `RecoveredHandoverCardReaderTest`、`--max-workers=1`，2 项仍为同一失败，退出码 1。

失败均为 `android.database.sqlite.SQLiteCantOpenDatabaseException`，堆栈停在 `FullSyncOpenHelper.getWritableDatabase`，由 `TemporaryHandoverFixture` 初始化触发，早于 recovered 只读卡片断言。当前证据只能归因于主机 Robolectric/SQLite 夹具建库或测试隔离，不能归因于产品恢复语义。三次同根因无新产品证据，按规则暂缓该主机复验，不再机械重跑。

## 边界核对

- `SyncWorker.kt` 没有调用 `TemporaryRecoveredConfirmationWorker`；默认 `RECOVERED_HANDOVER_DEVELOPMENT=false` 保持。
- 取消的 recovered-confirmation/TEMP 接收确认、激活、换机承接和多跳链仍只保留继承源码与历史失败证据，不接回产品入口。
- 既有审计中的通过报告只作为此前环境的历史证据；本次失败不覆盖范围判定，也不计 D17、D14、E06 L2、D18 L3 或总目标通过。

## 保全

失败原件位于 `.local/goal-20261018-resume/wip-audit-verification01/`、`wip-audit-verification02/`、`wip-audit-verification03/`，包含命令、退出码、Gradle 输出、测试 XML（由 Gradle 输出目录保留）和哈希清单。后续只有出现新的 fixture 隔离或 Robolectric SQLite 诊断依据，才重新入队；独立 E06/E07 组合可继续推进。
