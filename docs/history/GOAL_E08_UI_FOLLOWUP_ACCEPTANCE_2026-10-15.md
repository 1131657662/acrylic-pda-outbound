# E08 UI 短路径后续复验记录（2026-10-15）

本页补记 E08 UI 开发备份在后续工作树上的定向复验。它只说明两个恢复确认相关单元测试在受控临时目录下通过，不把该结果扩写为 U01–U04、E06 L2、D18 L3 或总目标通过。

## 输入与边界

- 工作目录固定为 `D:/acrylic-pda-outbound`，分支/HEAD 及被测文件工作树哈希见 `.local/goal-20261015/e08-ui-followup-04/input-snapshot.txt`；该快照同时记录了当次完整脏树清单，继承改动未被覆盖。
- 运行证据目录为 `.local/goal-20261015/e08-ui-followup-04/`：`command.txt`、`gradle.log`、`exit-code.txt` 以及输入快照均保留。命令使用 `--no-daemon --console=plain --rerun-tasks`，并将 `JAVA_TOOL_OPTIONS` 的 `java.io.tmpdir` 指向 `D:/acrylic-pda-outbound/.local/rtmp-ui-followup-04`。
- 复验只涉及 `RecoveredHandoverCardReaderTest` 和 `TemporaryRecoveredConfirmationRetryTest`，不启动服务、不接原库/现网、不改变生产开关；`localDeletionAllowed=false` 继续有效。

## 结果

- `RecoveredHandoverCardReaderTest`：2/2 通过。
- `TemporaryRecoveredConfirmationRetryTest`：1/1 通过。
- Gradle 日志以 `BUILD SUCCESSFUL` 结束，`exit-code.txt` 为 `EXIT_CODE=0`。本次共 3/3 通过，未记录测试失败或错误。
- 复验期间目标端口均无监听；共享 MySQL 3306 与原 AVD/ADB 是既有保全资源，未被本轮测试写入。执行前后磁盘、进程和监听复核结果随本轮看板断点记录。

## 保留的限制

此前长路径运行产生的两个 Robolectric `SQLiteCantOpenDatabaseException` XML 仍保存在 `.local/goal-20261015/ui-resume-02/` 及 Android 测试输出目录。本次通过是把 Java 临时目录移到项目 D 盘后的运行器路径复验，不能替代真机导航、系统栏/实体键/键盘、硬件性能、长时队列、真实 Chrome、后台组合或 U01–U04 完整验收。该证据也不改变 E06 L2、E07/E09、剩余 D/O/U 闸门和 D18 L3 的未完成状态。

## 结论

本记录收口一个可复核的 E08 定向开发复验切片。E08 仍是开发备份，总目标保持 `active`；下一步沿看板执行 E06 受影响组合/L2 与独立 E09/剩余闸门，最终候选仍需 D18 L3 全链整合。
