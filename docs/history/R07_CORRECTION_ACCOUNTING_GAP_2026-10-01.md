# 更正账务贡献静态缺口（2026-10-01）

输入 `a9e926ad3b9e5d1c5993a39c55d9d5f3a534284a`，主树 `codex/development`。本记录是实现补扣前的源码核对，不是新运行验收。版本事实见[看板](../ORCHESTRATION_STATUS.md)。

## 依据与偏差

[已定更正设计 §4](../parallel/R07/CORRECTION_RESULT_DESIGN.md)要求：原来未扣账且无人工补扣的退货应为“0条反向”；“每条完整反向必须有 reversesEntryId”；人工补扣以实际剩余贡献和当前有效重量执行，退货应逐笔反向实际扣减及调重。

源码 [CorrectionOperations.Approve](../../server/src/main/java/com/acrylic/outbound/correction/CorrectionOperations.java) 第296–305行在 `dispatchId != null` 时无条件调用 `insertCorrectionLedger`，参数是结果数量/重量差额，完整反向的 `reversesEntryId` 为 null。该路径没有先读取 `inventory_ledger` 或已有补扣流水来决定是否有账务贡献、应反向哪些行。[CorrectionStore.insertCorrectionLedger](../../server/src/main/java/com/acrylic/outbound/correction/CorrectionStore.java) 第262行起只执行 INSERT，没有补上该检查。

因此现有实现不能证明“未扣账不凭空反向”“已扣账按实际流水逐笔反向”“补扣后再更正保持贡献一致”。这是**静态确认的产品规则缺口**；本轮未对该缺口构造新的真实数据库反例，不声称已测到线上数据错误。既有重量更正/撤销验收只在其输入范围内保留，不能外推完整账务设计通过。

## 处理

按活动目标“发现真实产品缺陷→报告、记录、跳过子项”处理。本轮不修改旧算法/断言/迁移，不实施会依赖该贡献模型的人工补扣；库存差异审批/补扣闭环继续未完成。新更正查询与原回执恢复独立推进，其组合测试通过也不修复/豁免本缺口。

后续需要独立修复范围：零原扣、实际原扣、正负调重、补扣后退货、重复/并发反向、逐笔引用、结果与账务版本分离、占用/周期完整闭环，并以真实 MySQL/HTTP 及旧回归验证。不得为方便实施把账务当作结果差额另一个副本。
