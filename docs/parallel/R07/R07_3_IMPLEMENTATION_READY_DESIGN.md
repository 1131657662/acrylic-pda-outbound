# R07-3 更正：实现前设计（2026-09-27）

> **性质**：实现前设计（implementation-ready），不是重复 D 会话设计。来源：`D:/acrylic-pda-worktrees/r07-design/docs/parallel/R07/`（`CORRECTION_RESULT_DESIGN.md` 全文、`INTERFACE_PROPOSAL.md` 权限与端点、`STAGED_IMPLEMENTATION.md` R07-3 行、`ACCEPTANCE_PLAN.md` R/V/M 场景）＋主树 `docs/BUSINESS_POLICY_R07.md`（已确认政策）。
> **本批不做实现**；粒度=辅助逻辑（文档+契约草案，无编译产出）。契约草案**不发布**，实现时同步。

## 1. 范围与不可动摇的前提

- 只针对**已成功出库**的事实（PDA 与 PC 两条渠道）；未成功作业仍走既有处置，不进入更正。
- **不可改写**：`dispatch_record`、原扫码/PC 录入、冻结正文、成功回执、结果 **v1**。
- 原订单**保持 `COMPLETED`**；净 0 也不回 `READY`、不复用旧审批、不重扫。
- 四类更正：`PHYSICAL_RETURN`（真实退货）、`DISPATCH_CANCELLATION`（错误出库）、`WEIGHT_CORRECTION`（重量更正）、`HEADER_CORRECTION`（非实物更正）。
- 政策已确认（P-R07-05/06/08/09）：审批岗位=**主管**；跨日补录**允许 3 天**；误批=**审批人发起撤销→申请人确认**；附件 **单文件 10MB / 单次 50MB / 单条 ≤5 个**。

## 2. 数据模型（迁移草案：版本号从看板事实块的 Flyway 最高版本之后分配，实现时再定号）

| 表 | 关键列 | 不变量 / 约束 |
| --- | --- | --- |
| `correction_document` | id、warehouse_id、order_id、type、expected_result_version、reason、document_sha256、status、requested_by/at、approved_by/at、source_recovery_epoch、row_version | 提交后正文固定（摘要不变）；批准绑定同摘要+版本；**每单一份凭据原子生效**；`UNIQUE(order_id, document_sha256)`；状态机 `DRAFT→SUBMITTED→APPROVED→EFFECTIVE` / `REJECTED` / `WITHDRAWN` |
| `correction_line` | id、document_id、line_no、dispatch_id（HEADER 行可空）、before_*、target_*、evidence_id/sha256、business_occurred_at | `UNIQUE(document_id, dispatch_id)`（HEADER 除外）；目标值由服务端**相对当前版本重算**；实物行必须有核验凭据 |
| `correction_effect` | id、line_id、order_id、result_version、dispatch_id 可空、count_delta、weight_delta_grams、action、cycle_before/after 可空、business_occurred_at、effective_at | `UNIQUE(line_id)`；**一行只产生一次结果差额**；账务关联不增加 effect 数量 |
| `order_result_version` | order_id、result_version、original_result_version=1、original_submission_id、source_correction_id 可空、revision/header_snapshot、effective_count、effective_weight_g、adjustment_status、created_at | `UNIQUE(order_id,result_version)`；v1 首次成功；v>1 对应**唯一**生效凭据；只读发布；`adjustment_status ∈ {NONE,FULL,PARTIAL}` |
| `order_effective_scan` | order_id、result_version、dispatch_id、original_scan_ref、original_sequence、is_effective、effective_weight_g、latest_correction_line_id | **每版本覆盖全部原 dispatch（含归零行）**；不改原 sequence、不新造扫描；`UNIQUE(order_id,result_version,dispatch_id)` |
| `order_effective_line` | order_id、result_version、product_ref、count、weight_g | 按 effective_scan 聚合；保留净 0 产品与原计划顺序 |
| `inventory_ledger`（**扩展既有表**，不改原行） | + entry_kind、correction_line_id、reconciliation_line_id、reverses_entry_id 可空、business_key | 每个 `business_key` 唯一；**完整反向的 `reverses_entry_id` 唯一**且 delta 完全相反；重量差额行**不占**完整反向引用 |
| `cycle_reopening` | id、original_dispatch_id、correction_line_id、cycle_before/after、evidence、state_version、issued_epoch | 原 dispatch **最多一次**；`after = before + 1`；**可用性独立表达**（重开≠可用） |
| `inventory_reconciliation` | id、warehouse_id、差异行全集、各 expected 版本、证据/结论、审批、command_receipt_id | 处理已知差异**不删除其他阻断**；账务与结果更正分开链接 |

