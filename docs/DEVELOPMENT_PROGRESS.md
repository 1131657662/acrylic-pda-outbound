> **无状态文档**：本文不含当前版本或进度事实。版本四元组与断点只看[看板](ORCHESTRATION_STATUS.md)的生成事实块（由 `tools/version_facts.ps1` 生成）；下文出现的“当前/最新/现网为”等措辞都是写作时点的历史记录，不作为现行状态。

- 2026-10-19：新增 [U01/U04 独立采集剩余数与最近保存卡 L1](history/GOAL_U01_U04_INDEPENDENT_PROGRESS_L1_2026-10-19.md)。有效数、超计划、任务专属最近保存、VOID身份保留、Room快照和任务切换竞态均有合成Robolectric定向证据；7套件27项及引擎回归3项通过。未验Activity/IME/真机、真实HTTP/MySQL、D18最终候选和总目标闸门。

- 2026-10-19：新增 [U01/U04 独立采集首屏与次级面板 L1](history/GOAL_U01_U04_INDEPENDENT_FIRST_SCREEN_L1_2026-10-19.md)。核对/手输显式展开，候选动作按状态显示；保留失败与几何诊断，最终16项合成显示/布局测试和instrumentation编译通过。Activity/IME/设备及U01/U04/D17整体未验。

- 2026-10-19：新增 [U04 独立采集文本按变化更新 L1](history/GOAL_U04_INDEPENDENT_INCREMENTAL_RENDER_L1_2026-10-19.md)。队列变化保留未变的原任务、计划和明细，保存/分页正确更新；预修复3项反例失败，修复后连同声效回归5项通过。仅合成显示L1，设备性能、U04/D17整体和第8节未验。

- 2026-10-19：新增 [U04 独立作业队列满边界 L1](history/GOAL_U04_INDEPENDENT_QUEUE_OVERFLOW_L1_2026-10-19.md)。拒收后计数从错误的66恢复到65，预修复反例失败、修复后4项主机测试通过；全部65条已接收输入保留，未新增有效扫描。仅合成主机L1，不计 Activity/HTTP、U04/D17整体、D16/D18或第8节。

- 2026-10-19：新增 [U04 独立采集声效未就绪提示 L1](history/GOAL_U04_INDEPENDENT_SOUND_READINESS_L1_2026-10-19.md)。独立页消费 `WorkSound.error` 并与已保存文案同时显示；新增显示测试2/0/0/0通过。既有声音持久化组合因 Robolectric `SQLiteCantOpenDatabaseException` 保留失败，不计 U04/D17整体、D16/D18或第8节。

- 2026-10-19：新增 [U04 保留明细四字号 tar 只读审计](history/GOAL_U04_RETAINED_DETAILS_TAR_AUDIT_2026-10-19.md)。八份 tar 逐成员及 24 个 Room 成员独立哈希、WAL 逻辑表消费完成；Room 物理/逻辑守恒，差异为新增测试证据、目录 mtime、profileInstalled 和 WorkManager 调度字段。原 `archiveUnchanged=false` 保留，仅补有界 L1，不计 U04/D17 整体、L2/L3 或第8节。

- 2026-10-19：新增 [U04 TEMP 队列满边界 L1](history/GOAL_U04_TEMP_QUEUE_OVERFLOW_L1_2026-10-19.md)。在隔离 TEMP 原生队列填满并拒收第 66 次输入，预修复对照按预期暴露计数错误；修复后 4 项主机测试通过，65 条已接收输入全部持久化。仅合成 U04 L1，不计 U04/D17 整体、真实 HTTP/MySQL、D16/D18 或第8节。

- 2026-10-19：新增 [D06/D07 五截点恢复事实 Python 独立消费](history/GOAL_E07_D06_D07_RECOVERY_PYTHON_CONSUMER_2026-10-19.md)。实际消费 run1615 的 57 个固定输入，五份重建与最终全表/私有文件一致，隔离签名、旧资格拒绝及逐包记账核对通过；17 项变异测试与错误 SHA CLI 拒绝通过。仅 Python L1，未重新实跑 Android/Chrome/HTTP/MySQL，不计 D 项整体、L2/L3 或第8节。

- 2026-10-19：新增 [E07/D06-D07 故障撤回、重发与恢复隔离切片](history/GOAL_E07_D06_D07_FAULT_WITHDRAWAL_L2_SLICE_2026-10-19.md)。run1615 的五截点真实 HTTP/MySQL/binlog 恢复 `1/0/0/0`；run998/999 的撤回、重发、权限、幂等、异文、并发、末条 SQL 回滚、可靠性和 default-off 业务测试合计 `19/0/0/0`，隔离账号认证停机、dump 和监听清零均保全。合成身份、隔离密钥和无 Android/Chrome/Python/原模拟器输入，仍不计 D06/D07 完整 L2、E07/D17、E06 L2、D18 L3 或总目标。

- 2026-10-19：新增 [E08/D17 fail-closed gate L1 run02](history/GOAL_E08_D17_FAIL_CLOSED_GATE_RUN02_2026-10-19.md)。接管后复跑 `TemporaryHandoverActionGateTest` 2/0/0/0、`lintDebug` 退出 0，并确认两个已取消确认 worker 在 Android 主源码无后台 caller；只补强默认关闭证据，不计 D17、E06 L2、D18 L3 或第 8 节。
- 2026-10-19：新增 [E08/D17 服务端 fail-closed 配置 L1 run01](history/GOAL_E08_D17_FAIL_CLOSED_SERVER_RUN01_2026-10-19.md)。既有恢复确认配置测试 3/0/0/0、TEMP 确认配置测试 2/0/0/0；默认入口、仅 commands 开关和缺少独立信任均保持 fail-closed。只补强配置层证据，不计 D17、E06 L2、D18 L3 或第 8 节。

- 2026-10-19：新增 [E08/D17/U04 高频 Room Python 消费 L1](history/GOAL_E08_D17_U04_HIGH_FREQUENCY_PYTHON_CONSUMER_L1_2026-10-19.md)。对 run925 的准备页、首页、`scan-device.tar` 与 `save-scan-device.tar` 执行独立只读 SQLite 消费，完整性/外键/准备请求五分片/capture关系/重复输入确认和取消链 0 均通过；输入 SHA 未变，敏感回执只保留摘要，仍只计 U04/D17 有界 L1，不计 D17、E06 L2、D18 L3 或第 8 节。

- 2026-10-19：新增 [E08/D17/U02 结果未知页面真实 HTTP Android 候选 run1611](history/GOAL_E08_D17_U02_RESULT_UNKNOWN_ANDROID_HTTP_RUN1611_2026-10-19.md)。独立 Pixel_6 实际消费新隔离 fixture，真实 HTTP/MySQL 完成设备登记、准备、冻结、丢响应、结果未知页原批次查询、审批恢复和 Room 重开；instrumentation `OK (1 test)`，认证 dump/停机通过。仅有界 U02/D17 Android L1，不计 U02/D17 整体、E06 L2、D18 L3、§8 或总目标。

- 2026-10-19：新增 [E08/D17 recovered-confirmation 当前接管审计 takeover12](history/GOAL_E08_RECOVERED_CONFIRMATION_WIP_AUDIT_2026-10-19_TAKEOVER12.md)。在 `9c6feaffe39633206734accac7c945a4b4bfcc1f` 输入上重新绑定逐路径哈希、进程/监听、ADB 和磁盘事实；复核 D06/D07、D13/D14、R07 边界、`SyncWorker` 负向 caller、Android/服务端 default-off 及测试 bridge。确认取消链仅保留源码/失败资料，R09 只读展示仍可保留；不计 D06/D07/D13/D14、D17、E06 L2、D18 L3 或总目标。

- 2026-10-19：新增 [E08/D17/U04 模拟器隔离启动器修复](history/GOAL_E08_D17_U04_EMULATOR_ISOLATION_FIX_2026-10-19.md)。高频运行器现在复制完整 AVD 到独立 D 盘 `ANDROID_AVD_HOME`，排除锁/快照，核对进程 datadir 绑定并在停机后验证默认源镜像未变；run05 启动/停机探针通过，但尚未安装 APK 或执行业务 Android L1，不计 U04、D17、E06 L2、D18 L3 或总目标。

- 2026-10-19：新增 [U02/U04 本单资格说明页原生 L1](history/GOAL_U02_U04_TASK_QUALIFICATION_L1_2026-10-19.md)。正式/TEMP 作业入口实际显示可看、可扫、可结、可传、可导出边界；重启阻断、正常产品连接校时、无此单拒绝、四档字号/安全区/重建和 83 表 Room＋WAL 消费通过。仅有界 Android L1 开发备份，不计 U02/U04 完整、D17、E06 L2、D18 L3 或总目标。

- 2026-10-19：新增 [U02 正式待确认与保存状态原生 L1](history/GOAL_U02_FORMAL_PENDING_STATUS_NATIVE_L1_2026-10-19.md)。真实 HTTP 发布/领取/准备、实际正式作业页确认前后及重建通过；原 submission/TEMP 表守恒、旧拒绝输入正常确认后保留，四次失败及调度偏差完整保全。仅状态专项 L1，不计 U02/D17 整体、E06 L2、D18 L3 或总目标。

- 2026-10-19：新增 [U02 TEMP 待确认与耐久保存状态原生 L1](history/GOAL_U02_TEMP_PENDING_STATUS_NATIVE_L1_2026-10-19.md)。保留隔离身份经产品连接检查重校时间，实际待确认→确认保存→重建通过；失败可信时间阻断的 83 表及最终包守恒通过。正式状态页另验，不计 U02/D17 整体、E06 L2、D18 L3 或总目标。

- 2026-10-19：新增 [O01/U04 原记录筛选与大字确认有界 L1](history/GOAL_O01_U04_FILTER_L1_2026-10-19.md)。原序号/产品筛选、非法条件重建、四字号、200% 实际确认及正式单冻结禁用通过；首轮既有 SQLite 失败、运行器失败与调度偏差原件保全。仅本子项开发备份，不计 O01/U04 完整、D17、E06 L2、D18 L3 或总目标。

- 2026-10-18：新增 [E07/D06-D07 故障撤回 fail-closed 隔离消费者组合（run913）](history/GOAL_E07_D06_D07_FAIL_CLOSED_COMBINATION_RUN913_2026-10-18.md)。新隔离非空迁移、真实 HTTP/MySQL、四条 Android、Python 只读消费者和 Chrome 两项通过；运行器显式隔离开关已实际消费，合成身份仍不计 D06/D07 L2、D17、E06 L2、D18 L3 或总目标。
- 2026-10-18：新增 [E08/D17 下一独立消费者探测](history/GOAL_E08_D17_NEXT_INDEPENDENT_PROBE01_2026-10-18.md)。同批保存原件/closure 输入上 `RelayIndependentOriginalTest` 14 项、closure 3 项、采集语义 6 项和 Android `RelayReceiptVerifierTest` 4 项通过；加密 `.pdarescue` 错配与既有 SQLite 同根因失败均保全，不计 O03/O02、D17、E06 L2、D18 L3 或总目标。

- 2026-10-18：新增 [E08/U03 独立 SKU 产品页面 L1 run01](history/GOAL_E08_U03_SKU_UI_L1_RUN01_2026-10-18.md)。全新隔离 AVD 实际消费 DocumentsUI、重复确认、坏行取消、重启原件守恒及 100%/200% 布局；合成资料仅计 U03 页面 L1 开发备份，不计 U03 完整、D17、E06 L2、D18 L3 或总目标。

- 2026-10-18：新增 [E08/D17 recovered-confirmation WIP takeover10 逐路径审计](history/GOAL_E08_RECOVERED_CONFIRMATION_WIP_AUDIT_2026-10-18_TAKEOVER10.md)。按 D06/D07、D13/D14 与 R07 重新绑定 worker/dialog/display/device test/fixture/bridge 及 Handover/Sync/Temporary 调用事实；取消的换机承接、多跳、接收者确认/激活链继续隔离，R09 只读展示保留，不计 D17、E06 L2、D18 L3 或总目标。

- 2026-10-18：新增 [E08/D17/U02 作业台状态语义修复 L1](history/GOAL_E08_D17_U02_STATUS_RENDERER_L1_2026-10-18.md)。正式/TEMP 渲染器区分待确认输入与队列待落盘，定向 Robolectric、lint 和 APK 构建通过；隔离 Android 因 tar 缺少登录会话在入口前失败，不计 D17/U02 Android L1、E06 L2、D18 L3 或总目标。

- 2026-10-18：新增 [E08/D17/U04 高频 UI 隔离候选 run904](history/GOAL_E08_D17_U04_HIGH_FREQUENCY_RUN904_L1_2026-10-18.md)。最新 Debug APK 在独立 `Run904_Pixel6` 上实际通过准备、首页、离线草稿扫码及保存/扫码/手动确认/暂停恢复/重复包拒绝，真实隔离 HTTP/MySQL、Room、停机和清理事实保全；输入为合成隔离资料，仅有界 L1 候选，不计 D17 整体、E06 L2、D18 L3 或总目标。

- 2026-10-18：新增 [E08/D17/U04 高频 UI 隔离候选 run911](history/GOAL_E08_D17_U04_HIGH_FREQUENCY_RUN911_L1_2026-10-18.md)。独立 `U02Session911` 上准备、首页、离线草稿扫码及保存扫码/手动确认/暂停恢复/重复包拒绝均由实际 Android 产品 instrumentation 报告 `OK (1 test)`；真实隔离 HTTP/MySQL、Room、`server_state=NORMAL`、Flyway 99 和认证停机事实保全。`adb` 包装器码为 -1，输入为合成隔离资料，清理命令被 Windows 策略拒绝且未绕过；仅有界 L1 候选，不计 D17 整体、E06 L2、D18 L3 或总目标。

- 2026-10-18：新增 [E08/D17 recovered-confirmation WIP takeover08 逐路径审计](history/GOAL_E08_RECOVERED_CONFIRMATION_WIP_AUDIT_2026-10-18_TAKEOVER08.md)。当前树复核取消的接收确认/激活链无生产 caller，R09 display/reader 仅保留只读候选；不恢复换机承接、多跳或确认链。
- 2026-10-18：新增 [E08/D17 fail-closed 回归](history/GOAL_E08_D17_FAIL_CLOSED_REGRESSION_TAKEOVER08_2026-10-18.md)。当前树只读恢复卡片/TEMP reader 单测、Debug/AndroidTest 构建和 lint 通过；高频首页/扫码链仍缺新的隔离准备、登录和可核验模拟器输入，不计 D17、E06 L2 或 D18 L3。
- 2026-10-18：新增 [E08/D17 TEMP fail-closed 修复 run01](history/GOAL_E08_D17_FAIL_CLOSED_FIX_RUN01_2026-10-18.md)。默认关闭 gate 单测与 `lintDebug` 通过；既有 reader 主机复跑继续命中 `FullSyncOpenHelper`/Robolectric `SQLiteCantOpenDatabaseException`，已保全并按同根因暂缓，不计 D17、E06 L2 或 D18 L3。

- 2026-10-18：新增 [E07/D06-D07 Web 故障撤回 fail-closed 入口收口 run01](history/GOAL_E07_D06_D07_WEB_FAIL_CLOSED_GATE_RUN01_2026-10-18.md)。Web 入口现仅在显式隔离 `VITE_FAULT_WITHDRAWAL_ENABLED=true` 构建显示；默认构建保留原记录且不自动恢复未决对话框。Node 纯函数测试、类型检查、默认/显式开启/恢复默认构建和运行器解析通过；未执行真实 Chrome/HTTP/MySQL 组合，不计 D06/D07 L1/L2 或总目标。

- 2026-10-18：新增 [E07/D06-D07 撤回重发消费者组合（run902）](history/GOAL_E07_D06_D07_CONSUMER_COMBINATION_RUN902_2026-10-18.md)。新隔离 attempt 完成非空迁移、真实 HTTP/MySQL、Pixel_6 emulator 四条 Android、Python 只读消费者和 Chrome 两项；合成身份仍不计 D06/D07 L2、E07/D17 整体、E06/E08 L2、D18 L3 或总目标。

- 2026-10-18：新增 [E08/O05 外置保管边界复核](history/GOAL_E08_O05_EXTERNAL_CUSTODY_BOUNDARY_2026-10-18.md)。逐路径绑定 M71 SAF/ADB 回读、同机加密副本、XLSX 电脑消费者及 recovered-confirmation 取消范围；`externalStorageVerified=false`、`businessReceiptVerified=false`、`localDeletionAllowed=false` 保持，外控介质/独立保管方/真实回执缺口未关闭，不计 O05、D16、D17、E06/E07/E08 L2 或 D18 L3。

- 2026-10-18：新增 [E08/O02 电脑核验与 XLSX 转换候选（run08）](history/GOAL_E08_O02_COMPUTER_XLSX_RUN08_2026-10-18.md)。对 Android 生成的合成应急包实际解密、包外信任校验、XLSX 导出、逐值回读和 PNG 预览；5 个工作表无公式且全部匹配。CLI 交互入口的管道输入失败及转换器失败半目录均保全；不覆盖真实 Excel、MTP/OTG、外置介质、真实身份/回执或 O02/O05、D18 L3。

- 2026-10-18：新增 [E08/O02 真机 SAF 与 USB 主机回读候选](history/GOAL_E08_O02_PHYSICAL_SAF_USB_2026-10-18.md)。连接 M71 实际选择 DocumentsUI `Download`，生产 `RescueFileTransfer` 回读和 USB ADB 主机 SHA 均为 `052B3F…156C6`、96,257 bytes；debug-only 合成包，不是 MTP/OTG/外置介质或真实业务回执，不计 O02/O05 整体、E08/D17、E06 L2、D18 L3 或总目标。

- 2026-10-18：新增 [E08/O02/O05 rescue 跨运行时候选](history/GOAL_E08_O02_O05_CROSS_RUNTIME_2026-10-18.md)。Android rescue 格式定向 27 项与 Python 电脑核验/副本/中转测试 57 项均通过；Android 生成的合成包完成独立解密验签、只读资料输出、加密副本创建和副本外摘要取回。`externalStorageVerified=false`、`businessReceiptVerified=false`、`localDeletionAllowed=false`；未覆盖真实 PDA/原身份/外置介质/USB 文件通道，不计 O02/O05 整体、E06/E07/E08 L2、D18 L3 或总目标。

- 2026-10-18：新增 [E07/D06-D07 撤回重发 Android/HTTP/Chrome 组合（run744）](history/GOAL_E07_D06_D07_EMULATOR_HTTP_RUN744_2026-10-18.md) 与 [recovered-confirmation WIP takeover05 逐路径审计](history/GOAL_E08_RECOVERED_CONFIRMATION_WIP_AUDIT_2026-10-18_TAKEOVER05.md)。run744 的隔离 HTTP/MySQL、实际 emulator 四条 instrumentation、Python 只读消费和 Chrome 两项通过；合成身份/输入不计 D06/D07 L2、E07 整体、E06/E08 L2、D18 L3 或总目标。旧看板当前区已归档至 [run744 前快照](history/ORCHESTRATION_STATE_before_run744_20261018.md)。
- 2026-10-18：新增 [E07/D06-D07 撤回重发恢复候选（run745）](history/GOAL_E07_D06_D07_WITHDRAWAL_RECOVERY_RUN745_2026-10-18.md)。新隔离 HTTP/MySQL 输入完成撤回、旧资格/异文拒绝、重新发布、新设备从头作业、五截点重建与原 binlog 回放；集成 1/0/0/0、179 表/2 文件事实和认证停机通过。身份为合成隔离事实，未覆盖 Android、Chrome、Python、原模拟器、现场身份或外置介质，不计 D06/D07 L2、E07/D14 整体、O05、D18 L3 或总目标。
- 2026-10-18：run744 清理回执见 [D 盘清理记录](history/D_DISK_CLEANUP_RUN744_2026-10-18.md)。已认证停机且无消费者的可重建 MySQL datadir 已删除；输入、dump、XML、日志、trace、APK 和继承 WIP 未清理。

- 2026-10-18：新增 [E06/D12 TEMP 仓库恢复组合候选（run743）](history/GOAL_E06_D12_TEMP_REPOSITORY_RUN743_2026-10-18.md)。当前源码在全新隔离实例完成真实 HTTP/MySQL、非空迁移、两阶段 Android 产品 JVM 测试和独立 Python 75 项检查；Chrome 因无 Web/UI 入口不适用。合成身份/开发签名/非原模拟器不替代现场，仍不计 E06 整体、D12/D17、E07、D18 L3 或总目标。

- 2026-10-18：新增 [D13 恢复事务与导出浏览器组合（run742）](history/GOAL_D13_RECOVERY_WRITER_RUN742_2026-10-18.md)。当前工作树在全新隔离实例上完成真实 HTTP/MySQL、非空迁移、恢复事务故障路径、导出/renderer、内置只读检查和 Chrome 入口；集成 1/0/0/0、浏览器 1 passed、认证停机且端口清零。合成身份/密钥和测试浏览器不替代 Android、原 PDA、现场回执或最终候选，不计 D13 整体、E06/E07/E08 L2、D18 L3 或总目标。

- 2026-10-18：新增 [E07/D06-D07 Android、真实 HTTP、Chrome 与 Python 消费候选（run1606）](history/GOAL_E07_D06_D07_ANDROID_HTTP_WEB_RUN1606_2026-10-18.md)。run1604 的 runner stderr 失败与 run1605 的应用绑定残留失败原件保留；run1606 四条 Android 方法、Chrome 两项、Python 只读消费者和隔离数据库停机通过。输入和身份为合成隔离事实，不计 D06/D07 L2、D17、E06 L2、D18 L3 或总目标。
- 2026-10-18：新增 [E07/D06-D07 非空迁移候选（run1607/run1608）](history/GOAL_E07_D06_D07_NONEMPTY_MIGRATION_RUN1608_2026-10-18.md)。run1607 非空迁移断言失败原件保留；run1608 provision、非空迁移、dump 和认证停机通过。仅局部隔离候选，不计 D06/D07 L2、D17、E06 L2、D18 L3 或总目标。

- 2026-10-18：新增 [E08/D17 Android 真实 HTTP 候选（run1600）](history/GOAL_E08_D17_ANDROID_HTTP_RUN1600_2026-10-18.md)。非空 V34→V36、真实隔离 HTTP/MySQL、R09 临时来源 Android 主机恢复和认证停机通过；Android 为 Robolectric，不计 D06/D07、D17 整体、E06 L2 或 D18 L3。

- 2026-10-18：新增 [E08/D17 recovered-confirmation WIP 接管增量审计](history/GOAL_E08_RECOVERED_CONFIRMATION_WIP_AUDIT_2026-10-18_TAKEOVER02.md)。逐路径核对取消的接收确认/激活链与 R09 只读展示边界，保留继承 WIP、默认关闭和旧失败证据；不恢复旧换机承接，不计 D17、D14、E06 L2、D18 L3 或总目标。

- 2026-10-18：新增 [E07/D06-D07 撤回重发入口审计](history/GOAL_E07_D06_D07_ENTRY_AUDIT_2026-10-18.md)。确认后台撤回、权限、旧资格失效、占用释放、重发布和新设备领取的可复用入口；run725 仍只属服务端隔离候选，Web/Android/真实组合和 L2 未验。

- 2026-10-18：登记 [E07/D14 Android 与真实 HTTP 运行失败（run728）](history/GOAL_E07_D14_ANDROID_HTTP_RUN728_FAILURE_2026-10-18.md)、[run729](history/GOAL_E07_D14_ANDROID_HTTP_RUN729_FAILURE_2026-10-18.md)、[run730](history/GOAL_E07_D14_ANDROID_HTTP_RUN730_FAILURE_2026-10-18.md)、[run731](history/GOAL_E07_D14_ANDROID_HTTP_RUN731_FAILURE_2026-10-18.md)、[run732](history/GOAL_E07_D14_ANDROID_HTTP_RUN732_FAILURE_2026-10-18.md)。五个隔离候选均有 Android/集成失败 XML、HTTP 日志、输入 SHA 和 MySQL 认证停机事实；不重跑、不计 E07/D14/D17 或任何 L2/L3。
- 2026-10-18：新增 [E07/D06-D07 撤回重发服务端组合（run733）](history/GOAL_E07_D06_D07_WITHDRAWAL_RECOVERY_RUN733_2026-10-18.md)。全新隔离实例完成真实 HTTP/MySQL、坏机离线有权撤回、旧资格/异文拒绝、同键幂等、重新发布新轮次成功、五恢复检查点和认证停机；未执行 Android/Chrome/外部 Python/原模拟器/现场身份，不计 D06/D07 L2、E07/D14 整体、D18 L3 或总目标。

- 2026-10-18：新增 [E07/D06-D07 Android 与真实 HTTP 候选（run735）](history/GOAL_E07_D06_D07_ANDROID_HTTP_RUN735_2026-10-18.md)。新隔离输入上 `FaultRestartConsumerFixtureIT` 1/0/0/0；显式 `emulator-5568` 四条 Android 方法通过，含实际 `am force-stop` 后恢复，应用 JSON、logcat、直接 instrumentation XML、MySQL dump 与认证停机事实保全。首次默认关闭及 Gradle 误枚举实体 m71 的失败原件保留；不计 D06/D07 L2、D17、E06 L2、D18 L3 或总目标。

- 2026-10-18：新增 [E07/D06-D07 Chrome 消费者组合（run739）](history/GOAL_E07_D06_D07_WEB_CONSUMER_RUN739_2026-10-18.md)。当前源码独立服务包、非空迁移、真实 HTTP/MySQL 与 Chrome 两项均通过，覆盖撤回/发布丢响应、刷新后原键重试和浏览器存储失败反例；run736旧包、run737运行器参数和run738 route断言失败均保留，不计 D06/D07 L2、D17、E06 L2、D18 L3 或总目标。

- 2026-10-18：新增 [E07/D14 Android 与真实 HTTP 运行失败（run727）](history/GOAL_E07_D14_ANDROID_HTTP_RUN727_FAILURE_2026-10-18.md)。隔离非空 V34→V36、Spring HTTP/MySQL 和 Android 均已启动，但首次读取因 `SubmissionStore.find` 静态 JOIN V36 尚不存在的后续表而失败；未发生业务提交写入，隔离库已认证停机。失败原件保留，不重跑，不计 E07/D14/D17、E06 L2、D18 L3 或总目标。

- 2026-10-18：新增 [E08/D17/U04 高频首页隔离启动失败保全](history/GOAL_E08_D17_U04_HIGH_FREQUENCY_RUNNER_FAILURES_2026-10-18.md)。run02/run03/run04 均未形成独立 ADB 设备，失败日志和 userdata 保全；未修改产品或测试前置，不计 U04、D17、E06 L2、D18 L3 或总目标。

- 2026-10-18：新增 [E07/D14 独立检查点 Python 消费补验](history/GOAL_E07_D14_INDEPENDENT_CHECKPOINT_PYTHON_CONSUMER_2026-10-18.md)。run722 只读消费者退出 0，输入 manifest 前后不变且非空快照一致；仅补齐 Python 消费证据，不计 E07/D14 L2、D18 L3 或总目标。

- 2026-10-18：新增 [E08/D17 recovered-confirmation 主机边界复验失败保全](history/GOAL_E08_RECOVERED_CONFIRMATION_HOST_TEST_FAILURE_2026-10-18.md)。三次定向主机复验均在 fixture 建库阶段命中同一 `SQLiteCantOpenDatabaseException`，未发生产品写入；按同根因三次规则暂缓，不覆盖既有范围审计结论。

- 2026-10-18：新增 [E08/D17 Android 真实 HTTP 候选运行器失败（run726）](history/GOAL_E08_D17_ANDROID_HTTP_RUN726_FAILURE_2026-10-18.md)。隔离 MySQL/Flyway/Spring 启动并认证停机；Android 夹具因固定 `baseUrl` 与运行时 HTTP 端口不一致在构造阶段退出，未发生产品业务写入。已修复测试输入生成器并完成显式隔离端口编译验证；修复后的 Android/真实 HTTP 组合待全新 attempt，不计 D17、E06 L2、D18 L3 或总目标。

- 2026-10-18：新增 [E07/D14 撤回重发恢复五检查点组合（run725）](history/GOAL_E07_D14_WITHDRAWAL_RECOVERY_RUN725_2026-10-18.md)。真实隔离 HTTP/MySQL、旧资格拒绝、撤回回执幂等、重新发布新轮次、五检查点 SQL/文件/原 binlog 事实及认证停机通过；未执行 Android/Chrome/外部 Python/原模拟器/现场身份，不计 E07/D14 L2、O05、D18 L3 或总目标。

- 2026-10-18：新增 [E07/O05 TEMP 与 FORMAL 归档恢复组合（run724）](history/GOAL_E07_O05_ARCHIVE_RECOVERY_RUN724_2026-10-18.md)。run724 的真实隔离 HTTP/MySQL、非空迁移、TEMP/FORMAL 归档 SQL/文件恢复、审批闭合、11 项故障注入、源/恢复独立 Python 消费和认证停机通过；未执行 Android/Chrome/原模拟器/外置介质，不计 E07 L2、D14/O05 整体、D18 L3 或总目标。

- 2026-10-18：新增 [E07/D14 独立检查点恢复组合（新隔离候选）](history/GOAL_E07_D14_INDEPENDENT_CHECKPOINT_COMBINATION_2026-10-18.md)。run 722 的真实隔离 HTTP/MySQL、非空迁移、原件/签名、关联默认关闭、恢复前后快照、原件字节回放、目标订单不处理和认证停机通过；合成输入、未执行 Android/Chrome/Python/原模拟器，范围仅开发候选，不计 E07 L2、D14 整体、D18 L3 或总目标。

