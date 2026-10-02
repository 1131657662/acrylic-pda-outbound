# R07 CYCLE_REOPENED 与历史 REVERSAL 审计（2026-10-02）

## 审计结论

`DISPATCH_CANCELLATION` 的生效路径已经在 `E2aCorrectionChainIT` 的真实隔离 HTTP/数据库链中覆盖：`cycle_reopening` 只写一次，`cycle_after = cycle_before + 1`，该包在新结果快照中仍为 `is_effective=0`，重复重开返回 `CYCLE_REOPENING_EXISTS`；同一订单的 outbox 事件为 `CYCLE_REOPENED`，结果版本关系保存在载荷中。差异案件不会产生该事件。

设计没有定义 `PHYSICAL_RETURN` 是否也应重开周期，也没有实现“重开后 PDA 重新扫码并落到新周期”的端到端路径。现有证据只覆盖取消派发这一已编码触发类型，不能把周期重开写成完整 R07 能力。

V54 为原 `inventory_ledger` 扣减反向增加 `reverses_inventory_entry_id`，为更正账务恢复增加 `restores_entry_id`，并以唯一键、外键和引用形状约束保证逐笔引用。`CorrectionContribution` 将原扣、已有更正和恢复统一建图；引用缺失、来源不在同一 dispatch、重复消费、逆值不相等或无引用的 `REVERSAL` 都返回 `CORRECTION_LEDGER_CONFLICT`。这会保留历史事实并阻止新审批在账务来源不明时继续。

## 为什么不扩展

旧数据若没有可验证的原库存行或更正行引用，无法从净数量/重量反推出唯一来源；自动把它忽略、补成原扣或绑定最近一条更正都会改变账务解释。当前政策没有给出历史无引用 `REVERSAL` 的迁移、人工确认或归属规则，因此继续 fail-closed，不添加回填迁移、不放宽贡献校验、不把静态推理当作数据库验收。

## 证据与边界

- `E2aCorrectionChainIT` 的取消派发段落验证周期行、有效快照、重复重开和 `CYCLE_REOPENED` outbox。
- `CorrectionContributionTest`、`CorrectionContributionExhaustedTest` 验证实际贡献图和原扣已耗尽时的冲突拒绝；真实账务 HTTP/迁移证据见 `R07_ACTUAL_ACCOUNTING_REPAIR_2026-10-02.md`。
- 历史更正 outbox 缺 `status` 的消费兼容已在 `R07_LEGACY_CORRECTION_OUTBOX_COMPAT_2026-10-02.md` 收口；本审计不重复修改同步消费者。
- 未执行原库/现网、生产迁移、设备重扫或删除 `.local` 证据。

## 自主决策

把“能证明的取消派发重开”与“未定义的其他触发/重扫/历史归属”分开记录；对历史无引用账务保持数据库和服务双层拒绝，等待可审计业务规则而不臆造恢复算法。
