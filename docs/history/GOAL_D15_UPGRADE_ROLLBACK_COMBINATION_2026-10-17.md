# D15 非空升级、程序回退读取与升级前备份恢复组合

同一新隔离候选已完成非空升级、七个独立 JAR 进程的真实 HTTP 读取，以及升级前 SQL/文件/开发验签配置恢复到空伴随库。56 次 HTTP、精确认证增量、业务响应一致、原行/结构/触发器/文件守恒及 Python 独立消费通过。本页只收口该有界组合开发备份，不计 D15 整体、E07 完整 L2、D18 L3 或总目标完成。版本事实见看板生成块。

## 设计、输入与写权

- 固定树 `D:/acrylic-pda-outbound`、分支 `codex/development`，输入 HEAD/实核远端 `26b572ff739dc670b1d3941e09b57c579af6e60b`。聊天 `01a147c5-0495-7703-ba98-2e7637cbc619` 为唯一写者；线程列表无其他活动项目写者，未创建子代理。共享 MySQL 只观察，原库/现网/正式身份未接触。
- 验收前默认：选择“升级前备份 → 新增迁移 → 新旧程序读取 → 空伴随恢复”，同一输入贯穿全程；原地 schema 回降、旧程序写入/worker、原设备和现场恢复不在本切片结论内。测试读者随机密码，只授独立 `order:read` 角色与本合成仓库；不授管理员权限。
- 接管输入 `.local/goal-20261017-resume/inputd15combination-before02.json` 共 5523 项，SHA `61a58ed50b0e8b3441c44ffed74545ef915ad87f2ae7a6bd9400b01354a36813`，同号 patch、未跟踪副本、所有权及环境快照保留。`d15-combination-closeout01/inherited-protection.json` 核对全部旧输入，只有看板生成事实变化；继承源码字节未改。
- 运行证据 `.local/e2a-cross-20260925-1554/inputs.json` SHA `43424ecc5a60e235714a25772015d94672d34e97de468de4f37dc2b03933de50` 绑定当次源码/配置/工具；`tracked.patch`、`status.txt`、`git.json`、每段 `process.json`/`listener.json`/`stopped.json` 与原日志保留。`d15-combination-closeout01/source-bindings.json` SHA `56f0388aaba1623b83c37d3c330d852611dfb7d6811b2eb3dce97c65e32ca07c` 绑定直接代码及复用测试设施。冻结 JAR 的行为不代表全部 HEAD/WIP 产品源码已构建成统一最终候选。
- 旧 JAR `.local/goal-20261013/e06-conflict-audit-1535/server.jar` SHA `adbd314f0fd619997dea0f16e0de36a963c3650173e991cbcf259ad1ae7184e1`；新 JAR `server/build/libs/acrylic-outbound-server-0.1.0.jar` SHA `e130aa2b7844daf6380045e3f3e809672b9c15f5ff31c4a24adf4cdf6bdc92ba`。每次启动前重新核哈希；直接 JDK，单个 JVM 与监听 PID 对应。