- [D13 原件中转收存文件入口 L1](history/GOAL_D13_RELAY_FILE_ENTRY_2026-10-17.md)：1571普通89项+真实IT1项、75 HTTP/6 ZIP签名及原下载/SQL/P5独立消费通过；仅原件收存、不新增提交账务，非整体/L2/L3。
- [D13 库存文件入口 L1](history/GOAL_D13_IMPORT_FILE_ENTRY_2026-10-17.md)：run1569真实multipart/非空迁移/权限/幂等/并发/末SQL回滚/锁后撤权、原生writer锁及Python原HTTP/文件/dump/P5消费通过；1568产品失败保留。仅服务端切片L1，普通完整回归4756项/零失败错误/19条件跳过，非D13整体/L2/L3。
- [D13 导出重试效果门兼容 L1](history/GOAL_D13_EXPORT_RETRY_2026-10-17.md)：run1567普通82项/IT1项、default-off/P5真实HTTP、并发/末SQL回滚/锁后撤权、10份导出及独立Python消费通过；1565/1566和消费者环境失败保留，仅服务端切片L1，非D13整体/L2/L3。

- [D13 正常P5退役有界L1](history/GOAL_D13_NORMAL_RETIREMENT_2026-10-17.md)：run1564普通定向126项/IT4项、真实P5 HTTP/Session→生产调度→五导出→排空/后继及独立Python通过；修改后完整普通4756项零失败/错误、19条件跳过。1560/1562/1563产品失败和消费者首次失败保留，仅本切片L1，非D13整体/L2/D18L3。
- [D13 renderer未确认排空围栏](history/GOAL_D13_RENDERER_HANDOFF_2026-10-17.md)：run1559定向20项/IT2项及独立消费通过；真实子JVM halt70/后继孤儿拒绝，回执丢失为MODEL。仅该有界L1，正常P5退役另见接续报告，整体组合仍未完成。

- [D13 生产调度恢复准入](history/GOAL_D13_WORKER_ADMISSION_2026-10-17.md)：run1557 默认关闭/准入后真实HTTP、生产调度、XLSX/PDF及独立Python消费通过；普通失败末SQL回滚/持根阻推进/旧权威拒绝，仍仅有界L1，未确认排空围栏另列。

- [D15 非空升级/程序回退/升级前备份恢复组合](history/GOAL_D15_UPGRADE_ROLLBACK_COMBINATION_2026-10-17.md)：run1554 同一隔离输入七个 JAR JVM/56次真实 HTTP、非空迁移、空伴随恢复、精确认证增量和 Python 原文/dump 消费通过。1552/1553及consumer01失败保留；仅有界组合开发备份，非D15整体/E07完整L2/D18 L3。

- [E06/D12 TEMP 仓库恢复 L2 候选](history/GOAL_E06_D12_TEMP_REPOSITORY_L2_ACCEPTANCE_2026-10-17.md)：run 692 真实隔离 HTTP/MySQL、非空 Flyway、Android 两阶段产品会话、丢响应 `RESULT_UNKNOWN`→原 ID `SYNCED`、SQL/文件/JWS/Room 守恒及独立 Python 75 项消费通过；run 691 固定端口夹具失败原件保留。Chrome 对该无 Web/UI 入口路径不适用；仅局部候选，不计 E06/D12/D17 完整 L2、D18 L3 或总目标。

- [E08/D17 `CONTINUATION_BLOCKED` 只读明细 L1](history/GOAL_E08_D17_CONTINUATION_BLOCKED_READONLY_L1_2026-10-17.md)：修复前驱明细页错误显示取消/调整入口；主机定向单测、lint、Debug 构建和独立 AVD instrumentation 通过，前后数据库字节相同。两次运行器/Launcher 环境失败原件保留；仅合成输入上的 Android UI L1 开发备份，不计 D17 整体、E06 L2、D18 L3 或总目标。

- [E08/D17 recovered-confirmation WIP 逐路径范围审计](history/GOAL_E08_RECOVERED_CONFIRMATION_WIP_AUDIT_2026-10-17.md)：接管后补充完整路径、调用事实、SHA、处置建议与直接验证；三类主机边界测试补验零失败；按 R07/R09 确认旧接收者确认/激活链已取消，只读恢复资料和普通阻断导航保留候选。仅范围审计/边界自审，不计 D06/D07/D13/D14、D17、E06 L2、D18 L3 或总目标。
- [E08/D17 recovered-confirmation WIP 接续补验](history/GOAL_E08_RECOVERED_CONFIRMATION_WIP_AUDIT_2026-10-18.md)：当前 HEAD 复核逐路径范围，三类主机边界测试补验零失败；默认开关关闭且无确认 POST/队列，取消接收/激活链不恢复。仅边界自审，不计 D17、D14、E06 L2、D18 L3 或总目标。

- [E08 recovered-confirmation 范围审计](history/GOAL_E08_RECOVERED_CONFIRMATION_SCOPE_AUDIT_2026-10-17.md)：接管后逐路径核对 Android worker/dialog/fixture/bridge 与 R07/R09；确认真实确认 POST、确认队列及主管激活等待属于已取消旧接收者链，只读恢复资料展示和普通阻断导航保留候选复用。仅范围审计，不计 D06/D07/D13/D14、UI L1、E08 L2 或 D18 L3。

- [E08/D17 HANDOVER_BLOCKED 只读明细收口](history/GOAL_E08_D17_HANDOVER_BLOCKED_READONLY_2026-10-17.md)：阻断入口明确为只读原件核对，隐藏取消/调整写入口，保留导出、分页、定位和返回；明细单测、Debug 构建/lint 通过。未安装或跑独立 instrumentation，UI L1、D17 整体、E06 L2、D18 L3及总目标仍未完成。

- [E08 / D17 UI构建、清理与最终交接](history/GOAL_E08_D17_UI_CLOSEOUT_HANDOFF_2026-10-17.md)：默认关闭/隔离构建和lint通过，业务UI未验、范围待核；旧D06命名已更正，Force停机及一次D盘清理有记录。仅文档/构建开发备份，交接后停止，非UI L1或D项整体/L2/L3。

- 2026-10-17：新增 [E07/D14 独立检查点恢复组合](history/GOAL_E07_D14_INDEPENDENT_CHECKPOINT_COMBINATION_2026-10-17.md)。run 814 的真实隔离 HTTP/MySQL、恢复前后事实、非空迁移、原件/签名、独立 Python 只读消费者和认证停机通过；854 文件 manifest 未漂移，恢复前后 179 表/622 行一致。run 813 的 `Attempt already exists` 原件保留。范围仅受影响组合开发验收，不计 E07 L2、D14 整体、D18 L3 或总目标。

- [D13 恢复受影响组合 L2](history/GOAL_D13_RECOVERY_COMBINATION_L2_2026-10-17.md)：run 805/806/812 的真实 HTTP/MySQL、非空迁移、原/未提交占用、事务与拒绝路径、renderer/export 及 Chrome 通过；Android、完整外部 Python、原模拟器和 D18 L3 仍未覆盖。

- [E06 多任务关联网页 L1 接续复核](history/GOAL_E06_MULTITASK_ASSOCIATION_WEB_L1_REVIEW_2026-10-17.md)：历史输入与八项日志、消费者、Chrome、180 表和 1538 保全证据复核完整；当前工作区输入漂移已单列。仅 L1 开发备份，非 E06 L2/D18 L3。

- [D15旧程序定时worker兼容 L1](history/GOAL_D15_OLD_WORKER_COMPATIBILITY_L1_2026-10-17.md)：新程序真实HTTP创建→旧worker实际执行→旧/新下载及Python七表/全表守恒通过；原driver exit1、消费者离线补核和676失败保留。仅ORIGINAL_CONFIRMED/XLSX有界L1开发备份，非D15整体/L2/L3。

- [TEMP产品会话与原ID恢复 L1](history/GOAL_E06_TEMP_REPOSITORY_RESTORE_L1_2026-10-17.md)：真实会话/仓库丢GET响应→UNKNOWN→重开及SQL恢复后原ID查询、73项Python消费通过；仅合成隔离L1开发备份，未提交运行器依赖和历史失败单列，非整体L2/L3。

- [D01必做范围与剩余验收矩阵](history/GOAL_D01_REQUIREMENT_EVIDENCE_MATRIX_2026-10-17.md)：27项/60报告SHA绑定，区分历史适用、旧失败、开发缺口、L2/L3和现场；盘点不计业务通过。

- [D17/U04实际作业Activity生命周期](history/GOAL_D17_ACTIVITY_LIFECYCLE_COMBINATION_2026-10-17.md)：FORMAL/TEMP四原生阶段、实际按钮与新PID原ID恢复及Python全表消费通过；原83表328行/491全部文件保持。合成身份/时间，有界测试组合开发备份，非整体L2/L3。

- [D17/U04原生广播暂停与跨进程原ID组合](history/GOAL_D17_NATIVE_FOREGROUND_QUEUE_2026-10-17.md)：FORMAL/TEMP四原生阶段、7项主机及Python全表守恒通过；原ID唯一保存/重复拒绝，原83表328行保持，失败副本完整保留。仅测试组合开发备份，非Activity生命周期、整体L2或D18 L3。

- [U04原生反馈异常与原ID恢复 L1](history/GOAL_U04_NATIVE_FEEDBACK_L1_2026-10-17.md)：实际instrumentation与Python全表消费通过，提交后反馈异常停扫/原ID新engine恢复无重复，原83表328行不变；WorkManager元数据单列，失败下载保留，仅测试L1，非整体L2/L3。

- [U04 TEMP明细有限分页 L1](history/GOAL_U04_TEMPORARY_DETAILS_L1_2026-10-16.md)：21项主机及受影响6项复验、最终APK四字号/阻断前驱实际交互及Python全表守恒通过；原5564的455文件/83表328行保持，1703/1704停机。首次失败库缺失限制和所有失败原件单列，仅L1开发备份，非U整体/E08 L2/D18 L3。

- [U04正式明细有限分页 L1](history/GOAL_U04_FORMAL_DETAILS_L1_2026-10-16.md)：23项主机、最终APK四字号真实分页/筛选/定位/身份/键盘交互及Python83表342行守恒通过；原5564的455文件/83表328行不变，失败1701及全部日志保留。仅L1开发备份，非U整体/E08 L2/D18 L3。

- [U04原生独立队列故障/恢复/满入口](history/GOAL_U04_NATIVE_INDEPENDENT_QUEUE_2026-10-16.md)：三次分离原生消费者与Python全表核对通过，原ID恢复一次/65条已接收全保留；原83表328行保持，运行元数据单列，仅测试有界组合备份，非整体L2/L3。

- [独立采集引用的旧同步缓存保护 L1](history/GOAL_INDEPENDENT_CACHE_RETENTION_L1_2026-10-16.md)：修复遗漏独立来源引用，25项主机及原AVD消费者通过；83表328行原业务事实不变，运行元数据单列，失败DB/封存器原件保留，仅缓存保护开发备份，非D16整体/L2/L3。

- [U04声效未就绪与耐久保存](history/GOAL_U04_SOUND_READINESS_2026-10-16.md)：新增四项及受影响31项主机原生Room组合通过；事务后回调、尾SQL回滚/原ID恢复、声音未就绪准确反馈有断言；编译失败/旧XML保留，仅测试开发备份，非U整体/L2/L3。

- [E08辅助导航与扫码队列组合](history/GOAL_E08_AUXILIARY_QUEUE_COMBINATION_2026-10-16.md)：同输入25项主机回归与原AVD三个200%实际页面场景通过，83表328行/403原文件保持；仅有界组合开发备份，非E08/U整体L2或D18 L3。

- [E06既有组合事实补录与电脑消费](history/GOAL_E06_RETAINED_COMBINATION_AUDIT_2026-10-16.md)：原1539真实HTTP/39份表快照/原模拟器83表150文件与347停机文件补核，本批原SAF实际Python/Excel460项通过；源码漂移与JAR去重取回分列，Chrome审批阻断保留，非E06 L2/D18 L3。

- [D15 原非空副本程序回退读取](history/GOAL_D15_PROGRAM_ROLLBACK_2026-10-16.md)：新→旧→新JAR/24次真实HTTP/180表与文件守恒，离线dump/会话补核通过；671部分导入、672子JVM排空失败保留。仅有界组合开发备份，非D15整体/E07 L2/D18 L3。

- [D15 Linux隔离资源约束 L1](history/GOAL_D15_ISOLATED_RESOURCE_L1_2026-10-16.md)：三模板九项实际user unit CPU/任务上限、分段内存OOM及停止排空通过，2101原件独立回读守恒；原软阈值限流和回收时序失败保留。仅合成负载L1开发备份，非项目Linux部署启停/D15整体/L2/L3。

- [D15 恢复后原导出任务执行](history/GOAL_D15_RESTORED_EXPORT_WORKER_2026-10-16.md)：670实际HTTP待处理任务经SQL/文件恢复，由另一JVM产品worker/renderer完成并下载；Python逐值回读及全表守恒补核通过。原driver exit1与669部分导入失败完整保留，未同库重跑；仅有界组合开发备份，非D15整体/E07 L2/D18 L3。

- [D15 恢复后任务事实与受限监控组合](history/GOAL_D15_RESTORED_JOBS_COMBINATION_2026-10-16.md)：新1668原667 dump/七存储文件/两验签配置守恒；派生合成任务实际备份恢复及CLI七项通过，180表/597停机文件保全。仅受影响组合开发备份，非D15整体/E07 L2/D18 L3。

- [E08 独立 SKU 返回导航 L1](history/GOAL_E08_SKU_NAVIGATION_L1_2026-10-16.md)：原 AVD 四档字号实际数据/SKU/身份/真实键盘输入与两次返回通过；83 表 328 行及 358 原文件不变，2018 证据双次核验，运行器失败保留。仅导航 L1 开发备份，非 E08/U 整体或 L2/L3。

- [E07 产品提交仓库与原未知批次恢复组合](history/GOAL_E07_FORMAL_REPOSITORY_RESTORE_COMBINATION_2026-10-16.md)：667实际PdaSession/SubmissionRepository真实提交丢响应→Room重开原ID查询→SQL/文件恢复→第二Android JVM原ID查询及原签归档通过；604原件独立Python守恒，666失败和consumer01时间断言原件保留。仅组合开发备份，非E07整体L2/D16整体/D18 L3。

- [D16 恢复后正式事实与引用组合](history/GOAL_D16_RESTORED_FORMAL_REFERENCE_COMBINATION_2026-10-16.md)：12项实际CLI/恢复/并发/锁后撤权/末DELETE回滚通过，665正常正式订单/有效guard/原签审计及原生trigger保持；250case/28次合成提交独立核对。两轮装配失败和部分自动提交原件保留，仅组合开发备份，非D16整体/E07 L2/D18 L3。

- [E07 正式原批次恢复与D16原生结构边界](history/GOAL_E07_FORMAL_RESTORE_NATIVE_RETENTION_2026-10-16.md)：665两阶段Android/真实HTTP/MySQL及Python通过，原签/179业务表/83本地表/591原件保持；101项原生trigger实际CLI回归通过，正常目标有效guard仍拒绝。全部失败保留，仅组合开发备份，非E07 L2/D16整体/D18 L3。

- [E07 丢失成功回执的原批次恢复组合](history/GOAL_E07_LOST_SUCCESS_RESTORE_COMBINATION_2026-10-16.md)：664三阶段Android/真实HTTP/MySQL及Python通过；原待审Room→首响应丢失→关闭重开原ID重查成功，仅三表受控增量，611原件守恒。首轮消费者元数据断言失败保留并只读补核；非E07 L2/D18 L3。

- [E07 Android原批次与整库恢复组合](history/GOAL_E07_ANDROID_ARCHIVE_RESTORE_COMBINATION_2026-10-16.md)：663实际冻结/HTTP成功归档→完整SQL/文件恢复→新Android JVM消费原Room/原签通过；独立Python核179业务表/83本地表及599原件守恒。661/662失败保全，非E07整体L2或D18 L3。

- [D16 无关审计引用保留 L1](history/GOAL_D16_UNRELATED_AUDIT_L1_2026-10-16.md)：82项实际CLI/恢复/并发/锁后撤权/末DELETE回滚通过，两代原签审计和依赖原样保留，404case/37次真实合成提交独立核对。失败保留，正常有效guard目标及D13–D15组合继续，非D16整体/L2/L3。

- [E07 旧恢复客户端非空升级 L1](history/GOAL_E07_LEGACY_RECOVERY_UPGRADE_L1_2026-10-16.md)：20项主机测试、两阶段原生迁移/强停重启及独立Python通过，23旧表42行/原签与恢复屏障保持，75原件多次SHA一致；首轮消费者封装错误保留，仅L1开发备份，非E07 L2/D18 L3。

- [D16 其他订单防重保留 L1](history/GOAL_D16_UNRELATED_GUARD_L1_2026-10-16.md)：49项CLI/全表恢复、default-off/并发/锁后撤权/末DELETE回滚通过；无关guard及引用行完整保留，目标有效guard仍拒绝。全部失败副本保留；正常目标/复杂引用/D13–D15组合及D16整体继续，非L2/L3。

- [E07 644原回执与固定版本导出独立消费](history/GOAL_E07_644_RECEIPT_EXPORT_CONSUMER_2026-10-16.md)：92份RSA/6份Excel及原payload/审批交叉绑定通过，526原件多次SHA不变；不启动旧库，不计全表恢复或E07 L2/D18 L3。

- [E08 独立口令标签与键盘布局 L1](history/GOAL_E08_CREDENTIAL_LAYOUT_L1_2026-10-16.md)：11项定向/APK/lint、四字号直接身份页键盘/输入/滑动/返回通过，83表328行及304原件不变；完整数据/SKU返回导航失败继续保留，仅布局开发备份，非U整体/L2/L3。

- [E08 更新页系统栏与大字 L1](history/GOAL_E08_UPDATE_LAYOUT_L1_2026-10-16.md)：29项更新模块、APK/lint及四字号实际入口/系统栏/返回通过；83表328行与269原文件不变，2786证据双次核验。截图错位补正前原件保留，仅页面开发备份，非E08/U整体或L2/L3。

- [E08 设置分组与连接诊断 L1](history/GOAL_E08_SETTINGS_DIAGNOSIS_L1_2026-10-16.md)：6项定向/APK/lint、原隔离模拟器四档字号实际导航/声音/离线诊断/详情/更新页/取消退出通过；83表328行及244原件保持，1956证据双次核验。滚动和取证失败保留，非E08/U整体或L2/L3。

- [D16合成隔离整单消费者 L1](history/GOAL_D16_WHOLE_ORDER_L1_2026-10-16.md)：实际CLI删除/完整恢复、27项定向、default-off/同键并发/真实锁等待撤权/末DELETE回滚通过；仅guard-free初轮FORMAL合成副本。正常带防重/复杂引用资格和D13–D15组合仍未完成，旧失败保留，不计D16整体/L2/L3。

- [E08 FORMAL/TEMP结单入口 L1](history/GOAL_E08_SUBMISSION_ENTRY_L1_2026-10-16.md)：TEMP原批次冻结失败后分段查询/展开/重建补验，FORMAL新隔离副本同ID查询原签回执及强停重启通过；独立Python、APK/lint及原件守恒有输入绑定。首轮失败和HTTP跳过保留，仅L1开发备份，真实HTTP/MySQL组合、E08/U整体、L2/L3与总目标继续。

- [E02 保存扫码分段接续与失败确认反馈 L1](history/GOAL_E02_SAVE_SCAN_ACK_L1_2026-10-16.md)：原身份真实HTTP/MySQL续备、原失败订单/原pending接续、改后实际确认反馈与独立Python通过；build08定向18项、APK/lint通过，83表受控差异与180服务表守恒，补齐原binlog哈希。F1误用失败及取证补正保留，非一次全程通过，仅L1开发备份，U整体/L2/L3/总目标继续。

- [E02 草稿视口与页面编辑 L1](history/GOAL_E02_DRAFT_VIEWPORT_EDITOR_L1_2026-10-16.md)：原模拟器真实触摸与编辑/移除/重建/丢弃/重开通过，83表及原草稿守恒；build10定向11项、APK/lint通过，917项既有Android输入仅旧error清除变化。历史失败保留，仅页面L1开发备份，后续保存→扫码分段证据见上项，U整体、L2/L3与总目标未完成。

- [E07 撤回恢复受影响组合验收（654–657）](history/GOAL_E07_WITHDRAWAL_RECOVERY_COMBINATION_654_ACCEPTANCE_2026-10-16.md)：654/655 原结果保留并明示原输入缺口；656 新隔离五截点真实 HTTP/MySQL 1/0/0/0，5301 项输入未变，657 只读消费 1362 项证据双次守恒，认证停机。外层 UTF-8 取证错误保留并只读补核。仅 E07 受影响组合开发验收，不计 E07 L2、D18 L3、第 8 节闸门或总目标。

- [E07/O04 独立采集检查点 L1（642）](history/GOAL_E07_O04_INDEPENDENT_CHECKPOINT_2026-10-16.md)：641 隔离恢复切片由独立只读 Python 消费，双次 854 文件清单、SHA/长度、七组 HTTP 响应、179 表/622 行事实与文件守恒均通过；首轮附件长度/库存前置解析失败原件保留。本批仅 L1 开发验收，不计 E07 L2、D18 L3 或总目标。

- [E07 受影响组合验收（647）](history/GOAL_E07_COMBINATION_647_ACCEPTANCE_2026-10-16.md)：新隔离实例 647 的真实 HTTP/MySQL、独立检查点恢复、非空迁移、179 表/622 行与 859 文件双次守恒、7 组 HTTP 200/OK、独立只读消费者及认证停机通过；645/646 运行器预检失败原件保留。本批仅组合开发验收，不计 E07 L2、D18 L3 或总目标。

- [E07 受影响组合验收（644）](history/GOAL_E07_COMBINATION_644_ACCEPTANCE_2026-10-16.md)：新隔离实例 644 的真实 HTTP/MySQL、更正账务、撤回、导出和退库周期组合 1/1 通过，认证停机且项目端口清零；643 端口配置失败原件保留。本批仅组合开发验收，不计 E07 L2、D18 L3 或总目标。

- [E07/O04 独立采集跨截点消费者](history/GOAL_E07_O04_INDEPENDENT_CHECKPOINT_CONSUMER_ACCEPTANCE_2026-10-16.md)：629 隔离实例的 5,292 项冻结输入、三库 540 表、854 文件、dump/binlog/存储守恒、原 ZIP/设备签名、credential/关联/实物附件签名与 HTTP/default-off 事实均由独立只读 Python 消费通过；626–628 原失败继续保全。本批为开发备份，不计 E07 L2、D14 整体、D18 L3 或总目标。

- [E07/O03原件中转跨截点组合](history/GOAL_E07_RELAY_CHECKPOINT_ACCEPTANCE_2026-10-15.md)：625真实HTTP/MySQL 1/1、原包/两原签/同键并发与前缀拒绝、独立Python及540表/830文件保全通过；消费者登录审计误判原件保留。仅开发备份，非E07 L2/D14整体/D18 L3。

- [E07更正前截点不退库及原binlog重建](history/GOAL_E07_RETURN_CHECKPOINT_ACCEPTANCE_2026-10-15.md)：624真实HTTP/MySQL 1/1、179业务表终点/前缀拒绝/五原签重放/default-off、独立Python与三库540表/809文件保全通过；623 CSRF取样失败及598原件保留。仅开发备份，非E07 L2/D14-D15整体/D18 L3。

- [E07独立退库审批与新周期HTTP组合](history/GOAL_E07_RETURNED_STOCK_HTTP_ACCEPTANCE_2026-10-15.md)：622真实HTTP/MySQL 1/1、180表/32份RSA/3项退库引用及新旧周期独立消费、507文件保全和认证停机通过；仅组合开发备份，恢复截点缺更正、E07 L2/D14-D15整体/D18 L3仍未完成。

- [E07撤回五检查点恢复](history/GOAL_E07_WITHDRAWAL_CHECKPOINT_ACCEPTANCE_2026-10-15.md)：新616五检查点真实恢复/HTTP重放1/1、独立Python/原RSA回执消费及617六库保全通过；613/614/615原失败与系统重启事实保留。仅开发备份，E07 L2/D14整体/D18 L3未完成。

- [E07原检查点实物与运维组合](history/GOAL_E07_PHYSICAL_OPERATIONS_ACCEPTANCE_2026-10-15.md)：新611实物与独立JVM重放、612运维各1/1，非空升级、Node/Python消费及两库181表保全通过；414/416停机文件不变。仅开发备份，E07 L2/D14-D15整体/D18 L3未完成。

- [E07全局编号历史与只读恢复对账](history/GOAL_E07_NUMBER_RECONCILIATION_ACCEPTANCE_2026-10-15.md)：新610真实HTTP/MySQL 1/1、相关回归54/54、Node/Python独立摘要/成员消费、非空升级与181表保全通过；609产品失败及消费者/预检原失败保留。仅开发切片，非E07 L2/D14整体/D18 L3。

- [E07历史覆盖与恢复后非空升级](history/GOAL_E07_HISTORY_COVERAGE_ACCEPTANCE_2026-10-15.md)：新608实际HTTP/MySQL 1/1、独立历史/137表成员消费者及全表/文件守恒；606/607失败保全，原输入5255项和来源393项不变。仅开发切片，不计E07 L2/D14整体/D18 L3。

- [P07历史钥及固定恢复引用核验L1](history/GOAL_P07_KEYRING_ACCEPTANCE_2026-10-15.md)：最终实际专项19/19、产品JVM原签取回后Python逐件消费与输入守恒；原编译/Windows夹具失败保留，运行源码与前批消费者按SHA复用。只计工具开发切片，外部保管、E07组合及D18 L3继续。

- [P07 显式原签还原 L1](history/GOAL_P07_RESTORE_ACCEPTANCE_2026-10-15.md)：最终实际IT38/38、Java输入不变的33项复用、JAR成功；布尔/整数误接受已复现修复，原失败/真实还原中止/末替换失败原件保留。仅工具切片，UNKNOWN/pending及E07组合仍未完成。

- [P07 原签全集封存 L1](history/GOAL_P07_INVENTORY_ACCEPTANCE_2026-10-15.md)：首次实际IT21/21、定向33/33；回读自审后Python供应5/5，其他输入按hash复用。真实中止/双侧缺件/旧备份边界通过，显式恢复未完成。

- [P07 中止诊断与启动拒绝 L1](history/GOAL_P07_INTERRUPTION_ACCEPTANCE_2026-10-15.md)：最终专项 IT 10/10、CLI/锁回归25/25；按产品输入哈希复用2005项及供应4项。真实中止与全部失败原件保留，显式可信恢复仍未完成。

- [D15 身份绑定任务监控 L1](history/GOAL_D15_JOBS_MONITOR_ACCEPTANCE_2026-10-15.md)：新隔离 MySQL/CLI 15/15、最终配置/ACL 4/4；既有 R09 回归139通过/11跳过。首次ACL误通过及修复、两实例保全完整；仅工具开发备份，D15整体、L2/L3和总目标继续。

- [D15升级检查点与恢复切片验收](history/GOAL_D15_UPGRADE_CHECKPOINT_ACCEPTANCE_2026-10-15.md)：1548连接耗尽失败保全；1550独立隔离运行器1/1通过，完成非空增量升级、SQL dump/import、storage/开发签名字节守恒、空伴随恢复与非空覆盖拒绝。仅开发切片，不计D15整体、E07 L2、D18 L3或总目标。

- [P07 外部秘密供应与停机重锚 L1](history/GOAL_P07_OFFLINE_REKEY_ACCEPTANCE_2026-10-15.md)：40/40 专项及受影响回归，包装改动后 Python 产品入口 4/4、JAR 构建通过；旧记录副本、失败和真实 Python/JVM/独立回读保全。仅工具切片，不计 D13 全部、E07 L2 或 D18 L3。

- [U01 共享扫码布局依赖开发备份](history/GOAL_U01_SCAN_DEPENDENCY_2026-10-15.md)：精确暂存树导出验证 23/23，Debug APK/lint 通过；补齐渲染修复遗漏的共享布局依赖。设备入口未验，不计完整 L1、U01/U02、E06 L2 或 D18 L3。

- [O01/U01 Android 交互定向验收](history/GOAL_O01_U01_ANDROID_ACCEPTANCE_2026-10-15.md)：工作树定向单测 26/26，lint 与 Debug APK 通过；测试清单及提交依赖已补核。仅开发证据，不计真机/U01-U04 完整验收、E06 L2、D18 L3 或总目标。

- [D10 故障撤回/重发真实 HTTP 验收](history/GOAL_D10_FAULT_WITHDRAWAL_HTTP_ACCEPTANCE_2026-10-15.md)：隔离 MySQL 上 15 项真实 HTTP/API/消费者测试、附件单测 17/17、Web typecheck/contract/build 全部通过，保留权限、幂等、并发、尾写回滚和新设备从头作业证据；仅收口 D10 当前切片，D10 其余范围、E06 L2、D18 L3、D01–D18 其他闸门与总目标继续。

- [E06 L2 Android 验收（未完成）](history/GOAL_E06_L2_ACCEPTANCE_2026-10-15.md)：当前 HEAD 下短 Java 临时路径独立 Android/Room 测试 90/0/0/0，Debug 与 AndroidTest APK、lint 成功；深路径 31 项同根因 SQLite 打开失败保全，未执行 instrumentation，1540 Chrome 受运行器审批阻断。仅收录 Android 开发证据，E06 L2 整体、D18 L3 与总目标继续。

- [O04独立采集验收](history/GOAL_O04_INDEPENDENT_COLLECTION_ACCEPTANCE_2026-10-15.md)：短路径 Java 临时目录绑定既有服务端凭据向量与独立包输出，90项独立 Android/Room/救援单测0失败、0错误、1个既有跳过；仅收口O04开发切片，E06 L2、U03完整人工关联、D18 L3与总目标继续。

- [E05/O03受控原件中转切片收口](history/GOAL_E05_O03_ACCEPTANCE_2026-10-13.md)：电脑专用权限/原登记钥/原提交守恒、原Android清单与签名文件消费；新未上传批次产品正向、原成功原键/丢响应/跨JVM、非空/default-off及受影响账务/净值/导出组合通过。E06–E09、完整D/O/U及总目标继续。

