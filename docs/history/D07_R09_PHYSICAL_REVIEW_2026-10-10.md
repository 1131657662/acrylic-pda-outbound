# D07/R09恢复态主管实物确认

沿[原目标](../AUTONOMOUS_DEVELOPMENT_GOAL_2026-10-07.md)与[暂停接续](D01_D18_PAUSE_2026-10-10_RECOVERED_FAULT.md)，本批仅为既有APPROVE建立恢复态接缝。D01–D18及第8节总目标未完成；下一步仍为独立运维批准、两阶段可信释放、本代次资格及接收机最终提交。版本事实见主看板生成块，替换前结论见[归档](ORCHESTRATION_STATE_before_physical_review_2026-10-10.md)。

## 输入、范围与自审

主树D:/acrylic-pda-outbound，codex/development，输入cc9947cc1f9a3f33f032f09f11f29c949557463e；当时实际开发远端相同，main未变。本聊天单助手唯一写者，其他项目聊天idle/notLoaded。received-physical-resume-01保存45条dirty及逐hash副本；前received-reconcile-postcommit-01的29条继承路径全部hash未变，69历史比较/22原STOP保护无漂移。快照第一次远端读取SSH重置，失败回执保留；remote-retry.json已成功重新取得相同远端，不能把失败的缺文件当成功。

产品变更为HandoverRecoveredPhysicalReview、RecoveryReleasePhysicalReview、精确APPROVE效果验证/对照副本及既有服务的package-private接入。无迁移/公开路由/默认配置变更，原NORMAL准入不变。设计与作者自审在.local/d01-d18-20261010/received-physical-review-design.md、received-physical-self-review.md；不是独立人员审查。

APPROVE绑定原FAULT_FENCE、closed authority及原归档/native，在同DataSource的closed外层事务中执行既有身份/expected/来源/逐包清单/库存/审计/防重。只允许唯一APPROVE回执、审计、防重、原序RETAINED清单和case批准字段。通过内存比较副本剥离这些已精确验证的效果，再对固定全130表核原终点；不改写原表来配平。原ABORT/占用/库存/账务/原正文代次和STOP/ACK不变。

事务外核历史与编号，事务内同控制/原件/全表校验，并在beforeCommit再次核对。不可用、unknown或协调器busy保持拒绝，未放宽journal。证明对象仅说明MATCHED和原事实保持，releaseAllowed/ordinaryWritesAllowed/localDeletionAllowed全部false。测试实物声明与主管身份均为合成fixture，不代现场实物。

## 实际证据

所有专项由原1233只读dump/binlog/原件恢复至全新目标，不重跑原来源。目录在.local/d01-d18-20261008/r09-temporary-evidence-restore-mysql-编号，目标在.local/e2a-cross-20260925-编号，保全在.local/d01-d18-20261010/temporary-evidence-restore-mysql-编号。

- received-physical-compile-01：compileTestJava exit0、0测试，不能算业务通过。
- 1275中间输入：MySQL+应用专项1/0/0/0，原键重放/6项拒绝/末审计SQL回滚；后续扩大反例后不以它代最终输入。
- 1278：专项1/0/0/0，exit0/sourceUnchanged。自批、无权、UNCHECKED、缺包、旧代次、错案件拒绝；末审计SQL/未知其他仓库变更时审批全回滚；两项跨仓尾写回滚；8项语义篡改、同键异文拒绝；并发结果或严格busy/unknown拒绝后原键恢复同字节，全库不变。
- received-physical-regression-01：普通recovery/handover3309全过、114XML、0skip、exit0/sourceUnchanged；不代表完整server或专项IT。其后仅增加跨JVM验证辅助代码和profile入口，产品源码未改；后补辅助类需由新专项编译/执行。
- received-physical-consumer-01：独立Node首版21输入不变；consumer-02进一步核对原payload pin及原序清单，22输入不变、130表成员/原终点、三份RSA签名/精确APPROVE效果与8反例通过。received-physical-number-consumer-01用原生mysqlbinlog独立回放编号、8输入不变。工具独立消费不等于独立人员/现场验收。
- 1278dump独立核对、认证停机exit0、原PID退出、19330/19434空；失败与中间库保留，未复用。

## 失败与限制

- 1276并发测试只预期CONTROL_TRANSACTION_OUTCOME_UNCONFIRMED，实际既有CONTROL_COORDINATOR_BUSY被断言遗漏。仅修测试限定拒绝集并要求竞争后成功/全库不变；1278复验通过。1276dump/log/XML及停机保留。
- 1277运行器未先显式分配，复制证据目录时失败，未建库；received-physical-runner-1277-failure.json保留。1279跨33树分配守卫遇目录子串拒绝，未建库，分配拒绝回执保留。
- 1280第一JVM确认通过1/0/0/0，第二JVM被测试profile一次性marker拒绝，未进业务；整个restart批次失败。原库dump/两次报告/停机保留，不复用。专用重启入口只允许同一在途批次的已成功阶段，拒绝已有retention目录，核实际datadir/schema/user/port/binlog与关闭态；默认初始入口仍要求空库且未运行。
- 1281新库首次确认及第二JVM重放各1/0/0/0，均exit0/sourceUnchanged；首进程33128、重放28648，重载独立pins/归档请求，无新身份/注册，原批准字节相同且DB/文件前后相同。不是OS断电或MySQL进程重启测试。两报告目录分别为r09-temporary-evidence-restore-mysql-1281与r09-physical-restart-mysql-1281。
- 最终1281独立physical-consumer-03复核22输入/130表/8反例与原payload清单；number-consumer-02原生mysqlbinlog复核8输入，均未变。dump、认证停机、PID退出与端口空均通过。close-preflight-01完整49条dirty、69历史/22STOP保护通过；无重型任务在途。

无新增业务正向HTTP、Android、APK、模拟器、真机、正式签名或部署。1233历史HTTP/Android不能外推本接缝终链。1150/1151/UI-06、1100–1102未重跑；原资料/旧BCD/526和C01–C08仍单列。生产默认与签名/七类决定能力关闭，不接原库/现网、不合main、不发布部署。

## 下一动作

完成跨JVM重放证据及本批明确路径提交后，按received-trusted-release-design.md实现独立运维批准→库外PREPARE→DB排他锁提交revision/epoch/范围→库外COMMIT→实例核两侧证明。Unknown范围保持隔离，RecoveryNormalRegistration/Intent不充当业务释放。随后另发目标代次资格、准备/激活和最终提交，再沿D08–D18；本批完成不缩小总目标。
