# D13/D15 完整恢复后的独立生产 scheduler 与后继消费者

本片新隔离 IT **1/0/0/0，103.339秒，exit0**；独立 Python 原件消费者 exit0。两个原 HTTP PENDING 导出任务随 SQL、storage 与合成验签材料恢复，由独立 API / 生产非 Web 定时 worker JVM 完成；干净退役后，另一对完整 API / worker 经原键取得原成功任务及逐字相同的 XLSX/PDF。没有测试显式调用 runOnce，没有新增产品功能。

仅 D13/D15 有界合成组合，不是整体、D18 实际最终候选或总目标第8节完成。版本事实见看板生成块。

## 输入、保全与缺口

- 固定树 D:/acrylic-pda-outbound、codex/development，输入/开工远端 135a8f2156aff2ca877720a45bebf98345543f15；main未改。单助手写源码/索引/看板，同项目清理聊天明确避开这些文件，无代理。
- 根 .local/goal-20261022-resume/d13-restored-scheduler01/。baseline.json、inherited-wip.zip、status-before.z、tracked-before.patch保全40条继承路径原字节/SHA。edited-originals.zip保全两helper原件，runner-head-exact.py保全Git原blob，文档原字节在closeout/docs-before.zip/json。
- [旧恢复worker](GOAL_D15_RESTORED_EXPORT_WORKER_2026-10-16.md)是另一Web JVM，定时worker关闭且测试显式调用runOnce；[上一片独立worker](GOAL_D13_SEPARATE_API_WORKER_P5_COMBINATION_2026-10-22.md)未实际恢复数据库。本片只补两者尚无的组合，不重跑旧670/1643或旧run03。
- execution01-source.zip/json保全2620条实际源码/测试/运行器；source-before/source-after逐项SHA一致。external-before.json绑定字体及浏览器275文件，结束逐项SHA一致。字体SHA固定为2c76254f6fc379fddfce0a7e84fb5385bb135d3e399294f6eeb6680d0365b74b。
- 实际树含继承测试输入，不能称clean最终候选。源/验证收口前，继承40路径中除授权看板维护外39条原字节均保持；索引随后仅追加本片入口。

| 明确改动路径 | 影响层与实际验证 |
| --- | --- |
| server/src/test/java/com/acrylic/outbound/e2averify/E2aRestoredSchedulerIT.java | 全新非空迁移、SQL/files恢复、首轮及后继组合，实际1项 |
| 同目录 E2aRestoredSchedulerExports.java | 原PENDING/payload、真实HTTP原键/异文、双格式下载 |
| 同目录 E2aRestoredSchedulerConservation.java | 逐表/DDL/迁移/原文件守恒、P5退役 |
| 同目录 E2aSplitWorkerMain.java、E2aSplitWorkerProcesses.java | 封闭测试scope允许新隔离_restore目标；身份/显式URL/storage绑定；旧split默认保持 |
| docs/parallel/R09/tests/run_d13_split_worker.py | 封闭IT类选项，默认旧类保持；新输出/独立build/身份/认证停机 |
| 同目录 verify_d13_restored_scheduler.py | 不启动服务/数据库，独立消费HTTP/SQL快照/P5/Excel/PDF |

产品源码、配置、契约及迁移未改。原HTTP、恢复工具及renderer按source清单逐路径SHA绑定。旧split分支没有重跑，其历史结果只在原输入范围引用；不把本次恢复组合当作旧probe末SQL回滚重验。

## 实际隔离运行

全35工作树核run1644不存在，fresh端口无占用后分配DB23644、HTTP24644；新.local/e2a-cross-20260925-1644/及build .local/s22f。既有字体/浏览器只读复用，renderer installDist从本片输入重建。TEMP/TMP/Java临时、build/报告均在D盘，不覆盖旧报告/APK。

精确命令见execution01-launch.json、execution01/compile-command.json与integration-command.json。testClasses/classpath/renderer installDist实际exit0；IT原XML为本轮1 test/0 failure/0 error/0 skip，非仅build结论。execution01是唯一业务运行，无业务失败或旧库重用。

