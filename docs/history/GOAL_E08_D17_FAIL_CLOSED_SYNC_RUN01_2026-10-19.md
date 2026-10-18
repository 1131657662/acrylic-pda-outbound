# E08/D17 fail-closed 同步 caller 修复（2026-10-19）

本切片输入树为 `D:/acrylic-pda-outbound`、分支 `codex/development`、HEAD/远端 `b745d58c673c25dcee519487173276063352419a`。继承脏改动、handoff-final01、旧 run03、原资料/身份/回执和失败证据均保留；本切片未启动 HTTP/MySQL、模拟器或旧 bridge。

## 改动路径、影响和范围

| 改动路径 | 影响层 | 变化 | 必跑清单 |
| --- | --- | --- | --- |
| `android/app/src/main/java/com/acrylic/outbound/pda/sync/SyncWorker.kt` | Android 后台同步 | 删除已取消 `TemporaryHandoverConfirmationWorker.restorePending(app)` caller；保留 E2a、草稿和现行 TEMP continuation 恢复 | `TemporaryHandoverActionGateTest`、Debug/AndroidTest 构建、lint、源码负向 caller 检查、默认关闭回归 |
| `docs/history/GOAL_E08_RECOVERED_CONFIRMATION_WIP_AUDIT_2026-10-19.md` | 范围/政策证据 | 保存 D06/D07、D13/D14、R07 对照及逐路径实际调用清单 | 文档检查；与当前源码 `rg` 结果一致 |

看板和本报告只记录本切片，不把取消链的开发开关、合成 fixture 或历史回执视为产品验收。`TemporaryHandoverConfirmationWorker`、`TemporaryRecoveredConfirmationWorker`、dialog、fixture、bridge、repository 和 activation WIP 继续保全但不恢复现行入口；显式开发开关只保留为隔离历史 WIP。

## 验证与复用

证据根：`.local/goal-20261019-resume/failclosed-sync-run01/`。

- `:app:testDebugUnitTest --tests com.acrylic.outbound.pda.ui.TemporaryHandoverActionGateTest`：2 tests，0 failures，0 errors。
- `:app:assembleDebug`、`:app:assembleDebugAndroidTest`、`:app:lintDebug`：组合命令成功；lint 报告保存在 `android/app/build/reports/lint-results-debug.xml`。
- `rg` 负向核对 `SyncWorker.kt` 不再包含两个取消确认 worker 的 `restorePending` caller；退出码 1 表示无匹配。
- `git diff --check` 退出 0；构建未连接真实 HTTP/MySQL、Chrome、Python、原 PDA 或现网。

复用依据：前序 fail-closed gate/lint 记录 `history/GOAL_E08_D17_FAIL_CLOSED_FIX_RUN01_2026-10-18.md`、逐路径范围 `history/GOAL_E08_RECOVERED_CONFIRMATION_WIP_AUDIT_2026-10-18_TAKEOVER10.md` 及本切片新审计。没有复用前序设备、HTTP 或数据库业务通过结论。

## 未完成与边界

本切片不计 D06/D07、D13/D14、D17 整体、E06 L2、D18 L3、第8节闸门或总目标完成。真实 Android 行为、真实 HTTP/MySQL、Chrome、Python 消费、非空迁移、原 PDA/现场身份、外置保管和恢复全链仍需统一候选另行验证。七类生产决定、TEMP 生产归档、`localDeletionAllowed=false`、正式签名、部署、Release、`main` 和原库/现网边界保持关闭。