补充约束：重量全部**整数克**（API 传十进制整数字符串），`rawWeight` 单独保留；拒绝溢出/超精度，不用浮点消除差额。

## 3. 锁序（申请 / 审批 / 生效三段）

| 段 | 动作 | 锁与校验 |
| --- | --- | --- |
| 申请 | 冻结文档摘要 | 只读：读 `order_result_version` 当前版本 + 相关 dispatch；**不加写锁、不改账、不放库存** |
| 审批前置 | 主管核对 | `expected_case/…` 同款语义：`expectedResultVersion`、`expectedDocumentSha256`、`expectedLedgerVersion`、`expectedStockVersion/expectedSnapshotId`、`expectedDispatchCycle` |
| **生效（单事务）** | effect + 结果版本 + 账务 + 周期 + 占用 + 状态 outbox | 锁序固定：**`server_state` → `outbound_order` → `order_result_version` → `dispatch_record`（按 dispatch_id 升序）→ `bale`（升序）→ `bale_commitment` → `inventory_ledger`**；锁后**重验全部 expected**，任一变化 → 409，保留原申请 |

冲突语义：审批与新的库存发布、出库、退货、解除隔离竞争时，**任一 expected 变化即拒绝**，绝不"锁后按新值继续"。

## 4. 结果版本与指针

- 公式：`净包数(v) = 原确认包数 + Σ_{i≤v} count_delta`；`净重量(v) = 原确认重量 + Σ_{i≤v} weight_delta`，且**必须等于该版本逐包有效快照合计**（两口径互相校验）。
- 不允许负包数/负净重量/重复归零；全单退货后 `0 包/0g` **合法**（0 包限制只针对首次确认出库）。
- `current_result_version` 指针**只指向同单真实版本**；发布后只读。
- 八个版本标识**互不混用**（设计 §7 表）：`resultVersion`（每次生效更正 +1；审批重试/账务补扣/库存流重放**不增**）、`workRound`、`orderRevision`、`assignmentEpoch`、`dispatchCycle`（**有凭据恢复才 +1**；重量更正/普通隔离解除不增）、`serverRecoveryEpoch`（不透明值，只判等）、`receiptVersion`、`stockStateVersion/ledgerVersion`。

## 5. 接口契约草案（不发布）

| 端点 | 权限 | 说明 |
| --- | --- | --- |
| `POST /api/v1/corrections` | `outbound:correct`（拟） | 建文档（类型 + 逐行目标 + 证据引用 + `expectedResultVersion`）；返回原键回执 |
| `POST /api/v1/corrections/{id}/approve` | `outbound:correct` + 主管岗位 | 锁定生效；同摘要同版本；重复提交返回原结果 |
| `POST /api/v1/corrections/{id}/withdraw` | 原审批人发起 → 申请人确认 | 误批撤销（P-R07-08）；保留原批准与撤销凭据 |
| `GET /api/v1/corrections/{id}`、`GET /api/v1/corrections/{id}/original` | 同族读权限 | 原回执字节、不重序列化（沿用 R06 命令回执形态） |
| `POST /api/v1/inventory/reconciliations…` | `inventory:reconcile`（拟） | 差异案件 `OPEN→EVIDENCE_PENDING→REVIEW_READY→RESOLVED`，冲突 `BLOCKED` |

**错误码**（草案，沿用既有命名风格）：`CORRECTION_CONTEXT_CHANGED`(409)、`CORRECTION_ALREADY_EFFECTIVE`(409)、`CORRECTION_LINE_TARGET_UNCHANGED`(409)、`CORRECTION_EVIDENCE_REQUIRED`(400)、`CORRECTION_WINDOW_EXCEEDED`(409，3 天窗口)、`CORRECTION_LEDGER_CONFLICT`(409)、`CYCLE_REOPENING_EXISTS`(409，原 dispatch 已重开一次)、`ATTACHMENT_LIMIT_EXCEEDED`(400，10MB/50MB/5)、`CORRECTION_PERMISSION_DENIED`(403)。

