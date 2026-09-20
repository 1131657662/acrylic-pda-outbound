# R09：V32 TEMP证据与正式编号恢复检查

日期：2026-09-20。总调度按用户授权接管原C暂停断点，仅在`codex/r09-ops-foundation`的`docs/parallel/R09/`完成本批收口。原C会话保持暂停。**V32隔离快照恢复通过，V33未演练，完整灾后重建未实现，未部署。**

## 固定基线及修改范围

业务源码固定为已提交`87e1893a49b598bbfdacee1a20d5eb455c5a5903`，经git archive导出到新专用实验目录，Flyway V32。JAR SHA-256为`2f8fe92d32472fb20b2323e8323ee2514bfea488bc5bed7a40148072c28b7fec`；没有复制主线未提交实现或重建替换本轮JAR。主线随后稳定为`6d814f8`/V33，仅用于兼容方案核对。

本批增加TEMP原审计来源、资格与清单JWT验签、五个准备包压缩/解压摘要检查，以及FH北京时间日期、扩展流水与计数器检查；恢复监控绑定最新备份及原恢复报告。增加专库故障演练入口及测试。原V21/V28资料保留，V33返回`UNSUPPORTED_SCHEMA`。没有改正式OpenAPI、Flyway、Room、版本、server_state、认证、Job/Worker、R06业务核心或主线总进度。

## 实际验证

| 项目 | 结果与证据边界 |
| --- | --- |
| 固定源码合成场景 | 暂停前8项现有MySQL/MockMvc通过，0失败/错误/跳过；原4项提交加成功归档、连续审计归档、混合TEMP审计、未确认激活资料保留各1项；bootJar通过。本次接管复核原JUnit/XML/JAR摘要，没有重跑seed/业务测试 |
| 实际备份恢复 | 暂停前93表/198组外键恢复，结构、行数/CHECKSUM、规范化全量数据摘要及全文件/配置一致；耗时11.926秒仅为本机小合成快照，不是生产RTO |
| 事实与文件 | 13处既有文件引用，1份成功归档的2处引用，3段审计归档/15条段内事件/16条原审计；6份签名终态，5成功/1ABORT；10包/3,007,500g，4条账务/−1,203,000g，10占用 |
| TEMP与编号 | 3份原资格/15个准备包、2个原来源/2条TEMP审计；8张FH订单/1日计数器。允许计数器高于已存订单最大值，不降低或修复计数器 |
| 故障演练 | 暂停前7项R06、9项V28、11项通用检查通过，各组注入后全表/文件恢复原值；本次只复核原证据，没有重复注入 |
| Worker/API | 暂停前专用Worker完成1任务；采集器修正V32识别后读取原任务PASS，未重跑。恢复库API健康NORMAL、2263ms（实验阈值3000ms），停后端口检测通过；不是Linux服务启停或生产延迟验收 |
| 运维专项 | 接管后最终56项全部通过，0失败/错误/跳过，ResourceWarning作为错误；包含旧恢复报告不能覆盖新备份、TEMP来源/准备包、FH缺失/回退/超位及旧号兼容 |
| 最终差异 | 接管后只恢复两专用MySQL，source的93表保持；restore仅`spring_session`和`spring_session_attributes`各新增1条原API探测会话，结构未变，其余91表保持；业务摘要及双方文件/配置一致，不声称探测后全库完全不变 |
| 监控实测 | 原最新备份完整性、新鲜度、绑定的恢复报告、19310端口和磁盘全部PASS；CLI退出0。`restore_verified`只由独立证据项给出，灾后重建及本地清理字段始终false |
| 静态与文档 | 37个Python文件AST、200行/函数30行限制及配置JSON/INI语法通过；10份Markdown的55个本地引用逐一解析均存在，git diff --check及敏感材料排查通过，47个源码/测试/配置指纹见最终JSON |

新监控不会重新创建备份或恢复报告，也不把报告mtime当作演练时间。只能使用本地受控写权限下的原报告，不能以摘要校验代替来源真实性或异地取回证明。

## 失败与修正

- 暂停前Worker实际已完成，但旧采集器限定V28，第一次报`UNSUPPORTED_SCHEMA`；修正版本支持后只读取原结果，不重跑任务，原日志保留。
- 新监控测试首轮55项中的5项因夹具使用禁止覆盖的`write_json`报`FileExistsError`；改为显式更新测试自建文件后55项通过。新增第56项及最终接线后，本次56项全部通过，原失败不省略。
- 本次恢复实例的首条辅助命令有一个不存在且未使用的Python导入，启动前即报`ImportError`；去掉该导入后按原datadir/账号/端口核对启动成功，没有setup、覆盖或重跑种子。
- V21/V28历史原生MySQL“退出0但空输出”的精确根因仍未知；查询路径沿用已验证PyMySQL1.1.2，不把本次成功写成原生工具根因已修复。

## 保留材料与收尾

- 原lab：`.local/r09/20260920T084455Z-26ba55/`；备份：`backups/20260920T084822Z-45d80d/`，SHA256SUMS摘要`c0740bbb1c0592367ba06818099f440354b161b90491b2b0f36b4b7b9eeb032e`。
- 原始证据保留：`source/fixture.log`、`source/worker-probe.log`、`source/worker-v32-collection.json`、导出源码下JUnit/XML、`restore/recovered/verification.json`、三份故障演练报告及`restore/api-probe.json`。
- 新最终核对：lab根`final-audit-v32.json`与`monitor-v32.json`；启动日志按唯一时间戳保留。脱敏汇总为[VERIFICATION_V32.json](VERIFICATION_V32.json)，当前工具/测试/配置指纹为[SOURCE_HASHES_V32.json](SOURCE_HASHES_V32.json)。不提交原库、SQL、密钥、配置凭据、日志或JAR。
- 接管后的两专用MySQL PID24824/PID9720已通过原实例身份核对后停止；19309/19310/18309均无监听，无本lab Java/MySQL进程。本次未启动API/Worker、Gradle、模拟器或M71，不操作共享MySQL、其他工作树与现网，不重试既有审批阻断。

## 交接与剩余门槛

两份设计：[恢复控制与写屏障](RECOVERY_CONTROL_CONTRACT.md)、[恢复证据及引用](RECOVERY_EVIDENCE_CONTRACT.md)。它们是接口提案，没有服务端恢复API或共享接入。当前可以交总调度审查并由主线固定下一检查点。

下一批先固定V33提交另建专库，覆盖正文前ABORT不造订单/assignment/摘要、不放实物占用，整包/分块待审及唯一正式化目标/FH计数器；不混入本V32报告。之后需要主线明确新恢复模块文件归属、RecoveryGate/证据接口、事务锁序、数字代次到库外UUID的历史映射与旧客户端策略、迁移编号及组合验证。截点外FH号的可信完整高水位缺口单列，不能按现存订单MAX猜测恢复发号。

完整跨截点成功/取消/调整链重建、故障代传、控制CAS/全写入口与Worker栅栏、异地加密防覆盖/PITR、Linux监控部署、R07/R08引用和现场验收仍未完成；`localDeletionAllowed=false`保持。本有界批次结束后等待总调度审查，不自动进入V33或共享业务开发。
