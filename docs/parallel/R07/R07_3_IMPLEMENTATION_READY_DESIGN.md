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
## 12. 步骤 1 验证的第 2 个卡点（结构级，交回决策）

attempt 157：`tests=1 failures=1 errors=0 skipped=0`。
- 已修：IT 的 `order_assignment` 查询把 `BIN_TO_UUID(assignment_id)` 改为 `BIN_TO_UUID(id)`——该表主键是 `id`，**没有 `assignment_id` 列**（`assignment_id` 是 `submission` 的外键列）。修后该查询通过，失败点前移。
- **新卡点（结构级，非语法）**：验证"CHECK 放宽后 0 包/0g 能落库"需要新建一条 scope+submission，而 `INSERT INTO submission_scope(source_order_id,canonical_order_id,...)` 被 **`Check constraint 'ck_submission_scope_source' is violated`** 拒绝——**这正是 R03 attempt 149 记录的第 5 道 FORMAL 前置**，当时已判定"不碰 FORMAL 夹具、留独立排期"。

**结论**：步骤 1 的 4 条断言里，第 3/4 条（数据保留、新表为空）与"迁移可应用"已由 attempt 156/157 证实；第 1 条（0/0 与 >0 双向落库）阻断于 FORMAL 级约束，**不是步骤 1 能独立解决的**。

**两条可选路线（交回决策，本轮未执行）**：
- **A'（推荐，不碰夹具）**：把第 1 条降为**定义级证据**——断言 `order_result_version` 上不再存在 `>0` 的 `actual_count/weight_g` CHECK，且新约束子句为 `>= 0`；同时用 seed 里**既有 v1 行**证明 `>0` 仍可落库。"0 能落库"则等 FORMAL 夹具独立排期后补功能级证据（并如实标注该差异）。
- **B'**：先解 `ck_submission_scope_source`（读其定义、构造满足它的来源），属 FORMAL 夹具范畴，**超出步骤 1 边界**。
## 13. 步骤 1 验收（A' 口径）与证据等级声明

**最终状态：部分完成**（attempt 158：`tests=1 failures=1 errors=0 skipped=0`）。

| # | 断言 | 证据等级 | 结果 |
| --- | --- | --- | --- |
| — | 迁移可应用（V43 无 Flyway 报错，7 张新表 DDL 被接受） | 实测（attempt 156/157） | ✅ |
| 3 | 既有数据保留（orders/ledger/results 计数不变） | 实测（attempt 156/157） | ✅ |
| 4 | 7 张新表存在且为空 | 实测（attempt 156/157） | ✅ |
| 1a | 无残留 `>0` 子句 | **定义级**实测（attempt 158 该断言通过） | ✅ |
| 1a' | 新子句文本为 `>= 0`（×2） | 定义级 | ❌ **未证实**：MySQL 存储的 CHECK 子句文本带反引号/规范化，字面 `LIKE '%actual_count >= 0%'` 不匹配（期望 2、实得 0）。**这是断言写法问题，不是迁移问题**——1a 已证明放宽生效 |
| 1b | `>0` 仍可读 | **读取级** | ⚠️ 未执行到（attempt 158 在 1a' 处中止；attempt 156/157 未含该断言） |
| 1c | **`0 包/0g` 新建能落库** | **功能级** | ❌ **未产出**（阻断于 `ck_submission_scope_source`，FORMAL 范畴） |
| 2 | `inventory_ledger` 原 UNIQUE + 两条 CHECK 未放松 | **定义级**（attempt 156/157 该断言段通过） | ✅ |
| 2' | 追加功能级反例（+1 数量、正重量仍被拒） | 功能级 | ⚠️ 仅当 seed 有 dispatch 时执行；属该段之后，本批次未单独跑到 |

### 三条补充条件（必须随本验收一并引用，不得省略）

1. **A' 是临时降级，不是永久绕过**：「『0 能落库』的功能级证据待 FORMAL 夹具独立排期后补，当前仅**定义级 + 读取级**证据。」**不得写成"已完成"**。
2. **「>0 仍可落库」的证据性质**：为「逻辑证明（`>= 0` 包含 `> 0`）+ 既有数据读取证明」，**不是新插入验证**。既有 v1 行是 V43 之前插入的，**严格来说不能证明 V43 之后新插入 >0 能过**。
3. **R07-3 步骤 4-6 的验收标准追加**：其端到端验证**若涉及「全单退货」场景，必须包含「0 能落库」的实测**，不得继续降级；该时点即使 FORMAL 夹具尚未排期也必须补上。

