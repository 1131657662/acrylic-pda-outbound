# R07 FORMAL 夹具：独立子项登记（2026-09-27）

## 1. 为什么单列

R07-1（原机可用正式单交接）的命令层已完成并通过编译与全量单测，但**端到端断言无法在既有夹具上落地**：仓库现有的提交/资格夹具（`SubmissionFixture`、`TemporaryConfirmationFixture`、`PdaTestDriver`）是围绕 **TEMP 与 PDA 自助领取**搭的，FORMAL 来源需要"发布→领取→提交→中止"四条真实链路同时在位，并叠加停止证明与保留占用。

两次尝试（各一轮）与原始证据：

| attempt | 结果 | 原样证据 |
| --- | --- | --- |
| 138 | 守卫星树缺 R07 主代码 + 测试里 `a` 挂错对象 | `错误: 程序包com.acrylic.outbound.handover不存在`；`符号: 变量 a` |
| 139 | 主代码同步、编译通过、IT 运行但 FORMAL 链路 400 | `java.lang.AssertionError: Status expected:<200> but was:<400>`（`SubmissionFixture.submit()/abort()` 内部的 MockMvc 断言），`tests=1 failures=1 errors=0 skipped=0` |

FORMAL 来源的三道硬前置（读实，非猜测）：

- `submission.assignment_id BINARY(16) NOT NULL` + `FOREIGN KEY(assignment_id) REFERENCES order_assignment(id)`（`V9__formal_submissions.sql:8,13`）→ 必须已有真实资格；
- `CHECK(status NOT IN ('SUCCEEDED','ABORTED') OR receipt_text IS NOT NULL)`（`V9:18`）→ `ABORTED` 必须带合法回执正文；
- `submission_scope.source_order_id NOT NULL UNIQUE`（`V9:3`，后经迁移改为可空以容纳 TEMP）→ FORMAL/TEMP 靠该列可空性区分。

## 2. 本次尝试暴露的两个真实缺陷（已修，均为"跑起来/读实定义"才发现）

1. `HandoverStore.scopeFacts` 只认 `c.source_order_id IS NULL`（TEMP 语义）→ **任何 FORMAL 来源发起 REQUEST 都 404**。已改为接受 TEMP（`IS NULL`）或 FORMAL（`= canonical_order_id`）。提交：本地已推。
2. `HandoverOperations.Request` 的原结果维度用的是**不存在的状态值**（`DRAFT/PENDING/REVIEWING`），真实词表是 `REGISTERED/RECEIVING/REVIEW_REQUIRED/REJECTED/ABORTED/SUCCEEDED` → `REGISTERED`（从未冻结，正是设计要的 `NONE_VERIFIED`）会落到 `UNKNOWN` 而永远无法激活。已按真实词表修正。提交 `49e3b0f`。

## 3. 独立子项定义（建议）

- **名称**：R07 FORMAL 来源夹具（可激活来源的构造器）
- **完成标准**：
  1. 能产出两种可激活来源：`ABORTED`（含合法回执正文）与 `REGISTERED`（从未冻结）；
  2. 来源绑定 `source_order_id = canonical_order_id`，并带真实 `order_assignment`、`order_publication`、`inventory_snapshot`、`policy_version`；
  3. 能种入 `handover_evidence(STOP_PROOF)` 与 `bale_commitment` 保留占用；
  4. **不修改既有 TEMP/PDA 夹具与生产代码**；
  5. 夹具自身可独立验证（一个 gated IT：构造 → 断言来源状态与绑定 → 断言占用数与回执存在）。
- **验收**：上述 IT 在隔离非空 MySQL（V34 seed → V42）上 `tests=1 failures=0 errors=0`。
- **为何值得单列**：R07 后续每一批（TEMP 适配 R07-2、更正 R07-3、对账 R07-4、故障换机 R07-5）都需要同一套 FORMAL 来源夹具，越晚做越贵；把它作为"验证基础设施"独立排期，避免继续混在业务子项里反复卡住。
- **明确不含**：R07-1 的 8 条反例、3 条幂等、3 条并发、原子性两模式——它们作为该子项的**下游**，在夹具成立后一次跑完。

## 4. 命令层收口点（已验证部分）

- 提交：`ec58801`（PREPARE/ACTIVATE + R07 专用激活路径 + 控制器 + 契约 0.35.0）、`49e3b0f`（状态词表修复）、以及 FORMAL scope 修复提交。
- 验证：`compileJava` 通过；服务端全量 `test` **718 项 / 0 失败 / 1 既有条件 skip / 0 error**。
- 明确未验：四个命令的**任何端到端/反例/幂等/并发**断言；outbox 接线（复用 `order_sync_outbox`，`entity_version=新 assignment_epoch`，`event_type='HANDOVER_ACTIVATED'`）未落代码；Web 入口未做。

## 5. 排期观察（供决策）

R06 的 TEMP 端到端用了九轮（attempt 115–123），R07 的 FORMAL 比 TEMP 多三条硬前置。两次尝试都在"构造可用来源"这一步受阻。这可能意味着需要在某个时点重新评估：**是否降低端到端验证粒度**（部分子项接受"编译 + 全量单测 + 隔离模型迁移"为主要验证），以及**是否把验证基础设施作为独立、有时间盒的工程任务**。
