# D07/R09 native verification原行绑定子项

只收口原生归档事实绑定及事务验证子项；D07/R09、D06/D07整体与D01–D18总目标均未完成。版本事实见[看板生成块](../ORCHESTRATION_STATUS.md)。原canonical/native来源恢复、主管围栏→接收最终链仍未验证，不用合成身份或当前数据库行替代原历史可信事实。

## 输入、设计与实现

输入982570f57c06936486303c020aecab43d0ffec2c，树D:/acrylic-pda-outbound，分支codex/development，HEAD/实际开发远端一致。native-facts-resume-01保全全部dirty副本/hash/diff、进程/监听/磁盘；69次继承比较及原22保护项零漂移。本聊天唯一写者，其他项目聊天notLoaded；既有MySQL/ADB未操作。开始接续的32条dirty包含前模型未提交native gate，全部继承保护。

设计在`.local/d01-d18-20261010/native-verification-binding-design.md`。沿已授权自主技术决策实施，使用brainstorming、single-responsibility、security-practices、testing-best-practices；没有子代理或独立人员审查。

RecoveryTemporaryNativeHandoverFacts要求显式TEMP source/canonical、原set/STOP水位、正文/无正文ABORTED终态身份一致。archive.source已经是STOP水位；冻结payload审计数少一，不能再给archive的auditThroughSeq加一。STOP数值是原生JSON整数，外部描述符和payload数值字段是规范字符串，二者不混用。两参数shape读取仅包内可见，无公开事务外native读取入口。

RecoveryTemporaryNativeVerification在同一DataSource实际事务内FOR UPDATE锁既存verification，核ID/set/scope/submission/round/hash/result与算法/验证版本。显式verificationSha256绑定完整原列，包括known_facts_sha256、findings原字节、verified_at；摘要协议是sorted canonical JSON加单个末尾LF，时间保留微秒表示。调用方必须独立保全这个原行摘要；此检查不建立独立根保管或原权限历史。

RecoveryTemporaryEvidenceRestore.withVerifiedNativeArchive复用closed归档事务，防变复制输入，在回调前、返回后和beforeCommit核原行；回调/SQL/重验失败一并回滚，提交后才返回。抽出的内部consumer只负责原行在事务中不变。没有生成/插入verification，没有Bean/HTTP/签发/普通准入；普通写权和localDeletionAllowed均false。HandoverCommandService仍要求自己拥有外层事务，不能直接嵌套调用。

## 当次验证与失败保全

- 定向03为23/0/0/0、1XML、exit0/sourceUnchanged，覆盖原生整数STOP、冻结/STOP分界、缺canonical、原set/hash/水位、正文来源、STOP和终态篡改、必需原verification摘要。此前定向01为22项通过；03补完整行摘要后取代其交付输入。
- 定向02是编译失败、0测试；AssertJ重载对嵌套事务泛型返回值推断不明确，改为明确Integer变量后03通过。原command/log/result保留。更早根工程不存在server子项目的命令失败和三项自造形状测试只在native-facts-early-01重建备注/旧XML记录，原stdout只在聊天工具回执，不伪称落盘原日志。
- 1207保守目录子串守卫命中两个无关随机fence目录，拒绝发生在建库前，native-allocation-rejected-01记录；没有复用或改造它们。1208/1209分别跨33树核分配的新空一次性实例。
- 1208真实MySQL1/0/0/0、exit0/sourceUnchanged：Flyway实际schema与明确合成canonical/E2A/verification；事务外拒绝、缺行/异来源、状态/版本/set hash拒绝，known-facts/findings/verified_at在进入前/回调后/beforeCommit修改拒绝且全表回滚；尾SQL失败、独立连接锁超时、commit前不可见/之后可见、消费者幂等。这是内部native consumer的数据库验证，不是原来源恢复或完整native归档正向。
- 1209真实MySQL1/0/0/0、exit0/sourceUnchanged：新空目标实际导入1189保全截点并恢复原四表，完整withVerifiedNativeArchive因原canonical缺失拒绝，未调用消费者，全表/文件/迁移守恒，RECOVERY_PENDING。不能用该拒绝代正向恢复。
- 独立Node消费者02为13输入hash不变、exit0；从完整快照逐原列还原verification摘要、核source/set/round及result，1208全业务表/文件/schema/迁移守恒，仅测试探针增一行；1209全快照完全一致；两dump独立重算一致并核认证停机回执。01遗漏控制协议末LF导致摘要不符，原脚本/失败日志保留，修后02通过。独立工具不等于独立人员。
- 自审发现完整findings合法长度可超过外部控制JSON小消息上限；改为native完整行canonical UTF-8加LF计算摘要，小行协议/摘要不变。新1210真实MySQL1/0/0/0、exit0/sourceUnchanged，复验1208全部反例并实际写最大findings、正确摘要消费后回滚守恒；保全并停机。独立Node native-consumer-1210-1209-01再次核13输入不变、exit0。1209缺canonical拒绝在摘要计算前，代码路径/原输入未受此修改影响，沿原证据复用。
- 普通回归01为3235/0/0/0、107XML、exit0/sourceUnchanged；这是findings修复前结果。修复后最终recovery/handover普通回归02仍为3235/0/0/0、107XML、exit0/sourceUnchanged，覆盖最终产品/测试输入；普通test不含IT，非完整server回归。

无本批HTTP、Android、APK、页面、模拟器、真机、部署或正式签名验收。1150/1151/UI-06原有效证据复用，未重跑；1100–1102未重跑，失败库未复用或删除。原资料/旧BCD/526、原身份和恢复限制继续保留。

## 证据、环境与接续

命令/源码前后hash/原log/XML/result在`.local/d01-d18-20261008/r09-native-facts-*`及`r09-temporary-evidence-restore-mysql-1208/1209`。分配/runner/provision/dump/retention在`.local/d01-d18-20261010/temporary-evidence-restore-mysql-1208/1209`；实际datadir/binlog/dump及完整快照在`.local/e2a-cross-20260925-1208/1209`。Node源/失败/输入hash/result在native-consumer-1208-1209-01/02。全部仍是本机证据，未上传或异地备份。

1208/1209/1210均dump重算一致、认证停机、原PID退出、19330/19434空。新增产物、任务TEMP/Java/cache均D盘，无清理。精确C/D余量与全部dirty见后续native-binding范围checkpoint；回归期间不改产品/测试输入。

只读盘点1131在native-source-inventory-1131-01：22原输入hash不变，逐snapshot原列绑定canonical/终态/三水位/4条intake与20原object文件字节/哈希，不连数据库、未验原签名或独立pin。确认该原ABORTED是已收正文，与本接口未收正文边界不同；1132/1137/1144同类终态也只读核得正文存在，不能直接当本接口正向来源。

下一步须先明确canonical关联且未收正文来源是否能由真实产品链产生；若必须使用已收正文来源，按其原submission_payload/REVIEW_RESERVED、native intake/verification/审计/slot设计独立受约束恢复接口，不放宽未收正文接口或篡改原终态。原准备与权限连续性缺口不得补造或借用。保全新开发来源精确原行/独立pin后跨截点恢复，再验证完整native归档正向和主管围栏→准备激活→接收最终提交。不得篡改1189/1206来源来伪造canonical、INITIAL_ACCEPTANCE或verification。本轮是开发接缝阶段备份，完整正向仍未通过。

生产默认、签名与七类生产决定能力关闭，localDeletionAllowed=false；不接原库/现网、不合main、不发布部署/标签/Release、不正式签名。D04/D05政策阻断与D08–D18及第8节总闸门保持未完成，C01–C08现场/生产清单不覆盖开发缺口。
