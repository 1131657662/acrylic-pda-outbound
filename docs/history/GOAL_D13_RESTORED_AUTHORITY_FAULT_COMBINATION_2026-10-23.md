# D13完整恢复后P5恢复根及可信水位损坏拒绝组合（2026-10-23）

## 输入与范围

输入提交5990136d48c63fff51a2548341a32f318388317c，固定树D:/acrylic-pda-outbound。9个代码路径仅测试、封闭runner选项及独立原件消费者；产品源码、生产配置、契约、迁移、Android/Web无改动。版本事实见[看板事实块](../ORCHESTRATION_STATUS.md)。

证据根.local/goal-20261023-resume/d13-restored-authority-fault01/，实际库根.local/e2a-cross-20260925-1649/与1650/。baseline.json/inherited-wip.zip保全40继承逐路径原字节/SHA；edited-originals.zip保存8已有测试的改前字节，其中两个最终无净变化、不提交；execution01-source.json/zip绑定2636执行源码，execution02-source.json核同。acceptance-design.md先定退出标准。edited-inputs01.json是早期草稿，最终执行以pre-execution-review及各execution的source-before/after为准。

## 有界设计与执行

原Reader反例和[前片stale组合](GOAL_D13_RESTORED_P5_STALE_COMBINATION_2026-10-22.md)不能替代恢复根/可信checkpoint损坏的活入口组合。每种故障独立新库、新run、新构建，运行内合成Java控制夹具供应，Python仅独立原件消费者；不将它称为Python生产权威、生产ops协议或独立人员审查。原helper默认分支保持。

非空历史迁移→真实HTTP原PENDING双格式→完整SQL/storage/合成验签恢复→独立API/非Web生产定时worker启用P5/cross-process effect gate→各JVM实测生产operation活租约→正常双格式导出下载→排空本片scheduler但保留JVM→第三PENDING→保全权威原字节并注入单一故障→六HTTP、显式生产poll/reminder及直接admission精确拒绝→全业务/P5树守恒→正常关JVM/认证停库→独立Python消费。

| 执行 | 隔离输入 | 实际结果 | 精确故障与拒绝码 |
| --- | --- | --- | --- |
| execution01/run1649 | DB23649、HTTP24649、build .local/s23a | IT 1测试/0失败/0错误/0跳过，67.593秒，exit0、completed=true | 仅restoreRoots改为run内错误根，CONTROL_RESTORE_SET_MISMATCH |
| execution02/run1650 | DB23650、HTTP24650、build .local/s23b | IT 1测试/0失败/0错误/0跳过，64.76秒，exit0、completed=true | 仅trusted checkpoint revision从1改2，CONTROL_CHECKPOINT_ROLLBACK |
| 两次compile | 各自独立构建输出 | testClasses/recoveryControlClasspath/report-rendering installDist均exit0 | 编译不替代业务IT |
| consumer01/02 | 分别消费两run原件 | 各exit0、stderr空 | 不重跑产品，不拼接不同故障为同一次运行 |

精确命令/参数/退出码见execution01/02-launch.json、各compile-command/integration-command、consumer01/02-command.json及stdout/stderr/XML/test-result。两种故障各自首次实际IT即通过；本片无业务IT失败。既有一次性run1648及其他旧库/旧run03均未重跑。

## 原件与守恒

- 每run恢复原179表；正常消费仅background_job、operation_event、spring_session、spring_session_attributes变化。故障前后179表/DDL/迁移/15文件完整快照相同，源全快照/原8文件保持。第三任务均PENDING、attempt0、无artifact/lease。
- HTTP每run三阶段7/16/11条，最后六条均503且精确上述各自拒绝码；显式生产poll/reminder的两类拒绝日志与直接admission同码，不能以暂停scheduler替代拒绝证明。
- 两run实际API/worker历史PID分别16100/17576、13268/22992；控制故障IT历史PID23996、6588，分别独立。每JVM实际活租约/index/process HMAC已由Python核验；故障前后P5全树字节/SHA保持，最终租约0、各两process record保留、两JVM正常关闭。历史PID不用于推断收口存活状态。
- 原身份或checkpoint字节先保存在private/p5-stale-authority/fault-original.bin；fault.json记录输入/变化SHA与目标。控制历史均只有revision1，没有推进或自动修复；恢复根故障未改其他identity字段，checkpoint故障未改identity/原记录。
- 两份XLSX均7表/2明细/0公式、原顺序/整数克；PDF各2页/2明细/0注解。四页实际Poppler渲染并view_image目视，无裁切/重叠/缺字；测试样例/非业务凭据、原/有效2包601.5kg和页码完整。两PDF分页不同但明细齐全，不要求逐像素相等。渲染/图SHA/作者审查见pdf-visual01/02。
- 两次离线mysqlbinlog各exit0，source/restore Table_map均374/408；恢复切点后source Table_map及全限定源行引用均0。按原输出字节查ASCII数据库token，不假定二进制行值为UTF8。命令/输入和输出SHA在binlog-consumer01/02.json。

