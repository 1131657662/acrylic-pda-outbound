# D（更正净结果导出）开工前实测：**比预期大，按边界停下报告**

2026-09-28。业主已拍板口径＝**C（两个都出：原始确认 v1 + 更正后净结果 v>1）**。
本页是**开工前实测**的结果——结论是**不能按"加一个版本参数"来做**，故按"如果发现导出 v>1 需要改更多面 → 停下报告"的约定停在这里。

## 一、决定性发现：v>1 的结果版本**根本没有提交来源**

`V46__correction_effect_and_result_version.sql:28-33`（只放宽、不收紧）：

```sql
ADD CONSTRAINT ck_result_channel CHECK (
    (submission_id IS NOT NULL AND pc_submission_id IS NULL) OR
    (submission_id IS NULL AND pc_submission_id IS NOT NULL) OR
    (submission_id IS NULL AND pc_submission_id IS NULL AND source_correction_id IS NOT NULL));
```

⇒ **v>1 行必然满足第三支**：`submission_id` 与 `pc_submission_id` **都为 NULL**，只有 `source_correction_id`。

而导出侧 `ExportFacts.head`（第 25-40 行）现在是：

```sql
LEFT JOIN order_result_version r ON r.order_id=o.id AND r.result_version=1
...
check(Set.of("WEB","PDA_TEMP").contains(h.source()) && h.status().equals("COMPLETED") && h.receipt()!=null, ...);
check((h.pdaSubmission()==null)!=(h.pcSubmission()==null), "原确认提交来源不唯一");
```

⇒ 只要把版本参数改成 v>1，**第三行检查必然失败** ⇒ `409 EXPORT_SOURCE_UNAVAILABLE`。
**这不是"少传一个参数"，而是"这条路径在读一个 v>1 行上不存在的提交"。**

## 二、因此 v>1 需要一条**新的读取路径**（不是参数化）

现有的三来源读取器全部以"提交"为锚：

| 读取器 | 锚 | v>1 可否用 |
| --- | --- | --- |
| `PdaExportSource` | `Head.pdaSubmission` → 冻结正文（`source.sourceOrderId` 等形状） | ❌ 无提交 |
| `PcExportSource` | `Head.pcSubmission` → PC 回执/条目 | ❌ 无提交 |
| `TemporaryExportSource` | TEMP 受理正文 + **NORMAL_CONFIRM 决定**（`TemporaryExportEvidence:49` 要求
`decision.businessDecision.submissionReceipt == target.receipt`） | ❌ v>1 的 `receipt` 是更正回执，不是原确认回执 |
| （新增，必需）更正来源 | `correction_document` + `correction_ledger` + `order_effective_scan`(该版本) + `order_effective_line`(该版本) | ✅ 只有这一条能表达"更正后净结果" |

`order_effective_scan` 是 V46 新建的**逐包版本快照**（"每个版本覆盖全部原 dispatch（含被归零的行），
不改原 sequence、不造新扫描"）⇒ 新读取器的数据来源是齐的，**但读取器本身要新写**。

## 三、实际受影响面（逐文件，供授权时评估）

