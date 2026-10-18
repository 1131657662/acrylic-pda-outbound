# D06/D07 五截点恢复事实的 Python 独立消费（2026-10-19）

本切片为既有 run1615 增加独立语言实现的只读证据消费者。它实际消费保留的场景、数据库事实、私有文件、隔离公钥、原 binlog、截点 dump 和恢复后 HTTP 结果，核对原件与逐包记账关系；不启动数据库、HTTP、浏览器或 Android。作者、自审与执行均为同一助手，不称独立人员复核。

输入树 `D:/acrylic-pda-outbound`，分支 `codex/development`，输入 HEAD/远端均为 `59ae099576d994d186aef6e843fc43cfbb3cf60d`。run1615 的产品输入与实际运行来自 [服务端隔离切片](GOAL_E07_D06_D07_FAULT_WITHDRAWAL_L2_SLICE_2026-10-19.md) 记录的 `9c6feaffe39633206734accac7c945a4b4bfcc1f`；本轮补消费其保留结果，不把旧服务重跑或不同输入拼接写成新统一候选。

## 改动、影响与必跑清单

| 路径 | 影响层与变化 | 必跑清单 | 复用依据（路径＋SHA） |
| --- | --- | --- | --- |
| `tools/fault_recovery_evidence.py` | 只读输入边界、Base64 原字节、文件 SHA 与隔离 RS256 回执 | 实际 57 文件消费、错误公钥/受众/回执正文/越界路径拒绝 | 下述 `input-bindings.json` 逐文件 SHA；原输入不改 |
| `tools/fault_recovery_accounting.py` | 撤回、新机从头作业与逐包账务交叉核对 | 旧资格不复活、独立设备/采集 ID、序号/重量/原回执、每包一笔账、取消链空表 | run1615 `final-facts.json` SHA `b4da205216f00498aa7a0c955096ea61c190eff678401b21369320070d3043e0` |
| `tools/fault_withdrawal_recovery_consumer.py` | 五截点、完整事实、文件、公钥、HTTP 及固定输入编排 | 实际 CLI 正向、错误 SHA 绑定退出 1、输入前后不变、输出与源隔离 | run1615 场景 SHA `3f365c62706c00dcc51aedecdb1dfb0ae45495d8c7d1f657bb63df98c2b99bd8` |
| `tools/test_fault_recovery_consumer.py` | 真实保留证据上的定向变异测试 | 17 项实际执行，零跳过/失败/错误 | `FAULT_RECOVERY_EVIDENCE_ROOT` 显式指向上述 run1615；无默认合成通过 |
| 本报告、进度/历史索引、看板及旧看板归档 | 证据与断点 | 引用、事实生成/上限、差异检查、继承保护 | `.local/goal-20261019-resume/fault-final-consumer-run01/takeover/` |

工具源码、Python 运行时、依赖及锁文件的最终 SHA 绑定见证据根 `runtime-final.json` 和 `evidence-index.json`。未改产品、迁移、继承脚本 `fault-restart-input-consumer.py` 或已取消链源码；直接受影响层为 Python，因此本切片实跑 Python，未把历史 Android/Chrome/HTTP 结果计作当次实跑。

## 实际执行与结果

证据根 `.local/goal-20261019-resume/fault-final-consumer-run01/`；消费源 `.local/goal-20261019-resume/e07-recovery-run1615/`。

```text
python tools/fault_withdrawal_recovery_consumer.py --source .local/goal-20261019-resume/e07-recovery-run1615 --bindings .local/goal-20261019-resume/fault-final-consumer-run01/input-bindings.json --output .local/goal-20261019-resume/fault-final-consumer-run01/positive02
FAULT_RECOVERY_EVIDENCE_ROOT=<上述源的绝对路径>
python -m unittest discover -s tools -p test_fault_recovery_consumer.py -v
```

