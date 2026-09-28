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

## 三、**④ 跨模块口径差异 → 已裁定：选 C（按效果归类）＋加断言防误解释**（业主 2026-09-28 决定）

**本质**：同一件事（整行归零）在两个模块用了不同表达 —— 引擎写
`WEIGHT_CORRECTION + target_count=0`（实测 attempt 476），渲染 `AdjustmentValidator.effect` 要求
`WEIGHT_CORRECTION` 必须 `afterCount==1 && afterWeight>0` ⇒ 两边口径不一致。

**裁定：C ＝ 在组装层按实际效果归类**，且**只对已证明合法的形状做转换**，其余一律拒绝：

| 引擎形状 | 组装层解释 | 断言 |
| --- | --- | --- |
| `WEIGHT_CORRECTION` + `0包0克` | 整行退出净结果 ⇒ `DISPATCH_CANCELLATION` | 必须恰好 `targetCount==0 && targetWeight==0` |
| `WEIGHT_CORRECTION` + `1包>0克` | 只改重量 ⇒ `WEIGHT_CORRECTION` | 必须恰好 `targetCount==1 && targetWeight>0` |
| `WEIGHT_CORRECTION` + 其它（如既改重量又改数量的合法组合） | **拒绝** | `409 CORRECTION_EXPORT_KIND_AMBIGUOUS`（**不得静默解释为取消**） |
| `PHYSICAL_RETURN` / `DISPATCH_CANCELLATION` | 归零类 | 必须 `0包0克`，否则拒绝 |
| `HEADER_CORRECTION` | 无出库行 | 本轮**明确拒绝**（片2 专门处理） |

另加统一前置断言：`before_count==1 && before_weight>0 && target_weight>=0`（形状非法即拒）。

**性质声明**：这是**边界层转换**——**不改变引擎（R07-3）与渲染（R08 已验证面）任一方的语义**，
也**不静默掩盖非法组合**（非法组合报可观测、可回归的错误码）。
**验证**：attempt **478** `E2aCorrectionChainIT` `tests=1 failures=0 errors=0 skipped=0`（加断言后仍绿）。

## 三之二、②③ 判定（已定，本轮只记录不改代码）

- **② 跨订单汇总按哪个版本 → 每个订单的「最新有效版本」**（汇总是"当前状态"：每单取最新 v，v1 或 v>1；
  单订单导出仍可看历史＝"两个都出"）。**本轮不改 `ExportRangeReader`**（改它需一并处理合计口径，随片3/后续）。
- **③ 命令完整性按哪个版本 → 按命令记录时的版本**（不是固定 v1）。改 `CommandBusinessIntegrity:81`
  要动 R05/R06/R07 面 ⇒ **本轮只记录、不改代码，列入「待授权项」**。

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
| ④ | 第三节的引擎/渲染口径差异 | **已裁定为 C 并落地**（见第三节） | 片2 已按此实现 |
| ⑤ | 更正净结果文件是否显示"调整凭据"页签与文案 | 模板 `r08-general-2` 本就含"调整凭据"表 | **片2 已验**：凭据 2 行落进文件 |
| ⑥ | 净结果行非空时的"版本有效明细"呈现 | 本 fixture 两包全归零 ⇒ 该表按设计为空 | 需"部分更正"样本补验 |

## 六、D 片2 交付：更正净结果**真出文件**（提交 `93bbfae`，attempt 486 绿）

**范围**：片2（渲染）。**未改 `ConfirmedV1Adapter`**、未碰请求面正则（片3）、未改 R05/R06/R07、未开开关、未发布契约。

| # | 改动 | 说明 |
| --- | --- | --- |
| 1 | `report-rendering/ReportSnapshot.Basis` **加 `CORRECTED`** | 枚举加值、**不动任何字段**。既有消费点语义正好承接：`PdfHtml`/`ReportText` 对非原确认口径输出"净结果"文案；`ScanRecordExcelRenderer` 仅支持原确认；`SnapshotValidator` 只在原确认口径下要求 `resultVersion==1` ⇒ CORRECTED 放行非 1 版本 |
| 2 | 新增 `exports/CorrectionAdjustmentSource` | **单职责拆分**：`CorrectionExportSource` 原 217 行超项目 200 行上限 ⇒ 凭据与边界层转换独立成类（现 98 + 117 行）。承载决策④转换、**整条更正链**（v2→目标版本：`AdjustmentValidator` 要求调整版本从 2 起连续）、**撤销即归零**、**抬头更正**支持 |
| 3 | `CorrectionExportSource` 口径改 `Basis.CORRECTED` | `dataVersion=result:<v>/revision:<r>`，其余逻辑不变 |

**验证（真服 + 真 MySQL + 真渲染子进程 + 真作业）**

| 项 | 结果 |
| --- | --- |
| 定向 IT | attempt **486** `E2aCorrectionChainIT` `tests=1 failures=0 errors=0 skipped=0`（同一测试覆盖片1 快照 + 片2 渲染） |
| 真渲染 | 经 `BackgroundJobStore.enqueue` + `BackgroundJobWorker.runOnce`（真实租约/handler/产物发布）：**XLSX 13776 B**、**PDF 382320 B**，均发布可下载且字节数一致 |
| openpyxl 独立回读 | 7 张表；订单汇总 `CORRECTED｜结果版本 2｜原 2.0包/601.5kg → 净 -2.0包/-601.5kg → 净结果 0包/0kg`；产品 003/571 各净 0；原始扫码明细保留 2 行；**调整凭据 2 行**（结果版本 2.0，带更正号/行ID/原因/凭据依据）；说明表 `数据版本 result:2/revision:1`、`口径 SHIPMENT_DATE_NET / CORRECTED` |
| 零结果语义 | 断言"版本有效明细"数据行＝0（渲染器 `if (e.count()==1)` 的设计）而调整凭据仍在 ⇒ **零结果仍可追溯** |
| 扫码模板 | 明确拒绝更正净结果（设计如此）；已断言为可观测拒绝、不静默出文件 |
| 既有路径回归 | R08 **run 28** `R08ExportIT` **14/14 绿** |
| 失败留档 | attempts 479–486（渲染器路径错、`ZipInputStream` 数据描述符、作业租约 null、表名索引取不到）全部保留 |

## 六之二、未验边界（累积，片2 后）

- **多版本链 v2→v3**：已实现"取 v2..目标版本整条链"以满足渲染侧连续性要求，**未真跑** v3（需二次更正生效样本）；
- **撤销（WITHDRAW/WITHDRAW_CONFIRM）**：已按"撤销即归零"实现并读入 `status`，**未真跑**；
- **抬头更正（HEADER_CORRECTION）**：已实现映射（字段名/前值/后值 + 撤销回退），**未真跑**（缺该类型样本）；
- **净结果行非空样本**：本 fixture 两包全归零 ⇒ "版本有效明细"按设计为空；**未验**"部分更正"时该表完整呈现；
- **片3 未接**：`ExportModels.Command.basis` 的 `@Pattern("ORIGINAL_CONFIRMED")` 未改 ⇒
  **当前无法经 HTTP 申请更正净结果导出**（片2 只证明"快照能渲染成正确文件"）；
- ② 汇总按最新有效版本、③ 命令完整性按命令记录时版本：**已判定、未实现**（③ 属待授权项）；
- 片1 遗留：`document` 仅用于溯源/序列证明，未写入凭据行（渲染已够，无需补）。
