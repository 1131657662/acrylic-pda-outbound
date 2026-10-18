# E07/D06-D07 撤回重发 Android/HTTP/Chrome 组合（run744）

本报告登记一次全新隔离候选。运行覆盖坏机离线撤回后的恢复消费者、重新发布、新设备从头作业、
真实隔离 HTTP/MySQL、实际 Android emulator、独立 Python 消费和 Chrome。输入身份、订单、签名、
数据库与文件均为一次性合成隔离资料；不恢复换机进度承接、多跳或接收者确认/激活链。

## 输入与隔离

- 工作树为 `D:/acrylic-pda-outbound`，分支 `codex/development`，输入 HEAD 为
  `2cf6f894e60523e76e28a0e354c07dff9fc07de3`；本次只新增报告/索引/看板记录，未覆盖继承源码。
- runner：
  `powershell -NoProfile -File tools/e07-d06-d07-emulator-run.ps1 -Run 744 -MysqlPort 19663 -HttpPort 19664 -WebPort 19665 -EmulatorPort 5590 -Avd Pixel_6`。
- attempt：`.local/e2a-cross-20260925-744/`；完整证据：
  `.local/goal-20261011/fault-restart-consumer-744/`。隔离服务使用 `19663/19664/19665`，
  emulator serial 为 `emulator-5590`；共享 `3306/33318/5173/8000` 与 USB 设备只观察未停止。
- runner 在结束阶段保存输入、环境、进程/监听、Android 日志、Chrome trace、数据库 dump 和认证停机日志；可重建 datadir 的清理回执见 `history/D_DISK_CLEANUP_RUN744_2026-10-18.md`。
  运行后 `19663/19664/19665/5590` 无监听，目标 MySQL 进程退出；共享项目监听未触碰。

## 实际结果

- `xml/fixture.xml` 的 `FaultRestartConsumerFixtureIT.prepareAssignedOrdersForRealConsumers()` 为
  `tests=1, skipped=0, failures=0, errors=0`；服务端与 Android Debug/AndroidTest 构建退出 0。
- Android emulator 四个独立测试均返回 `OK (1 test)`：新设备领取/下载/扫码/冻结/自身成功、
  强制停止后保留成功结果与冻结原字节、扫描设备消费签名撤回并保留本机订单、重启产品进程后撤回
  恢复到停止态。每个原文日志、logcat、getprop 和结果 JSON 均保留。
- Python `fault-restart-input-consumer.py` 以只读模式消费三类消费者输入（3 consumers、2 scans），
  输入 SHA 为 `A7EB7337D386321C56323C11D61B7BBA223A427CDE566415ADA427830518B74D`，退出 0。
- Chrome `fault-restart.spec.ts` 两项实际测试通过：页面 reload/丢 HTTP 响应后恢复撤回与重发命令，
  以及本地请求失败时不发送撤回并保留已领取订单；Playwright `expected=2, unexpected=0,
  skipped=0, flaky=0`，trace 保留。
- 运行包含非空迁移和真实数据库写入，结束生成最终 dump；隔离账号认证停机成功。没有连接原库/现网、
  没有使用正式签名、没有改变生产默认开关或 `localDeletionAllowed=false`。

## 证据绑定

