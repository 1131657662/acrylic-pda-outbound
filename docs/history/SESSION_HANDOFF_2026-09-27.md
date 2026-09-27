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
  `CORRECTION_COMMAND_NOT_OPEN`**（此条已被步骤 2 收尾取代，见下）。
- **步骤 2 收尾 + 步骤 3 开局**（`71cafc3` + `3c3e297`，attempt **199** 绿）：内核
  `CorrectionCommandService`/`Controller`（`/api/v1/corrections` + PDA 别名 `/api/v1/pda/corrections`）/
  `Configuration`（`@ConditionalOnProperty`，只注册 CREATE）+ **CREATE Operation**（文档与行 +
  **目标值服务端重算**：before 侧由服务端从 `dispatch_record`+`inventory_ledger` 读出，目标等于 before → 409，
  3 天窗口，实物行凭据必填；**不写账、不改结果版本**）。IT 实测 ①关闭 404 ②无效会话 401 ③有效 200
  ④文档 SUBMITTED/2 行 ⑤before 值等于服务端事实 ⑥回放一致 ⑦目标等于 before → 409。
- 实测约束：**无 `order_effective_scan` 表**（§2 假设，属步骤 5）⇒ before 值由 dispatch+ledger 推出；
  **Web 会话 POST 被 403 挡住**（已知 A3 CSRF/会话问题，未硬做）⇒ 按 R06 做法用 PDA 别名 +
  已验证 PDA Bearer 通道。
**下一步**＝R07-3 **已收口（能力默认关闭、未发布）**：D9/D10 两处共享表缺口均裁决为**接受缺口**（D9＝服务自身 COALESCE 兜底；
D10＝经查 `order_current_result` **无生产读方**，权威指针是 `outbound_order.current_result_version`），R05/R06 写入器未动。
剩余全部是**政策项**（撤销期限/拒绝权、SoD、§3 三项审批前置来源、案件驱动条件、重开触发类型、契约形态与事件命名）
与**现场/发布**（全量 IT、Web 路由 A3 CSRF、契约发布、部署）。若继续推进，建议从**政策裁决**或**扩大 IT 回归面**入手，
不要重开已裁决的缺口。

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
4b. **R07-3**：步骤 1 **已收口**（1a'/1c 已产出，1b/2' 因 seed 形状**不可证**并如实记录）；步骤 2 收尾 + 步骤 3 开局**已交付**（attempt 199；开关关闭 404 / 开启且有效会话时 CREATE 落地）。**未验**：APPROVE/WITHDRAW 未实现、文档停在 SUBMITTED；effect/账务/结果版本/周期/对账（步骤 4-8）全未做；实物凭据只做必填、附件限额未实现；3 天窗口时点暂取结果版本 `created_at`；Web 路由未经真 HTTP（A3 CSRF 挡住）；契约登记待开放时发布；
5. **Web CSRF（A3）**：已知问题未解（真浏览器 403 ACCESS_DENIED）；
6. **生产边界**：七类命令开关默认关闭；**TEMP 归档前门已改为默认关闭开关** `outbound.temporary.archive.enabled`（默认 false，`2a3acce`/`a91572e`）；看板未声明能力开放；
7. 已知 flake：`ExportRendererProcessTest.interruptionTerminatesRendererProcess` — **已收口 ✅**（`9480a21`：pid 文件「存在即完成」握手竞态 → 原子发布 + 等非空内容；隔离单类 3/0/0/0、12 路 CPU 负载 3/0/0/0、全量 718/0/0/1；机制 A/B 反证 12998→0）。

## f. 下一项决策待办（交业主）
① **R07-3 下一步（建议）**：步骤 4-5 开局——`correction_effect` 唯一 + `correction_ledger` 追加 + 完整反向唯一（步骤 4）→ `order_result_version` v+1 + 逐包有效快照 + current 指针（步骤 5）；**并补 APPROVE**（否则文档无法离开 SUBMITTED）；② R06 发布准备期项（波次 IT 专用库、七类开关授权、A3 CSRF）；③ R07-1 的 14 条断言（**已冻结为部分验证**：`scopeFacts` 一行修已在 `ef21fb1` 完成、FORMAL 路径 attempt 160-165 已证可达，卡点是**夹具幂等语义**，不是缺授权）。
---

## R07-1 状态更新（2026-09-27，指令 v5，提交 `3d37497`，attempt 283 绿）

