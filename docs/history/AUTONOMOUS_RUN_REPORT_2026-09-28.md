# 自主运行报告（2026-09-28，指令 v11）

用户离开期间的自主开发。**未开任何开关、未发布契约、未改 R05/R06/R07 已验证业务逻辑、未提交任何 `.local`
临时密钥、未改生产配置默认值。** 远端 `origin/codex/development` 与本地一致（0 0），工作树干净；
事实块 `-Check` 通过（当前区 62 行 / 7,902 字符）。

---

## 一、完成项（提交号 + 验证 + 证据等级）

| # | 子项 | 提交 | 验证（attempt / 结果） | 证据等级 |
| --- | --- | --- | --- | --- |
| 1 | R08 子项 C 实现（TEMP 来源接入正式订单导出） | `f9a73c8` | 383 绿（1/0/0/0）＋ 既有导出路径回归 runs 19-22 全绿 | **真 MySQL + 真 HTTP**；渲染彼时未验 |
| 2 | 渲染验证接线（`-CoordinatesOnly` + 渲染器透传 + 新 e2a IT） | `f6ecdd2` | **390 绿**（1/0/0/0） | **真 MySQL + 真服 19434 + 真 HTTP + 渲染子进程** |
| 3 | e2a harness 两处断言级缺陷修复 | `8649295` | **401 绿**（`E2aJobFenceHttpIT` 1/0/0/0） | **真锁竞争取证**（performance_schema） |
| 4 | 非 e2a 回归三批 | （文档 `17ee508`） | attempts **431-463** | **真 MySQL + 真 HTTP/MockMvc** |
| 5 | D1 契约草案补齐 | `143b0f1` | 逐条与实现核对（命令类型/限额/窗口/权限） | 静态核对（草案未发布） |
| 6 | D2 看板瘦身 + v18 条目 | `bce5a26` | `version_facts -Check` 通过 | 文档 |
| 7 | D3 工作树清理评估 | `0742e17` | 34 棵逐棵核对 merged/dirty | 只清点 |
| 8 | C3 两树整合评估 | `587d80a` | 逐文件比对（忽略行尾） | 只评估 |
| 9 | E2 覆盖率工具 + 基础设施自验 | `c37d40b` | 工具首跑：102 类 / 133 套件 | 工具 + 文档 |

### 1) A1 —— TEMP 来源渲染验证（**本轮最高价值**）

- **接线**：e2a harness 新增 `-CoordinatesOnly`（只导出库坐标、`OUTBOUND_MIGRATE=false`，schema 由 IT 自建）
  ＋ `-RendererHome`/`-Browsers` 透传；新 IT `E2aTemporaryExportRenderIT` 用
  `E2aCrossSourceHttpIT.start(...)` 一次满足"同一 attempt 库 + 真服 19434 + 可注入 bean"。
- **证据（attempt 390）**：`XLSX=13965B`、`SCAN_XLSX=4400B`、`PDF=360092B` 三种格式真生成；
  **openpyxl 独立回读**：订单汇总 `FH-WEB-0001` / 来源 `TEMP_PDA` / 修订 1 / 结果版本 1 / 实绩 2.0 包 /
  精确 **601.5kg**；产品汇总 003→300.5kg、571→301.0kg；原始扫码明细 2 行；扫码 Excel 单表 2 行。
  ⇒ **"渲染是导出功能的另一半"已补齐**。
- **边界**：PDF 只验容器签名 + 内嵌 `TEST_SAMPLE`（本机无 pypdf/fitz；Chromium 用 Adobe-UCS 字形编码）。

### 2) A2 —— 重启后新代次接管

- **结论：无需新增验证**——既有 `JobFenceChecks.restartedOldJob` 已覆盖（旧代次作业重启后
  renew/complete/fail/recoverExpired/claim 全部不成立；伪造新代次 DTO 被 DB 原代次挡住；
  `E2aJobFenceHttpIT` 含"重启与新代次正例"）。
