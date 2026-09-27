# 会话交接（2026-09-27）

## a. 当前主线
**R07-3 更正**（`docs/parallel/R07/R07_3_IMPLEMENTATION_READY_DESIGN.md`）——R06 能推的都推了，剩余属发布准备期任务（分批续跑需波次专用库、七类开关授权、A3 CSRF）。

## b. 当前卡点
**R07-3：步骤 1 已收口 ✅、步骤 2 第一片 ✅（内核尚未接线）**：
- **步骤 1 收口**（`cf14bf5`，attempt **190** 绿）：1a' 改按 V43 命名约束
  `ck_result_version_count`/`ck_result_version_weight` 取值、去反引号+压缩空白比对（原文留证）；
  1b **如实改判**（本 seed `order_result_version=0` 行，原「既有 v1>0」不可证）；**1c 功能级产出**
  （复用 TEMP authorization 建新 submission，插入 `0 包/0g` 行并读回）；2' 不可产出（seed 无 dispatch）
  并补 diff 级证据（V43 对 `inventory_ledger` 只有注释、无 ALTER）。附带收口 §13 补充条件 2 的缺口
  （V43 之后新插入 `>0` 行实测成功）。
- **步骤 2 第一片**（`22e9b81`，attempt **192** 绿）：**V45** 建 `correction_command_receipt`
  （照 V42 形态）+ **同批登记** §5 草案权限码 `outbound:correct`/`correction:approve` 并绑
  `WAREHOUSE_ADMIN`（封堵 R07-1「审批门权限码从未注册」陷阱）；`CorrectionCommand`/`CorrectionStore`/
  `CorrectionReceiptCodec` 三件按 R07-1 形态落地。实测修正：本仓库**无 `stock_state` 表**，故
  `orderFacts` 不读库存版本（留待 effect 步骤）。
- **本片无 service/controller 接线** ⇒ 无路由/无 bean、启动面不变；**打开开关也无 operation bean ⇒ 一律
  `CORRECTION_COMMAND_NOT_OPEN`**。
**下一步**＝同一片交付「步骤 2 收尾 + 步骤 3 开局」：`CorrectionCommandService`(≈110 行) +
`CorrectionCommandController`(≈35 行) + `CorrectionCommandConfiguration`(`@ConditionalOnProperty`) +
首个 Operation（文档与行 + **目标值服务端重算**）。

## c. attempt 号分配（避免混乱）
- **168** 全量 IT 跑批（结论：跑批模式不兼容）；**169** 未用；**170** 归档 IT（装配问题）；**171** Android LINK L4（**未运行**）；
- **172** TEMP 归档定向 IT（红：夹具路径守卫拒绝三位 attempt 号）；**173** **未运行**；**174** TEMP 归档定向 IT（守卫已修，红：夹具过时预期）；**175** 开关关闭态（红：无事务调 `access.pda` → 500）；**176** 开关关闭态验收（**绿**）；**177** 开关关/开两场景 IT（红：自测重放断言过严）；**178** 同 IT（**绿**）。
- **179** 分批评估（红：one-shot 结构性）；**180** 计数修复验证（绿）；**181–184** 分批入口四类（**绿**）；**185/187** Android LINK L4（**绿**）、**186** 红（Gradle UP-TO-DATE 静默空跑）；
- **188** 步骤 1（红：seed 无结果行）、**189**（红：`uq_submission_round`）、**190** 步骤 1 收口（**绿**）、**191** 红（V45 未同步守卫星树）、**192** 步骤 2 第一片（**绿**）；**193+** 可用。

## d. 关键文档路径
- 欠债清单：`docs/history/R06_DEBT_LIST_2026-09-27.md`
- 本交接：`docs/history/SESSION_HANDOFF_2026-09-27.md`
- Android L4 方案：`docs/history/ANDROID_L4_TWO_PROCESS_PLAN_2026-09-27.md`
- R07-3 实现前设计（含 §10-§16 关键发现）：`docs/parallel/R07/R07_3_IMPLEMENTATION_READY_DESIGN.md`
- R02/R01 验证基础设施：`docs/history/VALIDATION_INFRASTRUCTURE_*.md`
- 全局未验证项盘点：`docs/history/UNVERIFIED_INVENTORY_2026-09-27.md`
- 看板：`docs/ORCHESTRATION_STATUS.md`（事实块为版本唯一来源）

## e. 未验边界清单
1. **TEMP 归档**：**已收口 ✅**——attempt 176（关闭态端到端）与 178（关/开两场景）均绿；能力默认关闭、未声明开放；
2. **Android LINK 安装**：**已升 L4 ✅**（`2bf2507` 三件套 + `516dfd7`/`eda91b9` 工具修正）：常驻 IT 真服 19434 + Android `RealServerSourceBindingTest` 真打真服 → `installBindings` → 本地行 == 服务端关联结果 + 幂等；attempt **185/187** 双绿（`server=1/0/0/0`、`android=1/0/0/0`）。未验：多设备/并发/断网重连/仪器化/真机现场；
3. **全量 IT**：**分批入口已封装并小规模验证**（`tools/e2a-batch-it.ps1`：每类一 attempt + 全 XML 聚合 + 失败分类；构造器计数修 `f34c886`）→ runs **181–184** 四类全绿 `4/0/0/0`，失败分类路径（E 类）实测。**覆盖仅限 28 个 `E2aCrossSourceProfile` IT**；波次 IT 仍缺专用库/账号（约束 c）；**未跑全量**；
4. **R07-1**：14 条断言 0 条有结论（1 条触达审批门后被夹具幂等构造阻断）；`order:handover` 权限缺口已由 V44 修复；
4b. **R07-3**：步骤 1 **已收口**（1a'/1c 已产出，1b/2' 因 seed 形状**不可证**并如实记录）；步骤 2 第一片**仅存储与构件、无行为可验**（无路由/无 bean；开关打开也一律 `CORRECTION_COMMAND_NOT_OPEN`）；`CORRECTION_*` 错误码仅定义未触发；§9.2「契约登记」按纪律待能力开放时发布；
5. **Web CSRF（A3）**：已知问题未解（真浏览器 403 ACCESS_DENIED）；
6. **生产边界**：七类命令开关默认关闭；**TEMP 归档前门已改为默认关闭开关** `outbound.temporary.archive.enabled`（默认 false，`2a3acce`/`a91572e`）；看板未声明能力开放；
7. 已知 flake：`ExportRendererProcessTest.interruptionTerminatesRendererProcess` — **已收口 ✅**（`9480a21`：pid 文件「存在即完成」握手竞态 → 原子发布 + 等非空内容；隔离单类 3/0/0/0、12 路 CPU 负载 3/0/0/0、全量 718/0/0/1；机制 A/B 反证 12998→0）。

## f. 下一项决策待办（交业主）
① **R07-3 下一片（建议立即继续）**：步骤 2 收尾 + 步骤 3 开局——`CorrectionCommandService` + `CorrectionCommandController` + `CorrectionCommandConfiguration`（`outbound.corrections.commands.enabled`，默认关闭）+ 首个 `CREATE` Operation（文档与行 + **目标值服务端重算**）；② R06 发布准备期项（波次 IT 专用库、七类开关授权、A3 CSRF）；③ R07-1 的 14 条断言（其 FORMAL 路径需业主授权改 `HandoverStore.scopeFacts` 一行：`IFNULL(canonical, source)`）。