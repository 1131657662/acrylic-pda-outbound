# R09 C2 B1-2 对抗性复核（2026-09-29）

输入：`D:/acrylic-pda-outbound`，`codex/development`，`8c723015e67cfe31af1819cefb254e211b45bdaf`。

**口径（必须原样保留）**：本轮由 **DSH 在同一会话内实施、自审并运行**，**不是独立第三方验收**；起点含 Codex 会话被模型中转商按会话屏蔽（403）时留下的 4 项在途改动，其中 2 项被采纳、2 项被修正。600 签名判定表**无新增/变更行**。

**结论：三处覆盖缺口被证实；两处已修并验证，第三处无认证锚点、如实登记为未解决。** 最终：定向单测 **2038/0**、并集全量 IT **662/0**（22 套）。默认关闭、未接线、未部署、未开生产开关；**K1 仍未通过**、**未进入 B1-3**。

## 1. 授权与边界

仅 `recovery` 包内必要修正、`recovery` 测试与文档；不注册/替换 DataSource，不接 HTTP/Session 或请求根，不做退役/孤儿解除/生产释放；两个开关保持默认 false；共享树只读；不合 main、不发布、不操作原库/现网；`localDeletionAllowed=false`。

## 2. 三处缺口、证据与处置

| 编号 | 缺口与真实影响（实测） | 处置 |
| --- | --- | --- |
| **H1** | `RecoveryLeaseProcess.alive()` 的**本机缓存按路径索引、从不比对缓存身份**。把 `processes/<id>.json` 移走再复制回同路径（新文件、无人持锁），并把租约的 `processFileIdentity` 改成新身份后，缓存以旧描述符作证 ⇒ 判"属主存活"⇒ **放行**（基线 run2：`delegate=1 observer=1`，3/3）。这违反已发布声明"锁可取 ⇒ OUTSTANDING"（当前路径上的锁确实可取）。 | **已修**：`alive()` 在缓存命中时要求 `local.nativeIdentity` 等于当前文件身份。修复后 `local-owner-identity-rebinding` **3/3 拒绝**（`RECOVERY_LEASE_OUTSTANDING`，`delegateDelta=0 observerDelta=0`），且攻击构造仍成立（`newLockAvailable=true`）。 |
| **H2** | `RecoveryLeaseScan` 原按**文件名**跳过 `*.unknown.json(.pending)` ⇒ 把孤儿租约改名即可让扫描跳过它、效果被放行（run4：`ADMITTED_SECURITY_GAP`，`delegate=1 observer=1`，6/6）。 | **已修**：仅当**同前缀租约仍存在**、且内容确为审计形态（有 `operationId/event/at`、无 `processFileIdentity/processSha256`）时才跳过；否则按租约处理。修复后 unknown/unknown-pending 改名 **6/6 拒绝**。 |
| **H3** | 孤儿租约的 `processIdentity/processFileIdentity/processSha256` 可被改写成**活属主**的值（摘要自洽）⇒ 孤儿隐形、效果被放行（run4：3/3 `ADMITTED_SECURITY_GAP`）。 | **已由认证锚点闭合**（同日补做，见 [P5 认证锚点补充规格](R09_C2_B1_2_ANCHOR_SPEC_2026-09-29.md)）：显式供给的 32 字节秘密绑定 `anchorId`，进程/租约记录均带 `anchorTag` 并在读取时校验，跨进程开启而锚点缺失即启动失败。用例翻转为 `orphan-live-owner-anchored` 后 **3/3 拒绝**（`delegateDelta=0 observerDelta=0`）。**仍未闭合**：**H3′＝直接删除租约记录**（需锚定操作索引），继续单列 `DEBT-R09-C2-LEASE-ANCHOR`。 |

**另证（保持通过）**：12 条 payload 变异（truncate/extend/rewrite/move）全部拒绝且 `lifecycleLockStillHeld=true`（1GiB 生命周期锁不受载荷改写影响）。

## 3. 探针与自审缺陷（如实记录，非产品行为）

