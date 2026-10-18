# U02 TEMP 待确认与耐久保存状态原生 L1（2026-10-19）

TEMP 作业台的继承修复已在保留身份的隔离 Android 客户端实际通过：手输校验后显示“待确认输入 1”，已保存仍为零；实际确认保存后才显示已保存一包，重建后保持。这个结果只补齐本状态子项的 Android L1，不计 U02/D17 整体、D06/D07 L2、E06 L2、D18 L3 或总目标完成。

## 输入与逐路径影响

固定树 `D:/acrylic-pda-outbound`、分支 `codex/development`，输入 HEAD/远端均为 `d4624d61bba6b0af57070394762f0a773913633d`。证据根 `.local/goal-20261019-resume/u02-native-retained914-run01/`，下列未写前缀的证据均在此目录。唯一写者为本聊天 `01a147c5-0495-7703-ba98-2e7637cbc619`；无子代理。`dirty-resume01.json/.zip`、`tracked-resume01.patch` 与 `inherited-protection-resume01.json` 证明原 202 项内容字节完全相同，新增一项设备测试。初版保全检查器误以为看板已漂移而断言失败，原结果留存；实际哈希为全量相同，未发生源码/数据库覆盖。

| 路径 | 影响层 | 必跑清单与结果 | 复用依据（路径＋SHA） |
| --- | --- | --- | --- |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/TemporaryWorkRenderer.kt` | TEMP 状态主行、待落盘提示及首屏高度 | 实际 TEMP 手输待确认→确认保存→重建；构建/lint通过 | 继承 WIP 原字节 `f027b69f4b792d8403cd73aa6f44770796b3a9459ef95d37220de1138633b7bb`，本轮不改写；与 `input.json`、O01 `source-bindings-final.json` 一致，按该范围纳入开发备份 |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/TemporaryPendingStatusDeviceTest.kt` | 隔离 Android 分阶段消费者 | 两个方法分别执行，各 `OK (1 test)`；一阶段正常联网重校时间，二阶段消费原准备资料并操作产品页 | 新测试，SHA见 `source-bindings-final.json`；仅 DEBUG/ranchu/固定隔离 HTTP；一次性阶段标记，禁止直接整类重跑覆盖证据 |
| 本报告、进度索引、看板和旧看板归档 | 证据与断点 | 链接/范围检查，version_facts Write/Check | 原始旧看板保存在 `board-before.md`；归档不复述版本数字 |

正式 `WorkRenderer.kt` 仍为继承 WIP，本轮没有正式作业台状态的实际客户端验收，不纳入该通过或提交。未改模型、契约、HTTP 产品代码、迁移或 Python 产品消费者；Chrome、非空迁移、权限/并发/末 SQL 等链无新增行为影响，本轮不重跑也不计这些组合已验。完整源码、依赖锁及产物绑定见 `source-bindings-final.json`；历史状态报告 `GOAL_E08_D17_U02_STATUS_RENDERER_L1_2026-10-18.md` 仅按未漂移路径复用主机范围。其“32 个定向测试任务”对应日志中的 32 actionable Gradle tasks，不能解释成 32 个用例。

## 实际客户端与独立消费

- 保留 `Filter914_Pixel6`/`emulator-5622` 的原 userdata、Keystore、会话和 Room；未 wipe、重新注册、导入或重绑身份。这里只是隔离合成身份，不冒充原 PDA/现场资料。
- 首次旧组合测试因设备重启后可信时间失效，在创建草稿时被产品拒绝，`private/instrumentation01.log`、`result01.json` 记失败，不改为通过。Python 直接消费前后设备 tar 和诊断快照，83 张表全部摘要守恒（`consumer02-result.json`），证明阻断前后没有业务表写入。
- 按原隔离实例身份恢复 run914 MySQL/HTTP，通过实际设置页“检查后台连接”正常重校授权时间。`revalidate01-command.json`、`private/revalidate01.log`、`revalidate01-result.json` 和设备包证明原 device/user/warehouse/localSessionId、公钥、Room 与原签名准备资料保持；未用 fixture 伪造时间。
- `pending-status01-command.json` 实际运行 TEMP 作业页：准备最小草稿，手输校验后待确认一条且已保存零条；点击“确认保存”后增加 capture/scan 各一条、已保存一包，待确认主行消失；Activity 重建仍一致，无 submission、未结单/放行。
- `consumer01-result.json` 独立 SQLite 消费核验完整性和 FK 正常；仅 temporary_capture/draft/event/guard/line/scan 与 work_device 七表按预期变化，其他表全部守恒；新事件为创建、计划、开始、接收、待确认、请求确认、保存。`consume_closeout.py` 再消费最终设备包，所有表与成功包一致。均是工具消费者，不是独立人员复核。
- `test-build-command.json` 实际执行 AndroidTest 构建和 lint，退出码零，`private/test-build01.log` 为 BUILD SUCCESSFUL（62 actionable tasks，不作为测试用例数）。产品 APK 字节与 O01 最终 APK 相同，SHA `c42ca36bd7f418fa92f00772dfedb9ef3047b1db7e41a843570f1c8d1e395992`；新增测试 APK 保存在 `apk/u02-native-test01.apk`，SHA `bd750cf715b18ab36a5243b126d600d205f408e0606f5c7249b29c5917a6fe57`。

前序启动包装器失败见 `startup-wrapper-failure.json`，未删日志或清零失败。U04 状态筛选审计 `u04-state-filter-audit.json` 已确认存在实现及四字号历史证据，正式 10/11、TEMP 13/16 源路径相同；发生漂移的路径不能直接拼成候选通过，因此没有重复开发筛选功能。

## 停机、保全及下一项

`closeout.ps1` 核对 PID/命令行和数据库身份后，先停止应用并保存 `private/device-final.tar`，停止本轮 HTTP，保存数据库身份及 `private/database-final.sql`，再认证 mysqladmin shutdown、正常退出 AVD。`stop-result.json`、MySQL Shutdown complete、`processes-final-stopped.json`、`listeners-final-stopped.json` 证明目标 19780/19781/5622/5623 零监听且所有目标 PID 已退出；共享 MySQL、ADB、USB 和其他项目进程只观察。身份文件、原签名、设备包、失败记录及最终 APK 保全，不上传私有证据。

切片清理审计见 `cleanup-review.json`；前序 datadir/AVD RAM 清理启动前被策略拒绝的记录沿用，未改用其他方式删除该批目标。新临时资料按文本摘要/哈希保全后，仅针对无消费者的正常 Kotlin daemon 日志做有界清理，实际执行/释放字节与 C/D/E 前后余量见清理回执；若删除被拒绝则保留原件并记零释放。最终全量工作树与 patch/zip、索引见 `dirty-candidate01.*`、`tracked-candidate01.patch`、`evidence-index-final01.json`；提交推送精确回执另见 `git-postcommit.json`。

下一项补正式 U02 作业台待确认/已保存语义的当前候选客户端证据，先审计 run914 冻结正式单与可用新发布输入，不能解冻/改写原正文。之后继续其余 O/U、D01–D18 和 §8；D16 仍缺可信合法退出依据。取消的换机承接、多跳、接收者确认/激活链不恢复；七类生产决定、TEMP 生产归档保持关闭，`localDeletionAllowed=false`，不合 main、不发布/部署、不用正式签名或连接原库/现网。