### 已知待办（步骤 1 收口所需，均为一处小改）
- 1a' 断言改为**反引号/规范化容忍**的匹配（或直接读 `CHECK_CLAUSE` 打印比对）；
- 1b 读取级断言与 2' 功能级反例需在 1a' 之后执行到；
- 1c 功能级证据等 FORMAL 夹具。
## 14. 步骤 1 冻结（A'' 未通过）与技术提醒

**A'' 结果**：未通过。attempt 159 未能产出有效运行（我为修 1a' 做的行级改写把 IT 文件写坏，编译失败；文件已 `git checkout` 恢复到已提交状态，未留下损坏源码）。按 1 轮硬边界，**步骤 1 冻结为「部分完成」**，§13 三条补充条件继续有效。

**技术提醒（务必遵守）**：MySQL 存储 `CHECK_CLAUSE` 时会**规范化并加反引号**（如 `` `actual_count` >= 0 ``），因此 `information_schema.CHECK_CONSTRAINTS` 上的 `LIKE '字面子句'` **不可靠**（本次期望 2、实得 0 即此坑）。后续断言 CHECK 子句应当：
1. 读出 `CHECK_CLAUSE` **原文**，去掉反引号/空白后再比较（并打印原文留证）；或
2. 直接打印原文人工核对。
另：**行级文本改写（按行号切片重写文件）本身就容易损坏源码**——本轮教训；改动应用编辑工具或整文件重写并立即编译。

**给业主的两条根本建议**：
1. **R07-3 时间盒需重新评估**：步骤 1-3 预算 3 轮、实际已用 5+ 轮（含本轮），若步骤 2-3 同量级，10 轮不够。
2. **读一次 `ck_submission_scope_source` 的定义**（`SHOW CREATE TABLE submission_scope`）：该约束已卡住两处（R03 四轮、R07-3 本轮），一条命令可能把"5 道前置拼图"变成"一次看懂"，为 FORMAL 夹具独立排期提供关键输入。
## 15. 【关键发现】ck_submission_scope_source 的定义与它揭穿的三件事

**定义（原文，`V33__temporary_submission_sources.sql:10-12`）**：
```sql
ADD CONSTRAINT ck_submission_scope_source CHECK(
    (source_order_id IS NOT NULL AND creator_device_id IS NULL AND client_order_id IS NULL AND canonical_order_id IS NULL) OR
    (source_order_id IS NULL     AND creator_device_id IS NOT NULL AND client_order_id IS NOT NULL));
```
即**互斥二选一**：
- **FORMAL scope**：`source_order_id NOT NULL` 且 **`canonical_order_id` 必须为 NULL**（creator_device_id/client_order_id 亦为 NULL）；
- **TEMP scope**：`source_order_id` 为 NULL，`creator_device_id + client_order_id` 非 NULL（`canonical_order_id` 由受控关联写入）。

### 揭穿的三件事

1. **R07-1 的 FORMAL 路径在当前定义下结构性不可达（根本原因）**：`HandoverStore.scopeFacts` 要求 `c.canonical_order_id IS NOT NULL`，而 FORMAL scope 的该列**按约束必须为 NULL**（FORMAL 的"规范单"就是 `source_order_id` 本身）。**TEMP 能跑通、FORMAL 永远 404**——这正是 R07 五轮与 R03 反复卡住的机制性原因，而不是"夹具没造对"。
   - **最小正确修法（一行，语义正确）**：`scopeFacts` 改用 `IFNULL(c.canonical_order_id, c.source_order_id)` 作为 canonical（有映射用映射，无映射则 FORMAL 的 source 即 canonical）。**属 R07-1 已验证面，需业主授权后才动。**
