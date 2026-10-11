# D07/R09 已核范围的库外与数据库双侧提交

本批沿原总目标D07/D13/D14接续主管围栏、实物及独立运维批准，交付显式范围PREPARE→闭事务DB投影→提交读回→COMMIT→双侧再验。它只是本代次新资格前的范围证明，不发新资格，不开启普通写、删除、生产签名或七类生产决定；D01–D18及第8节目标未完成。版本事实仅见主看板生成事实块。

## 输入与实现边界

- 主树D:/acrylic-pda-outbound，codex/development，输入HEAD af78c0943f2677cb158d4989be30bdfddc907c2e；单助手唯一写者，其他项目聊天notLoaded。原1233仅只读消费扫码前dump、原binlog及独立pin，不重跑源。
- RecoveryScopeReleaseIntent/Store/Projection/Coordinator及HandoverRecoveredOperationsReview显式project接缝。独立预置库外根、锁identity、运维身份/批准pin绑定同安装、会话、控制revision/epoch、仓库/scope/case和主管物理proof。
- 库外存储只复用InitialNormalDisk不可覆盖/force/原子发布原语；不复用NORMAL政策或开放普通准入。短STORE_LAYER锁不跨DB事务；原closed lease/journal及server_state排他锁保持，原身份/来源/主管/全表在同事务与提交前重新核对。
- 新不可变范围元数据迁移有控制/代次/真实scope/case外键、原命令及scope+epoch唯一约束、提交原文hash、插入状态约束和禁止update/delete触发器。跨33树迁移分配预检见received-release-migration-preflight.json。非空恢复后显式迁移，全部原业务表和文件不变。
- 此步保守限定一个已验证scope投影；未知其他投影拒绝，不称多scope发布。原ABORT、原正文代次、原库存/占用/账务和已签事实均不改。结果ordinaryWritesAllowed/localDeletionAllowed保持false，服务保持RECOVERY_PENDING。

## 实际验证

所有路径以下均相对主树；脚本、命令、输入hash/diff、XML与原日志保留，不用复制历史报告代当次执行。

- 1291基础新隔离MySQL/应用专项1/0/0/0、exit0/sourceUnchanged，验证PREPARE-only、DB-only拒绝，末SQL回滚、同键补COMMIT及不可变触发器。原表/文件迁移守恒；独立Node received-scope-release-consumer-01为12输入不变及6元数据反例。该库保全后未复用。
- 1292准备、跨JVM恢复两阶段分别1/0/0/0、exit0/sourceUnchanged；另启动实际应用子进程，在DB已确认读回而库外COMMIT缺失时Runtime.halt(73)，后继JVM重载同一原pin/请求/批准完成COMMIT，不新签/登记身份。并发同键实际CONTROL_COORDINATOR_BUSY与COMMITTED，结束再验一致。该输入尚未包含后补UNKNOWN专项。
- 1293是后补反例后的最终新库输入。r09-temporary-evidence-restore-mysql-1293与r09-scope-restart-mysql-1293各1/0/0/0、exit0/sourceUnchanged。原1233dump/binlog重建、原范围闭合、批准、PREPARE/DB/COMMIT、真实halt及原pin新JVM回放均执行。
- 1293拒绝外部COMMIT存在但DB缺行、PREPARE丢失、原键异有效签名、异scope有效签名、换store/lock、路径别名、未知文件、部分/冲突pending；匹配pending可按原键完成。SQL末写、批准文件及PREPARE尾改使真实MySQL事务回滚。UPDATE/DELETE触发器拒绝；原全库/文件守恒。
- 最后在真实MySQL commit成功后注入丢确认，保留原scope双侧证明但closed会话持久UNKNOWN；coordinator verify/release及重新构造authority均CONTROL_TRANSACTION_OUTCOME_UNCONFIRMED。outcome-unconfirmed.json和hash保全，不清除围栏、不把静态历史COMMIT称当前可用许可。故障验证在成功路径之后执行，不能复用该实例继续业务。
- 最终独立Node：received-scope-release-consumer-03为20输入不变/6元数据反例，并核迁移守恒、三个不同PID、halt退出码、原pin、两侧提交及UNKNOWN文件；operations-consumer-02为14输入/8反例；physical-consumer-02为22输入/130原表/3签名/8反例；number-consumer-02为8输入与原生mysqlbinlog。均exit0，完整目录前缀为.local/d01-d18-20261010/received-scope-release-。
- 普通recovery/handover受影响回归received-scope-release-regression-01：3317测试/116XML，failures=errors=skipped=0、exit0/sourceUnchanged；不称完整server或全部IT通过。没有新增APK、Android正向HTTP、模拟器、真机、正式签名或部署。

## 失败与限制保全

- 1289因原截点恢复后未显式迁移、而应用测试启动禁用Flyway，测试建trigger发现新表缺失；失败XML/log/datadir/dump/binlog保留，未复用。补显式迁移和原非空表/文件守恒后使用新库验证。
- 1290被既有随机目录子串分配守卫拒绝，未建库；分配候选记录保留。1291/1292分别是中间输入，不能替代1293后补范围。
- 自审发现坏COMMIT.pending曾在DB投影前未校验，已补原字节一致检查并实际验证；外COMMIT缺PREPARE不补造。运行期间若工具transport语法错误，无shell执行；一次不存在文件读取和rg通配符诊断不计业务测试。
- 生成运维身份/签名、主管声明及合成SessionUser是开发验证条件，不代原操作者/现场物理事实。独立工具不代独立人员。新双侧协议不代HTTP/Android最后交接，更不代表D06/D07或D13/D14全完成。
- 1150/1151/UI-06及1100–1102未重跑，原资料/旧BCD/526与历史未验继续单列；C01–C08保持。全部失败原件和继承改动保留，不执行清理。

## 保全、Git及下一步

24条明确路径已提交推送a4a37f772826fdf1f23325fb1278ae581dc6b833，实际开发远端一致，main未动。received-scope-release-precommit-01保全53条dirty，postcommit-01剩余29继承路径与precommit/operations-postcommit逐hash一致，69历史/22STOP通过。用户随后要求完成必要核验后收口；文档提交/最终快照与新会话指令见[收口记录](D01_D18_PAUSE_2026-10-11_SCOPE_RELEASE.md)。各2677报告输入及四组原件收口无漂移，仅补文档未重跑业务；新资格只调查未实现，不能将本批当D07全链完成。

1291/1292/1293各自dump成功、认证shutdown=0、原PID退出、19330/19434空；最终retention见.local/d01-d18-20261010/temporary-evidence-restore-mysql-1293/。既有3306/13316/ADB不操作。任务临时/Java/cache和证据在D盘，C/D余量见完整快照；.local私有物料并未因源码推送而异地备份。

received-scope-release-resume-01保存全部41条输入dirty、69历史比较/22STOP校验及进程/监听/盘余量，29继承路径除看板均与operations-postcommit-01逐hash一致。提交前后精确清单、副本、diff、SHA、远端回执在本批precommit/postcommit快照；只暂存明确范围，不混入29继承路径，不合main。

下一步继续本代次新资格→接收准备/激活→最终提交，再沿原计划D08–D18及§8。新资格必须限定可信scope、指定接收者、目标epoch和原source映射；原正文/票据仍旧代次不可激活。后续合法变化须签名且精确投影、同事务重验，不能移除清单比较、借任意permit或开NORMAL绕过。
