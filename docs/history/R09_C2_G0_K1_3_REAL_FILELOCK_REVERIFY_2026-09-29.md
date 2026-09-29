# R09 C2 G0-K1-3 真实跨进程 FileLock / fenced.lock / 进程终止与跨 JVM 可见性验证（2026-09-29）

> 输入基线：`274a70931829f2b45143e7b965b3715312bd654e`（`codex/development`）。
>
> ## 0. 授权前提（原样记录）
> 本轮为**独立有限授权**：仅新增测试/实验代码与文档，用于把 G0-K1-3 围栏失败矩阵中 F1′（fenced.lock 共享锁）/ F3（FileLock.release()）/ F5（第三方跨 JVM 观察）从"模型级"推进为"真实跨进程已实测"。
> - **不授权 B1**：不得在 src/main 实现效果门/fenced.lock/应用入口接线，不得改任何默认开关。
> - 不得开启 `outbound.recovery.admission.enabled`。
> - **K1 总体判定不因本轮改变**：K1-1 生产效果门未实现/未验、K1-2 窗口 W、K1-3 依条件分支三项在未真正闭合前，K1 一律记为"仍未通过"。
>
> **本轮结论**：F1′／F3／F5 的**文件锁语义**已由真实多 JVM 进程实测（46 条 `REAL|` 记录，10 场景 ×3 重复全绿）；其中 **S8/S9 与设计假设冲突，单列阻断并给出最小调整建议**（本轮不改设计结论、不改代码）。**K1 仍未通过**，**B1 产品实现仍待授权**。

## 1. 前置检查（开工前，全部留档）

| 项 | 实测 | 证据（`.local/r09-c2-g0-k1-3-filelock-20260929-a/`） |
| --- | --- | --- |
| 分支 / HEAD / 实际远端 / 领先落后 | `codex/development` / `274a709` / `git ls-remote` 同 SHA / `0/0` | `01-pre-head.txt`、`01-pre-remote.txt`、`01-pre-left-right.txt` |
| 工作区 / 锁 / `git diff --check` | status 为空、无 `.git/index.lock`、退出 0 | `01-pre-status.txt`、`01-pre-checks.txt`、`01-pre-diff-check.txt` |
| 共享树 | detached `ea4d166`、30 修改＋67 未跟踪＝97、状态哈希 `C76522C5…`（前后逐字节一致） | `01-pre-shared-tree-*.txt`、`02-post-shared-tree-*.txt` |
| 查重 | 证据目录后缀 `-a` 唯一（创建前已确认不存在）；无 `G0K1*FileLock*` 既有类；**本轮不需要数据库/端口**，未分配 attempt/SmokeRun | `01-pre-env.txt` |
| 监听（只读观察） | 19387＝DSH、3306＝系统 mysqld；19330/19434/19437/23308/19438 无监听；本轮未起任何服务 | `01-pre-listeners.txt` |
| 单写者 | 除本会话外无其他引用主树/共享树的进程 | `01-pre-writers.txt` |

## 2. 方法（真实跨进程，非 Mock）

- 新增 IT `G0K1RealFileLockIT`（10 场景 ×**3 次重复**）＋ 子 JVM 探针 `G0K1FileLockProbeMain`（JDK-only，无第三方依赖）。
- 子进程由 `ProcessBuilder` 启动为**真实独立 JVM**（`java -cp <测试类目录> …ProbeMain <mode> …`），stdout/stderr **重定向到文件、不使用管道**；父进程通过**状态文件轮询**同步，每条记录含**子进程 PID、JVM 版本、退出码、起止时间戳**。
- 记录 52 行 TSV（`filelock-records.tsv`，UTF-8）：`REAL|` 46 行＝真实实测；`MODEL|` 6 行＝既有设计/文档结论（仅对照，**不并入**真实结论）。
- 环境：`java 21.0.11`、`Windows 11`、NTFS 本地盘。

## 3. 场景结果与设计映射

