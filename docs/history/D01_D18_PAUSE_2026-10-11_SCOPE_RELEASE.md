# D01–D18 已核范围双侧提交后的收口与接续

用户要求先完成本批必要事项、收口并给出新会话指令。本次完成范围证明协议、最终输入核验、保全与文档，不启动下一批新资格实现。按用户要求暂停本聊天的完整目标；目标未完成，[原计划](../AUTONOMOUS_DEVELOPMENT_GOAL_2026-10-07.md)D01–D18及第8节范围不变。版本事实只见主看板生成事实块。

## Git、写权与最终断点

- 主树D:/acrylic-pda-outbound，分支codex/development。业务输入af78c0943f2677cb158d4989be30bdfddc907c2e；本批24条明确路径已提交推送a4a37f772826fdf1f23325fb1278ae581dc6b833，实际开发远端一致。main保持c6e9e0c238c657baecbf699ba6fdbada5d979b86，未合并、强推、发布或部署。
- 业务清单及回执位于.local/d01-d18-20261010/received-scope-release-commit-paths.json和received-scope-release-push-receipt.txt。收口文档另按明确路径提交；精确文档提交/远端见received-scope-release-doc-close-01，最终HEAD/分支/远端见下述final，不把文档提交当业务复验。
- 本聊天单助手唯一活动项目写者；收口后无业务开发写者或在途任务。不创建新聊天、代理或工作树；新会话在原主树核对实际写权后接续。
- received-scope-release-precommit-01保全53条dirty；received-scope-release-postcommit-01保存剩余29条，逐hash与precommit及operations-postcommit一致，69项历史比较/22项原STOP保护无漂移。继承规则、代码、旧测试、历史补记和本机资料不混入本批提交，不restore/清空或全部暂存。
- 最终快照：D:/acrylic-pda-outbound/.local/d01-d18-20261010/received-scope-release-pause-final-01/。status.txt、dirty-hashes.json、files/、working.diff、staged.diff保存完整清单/副本/hash/差异；head.txt、branch.txt、remote.txt保存精确Git；protection.json、inherited-comparison.json、result.json保存保护核验。提交后事实块刷新单独保留为看板dirty，不循环提交自身SHA。

## 本批退出标准与实际结果

[范围双侧提交执行记录](D07_R09_SCOPE_RELEASE_2026-10-11.md)保存设计边界、失败与证据入口。本机设计、自审为.local/d01-d18-20261010/received-trusted-release-design.md及received-scope-release-self-review.md。

- 独立批准/库外根及锁identity绑定PREPARE；原closed lease/journal内DB排他投影；确认提交后新事务读回，再发布COMMIT；消费时核对两侧。只复用不可覆盖的耐久存储原语，结果是范围证明，服务保持RECOVERY_PENDING，普通写/删除能力false。
- 最终1293全新隔离目标两阶段各1测试、0失败/错误/跳过、exit0/sourceUnchanged。实际应用子进程在DB确认读回后、外部COMMIT前halt(73)，后继JVM重载原pin/请求/批准补COMMIT，未重新登记或签发原身份。同键并发/重放、非空迁移/原表及文件守恒均有实际证据。
- 缺DB侧、缺PREPARE、坏pending、冲突/未知文件、换根/锁、别名路径、同键异有效签名及异scope签名拒绝；匹配pending可完成。末SQL、批准/PREPARE尾改回滚；不可变UPDATE/DELETE触发器拒绝。
- 最后真实MySQL提交成功但确认丢失，持久UNKNOWN保留；verify/release/新authority均拒绝。证据.local/e2a-cross-20260925-1293/scope-release-unknown-commit.json指向围栏文件/hash。1293不能清除围栏或复用继续业务，历史COMMIT不代表该实例仍能签发资格。
- 独立Node范围/批准/原事实/编号消费者分别核20/14/22/8输入不变；含6/8/8反例、130原表/3签名、原生mysqlbinlog及三PID/halt校验，均exit0。工具独立消费不代表独立人员或现场验收。
- 受影响普通recovery/handover回归3317测试、116XML、0失败/错误/跳过、exit0/sourceUnchanged；不称全server或全部IT。收口重新计算两阶段及回归各2677项输入和四组消费者原件hash，无漂移，见received-scope-release-close-verification.json。本次只改文档，不机械重跑业务测试。
- 没有新增本代次资格、接收准备/激活/最终提交，也没有该链正向HTTP/Android、APK/模拟器/真机验证。合成SessionUser、运维身份/签名及主管声明是开发条件，不代原身份或现场事实。

