# B 组确认：R08 跨订单汇总 / 完整导出中心 —— 到底缺什么

2026-09-28，自主运行 v11 后续。**只确认与列清单，不自行扩大实现。**
方法：读 `docs/parallel/R08/EXPORT_CENTER.md` 的"已实现边界 / 待办"，逐条与**源码 + 契约 + 测试证据**对照。

## 一、结论速览

**文档声称的两块（跨订单汇总、完整导出中心）在源码、正式契约与测试上都已具备**；
`EXPORT_CENTER.md` 第 33–46 行声明的"待主线整合的正式接口"**已经整合进 `contracts/openapi.yaml`**（该文档那一段已过期）。
**没有发现"应实现而未实现"的缺口**；剩下的全部是**明确保留的待办**（政策/规模/现场/运维类）。

## 二、逐项对照

### 2.1 跨订单汇总

| 文档声明的能力（`EXPORT_CENTER.md`） | 实现 | 证据 |
| --- | --- | --- |
| 按当前仓库查本人任务；含单订单文件与跨订单汇总 | ✅ `ExportCenterController`（`/api/v1/exports` 一整套）；`ExportSummary`/`ExportSummaryQuery`/`ExportRangeReader` | `R08CenterPaginationIT` 4/4、`R08SummaryIT` 6/6 绿（attempts 440/445/458 等） |
| 汇总限 `ORIGINAL_CONFIRMED` 首版、支持通用 Excel 与 PDF | ✅ `ExportSnapshotReader` 固定 `Basis.ORIGINAL_CONFIRMED` | `R08SummaryIT.crossOrderExcelAndPdfUseSameOrderedOriginalFacts` |
| 扫码模板仍走单订单 | ✅ `ExportModels.Format.SCAN_XLSX` 仅单订单（中心 `POST` 只接受 XLSX/PDF 的范围请求） | `R08ExportIT` 14/14（含 SCAN_XLSX 单订单） |
| 产品按原代码/名称/型号分组；整数字符串重量；Web 用 BigInt | ✅ 见 `ExportSummary`/Web `useExportSummary.ts` | `R08SummaryIT.shanghaiInclusiveDateRangeUsesOriginalConfirmationAndExactTotals` |
| 北京时间原确认日、含首尾、UTC 半开区间 | ✅ | `R08ClockBoundaryIT` **2/2 绿**（含"库时钟领先 JVM"用例） |
| 最多 31 天 / 100 单 / Excel 5000 包 / PDF 1000 包，超限拒绝不截断 | ✅ 上限检查在 `ExportFacts.head`/`ExportSnapshotReader` | `R08SummaryIT.rejectsOversizedReversedOrWrongScopeRanges`；`R08ExportIT` 的 5000 包限制 |
| 空范围可查询并生成空文件 | ✅ | `R08SummaryIT.emptyRangeReturnsZeroSummaryAndRenderableEmptyFile` |
| 查询=当时结果；申请在一致性事务内重新固定成员 | ✅ | `R08SummaryIT.rangeRequestFreezesMembersAndReplaysOriginalSnapshotAfterNewConfirmation` |

### 2.2 导出中心

| 文档声明的能力 | 实现 | 证据 |
| --- | --- | --- |
| 任务列表每页 20、`created_at DESC,id DESC` + UUID 游标 | ✅ | `R08CenterPaginationIT.timestampTiesPaginateWithoutDuplicatesOrMissingRows` |
| 格式与有效状态筛选在**分页之前**执行 | ✅ | `R08CenterPaginationIT.formatAndEffectiveStatusFiltersApplyBeforePagination` |
| 代次变化优先显示 `INVALIDATED`；过期显示 `EXPIRED` | ✅ | `R08ExportFenceIT.recoveryGenerationBlocksPublicationAndDownload`（本会话已修正为实测语义） |
| 他人游标/重试对外不可见 | ✅ | `R08CenterPaginationIT.foreignCursorAndRetryAreHiddenFromAnotherActor` |
| 失败/已成功/过期可**按原快照重生成**（新 ID/新有效期、复用摘要成员格式模板） | ✅ `ExportRetry` | `R08RetryIT` **7/7 绿**（含并发同键、旧代次/模板不可用拒绝、旧 payload 仍可重试） |
| 未到期在途任务不可重生成 | ✅ | `R08RetryIT.activeTaskCannotRetryAndRequestKeyCannotChangeToAnotherOperation` |
| 创建/重生成事务：`server_state` 共享锁 → 用户行锁 → **锁后重新鉴权** → 才读快照 | ✅ | `R08AuthorizationRaceIT` **9/9 绿**（session/role/warehouse 三档 × retry 两态的真实双连接测试） |
| 丢响应按原键回放（原日期/格式/代次），不变成新申请 | ✅ | `R08SummaryIT.changingRangeWithOriginalKeyRejectsReplay`、`R08RetryIT` 各幂等用例 |
| 旧持久任务 payload 缺新字段按单订单处理（无需迁移） | ✅ | `R08RetryIT.legacySingleOrderPayloadRemainsQueryableAndRetryable` |

