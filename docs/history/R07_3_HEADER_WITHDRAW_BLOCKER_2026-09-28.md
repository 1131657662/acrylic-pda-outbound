# R07-3 抬头撤销空账阻断（2026-09-28）

## 结论与状态

**R07-3 待办，静态确认；未实际执行抬头 WITHDRAW → WITHDRAW_CONFIRM 的HTTP组合。只记录，不修复。**

合法HEADER_CORRECTION按设计没有correction_line/effect/账务行，而WithdrawConfirm统一要求非空账务行；满足前置身份和状态条件后仍会在空账守卫被拒，无法完成撤销确认。这是R07-3写入/撤销面问题，不是R08导出面问题。D已经用户按已验范围接受交付，抬头生效导出通过不代表抬头撤销通过。

## 证据链

核对输入：开发分支ec79b7f（本次文档输入11c20e8，相关生产代码相同）。源码版本事实见[看板生成块](../ORCHESTRATION_STATUS.md)。

1. [设计§26 B3](../parallel/R07/R07_3_IMPLEMENTATION_READY_DESIGN.md)：白名单式标量补丁保存在冻结CREATE命令字节；批准时合并到新header_snapshot，旧版本不变，明确“不产生effect/账务行”。Create拒绝非空抬头lines并跳过插入数值明细。
2. [CorrectionStore.ledgerEntries](../../server/src/main/java/com/acrylic/outbound/correction/CorrectionStore.java) 289–295行从correction_ledger内连接correction_line，按document_id取账。合法零明细抬头得到空集合。
3. [CorrectionOperations.WithdrawConfirm](../../server/src/main/java/com/acrylic/outbound/correction/CorrectionOperations.java) 428–430行无抬头类型分支，entries.isEmpty()直接抛409 CORRECTION_LEDGER_CONFLICT（“该更正文档没有可回退的账务行”）。
4. 同文件Withdraw（380–402行）按EFFECTIVE和原审批人校验后发起撤销，没有排除抬头类型，因此存在进入WITHDRAW_PENDING后无法确认完成的风险。此项是静态推导，不声称已在HTTP/数据库复现待确认状态。
5. 历史495已实际验证抬头批准后的零line/effect、新抬头及数重不变，但未调用撤销。历史493只验证实物撤销，不能覆盖抬头撤销。原始发现及全扫描清单见[抬头验收§7](R08_D_HEADER_DESIGN_AND_FIX_2026-09-28.md)。

## 影响与待授权评估

需要单独授权评估R07-3 WithdrawConfirm设计：抬头撤销如何从不可变补丁/相邻快照恢复原字段，如何避免覆盖后续更正，如何表达新合法结果版本、两方身份、幂等和并发冲突。现实现的新结果复制当前header_snapshot，因此不能把“删掉非空守卫”当作完整修复方案；本记录不自行决定语义或实现。

R08现有撤销凭据只覆盖实物正反账，抬头反向字段凭据与混合链仍未验；若后续R07语义明确，再单独评估消费范围。不得伪造账行、绕过设备/审批门槛或为过测修改默认开关。

## 本次动作与限制

本次只新建本记录、加入欠债待办和索引、同步看板；未修改任何R07或其他业务代码，未新增测试attempt，未运行该HTTP组合。需用户单独授权设计评估/修复；A3回归可继续，R09 C2另行决策。