| # | 场景 | 真实结果（3/3 重复一致） | 映射到设计 v5 | 判定 |
| --- | --- | --- | --- | --- |
| S1 | 同 JVM 重叠请求 | `OverlappingFileLockException`（`java.nio.channels.OverlappingFileLockException`），栈帧已记录 | L-本机层：同 JVM 内 FileLock **不重入、也不互相可见**（JVM 级锁表） | **通过（支持设计）**：同 JVM 互斥必须由内存态承担 |
| S2 | 跨 JVM 排他 | A 持排他 → B `tryLock` = `TRYLOCK-NULL`，`elapsedMs=0`（非阻塞） | §5.5 进程记录锁／探针；S13-③、S17 的“探针可判他人持锁”前提 | **通过** |
| S3 | 跨 JVM 共享锁（F1′） | A、B **同时**共享成功（`shared=true valid=true`），C 排他 = `TRYLOCK-NULL` | §5.4 X-围栏：共享锁可与其它 FENCED 进程共存，门的独占 tryLock 必失败 | **通过（F1′ 前提成立）** |
| S4 | F5 第三方可见性 | 观察者只能得 `TRYLOCK-NULL`；`FileLock` **无进程/PID 访问器**；`acquiredBy()` 返回 `java.nio.channels.Channel`（本 JVM 句柄） | §5.4 F5：第三方只能靠 tryLock 结果推断持有者 | **通过（需精确表述）** |
| S5 | 正常释放（F3） | 持锁进程 `release()` 并正常退出后，下一 JVM `TRYLOCK-ACQUIRED`（`RELEASED` 已记录） | F3 行：`FileLock.release()` 正常返回＝已确认释放 | **通过** |
| S6 | `destroyForcibly()`（≈`taskkill /F`） | 强杀后下一 JVM `TRYLOCK-ACQUIRED`；**锁文件仍存在** | §5.5 F5：终止 ⇒ OS 释放全部锁；残留文件≠持锁 | **通过** |
| S7 | `Runtime.halt(70)` | 退出码 **70**（绕过 shutdown hook）；锁被释放；锁文件仍在 | 同上（F5 最极端路径） | **通过** |
| S8 | 持锁时删除/重命名锁文件 | **均成功**：`DELETE-WHILE-LOCKED-OK deleted=true existsAfter=false lockStillValid=true`；`RENAME-WHILE-LOCKED-OK targetExists=true sourceExists=false lockStillValid=true` | 设计假定锁文件在场且身份稳定 | **矛盾（见 §4）** |
| S9 | 外部删除锁文件后新 JVM 加锁 | 新进程在**旧持锁者仍存活**时获得新文件身份的排他锁 ⇒ **同一路径两个持锁者** | 设计以 `writers.json` 的 `fileIdentity` 绑定锁文件身份 | **矛盾（见 §4，阻断）** |
| S10 | 同 JVM close 后语义 | `VALID-BEFORE-CLOSE=true` → `VALID-AFTER-CLOSE=false` → `RELOCK=ACQUIRED` | F3 行：close 使锁失效、同 JVM 可再次加锁 | **通过** |

**与本轮结果无关但必须保留的未验项**：窗口 W、F1′/F3/F5 之外的围栏分支、真实应用接线、HTTP/Session、Linux 与其他文件系统、网络/共享盘、断电/硬复位、现场组合（见 §6）。

## 4. 矛盾单列（阻断 / 需设计调整建议；本轮不改设计结论、不修代码）

**矛盾 A（S8）**：Windows/NTFS + JDK 的打开模式允许**在持有 FileLock 期间删除或重命名锁文件**（删除/重命名均成功，且原锁在已删除的文件上**仍为 valid**）。
- 影响面：任何“清理/轮转/重建锁文件”的操作都会**静默破坏互斥**；`writers.lock`、`fenced.lock` 的“文件始终在场”假设不成立。

