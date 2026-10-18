# U02/U04 本单资格说明页原生 L1（2026-10-19）

## 范围

本切片补 U02 要求的逐单“可看、可扫、可结、可传、可导出”实际资格说明，并从正式/TEMP 作业页提供同一只读入口。它没有打开扫码、冻结、上传、救援凭据或任何生产决定能力；资格页只消费本机已有资料、签名、身份、时间、恢复围栏、防重和队列状态。

U04 只计该辅助页的实际 Android 入口、四档字号和安全区验证。原有明细筛选、设置诊断和高频状态语义按路径+SHA复用，不把本切片写成完整 U04 或 D17。

## 逐路径审计

审计清单保存在 `.local/goal-20261019-resume/u02-qualifications-run01/u02-u04-path-audit.json`，每项含标准、报告 SHA、现行路径 SHA 和剩余验证：

| 范围 | 现行事实 | 处置 |
| --- | --- | --- |
| U02 队列/待确认 | `WorkRenderer.kt`、`TemporaryWorkRenderer.kt` 已区分待确认、待落盘和本机已保存；原生状态 L1 仍不覆盖完整故障组合 | 按原 SHA 复用，不重复重写 |
| U02 结单下一动作 | 正式/TEMP `SubmissionNextAction` 已对冻结、结果未知、拒绝和终态给出主动作；历史原生验证为 MockWebServer | 按原 SHA 复用，保留真实 HTTP/L2缺口 |
| U02 设置诊断 | `ConnectionDiagnosis` 已区分传输、身份、权限、恢复、服务和未知原因；四字号负向入口已验 | 按原 SHA 复用；本轮正常连接 GET 只用于原身份校时 |
| U02 资格说明 | 首页卡片和 TEMP 准备页只有概括状态，没有逐单可看/可扫/可结/可传/可导出说明 | 新增资格报告、正式/TEMP 实作和入口 |
| U04 明细/状态筛选 | 正式/TEMP 原记录筛选和定位已有实现；漂移路径仍需组合候选 | 保留继承路径，未重写 |

## 实现

- `TaskQualificationActivity` 使用安全窗口、系统栏内边距、可展开原始原因和重试；无业务写入。
- `FormalTaskQualification` 核对原人员/设备/仓库、离线授权、计划摘要、本机行数、恢复/召回、防重、队列、待确认和本机汇总。上传只校验原冻结正文摘要并给出“先查原 ID”提示。
- `TemporaryTaskQualification` 核对原临时授权清单、五份资料、身份、期限、原状态、防重、队列和有效数量；不把旧交接/续作原件变成新资格。
- 两类页面都明确到期、重启、退出不删原记录；导出仍需只读救援页的预置凭据和范围核验；最终确认/放行保持阻断。
- 生产默认属性、`localDeletionAllowed=false`、七类生产决定、取消的换机承接/多跳/接收确认链均未改变。

## 验证

输入和环境：保留 `Filter914_Pixel6` 原 userdata、原设备/人员/仓库、原 Keystore、公钥、Room 与隔离 HTTP/MySQL；服务使用原 run914 jar，数据库身份见 `services-resumed.json`。目标端口、HTTP、认证 MySQL 和模拟器均已在 `stop-result.json` 中正常停止。

- `assembleDebug`、`assembleDebugAndroidTest`、`lintDebug`：第二次构建 `build02` 成功，lint `0 errors, 492 warnings`。
- 原生 `reboot01`：`OK (1 test)`。模拟器重启后的正式/TEMP资格页可读原记录，扫码显示“须联网核对时间”，无冻结批次则显示无原批次可上传；Room＋WAL 前后保持。
- 原生 `positive01`：`OK (1 test)`。正常产品“检查后台连接”实际 GET 重校可信时间，原身份和公钥不变；正式/TEMP作业入口均可打开资格页，报告本机前置，不创建输入、不冻结、不上传。
- 原生 `missing01`：`OK (1 test)`。伪造不存在的 UUID 只显示核对未完成，不泄露另一单资格；Room 保持。
- `font100`、`font130`、`font150`、`font200`：各 `OK (1 test)`。实际系统字号分别为 1.0/1.3/1.5/2.0；进程内视图检查覆盖系统栏、输入法安全区、文字无省略、按钮可达和 Activity 重建。
- Python `consumer02.py` 消费 `device-preinstall`、重启、正向、缺单、四档字号和最终 tar；83 张表 `integrity_check=ok`、外键无错误、所有阶段字节相同、无新 capture/scan/submission。

截图在 `FLAG_SECURE` 下被系统按预期保护而显示黑屏；因此不把黑屏当页面证据，布局证据来自测试进程内 `View` 绘制/可达/省略检查，截图原件仍保留。

## 边界

这是 U02/U04 的有界 Android L1 开发备份，不是 U02、U04、D17、E06 L2、D18 L3 或 §8 闸门通过。仍缺真实最终候选的 Android/HTTP/MySQL/Chrome/Python/非空迁移/原模拟器/恢复全链，结单真实回执、权限并发、O02/O03/O04/O05、D16和现场条件也未因本页关闭。失败证据、继承 WIP 和取消范围源码继续保留。
