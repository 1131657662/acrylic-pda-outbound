# R09 C2 B1-3 S11：v2 初始权威形状与显式供给（设计第 6–8 行）（2026-09-29）

> 轮次身份：主树 `D:/acrylic-pda-outbound`、分支 `codex/development`；写者＝DSH 本会话；**同一会话实施＋自审＋运行，非独立第三方验收**。授权：用户 2026-09-29"继续"＝**v2 初始权威供给**（S10 记录列为"优先级上升"）。边界：仍**不开任何生产开关**，不接生产、不部署、不合 main。起点 HEAD `5e3b6f8`（S10 收口）。

## 1. 问题（S10 现场实测的阻塞）

S10 现场发现：**准入层与跨进程 P5 层的身份字段集互斥**——准入的权威加载要求 `identity.json` 的 **v1 字段集**（多出 `authorityKind/closedAuthority/leaseRoot` 即 `CONTROL_FIELDS`），而 P5 `RecoveryLeaseRoots.provision/verify` 要求 **v2 字段集**（v1 身份同样 `CONTROL_FIELDS`）⇒ 生产形态下 **准入＋跨进程无法同开**，"跨进程效果门落库生效"无法验证。

设计第 6 行（"初次权威形状与 P1 绑定 | `RecoveryInitialAuthority.validate(...)`"）正是这一环，但此前**未实现**。

## 2. 实现（产品代码）

| 文件 | 改动 |
| --- | --- |
| `RecoveryInitialAuthority`（新） | **版本感知**的初始权威形状校验：**v1**＝`formatVersion/installationId/restoreRoots/initialCommandSha256`（旧控制链形状，不带 kind，由 purpose 决定是否允许缺席）；**v2**＝v1 字段＋`authorityKind/closedAuthority/leaseRoot`（`INITIAL_NORMAL_INSTALL`，P5 绑定所需）。两版的字段集仍然**精确匹配**（多一个/少一个字段一律 `CONTROL_FIELDS`），`leaseRoot`/`closedAuthority` 必须是**绝对且非空且互不相同**的路径，未实现的版本一律 `CONTROL_FORMAT_UNSUPPORTED`（绝不当作 v1 读）。**kind 归 purpose 管**（此校验不碰 kind 规则） |
| `RecoveryControlRecords.identity` | 委托给该校验（唯一入口不变）⇒ 准入路径从此接受 v2 初始权威，v1 行为逐字不变 |

## 3. 验证

### 3.1 一级（新增 `RecoveryInitialAuthorityTest` 6 项，替换 S10 的冲突用例）

| 用例 | 钉住的性质 |
| --- | --- |
| 旧 v1 身份 | 准入照旧接受（回归） |
| 供给后的 v2 身份 | 准入**接受**（冲突消解）；`authorityKind=INITIAL_NORMAL_INSTALL`、marker 64 位、P5 树存在 |
| v2 带外来 kind | 被 purpose 拒 `CONTROL_AUTHORITY_PURPOSE_FORBIDDEN`（各入口保留自己的码） |
| v2 缺 P5 路径 | `CONTROL_FIELDS` |
| 未知字段／未实现版本 | `CONTROL_FIELDS`／`CONTROL_FORMAT_UNSUPPORTED` |
| P5 供给 | 旧 v1 身份被拒（`CONTROL_FIELDS`）；供给出的 v2 被接受 |

全量单测（不带 `--tests` 白名单）：**132 类 / 2836 项 / 0 失败 / 0 错误 / 1 既有无关 skip**。

### 3.2 二级 现场：生产形态整套 opt-in（`run1-s11-real-authority`，全新实例 19473／应用 19458，1 用例 0 失败）

```
provision: identitySha256=2868f6b9…80ed markerSha256=be85628c…ec94
unfenced: constant read ok, write ok, commit ok, counter=1
fenced: write refused, relation read refused, commit refused, constant read ok
counterAfterFence=1 (unchanged, the driver never saw the refused effects)
```

这一次是**真实应用＋真实隔离库＋真实 P5 authority**（`admission.enabled=true`、`lease.enabled=true`、`effect-gate.enabled=true`、`cross-process-enabled=true`，身份为供给出的 v2 形状、`fenced.lock` 为真实文件锁）：未围栏时真实写入与提交成功；`authority.fenceLocal()` 之后**写、关系读、提交**全部被拒 `RECOVERY_LEASE_FENCED`，常量读仍可用，且被拒效果**没有落到驱动**（计数不变）。⇒ **S10 遗留的"跨进程效果门在生产形态下落库生效"缺口就此闭合**。

### 3.3 二级 回归：恢复链 25 类 IT 批次

