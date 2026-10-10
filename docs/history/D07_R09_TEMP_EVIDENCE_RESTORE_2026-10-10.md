# D07/R09 原 E2A 与占用跨截点恢复

输入主树D:/acrylic-pda-outbound、codex/development，HEAD/实际开发远端均22f82406577a9a59cc39cd54bc183faf200abcd8。从原准备/ACK恢复接续，未重跑1150/1151/UI-06或1100–1102。版本事实仅见看板生成块；总目标active、未完成。

## 范围与实现

设计`.local/d01-d18-20261010/temporary-evidence-restore-design.md`。首轮TEMP原无正文ABORTED头、原授权/准备/ACK/基线在截点，E2A与SOURCE_RESERVED原行缺失。原观察没有对象内部ID/file_key/received_at、集合received_at和占用created_at，不能从摘要逆造这些列。

新增显式NORMAL原端只读采集source_evidence_object/source_evidence_set/source_evidence_item/bale_commitment完整原列，原ID、UTC毫秒时间、文件键和owner列原样保存到库外独立pin。恢复端同时验独立登记根/原P256公钥、同原实例受约束连续权限窗口、五件原字节、完整E2A页链和独立占用pin。目标文件先由显式流程恢复，本接口只校验原字节。

`RecoveryTemporaryEvidenceRestore`沿既有closed authority/lease/journal/transaction，只INSERT缺行，精确一致幂等、冲突拒绝；锁原authorization/准备/基线/ABORTED头、bale及inventory_position，提交前重读原件/pin/目标头/原列/文件/库存。冻结正文仅保留E2A文件，不插submission_payload，不补造或改原终态、回执、权限、凭据或时间。没有HTTP/bean/签发/普通写权；RECOVERY_PENDING、localDeletionAllowed=false。

退出标准：实际截点dump→另新空目标导入→四表原列补齐；错根/缺窗/原件冲突/合法竞争owner/额外object/错周期/库存不可用/损坏文件拒绝、末SQL/beforeCommit全表回滚、缺页补齐、幂等、实际并发及成功目标新JVM重开。只收口该子项，不代主管围栏/接收激活/最终提交。

## 作者与组合验证

- 编译01 exit0、依赖定向02为146全过、原行/文件03为8全过；binding-04为26项/8失败，夹具读旧manifest水位，修后binding-05为26全过；compile-06 exit0。原log/XML/hash/result保留，编译不作业务验证。
- 1181/1182/1184/1185/1190/1193/1195/1200目录数字子串分配守卫拒绝，未建库。1183水位修改未发布pin、1186收紧账号trigger definer缺TRIGGER权、1187基础seed包有另一owner、1188准备part/ACK写进受约束窗口白名单外，失败库/log/XML/datadir/binlog/dump保全、认证停机，不复用。准备移至begin前，未放宽日志白名单；迁移沿隔离root后排空/锁root。
- 1189新源真实MySQL1/0/0/0、exit0/sourceUnchanged：实际登记、受约束权限窗口、截点dump、E2A/占用原列只读采集及独立pin；截点原无正文ABORTED/准备/ACK/基线在，四表本源范围缺。合成源/开发签名不代原人或现场。
- 1191新空目标1全过，实际导入/原四表补齐、缺pin/窗口、库存不可用、错终态、原行冲突、末SQL/beforeCommit原件变化回滚/幂等。1192竞争owner夹具置空set违反ck_commitment_owner，失败保全；修为同仓库合法scope/set，1194新目标1全过，另验错周期/额外object/缺页补齐/实际库存行锁阻塞。
- 1196新目标1全过，补错登记根/损坏私有原文件拒绝与守恒；成功目标新JVM重开exit0、零插入/全表文件未变。1197新目标1全过，补落盘实际导入全表快照；各仅计对应输入。
- 自审提出RR快照风险。1198在并发探针前因DriverManager全表快照耗尽Windows临时套接字失败；目标夹具改已有Hikari三连接池并在实际库存锁SQL同步并发。1199确认产品缺陷：等待库存锁期间另一事务提交upstream_present=0，恢复仍读旧快照成功返回；原失败保全。
- 修复`RecoveryUnsubmittedOccupancyRows.readLocked`，只在实际事务内FOR UPDATE当前读，原只读采集仍read。1201新空目标1/0/0/0、exit0/sourceUnchanged：并发提交库存不可用后拒绝/全表文件回滚、所有四表正向/反例/部分恢复/幂等通过，目标原列匹配源。
- 完整普通server回归07为4156/0/0/1、243XML、exit0/sourceUnchanged；新增三单测类26项执行。普通test不含IT，Windows符号链接条件skip未执行、不计通过。作者自审固定SQL/有界输入/链接拒绝/事务/生产接线、新文件均≤200行；git diff --check通过。无新HTTP/Android/APK/页面/模拟器/真机/正式签名/部署验证。

