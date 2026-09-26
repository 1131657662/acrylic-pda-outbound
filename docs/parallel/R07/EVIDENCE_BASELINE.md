# 证据与基线

## 1. 核对方法与范围

2026-09-18 先在原目录只读检查 `AGENTS.md`、Git状态/远端/worktree、Git说明、路线、总进度、V2.3、已确认规则和UI规范，再从固定提交创建worktree。源码核对以 `2369392076083a5a4f59d8ea3d6edebe5067a03f` 为准，以下链接均指向本worktree中的基线文件。源码阅读不等于运行验证。

创建前：目标目录不存在，同名分支不存在，仓库只有原开发worktree。远端为 `https://github.com/1131657662/acrylic-pda-outbound.git`，原目录分支 `codex/development`，起点提交标题 `docs(r05): 记录订单同步测试部署核验`。

原目录初查已有未提交文件：`AGENTS.md`、`README.md`、`docs/DEVELOPMENT_PROGRESS.md`、`docs/DEVELOPMENT_ROADMAP.md`、`docs/design-system.md`、`docs/pda-page-template.md`，以及未跟踪的 `docs/PDA_APP_UPDATE_DESIGN.md`。只读参考其中新登记的R05应用升级边界，不暂存、恢复、复制或覆盖它们。并行会话后续可继续改动，不能用本记录声称原目录一直不变。

续接设计时又只读核对了R05升级代码及R06/R08/R09独立目录的未提交设计/工具，路径、观察哈希和明确差异记录在[跨任务联审](CROSS_TASK_ALIGNMENT.md)。它们是另列的工作中证据，不替换下表固定提交基线，也未复制其实现到本分支。

## 2. 已确认规则

| 证据 | 约束及本设计落实 |
| --- | --- |
| [AGENTS](../../../AGENTS.md)、[Git说明](../../GIT_WORKFLOW.md) | 短期分支来自开发分支，明确文件范围，动作与验证分别报告；本轮用户指定仅更新本任务目录 |
| [路线](../../DEVELOPMENT_ROADMAP.md)、[进度](../../DEVELOPMENT_PROGRESS.md) | R05仍在开发，R07含换机、更正、退货再出库、库存差异；R08默认原出库日最新净值；不能把PC换人当PDA换机 |
| [V2.3](../../系统设计方案与开发计划-V2.3.md) §6.5/6.7、7.4、10.8 | 原设备/原人员/原授权/原序号不可变，清单逐条绑定；缺高水位不可假造精确顺序 |
| V2.3 §5.7、7.5、12.12 | 接收输入时固定周期；更正追加凭据；原成功回执不变；只反向实际账务，结果按版本固定 |
| V2.3 §6.6/6.8、13.4 | 临时来源和活动提交槽唯一；恢复代次变化先对账，登录/新基线不解除阻断 |
| [Q01～Q16](../../OPEN_QUESTIONS.md) | 48小时；0包不可首次出库；退货核实后可新周期；精确重量；上游全量可重叠；本地90天/云端至少5年；待审30分钟仅提醒；默认原出库日净值；B模式 |
| [总UI规范](../../design-system.md)、[Web模板](../../web-page-template.md)、[PDA模板](../../pda-page-template.md)、[状态语义](../../status-semantics.md) | 一个阶段一个主动作，真实状态、原请求恢复、分页及上下文、48/56dp、200%字体、FLAG_SECURE |

## 3. 源码证据矩阵