**矛盾 B（S9，阻断）**：锁文件被外部删除后，新进程在**旧持锁者仍存活**时即可对**同一路径的新文件**取得排他锁 ⇒ **同一路径同时存在两个“持锁者”**（旧身份 + 新身份）。只按路径加锁不足以保证互斥。
- 与设计的接口：设计 v5 已要求 `writers.json` 记录 `writers.lock` 的 `fileIdentity`，并在启动阶段 0 校验；本轮实测证明**该校验是承重的、不可选**，但设计尚未把“身份不匹配 ⇒ 失败关闭且不得自动重建”写成显式强制点，也未禁止删除/重建锁文件。

**最小调整建议（供 B1 决定，本轮不实施）**：
1. `writers.lock`／`fenced.lock` 一律**只创建、不删除、不替换**（含禁止清理/轮转/自动重建）。
2. **每次获取锁之前**都必须先校验当前文件的 `fileIdentity` 与权威记录一致；不一致 ⇒ **失败关闭**（启动门/效果门均拒绝），不得“当作 stale 文件重建”。
3. 启动阶段 0 的校验从“检查文件存在”升级为“**检查身份一致**”，并把不一致明确归为阻断（不是自动修复）。
4. 为“锁文件被外部删除”增加显式审计事件与拒绝码（名称由 B1 定），使运维可见。

## 5. 追加的 B1 待实施需求（本轮不修）

**DDL 隐式提交 ⇒ close 期 rollback 假设**（源自 [G0-K1-1 真实驱动验证](R09_C2_G0_K1_REAL_JDBC_REVERIFY_2026-09-29.md) 实测：`autoCommit=false` 下的 DDL 会隐式提交未完成事务，且后续 `rollback()` 无法撤销）：B1 实施效果门/连接收尾时，**不得假设“未显式 commit ⇒ close 期 rollback 能撤销”**；DDL 类调用必须按提交类处理并过门。**属 B1 范围，本轮不修**，已登记入设计 §5.4 追加段与欠债 C2。

## 6. 运行与证据

| 层 | 命令 | 退出码 | 结果 |
| --- | --- | --- | --- |
| 首次运行 | `gradlew -p server integrationTest --tests 'com.acrylic.outbound.recovery.G0K1RealFileLockIT' --rerun-tasks --no-daemon --console=plain --info` | **1** | 编译错误（探针 `catch` 块内调用抛检查异常的方法）——失败轮保留 |
| 第二次 | 同上 | **1** | `S4` 断言过粗（未计入 JDK 10+ 的 `acquiredBy()`）——失败轮保留 |
| 第三次 | 同上 | **1** | `S4` 断言返回类型错误（实为 `java.nio.channels.Channel`）——失败轮保留 |
| 第四次（最终） | 同上 | **0** | **10 项 / 0 失败 / 0 跳过**（47.96s），52 行 TSV 记录（REAL 46 / MODEL 6） |

证据目录 `.local/r09-c2-g0-k1-3-filelock-20260929-a/`（不入库）：`01-pre-*`（Git/工作区/共享树/监听/写者/查重）、`filelock-records.tsv`（汇总 TSV）、`filelock-work/`（锁文件与探针状态文件）、`probe-logs/`（每个子进程的 stdout/stderr 原始重定向文件）、`probe-commands.txt`（逐条子进程命令行，含 PID 归属可回溯）、`run1-it`–`run4-it`（逐轮 gradle 日志、退出码、JUnit XML、记录抽取）、`02-post-shared-tree-status.txt`（与前置逐字节一致）。

## 7. 未验边界（原样保留，不得被本轮通过覆盖）

本轮**不**验证：B1 生产效果门及其应用入口接线、HTTP/Session、窗口 W、F1′/F3/F5 之外的围栏分支、**Linux 与其他文件系统**（NTFS 之外）、网络/共享盘上的锁语义、**断电与硬复位**、现场组合、Java/Python 允许集差异、C1 历史未验项、孤儿解除与生产释放、Session 默认清理。
**K1 仍未通过**：K1-1 生产效果门未实现/未验、K1-2 窗口 W、K1-3 依条件分支；**B1 产品实现仍待授权**。

## 8. 停止点

完成真实跨进程实测与留档即停止：**不进入 B1、不改 `src/main`、不修设计结论**。§4 的矛盾已单列阻断与最小调整建议，等待下一轮决定。