### 2.3 与正式契约的关系（**文档已过期，实际已整合**）

| 项 | 文档说法 | 实际 |
| --- | --- | --- |
| 中心接口是否进 OpenAPI | `EXPORT_CENTER.md:33-35`"**尚未进入正式 OpenAPI**" | **已进**：`contracts/openapi.yaml:27-37` 列出 `/orders/{order}/exports`、`/exports/{id}`、`/exports/{id}/file`、**`/exports/summary`**、**`/exports`**、**`/exports/{id}/retry`**；契约文件为 `export-center-paths.yaml` / `export-components.yaml`（另有 `export-paths.yaml` / `export-schemas.yaml`） |
| 任务 View 新字段 | 文档列为"新增" | 已在契约 `export-components.yaml` |

⇒ **`EXPORT_CENTER.md` 第 33–35 行的"待整合"段落应视为已过期的历史描述**（本页即更正记录；未改该文档原文）。

### 2.4 本会话新增的第三来源（文档当时还没有）

TEMP 来源接入已完成：`exports` 包三处（`f9a73c8`），入口受理 + 首版快照 attempt **383**，
**渲染 attempt 390**（XLSX/SCAN_XLSX/PDF 三格式内容回读正确）⇒ 汇总/中心的"来源"维度已含 TEMP。

## 三、缺口清单（**均是文档"明确保留的待办"，非缺失功能**）

| # | 项 | 性质 | 是否需要决策 |
| --- | --- | --- | --- |
| G1 | **TEMP / 更正净结果 / 多设备来源**接入导出 | TEMP **已做**（`f9a73c8`+390）；**更正净结果＝子项 D，等口径**；多设备来源**前置 R07-2/R07-5 不存在** | D 需业主口径；多设备需先做 R07-2/5 |
| G2 | **任务筛选仍扫 JSON 字段**，未加索引/专用元数据表；**生产规模压测未做** | 性能工程 | 需业主定"是否要生产规模验收" |
| G3 | **文件保留 7 天 ≠ 自动删除**：清理、配额、恢复引用、异地备份未接（与 R09 衔接） | 运维（R09 面） | 需 R09 立项 |
| G4 | **Web 浏览器验收缺口**：分页与"有效状态筛选"只有后端证据；"未应用筛选后再跨页组合"未做浏览器验收 | 测试覆盖 | 不需要决策，可补（约 1 轮） |
| G5 | **现场项**：真实 Excel/打印机、异地 HTTPS、公网部署 | 现场/发布 | 需业主授权与现场条件 |
| G6 | `EXPORT_CENTER.md` 第 33-35 行"尚未进 OpenAPI"已过期 | 文档漂移 | 本页已更正；如需改原文另提 |
| **G7** | **`contracts/export-center-paths.yaml:4`（属已发布契约面，`openapi.yaml:34` 引用）写着汇总"不含 TEMP"** —— 但本会话子项 C 之后 TEMP 来源订单已可由该入口导出（attempts 383/390 实测） | **已发布契约与实际行为的描述性偏差** | **需业主授权**：该文件是已发布契约，按硬边界本轮**未改**；建议在下次契约变更时把该句改为"含 TEMP 来源（R06 受控关联 + 确认后的正式单）"，或明确写出 TEMP 的适用条件 |

## 四、B 组标记

- **B1 跨订单汇总 → ✅ 已具备（证据见 2.1）**，无需新开发。
- **B2 完整导出中心 → ✅ 已具备（证据见 2.2）**；"还差什么"＝上表 G1-G6（政策/规模/现场/运维），无功能缺口。
- **B3 多格式×来源 → ✅ 已覆盖**：TEMP×3 格式（attempt 390）、PC/PDA×3 格式（`R08ExportIT` 14/14）。

## 五、未验边界

- 本页为**源码/契约/既有 IT 证据**的对照确认；**未新跑**任何浏览器、现场、生产规模或异地项；
- `EXPORT_CENTER.md` 里"6 项真实 MySQL 双连接测试"我只核到 `R08AuthorizationRaceIT` 9/9（stub 覆盖同类窗口），
  未逐条复现该文档当时的那 6 项编号；
- 未核对中心页面的**全部 Web 交互**（只用 README/测试名与实现入口对照）。
