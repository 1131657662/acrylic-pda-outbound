# 开发看板

<!-- CURRENT_STATE_START -->
历史输入事实与原始看板字节见 `.local/goal-20261018-resume/o01-filter-run01/board-before.md`；版本事实只在看板生成块维护。

总目标 D01-D18、第8节全部闸门、O01-O05/U01-U04 及 UI 重设计保持 active、未完成。R07 已取消的换机承接、多跳、接收者确认/激活链不恢复。

## Git、写权与保全
- 固定树 `D:/acrylic-pda-outbound`、分支与完整 SHA 以生成事实块为准；当前唯一源码写者为本次 Codex chat，登记见 `.local/goal-20261018-resume/takeover11/writer-takeover.json`；未观察到其他本项目源码写者或子代理。
- 继承 WIP、handoff-final01、run03 失败原件和全部失败输入继续保全；不还原、覆盖、重排或 `git add .`。handoff 保护清单见 `.local/goal-20261017-resume/handoff-final01/`。
- 本次 takeover05 逐路径清单见 `history/GOAL_E08_RECOVERED_CONFIRMATION_WIP_AUDIT_2026-10-18_TAKEOVER05.md`：确认 recovered-confirmation worker/dialog/fixture/device-test/bridge 属取消范围，只读 display/reader 仍属 R09 候选。
- 本次 takeover08 复核清单见 `history/GOAL_E08_RECOVERED_CONFIRMATION_WIP_AUDIT_2026-10-18_TAKEOVER08.md`：逐路径确认取消链无生产 caller，R09 只读 display/reader 保留；未恢复换机承接、多跳或接收确认/激活。
- 本次 takeover10 逐路径清单见 `history/GOAL_E08_RECOVERED_CONFIRMATION_WIP_AUDIT_2026-10-18_TAKEOVER10.md`：按 D06/D07、D13/D14 与 R07 复核 worker/dialog/fixture/bridge 及 Handover/Sync/Temporary；取消链继续隔离，R09 只读 display/reader 保留。
- takeover07/O05/run902/run913 快照、脏项哈希、进程/监听/设备/磁盘见各自 `.local/goal-20261018-resume/`、`.local/goal-20261011/fault-restart-consumer-913/`；唯一写者仍为本聊天。
- 事实刷新已执行 `tools/version_facts.ps1 -Write/-Check`，当前区仍受脚本上限约束。
- 本次 fail-closed 修复的全量输入、运行日志、首轮失败 XML/HTML 和收口快照见 `.local/goal-20261018-resume/failclosed-fix-run01/`；专项报告见 `history/GOAL_E08_D17_FAIL_CLOSED_FIX_RUN01_2026-10-18.md`。
- E07/D06-D07 Web fail-closed 收口见 `history/GOAL_E07_D06_D07_WEB_FAIL_CLOSED_GATE_RUN01_2026-10-18.md`；默认隐藏入口，隔离运行器显式开启；Node/typecheck/build/脚本解析及 run913 Chrome/HTTP/MySQL/Android/Python 组合通过，合成身份不计 L2/L3。
- 独立消费者探测见 `history/GOAL_E08_D17_NEXT_INDEPENDENT_PROBE01_2026-10-18.md`：原件 14/14、closure 3/3、采集 6/6、Android 回执验签 4/4；加密包错配和 SQLite 同根因保全，仍不计 O02/O03/D17。
- run904 高频 UI 有界 L1 候选见 `history/GOAL_E08_D17_U04_HIGH_FREQUENCY_RUN904_L1_2026-10-18.md`：最新 Debug/AndroidTest APK 在独立 `Run904_Pixel6` 上通过准备页、首页、离线草稿扫码和保存扫码/手动确认/暂停恢复/重复包拒绝；真实隔离 HTTP/MySQL、Room、停机和 D 盘清理事实保全。合成输入不计 D17 整体、E06 L2、D18 L3 或总目标。
- run911 高频 UI 候选见 `history/GOAL_E08_D17_U04_HIGH_FREQUENCY_RUN911_L1_2026-10-18.md`：独立 Android instrumentation 通过准备、首页、离线草稿扫码及保存/恢复/重复包拒绝；隔离 HTTP/MySQL、Room、停机事实保全。合成输入不计 D17/E06 L2/D18 L3。
- U02 作业台状态语义修复见 `history/GOAL_E08_D17_U02_STATUS_RENDERER_L1_2026-10-18.md`：正式/TEMP 渲染器将待确认输入并入主状态行、队列待落盘独立提示；定向 Robolectric、`lintDebug` 和 Debug/AndroidTest 构建通过。新隔离 AVD 因恢复 tar 缺 `noBackupFiles/session.enc`，两条 Android 入口在登录前置处失败，未计设备 L1 或 D17 整体。
- run904 最终证据与状态在 `.local/goal-20261018-resume/high-frequency-904/`：最终输出、设备 tar/logcat、Room before/after、`final-db-state02.tsv`、`environment-final-{running,after-stop}.json`、`cleanup-{before,after}.json`；目标端口清零，USB M71 只读枚举，共享监听未操作。
- run911 证据与 `dirty-final-run911.json`/`tracked-final-run911.patch` 在 `.local/goal-20261018-resume/high-frequency-session-run911/`：四份 instrumentation、设备 tar、Room/DB、输入和环境快照保留；19341/19441/5566/5567 清零，USB M71 仅只读。清理前可重建目标 6,765,823,763 bytes，Windows 策略拒绝后零删除。
- run904 收口 Git 快照为 `dirty-post-run904.json`、`dirty-post-run904.zip`、`tracked-post-run904.patch`，记录 HEAD/远端、183 条路径及逐路径 SHA；未执行 `git add .`、还原或重排。
- takeover09/10 接管快照见各自 `.local/goal-20261018-resume/takeover*/environment-before.json`；共享/外部 3306/33060/33318/5173、ADB 5037 与 USB M71 仅观察未停止；handoff-final01 继承 98 条脏项、93 条保护项未覆盖。

