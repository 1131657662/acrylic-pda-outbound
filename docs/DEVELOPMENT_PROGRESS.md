# 开发进度索引

本文只保留当前有效结论和证据入口；版本、分支、SHA、运行环境与写权只看 [开发看板](ORCHESTRATION_STATUS.md) 的 `CURRENT_STATE_START/END`。详细历史原文已归档至 [2026-10-20 原进度索引](archive/2026-10-20/DEVELOPMENT_PROGRESS.md)，本文件不再复制逐轮日志。

## 当前有效结论

- TEMP原记录已有[新产品APK四字号受影响消费者](history/GOAL_U04_TEMP_NEW_APK_STATUS_CONSUMER_2026-10-20.md)：复用原22条合成记录，四档各1项通过，83表/会话密文及旧证据守恒；重启围栏全部调整行禁用，非空可信保留、真实IME和完整UI/统一候选仍未验。
- 原记录筛选页已有[真实IME四字号有界验证](history/GOAL_U04_ADJUSTMENT_FILTER_NATIVE_IME_2026-10-20.md)：仅补系统栏/IME留白；同一保留合成正式输入四档各1项通过，14项定向回归、lint 0错误/508警告；83表与会话密文守恒，两次失败保全，不记完整UI或统一候选通过。
- 正式订单原记录筛选已有[同一合成输入四字号原生消费者](history/GOAL_U04_FORMAL_STATUS_FILTER_NATIVE_CONSUMER_2026-10-20.md)：100%/130%/150%/200%各1项通过，83表与会话内容守恒，产品未改；未预采密文SHA，非空可信主管保留及完整UI/统一候选仍未验，不记U04/D17整体通过。
- 原记录状态筛选已有[重启前100%/200%消费者](history/GOAL_U04_STATUS_FILTER_NATIVE_CONSUMER_2026-10-20.md)与[重启围栏下130%/150%消费者](history/GOAL_U04_STATUS_FILTER_REBOOT_FONT_CONSUMER_2026-10-20.md)：实际四选项、分页/组合/重建，83表及身份守恒；后者所有调整行禁用。130%首轮测试预期失败保留；主管保留仅空结果，不拼成同条件四字号或U04/D17/L2/L3通过。
- 总目标、D01–D18、O01–O05、U01–U04 和第 8 节闸门仍未完成；局部 L1、合成输入和开发备份不替代实际最终候选上的组合验收。当前断点和下一动作以 [看板](ORCHESTRATION_STATUS.md) 为准。
- 四档字号保留明细的 tar 只读比较已形成独立历史报告：[U04 retained details tar audit](history/GOAL_U04_RETAINED_DETAILS_TAR_AUDIT_2026-10-19.md)。该报告区分 Room DB/WAL、应用资料和运行元数据，并保留输入与输出哈希；不改变业务验收状态。
- 独立采集大字首屏原生 L1 已形成报告：[U04 independent large font](history/GOAL_U04_INDEPENDENT_LARGE_FONT_L1_2026-10-19.md)。它只覆盖合成隔离输入和有界 Android L1，原身份、现场、实体键、真机及 L2/L3 仍未验。
- 恢复租约退休与效果门当前源 L1 已形成报告：[recovery lease retirement](history/GOAL_RECOVERY_LEASE_RETIREMENT_L1_2026-10-19.md)。它是文件树/主机层证据，不是可信恢复 Authority、真实 MySQL/HTTP 或业务链验收。
- Web 故障撤回 fail-closed 当前源 L1 已形成报告：[E07/D06-D07 Web run02](history/GOAL_E07_D06_D07_WEB_FAIL_CLOSED_CURRENT_RUN02_2026-10-19.md)。未构建或覆盖既有发布产物，真实 HTTP、MySQL、Chrome、Android 和现场边界仍未验。
- O02 电脑端当前源只读消费者 L1 已形成报告：[O02 current-source consumer](history/GOAL_E08_O02_CURRENT_SOURCE_CONSUMER_RUN04_2026-10-19.md)。业务回执、外置介质、真实 PDA/原身份和删除能力仍未证明。
- O02 Android rescue 主机回归已形成报告：[O02 rescue host run](history/GOAL_E08_O02_ANDROID_RESCUE_HOST_L1_2026-10-20.md)。宽过滤器失败原件保留；排除失败类的主机回归不能替代失败类修复，也不能替代设备、SAF/MTP/OTG、真实 HTTP/MySQL 或 O02/O05 L2/L3。

## 仍未验与边界

- rescue 宽过滤器中的失败类、当前候选与原身份/原资料/外置介质/现场回执仍是未验资料；失败报告和原始证据按历史入口保留。
- 不恢复换机承接、多跳、接收者确认/激活链；不接原库/现网，不使用正式签名，不部署、Release 或合并 `main`；生产决定保持 fail-closed，`localDeletionAllowed=false`。
- 治理整理、索引压缩、证据清理不计 D01–D18、O/U 或第 8 节闸门。

## 接手入口

1. 先读 [AGENTS.md](../AGENTS.md)、[项目文档地图](PROJECT_DOCUMENT_MAP.md)、[Git 工作流](GIT_WORKFLOW.md) 和 [看板当前区](ORCHESTRATION_STATUS.md)。
2. 需要复核旧进度索引时读取 [原进度索引归档](archive/2026-10-20/DEVELOPMENT_PROGRESS.md)；需要专项细节时直接读取 `docs/history/` 下的报告，不把本索引当作完整证据。
3. 新切片的输入 SHA、命令、环境、退出码和输出哈希按 [剩余计划](REMAINING_DEVELOPMENT_PLAN.md) 建在新的 `.local/<任务根>/` 下。

## 旧索引映射

旧索引保留原字节，映射清单和 SHA 见 `.local/goal-20261020-resume/index-compaction01/pre-index-manifest.json`。旧索引中的逐轮段落、锚点和历史措辞不再承担当前状态；需要原文时按归档文件读取。

## 2026-09-18 开发阶段提交

这是旧索引锚点的兼容入口；原文位于 [原进度索引归档](archive/2026-10-20/DEVELOPMENT_PROGRESS.md)。