1. Codex 版伪装用例同时起**两个** `adversarial-owner` 子 JVM，而 `validateStartup()/registerOperation()` 全程抢**排他协调锁**（`RecoveryControlLock` 为 `tryAcquire` 非阻塞）⇒ 抢占失败者在打印 `OWNER` 前即抛，12 条报 `Missing OWNER|operation=`（run2）。**与产品行为无关。** 改为"一个死孤儿 + 复用父进程活属主"后消失。
2. 我（DSH）首次改造引入新缺陷：在记录行对**父进程自己的进程记录**调用 `lockAvailable()`，同 JVM 再取同一把 1GiB 锁 ⇒ `OverlappingFileLockException`，9 条被挡在断言之前（run3）。
3. 我的 H2 修法首版**过紧**：要求审计标记内容可解析，导致 B1-2 已发布用例 `ownLiveRecordsAllowEffectsAndAuditAloneIsNotABasis`（写 `"audit only"` 的无主标记）失败（run8：662/3）。按"不得为让新用例通过而放宽断言"，采取**收紧而非放宽**的方式更新该用例：标记改为与真实租约成对、并用产品自身的 `canonical` 写法构造（run9 仍红因手写 JSON 非规范形态），并**新增**"无租约的标记不可忽略"断言。
4. **教训**：本轮先用 `RecoveryEffect*IT` 过滤器跑全量（536/0），漏掉 `RecoveryFence*IT`——而改动恰好落在 Fence/Lease 逻辑；改用 `RecoveryEffect*IT|RecoveryFence*IT` 并集后才暴露 run8/run9 的问题。**覆盖过滤器本身也是被验证对象。**
5. **我在收敛看板时把生成事实块一并删掉了**：事实块位于 `CURRENT_STATE_START/END` 之内，我整段替换当前区时漏掉它。`version_facts.ps1 -Check` 立即失败（"no FACTS_START/END markers"），说明该守卫有效；已恢复标记、重跑 `-Write`（`head=12f6e01`、`dirty_entries=1`）与 `-Check`（当前区 **39 行 / 2490 字符**通过），并以修正提交补回。**教训：重写看板当前区必须先保留事实块标记。**

## 4. 逐轮运行

| 轮 | 执行者 | 退出码 | 结果 |
| --- | --- | --- | --- |
| run1-baseline-durable | Codex | 1 | Gradle 参数错误，未编译、无 XML（保留） |
| run2-baseline-durable | Codex | 1 | 24 项 / 12 失败：9 条探针缺陷 + **3 条 H1 真信号**（`delegate=1 observer=1`） |
| run3-fixed-durable | DSH | 1 | 24 项 / 9 失败：我的 `lockAvailable` 自锁缺陷，H2/H3 尚未触达 |
| run4-fixed-durable | DSH | 1 | 24 项 / 9 失败：**H2/H3 全部 `ADMITTED_SECURITY_GAP`**（真缺口证实） |
| run5-verify-durable | DSH | 0 | 24 项 / 0 失败：H1+H2 修复生效，H3 转为登记的 declared-gap |
| run6-full-unit | DSH | 0 | 定向单测 **2038 / 0**（与 B1-2 基线一致，无回退） |
| run7-full-it | DSH | 0 | 536 / 0（**过滤器漏 Fence 侧**，仅过程证据） |
| run8-full-it | DSH | 1 | 662 / 3：并集暴露我的过紧修法破坏已发布用例 |
| run9-full-it | DSH | 1 | 662 / 3：标记非规范形态 ⇒ 不可读 |
| **run10-full-it** | DSH | **0** | **662 / 0 / 0**（22 套）最终并集回归 |
| run11-anchor-durable | DSH | 1 | 连接失败：我上一轮已收尾关闭 19442（非产品问题；已按既有做法在同一保留 datadir 重启并核对身份） |
| run12-anchor-durable | DSH | 0 | 24 / 0：**锚点生效，H3 用例翻转为必须拒绝**（orphan-live-owner-anchored 3/3 拒绝） |
| run13-full-unit | DSH | 0 | 2038 / 0（锚点改动后无回退） |
| run14-full-it | DSH | 1 | 662 / 3：新守卫（跨进程缺锚点即启动失败）使 RecoveryFenceStartupIT 需补锚点属性——**预期收紧** |
| **run15-full-it** | DSH | **0** | **665 / 0 / 0**（22 套）最终并集回归；含新增 crossProcessWithoutAnchorMustFailStartup |

