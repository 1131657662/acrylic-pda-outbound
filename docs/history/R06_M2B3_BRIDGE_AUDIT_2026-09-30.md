# R06 M2b-3 TEMP 桥接审计（2026-09-30）

## 结论

R06 M2b-3 当前不是一个可以通过删除首轮守卫、复用正式续作端点或把 REJECTED 直接当作释放来完成的缺口。现有代码明确 fail-closed：TEMP 只能首轮确认，正式续作和实物处置只接受正式 recall/recovery package/assignment 关系。要实现“拒绝 → 原机完整恢复/停止证明 → 卸货核实 → 释放占用”和跨来源独立审计，必须增加 TEMP 专用的拒绝回执、处置证据、来源锁与续作票据模型。本轮不改产品行为。

## 可复用的正式路径

1. ContinuationChecks.validate（server/src/main/java/com/acrylic/outbound/continuation/ContinuationChecks.java:20-37）要求 recall 已停止、存在 recovery package、旧 submission 属于该 assignment、旧批次为 ABORTED/REJECTED、订单处于 RECALL_PENDING，且只能原人原机继续。它不是 TEMP 拒绝的入口。
2. DispositionApprovals.approve（server/src/main/java/com/acrylic/outbound/continuation/DispositionApprovals.java:24-53）要求 order:dispose、停止后的 recovery package、原包摘要和逐包物理凭据；凭据按 recovery package 与 origin scan 唯一保存。
3. DispositionStock.apply（server/src/main/java/com/acrylic/outbound/continuation/DispositionStock.java:20-41）只处理 CONTINUATION_RESERVED 占用，RELEASE 删除占用，QUARANTINE 改为隔离并绑定处置凭据，再写回应用 submission。
4. ContinuationActivation.activate（server/src/main/java/com/acrylic/outbound/continuation/ContinuationActivation.java:20-37）在已批准的 continuation 上核对 recovery package、保留清单、计划摘要和序号高水位，生成签名激活回执。它依赖正式 assignment/recall。

## TEMP 侧的 fail-closed 约束

- SubmissionReview.decide 在 SubmissionReview.java:35 明确拒绝 TEMP 审批，返回 TEMPORARY_CONFIRMATION_REQUIRED；FORMAL 的 APPROVE/REJECT 写入 submission_review 并由正式 finalizer 处理。
- SubmissionControl.temporary 在 SubmissionControl.java:23-29 要求 workRound==1、无 assignmentId、无 continuationId，防止把 TEMP 正文伪装成正式续作。
- TemporaryConfirmationSource.read 在 TemporaryConfirmationSource.java:26-27 只接受 TEMP、REVIEW_REQUIRED、首轮；SubmissionAccountingPolicy.temporary 在 SubmissionAccountingPolicy.java:37-45 同样锁定首轮 TEMP 来源和当前映射目标。
- CommandUnitOfWork.operation 在 CommandUnitOfWork.java:42-46 只注册 NORMAL_CONFIRM、SHORT_APPROVE、LINK。CommandPermissions 虽列出 PHYSICAL_DISPOSITION→order:dispose 与 REOPEN_WORK→order:resume，TemporaryCommandShape 也允许这两个命令类型，但它们没有可执行 CommandOperation，运行时仍返回 unsupported。

## 缺失连接点

1. 拒绝回执与原键：TEMP REJECT 没有独立写入口、不可变命令回执或单一审计事件，无法把拒绝原因、正文摘要、原来源、设备/恢复代次和停止证明绑定成可重放事实。
2. 无正文/未建正式单恢复包：正文前 abort、未创建 canonical formal order 的 TEMP 不能直接套 recovery_package，也不能伪造 assignment、0 包成功或正式 submission。
3. 处置证据与占用锁：现有处置只对 CONTINUATION_RESERVED 生效；TEMP 待审占用的释放需要单独的来源/订单锁、逐包 RETAIN/RELEASE/QUARANTINE 凭据、周期校验和幂等回执。
4. 续作票据与来源审计：正式续作票据携带 assignment、recall、previous submission；TEMP→FORMAL 或 TEMP→TEMP 的跨来源续作必须保留来源分离、各自审计序和原请求恢复，不能只把 canonical 指针切换后复用正式路径。
5. 权限与策略边界：order:dispose/order:resume 的代码枚举不等于 TEMP 生产授权；P-R06-01 要求拒绝本身不释放占用，必须由卸货核实和处置命令成功触发。

## Fail-closed 决定

- 不删除 round==1、assignmentId/continuationId 约束，不把 TEMP REJECTED 直接映射为 RELEASE，不把正式 recovery package 当作 TEMP 处置证据。
- 需要后续实现时，至少新增 TEMP rejection receipt/事件、来源锁与恢复代次、逐包处置证据、占用迁移、续作 ticket，以及 TEMP/FORMAL 分离的审计和原键幂等；再分别做隔离 MySQL、真实 HTTP 和组合回归。

## 状态与后续

本审计子项完成为缺口登记，不是业务功能完成。由于缺失连接点需要新表/端点/锁图，按自主方案转入 R07-P08-A 的 append-only 隔离迁移；P-R06-01/P-R06-04 继续列为未验，生产入口保持关闭。

## 证据索引

- 政策依据：docs/BUSINESS_POLICY_R06.md:13,16,26-29,35-38。
- 设计依据：docs/R06_M2B_IMPLEMENTATION_PLAN.md:29-35,55-59,78,102-112。
- 源码依据：上文列出的 10 个 Java 文件与行号；本审计未修改产品源码、迁移或默认开关。
