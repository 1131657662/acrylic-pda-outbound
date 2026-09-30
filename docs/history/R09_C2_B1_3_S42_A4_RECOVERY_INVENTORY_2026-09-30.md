# R09 C2 B1-3 S42：A4——R09 恢复与运维盘点（A4.1 证据对照；A4.2/A4.3 记阻断与待决）（2026-09-30）

> 轮次身份：主树 `D:/acrylic-pda-outbound`、分支 `codex/development`；写者＝DSH 本会话。
>
> 口径：本批**只做只读盘点 ＋ 一处注释纠正**（注释与 V46 实况对齐），**未改行为**。A4.1 的证据来自本会话已跑通的批次（不重复跑）；未验项原样单列。开关保持全默认 false；未部署。

## 1. A4.1 恢复与围栏：证据对照（结论＝已覆盖，不重跑）

剩余计划 A4.1 的动作是"共享恢复控制/fence、截点外 FH、历史 keyring、V36/V37 恢复语义、灾后重建"，退出标准是"**隔离故障验证通过，恢复前后原 ID/回执/调整保持、记账不重复**"。本会话的既有批次已逐项给出隔离证据：

| A4.1 子项 | 证据（本会话直接复用） | 结果 |
| --- | --- | --- |
| 共享恢复控制 / fence | 恢复链 25 类批次（`tools/r09-recovery-it-batch.ps1`，`.local/r09-a03-20260930-pinned-b/`），含 `RecoveryFenceAdversarialDurableIT`、`RecoveryFenceConnectionIT`、`RecoveryFenceCrossProcessIT`、`RecoveryFenceIntegrityIT`、`RecoveryFenceStartupIT`、`RecoveryFenceUnknownIT`、`RecoveryEffect*` 全家族、`G0K1*` | **25 类 / 696 项 / 0 失败 / 0 错误 / 0 跳过**；`portReleased=True` |
| 截点外 FH（fence 之外的分支） | 同上：`RecoveryFenceUnknownIT`（歧义 JDBC 结果走生产序列＋**真实 halt**）、`RecoveryFenceIntegrityIT`（原生身份拒绝且**从不重建**、每条非法记录判孤儿、自存活记录允许效果而**仅审计不构成依据**、线程与多 authority 共享内存围栏） | 通过（含在 696 项内） |
| V36 恢复语义 | V36 家族 6 项：`E2aV36MigrationIT`／`DeviceKeyIT`／`PairedKeyIT`／`AuditPrefixIT` 沿用已验基线；**`E2aV36StopWriterIT`（attempt3）与 `E2aV36AuditArchiveIT`（attempt7）本轮新跑通**（`.local/e2a-v36-{stop-attempt3,audit-attempt7}/`，各 1/0/0/0） | **6/6 通过** |
| V37 恢复语义 | `V37__unsubmitted_draft_evidence.sql` 的语义由 E2a 门控 IT 覆盖：`E2aDraftEvidenceIT`（run 717，1/0/0/0）、`E2aUnsubmittedHttpIT`（run 729，1/0/0/0）、**`E2aWithdrawnCorrectionExportIT`（run 730，1/0/0/0）** | 通过 |
| V38 恢复语义（后台作业围栏） | `E2aJobFenceHttpIT`（已验基线 `.local/e2a-cross-20260925-630`，1/0/0/0） | 通过 |
| 灾后重建（隔离） | 恢复链批次内的 `G0K1RealFileLockIT`（10 项，含 `destroyForcibly`／`Runtime.halt(70)` 后 OS 释放锁、残留文件≠持锁）＋`G0K1WriterLeaseIdentityRealIT`（5 项，同名替换/缺失 fail-closed）＋`G0K1RealJdbcBoundaryIT`（13 项） | 通过（含在 696 项内） |
| 原 ID / 原回执 / 取消与调整保持、记账不重复 | 由上述批次与 A0.1／A2.2 的组合断言覆盖（如更正撤销的逐笔反向与 `reverses_entry_id`、导出回归"区间成员冻结＋快照重放"、V36 stop writer 原字节持久化与回滚） | 通过（**本批未新增组合**） |
| **历史 keyring** | **未实现/未验**：`RecoveryLeaseRoots.verify()` 只比对 `anchorId`（当前秘密），**没有**多代秘密并存或历史 keyring 机制；B4 提案已给出"停机重锚、无运行期历史 keyring、旧秘密只读归档 90 天"的建议 | **记未验**（见 §2） |

