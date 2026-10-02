# R06 mapping3+ fail-closed 审计（2026-10-02）

## 代码事实

`TemporaryLinkMapping.install` 可以算出下一映射版本，但同一事务还要求原 canonical 订单仍是 `PDA_TEMP` 且状态为 `REVIEW_REQUIRED`，随后把 scope canonical、slot 和来源订单切换到一次人工核对的正式订单。首次 LINK 后这些前置条件已经改变，因此版本字段可表达更高值，却没有现成入口安全地再次 LINK。

`TemporaryConfirmationSource` 只接受未关联的初始映射，或由 `CONTROLLED_LINK` 写入的已关联映射；它不会因为版本字段单独升高而放行。既有 `TemporaryLinkChecks.staleContextAfterLink` 也证明旧上下文在一次关联后会拒绝，且不会追加回执或映射。

## 自主决策

旧目标只给出“mapping3+”范围，没有定义重复关联、旧目标失效、占用迁移、确认计划和权限分离的业务语义。继续增加一个自动重链入口会臆造政策并可能留下旧目标仍可消费的状态，因此本轮保持现有前置守卫和 fail-closed 行为，不修改产品代码，不把字段可表达性写成已完成。

## 仍未验

mapping3+、旧目标失效、跨来源/高轮次续作和现场身份仍未完成。后续需要明确的业务模型、权限和可回滚事实后，才能拆出独立实现与隔离验收；本结论不放宽生产默认开关或任何 Primary 冻结白名单。