报告目录为.local/d01-d18-20261008/r09-temporary-evidence-restore-mysql-1293/、r09-scope-restart-mysql-1293/和received-scope-release-regression-01/。独立消费者位于.local/d01-d18-20261010/received-scope-release-consumer-03/、received-scope-release-operations-consumer-02/、received-scope-release-physical-consumer-02/及received-scope-release-number-consumer-02/。原命令/XML/日志/输入diff/hash分别保留。

## 失败、环境与保全

- 1289因恢复后的测试启动未显式迁移，建trigger时新表缺失；修正后换新库验证，失败XML/log/datadir/dump/binlog保留。1290分配守卫拒绝未建库；1291/1292是中间输入，不代1293后补反例。其他既有失败/未验按历史记录保留。
- 1289及1291/1292/1293的retention重新核对dump成功、认证shutdown=0、原PID退出、监听空；目录.local/d01-d18-20261010/temporary-evidence-restore-mysql-<编号>/。保全/失败库均不再用。原1233只读未重跑；1150/1151/UI-06及1100–1102未重跑。
- 没有重型Gradle、任务MySQL/HTTP/页面/模拟器在途，19330/19434/5560/5173为空。既有3306/13316/ADB保持。全部进程/监听、C/D余量见final的processes.json、listeners.json、disks.json，活动聊天见writers.json。
- 新证据/临时文件/缓存在D盘，仅子进程配置TEMP/Java/cache，本次无清理。原失败库、dump/binlog、签名/HTTP/日志/XML、原资料及唯一证据保全。源码推送不等于.local/、私钥、APK或继承未跟踪材料已异地备份；新会话使用原主树及本机快照。

## 接续第一动作与约束

范围证明协议已完成；下一步是指定接收者的本代次新资格→接收准备/激活→最终提交。先写该最小纵向链退出标准并处理以下真实缺口，不重做已验协议，不把无接收者当正向验收完成。

1. 1293的scope-release-after.json中，原同仓设备DISABLED；另一ACTIVE设备在其他仓库且TEMP关闭。APPROVED案件的target_device_id/target_user_id仍空，原1233没有合法同仓接收机。应在新隔离目标补有依据、可审计的恢复态指定接收者准入/登记协议及可信证明；真正缺政策时单列阻断。不能重启原设备、借跨仓设备、伪造原登记、任意插入接收机后跳过核对，也不能重跑或用新fixture冒充原1233。
2. HandoverTemporaryTarget要求资格代次等于原正文sourceRecoveryEpoch，HandoverTemporaryActivationTicketStore保存source代次，HandoverTemporaryActivationIssuer据此签发；TemporaryRoundAccess要求新正文/资格与target传输代次一致。必须保留原事实/来源映射，另发target代次限定scope/接收者的资格，旧正文/旧票据不能改成可激活资格。
3. RecoveryReleaseInventoryCapture、RecoveryReleasePhysicalReview和RecoveryReleaseScopeSemantics.knownWork保持全原表、完整成员及beforeCommit守恒。新设备/资格/PREPARE/激活会改变原表，须补可信签名且有精确效果边界的投影与核对，参考RecoveryReleaseApprovedRows。不能删除比较、排除整表或静默接受新增行。本项只完成调查，尚未实现或验收。
4. TemporaryIssuer普通TEMP_WORK资格有首轮能力，不能直接用作恢复准入。新资格须隔离无关首轮创建/扫描/提交，实时锁定核验接收者权限、仓库和设备。DeviceService普通入口仍被恢复门阻断；不开NORMAL、不开放任意permit、不将RecoveryScopeReleaseCoordinator.Result变成公共写许可。
5. 保留同一DataSource、closed lease/journal、身份/来源/仓库锁序及beforeCommit重验。closed事务内不能绕operation token裸调authority.read。批准后清单摘要与主管proof时点分开；原ABORT/占用/账务/正文保持，不能造原机STOP/ACK。
6. 跨所有树核对新编号/迁移与写者后，再建立全新隔离恢复目标，只读复用原1233的dump/binlog/pin。1293有UNKNOWN且已保全，1291/1292及其他保全/失败库也不可再用。实际MySQL/HTTP/Android正向组合和原键/异文/并发/重启/丢响应/末SQL及独立消费达到标准后，明确路径提交推送。