| # | 文件/面 | 要改什么 | 风险 |
| --- | --- | --- | --- |
| 1 | `ExportFacts.head` | 由"硬编码 `result_version=1`"改为"取指定版本"；v>1 时**不能**要求提交非空 | 中（默认 v1 路径必须逐字不变） |
| 2 | `ExportFacts.result` / `dispatches` | 现按 `result_version=1` 取 `order_effective_line`；需按指定版本 | 中 |
| 3 | `ExportFacts`/(新增) `CorrectionExportSource` | **新读取器**：从 correction_document/ledger/order_effective_scan 组装 `ConfirmedV1Input.Order` | **高（新代码 + 新校验）** |
| 4 | `ExportSnapshotReader` | 选路增加"basis/版本"维度；`Basis` 固定 `ORIGINAL_CONFIRMED` 需支持更正口径 | 中 |
| 5 | `report-rendering` 的 `ReportSnapshot.Basis` | 新增 `CORRECTED`（现只有 `ORIGINAL_CONFIRMED`/`CURRENT_EFFECTIVE`） | 中（跨模块，`ConfirmedV1Adapter` 现在只适配 v1 形态） |
| 6 | `ExportModels` + `ExportCenterController`/`ExportController` | 请求面新增"取哪个版本"参数（默认 v1） | 低-中（契约面：**需发布契约**或至少草案） |
| 7 | `ExportRangeReader`（跨订单汇总） | 现固定 `r.result_version=1`；"两个都出"下汇总要能按版本汇总或用 `current_result_version` | **中-高（汇总语义要定：按 v1 汇总？按当前版本汇总？）** |
| 8 | `CommandBusinessIntegrity:81` | 现按 `v.result_version=1` 核对；更正后是否仍按 v1 核对需裁定 | 中（涉及 R06 命令回执完整性口径） |
| 9 | 导出契约（`export-center-paths.yaml` / `export-components.yaml`） | 新增 basis/版本参数与错误码 | **需业主授权发布** |
| 10 | 渲染模板 | 更正净结果要不要改口径/页签/标记（现在模板 `r08-general-2` 的"版本有效明细"按 v1 语义写） | 中（涉及 `report-rendering` 与样例） |

## 四、与授权范围的关系（为什么停下）

授权与硬边界原文：

- 授权范围列了 7 条（含"`basis` 字段加 `CORRECTED`"、"导出 API 支持取哪个版本"），但**没有**列
  "新写一个更正来源读取器"与"跨模块改 `ReportSnapshot.Basis`"；
- 硬边界同时写着"**如果发现『导出 v>1 需要改更多面』→ 停下报告**"；
- 第 7 项（汇总语义）与第 8 项（命令完整性口径）**都是需要裁定的语义问题**，不是实现细节；
- 第 9 项触碰**契约发布**（硬边界禁止）。

⇒ 三条叠加：**实际面 > 授权面 + 两个待裁语义 + 触碰契约** ⇒ 停下报告。

## 五、下一步建议（三选一，供业主挑）

**方案 A（最小、最快、不动语义）——"对比导出"而非"净结果导出"**
只做口径 C 里"**两个都出**"的**对比**形态：v1 与 v>1 **各自**沿用"原确认"口径各自出一份，
不做净结果重算（即 v>1 从 `order_effective_scan` 读**该版本的逐包有效事实**，仍然只当"那一版的快照"）。
仍需第 1/2/3/5 项 ⇒ **不是零风险，但不需要裁定汇总语义**。
→ 需授权：新读取器 + `Basis.CORRECTED`（跨模块）。

**方案 B（完整、语义正确、面最大）**
按第三节 1-10 全做，并先裁定：汇总按哪个版本、命令完整性按哪个版本、模板怎么标。
→ 建议**先出一页语义裁定单**（3 个问题），再开工；估时 3-5 轮。

**方案 C（拆成两步）**
第一步只做**单订单**的 v>1 净结果导出（第 1/2/3/5 项，不含汇总），定向 IT 覆盖
"v1 不变 / v>1 新能力 / 对比一致"；第二步再做汇总与命令完整性口径。
→ 估时 2-3 轮，风险集中在第一步，且**不触碰契约发布**（请求参数先按草案，不并入 openapi）。

**我的建议：方案 C**。它守住"默认行为不变"，把语义待裁项（汇总/命令完整性）留到第二步，
且能先交付一个可验证的净结果导出。

## 六、未验边界

- 本页是**开工前实测**（读 V46 约束 + 既有代码路径 + `ReportSnapshot.Basis`），**未写任何导出 v>1 的代码**；
- 未实测"用 `order_effective_scan` 组装一个 v>1 快照"的可行性（未写读取器）；
- 未核对 `correction_document` 的检索面是否足以支撑"按订单+版本"取更正凭据（未写 SQL）；
- 未评估渲染模板对净结果口径的改动量（只标注了它需要动）。

---

## 七、业主批准方案 C 后的实测：**`report-rendering` 隐藏成本已确认，按约定停下报告**（同日）

业主批准方案 C（先做单订单 v>1 净结果导出），并给出停止条件：
"如果发现 report-rendering 改动比预期大（比如要改多个模板/多处枚举消费点）→ 停下报告"。

### 7.1 实测：**确实比预期大**，而且不止"枚举 + 模板"

