# 会话交接（2026-09-28）— DSH → Codex

**当前 HEAD**：`f51fc85`（分支 `codex/development`；本轮收尾前的交付 HEAD 为 `184bfd7`，其后仅追加收尾文档提交 `ec1dd14`/`3dc628d`/`f51fc85`）
**远端同步**：`git rev-list --left-right --count origin/codex/development...HEAD` ＝ **0 0**（已全部推送）
**工作树**：干净（`git status --porcelain` 无输出；`.local/` 由 `.gitignore:5` 忽略，无被跟踪文件）
**事实块**：`tools/version_facts.ps1 -Check` 通过（看板当前区见事实块与脚本输出）

---

## a. 本轮（DSH）完成了什么

| # | 事项 | 提交 | 验证（attempt / 结果） |
| --- | --- | --- | --- |
| 1 | R08 子项 C：TEMP 来源接入正式订单导出 | `f9a73c8` | 383 绿；既有导出路径回归 runs 19-22 全绿 |
| 2 | A1：TEMP 来源**渲染验证**接通（新 IT + `-CoordinatesOnly` + 渲染器透传） | `f6ecdd2` | **390 绿**；XLSX 13965B / SCAN_XLSX 4400B / PDF 360092B；openpyxl 回读 601.5kg、2.0 包、产品 003→300.5 + 571→301.0 |
| 3 | A2：重启后新代次接管（判定＝既有 `JobFenceChecks` 已覆盖） | 判定记录 + `8649295` | **401 绿**；同时修 e2a harness 两处断言级缺陷（provisioning SQL 静默截断、`performance_schema` 无权） |
| 4 | A3：非 e2a 回归三批 | 文档 `17ee508` | attempts **431-463**；去重 **19 个新类 / 241 项**（240 通过 / 1 条件 skip / **0 产品失败**） |
| 5 | 磁盘阻塞处置 + **例行清理策略** | `112c345` | 删 501 个 attempt 的 datadir（91.4 GB）解除 D: 0GB；新增 `tools/cleanup-old-attempt-datadirs.ps1` 并接入两个 harness 收尾 |
| 6 | B 组确认（跨订单汇总 / 完整导出中心） | `019e3fd` | **已具备**，无功能缺口；`Summary 6/6 / CenterPagination 4/4 / Retry 7/7 / AuthorizationRace 9/9 / ClockBoundary 2/2` 全绿 |
| 7 | G7：汇总契约描述修正（**只改描述**） | `4b8ab5b` | 明确"含经 R06 受控关联确认正式化的订单；未关联 TEMP_PDA 临时单不在汇总内"；版本不单独递增（0.38.0 留下次实质变更） |
| 8 | D 开工前实测（面超授权 → 停下报告） | `aa4c8ed` | 发现 `V46 ck_result_channel` 第三支使 v>1 行**无提交来源** |
| 9 | ④ 口径差异裁定落地（**边界层转换** + 断言） | `1f422c2` | **478 绿**；非法组合报 `409 CORRECTION_EXPORT_KIND_AMBIGUOUS` |
| 10 | **D 片1**：更正净结果快照可组装（服务端**只读**、形状零变更） | `b74078d` | **477 绿**；逐包有效行/合计/凭据逐条对照库内 v2 事实；R08 run 27 `R08ExportIT` **14/14** |
| 11 | **D 片2**：更正净结果**真出文件**（XLSX/PDF） | `93bbfae` | **486 绿**；真服+真 MySQL+**真渲染子进程**+**真作业租约**；XLSX 13776B / PDF 382320B；openpyxl 回读 7 表；R08 run 28 **14/14** |
| 12 | C3 两树整合评估 | `587d80a` | 业务内容 52/53 相同，唯一差异是应丢弃的临时探针 ⇒ **建议暂不退役** |
| 13 | D1/D2/D3 | `143b0f1` / `bce5a26` / `0742e17` | R07-3 契约草案补齐（6 种命令）；看板瘦身；34 棵工作树清点（24 可删 / 6 待取证 / 3 在用） |
| 14 | E2：测试基础设施自验 + 覆盖率工具 | `c37d40b` | `tools/it-coverage.ps1`（102 类 / 133 套件）；记录两类"假通过"模式 |

**关键结论**：D 片1/片2 把"更正净结果"从**无法导出**推进到**能组装正确快照 + 能真出正确文件**，
且**默认 v1 行为逐字未变**（两轮 `R08ExportIT` 14/14）。

## b. 未完成 / 待决策（交业主）

