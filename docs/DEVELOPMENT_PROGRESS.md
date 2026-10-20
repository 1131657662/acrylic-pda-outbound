# 开发进度索引

本文只保留当前有效结论和证据入口；版本、分支、SHA、运行环境与写权只看 [开发看板](ORCHESTRATION_STATUS.md) 的 `CURRENT_STATE_START/END`。详细历史原文已归档至 [2026-10-20 原进度索引](archive/2026-10-20/DEVELOPMENT_PROGRESS.md)，本文件不再复制逐轮日志。

## 当前有效结论

- O02补[rescue完整主机回归](history/GOAL_O02_NATIVE_FULL_HOST_REGRESSION_2026-10-20.md)：同输入13类63项+2guard共65/0/0/0，实际执行；NATIVE/源码/fixture/继承及旧证据守恒。只收口该主机集合，非完整O02/设备/HTTP或最终候选。

- O02补[原生SQLite短路径复验](history/GOAL_O02_NATIVE_SQLITE_SHORT_PATH_RECOVERY_2026-10-20.md)：原19个失败方法与2项夹具检查在同次执行21/0/0/0；NATIVE/断言/源码及继承SHA保持，资源加载失败另存。仅合成主机定向，非完整O02或最终候选。

- E06补[Android实际服务端SKU向量消费者](history/GOAL_E06_ANDROID_SKU_PARITY_CONSUMER_2026-10-20.md)：1项测试实际消费47组原字节、零失败/错误/跳过；1024源输入/10 fixture/31继承SHA守恒。仅主机解析，不计Room/设备/O04整体或最终候选。

- E06补[当前源封存原件条件消费者](history/GOAL_E06_CURRENT_SEALED_CONSUMERS_2026-10-20.md)：显式历史哈希输入四类24/0/0/0，原件33文件/两归档及继承改动守恒；该轮新47组向量的Android后续消费另见上述专项报告。独立定向不改写完整回归skip，不计E06或最终候选通过。

- [服务端普通完整回归](history/GOAL_SERVER_ORDINARY_FULL_REGRESSION_2026-10-20.md)：实际主树+保护WIP同次执行4775项，4756通过、0失败、19跳过、exit0；外部向量/Windows符号链接skip逐类留证。不含IT，不计D18/最终候选或部署放行。

- D13补[孤儿 owner 生命周期测试修复](history/GOAL_D13_ORPHAN_OWNER_LIFECYCLE_TEST_REPAIR_2026-10-20.md)：原11/3复现，改为真实独立子JVM发布后退出；四类21/0/0/0，原孤儿拒绝/清理/只读及延迟退休断言保留。产品不变，仅主机定向，不计D13整体或完整候选回归。

- D04已补[审计前缀流式JDBC测试覆盖修复](history/GOAL_D04_AUDIT_STREAMING_TEST_REPAIR_2026-10-20.md)：当前主树原失败9/1复现，修正旧mock为真实回调消费者后18/0/0/0；两审计表ID/设备序号冲突、幂等及JDBC失败覆盖。生产校验不变，不计MySQL/HTTP、完整回归或部署放行。
- O03补[当前Android封存电脑返还文件消费者](history/GOAL_O03_CURRENT_ANDROID_SEALED_RETURN_2026-10-20.md)：显式外部向量实际调用产品双层验签，覆盖成功、错根/范围/正文/汇总/删除边界及旧终态反例；只作主机有界组合，不替代原机文件持久消费、真实HTTP或最终候选。
- O03已补[处理响应与返还下载一致性修复](history/GOAL_O03_RESULT_DOWNLOAD_BINDING_2026-10-20.md)：23项定向测试及封存真实传输字节离线回放通过；错结果/内容/签名不发布完整文件，冲突保全且原键保持。不计新增HTTP、签名信任、现时净值或O03整体通过。
- O02已补[TEMP首轮历史成功原文件离线核验](history/GOAL_O02_TEMP_RECEIPT_OFFLINE_2026-10-20.md)：36项TEMP及30项FORMAL定向回归通过，封存TEMP成功原正文/HTTP响应实际CLI退出0；包外原范围/映射/公钥与原件守恒。仅历史回执，不确认应急包完整性、现时终态或放行/删除，不计完整O02或最终组合。
- O02已补[FORMAL历史成功原回执离线核验](history/GOAL_O02_FORMAL_RECEIPT_OFFLINE_2026-10-20.md)：30项定向测试及封存模拟器原包实际CLI通过；包外固定公钥验签、原冻结字节/来源/逐包/分产品守恒。仅历史成功原回执，不查询现时业务状态、不授放行/删除，不计完整O02或统一候选。
- 故障撤回默认关闭已补[真实Chrome/HTTP有界消费者](history/GOAL_D06_D07_DEFAULT_OFF_CHROME_HTTP_2026-10-20.md)：四项通过、页面零撤回POST，直调409；13业务表与原审计守恒，仅新增4登录审计。原运行器守恒断言退出1保留，独立只读复核退出0；不计完整D06/D07或统一候选通过。
- TEMP原记录筛选已有[真实IME四字号消费者](history/GOAL_U04_TEMP_NATIVE_IME_2026-10-20.md)：新测试APK独立构建，复用原记录/产品APK，四档各1项通过；83表/会话密文及旧证据守恒，重启围栏保持。内容图不含键盘像素，非空可信保留和完整UI/统一候选仍未验。
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

- rescue 旧19失败方法的本轮NATIVE短路径定向结果见上述报告；完整rescue主机集合已另有上述65项同次结果；最终候选、原身份/原资料/外置介质/现场回执仍未验，历史失败原件保留。
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