- [D16缓存投影清理与引用保护开发证据](history/GOAL_D16_RETENTION_ACCEPTANCE_2026-10-15.md)：历史19/19只覆盖缓存投影判定、引用、批量清理、回滚及迁移保留；2026-10-16已纠正范围并保存原报告。整单消费者首切片另见本页新L1索引，D16整体继续。

- [E08 UI 后续短路径复验](history/GOAL_E08_UI_FOLLOWUP_ACCEPTANCE_2026-10-15.md)：恢复卡片读取 2/2、恢复确认重试 1/1，在 D 盘 Java 临时目录下共 3/3 通过；输入哈希、命令和日志见 `.local/goal-20261015/e08-ui-followup-04/`。仅补定向开发证据，长路径失败、真机/U01–U04、E06 L2、D18 L3 与总目标继续。

- [E08 UI 定向验收](history/GOAL_E08_UI_TARGETED_ACCEPTANCE_2026-10-15.md)：`e08-ui-targeted-05` 六个 UI/恢复相关主机测试 13/13 通过，lint 成功；D 盘缓存失败与共享缓存重试均留档，未启动服务或写入业务库。仅定向开发证据，不计 U01–U04、E06 L2、D18 L3 或总目标。

- [E09 Web 候选阶段验收](history/GOAL_E09_WEB_CANDIDATE_ACCEPTANCE_2026-10-15.md)：typecheck、契约校验、Web 构建及临时审查 7/7 通过，输入哈希已复核；无浏览器、服务端/数据库、文件或恢复组合证据，仅阶段候选成果，不计 D18 L3 或总目标。

# 开发验收索引

- [E07更正账务、缺记账与退库候选组合](history/GOAL_E07_RETURNED_STOCK_COMBINATION_ACCEPTANCE_2026-10-15.md)：621真实HTTP/MySQL 1/1，独立Python消费180表/103份RSA回执/3项补记账引用/13条冲正与674文件保全通过；消费者引用图误判失败原件保留。仅局部组合开发备份，完整退库入口、恢复后缺更正围栏、E07 L2和D18 L3继续。

- [E07更正身份与两阶段撤回组合](history/GOAL_E07_IDENTITY_COMBINATION_ACCEPTANCE_2026-10-15.md)：新620非空升级后真实HTTP/MySQL 1/1、四份RSA/180表/独立Python及保全通过；619临时目录与消费者二进制SHA误读失败保留。仅组合开发备份，不计E07 L2、D14/D15整体或D18 L3。

- [E07/D14 恢复归档消费与主管故障围栏](history/GOAL_E07_RECEIVED_ARCHIVE_FAULT_ACCEPTANCE_2026-10-15.md)：新604/605分别执行原归档闭合事务消费和恢复态独立主管故障围栏，XML 各 1/0/0/0，Node/Python 输入守恒、数据库与文件守恒、认证停机及零监听；仅开发切片，不计 E07 L2、D14/D15 整体、E06 L2、D18 L3 或总目标。

- [E07/D14 原检查点与 binlog 重建](history/GOAL_E07_CHECKPOINT_REBUILD_ACCEPTANCE_2026-10-15.md)：新603真实导入/重放1/1，109触发器、146业务表与迁移历史/19文件守恒，三项写前拒绝，147表独立dump/停机保全；601/602失败保留，仅开发切片，不计E07 L2/D18 L3。

- [E08 页面与作业体验开发验收（未完成）](history/GOAL_E08_UI_ACCEPTANCE_2026-10-15.md)：Work/Submission/Temporary/Handover 重设计、恢复确认和 Temporary 搜索已实现；lint/debug/staging 成功，短路径隔离复验 13/13 主机测试通过。长路径 Robolectric SQLite 失败根因和原 XML 保留；仅开发备份，U01–U04 完整验收、真机/长时、E06 L2、D18 L3 与总目标继续。

- [E06受影响组合补验（未完成）](history/GOAL_E06_COMBINATION_PARTIAL_2026-10-14.md)：原AVD封存只读/83表与旧证据守恒、实际SAF/460值转换、1539真实事务/新JVM/default-off、13+16网页消费及21项Python通过；1540零关联保全，Chrome预览启动遭自动审批拒绝。仅开发备份，不计E06 L2/D18 L3；E07独立范围继续。

- [E06多任务人工关联网页L1](history/GOAL_E06_MULTITASK_ASSOCIATION_WEB_L1_2026-10-14.md)：完整成员与合计短装、Chrome实际丢响应/双页原键/主成员回读、逐值守恒和保全输入复核；仅开发备份，E06受影响L2及最终L3继续。

- [E06多任务人工关联服务L1](history/GOAL_E06_MULTITASK_ASSOCIATION_L1_2026-10-14.md)：显式成员/不可变台账、真实聚合正向/事务/权限/原键、严格非空与固定钥消费；仅开发备份，多任务网页与E06组合继续。

- [E06人工关联消费完整冲突决定L1](history/GOAL_E06_CONFLICT_ASSOCIATION_L1_2026-10-14.md)：显式原决定/不可变引用，真实正向/默认开关/权限/事务/并发/锁后撤权、固定外部钥与新JVM/守恒；[设计](history/GOAL_E06_CONFLICT_ASSOCIATION_DESIGN_2026-10-14.md)。网页入口、短装/多任务与E06组合仍待验，非整体收口。

- [E06跨机实物冲突服务L1](history/GOAL_E06_CONFLICT_L1_2026-10-14.md)：真实正向/双权限/原键并发/末SQL/锁后撤权、不可变引用和固定外部钥/新JVM/原事实守恒；[设计](history/GOAL_E06_CONFLICT_DESIGN_2026-10-14.md)。仅开发备份，网页/关联消费、短装/多任务、E06组合与总目标继续。

- [E06人工关联网页L1](history/GOAL_E06_ASSOCIATION_WEB_L1_2026-10-14.md)：完整候选分页、Chrome丢响应/双页原键恢复、空本地存储读回及严格守恒；[服务端首切片L1](history/GOAL_E06_ASSOCIATION_L1_2026-10-14.md)默认关闭、真实事务/权限/固定钥消费复用。均为开发备份，冲突处置、多任务/短装、E06组合与最终闸门继续。

- [E06实物登记限定补验](history/GOAL_E06_PHYSICAL_ACCEPTANCE_2026-10-14.md)：新隔离副本JDBC严格非空/default-off、原包/附件/原键/完整上下文及全表守恒，权限/并发/末SQL/锁后撤权通过；旧CLI失败保留、不倒推旧业务事实。[SQL读取修复](history/GOAL_E06_SQL_REPAIR_2026-10-14.md)附15项定向和真实消费。仅实物单元，人工关联/跨机冲突、完整D/O/U与最终L3未完。

- [E06实物开发备份与新会话接续](history/D01_D18_HANDOFF_2026-10-14_E06_PHYSICAL_WIP.md)：实物登记仅开发中备份；输入绑定复用，严格非空/组合守恒仍待验，SQL读取同潜在根因连续两次；失败库保全停机、继承改动/预览保留，用户要求暂停，不计E06或总目标通过。

- [进度整理与开发产物清理截点](history/DEVELOPMENT_PROGRESS_AND_CLEANUP_2026-10-14.md)：验收链接整理、保全原字节的停机库压缩与同源闲置DLL清理；没有新增业务验收，不计D16或总目标通过。

- [E06主管完整核对依据](history/GOAL_E06_RECONCILIATION_READ_ACCEPTANCE_2026-10-14.md)：完整候选/库存/已接收原件共同摘要、分页与权限/未知钥/资源上限反例，实际HTTP/新JVM/Chrome和逐行守恒通过。实物与人工关联继续，非E06/O04/U03或总目标完成。

- [E06独立任务完整结束边界](history/GOAL_E06_CLOSURE_ACCEPTANCE_2026-10-14.md)：原机暂停排空、原Keystore末事件封存与只读导出、电脑/服务独立核验及SQL替换保护；最终Android/APK/lint、真实HTTP/新进程和旧事实守恒证据分列。实物、完整候选、人工关联及跨机冲突消费者继续，不计E06/O04/U03或总目标完成。

- [E06独立SCAN/VOID与三来源防重](history/GOAL_E06_SCAN_VOID_2026-10-14.md)：60项定向/82项受影响回归、APK/lint、原模拟器广播/手输/失败/放弃/实物VOID/再扫与尾SQL新进程恢复、320dp四字号目视及独立签名/原资料/74旧表守恒通过；导出/主管对账及E06/O04/U03整体继续，原失败保留。

- [E06本地任务计划](history/GOAL_E06_LOCAL_TASK_2026-10-13.md)：固定原资料/原签名身份/可信期限、耐久原请求与原子计划审计；定向44项/APK/lint、原机离线页面/新进程/尾回滚恢复、非空与独立文件/旧表守恒、320dp四字号目视通过。SCAN/VOID/防重/文件/主管对账继续，非E06/O04/U03整体完成。

- [E06受限独立采集身份](history/GOAL_E06_IDENTITY_2026-10-13.md)：原登记钥/专用权限/原键签名事务、独立本地核验与可信期限；定向服务/Android、真实HTTP/锁后撤权/回滚、固定钥/新JVM、O03原非空守恒、原模拟器页面/断网新进程/实际重启/撤权/注销/320dp字号键盘通过。任务、防重、文件及对账仍未完成。

- [E06独立sku资料切片](history/GOAL_E06_SKU_CATALOG_2026-10-13.md)：同字节解析、分块原件/预检/去重/原子启用及历史取回；Room/尾SQL/并发/非空升级、实际SAF/新进程/320dp字号和66旧表守恒通过。身份/任务/防重/导出/对账继续，非O04/U03或总目标完成。

- [E05净值汇总切片收口](history/GOAL_E05_NET_SUMMARY_2026-10-13.md)：原出库日最新净值、固定成员/版本、原序/归零行及PC/TEMP/FORMAL消费者；定向服务/渲染、真实HTTP、Web/Chrome/新JVM、独立文件、原非空兼容及受影响组合通过。本净值切片不覆盖O03，O03另见上方验收；完整D11/D12和最终候选未完成；[换会话断点](history/D01_D18_HANDOFF_2026-10-13_E05_NET_CLOSE.md)保存实际Git/写权、清理与下一步。

- [E04/P06未终结FORMAL实物应用与解隔离](history/GOAL_E04_P06_OPEN_FORMAL_QUARANTINE_2026-10-13.md)：原事务内不可变独立应用签名、原件核验及生产默认关闭；普通定向/bootJar、最终真实HTTP、固定公钥SQL、Web/Chrome双角色/原键/窄屏、新JVM与原FORMAL非空守恒/原字节、账务及终态组合通过；其他保留包继续待处理，D09/D10及总目标未完成。

- [E04/P06历史贡献与新周期解隔离](history/GOAL_E04_P06_QUARANTINE_HISTORY_2026-10-13.md)：原退货/撤销、实际反向贡献及独立库存恢复逐周期核验，摘要和受限原件下载；371项定向/bootJar、真实HTTP/Chrome/新JVM、旧TEMP/PC非空原字节、固定钥SQL及受影响账务组合通过。容量产品缺陷和来源夹具失败均保留；不计未终结FORMAL、D09/D10或总目标整体完成。

- [E04/P06未关闭TEMP原逐包解隔离](history/GOAL_E04_P06_OPEN_TEMP_QUARANTINE_2026-10-13.md)：原Android退审来源及原逐包签名/引用固定、实际附件和独立批准，仅解除精确owner；普通定向/真实HTTP/非空兼容/Chrome/新JVM/固定公钥及受影响账务组合通过。原来源不关闭、库存账/周期保持；历史贡献、D09/D10及总目标继续未完成。

- [E04/P06 FORMAL原实物处置解隔离](history/GOAL_E04_P06_FORMAL_QUARANTINE_2026-10-13.md)：原恢复/STOP、不可变实物批准、同原机续作与应用签名交叉核验；168项定向/bootJar、真实HTTP自批/篡改/回滚/并发、TEMP/PC原副本非空及原字节、新JVM、Web/Chrome/固定钥/SQL和受影响账务组合通过。未关闭TEMP及历史贡献继续，D09/D10及总目标未整体完成。

- [E04/P06 PC原处置解隔离](history/GOAL_E04_P06_PC_QUARANTINE_2026-10-13.md)：原STOP/方案/审批/应用与逐包摘要交叉核验，实物附件及独立批准仅删除精确owner；153项定向、真实HTTP自批/篡改/回滚/并发、原非空与TEMP组合、Web/Chrome/新JVM、固定钥/SQL及账务组合通过。FORMAL、未关闭TEMP和历史贡献继续，D09/D10及总目标未整体完成。

- [E04/P06已关闭TEMP实物解隔离](history/GOAL_E04_P06_QUARANTINE_2026-10-13.md)：原签名、上游与精确隔离owner固定，实物附件和独立审批仅解除原隔离；原非空HTTP、最终Web/Chrome、固定钥/SQL、新JVM与受影响账务组合通过。FORMAL/PC、未关闭来源及历史贡献仍必做，D09/D10和总目标未整体完成。

- [E04/P06返库库存账恢复](history/GOAL_E04_P06_RETURNED_STOCK_2026-10-13.md)：原退货/错误出库真实贡献与周期固定、实物附件及独立主管审批后恢复库存账投影；118项服务定向、真实HTTP、原非空兼容、受影响组合、Web/新JVM/签名/分页及动画结束视觉复核通过。QUARANTINED解除、D09整体和E05–E09继续。

- [E04/P06缺账逐笔补扣](history/GOAL_E04_P06_RECONCILIATION_2026-10-13.md)：可信成功/当前贡献/上游/实物/占用及完整账务固定，实际附件与独立主管审批产生唯一补扣；真实HTTP并发/回滚/撤权、后续更正退货反向、Web原键恢复/独立签名及SQL、新JVM/布局、原非空兼容及受影响组合通过。其他库存差异/解隔离/新周期和E05–E09继续，D09及总目标未整体完成。

- [E04/P06抬头更正撤销补偿](history/GOAL_E04_P06_HEADER_COMPENSATION_2026-10-12.md)：原命令/签名及完整版本链证明字段贡献，后继/数重保持；真实HTTP并发/末SQL/新JVM、原历史非空升级、Web双角色/丢响应原键、独立XLSX/PDF/固定钥/SQL及受影响账务链通过。库存差异与E05–E09及最终组合继续，总目标未完成。

- [E04/P05非零原机取消与主管实物关闭](history/GOAL_E04_P05_NONZERO_CANCEL_2026-10-12.md)：已上传与冻结未上传原Keystore取消、完整STOP及停服新进程、主管原键丢响应恢复和释放/隔离、真实HTTP回滚及固定公钥/SQL守恒、受影响组合通过；保留竞态/运行器失败及恢复切片范围，生产默认与删除边界不变。P06及E05–E09继续，D05/D18和总目标未整体完成。

- [E04/P04原机产品与历史兼容](history/GOAL_E04_P04_NATIVE_PRODUCT_2026-10-12.md)：实际Keystore原冻结批次页面上传、主管HTTP排除/关联/重核/确认、Android自行取得映射/验签与停服新进程；52项受影响组合、16份真实旧LINK非空恢复读者及原机数据库事实通过。P04子项收口，D04旧前缀暂缓、P05/P06与E05–E09继续，总目标未完成。

- [D01–D18阶段收口及新会话断点](history/D01_D18_HANDOFF_2026-10-12_P04.md)：P04阶段备份、已验与未验边界、全部改动快照、活动预览及磁盘管理；总目标未完成，不计新增开发验收。

- [E04候选逐项排除阶段证据](history/GOAL_E04_CANDIDATE_EXCLUSION_2026-10-12.md)：服务完整回归、Chrome/HTTP及Android原件native消费；后续产品/非空兼容补证见上项，D04整体与D18仍未通过。

- [E04普通本机零作业取消](history/GOAL_E04_ZERO_DRAFT_CANCEL_2026-10-12.md)：原身份/队列/占用与未决保护、Room原子取消审计/回滚/原键、新进程历史和旧冻结拒绝、129项受影响回归及APK/lint；非零主管取消、P04/P06和总目标继续，原UI失败保留。

- [E03 FORMAL原件、规模与进程中断](history/GOAL_E03_FORMAL_SCALE_2026-10-12.md)：停服/关闭网络的真实FORMAL导出与SAF回读、包外登记及固定钥原回执/Excel、生成/复制中断新进程保全、106项受影响回归；继承失败保留，独立来源/O02全范围与E04–E09继续。

- [E03 Web主管只读救援产品](history/GOAL_E03_RESCUE_WEB_2026-10-12.md)：原Keystore申请的实际审批/撤销、丢响应重载原键、期限拒绝原件保留、存储失败与权限反例及独立签名消费者通过；仅本切片，完整O02/最终组合继续，失败原件保留。

- [E03原人耐久救援申请产品](history/GOAL_E03_RESCUE_REQUEST_PRODUCT_2026-10-12.md)：原机先落盘/原键正文签名、实际页面跨进程提交重试/签名审批材料、身份切换及停服保全通过；Web审批产品与完整O02继续，可选证据目录收集失败保留。

- [E03 Android主管只读救援消费](history/GOAL_E03_SUPERVISOR_ANDROID_2026-10-12.md)：严格授权/撤销、独立限速凭据、真实HTTP权限预置、停服新进程限定原单导出与离线耐久撤销、包外登记电脑核验/转换通过；原人申请/Web审批及完整O02仍缺，隔离运行器失败原件保留。

- [E03主管预授权救援](history/GOAL_E03_DELEGATED_RESCUE_2026-10-12.md)：严格来源协议、服务端权限/原键/不可变材料/真实HTTP并发回滚与非空升级有定向证据；Android跨人产品仍开发中，夹具失败与源码漂移轮保留且不计交付。

- [E03本人只读救援产品切片](history/GOAL_E03_PRODUCT_READ_2026-10-12.md)：Room一致性/前驱、本人凭据/私有包恢复、真实HTTP预置、停服注销TEMP导出/SAF回读/新进程取消及包外登记电脑核验通过；主管跨人/规模与完整O02继续，失败原件保留。

- [E03应急包格式与电脑核验切片](history/GOAL_E03_EMERGENCY_EXPORT_2026-10-11.md)：Android签封/加密、原冻结和处置守恒、独立来源/语义核验及安全Excel逐格回读；仅合成格式消费者，真实快照/救援权限/文件入口继续，O02与总目标未完成。

- [E02首页导航与暂缓项](history/GOAL_E02_HIGH_FREQUENCY_2026-10-11.md)：首页真实导航、有效保存进度和旧冻结只读入口定向复验；扫码首屏/草稿添加连续失败保全并暂缓，U01/U02整体未完成。
- [总目标E00写权与逐项证据映射](history/GOAL_E00_BASELINE_2026-10-11.md)：继承改动原件/哈希、D02–D18与O/U缺口/依赖、旧失败适用性；盘点不计新增功能通过。
- [E01故障撤回与独立重发](history/GOAL_E01_RECALL_REPUBLISH_2026-10-11.md)：无STOP及既存REQUESTED申请撤回、实物占用核验释放、新修订与旧资格围栏；真实HTTP/MySQL并发/末SQL、Web原键重载、Android采集验签/进程恢复与非空升级证据；默认关闭、原件保留。最终组合和其余D/O/U继续，总目标未完成。
- [E02任意旧包调整](history/GOAL_E02_SCAN_ADJUSTMENT_2026-10-11.md)：正式/TEMP事务、原序号/主管保护/回滚、独立定位页、大字分页、真实HTTP与模拟器及新进程只读/停服正向均有定向证据；高频首页/扫码/结单及总目标仍未完成。

- [十步完成度统计与无人值守计划修订依据](history/TEN_STEP_COMPLETION_AUDIT_2026-10-11.md)：修订范围下的工作包计分、来源证据及自主决策/失败暂缓规则；只计统计和文档核对，不计新增功能通过。执行入口为[总目标计划](AUTONOMOUS_DEVELOPMENT_GOAL_2026-10-07.md)。

- [PDA断网补救与UI/UX分析纳入计划](history/PDA_OFFLINE_UI_PLAN_2026-10-11.md)：两份报告映射十步/D项，新增O01–O05/U01–U04完成标准与政策依赖；只计文档核对，不计新增功能、真机或组合验收。

- [R07取消换机接续，改为故障订单撤回重发](history/R07_RECALL_REPUBLISH_SCOPE_2026-10-11.md)：用户范围决定、旧测试保全停机及文档交接；只计范围/文档更新，不计新方案业务验收。下文旧换机链条目保留历史范围，其旧“继续确认/激活/最终承接”提示不再作为必做指令；其余验收结论按各自输入保持。

- [D07/R09限定恢复Android耐久接收确认](history/D07_R09_RECOVERED_ANDROID_CONFIRMATION_2026-10-11.md)：明确原意图、实际POST/Room尾回滚/两Android JVM、独立SQLite/签名及APK/lint；页面调度、激活/最终提交继续。

- [D07/R09限定恢复接收确认产品HTTP](history/D07_R09_RECOVERED_CONFIRMATION_HTTP_2026-10-11.md)：默认关闭固定信任、真实POST/首次并发/丢正文/新JVM原回执与独立消费；Android确认及激活、最终提交继续。

- [D07/R09限定恢复接收确认服务](history/D07_R09_RECOVERED_CONFIRMATION_2026-10-11.md)：精确专用签名、实际MySQL/并发/尾回滚/新JVM原回执、原表守恒与独立消费；产品HTTP/Android、激活和最终提交继续。

- [D07/R09限定恢复Android原件暂存](history/D07_R09_RECOVERED_ANDROID_STAGE_2026-10-11.md)：实际HTTP/native Room/新JVM原件、事务与迁移拒绝及独立SQLite消费；确认/激活/最终提交和页面操作继续。

- [D07/R09恢复本人产品HTTP下载](history/D07_R09_RECOVERED_DOWNLOAD_HTTP_2026-10-11.md)：固定服务端信任、原Bearer/字节、并发/权限/尾改拒绝及新JVM；Android确认链继续。
- [D07/R09恢复本人原正文服务读取](history/D07_R09_RECOVERED_DOWNLOAD_2026-10-11.md)：已审计session、原字节/签名清单、全原表守恒、服务并发/尾回滚/新JVM及独立消费。

- [D07/R09恢复本人会话准入](history/D07_R09_RECIPIENT_SESSION_2026-10-11.md)：实际HTTP认证、精确登录效果、全原表守恒/尾回滚、新JVM原token与独立消费；下载确认和激活链继续。

- [D07/R09恢复态指定接收者准入](history/D07_R09_RECIPIENT_ADMISSION_2026-10-11.md)：独立签名/持钥证明、精确新增效果/原表守恒、MySQL回滚/新JVM与独立消费；目标代次新资格及最终接收链继续。

- [范围双侧提交后的收口与接续](history/D01_D18_PAUSE_2026-10-11_SCOPE_RELEASE.md)：最终证据、Git/dirty保全和新资格接缝约束；用户要求暂停，总目标未完成。
- [D07/R09已核范围双侧提交](history/D07_R09_SCOPE_RELEASE_2026-10-11.md)：库外PREPARE/DB闭事务/COMMIT、真实崩溃与原pin重载、丢确认持久围栏、独立消费；新资格与最终交接继续。

- [D07/R09独立运维批准核验](history/D07_R09_OPERATIONS_APPROVAL_2026-10-10.md)：独立信任/签名、批准后固定范围、实际闭事务重验和尾部回滚、独立消费；两阶段释放与新资格链继续。
- [D07/R09恢复态主管实物确认](history/D07_R09_PHYSICAL_REVIEW_2026-10-10.md)：原事实/精确APPROVE效果、MySQL回滚、跨JVM原键重放与独立消费；运维/两阶段释放和新资格链待接续。
- [D07/R09原终点与主管围栏效果核对](history/D07_R09_RELEASE_RECONCILIATION_2026-10-10.md)：原行/精确命令效果守恒、实际MySQL反例及独立消费；实物、运维与两阶段释放仍待接续。
- [D07/R09固定仓库及子表覆盖](history/D07_R09_RELEASE_SCOPE_COVERAGE_2026-10-10.md)：准备/库存基线/PC/同步/证据子表、完整成员独立消费和尾写回滚；不计语义闭合或范围释放。

- [D07/R09原全局编号历史核对](history/D07_R09_RELEASE_NUMBER_HISTORY_2026-10-10.md)：原日志逐笔分配、全仓库号码映射、目标漂移/尾写回滚及独立mysqlbinlog回读；不计范围释放。

- [D07/R09释放前原历史覆盖](history/D07_R09_RELEASE_HISTORY_2026-10-10.md)：原受控日志覆盖实际损失窗、错误窗口拒绝及独立消费；不计语义闭合或可信释放。

- [D07/R09主管围栏后实际范围清单](history/D07_R09_RELEASE_INVENTORY_2026-10-10.md)：只读清单、仓库边界、尾写回滚和独立工具证据；不计可信释放、新资格或最终交接完成。

<!-- CURRENT_ENTRY_START -->
唯一当前状态、Git/写权、最新测试和下一断点见[看板当前区](ORCHESTRATION_STATUS.md)。本页只索引已有验收，不复制另一份“最新状态”；接续范围见[路线](DEVELOPMENT_ROADMAP.md)及[剩余计划](REMAINING_DEVELOPMENT_PLAN.md)。
<!-- CURRENT_ENTRY_END -->

- 2026-10-19：新增 [E07/D06-D07 Android、真实 HTTP、Chrome 与 Python 候选 run1614](history/GOAL_E07_D06_D07_ANDROID_HTTP_WEB_RUN1614_2026-10-19.md)。全新隔离非空迁移、四条 Android、真实 HTTP/MySQL、Python 只读消费者和 Chrome 两项均通过，认证停机及 AVD 源守恒通过；run1612/1613 运行器失败原件保全并修复，run1613 源 AVD 变更按 fail-closed 不计入。仅局部合成候选，不计 D06/D07 L2、E07/D17、E06 L2、D18 L3 或第 8 节。

- 2026-10-19：新增 [E08/D17 O04/U04 独立凭据 parity 输入修正（run02）](history/GOAL_E08_D17_O04_U04_PARITY_RUN02_2026-10-19.md)。为前批主机回归绑定已登记服务端签名向量后，`IndependentCredentialParityTest` 1/0/0/0、Gradle 退出 0；确认原 `IllegalStateException` 是运行器缺少 `E06_CREDENTIAL_VECTOR_OUTPUT`，不是产品 parity 失败。原 44 项 SQLite `SQLITE_CANTOPEN` 失败保全且未重跑；仅补齐输入分类与独立消费者证据，不计 O04/U04、D17、E06 L2、D18 L3 或第 8 节。

## 使用口径

有实现、作者验证、独立消费者、组合验证、现场验收及部署是不同结论。下列记录各自固定到对应输入/版本，不能拼成当前全仓通过。R01–R05 已完成的开发范围按历史复用，关键未验继续保留；R06–R09 局部通过不代表整体完成。

## 近期验收入口

- [E07/P07 隔离恢复锚点重锚](history/GOAL_E07_P07_REKEY_ACCEPTANCE_2026-10-14.md)：停机窗口内外部新锚、保留进程记录/空索引重签、活动写者与活动租约拒绝、原子替换失败回滚；同机合成定向 4/4 通过。异地恢复、运维编排、E07整体、D18 L3 和总目标继续。

- [D07/R09恢复态主管故障围栏](history/D07_R09_RECEIVED_FAULT_2026-10-10.md)：实际原截点恢复→原归档/native→既有申请与独立主管围栏，真实HTTP普通写拒绝、投影反例/事务回滚及独立消费；范围释放、新资格和最终接收仍未完成。
- [主管围栏后的暂停与接续](history/D01_D18_PAUSE_2026-10-10_RECOVERED_FAULT.md)：用户要求本步收口；完整断点、已验范围和沿原总计划的下一指令。

- [D07/R09已收正文原归档closed/native消费](history/D07_R09_RECEIVED_ARCHIVE_2026-10-10.md)：实际全量恢复目标的原verification/字节绑定、库存锁与消费者/提交前篡改回滚、独立工具消费及受影响普通回归；主管围栏和接收最终链另列。

- [D07/R09已收正文原verification恢复](history/D07_R09_RECEIVED_VERIFICATION_RESTORE_2026-10-10.md)：实际Android/HTTP原来源采集、独立pin、单表缺失样本原行恢复/锁/回滚/守恒；完整时间截点恢复与接收最终链仍缺。

- [D07/R09 native verification原行绑定](history/D07_R09_NATIVE_VERIFICATION_BINDING_2026-10-10.md)：显式原完整行摘要、事务锁/提交前重验与合成MySQL验证、真实恢复来源缺canonical拒绝分列；原native恢复及接收最终链仍缺。

- [D07/R09恢复归档同事务消费](history/D07_R09_ARCHIVE_TRANSACTION_CONSUMER_2026-10-10.md)：closed归档核验与受信消费者同事务、提交前重验、实际新目标回滚/可见性与独立原件消费；仅事务接缝，不代主管围栏或接收最终链。

- [D07/R09原E2A与占用跨截点恢复](history/D07_R09_TEMP_EVIDENCE_RESTORE_2026-10-10.md)：新空目标导入、原四表闭事务补齐、并发库存当前读修复、独立消费及新JVM重开分列；[恢复后归档读取](history/D07_R09_RECOVERED_ARCHIVE_READ_2026-10-10.md)另列，主管围栏/接收最终链仍需组合。

- [D07/R09原TEMP准备跨截点恢复](history/D07_R09_TEMP_PREPARATION_RESTORE_2026-10-10.md)：原截点dump→新空目标导入→五原分片/ACK闭事务恢复、冲突/回滚、独立消费与重启分列；正文/占用及接收最终链仍未完成。

