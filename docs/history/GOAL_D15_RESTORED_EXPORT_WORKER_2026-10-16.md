# D15 恢复后原导出任务实际执行与只读补核

本页记录有界受影响组合的开发备份。真实 HTTP 创建的待处理任务已随数据库、文件及验签材料恢复，并由另一服务 JVM 的产品 worker/renderer 执行、经 HTTP 下载与 Python 回读。原主驱动最终 exit1 保留；修正守恒断言后只读补核通过，未在旧库重跑业务。本页不代表 D15 整体、E07 整体 L2、D18 L3 或总目标完成。

## 输入、写权与复用

- 树 `D:/acrylic-pda-outbound`，分支 `codex/development`，输入 HEAD/实核远端 `966113d88de6b18ebad08336abfa1fdc5ea8779c`。本聊天 `01a13a57-8082-7dd2-a08f-987483dcda4b` 是源码/看板唯一写者，无子代理。
- 全部 tracked/untracked 原字节、长度/SHA、binary diff、未跟踪副本、worktrees、旧看板、写权和进程/监听/磁盘保存在 `.local/goal-20261016-resume/input06–08.json` 及同号附件。input06 SHA `7874240acd5c2ab9e6687f838260f8faa395bd1910e41b2c9af4bdda1901783f`；input07/08 共5391项，SHA同为 `63611e0656c4b548c386c7f536337193ab5b9beb8259d7ea7786d06d7dbaeeab`。
- 证据根 `.local/goal-20261016/d15-restored-worker01/`。`reuse-input01.json` 固定1141个产品/构建/helper路径及逐项SHA，清单SHA `4009b6c5ba05a599b958ff19633206a72671f9e6c7dc69e7652e46a054ccf7c4`；已与本批input06及 `.local/goal-20261016/e07-formal-repository01/input06.json` 逐项核同。原667事实来源见 [E07 产品提交仓库恢复组合](GOAL_E07_FORMAL_REPOSITORY_RESTORE_COMBINATION_2026-10-16.md)。
- 本次仅新增三个测试入口；依赖本树继承的未提交 `E2aTestPorts`、`CorrectionExportHttpClient` 等helper，逐项SHA见上述清单。不将其混入本提交，也不声称该提交脱离记录的工作树可独立复现。

| 改动路径 | 影响层 | 必跑清单 | 复用依据（路径+SHA） |
| --- | --- | --- | --- |
| `server/src/test/java/com/acrylic/outbound/inventory/RestoredExportWorkerChecks.java` | 真实HTTP登录/CSRF、任务幂等、产品worker/renderer/下载 | PENDING创建、同键重放、异文409、恢复后原任务成功且仅执行一次、HTTP原字节下载、固定快照守恒 | 1141路径清单含exports/jobs/report-rendering及HTTP helper SHA；原667 dump见下 |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aRestoredWorkerProcess.java` | 独立恢复服务进程、隔离身份 | 固定新根/端口/schema/datadir/用户/日志身份；另一JVM实际执行、正常退出、完成marker | `reuse-input01.json` 中产品与恢复helper SHA |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aRestoredWorkerRestore.java` | SQL/私有文件恢复、全表守恒、只读证据消费者 | 全库/文件恢复、表/DDL/迁移历史及允许变化核对；修正后只读补核原记录 | 原667完整dump SHA `fa024e4683d9471fd329107614993d4bec0368a6c919ae333f9bb363a8bcca30`；清单中恢复工具SHA |

三个入口均为 main 驱动，不是 JUnit；本页没有新增 JUnit/XML 数量结论。未修改产品、Android、网页或迁移，直接产品入口在本切片已实际运行；其余未受影响链按上述输入复用，不将历史通过合成最终候选通过。

## 实际业务与恢复结果

1. 新670根 `.local/e2a-cross-20260925-670`，DB19380、HTTP19440，预留19441/19442。源schema `acrylic_e2a_cross_20260925_670`、伴随恢复schema后缀 `_restore`；分配前核未占用、独占bind、进程及C/D余量，详见allocation670和run670-command。
2. 源进程21832实际登录、取得CSRF，通过HTTP创建 PENDING 导出；同键同文返回原任务，同键异文409。任务 `01a143ce-c2a5-746b-8d1b-3fff69744739`，attempt为0；保存原payload、请求、响应与固定snapshot。
3. 完整dump和文件/验签材料恢复到空伴随schema。恢复服务为另一Java进程18896，生产定时worker保持关闭，由测试显式调用产品 `BackgroundJobWorker.runOnce`；产品handler/renderer实际完成原任务。真实HTTP下载XLSX成功；再次worker无任务、同键请求仍指向原成功任务、attempt为1。
4. 原/恢复固定snapshot逐字相同。XLSX为13696字节，SHA `07eacc66b9a0dd57ed61c459e58a16e81b910d24539594dc541478b36305ed3d`；下载字节与产品artifact长度/摘要核验结果一致，子进程exit0与 `restored-worker-completed.marker` 保留。
5. `pending-restored-before.json` → `restored-worker-after.json` 覆盖179业务表（迁移历史另存）：229→235行；175表逐值相同，仅 `background_job`、`operation_event`、`spring_session`、`spring_session_attributes` 变化。原14条审计逐条保留，仅新增 WEB_LOGIN、REPORT_EXPORT_GENERATED、REPORT_EXPORT_DOWNLOADED。任务仅status/attempt/updated_at及payload的artifact允许变化；DDL除审计自增值15→18外相同，迁移历史不变。原8存储文件逐字保留，只新增该任务renderer.log与report.xlsx。
6. Python独立解析XLSX ZIP/XML，七表、无公式/外部链接；两条原包 `003X37AC521` / `571X37AC521`、前导零代码003、原设备/订单/输入ID、序号1/2、300500/301000克及总601500克均保持，产品计划/元数据/空调整与异常页一致。
7. 离线mysqlbinlog解码exit0，`binlog670-decoded01.log` SHA `8c5e100b1724042761af4c452a6da233b66ddbdaa8ae67414c25e3ce385a1444`。原bytes的ASCII事件头独立解析：源337、恢复349行事件，恢复切点后无源schema写入/引用，补核主驱动未到达的源最终守恒断言。未改写binlog原件或行值。

