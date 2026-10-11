# D07/R09 恢复态指定接收者准入

本批解决原来源没有合法同仓接收机的开发缺口，完成可信登记与精确签名效果核对；不将本批计为 D06/D07、D01–D18 或总目标完成。版本事实只见主看板生成块。

输入主树 D:/acrylic-pda-outbound、codex/development、HEAD 3156b13d8fdb5547c3cc42cb866be7bd92606cd1。恢复时与开发远端一致、main 未变；原 scope-release 最终快照的30条路径除允许更新的看板外逐hash相同，69历史比较/22原STOP无漂移。完整输入、副本/hash/diff、实际进程/监听/C/D余量见 .local/d01-d18-20261010/received-recipient-resume-02/、received-recipient-resume-running-03/和received-recipient-precommit-01/。

## 行为与边界

沿既有 closed archive / lease / journal 接缝登记一个新接收设备。独立pin的运维RS256声明绑定控制记录、原来源、scope/case、双侧COMMIT、主管/登记人/接收用户及授权版本、新机公钥；新机P-256持钥证明覆盖同一规范正文。实时核对既有用户/权限与仓库，原机仍禁用、原session仍撤销；不能用其他仓设备或伪造原登记。

新增不可变准入审计，保存原签名bytes/hash与release/用户/设备外键。新设备ACTIVE但普通TEMP=false。sealed Effects只逐列认可该设备及精确审计行，从比较副本剥离这个已核效果后继续全原表/完整成员/global-number/物理/运维/双侧核对，数据库原件不改；beforeCommit再核raw after。未知第二审计、其他设备、session或权限变化不能静默接受。

事务绑定实际同DataSource/连接/journal operation；身份锁序及提交前身份/签名/材料重验保持，不在closed operation内裸读authority。重启仅重载原pin/批准/范围COMMIT/准入字节，缺失DB登记拒绝重建。该结果不是新资格或普通写许可，RECOVERY_PENDING、ordinaryWritesAllowed=false、localDeletionAllowed=false。

本机设计/作者自审：.local/d01-d18-20261010/received-recipient-admission-design.md及received-recipient-self-review.md。只有单助手作者；独立工具消费者不等于独立人员或现场验收。

## 实际验证

- 新1297从原1233 dump/binlog只读重建，显式非空迁移；原表/文件迁移守恒。既有REQUEST/FAULT_FENCE/APPROVE/范围证明只作为本实例依赖准备，未机械重跑旧专项或旧来源。
- 定向integrationTest仅运行E2aReceivedRecipientIT.admitsOnlySignedRecipientAcrossOriginalCheckpoint：1测试、0失败/错误/跳过、exit0/sourceUnchanged。包括登记成功、原字节重放、有效异文/异签名冲突、两线程、错pin/控制/scope/原机/跨仓/错钥/坏签名/权限版本/过期未来拒绝、末SQL全回滚、跨仓warehouse/device尾写回滚、审计UPDATE/DELETE拒绝。
- 实际后继JVM只重载原pin/bytes，无新签名、DB不变、不同PID与exit0；此为已提交结果丢响应/重启重放验证，没有声称新崩溃切点。见.local/e2a-cross-20260925-1297/recipient-restart-*及recipient-checks.json、recipient-before.json、recipient-after.json、recipient-migration-*.json。
- 独立Node消费者received-recipient-consumer-02：16输入hash不变，149表精确守恒，RS256及接收P-256签名/COMMIT/DB侧/原来源pin核对，4项篡改反例、新JVM/retention核对，exit0。命令为node .local/d01-d18-20261010/received-recipient-consumer.cjs 1297 received-recipient-consumer-02。
- 受影响普通recovery/handover回归3317测试、116XML、0失败/错误/跳过、exit0/sourceUnchanged；这是该范围普通test，不是全server或全部IT。目录.local/d01-d18-20261008/received-recipient-regression-01/，实际命令和2693项输入hash/原log/XML完整保留。专项报告在同根r09-temporary-evidence-restore-mysql-1297/。
- 未新增正向HTTP/Android组合、APK/模拟器/真机；合成SessionUser、新接收钥、独立运维签名与主管实物声明明确为开发条件，不代原身份/现场。范围证明历史结果未外推本链后续能力。

## 失败、Git与保全

1294分配守卫拒绝未建库。1295故障注入异常类型断言失败（实际Spring DataAccessException，原断言要求RecoveryControlException）保留；1296修正后中间通过，1297补齐反例后最终通过。consumer-01规范换行核对失败保留，consumer-02修正后通过；消费者修正不冒充业务复验。

1295/1296/1297的dump成功、认证shutdown=0、原PID退出、任务监听空，见.local/d01-d18-20261010/temporary-evidence-restore-mysql-<编号>/retention.json。原1293持久UNKNOWN及全部保全/失败库不复用，原1233只读；未跑1150/1151/UI-06/1100–1102。失败库/datadir/dump/binlog、原log/XML/签名原件保留；无清理。任务产物及子进程TEMP/Java/cache在D盘，源码推送不代表私有本机材料异地备份。

明确暂存路径、提交/推送回执见.local/d01-d18-20261010/received-recipient-commit-paths.json、received-recipient-push-receipt.txt；提交后Git/全部剩余dirty/hash/副本/保护/环境见received-recipient-postcommit-01。继承29条非看板dirty不混入本批、不restore/清空，main/发布/部署/正式签名未授权亦未执行。

## 接续

下一项为target代次、限定scope/case/接收人的独立新资格：保留原正文/source代次与来源映射，旧票据仍不可激活；隔离无关首轮创建/扫描/提交，不能直接借通用TEMP_WORK。随后继续接收准备/激活/最终提交的精确签名效果与实际MySQL/HTTP/Android组合反例。

D06/D07其他缺口、D08–D18及第8节仍未完成；D04/D05未定政策、原资料/旧BCD/526及历史失败不移入现场。生产默认/签名与七类生产决定handler/POST/放行/证据写入/关闭能力保持关闭，localDeletionAllowed=false；不开NORMAL，不接原库/现网、不合main、不发布部署/正式签名。C01–C08按原计划保留。
