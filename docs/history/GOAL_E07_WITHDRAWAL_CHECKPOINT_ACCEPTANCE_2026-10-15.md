# E07/D14 故障撤回跨检查点恢复开发验收（2026-10-15）

本批完成故障撤回、重发、新设备从头领取和成功提交的五个检查点精确恢复，并在每个恢复结果上通过真实 HTTP 重放原请求。仅收口该开发切片；E07 L2、D14/D15 整体、E06 L2、D18 L3 与总目标仍未完成。合成准备、开发身份/签名、同机 MySQL 和下载 ACK 不代表 Android 现场采集、原 PDA 或外部保管事实。

## 输入与影响

树为 `D:/acrylic-pda-outbound`、分支 `codex/development`；运行 HEAD/远端均为 `cf831df355af309538a64d9150a667b21ec03eaa`。本聊天 `01a13a57-8082-7dd2-a08f-987483dcda4b` 是唯一写者；无子代理，清理聊天已 complete-released。接管及重启后均实查其他聊天、进程和监听。

证据根为 `.local/goal-20261015/e07-withdrawal01/`、`e07-withdrawal02/`。前者保留原设计、5,268 项输入、全部改动及 613 运行；后者 `input616.json` 固定包含四个新增测试文件的 5,272 项输入、HEAD/远端、写权、工作树及完整补丁。`inherited-conservation.json` 实际重算前批全部输入，0 项改变；四个测试文件是新增项。原 Android/UI/文档/工具和私有请求保持，未随本批暂存。

