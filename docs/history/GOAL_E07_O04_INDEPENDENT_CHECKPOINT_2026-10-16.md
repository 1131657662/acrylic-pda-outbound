# E07/O04 独立采集检查点 L1（2026-10-16）

本页记录 641 隔离恢复切片的独立只读消费者复核。它是一个 L1 开发验收单元，不把 641 记作 E07 L2、D18 L3、D18 候选或总目标通过。

## 范围和输入绑定

- 输入根：`.local/e07-independent-checkpoint-20261016-641/`。
- 场景身份由 `independent-checkpoint/scenario.json` 动态读取：relay、target、task、warehouse 及关联回执；没有在脚本中硬编码 641 的身份、端口、库名或业务数量。
- 复用依据：641 的 `integration-result.xml`（1 test、0 failure、0 error、0 skip）、`final-facts.json`、`checkpoints/before-association/rebuilt.json`、七组 HTTP status/body、连接/停机身份和已有保全日志。对应输入字节由消费者结果的 `manifestBefore/manifestAfter` 绑定。
- 失败保全：`consumer-failure-01.json` 保留第一次发现的附件长度字符串与初始库存前置 context 表示差异；修正只改变消费者解析，不改变 641 输入或业务源码。

## 改动路径 → 影响层 → 必跑清单

| 改动路径 | 影响层 | 本切片必跑清单 | 复用依据 |
| --- | --- | --- | --- |
| `tools/e07-independent-checkpoint-consumer.py` | 独立证据消费者/工具 | Python 语法与实际只读运行；双次输入清单；HTTP/事实/文件/身份核对 | 641 输入根及其 SHA 清单 |
| `.local/goal-20261016/e07-independent-consumer-642/*` | 证据产物 | consumer-result、失败首轮、命令、Git/进程/监听/磁盘和哈希快照 | 本页所述 641 输入；不得作为业务源 |
| `docs/history/GOAL_E07_O04_INDEPENDENT_CHECKPOINT_2026-10-16.md` | 验收索引 | 文档自审、路径和边界核对 | 结果 JSON 与原 641 报告 |

## 实际结果

- 独立消费者只读执行，未连接数据库、未打开网络 socket；输入根双次 854 文件清单、长度和 SHA-256 完全一致。
- 七组保存的 HTTP 响应均为 `200`/`OK`；receive、attachment、候选、库存、physical、association 的身份和 context 由输入动态绑定。
- `final-facts.json` 与 pre-association `rebuilt.json` 的表结构、行宽和文件事实均可解析并与各自 `final-storage` 字节核对；两套事实均记录 179 张表和 622 行，文件事实逐项匹配。
- association 回执保持 `associationRecorded=true`、`pendingReconciliation=true`、`newCollectionAuthorized=false`、`releaseAllowed=false`、`localDeletionAllowed=false`；没有把证据复核提升为生产放行。
- 连接和停机身份文本一致；641 的 MySQL/HTTP 进程已停机，监听核对为空。系统既有 MySQL 监听不属于本切片且未被复用。
- 结果：通过本 L1 独立消费者检查；仅证明已生成事实可被独立读取、绑定和守恒核对，不证明异地恢复、现场身份、人工主管签字、外置介质、人工作业或生产 RPO/RTO。

## 证据索引

- 结果：`.local/goal-20261016/e07-independent-consumer-642/consumer-result.json`
- 首轮失败保全：`.local/goal-20261016/e07-independent-consumer-642/consumer-failure-01.json`
- 输入/脚本/事实哈希：`.local/goal-20261016/e07-independent-consumer-642/hashes.json`；当前消费者脚本 SHA-256 为 `8c223b05213d9d569e15d281b6cee228afde853693e0d0eb2b6611dbd7b9228e`，结果 JSON 同步记录。
- 命令、Git、进程、监听、磁盘和时间快照：同目录 `command.txt`、`git-snapshot.json`、`processes.json`、`listeners.json`、`disks.json`、`captured-at.txt`。
- 641 原始集成结果、binlog、数据库和私有日志均保留在 `.local/e07-independent-checkpoint-20261016-641/`，未清理、未接原库、未复用 1538 或旧失败库。

## 后续边界

本页不收口 E07 L2、D18 L3、E06 受影响组合、E08/E09、任何 D/O/U 闸门或第 8 节总目标。下一步应在新的隔离身份上执行 E07 受影响组合验证，并继续 E08/E09 及剩余闸门；历史失败和本次首轮失败继续保留。
