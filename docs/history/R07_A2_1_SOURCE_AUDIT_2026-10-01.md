# R07 A2.1 源码与证据审计（2026-10-01）

## 审计范围与方法

本审计只读核对 BUSINESS_POLICY_R07 的 P-R07-05～P-R07-09、R07-1 命令层、handover/correction 源码、V42～V51 迁移、现有 R07 测试及 2026-09-30/2026-10-01 的专项报告。没有修改产品代码、迁移、默认开关或既有测试，也没有把历史报告重新计作本轮运行。

真实断点以源码和近期报告为准。剩余计划中的旧表格仍把 R07-1 outbox 和 A2.2 矩阵写成未落地；近期 S38/S39 报告和当前源码已经证明这两项有实现与实跑证据，本审计把该文档偏差单列，不反向改写历史原文。

## 逐条结论

| 政策/子项 | 源码事实 | 近期证据 | 当前结论 |
| --- | --- | --- | --- |
| P-R07-05 主管审批 | HandoverCommand 使用 order:handover/order:resume（HandoverCommand.java:27-28）；V44 将 order:handover 绑定 WAREHOUSE_ADMIN（V44__handover_approval_permission.sql:3-13）。源码与迁移没有 SUPERVISOR 角色。CorrectionCommand 也复用权限门。 | R07_SUPERVISOR_SIGNED_HTTP_2026-10-01 证明迁移后角色在测试库可分离签名；R07_SUPERVISOR_WITHDRAWAL_HTTP_2026-10-01 证明审批人/申请人两阶段身份边界。 | 换机/更正主审批门已编码且有专项实跑，但“主管”岗位与 WAREHOUSE_ADMIN 映射仍需业主确认；库存差异没有独立审批门；补扣入口未实现。 |
| P-R07-06 三天补录 | CorrectionOperations.java:约 90-150 的时间窗口校验限制 CREATE 的 businessOccurredAt；CorrectionStore.java:约 376-386 以原始结果版本创建时间作为基准。 | CorrectionPolicyLimitsIT 有窗口边界的定向落点；未见 MOVEMENT_DATE 或财务锁期实现。 | 更正 CREATE 的三天边界已编码，交接/故障补录、MOVEMENT_DATE、财务锁期例外仍缺。 |
| P-R07-07 故障最低证据 | HandoverOperations.java 的 APPROVE 分支要求 DEVICE_ACKED、COMPLETE、ABORTED、MATCHED、STOP_PROOF/清单；FAULT_FENCED 只在设计/枚举中出现，未形成独立分支。 | 当前 R07 专项均为原机可用/更正链；没有故障换机真 HTTP 或代传证据。 | 故障换机整条分支未实现；“主管同意即可”的最低证据政策没有接入，仍不能当作车辆放行或库存释放。 |
| P-R07-08 误批撤销 | CorrectionStore.java:308-326 实现原审批人发起 WITHDRAW、原申请人确认 WITHDRAW_CONFIRM；CorrectionStore.java:327-350 写逐笔 reversal/cycle reopening；V48 status 扩展并保留原回执。Correction 包没有 bale_commitment 引用。 | R07_SUPERVISOR_WITHDRAWAL_HTTP_2026-10-01 通过两阶段分离身份、同键重放和逐笔账务回退。 | 撤销身份链与账务回退已有实跑；占用回退缺失，撤销过期/拒绝语义无政策依据，更正 append-only 仅有未完成的 V51 草稿，803 断言失败轮未完成验收。 |
| P-R07-09 附件容量 | CorrectionOperations.java:约 60-145 校验单条最多 5 个、单文件 10MB、单次 50MB，并依据客户端 sizeBytes/sha。 | CorrectionPolicyLimitsIT 有容量反例；未见上传端点、真实字节校验、持久附件存储或页面/限流/保留。 | 上限校验已编码，完整附件能力仍部分实现；交接/故障附件通道缺失。 |
| R07-1 outbox | HandoverActivation.java:173-184 在 ACTIVATE 内调用 HandoverStore.insertOutbox；HandoverStore.java:约 165-190 使用共享 order_sync_outbox 版本空间并保留 published_seq=NULL。 | R09_C2_B1_3_S39_A2_3_OUTBOX_2026-09-30 记录 run 751 1/0/0/0：REQUEST/APPROVE/PREPARE 不写，ACTIVATE 恰好一条 HANDOVER_ACTIVATED，再激活拒绝且不增长。 | A2.3 已实现并通过服务层端到端验收；outbox worker/真实 HTTP/插入后故障回滚仍是边界。旧政策页第 40 行“无 outbox”属于历史偏差。 |
| A2.2 矩阵 | E2aHandoverConcurrencyIT 实际覆盖并发审批、同键并发重放及 REQUEST/APPROVE 交错；E2aHandoverOutboxIT 覆盖 A2.3 组合。 | S39 报告称 A2.2 16 项断言全部跑通；S38/S39 均记录真实隔离库与全量单测无新增失败。 | 近期报告优先于剩余计划旧表格；A2.2/A2.3 不再列为“零落点”。真实设备、HTTP 全组合和 worker 仍未验。 |

## 文档偏差记录

1. REMAINING_DEVELOPMENT_PLAN.md 的 A2.2 表格仍写“当前全未跑”，与 S38/S39 的近期报告及测试源码不符；本审计采用近期报告和源码。
2. BUSINESS_POLICY_R07.md 第 40 行仍保留“handover 包无 outbox”的旧描述；S39 已记录 A2.3 实现和 run 751 通过；政策页的待实现清单需后续仅做受控状态同步，不能删除历史报告。
3. P08-A V51 与 E2aCorrectionAppendOnlyIT 仍是未提交草稿。803 在第 115 行把 EFFECTIVE→SUBMITTED 当成必拒断言，失败分类为测试断言问题；不能计作 append-only 通过。

## 可执行缺口排序

1. R07-P08-A：修测试只验证已定义终态（REJECTED/WITHDRAW_PENDING/WITHDRAWN）不可重开，先离线自检，再新隔离库跑一次；保持 V51 生产默认关闭边界。
2. P-R07-08 占用回退：需要在现有 bale_commitment 模型上做源码/政策评估；当前 correction 包零处引用，不能直接实现或宣称完成。
3. P-R07-05：需业主确认主管与权限码映射后，才可把角色语义编码成产品策略；当前只记录待决。
4. P-R07-07/P-R07-09：故障换机和真实附件存储均需要新入口/存储/跨模块契约，不能由现有主路径小修替代。
5. 完整组合验收：outbox worker、真实 HTTP、设备半场、真实附件字节和现场身份保持未验。

## 证据与边界

- R07 主管审批：docs/history/R07_SUPERVISOR_SIGNED_HTTP_2026-10-01.md。
- R07 误批撤销：docs/history/R07_SUPERVISOR_WITHDRAWAL_HTTP_2026-10-01.md。
- R07 outbox：docs/history/R09_C2_B1_3_S39_A2_3_OUTBOX_2026-09-30.md。
- V51 失败轮：.local/autonomous-20261001/p08-a-803/，失败分类见 failure-classification.txt；MySQL 已认证关停，证据未删。
- 当前没有产品缺陷结论；P08-A 是测试断言未完成。生产开关、localDeletionAllowed、原库/现网、main、发布边界均未改变。