- CLI 退出 0，57 文件、7,977,653 字节的输入绑定与消费前后 manifest 一致；绑定文件 SHA `b26e98b935d67e63ded83da4c8f4e19c595d7ef25f51c2387d13d06f17a8988b`。
- 撤回前、撤回后、重发后、成功前、成功后五个截点的 dump SHA 与记录一致，binlog 文件一致且位置严格递增；五份重建事实与最终 179 表、199 行及 schema/迁移历史逐字节相同，截点前后引用的私有文件长度和 SHA 匹配。
- 原领取为 REVOKED，新领取为 CLOSED；设备不同、原/新 capture ID 不重用，新机扫描从 1 开始。原撤回与成功回执与数据库正文一致；两个隔离 RS256 回执均以固定保留公钥验证，提交文件字节 SHA 与成功回执一致。
- 两包各有一个 dispatch 和一笔库存负向贡献，逐包重量、序号、总重量和唯一结果版本一致；已取消确认/激活链无新增事实。恢复后 HTTP 记录拒绝旧提交/旧计划/同键异文，重放原回执及成功结果不改变三张账务相关表。
- 17 项测试零跳过、零失败、零错误。反例包括重复记账、错误重量/顺序、旧领取复活、继承旧 capture、错误公钥/受众、回执正文篡改、旧上传错误放行、账务丢失、空迁移历史、坏 Base64、schema 缺项和路径/输出越界。
- CLI 错误输入绑定反例退出 1，未创建成功输出目录；`negative01-result.json` 保留。正向最终结果为 `positive02/consumer-result.json`，前一正向及自审前日志均保留。

## 接管、范围与环境

唯一项目写者仍为本聊天 `01a147c5-0495-7703-ba98-2e7637cbc619`；实际项目聊天仅本聊天 active。接管的 207 个继承脏文件逐路径哈希保持不变，完整原字节 ZIP、patch 和 5,735 项输入哈希保存在 `takeover/`。初始全量快照与本助手版本事实刷新在同一时段执行，不能称原子快照；最终快照另存并在收口顺序生成。源码/私有证据无其他写者或运行任务。

当前重新核对 worker/dialog/display/device test/fixture/bridge 及 Handover/Sync/Temporary 的逐路径范围，复用 [takeover12 清单](GOAL_E08_RECOVERED_CONFIRMATION_WIP_AUDIT_2026-10-19_TAKEOVER12.md)，报告 SHA `675174c870c000f7c07836aa171873628f14b3ce8544896b4572a7b3f0bb0190`；本轮 11 路径 SHA 和调用事实在 `cancelled-wip-review.json`。两个确认 worker 无后台 restore caller、dialog 无产品 caller；D06/D07 撤回重发与 D13/D14 灾后恢复语义保持分离，不重启旧 run03。

共享 MySQL 3306/13316/13317/33318、外部 Vite 5173、ADB 5037、USB M71 仅观察，不连接/停止；接管时的空闲 Gradle daemon 在收口进程中已不存在，无新目标模拟器或业务服务。环境/监听/数据库命令行、Keystore 存在性及 C/D/E 余量见前后快照。未新建 datadir、AVD、大缓存或可丢弃大文件，本切片清理释放 0 字节；此前 998/999 递归清理的策略拒绝保持，不换方式重试。原 binlog、dump、身份、公私钥、回执、APK、失败原件及继承 WIP 全部保留。

## 未通过与下一步

本切片只补 Python L1 与固定隔离输入的独立消费，不重新执行产品入口，不计 D06/D07 完整 L2、D13/D14 整体、D17、E06 L2、D18 L3、O/U 整体、第8节或总目标通过。隔离公钥验证只证明保留开发输入的签名一致，不能证明原身份、现场审批或外置保管。没有统一最终候选，不能把多轮局部结果相加作最终闸门。

下一步按逐路径 SHA 核对未受影响的 Android/Chrome/HTTP/MySQL 与本轮 Python/恢复输入，选择剩余 O/U 产品组合；D16 仍缺可信合法退出依据，原模拟器/原资料/外置保管缺口继续单列。七类生产决定、TEMP 生产归档、正式签名、main、发布/部署、原库/现网保持关闭，`localDeletionAllowed=false`，总目标 active。
