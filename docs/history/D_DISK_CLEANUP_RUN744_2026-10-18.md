# D 盘清理回执：run744 可重建隔离库

本次清理只针对已认证停机、无在途消费者且可由 runner 重建的 run744 MySQL datadir；不涉及源码、继承改动、原资料、原身份、签名回执、输入、XML、日志、trace、APK 或最终数据库 dump。

## 清理前核对

- 目标：`D:/acrylic-pda-outbound/.local/e2a-cross-20260925-744/data`
- 清理前文件数/字节：347 / 222,553,496 bytes。
- 运行器已以隔离账号认证停机；目标端口 `19663/19664/19665/5590` 无监听，目标进程为 0；共享项目监听未停止。
- 保留输入 SHA：`A7EB7337D386321C56323C11D61B7BBA223A427CDE566415ADA427830518B74D`。
- 保留最终 dump SHA：`9EABCD7FA853935048E51E4C2C808600189C38BC9AA4BA83424093DD6398B349`。
- 依赖/命令输入：`tools/e07-d06-d07-emulator-run.ps1`、`tools/fault-restart-input-consumer.py`、服务端/Android/Web 构建产物及锁文件均保留；删除命令为 `Remove-Item -LiteralPath D:/acrylic-pda-outbound/.local/e2a-cross-20260925-744/data -Recurse -Force`。

## 清理结果

- 删除结果：已执行；MySQL datadir 与空的 `.../fault-restart-consumer-744/emulator-data` 目录均不存在，目标端口/进程仍为 0。清理前 D 盘可用 `38,166,216,704` bytes，清理后 `38,393,233,408` bytes，实际释放 `227,016,704` bytes；文件逻辑字节为 `222,553,496`，差额来自文件系统分配单位。
- 证据保留位置：`.local/goal-20261011/fault-restart-consumer-744/`（输入、XML、Android 原文、Python/Chrome JSON、trace、服务日志、最终 dump、认证停机及进程快照）。