| 改动路径 | 影响层 | 必跑清单 | 复用依据（路径 + SHA） |
| --- | --- | --- | --- |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aUpgradeCheckpoint.java`、`E2aTestPorts.java`、`E2aCrossSourceProfile.java` | 一次性隔离库/端口守卫 | 空库、账号、datadir、端口、binlog 与重复使用拒绝；自定义端口真实恢复 | Ports/Profile 沿用接管未提交字节，作为本切片必要依赖明确保存；精确 SHA 见 source-bindings，不带入其他继承业务改动 |
| 同目录 `E2aUpgradeRollbackCombinationIT.java`、`E2aUpgradeRollbackProgram.java` | 非空升级/原备份恢复及 JAR 入口 | 同一候选升级前旧/新、升级后新/旧/新、恢复后旧/新，原备份恢复与非空目标拒绝 | Checkpoint、MigrationSnapshot、ArchiveSnapshotRestore、Seed/BaseFixture 的路径/SHA 见 source-bindings；旧 1550/673 只作设计参考，不计本次执行 |
| 同目录 `E2aProgramReadHttp.java`、`E2aProgramReadFacts.java` | cookie/CSRF/订单读取、全量事实 | 每段 401、CSRF、真实登录、五读取；每段只许三条认证增量，全部旧行/DDL/触发器/文件与历史保持 | 两份 JAR 固定 SHA 见上；HTTP 原文与前后快照逐段保存，不用 DTO/MockMvc 代替 |
| `docs/parallel/R09/tests/d15_combination_runtime.py`、`run_d15_upgrade_rollback.py` | Python 进程/字节输出/数据库生命周期 | 新路径和空闲端口、原生参数列表、受限私有目录、实际 IT、原生 dump、认证停机与排空 | run1554 inputs 绑定实际运行工具；不复用 1552/1553 数据库 |
| 同目录 `d15_combination_consume.py`、`verify_d15_upgrade_rollback.py` | 离线独立算法消费 | 原文 SHA、七段逐值/触发器/DDL、精确读者增量、迁移前缀、原文件、两库 dump 表/行 | consumer02 真实消费 run1554；解析修复仅离线执行，consumer01 失败原件保留 |
| 本报告、历史索引/验收索引、看板与矩阵增量 | 证据索引 | 引用、暂存差异、事实 Write/Check、当前区上限 | 旧看板结论归档 `ORCHESTRATION_STATE_before_d15_upgrade_rollback_20261017.md`；原字节在输入快照 |

Android/Web 产品源码与迁移脚本本切片未改，未运行 Android/Chrome 或新产品构建；不把这些入口记成本次通过。作者、执行和自审为同一助手，Python 是独立工具算法，非独立人员审查。

## 实际执行与产物

命令 `python docs/parallel/R09/tests/run_d15_upgrade_rollback.py --run 1554 --port 19333 --http 19437`，exit0。Gradle `integrationTest --tests '*E2aUpgradeRollbackCombinationIT'` 当次 XML 为 1 项、0 失败/错误/跳过；构建约两分钟，测试约 96 秒，仅报告本样本，不承诺生产 RTO。

1. 新 1554 专用 datadir，schema/账号尾缀一致；SQL 回读端口、datadir、DATABASE/CURRENT_USER、binlog 和 server UUID。原非空合成订单有修订/发布、四个存储文件和来源事实。升级前备份为原生字节 SQL，含原 trigger 与两份开发 DER，备份生成前后全量事实相同。
2. 七段 `pre-old/pre-new/post-new/post-old/post-new-again/restored-old/restored-new` 均独立直接 JDK JVM；共 56 次 HTTP。每段未登录订单详情 401、CSRF/真实登录 200、列表/详情/修订/发布/exports 五读取 200；原订单 ID、非空修订和发布存在，七段业务 data 完全一致。exports 为该合成读者的空列表，不声称导出下载兼容。
3. 每段仅新增本读者一条 `WEB_LOGIN`（actor/entity/type/details 精确检查）、一个本身份 session、该 session 唯一 `SPRING_SECURITY_CONTEXT`；原认证行也保持。操作审计下一自增值严格增加一，其余 DDL 不归一化。升级前/恢复副本每段 175 张其他表精确保持；升级后每段 176 张其他表精确保持。
4. 增量升级保留全部原表/行/DDL、私有文件与迁移历史前缀；新增成员表为空，原触发器保留并新增该表的两项不可变守卫。历史验证成功，无 Flyway repair 或 schema 回降。
5. 升级前原 SQL、四文件和两 DER 实际恢复到全新空伴随 schema，序列化全量事实及触发器与检查点一致；原升级后源库保持。恢复后旧/新程序读取成功。再次 restore 因目标非空被拒绝，拒绝后源库与副本事实均不变。
6. 各 HTTP JVM 结束后定向终止本进程并核监听排空；Windows TerminateProcess 不称优雅停机。最终两库 dump 原字节 SHA `1cca0d382f5a2418132ff742040684b4eebcb115c08530da9e0c25c6bdda48c9`，源 180 表/262 行、伴随 179 表/252 行（含迁移历史）。MySQL 认证 shutdown exit0、父进程 exit0、19333/19437 无监听。
7. `python -X utf8 docs/parallel/R09/tests/verify_d15_upgrade_rollback.py --attempt D:/acrylic-pda-outbound/.local/e2a-cross-20260925-1554` 的 consumer02 exit0（摘要 SHA `c13be77cdf752244a929e5449cb9ad8a20191345d28b396a7981acdaa87cc2a7`）：原 HTTP SHA/业务 data、进程/监听归属、七段精确认证增量、原触发器/结构/行、增量升级、三份存储字节和原 DER、完整 dump 表/行均通过。原升级前 SQL SHA `33be623aa1d5102ac2f05113008f9698c5d75cb3da5ee35bfb2c3108f9134341`。

## 失败、清理与未完成

- 1552 原 PowerShell 运行器将 `Picked up JAVA_TOOL_OPTIONS` 的 native stderr 当异常，业务测试未启动；原脚本、日志、认证停机回执及失败 dump 保留。该 dump 经 PS 重定向，不充当正式恢复 SQL。后续 Python 原生字节流修复已实跑。
- 1553 非空升级与五段 HTTP 完成，恢复全量比较因 JDBC 触发器 `ACTION_ORDER` 的 LongNode 与 JSON 回读 IntNode 类型严格相等失败。失败 XML、实际快照与 `failure-diagnosis.json` 保留；独立 JSON 解析逐值相同。修复触发器的序列化规范后使用全新 1554，1553 不复用、不改记通过。
- 编译日志 `d15-combination-compile01.log` 为 BUILD SUCCESSFUL；包装器向 GBK 终端打印解码替换字符失败，未把该包装器 exit1 说成测试通过。最终 run1554 再编译并完成实际 IT。
- consumer01 已完成 HTTP/快照检查，末段 dump parser 未接受生成列导致的 INSERT 列名列表而失败；consumer02 支持带/不带列名后只读重消费通过，不重启任何服务/库。诊断命令一次 PowerShell 引号解析失败，未执行业务或写库。
- 按持续清理授权，先保存三个 attempt 全文件 SHA/长度清单、输入/命令/依赖、失败摘要和停机/无消费者证据，再清 1552/1553/1554 的合成 datadir/binlog。删除逻辑长度 701334144 bytes，D 可用 56638976000→57357721600 bytes，卷观测增加 718745600 bytes；C/E 与进程/监听见 `d15-combination-closeout01/cleanup-{before,after}.json`。SQL、XML、原日志/HTTP、开发签名字节、所有源码和用户指定原失败证据均保留。
- D16 仍无正常目标可信合法退出依据，不移除 guard、不降标准。D04 旧前缀、E06/E07/E08 剩余组合、D15 其他运维/恢复范围、E09 统一候选、D18 实际全链 L3 与第8节闸门继续未完成。旧程序写入/worker 与本组合不是同一输入，旧 PDF/L1 不能拼接为完整 D15。
- 七类生产决定能力、TEMP 生产归档和 `localDeletionAllowed=false` 保持；取消的换机承接/多跳/接收者确认激活不恢复；不合 main、不发布部署、不使用正式签名、不绕 Chrome/设备审批。真实原身份/原PDA、原资料/旧BCD、异地介质、实际规模/长时与项目 Linux 现场仍单列。
