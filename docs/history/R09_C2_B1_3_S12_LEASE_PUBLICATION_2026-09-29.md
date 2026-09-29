# R09 C2 B1-3 S12：逐根租约发布与孤儿退役（设计第 10/12 行）（2026-09-29）

> 轮次身份：主树 `D:/acrylic-pda-outbound`、分支 `codex/development`；写者＝DSH 本会话；**同一会话实施＋自审＋运行，非独立第三方验收**。授权：用户 2026-09-29"继续"＝**第 10/12 行（逐根发布/探针＋逐操作退役）**。边界：仍**不开任何生产开关**，不接生产、不部署、不合 main。起点 HEAD `4364b68`（S11 收口）。

## 1. 问题（设计原文对齐）

- 第 10 行"租约发布/探针（逐记录事件语义）/前后探针"：接纳探针（S3-k）与效果门内部的后探针都要能**看到每个已接纳操作**。B1-2 有 `RecoveryLeaseScan.publish/check`，但**请求根路径从不发布**：`RecoveryOperationRegistry` 不持有 P5 权威 ⇒ 接纳过的根在 P5 里不可见。
- 第 12 行"退役协议"：设计 §5.1/§5.3 规定"后探针若发现孤儿 → **退役**该记录（失败即围栏）并拒绝"，L194 又明确"**退役只删除租约记录、绝不改进程记录**"。B1-2 的扫描**只拒绝、不退役** ⇒ 孤儿记录永远留着，下一个进程永远起不来（S9 现场就停在这一步）。

## 2. 实现（产品代码）

| 文件 | 改动 |
| --- | --- |
| `RecoveryOperationRegistry` | 新增嵌套 `LeaseChannel`（`UUID publish()`）与三参构造器；`admit(...)` 在**临界区之外**发布一条 P5 租约记录并把 id 挂到根上（`RecoveryOperationLease.leaseId()`）。发布失败 ⇒ 收回根并让**本次接纳失败**（"P5 里看不见的根不得服务"）。**初始化窗口自身的根不发布**（见 §4 环） |
| `RecoveryLeaseScan` | `check(...)` 改为：逐记录判定（§5.3 语义不变）→ 把**判定为孤儿**的记录收集起来 → `retireOrphans(...)`（删除记录＋重写锚定索引、去掉这些条目）→ 仍然拒绝 `RECOVERY_LEASE_OUTSTANDING`。**只退役存在且被判孤儿的租约记录**；`.pending` 也算孤儿（半写记录绝不采纳）；**索引有、文件无**的删除证据**不修复**（保留篡改证据）；进程记录永不退役 |
| `RecoveryLeaseProcess.alive` | 修一个真实缺陷：本 JVM 已 `retire()` 的进程记录，其缓存通道已关闭，原实现会先 `readHeld()` 抛 `ClosedChannelException` ⇒ 孤儿被判成"非孤儿"而**永不退役**。现在身份校验后若本地锁已失效即判"已死"（不再读已释放的描述符） |
| `RecoveryAdmissionConfiguration` | 注册表 bean 注入 `ObjectProvider<RecoveryFenceAuthority>`，并把它包装成**惰性**通道（接纳时解析）——见 §4 |

## 3. 验证

### 3.1 一级（新增 `RecoveryLeasePublicationTest` 7 项）

| 用例 | 钉住的性质 |
| --- | --- |
| 逐根发布 | 每个被接纳的根恰好发布一条记录，`leaseId()` 可见，且**关闭根不删除记录**（L194：只增，直到被后进程扫描退役） |
| 发布失败 | 接纳失败、无残留根、代码保留（`RECOVERY_LEASE_FENCED`） |
| 无通道 | 不发布（默认行为不变，`leaseId()==null`） |
| 孤儿退役 | 扫描判定孤儿后**删除记录＋去掉索引条目**，进程记录保留 |
| 退役后树干净 | 记录与索引皆空；同 JVM 的共享围栏信号已被本机围栏 ⇒ 后续 authority 被 `RECOVERY_LEASE_FENCED` 拒（跨进程"下一个进程能起"由子 JVM 证据负责） |
| 删除证据不修复 | 索引有、文件无 ⇒ 拒绝且**索引保留**该条目（不抹除篡改证据） |
| 半写记录 | 遗留 `.json.pending` 判孤儿并退役，绝不采纳 |

全量单测（不带 `--tests` 白名单）：**133 类 / 2843 项 / 0 失败 / 0 错误 / 1 既有无关 skip**。

### 3.2 二级 现场：跨进程发布→退役→再启动（`run6-s12-lease-publication`，全新实例 19475／应用 19458，1 用例 0 失败）

```
boot1: records=[1e768f18-3b0d-422e-abd5-e032a97e9cf5.json] index=[1e768f18-3b0d-422e-abd5-e032a97e9cf5]
child1（独立 JVM）: RECOVERY_LEASE_ORPHAN|<uuid>.json|RECOVERY_LEASE_OUTSTANDING
                   RECOVERY_LEASE_OUTSTANDING retired 1 orphan lease record(s)   ← 退出码 1（拒绝）
afterRetirement: records=[] index=[]
child2（独立 JVM）: FENCE_F3_RETIRED|lifecycle lock released on clean shutdown
                   STARTED                                                        ← 退出码 0
```

即：真实应用里被接纳的根**发布了** P5 记录（锚定索引同步）；进程退出后，**另一个进程**判定它是孤儿、**退役**了该记录、拒绝启动；树干净后，**再一个进程**成功启动 ✔。子进程用测试侧 `RecoveryLeaseStartupCheck`（只做 `validateStartup()` + `retire()`，不连数据库）。

