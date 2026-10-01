# R07 交接 outbox 写后回滚验收（2026-10-01）

输入树 `D:/acrylic-pda-outbound` / `codex/development`，输入 SHA `2068b6952fb7677b8bd8ad610975ba6b82221f80` 加本批七份新增测试源码；七项原有改动保持原样、未计入提交。版本事实见[看板生成块](../ORCHESTRATION_STATUS.md)，设计与退出标准见[方案](AUTONOMOUS_PLAN_2026-10-01_HANDOVER_SQL_ROLLBACK.md)。

## 验收结论

**R1b-SQL 服务层有界范围通过。** 全新隔离 MySQL attempt 807 一次执行 `E2aHandoverSqlRollbackIT`：**1 test / 0 failures / 0 errors / 0 skipped，14.610s，Gradle exit 0**，started/completed marker 均存在。覆盖实际 outbox/资格/回执写入后的事务回滚、同一原命令恢复、字节一致重放及应用上下文重启。不是 HTTP/PDA/worker/现场恢复验收。

| 阶段 | 实际证据 |
| --- | --- |
| 准备 | 真实 REQUEST→APPROVE→PREPARE command service；前状态 TARGET_PREPARED，旧资格未撤销，轮次 1，ACTIVATE 的 outbox/回执/审计均 0，非空占用 1 |
| 写后故障 | 新隔离库的专用 AFTER INSERT 触发器只匹配本 case 的 HANDOVER_ACTIVATE；触发时核对新激活资格 1、旧资格撤销 1、轮次 2、待发布 outbox 1、该审计实体对应 ACTIVATE 回执 1、非空占用 1，随后 SIGNAL |
| 故障归因 | 精确匹配 SQLSTATE `45000`、MySQL 1644 和 `E2A_HANDOVER_AFTER_AUDIT_WRITES_VERIFIED`；其他错误或状态不符消息会令测试失败。没有使用宽泛 DataAccessException 作为通过判据 |
| 事务回滚 | 写前/失败后 16 表 typed 全行快照字节与 SHA-256 完全相同：`A1B8BD826F6A8EBD37BA2F9521683F3514A7DBF984DD7DA1D898F270031200F0` |
| 原键恢复 | 移除本测试触发器后以同一 commandId/正文成功；再调用返回原始回执字节，无重复写；旧占用全行快照不变 |
| 上下文重启 | 关闭并重建应用上下文，同一原键仍返回同字节回执；提交后/重启后 16 表快照摘要均 `A7F97FFC3DFA6799B95800B31A5471504C43185BF69B7F00405D2B224E58E46B` |

AFTER INSERT 信号证明审计 INSERT 已进入触发器，**不声称审计行曾独立提交**。快照覆盖 `outbound_order/order_assignment/offline_authorization/submission/submission_scope/order_submission_slot/handover_case/handover_evidence/handover_checklist/handover_command_receipt/api_idempotency_request/operation_event/order_sync_outbox/bale_commitment/dispatch_record/inventory_ledger`；保留类型、二进制、null、精确数值和时间，不要求 MySQL 自增计数回退。

## 离线验证与自审

- 本批编译及完整服务端单测：**135 suites / 2857 tests / 0 failures / 0 errors / 1 既有 skip**。其中 typed 快照测试 **10/0/0/0**、冻结 Primary 白名单守卫 **3/0/0/0** 均实际执行；脚本 parseErrors=0。
- 测试只使用新隔离库，凭据来自本地随机生成文件；先校验 datadir、库名、用户、端口及空库。触发器 SQL 中可变值全部来自 fixture UUID，表名/触发器名固定。无需 Java Bean 替换，不新增 Primary，不放宽守卫或修改历史迁移。
- typed 快照与合成夹具从旧保全草稿复制并核摘要；旧失败源码、793/794 与冻结白名单失败轮原样保留。807 是新方法的一次运行，未重复旧 attempt。
- 新代码经作者自审；未称独立评审。既有 `E2aHandoverOutboxIT` 的“写后回滚未验”是其历史范围，本批仅补这一服务层缺口，不重跑完整历史矩阵。

## 证据与资源

- 原始证据 `.local/e2a-cross-20260925-807/`：XML、日志、started/completed marker、`rollback-fault-trigger.sql`、精确错误 JSON、前后/提交/重启快照、原命令/回执及完整 SQL dump。
- 执行证据 `.local/autonomous-20261001/handover-sql-rollback/`：`before/`、`offline/`、`closeout/`、`commit-review/`；源码摘要、保护清单、环境监听、设备及磁盘均可核对。
- 807 已认证停库；完整 ZIP **290 文件**逐项长度/SHA-256 回读相同后仅清理本轮新 datadir，ZIP SHA-256 `7526ADB6C716D0AEBD650370FD81F5691D0778F23BC655364FA2864FB2BA3D3E`。dump、ZIP、binlog、日志及历史失败均保留，不入 Git。
- D 可用空间：本轮总前后 **32094982144 → 32025157632 字节**；attempt 建库前/停库后 **32039673856 → 31824330752**，均高于 20 GiB。
- SDK adb 真机 `22D2910042 device product:m71 model:m71 device:m71 transport_id:1` 全程前后一致；目标设备命令和 AVD 均 0。19330/19434 无监听、系统 MySQL 未动；七项保护文件摘要一致。

## 未验与后续

合成 FORMAL 来源、主管会话与停止凭据不替代现场事实。HTTP 权限/传输、真实设备停止、OS/进程硬死亡、outbox worker/消费者、库存实物校验及 TEMP 跨来源续作仍未验。MySQL 进程在上下文重启时保持运行，不称数据库重启。P08 身份保护/触发器归因/非空升级与来源绑定分页缺陷仍停止。

这次关闭 R1b 的上述原子性/原键恢复缺口；R06/R07 整体未完成。接续优先独立的 R06 来源/高轮次或 R07 真实附件、故障换机、库存差异审批/调整等可执行项，按已定政策拆分，不将未知权限自行开放。默认开关、`localDeletionAllowed=false` 和原库/生产/main/发布边界保持。