## 已验范围与新增候选
- E08/O02 真机 SAF/USB 候选见 `history/GOAL_E08_O02_PHYSICAL_SAF_USB_2026-10-18.md`：实际 M71 通过 DocumentsUI `ACTION_CREATE_DOCUMENT` 选择 Download，生产 `RescueFileTransfer` 设备端回读与 USB ADB 主机回读均为同一 SHA、96,257 bytes；debug-only 合成包，不是 MTP/OTG/外置介质或真实业务回执，不计 O02/O05 整体或任何 L2/L3。
- E08/O02/O05 rescue 跨运行见 `history/GOAL_E08_O02_O05_CROSS_RUNTIME_2026-10-18.md`：Android 27 项、Python 核验/副本/中转 57 项通过；合成包，`externalStorageVerified=false`、`businessReceiptVerified=false`，不计 O02/O05 或 L2/L3。
- E08/O02 电脑核验与 XLSX 转换候选见 `history/GOAL_E08_O02_COMPUTER_XLSX_RUN08_2026-10-18.md`：合成包实际完成包外信任校验、XLSX 导出、五表逐值回读和 PNG 预览；CLI 管道入口与转换器失败半目录均保全，不计真实 Excel、外置介质、业务回执或 O02/O05 整体。
- E08/O05 外置保管边界复核见 `history/GOAL_E08_O05_EXTERNAL_CUSTODY_BOUNDARY_2026-10-18.md`：逐路径复核 M71 SAF/ADB 回读、同机加密副本、XLSX 消费和取消范围 WIP；`externalStorageVerified=false`、`businessReceiptVerified=false`、`localDeletionAllowed=false`，外控介质/独立保管方/真实回执仍缺，不计 O05 或任何 L2/L3。
- E07/D06-D07 run745 见 `history/GOAL_E07_D06_D07_WITHDRAWAL_RECOVERY_RUN745_2026-10-18.md`：全新隔离真实 HTTP/MySQL、非空迁移、撤回/重发、新设备从头作业、五截点重建、原 binlog 回放和认证停机均通过；身份与输入为合成隔离事实，未计 D06/D07 L2。
- E07/D06-D07 run902/run913 见对应历史报告：全新隔离非空迁移、真实 HTTP/MySQL、Pixel_6 四条 Android、Python 只读消费者和 Chrome 两项通过；Vite `ResizeObserver` 控制台错误已保全，合成输入不计 D06/D07 L2 或任何 L2/L3。
- E07/D06-D07 run744 见 `history/GOAL_E07_D06_D07_EMULATOR_HTTP_RUN744_2026-10-18.md`：全新隔离真实 HTTP/MySQL、非空迁移、实际 Pixel_6 emulator 四条 instrumentation、独立 Python 只读消费、Chrome 两项和认证停机均通过。
- 前序 run733、run741/run742、run743 和 1604-1608 仅按各自报告范围复用；D06/D07 仍缺原 PDA/现场身份、完整权限/实物和统一最终候选。