## 独立消费与重开

独立Node1189/1197与1189/1201均exit0，各39输入hash不变，核实际截点dump pin/导入全表、独立四表pin逐原列/ID/时间、登记P256文本hash/公钥、RSA无正文ABORTED原终态、P256 STOP完整页链/对象字节、私有文件实际hash/全表守恒和RECOVERY_PENDING。首轮1196缺导入快照失败保留；后补真实落盘快照，用新目标复验，不造快照。首败脚本为事后重建记录，错误原输出在聊天，不冒充当时落盘精确源码。

独立Node权限日志exit0、4输入hash不变：begin/end canonical/pin、同实例、prefix位置/CRC/ROW/XID/UTC微秒半开提交，共25次提交/25条ROW，仅修改submission及本四表；九类权限表无修改，root锁定/单活动账号/精确GRANT/后台写者关闭。独立工具不是独立人员或异地保管，不代原身份/设备或现场。

1201重开01与Gradle重编译共享class目录并行，ClassNotFound失败、原日志保留且认证停机；修classpath并等待回归结束后串行重开02，新JVM/同成功datadir exit0、5输入hash不变、零插入/全表文件未变，ordinaryWritesAllowed/localDeletionAllowed=false，shutdown0/PID退出/19330及19434空。不是失败库复用。

证据：普通测试`.local/d01-d18-20261008/r09-evidence-restore-*`；真实MySQL测试`r09-temporary-evidence-restore-mysql-<run>`。分配/runner/retention/dump在`.local/d01-d18-20261010/temporary-evidence-restore-mysql-<run>/`；原datadir/binlog/dump/截点/快照在`.local/e2a-cross-20260925-<run>/`。Node脚本/39输入hash/result在`evidence-restore-consumer-1189-1197-02`、`evidence-restore-consumer-1189-1201-02`；权限结果`evidence-permission-consumer-1189.json`/脚本`verify-evidence-permission-log.cjs`。重开源/class/compile、命令/hash/回执在`evidence-restore-reopen-source`、`evidence-restore-reopen-1196-01`和`evidence-restore-reopen-1201-01/02`。

## Git与接续

本聊天唯一写者，其他项目聊天notLoaded，单助手无子代理。继承25条dirty保持；FORMAL final、磁盘final和准备恢复postcommit逐hash核对、旧HEAD为祖先；全dirty副本/hash/diff和环境见`evidence-restore-resume-01`及`evidence-restore-precommit/postcommit/final`。仅明确暂存本批四表恢复、事务当前读修复及对应测试/本页/看板/索引，提交推送开发分支；精确SHA/远端以postcommit/final及原commit/push回执为准。本机证据/失败库/私钥/配置/资料未上传或异地备份。

30条明确路径已提交并正常推送`757eccba11fd45c18c395f4925dcd4e26bd31d1e`，原commit/push回执在`evidence-restore-precommit-03`。提交后重新查询实际开发远端一致；`evidence-restore-postcommit-04`保存全部25条剩余dirty副本/hash/diff、HEAD/远端/树/看板、全部进程/监听/磁盘。69次继承比较及原22保护项零漂移、旧HEAD为祖先，无任务消费者或隔离监听在途。后续仅断点文档补记不改变已验业务输入，不机械重跑该批测试。

实际建库均dump保全/认证停机/PID退出/隔离端口空，成功目标重开后再次停机；独立重算见evidence-restore-dump-recheck.json。C/D精确余量在final/disks.json；新增产物/TEMP/Java/cache均D盘，无清理，既有3306/13316/ADB不操作。

下一步接恢复后原ABORTED/E2A/SOURCE_RESERVED的主管故障围栏→接收准备/激活→最终提交。恢复普通写门关闭，不能借旧库行当可信归档；沿HandoverFaultArchive/HandoverTemporaryOriginal定位最小closed供给与准入交叉点，再新隔离组合验实际HTTP/Android及各阶段尾SQL/幂等/冲突/重启。

D06/D07整体、FORMAL各阶段产品入口、D08–D18和第8节未完成；D04/D05政策、原FORMAL准备缺失/高轮/撤销/跨来源、原资料/旧BCD/526与C01–C08限制继续单列。生产默认/签名/七类决定关闭、localDeletionAllowed=false；不接原库/现网、不合main、不发布部署/标签/Release、不正式签名。
