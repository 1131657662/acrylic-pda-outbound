# E08/D17/U04 高频 UI 隔离候选 run904（2026-10-18）

本记录承接 run903 的前置缺口，只验证当前 fail-closed 范围内的真实 Android 产品入口：独立隔离 MySQL/HTTP、独立 `Run904_Pixel6`、准备页、首页入口、离线草稿扫码、保存扫码、手动确认、暂停/恢复和重复包拒绝。输入是可审计的隔离合成资料，不代表现场 PDA、原身份、生产回执或 D18 最终候选。

## 输入、构建与环境

- 树：`D:/acrylic-pda-outbound`，分支和精确 SHA 以看板生成事实块为准；继承 WIP、取消范围源码、失败原件和未跟踪资料均保留。
- 隔离 HTTP：`127.0.0.1:19438`，JAR 为 `server/build/libs/acrylic-outbound-server-0.1.0.jar`；隔离 MySQL 使用独立 datadir、`127.0.0.1:19332`、库 `acrylic_e2a_cross_20260925_904`，身份记录在 `private/database-identity.tsv`。
- 独立设备：`Run904_Pixel6`，ADB `emulator-5568`；启动前后 PID、监听、USB M71 只读枚举和 C/D/E 余量见 `environment-final-running.json`、`environment-final-after-stop.json`。
- 最终 Debug APK SHA-256：`E0EAEC242D9D3CCD3E04FE0A8DD555FFD5627617AF63176D10A0A349BABE8055`；AndroidTest APK SHA-256：`103E3B743E54D25B0A9A20C3EF8B618F46C3A9F35E8B9AE7A9D73328D84729D3`。
- 服务端 fixture XML 实际为 `1/0/0/0`；fixture 启动器的 PowerShell 编译告警误报原文仍保留在 `private/fixture.log` 和 `xml/fixture.xml`，不以包装器退出状态替代 XML 事实。

## 实际验证

以下均为最新 APK 上的独立 instrumentation；每项均有原始输出和 `run-as` 设备文件备份：

| 范围 | 结果与输出 SHA-256 |
| --- | --- |
| `HighFrequencyPreparationDeviceTest#admittedDevicePreparesSignedTemporaryDataThroughProductPage` | 通过；`preparation06-result.json`，输出 `8c6e964ab4ac6ec53bd780378a4f765334659ea8f5a7b95ed1fa971f5c4f9415` |
| `HighFrequencyOfflineDeviceTest#realHomeNavigationKeepsMaintenanceOutsideWorkPage` | 通过；`home03-result.json`，输出 `cd2b3bc8ede6233c7fd9791dc31c8dd3c5affb6db9abb3b166958e89f28c06ee` |
| `HighFrequencyOfflineDeviceTest#independentDraftSearchEditThenManualConfirmPauseAndReturnPreserveFacts` | 通过；`scan03-result.json`，输出 `84cd526e8be8c19e23e6a54ae20a0afb891c4358e8b9e66e59a364f40c85c0124` |
| `TemporarySaveScanCombinationDeviceTest#savedPlanFeedsManualConfirmationAndPauseResume` | 通过；本次最终重跑 `run904-final01`，输出 `afaf4f31bb7edfe3698c266837ee98f9453712b0b859e6008cc58eb5b36f9957`，设备 tar `b2f0c69d6ea5a7e61d92d129c1cef0899dfcaa32a446aa89665689cfbd36ecc5` |

最终保存扫码测试的 Room before/after JSON SHA-256 分别为 `f0dc03c1eebcfc78a54d85b82e72d2564beab48bb1fb3702d0cce87145ecfeef`、`db5bc792106640e4367e864a2c272c84870018dc6e8b9a2249f23e14c33632c0`；均由设备 `run-as` 读取，原始文件在 `private/room-before-final01.json`、`private/room-after-final01.json`。数据库停机前只读状态见 `private/final-db-state02.tsv`：独立身份匹配，Flyway 成功迁移非空，`device=4`、`temporary_authorization=4`、`temporary_preparation_ack=4`、`submission=0`、`app_user=1`、`product=1`，服务仍为 `NORMAL/recovery_epoch=1`。

## 停机、清理与边界

- 通过 `mysqladmin shutdown`、停止 PID 18592、`adb emu kill` 完成认证停机；目标端口 `19332/19438/5568/5569` 清零。共享 `3306/33318/5173/5175/8000` 和 USB `22D2910042` 未停止、未连接、未写入。
- 停机日志为 `private/shutdown.log`；最终环境为 `environment-final-after-stop.json`，服务日志和 MySQL 日志哈希在其中登记。
- 依据本次 D 盘清理授权，在无消费者、已认证停机并保留文字事实后删除可重建的 `high-frequency-904/avd`、`e2a-cross-20260925-904/data` 和 `storage`；`cleanup-before.json`/`cleanup-after.json` 记录路径、输入字节、命令、前后余量和释放 `6,265,477,018` bytes。源码、APK、Room/HTTP/DB 状态、失败原件、输入和继承 WIP 未清理。
- 这是一组合成隔离输入上的 U04/D17 有界 L1 候选。它不满足真实现场身份、原 PDA/Keystore、Chrome、Python 独立消费、非空迁移组合的完整 L2，也不满足 D18 全链 L3；D17 整体、E06 L2、D18 L3、第8节闸门和总目标保持未完成。取消的换机承接、多跳、接收确认/激活链未恢复。

## 失败和未验

run903 的无临时资料首页 GONE、run904 初始 F1 键位、`recentSaved` 异步刷新及 fixture 启动器误报失败原文继续保存在本目录 `private/`，不覆盖、不删除。未执行 Chrome、Python 消费、原 PDA/现场身份、外置介质、长时/断电、正式签名、发布或部署。

