# 验证基础设施工程任务 —— 总收口（2026-09-27）

时间盒 10 轮，**实际用 9 轮**（余 1 轮未用）。三项资产/知识 + 一项未完成，逐项如实记录。

## 1. R01 Robolectric 基线 —— **资产，自验通过**（2 轮）

- `android/app/src/test/java/com/acrylic/outbound/pda/support/TestInfrastructure.kt` + `TestInfrastructureTest.kt`（5 项自验全绿，独立于业务）；
- 固化两条经验：**JSON `null` ≠ 字符串 `"null"`**（`optString` 会返回字面量）、**迁移测试只引用 `PDA_DATABASE_VERSION`**；
- 文档：[VALIDATION_INFRASTRUCTURE_R01_ROBOLECTRIC_2026-09-27.md](VALIDATION_INFRASTRUCTURE_R01_ROBOLECTRIC_2026-09-27.md)；
- 全量 Android 单测：`tests=460 failures=0 errors=0 skipped=4`。

## 2. R02 服务端真实 HTTP 构造器 —— **资产，自验通过**（2 轮）

- `tools/e2a-provision-and-run-106.ps1` 提炼为通用构造器：参数 `-Run/-Tests/-Port/-Workspace/-ProvisionOnly/-KeepServerRunning`；保留真连接就绪判定、exit code 全检查、重跑守卫；**新增停库收尾**；
- 自验（attempt 145）：`tests=1 failures=0 errors=0 skipped=0 [pass]` + `teardown: port 19330 free = True`；
- 文档：[VALIDATION_INFRASTRUCTURE_R02_REAL_HTTP_2026-09-27.md](VALIDATION_INFRASTRUCTURE_R02_REAL_HTTP_2026-09-27.md)。

## 3. R03 FORMAL 来源夹具 —— **不成立**（4 轮，含经批准的第 8 轮）

- attempt 146 **通过**（SQL 造 `REGISTERED` FORMAL 来源被接受：复用 V34 seed 的已发布订单作宿主，绕开冻结/回执/采集序号三链）；
- attempt 147 失败：`交接案件不存在或无权访问` → 发现**第 4 道前置**（FORMAL scope 必须设 `canonical_order_id`）；
- attempt 148 失败：`BadSqlGrammarException`（我补列漏补实参）；
- attempt 149 失败：`Check constraint 'ck_submission_scope_source' is violated`（HY000/3819）→ **第 5 道前置，定义未读**；
- **4 条反例（未停止/未决未归零/缺审计/非主管审批）一条未跑**；验证层级若跑通也仅为**服务层**；
- 文档：[VALIDATION_INFRASTRUCTURE_R03_FORMAL_FIXTURE_CLOSURE_2026-09-27.md](VALIDATION_INFRASTRUCTURE_R03_FORMAL_FIXTURE_CLOSURE_2026-09-27.md)。

**FORMAL 来源的 5 道硬前置**：① `submission.assignment_id NOT NULL → order_assignment`；② `ABORTED/SUCCEEDED 需 receipt_text`（`REGISTERED` 不触发）；③ `submission_scope.source_order_id NOT NULL UNIQUE`；④ FORMAL 必须设 `canonical_order_id`；⑤ `CHECK ck_submission_scope_source`（定义未读）。

## 4. HTTP 层验证（R07 路由拦截）—— **未完成**（1 轮）

- attempt 150：`tests=1 failures=1 errors=0 skipped=0`，断言 ① 得到状态 `-1`——**夹具缺陷**：探测用户以 `password_hash='x'` 写入，`/auth/web/login` 从未成功，因此 4 条断言**一条都没被真正求值**（不是 HTTP 层的发现）；
- 一行修复方向（下一批用）：用真实 `PasswordEncoder` 生成口令散列（同 `IdentityFixture` 的做法）；
- 本轮**未得到**任何 HTTP 层结论；R07 端点拦截（开关关 404 / 无会话 401 / 无 `order:handover` 403 / 有权限进入业务）**仍全部未验**。

## 5. 时间盒账目与结论

| 项 | 预算 | 实际 | 结论 |
| --- | --- | --- | --- |
| R01 Robolectric 基线 | 3 | 2 | ✅ 资产 |
| R02 真实 HTTP 构造器 | 4 | 2 | ✅ 资产 |
| R03 FORMAL 夹具 | 3 | 4 | ❌ 不成立，5 道前置固化 |
| HTTP 层验证 | — | 1 | ❌ 未完成（夹具缺陷） |
| 合计 | 10 | **9** | 余 1 轮 |

**结论**：
1. 「验证基础设施」工程任务**正式收口**——两项资产可用（Robolectric 基线、真实 HTTP 构造器），一项知识固化（FORMAL 5 道前置），两项未完成（FORMAL 夹具、HTTP 层验证）；
2. **FORMAL 来源夹具需独立排期**，给**更长的时间盒**（已知 5 道前置 + 可复用 R02 构造器 + R03 第 1 轮的可用起点）；
3. **R07-1 的 14 条断言仍全部未跑**，且 HTTP 层验证也待做；两者都依赖 FORMAL 夹具或 HTTP 探测夹具的独立投入；
4. 结构性事实（第四次记录）：R06 九轮、R07 五轮、本任务 9 轮——**端到端与验证基础设施的成本被系统性低估**，后续应按独立工程任务排期与验收，不再混入业务子项按需消耗。