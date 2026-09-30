# R09 C2 W-2-d：未决孤儿计数与定时器设计（授权范围＝可见性＋配置项）（2026-09-30）

> **裁定依据**：用户 2026-09-30 授权"新增计数器/查询与同事务读取"，并要求一并决定**是否加定时器**、确认**不放宽任何 fail-closed 判断、不动围栏语义**；边界＝**只加可见性＋配置项，不改拒绝/放行行为**。
>
> **本页是设计（未实施）**：给出建议方案、配置项设计、改动面清单与验收口径，供逐子项授权后实施。

## 1. 先厘清 W-2-d 缺口的真实形状（读源码后修正）

原表述"未决孤儿计数未实现，窗口 W＝两次触发之间的间隔"需要两处**精确化**：

| 事实 | 源码位置 | 含义 |
| --- | --- | --- |
| 孤儿**在发现它的那一次 `check()` 内就被同步退休** | `RecoveryLeaseScan:51-65`：`retirable(...)` → 收集 → `retireOrphans(...)` → **抛** `RECOVERY_LEASE_OUTSTANDING`（fail-closed） | 孤儿**不残留**；"未决"只存在于该次调用之内 |
| 因此**拒绝是同步的、不依赖定时器** | 同上 | ⇒ **加定时器不是为了 fail-closed**（安全性已成立），而是为了**运维可见性与 RPO** |
| 窗口 W 的真实含义 | `RecoveryFenceAuthority:20-48`：`check()` 只在 `validateStartup`／`checkEffect`／`registerOperation` 被调用 | W ＝ **两次受保护调用之间的间隔**；期间**不产生 DB 写入**，所以 W 是**恢复延迟**而非数据风险窗口 |
| 触及三类数据 | `processes/<pid>.json`、`leases/<uuid>.json`、`leases/index.json` | 计数只应**读**这三处，不写入 |

**另一处精确化**：`retireOrphans` 一次可退休 **N 条**（`orphans` 列表），所以"1 条"与"N 条"应分别计数，否则运维看不出批量退化。

## 2. 建议方案（三层，逐层可独立授权）

### 第 1 层（**建议立即做**）：同事务计数 ＋ 结构化指标输出

- **机制**：在 `RecoveryLeaseScan.check()` **同一次扫描内**（同一把 `RecoveryControlLock` 持有期内）统计：
  - `orphansObserved`：本次判定为孤儿的条数（进入 `retirable` 分支的数量）；
  - `orphansRetired`：本次成功退休的条数（`retireOrphans` 实际删除并改写索引的数量）；
  - `orphansRefused`：因**不可退休**而抛 `RECOVERY_LEASE_OUTSTANDING` 的条数（`retirable` 返回 false 的那条）；
  - `indexMismatches`：锚定索引与盘上记录不一致的条数（`check` 里的两处 `require` 失败路径）。
- **输出位置**（三处，均为**只读**）：①既有 `RecoveryFenceAudit.event(...)` 的字段扩展（**不新增事件类型**，沿用 `RECOVERY_LEASE_ORPHAN`／`RECOVERY_LEASE_OUTSTANDING`）；②一个**只读查询方法**（如 `RecoveryLeaseScan.counts(roots)`）供运维/健康检查调用；③可选暴露为 Spring Actuator 指标（`outbound.recovery.lease.orphans.*`）。
- **同事务口径**：计数在**同一次 `coordinated(...)` 内**完成，与 `roots.verify()` 同锁；**不新增锁、不新增写**。
- **不改行为**：拒绝码、拒绝时机、退休时机、`UNCONFIRMED_NO_CROSS_PROCESS_BASIS` 全部不变。

### 第 2 层（**建议做，默认关闭**）：可选扫描定时器 ＋ 间隔配置项

- **配置项设计**（沿用既有 `@ConfigurationProperties` 前缀惯例）：

