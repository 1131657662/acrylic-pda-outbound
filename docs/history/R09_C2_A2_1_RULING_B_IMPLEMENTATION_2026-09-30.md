# R09 C2 A2.1 裁定 B 实施：更正进门码按命令类型分离 ＋ maker-checker（2026-09-30）

> **状态：已实施、已验证、待提交。** 依据用户 2026-09-30 裁定 **B**（进门码按命令类型分）与**授权扩界**（允许修改更正内核授权判决逻辑，超出"只做角色/权限映射"字面范围）。
>
> 验证方式遵命：**复用已有 datadir 起同一 mysqld 查表／跑单类**，**未起全套 provision**（0 个新 `e2a-cross-*` 目录）。

## 1. 改了什么（3 处授权判决）

| # | 文件 | 改动 |
| --- | --- | --- |
| 1 | `correction/CorrectionCommand.java` | 新增**唯一**判定 `static boolean requiresApproval(String kind)`（`APPROVE`/`WITHDRAW`），并把 `Operation.requiresApproval()` 默认实现改为委托它，避免两处定义漂移 |
| 2 | 同上 | 身份门 `Identity.current(UUID warehouse)` → `current(UUID warehouse, boolean approval)`：`if (!permissions.contains(approval?APPROVE_PERMISSION:CORRECT_PERMISSION)) throw denied();` ⇒ **审批类只要求 `correction:approve`；其余仍要求 `outbound:correct`** |
| 3 | `correction/CorrectionCommandService.java` | 调用点改为 `identities.current(warehouseId, CorrectionCommand.requiresApproval(input.kind()))`（在 operation 解析**之前**就按类型裁决） |
| 4 | `correction/CorrectionOperations.java`（`Approve.execute`） | **新增 maker-checker 守卫**：`write.actor().principalId == facts.requestedBy` ⇒ 抛**新错误码** `CORRECTION_SELF_APPROVAL_DENIED`（403），检查点放在状态/前置校验**之前**，避免被 `stale()` 掩盖 |

### 必须写清的一点：③ 是**新增规则**，不是"补个测试"

审阅源码后确认：**改前并不存在"建档人不得自批"的守卫**（既有的相近逻辑只有 `WITHDRAW_CONFIRM` 要求**由建档人本人确认**，方向相反）。因此反例③**不是补一个已有规则的用例，而是新增了一条授权规则**——这正是裁定里"maker-checker 核心"所要求的，我把它作为**产品行为变更**在此明示，而不是当作纯测试补充。

## 2. 三条反例与实测证据

**验证环境**：复用 `.local/e2a-cross-20260925-773/data`（该库已应用 **V49/V50**）在同一 mysqld（19330）上起服，**不播种、不迁移**；`A21_GATE_VERIFY=true` 走真实 `CorrectionCommandService`。

**证据（`.local/a21-gate-evidence.txt`）**：

```text
verification level=real service on a reused migrated schema (no provision)
SUPERVISOR=[correction:approve, order:handover, order:resume]
WAREHOUSE_ADMIN holds 23 code(s)
①WAREHOUSE_ADMIN(correct-only) 批单 -> CORRECTION_PERMISSION_DENIED
②SUPERVISOR(approve-only) 批单 -> SIGNING_KEY_UNAVAILABLE (期望：不是权限拒绝)
③SUPERVISOR 批自己建立的单 -> CORRECTION_SELF_APPROVAL_DENIED (期望 CORRECTION_SELF_APPROVAL_DENIED)
```

| 反例 | 前提（同一次运行内实测） | 结果 | 结论 |
| --- | --- | --- | --- |
| **① 非 SUPERVISOR 被拒** | `WAREHOUSE_ADMIN` 持 23 码、含 `outbound:correct`、**不含** `correction:approve` | `CORRECTION_PERMISSION_DENIED` | 审批门对非审批主体关闭 ✓ |
| **② SUPERVISOR 无 `outbound:correct` 也能批** | 该运行实测 `SUPERVISOR=[correction:approve, order:handover, order:resume]`——**确实不含** `outbound:correct` | `SIGNING_KEY_UNAVAILABLE`（**不是**权限拒绝） | 主管**越过了权限门与全部业务校验**，只在最后**签名**环节因本机未配该库的密钥而止步 ⇒ 进门码分离成立 ✓ |
| **③ 建档人不能自批** | 该单 `requested_by = SUPERVISOR` 本人，且其人持有 `correction:approve` | `CORRECTION_SELF_APPROVAL_DENIED` | maker-checker 生效，且**与"缺权限"可区分** ✓ |

