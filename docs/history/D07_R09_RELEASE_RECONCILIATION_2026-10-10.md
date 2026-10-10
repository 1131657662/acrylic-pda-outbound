# D07/R09 原终点与主管围栏效果核对

本批接续完整D01–D18目标，只收口固定原终点事实与REQUEST/FAULT_FENCE效果核对；实物/主管运维确认、可信释放、新资格、最终交接及总闸门未完成。版本事实见看板生成事实块。替换前结论见[前看板](ORCHESTRATION_STATE_before_release_reconciliation_2026-10-10.md)。

## 输入与实现

主树D:/acrylic-pda-outbound，codex/development，输入HEAD/实际开发远端0e5173cc53d1e47f85767bcda30b594102459c64。received-reconcile-close-preflight-01保全全部44条dirty及副本/hash/binary diff、Git/进程/监听/磁盘；与received-scope-postcommit-01的29条继承dirty逐hash未变，69历史比较和22原STOP保护无漂移。单助手唯一活动项目写者，其他项目聊天idle/notLoaded。

15条产品/测试路径建立严格原列解码、固定130表全库读取、独立公钥验签、原REQUEST/FAULT_FENCE绑定、精确效果核对和不可变只读证明；由HandoverRecoveredTemporaryReview.reconcile接既有closed归档事务。固定表名、有界行数及字节数；全局原行和其他仓库保留，原ABORT、占用、库存、账务、准备/基线/子表不变。仅允许原设备停用/版本+1、原session围栏执行时间内撤销、订单outbox版本效果，以及逐项绑定的case/fence/outbox、两条回执/防重/审计。审计生成ID、request_id、执行时间保留并绑定摘要，不称这些字段全部已签名。

原1233始末快照独立pin只读复用：begin为1e23ca32e6c67da0ab039022ef6d4917453ec961a061aa320de3efe602d5864b，end为3393867cb13b75b9228ff067a5482b60fac6d0ec1d7caa7f9516a70f1f18694c。已有真实损失窗/全局编号证明在事务外校验，进入新的closed事务要求相同清单；事务内及提交前重核独立原件与全库原列。闭事务journal保持原严格语义，不放宽authority.read或未知结果围栏。

明确范围为目标仓库单一已收TEMP/ABORTED来源，无未核其他设备/来源/PC/调整链；未知范围继续拒绝。证明physicalState仍UNCHECKED，releaseAllowed/ordinaryWritesAllowed/localDeletionAllowed全部false。没有调用Normal登记或构造放行permit。

## 当次验证与原失败

- 1274全新隔离目标从原1233扫码前dump/binlog恢复后，实际MySQL+应用专项1/0/0/0、exit0、sourceUnchanged。签名REQUEST来源/案件/身份/目标代次绑定后最终输入；同证明重复读取、12语义反例、两项跨仓库提交前尾写全回滚，前后全库守恒。
- received-reconcile-unit-01：8项签名用途/内容等定向测试全过、sourceUnchanged。
- received-reconcile-consumer-01：独立Node工具不导入产品代码，固定原终点pin、130表原行/精确新增、原RSA命令回执、证明与全局原列摘要、两项篡改反例、停机保全回读通过；9输入hash不变。
- received-reconcile-scope-consumer-01：完整成员10输入hash不变、130表及三类反例；received-reconcile-number-consumer-01：原生mysqlbinlog独立回读与全局编号8输入hash不变。均使用1274最终输入，没有重跑原来源。
- 普通recovery/handover回归received-reconcile-regression-01：3309全过、114XML、无skip、exit0、sourceUnchanged；不是完整server或IT。命令由run-gradle-check.ps1保留，普通test排除IT；与最终1274业务输入一致。
- compile-01歧义静态导入、compile-02跨包测试编码访问失败分别保留；修复为显式bytes与测试本地规范UTF-8编码。1269在事务内无operation token读取authority被严格journal拒绝，调整为事务外预检及事务内固定原件复核；失败库/dump/log保留，不复用。
- 1270中间专项通过；1272加入语义/尾写反例后通过；1274加入REQUEST额外绑定后最终通过，不能用中间输入冒充最终。1271/1273因已有随机目录名字串分配守卫拒绝，未创建数据库，拒绝回执保留。

测试报告/源码hash/XML在.local/d01-d18-20261008/r09-temporary-evidence-restore-mysql-1274和received-reconcile-*；实际源/目标文件在.local/e2a-cross-20260925-1233及1274；retention、独立工具、设计/自审和断点在.local/d01-d18-20261010对应目录。1274dump核验、认证停机exit0、原PID退出且监听空。失败与中间原件全部保留。

## 边界与接续

新接缝成功路径是合成在线主管身份的实际服务调用及开发签名，不是原身份/现场实物证明，也没有新增正向HTTP/Android/APK/模拟器/真机。独立工具不等于独立人员。原有HTTP拒绝等仅按既有输入引用，不外推新链。1150/1151/UI-06、1100–1102及原1233未重跑。

下一步复用既有主管APPROVE及实物集合/库存检查，在closed范围内记录原键不可变确认，运维证明绑定固定范围及控制代次；再实现库外PREPARE→DB排他锁提交revision/epoch/范围→库外COMMIT→实例核两侧证明。不能让Normal登记代替释放。旧票据保持不可激活，释放后另发本代次资格并保留原事实/来源映射，接准备/激活/最终提交后继续D08–D18。

生产默认/签名及七类生产决定能力保持关闭，localDeletionAllowed=false；不接原库/现网、不合main、不发布部署/正式签名。新产物及任务子进程临时目录在D盘，无清理。C/D精确余量、进程及监听见断点JSON；既有3306/13316/ADB未操作。源码备份不代表本机私有材料已异地保存。D04/D05未定政策、原资料/旧BCD/526及C01–C08限制保持单列，总目标未完成。
