# D16 合成隔离整单消费者 L1 开发备份（2026-10-16）

本切片实现实际整单 DELETE、完整备份恢复和引用拒绝，27项验证通过。只接受 guard-free 的初轮 FORMAL 合成数据库，不代表正常带防重产品链、D16整体、E08 L2、D18 L3或总目标完成。旧缓存投影报告的范围已纠正，原件与历史失败保留。

## 输入与授权决定

- 工作区 `D:/acrylic-pda-outbound`，分支 `codex/development`，输入 HEAD/远端 `f0a99181ddf4cd9828e956ae26763141a79c0961`；唯一写者为聊天 `01a13a57-8082-7dd2-a08f-987483dcda4b`。清理聊天已 `complete-released` 且实查 idle，无本批并发写者。
- 证据根 `.local/goal-20261016/d16-whole-order01/`。input01/02保存实现过程，input03保存自审修复前，input04绑定27项实际运行：5331项全量输入，清单 SHA `beb68542912692591f1e6b14fab0ddcdcbc43bd3bdd3dc671191f9b3364a58ef`。后续纯README/报告/索引/看板变化不改变该次运行代码。
- 每次快照含所有tracked/untracked哈希与长度、binary diff、未跟踪原字节、Git/worktrees、写权、进程/监听及C/D余量。ownership01原模板误写旧E02/E08活动，原件保留，以ownership03/04及补记的ownership01-current为准。
- 按总目标第3节已有自主开发授权，选择离线Python消费者，仅写工具新建候选，不增加Android生产删除入口。与直接在产品库删除相比，该方案能在原库与生产资格保持关闭的条件下实际验证事务和恢复；与dry-run相比有真实整单业务删除。
- 合成声明、固定计划SHA和单独permit只表示隔离测试资格；不能替代生产可信授权。原签归档仍要求 `localDeletionAllowed=false`。正常成功订单可能仍有有效guard，首切片明确阻断，不以移除guard伪造资格；该正常链及复杂引用资格仍须开发验证。

## 路径 → 影响层 → 必跑 → 复用

| 改动路径 | 影响层 | 本切片必跑 | 复用依据 |
| --- | --- | --- | --- |
| `tools/pda_retention/{consumer,store,catalog,references}.py` | 新离线SQLite消费者及引用闭包 | 实际CLI允许/拒绝、全表守恒、实际恢复、同键并发、真实锁等待后撤权、末条DELETE失败回滚 | 使用实际Room schema；其原字节SHA、工具逐文件SHA及依赖版本在 evidence manifest |
| `tools/pda_retention/{codec,proof,signatures,__main__}.py` | 输入、原签、期限、default-off | 正向原签消费、异文/错钥/无权拒绝、严格时间边界 | 原签fixture由 `SubmissionArchiveIT.java` 指定方法生成；原字节SHA在manifest，不重跑未受影响的服务IT |
| `tools/pda_retention/tests/`、README、requirements | 合成独立库、CLI子进程和可重建说明 | Python/SQLite真实执行，不mock数据库 | 工厂明确为合成，不代表Android现场；未修改Android、HTTP路由、Web、迁移或服务模型 |
| 旧D16报告、进度、历史索引、看板 | 范围纠正和断点 | 引用/事实块检查、旧结论保全 | 旧缓存投影19项仍仅按旧报告路径+SHA复用，不冒充本批整单清理 |

没有改Android/Web产品入口、队列/布局或服务迁移，本L1不需要新Chrome/APK/MySQL运行。受影响的Python消费者已实际通过CLI消费。后续D16组合和D18候选仍必须按相应层级运行，不能免除。

## 行为与自审

1. 仅项目 `.local` 下已停止、显式合成的单文件SQLite；拒绝路径穿越、软/硬链接、reparse point和WAL输入。新run UUID、备份、候选、计划和attempt使用独占创建；源库始终只读。单写者工作区是执行前提，不把路径预检描述为抵御同账号恶意替换文件。
2. 实际SQLite backup恢复两次，核所有表/原schema/数据库元数据；源和backup的字节SHA固定。候选必须与计划全表前事实一致。结构验证包含列/隐藏列、PK、FK、索引和原DDL，未知表、trigger/view及未审schema拒绝；自审补了CHECK/索引表达式和work.orderId核对。
3. 仅唯一SYNCED且autoRetry关闭的当前批次、初轮FORMAL、FROZEN；原正文SHA、回执和归档RS256用途/固定钥、人机仓/来源/批次/数重及冻结capture/scan/event逐字段一致。复杂引用表任意非空即拒绝；其他FORMAL行对原assignment/submission/order/capture/barcode的引用拒绝。
4. 严格超过完成后90天，归档至少5年且模拟核验时仍有效，UTC纳秒比较。没有真实90天/5年观察；未改变生产资格。
5. 一次 `BEGIN IMMEDIATE` 下按外键顺序删除9张owned表。全表仅允许计划选择行消失，其他订单、设备序号、元数据等全部保留。锁等待后重读permit；末次提交前再读，JSON数字1不能当布尔true授权。permit读取与SQLite提交非跨资源原子协议，撤权语义以最后检查时点为界。
6. 成功/重放单独新建结果。STARTED及 `databaseMayHaveCommitted=true` 提醒异常后必须检查候选；非零或缺result不等于无写入。实际删除后再次从backup恢复到新路径，全表与源一致。