`tools/r09-recovery-it-batch.ps1`，全新专用实例 **19474**、schema＝账号：**25 类 / 696 项 / 0 失败 / 0 错误 / 0 跳过 / 0 类失败**，`NOT-COVERED` 为空，TIME_WAIT 峰值 1478（节流回落），收尾 `exit=0`、`port_listening_after=0`。本轮改了**权威加载**（身份形状校验）这条所有恢复链入口的公共路径，故该批次是必要回归。

## 4. 供给协议与未定项

- 新增**显式供给方** `RecoveryInitialAuthorityProvision`（测试/离线作用域）：把 v1 权威**就地提升**为 v2 初始权威（补 `authorityKind/closedAuthority/leaseRoot`、`formatVersion=2`，`initialCommandSha256` 与已关闭链保持不变），随后 `RecoveryLeaseRoots.provision` 建 P5 树并返回 `identitySha256/markerSha256/anchor`。已有 kind 的权威**不会被悄悄改形**；`leaseRoot` 必须尚不存在。
- **顺序有语义**（本轮被单测抓到）：写者协议（`writers.json`）把身份摘要钉住，所以**必须先提升身份、再钉写者协议**；提升一个已运行/已钉协议的安装会被拒 `CONTROL_WRITER_PROTOCOL_MISMATCH`，属正确的 fail-closed。
- **锚点保管/轮换仍未定**（用户 2026-09-29 裁定）：供给方以显式传入/随机生成的锚点工作，只有 `anchorId` 进可写树；生产级 provision 工具与 custody 政策一并留待裁定后的批次。
- **Python 侧供给仍是 v1**（`docs/parallel/R09/ops/control_paths.ControlLocation.identity()`）：v2 形状目前由 Java 侧供给方写入；Python 与 `closedAuthority` 身份文件同属 B9，未做。

## 5. 未做与边界

- **第 10 行逐根发布/探针、第 12 行逐操作退役**：`RecoveryOperationRegistry.admit` 仍未调用 `authority.registerOperation()`，根关闭也未写退役/unknown 记录。
- 第 12 行的 F1–F5 序列与 `halt(70)` 路径未接请求根；本轮只用了安全的**本机层** `fenceLocal()`。
- 开关（admission／effect-gate／cross-process／lease）全部默认 false；未接生产、未部署、未合 main；K1 仍未通过。
- 未验：C1/E2a 实验舱重跑、Python 侧 v2 供给、跨进程第二 JVM 的新装配、长稳/性能、断电与硬复位、现场组合。

## 6. 逐轮失败保留（未删）

| 轮 | 现象 | 处置 |
| --- | --- | --- |
| `run1-s11-targeted` | 3/6 失败：①kind 码期望写成了 P5 入口的 `CONTROL_AUTHORITY_KIND_FORBIDDEN`（准入入口实为 `CONTROL_AUTHORITY_PURPOSE_FORBIDDEN`）②供给后写者协议错配 `CONTROL_WRITER_PROTOCOL_MISMATCH`（提升在协议之后）③手工权威缺 `coordinator.lock` ⇒ `CONTROL_FILE_UNAVAILABLE` | ①改期望（各入口保留自己的码是设计明文）②给写者夹具加 `protocol(Reshape)` 钩子，**先提升身份再钉协议**③补 `coordinator.lock`、用 `toRealPath()` 路径 |
| `run2-s11-targeted` | 1/6 失败：仍缺 `coordinator.lock` | 补上后 **6/6 绿** |
| `run3-s11-targeted` | **绿**（6/6） | 一级定向证据 |
| `run1-s11-real-authority` | **绿**（1 用例，生产形态整套 opt-in 首次启动成功） | 本轮二级现场证据 |
| `run2-unit-full` | **绿**（132 类 2836 项） | 一级全量证据 |

## 7. 证据位置（不入库，按约定保留）

`.local/r09-c2-b1-3-s11-20260929-a/`：`run1/run2/run3-s11-targeted`、`run1-s11-real-authority`（含 `effect-gate-wiring.txt`、`real-records.tsv`）、`run2-unit-full`、`it-batch-25/`；P5 夹具树保留在 `.local/control-java-cases/case-*/p5/`。实例 19473 已正常 shutdown、端口释放，datadir 保留。

## 8. 下一步

① **第 10/12 行**：逐根发布/探针（`registry.admit` → `authority.registerOperation()`）与逐操作退役（根关闭写退役/unknown，含 unknown 时的 F1–F5 与 `halt`）；
② **Python 侧 v2 供给与 `closedAuthority` 身份文件（B9）**——需先定锚点保管/轮换政策；
③ C1/E2a 实验舱重跑（其启动参数已在 S8 补齐）。均需独立授权/验收，不改默认开关。