命令面沿用 R06 已实现并验证的**默认关闭 + 原键幂等 + 签名回执 + 单一审计事件**骨架（见 §6）。

## 6. 与 R05/R06 的复用点

| 复用 | 具体 |
| --- | --- |
| **命令内核模式** | R06 `HandoverCommandService` 已实证的形态：`api_idempotency_request` 原键预留 + 不可变回执 + 同键同文回放 + 同键异文 409 + 单一 `operation_event`；R07-3 直接照此建（**不要再造第三套设计**） |
| 审计/outbox | 复用 `AuditWriter` + 共享 `order_sync_outbox`（`entity_version` 用结果版本或状态版本，`event_type='CORRECTION_EFFECTIVE'`/`CYCLE_REOPENED`） |
| 恢复代次 | `ServerState`（`requireWritable`）+ `sourceRecoveryEpoch` 判等 |
| 权限/身份 | `Access`（Web 会话）与 `PdaAccess`（Bearer）；R06 已验证的 PDA Bearer 通道用于设备侧读取 |
| 编号 | `OrderNumbers`（R07-3 不改编号；再出库走新订单） |
| R08 只读消费 | 结果版本 + 逐包有效快照 + `view=ORIGINAL/EFFECTIVE/ADJUSTMENTS`；R08 现有 `basis=ORIGINAL_CONFIRMED/CURRENT_EFFECTIVE` 映射到事实层，**日期仍固定 SHIPMENT_DATE_NET** |

## 7. 政策落地要点（三条必须编码，不得只写在文档里）

1. **3 天补录窗口（P-R07-06）**：`businessOccurredAt` 与原出库日（`shipmentConfirmedAt` 的 Asia/Shanghai 日）比较，超窗口即 `CORRECTION_WINDOW_EXCEEDED`；**不得静默改写成窗口内时间**。
2. **误批撤销（P-R07-08）**：审批人发起 + 申请人确认；保留原批准与撤销凭据；账务与占用**逐笔回退**、可追溯，不能只改汇总数。
3. **附件限额（P-R07-09）**：单文件 10MB、单次 50MB、单条 ≤5 个，**三者同时生效**；超限拒绝并给明确原因，不截断静默通过。

## 8. 验收要点（映射 ACCEPTANCE_PLAN，实施时逐条实测）

- `v1` 不变；增/减重后退货；原未扣/后补扣；净 0；并发批准/重放；账务多行**不重复计包**；`reversesEntry_id` 唯一且 delta 相反；`correction_effect` 与 ledger **不是同一行数口径**（汇总只求 effects，不 JOIN 放大）。
- 反例：非当前版本、非当前有效 dispatch、超 3 天窗口、非主管审批、附件超限、重复批准、同文档既退又调重（应拒）。

## 9. 下一轮实现清单（供实现排期，按依赖排序）

1. **迁移（版本号实现时按事实块分配）**：§2 的表（`inventory_ledger` 只加列、不动原行；`cycle_reopening` 唯一约束）；
2. **命令内核**：照 R06 `HandoverCommandService` 形态建 `CorrectionCommandService`（默认关闭 `outbound.corrections.commands.enabled`）+ 控制器 + 契约登记；
3. **文档与行**：建文档/提交（摘要冻结）+ 目标值服务端重算；
4. **effect + 账务**：`correction_effect` 唯一 + ledger 追加 + 完整反向 `reverses_entry_id` 唯一性；
5. **结果版本**：`order_result_version` v+1 + `order_effective_scan/line` 全量重算 + current 指针 + `adjustment_status`；
6. **周期重开**：`cycle_reopening` 一次性 + 可用性独立表达 + `CYCLE_REOPENED` 状态 outbox；
7. **审批与撤销**：主管岗位校验 + 误批撤销链（审批人发起/申请人确认）；
8. **对账**：`inventory_reconciliation` 案件状态机 + 补扣唯一业务键；
9. **R08 只读**：结果版本/有效视图接口（日期固定 SHIPMENT_DATE_NET，MOVEMENT_DATE 后置）。

