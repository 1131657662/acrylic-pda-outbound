# E08 / U03 独立凭据、离线任务与扫描链 L1（2026-10-18）

## 范围

本轮是全新隔离开发候选，验证 Android 自己生成的 Keystore 公钥经真实 HTTP 登记后，产品凭据页、断网独立任务、扫描回滚/恢复和撤权边界能够消费同一原件。身份、账号、SKU 和任务均为隔离合成输入，不代表原 PDA、现场身份、实物回执或生产审批；不计 U03 完整、D17、E06 L2、D18 L3 或总目标完成。

取消范围的换机承接、多跳、接收者确认/激活链没有运行。

## 隔离输入与证据

- 证据根：`.local/goal-20261018-resume/u03-native-chain-run01/`
- MySQL：本轮新实例端口 19330、独立库和 datadir；HTTP：19442；服务与数据库 PID、命令行、日志在 `private/*process.json` 和 `private/*log`。
- Android：保留的 `U03Sku01` / `emulator-5580`，使用 `adb reverse` 仅转发本轮 HTTP；USB M71 未使用。
- 主机夹具：`private/fixture.log`、`private/fixture.exit`、Flyway 非空迁移；`private/input.json` 明确标记 `SYNTHETIC_INDEPENDENT_NATIVE`，凭据不写入本报告。
- Android 设备取证：`u03-native-chain-device.tar`、`private/device-files.txt`、`private/logcat-final.txt`；tar SHA 见 `private/artifact-sha256.json`。
- SKU 输入：`docs/sku.txt` 经实际 DocumentsUI 选择为 `E06_TASK_SOURCE.txt`；主机源 SHA 和设备文件状态均在 `private/artifact-sha256.json`、`private/source-push.log`。

## 实际验证

| 入口/情形 | 结果 | 原始证据 |
| --- | --- | --- |
| Android 本机公钥登记（真实 HTTP） | `OK (1 test)`；未注入会话或凭据 | `private/admission.instrumentation.log`、设备 tar |
| 产品凭据页签发与本地核验 | `OK (1 test)`；签名原件写入本机 | `private/credential-provision.instrumentation.log` |
| 断网新进程、错误口令拒绝、原回执不变 | `OK (1 test)` | `private/credential-offline.instrumentation.log` |
| 实际 DocumentsUI 选择 SKU、预检与启用 | `OK (1 test)`；12639 行、12639 包、145 产品 | `private/source02.instrumentation.log`、设备 tar |
| 断网独立计划创建、新进程重读 | 两项均 `OK (1 test)` | `private/task-create.instrumentation.log`、`private/task-restart.instrumentation.log` |
| 断网扫码、手动确认、失败确认、无效/重复包、作废、丢弃与重扫 | `OK (1 test)` | `private/scan-product.instrumentation.log`、设备 tar |
| 新进程重读扫描/作废/原序列 | `OK (1 test)` | `private/scan-new-process.instrumentation.log` |
| 任务审计末条 SQL 失败及新进程按原请求恢复 | 两项均 `OK (1 test)` | `private/task-tail-failure.instrumentation.log`、`private/task-tail-recovery.instrumentation.log` |
| 扫描末条写入失败及按原 captureId 恢复 | 两项均 `OK (1 test)` | `private/scan-tail-failure.instrumentation.log`、`private/scan-tail-recovery.instrumentation.log` |
| 大字体/键盘/小屏可达性 | `OK (1 test)` | `private/scan-layout.instrumentation.log`、设备 tar |
| 本机撤权、断网新进程阻断并保留原件 | 两项均 `OK (1 test)` | `private/credential-local-revoke.instrumentation.log`、`private/credential-local-revoke-restart.instrumentation.log` |
| 实际模拟器重启后原件保留、采集阻断 | `OK (1 test)` | `private/credential-reboot02.instrumentation.log`、`private/emulator-reboot.txt` |

## 失败与处置

- `private/source.instrumentation.log`：首次 DocumentsUI 在文件推入前打开，目录缓存未显示源文件；保留日志并重新打开 picker，第二轮通过。属于运行时输入时序，不计产品根因。
- `private/fixture.exit` 为外层归档包装器退出码 1；Gradle `private/../e2a-cross-20260925-810/exit.txt` 为 `testExit=0`，XML 为 1 test / 0 failure，包装器在测试完成后的归档阶段被中断，不改变夹具测试结果。
- `private/scan-resume.instrumentation.log`、`private/scan-resume02.instrumentation.log`：继承测试方法把已完成的多条历史记录当作单条，并硬编码 `actualCount == 1`；未改产品、未纳入通过矩阵。扫描主链和独立新进程读取已有直接证据。
- `private/credential-reboot.instrumentation.log`：重启后实际状态已是“本机独立采集资格已失效；原件保留”，测试只匹配“设备已重启”；修正测试提示后 `credential-reboot02` 通过。该修正只在 AndroidTest。
- 原始失败不删除、不覆盖；reader Robolectric 同根因失败继续按看板暂缓。

## 服务端只读事实

`private/final-db-state.tsv` 记录本轮端口、库、datadir、当前用户、隔离用户/设备/独立凭据计数、迁移末项及 `server_state=NORMAL`。没有真实 HTTP 之外的产品数据库写入；隔离服务随后认证停机。

## 改动与验证绑定

- `server/src/test/java/com/acrylic/outbound/inventory/IndependentNativeDeviceFixtureIT.java`：仅创建隔离账号输入；执行一次真实 Spring/MySQL 夹具并通过。
- `android/app/src/androidTest/java/com/acrylic/outbound/pda/IndependentNativeAdmissionDeviceTest.kt`：仅登记本机公钥的测试前置；真实 Android/HTTP 通过。
- `android/app/src/androidTest/java/com/acrylic/outbound/pda/IndependentScanDeviceTest.kt` 和 `IndependentCredentialDeviceTest.kt`：修正测试阶段提示/前置断言，不改变产品实现；AndroidTest APK 构建成功并由上述 instrumentation 实际消费。
- `private/artifact-sha256.json`、`private/environment-before-stop.json` 和工作树快照绑定本轮输入、APK、设备 tar 与精确 HEAD。

## 限制

本轮为开发 L1 候选。没有原 PDA/原 Keystore、现场身份、真实业务回执、第二物理设备、外置介质、真实生产审批或统一最终候选；不满足 U03 完整、任何 D 步骤 L2、D18 L3 或第 8 节全部闸门。