| 编号 | 文件/符号 | 已有行为 | R07差距/影响 |
| --- | --- | --- | --- |
| E01 | [RecallAccess.java](../../../server/src/main/java/com/acrylic/outbound/recall/RecallAccess.java) `pda` | 当前在线人员、设备须等于原授权；写入要求NORMAL及当前代次 | 新机代传、原人员不可登录、灾后证据入口均不能直接复用为已可用能力 |
| E02 | [RecoveryEvidence.java](../../../server/src/main/java/com/acrylic/outbound/recall/RecoveryEvidence.java)、[RecoveryAudit.java](../../../server/src/main/java/com/acrylic/outbound/recall/RecoveryAudit.java) | 已提交原captures/scans/events逐条摘要不变；审计仅一来源，从0至停止连续覆盖 | 多设备审计需逐来源核验；不能把收到一个恢复包当作全链覆盖 |
| E03 | [RecallStops.java](../../../server/src/main/java/com/acrylic/outbound/recall/RecallStops.java) `acknowledge` | 已接收包、摘要、高水位和审计终点绑定，撤销原授权并签停止证明；仍保留订单 | 可用原机停止基础；无损坏机替代证明，不提供新机激活 |
| E04 | [ContinuationChecks.java](../../../server/src/main/java/com/acrylic/outbound/continuation/ContinuationChecks.java)、[ContinuationActivation.java](../../../server/src/main/java/com/acrylic/outbound/continuation/ContinuationActivation.java) | 原人原机、旧批次ABORTED/REJECTED、无恢复冲突、核验集合及高水位+1 | 换机及尚无旧批次的交接不能假装现有续作已支持 |
| E05 | [ContinuationVerifier.kt](../../../android/app/src/main/java/com/acrylic/outbound/pda/continuation/ContinuationVerifier.kt)、[ContinuationRepository.kt](../../../android/app/src/main/java/com/acrylic/outbound/pda/continuation/ContinuationRepository.kt) | 校验新deviceId等于原freezeDeviceId；本地激活依赖原恢复和原冻结批次 | 需独立导入/接收事务；不得删校验就开放换机 |
| E06 | [SubmissionEvidence.java](../../../server/src/main/java/com/acrylic/outbound/submissions/SubmissionEvidence.java) `captures/events/scan` | captures的deviceCaptureSeq按单个Long递增；events按单个localSeq索引并绑定顶层设备/人员 | A/B均有序号1会拒绝或错误定位；必须用来源复合键及逐记录原资格核验 |
| E07 | [WorkCapture.kt](../../../android/app/src/main/java/com/acrylic/outbound/pda/work/WorkCapture.kt)、[WorkScan.kt](../../../android/app/src/main/java/com/acrylic/outbound/pda/work/WorkScan.kt)、[WorkOrder.kt](../../../android/app/src/main/java/com/acrylic/outbound/pda/work/WorkOrder.kt) | 输入/事件已有来源复合唯一键；WorkScan从capture取得来源；guard按条码；设备计数器独立 | 不能据此宣称支持导入；需保护接收端原有计数器、guard和作业容器，解决本地同单冲突 |
| E08 | [SubmissionStock.java](../../../server/src/main/java/com/acrylic/outbound/submissions/SubmissionStock.java)、[SubmissionFinalizer.java](../../../server/src/main/java/com/acrylic/outbound/submissions/SubmissionFinalizer.java) | 锁包核验周期、占用、实时库存和固定快照；审批不覆盖冲突 | 交接不得释放再抢占，旧周期不可自动改新；全部最终写入口须共用互斥边界 |
| E09 | [SubmissionAccounting.java](../../../server/src/main/java/com/acrylic/outbound/submissions/SubmissionAccounting.java)、[PcAccounting.java](../../../server/src/main/java/com/acrylic/outbound/pc/PcAccounting.java) | PDA/PC均写dispatch_record、结果v1、产品汇总、可选扣账及COMPLETED | 更正须覆盖两渠道；PC没有PDA capture不得伪造。尚无更正后逐包结果快照 |
| E10 | [V9结构](../../../server/src/main/resources/db/migration/V9__formal_submissions.sql)、[V15结构](../../../server/src/main/resources/db/migration/V15__pc_manual_outbound.sql) | ledger的dispatch_id唯一，数量仅-1且重量<0；结果绑定唯一提交且总数/重量>0；V15兼容PC来源 | 不能直接写多笔差额/反向或净0版本；迁移前必须重设约束，保留首次成功0包禁令 |
| E11 | [PcExceptionHandover.java](../../../server/src/main/java/com/acrylic/outbound/pc/PcExceptionHandover.java) `apply` | 在线指定人员交接，writer_fence及轮次递增，原子处置 | 不传PDA原始输入、审计或离线资格；只能借鉴阶段/实物核验交互 |
| E12 | [SnapshotWriter.java](../../../server/src/main/java/com/acrylic/outbound/inventory/master/SnapshotWriter.java)、[DiscrepancyWriter.java](../../../server/src/main/java/com/acrylic/outbound/inventory/master/DiscrepancyWriter.java)、[InventoryQueries.java](../../../server/src/main/java/com/acrylic/outbound/inventory/master/InventoryQueries.java) | 发布上游在库声明、记录差异和已扣账重现冲突；查询OPEN差异；不清出库占用 | 不具备完整差异审批/关单；仅看DEDUCTED不足以列全“未扣账但已出库重现”差异 |
| E13 | [SyncService.java](../../../server/src/main/java/com/acrylic/outbound/sync/SyncService.java)、[SyncStreamStore.java](../../../server/src/main/java/com/acrylic/outbound/sync/SyncStreamStore.java)、[SyncApply.kt](../../../android/app/src/main/java/com/acrylic/outbound/pda/sync/SyncApply.kt) | ORDER流发布序号、固定投影基线及事务游标 | 尚非条码可用状态流，不能接CYCLE_REOPENED就认为已经可重扫 |
| E14 | [AuditWriter.java](../../../server/src/main/java/com/acrylic/outbound/audit/AuditWriter.java) | 后台operation_event写入 | 不等于PDA通用审计补传及逐来源确认协议；与E02分开 |
| E15 | [RecoveryProtection.kt](../../../android/app/src/main/java/com/acrylic/outbound/pda/sync/RecoveryProtection.kt)、[ServerState.java](../../../server/src/main/java/com/acrylic/outbound/common/ServerState.java) | 持久RECONCILING；当前代次仍数字字符串；NORMAL/登录不解锁 | V2.3外部UUID恢复控制及解阻断证明未实现；现有错误码与总体方案名称不同，需兼容映射 |

## 4. 历史验证与现状不能混用

[R05同步交付记录](../../R05_ORDER_SYNC_GUIDE.md)记载历史24单元/262集成、81 Android主机测试及90路径契约检查，另记独立test部署提交 `4e0e249476aded2ab491751143a94e7e37c02f30`。这些不是本轮运行结果；总进度中“本轮未部署”的阶段叙述与后补test部署记录应按时间和范围读取。R05条码流、审计补传、持续任务以及R09完整恢复仍未完成。

本轮未连接业务数据库、操作PDA、调用业务写接口或部署服务。仅进行源码/文档比对、设计审阅及文档检查；[验收方案](ACCEPTANCE_PLAN.md)全部是将来待执行场景。