### 3.3 二级 回归：恢复链 25 类 IT 批次

`tools/r09-recovery-it-batch.ps1`，全新专用实例 **19476**、schema＝账号：**25 类 / 696 项 / 0 失败 / 0 错误 / 0 跳过 / 0 类失败**，`NOT-COVERED` 为空，TIME_WAIT 峰值 1457（节流回落），收尾 `exit=0`、`port_listening_after=0`。本轮改了接纳路径（发布）、孤儿扫描（退役）与 `alive()` 判定，正是该批次围栏/租约对抗面的核心，必要回归。

## 4. 本轮撞到的两个环（都已修，并留下语义）

1. **启动环**：把发布放进接纳后，`recoveryInitializationWindow`（构造时接纳 `INITIALIZATION` 根）会去解析 P5 权威，而 S5 守卫刻意把 P5 bean 排在窗口**之后** ⇒ `BeanCurrentlyInCreationException`。处置：注册表 bean 的通道改为**惰性**（接纳时才 `getIfAvailable()`），且**初始化窗口自身的根不发布**——启动期访问由第 8–9 行的进程记录/围栏信号覆盖，**服务态根一律发布**。这是本轮唯一的语义收窄，已在代码注释与本节写明。
2. **同 JVM 围栏不可复位**（设计明文）：`RecoveryFenceLock` 按围栏文件身份在 JVM 内共享，本机 FENCED 一旦置位不再复位 ⇒ "下一个进程能启动"**只能**跨进程验证 ⇒ 现场用两个独立 JVM 子进程取证（§3.2），单测只钉"树已干净"。

## 5. 未做与边界

- **逐根退役事件本身**：设计只要求"退役＝删除孤儿租约记录、不改进程记录"（§5.1/L194），故干净关闭**不写**任何记录事件；本轮按此实现，未自行发明 `released` 之类事件。若后续需要"逐操作结束事件"，属设计新增，须独立授权。
- **F1–F5 序列与 `halt(70)`** 已由 S10 接上（网关 poison → `fenceUnknown()`），其逐行语义仍由批次的子 JVM 用例（`RecoveryFenceUnknownIT`、`FenceChild`）覆盖；本轮未新增该路径证据。
- 第 10 行的"后探针若发现孤儿→退役"已实现；**效果门内的逐记录扫描**（`checkEffect` → `check`）同样走退役路径 ✔（现场 child1 即经 `validateStartup`；门内路径由同一 `check` 复用）。
- 未做：Python 侧 v2 供给与 `closedAuthority` 身份（B9，需先定锚点政策）、生产级 provision 工具、C1/E2a 实验舱重跑、§5.6 终端语义细化、完整接纳门 S0–S4。
- 开关（admission／effect-gate／cross-process／lease）全部默认 false；未接生产、未部署、未合 main；K1 仍未通过（窗口 W 与 `UNCONFIRMED_NO_CROSS_PROCESS_BASIS` 原样保留）。
- 未验：跨进程并发接纳、长稳/性能、断电与硬复位、Linux/其他文件系统、现场组合、PDA 对 503 envelope 消费。

## 6. 逐轮失败保留（未删）

| 轮 | 现象 | 处置 |
| --- | --- | --- |
| `run1-s12-targeted` | 4/7 失败：①`control()` 被调用两次造出两个夹具 ⇒ `CONTROL_INSTALLATION_MISMATCH` ②孤儿未退役 ③同 JVM"第三个进程能起"实为 `RECOVERY_LEASE_FENCED` | ①夹具只取一次②**查出真实缺陷**：本 JVM 已退役的过程记录其通道已关闭，`alive()` 抛 `ClosedChannelException` ⇒ 孤儿被判非孤儿；修为"本地锁失效即已死"③按设计改写为"树干净"＋同 JVM 围栏不可复位，跨进程交由子 JVM |
| `run1/2-s12-lease-publication` | 现场启动失败：`BeanCurrentlyInCreationException`（发布与初始化窗口成环） | 通道惰性解析 ＋ 初始化窗口根不发布 |
| `run1-s12-lease-publication` | 编译失败：局部变量名 `java` 遮蔽包名 | 改名 `executable` |
| `run4-s12-lease-publication` | 子进程 1 期望失败却按成功断言 | 用例改 `expectFailure=true`（该子进程正是"拒绝"证据） |
| `run5-s12-lease-publication` | 子进程 2 输出含日志前缀，整体等值断言失败 | 改 `contains("STARTED")` |
| `run6-s12-lease-publication` | **绿**（1 用例） | 本轮二级现场证据 |
| `run7-unit-full` | **绿**（133 类 2843 项） | 一级全量证据 |

## 7. 证据位置（不入库，按约定保留）

`.local/r09-c2-b1-3-s12-20260929-a/`：`run1/run2/run3-s12-targeted`、`run1`–`run6-s12-lease-publication`（含 `lease-publication.txt`、`real-records.tsv`、失败 XML 与 `gradle.log`）、`run7-unit-full`、`it-batch-25/`；P5 夹具树保留在 `.local/control-java-cases/case-*/p5/`。实例 19475 已正常 shutdown、端口释放，datadir 保留。

## 8. 下一步

① **Python 侧 v2 供给与 `closedAuthority` 身份文件（B9）**——需先定锚点保管/轮换政策（用户裁定暂缓），它是"准入＋跨进程同开"的供给闭合件；
② **C1/E2a 实验舱重跑**（启动参数已在 S8 补齐；本轮新增的发布/退役会让这些 harness 的 P5 行为更完整）；
③ 第 12 行的终端语义细化与 §5.6（若设计需要逐操作事件）。均需独立授权/验收，不改默认开关。
