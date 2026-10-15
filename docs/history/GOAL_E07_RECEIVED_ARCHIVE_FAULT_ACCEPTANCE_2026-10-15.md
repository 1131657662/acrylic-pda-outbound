# E07/D14 恢复归档消费与主管故障围栏验收（2026-10-15）

本批接续 E07 检查点重建，在全新隔离实例上分别验证原已收归档的闭合事务消费和恢复态主管故障围栏。取消的换机承接、多跳和激活范围没有重新启用；总目标仍为 `active`。合成隔离身份、开发签名与历史来源不代表原 PDA、现场或生产事实。

## 输入、写权与隔离

| 改动路径 | 影响层 | 必跑清单 | 复用依据 |
| --- | --- | --- | --- |
| `tools/r09-e2a-entry-rerun.ps1` | 一次性 MySQL/Gradle 运行器 | PowerShell 解析；新库身份、主排序规则、root loopback 配置；测试、停机、零监听 | E07 检查点报告及 `.local/goal-20261015/e07-cross602/input-before.json`；运行器最终 SHA 另存于本批输入清单 |
| `.local/goal-20261015/e07-cross604/`、`e07-cross605/` | 隔离证据与独立消费者 | 每方法新数据库；XML/marker/exit；JDBC/dump/storage 守恒；原字节、回执、输入 hash 消费 | 原来源 `.local/e2a-cross-20260925-1233/` 只读；393 文件清单见 `e07-cross604/source-before.json` |
| 本报告、进度、索引、看板 | 验收记录 | 独立 Python/Node 回读，环境收尾 | `e07-cross604/independent-verification.json` 与两个 `preserve/result.json` |

唯一源码/库/看板写者为本聊天 `01a13a57-8082-7dd2-a08f-987483dcda4b`；清理聊天已 idle 并释放写权。接手时 33 个 worktree 均检查 604/605 不存在，19330/19434 无监听；两个实例顺序执行，未并发运行重型 Gradle。继承输入在运行前 5,252 项、原来源 393 项，收尾均未变。

## 实际运行

- **604 `consumesActualReceivedArchiveWithinClosedTransaction`**：新目标从原 1233 来源读取冻结归档，在同一 closed transaction 中提交唯一 consumer probe；重复消费幂等，消费者异常、末 SQL 失败、verification 当前/提交前篡改、原件变化、库存锁等待、owner 变化和外层事务均拒绝并回滚。XML `1/0/0/0`、`testExit=0`、完成 marker 存在。独立 Node 消费者 403 项输入通过，归档 payload/terminal/STOP、RSA/ECDSA 回执、verification、文件与全表守恒通过；`ordinaryWritesAllowed=false`、`localDeletionAllowed=false`。
- **605 `fencesRecoveredActualTemporarySourceWithIndependentSupervisor`**：在另一全新目标上绑定原归档与独立主管，实际执行既有 REQUEST/FAULT_FENCE 内核；普通写入、异 scope/代次/权限、外层嵌套、异 DataSource、尾 verification 篡改和围栏投影反例保持拒绝，合法围栏及唯一 outbox 变化严格核对。XML `1/0/0/0`、`testExit=0`、完成 marker 存在。独立 Node 消费者 404 项输入通过，原 REQUEST/FAULT_FENCE 回执、设备/会话/订单版本/围栏/outbox 合法变化与其余原表/文件守恒通过；普通写仍关闭，`localDeletionAllowed=false`。

两个实例均经过 JDBC 快照、mysqldump、storage 文件哈希和认证 shutdown；各自 148 表快照保持，604 dump SHA256 为 `febc704851ca45bcd5f8d3744d418a799100ad885aa1449745ba3178159c92f1`，605 为 `596dbee614c78d40b15c284763e2605054eacca904eb5b006d5c4802d5e9cd8f`。收尾 19330/19434 零监听，所有 datadir、dump、binlog、XML、日志、停机文件及消费者输入保留在 D 盘证据目录。

## 自审、边界与接续

本批未修改产品 Java/Android/Web/Python、契约或迁移；运行器仅修复隔离主库排序规则并生成已有 loopback root 客户端配置。源 393 文件和 5,252 项输入由独立 Python 复核。作者自审与独立 Node/Python 消费分层记录；独立工具不等于独立人员或现场审查。

该批只收口 E07/D14 的恢复归档消费与主管故障围栏开发切片，不计 E07 L2、D14/D15 整体、E06 L2、D18 L3、O/U 完整验收或第 8 节总目标。历史 1551/601/602 失败、旧 1242–1251 原件和旧消费者继续保留，不复用失败库。下一步执行 E07 受影响组合与 E06 L2 可运行范围，随后推进 E08/E09 和剩余 D/O/U；生产默认、七类决定能力及 `localDeletionAllowed=false` 保持关闭。