2. **我在 R03 收口文档里记的"第 4 道前置（FORMAL 必须设 canonical_order_id）"是错的**：事实相反——FORMAL **必须不设**。R03 attempt 146 之所以通过，正因为它只设了 `source_order_id`（符合分支 1）；attempt 147 之所以 404，是内核要求 canonical 非空，而非数据库拒绝。R03 closure 文档已加订正。
3. **R07-3 步骤 1 的 1c（「0 包/0g 能落库」）无需 FORMAL 级配方**：只要按**分支 1 的合法形状**建 scope（只设 `source_order_id`，`canonical_order_id` 留 NULL），约束即满足，`correction_effect`/`submission` 等依赖即可继续。**我此前在 IT 里设了 canonical，属我写错**（attempt 157 的报错正是这个）。

### 影响评估
- **R03 FORMAL 夹具**：性质从"缺配方"变为"**缺一行内核修正**"。夹具形状已明确（分支 1），无需再解"5 道前置拼图"；真正的门槛是是否授权改 `HandoverStore.scopeFacts` 一行。
- **R07-3 步骤 4-6**：`ck_submission_scope_source` **不再构成阻断**（按分支 1 建 scope 即可）；「0 能落库」的功能级证据**可以在步骤 1/4 内自行补出**，不必等 FORMAL 夹具排期——§13 补充条件 3 的门槛因此可提前满足。
- **R07-1 的 14 条断言**：一旦授权那一行修正，FORMAL 夹具即可成立（复用 TEMP 已验证机制），14 条断言有望真正开跑。
## 16. 【R06 欠债 · TEMP 归档】接线方案被证伪（attempt 170/171 前置发现）

**attempt 170 原样错误**：`UnsatisfiedDependencyException: 'submissionArchiveService' 构造参数 6: No qualifying bean of type 'com.acrylic.outbound.temporary.TemporaryArchiveEvidence'`。

**读代码后的真实装配点**（不是猜）：
- `server/src/test/java/com/acrylic/outbound/inventory/TemporaryArchiveDriver.java:24-26` —— **位于 test 源码**，其 `service(archives, proofs)`：
  ```java
  var evidence = new TemporaryArchiveEvidence(bean(SubmissionFiles.class), bean(SubmissionStore.class),
      bean(JdbcTemplate.class), bean(CommandStore.class), bean(CommandReceiptIntegrity.class));
  return new TemporaryArchiveService(new TemporaryArchiveService.Dependencies(
      bean(SubmissionAccess.class), bean(SubmissionStore.class), archives, evidence, proofs),
      bean(PlatformTransactionManager.class));
  ```
- 即：TEMP 归档的装配**只存在于测试驱动**里（`TemporaryArchiveService` 自身注释也写明 "Explicit internal assembly only. **The production archive controller continues to reject TEMP**"），且 `archives`/`proofs` 是**按调用方传入**的。

**结论（架构级，不是"再修一处"）**：把 `TemporaryArchiveService` 接进**生产**装配，等于**把刻意留在测试域的临时归档装配提升为生产组件**——这是产品边界决定，不是装配细节。两条路线：
- **A（推荐，需明确授权）**：在生产 `@Configuration` 里按上面同一方式建 `@Bean`（依赖 9 个既有 bean + `PlatformTransactionManager`），并明确接受"生产临时归档"这一能力（开关默认关闭）；
- **B（保守）**：撤销接线，保持"生产拒绝 TEMP"，TEMP 归档继续只在测试域验证（本项记为**边界不变**，不再是欠债）。
**本轮采取 B 的即时部分：把会让应用启动失败的生产改动回退**（见提交），路线选择交回业主。

## 17. 步骤 1 收口 + 步骤 2 第一片（2026-09-27 实测，attempt 188–192）

### 17.1 步骤 1：**已收口**（`cf14bf5`，attempt **190** 绿）
`E2aCorrectionSchemaIT` `classes=1 tests=1 failures=0 errors=0 skipped=0`。逐条结果（取代 §13 表）：

