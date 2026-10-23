# D13 后继数据库单 SELECT 只读准入

已实现并验证后继只读数据库准入：已有不可变 binding/NORMAL 的非空源库实际 dump 到新 schema 后，两轮完整 lineage 与准确轮次 NORMAL 一致时，真实 verifier 和连接入口均准入；每个入口各 1 次 SELECT、0 DML，binlog、原行、业务表及原文件保持。相关主机 100 项及新隔离 IT 1 项通过。该结果是数据库观察及连接入口的有界验证，不是后继真实 API/独立 worker/P5/文件组合、D13 整体或最终候选通过。

## 输入、改动与影响层

- 固定树 `D:/acrylic-pda-outbound`，分支 `codex/development`，输入 `6b28cd05f464e9ca4ef18f0a608db17e8c1827f3`。总目标 active、未完成；版本事实只见[看板生成块](../ORCHESTRATION_STATUS.md)。
- 证据根 `.local/d13-successor-admission-20261023/run01/`；`decision.md` 绑定用户已批准的最小 D13 设计、本片范围及退出标准。`baseline/` 保存输入原字节/SHA/patch，后续快照分别保存新增改动和实际环境，不依赖旧 PID。
- 11 个产品路径与 5 个测试路径的完整清单、SHA、行数及冻结输入对比见 `closeout-review/review.json` / `selected.json`。受影响层为恢复数据库观察、指定轮次意图读取及原追加登记共用的 lineage 校验；必跑为既有准入/控制主机回归、新隔离非空 dump 两轮 IT、Python 原件消费。
- 新 `RecoveryLineageFacts` 将数据库读取与验证事实分离，原 closed 登记路径仍在持有事务和状态锁时读原事实。`RecoverySuccessorLineage` / `RecoveryDatabasePrefix` 复用原完整前缀和 source pin 约束，没有另建宽松验证器。
- `RecoverySuccessorAdmissionSql` 在同一固定 SELECT 内取得实际 UUID/schema、原 binding/NORMAL、全部投影/数字映射、后继 binding/NORMAL 及 server_state。每个数组读取上限再多一行，解码超过上限拒绝；不拼接外部表名，不靠多张一对多表 JOIN 放大事实。
- `RecoveryLineageObservation` 排序并检查数字/installation，原行摘要包含全部原列和登记微秒时间。JSON 整数按协议规范化，避免 Jackson IntNode/LongNode 对象比较的类型差异。
- `RecoveryDatabaseControl.readOnly` 在协调锁下重新核完整原件及已经读到的 revision/hash；`RecoveryAdmissionIntents` 按准确轮次取 NORMAL，初始原件不覆写。实际数据库身份、源 pin、中间前缀、外部原件或数据库 NORMAL 缺失/冲突均拒绝。
- 既有首轮单语句路径保留；没有新增迁移、bean、路由或生产开关。`admitted` 是观察一致的结果，`ordinaryWritesAllowed=false`、`localDeletionAllowed=false` 保持，不产生业务授权。
- 复用依据不是历史测试计数：本次重新执行 100 项相关主机测试，并在同一新 IT 内再次覆盖追加登记、末 SQL 回滚和原件守恒。`compile03/source-inputs.json`、`dependencies.json` 及自审记录绑定实际路径/SHA；2720 项源/资源/运行器输入和 110 项依赖无漂移。Android、Web及真实 HTTP/独立 worker/P5 本片未运行，不计组合覆盖。

## 实际结果

| 证据 | 结果及边界 |
| --- | --- |
| `red-compile01/`、`red-it01/` | 旧实现编译成功；全新隔离 allocation 1753 在第二轮登记后真实返回 admitted=false，预期 true 的断言准确失败，IT 0 通过/1 失败。不是编译或环境失败冒充 red。 |
| `compile03/` | 独立 javac exit0、冻结输入和依赖无漂移；非 Gradle 全量测试或 APK 构建。compile01/02 为成功中间编译，原件保留。 |
| `unit01/` | found/started/succeeded 均为 100；失败、跳过、中止均为 0，exit0。涵盖外部控制/锁/完整性、原 NORMAL、准入比较/身份和 closed connection 等受影响既有消费者。 |
| `it02/` | 全新隔离 allocation 1755，IT 1/0/0/0、runner exit0；已有 binding/NORMAL 的实际非空 dump 跨 schema，追加第二、第三轮后分别运行真实 verifier 与 `RecoveryClosedConnectionAdmission`。每入口各 1 SELECT/0 DML，binlog 不变。 |
| 两轮正向及原件反例 | 原 schema 拒绝后继身份；准确当轮 NORMAL 原件暂时移出时精确拒绝，原字节恢复后重新准入。初始原件、七张控制/状态表及控制文件守恒，原源库全量保持。 |
| 每轮 10 类纯观察反例 | 从真实 SQL 原 observation 派生：错实际 UUID、错 schema、缺中间投影、缺代次映射、错初始 NORMAL、原 binding 时间改变、错源 pin、错轮次 NORMAL、孤儿 NORMAL、缺本轮 NORMAL。分别核精确错误码；这些是纯反例消费者，不冒充真实改库或跨实例正向。 |
| 同一 IT 的登记回归 | 原 binding/NORMAL 不更新；后继重放幂等、不可变触发器、错 pin/目标、binding 末 INSERT 失败和每轮 NORMAL 末 UPDATE 失败回滚保持。 |
| `consumer02/` | 同助手操作的 Python 原件消费者 exit0：独立重算两轮摘要及 source pin、每轮 10 反例，核 380 份原件/文件；源库 181 表全量不变。不同工具不等于独立人员。 |

