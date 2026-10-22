# 提交源四路径页面与电脑文件组合

日期：2026-10-22。输入为开发提交ad11fb16cf44665dd40edd4f718889554c34f100。本片没有修改产品、测试、依赖或迁移；只增加同提交独立源组合证据，作为开发备份，不计D/O/U、第8节或完整D18验收通过。

## 输入与依赖

证据根.local/goal-20261022-resume/clean-pages-combination01/；独立提交源.local/c22p/，由git archive提取Android、Gradle、tools/pda_rescue及两个提交内E2A静态向量。归档SHA-256为ee78c46469835dcf0493cbd26980d1090028f0a86fb6a4d8713ad1ae7b661903。

1162份提交文件逐blob核同：24份原字节相同，其余1138份仅Git换行转换，规范化字节完全一致。准备阶段直接原字节比较在android/app/build.gradle.kts失败，当时尚未复制外部输入或执行测试；后用逐blob规范化核对确认，preparation-failure01.json保留，不算业务测试失败。没有覆盖、删除或重取原归档。

只复制10份现有server/build合成fixture及本机SDK指针，共11份外部输入，分别登记原路径/长度/SHA、保全原字节ZIP；没有复制主树未提交源码、正式签名配置、密钥或数据库。运行前后，1162提交文件、11份外部输入及其主树原件全部守恒。fixture是既有合成事实输入，不代表本片真实服务端或新HTTP验签链。

前片[主管组合](GOAL_PDA_SUPERVISOR_PAGES_EXPORT_CONSUMER_2026-10-22.md)的1125项清单不含server/build外部fixture，因此不能由该清单反推旧fixture运行前后守恒；本片补的是新独立运行的明确输入边界，不补写历史通过。开工40条继承WIP原字节ZIP和SHA在baseline.json/inherited-wip.zip，文档维护前40条全部保持。

## 实际执行

独立源Gradle使用新build、project-cache、XML/HTML/binary、工作目录及资源绝对路径；子进程TEMP/TMP/java.io.tmpdir为.local/cp1，不改全局环境。offline、no-daemon、max-workers=1、in-process，测试任务禁止UP-TO-DATE及结果缓存；32个任务实际执行，退出0。没有启动旧bridge、恢复确认、接收者或激活链。

| 同次实际测试类（省略包前缀） | tests/failures/errors/skipped |
| --- | --- |
| FormalPagesExportTest | 1/0/0/0 |
| IndependentPagesExportTest | 1/0/0/0 |
| RescueSignedSupervisorFileActivityTest | 8/0/0/0 |
| SupervisorPagesExportTest | 1/0/0/0 |
| SupervisorRescueCardActivityTest | 7/0/0/0 |
| TemporaryPagesExportTest | 1/0/0/0 |
| 合计 | 19/0/0/0 |

以上为同次六类，不与历史或前片16项相加。原XML、逐方法、运行时间及日志在run01/和closeout01/xml-result.json，另无损归档original-xml-log.zip。没有本片业务测试失败或skip；准备原字节检查失败另列。

四个非空页面分别执行本人FORMAL、本人TEMP、独立来源本人卡与主管TEMP卡的原页面/离线注销/快照/独立材料/复制链。各自83张Room表类型摘要前后相等，摘要保存在逐类XML及xml-result.json；不同来源输入分别固定，不能称一个共同业务事务。TEMP/FORMAL的WorkFixture提供原非空行，不经过实际扫码Activity；独立来源经过合成主机原采集页。主管越界拒绝、原记录/卡/材料守恒和秘密不出包沿本次原断言实际执行。

## 四个新包的独立电脑消费者

使用同归档提交的tools/pda_rescue，从独立源工作目录启动Python CLI；没有导入主树未提交工具。四个来源分别使用本次新包、独立解密材料及包外合成登记信任。每包三场景退出码均为可信输入0、缺包外信任1、既存目标防覆盖1；拒绝时不创建新目标、不改变既有目标，原包/材料/信任/工具字节前后守恒。

| 来源 | captures/scans/events | 有效包/整数克 | VOID/failed |
| --- | --- | --- | --- |
| 本人TEMP | 3/2/12 | 1/301000 | 1/1 |
| 本人FORMAL | 3/2/10 | 1/301000 | 1/1 |
| 独立来源 | 1/1/6 | 1/300500 | 0/0 |
| 主管TEMP | 3/2/12 | 1/301000 | 1/1 |

TEMP/FORMAL的原capture/scans字段及顺序、事件ID、来源和汇总逐项核对；独立来源原order.json逐字核同，原扫码序号/条码/状态/克数核同。四份TSV文本字段分别与对应原NDJSON相符。integrityVerified/sourceTrusted均true，businessReceiptVerified均false，没有submission/receipt；独立来源pendingReconciliation=true、serverAuthorizationVerified=false，不伪装服务端授权。

命令、stdout/stderr、输入/输出哈希及结果在run01/pc01/各来源目录和result.json。新密包与独立材料只留私有.local，不提交。电脑是独立实现消费者，作者/执行/自审仍同一助手，没有独立人员审查。

## 保存、清理与限制

本片专用Gradle缓存3513298逻辑字节无损NTFS压缩减少分配2198482字节，删除0字节，长度/SHA全数不变；D盘余量39466823680→39469023232字节。每文件原长度/SHA、精确路径、命令、退出码和回执见closeout01/cache-*.json及cache-compact.log。独立提交源、原日志/XML、失败准备记录、向量、原资料、身份/Keystore、签名回执、唯一证据、最终APK和继承源码保留。

未构建/安装APK、启模拟器或操作实体设备，未连接SQL/原库、运行真实业务HTTP/MySQL、使用正式签名、部署、Release或合main；生产开关及localDeletionAllowed=false保持。Robolectric NATIVE Room/JCA/回环HTTP/file URI不是原身份/Keystore、SAF、外置介质或现场回执。未覆盖全套Android/Room迁移、服务端IT、Web、恢复及其余O/U组合，不能称实际最终候选或完整D18。

总目标active、未完成。下一片从总目标逐项缺口与最终候选输入关系接续；复用本次六类与四包结果，不机械重跑筛选或四条页面正向，不恢复已取消链，也不把其他开发缺口移作现场待验。