> 验证粒度（沿用本会话确立的约定）：**核心**（effect/账务/结果版本/周期/并发幂等）→ 必须端到端（真实 MySQL + 真实 HTTP）；**辅助**（查询/展示/契约登记）→ 编译 + 全量单测。

## 10. 实现第 1 轮实测发现（**阻塞步骤 1，必须先决策**）

在写迁移前核对既有 schema，发现本设计 §2 的两处假设**不成立**，且既有约束**与更正账务直接冲突**：

| 既有对象（`V4__inventory_snapshots_and_mappings.sql`） | 原文约束 | 冲突 |
| --- | --- | --- |
| `inventory_ledger` | `dispatch_id BINARY(16) NOT NULL **UNIQUE**` | 一个 dispatch 只允许一条流水；更正需要**多条**（原扣 + 重量差额 + 完整反向） |
| `inventory_ledger` | `CHECK(quantity_delta=-1)`、`CHECK(weight_delta_g<0)` | 完整反行为 `+1` / `+weight` |
| `order_result_version` | 已存在；`CHECK(actual_count>0)`、`CHECK(weight_g>0)` | 全单退货后 `0 包/0g` **必须合法** |
| `order_effective_line` | 已存在；PK `(order_id,result_version,delivery_code)`，CHECK ≥0 | ✅ 直接复用（设计里的 `product_ref` 即 `delivery_code`） |

**两条路线（需业主/技术决策，我建议 A）**：

- **A（推荐）不动 V4 已验证面**：保留 `inventory_ledger` 作为"原政策事实"表（`dispatch_id` UNIQUE、原 CHECK 全部不动），**新增 `correction_ledger`**（`correction_line_id`、`reverses_entry_id` 可空且唯一、`entry_kind ∈ {REVERSAL, WEIGHT_DIFF, MANUAL_DEDUCT}`、delta 可正可负、`business_key` 唯一）。"当前实际账务"= 两表并按 `dispatch_id` 串联；结果版本仍可用既有 `order_result_version`（但需放宽两个 CHECK 至 ≥0，**这是唯一需要改既有表的一处**）。
- **B 就地放宽**：ALTER 掉 `inventory_ledger.dispatch_id` 的 UNIQUE 与两条 CHECK，让一张表承担全部流水。改动小，但**改动 R05 已验证的账务不变量**，回归面大（需重跑 R05 相关测试与 IT）。

**若选 A**，步骤 1（迁移）实际内容变为：新增 `correction_document`、`correction_line`、`correction_effect`、`correction_ledger`、`cycle_reopening`、`inventory_reconciliation`、`orders.current_result_version`（列或指针表）+ **放宽 `order_result_version` 两条 CHECK 至 ≥0**，并说明为何不动 `inventory_ledger`。

**纪律遵守**：本轮**未写任何迁移代码**（避免按错误假设建表）；步骤 1 待路线确定后开工。
## 11. 步骤 1 进展与脆弱模式提醒（2026-09-27）

**进展（attempt 156）**：四处 JOIN 修复后 **V43 迁移本身已成功应用**（不再出现 Flyway 错误）；失败点前移到验证 IT 自己的断言查询（`order_assignment` 查询报 bad SQL grammar）。attempt 154 的原错误 `Unknown column 'TABLE_NAME'`（`information_schema.CHECK_CONSTRAINTS` 无该列）已解决。

**脆弱模式提醒（务必记录）**：
1. V43 用 `information_schema` **动态查约束名**来放宽 CHECK（按 `CHECK_CLAUSE LIKE` 匹配内容，比按名字稳）——R06 的 V41 也用过同类手法；**若将来在别的 MySQL 实例跑不通，优先检查约束名/子句匹配**（大小写、版本差异、`CHECK_CLAUSE` 文本格式）。
2. **Flyway SQL 文件里用 `@变量 + PREPARE/EXECUTE` 动态 SQL 是脆弱模式**：无法被静态检查、报错定位差、跨版本行为不稳。
3. **步骤 2 之后评估**：是否改为**显式命名约束**（建表时即 `CONSTRAINT ck_xxx CHECK(...)`，放宽时按名字 `DROP CHECK ck_xxx`），从而彻底去掉动态 SQL。当前 V4 的既有约束是内联未命名，故本步骤只能动态匹配。