## 环境、失败与未验
- O02/O05 候选未启动服务、数据库或 emulator；本次真机使用 USB `22D2910042`，共享 `3306/33318/5173/8000` 只观察未停止，最终 M71/ADB/目标文件事实见 `.local/goal-20261018-resume/physical-saf-run07/`。`TemporaryAdjustment1364` 的 emulator/qemu 仍由外部进程监听 5564/5565，但 ADB 未列出 `emulator-5564`，未停止并保留所有者证据。
- O02/O05/run902 收口复核磁盘余量曾为 C=77,313,830,912、D=37,599,989,760、E=195,634,057,216 bytes；本次收口实测 C=75,552,821,248、D=19,515,752,448、E=195,634,057,216 bytes。XLSX、dump、trace、继承改动和失败证据继续保全；run902 隔离端口/模拟器已清零。
- recovered-confirmation 主机 fixture 同根因三次失败继续暂缓；本次两个既有 reader 类串行复跑仍命中同一 `FullSyncOpenHelper`/Robolectric `SQLiteCantOpenDatabaseException`，首轮与复跑原件均保全；旧 run03 不重启。D16 合法退出依据、D13/D14/D15 剩余、E06/E07/E08 L2、O01-O05/U01-U04 完整范围、统一最终候选及 D18 L3 均未满足。
- takeover08 fail-closed 回归及本次修复见 `history/GOAL_E08_D17_FAIL_CLOSED_REGRESSION_TAKEOVER08_2026-10-18.md`、`history/GOAL_E08_D17_FAIL_CLOSED_FIX_RUN01_2026-10-18.md`：默认关闭 gate 定向单测和 `lintDebug` 通过，所有 TEMP action 在副作用前被拒绝；不计 Android 触摸或任何 L2/L3。高频首页/扫码链仍因缺新的隔离准备、登录和可核验模拟器输入而未入队。
- run903 新绑定候选见 `history/GOAL_E08_D17_RUN903_FIXTURE_AUDIT_2026-10-18.md`：19436 真实 Spring、19330 非空 MySQL、独立 `Run903_Pixel6`/`emulator-5568` 上 `PreparationEndToEndTest` 1/0/0/0 通过，设备 evidence/Room 重开/丢响应恢复和数据库回读已留证；仅局部 Android/HTTP/MySQL，不计 D17、E06 L2 或 D18 L3。
- 本切片的 run-local 临时目录清理曾按授权尝试但被 Windows 执行策略拒绝，未绕过；日志、失败 XML/HTML、输入快照、APK 基线和继承改动继续保全，清理结果写入 `final-state.json`。
- 同一 run903 设备的高频首页 L1 实测因 `temporaryPreparation` 在无临时准备资料时为 GONE 失败；失败日志已保全，归因是输入/前置缺口。该失败不覆盖 run904 的新隔离候选；U04/D17 仍只计 run904 有界合成 L1，不计整体或 L2/L3。
- run903-preparation01 已收口：19436 服务 PID 18740、独立 `emulator-5568` 和 19330 已停止；共享 3306/33318、USB M71 仅观察。新建 AVD userdata 清理前清单/哈希与前后余量见 `.local/goal-20261018-resume/run903-preparation01/avd-cleanup-{before,after}.json`，释放 5,986,930,688 bytes；最终环境见 `post-stop-final.json`。
- run904 收口：服务 PID 18592、MySQL 19332/独立 datadir 和 `Run904_Pixel6` 已认证停机；可重建 AVD/datadir/storage 已按授权删除，释放 6,265,477,018 bytes。`environment-final-after-stop.json` 仅显示共享 3306/33318/5173/5175/8000 与 USB M71，源码、APK、失败原件和继承 WIP 保留。
- U02 run-local AVD `emulator-5620` 已停止；设备安装、无会话失败原文、logcat、输入 tar、APK 哈希和单测/lint 日志保存在 `.local/goal-20261018-resume/u02-status-renderer-run01/`。重建 datadir 无可清理字节，递归清理命令受 Windows 执行策略拒绝，未绕过；共享监听和 M71 未操作。
- 七类生产决定、TEMP 生产归档、`localDeletionAllowed=false`、原库/现网、正式签名、部署、Release 和 main 合并边界保持关闭；现场 PDA、真实 Keystore、第二设备、外置介质、长时/断电和生产审批按 C 档单列。
- 本切片快照/矩阵见 `.local/goal-20261018-resume/web-failclosed-run01/`，run913 组合证据见 `.local/goal-20261011/fault-restart-consumer-913/`；run-local 清理被 Windows 策略拒绝、释放 0 bytes；继承 WIP/失败证据未覆盖，下一步仍需可信原身份 L2 或真实外控介质。
- U03 host/SKU/native chain 报告见对应历史文件：隔离 HTTP/MySQL 上 Android 凭据、断网任务/扫描、DocumentsUI、末 SQL/重启恢复和撤权边界通过；合成输入不计 U03 完整/D17/E06 L2/D18 L3，阶段失败原件保留。

<!-- CURRENT_STATE_END -->

> 长期规则见AGENTS.md；版本事实只读生成块；历史从docs/history/README.md检索。
