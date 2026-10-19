# E08 / D17 U04 高频 Android L1 run911（2026-10-18）

## 范围与结论

本轮是独立隔离的合成输入候选，只验证 U04 高频工作流的产品入口 L1：准备页、首页导航、离线草稿扫码、保存扫码、手动确认、暂停/恢复与重复包拒绝。它不满足原 PDA、现场身份、真实业务回执或生产审批要求，因此不计 D17 整体、E06 L2、D18 L3 或总目标。

结果：四个产品入口均实际通过；`adb` 进程包装器返回 `-1`，但每份 instrumentation 原始输出均有 `OK (1 test)`，并以原始日志作为测试结果依据。没有 submission、换机承接、多跳、接收者确认或激活写入。

## 隔离输入与环境

- evidence：`.local/goal-20261018-resume/high-frequency-session-run911/`
- HTTP：`http://127.0.0.1:19441/api/v1/`；专属 Spring PID 22660
- MySQL：127.0.0.1:19341，库 `acrylic_e2a_cross_20260925_911`，专属 datadir `.local/e2a-cross-20260925-911/data`
- AVD：`U02Session911`，`emulator-5566`，专属 userdata；USB M71 `22D2910042` 只读观察
- fixture：`private/fixture.log`，退出包装器为 3；日志末尾为 fixture 通过 `1/0/0/0`
- 输入：`private/input.json`，明确标记 `SYNTHETIC_HIGH_FREQUENCY_UI`

## 实际产品验证

| 入口 | 原始证据 | 结论 |
| --- | --- | --- |
| 准备页 | `private/preparation.instrumentation-success.log`、`preparation-device.tar` | `OK (1 test)`；tar 含 `no_backup/session.enc`、设备绑定、Room/WAL 与结果 |
| 首页导航 | `private/home.instrumentation.log`、`home-device.tar` | `OK (1 test)`；维护入口保持在首页，工作页不误显 |
| 离线草稿扫码/编辑/手动确认/暂停恢复 | `private/scan.instrumentation.log`、`scan-device.tar` | `OK (1 test)`；状态与草稿事实保留 |
| 保存扫码组合/重复包拒绝/手动确认与暂停恢复 | `private/save-scan.instrumentation.log`、`save-scan-device.tar` | `OK (1 test)`；重复包拒绝且无额外提交 |

每个 tar 均含 `no_backup/session.enc`、`no_backup/device-binding.json`、Room 数据库及 WAL；设备截图与结果 JSON 由 tar 原样保全。Android 实际数据库最终状态为 temporary draft=2、capture=4、scan=2、submission=0、temporary handover confirmation=0、temporary handover activation=0。

## 隔离 HTTP/MySQL 最终只读状态

修正后的查询在 `private/final-db-state.sql`，输出在 `private/final-db-state.tsv`。实际返回：端口 19341；库与用户为上述隔离身份；`log_bin=1`；`server_state.service_mode=NORMAL`、`recovery_epoch=1`；Flyway `99/1`；`app_user=1`、`device=1`、`temporary_authorization=1`、`temporary_preparation_ack=1`、`outbound_order=0`、`product=1`、`submission=0`、`temporary_handover_confirmation=0`、`temporary_handover_activation=0`。

## 边界、失败与后续

- 这不是统一最终候选，不能替代 D17 业务 UI L1 缺口、E06 L2 或 D18 全链 L3。
- 不恢复 R07 已取消的换机进度承接、多跳、接收者确认/激活链；相关 WIP 继续保留且无生产 caller。
- `private/final-db-state.stderr` 中仅保留 mysql CLI 密码警告；先前错误表名 `recovery_control` 已改为实际的 `server_state`，不影响业务数据。
- 认证停机后另存 `environment-final-after-stop.json`，专属可重建 AVD/datadir 按 D 盘持续清理规则评估；唯一日志、输入、设备 tar、失败原件和最终 APK 保留。

## 复用与验证清单

- 直接受影响入口：`HighFrequencyPreparationDeviceTest`、`HighFrequencyOfflineDeviceTest` 两项、`TemporarySaveScanCombinationDeviceTest`；均已由实际 Android instrumentation 消费。
- 依赖：真实隔离 HTTP/MySQL、非空 Flyway 迁移、Room WAL、独立 AVD；未使用正式签名、原库、现网或合成身份冒充现场事实。
- 本轮仅 L1；无 L2/L3 复用声明。

## SHA 绑定

完整逐文件绑定见 `.local/goal-20261018-resume/high-frequency-session-run911/artifact-sha256.json`。关键输入/交互原文/设备 tar/APK：`input.json`=`4BEE67C9…B7084B`，四份 instrumentation=`C2E6AE3D…A3B4F`、`0A815937…A725D`、`5E85823E…BCA66`、`28CEE588…E55AF`，四份设备 tar=`4CC20D2B…A00736`、`96703CC4…F6420`、`56908721…9034E7`、`F8CD0C28…EDC9E`，Debug APK=`7C848DF1…7C9621`。最终 DB 查询、环境和清理文件也已纳入同一 JSON。
