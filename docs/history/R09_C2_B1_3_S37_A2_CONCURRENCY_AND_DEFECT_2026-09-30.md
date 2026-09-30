# R09 C2 B1-3 S37：A2.2 第二批——并发断言落成（M10–M12）＋ **R07-1 激活路径产品缺陷（`disabled_at`）**（2026-09-30）

> 轮次身份：主树 `D:/acrylic-pda-outbound`、分支 `codex/development`；写者＝DSH 本会话。
>
> 口径：本批**只新增测试**（`server/src/test`），**未改产品代码**、未开开关、未部署。验证层级＝**服务层＋真实线程**，**不是** HTTP 端到端、不涉及设备半场/真机。

## 1. 本批交付

| 文件 | 改动 |
| --- | --- |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aHandoverConcurrencyIT.java` | **新增**：M10（双 APPROVE 同版本）、M11（REQUEST 与 APPROVE 交错）、M12（同键并发重放）三条并发断言，用**真实线程 + `CountDownLatch`** 对真实服务并发调用 |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aFormalRegisteredOperationsIT.java` | 追加 **M7** 片段（激活的恢复代次守卫）——**因发现产品缺陷而未能运行**，见 §3 |
| 证据（不入库） | 通过轮 `.local/e2a-cross-20260925-742/`（`TEST-*.xml` 1/0/0/0/13.6s、`concurrency.txt`）；失败轮 `738`／`739`／`740`／`741` 与缺陷轮 `737` 全部保留 |

## 2. 落成的并发断言（实测原始输出）

`.local/e2a-cross-20260925-742/concurrency.txt` 原文：

```text
verification level=service layer, real threads; HTTP layer not verified
M12 同键并发重放 -> [OK, OK]
M10 双 APPROVE 同版本 -> [HANDOVER_CONTEXT_CHANGED, OK]
M11 REQUEST 与 APPROVE 交错 -> [HANDOVER_STATE_INVALID, HANDOVER_RESULT_UNKNOWN]
```

| 矩阵行 | 场景 | 实测结果多集 | 同用例内的不变量断言 |
| --- | --- | --- | --- |
| **M10** | 两线程用**同一 `caseVersion`** 各自 APPROVE 同一案件 | `[HANDOVER_CONTEXT_CHANGED, OK]` | 恰好 1 个成功、1 个被**具名**拒绝；案件终态 `APPROVED`（**不半途**）；**只有赢家留下回执**（败者事务整体回滚，`receiptBaseline+1`）；清单**只写一次**（`checklistBaseline+1`） |
| **M11** | 一线程 APPROVE（关闭该来源的案件）、另一线程对**同一 scope** 发第二个 REQUEST | `[HANDOVER_STATE_INVALID, HANDOVER_RESULT_UNKNOWN]` | 成功数 **≤1**（两个都被拒也是合法结果：REQUEST 与 `UNIQUE(scope_id,open_flag)` 冲突，APPROVE 遇到案件已被推进/状态不再可批准）；`handover_case` 中该 scope **始终只有 1 行**；案件终态落在合法集合内 |
| **M12** | 两线程并发**重放**同一原键同一字节 | `[OK, OK]` | 两者都返回**已存响应**；回执数**仍为 1**、案件数**仍为 1**、审计行**仍为 1**（并发重放不产生第二条） |

**观察到的语义（写进用例注释，不用单一预测码冒充）**：
- APPROVE 的**失败路径整体回滚**，因此"被拒的命令**不留回执**"（`HandoverCommandService.executeOnce` 的回执插入在业务操作之后，同事务）。
- 对同一 scope 的**第二个 REQUEST 无法执行**：`handover_case` 的 `UNIQUE(scope_id, open_flag)` 使其在业务插入处即冲突，服务按 `HANDOVER_RESULT_UNKNOWN` 收口（源码 `HandoverCommandService.java:51-55` 的 `DuplicateKeyException` 分支）。
- **M12 的口径限制（明示）**：回执在并发前已被顺序调用写入，因此本条证明的是"**并发重放**不产生第二条记录、且返回已存响应"，**不是**"同键写入竞争"。写入竞争由 M10 覆盖。此处按实测口径记录，不拔高。

## 3. **产品缺陷（本批新发现，已复现）**：R07-1 激活路径查询了不存在的列 `disabled_at`

### 3.1 现象（run 737，原文）

```text
FAILED com.acrylic.outbound.e2averify.E2aFormalRegisteredOperationsIT
org.springframework.jdbc.BadSqlGrammarException: PreparedStatementCallback; bad SQL grammar
[SELECT row_version FROM device WHERE id=? AND warehouse_id=? AND disabled_at IS NULL]
```

### 3.2 根因（源码 vs 迁移，双向核对）

