# E08 / D17 U04 高频 UI 隔离候选 run925（2026-10-19）

本轮在 fail-closed 范围内完成一次新的高频 Android 产品入口 L1。输入是可审计的隔离合成账号、库存和设备密钥，不能代表原 PDA、现场身份、生产回执或审批事实。结果只计 U04/D17 有界 L1，不能计 D17 整体、E06 L2、D18 L3、第 8 节闸门或总目标完成。

## 输入与隔离

- 证据根：`.local/goal-20261019-resume/high-frequency-run06/`。
- fixture 输入副本：`.local/goal-20261018-resume/high-frequency-run925/private/input.json`；证据 SHA-256 为 `A7579A3EE522CF95B32DFDD30A9C7D4166D91EC7A91DAEF65FD5571A0A31A810`，用途标记为 `SYNTHETIC_HIGH_FREQUENCY_UI`。
- 专属 HTTP/MySQL：端口 `19475`/`19375`，数据库 `acrylic_e2a_cross_20260925_925`；fixture XML 为 1 test、0 failure、0 error，完成非空迁移后创建合成用户、仓库、库存和临时授权。数据库最终 dump 保留在 `private/final-database.sql`，SHA-256 为 `4C92AC5AED4517E83B5C0049883B09028A0385BA50C8A1ADE9F497E77D26AAEF`。
- AVD：源 `C:\Users\qq113\.android\avd\Pixel_6.avd` 只读复制到本轮 D 盘目标，运行时显式绑定目标 datadir；启动/停机后源 AVD 四个可写镜像均 `unchanged=true`。目标 AVD 与隔离 datadir 已在认证停机并确认无消费者后清理。
- APK：本轮构建的 Debug APK 和 AndroidTest APK 保留在 `apk/`，哈希索引为 `artifact-sha256.json`；未使用正式签名、原库或现网。

## 实际 Android L1

以下四项均由当次 APK 在 `emulator-5590` 上实际执行，原始输出均包含 `OK (1 test)`，设备 tar、Room/WAL 和截图均保留：

| 入口 | 原始证据 | 事实 |
| --- | --- | --- |
| `HighFrequencyPreparationDeviceTest#admittedDevicePreparesSignedTemporaryDataThroughProductPage` | `private/preparation.log`, `preparation-device.tar` | 真实 HTTP 登录、设备注册、临时授权、准备页下载确认；结果 JSON 记录 inventory rows=2、product page used、`localDeletionAllowed=false` |
| `HighFrequencyOfflineDeviceTest#realHomeNavigationKeepsMaintenanceOutsideWorkPage` | `private/home.log`, `home-device.tar` | 首页/工作/我的/数据导航和维护入口边界通过，工作页未误显维护入口 |
| `HighFrequencyOfflineDeviceTest#independentDraftSearchEditThenManualConfirmPauseAndReturnPreserveFacts` | `private/scan.log`, `scan-device.tar` | 离线草稿、搜索编辑、手动确认、暂停/恢复和重复输入事实保留；尺寸记录在 tar 内 |
| `TemporarySaveScanCombinationDeviceTest#savedPlanFeedsManualConfirmationAndPauseResume` | `private/save-scan.log`, `save-scan-device.tar` | 保存计划、手动确认、暂停/恢复、重复包拒绝；最终 Room 快照为 2 drafts、4 captures、0 submissions |

最终设备快照中的 `temporary_handover_confirmation`、`temporary_handover_activation` 和 `temporary_submission` 均为 0；没有启动换机承接、多跳、接收者确认或激活链。数据库 dump 中的合成设备、临时准备和库存只用于证明这次隔离输入，不构成现场证据。

## 构建、失败与清理

- `server bootJar`、Android Debug/AndroidTest 构建、fixture、HTTP 服务、ADB 安装、设备反向代理和四项 instrumentation 的命令日志及退出码均在证据目录；`run-result.json` 为 `stage=complete, exit=0`。
- 运行器前两次失败均为夹具/记录器问题，没有消费产品输入；原因和保全范围见 `failures-summary.md`。最终运行器脚本哈希见 `artifact-sha256.json`。
- 认证 MySQL shutdown、ADB emulator kill、HTTP/目标端口清零和源 AVD 守恒记录在 `processes-after.json`、`environment-final.json`、`avd-source-after.json`。共享 MySQL、Node、ADB 和实体 USB 仅观察。
- 清理前逐路径实测：`avd-home` 30 文件、`6,876,835,598` bytes；`.local/e2a-cross-20260925-925` 355 文件、`222,761,098` bytes。清理命令和修正后的前后余量见 `cleanup-corrected-summary.md`；D 盘观察到增加 `7,104,090,112` bytes。输入、APK、最终 dump、日志、fixture XML、设备 tar 和源码未清理。

## 复用与未验

- 直接受影响产品入口是三个 Android 测试文件：`HighFrequencyPreparationDeviceTest.kt` SHA `B97B200D37E4DE3F569D5DECECD4C93011956E4BDE5DEB12E1352A3317B5F889`、`HighFrequencyOfflineDeviceTest.kt` SHA `639A6A8B778CD645AD9FE2F513957AA53D64FE8B9F4C453744DC8A3B7635A867`、`TemporarySaveScanCombinationDeviceTest.kt` SHA `8F15456CB138F94B29DDE1298531E3BBC821F60C05EC1FD81E09FFE45FD5B6EA`。本轮未改动这些继承 WIP 文件，仅消费当次输入。
- 本轮没有 Chrome、Python 独立消费、原模拟器恢复、真实外置介质、现场身份、正式签名、长时/断电或跨来源 L2。仍需统一最终候选和 D18 全链 L3；总目标保持 active，§8 全部闸门未满足。
