# E07 闭合恢复跨截点前置验收（2026-10-15）

本批只验证 R09 闭合恢复控制与投影的隔离前置范围，输入为接续时保存的 `142033de792f2b04613551406add862f52f4bead` 及 `.local/goal-20261015/e07-cross02/input-hashes.json`。不接原库或现网，不把合成恢复权威、隔离身份或测试数据当作现场事实；总目标仍为 `active`。

## 实际验证

- 1545 原 attempt 运行 `E2aClosedRecoveryAuthorityIT` 时，业务测试已开始但在 `RecoveryClosedDatabaseFaultChecks` 创建 `_closed_copy` 辅助库时收到 `Access denied`。该失败归因为运行器/夹具未预授权，探针写入、XML、日志、datadir 和 370 个文件哈希全部保留在 `.local/goal-20261015/e07-cross02/1545-failure/` 及 `.local/e2a-cross-20260925-1545/`；不重用该 attempt。
- 1546 使用新 datadir、主库、5 个明确列出的 `closed_*` 辅助 schema 和逐库授权，实际命令为 `e07-cross02/run.ps1 -Run 1546 -Kind Authority -Provisioned`（此前新实例的路径转义误判发生在业务测试前，指纹另存 `1546-prebusiness-runner-failure.json`）。`E2aClosedRecoveryAuthorityIT` 通过，XML、日志和结果见 `.local/goal-20261015/e07-cross02/1546-*`；6 个数据库、206 张表的 JDBC 快照与 dump 前后守恒通过，storage 文件不变，认证停机、19330 零监听，dump SHA256 见 `1546-preserve/result.json`。
- 1547 使用全新 datadir 和 9 个 projection 负例 schema，实际命令为 `e07-cross02/run.ps1 -Run 1547 -Kind Projection`。`E2aClosedRecoveryProjectionIT` 通过四个闭合阶段、并发注册、基线/缺失/冲突/间隙/溢出/回滚/实例拒绝和重放检查；10 个数据库、225 张表的 JDBC 快照与 dump 前后守恒通过，认证停机、19330 零监听，结果见 `.local/goal-20261015/e07-cross02/1547-preserve/result.json`。
- 两个通过 attempt 的测试结果均有 `test-completed.marker` 和 XML；`input-after-tests.json` 显示除本看板更新外，接续时 5,241 个输入文件未漂移，源码产品改动为零。C/D 盘余量、进程和监听收尾记录在 `disks-after.json`、`processes-after.json`、`listeners-after.json`。

## 运行器修复与边界

运行器仅新增隔离实例内的显式辅助 schema 和最小测试账号授权，未修改产品逻辑、生产默认或业务权限。每个辅助 schema 名称来自测试源码固定白名单；主库先核对 `DATABASE()`、端口、datadir 和空表，再允许测试启动。JDBC 独立快照、mysqldump、文件哈希和认证停机均在测试后执行。

| 改动路径 | 影响层 | 必跑清单 | 复用依据 |
| --- | --- | --- | --- |
| `.local/goal-20261015/e07-cross02/run.ps1`、`preserve.py` | 本机隔离运行器 | 新实例身份/空表/逐库授权、两类实际业务探针、严格 JDBC/dump/文件守恒、认证停机与零监听 | 产品输入 `142033de792f2b04613551406add862f52f4bead`，逐文件 SHA256 见同目录 `input-hashes.json`；两类 XML 与完成标记逐项绑定新 attempt |
| 本报告、历史索引、进度索引和看板 | 验收记录 | 核对 XML/退出码/marker、dump SHA256、继承输入和明确路径暂存 | 同目录 `1546-test-result.json`、`1547-test-result.json`、两个 `*-preserve/result.json`；文档修正不改变产品输入 |

未改变 Android、Web、Python 产品消费者或迁移输入；本批没有新增这些入口的通过声明。1547 实际调用的既有 Python 控制记录消费者纳入本次执行，不能据此前置探针复用为完整跨截点业务重建。

本批是 E07/D14 的闭合恢复前置组合证据，不是异地介质、原 PDA、实际外置保管、完整 D14/D15、E07 L2、D18 L3 或第 8 节通过。1545 失败和 1546 运行器误判仍保留，不能清零或改写为产品失败。