**为什么②比"没被拒"更强**：`executeOnce` 的顺序是 `进门码 → replay → operation → 状态闸 → 锁(document→order) → 仓库校验 → Expected.validate → mayApprove → execute`。②能走到 **`SIGNING_KEY_UNAVAILABLE`**，说明它已经通过了 `Expected.validate` 与 `mayApprove`，即**授权链完全通过**；失败点是加密签名（本机未为复用库配置匹配密钥），与授权无关。

**失败轮（保留）**：首轮 IT 得 `CORRECTION_COMMAND_NOT_OPEN`——因为 `Approve` 的 operation bean 受 `outbound.corrections.commands.enabled` 控制而我的 IT 未打开该开关；补 `--outbound.corrections.commands.enabled=true` 后 ①（在闸前被拒）②③ 才真正走到授权判决。该失败与修正过程保留记录，未删除。

## 3. 回归

- **一级全量单测**：见 §5 结果（产品改动为 `CorrectionCommand`/`CorrectionCommandService`/`CorrectionOperations`）。
- **零新增 provision**：本次验证**未创建任何** `e2a-cross-*` 目录；复用 773 的 datadir 起停同一 mysqld，验证后端口 19330 已释放。

## 4. 一处已定位的连带影响（**已登记，未臆改**）

**`E2aCorrectionChainIT` 必须改**：它在**同一个 actor** 上先建档（L61 `post(token, create)`）再批单（L111 起 `approveBody(...)` + 后续 (c)(d) 步骤）。裁定 B 只改授权门、**不改"同一人不得既建又批"这条新规则**，所以该 IT 的批单阶段现在会被 `CORRECTION_SELF_APPROVAL_DENIED` 拒绝。

- **这不是缺陷，而是裁定③的既定后果**：该 IT 的旧前提"建档人可以自己批"已被新规则取代。
- **修法方向**（待授权后执行，需一次真跑验证）：为该 IT 引入**独立的审批主体**（绑定 `SUPERVISOR` 或把 `correction:approve` 授予另一个 actor 的角色），批单阶段改用该主体；同时可把"建档人自批被拒"作为反例并入该 IT。
- **为什么不现在瞎改**：该 IT 有 14+ 个步骤、依赖真实 HTTP 链，且按你的指令**本轮禁止起全套 provision**，改动无法就地验证；盲改会让测试以错误的理由通过。故**登记 + 给出修法**，等一次被授权的 provision 再改。
- **附注**：该 IT 只迁移到 **V48**，因此**不受 V50 角色改绑影响**（V50 未应用到它）；它受影响的原因只有 maker-checker 这一条。这一点已核对，避免把两件事混为一谈。

## 5. 记录要求（用户 2026-09-30 指定，逐字固化）

> **D 的 `E2aMigrationEnvironmentTest` 改动必须在提交说明或文档里明确标注**：「因共享树移除导致原测试前提（主树是"另一个 worktree"）失效，改为结构性 sibling 前提，**不是削弱断言**」，避免以后被误读。

**登记（见 [I3 执行记录](R09_C2_I3_EXECUTION_2026-09-30.md) §D 与本页）**：`E2aMigrationEnvironmentTest.rejectsAnotherWorktreeBeforeAnyDatabaseAction` 原先把硬编码的 `D:/acrylic-pda-outbound` 当作"**另一个** worktree"来断言必须被拒；在 `E2aWorktree.ROOT` 改为该路径后，这个前提**自我指涉**而失效。改法是换成 `WORKSPACE.resolveSibling(<name>-not-the-workspace)`——**由构造保证**不是工作区。**该改法没有削弱断言**：被拒绝的仍是一个真实的非工作区路径，`isInstanceOf(IllegalStateException.class)` 的强度不变；它反而**去掉了一处硬编码**，使断言不再依赖"主树恰好不是 ROOT"这一偶然事实。

## 6. 边界声明

- 开关**全默认 false**（含 `outbound.corrections.commands.enabled`，仅在本 IT 进程内以参数打开）；未部署、不接原库现网、未合 `main`、未打标签；`localDeletionAllowed=false`。
- **未删除任何 `.local` 证据**（含本次复用库与全部 `e2a-cross-*`）；失败轮保留。
- 本页的验证是**真实服务层 + 复用真实库**；**不是** HTTP 端到端，也不是真机 PDA。
