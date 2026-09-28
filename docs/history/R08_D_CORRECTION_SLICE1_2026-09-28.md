# D 片1 交付记录：更正净结果（v>1）快照可组装（2026-09-28）

**范围**：服务端**只读**——证明能组装出正确的 v>1 快照并落库 JSON。**不做渲染（片2）、不碰请求面（片3）。**
**默认行为不变**：不传版本时仍是 v1（`ORIGINAL_CONFIRMED`）。

## 一、实现（提交 `b74078d`）

| # | 文件 | 改动 |
| --- | --- | --- |
| 1 | `exports/ExportFacts` | 新增 `head(warehouse,order,resultVersion)` 重载（原 `head(w,o)` 委托 v1）；`Head` 记录增 `resultVersion` 并**保留旧构造器**（JSON 形状不变）；新增 `correctionDocumentFor(order,version)` |
| 2 | `exports/CorrectionExportSource`（新） | 从 `correction_line` + 该版本 `order_effective_scan` 组装 `ReportSnapshot`；原始事实行/产品计划**沿用原确认那一版的既有读取器**（与 `ExportSnapshotReader` 同一套 `facts.tempOrigin` 选路） |
| 3 | `exports/ExportSnapshotReader` | 新增 `read(...,long resultVersion)` 重载；**v==1 与既有逻辑逐字相同**（WEB/PDA/TEMP 三路未动），v>1 走更正来源 |

**未碰**：`report-rendering`（片2）、`ExportModels.Command` 的 `@Pattern`（片3）、`ConfirmedV1Adapter`（R08 已验证面）、
R05/R06/R07 业务代码；未开开关、未发布契约。

**为什么片1 不新增 `Basis.CORRECTED`**：`ReportSnapshot.Basis` 是 `report-rendering` 的形状，
加枚举值属跨模块改动（片2）。片1 用既有 `CURRENT_EFFECTIVE`（"按固定版本的有效结果"）+
`dataVersion=result:<v>/revision:<r>` 表达版本，**快照形状零变更**。

## 二、语义判定①：更正净结果**带**"调整凭据"行 ✅

**判定＝带。依据**（R07-3 设计原则"逐笔调整可追溯、净结果可追溯"）：
- 净结果若不由凭据推出，文件里就只剩一个合计数字；事后无法回答"哪一笔被改、谁批的、依据什么、何时发生"，
  与 R07-3 的更正台账设计目的相悖；
- 渲染侧本就为更正预留了凭据位（`OrderSnapshot.adjustments` + `Adjustment` 记录，注释写明是
  "R07 integration proposal: supplied approved result effects"）⇒ 带凭据是**顺着既有形状**，不是新增形状；
- 凭据来源＝该版本更正文档的 `correction_line`（before/target + `business_occurred_at`）＋其
  `correction_ledger` 行数（含 `reverses_entry_id` 反向引用链），只输出**该版本实际写入且写了账**的行；
- 每条凭据带 `correction_document:<id> / correction_line:<id> / ledger_rows:<n>` 溯源串。

## 三、**发现：跨模块口径差异（待裁定，片2 处理）**

**实测证据（attempt 476 探针）**：更正引擎把"整行归零"的更正写成
`type=WEIGHT_CORRECTION, before_count=1, target_count=0, target_weight_g=0`；
而 R08 渲染侧 `AdjustmentValidator.effect` 规定
`WEIGHT_CORRECTION` 必须 `afterCount == 1 && afterWeight > 0`（"重量更正不能改数量"）⇒ **两边口径不一致**。

- 影响：若把引擎的 `WEIGHT_CORRECTION` 原样映射到 `Adjustment.Kind.WEIGHT_CORRECTION`，
  渲染校验会拒绝（attempt 475 实测报"重量更正不能改数量"）。
- 本片处置：`CorrectionExportSource` **按实际效果归类**（`target_count==0 && target_weight==0` ⇒ 退出净结果，
  记为 `DISPATCH_CANCELLATION`/`PHYSICAL_RETURN`），**未改动任何一方的既有规则**。
- **待片2 裁定**：是"引擎的 `WEIGHT_CORRECTION` 允不允许把整行归零"（改 R07-3 引擎＝越界，需业主授权），
  还是"渲染的 `WEIGHT_CORRECTION` 应接受 count=0"（改 R08 已验证面，同样需授权），或"按效果归类"就是最终口径
  （则需在设计与契约里写明"文档类型 ≠ 调整凭据类型"）。

## 四、验证证据

| 项 | 结果 |
| --- | --- |
| 定向 IT | **attempt 477** `E2aCorrectionChainIT` `tests=1 failures=0 errors=0 skipped=0`（真 MySQL + 真服 19434 + 真 HTTP + R06/R07-3 真实更正链） |
| 新增检查 | `CorrectionExportSnapshotChecks`：默认 v1 路径仍产 v1 快照；v2 快照版本号＝库内实际版本；**逐包有效行与 `order_effective_scan` 逐条一致**；合计＝`order_result_version`（0/0）；**2 条调整凭据**与 `correction_line`(有账行者) 数一致且带溯源；落库 JSON 复核 basis/版本/凭据数 |
| 关键输出 | `D-SLICE1 snapshot hash=1dd7262c… version=2 lines=2 adjustments=2 count=0 weightG=0`；JSON 实测落地 4786 字节 |
| 既有路径回归 | R08 **run 27** `R08ExportIT` **14/14 绿** |
| 失败留档 | attempts 465–476（含 469 探针语法错误、474 选路错误 `sourceOrderId`、475 口径不一致）全部保留 |

## 五、待决策清单（②③ 语义，不阻塞片1）

| # | 问题 | 现状 | 影响面 |
| --- | --- | --- | --- |
| ② | **跨订单汇总按哪个版本** | `ExportRangeReader:45` 固定 `r.result_version=1`；口径 C（两个都出）已定但**汇总的成员/合计口径未定** | 决定汇总是"只按原确认"、"只按当前版本"还是"两份都出" |
| ③ | **命令完整性按哪个版本** | `CommandBusinessIntegrity:81` 按 `v.result_version=1` 核对 | 更正后再确认/归档时，完整性核对是否仍按 v1 |
| ④ | 第三节的引擎/渲染口径差异 | 本片按效果归类规避 | 片2 渲染必须给出最终口径 |
| ⑤ | 更正净结果文件是否显示"调整凭据"页签与文案 | 模板 `r08-general-2` 有该页签 | 片2（渲染） |

## 六、未验边界

- 片1 **不渲染**：未验证更正净结果能否真的出 XLSX/PDF（那是片2，且会撞 `ConfirmedV1Adapter` 的硬门）；
- 未验证**多版本链**（v2→v3 连续更正）下 `AdjustmentValidator` 的"版本链完整"约束是否满足（疑似 `lastVersion` 需从 1 起链式）；
- 未验证**撤销（WITHDRAW/WITHDRAW_CONFIRM）之后**的 v>1 快照（`reverses_entry_id` 链与 `WITHDRAW_PENDING` 状态已读入，但未跑该场景）；
- 未验证 **HEADER_CORRECTION** 场景（本片按 dispatch 归类，抬头更正无 dispatch ⇒ 会走"缺少出库行引用"检查，需片2 处理）；
- `document` 仅用于溯源/序列证明，未写入凭据行（片2 若需可按文档渲染）。