**⇒ A4.1 判定**：共享恢复控制/fence、截点外 FH、V36/V37/V38 恢复语义、隔离灾后重建**均已有本机/隔离证据**；**历史 keyring 未实现**，属 A4.3／B4 范围。

## 2. A4.2 / A4.3：阻断与待决（不得臆断）

| 子项 | 状态 | 阻断条件 / 下一动作 |
| --- | --- | --- |
| **A4.2** RPO/RTO 与异地防覆盖的可隔离部分 | **部分未验** | 可隔离部分＝截点/围栏/重建已有证据（§1）；**RPO/RTO 的具体数值目标**属运维政策，**未定不臆断**；异地防覆盖需要第二处存储/异地介质（C 档现场条件） |
| **A4.3** Python 侧 v2 权威供给与 `closedAuthority` 身份（＝B9） | **未实施** | **前置＝B4 锚点保管/轮换提案获批后实施**；该提案已交付并获"按提案建议执行**已授权部分**"，其**实施级**内容（新 rekey 入口、轮换流程落地、旧秘密归档期限）仍需**单独授权**。在授权前**不写 B9 产品代码** |
| **历史 keyring** | **未实现** | 与 B4 绑定：B4 建议**不保留运行期历史 keyring**（改用"轮换时重打存活记录"）⇒ 若该建议获批，则本条按"设计上不保留"收口，而不是"实现一个 keyring" |

## 3. 本批唯一产品改动：注释与 V46 实况对齐（无行为变化）

`correction/CorrectionStore.java:75-81` 的注释原文称"**本仓库没有 `order_effective_scan` 表**"，而同一方法的 SQL（`:88-89`）已 `LEFT JOIN order_effective_scan`，且 `V46__correction_effect_and_result_version.sql` 明确写着"**新增 `order_effective_scan`**"。已把注释改为与实况一致（`V46` 增加了该表，重算优先读该扫描）。

**验证**：`gradlew -p server compileJava` `BUILD SUCCESSFUL`（纯注释改动，无行为变化）。**未跑回归**：改动不触碰字节码语义（注释），且 A3/S41 轮刚跑过全量单测（133 XML／2843 项／0 失败）与 R08 家族（47 项）。

## 4. 未验边界（原样保留）

- 真实外部环境/长时部分、异地介质、真实断电/OS 死亡、Linux 与其他文件系统、网络/共享盘锁语义、现场组合、R10 现场验收：**C 档，保持未验**。
- `DEBT-R09-C2-LEASE-ANCHOR`、孤儿解除与生产释放、Java/Python 允许集差异、`IT-DISCOVERY`（`E2aMigrationExecutionIT` 仍只有 skipped 证据、须 19329 专用舱）、`IT-EVIDENCE-AUDIT`、TZ-GMT 显示、R06 FORMAL 夹具、`R08PreviewSeedIT`：**继续单列**。
- 开关全默认 **false**；未部署、不接原库/现网、未合 main、未打标签/Release；`localDeletionAllowed=false`。
- **K1 仍未通过**。

## 5. 下一步

1. **A5（整合）**：按已获批的 [B7 提案](R09_C2_B7_TWO_TREE_INTEGRATION_PROPOSAL_2026-09-30.md) 执行 **I1＋I2**（判定唯一差异＝共享树那 5 行 attempt-117 诊断块，**不并入**；ROOT 依赖核对＝全仓 grep 无残余硬编码），并跑 B7 §4 的受影响回归清单。
2. **待决**：B4 的实施级授权（rekey 入口/轮换流程/旧秘密期限）⇒ A4.3/B9；A2.1 的主管↔权限码映射；W-2-d 未决孤儿计数。
3. **K1-1 证据核实**（提案 §6，只验证、不需新授权）；**K1-3** 补真实跨进程用例。