- **跑出绿证据 attempt 401**。取证前撞到**两个测试基础设施缺陷（非产品）**并修掉：
  ① provisioning SQL 经 PowerShell 管道**静默截断**（GRANT 丢失、退出码仍 0）⇒ 改 `cmd /c "mysql … < file"`
  并**执行后立即探测**该权限；② 隔离账号无权读 `performance_schema`（锁竞争取证用）⇒ 补只读授权；
  ③ 另统一脚本 CRLF（LF-only 令 PS 5.1 报错行号错位）。

### 3) A3 —— 剩余非 e2a 回归（三批）

| 批 | attempts | 结果 |
| --- | --- | --- |
| A | 431-439 | **9 类 56 项全绿** |
| B | 440-453 | 13 类通过（94 项：88 通过 / 6 红=环境守卫 / 9 skip） |
| C | 454-463 | 7 类 94 项：93 通过 / 1 skip（另 1 类是我误列的夹具 record） |

**去重后新增 19 个非 e2a 类 / 241 项测试（240 通过 / 1 条件 skip / 0 产品失败）。**
两处红均为**环境守卫**（`IdentityLockTemporaryIT` 449、`IdentityLockAuthenticationIT` 463：需
`19321/acrylic_outbound_l2_20260921` + 同名账号）。

### 4) 环境阻塞（已处置，证据未删）

- **D: 磁盘曾 0 GB 可用** ⇒ MySQL `--initialize` 失败，attempts 416-424 全部 `NOT-EXECUTED`。
  处置：删除 501 个 attempt 目录的 **datadir（91.4 GB）**，**保留**每个 attempt 的
  `integration-test.log`、markers、`storage/`（渲染产物）、`private/`（签名材料）、`provision.sql`
  ⇒ 腾出 92.7 GB 后自 431 起恢复。

---

## 二、逐组状态

| 组 | 项 | 状态 |
| --- | --- | --- |
| **A** | A1 渲染验证 | ✅ 完成（390） |
| | A2 重启接管 | ✅ 完成（判定=已覆盖 + 401 绿 + harness 修复） |
| | A3 剩余非 e2a | ✅ 完成 3 批（19 类 241 项；余下约 80 类未跑，见"未完成"） |
| **B** | B1 跨订单汇总 | ⏸ **判定=既有实现已具备**（`ExportSummary*`/`ExportRangeReader`/Web 面板均在树，`R08SummaryIT` 6/6 绿）；**未新增开发** |
| | B2 完整导出中心 | ⏸ 同上（`ExportCenterController`/`ExportRetry`/Web 中心存在，`CenterPagination`/`Retry`/`AuthorizationRace` 全绿）；"还差什么"需业主口径 |
| | B3 多格式×来源 | ⏸ 已覆盖：TEMP×3 格式（A1，390）、PC/PDA×3 格式（`R08ExportIT` 14/14） |
| **C** | C1 NORMAL 双侧登记 | ⏸ **判定=未完成**（`APPLICATION_ADMISSION.md`：HTTP/Session 外层、Worker/文件入口、NORMAL 双侧证据与生产释放均"未接"）⇒ 属大块生产实现，记录转下方待决策 |
| | C2 应用入口接线 | ⛔ 未启动（同上，跨生产代码大改；**建议单独立项**） |
| | C3 两树整合评估 | ✅ 完成（`587d80a`）：业务内容 **52/53 相同**，唯一差异是应丢弃的临时探针 ⇒ **建议暂不退役**（它仍是唯一真服验证树） |
| **D** | D1 契约草案 | ✅ 完成（补 `WITHDRAW_CONFIRM`，6 种命令；限额/窗口/权限逐条核对） |
| | D2 文档一致性 | ✅ 完成（清 5 条冗余"已归档"占位；现 62 行 / 7,902 字符） |
| | D3 工作树清理评估 | ✅ 完成（34 棵：**24 可删 / 6 待取证 / 3 在用**；只清单不删） |
| **E** | E1 自主探索 | ✅ 产出：harness 两缺陷、V40"漂移"实为 CRLF 假警报（**已澄清，无真实漂移**）、覆盖率工具局限 |
| | E2 测试基础设施 | ✅ 完成（`tools/it-coverage.ps1` + 假通过模式记录） |

