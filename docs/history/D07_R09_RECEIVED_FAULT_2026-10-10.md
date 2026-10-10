# D07/R09 已收正文恢复态主管故障围栏

本轮接续[原总计划](../AUTONOMOUS_DEVELOPMENT_GOAL_2026-10-07.md)D07/D13/D14，从1233实际原时间截点与原binlog重建接既有REQUEST和独立主管FAULT_FENCE。仅收口本节指定的恢复态业务接缝；D06/D07整体、范围释放、新资格、接收准备/激活与最终提交和D08–D18/总闸门未完成。版本事实仅见看板生成事实块。

## 实现与退出标准

- HandoverCommandService仍自持外层事务；新增显式offline原归档binding，只许REQUEST/FAULT_FENCE。身份锁先于原业务行锁，核相同DataSource实际事务、RECOVERY_PENDING、目标传输代次和Web身份，再复核真实用户、仓库、原设备/session及权限。
- 严格原归档/native/verification核验后，复用既有案件、权限、原键、开发签名回执、设备禁用、主管围栏、outbox及审计。普通服务和HTTP入口不获得恢复态准入，其他命令不能借本接缝执行。
- FAULT_FENCE既有outbox会使订单版本递增。新增只读投影证明，只认可精确递增一次、本命令原字节/完整签名回执/实际围栏、唯一未发布outbox、原来源/目标代次/时间/payload相同；其余原行严格不变。默认归档消费者仍要求原版本不变，不放宽为忽略订单版本。
- 退出标准：实际新目标正向申请/独立主管围栏及同键原字节重放；异scope、旧代次、权限不足、自批、错verification、实物未决拒绝；末审计SQL与原verification尾篡改全事务回滚；原正文、STOP、verification、owner、库存及账务守恒；普通真实HTTP与服务拒绝，外层嵌套/异DataSource拒绝，订单版本/outbox篡改拒绝。
- 无新写handler/HTTP、生产签名开关、NORMAL或释放permit。新增@Component只读证明投影，不产生业务写入或准入。无APK/接口/迁移变化。

## 实际验证与失败保全

- 1242失败：合法故障outbox递增订单版本，原归档尾重验拒绝，命令整体回滚。修正为上述签名投影证明，未忽略版本变化。
- 1243失败：MySQL时间值为LocalDateTime，错误强转Timestamp。改为显式RowMapper读取Timestamp再转Instant；失败回滚保全。
- 1244/1245跨树随机目录子串守卫拒绝，未建库；分配命中记录保全。
- 1246业务申请/围栏/重放已执行，但最终DDL比较失败；唯一差异是operation_event正常AUTO_INCREMENT递增。只对该表计数器归一化，原DDL保留，1246仍按失败登记。
- 1247中间通过：专项1/0/0/0、exit0/sourceUnchanged，原dump/认证停机/原PID退出、19330/19434空。Node01为55输入hash不变/exit0，核原签名/verification/文件及合法变化；无恢复态普通HTTP证据。外层PowerShell尝试读取不存在failure.txt导致exit1，独立Node实际exit0/result明确，原输出保留。
- 1248失败：普通真实HTTP已返回503/SERVER_RECOVERY_REQUIRED，拒绝后全库相等断言发现仅spring_session访问时间变化。保留原XML/dump；将判据限定为原会话访问时间单调推进、到期时间与访问时间差不变，其余会话列和全部业务表/文件/schema不变。
- 1249/1251分配子串守卫拒绝，未建库；1250跨33树分配。
- **最终1250专项1/0/0/0、exit0/sourceUnchanged**：上述正向/拒绝/阶段内原字节重放（REQUEST在围栏前，FAULT_FENCE在围栏后）、真实Web登录+CSRF+目标代次HTTP503、同库不同DataSource资源拒绝、外层嵌套拒绝、原verification尾改全事务回滚、订单版本回退/再推进、outbox正文/发布状态篡改拒绝均通过。尾改比较只归一化operation_event计数器，原前后快照保留；所有业务行和文件严格守恒。
- 唯一主管围栏、设备DISABLED、目标session撤销和唯一outbox；不生成原机新STOP/ACK，不释放原占用。目标仍RECOVERY_PENDING、localDeletionAllowed=false，ACTIVATE不能使用此接缝。dump重算一致、认证停机、原PID退出、19330/19434空。
- **独立Node02 exit0，64输入hash不变**：RSA两份业务回执、原ECDSA STOP、原verification逐列/全部文件原字节/全表守恒、精确合法变化、HTTP拒绝/原会话时间边界、事务拒绝、verification尾回滚及投影反例结果通过。独立工具不等于独立人员。
- 普通recovery/handover回归received-fault-regression-01为3269/0/0/0、108XML、exit0/sourceUnchanged；收口逐hash复核934个产品/契约/构建输入无漂移。后续仅新增专项测试辅助类，1250实际编译并执行覆盖，未机械重跑普通回归。普通test排除IT，不计完整server测试。

