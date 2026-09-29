# R09 C2 B1-3 S10：效果门接入真实 JDBC 入口（设计第 11 行）（2026-09-29）

> 轮次身份：主树 `D:/acrylic-pda-outbound`、分支 `codex/development`；写者＝DSH 本会话；**同一会话实施＋自审＋运行，非独立第三方验收**。授权：用户 2026-09-29"继续"＝**效果门接入真实 JDBC 入口（第 11 行）**。边界：仍**不开任何生产开关**，不接生产、不部署、不合 main。起点 HEAD `0e5638d`（S9 收口）。

## 1. 问题

第 11 行要求"效果门冻结全部 JDBC 效果入口，无豁免"，点名两处真实入口：`RecoveryConnectionHandler.invoke`（`commit`/`setAutoCommit` 分支，委托前的本机层＋跨进程层）与 `RecoveryJdbcObjectHandler.invoke`（子对象级委托前门；SQL 归类只读白名单）。

基座早已实现（含 `useEffectGate(gate)`、`beforeConnectionEffect`、`beforeEffect` 与 `RecoveryEffectSql` 的**小而严**文法），旧连接级适配器也早已接线；**缺的是请求根路径的挂载**：`RecoveryRequestAdmittedDataSource` 构造 handler 时从不调用 `useEffectGate` ⇒ 门恒为 `disabled()`，开关打开也不冻结任何效果。

## 2. 实现（产品代码，两处小改）

| 文件 | 改动 |
| --- | --- |
| `RecoveryRequestAdmittedDataSource` | 新增 `(delegate, operations, effects)` 构造器；`admit(...)` 在建好 handler 后调用 `handler.useEffectGate(effects)` ⇒ 本机层（`gate.fence()` 后的本地拒绝）与跨进程层（authority 的 `checkEffect()`：探针＋孤儿扫描）都在**委托前**生效。旧构造器保持 `RecoveryEffectGate.disabled()`，故未 opt-in 的装配行为完全不变 |
| `RecoveryAdmissionConfiguration` | 主数据源 bean 注入 `ObjectProvider<RecoveryEffectGate>`，取到即挂（`getIfAvailable(RecoveryEffectGate::disabled)`）：开关默认 false ⇒ 门不存在／为 disabled，行为与之前一致；开启后由 `RecoveryEffectConfiguration` 构造的真实 gate（跨进程时即 S9 的 `RecoveryFenceAuthority`）被挂到应用自己的主数据源上 |

## 3. 验证

### 3.1 一级（新增 `RecoveryRequestAdmittedDataSourceGateTest` 5 项）

| 用例 | 钉住的性质 |
| --- | --- |
| 挂门后逐效果检查 | `commit` 与写语句各一次检查、且都在**委托前**（单测对这两项钉为恰好 2 次） |
| 被拒效果到不了驱动 | authority 拒绝时 `commit` 抛码且 **never** 代达到驱动 |
| 只读白名单的严格性 | 常量读 `SELECT 1` = `READ`（不检查）；**关系读** `SELECT counter FROM …` = `SOURCE_READ`（**照查**，因为视图/存储函数可能写）；写语句、未识别 SQL（`VACUUM …` ⇒ `CONTROL_EFFECT_UNCLASSIFIED`）一律拒绝；预备语句按自身 SQL 在执行时归类 |
| 未挂门 | 默认装配（`disabled()`）下同样的效果不被检查 |
| 本机层先行 | `gate.fence()` 后本地拒绝，**不**调用 authority（`checks==0`） |

另新增 `RecoveryIdentityFormatConflictTest` 3 项，把本轮现场撞到的身份格式冲突**测出来并钉住**（见 §4）。全量单测（不带 `--tests` 白名单）：**132 类 / 2833 项 / 0 失败 / 0 错误 / 1 既有无关 skip**。

### 3.2 二级 现场：真实入口挂门（`run4-s10-effect-gate`，全新实例 19471／应用 19458，1 用例 0 失败）

```
unfenced: constant read ok, write ok, commit ok, counter=1 checks=4
fenced: write refused, relation read refused, commit refused, constant read ok
counterAfterFence=1 (unchanged, the driver never saw the refused effects)
```

即在**真实应用**（`admission.enabled=true`、真实隔离库、`effect-gate.enabled=true`）里：应用自己的主数据源（`RecoveryRequestAdmittedDataSource`）挂上了门，未围栏时真实写入与提交成功（`counter=1`）；authority 一旦拒绝，**写、关系读、提交**全部被拒（`RECOVERY_LEASE_FENCED`），而**常量读仍可用**，且被拒效果**没有落到驱动**（计数保持 1）。

### 3.3 二级 回归：恢复链 25 类 IT 批次

`tools/r09-recovery-it-batch.ps1`，全新专用实例 **19472**、schema＝账号：**25 类 / 696 项 / 0 失败 / 0 错误 / 0 跳过 / 0 类失败**，`NOT-COVERED` 为空，TIME_WAIT 峰值 1432（节流回落），收尾 `exit=0`、`port_listening_after=0`。该批次含效果门与围栏的对抗面（`RecoveryEffectAdversarial*`、`RecoveryFence*`、`G0K1*`），是本轮改动（挂门路径）的必要回归。

## 4. 发现（必须记录）：准入层与跨进程 P5 层**身份字段集互斥**