- [D07/R09原权限截点与logged连续性](history/D07_R09_PERMISSION_LOG_BOUNDARIES_2026-10-10.md)：原有效权限/微秒时间与完整binlog前缀绑定，九类权限变化还原反例、MySQL及独立工具消费分列；不计历史授权或实际恢复通过。
- [D07/R09第一轮同一来源组合核验前置](history/D07_R09_INITIAL_COMPOSITION_2026-10-09.md)：同一pin逐门验证、双水位及原capture审计状态/保存引用；当次失败和普通回归分列，不计实际截点恢复通过。
- [D07/R09原FORMAL续期准备绑定前置](history/D07_R09_FORMAL_RENEWALS_2026-10-09.md)：原逐jti链复用首份准备基线及各自保存窗口；失败与回归分列，不计整体恢复通过。
- [D07/R09原FORMAL下载确认前置](history/D07_R09_FORMAL_ACTIVATION_2026-10-09.md)：无签名原ACK绑定独立固定原审计和assignment事实；当次验证与原准备缺失分列，不计整体恢复通过。
- [D07/R09原TEMP激活ACK前置](history/D07_R09_TEMP_ACTIVATION_2026-10-09.md)：公钥核原请求摘要、原激活与回执时间；开发夹具与保留原件消费者分层登记，不计整体恢复通过。
- [D07/R09原准备包字节与扫描基线前置](history/D07_R09_PREPARATION_2026-10-09.md)：原公钥/manifest/有界分片、库存周期/计划/扫描总量只读核验；续期/ACK/TEMP执行语义及保留原准备缺口分列，不计整体恢复通过。
- [D07/R09原TEMP政策与计划基线前置](history/D07_R09_TEMP_BASELINE_2026-10-09.md)：原政策、规则、可用游标、映射及首轮计划/扫描含义只读核验；定向反例与回归边界分列，不计整体恢复通过。
- [D07/R09原占用与权限语义前置](history/D07_R09_SOURCE_STATE_2026-10-09.md)：只读核原owner完整列、顺序、签名版本和逐确认/保存权限窗口；当次受影响回归及保留物料缺口分列，实际跨截点恢复仍缺，不计整体通过。
- [D07/R09关闭恢复事务前置](history/D07_R09_CLOSED_TRANSACTION_2026-10-09.md)：独立会话权威/独占租约/耐久日志、实际关闭投影和probe事务、Python互通与普通server回归；业务事实重建及接收最终链仍缺，不计整体通过。

- [D07/R09最小可信来源读取层](history/D07_R09_TRUSTED_SOURCE_2026-10-09.md)：外部固定原件/控制范围、公钥验签、原终态/原审计及逐来源逐保存时间资格前置；更高轮原激活/TEMP准备基线、受控恢复写入与接收最终提交仍待实现，不计D07/R09整体通过。
- [D07 FORMAL可信归档故障链](history/D07_FORMAL_FAULT_EXECUTION_2026-10-09.md)：独立主管围栏、原机无新STOP/ACK、非空TEMP附件迁移保全、FORMAL原件/权限/绑定反例、实际Web/HTTP/Android最终记账与重启、PDA原结果丢响应/重试/强停恢复、并发/尾SQL/Room回滚及独立消费者分层登记；无可信归档/R09及其余产品入口仍保留，不计D07整体通过。
- [FORMAL子项后的暂停与接续](history/D01_D18_PAUSE_2026-10-09_FORMAL.md)：本批退出标准、失败/跳过、明确范围Git回执、本机全量断点与下一可信恢复子项。
- [D07 TEMP故障附件开发记录](history/D07_FAULT_ATTACHMENTS_2026-10-09.md)：真实原字节/三项容量、原键恢复与签名有序引用、围栏及接收最终链、损坏或缺件不可核验、关闭写门重启下载、页面恢复/布局及独立消费者分层登记；不计D07或D10整体通过。
- [D07 TEMP故障可信归档链开发记录](history/D07_TEMP_FAULT_EXECUTION_2026-10-09.md)：范围、输入、原件消费者和失败原件分层登记；故障产品页面、FORMAL和R09接续缺口仍保留。
- [D07 TEMP故障产品页面开发记录](history/D07_TEMP_FAULT_UI_2026-10-09.md)：实际表单/可信读面/丢响应原键与接收最终链、恢复反例、布局、自审及回归入口；页面子项不等于D07整体通过。

- [D01–D18 用户要求的暂停断点](history/D01_D18_PAUSE_2026-10-08.md)：1102非空升级与接收上传已执行但E2A累计来源序列失败；失败库及继承路径保全，不能作为STOP通过。

