# 验证基础设施 R03：FORMAL 来源夹具 —— 收口记录（2026-09-27）

## 结论：**FORMAL 夹具不成立**，需更长独立排期

预算 3 轮，实际为此额外用 1 轮（第 8 轮，经业主批准"一次为限"），共 4 次尝试。**4 条反例一条未跑成**。

## 逐步证据（全部为真实运行）

| attempt | 结果 | 原样错误 |
| --- | --- | --- |
| 146 | **通过** | SQL 造 REGISTERED FORMAL 来源被接受（复用 V34 seed 的已发布订单作宿主；绕开冻结链/回执链/采集序号链） |
| 147 | 失败 | `BusinessException: 交接案件不存在或无权访问` → 发现**第 4 道前置**：FORMAL scope 必须同时设 `canonical_order_id`（= source order） |
| 148 | 失败 | `BadSqlGrammarException`（我补列时漏补实参，4 占位符 / 3 实参） |
| 149 | 失败 | `Check constraint 'ck_submission_scope_source' is violated`（SQL state HY000 / error code 3819）→ **第 5 道前置** |

第 9 轮按约定**不再重试、不再挖该约束的定义**（属独立排期范围）。

## FORMAL 来源的硬前置（5 道，前 3 道为本任务前 5 轮积累，第 4/5 道为本任务实测发现）

1. `submission.assignment_id NOT NULL` → `FOREIGN KEY → order_assignment`（`V9__formal_submissions.sql:8,13`）
2. `CHECK(status NOT IN ('SUCCEEDED','ABORTED') OR receipt_text IS NOT NULL)`（`V9:18`）——`REGISTERED` 不触发，故可绕开冻结/回执链
3. `submission_scope.source_order_id NOT NULL UNIQUE`（`V9:3`）
4. **FORMAL scope 必须设 `canonical_order_id`**（= source order）；TEMP 与 FORMAL 在此分道
5. **`CHECK ck_submission_scope_source` 约束**（attempt 149 命中；**定义未读**，属独立排期事项）

## 4 条反例逐条结果（全部未执行）

| 反例 | 期望错误码 | 实测 |
| --- | --- | --- |
| ① 未停止（`stopState=REQUESTED`） | `HANDOVER_STOP_NOT_PROVEN` | **未执行**（夹具在建来源时即失败） |
| ② 未决输入未归零（`MISSING_RECORDS`） | `HANDOVER_INTEGRITY_INCOMPLETE` | **未执行** |
| ③ 缺审计（`MISSING_AUDIT`） | `HANDOVER_INTEGRITY_INCOMPLETE` | **未执行** |
| ④ 非主管审批（无 `order:handover`） | `PERMISSION_DENIED` | **未执行** |

## 验证层级标注

- **验证层级 = 服务层**（`SecurityContextHolder` + 直接驱动 `HandoverCommandService`）；**HTTP 层拦截未验**。
- **「非主管审批 → PERMISSION_DENIED」在 HTTP 层未验**：服务层的 `HandoverCommand.Identity` 权限门与 HTTP 安全链是两套东西；服务层通过不等于 HTTP 入口会拦截。

## 时间盒账目（10 轮工程任务）

| 项 | 预算 | 实际 |
| --- | --- | --- |
| R01 Robolectric 基线 | 3 轮 | **2 轮**（自验通过） |
| R02 真实 HTTP 构造器 | 4 轮 | **2 轮**（自验通过） |
| R03 FORMAL 夹具 | 3 轮 | **4 轮**（146/147/148/149；第 4 轮经批准） |
| 合计 | 10 轮 | **用 8 轮，剩 2 轮** |

## 未完成项清单

1. FORMAL 夹具**不成立**（未达"能用于跑 R07-1 断言"的最小可用定义）；
2. 4 条反例**一条未跑**；
3. HTTP 层端到端（含非主管审批拦截）**未验**；
4. 第 5 道前置 `ck_submission_scope_source` 的**定义未读**；
5. 由此，**R07-1 的 14 条断言仍然全部未跑**（命令层只有编译 + 全量单测）。

## 建议

FORMAL 来源夹具**正式列为需更长独立排期的子项**（已知 5 道前置 + 一个可直接复用的 R02 构造器 + R03 第 1 轮的可用起点：复用 V34 seed 的已发布订单作宿主）。剩余 2 轮可用于：给 R07 命令做**HTTP 层**验证（R02 构造器已就绪，不依赖 FORMAL 夹具可用 TEMP 来源路径），或把 R03 的失败证据固化为该独立子项的输入。
## 订正（2026-09-27，读到 ck_submission_scope_source 定义后）

本节前文把「FORMAL scope 必须设 `canonical_order_id`」列为**第 4 道硬前置**，**该结论错误**。`V33__temporary_submission_sources.sql:10-12` 的 CHECK 明确规定 FORMAL 分支要求 **`canonical_order_id IS NULL`**（FORMAL 的规范单就是 `source_order_id`）。
- attempt 146 通过＝只设 `source_order_id`，符合分支 1 ✓；
- attempt 147 的 `HANDOVER_CASE_NOT_FOUND`＝`HandoverStore.scopeFacts` 要求 `canonical_order_id IS NOT NULL`，而该列对 FORMAL **必须为空** → **R07-1 的 FORMAL 路径在当前内核下结构性不可达**，这才是 5 轮反复卡住的机制性原因。
- 最小正确修法：`scopeFacts` 用 `IFNULL(c.canonical_order_id, c.source_order_id)`（需业主授权，属 R07-1 已验证面）。
详见 `docs/parallel/R07/R07_3_IMPLEMENTATION_READY_DESIGN.md` §15。