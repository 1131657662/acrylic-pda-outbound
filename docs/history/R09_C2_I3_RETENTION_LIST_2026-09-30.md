# R09 C2 I3 前置：共享树「待保留清单」（执行前交付，待确认）（2026-09-30）

> **裁定依据**：用户 2026-09-30 原则同意本地树处置，但**执行前必须先交付"待保留清单"**确认；清单确认后才执行归档/重建。**本页只是清单，未做任何处置动作。**
>
> 边界（不变）：**不改 git 历史、不动 `origin`／主树／`main`**；`localDeletionAllowed=false`。

## 0. 树现状（2026-09-30 实测）

| 项 | 值 |
| --- | --- |
| 路径 | `D:\acrylic-pda-worktrees\r06-e2a-migration` |
| HEAD | **detached `ea4d166`**；`main..HEAD`＝**234** 个提交；`HEAD..codex/development`＝**落后 378** |
| 脏内容 | **83 项**＝30 项已跟踪被改 ＋ 53 项未跟踪（原登记 97/95 为不同时点的测量，**以本次 83 为准**） |
| 体积 | **约 14.3 GB**（几乎全在构建产物与 `.local` 数据；**未跟踪的源码项合计 < 1 MB**） |

## 1. 必保留项 A：唯一实质差异（**绝不进 main**）

**A-1（已跟踪、被改）** `server/src/main/java/com/acrylic/outbound/temporary/command/CommandReceiptIntegrity.java`

- 共享树版本**多 5 行**，内容自述为临时诊断：

```text
// TEMPORARY DIAGNOSTIC (attempt 117): name the failing invariant instead of swallowing it.
System.err.println("RECEIPT-INTEGRITY-CAUSE: " + damaged);
damaged.printStackTrace(System.err);
```

- 主树/HEAD 版本为**干净版本**（grep `DIAGNOSTIC|attempt 117|printStackTrace|System.err` 为空）。
- **保留决定**：**归档保留**（作为 attempt 117 的失败证据），**绝不并入任何分支、绝不进 main**。同类诊断在 S31 已有 `git checkout --` 撤销先例。

## 2. 必保留项 B：**6 个差异源码文件**（本轮新发现，务必看）

**更正**：此前 B7 的只读比对覆盖了 28 项已跟踪改动，得出"唯一实质差异＝A-1"。本次把 **53 项未跟踪**也逐文件比对后，发现 **另有 6 个未跟踪源码文件与主树不同**（其余 61 项未跟踪源码与主树**逐字节相同**）。逐个定性：

| # | 文件（共享树未跟踪副本） | 定性 | 与主树/HEAD 的关系 |
| --- | --- | --- | --- |
| B-1 | `correction/CorrectionStore.java` | **仅注释差异** | 共享副本仍是旧注释"There is no `order_effective_scan` table…"；主树已更正 |
| B-2 | `handover/HandoverActivation.java` | **代码差异＝旧缺陷版** | 共享副本 `SELECT row_version FROM device WHERE … AND disabled_at IS NULL`——**`disabled_at` 列不存在**（S38 修复前版本） |
| B-3 | `handover/HandoverCommand.java` | 代码差异 | 共享副本缺 `corruptUnless`（S38 接线前版本） |
| B-4 | `handover/HandoverCommandService.java` | 代码差异 | 共享副本 `replay` 直接 `row.get("response_bytes")`，**无 `codec.verify(row)`**（S38 修复前版本） |
| B-5 | `handover/HandoverReceiptCodec.java` | 代码差异 | 共享副本缺 `Base64` 导入及其校验实现（S38 修复前版本） |
| B-6 | `handover/HandoverStore.java` | 代码差异 | 共享副本 `activationHex=…sha(activationReceipt)`，**未对 `prepared_ticket_sha256` 做 `unhex`**（S39 修复前版本） |

**这 5 个 handover 副本（B-2…B-6）是"S38/S39 三处产品缺陷修复之前"的版本**（`disabled_at`、`prepared_ticket_sha256` 截断、回执未校验），**主树/HEAD 已修复**。

**保留决定（建议）**：
1. **不并入**（并入会把三处已修缺陷带回来）；
2. **作为失败证据归档**——它们正是 S38/S39 修复的**输入侧证据**；
3. 主树已有对应的**修复记录与回归证据**（S38／S39 记录＋133 XML/2843 项回归），因此这 6 个文件**不需要在主树重建**。

## 3. 必保留项 C：失败证据与不可再生数据