- **从「冻结为部分验证」→「部分已验（服务层）」**：14 条断言里 **8 反例中的 ①②③④** 与 **3 条幂等**已实测通过
  （①`HANDOVER_STOP_NOT_PROVEN` ②③`HANDOVER_INTEGRITY_INCOMPLETE` ④`PERMISSION_DENIED`；
  幂等①同键同文回原回执、②同键异文 `IDEMPOTENCY_CONTENT_MISMATCH`、③丢响应按原键恢复）。
- **原卡点性质已澄清**：不是生产语义，而是**夹具构造**——幂等探测对同一 scope 二次 REQUEST，撞
  `UNIQUE uk_handover_scope_open(scope_id, open_flag)`（V42，同一来源只允许一个开案）。修法＝重放原 REQUEST（同键同字节）。
- **仍未验**：⑤–⑧ 反例（需额外夹具）、3 真并发、2 原子性、**HTTP 层**拦截（`E2aHandoverHttpLayerIT` 被 Web 登录/A3 CSRF 挡住）。
- **接续建议**：若继续 R07-1，先做 ⑤–⑧（夹具扩展）或先把 A3 CSRF 专项查清以解 HTTP 层；**不要**再把它当作"冻结不可动"。

---

## A3 CSRF 根因定位（2026-09-27，指令 v6，提交 `0d14ba8`，attempts 284/285）

- **根因（主因）**：`application.yml` 的 `server.servlet.session.cookie.secure: ${OUTBOUND_COOKIE_SECURE:true}`
  ⇒ harness 走**明文 HTTP** 时 Secure Cookie **不回传** ⇒ 每请求新会话 ⇒ CSRF 令牌与所出示会话不匹配 ⇒ **403 ACCESS_DENIED**。
  **生产默认正确、未改**；`tools/e2a-provision-and-run-106.ps1` 启动 IT 前显式置 `false`（测试侧修复）。
- **次因（夹具）**：`E2aHandoverHttpLayerIT` ② 原先发**无令牌匿名 POST** 期望 401，但 CsrfFilter 在认证之前 ⇒ 403；
  改为**先取令牌再 POST** ⇒ 401 ✓（只改探测构造，断言不变）。
- **证据**：该 IT 由 attempt 150 起的 `-1`（登录失败）变为 ①**404** ✓ ②**401** ✓（attempts **284/285**）。
- **残余（非产品缺陷，待专项）**：① R07-1 ③/④ 需真实 scope 夹具 + REQUEST 需 `order:resume`/APPROVE 需 `order:handover` 的权限拆分；
  ② `TemporaryCommandWebHttpChecks` 登录轮换出的 Cookie 未被后续请求采用（该 IT 仍红，半成品已回退）；
  ③ R07-3 `/api/v1/corrections` 的 **Web 路由真 HTTP** 未验（本轮上限内未做）。
- 详见 [A3 根因记录](A3_CSRF_ROOT_CAUSE_2026-09-27.md)。

---

## v8：非 e2a 回归面解锁（2026-09-27，提交 `36d2368`，attempts 293-328）

- **解锁方式**（测试侧，生产默认未动）：一次性 RSA-2048 密钥对生成到 gitignored `.local/it-keys/`（不入 Git、非生产材料）；
  harness `-NonE2a` 导出 `OUTBOUND_DB_URL/USER/PASSWORD`、`OUTBOUND_MIGRATE=true`、`OUTBOUND_AUTH_SIGNING_*` 与
  **`OUTBOUND_STORAGE`**（文件类 IT 的前置）；批量入口新增 `-ExtraConstructorArgs '-NonE2a'` 透传。
- **真回归（已修）**：`SubmissionArchiveIT`（FORMAL 归档确认）8/14 红，`expected 409 but was 500`；
  根因＝`859edce`（本会话接线 TEMP 分派）把单个 `@Transactional confirm(...)` 拆成分派 + `confirmFormal(...)`，
  分派 `this.` 自调用 ⇒ 代理不介入 ⇒ 无事务 ⇒ `IdentityWriteGuard`（MANDATORY）抛错。修法＝同文件既有
  `closedTemporary` 同款 `TransactionTemplate formalConfirmation` 包住 FORMAL 分支（恢复性、未改语义）；
  **attempt 316 复验 14/14 绿**。**FORMAL 归档确认此前无任何 IT 覆盖** ⇒ 正是非 e2a 盲区。
- **覆盖**：非 e2a 共 **33 类**（31 通过 / 2 需专用库：R08 族、身份锁族）；**累计本会话在最新 schema 上跑过 72 个 IT 类**
  （39 e2a + 33 非 e2a）。
- **剩余盲区**：R08（8 类）与身份锁族（约 6 类）需专用库/账号（独立工程，未逐波配置）；其余约 103 个非 e2a 类未跑；
  并发/压力类未抽样。
