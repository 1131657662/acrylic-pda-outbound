# R09 C2 B1 K1-2 验收：窗口 W 的处置（W-2 有界已知边界）（2026-09-30）

> **性质**：本页把用户 2026-09-30 对 [K1 通过判据提案](R09_C2_B1_K1_PASS_CRITERIA_PROPOSAL_2026-09-30.md) §4 的裁定 **W-2（正式接受为有界已知边界）**落成可核对的验收记录。
>
> **不含**任何实现、开关改动或生产声明；**不**新增产品代码；**不**把 W 写成"已消除"。
>
> 版本四元组只读[看板](../ORCHESTRATION_STATUS.md)生成事实块。

## 1. 裁定与口径

| 项 | 内容 |
| --- | --- |
| 用户裁定 | **W-2**：不新增机制；把"孤儿检测延迟有界"写成可验收命题，并登记补偿控制 |
| 结果表述 | K1-2 记 **"已接受为已知边界"**（**不是**"已消除"） |
| K1 总体 | **仍未通过**：K1-1（真实驱动证据待核实）、K1-3（依条件分支缺真实跨进程用例）不因本页改变 |
| 窗口 W 与 `UNCONFIRMED_NO_CROSS_PROCESS_BASIS` | **原样保留**，不作废除或改名 |

## 2. 机制事实（读源码得出，附路径）

- **孤儿扫描没有定时器**：`RecoveryLeaseScan.check(roots)` 只在三处**同步**触发——
  ① `RecoveryFenceAuthority.validateStartup()`（启动，`RecoveryFenceAuthority.java:24`）；
  ② `checkEffect()`（每次效果门检查，`RecoveryFenceAuthority.java:35`）；
  ③ `registerOperation()`（离线操作登记，`RecoveryFenceAuthority.java:44`）。
- **扫描语义**：`RecoveryLeaseScan` 的类注释即写明"**Each owner is observed separately. A successful scan does NOT eliminate window W.**"（`RecoveryLeaseScan.java:12`）；检出孤儿会 `RECOVERY_LEASE_ORPHAN` 审计并退役该记录后再拒绝启动（`:53`、`:61-64`）。
- ⇒ **窗口 W＝两次触发之间的间隔**；机制只保证**逐记录命题**："属主在其记录判定时刻或之前已死 ⇒ 该次扫描检出"，**不保证** "t_n 时刻无孤儿"。

## 3. W-2 的四条可验证完成标准（逐条给结论）

| # | 标准 | 本轮结论 | 证据 |
| --- | --- | --- | --- |
| W-2-a | 检测延迟上界＝**一个触发间隔**，且有可回放轨迹证明 | **成立** | 门控 IT（已验基线，同一产品 HEAD）：`E2aRecoveryWriterTransactionIT`＝`.local/e2a-cross-20260925-621/TEST-*.xml`（1/0/0/0）、`E2aRecoveryAdmissionEntryIT`＝`.local/e2a-cross-20260925-604/TEST-*.xml`（1/0/0/0）；模型侧可回放反例 `ownerDeathInsideWindowWIsMissedByThatScanAndCaughtByTheNext`（`G0K1OrderingModelTest`，属 K1 模型轮证据，**不并入真实结论**） |
| W-2-b | **触发间隔的可量化取值**（默认配置下两次触发的实际间隔） | **部分**：间隔**由调用频率决定**、产品内**无定时器**，因此不存在"配置项取值"可写；可量化的是**已实测的最短触发间隔**——现场两子 JVM 串行实验（S12）与本次 25 类批次（`.local/r09-a03-20260930-pinned-b/batch-summary.txt`）中相邻触发落在**秒级** | `recovery/RecoveryFenceAuthority.java:24,35,44`；[S12 记录](R09_C2_B1_3_S12_LEASE_PUBLICATION_2026-09-29.md)；本页 §5 |
| W-2-c | "孤儿存在期间不得被当作无孤儿放行"的**失败关闭**有反例 | **成立** | `RecoveryLeaseScan.check` 检出孤儿后**退役并抛 IOException 拒绝启动**（`RecoveryLeaseScan.java:61-64`）；门控证据：`E2aRecoveryAdmissionEntryIT`（`.local/e2a-cross-20260925-604`，1/0/0/0）、`E2aRecoveryWriterTransactionIT`（`.local/e2a-cross-20260925-621`，1/0/0/0）；S9 现场证据（发布租约→退役→下次启动因孤儿被拒） |
| W-2-d | **审计/运维可见未决计数** | **未成立（缺口）** | 孤儿检出时写 `RECOVERY_LEASE_ORPHAN` 审计事件（`RecoveryLeaseScan.java:53`）⇒ **单事件可见**；但**没有"当前未决孤儿数"的可读计数/端点**（grep 无实现）⇒ 本条记 **未验/缺口**，登记为补偿控制的待补项 |

