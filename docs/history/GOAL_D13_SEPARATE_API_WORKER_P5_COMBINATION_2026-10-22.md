# D13 独立 API / 非 Web worker / P5 有界组合

本片仅补独立进程组合测试；产品源码、生产配置、契约及迁移未改。最终 execution03 的实际 IT 为 **1/0/0/0，64.202 秒，exit0**，独立 Python 原件消费者通过。不是 D13 整体、D18 最终候选或第8节完成。版本事实见看板生成块。

## 输入、依据与影响

输入提交 `69c21d32d21185ed9fba1e93854b24bf931b124c`，树 `D:/acrylic-pda-outbound`，分支 `codex/development`。本聊天唯一源码/索引/看板写者；其他清理聊天仅磁盘范围。40条展开继承WIP原字节、SHA和diff在 `.local/goal-20261022-resume/d13-split-worker01/baseline.json`、`inherited-wip.zip`、`tracked-before.patch`，不整文件混入提交。

旧 [worker 准入](GOAL_D13_WORKER_ADMISSION_2026-10-17.md)及[退役](GOAL_RECOVERY_LEASE_RETIREMENT_L1_2026-10-19.md)测试使用同一 JVM 中两个 Spring context；旧独立 restored worker 入口是 Web 且 scheduler 关闭，不能代替此处独立 API 与生产非 Web scheduler 的组合。旧测试不重跑，历史成功/失败不合并计数。