| 位置 | 实测内容 | 影响 |
| --- | --- | --- |
| `ReportSnapshot.Basis` | 枚举 `{ORIGINAL_CONFIRMED, CURRENT_EFFECTIVE}` | 加 `CORRECTED` 只是第一步 |
| `ConfirmedV1Adapter:11` | **硬门**：`require(basis == ORIGINAL_CONFIRMED, "confirmed-v1 only accepts ORIGINAL_CONFIRMED…")` | 不是分支，是**直接拒绝** |
| `ConfirmedV1Adapter:29` + `:36` | **`require(result.version() == 1)`** 且 `OrderSnapshot(..., 1, ...)` 写死 | **整数硬编码** ⇒ v>1 必然失败 |
| `PdfHtml:22` / `ReportText:6` | 按 basis 选"原确认"文案 | 两处文案分支 |
| `SnapshotValidator:47` | `if (basis == ORIGINAL_CONFIRMED) require(resultVersion==1 && adjustments.isEmpty())` | 非原确认 basis 不校验版本（有空间），但也不校验更正形状 |
| `ScanRecordExcelRenderer:17` | 要求 basis == ORIGINAL_CONFIRMED | 扫码模板与更正口径的关系需定 |
| `AdjustmentValidator` / `Adjustment` | 有调整（adjustments）装配与校验 | 更正净结果是否带"调整凭据"行需定 |

**关键**：`ConfirmedV1Adapter` 是**原确认 v1 专用适配器**（类注释原话：
`Reconciles original confirmations only; never infers a current result or a correction`）。
要渲染更正净结果，**必需新增一个更正适配器**（取实际版本号、无 `submissionId` 校验、有效行来自该版本快照、
并定调整凭据/合计口径）＋其自带测试；
"改 `ConfirmedV1Adapter` 让它接受 v>1"等于**改动 R08 已验证面**（硬边界不允许）。
⇒ 这是 `report-rendering` **模块内的新代码 + 新测试**，不是"枚举加值 + 模板支持"。

### 7.2 另一处阻塞：服务端请求面是**正则硬编码**

`ExportModels.Command:11`：`@Pattern(regexp="ORIGINAL_CONFIRMED") String basis`
⇒ 传 `CORRECTED` 会被 **Bean Validation** 直接拦下。要支持就得改这个**已发布契约对应的请求约束**。

### 7.3 判定：**停下报告**（正是业主给的停止条件）

- 触发条件："report-rendering 改动比预期大 → 停下报告（跨模块，可能需要单独评估）"——**已确认触发**；
- 实际成本＝`report-rendering` **新适配器 + 测试**（跨模块）＋服务端请求面正则（契约面）；
- **本轮未写任何 v>1 导出代码**，未改 `report-rendering`、未改 `ExportModels`。

### 7.4 下一步建议（供业主挑）

**建议 7.4-A（拆三片，最小可验证）**
1. **片 1（服务端只读，不渲染）**：`CorrectionExportSource` + `ExportFacts` 版本化，只产出**内存快照并落库**
   （`ExportFiles.store` 写 JSON）；IT 断言快照 JSON 的版本号/有效行/合计与库内 v>1 事实逐条一致。
   ⇒ **不碰 `report-rendering`、不碰请求面正则**。
2. **片 2（渲染）**：单独实现"更正适配器"（新文件 + 自带测试），此时才动 `report-rendering`。
3. **片 3（请求面）**：`basis` 正则放宽 + 请求参数 + 契约草案；**需业主授权触碰契约**。

**建议 7.4-B**：若希望一次做完，请把"`report-rendering` 新适配器"与"请求面正则"明确写进授权范围，
并同时裁定 §7.5 的 3 个语义。

### 7.5 仍待裁定的语义（不是实现细节）

1. **更正净结果文件是否带"调整凭据"行**（`adjustments`）——现仅在原确认 basis 下要求为空；模板有该页签。
2. **跨订单汇总按哪个版本**（口径 C 已定"两个都出"，但汇总的成员/合计口径未定）。
3. **命令完整性按哪个版本**（`CommandBusinessIntegrity:81` 现按 `result_version=1` 核对）。

