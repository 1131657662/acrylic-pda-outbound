# D13/D15 恢复后渲染依赖失败与完整后继重试组合

本片实际新隔离IT **1/0/0/0，109.752秒，exit0**；独立Python原件消费者exit0。完整恢复后的两个原PENDING导出任务，遇本片明确注入的渲染依赖不可用，各实际尝试3次后FAILED；完整后继API/生产非Web worker经真实HTTP创建原快照重试，各一次成功。原失败任务全行、原快照、源库和原件保持。

仅D13/D15合成有界组合，不是D13–D15整体、D18实际最终候选或第8节完成；版本事实见看板生成块。

## 输入、依据与改动

- 固定树D:/acrylic-pda-outbound、codex/development，输入/实核开发远端f86b49c7351b977c0403f10e0bf75c12f5f800eb；main未改。单助手唯一源码/索引/看板写者，无代理；清理聊天仅磁盘，进度聊天只读。
- 证据根.local/goal-20261022-resume/d13-restored-retry01/。baseline.json、status-before.z、tracked-before.patch、inherited-wip.zip保全40条展开继承WIP/SHA/原字节；edited-originals.zip/json保全既有runner与文档，board-before.md为替换前原看板。
- [前片恢复scheduler](GOAL_D13_RESTORED_SCHEDULER_COMBINATION_2026-10-22.md)只有恢复成功任务与完整后继正向；[旧重试](GOAL_D13_EXPORT_RETRY_2026-10-17.md)没有本片实际完整恢复后的独立进程依赖失败组合。复用既有产品机制与测试工厂，不重跑旧1644/1643/1567或旧run03。
- acceptance-design.md在改动前固定注入及退出标准。仅将新worker指向本片新建空lib，令真实子Java找不到ReportCli；不删除、移动、改写既有renderer/browser。不是渲染业务逻辑缺陷、OS拒绝kill、stale或UNKNOWN恢复。

| 明确源码路径 | 范围与实际验证 |
| --- | --- |
| server/src/test/java/com/acrylic/outbound/e2averify/E2aRestoredRetryIT.java | 非空迁移、原PENDING/完整恢复、首轮真实依赖失败、完整后继、守恒及P5退役 |
| 同目录E2aRestoredRetryExports.java | 原请求/状态/下载拒绝，真实HTTP原快照重试/原键重放/冲突、双格式文件 |
| 同目录E2aRestoredRetryChecks.java | 仅改一次性worker私有配置、原失败任务全行/原件守恒 |
| docs/parallel/R09/tests/run_d13_split_worker.py | 封闭可选新IT类；旧默认保持，独立build/新库/认证停机 |
| 同目录verify_d13_restored_retry.py | 独立回读原HTTP/表快照/P5、六次原渲染失败、XLSX/PDF和元数据 |

产品源码、配置、契约、迁移、Android和Web未改。本次没有重跑普通全回归/Android/Chrome；本片测试实际执行生产恢复后任务、HTTP、worker与renderer入口，不能因此免除受影响D整体L2或最终L3。

execution01-source.zip/json绑定2624源码/测试/运行器，source-before/source-after及收口逐路径SHA一致；字体/browser共275外部文件执行前后保持。实际输入仍含继承测试文件，不能称clean最终候选。既有生产开关未改变，合成TEMP事实/身份/签名不冒充原设备或现场审批。

## 实际运行

分配前核35工作树无run1645，新DB23645/HTTP24645空闲，使用新.local/e2a-cross-20260925-1645/及独立build.local/s22g。全部临时目录/构建/证据在D盘；复用已装字体/browser，不重装共享依赖、不修改全局环境。精确参数见allocation-preflight.json、execution01-launch.json、compile-command.json与integration-command.json。