| 新路径 | 影响及必跑 | 实际结果/复用边界 |
| --- | --- | --- |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aSplitWorkerExportIT.java` | 新隔离 MySQL、非空迁移、真实 HTTP/Session、文件、P5组合 | execution03 实跑；旧 HTTP/helper/合成TEMP输入按 source-before/source-after SHA绑定 |
| 同目录 `E2aSplitWorkerMain.java`、`E2aSplitWorkerProcesses.java` | 独立 API/worker JVM、非Web bean、PID、显式私有选项及自有进程生命周期 | 两进程实际启动/干净关闭；不是仅创建两个context |
| `server/src/test/java/com/acrylic/outbound/recovery/RecoverySplitWorkerChecks.java` | 实际 scheduler 操作根、根外JDBC拒绝、独立JVM排他写者拒绝、排空 | 持根/退根及末SQL真实回滚；没有授予新恢复或生产资格 |
| `docs/parallel/R09/tests/run_d13_split_worker.py` | 全新库/端口、独立build/report、runtime依赖、输入守恒、认证停机 | 一次性编号1641/1642失败分别保留，1643成功；原库不连接 |
| 同目录 `verify_d13_split_worker.py` | 独立解析原HTTP、XLSX/PDF、快照、P5及停机dump | 不启动服务/数据库；原顺序/精确克数/原件SHA核对通过 |

证据根 `.local/goal-20261022-resume/d13-split-worker01/`。execution03 绑定 **2616个**实际源码/测试/运行器输入，执行前后及收口核同；**275个**外部字体/浏览器文件原字节核同，见 `source-review01.json`、`execution03/source-before.json`、`source-after.json`、`external-before.json`、`external-after.json`。输入含保全的继承测试源码，不能声称是清洁最终候选。无 Android/Web 产品行为变化，本片没有重跑旧 Android/普通全回归/四字号或旧取消链。

## 实际运行与结果

命令、参数和退出码保存在每轮 `compile-command.json`、`integration-command.json`、日志、原XML及 `test-result.json`；运行入口：

```text
python docs/parallel/R09/tests/run_d13_split_worker.py --run 1643 --port 23643 --http 24643 --output .local/goal-20261022-resume/d13-split-worker01/execution03 --build .local/s22e --font .local/s22b/r/generated/resources/NotoSansCJKsc-Regular.otf --browsers .local/worktrees/deploy-remediation-20261020/report-rendering/build/browsers
```

字体从已保留文件读取并核固定SHA；渲染器 `installDist` 在本片独立build目录按实际源码重建。浏览器只读复用已有安装，不改其他树或重装工具；所有本片临时目录在D盘，未改变全局环境。

- 新建合成隔离库1643，实际数据库/用户/端口/datadir/binlog/UUID由 `identity-before.stdout`、`identity-final.stdout`绑定；历史非空种子后迁移到源码定义的目标。共享3306/33060仅观察，未SQL连接，内部库身份未认证。
- 运行时API PID23176、worker PID20560，彼此及IT JVM不同；worker context非Web，实际 `BackgroundWorkerProcess` bean存在。P5进程记录和ready JSON与实际PID核同。这里的PID只作历史证据，不推定后续存活。
- 两格式各12次真实HTTP：未登录401、CSRF/登录、跨仓404、同键同ID、异文409、任务SUCCEEDED、下载；每份HTTP正文摘要核同。实际调度启动renderer，下载字节与API/payload元数据及固定快照绑定。
- XLSX独立回读7表、2条原明细、无公式，原采集顺序及精确整数克保持；PDF独立解析2页、2条明细、无注解、顺序/快照元数据保持。两快照订单事实相同。不是原生Excel打印或人工视觉验收。
- 合成probe由真实scheduler执行，根是WORKER_JOB/SERVING；根外JDBC被拒，IT JVM的生产排他writer acquisition被另一JVM持根阻断，current控制文件字节不变。最后一条真实SQL主键重复使完成事务回滚；任务记FAILED、probe表0行，操作根及子操作释放。probe只作故障注入，不替代业务handler。
- 两context停止且原生P5 lease索引为空，仅余index；两进程生命周期关闭后，第三个独立后继JVM PID16736启动检查并退役，输出STARTED。不是生产完整API/worker重启后的全部业务复验，也没有在本片实际恢复数据库。
- 三个新隔离库均认证shutdown exit0，进程退出与所分DB/HTTP端口零监听复核；无本片Java/Python/renderer残留，不操作共享系统MySQL或其他项目设备。

独立消费命令用现有 bundled Python（含pypdf），完整路径/参数见本机回执；不是独立人员审查。关键SHA-256：

| 原件 | SHA-256 |
| --- | --- |
| `execution03/integration.stdout` | `86e65b1faf1f7b9ccfa88655ad28496be76a941eb49d7a2f581823fc51ec9466` |
| 原IT XML | `102cafcb7fa96981c0a32657174f47839910c0f111f9cfc3cd8bd3703932b7ef` |
| `independent-consumer01.json` | `60934b1e4f8a3e64349d9851638136a05948f09370bf515b4736ac1b23f150a8` |
| 下载XLSX | `79899b263e31a5a45c060c9d3b430bdd1c264a9ec28180f350abbef65b7959d7` |
| 下载PDF | `28473ba1e862659a945f1787bafdf1f858f3bc80be225f52a4db294a893e5523` |
| 隔离1643终态dump | `b0153a23804321f056b240ae32d0b59fc6ad4253ac3f31832df281334a973fdd` |

## 失败、保全与清理

1. execution01/run1641：重复worker开关在Spring被组合，worker bean不存在，启动断言失败；尚未执行导出。修复私有选项去重，增加重复key断言。此根因1次。
2. execution02/run1642：两进程启动后首个导出返回503 EXPORT_UNAVAILABLE，磁盘原renderer install/browser目录已不存在。属于本片运行依赖遗漏；后续按独立build重建renderer、复用实存浏览器和固定字体。此根因1次。两轮原XML、日志、私有输入、停机库及失败源码ZIP均保留，不把失败改写成功或复用失败库。
3. 系统Python及旧控制venv缺pypdf，仅import探测失败；改用已有bundled Python，不重复安装。本片数次运输脚本语法错误均未执行/未改变磁盘，不计业务失败。

原资料/身份/Keystore/签名回执/唯一证据/最终APK及继承WIP保持；此前删除审批拒绝不换包装重试。仅本片已结束build及datadir的11,643个可重建文件无损压缩，分配减少606,090,976 bytes，逐文件长度/SHA前后一致，删除0；明确路径/命令/输入与结果见 `compact-plan.json`、`compact-result.json`及 `compact.stdout`。D盘观测37,689,335,808→38,312,226,816 bytes，与精确文件分配减少分开报告，不累计前片或其他清理聊天。完整原看板字节在 `board-before.md`及哈希快照，历史看板叙述归档省略生成事实。

## 自审、Git与未完

作者自审：无产品代码/生产开关改动；子进程只连身份核实的本片新库，私有密码/合成anchor仅在ignored私有选项中；源码及报告无硬编码秘密/真实用户资料。明确路径提交开发备份，历史索引只暂存本片入口，保护其继承字节；看板本地维护，提交/推送回执及最终40条WIP全SHA见 `closeout/`。

本片不含stale权威、崩溃/UNKNOWN、renderer故障、全部入口/Android/Chrome UI/恢复数据库/外置介质或最终候选组合；旧相关报告各按原范围，不据此扩大结论。取消的换机承接/多跳/接收者确认激活及旧run03不恢复。生产开关、正式签名、原库现网、main、部署/Release关闭，`localDeletionAllowed=false`。

总目标保持active、未完成。下一步从D13–D15真实恢复输入、跨入口矩阵选尚未覆盖的组合，优先将独立生产进程与恢复原件/后继的实际消费者绑定；不重复此处正向和简单文件反例。D16正常有效guard目标/复杂引用及D18最终候选、第8节闸门继续未完，不以开发备份或本片通过代替。