| 属性 | 类型 | 默认 | 含义 |
| --- | --- | --- | --- |
| `outbound.recovery.lease.scan.enabled` | boolean | **false** | 是否启用独立扫描定时器 |
| `outbound.recovery.lease.scan.interval` | Duration | `PT5M` | 定时扫描间隔（仅在 enabled 时生效） |
| `outbound.recovery.lease.scan.initial-delay` | Duration | `PT1M` | 首次扫描延迟 |
| `outbound.recovery.lease.scan.fail-fast` | boolean | **true** | 定时扫描发现孤儿时：true＝记事件并继续（**不改拒绝语义**）；false＝保留给未来，**不实现** |

- **建议：默认关闭**。理由：①安全性不依赖它（第 1 层已把可见性补齐）；②开启后引入**后台线程触碰恢复控制树**，与"同步、可审计、单一写者"的现有纪律冲突，需单独评审；③生产是否需要更低的恢复延迟，属**运维政策**（与 A4.2 的 RPO/RTO 目标绑定）——**未定前不默认开启**。
- **若开启的硬约束**：定时扫描必须走**与 `checkEffect()` 完全相同的路径**（`requireOwner` ＋ `coordinated` ＋ `roots.verify()`），**不得**成为"绕过围栏的旁路"；扫描只做"发现＋退休＋计数＋记事件"，**不获得任何放行能力**。
- **配置项与版本事实**：间隔值属"时效措辞/版本事实"敏感项，文档中**只写属性名与默认值**，不写"当前值"。

### 第 3 层（**不建议现在做，需另外授权**）：主动仲裁/自动恢复

- 把"窗口 W"从"由调用频率决定"改成"由定时器保证上限"。这会**改变恢复延迟的语义与承诺**，触及运维/灾备政策，**不在本轮可见性授权内**，本页不设计。

## 3. 改动面清单（**待授权后才动**）

| # | 文件 | 改动 | 是否触碰拒绝/放行 |
| --- | --- | --- | --- |
| 1 | `recovery/RecoveryLeaseScan.java` | `check()` 内新增四个计数器并在同锁内输出；新增只读 `counts(roots)` | **否**（只读＋事件字段） |
| 2 | `recovery/RecoveryLeaseConfiguration.java` | 新增 `scan` 配置类（`enabled/interval/initial-delay`），默认关闭 | 否 |
| 3 | 新类 `recovery/RecoveryLeaseScanScheduler.java` | 仅当 `enabled=true` 时注册；走与 `checkEffect()` 相同路径 | **否，但新增后台线程 ⇒ 需单独评审** |
| 4 | 测试：`RecoveryLeaseScanCountsIT`（新） | 断言：孤儿被计数且**仍拒绝**、不引入新放行、默认关闭时无定时器 | 否 |
| 5 | 文档 | 本设计页＋看板 W-2 条目更新 | 否 |

## 4. 验收口径（若授权第 1＋2 层）

1. **行为不变**：`RecoveryFence*`／`RecoveryEffect*`／`G0K1*` 全家族（本轮基线 **25 类/729 项/0/0/0**）**逐项不变**；
2. **计数正确**：合成 1 条／N 条孤儿时，`orphansObserved/Retired/Refused` 与索引改写后的条数一致；
3. **拒绝不变**：存在孤儿时**仍抛** `RECOVERY_LEASE_OUTSTANDING`（fail-closed 不放松）；
4. **默认关闭**：`enabled=false` 时**无后台线程**（断言线程数/bean 未注册）；
5. **开启后等价**：`enabled=true` 且间隔设为极短时，扫描路径与 `checkEffect()` 的调用序列一致（不接受旁路）。

## 5. 边界声明

- 本页**只读＋设计**：**未改任何代码**、未新增配置项、未加定时器、未开任何开关。
- **不放松 fail-closed**：拒绝码、拒绝时机、围栏语义、`UNCONFIRMED_NO_CROSS_PROCESS_BASIS` 全部不动（写入第 1 层与第 4 节的验收口径）。
- 不部署、不接原库现网、未合 main；`localDeletionAllowed=false`；开关全默认 false。
- **W-2 的边界表述保留**：窗口 W 仍＝受保护调用间隔，**未被本设计改变**。
