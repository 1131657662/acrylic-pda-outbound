# E07 受影响组合验收（644）

本页记录一次新的 E07 受影响组合开发切片。它验证更正账务、撤回、导出和退库周期在真实 HTTP/MySQL 隔离实例上的组合路径；不把本次结果记作 E07 L2、D18 L3 或总目标完成。

## 输入绑定与范围

- 工作区：`D:/acrylic-pda-outbound`，分支 `codex/development`。
- 执行时 HEAD：`a213b4b447acfe6cfde38dfebfe7f7c85de711b3`；执行后仅新增本报告、索引和看板断点文档。
- 改动路径 → 影响层 → 必跑清单：

  | 改动路径 | 影响层 | 必跑清单 | 复用依据 |
  | --- | --- | --- | --- |
  | `server/src/test/java/com/acrylic/outbound/e2averify/E2aCorrectionAccountingHttpIT.java` | 真实 HTTP/MySQL 组合测试 | 更正账务→撤回→导出→退库周期正向路径、权限、事务与末条 SQL 失败回滚 | SHA-256 `47d6eca474091e38d25e2e8c064485e0a8afa3aaf98e4a9e999eb7d53bf60307` |
  | `server/src/test/java/com/acrylic/outbound/e2averify/E2aCrossSourceHttpIT.java`、`E2aCrossSourceProfile.java` | 隔离服务启动、端口和数据库绑定 | 新库/新端口启动、真实 HTTP、认证停机、监听复核 | SHA-256 分别为 `c57a244b3f96976c1538c18e9db6ab12f8c0a3ea4e57718185505e29e17a6f1c`、`b48746fc09bcf3e31f1c8666e1dc2269c055cb7ceaec0fd1fc73f472165d3af0` |
  | `tools/e2a-cross-source-verify.ps1` | 验证运行器/证据封存 | 运行器命令、退出码、数据库停机和输入输出快照 | SHA-256 `e598f52f8f171106b533ada1b1ea0981d941b856639023ccaa31469683c9893c` |
  | `.local/e2a-cross-20260925-644/`、`.local/goal-20261016/e07-l2-combination-644/` | 证据与隔离实例 | XML/日志、数据库、dump/binlog、文件、端口/进程/磁盘和 Git 快照 | 目录均保留；运行器日志与 `runtime-after.json` 绑定本页 |

## 644 实际执行

命令见 `.local/goal-20261016/e07-l2-combination-644/command.txt`：使用新隔离数据库 `acrylic_e2a_cross_20260925_644`、DB 端口 `19350`、HTTP 端口 `19434`。执行前四个项目端口均空闲，子进程 TEMP/Java 临时目录位于本项目 D 盘证据目录。

- Gradle `integrationTest`：1 test / 0 failures / 0 errors / 0 skipped，退出码 0；`runner.log` 含 `BUILD SUCCESSFUL`，`exit.txt` 为 `exit=0`，完成标记存在。
- 正向覆盖：真实 HTTP/MySQL、更正账务、撤回、导出、退库周期；测试内的权限、事务、幂等、并发和末条 SQL 失败回滚断言通过。
- 运行器通过认证方式停止 644 数据库；`runtime-after.json` 记录退出码 0，`19350/19434/19435/19436` 均无监听。系统 MySQL 进程仅被观察，未复用。
- 644 目录内隔离实例、日志、数据库文件和验证输出完整保留；未删除或覆盖既有资料。

## 643 失败保全

643 的原命令、日志、运行时快照和隔离库保存在 `.local/goal-20261016/e07-l2-combination-643/` 与 `.local/e2a-cross-20260925-643/`。失败指纹为 `E2aCorrectionAccountingHttpIT` 在启动后连接 `19466` 失败；运行器实际给 Spring 分配了 `19466`，而 `TemporaryAccountingChecks` 固定访问 `19434`。这是端口配置偏差，未归因于产品业务写入。643 通过认证停机并保留原失败文件；644 使用一致的 `19434` 端口重新执行后通过。该失败计数继续保留。

## 结论与边界

644 是一项受影响组合开发验收单元，结果为通过。它没有独立 Python 消费者报告、完整跨截点 dump/事实守恒消费者或 Android/Chrome/原模拟器/恢复组合，因此不收口 E07 L2、D14/D15 整体、D18 L3、任何第 8 节闸门或总目标。生产默认、七类生产决定、`localDeletionAllowed=false`、原库/现网、发布和正式签名边界保持关闭。下一步继续 E07 组合剩余范围，再推进 E08/E09 与未完成 D/O/U 闸门。
