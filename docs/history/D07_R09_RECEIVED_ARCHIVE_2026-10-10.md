# D07/R09已收正文原归档closed/native消费

本批只收口1233实际来源经新目标全量恢复后的原归档同事务消费；D06/D07整体、主管故障围栏、接收准备/激活与最终提交、D08–D18及总闸门未完成。版本事实见看板生成事实块。

## 输入与退出标准

树D:/acrylic-pda-outbound，codex/development，源码输入HEAD 75f4d08327a5045cc428b0d644f018e1bdfd44fe加本批明确路径。设计在`.local/d01-d18-20261010/received-archive-design.md`。复用只读1233实际Android/HTTP原正文、ABORT、STOP、intake、完整原verification与扫码前dump/binlog；未重跑来源或1150/1151/UI-06、1100–1102，未复用失败库。

标准为冻结来源与紧接STOP边界严格关联，独立准备/注册根/占用/权限历史与原行pin共同核验；closed事务锁实际head、E2A、owner、history、verification及库存，核原文件，消费者前后及beforeCommit重验，保留原字节/ID/签名/正文。消费应有提交可见性、幂等、消费者异常/末SQL/篡改回滚及库存锁反例。

## 实现与自审

- RecoveryReceivedTemporaryArchiveConsumer/Inputs沿RecoveryClosedTransaction及既有native消费者，取得closed租约后重读原件并核全部前置事实；受信回调使用同一数据库的事务绑定连接且无外部副作用。
- RecoveryReceivedTemporaryMaterial.readAtStop先验证冻结到紧接STOP的原audit边界，再按STOP身份读取原行pin；没有构造伪造的pinned-source或改写原正文epoch。
- RecoveryTemporaryNativeHandoverFacts支持payloadPresent=true时hash必须精确等于原正文；false仍须hash为null。原verification按完整行摘要/ID锁定，消费前后及提交前核验。
- 没有注册生产Bean/HTTP、签发/补造证据、普通或交接准入、释放/删除。RECOVERY_PENDING及旧资格拒绝仍保持。

作者自审核源码、差异、实际XML/输入hash、独立消费者及回滚/锁事实；独立Node工具消费不等于独立人员审查。控制权威与开发身份仍合成，准备MockMvc、Android Robolectric分别计证，不代原身份/生产/现场。

## 实际验证与失败保全

| 输入 | 实际结果与范围 |
| --- | --- |
| 1238新目标 | AssertJ泛型重载歧义，编译失败、0测试；改为明确Integer断言。失败空库dump/认证停机保全，不复用。 |
| 1239新目标 | 专项1全过、exit0/sourceUnchanged；消费提交可见性/幂等/回调异常/末SQL/verification消费中与beforeCommit篡改回滚。 |
| 1240新目标 | 专项1全过、exit0/sourceUnchanged；追加原件消费期间变化回滚、库存独立连接锁超时且回调不执行、owner周期变化拒绝。 |
| 1241最终新目标 | 专项1全过、exit0/sourceUnchanged；追加原归档payload/terminal/STOP及native事实输出供独立消费。dump/认证停机/原PID退出、19330/19434空。 |
| received-archive-consumer-01 | 独立Node exit0，65输入hash不变；原verification逐列/digest、归档原字节、ECDSA STOP、RSA终态/intake、文件、全表守恒和唯一probe行通过。 |
| received-archive-regression-01 | 普通recovery/handover 3269/0/0/0，108XML，exit0/sourceUnchanged；普通test不含IT，非全server整合。 |

未运行新APK/模拟器/真机/部署；本批未改Android产品源码。不把原件消费或probe事务当主管围栏/接收最终链通过。

## 证据、Git及环境

Gradle命令/源码hash/日志/XML/result在`.local/d01-d18-20261008/r09-temporary-evidence-restore-mysql-<编号>/`和`received-archive-regression-01/`；分配、runner、dump/retention在`.local/d01-d18-20261010/temporary-evidence-restore-mysql-<编号>/`；数据库/binlog/实际文件在`.local/e2a-cross-20260925-<编号>/`。独立脚本为`.local/d01-d18-20261010/consume-received-archive.cjs`，输出在同父目录`received-archive-consumer-01/`。1241的received-archive-before/after/result、native-facts和三份bin均保全。

单助手唯一写者；接续snapshot received-archive-resume-01保全39条dirty副本/hash/diff、进程监听及磁盘，69历史比较和22原STOP保护无漂移，前final除可更新看板外29条路径逐hash不变。仅本批源码/测试与文档明确暂存；提交/推送回执和最终全部dirty、实际HEAD/远端见received-archive-precommit-01/postcommit-01/final-01。继承dirty不混入，`.local`证据/数据库/私有材料未上传或异地备份。

一次性库停止，既有3306/13316/ADB不操作；Gradle回归已完成，残留daemon不视为在途测试写者，进程详情见snapshot。新增产物/TEMP/Java/cache继续D盘，未清理原件或失败证据，C/D实际余量见final-01/disks.json。

## 下一业务接缝

HandoverCommandService自持外层事务，不能直接嵌套closed回调；HandoverWriteAccess经IdentityWriteGuard要求NORMAL，恢复目标仍RECOVERY_PENDING。接收资格epoch须等于原正文epoch，恢复投影推进目标epoch，旧正文/旧资格不能直接激活。不得简单开NORMAL、改原正文或绕过这些拒绝。

沿既有可信恢复/范围核对协议盘点最小实际接入：主管故障围栏复用原archive、独立主管与设备禁用/围栏/outbox，恢复范围释放后另行新资格，再接收准备/激活至最终提交；不建立平行命令系统或任意permit。生产默认/签名和七类能力关闭，localDeletionAllowed=false；不接原库/现网、不合main、不发布部署、不正式签名。D04/D05政策缺口与C01–C08、原资料/旧BCD/526和历史恢复限制继续单列。
