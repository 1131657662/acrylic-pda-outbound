# D07/R09 恢复后原TEMP归档显式读取

输入主树D:/acrylic-pda-outbound、codex/development，HEAD/实际开发远端757eccba11fd45c18c395f4925dcd4e26bd31d1e。前批四表恢复已推送，提交后final快照为`.local/d01-d18-20261010/evidence-restore-final-04`。总目标active、未完成，版本事实仅见看板生成块。

## 缺口与边界

HandoverTemporaryRoots/TemporaryRoundStopProof依赖native intake回执、服务端verification和数据库审计前缀。四表恢复不能补造这些旧事实，也不能直接冒充可信native归档。设计在`.local/d01-d18-20261010/recovered-archive-design.md`。

新增显式`RecoveryTemporaryEvidenceRestore.verifyArchive`：同一closed事务重验原五件、登记根、受约束连续权限窗口、完整E2A、独立占用与原四表pin，锁目标授权/准备/原ABORTED头/bale/库存，并要求全部原列已完整一致。缺行只拒绝，不修复；提交前重验原源、文件、目标行及占用当前读。

返回`RecoveryRecoveredTemporaryArchive`原正文、终态回执、设备STOP原字节、原set/stop-object ID及source/rows/occupancy/registration/control pin。构造器非公共，数组输入输出防变。source包含STOP水位，原冻结边界仍由原payload和manifest证明。对象只是一次成功事务的快照，不能缓存作能力；不代表native verification、主管同意、物理控制、持久准入或签名授权。没有HTTP/bean/生产接线，仍RECOVERY_PENDING、localDeletionAllowed=false。

## 验证与失败

- 定向01为28/0/0/0、4XML、exit0/sourceUnchanged；含新对象原字节防变和公共构造边界两项，及已有四表/根/权限校验。编译不代业务验证。
- 分配1202/1203守卫拒绝、未建库；实际跨33树选择1205，分配记录保留。新空目标1205真实导入1189成功源的原截点dump，不重跑或修改源库，不复用1201/失败库。实际四表恢复后新读取1/0/0/0、exit0/sourceUnchanged：缺行读取拒绝、完整/重复读取原字节匹配、全表/文件不变，原行/原文件/登记pin篡改及beforeCommit原件变化拒绝。新读取专用故障探针在事务beforeCommit最先换件，不借INSERT探针冒充执行。
- 独立Node四表消费者1189/1205为39输入hash不变、实际导入/逐原列/ID/时间/RSA/P256/页链/对象/全表守恒通过。新增归档消费者02为11输入hash不变，逐字节核正文/原终态/STOP、原set/object ID、独立pin与目标control、冻结与STOP分界，以及native intake/verification/审计/来源/原submission未改。首轮消费者误引用不存在表pda_audit_origin失败，原脚本/hash/result保留；首败输出仅聊天工具回执，未冒充落盘原日志；修为实际temporary_work_origin/authorization后成功。
- 受影响普通recovery/handover回归02为3218/0/0/0、118XML、exit0/sourceUnchanged；新两项实际执行。普通test非IT，非完整普通test。作者自审固定SQL/closed重验/当前读/防变及生产未接线，新改文件均≤200行，git diff --check通过。无新HTTP、Android/APK、页面/模拟器/真机或部署验收，不外推主管围栏/接收准备激活/最终提交。

证据：定向/回归在`.local/d01-d18-20261008/r09-recovered-archive-*`；1205原log/XML/command/source hash在`r09-temporary-evidence-restore-mysql-1205`，分配/provision/runner/dump/retention在`.local/d01-d18-20261010/temporary-evidence-restore-mysql-1205`，实际datadir/binlog/dump/全表与读取字节在`.local/e2a-cross-20260925-1205`。Node脚本/hash/result在`evidence-restore-consumer-1189-1205-02`与`recovered-archive-consumer-1205-01/02`；02原日志单独保留。

## 环境与接续

1205 dump已保全、认证停机/原PID退出、19330/19434空。无清理；新增TEMP/Java/cache/证据继续D盘。此前原1150/1151/UI-06、1100–1102未重跑，失败库和原资料保留。

单助手唯一写者，保护25条继承dirty；精确提交/远端和全部dirty副本/hash/diff/进程/监听/磁盘以本批precommit/postcommit/final快照为准。只按明确路径提交推送开发分支，本机资料/私钥/配置/数据库未上传或异地备份。

12条明确路径已提交并正常推送`88be3e536453ea259949f6f4c007bfe71c8b5a4d`，实际开发远端重新查询一致；原commit/push回执在`recovered-archive-precommit`，`recovered-archive-postcommit`保存全部25条剩余dirty与环境，69次继承及原22保护项无漂移、旧HEAD均为祖先。1205 dump独立重算一致，结果在recovered-archive-dump-recheck.json。后续仅断点补记不改变已验业务输入。

下一动作：用显式恢复归档消费接主管故障围栏，并决定closed恢复与正常准入的受控交叉点；不得把本读取快照当作native verification或普通写门。随后新隔离真实HTTP/Android组合至接收机最终记账及各阶段回滚/幂等/冲突/重启。

D06/D07整体、FORMAL各阶段产品入口、D08–D18及第8节仍未完成；D04/D05政策、原FORMAL准备缺失/高轮/撤销/跨源、原资料/旧BCD/526与C01–C08继续单列。生产默认/签名/七类决定关闭；不接原库/现网、不合main、不发布部署/标签/Release、不正式签名。