## 5. 环境、证据与哈希

- 隔离实例：端口 **19442**、datadir `.local/mysql19442-b12-adversarial/data`、schema/账号 `b12_adv_20260929`（承接同轮中断环境，未新建）；收尾正常 shutdown、端口释放，datadir 与失败证据保留。
- 证据根：`.local/r09-c2-b1-2-adversarial-20260929-a/`（不入库）：Codex 前置快照与 `provision.ps1`、`run.ps1`、`run1`–`run10`（逐轮 `command.txt`/`gradle.log`/`exit.txt`/JUnit XML/`results.json`/`files.sha256`）、`real-records.tsv` 与逐子 JVM `fence-*/child-*` 回执、`RESUME-DSH-handover.md`、最终 Git/环境回执。
- 产品改动面：`RecoveryLeaseProcess.java`（H1）、`RecoveryLeaseScan.java`（H2）；测试改动面：`RecoveryFenceAdversarialDurableIT.java`（新增）、`RecoveryFenceIntegrityIT.java`（收紧一条已发布用例并新增断言）、以及 Codex 在途的 `EffectRealEnvironment.java`/`RecoveryFenceChildMain.java`/`FenceAdversarialEvidence.java`。

## 6. 未验边界与停止点

**窗口 W**（原证明保留、未消除）、**`DEBT-R09-C2-LEASE-ANCHOR`（租约/进程记录无认证锚点，实测可放行）**、`UNCONFIRMED_NO_CROSS_PROCESS_BASIS`（须外部隔离）、HTTP/Session 接线、生产默认 Session 清理、孤儿解除与生产释放、现场组合、Linux 与其他文件系统、网络/共享盘、断电与硬复位、Java/Python 允许集差异、C1 历史未验项、包装启用前外部取得的物理句柄、已开始执行的命令不可撤销。未运行全仓完整测试/构建、APK、模拟器、真机或真实 HTTP/Session 组合。

**默认关闭、未接线、未部署、未开生产开关；K1 仍未通过；未进入 B1-3。** 不合 main、不操作原库/现网、不修改共享树；本轮完成即停。

## 8. 锚点补做（同日，方案 A 记录锚点部分）

- 实施：RecoveryControlFormat（hmac/anchorTag/hex/bytes/sha256）、RecoveryLeaseRoots（32 字节秘密绑定 nchorId，erify 不一致即 CONTROL_ANCHOR_MISMATCH）、RecoveryLeaseProcess/RecoveryLeaseScan（记录带 nchorTag 并在读取时校验）、RecoveryEffectConfiguration（新属性 outbound.recovery.effect-gate.anchor；**跨进程开启而锚点缺失或形状不符 ⇒ CONTROL_ANCHOR_REQUIRED 启动失败**）。
- 验证：un12 H3 用例翻转 3/3 拒绝；un13 单测 2038/0；un15 并集 IT 665/0/0；新增 crossProcessWithoutAnchorMustFailStartup（3/3）。
- **仍未闭合**：**H3′＝直接删除租约记录**（需"锚定的操作索引"，规格见 [P5 认证锚点补充规格](R09_C2_B1_2_ANCHOR_SPEC_2026-09-29.md) §3.2）⇒ DEBT-R09-C2-LEASE-ANCHOR 继续单列。锚点的强度等于**配置来源**的强度；秘密保管/轮换属运维政策，需与七类生产决定同级授权。

## 7. 后续（B1-3 之前必须先决定）

1. **认证锚点**：为 P5 租约/进程记录引入安装级秘密（HMAC）或外部权威，否则 F5/孤儿扫描**不得**被当作防伪依据；在锚点落地前，`DEBT-R09-C2-LEASE-ANCHOR` 与窗口 W 并列单列。 实现规格与决定项见 [P5 认证锚点补充规格](R09_C2_B1_2_ANCHOR_SPEC_2026-09-29.md)（方案 A：锚点＋锚定索引；方案 B：书面确认该威胁不在模型内）。
2. 若决定"P5 目录被改写不在威胁模型内"，需以用户书面决定记录，并同步修订设计对 F5 的表述；不得默认视为已解决。
3. 其后才是 B1-3（HTTP/Session 与请求根接线）等切片，各自独立授权与验收。