- [D01–D18 总目标执行证据](history/D01_D18_EXECUTION_2026-10-07.md#d06-专用stop组合及产品页接续)：1103在新隔离输入完成真实HTTP/MySQL/Android接收机STOP组合；累计前缀修复、受影响回归、默认APK/lint和大字号产品页UI-08另有原件。失败轮、条件跳过、合成身份/回放HTTP及D06其余范围均分列，未构成D06或总目标完成。
- [D06 多跳与新开轮执行记录](history/D06_MULTIHOP_EXECUTION_2026-10-08.md)：1110完成真实隔离A→B→C多跳、连续累计序号与局部设备计数重叠；独立Node/SQLite消费者、服务端全量回归和受影响Android回归留有原件。UI-04实际新开轮页面在大字、丢ACK、重试、作业页及进程重开场景通过。混合R06/TEMP、LOCAL-only真实组合、减包/未冻结/无canonical及D06其余必做范围继续未验，不构成D06或总目标完成。
- [D06 混合交接与续作](history/D06_MIXED_EXECUTION_2026-10-09.md)：1117真实隔离A→TEMP交接B→R06续作B→成功及重启消费；独立签名/正文和只读Room消费者、失败轮及SQL尾写归因分别留证。反向、LOCAL-only及前置拒绝仍须对应组合证据，不能外推为D06整体通过。

- [D01–D18 总目标执行证据](history/D01_D18_EXECUTION_2026-10-07.md)：D01基线、D02/D03退出标准、D04/D05与政策阻断；D06 TEMP停止/主管审批、本人准备/确认/独立激活、新输入/冻结/独立上传与成功验签、实际Android大正文分块/重启续传与故障组合原件，失败输入及修复复验分列。后续D项按总目标退出标准独立判断。

- [前九步开发完成度审查](history/R01_R09_DEVELOPMENT_AUDIT_2026-10-07.md)：源码、阶段范围与保存证据的核对；未新增业务验收，历史通过不拼接为全仓通过。

- [R06 来源、停止保全与候选消费 B3](history/AUTONOMOUS_PROGRESS_2026-10-06.md#b3-当次交付)：VERIFY 原件状态一致、Android 丢回执恢复与非空迁移、真实非零候选消费者、五阶段 SQL 回滚和受影响全套；失败轮、交付回执与未验边界见记录。

- [R06 受控重关联 B1](history/AUTONOMOUS_PROGRESS_2026-10-05.md#b1受控重关联当次交付)：冻结来源映射切换、历史回执与原键、反例/回滚、受影响服务端回归；范围、失败轮、提交回执与非现场边界见记录。

| R06 **A1.1 收口：设备层可重复通过（已完成本批范围）** | [收口记录](history/R06_A1_1_CODEX_2026-09-30.md)：G1 在已 seed 设备上重复通过；②坏件专项通过；G4-2/G4-3 非干净重复运行通过（g4-approved-2b）；⑤丢回执三场景通过（loss-approved-2）。旧失败轮与包可见性诊断均保留；真实 HTTP/POST、真机重启/断电、真实登录配对、Keystore、A1.2 来源事实与候选消费仍未验。
| R06 **A1.1 第 2 步：单测缺口 G2/G3**（子步，非 A1.1 验收；提交 `a87d437`） | [子步记录](history/R06_A1_1_STEP2_UNIT_COVERAGE_2026-09-30.md)：新增 `LocalDraftStopWriterTest`（**4/0/0/0**，真实 Room/SQLite@Robolectric）覆盖 ① 先拒新输入／耐久保存／**失败不签停止**（补齐既有 rollback 测试未断言的"无签名行"与"未入队"）；新增 `LocalDraftSourceBindingMigrationTest`（**1/0/0/0**）覆盖 ④ 非空迁移 **27→28**（唯一落在 LocalDraft 时代的未核段）：原键/请求/回执逐项相等、**迁移不复活已暂停的自动续传**、新表建出为空、FK 干净、密钥不可用即不追补签名。并发级如实标注：3 条**顺序交织模拟**＋1 条**真实 4 线程串行化/幂等探针**，**真实竞态与真机重启未验**（G1/G4 留第 3 步）。**"6 事件 vs 4 尝试"最终定性＝我的判据误用**（`LocalDraftStopDao.count` 数的是 stop **明细项**；`event()` 首行 `check(db.inTransaction())` ⇒ 与停止同原子；恢复严格断言"事件==1"在 4 并发下通过）⇒ **无产品缺陷**。未改产品代码、未跑 AVD |
| R09 C2 **I3 执行（归档＋移除＋脚本/默认值改写＋定向回归）**（**已完成**） | [I3 执行记录](history/R09_C2_I3_EXECUTION_2026-09-30.md)：**A** 归档 `.local/archive/r06-e2a-migration-20260930.zip`（**62.5 MB／32,383 条目**），回读校验 **PASS**（逐条从包内重算 SHA256，全部 32,383 条，0 不一致 0 缺失）；**失败轮**：`bsdtar -T` 漏包 1,903 个，**改用 .NET `ZipArchive`** 后通过（证据 `.local/i3-archive/tar-attempt-failure.txt`）。**B** 移除共享树**共回收 14.21 GB**（`git worktree remove --force` 12.65 GB＋`rd /s /q \\?\…` 1.56 GB），D 盘可用 18.53→**32.73 GB**；**历史／`origin`／主树／`main` 未动**，`git worktree list` 已不含该树。**C** 8 个 `tools/` 脚本改默认值（两处**参数化 `-Root`**，我误加第二个 `param` 块的自身缺陷已修，8 个全部 parse 校验通过）。**D** `E2aWorktree.java:15` 默认值改主树（**失败轮**：`E2aMigrationEnvironmentTest` 的"另一个 worktree"前提反转，已改为 `resolveSibling` 结构性前提）。**E** 一级 **134 XML／2847 项／0 失败／0 错误／1 既有跳过**、E2a 抽样 **1/0/0/0**（走 D 的新默认值）、R08 抽样 **4/0/0/0**（走 C 的新默认值）——同时证明**无在跑路径依赖已移除的树**。`.local` 证据（223 个 `e2a-cross-*` 等）**未删除** |

| R09 C2 **W-2-d 第 1 层：恢复租约可见性只读计数（已实现并验证）**（**有产品改动**） | 新增 `recovery/RecoveryLeaseVisibility`（公共只读入口）＋`RecoveryLeaseScan.counts(roots)`＋`RecoveryLeaseVisibilityTest`：四类计数（`indexedEntries`/`indexedMissingRecords`/`indexMismatches`/`orphansObserved`/`orphansRetired`/`orphansRefused`）在**同一次协调锁内**采集，**绝不退休/改写/删除**任何文件，**不改变 `check` 的任何判决**；第 2 层定时器保持**默认关闭**、第 3 层不做（按裁定）。验证：`RecoveryLeaseVisibilityTest` **4/0/0/0**（`@TempDir`，无需 DB）；一级全量 **134 XML / 2847 项 / 0 失败 / 0 错误 / 1 既有跳过**（+4 项即本批）。失败轮 1 项（夹具把"已退休"当"干净树"）已修正 |

| R09 C2 **A2.1 主管分离：V50 已编码但未提交（阻断待裁定）** | [阻断诊断](history/R09_C2_A2_1_SUPERVISOR_SEPARATION_BLOCKER_2026-09-30.md)：根因＝更正内核是**两个 AND 主体**（`outbound:correct` 为所有命令的进门码 ＋ `correction:approve` 为审批码），V50 只切审批码 ⇒ **无人可批更正**；实证为**单条 SQL**（复用 run 773 datadir、0 新 provision）：同时持两码的角色查询**为空**。修法 A/B/C 待裁定，**B 推荐**（进门码按命令类型）。**V50 与 3 个夹具改动停在主树工作区，未提交**（避免在分支留不可用授权路径）。顺带结论：更正内核**上下文先于权限**校验，属 fail-closed 合理设计 |

| R09 C2 **四项待授权说明交付**（A2.1 现状登记 · B4 前置补齐①② · I3 待保留清单 · W-2-d 计数器与定时器设计）（**只交付说明，未执行授权内容**） | [交付索引](history/R09_C2_FOUR_AUTHORIZATIONS_DELIVERABLES_2026-09-30.md)：**A2.1**（[现状登记](history/R09_C2_A2_1_ROLE_PERMISSION_INVENTORY_2026-09-30.md)）全库 **12 个角色码、无"主管/SUPERVISOR"角色**，审批码 `order:recall/resume/dispose/handover`＋`correction:approve`（V45 草案）＋`outbound:correct` **全部只绑 `WAREHOUSE_ADMIN`**，无金额/范围/代理 ⇒ 6 个待决问题，**保持待确认**。**B4-①**（[历史 keyring 方案](history/R09_C2_B4_PREREQ_HISTORICAL_KEYRING_DESIGN_2026-09-30.md)）锚点**从不落盘**、只认当前一代，**推荐方案 B**（停机重锚＋重打，不落锚点）。**B4-②**（[重锚实测](history/R09_C2_B4_PREREQ_REKEY_MEASUREMENT_2026-09-30.md)）**1k=2.42s／10k=20.6s／100k=10.6min**、**非线性**（2.05→6.38 ms/条），**1M≈1.8h 为外推（主动中止）**，回滚＝还原快照＋恢复旧锚、不涉数据库。**I3**（[待保留清单](history/R09_C2_I3_RETENTION_LIST_2026-09-30.md)）实测 **83 项脏内容／约 14.3 GB**，**新发现 5 个 handover 未跟踪副本仍是 S38/S39 修复前的缺陷版**（`disabled_at`／`prepared_ticket_sha256` 未 unhex／回执未校验）⇒ 与 attempt-117 诊断块**均绝不并入、只归档**；8 个 tools 脚本默认值逐行清单。**W-2-d**（[计数器与定时器设计](history/R09_C2_W2D_ORPHAN_COUNTER_TIMER_DESIGN_2026-09-30.md)）修正形状：孤儿在同一次 `check()` 内同步退休并同次抛 `RECOVERY_LEASE_OUTSTANDING` ⇒ **拒绝不依赖定时器**；**第1层同事务计数立即做／第2层定时器默认关闭／第3层不建议**。**均未改代码、未处置共享树、未开开关** |

| R09 C2 **K1 裁定：通过（附 W-2-d 已知边界）**（**用户裁定登记＋文档同步**；**无代码改动**） | [K1 裁定记录](history/R09_C2_B1_K1_VERDICT_2026-09-30.md)：①厂商方言/DDL 变体**不需逐条单列**（拒绝为 fail-safe 方向、同一 `unclassified` 分支、逐条只重复验证同一路径）；②**改判 K1-1／K1 为通过**——判据②隐式提交满足（`G0K1RealJdbcBoundaryIT:381`/`:134`）；判据①③原缺口四类（`CallableStatement` 调用体、多语句、带注释语句、会话级 `SET`）已在门开启模式下补齐实测（`RecoveryEffectAdversarialSqlSurfaceIT` **135/0/0/0**、`delegate=0`、无效果），全批 **25 类/729 项/0 失败/0 错误/0 跳过**；K1-2 按 W-2 记**有界已知边界**并**保留 W-2-d 缺口**；K1-3 真实跨进程用例已交付（8 组合×3、`steps=000` 含 `basis`）；③看板/欠债条目/导航同步，失败轮 `.local/r09-k11-20260930/` **原文保留**；④**K1 通过 ≠ 生产就绪**：开关仍全默认 false、未部署、未合 main |

| R09 C2 **K1 判据证据核实与补齐（E1–E6 ＋ 不可归类形态 102→135 ＋ K1-3 真实用例更正）**（**同一会话实施＋自审＋运行**；**只新增测试**） | [K1 核实与补齐记录](history/R09_C2_B1_K1_1_EVIDENCE_VERIFICATION_2026-09-30.md)：按[判据提案](history/R09_C2_B1_K1_PASS_CRITERIA_PROPOSAL_2026-09-30.md) §6 重建 E1–E6——E1 **10/0/0/0**、E2 **5/0/0/0**、E3 **13/0/0/0**、E4 `RecoveryEffectAdversarial*` **501/0/0/0**、E5 `RecoveryEffectGateWiringRealIT` **1/0/0/0**（真实 MySQL＋生产注册表）、E6 **15/0/0/0**；**K1-1②满足**（`G0K1RealJdbcBoundaryIT:381` 真实 MySQL 观测 DDL 隐式提交后 `rollback()` 无法撤销）。**补齐**：`RecoveryEffectAdversarialSqlSurfaceIT` 102→**135 项**（新增 9 种不可归类形态×3 重复＝27，＋`prepareCall`→`execute` 调用体入口×2×3＝6）；实测 **135/0/0/0**，整批 **25 类/729 项/0 失败/0 错误/0 跳过**；`real-records.tsv` 记 `refusal=CONTROL_EFFECT_UNCLASSIFIED|delegate=0|observer=1:62:original;`。首轮按 `RECOVERY_FENCED` 断言致 33 项失败（**失败轮 `.local/r09-k11-20260930/` 保留**）；自审发现 `prepareCall` 本身不分类、分类在执行路径。**K1-3 更正**：真实跨进程用例**已交付**——`RecoveryFenceCrossProcessIT` 8 组合×3 含 `steps=000` 的 `basis=UNCONFIRMED_NO_CROSS_PROCESS_BASIS: external isolation required; canary delegate=1 observer=1`（3/3，run119）。⇒ K1 三项判据中 **K1-2、K1-3 已有落地结论**，**K1-1 仅剩"方言/DDL 变体是否需逐条单列"待裁定** |

| R09 C2 **K1-1 证据核实 ＋ 缺口补齐（SQL 形态 102→135 项）**（**同一会话实施＋自审＋运行**；**只新增测试**） | [K1-1 核实记录](history/R09_C2_B1_K1_1_EVIDENCE_VERIFICATION_2026-09-30.md)：按[K1 判据提案](history/R09_C2_B1_K1_PASS_CRITERIA_PROPOSAL_2026-09-30.md) §6 重建 E1–E6——E1 **10/0/0/0**、E2 **5/0/0/0**、E3 **13/0/0/0**、E4 `RecoveryEffectAdversarial*` **501/0/0/0**、E5 `RecoveryEffectGateWiringRealIT` **1/0/0/0**（真实 MySQL＋生产注册表）、E6 **15/0/0/0**；**判据②满足**（`G0K1RealJdbcBoundaryIT:381` 真实 MySQL 观测 DDL 隐式提交后 `rollback()` 无法撤销）。**缺口补齐**：`RecoveryEffectAdversarialSqlSurfaceIT` 由 102→**135 项**——新增 9 种不可归类形态（`SET SESSION`／`SET NAMES`／多语句／注释／`CALL`／`{call …}`）×3 重复＝27 项，＋`prepareCall`→`execute` 调用体入口×2 形态×3 重复＝6 项；实测 **135/0/0/0**，整批 **25 类/729 项/0 失败/0 错误/0 跳过**；`real-records.tsv` 记 `refusal=CONTROL_EFFECT_UNCLASSIFIED|delegate=0|observer=1:62:original;`。首轮按 `RECOVERY_FENCED` 断言致 33 项失败，**失败轮 `.local/r09-k11-20260930/` 保留**；自审发现 `prepareCall` 本身不分类、分类在执行路径。⇒ K1-1 判据①③原缺口形态**已补齐**，唯一残余＝**厂商方言/DDL 变体未逐条单列**（同一 `unclassified` 分支），改判与否属裁定。K1-2 已按 W-2 收口；K1-3 未补 |

| R09 C2 **K1-1 证据核实：E1–E6 事实表重建 ＋ 真实驱动形态缺口定位**（**只读＋复用本会话实测**；**无产品改动**） | [K1-1 核实记录](history/R09_C2_B1_K1_1_EVIDENCE_VERIFICATION_2026-09-30.md)：按[K1 判据提案](history/R09_C2_B1_K1_PASS_CRITERIA_PROPOSAL_2026-09-30.md) §6 重建六项证据——E1 `G0K1RealFileLockIT` **10/0/0/0**、E2 `G0K1WriterLeaseIdentityRealIT` **5/0/0/0**、E3 `G0K1RealJdbcBoundaryIT` **13/0/0/0**、E4 `RecoveryEffectAdversarial*` 全家族 **501/0/0/0**（12 类逐项列明）、E5 `RecoveryEffectGateWiringRealIT` **1/0/0/0**（＋`SqlIT` 11/`CommitIT` 14/`CursorIT` 7/`BatchIT` 3，均 0/0/0；走**真实 MySQL**与**生产注册表**）、E6 `RecoveryEffectAdversarialDefaultOffIT` **15/0/0/0**。**判据②满足**：`G0K1RealJdbcBoundaryIT:381` 在真实 MySQL 观测 DDL 隐式提交后 `rollback()` 无法撤销（另有 `setAutoCommit(true)` 同类）。**判据①③部分满足**：缺门开启模式证据的形态＝`CallableStatement` **调用体文本**（`CallableIT` 45 项实为参数 setter）、**多语句**、**带注释语句**、**`SET SESSION …`**、厂商方言/DDL 变体；`DefaultOffIT` 的 comment/multi/session 三项是"门关时放行"基线、**不是**拒绝证据。⇒ **K1-1 仍不通过**，缺口收敛为**4–5 类形态**，补齐只需测试侧新增。开关全默认 false |

| R09 C2 **B1-3 S43：A5——两树整合 I1＋I2（只读）＋ B7 §4 受影响回归**（**同一会话执行＋自审＋运行**；**无产品改动**） | [S43 记录](history/R09_C2_B1_3_S43_A5_TWO_TREE_I1_I2_2026-09-30.md)：按已获批 [B7 提案](history/R09_C2_B7_TWO_TREE_INTEGRATION_PROPOSAL_2026-09-30.md)执行。**I1**：唯一实质差异＝`CommandReceiptIntegrity.java` 共享树版多 5 行自述 `TEMPORARY DIAGNOSTIC (attempt 117)`（stderr＋栈），主树为干净版本 ⇒ **不并入**。**I2**：可执行路径扫描命中 9 文件——`E2aWorktree.java:15`（`E2A_CROSS_ROOT` 可覆盖默认值，runner 恒导出）、`r08-provision-and-run.ps1` 与 `e2a-android-link-l4.ps1`（`-Workspace` 默认值）、6 个旧 e2a/batch 工具族回退路径 ⇒ **主树当前 run 路径不依赖共享树、可独立运行**。**回归**：新跑 E2a 门控抽样（run 757，**1/0/0/0**）与 V36 迁移（`E2A_V36_ATTEMPT=e2a-v36-attempt5`，**1/0/0/0**，兼证 attempt 可配置真跑生效）；R-1/R-2/R-4/R-6/R-7 标注**同一 HEAD 既有证据、不重跑**（本批无任何可执行代码改动）。**I3 未做**（需单独授权），共享树 95 项脏内容原样保留 |

| R09 C2 **B1-3 S42：A4——R09 恢复与运维盘点（A4.1 证据对照；A4.2/A4.3 记阻断）**（**同一会话盘点＋自审**；产品侧仅一处注释对齐） | [S42 记录](history/R09_C2_B1_3_S42_A4_RECOVERY_INVENTORY_2026-09-30.md)：**A4.1** 逐项对照既有隔离证据——共享恢复控制/fence 与截点外 FH＝恢复链 **25 类/696 项/0 失败/0 错误/0 跳过**；**V36 恢复语义 6/6**（本轮新跑通 `E2aV36StopWriterIT` attempt3 与 `E2aV36AuditArchiveIT` attempt7）；**V37**＝`E2aDraftEvidenceIT`(717)/`E2aUnsubmittedHttpIT`(729)/`E2aWithdrawnCorrectionExportIT`(730) 全通过；**V38**＝`E2aJobFenceHttpIT` 已验；**隔离灾后重建**＝`G0K1RealFileLockIT`（强制终止与 `halt(70)` 后 OS 释放锁、残留文件≠持锁）＋租约身份 fail-closed。**历史 keyring 未实现**（`RecoveryLeaseRoots.verify()` 仅比对当前 `anchorId`）。**A4.2 部分未验**（RPO/RTO 数值目标属运维政策、未定不臆断；异地防覆盖属 C 档）。**A4.3/B9 未实施**（前置＝B4 实施级授权）。**唯一产品改动**：`CorrectionStore.java:75-81` 注释与 V46 实况对齐（纯注释、无行为变化，`compileJava` 通过） |

| R09 C2 **B1-3 S41：A3——R08 只读核对 ＋ 导出/汇总/重试/授权回归取证**（**同一会话实施＋自审＋运行**；**无产品改动**） | [S41 记录](history/R09_C2_B1_3_S41_A3_R08_REGRESSION_2026-09-30.md)：用既有 `tools/r08-provision-and-run.ps1`（主树 workspace、**端口必须 23308**、隔离 mysqld）跑通 **7 个 R08 类／47 项／0 失败／0 错误／0 跳过**：`R08ExportIT` 14（PC Excel 冻结原事实＋已验字节下载、PC/PDA 扫码冻结操作人与原始采集时间、PDA PDF 保留输入异常与原日期、占位操作人建任务前拒绝、短装计划含零合计与作废证据、已撤销权限/错误仓库拒绝、篡改产物与快照不发布会话、并发同请求单任务、幂等重放与格式变更、拒绝当前净值与未完成订单）、`R08SummaryIT` 6（空区间零合计可渲染、区间成员冻结与快照重放、同原键改区间拒、越界区间拒）、`R08CenterPaginationIT` 4、`R08ClockBoundaryIT` 2、`R08ExportFenceIT` 4、`R08RetryIT` 7、`R08AuthorizationRaceIT` 9。**harness 事实**：R08 静态守卫写死 23308 与库名；`R08ExportIT` 只要前缀、`R08CenterFixture` 要求**精确** `acrylic_r08_it` ⇒ 两族不能共库；`-DatabaseSuffix` 参数因脚本无 BOM 被 PS 5.1 截断不可绑定 ⇒ 改用 `-DatabasePrefix`。**未运行** `R08PreviewSeedIT`（需 `R08_PREVIEW_SEED=true`＋`acrylic_r08_preview`，记未尝试）。**A3.1**：原结果/调整/净值分别取 v1 读取器、更正凭据链、`order_result_version`＋`order_effective_scan`，重算仅作缺一即拒校验，**未发现报表自行重算**。失败轮 `.local/r08-it-1` 保留 |

| R09 C2 **B1-3 S40：A2.4 R07 政策边界验收（三天补录窗口 ＋ 附件三项上限）**（**同一会话实施＋自审＋运行**；**只新增测试，无产品改动**） | [S40 记录](history/R09_C2_B1_3_S40_A2_4_POLICY_LIMITS_2026-09-30.md)：新增 `E2aCorrectionPolicyLimitsIT`（run 756，**1/0/0/0/17.4s**，服务层、跑在已验证 R06 订单上）逐条验证接受/拒绝边界——附件数 6 拒；**单个正好 10MB 接受**、10MB+1 拒；**单行 50MB−5B 接受**、第二行推过 50MB 拒（均 `ATTACHMENT_LIMIT_EXCEEDED`）；业务时间**正好出库日−3 天接受**、−1ms 拒、−30 天拒、当前+30 分钟拒（`CORRECTION_WINDOW_EXCEEDED`）；被拒命令均不落文档。**口径核对**：窗口基准取 `order_result_version` **v1** 行 `created_at`（后续更正不移动窗口），判定式左右**含端点**。A2.4 结论：P-R07-06／P-R07-09 **已编码且边界已验**；**主管↔权限码映射待业主确认**（全库无"主管"角色）；附件仅校验客户端声明、无上传端点/真实字节/存储。失败轮 752–755 保留（含产品约束发现：同文档同 dispatch 仅一行）。回归 **133 XML/2843 项/0 失败/0 错误/1 既有跳过**。开关仍全默认 false |

| R09 C2 **B1-3 S39：A2.3 R07-1 outbox 落地 ＋ 第三处产品缺陷（prepared_ticket_sha256）**（**同一会话实施＋自审＋运行**） | [S39 记录](history/R09_C2_B1_3_S39_A2_3_OUTBOX_2026-09-30.md)：**A2.3 完成**——`HandoverStore` 新增 `nextOutboxEntityVersion`／`insertOutbox`／`serverRecoveryEpoch`；`Activate.execute` 在**同一事务内**写 `HANDOVER_ACTIVATED` 出站通知（只有 ACTIVATE 发通知＝交接链唯一的订单状态变更）。定向验证 run 751 **1/0/0/0/13.5s**：REQUEST/APPROVE/PREPARE 零通知、ACTIVATE 恰好一条、`published_seq=null`（待发可恢复）、同版本唯一、再激活被拒且 outbox 不增长。**第三处产品缺陷（已修）**：`prepared_ticket_sha256` 为 `BINARY(32)` 摘要列却被写入票据原始字节 ⇒ `Data truncation` ⇒ **PREPARE 从未成功**；改为入库前 `sha256(preparedTicket)`。**两处 harness 夹具缺口**（仓库 `mapping_revision` 与已发布修订不一致、`order_submission_slot` 无行）非产品缺陷，测试内如实补足。**已知限制**：outbox INSERT 之后的回滚无法在本 harness 演示（制造撕裂需删数据库守卫，harness 不做），同事务性由构造方式＋反例支撑。回归 `gradlew -p server test` **133 XML/2843 项/0 失败/0 错误/1 既有跳过**。**A2.2 矩阵 16 项全部收口**。失败轮 747–750 保留。K1 仍未通过；开关全默认 false |

| R09 C2 **B1-3 S38：两处授权产品修复（激活 `disabled_at` 缺陷 ＋ M6 回执完整性接线）**（**同一会话实施＋自审＋运行**；**产品代码改动，用户已授权**） | [S38 记录](history/R09_C2_B1_3_S38_R07_PRODUCT_FIXES_2026-09-30.md)：①`HandoverActivation.targetDevice/targetUser` 由查询**不存在的** `disabled_at` 改为真实列 `status='ACTIVE'`（拒绝码不变）⇒ 激活路径不再 SQL 语法失败；②`HandoverReceiptCodec.verify(row)` 新增（规范化重算摘要比对＋字段/标识核对＋JWS 签名校验），`HandoverCommandService.replay` 校验通过前不返回响应 ⇒ **M6 落成**。自审修正：签名 key id 改从 **JWS header** 读取（回执表**无** `signature_key_id` 列）；BINARY(16/32) 列按 UUID 文本/小写 hex 归一化。定向验证 run 746 **1/0/0/0/13.3s**：**M7** `ORDER_RECOVERY_REQUIRED`（此前为 `BadSqlGrammarException`）、**M6** `HANDOVER_RECEIPT_INTEGRITY_FAILURE` 且未新增回执行。**回归** `gradlew -p server test` **133 XML/2843 项/0 失败/0 错误/1 既有跳过**，与修改前基线一致。失败轮 743–745 保留。A2.2 矩阵：**16 已跑通／M13-① 待 A2.3**；A2.1 主管↔权限码映射待业主确认 |

| R09 C2 **B1-3 S37：A2.2 并发断言（M10–M12）＋ R07-1 激活路径产品缺陷（`disabled_at`）**（**同一会话实施＋自审＋运行**；**只新增测试，无产品改动**） | [S37 记录](history/R09_C2_B1_3_S37_A2_CONCURRENCY_AND_DEFECT_2026-09-30.md)：新增 `E2aHandoverConcurrencyIT`（run 742，**1/0/0/0，13.6s**，真实线程＋`CountDownLatch`）——**M10** 双 APPROVE 同版本 → `[HANDOVER_CONTEXT_CHANGED, OK]`（案件终态 `APPROVED`、**只有赢家留回执**、清单只写一次）；**M11** REQUEST×APPROVE 交错 → `[HANDOVER_STATE_INVALID, HANDOVER_RESULT_UNKNOWN]`（成功数≤1、每来源始终 1 行案件）；**M12** 同键并发重放 → `[OK, OK]`（回执/案件/审计仍各 1）。**新发现产品缺陷（已复现）**：`HandoverActivation.java:86,93` 查询 `device`／`app_user` 的 **`disabled_at`** 列——`V7` 起全部迁移中**不存在**该列（真实列是 `status IN ('ACTIVE','DISABLED')`），且全仓 grep `disabled_at` 仅此两处 ⇒ **PREPARE→ACTIVATE 在真实库上必然 `BadSqlGrammarException`，激活从未跑通**；M7（恢复代次守卫）位于其后、**当前无法到达**。修复建议（2 行改 `status='ACTIVE'`）**未实施、需授权**。A2.2 矩阵：**14 已跑通／M6 实现缺口／M7 被缺陷阻塞／M13-① 待 A2.3**。失败轮 737（产品缺陷）与 738–741（测试自身缺陷）全部保留 |

| R09 C2 **B1-3 S36：A2.2 零落点断言第一批（M5/M8/M9/M13-② 落成＋M6 定性）**（**同一会话实施＋自审＋运行**；**只新增测试，无产品改动**） | [S36 记录](history/R09_C2_B1_3_S36_A2_ASSERTIONS_2026-09-30.md)：新增 `E2aFormalRegisteredOperationsIT`（run 736，**1/0/0/0，13.3s**）——M5 清单状态无效→`HANDOVER_CHECKLIST_REQUIRED` 且**无残留清单行**；M8 原结果未终结→`HANDOVER_ORIGINAL_RESULT_OPEN`；M9a 案件版本过期→`HANDOVER_CONTEXT_CHANGED`；M9b 案件不存在→`HANDOVER_CASE_NOT_FOUND` 且**未留回执/未留案件行**；M13-② 以"被拒命令不留半条"（清单/证据/回执计数与基线相等、案件状态与 row_version 不变、`order_assignment` 仍 1 条 ACTIVE）收口。**M6 定性为实现缺口**：`HandoverCommand.corrupt()`（`HANDOVER_RECEIPT_INTEGRITY_FAILURE`）**零调用点** ⇒ R07-1 无回执完整性校验路径，篡改只命中 `replay` 逐字节比较（`IDEMPOTENCY_CONTENT_MISMATCH`），补它需产品改动＋单独授权。A2.2 矩阵：**11 已跑通／1 实现缺口／3 仍零落点（M7＋M10–M12）／1 待 A2.3**。失败轮 733/734/735 全为测试自身缺陷并保留。开关仍全默认 false |

| R09 C2 **A0.2 收口（V36 attempt 可配置）＋ K1-2 窗口 W 验收（W-2）＋ S35（A2.1/A2.2 R07 清单与断言矩阵）**（**同一会话实施＋自审＋运行**） | [K1-2 验收页](history/R09_C2_B1_K1_2_WINDOW_W_ACCEPTANCE_2026-09-30.md)＋[S35 记录](history/R09_C2_B1_3_S35_A2_R07_MATRIX_2026-09-30.md)：**A0.2** 按用户裁定把 V36 attempt 改为环境变量可配置（新增 `E2aV36Attempts`，6 个 V36 IT 默认值不变；`tools/r09-e2a-v36-run.ps1` 支持 `E2A_*_ATTEMPT`／`E2A_V36_DATABASE`／`E2A_V36_USER`，并修掉"未创建 attempt/private 目录"的 harness 缺陷）⇒ 两项此前未验的 **`E2aV36StopWriterIT`（attempt3）与 `E2aV36AuditArchiveIT`（attempt7）各 1/0/0/0 通过**，V36 家族 **4→6/6 全绿**；第一次失败轮（attempt2 缺 `stop-it-private.pk8`）保留。**K1-2** 按裁定 W-2 落成验收：孤儿扫描**无定时器**（只在启动/`checkEffect`/`registerOperation` 同步触发）⇒ 窗口 W＝触发间隔；四条标准 a/c 成立、b 成立但无配置项、**d（未决孤儿计数）为缺口** ⇒ 记"**已接受为有界已知边界**"，K1 仍未通过。**A2.1/A2.2**：R07 非"未实现"而是主路径已编码、缺口集中（主管角色不存在、库存差异无审批门、无补扣、故障换机仅枚举占位、撤销缺占用回退、更正表无触发器、附件无存储、无 outbox）；14 条断言实测 **7 条已跑通（服务层）＋9 条零落点**（run 718 1/0/0/0）。开关仍全默认 false |

| R09 C2 **B1-3 S34：A0 收尾批次（A0.1 剩余门控 IT ＋ A0.3 恢复链批次 ＋ 659 根因定位）**（**同一会话实施＋自审＋运行**；659 仪表化**运行后已撤回**） | [S34 记录](history/R09_C2_B1_3_S34_A0_CLOSURE_2026-09-30.md)：**A0.1** 新增 `tools/r09-a01-remaining-it-run.ps1`（驱动自己管 MySQL 生命周期；每类一次性 attempt），剩余门控 IT **19 类尝试／15 类通过／4 类失败／0 跳过**（run 712–730；补漏 `E2aWithdrawnCorrectionExportIT`）、`E2aServeBindingForAndroidIT` 连 Android 半场一并跑通 ⇒ **L4** ⇒ E2a 主树已验 **24→39**；4 个失败（`E2aFormalRegisteredSourceIT` 旧 `submission_scope` 列集、`E2aHandoverFormalIT` 名义/实现不符＋夹具漂移、`E2aTemporaryArchiveRecoveryIT` 归档子进程、`E2aTemporaryCommandBrowserIT` 见下）**均非产品缺陷**。**A0.3** 恢复链 pinned 批次 **25 类/696 项/0 失败/0 错误/0 跳过**、`portReleased=True`（首轮因保留实例崩溃恢复需约 41 秒预热超时，失败轮保留）。**659** 按裁定⑤仪表化＋**事后全部 `git checkout --` 撤回**：唯一抛出点 `TemporaryLinkOperation.java:43`，`want` ≠ `got`（`total=1`）⇒ 产品守卫按设计拒绝**过期候选摘要**，**不是产品缺陷**，改判"**harness 侧协议未闭合、单列**"，无需再对产品侧仪表化。**A0.2 未执行**（两个 V36 IT 自生成密钥对；真正阻塞＝一次性 attempt 已被消费，待用户选择改 attempt 常量或改为可配置）。另交付四份提案：K1 通过判据、B2 单批范围＋最小验收、B4 锚点保管/轮换、B7 两树整合顺序＋回归清单。开关仍全默认 false；K1 仍未通过 |

| R09 C2 **B1-3 S33：可执行清单固化与用户裁定/授权登记**（**纯文档轮，未开工**） | [S33 记录](history/R09_C2_B1_3_S33_EXECUTION_CHECKLIST_2026-09-29.md)：把十步完成度盘点固化为[可执行清单](REMAINING_DEVELOPMENT_PLAN.md)（A0 主线收尾／A1 R06／A2 R07／A3 R08／A4 R09／A5 两树整合／C 现场条件项），并登记用户 2026-09-29 裁定/授权七项：①先出 K1 通过判据提案再决定补做到通过或接受为已知边界（提案前 K1 仍未通过）；②先出 B2 单批范围＋最小验收提案，获批后接线；③**允许** harness 用 JDK 生成**仅隔离用**等价私钥跑 V36 stopwriter/auditarchive（只落 `.local`，不入库/不入 APK）；④先出锚点保管/轮换提案再实施 B9；⑤**授权**对 659 的产品侧命令上下文守卫做一次带日志仪表化运行（仅本机隔离、事后撤回；仍无法定位则改判"当前 harness 不可验"并单列）；⑥生产边界保持全关；⑦先出两树整合顺序＋回归清单提案。**本轮未跑测试、未改业务/测试代码、未开开关**；已验基线（E2a 主树 24 个门控 IT、库存家族 10 个 IT＋11 夹具、Android 套件 0 失败、恢复链 25 类/696 项/0/0）作为交接输入复用。开关仍全默认 false；K1 仍未通过 |

| R09 C2 **B1-2 跨进程围栏切片** | [规则↔实现↔真实多 JVM 用例、失败矩阵与证据](history/R09_C2_B1_2_FENCE_2026-09-29.md)；默认关闭、未接线/部署；K1 仍未通过，未进入 B1-3 |

| R09 C2 **B1-3 S32：夹具链与 Android 打通（10 个库存 IT ＋ Android 两项）**（**同一会话实施＋自审＋运行**；**无产品代码改动**） | [S32 记录](history/R09_C2_B1_3_S32_FIXTURES_ANDROID_2026-09-29.md)：**根因一次定死**——测试侧探针复刻 `OfflineSigner.keys()` 的载入逻辑得到 **`InvalidKeySpecException: Unable to decode key`** ⇒ 此前 **openssl 生成的 DER 不是 JVM 能解码的 PKCS#8/SPKI**；改用 **JDK 生成**（`getEncoded()`，与 `E2aCrossSourceHttpSetup.signing` 同编码）后 `SIGNING_KEY_UNAVAILABLE` 消失。改动（仅 harness/测试侧）：新增 `tools/GenSigningKey.java`；运行器加 **`-AppMigrates`**、缺密钥时用 JDK 生成（openssl 方案注明废弃）、补导出 `OUTBOUND_DB_*`／`OUTBOUND_SIGNING_*`；**4 个 Android 测试**去掉硬编码旧工作树路径的守卫（同 S17 思路）。**结果**：库存家族 **10 个 IT 全部通过**（PdaPreparation 8/8、SubmissionApi 12/12、SubmissionArchive 14/14、RecallApi 11/11、ContinuationApi 12/12、TemporaryContract/AuditContract/SubmissionContract 各 1/1、AuditArchive 15/15、DispositionApi 11/11）并生成 **11 个 PDA 夹具**；**Android 两项关闭**——710 `E2aTemporarySuccessConsumerIT` ✔（48.1s）、711 `E2aTemporaryArchiveIT` ✔（43.1s），Android 套件 0 失败。⇒ **E2a 主树已验证 22→24 个**。过程失败全留档（680–693、702–709）。未验：659（待裁定）、V36 stopwriter／auditarchive（待裁定私钥物料）；开关仍全默认 false；K1 仍未通过 || R09 C2 **B1-3 S31：夹具链——签名守卫的进程内证据**（**同一会话实施＋自审＋运行**；**无产品代码改动**，诊断探针已撤销） | [S31 记录](history/R09_C2_B1_3_S31_SIGNING_BINDING_2026-09-29.md)：①**应用侧绑定**——`server/src/main/resources/application.yml` L38–41 把 `outbound.signing.private-key/public-key` 直接绑到 **`OUTBOUND_AUTH_SIGNING_PRIVATE_KEY/PUBLIC_KEY`**，而这两个变量自 S21 起一直在导出 ⇒ **"属性空白"假设被推翻**。②**进程内生效值**（run 692，临时探针打印后已 `git checkout --` 撤销）：`OUTBOUND_AUTH_SIGNING_PRIVATE_KEY=…\private\server-private.der exists=true`、`…PUBLIC_KEY=… exists=true`、`OUTBOUND_MIGRATE=true` ⇒ 环境确实到达测试 JVM、密钥文件确实存在（私钥 1191 B PKCS#8、公钥 294 B SPKI，首字节 0x30）。⇒ `OfflineSigner.keys()` 抛的是 **catch 分支**（L71，非空白判定 L61），即 **try 块内失败**（PKCS8/X509 载入、模长 ≥2048、或 SHA256withRSA 自签验签）。**下一步（二选一）**：①测试侧反射取 `OfflineSigner` bean 调 `keys()` 并打印 `e.getCause()` 链（不改产品）；②若要应用内部细节则须**授权产品侧仪表化**（未授权不做）。在①结果前 **644／647＋8 个夹具 IT 一律判未验**，不得当产品缺陷。**E2a 主树已验证 22 个（不变）**；未验：659（待裁定）、V36 stopwriter／auditarchive（待裁定私钥物料）、644／647＋夹具链；开关仍全默认 false；K1 仍未通过 || R09 C2 **B1-3 S30：夹具链推进——上下文可起、503=签名密钥守卫**（**同一会话实施＋自审＋运行**；**无产品代码改动**） | [S30 记录](history/R09_C2_B1_3_S30_FIXTURE_SIGNING_2026-09-29.md)：给运行器加 **`-AppMigrates`**（`OUTBOUND_MIGRATE` 由硬编码 false 改为按开关）后，8 个库存夹具 IT **能起 Spring 上下文**（此前 `server_state` 不存在 ⇒ 上下文失败），但统一拿到 **503**；`-GradleInfo` 的 MockMvc 输出给出真实响应体 **`{"code":"SIGNING_KEY_UNAVAILABLE","message":"离线签名密钥不可用，暂不能领取订单"}`**（其前步骤均 `code:OK` ⇒ 非全局拦截）。harness 再补：自动生成离线签名密钥（Git 自带 `openssl` 产出 PKCS#8＋SPKI；调用处局部 `Continue`＋`*> $null`——openssl 进度点走 stderr，在 `Stop` 下会成终止错误）并导出 `OUTBOUND_SIGNING_*`；实测密钥文件正常（私钥 1191 B、公钥 294 B，均 0x30 开头）但守卫仍报不可用 ⇒ **下一处待查＝该 IT 是否覆盖/置空了 `outbound.signing.*`**（`OfflineSigner.keys()` 在 `privatePath/publicPath` 空白时即抛该码）。⇒ **644／647 与 8 个夹具 IT 一律判未验**。**E2a 主树已验证 22 个（不变）**；未验：659（待裁定）、V36 stopwriter／auditarchive（待裁定私钥物料）、644／647＋夹具链；开关仍全默认 false；K1 仍未通过 || R09 C2 **B1-3 S29：树内 chromium 安装＋Android/夹具链根因定位**（**同一会话实施＋自审＋运行**；**无产品代码改动**） | [S29 记录](history/R09_C2_B1_3_S29_CHROMIUM_ANDROID_CHAIN_2026-09-29.md)：①**树内 Playwright chromium 装好**（`gradlew -p report-rendering installChromium` BUILD SUCCESSFUL 37s，`report-rendering/build/browsers` 含 chromium-1187 等）⇒ S20 声明的"跨树回退 r08-export"**不再需要**。②**Android 两项（644／647）阻塞链定位到最后一环**：直接原因是 `:app:testDebugUnitTest` 的 **inputs 不存在**（`server/build/pda-*-fixture.json`，`android/app/build.gradle.kts` L85–94 声明）；这些夹具由 **8 个 inventory IT** 生成（PdaPreparation／SubmissionApi／SubmissionArchive／RecallApi／ContinuationApi／TemporaryContractFixture／TemporaryAuditContractFixture／TemporarySubmissionContractFixture；`docs/ORDER_NUMBER_GUIDE.md` 记过同坑）；而这 8 个 IT 在本 harness 先缺 `OUTBOUND_DB_*`（已补），再报 **`Failed to load ApplicationContext` → `SELECT recovery_epoch, service_mode FROM server_state` → `Table '…server_state' doesn't exist`** ⇒ **schema 未被迁移**，因为我按参考值导出了 `OUTBOUND_MIGRATE=false`。⇒ **下一步只需对该批 IT 用 `OUTBOUND_MIGRATE=true`（无产品改动）**。③harness 改动一处：运行器补导出 `OUTBOUND_DB_URL/USER/PASSWORD`（与 SPRING_DATASOURCE 同值）；**单靠它未让 8 个夹具 IT 通过**，如实记录。④过程失败留档：我改坏过运行器参数块（3 个解析错误）⇒ 用 `git checkout --` 恢复已提交版本后重做；run 664–671（缺 DB 变量）与 672–679（上下文失败）全部保留。**E2a 主树已验证 22 个（本轮不变）**；未验：659（待裁定）、V36 stopwriter／auditarchive（待裁定私钥物料）、644／647（下一步 `OUTBOUND_MIGRATE=true`）；开关仍全默认 false；K1 仍未通过 || R09 C2 **B1-3 S28：V36 Audit 两 IT——1 通过、1 只差私钥物料**（**同一会话实施＋自审＋运行**；**无产品代码改动**） | [S28 记录](history/R09_C2_B1_3_S28_V36_AUDIT_2026-09-29.md)：读 `requireIdentity` 发现两个 Audit IT 与 V36 迁移类**相反**，断言 **`SELECT @@log_bin == 0`（必须关 binlog）**，而我的运行器一律开 binlog ⇒ 即 `Connected MySQL is not the isolated audit … target` 之因。改动：`tools/r09-e2a-v36-run.ps1` 按 item 决定启动参数（`auditprefix`/`auditarchive` → `--skip-log-bin`，其余 → `--log-bin-trust-function-creators=ON`）。**结果**：`E2aV36AuditPrefixIT` **✔ 通过**（6.4s）⇒ **E2a 主树已验证 21→22**；`E2aV36AuditArchiveIT` 前进到只缺**私钥物料** `<attempt>/private/private.pk8`（与 `E2aV36StopWriterIT` 需 `stop-it-private.pk8` 同类）。⇒ **待用户裁定②**：是否允许在 harness 内生成**仅用于隔离实验舱**的等价私钥并落在 `.local` attempt（私钥不入库）；裁定前两项保持未验、不做猜测性替代。**659 待裁定①**（产品侧仪表化 or 改判不可验单列）。未做：Android 工具链（644／647）、树内 chromium、Python v2 供给（B9）；开关仍全默认 false；K1 仍未通过 || R09 C2 **B1-3 S27：V36 家族供给与首批运行（3 通过 / 3 定位）**（**同一会话实施＋自审＋运行**；**无产品代码改动**） | [S27 记录](history/R09_C2_B1_3_S27_V36_FAMILY_2026-09-29.md)：新增 `tools/r09-e2a-v36-run.ps1`，为六个门控 IT 各自供给隔离实例——datadir 放在 **IT 自己断言的** `<attempt>/data`、端口 19330、每次新建 schema/用户并 `GRANT ALL`（含 PROCESS/SHUTDOWN 与环回 root）、binlog＋trust_function_creators、导出 `E2A_<PREFIX>_URL/_USER/_PASSWORD` 与 **`E2A_CROSS_ROOT=<主树>`**（漏掉此变量会去共享树找 attempt，实测报 "V36 one-shot profile mismatch"）、口令落盘供 `-Reuse` 复用、`test-started.marker` 存在即拒绝重跑、`-Shutdown` 关实例。**结果**：`E2aV36MigrationIT` ✔（7.0s）、`E2aV36DeviceKeyIT` ✔（6.7s）、`E2aV36PairedKeyIT` ✔（6.6s）⇒ **E2a 主树已验证 18→21**；`E2aV36StopWriterIT` ✗ 需**预置私钥** `<attempt>/private/stop-it-private.pk8`（IT 不自己生成，物料引入需授权）；`E2aV36AuditPrefixIT`／`E2aV36AuditArchiveIT` ✗ 报 `Connected MySQL is not the isolated audit … target`（其 `requireIdentity` 与供给仍有差异，下一步逐项比对）。三者阻塞**均非产品问题**。**659 仍待用户裁定**（产品侧仪表化需授权，或改判不可验单列）。工具自身迭代失败（`class` 保留字、注释与 `$table` 并行、Gradle stderr 在 Stop 下成终止错误、`--defaults-extra-file` 必须首位、`password.txt` 早于建目录、误用自动变量 `$args`）全部留档未删。开关仍全默认 false；K1 仍未通过 || R09 C2 **B1-3 S26：659 的 409 两次驱动实验（均仍 409，未验保持）**（**同一会话实施＋自审＋运行**；**无产品代码改动**） | [S26 记录](history/R09_C2_B1_3_S26_659_CONTEXT_409_2026-09-29.md)：两次单点实验都带证据——①**把 `candidateDigest()` 提到 `facts#1` 之前**（假设候选读取推进上下文使快照过期）：序列变为 `… csrf (after login) → candidates 200 → facts#1 200 → LINK 409 COMMAND_CONTEXT_CHANGED` ⇒ **409 仍在**；②**命令 `expected.candidateContextSha256` 改用快照自身值**（不再用候选接口值覆盖）＋两值不同时打日志：**仍 409**，且日志**无 `digest mismatch`** ⇒ **两来源一致，覆盖不是原因**。③663 datadir 离线审计（19335，已 shutdown）：最新事件为 `WEB_LOGIN`／`SUBMISSION_REVIEW_REQUIRED`／`TEMPORARY_PREPARATION_*`／`ORDER_ASSIGNED`，**无该 409 的事件** ⇒ 字段级差异**不在审计里**。**结论**：S25 的 CSRF 修复保留有效（403 不再出现）；659 **仍未验**，已排除"候选顺序"与"摘要覆盖"。**停止点/授权边界**：定位不匹配字段须**对产品侧命令上下文守卫做带日志的仪表化运行**（改动产品代码）⇒ **需明确授权**；未授权前不做、也**不当产品缺陷**（现有证据只支持"守卫按设计拒绝，原因未定"）。E2a 主树已验证 18 个不变；未做：Android 工具链（644／647）、V36 门＋供给、树内 chromium、Python v2 供给（B9）；开关仍全默认 false；K1 仍未通过 || R09 C2 **B1-3 S25：659 的 403 根因修复（测试侧）＋推进到业务级 409**（**同一会话实施＋自审＋运行**；**无产品代码改动**） | [S25 记录](history/R09_C2_B1_3_S25_DRIVER_CSRF_FIX_2026-09-29.md)：**代码级根因**——`web/tools/e2a-web-command-flow.mjs` 只在登录**前**取一次 `/api/v1/auth/csrf`，登录后（认证会**轮换** CSRF 令牌）仍复用旧令牌发 POST ⇒ 被**业务之前**的安全层拒绝（403 `ACCESS_DENIED`＋文案"安全凭据失效，请刷新后重试"，且**不留审计行**，与 S24 库证据一致）。改动：把取 CSRF 抽成 `readCsrf(label)`，**登录成功后重新取一次**（`csrf (after login)`），`node --check` 通过。**结果（run 661）**：403 **消失**，`steps=[csrf, login 200 authenticated=true, **csrf (after login)**, facts#1 200, candidates 200, **LINK 409 COMMAND_CONTEXT_CHANGED**]` ⇒ 安全层问题彻底关闭，阻塞前移到**业务级乐观并发守卫**（`facts#1` 与 LINK 之间驱动还调了 `duplicate-candidates`，很可能推进了 `candidateContextSha256`/`scopeRound` 使快照过期）——**这是产品守卫按设计工作，非缺陷**。下一步（最小）：驱动在取回候选摘要后**重读 facts** 再用最新 `expected` 发 LINK；若通过则 659 转已验（第 19 个）。E2a 主树已验证 18 个不变；未做：Android 工具链（644／647）、V36 门＋供给、Python v2 供给（B9）；开关仍全默认 false；K1 仍未通过 || R09 C2 **B1-3 S24：659 的 403 定性——非业务层拒绝（安全/凭据）**（**同一会话实施＋自审＋运行**；**无代码改动**） | [S24 记录](history/R09_C2_B1_3_S24_659_SECURITY_LAYER_2026-09-29.md)：三步证据——①**读源码排除两假设**：`TemporaryCommandBrowserChecks` 自己 `INSERT INTO role_permission … code=''temporary_order:link''` 并 `INSERT INTO user_role` 给 supervisor，案卷按**同一仓库**筛选 ⇒ 不是"缺角色"也不是"仓库不匹配"；②**现场响应体**（run 660 的 `browser-command-flow.json`）：`code=ACCESS_DENIED`、`message="权限不足或安全凭据失效，请刷新后重试"`、`serverRecoveryEpoch=1`、`steps=[csr f, login 200 authenticated=true, facts#1 200, candidates 200, **LINK 403**]`；③**应用审计决定性**：660 datadir 离线查库 `operation_event` 共 14 行、**无任何 `%DENIED%`/`%COMMAND%`/`%LINK%` 事件** ⇒ 403 **未进入业务/审计层**，是**安全层（凭据/CSRF）**在业务之前拒绝。⇒ 与 S22 同源类：GET 通过、**POST 被拒**且文案写"安全凭据失效，请刷新后重试" ⇒ 指向**登录后 CSRF 令牌轮换**而 SPA 驱动仍用旧令牌。659 **仍判未验**，但**不是产品缺陷**的证据。下一步（小动作二选一）：①查是否有"关闭认证后 CSRF 轮换"的配置项供 harness 打开；②读驱动序列确认 `login` 后是否重新取 `/auth/csrf`（若无则属测试侧修正）。E2a 主树已验证 18 个不变；未做：Android 工具链（644／647）、V36 门＋供给、Python v2 供给（B9）；开关仍全默认 false；K1 仍未通过 || R09 C2 **B1-3 S23：659 的权限/作用域查证与收窄**（**同一会话实施＋自审＋运行**；**无代码改动**） | [S23 记录](history/R09_C2_B1_3_S23_659_SCOPE_2026-09-29.md)：查证（源码＋659 保留 datadir 离线查库）——①LINK 需要的就是 `temporary_order:link`（与 IT 一致）；②`role_permission`／`user_role` **都没有仓库/作用域列**；③该库授予计数 `temporary_order:confirm` 3、`create` 1、**`link` 2**、`manage` 1；④IT 启动时**已显式打开** `--outbound.temporary.commands.enabled=true`，LINK 在 `TemporaryCommandBrowserChecks` 内 ⇒ 排除"权限缺失"与"作用域列绑定"，剩余三选一：①浏览器流程的 actor 不持有含该码的角色；②**代码级仓库比对**；③真实产品行为差异。**下一步最小**：读 `TemporaryCommandBrowserChecks` 的 LINK 构造（登录用户/角色/案卷仓库）＋一次查库即可判定；**此前不得当作产品缺陷**。**E2a 主树已验证＝18 个**（604/621/622/623/628/629/630/631/634/635/638/639/640/641/643/648/657/658）＋C1 611，本轮不变。未做：659 判定、Android 工具链（644／647）、V36 门＋供给、树内 chromium、Python v2 供给（B9）；开关仍全默认 false；K1 仍未通过 || R09 C2 **B1-3 S22：Secure cookie／CSRF 根因——633 与 642 转为已验**（**同一会话实施＋自审＋运行**；**无产品代码改动**） | [S22 记录](history/R09_C2_B1_3_S22_SECURE_COOKIE_2026-09-29.md)：给 harness 加 `-GradleInfo`（Gradle `-i` 转发测试 stdout/stderr）；读源码得登录流程（`/auth/csrf` → form POST `/auth/web/login`，断言 200）；**决定性诊断**：655 保留 datadir 离线查库**无任何 `WEB_LOGIN_FAILED` 审计行** ⇒ 403 发生在**认证之前的 CSRF 过滤器**；对照通过的 640 显式传 `--server.servlet.session.cookie.secure=false` ⇒ **Secure cookie 在明文 HTTP 下不被 JDK HttpClient 回送**导致 CSRF 失败。harness 补 `SERVER_SERVLET_SESSION_COOKIE_SECURE=false`（显式参数仍优先）后：**657 `E2aCorrectionChainIT` ✔（43.0s）、658 `E2aHandoverHttpLayerIT` ✔（16.8s）⇒ 633／642 由未验转已验**；659 `E2aTemporaryCommandBrowserIT` 仍失败但**性质澄清**（登录 200 后在 LINK 得 403，权限行与授予均在 ⇒ 余下只可能是**作用域绑定**或**另一个权限码**，不再是 cookie/渲染/权限缺失）。**E2a 主树已验证＝18 个**（604/621/622/623/628/629/630/631/634/635/638/639/640/641/643/648/657/658）＋C1 611。未做：659 作用域判定、Android 工具链、V36 门＋供给、树内 chromium、Python v2 供给（B9）；开关仍全默认 false；K1 仍未通过 || R09 C2 **B1-3 S21：权限/角色前提第三次尝试＋决定性诊断**（**同一会话实施＋自审＋运行**；**无产品代码改动**） | [S21 记录](history/R09_C2_B1_3_S21_PERMISSION_DIAGNOSIS_2026-09-29.md)：①按参考 runner 补齐四个环境变量（`OUTBOUND_MIGRATE=false`、`OUTBOUND_STORAGE`、`OUTBOUND_AUTH_SIGNING_PRIVATE_KEY/PUBLIC_KEY`）后复跑 651 `E2aCorrectionChainIT`／652 `E2aHandoverHttpLayerIT`／653 `E2aTemporaryCommandBrowserIT`——**失败点与补前完全一致** ⇒ **排除"缺参考环境变量"这一假设**。②对 653 保留 datadir 离线挂载查库（查完已 shutdown）：`temporary_order:{confirm,create,link,manage}` **权限行 4 条、授予 7 条、user_role 6 行** ⇒ **`LINK 403 ACCESS_DENIED` 不是"权限行缺失/未授予"** ⇒ 收窄到**作用域/权限码/临时模块开关**三选一。③停止继续盲试的理由与下一步已写明：需先给 harness 加**测试 stdout 转发**（`showStandardStreams`／`--info`）取回各 IT 的 `log=` 与断言上下文，再逐项判定。633／642／645 仍判**未验**；E2a 已验 16 个不变；开关仍全默认 false；K1 仍未通过 || R09 C2 **B1-3 S20：报表/渲染服务供给**（**同一会话实施＋自审＋运行**；**无产品代码改动**） | [S20 记录](history/R09_C2_B1_3_S20_RENDERER_HOME_2026-09-29.md)：**读源码定位根因**——`ExportRendererProcess.requireAvailable` 要求 `renderer-home` 下有 `lib/`（PDF 还要 `browsers`），渲染是子进程 `java -cp <home>/lib/* …ReportCli`；E2a 侧只有部分 IT 显式传 `renderer-home`，没传的（646／633）必然拿到 `EXPORT_UNAVAILABLE`。harness 补 `OUTBOUND_EXPORTS_RENDERER_HOME=<tree>/report-rendering/build/install/acrylic-report-rendering` 与 `OUTBOUND_EXPORTS_BROWSERS`（树内优先，否则回退到 IT 自己硬编码的 `r08-export` 那份，已声明）。**结果**：648 `E2aTemporaryCommandWebHttpIT` **✔ 通过**（E2a 主树累计 **16 个**）；633 `E2aCorrectionChainIT` **推进两级**——649 失败点由「渲染必须成功（XLSX）」变「（PDF）」，650 变 **`[real Web login] 200 vs 403`** ⇒ **渲染已不再是阻塞项**，633 与 642／645 同源地卡在**权限/角色种子前提**（仍判未验）。未做：权限/角色种子与迁移目标、Android 工具链（644／647）、V36 门＋供给、树内 chromium 安装、Python v2 供给（B9）；开关仍全默认 false；K1 仍未通过 || R09 C2 **B1-3 S19：E2a 批次四**（**同一会话实施＋自审＋运行**；**无产品代码改动**） | [S19 记录](history/R09_C2_B1_3_S19_E2A_BATCH4_2026-09-29.md)：**643 `E2aSourceBindingHttpIT` ✔**；644 `E2aTemporaryArchiveIT`／647 `E2aTemporarySuccessConsumerIT` ✗ 依赖 **Android 单测子进程**（`See protected android-*.log`）；645 `E2aTemporaryCommandBrowserIT` ✗ 轨迹 `…\"LINK 403 ACCESS_DENIED\"` ⇒ **权限行未授予**（较晚迁移才注册权限，与本 harness 的种子/迁移状态同类老陷阱）；646 `E2aTemporaryCommandWebHttpIT` ✗ 响应 `EXPORT_UNAVAILABLE / 报表生成服务尚未配置` ⇒ **报表/渲染服务未配置** ⇒ **同时给出 633「渲染必须成功（XLSX）」的有证据假设**（同一处 `EXPORT_UNAVAILABLE`，仍判未验）。**本批无任何 B1-3 相关产品缺陷**：四个失败各自定位到具体环境前提。**E2a 主树已验证＝15 个**（604/621/622/623/628/629/630/631/634/635/638/639/640/641/643）＋C1 611。未做（均为独立环境批次）：报表服务配置、权限种子/迁移到较晚版本、Android 工具链、V36 门＋供给、Python v2 供给（B9）；开关仍全默认 false；K1 仍未通过 || R09 C2 **B1-3 S18：E2a 批次三**（**同一会话实施＋自审＋运行**；**无产品代码改动**） | [S18 记录](history/R09_C2_B1_3_S18_E2A_BATCH3_2026-09-29.md)：**638 `E2aTemporaryAccountingIT` ✔、639 `E2aTemporaryCandidatesIT` ✔、640 `E2aTemporaryExportIT` ✔、641 `E2aTemporaryLinkIT` ✔**；642 `E2aHandoverHttpLayerIT` ✗ —— 断言实得 `-1`，源码 `status = session.login(outsider) ? … : -1` ⇒ **登录/会话前提未满足**（请求根本没发出），非产品判定亦非 B1-3 回归。顺带查明**额外环境门清单**（`E2aMigrationExecutionIT`→`E2A_MIGRATION_RUN`；V36 家族 6 个各需 `E2A_V36_VERIFY`／`E2A_DEVICE_KEY_VERIFY`／`E2A_KEY_VERIFY`／`E2A_STOP_VERIFY`／`E2A_AUDIT_PREFIX_VERIFY`／`E2A_AUDIT_ARCHIVE_VERIFY`；未开即 skip，但**不得为跑而开门**，须先补供给）。**633 收窄**：640 通过 ⇒ 导出/渲染链路整体可用，633 的"渲染必须成功（XLSX）"是该路径特有，下一步读该 attempt 的渲染作业日志定性。**E2a 主树已验证＝14 个**（604/621/622/623/628/629/630/631/634/635/638/639/640/641）＋C1 611。未做：余下约 20 个门控 IT、V36 供给、633 判读、642 登录供给、Python v2 供给（B9）；开关仍全默认 false；K1 仍未通过 || R09 C2 **B1-3 S17：E2a 批次二 + 工作树根收敛**（**同一会话实施＋自审＋运行**；**无产品代码改动**） | [S17 记录](history/R09_C2_B1_3_S17_E2A_BATCH2_2026-09-29.md)：**发现**12 个 E2a 测试文件各自硬编码旧共享工作树路径（632 的 `NoSuchFileException …r06-e2a-migration\…confirmation-race-same-key.json` 即此）⇒ 新增单一来源 `E2aWorktree.ROOT`（`E2A_CROSS_ROOT` 覆盖、默认不变）并替换全部 12 处（含 `E2aV36*IT`、迁移环境/运行器/启动器、`inventory` 探针），编译通过。**结果**：630 `E2aJobFenceHttpIT` ✔、631 `E2aTemporaryDecisionProtocolIT` ✔、634 `E2aHandoverModelIT` ✔、**635 `E2aTemporaryConfirmationIT` ✔（632 失败→根收敛后通过）**；633 `E2aCorrectionChainIT` ✗（导出/渲染断言 `CorrectionExportSnapshotChecks.renderAndVerify:130`，未定性）；636/637 `E2aV36MigrationIT`/`E2aV36DeviceKeyIT` **skip=1（另有环境门，记未尝试不记通过）**。**E2a 主树已验证＝10 个**（604/621/622/623/628/629/630/631/634/635）＋C1 611。未做：其余约 25 个门控 IT、V36 环境门、渲染断言判定、Python v2 供给（B9）；开关仍全默认 false；K1 仍未通过 || R09 C2 **B1-3 S16：E2a 闭合投影攻克 + 修正 schema 抽样**（**同一会话实施＋自审＋运行**；**无产品代码改动**） | [S16 记录](history/R09_C2_B1_3_S16_PROJECTION_2026-09-29.md)：**根因是供给清单不完整，非产品缺陷**——`RecoveryProjectionDatabases` 类注释即写明负例 schema"由隔离运行器预分配"，要求 `${schema}{copy,conflict,missing,gap,overflow,rollback,instance,skip,mapping}` 存在且为空（S15 只凭失败逐个补了 3 个）。工具改为精确集合＋`_restore`，删除自造后缀。**结果**：`E2aClosedProjectionIT`（628）通过 1 用例/0 失败/67.4s，产物 `projection-before-migration.json`(193 872 B)、`projection-before-control.json`(622 952 B)、`projection-after-control.json`(624 628 B)＋负例 setup 日志（非空原库逐字节保持、负例各达预期控制码）；`E2aCorrectionSchemaIT`（629）通过。**E2a 已验证清单（主树 B1-3 代码）＝604 入口、621 写者事务、622 最新 schema 回归、623 跨源 HTTP、628 闭合投影、629 修正 schema（6 个）**；C1 侧 611 通过。未做：其余约 30 个门控 IT、Python v2 供给（B9）；开关仍全默认 false；K1 仍未通过 || R09 C2 **B1-3 S15：E2a 其余 IT 首批重跑**（**同一会话实施＋自审＋运行**；**无产品代码改动**） | [S15 记录](history/R09_C2_B1_3_S15_E2A_BATCH_2026-09-29.md)：按 S14 配方分批重跑，首批 4 个：**`E2aRecoveryWriterTransactionIT`（621）、`E2aLatestSchemaRegressionIT`（622）、`E2aCrossSourceHttpIT`（623）在主树 B1-3 代码上通过**；`E2aClosedProjectionIT`（620、624–627）未通过——它按用例名在运行期拼出伴随 schema（`<schema>_{copy,instance,conflict,missing,…}`），本轮为工具补了 schema 族与一次性隔离实例的全局授权（**harness 偏差已声明**），最终停在 `Unknown database '<schema>_missing'`（权限已通、缺库/或该步"库缺失时优雅拒绝"未成立）；625 的 attempt 已产出 `admitted-datasource-check.txt`、`leased-transaction-checks.txt` ⇒ 失败在投影阶段后段，**不是 B1-3 早期步骤回归**，但该项仍判未验。未做：其余 30+ 门控 IT、`_missing` 期望判定、Python v2 供给（B9）；开关仍全默认 false；K1 仍未通过 || R09 C2 **B1-3 S14：C1 实验舱重跑（主树）**（**同一会话实施＋自审＋运行**；**无产品代码改动**） | [S14 记录](history/R09_C2_B1_3_S14_C1_RERUN_2026-09-29.md)：C1 与 E2a 共用主树 ROOT 与同一 `.local/e2a-cross-20260925-<run>` 一次性布局（run≥557、19330、binlog 必开、空 schema 自种子），故 S13 的工具直接适用；本轮只补一件事——**C1 启动应用不带 datasource 参数**，工具需额外导出 `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`（显式 `--spring.datasource.*` 仍优先）。验证：**`C1InitialNormalHttpIT` 在主树 B1-3 代码上通过**（实例 19330、schema `acrylic_e2a_cross_20260925_611`）1 用例/0 失败/0 错误/23.3s；产物含 `c1-http-1..20.txt`、`c1-lost-commit.txt`、`c1-original-facts-before/after.json`（逐字节相等，11 191×2）、`test-completed.marker`；入口语料（`C1EntryRegression` 复用 attempt105，已按 B1-3 重基）`rootRequired=RECOVERY_OPERATION_REQUIRED`、`closeWaitedMillis=2512 innodbTrx=0`、`admittedRootStillServes=allowed`＋`newRootWithPendingIntent=CONTROL_CLOSING_INTENT`、丢回执 `committedCounter=2`。失败轮 `610`（缺数据源环境变量⇒`'url' must start with "jdbc"`）保留。产品未改故沿用 S12 轮 25 类批次证据。未做：E2a 其余 IT、C1 smoke 自身仍需 pwsh 7、Python v2 供给（B9）；开关仍全默认 false；K1 仍未通过 || R09 C2 **B1-3 S13：E2a 实验舱重跑（主树）与实验舱重基**（**同一会话实施＋自审＋运行**；**无产品代码改动**） | [S13 记录](history/R09_C2_B1_3_S13_E2A_RERUN_2026-09-29.md)：先量后动——C1 smoke 需 pwsh 7（本机无）且只是启动冒烟；E2a 运行器与 `E2aCrossSourceProfile` 都把 ROOT 硬编码为只读共享树（detached ea4d166＝B1-3 之前）⇒ 在那里跑**验不到主树**。改动：`E2aCrossSourceProfile.ROOT` 支持 `E2A_CROSS_ROOT` 覆盖（默认不变、其余一次性约束全保留）＋新增 5.1 兼容工具 `tools/r09-e2a-entry-rerun.ps1`（自供给 binlog 实例＋建库建号＋`GRANT PROCESS/SHUTDOWN`＋`root@127.0.0.1`＋跑 `integrationTest`）。验证：**E2a 入口 IT 在主树代码上通过**（实例 19330、schema `acrylic_e2a_cross_20260925_604`）1 用例/0 失败/18.9s，attempt 内 `rootRequired=RECOVERY_OPERATION_REQUIRED`、`closeWaitedMillis=2497 innodbTrx=0`、`admittedRootStillServes=allowed`＋`newRootWithPendingIntent=CONTROL_CLOSING_INTENT`、连接丢失/丢回执均 `CONTROL_WRITERS_ACTIVE`。**供给配方（复用）**：binlog 必开＋trust_function_creators；账号需 PROCESS；`--skip-name-resolve` 下需 `root@127.0.0.1`；IT 走 `integrationTest`（`test` 排除 `**/*IT.class`）。产品未改故沿用 S12 轮 25 类批次证据。未做：C1 版运行器、E2a 其余 IT、Python v2 供给（B9）；开关仍全默认 false；K1 仍未通过 || R09 C2 **B1-3 S12：逐根租约发布与孤儿退役**（**同一会话实施＋自审＋运行**；设计第 10/12 行） | [S12 记录](history/R09_C2_B1_3_S12_LEASE_PUBLICATION_2026-09-29.md)：`RecoveryOperationRegistry` 新增 `LeaseChannel`，`admit(...)` 在临界区外为每个**服务态**根发布一条 P5 租约记录（`leaseId()` 可见；发布失败⇒收回根、接纳失败）；`RecoveryLeaseScan.check(...)` 按 §5.3 判定孤儿后**退役**（删除记录＋重写锚定索引去掉条目）再拒绝，`.pending` 亦判孤儿，**删除篡改证据不修复**，进程记录永不退役；`RecoveryLeaseProcess.alive` 修真实缺陷（本 JVM 已退役记录的关闭通道会让孤儿永不退役）；配置层用 `ObjectProvider` **惰性**通道。验证：一级 **133 类 / 2843 项 / 0 失败 / 1 既有无关 skip**（新增 7 项）；**现场**（全新实例 19475）`boot1: records=[<uuid>.json] index=[<uuid>]` → `child1`（独立 JVM）判孤儿并 `retired 1 orphan lease record(s)`、拒绝启动（退出码 1）→ `afterRetirement: records=[] index=[]` → `child2`（独立 JVM）`STARTED`（退出码 0）；恢复链 25 类批次（实例 19476）**25/696/0/0**。**语义决定**：初始化窗口自身的根不发布（否则与守卫排序成启动环），启动期访问由第 8–9 行进程记录/围栏信号覆盖；同 JVM 围栏不可复位 ⇒ "下一个进程能启动"只能跨进程取证（已用两个子 JVM 取证）。未做：Python 侧 v2 供给与封闭权威身份（B9）、生产级 provision、实验舱重跑、§5.6 事件细化；开关仍全默认 false；K1 仍未通过 |
| R09 C2 **B1-3 S11：v2 初始权威形状与显式供给**（**同一会话实施＋自审＋运行**；设计第 6–8 行） | [S11 记录](history/R09_C2_B1_3_S11_INITIAL_AUTHORITY_V2_2026-09-29.md)：新增 `RecoveryInitialAuthority`（**版本感知**形状校验：v1＝`formatVersion/installationId/restoreRoots/initialCommandSha256`；v2＝v1＋`authorityKind/closedAuthority/leaseRoot`；两版字段集**精确匹配**、P5 路径须绝对非空互异、未实现版本⇒`CONTROL_FORMAT_UNSUPPORTED`；kind 仍归 purpose）并让 `RecoveryControlRecords.identity` 委托之 ⇒ **S10 现场实测的"准入 v1 字段集 vs 跨进程 P5 v2 字段集互斥"消解**；新增测试/离线作用域的显式供给方 `RecoveryInitialAuthorityProvision`（就地提升 v1→v2＋建 P5 树，返回 identity/marker/anchor）。验证：一级 **132 类 / 2836 项 / 0 失败 / 1 既有无关 skip**（新增 6 项：v1 照旧接受、v2 被准入接受、外来 kind⇒`CONTROL_AUTHORITY_PURPOSE_FORBIDDEN`、缺 P5 路径/未知字段⇒`CONTROL_FIELDS`、版本 3⇒`CONTROL_FORMAT_UNSUPPORTED`、P5 供给拒 v1 受 v2）；**现场**（全新实例 19473）**生产形态整套 opt-in 首次启动成功**（准入＋lease＋cross-process＋效果门、v2 身份、真实 `fenced.lock`）：未围栏写/提交成功，`authority.fenceLocal()` 后写/关系读/提交全被拒且 `counterAfterFence` 不变 ⇒ **S10 的"跨进程效果门生产形态落库"缺口闭合**；恢复链 25 类批次（实例 19474）**25/696/0/0**。**协议语义**：写者协议钉住身份摘要 ⇒ 必须先提升身份再钉协议（否则 `CONTROL_WRITER_PROTOCOL_MISMATCH`）；Python 侧供给仍为 v1（B9）。未做：第 10/12 行逐根发布/退役、Python v2 供给与 `closedAuthority`、生产级 provision 工具、实验舱重跑；开关仍全默认 false；K1 仍未通过 |
| R09 C2 **B1-3 S10：效果门接入真实 JDBC 入口**（**同一会话实施＋自审＋运行**；设计第 11 行） | [S10 记录](history/R09_C2_B1_3_S10_EFFECT_GATE_WIRING_2026-09-29.md)：`RecoveryRequestAdmittedDataSource` 新增带门构造器并在 `admit(...)` 里 `handler.useEffectGate(effects)` ⇒ 本机层（`fence()` 后本地拒绝）与跨进程层（authority 探针＋孤儿扫描）都在**委托前**生效；`RecoveryAdmissionConfiguration` 用 `ObjectProvider<RecoveryEffectGate>` 取门即挂（缺省 `disabled()`，未 opt-in 行为不变）。验证：一级 **132 类 / 2833 项 / 0 失败 / 1 既有无关 skip**（新增 5 项：逐效果委托前检查｜被拒效果到不了驱动｜**只读白名单严格性**（常量读不查、**关系读照查**、未识别 SQL `CONTROL_EFFECT_UNCLASSIFIED`、预备语句执行时归类）｜未挂门不查｜本机层先行不调 authority；另 3 项钉住身份冲突）；**现场**（全新实例 19471）真实应用：未围栏 `write ok, commit ok, counter=1`；authority 拒绝后 `write refused, relation read refused, commit refused, constant read ok` 且 `counterAfterFence=1`（被拒效果没落到驱动）；恢复链 25 类批次（实例 19472）**25/696/0/0**。**发现**：①**准入层（v1 字段集）与跨进程 P5 层（v2 字段集）身份字段集互斥**（两侧均 `CONTROL_FIELDS`）⇒ 今天无法同开，"跨进程效果门生产形态落库"仍未验；②只读白名单比"任意 SELECT"窄。未做：第 10/12 行逐根发布/退役、v2 权威供给（第 6/7 行、B9）、实验舱重跑；开关仍全默认 false；K1 仍未通过 |
| R09 C2 **B1-3 S9：阶段 0 安全前置第一批——P5 根身份/围栏通道/进程记录接线**（**同一会话实施＋自审＋运行**；设计第 8–9 行应用侧接线） | [S9 记录](history/R09_C2_B1_3_S9_P5_ROOT_WIRING_2026-09-29.md)：新增 `RecoveryLeaseConfiguration`（仅 `outbound.recovery.lease.enabled=true` 装配；绑定 P5 根/封闭权威/P3 身份摘要/provision marker，缺项⇒`RECOVERY_LEASE_ROOT_UNBOUND`）与 `RecoveryLeaseLifecycle`（锚点校验⇒`CONTROL_ANCHOR_REQUIRED`；`validateStartup()`=围栏通道＋孤儿扫描＋进程记录与生命周期锁；fail-closed 启动；Spring 关停退役）；新增 `RecoveryFenceAuthority.retire()`（干净关停 F3，幂等，不声明围栏）。验证：一级 **130 类 / 2825 项 / 0 失败 / 1 既有无关 skip**（新增 7 项：默认不取根、无锚点／摘要不符／`fenced.lock` 缺失各自拒绝且不修复、进程记录＋锁持有＋退役复用、两进程共用同根各留记录、发布后删租约⇒下次启动 `RECOVERY_LEASE_OUTSTANDING`）；**现场**（全新实例 19469）真实应用取 P5 根：`gate enabled, checkEffect=ok`、树 `[fenced.lock, lease-root.json, leases/index.json, processes/<uuid>.json]`、经真实 authority 发布租约→退役→下次启动因孤儿租约被拒；恢复链 25 类批次（实例 19470）**25/696/0/0**。**发现**：①产品缺陷——lease 配置未启用准入 settings ⇒ 生产启用必缺 bean（现场发现，单测因自带该 bean 未暴露）；②P5 根是共享的（更正"第二进程应被拒"的错误预设）；③v2 初始权威供给仍缺（现场由测试侧补形状）。未做：第 10 行逐根发布/探针、**第 11 行效果门接入 JDBC 入口**、第 12 行逐操作退役、v2 权威供给、实验舱重跑；开关仍全默认 false；K1 仍未通过 |
| R09 C2 **B1-3 S8：入口 IT 重基到请求根语义**（**同一会话实施＋自审＋运行**；看板长期单列的"入口 IT 重基"；**无产品代码改动**） | [S8 记录](history/R09_C2_B1_3_S8_ENTRY_REBASE_2026-09-29.md)：`RecoveryAdmissionEntryChecks` 三处检查改为**在入口接纳的操作根内**运行（`registry.admit`＋`bindDispatch`），主源断言改 `RecoveryRequestAdmittedDataSource`，新增**无根必拒**（`RECOVERY_OPERATION_REQUIRED`）证据，关闭意图检查改为"**保留根继续服务＝allowed**／**新根＝`CONTROL_CLOSING_INTENT`**"（旧检查钉的是旧连接级模型）；`RecoveryEntryLostCommitConfiguration` 把丢回执故障下沉到请求根数据源之下并补齐启动覆盖（注册表／初始化窗口／守卫，static 工厂避免守卫 `dependsOn` 自环）；新增 `RecoveryAdmissionEntryRebaseIT` 作为可在隔离实例运行的第三处调用者；`C1MainProfile`／`E2aCrossSourceHttpIT` 补 `cleanup-cron=-`（**不补则两实验舱连启动都被拒**）。验证：全量 **129 类 / 2818 项 / 0 失败 / 1 既有无关 skip**；**现场**（全新实例 19468）live／unknown／lost 三上下文同轮全绿——`rootRequired=RECOVERY_OPERATION_REQUIRED`、`roundTrip committed=1 rolledBack=1`、`closeRace closeWaitedMillis=2500 innodbTrx=0`、`admittedRootStillServes=allowed`＋`newRootWithPendingIntent=CONTROL_CLOSING_INTENT`、提交期连接丢失与丢回执均 `CONTROL_WRITERS_ACTIVE`（丢回执 `committedCounter=2`）、夹具恢复根＝实例真实 datadir。产品未改故沿用 S7 轮 25 类批次证据。未做：阶段 0 安全前置（第 8–12 行）、实验舱重跑、效果门接线；K1 仍未通过 |
| R09 C2 **B1-3 S7：拒绝码 HTTP 映射与无状态拒绝写出口**（**同一会话实施＋自审＋运行**；设计 §4.4／第 20 行） | [S7 记录](history/R09_C2_B1_3_S7_REFUSAL_MAPPING_2026-09-29.md)：新增 `ApiResponses.writeWithoutState`（拒绝 envelope 同形状、不读 `ServerState`）＋`RecoveryRefusalAdvice`（`@Order(HIGHEST_PRECEDENCE)`：服务中 `RecoveryControlException` ⇒ 503＋原码）；接纳拒绝由请求根过滤器自己写 503（不再冒到容器）；`ApiErrors` 超限 ⇒ 无状态 413、新增 `MultipartException` ⇒ 尺寸 413／其他 400；`body(...)` 的状态读取 catch 放宽到 `RuntimeException`（报告状态不得二次失败）。验证：定向 **24/0**、全量 **129 类 / 2818 项 / 0 失败 / 1 既有无关 skip**；**现场**（全新实例 19466）围栏 ⇒ 503 `RECOVERY_LEASE_FENCED`、超限 ⇒ 413、畸形 multipart ⇒ 400 `INVALID_REQUEST`、权威过期后的接纳 ⇒ 503 `CONTROL_INSTANCE_STALE` 且零根登记，四者 envelope 均 `unknown/UNAVAILABLE`（证明未读库）；恢复链回归 **25 类 / 696 项 / 0 失败 / 0 跳过**（全新实例 19467）。**口径更正**：撤回 S4 记录中"超限 ⇒ 500"的定性——真因是探针代理未解包 `InvocationTargetException`（测试仪表缺陷被记成产品缺陷）。新增已知限制：接纳拒绝的 `requestId` 可能为空。未做：入口 IT 重基、阶段 0 安全前置；K1 仍未通过 |
| R09 C2 **B1-3 S6：控制锁按根可嵌套与有界等待**（**同一会话实施＋自审＋运行**；设计第 13 行） | [S6 记录](history/R09_C2_B1_3_S6_CONTROL_LOCK_2026-09-29.md)：`RecoveryControlLock` 重写为**按根信号量＋线程级根集合（根→层号 P3=1/P2=2/P5=3）**；同根重入按误用拒绝（立即式 `CONTROL_COORDINATOR_BUSY` 不变，等待式 `CONTROL_COORDINATOR_REENTRANT`）、层号不严格递增 ⇒ `CONTROL_LOCK_ORDER_VIOLATION`；新增 `lockedWaiting`（预算 1..10000ms ⇒ `CONTROL_LOCK_TIMEOUT_RANGE`；JVM 信号量与跨进程文件锁同一预算内重试，耗尽仍 `CONTROL_COORDINATOR_BUSY`）；`InitialNormalStore` 落到 P2 层；`registry.admit` 改用 2000ms 有界等待 ⇒ 并发请求排队而非被拒。验证：定向 **44/0**、全量 **128 类 / 2814 项 / 0 失败 / 1 既有无关 skip**（recovery 33 类 2176 项 0 失败）；**现场并发**（实例 19464）**4 路并发全部 200**、`concurrentRoots=[4,3,2,1]`（真重叠）、并发窗口内保存 `liveRoots=4,4,3,3,2,2,1,1`、轨迹接纳段 `liveRoots=1,2,3,4`、`liveRootsAfter=0`；恢复链回归 **25 类 / 696 项 / 0 失败 / 0 跳过**（全新实例 19465）。**唯一语义变更**：等待式同根重入码 BUSY→REENTRANT（设计 §2.1 明文）。未做：阶段 0 安全前置（第 8–12 行）、拒绝码 HTTP 映射、入口 IT 重基；K1 仍未通过 |
| R09 C2 **B1-3 S5：启动守卫与初始化窗口**（**同一会话实施＋自审＋运行**；设计第 19/22 行） | [S5 记录](history/R09_C2_B1_3_S5_STARTUP_WINDOW_2026-09-29.md)：解决 S3 实测的"开开关即起不来"——新增 `RecoveryStartupGuard`（`BeanFactoryPostProcessor`：清理未禁用⇒启动失败 `CONTROL_SESSION_CLEANUP_UNCOVERED`；把应用 Bean 的 `dependsOn` 显式并入初始化窗口）与 `RecoveryInitializationWindow`（协调锁内建 `INITIALIZATION` 根、绑定刷新线程覆盖 bean 构造与启动 runner、`ApplicationReadyEvent` 时退役）；未改任何启动消费者。验证：全量单测 **127 类 / 2806 项 / 0 失败 / 1 既有无关 skip**（recovery 32 类 2168 项 0 失败；新增启动门 3 项＋无根能力白名单 2 项）；**现场**（全新实例 19462，`migrate executed=49`，**已删除探针替身**）带 admission **真实启动成功**且 S3/S4 判据全部复现（`liveRootsDuringRequests=[1,1,1,1]`、解析/暂存/保存全在根内、`liveRootsAfter=0`），同轮另一用例：清理保持默认 ⇒ 启动失败 `CONTROL_SESSION_CLEANUP_UNCOVERED` 且端口未占用；恢复链回归 **25 类 / 696 项 / 0 失败 / 0 跳过**（全新实例 19463）。未做：阶段 0 安全前置（第 8–12 行，需 leaseRoot/锚点配置）、锁并发（第 13 行）、拒绝码 HTTP 映射、入口 IT 重基；开关仍默认 false；K1 仍未通过 |
| R09 C2 **B1-3 S4：multipart 与错误分派判据**（**同一会话实施＋自审＋运行**；**无需产品改动**） | [S4 记录](history/R09_C2_B1_3_S4_MULTIPART_2026-09-29.md)：结论先行——multipart 解析/暂存在 `DispatcherServlet` 内，S3 的最外层请求根已覆盖，`git diff c2bc073 -- server/src/main` 为空；本轮用真实容器把该结论观测成证据：`POST /__s3/multipart`（4096B）⇒200＋Session 行/属性；解析期观测 `parses=[BOUND(staged=1), BOUND(staged=0)]`；请求后暂存目录 `0`（容器清理）；错误分派 `ERROR` 趟同根复用不新建根；`liveRootsDuringRequests=[1,1,1,1]`（根数＝请求数）、14 次保存全 `BOUND`、`liveRootsAfter=0`、清理已禁用生效。验证：全量单测 **125 类 / 2801 项 / 0 失败 / 1 既有无关 skip**（recovery 30 类 2163 项 0 失败）、恢复链回归 **25 类 / 696 项 / 0 失败 / 0 跳过**（全新实例 19461）。发现（**已于 S7 轮撤回**）：multipart 超限曾观测 **500**，S7 查明真因是 S4 轮探针代理未解包 `InvocationTargetException`；修正后超限即 **413**。**B1-3 S1–S32 已完成（S32 见上行）**；开关仍全默认 false；K1 仍未通过 |
| R09 C2 **B1-3 S3：请求根过滤器与真实 Session 覆盖**（**同一会话实施＋自审＋运行**） | [S3 记录](history/R09_C2_B1_3_S3_REQUEST_ROOT_SESSION_2026-09-29.md)、[开工说明 §1](history/R09_C2_B1_3_PLAN_2026-09-29.md)：`RecoveryRequestAdmissionFilter`（一请求一根、每次分派绑定、ASYNC/ERROR/嵌套重分派复用同根、根标记入请求属性）＋`RecoveryRequestRootListener`（销毁时清该根全部绑定并退役）＋`RecoveryOperationRegistry.admit`（P3 协调锁内建根）；注册顺序 `HIGHEST_PRECEDENCE`（先于会话过滤器 `-2147483598`）、分派范围 `{REQUEST,ASYNC,ERROR}`，仅 `admission.enabled=true` 装配。验证：全量单测 **125 类 / 2800 项 / 0 失败 / 1 既有无关 skip**（recovery 30 类 2162 项 0 失败，新增 S3 单测 10 项）；**现场判据**（真容器＋Spring Session JDBC＋隔离 MySQL 19456，`migrate executed=49`）：session 200＋Session 行/属性、error 500＋行/属性（错误分派同根覆盖不新建根）、async 200 且轨迹含 ASYNC、`liveRootsDuringRequests=[1,1,1]`（根数＝请求数）、10 次 Session 保存全部 `BOUND`、`liveRootsAfter=0`、清理已禁用生效；恢复链回归 **25 类 / 696 项 / 0 失败 / 0 跳过**（全新实例 19457）。发现（未修）：开开关时 `JobRecoveryFence` 启动期无根即 fail closed（需设计第 19/22 行能力）、现有锁非嵌套无等待⇒并发接纳受限（第 13 行）、拒绝码 HTTP 映射未实现；**S4 已另行验收（见上行）；B1-3 四切片全部完成**；开关仍默认 false；K1 仍未通过 |
| R09 C2 **B1-3 S2：请求根数据源**（**同一会话实施＋自审＋运行**） | [S2 记录](history/R09_C2_B1_3_S2_REQUEST_ROOT_2026-09-29.md)、[开工说明 §5](history/R09_C2_B1_3_PLAN_2026-09-29.md)：`RecoveryRequestAdmittedDataSource` 成为应用**唯一 `@Primary`** 数据源——每次借用必须解析到本线程的有效根，**永不回退**旧连接层；六码各配按码单测（无根 `RECOVERY_OPERATION_REQUIRED`、有根标记未绑定 `RECOVERY_OPERATION_CONTEXT_LOST`、令牌失效 `CONTROL_OPERATION_RELEASED`、越集子操作 `CONTROL_SUBOPERATION_FORBIDDEN`、根 FENCED `RECOVERY_LEASE_FENCED`，以及有效绑定借用并登记 `CONNECTION`）。旧 `RecoveryAdmittedDataSource` 降为非 `@Primary`、主代码除定义处**零引用**（静态白名单测试强制；测试侧仅冻结的丢失提交装置保留 `@Primary`，与主入口上下文互斥）。新增根骨架：`RecoveryOperationRegistry`（协调锁内读权威＋身份比对＋共享写者租约）、`RecoveryOperationLease`、`RecoverySubLease`、`RecoveryOperationToken/Kind/Phase/SubOperation`。验证：定向单测 **47/0**（旧类 17/17 仍绿）、全量单测（**不带 `--tests` 白名单**）**123 类 / 2790 项 / 0 失败 / 1 既有无关 skip**（recovery 包 2152/0）、恢复链 IT **25 类 / 696 项 / 0 失败 / 0 跳过**（全新实例 19453，NOT-COVERED 空）；自审另补一项失败借用路径单测（仅测试代码，失败首版保留）。**S3/S4 未开工**；开关仍默认 false；K1 仍未通过；入口 IT 的 live/unknown 检查待 S3 重基 |
| R09 C2 **B1-3 S1：purpose 守卫接线**（**同一会话实施＋自审**） | [开工说明 §4/§5](history/R09_C2_B1_3_PLAN_2026-09-29.md)：`RecoveryControlPurpose`＋`RecoveryControlHistory.load(...,purpose)` 成为**唯一入口**、4 个直接调用点显式穿参、kind 校验集中到同一 purpose 规则并落到 fence/租约根基座入口（**错误码不变**）。验证：干净重建单测 `run25-s1b-unit` **14 suite / 2044 项 / 0 失败**（含 6 项 purpose 测试）；恢复链 IT `run e`（全新实例 19450）**25 类 / 696 项 / 0 失败 / 0 跳过**。发现：**control 身份无 `authorityKind`**（设计 §9 第 2 行的 5 个类并不直接调用 load）、`run.ps1` 的 `--tests` 白名单会**静默漏跑**新测试类（已补）、"全量单测 2038/0"实为 **recovery 范围**单测。**S3 已另行验收（见上行）；S4 未开工**；开关仍默认 false；K1 仍未通过 |
| R09 C2 **恢复链 IT 分批可解读跑批**（**同一会话实施＋自审**） | [跑批基础设施与结果](history/R09_C2_IT_BATCH_2026-09-29.md)：`tools/r09-recovery-it-batch.ps1`（预检失败关闭/两种环境模式/逐类 attempt/聚合），实跑 **24 类 / 683 项 / 0 失败**；TIME_WAIT 峰值 1664 ⇒ 逐类＋节流必要；**IT-BATCH-PIN 已修**（守卫契约化：回环+端口区间+拒 3306+schema=账号+证据目录契约），并在全新专用实例上复跑 **25 类 / 696 项 / 0 失败**（含契约化后的 G0K1RealJdbcBoundaryIT；恢复链 26 类仅 C1 HTTP 类不在批内）。未含 C1 HTTP、19438 边界类与 21 类发布前清单 |
| R09 C2 **B1-2 认证锚点补做**（**同一会话实施＋自审**） | [P5 认证锚点补充规格](history/R09_C2_B1_2_ANCHOR_SPEC_2026-09-29.md)：H3（租约 owner 引用改写为活属主）**已由显式供给的锚点秘密闭合**——记录带 HMAC 标签并在读取时校验、跨进程缺锚点即启动失败（`CONTROL_ANCHOR_REQUIRED`）；用例翻转为 3/3 拒绝。**H3′（删除记录）亦已闭合**（锚定操作索引双向核对；run16 27/0、run19 668/0）。验证：run12 24/0、run13 单测 2038/0、run15 并集 IT 665/0/0。默认关闭、未接线；K1 仍未通过 |
| R09 C2 **B1-2 对抗性复核**（DSH 接手 Codex 中断会话；**同一会话实施＋自审，非独立第三方**） | [B1-2 对抗性复核记录](history/R09_C2_B1_2_ADVERSARIAL_2026-09-29.md)：三处缺口证实——H1 本机缓存未绑定文件身份（**已修**，3/3 拒绝）、H2 按文件名跳过审计标记致租约改名隐形（**已修**，6/6 拒绝）、H3 租约 owner 引用可改写为活属主（**未解决**，登记 `DEBT-R09-C2-LEASE-ANCHOR`，用例显式标注 tracked 非接受）。最终定向单测 **2038/0**、并集全量 IT **662/0/0**；失败轮 run2/3/4/8/9 保留。默认关闭、未接线、未部署；**K1 仍未通过、未进入 B1-3** |
| R09 C2 **B1-1 效果门第一切片**（独立有限授权） | [规则/实现/用例与驱动对照、默认关闭证明、隔离身份及逐轮证据](history/R09_C2_B1_1_EFFECT_GATE_2026-09-29.md)。默认关闭、未接线、未部署、未开生产开关；本切片验收不等于完整 K1/B1 通过；停止于本切片 |
| **DSH 收口与写权交接**（2026-09-29，纯文档） | 停止推进并收敛：看板当前区改为**交接态**（旧区归档为 [交接前看板原文](history/ORCHESTRATION_STATE_before_R09_C2_HANDOFF_2026-09-29.md)）；**写权移交 Codex（2026-09-29）**；验收记录第 9 行与结论表冲突已就地更正；`G0K1WriterLeaseIdentityRealIT` 未入库路径硬断言已改并可复现（重跑 **exit 0**，含 clean-sim 分支 `previous_round_evidence_present=false`，证据 `.local/r09-c2-handoff-20260929-a/`）。**未改 `src/main`、未实现效果门、未开生产开关、未接线、未部署**；K1 仍未通过；**单一下一步＝B1-1 待用户明确授权** |
| R09 C2 G0-K1-3 遗留卫生修正（**B1-1 未获授权，保持停止**） | 纯文档/测试卫生：①[验收记录](history/R09_C2_G0_ACCEPTANCE_2026-09-28.md) 中“真实 FileLock 轮”段落的**旧“矛盾阻断”表述已就地更正**并指向[更正与闭合轮](history/R09_C2_G0_K1_3_CORRECTION_2026-09-29.md)；②`G0K1WriterLeaseIdentityRealIT.v4_…` 对**未入库** `.local` 路径的硬断言改为**只记录存在性**（干净检出可复现）。验证：`runA-clean-sim`（父目录不含上一轮证据）exit 0 / IT 5 项 0 失败 / `previous_round_evidence_present=false`、`runB-normal` exit 0 / 5 项 0 失败 / `…=true`、`runC-test` 定向单测 **60 项 0 失败**。**未改 `src/main`、未开生产开关**；K1 仍未通过、B1 仍待授权 |
| R09 C2 G0-K1-3 锁文件删除/替换**走产品路径复验与结论更正**（**独立有限授权**） | [更正与闭合轮](history/R09_C2_G0_K1_3_CORRECTION_2026-09-29.md)：真实子 JVM 走产品路径（`RecoveryControlLock.locked` → `RecoveryWriterLease.acquire` → `RecoveryWriterProtocol.validate` 原生身份校验），`integrationTest` **5 项 0 失败**。**V1a 同名替换 ⇒ `CONTROL_WRITER_LOCK_REPLACED`（3/3）**、**V1b 缺失 ⇒ `CONTROL_FILE_MISSING`（3/3）**、不自动重建、无第二持锁者 ⇒ `writers.lock` 为**已缓解/已覆盖**，上一轮“矛盾 A/B（阻断）”定性撤回；`fenced.lock` 转 **B1 承重需求**；保留**可用性威胁**。**K1 仍未通过；B1 待授权** |
| R09 C2 G0-K1-3 真实跨进程 FileLock 验证（独立有限授权；**S8/S9 定性已由上一行更正**） | [真实 FileLock 验证](history/R09_C2_G0_K1_3_REAL_FILELOCK_REVERIFY_2026-09-29.md)：真实独立 JVM 子进程（Windows 11/NTFS、Java 21.0.11；stdout/stderr 重定向到文件、无管道），10 场景×3 重复、`integrationTest` **0 失败**（47.96s）。**通过**：F1′（跨进程共享共存＋排他必失败）、F3（`release()`/正常退出可再获取）、F5（`destroyForcibly`/`Runtime.halt(70)` 后 OS 释放且残留文件≠持锁）、第三方无持锁者查询、同 JVM 重叠抛 `OverlappingFileLockException` |
| R09 C2 G0-K1-1 真实 JDBC 驱动验证（**独立有限授权**） | [真实驱动验证](history/R09_C2_G0_K1_REAL_JDBC_REVERIFY_2026-09-29.md)：隔离 MySQL 8.0.46＋Connector/J（端口 19438、独立 datadir/账号/schema；系统 3306 未使用）。`test` 定向回归 **60 项 0 失败**、`integrationTest` **13 项 0 失败 0 跳过**；**`getAutoCommit()` 非提交证据**；**DDL 隐式提交**（⇒ close 期 rollback 假设列入 B1 待实施需求）。**K1 仍未通过** |
| R09 C2-G0/K1 授权包（**B1 待授权**，纯文档） | [K1授权包](history/R09_C2_G0_K1_AUTHORIZATION_PACKET_2026-09-28.md)：封存 `K1 仍未通过` 的边界、五类验证分类、证据清单、未验边界、B1 范围与排除（B9 分离）、技术性前置条件与影响/风险边界。**该轮为授权准备，不代表 B1 获批或运行验收** |
| R09 C2-G0/K1 模型最终复验（历史） | [K1模型最终复验](history/R09_C2_G0_K1_MODEL_FINAL_REVERIFY_2026-09-28.md)：围栏矩阵按观察时状态；时序三条轨迹严格断言；JDBC 规则行↔测试方法唯一映射双向校验（20/17/唯一）；`run2` 60 项 0 失败 |
| R09 C2-G0/K1 模型更正轮（历史） | [K1模型更正](history/R09_C2_G0_K1_MODEL_CORRECTION_2026-09-28.md)：围栏矩阵改状态迁移、时序加调度点、JDBC 补信息流；登记第 2 次失败 XML 缺失（未补造）、证据改逐轮独立目录 |
| R09 C2-G0/K1 测试侧模型复验（历史，已被更正轮部分更正） | [K1测试侧模型复验](history/R09_C2_G0_K1_MODEL_REVERIFY_2026-09-28.md)：四项反例与 W 反例有效；文首有更正块 |
| R09 C2-G0 v5 定向收敛（历史，已被 K1 模型轮接续） | [设计定稿v5](history/R09_C2_G0_DESIGN_2026-09-28.md)、[v5定向复验](history/R09_C2_G0_V5_REVERIFY_2026-09-28.md)、[验收与阻断记录v5](history/R09_C2_G0_ACCEPTANCE_2026-09-28.md)：冻结 JDBC 效果入口、`fenced.lock` 原子观察、F3 用 `FileLock.release()`、六行失败矩阵、逐记录事件语义与窗口 W |
| R09 C2-G0 设计定稿 v4（历史，已被 v5 取代） | v4 原文见 Git `d656ab9`；其“章节引用与跨文档一致性检查全部通过”因原始输出为 `unresolved=6.6` 已被撤回 |
| R09 C2-G0 设计定稿 v3（历史，已被 v4 取代） | v3 原文见 Git `a495ef5`；两处结论被 v4 撤回重写 |
| R09 C2-G0 设计定稿 v2（历史，已被 v3 取代） | v2 原文见 Git `da4d56a`；其两处证明被推翻 |
| R09 应用入口接线第一批（DataSource 与 JdbcTemplate/事务） | [验收](history/R09_APPLICATION_ENTRY_ACCEPTANCE_2026-09-26.md)：`RecoveryJdbcObjectHandler.invoke` 未同步判为**缺口**并修复（子对象调用并入同一监视器；去掉共享监视器后 17 项中 2 项失败、按字节还原）；新增 opt-in `RecoveryAdmissionConfiguration`（admitted `@Primary` DataSource 供 JdbcTemplate/事务管理器，原始池只以 `@FlywayDataSource` 给迁移，默认关闭、权威缺失启动即失败）。隔离副本全量 714 tests=713 pass/1 skip、bootJar `faae1cbc…`；独立非空 MySQL attempt105 IT 1/1 无 skip：关闭竞争（close 等待 2513ms）、关闭意图拒新接纳、提交期连接 KILL 的未知提交、丢失提交回执均保持 fence，退出后连接/事务 0/0、耐久原意图完成、认证 shutdown。未接 HTTP/Session/Worker/文件入口与 NORMAL |
| R09 运行时只读门禁（在制） | [验收](history/R09_NORMAL_ADMISSION_ACCEPTANCE_2026-09-26.md#追加运行时只读门禁本批新增真实验证未完成)：`RecoveryAdmissionEvidence`/`RecoveryAdmissionQueries`/`RecoveryAdmissionVerifier` 逐项比对库外快照、耐久意图、库内登记与投影、`server_state`，任一侧缺失即 `admitted=false`，不写库/不登记/不初始化；V40 增列 `authority_revision`。定向 8 项、全量 708 tests=707 pass/1 skip、0failure。**未完成**：加列后的真实 MySQL 复验未绿（attempt97 两次分别为浏览器登录超时与导出 `RETRY_SCHEDULED`），绿色基线仍为 attempt90m |
| R09 初次 NORMAL 登记 | [验收](history/R09_NORMAL_ADMISSION_ACCEPTANCE_2026-09-26.md)：V40 `recovery_normal_admission` + 不可改删触发器（迁移不插行）；`RecoveryNormalIntent`/`RecoveryNormalRegistration`/`RecoveryNormalRows`，唯一可置回 NORMAL 的组件，要求 RELEASE_READY 终态 + 双侧一致 + 外部写者租约，幂等且 `ordinaryWritesAllowed=false`。定向 75/75、全量 700 tests=699 pass/1 skip、0failure；**隔离 attempt90 通过**（V34→V40、IT 1/1 无 skip、连接/事务排空 0、认证 shutdown；四类拒绝无半条记录且保持 RECOVERY_PENDING）。未接 API/Session/Worker/文件入口 |
| R09 连接适配器并发 seam/可信反证 | [验收](history/R09_ADMISSION_PROBE_ACCEPTANCE_2026-09-26.md)：新增包内 `RecoveryAdmissionProbe`（默认 `NOOP`）与 `admit(probe)`，probe 点位于 `synchronized invoke` 监视器内；新增 `probeForcedOverlapKeepsAConcurrentCloseOutOfTheInFlightDelegateCall`（14→15）。**反证成立**：仅去掉 `invoke` 的 `synchronized` 后 `gradle exit=1`、failures=1 且失败用例即该断言，备份按字节还原；全量 `test bootJar --rerun-tasks` 691 tests=690 pass/1 既有符号链接条件 skip、0failure/0error。未跑 MySQL/HTTP/APK |
| R09 连接适配器并发补强 | [验收](history/R09_ADAPTER_CONCURRENCY_ACCEPTANCE_2026-09-26.md)：补 Statement 代理路径的并发执行/关闭与 08006 反例，`RecoveryAdmittedDataSourceTest` 12→14；`test bootJar --rerun-tasks` 全量 690 tests=689 pass/1 既有符号链接条件 skip、0failure/0error；去同步反证当时未成立（监视器无法外部抢占），只声明当前实现下断言确定性通过；主代码零改动、未跑 MySQL/HTTP/APK |
| R09 连接生命周期 adapter（attempt89） | [本机资料位置说明](LOCAL_ARCHIVE_LOCATIONS.md)（原清单outputs/r06-product-20260925-01a0d8fa/admission-attempt89/manifest.sha256在主树未找到；需按归档说明定向核查，以下仅保留历史结论）：服务端 `RecoveryAdmittedDataSource`/JDBC 代理/租约毒化修复；全量 688 tests=687 pass/1 既有符号链接条件 skip，bootJar 成功；独立非空 MySQL V34→V39、真实 HTTP、JVM 退出后连接/INNODB_TRX=0/0；未注册 Spring/NORMAL，全入口和生产写权限仍未开放 |

| R07-1 交接命令层（REQUEST/APPROVE/PREPARE/ACTIVATE + R07 专用激活路径） | [状态归档](history/R07_1_COMMAND_LAYER_STATUS_2026-09-27.md)：**核心逻辑**已实现，**仅完成编译 + 全量单测（718 项 0 失败 / 1 既有 skip）**；**端到端未验**——14 条断言（8 反例 + 3 幂等 + 3 并发 + 原子性两模式）**全部未跑**，outbox 接线未落代码。控制器 + 契约 0.35.0（paths 130）属**辅助逻辑**，已达"编译 + 全量单测"粒度。提交 `ec58801`、`49e3b0f`。**不得视为 R07-1 完成** |
| R07 FORMAL 来源夹具（**验证基础设施**类目，未启动） | [阻塞记录](history/R07_FORMAL_FIXTURE_BLOCKER_2026-09-27.md)：累计 **5 轮未成**（attempt 138/139 编译与运行期、140/141/142 三轮推进、143/144 方向 2 自建设备资格）。根因：复用 TEMP 夹具的设备侧采集序号与审计批次（`CAPTURE_CONTENT_CONFLICT`、`冻结审计批次错误`）；方向 2（自建 device/app_user/session/assignment）亦未成立（401→404）。**不是"多修几处"能解决**，需**独立时间盒（长于普通子项）+ 独立验收标准**；现在不启动，待 R07 其他部分或 R06 完成后再专门排期 |

| 范围 | 证据及适用边界 |
| --- | --- |
| R09 C2 B1-1 对抗性复核 | [复核记录](history/R09_C2_B1_1_ADVERSARIAL_2026-09-29.md)：三处真库漏口与门内修正、九项清单、三次重复、完整接口枚举；默认关闭，K1 仍未通过，停止于 B1-1 |
| A3 非e2a回归接续 | [496–500结果及停报](history/A3_NON_E2A_REGRESSION_2026-09-28.md)：真MySQL+MockMvc，4类绿/1类红；证据及根因分列，不代表全量通过 |
| R08 D 更正导出与四项边界 | [交付确认](history/R08_D_DELIVERY_ACCEPTANCE_2026-09-28.md)、[撤销验收](history/R08_D_WITHDRAW_DESIGN_AND_FIX_2026-09-28.md)、[抬头验收与假设扫描](history/R08_D_HEADER_DESIGN_AND_FIX_2026-09-28.md)、[四项历史](history/R08_D_BOUNDARIES_2026-09-28.md)；仅作证据入口，状态见看板 |
| R09 耐久关闭意图/接纳排空 | [验收](history/R09_CLOSING_INTENT_ACCEPTANCE_2026-09-26.md)：协议v2原意图永久保留、新写者拒绝；Windows/Linux各31无skip、Java675pass/1skip、Python114pass/11skip；attempt82实库事务/投影/HTTP。全应用接线与NORMAL仍未完 |
| R09 关闭阶段数据库投影 | [验收](history/R09_CLOSED_PROJECTION_ACCEPTANCE_2026-09-26.md)：V39非空迁移、原事实字节不变、四阶段及并发/回退/错库反例；670单测通过/1skip，attempt80实库+HTTP/浏览器。仍未接共享入口或NORMAL |
| R09 真实事务生命周期租约 | [验收](history/R09_LEASED_TRANSACTION_ACCEPTANCE_2026-09-26.md)：提交/回滚/UNKNOWN/内层事务反例、670单测通过/1skip，attempt78独立非空MySQL及原HTTP/浏览器回归，JVM退出后连接/事务0；API共享接入和DB投影仍未完 |
| R09 本机跨进程写者租约 | [验收](history/R09_WRITER_LEASE_ACCEPTANCE_2026-09-26.md)：Java41项、Python33专项、Windows/Linux各25真实跨进程；原锁身份/引用计数/旧资料只读；服务端665通过/1skip。事务及完整共享门禁继续 |
| R09 导出进程容器 | [验收](history/R09_PROCESS_CONTAINMENT_ACCEPTANCE_2026-09-26.md)：Windows真实Job父孙进程/握手9项、服务端659通过/1条件skip、report124通过；attempt74真实HTTP/浏览器；Linux独立用户cgroup2项通过，未部署项目服务；完整共享门禁继续 |
| R09 已追踪导出进程排空 | [验收](history/R09_PROCESS_DRAIN_ACCEPTANCE_2026-09-26.md)：真实父子进程退出、5定向；655单测通过/1条件skip、JAR；attempt73非空MySQL/真实HTTP+浏览器各1通过。孤儿/完整树与共享租约仍待接 |
| R09 Java库外原件消费者 | [验收](history/R09_JAVA_CONTROL_ACCEPTANCE_2026-09-26.md)：Java35定向、服务端653通过/1条件skip、Python103通过/11专用环境skip、Windows/Linux各16真实跨进程及独立原件消费；共享API/DB/文件排空继续开发 |
| R09 Job共享事务/代次屏障 | [验收](history/R09_JOB_FENCE_ACCEPTANCE_2026-09-26.md)：V38非空迁移原Job保留、实例/Job双代次、恢复锁竞争与HTTP；attempt72 1+23集成测试无skip，attempt71导出/浏览器通过；库外共享接入待续 |
| R09 库外控制本地模块 | [验收](history/R09_LOCAL_CONTROL_ACCEPTANCE_2026-09-26.md)：原命令/CAS/OS锁/进程崩溃恢复29/29；完整R09为103通过/11专用环境未验；普通写始终关闭，共享门禁与释放未接入 |
| R06 M2b-2b 受控关联闸门＋指针切换 | [闸门验收](history/R06_CONTROLLED_LINK_ACCEPTANCE_2026-09-26.md)、[切换验收](history/R06_CONTROLLED_LINK_SWITCH_ACCEPTANCE_2026-09-26.md)：`LINK` 放行＋锁内候选摘要逐字比对、0/多候选失败关闭、锁内目标重验；同一事务写映射 v+1（`CONTROLLED_LINK`）/scope canonical/slot 改指/被取代单 `LINKED`（V41 约束扩展）。attempt113 独立非空 V34→V41 实库 IT 1/1 无 skip；attempt114 按字节去守卫后同一 IT 真实失败并原字节还原。**关联后确认/成功链（M2b-4b）、排除台账、锁图并发未实现**，生产入口仍无 CommandOperation bean |
| Android LINK 映射安装 **L4**（真服） | 三件套 `2bf2507`：常驻 IT `E2aServeBindingForAndroidIT`（R06 链→铸 PDA Bearer→真 HTTP 自证→写握手→轮询 stop）+ `RealServerSourceBindingTest`（OkHttp 真打 19434→`installBindings`→本地行==服务端关联结果+幂等）+ 编排 `tools/e2a-android-link-l4.ps1`（同 attempt、双侧有界）。attempt **185/187** 双绿：`server=1/0/0/0`、`android=1/0/0/0` ⇒ 由 L3 升 **L4**。细节更正：`java.net.http` 不在 Android 测试 classpath → 改用 OkHttp 4.12；构造器补 `--rerun-tasks`（同过滤器复跑会被 Gradle 判 UP-TO-DATE 而静默空跑，attempt 186 实测）。未验：多设备/并发/断网重连/仪器化/真机现场 |
| R06 其他未提交来源事实 | [验收](history/R06_UNSUBMITTED_SOURCE_ACCEPTANCE_2026-09-25.md)：2026-09-26 attempt67非空MySQL/真实HTTP，原授权/库存/审计状态核验；其他来源不误占K，613单测通过/1条件跳过，64/65失败保留；其他TEMP计划和跨来源续作限制单列 |
| R06 草稿历史归档信任 | [验收](history/R06_DRAFT_ARCHIVE_TRUST_ACCEPTANCE_2026-09-25.md)：配置历史钥/已存原凭据/文件/逐事件一致；attempt63实际Android归档+换钥重启HTTP，62失败及修复保留；其他来源业务事实仍未完 |
| R06 非零候选独立消费 | [验收](history/R06_NONZERO_CANDIDATE_ACCEPTANCE_2026-09-25.md)：attempt61实库/HTTP/Python54通过，完整候选/上下文/游标独立重建；非零短装批准不授普通确认，关联/排除仍未完 |
| R06 Room27 产品停止/持久补传队列 | [验收](history/R06_DRAFT_PRODUCT_ACCEPTANCE_2026-09-25.md)：最终Android440通过/4HTTP跳过、APK/lint及attempt59产品写者→真实HTTP；坏资料拒绝、身份切换持久暂停、非空26→27原件保留；不授业务能力 |
| R06 Room26 原键/回执仓储恢复 | [验收](history/R06_DRAFT_TRANSFER_ACCEPTANCE_2026-09-25.md)：Android420通过/4HTTP跳过、APK/lint及attempt57真实HTTP；产品停止/补传接线由后续批次验收 |
| R06 V37 草稿原件接收 | [验收](history/R06_DRAFT_INTAKE_ACCEPTANCE_2026-09-25.md)：可信准备、原响应及attempt56；ORIGINALS_STORED不等于业务事实已评估 |
| R06 原件结构闭合 | [验收](history/R06_DRAFT_SNAPSHOT_ACCEPTANCE_2026-09-25.md)：原件根/数量/全设备截点，不能替代其他来源业务事实 |
| R06 Room25 原机签名 | [验收](history/R06_DRAFT_SIGNATURE_ACCEPTANCE_2026-09-25.md)：停止事务签名，旧无签名原件不补签；非真实PDA Keystore证明 |
| R06 Room24 本机停止 | [验收](history/R06_LOCAL_DRAFT_STOP_ACCEPTANCE_2026-09-25.md)：未冻结/零包/未决原件；attempt54只证明旧冻结链兼容 |
| 来源事实、确认记账、独立短装、Room20–23、R08 TEMP导出、R09混合归档 | [专项历史索引](history/README.md)：逐项读取原验收、失败和未验，不以原确认导出替代净结果、不以混合归档替代完整恢复 |
| R01–R05及更早版本 | [原进度完整归档](history/management-2026-09-25-before-refresh/docs/DEVELOPMENT_PROGRESS.md)：按章节定位；包括code20/Room17及现场、升级、长时等未验 |

<a id="2026-09-18开发阶段提交"></a>

## 历史阶段提交入口

原2026-09-18开发阶段提交及679d5e4记录见[原进度完整归档](history/management-2026-09-25-before-refresh/docs/DEVELOPMENT_PROGRESS.md)。保留此锚点供旧交付文档跳转；该提交不代表当前工作区版本。

## 证据维护

每次子项验收保存：输入树/精确SHA及未提交差异、实现与自审、当次实际测试、独立/组合关系、原始证据路径、失败和限制、下一依赖。当前断点只更新看板；本页在增加新的验收记录时增加入口。

2026-09-25管理整理只归档原文、核对测试报告并更新入口，没有新增业务验收。原文与SHA校验见[归档说明](history/management-2026-09-25-before-refresh/README.md)。

| R06 发布前必须解决项：全量服务端 IT **从未成功跑过**（未知状态） | attempt 168 首次全量跑批（R02 构造器，隔离 MySQL）：`classes=172 tests=767 failures=722 errors=0 skipped=45`；失败由 `One-shot attempt already used`(28) 与 `ApplicationContext failure threshold exceeded`（级联，占绝大多数）主导 → **不可解读为产品缺陷，也不能视为通过**。发布前必须改为分批覆盖并取得一次可解读的全量结果。 |
| `ExportRendererProcessTest` pid 握手 flake 修复（`9480a21`） | 根因＝fixture 用 `Files.writeString` 发布 `attempt/pid`（该 API 先建**空文件**再写），测试旧逻辑只判 `Files.exists` 就 `Long.parseLong(Files.readString(...))` → 负载下命中空窗，`NumberFormatException: For input string: ""`；非 stdout 时机、非并行共享资源（全仓无 JUnit 并行配置）。修法：原子发布（`.tmp` + `ATOMIC_MOVE`）＋等「非空可解析」内容。验证：隔离单类 3/0/0/0、12 路 CPU 负载单类 3/0/0/0（15.6s→26.3s）、全量 718/0/0/1 既有条件 skip；机制 A/B 反证 12998→0。**未复现原始失败本身**（概率性）。详见[欠债清单](history/R06_DEBT_LIST_2026-09-27.md)。 |

## Codex 接续验收入口（2026-09-30）

- R06 A1.1：[G1 重复、②设备专项与前置阻断记录](history/R06_A1_1_CODEX_2026-09-30.md)。只作本批证据入口，接续断点与未验项见看板。

## Codex A1.1 批准执行收口入口（2026-09-30）

- R06 A1.1：[G1 可重复性、②坏件、G4-2/G4-3 重复与⑤丢回执验收](history/R06_A1_1_CODEX_2026-09-30.md)。按 DSH 分层范围收口；包可见性诊断、全部失败轮和仍缺边界见原记录，下一断点只看看板。


## R06→R07 无人值守接续入口（2026-09-30）

- [A1.2 开局技术边界停报与完整剩余范围](history/R06_R07_PROGRESS_2026-09-30.md)：静态源码核对与断点记录，未取得 A1.2/A1.3 新验收，R07 未启动；不计业务完成。

## R07 跨设备续作最终提交（2026-10-05）

- [自主进度](history/AUTONOMOUS_PROGRESS_2026-10-05.md)：接收机确认后的跨设备续作服务端最终提交、账务、回放与回滚验收；`102cda9`，失败轮和生成身份/非物理设备边界均在记录中保留。

## R07 接收机 Android 续作消费（2026-10-05）

- [自主进度 B2 交付段](history/AUTONOMOUS_PROGRESS_2026-10-05.md#b2-当次交付)：Android 本地原键命令、原始签名回执、网络重试、身份/权限/恢复屏障重验、审计排序迁移和交接页面已实现；真实回环 HTTP、非空迁移、服务端回归、Debug APK 与 lint 均有当次证据。
- 退出边界：组合使用生成身份、JVM/回环 HTTP 和 Debug APK；APK 安装、真机、两个物理设备、原身份、现场网络、断电/长时及发布部署仍未验，生产 gate 默认保持关闭。

## 自主开发验收补充（2026-09-30）

| 范围 | 验收入口 |
| --- | --- |
| R06 关联回执独立消费 S1 | [自主进度](history/AUTONOMOUS_PROGRESS_2026-09-30.md)：实现 f892586580423abedbe5b38721cbf89b3e908245；单候选关联后确认的真实 HTTP 与 Python 独立消费；范围、前置失败、未验与证据见记录，不代表 A1.2 整体完成。 |

## 自主接续核对与停报索引（2026-10-01）

- [S2/S4a 范围核对](history/AUTONOMOUS_SCOPE_AUDIT_2026-10-01.md)：既有来源信任、R07 outbox 与主管分离的源码/历史证据定位，不是新增验收。
- [S4b 三次失败停报](history/AUTONOMOUS_PROGRESS_2026-10-01.md)：两次 IT 和一次离线单测失败、源码/失败库保全及停止点；未取得写后回滚通过结论。


## 修订目标接续验收（2026-10-01）

- [R06 正式单反向候选独立消费](history/R06_REVERSE_CANDIDATE_CONSUMER_2026-10-01.md)：实际 HTTP/原始关系消费者、失败轮与回归证据；[接续报告 §11 起](history/AUTONOMOUS_PROGRESS_2026-10-01.md)区分 R1 守卫失败与 R2 通过，不作为整体完成。
- [R07 迁移后主管真实签名审批 HTTP 专项](history/R07_SUPERVISOR_SIGNED_HTTP_2026-10-01.md)：权限分离、签名生效、拒绝与原键重放的验收及边界。

- [R07 分离身份误批撤销 HTTP 专项](history/R07_SUPERVISOR_WITHDRAWAL_HTTP_2026-10-01.md)：原审批人发起、原申请人确认、逐笔 reversal、幂等与签名验收及边界。

## R07-P08-A acceptance entry (2026-10-01)

- [Correction append-only acceptance](history/AUTONOMOUS_PROGRESS_2026-10-01.md#16-r07-p08-a-append-only-guards-passed-2026-10-01): 803/804 failures preserved and classified; 805 isolated MySQL 1/0/0/0, idempotence rejection, offline compilation/parsing, and server regression passed. P08-B commitment rollback, real attachments/fault replacement, and other R06/R07 gaps remain open.

## R07-P08 review stop (2026-10-01)

- [P08 review correction and product-gap stop](history/R07_P08_REVIEW_STOP_2026-10-01.md): 805 XML and regression artifacts remain preserved, but acceptance is stopped for missing primary-key immutability, incomplete trigger attribution, and untested migration preservation. Product fix authorization is required before a new attempt.

## 2026-10-01 来源绑定边界记录

| 范围 | 验收入口 |
| --- | --- |
| R06 来源绑定分页（attempt 806 失败停止） | [源码与实测审计](history/R06_MAPPING_HIGH_ROUND_AUDIT_2026-10-01.md)：真实 HTTP 列表缺少下一页游标，1 test / 1 failure；失败证据保全，无产品修复或复跑。mapping3+、TEMP 高轮次及跨来源续作仍未验。 |

## 2026-10-01 交接事务回滚增量

| 范围 | 验收入口 |
| --- | --- |
| R07 outbox 实际写后回滚及原键恢复 | [807 验收](history/R07_HANDOVER_SQL_ROLLBACK_2026-10-01.md)：隔离 MySQL 1/0/0/0，精确 SQL 故障信号、16 表 typed 快照守恒、原键/上下文重启重放、非空占用不变；完整服务端 2857 项无新增失败。服务层合成前置，不替代 HTTP/PDA/worker/现场。 |

## R06 非空请求与回执设备迁移增量（2026-10-01）

| 范围 | 验收入口 |
| --- | --- |
| R06 设备迁移原键/回执字节保全 | [专项验收](history/R06_DRAFT_RECEIPT_MIGRATION_DEVICE_2026-10-01.md)：两个独立库各11请求、回执1/11，真force-stop后51业务表全行一致、宿主四库只读核验；相关单测16项通过。设备断言通过与包装器收尾FAILED/后续环境补核分列，三类失败证据保留。不代表旧APK升级或真实身份/HTTP。 |

## R07 受控更正附件增量（2026-10-01）

- [附件字节闭环验收](history/R07_CORRECTION_ATTACHMENTS_2026-10-01.md)：真实Web HTTP上传/原键/权限/CREATE消费/回滚与重启，非空迁移及精确SQL反例；808夹具失败保留、809通过。前端/PDA/现场/留存等边界见记录，不代表R07整体完成。


## R07 更正查询与原回执恢复（2026-10-01）

- [查询验收](history/R07_CORRECTION_READS_2026-10-01.md)：产品GET上下文/原文/原键响应，810真实Web HTTP与重启恢复、全服务端回归；[账务贡献静态缺口](history/R07_CORRECTION_ACCOUNTING_GAP_2026-10-01.md)独立停报，GET通过不恢复完整账务验收。

- R07 首次同键激活并发：[811失败与停报](history/R07_HANDOVER_FIRST_REPLAY_FAILURE_2026-10-01.md)、[同步消息静态缺口](history/R07_HANDOVER_SYNC_STATUS_GAP_2026-10-01.md)；保留反例，不计交付。


## R07 交接上下文与原回执读取（2026-10-01）

- [交接查询验收](history/R07_HANDOVER_READS_2026-10-01.md)：812真实Web HTTP由GET上下文驱动四步命令、四类原回执重建后逐字节恢复、6组16业务表不变；22项定向与完整服务端回归通过。PDA/页面/现场未验，811并发和同步消息等旧缺陷仍停报。

## R06 Web 原命令恢复反例（2026-10-01）

- [停报记录](history/AUTONOMOUS_PLAN_2026-10-01_TEMP_WEB_RECOVERY.md)：Web hook 一次反例10项中2项控制通过、8项失败；编辑、重挂载、存储失败、候选刷新及恢复代次变化会改写未知请求。未改产品，依赖该hook的恢复子项停报。

## R07 订单内交接列表（2026-10-01）

- [列表与浏览器验收](history/R07_HANDOVER_LIST_2026-10-01.md)：813–815失败保留，816真实MySQL/HTTP与浏览器6场景通过；分页无重漏、权限及晚响应保护、业务表不变。其余缺陷按[修订修复方案](history/AUTONOMOUS_PLAN_2026-10-01_DEFECT_REPAIR.md)接续，未将局部通过计作R07完成。


- R06 来源绑定分页修复：[验收](history/R06_SOURCE_BINDING_PAGINATION_REPAIR_2026-10-02.md)，单测及822真实HTTP通过，旧失败保留；只收分页范围。

- R06 TEMP续作受限完整intake：[验收与失败保全](history/AUTONOMOUS_PROGRESS_2026-10-07.md)，见第200–200b条；确认记账、原设备及生产能力另列未验。

## D01–D18 接续验收索引（2026-10-07）

| 范围 | 验收入口 |
| --- | --- |
| D07/R09 已收正文实际时间截点 | [截点恢复记录](history/D07_R09_RECEIVED_CHECKPOINT_2026-10-10.md)：1233实际来源扫码前dump/可信窗口及1237新目标原binlog全量恢复、写前拒绝/独立文件签名消费/受影响普通回归与失败保全分列，非closed/native或最终交接完成。 |
| D07/R09 原TEMP E2A字节前置 | [原件记录](history/D07_R09_ORIGINAL_EVIDENCE_2026-10-09.md)：原OPEN/PAGE/STOP、P256与页链/水位/原正文绑定，定向及受影响回归、保留1151只读消费者分列；原登记根/独立pin、权限/占用/终态与实际恢复仍缺，非D07/R09整体通过。 |
| D07/R09 原TEMP正文同一pin组合 | [组合记录](history/D07_R09_NATIVE_COMPOSITION_2026-10-09.md)：原E2A与五原件重读、冻结/STOP分界、原准备/ACK/终态/权限/占用；定向和受影响回归分列，只读前置，非实际恢复。 |
| D07/R09 原设备登记根采集 | [登记记录](history/D07_R09_DEVICE_REGISTRATION_2026-10-09.md)：故障前只读实际时间/实例采集、独立pin直接/配对公钥读取；定向、1156失败、1157真实MySQL、独立字节消费与回归分列，非实际业务恢复。 |
| D07/R09 原提交占用观察 | [占用记录](history/D07_R09_ORIGINAL_OCCUPANCY_2026-10-09.md)：原NORMAL实例/已收正文/ABORTED占用原列与独立pin只读消费；1158失败保留、1159及独立原字节/数据库列核对，非实际恢复或权限历史证明。 |
| D07/R09 原TEMP准备与ACK采集 | [原准备记录](history/D07_R09_TEMP_PREPARATION_CAPTURE_2026-10-09.md)：原五gzip/三签名/纳秒command只读观察与独立pin消费；1161实际MySQL/Node原列与受影响回归分列，非权限历史或实际业务恢复。 |
| D07/R09 原未收正文占用 | [未收正文记录](history/D07_R09_UNSUBMITTED_OCCUPANCY_2026-10-09.md)：原无hash ABORTED、E2A完整集合/对象引用与SOURCE_RESERVED独立pin消费；1162/1163失败保留、1164实际MySQL/Node及受影响回归分列，非权限历史或实际业务恢复。 |
| D07/R09 受约束原端权限窗口 | [原窗口记录](history/D07_R09_CONTROLLED_PERMISSION_2026-10-10.md)：同FTWRL精确GRANT、旧特权会话/后台写者及原日志独立pin；失败保全、1174真实MySQL、独立原字节消费者与受影响回归分列，非实际业务恢复。 |
| D07/R09 原未收正文组合 | [组合记录](history/D07_R09_UNSUBMITTED_COMPOSITION_2026-10-10.md)：原五件/独立占用pin、原E2A/准备ACK/逐保存资格/权限窗口语义同来源重读；定向和recovery普通回归分列，非连续权限历史或实际业务恢复。 |
| E08/D17/U04 高频首页导航 L1 | [专项记录](history/GOAL_E08_D17_U04_HIGH_FREQUENCY_L1_2026-10-18.md)：请求的 D 盘 datadir 不存在并记录 `Invalid -datadir`，实际使用默认 `Pixel_6.avd`；首页断言因未绑定设备/人员会话失败，故本次隔离证据无效。编译通过、未修改产品代码，后续扫码/暂停/恢复未运行。 |
| E08/D17 UI 主机回归 run01 | [专项记录](history/GOAL_E08_D17_UI_HOST_REGRESSION_RUN01_2026-10-18.md)：当前受影响 UI 主机单测定向执行退出0；覆盖订单卡片、TEMP 明细 reader、恢复只读卡片/对话框。仅作者同会话回归，不计 D17 整体、E06 L2、D18 L3。 |
| E07 恢复围栏主机回归 run01 | [专项记录](history/GOAL_E07_RECOVERY_FENCE_HOST_REGRESSION_RUN01_2026-10-18.md)：lease 退役、效果门默认关闭和恢复 SQL 选择器定向单测退出0；仅主机验证，不计 E07/D13 整体或 L2/L3。 |
| D04 跨来源审计、候选、旧 schema 首轮读取 | [当日执行记录](history/D01_D18_EXECUTION_2026-10-07.md)：1034、1037、1038及1042隔离证据；来源回归343/0/0/1。1043旧合成审计前缀失败保留并归因为输入不完整。D04仍因候选排除缺少授权而未通过；接续D05拒绝至实物处置组合。 |
| D05 拒绝后实物处置与冲突分流 | [当日执行记录](history/D01_D18_EXECUTION_2026-10-07.md)：1048真实隔离 HTTP/MySQL与Android Room通过拒绝→实物核验→释放；1050完整确认回归及批准后库存锁拒绝，1052真实 HTTP 两类冲突/原件重放/业务表守恒通过。1044–1047、1049及1051失败证据保留，取消释放政策仍未定义，D05未整体通过。 |
| D06 TEMP 交接与接收（开发中） | [当日执行记录](history/D01_D18_EXECUTION_2026-10-07.md)：原停止/独立审批/本人资格准备与下载已验；1076来源Android及接收Android实际准备/ACK/原件暂存/Room回滚和重开通过，非空迁移/受影响回归/默认APK与lint另列。全部早期失败保留；1078指定接收人服务端确认/并发/回滚/重启及默认关闭通过；1079 Android原键确认/实际HTTP/Room原子回滚已验，回归/APK/lint及UI01另列；UI02–04测试等待/ScrollToAction失败保留，UI05小屏大字/滑动/返回通过；[收口交接](history/D01_D18_HANDOFF_2026-10-07.md)。激活/最终提交、未冻结/无canonical等必做项未完成，D06未通过。 |
| D06 TEMP 专用激活服务端 | [执行记录](history/D01_D18_EXECUTION_2026-10-07.md)：1086真实HTTP/MySQL独立TEMP票据/本人会话确认/唯一轮次与占用直接转移、并发/末SQL回滚/重启/默认关闭通过；1087 FORMAL资料专项通过，普通回归3304/0/0/1，独立契约7项通过。1084/1085及消费者失败保留。Android接收激活至最终提交和其他D06必做范围仍未完成。 |
| D06 Android 激活安装、开轮与冻结（阶段备份） | [执行记录](history/D01_D18_EXECUTION_2026-10-07.md)：1088实际HTTP/MySQL与四段Android安装、独立SQLite/hash消费者及UI-08安装页；本人新资格ACK、唯一开轮、新输入、原前缀累计冻结/STOP及Room故障回滚。回归/APK/lint分列，条件跳过与全部早期失败保留。专用上传/成功链见下项，新开轮页面和相邻必做范围仍未通过，D06整体未完成。 |
| D06 独立TEMP受理与单跳最终成功 | [执行记录](history/D01_D18_EXECUTION_2026-10-07.md)：1093实际Android/HTTP/MySQL专用受理、独立主管记账与重启成功验签；1096补默认关闭、outbox/末审计SQL回滚、同原件并发/唯一submission及异文拒绝，同输入Android冻结/上传恢复/Room回滚/成功消费通过。普通/受影响回归、默认APK/lint及独立正文/三签名/SQLite消费者分列；1094/1095夹具失败原件保持。高轮、多跳、减包、未冻结/无canonical、分块/专用STOP与页面仍必做，非D06整体通过。 |
| D06 接收机来源与专用STOP | [执行记录](history/D01_D18_EXECUTION_2026-10-07.md)及[暂停收口](history/D01_D18_PAUSE_2026-10-08.md)：1103实际HTTP/MySQL/Android单跳、独立消费者、UI-08及受影响回归/APK/lint分列；1100–1102失败继续保全，不重跑，不外推D06整体。 |
| D06 多跳、混合及反向四轮（范围交付） | [多跳记录](history/D06_MULTIHOP_EXECUTION_2026-10-08.md)：1110实际A→B→C及TEMP_START产品页UI-04；[混合记录](history/D06_MIXED_EXECUTION_2026-10-09.md)：1118 A→TEMP B→R06 B、1120 A→TEMP B→R06 B→TEMP C，实际HTTP/MySQL/Android原件、SQL/Room尾故障、独立消费者与两端回归。失败、skip、历史APK输入分别登记；LOCAL后续证据见下行，非D06整体通过。 |
| D06 LOCAL独立冻结与前置拒绝 | [LOCAL记录](history/D06_LOCAL_EXECUTION_2026-10-09.md)：1124三轮实际HTTP/MySQL/Android清单-only→LOCAL E2A/STOP→最终成功，1125首轮LOCAL无canonical拒绝；未冻结/清单-only REQUEST、APPROVE/PREPARE原件/减包/撤权拒绝及精确SQL/Room尾回滚，独立Node/只读Room消费者通过。失败、回归和当批APK/lint分列；非D06整体通过。 |
| D07/R09 恢复交接target代次新资格 | [验收记录](history/D07_R09_RECIPIENT_QUALIFICATION_2026-10-11.md)：原来源/source代次保持、限定接收者与scope签名资格、真实MySQL/新JVM/独立消费及受影响回归；不代准备/激活/最终提交。 |
| D07/R09 恢复交接PREPARE | [验收记录](history/D07_R09_RECOVERED_PREPARATION_2026-10-11.md)：既有准备协议、精确签名效果及原全表守恒，MySQL/新JVM/独立消费；后续下载确认/激活/最终提交未完成。 |

- E06/O04独立采集只读应急导出：[验收记录](history/GOAL_E06_INDEPENDENT_EXPORT_2026-10-14.md)，原机产品文件/固定登记根/原库与Excel逐值守恒；主管人工对账及总目标未完成。

- E06/O04独立原件接收与主管核原授权：[验收记录](history/GOAL_E06_REVIEW_ACCEPTANCE_2026-10-14.md)，实际Android原包/非空恢复/真实HTTP、历史签发钥/双权限/新JVM及旧中转组合回归；完整结束、实物与人工关联继续。
- E06网页显式引用冲突决定：[L1开发备份](history/GOAL_E06_CONFLICT_ASSOCIATION_WEB_L1_2026-10-14.md)；Chrome正向/原键恢复与全表守恒，非E06整体收口。
- E06跨任务实物冲突网页：[L1开发备份](history/GOAL_E06_CONFLICT_WEB_L1_2026-10-14.md)；完整各方/原键/明确编号实际Chrome及全表守恒，短装/多任务与L2继续。

- E06人工关联短装批准服务：[L1开发备份](history/GOAL_E06_ASSOCIATION_SHORTFALL_L1_2026-10-14.md)；两种差额精确审批/真实事务与全表守恒、固定钥/原键保持，网页批准与E06组合继续。

- E06人工关联短装批准网页：[L1开发备份](history/GOAL_E06_ASSOCIATION_SHORTFALL_WEB_L1_2026-10-14.md)；显式两差额/说明、Chrome原键与全表守恒，非E06组合或UI整体收口。

- E06增量冲突责任链服务：[L1开发备份](history/GOAL_E06_CONFLICT_EXTENSION_L1_2026-10-14.md)；不可变追加/新关联最新引用、真实事务与原claim守恒、新JVM默认关闭；网页追加与E06组合继续。

- E06增量冲突责任链网页：[L1开发备份](history/GOAL_E06_CONFLICT_EXTENSION_WEB_L1_2026-10-14.md)；完整各方追加、Chrome丢响应/刷新/双页原key与历史保全、全表精确增量；多任务及E06组合继续。

- E07/O05同机恢复与加密副本：[专项验收](history/GOAL_E07_RESTORE_COPY_ACCEPTANCE_2026-10-14.md)；1541恢复180表/64非空表和15个私有/存储文件守恒，写前缺失/损坏输入4/4拒绝；O05专项14/14、受影响回归41/41、实际SAF CLI创建/取回及覆盖/损坏拒绝通过。仅同机开发演练，异地/现场保管、P07、E08/E09、E06 L2、D18 L3和总目标未完成。
- E07/O05归档恢复隔离候选：[验收记录](history/GOAL_E07_O05_ARCHIVE_RECOVERY_CANDIDATE_2026-10-17.md)；run695新隔离输入真实HTTP/MySQL、Python源/恢复消费、179表/15文件守恒、11/11故障回滚和恢复后HTTP通过；run693/694失败保留。仅有界候选，不计O05完整L2、E07/D14/D15整体、Android/Chrome或D18 L3。
- [E07闭合恢复跨截点前置验收](history/GOAL_E07_CLOSED_RECOVERY_ACCEPTANCE_2026-10-15.md)：1546 Authority、1547 Projection 均通过真实隔离 MySQL、辅助 schema、JDBC/dump/文件守恒和认证停机；1545 ACL 失败与新运行器误判保留。仅闭合恢复前置组合，不计 D14/D15 整体、E07 L2、D18 L3 或总目标。
- [E07更正五检查点恢复验收](history/GOAL_E07_CORRECTION_CHECKPOINT_ACCEPTANCE_2026-10-15.md)：618真实HTTP/MySQL完成更正 create/approve/withdraw/confirm，五个非空dump与原ROW binlog重建、179表/迁移/触发器/文件守恒、四份RSA原回执及20次原键重放，独立Python和认证停机通过；不计E07 L2、D14/D15整体、D18 L3或总目标。
- [E07/D06-D07 撤回恢复 run741](history/GOAL_E07_D06_D07_WITHDRAWAL_RECOVERY_RUN741_2026-10-18.md)：真实隔离 HTTP/MySQL 完成撤回、重发、新设备独立作业及五截点 binlog 重建，1/0/0/0、认证停机通过；合成身份且未覆盖 Android/Chrome/Python/原模拟器，不计 D06/D07 L2、E06 L2 或 D18 L3。
- [D11 R08完整导出套件 L1](history/GOAL_D11_R08_EXPORT_INTEGRATION_L1_2026-10-17.md)：新隔离库 680 的 `R08ExportIT` 14项真实 HTTP/MySQL/renderer 全部通过；678 旧浏览器路径 503 与 679定向PDF补验、自动删除 datadir 事件均保留并单列。仅补该套件 L1，D11受影响组合、E06/E07 L2及D18 L3仍未完成。
- [D13 HTTP/Session入口 L1](history/GOAL_D13_RECOVERY_HTTP_SESSION_L1_2026-10-17.md)：新19444隔离实例真实 Spring Session JDBC/HTTP 2项通过，覆盖 ERROR/ASYNC/multipart、并发、fenced/stale 拒绝和 cleanup guard；证据路径身份不匹配的 19442/19443 失败原件保留。仅补 HTTP/Session L1，worker/文件/导出跨入口组合、D13整体及D18 L3仍未完成。
- [D13 跨进程 lease L1](history/GOAL_D13_RECOVERY_LEASE_PUBLICATION_L1_2026-10-17.md)：新19445隔离实例真实应用根发布 lease，子进程拒绝并退休孤儿后再次启动，1项通过；仅补 lease 发布边界，worker/文件/导出、错库/水位与 D13 整体仍未完成。
- [D13 恢复 worker、文件与导出 L1](history/GOAL_D13_RECOVERY_WORKER_FILE_EXPORT_L1_2026-10-17.md)：新隔离 run 684 真实 HTTP 创建、SQL/文件恢复、独立 worker JVM、renderer/XLSX 下载及 Python/Java 只读消费者通过；681–683 失败原件保留。仅有界 L1 开发备份，不计 D13 L2、D18 L3 或总目标。
- [D15 旧程序写入→新 worker L1](history/GOAL_D15_OLD_PRODUCER_NEW_WORKER_L1_2026-10-17.md)：run698 完成 `ORIGINAL_CONFIRMED/XLSX`，run700 完成 `CURRENT_EFFECTIVE/XLSX` 的旧 JAR 创建→新 JAR worker→旧/新下载及独立 Python/全表守恒；run699 的消费者口径失败和原件保留。527 个原 InnoDB datadir 文件已缺失，不能写成原字节恢复，非 D15 整体/L2/L3。
- [D15 旧程序写入→新 worker `SCAN_XLSX` L1](history/GOAL_D15_OLD_PRODUCER_SCAN_XLSX_L1_2026-10-17.md)：run701 完成 `SCAN_XLSX/ORIGINAL_CONFIRMED` 的旧 JAR 创建→新 JAR worker→旧/新下载、独立六列 ZIP/XML 消费及全表/文件守恒；单订单单任务有界候选，不计 D15 整体/L2/L3，多任务/PDF/升级回退仍未验。
- [D15 旧程序写入→新 worker 双任务 `SCAN_XLSX` L1](history/GOAL_D15_OLD_PRODUCER_SCAN_XLSX_MULTITASK_L1_2026-10-17.md)：run704 完成两个独立任务排队、单新 worker 执行、旧/新各两次下载及独立消费者/全表守恒；run702/703 夹具失败原件保留，仍不计 D15 整体/L2/L3，多订单/PDF/升级回退未验。
- [D15 旧程序写入→新 worker PDF L1](history/GOAL_D15_OLD_PRODUCER_PDF_L1_2026-10-17.md)：run713 完成单任务、run714 完成双任务 `PDF/ORIGINAL_CONFIRMED` 的旧 JAR 创建、新 JAR worker、旧/新下载、独立 PDF 消费及 180 表/文件守恒；run705–711 失败原件保留。仅隔离 L1，不计 D15 整体/L2/L3，升级回退和现场仍未验。
- [E08/D17 run903 fixture 与 Android/HTTP 候选](history/GOAL_E08_D17_RUN903_FIXTURE_AUDIT_2026-10-18.md)：独立 userdata、真实 Spring/MySQL 上 `PreparationEndToEndTest` 1/0/0/0 通过，设备 evidence、Room 重开、丢响应恢复和数据库回读已保全；高频首页因无临时准备资料失败并暂缓，合成输入不计 D17 L1、E06 L2 或 D18 L3。
- E08/D17 V36 列兼容后的 Android/真实 HTTP 候选：[验收记录](history/GOAL_E08_D17_ANDROID_HTTP_RUN1597_2026-10-18.md)：run1597 非空 V34→V36、真实 Spring HTTP/MySQL、Android Room 丢回执恢复及认证停机通过；run1596 的列缺口失败和定向 Mockito 夹具失败保留。仅局部候选，不计 D17 整体、E06 L2、D18 L3 或总目标。
- [E08/D17/U04 高频 Android L1 run925](history/GOAL_E08_D17_U04_HIGH_FREQUENCY_RUN925_L1_2026-10-19.md)：修复后的完整隔离 AVD、真实 HTTP/MySQL、非空迁移和当次 APK 实际通过准备页、首页导航、离线扫码/暂停恢复、保存扫码/重复包拒绝；输入为合成隔离身份，设备最终 0 submission/0 recovered confirmation/activation。仅计 U04/D17 有界 L1，Chrome/Python、统一候选、E06 L2、D18 L3仍未验。
## E08/U03 独立采集 host 验证（2026-10-18）

- [专项记录](history/GOAL_E08_U03_INDEPENDENT_HOST_RUN02_2026-10-18.md)：绑定既有服务端签名向量后，独立凭据 parity、救援快照和跨来源防重共 17 项 host/Robolectric 实际执行，0 failure/0 error/0 skip。首轮缺向量输入的运行器失败原件保留并单列。
- 本轮没有独立 AVD、原身份/Keystore、真实 DocumentsUI 输入或 Android 页面消费；因此只计 U03 实现级候选，不计 U03 完整验收、E06 L2、D17、D18 L3 或总目标。
- [SKU 页面 L1](history/GOAL_E08_U03_SKU_UI_L1_RUN01_2026-10-18.md)：全新隔离 AVD 实际 DocumentsUI 选择、重复确认/坏行取消、进程重启原件守恒和 100%/200% 字号布局均通过；dirty/status/patch/manifest 已刷新。输入为合成开发备份，不计 U03 完整、D17、E06 L2、D18 L3 或总目标。
- [独立凭据/离线任务/扫描链 L1](history/GOAL_E08_U03_NATIVE_CHAIN_L1_RUN01_2026-10-18.md)：全新隔离账号、真实 HTTP 登记 Android 原生公钥、产品凭据页、断网任务与 DocumentsUI SKU、扫描/任务末条 SQL 失败回滚和新进程恢复、撤权/重启边界均由实际 instrumentation 通过；阶段断言失败和 picker 缓存失败原件保留。合成开发输入，不计 U03 完整、D17、E06 L2、D18 L3 或总目标。