## 证据入口与适用限制

- 设计及作者自审：`.local/d01-d18-20261010/received-fault-design.md`、received-fault-self-review-hashes.json、received-fault-regression-production-recheck.json；本批precommit保存明确路径、完整暂存diff、审查/已验hash与提交推送回执。
- 编号证据：`.local/d01-d18-20261008/r09-temporary-evidence-restore-mysql-<编号>/`为Gradle命令/源码hash/result/XML；`.local/d01-d18-20261010/temporary-evidence-restore-mysql-<编号>/`为分配/runner/dump/retention；`.local/e2a-cross-20260925-<编号>/`为实际DB/binlog/文件。1250新业务及反例原件在recovered-fault，消费者在received-fault-consumer-02。
- 1233原来源只读复用，未重跑。1242/1243/1246/1248失败库/原件完整保留停机，不复用。1150/1151/UI-06和1100–1102未重跑。全部产物及任务TEMP/Java/cache在D盘，无清理；源码备份不代表私有材料已异地备份。
- 本接缝的成功业务是实际MySQL+真实应用中的服务调用，认证上下文为合成SessionUser；普通拒绝另有真实Web登录/HTTP证据。1233原来源有Android/HTTP证据，不能外推新业务为实际HTTP/Android最终交接。未新增APK/模拟器/真机/正式签名/部署验证。
- 本接缝REQUEST仍要求订单原版本，围栏后原REQUEST通过此接缝会被原版本前置拒绝；本轮未执行该跨阶段重放或并发同键验证。后续完整业务链须覆盖阶段变更后的原键查询/重试与并发，不能把本轮阶段内重放外推为全链幂等。

## 下一最小必做事项

从主管围栏后的范围核对/可信释放接续，沿既有库外PREPARE→DB排他锁提交revision/epoch/范围→库外COMMIT→实例核两侧证明的协议，未知设备或范围不放行。RecoveryNormalRegistration/RecoveryNormalIntent只有既有登记基础，不能直接充当业务释放证明。

原正文代次和恢复目标传输代次不同；HandoverTemporaryTarget要求接收资格与原正文代次一致，激活票据保存sourceRecoveryEpoch，HandoverTemporaryActivationIssuer用它签发。TemporaryRoundAccess要求新正文资格代次与目标传输代次一致。必须按D14“旧票据不激活，范围释放后另行新资格”补最小可信映射/新资格链；不能改原正文代次、简单开NORMAL或构造任意permit。接范围释放→新资格→接收准备/激活→最终提交并验证实际组合、回滚/幂等/冲突/重启，然后按原依赖继续D08–D18与第8节。

用户要求本步后收口，接续以[暂停文档](D01_D18_PAUSE_2026-10-10_RECOVERED_FAULT.md)与主看板为准。生产默认、签名和七类生产决定能力关闭，localDeletionAllowed=false；不接原库/现网、不合main、不发布部署/标签/Release、不正式签名、不绕设备审批。原资料/旧BCD/526、D04/D05政策缺口、历史失败与C01–C08现场限制保持。

## 明确路径开发保存

17条范围已提交并正常推送3ab3d91ad9777140b66e4cbfda21c30f6647a465，实际开发远端一致，main未动。全部暂存路径/diff/作者自审、最终1250已验11条代码hash与原commit/push回执在received-fault-precommit-01；postcommit-01保全29条剩余dirty并逐hash核对前final不变、69历史比较/22原STOP无漂移。其后仅补Git断点文档，最终精确HEAD/远端/全部dirty/环境及C/D余量见received-fault-pause-final-01，不机械重跑已验业务。