| 项 | 位置 | 为何不可再生 |
| --- | --- | --- |
| attempt 117 诊断块 | 共享树 A-1 文件 | 该次 attempt 的失败现场；删除后无法复现 |
| 5 个修复前源码副本 | 共享树 B-2…B-6 | 修复前状态的唯一在工作区里的留存（committed 侧不再有） |
| 共享树 `.local/` 下的历史 attempt 数据 | 共享树 `.local/**` | 大量隔离实例数据目录（**体积主体**）；失败/跳过证据按纪律**不删除** |
| 共享树的 `build/` 产物 | 共享树 `**/build/**` | **可再生**（可由源码重建），**不属必保留**；处置时可直接丢弃 |

> **注意**：共享树的 `.local/` 与主树 `.local/` 是**两套独立证据**。归档共享树时必须**连 `.local/` 一起打包**，否则 A-1／B-2…B-6 之外的 attempt 证据会丢失。

## 4. 必保留项 D：8 个 `tools/` 脚本的默认值清单与影响

| # | 文件 | 行 | 现状默认值 | 建议改法 | 影响（不改会怎样） |
| --- | --- | --- | --- | --- | --- |
| D-1 | `tools/cleanup-old-attempt-datadirs.ps1` | L40 | `$Local = 'D:\acrylic-pda-worktrees\r06-e2a-migration\.local'` | 指向主树 `.local`，或改为**必填参数** | 清理脚本会去**已不存在的路径**，静默什么都不做（或误清共享树残留） |
| D-2 | `tools/e2a-android-link-l4.ps1` | L28 | `-Workspace` 默认共享树 | 改主树 | 运行即在错误树上作业 |
| D-3 | `tools/e2a-batch-it.ps1` | L31 | 同上 | 改主树 | 同上 |
| D-4 | `tools/e2a-cross-source-verify.ps1` | L3 | `$root` 硬编码共享树（**非参数**） | 参数化＋默认主树 | 无法覆盖，必然指向旧树 |
| D-5 | `tools/e2a-link-counterproof.ps1` | L6 | `$root` 硬编码共享树（**非参数**） | 参数化＋默认主树 | 同上 |
| D-6 | `tools/e2a-provision-and-run-106.ps1` | L29 | `-Workspace` 默认共享树 | 改主树 | 同上 |
| D-7 | `tools/it-coverage.ps1` | L29 | `-Workspace` 默认共享树 | 改主树 | 覆盖率统计扫错树 |
| D-8 | `tools/r08-provision-and-run.ps1` | L40 | `-Workspace` 默认共享树 | 改主树 | R08 IT 默认跑在旧树（**本会话已用显式 `-Workspace` 规避**） |

**补充说明（不在 8 个之列，但需知悉）**：`server/src/test/.../e2averify/E2aWorktree.java:15` 的 `E2A_CROSS_ROOT` **默认值**也指共享树；两个 r09 runner **每次都会导出** `E2A_CROSS_ROOT=<被测树>`，故**当前 run 路径不受影响**。建议保留该默认值为**文档化回退**，或一并改为主树（**二选一，请裁定**）。

## 5. 建议的处置方式（待确认后执行）

| 方案 | 内容 | 体积影响 | 风险 |
| --- | --- | --- | --- |
| **①归档后移除（推荐）** | 打包**整棵树**（含 `.local/`，排除 `**/build/**`）为带 SHA256 清单的归档，置于 `.local/archive/`；校验归档可读后 `git worktree remove`；再按 D-1…D-8 改脚本 | 归档后磁盘回收约 14 GB（build 产物不打包） | 归档为唯一快照 ⇒ **必须先校验归档完整性**再移除 |
| ②仅归档、不移除 | 同上但不 remove | 不回收磁盘 | 无 |
| ③仅改脚本、树保持 | 只做 D-1…D-8 | 无 | 树继续占 14 GB；将来仍可能误用 |

**执行前需要你确认的三点**：①是否采用方案①（移除）还是②（只归档）；②归档是否**排除 `**/build/**`**（建议排除，体积主体且可再生）；③`E2aWorktree` 默认值是否一并改主树（见 §4 补充）。

## 6. 边界声明

- 本页**只是清单**：**未移动、未删除、未归档、未修改**共享树与任何 `tools/` 脚本；共享树保持 detached `ea4d166`、83 项脏内容原样。
- **不改 git 历史、不动 `origin`／主树／`main`**；不部署；`localDeletionAllowed=false`。
- 清单获得确认后，再按确认的选项执行，并**先校验归档完整性**。