核对入口：docs/parallel/R09/RECOVERY_CONTROL_CONTRACT.md、RECOVERY_EVIDENCE_CONTRACT.md、DEPENDENCIES.md及ops，源码以主树为准。原source快照/终点SHA分别为1e23ca32e6c67da0ab039022ef6d4917453ec961a061aa320de3efe602d5864b和3393867cb13b75b9228ff067a5482b60fac6d0ec1d7caa7f9516a70f1f18694c；路径.local/e2a-cross-20260925-1233/checkpoint-snapshot.json及checkpoint-source-final.json，收口重核一致。

D06/D07整体、FORMAL各阶段入口、其他无可信归档来源/R09跨截点重建、D08–D18及第8节未完成，D18无最终候选。D04候选排除/D05取消释放缺政策保持拒绝，不阻断独立可执行项。原资料/旧BCD/526、历史关键未验及C01–C08限制保留，开发缺口不能转现场。

## 新会话接续指令

~~~text
/goal 恢复并继续完整执行 D:/acrylic-pda-outbound/docs/AUTONOMOUS_DEVELOPMENT_GOAL_2026-10-07.md

严格按 docs/history/D01_D18_PAUSE_2026-10-11_SCOPE_RELEASE.md 的“新会话接续指令”恢复。本次暂停仅为用户要求收口换会话，目标仍是D01–D18及第8节全部闸门，不缩小为一个批次。
先读主树AGENTS.md、docs/GIT_WORKFLOW.md、看板当前区/生成事实块、本收口文档、D07_R09_SCOPE_RELEASE_2026-10-11.md及原总计划；历史只定向引用，不恢复旧角色/过期指令。
核实际HEAD/远端、全部dirty/未跟踪、写者、进程/监听、C/D余量和 .local/d01-d18-20261010/received-scope-release-pause-final-01/ 最终快照；逐hash保护29条继承改动及最终看板dirty、69历史比较和22原STOP。单助手/单写者，不全部暂存、不restore/reset，不创建无关工作树。
范围双侧协议业务提交a4a37f772826fdf1f23325fb1278ae581dc6b833已推；文档收口终点以final/head.txt、remote.txt为准。1293两JVM/实际halt/丢确认围栏、四组独立消费及3317普通回归已验，2677项输入无漂移，不机械重跑。1293持久UNKNOWN保留，保全/失败库均不复用；原1233只读不重跑；1150/1151/UI-06和1100–1102不重跑。
从范围证明之后接续：先解决原来源没有合法同仓接收机的缺口，补可信且可审计的指定接收者准入及精确签名效果核对，再接target代次新资格→接收准备/激活→最终提交。原正文/source代次与target代次分开、保留来源映射，旧票据不激活。不得重启原设备、借跨仓设备、任意插入后跳过守恒、用新fixture冒充原来源、删除原清单比较或开NORMAL。首轮资格隔离/closed journal/锁序等按收口文档约束。
先明确退出标准，再在跨树核对后的全新隔离目标开发；实际MySQL/HTTP/Android组合、权限、原键/异文、并发、丢响应、重启、末SQL回滚、独立消费及受影响回归/自审后，明确路径提交推送codex/development。
随后沿原计划继续D08–D18和第8节，不漏D06/D07其他缺口。D04/D05未定政策、原资料/旧BCD/526、历史失败及C01–C08限制单列，不外推局部通过；全部闸门满足才可complete。
生产默认/签名和七类生产决定handler/POST/放行/证据写入/关闭能力关闭，localDeletionAllowed=false；不接原库/现网、不合main、不发布部署/标签/Release、不正式签名、不绕设备审批。产物及仅本任务子进程TEMP/Java/cache放D盘，失败库/原件/唯一证据保留，不改全局环境。
~~~