**⇒ 判定**：四条中 **a、c 成立，b 成立但表述为"触发间隔由调用频率决定、无配置项"，d 未成立**。
据此：K1-2 记 **"已接受为有界已知边界"**，并**附一条明确的运维补偿缺口**（W-2-d：缺少未决孤儿计数）。**不得**把本条写成"W 已消除"或"孤儿检测已完备"。

## 4. 为什么这样表述（不得弱化）

- 机制的正确性主张只有**逐记录命题**；把它读成"系统任何时刻都没有孤儿"是**过度主张**。
- 触发是**同步、由调用驱动**的：进程空闲时不会扫描 ⇒ 检测延迟的**真实上界取决于部署调用节奏**（HTTP 请求/效果门检查/启动），运维必须知道这一点。
- 补偿控制：①外部隔离流程按既有要求执行；②运维需按调用节奏评估孤儿存留时间；③**W-2-d 缺口**（未决孤儿计数）补齐前，不得声称"运维可见性完备"。

## 5. 本轮实测（与本页相关的部分）

| 项 | 结果 |
| --- | --- |
| 恢复链 25 类批次（含 `RecoveryFence*`／`G0K1RealFileLockIT` 等） | **25 类 / 696 项 / 0 失败 / 0 错误 / 0 跳过**、`portReleased=True`（`.local/r09-a03-20260930-pinned-b`） |
| 门控 IT（与本页直接相关，已验基线的同产品 HEAD 证据） | `E2aRecoveryAdmissionEntryIT`＝`.local/e2a-cross-20260925-604` 1/0/0/0；`E2aRecoveryWriterTransactionIT`＝`.local/e2a-cross-20260925-621` 1/0/0/0 |
| 未包含 | `RecoveryRequestRootHttpIT`、`C1InitialNormalHttpIT` 不在本批与 25 类清单内（需各自环境），**未验** |

## 6. 边界声明

- 本页**不构成**生产/部署/main/发布授权；开关保持全默认 **false**；`localDeletionAllowed=false`。
- **K1 仍未通过**；本页只把 K1-2 从"未处置"变为"**已接受为有界已知边界（附 W-2-d 缺口）**"。
- 窗口 W 的原始反例、`UNCONFIRMED_NO_CROSS_PROCESS_BASIS` 与历史失败证据**全部保留**，不删除不重写。

## 7. 待办（下一步，均不需产品改动）

1. **W-2-d**：为"未决孤儿数"增加可读计数/端点（**需产品改动 ⇒ 单独授权**）；在补齐前，运维文档须按本页 §4 表述。
2. **K1-3**：补一条真实跨进程用例（可确认通道三者都不可确认 ⇒ 拒绝/外部隔离、委托增量=0）——属隔离测试，**不需要产品改动**。
3. **K1-1**：按提案 §6 用当次真实证据重建 E1–E6 事实表（只验证，不需新授权）。