| 路径 | SHA-256 |
| --- | --- |
| `tools/e07-d06-d07-emulator-run.ps1` | `3EA8183BC84C94DDB30495A2F651DA630818A86593A8FDBE33664246CD444C52` |
| `tools/fault-restart-input-consumer.py` | `ECBA27BEB80D4CF5060243C4C539EAA0AF144F004A21DDE2052CFD32A7226ADE` |
| `server/src/test/java/com/acrylic/outbound/inventory/FaultRestartConsumerFixtureIT.java` | `21FAF0EC39861522D9C38F1A93BCC0559684EB4C21AA94907D686744B2D08A9F` |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/FaultRestartDeviceTest.kt` | `B8CC61251B75F2C5515067144E498DFD764639601228E868A66A465BB646414D` |
| `web/tests/fault-restart.spec.ts` | `6732670727E863D3C3643D50BBCD2D485ED565603D90994C7D3F5836CE29820C` |
| `.local/goal-20261011/fault-restart-consumer-744/xml/fixture.xml` | `7E792274BA23F7CC019855AE54E1DB340A23CDB7122CB55A999D5897D544195F` |
| `.local/goal-20261011/fault-restart-consumer-744/private/input.json` | `A7EB7337D386321C56323C11D61B7BBA223A427CDE566415ADA427830518B74D` |
| `.local/goal-20261011/fault-restart-consumer-744/private/python-result.json` | `580D94E486C8415E6FEDBB918495D321813414B0D301F03A4EFEFDA2EC9077B4` |
| `.local/goal-20261011/fault-restart-consumer-744/private/web-result.json` | `BC477B9FF8B80E4D06CA3C93CDDBF46A3D5CA86D698757640E9706587EA93E4D` |
| `.local/goal-20261011/fault-restart-consumer-744/playwright.json` | `40F00B0CD5A258E31D46211FB031CA10A9303B9DB49DBC9F45B063D9C11F6E79` |
| `.local/goal-20261011/fault-restart-consumer-744/final-database.sql` | `9EABCD7FA853935048E51E4C2C808600189C38BC9AA4BA83424093DD6398B349` |
| `.local/goal-20261011/fault-restart-consumer-744/fixture.log` | `676BD7575998641162639D93DA481E26B6DE620940D02EF7C33BE27640A4069B` |
| `.local/goal-20261011/fault-restart-consumer-744/authenticated-shutdown.log` | `E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855` |
| `.local/goal-20261011/fault-restart-consumer-744/android-1-newDeviceClaimsDownloadsScansFreezesAndVerifiesOwnSuccess.log` | `C2CBDC865CA86E091DA3EF993B810C19E94D83EEBCB78E0A449A4A635BD8B9B7` |
| `.local/goal-20261011/fault-restart-consumer-744/android-2-forceStoppedProcessRetainsVerifiedResultAndOriginalFrozenBytes.log` | `9CD2E2B12A6FBF6345808B2035FF99B3F737ABDF35E70DD331BEB37723861425` |
| `.local/goal-20261011/fault-restart-consumer-744/android-3-scanningDeviceConsumesSignedWithdrawalAndRetainsItsLocalOrder.log` | `36B4EBAA8203A477C016939BE46B18B92EC2C4BE02080ED43098C2802460FD41` |
| `.local/goal-20261011/fault-restart-consumer-744/android-4-retainedWithdrawalReplaysIntoStoppedStateAfterNewProductProcess.log` | `11E16C914A54ADB634798B18369A5E953EACCC7329BC71EC3271B3EE2CA6734F` |

四个 Android 原文日志的 SHA 分别记录在 runner evidence 目录并与本报告输入绑定；每个均含
`OK (1 test)` 和 `INSTRUMENTATION_CODE: -1`。浏览器 trace、`processes-after.json`、
`environment-after.json`、emulator 输出、隔离 MySQL 身份/日志和失败/停机原件均保留；可重建
datadir 的删除与释放空间见清理回执。

## 范围结论

run744 补齐了一次带实际 emulator、Chrome 和独立 Python 消费者的 R07 撤回重发开发组合，证明
隔离输入上的丢响应、重启、撤回和新设备从头作业路径可运行。由于身份/资料/设备均为合成输入，
且尚未覆盖原 PDA、原身份、现场实物、外置介质和统一最终候选，本报告不计 D06/D07 L2、E07
整体、D13/D14/D17、E06 L2、D18 L3 或第 8 节闸门。下一步继续补真实边界和独立范围，不重启已
按三次同根因暂缓的 recovered-confirmation 路径。

## 提交前记录

- 改动路径：本次提交只包含本报告、`GOAL_E08_RECOVERED_CONFIRMATION_WIP_AUDIT_2026-10-18_TAKEOVER05.md`、`ORCHESTRATION_STATE_before_run744_20261018.md`、`docs/DEVELOPMENT_PROGRESS.md` 和 `docs/ORCHESTRATION_STATUS.md`；影响层为历史证据/进度看板，不包含产品源码或生产配置。
- 必跑清单：run744 runner 已完成 fixture、隔离 HTTP/MySQL、Android emulator、Python、Chrome、认证停机；提交前另跑 `git diff --check` 与 `tools/version_facts.ps1 -Check`，并确认目标端口清零。
- 复用依据：takeover05 审计（当前树调用检索与路径哈希）、handoff-final01 保护清单，以及本报告证据绑定表；不拼接旧 run 的业务通过结论。