1. 新库经历史非空种子迁移到源码目标。identity-before/final实核库/用户/端口/datadir/binlog/UUID；系统3306/33060仅观察，未SQL连接，内部库身份未认证。
2. 合成TEMP用既有RecoveryExportInput/TemporaryConfirmationFixture准备，非现场产品确认/原身份。源真实HTTP登录/CSRF后产生两个PENDING原任务，无定时worker在源库消费；共7请求。
3. E2aArchiveSnapshotRestore实际dump并导入空伴随schema，复制8个storage原文件及合成private/public验签材料。导入后179表、DDL、迁移历史及原文件与源快照严格一致，才启动消费者。
4. 首轮API PID26020 / worker PID15608；后继API PID23176 / worker PID17316。四PID不同，ready/command/正常closed原件保留。worker为非Web、真实BackgroundWorkerProcess bean存在且生产scheduler实际执行；无测试显式runOnce。
5. 两轮恢复HTTP各16请求：401、登录、跨仓404、同键原ID、异文409、成功任务metadata/下载。每任务attempt=1；除artifact外原payload及snapshotHash保持。首轮日志每原ID仅一次SUCCEEDED，后继日志没有再次执行原任务。
6. 两轮均正常排空/关闭，P5 lease索引空且目录仅index.json。首轮2个/最终4个进程登记，完整后继实际消费同一恢复库，不只是启动检查。P5为合成权威；其config引用的.local/control-java-cases原件另绑定到最终清单，不把private目录冒充完整权威。
7. 最终恢复库仅background_job、operation_event、spring_session、spring_session_attributes变化；其余175表、迁移历史/DDL保持。仅operation_event自增计数作明示规范化。两轮间任务全行及14个最终storage文件保持；8个恢复前原文件和两验签材料始终相同。
8. 源最终全快照等于恢复前。认证停机后离线mysqlbinlog解码exit0：源374、恢复419个Table_map；首个恢复表事件后源Table_map和源全限定行引用均0。原binlog/dump保留，未重启/重连库。

## 独立原件消费

使用既有bundled Python，精确路径/参数见consumer-command.json；这是同一助手的独立工具消费者，不是独立人员审核。

- XLSX独立ZIP/XML解析7表、2条原明细，无公式/外部链接；原顺序、文本代码/ID及精确整数克保持。
- PDF独立解析2页、2条原明细，顺序及snapshot metadata保持、无注解；不是原生Excel打印或完整人工视觉验收。
- 两轮同格式文件SHA相同；HTTP原body、view、payload artifact、文件长度/SHA与snapshot SHA绑定。
- source-review01.json、original-output-binding.json、binlog-consumer.json、independent-consumer01.json保留完整核对；消费者不修改原证据。

| 产物 | SHA-256 |
| --- | --- |
| integration.stdout | 17340e1f193276886648c0a71d2b35c767b47fd5d0e5fb5ccf0d4a78b889dec3 |
| 原IT XML | 471e248637314fde0a3600043a494f94298c864eb84dc8248f5760c2907dd20e |
| independent-consumer01.json | 46bf6ab8c51bdcf076ad98ec8279ba76064ebda2a0b24633fcc285f0f49a81c7 |
| archive-snapshot.sql | 46907e424c2e70692789b475c4498dbd651d5609fb12c5374468326fdd3edc5d |
| 终态双schema dump | 0c9af8ac43f8ed8b253e00a7c1fbe4cfb5500da8f7ef86f6a45f824beec27305 |
| XLSX | cacce412b9e918d39fde0ea3bb50f93c004433b3b9a065442d7b194016c53a9e |
| PDF | ac77d8a8079e2fc55c8e40a7fa26142e426fb01dcba94e069fc906c15bac2168 |

## 收口与限制

本片MySQL认证shutdown exit0，fresh无本片Java/renderer/MySQL及所分DB/HTTP监听。共享MySQL、日常Chrome和其他项目进程/设备未操作。ADB仅枚举M71 offline/MI6 device；无模拟器操作、Android构建、真机或正式签名。

只对已结束的s22f build与1644/data共4090文件做无损NTFS压缩：分配减少226319382字节，删除0；所有长度/SHA保持。D观测37690728448→37926641664字节，卷余量和分配变化分别记录，不累计旧片。compact-plan/result/stdout及runtime/监听/停机回执保留。首次预检查含已完成只读shell而拒绝，未执行压缩；重新独立采样确认reader退出后执行，未削弱进程guard。工具运输/脚本文法失败未执行业务，不计业务失败。既有删除策略拒绝不重试、不绕行。

保留原资料、身份/Keystore、原回执/签名、唯一证据、最终APK及继承改动。七类生产决定/TEMP归档、正式签名、原库/现网、main、部署/Release关闭，localDeletionAllowed=false。换机承接、多跳、接收者确认/激活及旧run03不恢复。

未覆盖stale/UNKNOWN、crash、renderer故障、全部入口撤权/竞争/末SQL的新恢复组合、真实灾后可信批准或外置副本。旧报告各按原输入适用，不拼成整体通过。D13–D15完整入口、D16正常有效guard/复杂引用、D18实际最终候选和第8节仍未完。下一项按矩阵选实际缺口，不重跑本片。

只暂存本片明确路径及本报告/短索引，标题标开发备份；提交不计验收。看板保留本地；历史索引仅暂存本片入口，继承原字节保持。Git/远端/全部WIP和最终证据SHA见本片closeout；总目标active、未完成。
