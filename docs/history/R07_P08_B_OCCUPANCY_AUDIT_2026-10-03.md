# R07-P08-B 更正撤销占用回退审计（2026-10-03）

## 结论

本项当前不能安全实现为“撤销确认后自动恢复原占用”。现有代码已经把 `DISPATCH_CANCELLATION` 的审批效果定义为：删除 `bale_commitment` 的 `DISPATCHED` 当前占用，并把 `bale.dispatch_cycle` 从 `cycle_before` 推进到 `cycle_after`。`WITHDRAW_CONFIRM` 只反向 `correction_ledger`、重建结果版本和写同步 outbox，没有占用历史或恢复入口。

在不新增业务政策的前提下，强行恢复会产生两个无法选择的语义：

1. 回退 `bale.dispatch_cycle` 并恢复旧周期占用，会违反 V2.3 §5.7 的周期单调推进和“原出库事实保留”约束，也会与已经发出的 `cycle_reopening` 事实冲突。
2. 保留新周期并插入旧订单的 `DISPATCHED` 占用，需要定义原 dispatch 与新周期占用的关系、PDA/PC 来源字段及后续再出库规则；当前 `bale_commitment` 没有 `dispatch_id` 或占用流转历史，不能证明这种映射是政策允许的。

因此本轮不改产品代码来猜测回退策略，不把现有撤销账务通过写结果版本误称为占用回退。该缺口保持未完成，后续需要明确“周期恢复后更正撤销”的业务政策和持久模型后再实现。

## 证据

- `CorrectionApprovalEffects.reopen` 在批准 `DISPATCH_CANCELLATION` 时调用 `CorrectionStore.reopenDispatchOccupancy`，要求原 `DISPATCHED` 占用唯一、订单和周期匹配，然后删除该占用并推进 `bale.dispatch_cycle`。
- `CorrectionWithdrawConfirm.execute` 只锁订单结果、派发记录和账务行；随后生成反向账务、结果版本、有效快照和 `CORRECTION_WITHDRAWN` outbox，没有读取或写入 `bale_commitment`。
- V9/V10/V34/V55 的 `bale_commitment` 是每包一个当前占用投影，虽然支持多种 owner 类型，但没有 dispatch 关联列或 append-only 占用事件。
- V2.3 §5.7 与 `docs/parallel/R07/CORRECTION_RESULT_DESIGN.md` 要求周期受控递增、原 dispatch 保留、可用性独立表达，并写明误批后新周期已建立时不自动开放。

## 未解决项与下一步

- 需要业务确认：撤销确认是否允许恢复占用；若允许，恢复到旧周期还是新周期；如何处理期间已被其他作业占用、PDA/PC 来源和真实退货路径。
- 需要模型确认：是否新增占用流转/回退事实（含原 dispatch、reopening、owner channel、expected cycle 和冲突版本）及对应锁顺序。
- 在政策与模型明确前，真实组合验收只能证明当前行为和缺口，不能把“账务回退通过”写成“占用回退完成”。

## 本轮边界

本审计为只读结论；未连接原库/现网，未修改生产默认开关，未删除 `.local` 证据，未合并 `main`、打标签、发布或部署。