| 侧 | 事实 |
| --- | --- |
| 产品源码 | `server/src/main/java/com/acrylic/outbound/handover/HandoverActivation.java:86` 查 `device ... AND disabled_at IS NULL`；`:93` 查 `app_user ... AND disabled_at IS NULL` |
| 数据库 schema | `device` 表（`V7__devices_sessions_and_assignments.sql:2-11`）列集为 `id, warehouse_id, device_code, name, public_key, key_sha256, status, row_version, created_by, created_at, last_seen_at`；`CHECK(status IN ('ACTIVE','DISABLED'))` —— **没有 `disabled_at`** |
| `app_user` 表 | 列集含 `status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'` 与 `CHECK (status IN ('ACTIVE','DISABLED'))`（`V7` 同轮），**没有 `disabled_at`** |
| 全仓搜索 | 对 `server/src/main/java` 全树 grep `disabled_at`：**只有上述两处**（`HandoverActivation.java:86,93`）；对 `server/src/main/resources/db/migration/*.sql` grep `disabled_at`：**0 命中** |

### 3.3 影响与定性

- **R07-1 的 PREPARE→ACTIVATE 链在真实库上必然失败**：`Activate.execute` → `activation.activate(...)` → `targetDevice(...)` 第一步即 `BadSqlGrammarException`。也就是**故障换机/可用换机的"激活"从未能跑通**。
- 该异常是**产品缺陷**，不是夹具漂移：DB 列不存在、代码查它、且两处都是 R07-1 激活路径的**第一道**目标校验。
- 与 A2.1 已登记的缺口一致（"R07 主路径已编码、边缘缺口集中"），但这是**主路径上的硬缺陷**，优先级高于此前登记的边缘缺口。

### 3.4 修复建议（**未实施，需授权**）

按 `device`/`app_user` 的真实语义把两处改为状态判定，例如 `... AND status='ACTIVE'`（`device`）与 `... AND status='ACTIVE'`（`app_user`），并保留既有的"无效目标"拒绝码 `HANDOVER_TARGET_DEVICE_INVALID`／`HANDOVER_TARGET_USER_INVALID`。**未按此修复的原因**：这属产品代码改动，接续规则要求先确认口径、逐项验收；本批只报告与留证。修复后 M7 才能运行（届时 M7 的 `ORDER_RECOVERY_REQUIRED` 断言才有意义：它位于该守卫**之后**）。

## 4. 过程失败轮（全部保留，不删除不重写）

| run | 失败点 | 定性 |
| --- | --- | --- |
| 738 | `BusinessException: 请先登录`（`UNAUTHENTICATED`） | 测试自身缺陷：顺序调用（`call`）没设置 `SecurityContextHolder` ⇒ 改为与工作线程同样的身份注入 |
| 739 | `DuplicateKeyException` on `submission_scope.source_order_id` | 测试自身缺陷：同一 order 已有 scope，不能再建第二个 scope 做"同键"对照 |
| 740 | `M10 both commands are recorded as receipts: expected 3 but was 2` | 测试自身缺陷（**且是重要观察**）：败者事务整体回滚 ⇒ 被拒命令**不留回执**，断言改为 `+1` |
| 741 | `M11 exactly one ... succeeds: expected 1 but was 0` | 测试自身缺陷：把"恰好一个成功"当作唯一合法结果；实测两个都被具名拒绝（见 §2）⇒ 改为断言"成功数 ≤1 且全部为具名结果" |
| 737 | **产品缺陷**（§3，非测试缺陷） | M7 无法运行的真实原因 |

## 5. A2.2 矩阵现状（14 条）

| 状态 | 条数 | 明细 |
| --- | --- | --- |
| 已跑通（服务层） | **14** | 反例①–④＋幂等①–③（run 718）＋M5/M8/M9a/M9b（run 736）＋**M10/M11/M12（run 742）** |
| 实现缺口（非缺测试） | **1** | M6：`HandoverCommand.corrupt()` 零调用点（S36 §3） |
| **被产品缺陷阻塞** | **1（M7 的一部分）** | 激活路径 `disabled_at` 不存在（§3）⇒ `ORDER_RECOVERY_REQUIRED` / `ORDER_BASELINE_CHANGED` 守卫位于该缺陷之后，**当前无法到达** |
| 待 A2.3 | 1 | M13-①（激活＋出站同事务，依赖 R07-1 outbox） |

> 说明：反例⑤–⑧（M5/M6/M7/M8）与 3 条并发（M10–M12）原为"零落点"。现 **M5/M8/M10/M11/M12 已落成**，**M6＝实现缺口**，**M7＝被 §3 缺陷阻塞**。

## 6. 未验边界

- 全部结论为**服务层**（M10–M12 为真实线程）；HTTP 层业务裁决、设备半场、真机、真实网络、断电、现场组合**未验**。
- 开关保持全默认 **false**（`outbound.handover.commands.enabled` 默认 false）；未部署、未接原库/现网、未合 main、未打标签/Release；`localDeletionAllowed=false`。
- **K1 仍未通过**。

## 7. 下一步（需授权）

1. **修复 §3 的 `disabled_at` 缺陷**（产品改动，1 处 2 行）⇒ 之后 M7 可运行、PREPARE/ACTIVATE 才能在真实库上往前走。
2. **M6**：回执完整性接线（产品改动）。
3. **M13-①**：待 A2.3（R07-1 outbox）。
4. **A2.1 待业主确认**：R07-05-a"主管↔权限码"映射。
