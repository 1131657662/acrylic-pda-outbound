# 磁盘例行维护：只清旧 attempt 的 datadir（2026-09-28）

## 一、背景（为什么要这条策略）

每次 e2a / R08 attempt 的 MySQL 数据目录（`<attempt>/data`）约 **0.2–0.25 GB**；批量回归按"一类一 attempt"
推进，累积很快。2026-09-28 实测：501 个 attempt 的 datadir 合计 **91.4 GB**，一度把 `D:` 撑到 **0 GB 可用**，
MySQL `--initialize` 直接失败（`data directory … is unusable` / `ibdata1 … Probably out of disk space`），
attempts **416–424 全部 NOT-EXECUTED**。

**datadir 是可再生缓存**（每次运行都会 `--initialize` 新建），**其余文件是证据**。因此策略是：
**只删 datadir，按"保留最近 N 个"滚动清理。**

## 二、策略

- 工具：`tools/cleanup-old-attempt-datadirs.ps1`（只读以外的唯一动作＝删 `<attempt>/data`）。
- **保留最近 50 个 attempt 的 datadir**（`-Keep 50` 默认），更早的删 datadir。
- **一律保留**（这些是证据，不是缓存）：
  - `integration-test.log`、`mysqld.log`、`initialize*.log`
  - `test-started.marker` / `test-completed.marker` / 各类自定义 marker（如 `render-completed.marker`）
  - `storage/`（渲染产物、快照、文件类 IT 输出）
  - `private/`（一次性测试签名材料；gitignored 但仍保留）
  - `run-password.txt`、`provision.sql`
- 覆盖目录：`e2a-cross-*`、`r08-it-*`、`e2a-v36*`、`mysql19329*`。
- 不改仓库、不改源码、不动任何 attempt 的非 datadir 内容。

## 三、例行化（已接线）

`tools/e2a-provision-and-run-106.ps1` 与 `tools/r08-provision-and-run.ps1` 的**收尾处**都会自动调用它
（`-CleanupAfterRun` 模式，只回一行汇总），因此每次跑批自动滚动清理，不会再把盘写满。
手动执行：

```powershell
# 先看会删什么
powershell -NoProfile -ExecutionPolicy Bypass -File tools/cleanup-old-attempt-datadirs.ps1 -DryRun
# 实际清理（保留最近 50）
powershell -NoProfile -ExecutionPolicy Bypass -File tools/cleanup-old-attempt-datadirs.ps1 -Keep 50
```

## 四、本次执行结果（2026-09-28）

| 项 | 值 |
| --- | --- |
| 执行前 `D:` 可用 | 85.78 GB |
| 带 datadir 的 attempt | 34（`-Keep 50` ⇒ 无需删除） |
| 工具输出 | `cleanup: nothing to do (attempts with datadir=34, keep=50)`，耗时 **0.6s** |
| 执行后 `D:` 可用 | 85.78 GB（无变化，符合预期） |
| harness 接线冒烟 | attempt **464**（`E2aJobFenceHttpIT`）`tests=1 failures=0 errors=0 skipped=0`，收尾自动跑清理 |

> 说明：2026-09-28 早些时候已一次性删除 460 个旧 datadir（91.4 GB）以解除 0 GB 阻塞；
> 本条策略是为了**不再发生**，故此后按 50 个滚动保留。

## 五、未验 / 边界

- **未验证**"删除 datadir 后该 attempt 的其它文件确实完好"的逐目录核对（只做了抽样：attempt 390 的
  `storage/` 7 个文件、markers、日志均在）；
- `-Keep 50` 是**估的**阈值（50 × 0.25 GB ≈ 12.5 GB，对 85 GB 余量安全）；若将来批量更密集可下调；
- 未把清理做成 CI/计划任务（目前只在 harness 收尾与手动调用）；
- 未清理**非 attempt** 的旧 `.local` 目录（如 `e2a-v36-attempt*`、`mysql19329-attempt*` 已纳入覆盖；
  其余零散探针目录体积极小，未处理）。