1. 新隔离MySQL先迁移历史非空种子再迁到源码目标；identity-before/final实核schema、用户、端口、datadir、binlog和UUID。系统3306/33060仅观察、未SQL连接，内部身份未认证。
2. 源真实HTTP创建XLSX/PDF两个原PENDING任务，attempt0；源无worker消费。SQL逻辑全量dump、8个storage原文件及两份合成验签材料实际恢复到空伴随schema，179表/DDL/迁移历史及文件先核同。
3. 首轮独立API PID11360、非Web worker PID9004。仅worker私有renderer-home指向新空lib；API仍用实存renderer。每原任务实际两次RETRY_SCHEDULED、第三次FAILED；六份renderer原日志均有ReportCli类不可用记录。原payload无artifact、HTTP409/EXPORT_NOT_READY拒绝下载；同原创建键仍原任务ID。
4. 首轮两JVM正常排空退役，保存restored-failed.json；P5 lease索引空且目录仅index，进程登记2项。随后完整后继API PID25916/worker PID17868启动，四PID不同。
5. 后继真实HTTP对失败原任务请求重试：新任务ID、retryOf与原snapshotHash绑定，同重试键原ID，另一原任务使用同键409/IDEMPOTENCY_CONFLICT，跨仓404，未登录401。两新任务均attempt1成功，实际下载XLSX/PDF；worker日志每重试ID一次SUCCEEDED，没有再次执行两个原失败ID。
6. 原两个FAILED任务在重试后全行逐字等同失败快照，含尝试数、payload、租约清空和时间字段；最终恰有4个导出任务。恢复库仅background_job、operation_event、spring_session、spring_session_attributes变化，175业务表/迁移历史/DDL保持，仅审计AUTO_INCREMENT作明示规范化。8原storage及合成验签材料保持，失败阶段14文件在最终20文件中逐字保留。
7. 源最终全表/DDL/迁移/文件快照严格等于恢复前；认证停机后原binlog离线解码exit0，源374/恢复439个Table_map；首个恢复表事件后源Table_map与源全限定行引用均0。首个只读解析误按无引号schema格式而失败，修正解析同一原件后核同，不重解码或重跑业务。
8. 两轮均正常关闭，P5最终索引排空、4进程登记；authority-originals.json另绑定.local/control-java-cases/case-15755587181464952053/authority的7份权威原件。MySQL认证shutdown exit0，fresh进程/所分监听无残留；不操作共享服务/其他任务或设备。

## 独立消费与保全

独立Python消费者不启动服务/数据库，只读实际原HTTP、SQL/文件快照、P5及下载产物：7个源HTTP、11个失败阶段HTTP、21个后继HTTP逐响应正文SHA/状态核同。XLSX7表/2条原明细/无公式和外部链接，原顺序、文本ID/代码/条码和整数克保持；PDF2页/2明细/顺序/元数据保持且无注解。作者/执行/自审为同一助手，工具消费者不代表独立人员审查或原生Excel/现场视觉验收。

| 原件 | SHA-256 |
| --- | --- |
| integration.stdout | 15da17c5225ddf3483a788071f1e067f6844e84f0e20249445e6f152f26a2a54 |
| 本轮原IT XML | bb8f415ae33d28f04da2a367c0ec22260d7914f0664deab08274ed0b7135798f |
| independent-consumer01.json | 58a2cf52a8fcb700e894d749fea179e8b244ff59f189089839abbd9174a7f53f |
| 原恢复输入archive-snapshot.sql | 0b47534a6b4731fc205c2a5f74d7cc05007cef37951c80de520c64b422a9366c |
| 终态双schema dump | 708c509d62a1eb3fe480ce57aeb221b8ccc6f71267ae61863800bddd7418c4ff |
| 实际重试XLSX | 9d53dda573fcca105f8559bfcd81288593946cd95b2229fec7ff33306c23f79e |
| 实际重试PDF | fe141ead713e0cbca6058c5de91af18b0ce6f637530de5373b85251c21b5c9ba |

本片只有execution01一次业务运行且整体exit0，六次预期任务失败是用例内明确注入，不改写为产品故障修复。补丁/运输语法错误未执行对应动作，离线解析错误和修正保存在tool-notes.json；原日志/XML、HTTP、dump/binlog及失败renderer原件保持。

仅对已结束独占s22g build与1645/data共4094文件无损NTFS压缩，分配减少225891258字节、删除0，逐文件长度/SHA保持；D余量观测37299322880→37534814208，与文件分配变化分列且不累计前片。compact-plan/result/stdout保存原文件哈希、命令与退出码。此前删除策略拒绝不重试或绕行。

原资料、原身份/Keystore、原回执/签名、唯一证据、最终APK及全部继承WIP保持。正式报告与短入口按明确路径开发备份；看板本地维护，历史索引只暂存本片入口，不混入继承内容。closeout保存完整Git/远端、全部WIP原字节/SHA及证据清单（含本片恢复原件和外置权威目录，不自引用）。

## 未完成与下一步

本片未覆盖实际stale/UNKNOWN/崩溃、OS未确认终止、撤权锁竞争与恢复后末SQL组合、重试成功后第三轮完整重启或全部恢复入口。旧各专项仅按各自输入适用，不拼成整体通过。D13–D15整体、D16正常有效guard/复杂引用、D18实际最终候选及第8节未完成，总目标active。

下一有界动作先核D13跨入口错根/错库/水位与stale权威已有报告/实际输入，选择尚未覆盖的恢复后组合；不机械重跑本片或旧单例。生产决定/TEMP归档、正式签名、原库/现网、main、部署/Release关闭，localDeletionAllowed=false；换机承接、多跳、接收者确认/激活和旧run03不恢复。