| # | 事项 | 状态 | 备注 |
| --- | --- | --- | --- |
| 1 | **D 片3（请求面）** | **待决策** | `ExportModels.Command.basis` 是 `@Pattern("ORIGINAL_CONFIRMED")` 硬编码 ⇒ **当前无法经 HTTP 申请更正净结果导出**；含契约草案 ⇒ 需授权 |
| 2 | **③ 命令完整性按哪个版本** | **待授权** | 已判定＝"按命令记录时的版本"（非固定 v1）；改 `CommandBusinessIntegrity:81` 要动 R05/R06/R07 面 |
| 3 | **② 跨订单汇总按哪个版本** | **已判定、未实现** | 判定＝"每个订单的最新有效版本"；`ExportRangeReader:45` 仍固定 `result_version=1` |
| 4 | **D 未验边界** | 见下 §e | v2→v3 多版本链、撤销后快照、抬头更正真跑、**部分更正样本**（净结果行非空） |
| 5 | **R09 C1/C2（NORMAL 双侧登记 / 应用入口接线）** | 未开工 | C1 判定＝**未完成**（`APPLICATION_ADMISSION.md`：HTTP/Session 外层、Worker/文件入口、NORMAL 双侧证据与生产释放均"未接"）；**建议单独立项**（跨生产面） |
| 6 | 退役 `r06-e2a-migration` 树 | 未决定 | C3 建议**暂不**（它是唯一真服验证树；退役需先有替代树 + 证据归档） |
| 7 | 三棵待取证工作树 | 未处理 | `r06-e2a-protocol` / `r06-e2a-foundation` / `r06-design` / `r07-design` / `datetime-display`（见 D3 评估） |

## c. attempt 号

- **本轮用至 486；下次从 487 起。**
- 关键绿：**390**（A1 渲染）、**401**（job fence）、**431-463**（非 e2a 三批）、**477**（D 片1）、**478**（④）、**486**（D 片2）。
- 失败留档（不删除）：attempts 465-486 全部保留（含 469 探针语法错、474 选路错、479 渲染器路径错、481 zip 读法、483 租约 null、485 表名索引）。
- R08 专用库运行号：**19-28**（`R08ExportIT` 最近两次为 **27 / 28，均 14/14**）。

## d. 关键文档路径

| 文档 | 用途 |
| --- | --- |
| `AGENTS.md` | 长期协作约定（必读） |
| `docs/ORCHESTRATION_STATUS.md` | 看板（事实块 + 当前区，含 v23） |
| `docs/history/R08_D_CORRECTION_SLICE1_2026-09-28.md` | **D 片1/片2 记录**（实现、④裁定、证据、未验边界） |
| `docs/history/D_CORRECTED_EXPORT_SCOPE_2026-09-28.md` | D 开工前实测 + 三片拆法 |
| `docs/history/R08_B_GROUP_CONFIRMATION_2026-09-28.md` | B 组确认 + **G7 判定**（§6） |
| `docs/history/R06_DEBT_LIST_2026-09-27.md` | 欠债清单（含 v19/v20 增补） |
| `docs/history/DISK_MAINTENANCE_POLICY_2026-09-28.md` | 磁盘例行维护策略 |
| `docs/history/TEST_INFRA_SELFCHECK_2026-09-28.md` | 测试基础设施自验 + 假通过模式 |
| `docs/history/TWO_TREE_INTEGRATION_ASSESSMENT_2026-09-28.md` | C3 两树评估 |
| `docs/history/WORKTREE_CLEANUP_ASSESSMENT_2026-09-28.md` | D3 工作树清点 |
| `docs/parallel/R08/EXPORT_CENTER.md` | R08 导出中心已实现边界（**注意**：§"待主线整合"段已过期，中心已进正式契约） |

## e. 未验边界清单（累积，不得被局部通过覆盖）

1. **D：v2→v3 多版本链** —— 已实现"取 v2..目标版本整条链"以满足渲染侧连续性要求，但**未真跑** v3（需二次更正生效的样本）；
2. **D：撤销（WITHDRAW / WITHDRAW_CONFIRM）** —— 已实现"撤销即归零"并读入 `status`，**未真跑**；
3. **D：抬头更正（HEADER_CORRECTION）** —— 已实现映射（字段名/前值/后值 + 撤销回退），**未真跑**（缺该类型样本）；
4. **D：部分更正样本** —— 本 fixture 两包**全部归零** ⇒ "版本有效明细"按渲染器设计为空；**未验**"部分更正、净结果行非空"时该表完整呈现；
5. **D 片3 未接** —— 无法经 HTTP 申请更正净结果导出（片2 只证明"快照能渲染成正确文件"）；
6. **PDF 内容级回读** —— 只验容器签名 + 字节数（本机无 pypdf/fitz；Chromium 用 Adobe-UCS 字形编码）；
7. **A3 剩余约 80 个非 e2a 类未跑** —— 通道已验证，可继续分批（每批 12-15 类约 40 分钟）；
8. **环境守卫族**需专用库才会真跑：R08 族（23308/`acrylic_r08_it`）、IdentityLock 族（19321/`acrylic_outbound_l2_20260921` 且账号同名）、ledgerverify 族；
9. **B 组缺口 G1-G7** —— 政策/生产规模/R09 运维/现场类，**非缺失功能**（G7 已修描述）。

## f. 验证基础设施资产（Codex 可直接复用）