## 失败、修正与复验

- 669在导入过程中缺SET_USER_ID权限而失败，已发生部分SQL写入；完整保全183文件/191408208字节，双次hash/长度相同，未复用。不是业务通过。
- 670业务链完成后，主驱动因会话表排除项大写、实际表名小写而在守恒检查exit1。完整日志还显示审计INSERT推进自增计数。修正为Locale.ROOT小写比较、全表集合大小检查，且只对operation_event的AUTO_INCREMENT数值归一化；其余DDL严格比较。
- 早期“worker尚未运行”的观察已被完整日志、子进程完成marker及XLSX纠正；`failure-corrections01.json` 明确记录。preserve670模板“No new dump”也不准确：670已有完整 `private/archive-snapshot.sql`，原模板保留并在纠正文件说明，不能当作无dump。
- build02因路径替换错误失败，build04因JsonNode/ObjectNode类型失败；修正后build05、最终build06离线testClasses/classpath均exit0。UTF-8/GBK文本读取binlog两次失败保留，最终按bytes检查事件头成功。
- `--verify-recorded` 只读固定670的JSON/marker/snapshot，不建立Spring/数据库连接。recorded-java01/02均exit0，consumer01/02均通过；未重启670、未重跑业务、未分配671。consumer02-result SHA `34804baa05b51c7a57e04dfebafdcae88bfce6b1e92eef67a67ce2cb39cba0c6`；recorded-java02-result SHA `56f3a341691e1896915daf0891b00c0f43011b51dea622df2818cf5f0fc0c71a`。保留原driver exit1，不将只读补核写成全程exit0。

## 保全、环境及限制

- 670认证shutdown，connected-identity与shutdown-identity相同，server.err.log正常停机；19380/19440–19442零监听，无本批Java/MySQL残留。583文件/258995013字节双次hash/长度不变，清单 `failed670-hashes01.json` SHA `36ff784804eedc7eeab1d56a1324113bebc9ea57a19c564e000a760d64a1ebf2`。原datadir、dump/binlog、HTTP、任务、XLSX及失败日志全部保留。
- 原667的604文件/261439064字节仍不变，清单SHA `180c908a8966ea3bf0a64a4355ad49e521b6a78b2ab4e145d363d5417f8042c3`。consumer01/02再次核对原667和670逐件相同。
- input07采样C/D余量82969149440/28969144320 bytes；续核input08附完整进程/监听/磁盘。共享mysqld4632/5440身份未知，仅观察不连接/停止；原TEMP AVD emulator12912/qemu12108及ADB保留。没有新产品APK、Chrome、真机、外置介质或Linux资源约束结论。
- 清理聊天全部窗口已释放，最终报告 `.local/disk-cleanup-20261016-round4/REPORT.md` SHA `2563abbd268484c37263cf36a369c7b3ace4155a6369940206fc55631efe6e8a`；机器回执 `completion-receipt.json` SHA `a51db8d1abeefea1eeea8beb9cdf6907e02b4ceb65bc39c757a01314dcde839c`。共17957可重建重复/缓存文件、10757603054逻辑bytes精确删除，donor保留；三冷ram.img压缩SHA/长度不变。35冷QCOW：23完成SHA/长度一致，第24调用中断后只读SHA/长度补核通过，25–35未执行，不将原调用称作原子完成。D卷余量异常下降后回升，原因未明；最终C/D为82963951616/45224742912 bytes，D较清理起点净增23421804544 bytes，不能将逻辑删除或allocated变化当作卷净释放。无后续清理进程/变更，未删除失败库或唯一证据。
- 七类生产决定、TEMP生产归档及 `localDeletionAllowed=false` 保持关闭；无main、部署、Release、原库/现网或正式签名操作。合成身份和隔离恢复不冒充原PDA现场事实。

下一项继续D15可隔离资源约束/进程启停与升级回退缺口，并按输入SHA推进E06/E07/E08受影响组合和E09实际候选。D16有效guard仍保护，不删除guard制造正常成功目标清理资格；其余未验和现场限制按看板保留。