| # | 断言 | 证据等级 | 结果 |
| --- | --- | --- | --- |
| — | 迁移可应用（V43，7 张新表） | 实测 | ✅ |
| 3 | 既有数据保留 | 实测 | ✅（注意：本 seed `results=0`、`ledgers=0`、`dispatches=0`、`orders=1`，"保留"对这三项是保留了 0） |
| 4 | 7 张新表存在且为空 | 实测 | ✅ |
| 1a | 无残留 `>0` 子句 | 定义级 | ✅ |
| **1a'** | 新子句为 `>= 0` | 定义级 | ✅ **已产出**：改按 V43 命名约束 `ck_result_version_count`/`ck_result_version_weight` 取值、去反引号+压缩空白后比对；原文留证 `raw=(`actual_count` >= 0)`、`raw=(`weight_g` >= 0)` |
| **1b** | `>0` 仍可读 | — | ⚠️ **改判**：本 seed **无任何 `order_result_version` 行**（实测 0），原「既有 v1 >0」写法不可证；§13 该行的「读取级」前提不成立 |
| **1c** | `0 包/0g` 新建能落库 | **功能级** | ✅ **已产出**：复用 seed 的 TEMP authorization 建新 submission（`ck_submission_authorization` 要求 `assignment_id` 非空 **或** `temporary_authorization_id` 非空；V12 限制一 scope 一 round，故取该 scope 的下一轮），插入 `actual_count=0/weight_g=0` 的行并读回 |
| 2 | `inventory_ledger` 原 UNIQUE + 两条 CHECK 未放松 | 定义级 | ✅ |
| **2'** | 功能级反例（+1 数量、正重量仍被拒） | 功能级 | ⚠️ **不可产出**：本 seed `dispatch_record=0`，无真实 dispatch 可插入；补 diff 级证据：**V43 文本对 `inventory_ledger` 只有注释、无任何 ALTER** |

**附带收口 §13 补充条件 2 的缺口**：本轮实测在 V43 **之后新插入** `>0` 行（result_version 首行）成功 ⇒
「`>= 0` 包含 `> 0`」不再只是逻辑推理 + 旧行读取。

**三次失败的原因（保留为证据）**：attempt 188 红于 1b（seed 无结果行，`queryForMap` 空结果）→ 如实改判 1b；
attempt 189 红于 `submission.uq_submission_round`（V12：一 scope 一 round，新 submission 必须取下一轮）→ 修正轮次取值；
attempt 191 红于 `No migration with a target version 45`（V45 只复制到主树、未同步守卫星树）→ 同步后 192 绿。

### 17.2 步骤 2 第一片：**存储与内核构件已落地**（`22e9b81`，attempt **192** 绿）
- **V45 `correction_command_kernel.sql`**：按 V42 `handover_command_receipt` 形态建
  `correction_command_receipt`（`original_key` 唯一 ⇒ 重试回放原字节；`document_id`/`order_id` 外键；3 条 CHECK），
  并**同批登记 §5 草案权限码** `outbound:correct`、`correction:approve` 且绑 `WAREHOUSE_ADMIN`
  ——直接封堵 §14/R07-1 记录的那类"审批门权限码从未注册"陷阱（V44 事后补的教训）。
- **内核构件**（照 R07-1 `Handover*` 形态，不另造设计）：`CorrectionCommand`（ROUTE_SCOPE/KINDS/Input/Identity/
  Locks/Expected/Operation SPI/Write + §5 草案错误码全集）、`CorrectionStore`（receipt 读取、原键预留、回执写入、
  orderFacts/documentFacts、audits）、`CorrectionReceiptCodec`（签名回执、五能力位恒 false、形状校验）。
- **实测修正一处**：本仓库**没有 `stock_state` 表**（§3 的 `expectedStockVersion/expectedLedgerVersion` 属 effect
  步骤），故 `orderFacts` 不读库存版本，留待步骤 4。
- 本片**不含** service/controller 接线 ⇒ 无路由、无 bean、启动面与默认关闭态不变（符合"默认关闭"纪律）。

### 17.3 步骤 2 剩余（下一片，估量已读实）
照 R07-1 同族文件规模：`CorrectionCommandService`（≈110 行）、`CorrectionCommandController`（≈35 行）、
`CorrectionCommandConfiguration`（`@ConditionalOnProperty(prefix="outbound.corrections.commands",name="enabled")`，
≈40 行）+ 第一个 `Operation`（属步骤 3：文档与行 + 目标值服务端重算）。**在此之前，即使打开开关也没有 operation
bean，命令一律 `CORRECTION_COMMAND_NOT_OPEN`** —— 故建议把「service/controller/configuration + CREATE operation」
作为同一片（步骤 2 收尾 + 步骤 3 开局）交付。