---

## 三、停下的边界（每个都记录，未硬做）

1. **R09 NORMAL 双侧登记 / 应用入口接线（C1/C2）**——需改动 Recovery 准入链路与 HTTP/Session/Worker/文件入口，
   属跨生产面的大实现；按"不改 R05/R06/R07 已验证代码 + 需授权"停下记录。
2. **IdentityLock 族 2 类（449/463）**——环境守卫要求 `19321` 专用库与同名账号（需外部条件）。
3. **`Ledger*` 2 类（A3 候选）**——ledgerverify 专用环境，未跑（避免浪费 12 个 attempt）。
4. **B1/B2**——源码已在树且测试绿，但"完整导出中心还差什么"需业主口径 ⇒ 未自行扩大实现。
5. **D: 磁盘满**——已处置但属环境硬约束（每次批量跑仍会累积 ~0.5 GB/attempt）。

---

## 四、待决策（需用户）

1. **D（更正净结果）口径**——`ExportFacts:29/:55`、`ExportRangeReader:45`、`CommandBusinessIntegrity:81`
   现固定 `result_version=1`；D 必须回答"更正后导出取哪个结果版本"。
2. **交接权限拆分**——内核门实际接受 `order:handover || order:resume`（实测 374 ⑤）；拆分＝授权面变更。
3. **R07-3 政策项**——撤销期限/申请人可否拒绝、SoD、§3 审批前置来源（契约草案已标"§政策待决"）。
4. **七类生产开关授权 + 契约发布 + 部署**。
5. **B2"完整导出中心"的判定标准**——是否还有业主认可的功能缺口。
6. **是否退役 r06-e2a-migration 树**（C3 建议暂不；若退役需先解决替代验证树 + 证据归档）。

---

## 五、下一片建议

1. **最强接续＝补 A3 剩余约 80 个非 e2a 类**（通道已验证、每批 12-15 类约 40 分钟；先跑"读被 V43-V48
   动过表"的剩余类），成本低、收益确定；
2. 或 **B2 口径确认后**补导出中心缺口；
3. **R09 C2 应用入口接线**建议单独立项（跨生产面，需明确授权与时间盒）；
4. 建议给 harness 补一条：把每次运行的 XML 复制进 attempt 目录，让 `it-coverage.ps1` 能看到直连复跑
   （消除"最新状态只能靠文档叙述"的洞）。

---

## 六、attempt 号使用（本会话）

- 本会话使用 **355-463**（约 95 个 attempt 目录）；明细见各子项记录。
- 红记录全部保留：355/356（401 诊断）、357-361（409 诊断）、365-373、378/379/382、387/388/391/392/395/396/399、
  416-424（磁盘满 NOT-EXECUTED）、449/463（环境守卫）、460（我误列夹具）。
- 关键绿：**363/364**（A3 残余②）、**374**（残余①）、**377**（A2 口径）、**383**（子项 C）、
  **390**（TEMP 渲染）、**401**（job fence）、**431-463**（三批回归）。
- **下次可用：464+**。

## 七、提交号列表（本会话，按序）

```
f9a73c8  feat(r08): 子项 C——TEMP 来源接入正式订单导出
f6ecdd2  test(r08): A1——TEMP 来源渲染验证接通（attempt 390 绿）
8649295  test(tools): 修复 e2a harness 两处断言级缺陷（A2 取证前提）
17ee508  docs(v11): A1/A2/A3 记录
bce5a26  docs(v18): 自主运行 A1/A2/A3 完成 + D2 看板瘦身
0742e17  docs(D3): 工作树清理评估
143b0f1  docs(D1): R07-3 契约草案补齐 WITHDRAW_CONFIRM
587d80a  docs(C3): 两树整合评估
c37d40b  test/tools(E2): 新增 it-coverage.ps1 + 测试基础设施自验
```
（本会话早前另含 `dcaf7ad`（A2 口径修复）、`9e448d7`（R08 构造器）、`8137790`/`0e8985d`/`8de3223` 等，
见看板 v14-v17。）
