# B7 提案：两树整合顺序与每批回归清单（2026-09-30）

> **状态：提案，未获批。未获批不实施整合动作。** 本页只是整合顺序与回归定义，**不含**任何合并、清理、删除、提交或生产动作。
>
> 授权依据：用户 2026-09-29 裁定⑦——"先产出**整合顺序＋每批回归清单提案**，按批独立验收；不新增长期分支，不混入他人未提交改动"。登记见[剩余计划](../REMAINING_DEVELOPMENT_PLAN.md)「已裁定/授权的接续决定」B7 行。

## 1. 术语：这里的"两树"到底指什么

| 树 | 路径 | 状态 |
| --- | --- | --- |
| **主树** | `D:/acrylic-pda-outbound` | 分支 `codex/development`；**唯一写者所在** |
| **共享树** | `D:/acrylic-pda-worktrees/r06-e2a-migration` | **detached HEAD** `ea4d166`（2026-09-27）；历史 E2a 测试曾把它硬编码为 ROOT（`E2A_CROSS_ROOT` 覆盖为 S13 新增） |

**不是**"两个仓库"：`git remote -v` 只有一个 `origin`；全部 35 个本地分支（含 `main`）都是 **0 ahead**（即没有任何未并入 `codex/development` 的提交），`ea4d166` 本身**已是** HEAD 的祖先且落后 **348 个提交**。

⇒ 因此"整合"的对象**不是提交**，而是**共享树上未提交的工作区内容**与**共享树作为旧 ROOT 的角色**。

## 2. 实测事实（本提案的依据）

| # | 事实 | 证据 |
| --- | --- | --- |
| F1 | 所有本地分支相对 `codex/development` **0 ahead**；`main` 落后 582 | `git rev-list --left-right --count codex/development...<branch>` 全量输出 |
| F2 | `ea4d166` 是 HEAD 的**祖先**，落后 348 | `git merge-base --is-ancestor ea4d166 HEAD` → 0；`git rev-list --left-right --count HEAD...ea4d166` → `348 0` |
| F3 | 共享树工作区：**28 个修改文件 + 67 个未跟踪文件 = 95 项** | `git -C <shared> diff --name-only`、`git ls-files --others --exclude-standard` |
| F4 | **95 项中 94 项与主树工作区逐字节相同（LF 归一化后）** | `.local/shared-tree-reconcile-20260930/normalized-diffs.txt`：`FILES_COMPARED=95 NORMALIZED_DIFFS=1` |
| F5 | 唯一实质差异是 `server/src/main/java/com/acrylic/outbound/temporary/command/CommandReceiptIntegrity.java`：共享树版本比主树/HEAD **多一段 5 行**的 `catch(RuntimeException damaged)` 诊断块（注释自述 `TEMPORARY DIAGNOSTIC (attempt 117)`，`System.err.println` + `printStackTrace`） | 行级归一化 diff（见同目录 `normalized-diffs.txt`） |
| F6 | 主树工作区干净（除本轮新增文档/工具与**另一会话**的 2 个 docs 项）；无 `index.lock`、无 `MERGE_HEAD`、无未合并路径；`git diff --check` 退出 0 | `git status --porcelain`、`git diff --check` |

**结论（重要）**：共享树**没有任何"未落地的实质工作"**。它的 94/95 项只是主树已提交/已存在内容的**旧快照**；剩下那 1 项是**历史上被授权"事后撤回"的临时诊断代码**（同类诊断在 S31 已用 `git checkout --` 撤销，见 [S31 记录](R09_C2_B1_3_S31_SIGNING_BINDING_2026-09-29.md)）。

## 3. 建议的整合顺序（每批独立验收，不新增长期分支）

| 批次 | 动作 | 验收标准 | 风险 |
| --- | --- | --- | --- |
| **I0** | **只读冻结快照**：记录共享树当前 95 项的路径+哈希、`ea4d166`、主树 HEAD，落 `.local/`（本轮已做） | 快照可复核；不改动共享树任何文件 | 无 |
| **I1** | **判定 F5 的唯一差异**：确认共享树那份 `CommandReceiptIntegrity.java` **仅为 attempt 117 的临时诊断**，**不**并入主树；把这一证据写进 `docs/history/` | 给出主树版本=正确版本、共享树版本=诊断版本的逐行依据；**不**把这 5 行合并进主树 | 低 |
| **I2** | **ROOT 依赖核对**：确认主树不再有任何测试/工具硬编码共享树路径（`E2A_CROSS_ROOT` 覆盖已存在，`tools/r09-e2a-entry-rerun.ps1` 用 `E2A_CROSS_ROOT`） | 全仓库 grep 无残余硬编码；主树自身可独立跑 E2a/库存/V36 类 IT | 低 |
| **I3** | **共享树处置建议（需单独授权）**：`git worktree remove` 或保留为只读归档 | 处置前先按 I0 快照留证；**不得**在未授权下删除 | 中（删除不可逆） |
| **I4** | **遗漏核对 R01–R09**：对"实际整合输入"（＝主树 HEAD）跑 §4 回归清单，为每个后延项列缺条件与下一动作 | 见 §4；**不以不同版本历史通过拼接为最终通过** | 中 |