| 资产 | 用途 | 关键约定 |
| --- | --- | --- |
| `tools/e2a-provision-and-run-106.ps1` | e2a 单类真服 IT（一 attempt 一库，端口 19330） | `-Run <n> -Tests '<Class>'`；新开关：`-NonE2a`、**`-CoordinatesOnly`**（只导出库坐标、`OUTBOUND_MIGRATE=false`）、`-RendererHome`、`-Browsers`、`-KeepServerRunning`、`-ProvisionOnly` |
| `tools/r08-provision-and-run.ps1` | R08 专用库（端口 23308、库名**必须** `acrylic_r08_it`） | 渲染默认自动指向 `report-rendering/build/install/acrylic-report-rendering` 与 r08-export 工作树的 playwright browsers |
| `tools/e2a-batch-it.ps1` | 批量（每类一 attempt + XML 聚合 + 分类） | **`-Classes 'A','B'` 必须逗号分隔**（PS 数组会崩） |
| `tools/cleanup-old-attempt-datadirs.ps1` | 磁盘例行维护：只删旧 attempt 的 `data/`，保留最近 50 | 日志/marker/`storage/`/`private/`/`run-password.txt`/`provision.sql` **一律保留**；两个 harness 收尾自动调用 |
| `tools/it-coverage.ps1` | 从 JUnit XML 汇总"哪些 IT 跑过、最新状态" | 局限：e2a harness **不按 attempt 归档 XML** ⇒ 直连复跑不在视野内；`LATEST-RED` 应读作"需重新确认" |
| `tools/version_facts.ps1` | 事实单一来源 | `-Check` 校验漂移与看板上限；`-Write` 更新 |
| 渲染前提（导出/渲染类 IT 必须） | `OUTBOUND_EXPORTS_RENDERER_HOME` + `OUTBOUND_EXPORTS_BROWSERS`（否则 503 `EXPORT_UNAVAILABLE`） | 渲染器安装目录必须**含最新 `report-rendering` 构建**（D 片2 的 `Basis.CORRECTED` 就需要 `.\gradlew.bat -p report-rendering installDist` 重建） |

## g. 硬边界（Codex 必须守）

1. **不改 R05/R06/R07 已验证业务代码**（含 `CommandBusinessIntegrity` —— ③ 只能等授权）；
2. **不开放七类生产开关**、不发布/部署、不动原库与现网；
3. **不发布契约**（`contracts/*.yaml` 的字段/版本变更需业主授权；G7 那次是**已授权的描述性更正**）；
4. **不提交 `.local` 临时密钥/数据库/真实业务数据**（`.local/` 已 gitignore）；
5. **不改生产配置默认值**；默认行为必须不变（D 的"取 v>1"是**新增能力**）；
6. **发现真 bug 立即停下报告**，不"顺手修"越过授权面；
7. **失败证据不删除**（attempts、日志、marker、storage 一律留档）；
8. 普通有权正常确认允许空理由；`localDeletionAllowed=false`。

## h. 切换到 Codex

**DSH 本轮交付**（§a 全表）：R08 子项 C、A1 渲染验证、A2 判定+harness 修复、A3 三批回归（19 类 241 项）、
B 组确认、G7 描述修正、D 片1、D 片2、④ 边界层转换、磁盘例行策略、C3/D1/D2/D3/E2。

**待 Codex 接续**（按建议顺序）：
1. **D 片3（请求面 + 契约草案）** —— 需业主授权；`basis` 正则放宽 + 请求参数 + 契约草案；
   建议**先确认片2 渲染路径已验**（已完成，attempt 486），再做片3；
2. **③ 命令完整性按命令记录时版本** —— 需授权（动 R05/R06/R07 面）；
3. **D 未验边界** §e 1-4：v2→v3 链、撤销、抬头更正、部分更正样本；
4. **A3 剩余约 80 个非 e2a 类** —— 通道已验证、成本低、收益确定；
5. **R09 C1/C2** —— 建议**单独立项**（跨生产面，需明确授权与时间盒）；
6. **② 汇总按最新有效版本** —— 已判定、待实现（`ExportRangeReader`）。

**Codex 应先读**（顺序）：
`AGENTS.md` → `docs/ORCHESTRATION_STATUS.md`（事实块 + 当前区）→ **本文件** →
`docs/history/R08_D_CORRECTION_SLICE1_2026-09-28.md`（D 片1/片2 全部细节与未验边界）→
`docs/history/R06_DEBT_LIST_2026-09-27.md`（欠债与待授权项）→
`docs/history/D_CORRECTED_EXPORT_SCOPE_2026-09-28.md`（三片拆法）。
接手前先核对：实际分支、HEAD、远端、全部未提交/未跟踪文件、有无活动写者与监听端口。

**Codex 复用的验证基础设施**：见 §f。**注意两个已踩过的坑**：
① 渲染器安装目录必须重建以包含最新 `report-rendering`（否则新 `Basis` 值反序列化失败）；
② R08 库名/端口与 IdentityLock 库坐标是**逐字比对**的守卫，不接受后缀或缺少查询参数（见 `TEST_INFRA_SELFCHECK`）。