准确 argv、cwd、JDK 参数、输入/依赖和退出码分别见 `compile03/command.json` / `javac.args`、`unit01/command.json`、`it02/command.json`；初始化、身份、实际导出/导入和认证停库回执保存在 `.local/e2a-cross-20260925-1753/` 至 `1755/`。数据库/身份/签名均为明确合成隔离输入，没有原身份、现网或生产证明。

关键结果 SHA-256（简单计数 JSON 可与其他运行同字节，须连同路径、命令及冻结清单识别输入）：

- `unit01/result.json`：`6a576416d40ba51211580ca649c759dc765ef29b57d1ac248048205574cad25b`。
- `it02/result.json`：`68daac89bf050a4ad26ca4b2f60ee245f1ef8e7f8f7c048ca544089c98c514aa`。
- `consumer02/result.json`：`17fd4548bef6652bb2613a977c6fea22377789ef44fa7ee59176502ba8a18898`。
- `cleanup01/result.json`：`09aae8d5e66727d5e74265cb8e7216cc0c8adafe753f6df67ce608f5146733d3`。

## 失败与修正

- `it01` / allocation 1754：测试读 `SHOW MASTER STATUS` 缺 REPLICATION CLIENT 权限失败。仅修本地运行器，在全新隔离实例 bootstrap 给合成账号加入该观察权限；产品未为该失败修改，1755 复验通过。未触碰系统 MySQL 或生产权限；1754 原日志/输入/停库回执保留，不计通过。
- `consumer01`：Python 初次摘要 preimage 漏协议末尾单 LF，停在原 binding 摘要断言；保留失败脚本及 `failure.md`。失败脚本 SHA 为 `6f284a7cdea5ec8c30e9a80bc3a926ac3ea4134b047dd184b3d4f2b7c5a2bd44`。只修消费者，以排序 JSON 加末尾 LF 在新目录 consumer02 通过，产品和原证据未改。
- 没有机械重跑旧库、覆盖旧日志或用中间编译代业务通过。代码自审、自动敏感模式扫描、明确路径输入校验及继承 22 项逐字节守恒见 `closeout-review/`；自审不是独立人员验收。

## 清理、保存与接续

- 1753–1755 已认证停库并重新核无进程/监听消费者后，只清本片 15 条明确路径的 datadir、binlog/index 与可重建 dump。保留原命令、输入/依赖冻结、失败日志、全量事实/文件摘要、控制/intent/身份原件和逐字关键恢复行 `cleanup01/retained-recovery-rows.sql.txt`。已清库不得重启或复用。
- 逻辑删除 774542189 字节；本片清理窗口 D 空闲 `25020846080 → 25821130752` 字节，实测净增 800284672 字节（约 0.75 GiB）。逻辑长度与卷净差分别列出；其他时点余量不归为本片释放。`before-cleanup/` 50 条展开 dirty、690 目标原件，`resume-closeout01/` 保存后续完整快照与新鲜环境。
- 共享文档写前原字节/SHA 与不存在的新路径登记在 `closeout-review/documents-before.json`；旧叙述按原文[归档](ORCHESTRATION_STATUS_BEFORE_SUCCESSOR_ADMISSION_2026-10-23.md)，不复述版本生成块。另一聊天的 Android 改动、所有继承 WIP、取消链及未提交资料保持，不代暂存/提交。
- 明确路径开发备份/推送及实际远端核验以本片 `closeout-submit/` 为准，最终 dirty/进程/监听/磁盘和写权以 `closeout-final/` 为准。开发备份、清理和文档动作不计 D/O/U 或第8节验收。
- 下一片：以全新已有 binding 的非空实际恢复输入，组合后继准入、真实 API/独立 worker/P5 与文件；不拼接历史无旧 binding 的组合结果。实际跨实例正向、进程/pending 边界、其余入口、OS 拒绝自 halt、D16、D18 实际最终候选和第8节仍未满足。
- 总目标保持 active、未完成。生产开关/TEMP归档、正式签名、原库/现网、main、部署/Release 关闭；`localDeletionAllowed=false`。不恢复取消链、多跳、接收者确认/激活或旧 run03。