**不做的事**：不新建 `codex/<topic>` 长期分支；不合并任何未提交改动进主树；不 `reset`/`checkout` 共享树；不删除共享树（I3 需单独授权）。

## 4. 每批回归清单

### I1（判定唯一差异）回归
- 无需构建：只做行级对比与源码阅读。
- 断言：主树 `CommandReceiptIntegrity.java` **不含** `System.err.println`／`printStackTrace`／`attempt 117` 诊断字样（grep 证据）。

### I2（ROOT 依赖核对）回归
| 项 | 命令/断言 |
| --- | --- |
| 无残余硬编码 | 全仓库 grep `r06-e2a-migration`：仅允许出现在**文档/历史记录**中，**不得**出现在 `tools/`、`server/src/test/`、`android/` 的可执行路径 |
| E2a 主树可独立跑 | `tools/r09-e2a-entry-rerun.ps1` 在同一 attempt 上跑 1 个门控 IT 通过（不复用共享树） |
| V36 可独立跑 | `tools/r09-e2a-v36-run.ps1` 至少 1 个 item 通过 |

### I4（遗漏核对）受影响回归清单

| # | 回归面 | 入口 | 通过标准 |
| --- | --- | --- | --- |
| R-1 | 一级全量单测 | `gradlew -p server test` | 无新增失败；既有 1 项 skip 保持不变 |
| R-2 | 恢复链 25 类批次 | `tools/r09-recovery-it-batch.ps1` | 25 类 / 696 项 / 0 失败 / 0 跳过 |
| R-3 | E2a 门控 IT（已验 24 类 + 本轮 A0.1 新增） | `tools/r09-e2a-entry-rerun.ps1` | 每类 tests/failures/errors/skipped 明确；skip 记"未尝试" |
| R-4 | 库存家族 10 类 + 11 个 PDA 夹具 | 同 R-3（`-AppMigrates`） | 全绿；夹具产物存在 |
| R-5 | V36 家族 6 项 | `tools/r09-e2a-v36-run.ps1` | 6/6 或逐项给出失败点定性 |
| R-6 | Android 套件 | `gradlew -p android :app:testDebugUnitTest` | 0 失败 |
| R-7 | 报表/渲染 | `report-rendering` 安装树 + 渲染类 IT | 0 失败 |
| R-8 | 契约与迁移 | `contracts/openapi.yaml` 路径数、Flyway 最高版本/迁移数与[看板事实块](../ORCHESTRATION_STATUS.md)一致 | `tools/version_facts.ps1 -Check` 通过 |
| R-9 | 文档上限 | 看板当前区 | ≤100 行且 ≤8000 字符（脚本校验） |

**执行纪律**：单次只允许**一个 Gradle 构建**与**一个数据库写者**；失败与跳过轮证据一律保留、不删除不重写；隔离库仅回环、端口 19400–19999（E2a 实验舱 19330），**禁 3306**。

## 5. 合并完整性说明（防止"看起来整合了"）

- **本轮不产生合并提交**，因为没有可合并的提交（§1）。
- 若用户认为"两树整合"指的是**另一层含义**（例如某个具体业务分支的语义合并），请在裁定中指出目标分支名；本提案会按"该分支 vs `codex/development` 的差异清单 + 受影响回归"重做，不沿用本页结论。
- **不得**用"历史通过"替代"当前整合输入上的回归"（AGENTS.md 与[剩余计划](../REMAINING_DEVELOPMENT_PLAN.md)第 19 行均明确）。

## 6. 边界声明

- 本提案**不构成**生产/部署/main/发布授权；不合 `main`、不打标签/Release、不部署、不接原库/现网、`localDeletionAllowed=false`。
- **不混入他人未提交改动**：主树当前 `docs/PDA_OFFLINE_CONTINUITY_ANALYSIS_2026-09-29.md`（已修改）与未跟踪 `docs/PDA_UI_UX_REVIEW_2026-09-29.md` 属**另一会话**，本提案的执行批**不得**代提交、代修改或删除。
- 共享树的处置（I3）**必须单独授权**；授权前只读。

## 7. 请用户裁定的三个问题

1. 是否确认本页 §1/§2 的"两树"含义（主树 + `r06-e2a-migration` 共享树），以及"无提交可合并、只剩 1 项临时诊断差异"的结论？
2. 是否批准 **I1 + I2 + I4**（判定差异、ROOT 依赖核对、受影响回归）？
3. **I3（共享树删除/归档）** 是否单独裁定？若裁定删除，请确认"先留 I0 快照再删除"的顺序。