| 改动路径 | 影响层 | 必跑清单 | 复用依据（路径与 SHA） |
| --- | --- | --- | --- |
| `server/src/test/java/com/acrylic/outbound/inventory/FaultWithdrawalRecoveryScenario.java` | 合成夹具与实际 HTTP 消费 | 撤回/重发/新领取/下载/成功；原回执重放、旧资格与异文拒绝、账务逐行不变 | 原业务入口按 `e07-withdrawal02/reuse-inputs.json`；D10 报告 SHA `9ca1b63896b8cc30c742d0b1749d980abac5906e1c9079454cca8777267323a9` |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aPointInTimeCommands.java` | 测试专用 CLI 与文件复制 | 实际 dump/import、校验和 binlog 重写/重放、文件长度/hash | 原检查点报告 SHA `fb8e2480a3070437de5c4f9c6ac69f1ddc72292d76e5dcc144d930ccf6a39158`；完整路径见同一 JSON |
| 同目录 `E2aPointInTimeCheckpoint.java`、`E2aFaultWithdrawalRecoveryIT.java` | 一次性隔离恢复编排 | 五个非空检查点、空目标/身份、非空拒绝、全表/迁移历史/触发器/文件、HTTP 重放、源事实不变 | 原检查点实物/运维报告 SHA `973cd9f53aa48eeb89759247b7cbe0a0efb8cb7a1e11fba3c6346337165ae7f4` |
| 报告、看板及索引 | 验收与断点 | 旧结论归档、事实生成、全部输入与明确暂存路径复核 | 本报告及 `finalize-input.json`、`precommit-review.json`、`post-commit.json` |

自主设计采用每个检查点独立 SQL dump、原开发签名字节、storage 及原始校验和 ROW binlog，恢复到预分配空伴随 schema 后重放至同一终点。这样直接保留原 ID、正文、顺序和回执。恢复后的原请求只用于幂等消费验证，不通过重新生成业务事实来补齐丢失记录。源库和伴随库使用本测试自己的有界连接池，避免全表逐查询新建连接；未修改共享运行器、产品、API、迁移或默认开关。

`reuse-inputs.json` 重新验证三份报告 SHA 以及 1,087 项服务端产品输入与运行 HEAD 一致；D10 原权限/竞争/尾 SQL 回滚和消费者范围按原报告复用，不宣称本次重跑。网页与 Android 产品输入未改，本批无新增 Chrome/Android 验收；相应 L2/L3 必跑项保留。

## 实际执行与结果

每次分配实查 33 worktree、未占用目录/编号和 19330/19434 端口；无并行重型任务。613、615、616 均有独立 datadir/schema，身份为对应的 `acrylic_e2a_cross_20260925_<编号>` 和 `e2a_cross_20260925_<编号>@127.0.0.1`；临时目录为 `.local/t<编号>`，仅子进程设置。旧失败库不复用。

最终命令为 `powershell -NoProfile -File .local/goal-20261015/e07-withdrawal02/run616.ps1`，调用 `tools/r09-e2a-entry-rerun.ps1 -Run 616 -Root D:/acrylic-pda-outbound -TestClass com.acrylic.outbound.e2averify.E2aFaultWithdrawalRecoveryIT`。原 XML 为 1/0/0/0、Gradle 和 runner 退出码均 0，完成标记及日志留在 616 原目录。

五个检查点按顺序为 `before-withdrawal`、`after-withdrawal`、`after-republish`、`before-success`（新领取并下载 ACK 后）、`after-success`。各自原 dump 与签名/config/storage 固定，导入空伴随库后精确核对检查点，再实际重放原 binlog。每个最终结果的 179 张业务表、单列迁移历史、schema、触发器和两个私有文件均与源终点一致。非空目标拒绝后事实不变；原 dump/binlog hash 在操作前后保持。

每个恢复结果启动真实服务并重新 Web 登录：撤回原键/原正文和发布原键返回原回执，成功提交重复两次均返回原成功回执；旧提交、旧领取内容请求和同键异文撤回均为 409。三张账务/占用表逐行（含原 ID 和全部列）前后相同，两笔出库与两笔扣账不重复；原撤回资格不复活。源库全表与文件终点在全部恢复/HTTP 消费后再次精确相等。

独立 Python 运行 `consume616.py`，最终 `consumer616-attempt3.log` 退出 0，`consumer616-result.json` 为 PASS。它实际读取五个检查点及重建/HTTP结果、逐项比较所有表、迁移历史、文件与账务，验证原 RSA 撤回和成功回执、两设备与领取 ID 不同、原校验和 binlog pin、全部 5,272 输入和两处停机文件。账务为数量 -2、重量 -601500 克。作者与自审为同一助手；独立算法消费者不等于独立人员审查。

## 失败、中断与保全

- 613：合成 MockMvc 准备遗漏 `RequestIdFilter`，库存导入审计的 `request_id` 为 NULL；未进入撤回业务。完整保全已写夹具、原日志/XML、dump/binlog/datadir；1 库 180 表、366 文件，认证停机。补过滤器后新编号验证，未放宽产品约束。
- 614：子进程 TEMP 目录未创建，MySQL 初始化在建库前退出；保留配置、初始化错误及目录，不改原记录，也不复用编号。615 前显式创建临时目录。
- 615：真实撤回至成功流程和五个目录已形成，随后全表快照逐查询新建连接触发 `BindException`。该连接耗尽根因与旧 1548 记录一起保留，不因新编号清零；保全 1 库 180 表、402 文件并认证停机后，本测试改用有界连接池，在新 616 完整复验。
- 616：18:38 测试成功后、最终保全前，系统于 20:03 重启；原实例已停止，未取得该实例的认证停机证据。封存原目录 1,374 文件后，从同字节 `data/storage/private` 创建独立 `.local/e2a-preservation-20261015-617/`。仅启动该副本作 crash recovery，绝未重启原 616。
- 617：身份确认 schema 仍为 616 家族、datadir 为新 617 副本、端口 19330，复制输入与启动参数见 `clone617.json`、`launch617.json`。全 11 个家族 schema（其中 6 个非空、共 1,080 表）有 JDBC 和 dump 前后守恒，文件不变。认证停机且 19330/19434 零监听；1,260 个副本文件已登记 hash。Python 另核源库 179 张业务表与 616 原终点逐值一致。原 616 的 1,374 文件前后完全不变。
- 消费者首次误将二进制 UUID 按 UTF-8 文本解码；第二次误要求不同业务时点的审计自增游标一致。原失败均保留。修复为只解码明确文本列，并逐点用实际最大审计 ID+1 校验游标；其余 schema 精确一致，重建终点仍与完整最终 schema 精确比较。全部业务行断言未放宽。输入比对时误用清单字段名的只读脚本错误也保留，随后按实际 `inputRows` 重算为 0 项改变。

617 dump SHA256 为 `98f28434c4df7c4fa858e049601a325f17688b45175da2d114d84225a4a6cc6b`。613/615/616/617 原件、失败库、日志/XML、签名、dump/binlog 和私有材料留在 D 盘 `.local/`；617 的保全原始查询/结果与停机回执在 `e07-withdrawal02/preserve-617/`。收尾磁盘和进程/监听以 `environment-finalize.json` 为准；无本项目重型任务，共享 MySQL 仅观察。

生产决定、TEMP 生产归档边界及 `localDeletionAllowed=false` 保持关闭。未接原库/现网、未合 main、未发布部署或使用正式签名；本机原件不冒充异地备份。下一步核对更正、取消与缺更正围栏的跨检查点覆盖，补直接缺口，再推进 E07 组合、E08/E09、剩余 D/O/U 和第8节；总目标保持 active。
