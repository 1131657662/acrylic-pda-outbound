# R07 HEADER 撤回入口保护（2026-10-02）

## 结论

`HEADER_CORRECTION` 的确认端没有冻结的明细/账务恢复证明，`WITHDRAW_CONFIRM` 已按 `CORRECTION_LEDGER_CONFLICT` 拒绝它。此前 `WITHDRAW` 仍会先把这类更正单写成 `WITHDRAW_PENDING`，形成必然无法完成的状态。提交 `8b203d0` 在撤回发起的数据库更新前复用同一拒绝码，保持事务内状态、结果版本、收据和 outbox 不变。

## 验证

- 编译：`server :testClasses` 通过。
- 离线回归：`server test` 通过，2956 项，0 失败、0 错误、1 个既有 skip。
- 真实隔离 HTTP：`.local/e2a-cross-20260925-847/integration-result.xml` 中 `E2aCorrectionAccountingHttpIT` 为 1 项通过、0 失败、0 错误、0 skip；`accounting/header-withdraw/` 保存 HEADER 创建、审批、拒绝回执。拒绝回执的 code 为 `CORRECTION_LEDGER_CONFLICT`，状态快照相等断言通过；连接与停库身份均为隔离实例。
- 失败证据：run 846 因未配置报表渲染器返回 `EXPORT_UNAVAILABLE`，未计入完成；失败库和日志保留。验证脚本新增 `-CorrectionAccounting -Renderer` 选择，未改变生产配置。

## 自主决策

沿用确认端已有的账务冲突码做入口 fail-closed，避免臆造 HEADER 恢复业务语义；把回归放入已有真实 HTTP 账务链，验证拒绝发生在 `requestWithdrawal` 更新前，并保留导出路径作为组合回归。

## 后续边界

HEADER 撤回的实际表头恢复、历史无引用 REVERSAL 和相关导出政策仍未定义，因此入口继续拒绝；本项只关闭不可完成状态入口。