本轮想用"准入＋跨进程 lease＋效果门"整套 opt-in 启一个真实应用，启动被拒：

```
Error creating bean 'recoveryOperationRegistry': … Factory method threw exception with message: CONTROL_FIELDS
```

逐项测出（`RecoveryIdentityFormatConflictTest`）：

| 层 | 对 `identity.json` 的要求 | 结果 |
| --- | --- | --- |
| 准入（`RecoveryOperationRegistry` → 权威加载按 purpose 校验） | **v1 字段集** | v1 通过；带 v2 追加字段（`authorityKind`/`closedAuthority`/`leaseRoot`，`formatVersion=2`）⇒ **`CONTROL_FIELDS`** |
| P5（`RecoveryLeaseRoots.provision/verify`） | **v2 字段集**（`authorityKind` 等） | v1 身份 ⇒ **`CONTROL_FIELDS`**（字段集校验先于版本校验） |

⇒ **两层的字段集校验都是严格的、且互为超集/子集关系**，因此今天**无法在同一个上下文里同时开启准入与跨进程 P5**；这不是配置错误，而是第 6/7 行（v2 初始权威形状，B9）尚未落地留下的缺口，现已用两个用例从两侧钉住（避免以后误以为"多层可以一起开"）。

本轮的现场证据因此拆成两条、互不掩盖：**真实 P5 authority 的挂载**由 S9 的 `RecoveryLeaseWiringRealIT` 举证（`gate enabled, checkEffect=ok`，真实 `fenced.lock`），**真实入口挂门与冻结**由本轮 `RecoveryEffectGateWiringRealIT` 举证（其 authority 是测试侧实现，已在用例注释与本节写明理由）。**"跨进程效果门在生产形态下落库生效"仍未验**，且被上述冲突阻塞。

## 5. 未做与边界

- **第 10 行逐根发布/探针、第 12 行逐操作退役**：`RecoveryOperationRegistry.admit` 仍未调用 `authority.registerOperation()`，根关闭也未写退役/unknown 记录（S9 只证明"权威能发布、孤儿能被拒"）。
- **跨进程效果门未被真实 P5 authority 覆盖**（被 §4 冲突阻塞）；跨进程第二 JVM 的新装配、长稳与性能未验。
- **锚点政策仍暂缓**（用户 2026-09-29 裁定）；不得把 F5/孤儿扫描当防伪依据。
- 开关（admission／effect-gate／cross-process／lease）全部默认 false；未接生产、未部署、未合 main；K1 仍未通过。
- 现场 `checks=4`（单测把 `commit`＋写语句钉为 2）：差额未逐项归因，仅作观测记录，未用作判据。

## 6. 逐轮失败保留（未删）

| 轮 | 现象 | 处置 |
| --- | --- | --- |
| `run6-s10-targeted` | 5/5 失败 `CONTROL_WRITER_PROTOCOL_REQUIRED` | 测试夹具改用 `RecoveryWriterLeaseTest.protocol()`（写者协议就绪的夹具） |
| `run7-s10-targeted` | 3/5 失败：`checks==0` 或"未抛异常" | 用例里忘建 enabled 门与挂门的 adapter，补上 |
| `run8-s10-targeted` | 编译失败（我批量替换把 `var gate/var adapter` 误插到无 `authority` 的用例里） | 修正该用例，改回默认 adapter |
| `run9-s10-targeted` | 1/5 失败：`a read is not an effect` 期望 0 实为 1 | 查证 `RecoveryEffectSql`：关系读是 `SOURCE_READ`（**照查**，视图可写），只有常量读是 `READ`；用例按真实语义改写 |
| `run10-s10-targeted` | **绿**（5/5） | 一级定向证据 |
| `run1-s10-effect-gate` | 现场启动失败 `CONTROL_FIELDS` | 逐项测出 §4 的身份字段集互斥；现场证据按可达成组合拆分 |
| `run2/run3-s10-effect-gate` | 编译失败（`\n` 被字面写入 Java 源；随后真实 MySQL 拒 `commit` when autocommit=true） | 修源码换行；用例改成先 `setAutoCommit(false)` |
| `run4-s10-effect-gate` | **绿**（1 用例） | 本轮二级现场证据 |
| `run5-unit-full` | **绿**（132 类 2833 项） | 一级全量证据 |

## 7. 证据位置（不入库，按约定保留）

`.local/r09-c2-b1-3-s10-20260929-a/`：`run1–run4-s10-effect-gate`（含 `effect-gate-wiring.txt`、`real-records.tsv`、失败 XML）、`run6–run10-s10-targeted`、`run2-s10-targeted`（身份冲突确认）、`run5-unit-full`、`it-batch-25/`。实例 19471 已正常 shutdown、端口释放，datadir 保留。

## 8. 下一步

① **第 10/12 行**：逐根发布/探针与逐操作退役接入 `RecoveryOperationRegistry.admit` / `RecoveryOperationLease.close()`；
② **v2 初始权威供给（第 6/7 行、B9）**——**优先级上升**：它现在不仅卡着跨进程效果门的落地，还卡着"准入＋跨进程同开"的整套 opt-in；需先定锚点政策（保管/轮换）再实现供给方；
③ C1/E2a 实验舱重跑。均需独立授权/验收，不改默认开关。