## 实际结果和保留失败

命令 `python -m unittest discover -s tools/pda_retention/tests -t . -v`，本轮 `tests04/unittest.log` / `result.json`：27项、0失败/错误/跳过，exit0，15.140秒。input04绑定运行前代码；manifest记录运行后逐文件实算及全部证据SHA。

`evidence-manifest01.json` SHA `0bb2113f2db511df37533b5c227d37248c9cac10bfe65d2985a9ac004ef3c366`；18个工具文件、405份tests01–04证据，后续双次实算长度/SHA一致，所有Python运行代码与input04一致。原签fixture SHA `e903687a492850e2c0b26ce9ac345f1cf0a0d9ef731d7f6fe36f7c0a7e7bc8e8`，schema原字节SHA `b5501747b7a088733d51acbb34e12f71b0d86f8f1db9e7195148aa1cce1740df`；实际Python/SQLite/cryptography为3.14.4/3.50.4/48.0.0。清单生成首轮Windows路径分隔符错误在任何输出保存前发生，改为正斜杠后成功，不重跑业务。

- 正向CLI：prepare→独立permit→consume返回 `COMMITTED_SYNTHETIC_ONLY`；删除后全表受控差异，原源字节不变；真实恢复全部原事实，重放 `REPLAY_NO_WRITE`。
- default-off有/无permit、enable无permit、数字permit、候选变文、源/备份/原签/固定钥、未知schema、有效guard、未决、autoRetry、在途、续作/恢复均拒绝并保全。
- 同计划双CLI子进程：一个COMMITTED、一个REPLAY。真实SQLite锁等待至少0.2秒且已观测BEGIN后撤权，再释放锁，消费者拒绝且全表不变，见case内 `lock-wait.json`。
- SQLite authorizer拒绝最后 `prepared_order` DELETE，至少9次DELETE已尝试；前序删除全部回滚，全表与计划一致，见 `denied-delete-trace.txt`。该失败注入不是MySQL/HTTP回滚证据。
- 严格90天前/等于/后1ns、5年少1ns、归档过期均按预期；结构篡改保留Room identity也拒绝。
- `tests01`：5个fixture setup错误，NOT NULL originAssignmentId，消费者未执行；按真实模型补字段后保留原失败库。
- `tests02`：4通过1失败，CLI事务后attempt结果路径过长，候选实际删除事实保留，不追认该case成功。缩短case名并在prepare前置路径长度限制。
- `tests03`：23项exit0，13.168秒。自审新增结构/身份/严格布尔检查后才跑tests04，不机械重复旧失败。全部数据库、原件和日志不清理。

作者及自审均为当前单助手；独立CLI/Python工具消费者不等于独立人员审查。依赖复用原合成fixture与公钥，未取私钥、未新签发现场身份；没有Android产品或真实HTTP/MySQL新增结果。

## 环境及后续

本批仅Python/SQLite，无监听/服务或MySQL隔离实例。原TEMP AVD/ADB保持；共享MySQL身份未核清，未连接/复用/停止。环境/磁盘采样见input04及后续快照，重型动作前复测；产物全部在本批D盘证据根。

提交前input05全量5334项SHA `92e247fce7da73c3023331b7003fa6567ba2159e0041cbbcfa03cf187860773c`保存文档/清单及旧看板；`disks-05.json`采样C/D余量83,075,272,704/34,357,403,648 bytes，唯一写者和活动环境见ownership05及processes/listeners-05。正式提交前再保存最终文档快照，继承改动不混入。

D16剩余：正常带防重成功链的合法退出资格、适用复杂引用资格与D13–D15可取回/恢复组合；不能用guard-free合成首切片计D16收口。继续E08辅助页/恢复确认入口、E06/E07剩余组合、E09及完整D/O/U闸门。历史浏览器审批阻断和失败计数保留，不绕过；生产删除和七类决定能力继续关闭，总目标active。