| 原件SHA-256 | run1649 | run1650 |
| --- | --- | --- |
| XLSX | 2586e412abe4704993d194d58076e7d905bf1630edc05e1499e3a2a56fef038e | 84b3298e6de4180f97096e30daf023f49cc51a3b6fa5df53df0fd8bcbed42826 |
| PDF | d99808a75fbcc1d07188390847ff87da3cf4df46d6049562bc96569f18fce377 | b860abbf9b9fa4a4f2745ee5eccc650629adc8cc74e016302c9bae593325e826 |
| final dump | 743168a05a6b0bd105f0519fa4d21375e4856eefcf3063b1027f29d09141b480 | 40ad66a1c2603e27fc845686f24e0704d53e7f0901f50fdc0ec2273abdffb326 |

两次源码2636文件执行前后及收口逐SHA相同，两次输入一致；275外部依赖逐SHA保持，external-after.json与source-review01.json补足收口核验。40继承中仅授权本地看板发生变化，历史索引本片追加单独保全，其他继承原字节保持。

## 环境与空间

两run认证身份/datadir/端口/UUID见各identity-before/final原件；MySQL认证shutdown均exit0，API/worker正常关闭。收口进程/监听/ADB只读/Debug Keystore SHA与C/D/E余量另fresh实查，不沿旧PID推定。系统3306/33060仅观察、未SQL连接，内部身份未认证。未安装APK、未正式签名、未接原库/现网。

删除策略此前已拦截，本片未换通道重试。仅对确认停机、无消费者的自有.local/s23a、s23b及两run/data执行无损NTFS压缩；8218文件前后长度/SHA全部相同，减少分配451368222字节，删除0字节。D卷观测35185541120→35656085504字节，卷净变470544384字节与分配减少分列，不累计前片。命令/参数/绝对路径/源码与依赖输入/停机/进程/监听/逐文件分配均见compact-plan/result及原件。原资料、身份、原签回执、唯一证据、最终APK与未提交/继承源码保持。

## 未验、Git与接续

仅两个合成恢复后P5权威损坏组合通过；**没有验证实际错库绑定、数据库身份供应或全部入口矩阵**，也非Python生产权威/生产ops协议通过。D13–D15整体、P5 UNKNOWN/crash/OS拒绝kill、D16正常有效guard/复杂引用、D18实际最终候选与总目标第8节仍未完成。Windows及隔离合成证据不替代原身份/可信事实/现场回执、真实PDA、异地或长时。已批准四状态只读筛选沿既有原证据，不重复开发。

仅本片9代码+此报告+片前看板叙述归档+2索引共13路径作开发备份；看板只本地维护，40继承WIP保持。history索引以HEAD blob加本片精确入口暂存，不能整文件add混入继承内容。Git实际回执、dirty全清单/SHA/ZIP及覆盖本片根和完整run1649/1650的manifest见closeout/。本机被忽略原件不虚称异地备份；提交、推送、文档与压缩不计验收。

总目标active、未完成。生产开关/TEMP归档、正式签名、原库现网、main、部署/Release关闭，localDeletionAllowed=false；换机承接、多跳、接收者确认激活及旧run03不恢复。下一有界项先读取实际数据库绑定供应与入口测试覆盖，确定错库故障可验证标准和独立新run；若涉及未知可信供应则如实记录并转独立P5 UNKNOWN/crash矩阵，不用全拒绝代替正向准入。
