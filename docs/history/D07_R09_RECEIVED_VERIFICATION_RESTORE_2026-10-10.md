# D07/R09 已收正文原 verification 恢复子项

这是独立原行保全与单表缺失恢复开发子项；不计完整时间截点恢复、完整 withVerifiedNativeArchive 正向、主管故障围栏至接收机最终链或 D01–D18 完成。版本事实见[看板生成块](../ORCHESTRATION_STATUS.md)。

## 输入与实现

输入 2a498fe608dc577d14a0b751380092c283af5a30，codex/development，树 D:/acrylic-pda-outbound。native-received-resume-01 保全29条dirty全部副本/hash/diff、进程/监听/磁盘；final29、69次历史比较及原22保护检查零漂移。唯一活动项目写者为本聊天，其余项目聊天notLoaded；既有3306/13316/ADB未操作。设计在`.local/d01-d18-20261010/received-native-design.md`。

RecoveryReceivedTemporaryCapture只读NORMAL实际来源，限定首轮TEMP、canonical已存在、已收正文ABORTED、slot FROZEN、原REVIEW_RESERVED完整占用。采集head的显式身份/状态/正文/slot/mapping版本保护列、E2A四表、owner全部业务列、全部来源intake原行与整个设备audit前缀、verification全部原列；不是head全部数据库列快照。验证原终态服务端公钥签名、payload及每个E2A文件hash，不造原权限/准备/ACK，不建立独立根保管或历史权限权威。

RecoveryReceivedTemporaryMaterial要求来源数据库身份、明确STOP三水位、观察截止与独立原材料pin；来源material必须位于控制、closed权威、checkpoint和restore set之外。RecoveryReceivedTemporaryVerificationRestore仅在closed租约事务内恢复缺失verification全部原列：原ID/set/scope/submission/轮次、验证/算法、set hash/result、known-facts hash/findings原字节/原时间。head保护列、canonical/mapping/slot、owner、E2A、intake/audit与文件先逐值核对，原行幂等、异文冲突拒绝，提交前重读pin/source和目标。锁定库存/owner当前行；末尾SQL改动触发回滚。结果在提交后返回，普通写权与本地删除均false。

这是显式离线开发接缝，无Bean/HTTP/签发/准入或命令消费者。旧未收正文入口限制不改。原verification读取复用既有完整列协议，不改旧native consumer的完整行摘要；occupancy增加内部锁定读取，原只读调用保持。不能凭本接口的单行恢复或签名终态判全部历史权限可信，更不能跳过恢复准入。

## 当次验证

- 1213及锁定/owner加强后的1215、最终1220各新NORMAL源真实MySQL/HTTP/Android组合1/0/0/0，exit0/sourceUnchanged；各原Android XML为1/0/0/0。原Android通过产品Room冻结、上传、完整E2A和开发签名原回执，真实HTTP ABORT后采集前后全表/file/schema/迁移相同。身份/密钥明确合成开发材料，非原PDA/真机。
- 1216及最终1222各新空目标实际导入单表缺失样本，真实MySQL1/0/0/0，exit0/sourceUnchanged。原verification插入一行、重入零行且全列/摘要相同；其他全业务表/文件/schema/迁移守恒，RECOVERY_PENDING。1222另用独立连接实际锁inventory_position，恢复超时回滚；canonical版本冲突、owner周期冲突、损坏原file、缺/零pin、错原库身份、过早截止、已存原行冲突、末SQL改canonical均拒绝并守恒。
- 单表缺失样本由原schema dump与排除verification的data dump组合，源库不删行。intake原回执的FK引用在导入时已保留，因此这是明确的缺失表数据恢复样本，不能称时间截点或完整业务事实重建。原dump、排除参数、全部失败材料均保留，不让无效样本冒充历史可信截点。
- Node独立消费者1220-1222-03为41输入hash不变、exit0；逐原列verification及canonical+末LF摘要、原ABORTED签名、四类intake签名/原请求带协议前缀摘要、原E2A文件字节、目标其他全部表/文件/schema/迁移及RECOVERY_PENDING均核一致；两最终dump独立重算、认证停机回执同时核对。该工具是作者运行的独立实现，不等于独立人员审查、异地保管或原设备验签。
- 普通recovery/handover回归received-native-regression-01为3235/0/0/0、107XML、exit0/sourceUnchanged；普通test排除IT，非完整server回归。无本批页面/APK/模拟器/真机/部署/正式签名验收。

## 失败与适用限制

received-native-compile-01参数以单字符串传递，Gradle拒绝任务，0测试；02/03编译通过。1211在采集head复用处遗漏原continuation/superseded字段别名而拒绝，1失败，失败库dump/认证停机保全；修后新源1213通过。安装的mysqldump不支持预设单表data排除参数，实际运行前已改为两dump组合，未伪称失败参数执行成功。1214缺文件错误码断言预期与底层CONTROL_FILE_MISSING不同，恢复前拒绝正确但测试1失败；修后新目标1216通过，不复用1214。

1212/1217/1218/1219/1221被跨树分配守卫因旧随机fence目录子串命中而拒绝，均未建库；各received-allocation-rejected目录保存命中，未删除/复用旧fence。Node1215-1216-01误读keyId算法失败，02通过；1220-1222-02漏请求摘要协议前缀失败，03修后通过。各失败日志/source保全，不计通过。

本轮全部实例1211/1213/1214/1215/1216/1220/1222已dump、认证停机、原PID退出，19330/19434空。分配/runner/provision/dump/retention在`.local/d01-d18-20261010/temporary-evidence-restore-mysql-<编号>/`；原log/XML/输入前后hash/result在`.local/d01-d18-20261008/r09-temporary-evidence-restore-mysql-<编号>/`；数据库/data/binlog/dump/HTTP/file在`.local/e2a-cross-20260925-<编号>/`。1220的received-originals含原pin；transfer只含原公钥，不上传。最终精确dirty/进程/监听/C/D余量见本批checkpoint。新增产物及TEMP/Java/cache均D盘，无清理，本机证据未异地备份。

## 接续

原未收正文1189/1206不能补造canonical/native或INITIAL_ACCEPTANCE。1131等已收正文来源不直接塞入未收正文入口。本轮已取得新实际开发来源原verification和完整相关行，下一步从1220保全材料核原准备/ACK、注册根与可信权限连续性，再补已收正文全量真实时间截点重建，明确closed到HandoverCommandService自持外层事务/准入交叉点，接主管故障围栏→准备激活→接收最终提交。不能继续以单表恢复或只读helper代业务闭环。

1150/1151/UI-06与1100–1102未重跑；失败库不复用。D06/D07整体、D08–D18/总闸门未完成，D04候选排除/D05取消释放政策缺口及原资料/旧BCD/526继续单列。生产默认、签名与七类生产决定能力关闭，localDeletionAllowed=false；不接原库/现网、不合main、不发布部署/标签/Release、不正式签名。

16条明确路径已提交推送0657248e114e41e2aa71423ec749b7a6dfd4cd0b，实际开发远端一致；原暂存路径、diff、自审与最终已验hash、commit/push回执见received-native-precommit-01。提交后余28条继承dirty逐hash保护；看板与本页仅补Git断点，不改产品/测试输入，不机械重跑业务验证。最终精确全部dirty/进程/监听/磁盘见received-native-final-01，总目标仍active。
