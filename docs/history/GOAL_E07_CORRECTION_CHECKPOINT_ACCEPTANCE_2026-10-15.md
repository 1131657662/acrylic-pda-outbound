# E07/D14 更正请求五检查点恢复开发验收（2026-10-15）

本批补齐更正请求在五个恢复时点的原请求、原签名回执和账务事实重建。范围是 `before-create`、`after-create`、`after-approve`、`before-confirm`、`after-confirm`；每个时点均恢复到独立空 schema，再用同一原始 ROW binlog 重放到终点并走真实 HTTP 原回执重放。仅为开发备份，不计 E07 L2、D14/D15 整体、D18 L3 或总目标完成。合成身份、开发签名、隔离 MySQL 和合成 TEMP 初始确认不代表原 PDA、现场或生产事实。

## 输入与影响

树为 `D:/acrylic-pda-outbound`，分支 `codex/development`，执行前 HEAD/远端均为 `122fbd5f44fc8a27867875579bdd828e8c5d3aaf`。当前聊天 `01a13a57-8082-7dd2-a08f-987483dcda4b` 是源码、隔离库、证据、看板和索引唯一写者；清理聊天已于 2026-10-15 16:28:02 `complete-released`，20:40:34 只读复核无清理进程、在途任务或 held scope。本批旧结论先保存在 `.local/goal-20261015/e07-correction01/board-before.md`，未覆盖继承改动。

| 改动路径 | 影响层 | 必跑清单 | 复用依据 |
| --- | --- | --- | --- |
| `server/src/test/java/com/acrylic/outbound/inventory/CorrectionCheckpointScenario.java` | 真实更正 HTTP、主管/申请人身份、账务和原回执 | create/approve/withdraw/confirm；附件、RSA、同键异文 409、原出库/库存逐行不变 | `e07-correction01/design-impact.json`；既有更正入口和 D10 原 HTTP 证据按未受影响输入复用 |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aCorrectionCheckpointIT.java` | 新隔离 MySQL、五个非空 dump、原 binlog 恢复 | 空目标导入、非空拒绝、全表/schema/trigger/storage、真实恢复后 HTTP 重放 | `GOAL_E07_CHECKPOINT_REBUILD_ACCEPTANCE_2026-10-15.md` SHA256 `fb8e2480a3070437de5c4f9c6ac69f1ddc72292d76e5dcc144d930ccf6a39158` |
| `.local/goal-20261015/e07-correction01/consume618.py` | 独立证据消费 | XML、五时点状态、179 表、迁移历史、文件、4 份 RSA 回执和 20 次原回放 | 本批输入 `input618.json`；保全结果 `preserve-618/result.json` |

初始确认沿既有 `TemporaryConfirmationFixture` 仅作为隔离测试准备；更正、附件上传、主管审批、撤回和申请人确认均走真实 HTTP 会话。`correction_effect` 是实际存在的表名，未引入产品迁移或默认开关。生产 handler/POST/放行/证据写入/关闭能力及 `localDeletionAllowed=false` 均保持关闭。

## 实际执行与结果

执行前复测 33 个 worktree、19330/19434 端口和 Gradle/Java/数据库重型任务；618 及 `.local/t618` 均不存在，C/D 可用约 77.7/61.5 GiB。命令为：

```text
powershell -NoProfile -File .local/goal-20261015/e07-correction01/run618.ps1
```

运行器分配 `acrylic_e2a_cross_20260925_618`、`e2a_cross_20260925_618@127.0.0.1`、端口 19330 和独立 datadir；环境身份、临时目录、数据库、原始签名文件均在 618 目录留证。`compileTestJava` 在表名修正后重新通过，集成 XML 为 1 test / 0 failures / 0 errors / 0 skipped，runner/test 退出码均为 0。

真实流程的原回执顺序为 `SUBMITTED`、`EFFECTIVE`、`WITHDRAW_PENDING`、`WITHDRAWN`；申请人和主管身份分离，创建/确认由申请人执行，审批/撤回由主管执行。原请求回执、RSA 公钥验证、重复原键幂等和同键异文 409 均通过；更正流水在 `after-approve`/`before-confirm` 为 `300500` 克，确认后归零，原 `dispatch_record`、`inventory_ledger` 及终点结果保持不变。

五个恢复点的 binlog position 依次为 `569603`、`588044`、`607499`、`617078`、`635371`；每个独立 schema 都先验证为空，再导入该点 dump 并重放固定原 binlog 到终点。179 张表、迁移历史、触发器、2 个 signing/storage 私有文件及最终快照逐项相等；预分配家族共 11 个 schema、其中 6 个非空、总计 1,080 张表。目标非空拒绝后事实不变。

保全命令为 `python .local/goal-20261015/e07-correction01/preserve.py 618`。JDBC/dump 前后守恒、storage 清单不变、dump SHA256 为 `eda7768f21dfcc109be875738fb78969206cfe7db72c9dd22d89ff7348853a10`，认证 shutdown 通过，19330/19434 零监听，618 目录 1,398 个文件有 hash/长度记录。失败库、原 binlog、dump、日志、XML、签名和隔离身份全部保留。

独立 Python 消费 `consume618.py` 实际读取 `input618.json` 的 5,276 个输入、集成 XML、五个 checkpoint、179 表、文件和四份 RSA 回执，结果 `consumer618-result.json` 为 `PASS`：20 次恢复后的原回执重放全部逐字节一致，账务/原出库快照无重复改变。该消费者是独立算法，不等于独立人员审查。

## 失败、边界与下一步

本切片未改产品 Java、Android、Web、契约、迁移或生产配置；没有业务失败。早期更正恢复测试中的 `correction_event` 表名错误在业务写入前修正并重新编译，原 `compile01.log` 和设计快照保留。取消、缺更正围栏、E07 受影响组合、外置介质/现场保管、Android/Chrome/原模拟器和最终 L3 仍未由本批证明，不能从本批结果外推。

本批是 E07/D14 的直接开发缺口开发备份；下一步为 E07 受影响组合验证，然后继续 E08/E09 和剩余 D/O/U。历史失败计数、旧数据库和未验输入继续保留；总目标保持 `active`。
