# D01 必做范围、历史证据与剩余验收矩阵

本次盘点覆盖D01–D18、O01–O05、U01–U04共27项。它替代早期盘点中的待实现判断，不改变原报告结论，不将多个输入的通过拼接为L2/L3。总目标第8节尚未全部满足，保持active；取消的换机承接、多跳及激活不恢复。

## 输入与复用规则

固定树`D:/acrylic-pda-outbound`，分支`codex/development`，实核HEAD/开发远端均`e76979aa887aae790ead004b2e548d63cfd4b93a`。本聊天唯一写者，清理及另两项目聊天idle，无本批Java/Gradle/MySQL任务；共享MySQL身份未知只观察，原5564/ADB保留，真机未操作。版本事实只见看板生成块。

`.local/goal-20261017-resume/input12.json`及同号附件保存5463项全部输入、binary diff、未跟踪副本、33树、写权、实际远端、进程/监听/C/D余量及旧看板；清单SHA`62302395e43d014eec273d63870b2bc2446666b0f7fe3645b4f137c0751906cd`，与提交后input11逐字相同。盘点前C/D余量78716080128/15522263040 bytes，无清理。

`matrix-report-bindings01.json`固定下表60份报告的完整路径、长度、SHA及最后提交，清单SHA`bf89cdbe2e41a824257269c9b312a12e41e49ba1087834f01cdc0fdacc419db6`；`matrix01.py`只读生成27项映射。**报告hash证明引用对象，不能证明产品输入未变**。每个下一验收单元仍须比较原source/input清单与实际源码，逐路径列出可复用SHA和漂移；仅对受影响范围补验，原日志/XML/dump/binlog/失败事实不变。

下表“已有”仅指报告明确执行过的范围；“仍必做”包括实现或材料缺口、受影响组合和候选复验，不能自动改记C档。作者/执行/自审为同一助手，Python/Node消费者不是独立人员审查。

## D01–D18

| 项 | 已有实现与正式证据入口 | 仍必做、依赖与下一验收单元 |
| --- | --- | --- |
| D01 | 本页、input12及60报告绑定；[前轮基线](GOAL_E00_BASELINE_2026-10-11.md)仅历史 | 本次盘点完成；每批增量维护输入/写权和本矩阵对应项，盘点本身不计业务通过 |
| D02 | [原执行D02退出复核](D01_D18_EXECUTION_2026-10-07.md#d02-页面与退出标准复核)：原键队列、受控确认、续扫累计冻结、实际Android/HTTP/MySQL成功、页面及尾写回滚；收口提交在原页 | 原开发子范围已验；新UI/调整/导出影响由E02/E03/E08定向覆盖。候选须重验对应正向/恢复，不重做旧起点 |
| D03 | [原执行D03退出复核](D01_D18_EXECUTION_2026-10-07.md#d03-退出标准复核)：高轮、分块、关联/重关联、K转移和确认账务 | 按原输入复用同人同机R06链；候选核高轮、原键/异文/并发/末SQL与Android消费者。已取消R07换机链不加入 |
| D04 | 原执行1034/1037/1038/1042来源/完整候选；[P04原机产品及旧非空读者](GOAL_E04_P04_NATIVE_PRODUCT_2026-10-12.md)补主管排除、映射取得及成功验签 | 不再沿用“候选排除未授权/未实现”。1043旧合成完整审计前缀、A1.4五轮失败仍暂缓，须新原材料或明确修复假设才入队；不得原样重复 |
| D05 | 原执行1048/1050/1052停止→拒绝→实物/库存冲突；[零草稿取消](GOAL_E04_ZERO_DRAFT_CANCEL_2026-10-12.md)、[P05非零主管取消](GOAL_E04_P05_NONZERO_CANCEL_2026-10-12.md)原ID续读及守恒 | P05旧“未实现”已被后续报告替代；对E06扩展后的实物/关联组合核输入漂移，重验受影响范围；成功终态走更正 |
| D06 | [E01撤回重发](GOAL_E01_RECALL_REPUBLISH_2026-10-11.md)、[D10故障撤回HTTP补验](GOAL_D10_FAULT_WITHDRAWAL_HTTP_ACCEPTANCE_2026-10-15.md) | 新范围有实际服务/Web/PDA证据，不能继承早期“仅零占用”结论。候选核无STOP撤回、竞争/旧上传拒绝/末SQL；灾后五截点依D14 |
| D07 | E01同一报告：新发布/领取、新机从头扫码上传验签及旧凭据隔离 | 候选重验实际Android/HTTP/MySQL/Web新范围，设备停用与撤回分开；不恢复旧进度迁移/接收者激活 |
| D08 | [P06 HEADER补偿](GOAL_E04_P06_HEADER_COMPENSATION_2026-10-12.md)、[净值多来源](GOAL_E05_NET_SUMMARY_2026-10-13.md)及D14更正检查点 | 保留原出库/原签、逐包贡献与账务；候选覆盖退货/错误/重量/抬头及撤销、独立审批/并发/后继冲突，不能只跑查询 |
| D09 | [对账](GOAL_E04_P06_RECONCILIATION_2026-10-13.md)、[退库新周期](GOAL_E04_P06_RETURNED_STOCK_2026-10-13.md)、FORMAL/开放FORMAL/开放TEMP/PC解隔离报告（完整路径见绑定清单） | 逐来源正向已有，按E06新增组合与恢复范围核漂移；最终新周期再出库/旧周期防重/账务唯一性必须实际组合，不以案件结案代业务完成 |
| D10 | D10故障撤回HTTP、P04产品、E01/P06各入口与附件证据 | 核附件三容量边界及真实字节、越权/撤权/原请求恢复的候选覆盖；受影响Android/Web实际成功操作仍必跑，Chrome1540阻断不绕过 |
| D11 | E05净值、[独立来源导出](GOAL_E06_INDEPENDENT_EXPORT_2026-10-14.md)、E03电脑工具、[R08完整导出套件 L1](GOAL_D11_R08_EXPORT_INTEGRATION_L1_2026-10-17.md) | R08ExportIT 已在新隔离库完整执行14项并通过；仍只收口该套件 L1。候选按正式/TEMP/R06/撤回重发/调整/独立来源映射汇总、固定版本及原顺序，独立来源不计成功出库；D11 受影响组合及 D18 L3 仍必做 |
| D12 | [O03受控中转](GOAL_E05_O03_ACCEPTANCE_2026-10-13.md)、E03救援/主管Android、[恢复后产品worker](GOAL_D15_RESTORED_EXPORT_WORKER_2026-10-16.md) | 格式/安全Excel/文件失败和回执消费已分层验证；候选实际执行任务/下载/重启/授权变化/损坏与Android文件消费组合，不将“已接收”当成功 |
| D13 | P07 keyring/offline-rekey/restore及[历史覆盖](GOAL_E07_HISTORY_COVERAGE_ACCEPTANCE_2026-10-15.md)；[HTTP/Session入口 L1](GOAL_D13_RECOVERY_HTTP_SESSION_L1_2026-10-17.md)；[跨进程lease L1](GOAL_D13_RECOVERY_LEASE_PUBLICATION_L1_2026-10-17.md)；原根/原钥与UNKNOWN围栏报告；[生产调度1557](GOAL_D13_WORKER_ADMISSION_2026-10-17.md)；[renderer围栏1559](GOAL_D13_RENDERER_HANDOFF_2026-10-17.md) | HTTP/Session真实容器、Session JDBC、ERROR/ASYNC/multipart、并发及fenced/stale拒绝已在19444通过；19445补真实lease发布、孤儿拒绝/退休/再启动。1557补默认关闭/准入后生产worker及真实文件导出、持根阻推进与末SQL回滚；1559补renderer子租约/未确认隔离/真实halt70及后继孤儿拒绝；完整P5正常退役、全部文件入口、错根/错库/水位反例及跨入口组合仍须同一受影响候选补齐，D13整体未完成 |
| D14 | [撤回656/657](GOAL_E07_WITHDRAWAL_RECOVERY_COMBINATION_654_ACCEPTANCE_2026-10-16.md)、更正618、退库624、中转625、[独立647](GOAL_E07_COMBINATION_647_ACCEPTANCE_2026-10-16.md)、[产品仓库667](GOAL_E07_FORMAL_REPOSITORY_RESTORE_COMBINATION_2026-10-16.md)、[TEMP产品仓库675](GOAL_E06_TEMP_REPOSITORY_RESTORE_L1_2026-10-17.md) | 不重复原截点。各受影响输入绑定后再组合Android/HTTP/MySQL/文件；TEMP原会话/原ID恢复具体单元已有L1，独立来源客户端及U入口剩余覆盖逐一核，最终L3不可免除 |
| D15 | 非空升级1550、恢复任务1668/worker670、[新→旧→新673](GOAL_D15_PROGRAM_ROLLBACK_2026-10-16.md)、[资源约束](GOAL_D15_ISOLATED_RESOURCE_L1_2026-10-16.md)、[旧worker677](GOAL_D15_OLD_WORKER_COMPATIBILITY_L1_2026-10-17.md)；后续[旧写入单/双PDF](GOAL_D15_OLD_PRODUCER_PDF_L1_2026-10-17.md)及[同一候选升级回退恢复1554](GOAL_D15_UPGRADE_ROLLBACK_COMBINATION_2026-10-17.md) | 指定格式旧写入/新worker与七段程序读取/非空升级/原备份恢复各有明确输入的有界证据，不能拼为D15整体。资源为合成user unit，不是项目Linux启停。剩余运维/恢复组合与最终候选继续，真实异地/生产RPO/RTO/Linux现场另列C06 |
| D16 | [实际整单消费者](GOAL_D16_WHOLE_ORDER_L1_2026-10-16.md)、无关guard/审计和[恢复后复杂引用](GOAL_D16_RESTORED_FORMAL_REFERENCE_COMBINATION_2026-10-16.md) | 正常成功单仍有有效guard；正常目标合法退出资格没有依据。不能删guard、改状态或以任意孤立fixture造资格；新可信退出依据前该部分未完成。其余引用/原生trigger反例已有，不机械重跑 |
| D17 | E02/E03/E06/E08与U项；[旧恢复客户端非空升级](GOAL_E07_LEGACY_RECOVERY_UPGRADE_L1_2026-10-16.md)、[原生广播四阶段](GOAL_D17_NATIVE_FOREGROUND_QUEUE_2026-10-17.md)、[实际作业Activity四阶段](GOAL_D17_ACTIVITY_LIFECYCLE_COMBINATION_2026-10-17.md) | 有界Activity暂停/新PID原ID恢复已补；整套组合/候选仍未完成。旧BCD/原资料/526逐材料核，不用fixture替代原件 |
| D18 | [Web候选阶段](GOAL_E09_WEB_CANDIDATE_ACCEPTANCE_2026-10-15.md)仅typecheck/契约/build/审查 | 尚无统一最终候选。实际Android、HTTP/MySQL、Chrome、Python、非空迁移、原模拟器及恢复全链L3必跑；未实现/暂缓/工具阻断未核清不能complete |

## O01–O05、U01–U04与UI重设计

| 项 | 已有证据入口 | 仍必做与复用边界 |
| --- | --- | --- |
| O01 | [旧包定位及原子VOID](GOAL_E02_SCAN_ADJUSTMENT_2026-10-11.md)、[Android定向](GOAL_O01_U01_ANDROID_ACCEPTANCE_2026-10-15.md)、后续E02保存反馈 | 主机/真实页面已有，候选核非末包、窗口外、队列/冻结竞争、尾写与保留原行。VOID不能代装载后主管处置 |
| O02 | E03应急导出、product-read、rescue-request-product、supervisor-android四报告与电脑工具（绑定清单列完整路径） | 合成/本地可隔离身份正向及文件故障已验；候选断网/一致快照/各来源与未决状态、外置包实际回读/电脑转换组合继续，真实USB/MTP/OTG通道C档 |
| O03 | E05 O03、E07中转625 | 原批次/专用权限/PC与原机幂等及回执有证据；候选结合旧资格撤回/恢复代次和原机消费。未冻结/独立包仅证据接收 |
| O04 | [本地90项](GOAL_O04_INDEPENDENT_COLLECTION_ACCEPTANCE_2026-10-15.md)、[多任务Web L1](GOAL_E06_MULTITASK_ASSOCIATION_WEB_L1_2026-10-14.md)、[1539事实补核](GOAL_E06_RETAINED_COMBINATION_AUDIT_2026-10-16.md)、647 | 多任务Web正式报告已补，不能继续称“缺报告”；1538停机347文件不变，禁止复用。1539实际Android及HTTP/表事实、Python/Excel460项补核不是E06 L2。Chrome1540阻断保留，剩余L2继续 |
| O05 | [副本工具/1541恢复](GOAL_E07_RESTORE_COPY_ACCEPTANCE_2026-10-14.md)、D14各故障截点 | 同机取回/验签/损坏/partial及实际恢复有证据，`externalStorageVerified=false`不改写；多来源/未保护增量/恢复组合须候选核，实际异地介质归C06 |
| U01 | 高频导航、E02草稿视口/搜索/数量/移除/重建、save-scan-ack实际反馈 | “作业/数据/我的”、首屏/320dp/键盘与按键按原报告范围复用；整套实际操作组合及生命周期继续，不能用静态预览通过 |
| U02 | [结单入口](GOAL_E08_SUBMISSION_ENTRY_L1_2026-10-16.md)、[原生反馈故障](GOAL_U04_NATIVE_FEEDBACK_L1_2026-10-17.md) | 接收/确认/待落盘/耐久保存文案及未知原ID查询有切片；真实HTTP/MySQL与客户端页面组合仍需候选实跑，不能把回放说成真实服务 |
| U03 | E03导出/救援、E06 SKU/审阅/实物、多任务Web、[SKU返回导航](GOAL_E08_SKU_NAVIGATION_L1_2026-10-16.md) | 格式/预检/待对账及分层核验有证据；受影响Android、真实文件往返、Web及恢复责任链组合继续，不以按钮或接收文件代完成 |
| U04 | 正式/TEMP明细四字号、辅助三页组合、独立原生队列、声音未就绪、原生反馈、FORMAL/TEMP广播及实际Activity四阶段报告 | 已验分页/筛选/定位/系统栏/IME/四字号和有界Activity生命周期按源SHA复用；整套业务/候选组合未完成。原生队列满/故障有证据，真机P50/P95/30分钟/实体键与操作员走查未验C档 |

UI重设计属于U01–U04的产品实现及真实交互标准，不另设“视觉静态通过”口径。上述布局/导航实现不能免除同一候选上的业务组合；也不因现场设备缺失而把可隔离页面/队列测试移出开发范围。

## 第8节逐门核对与失败约束

| 门 | 本次核对 |
| --- | --- |
| 1 全部D项证据 | 未满足：D04旧前缀、D16正常资格、剩余组合及D18等仍有开发未完成 |
| 2 各业务正向闭环 | 多条正向已有；整单正常目标与完整受影响组合未满足，不能仅用拒绝/收存代替 |
| 3 最终候选第7节 | 未满足；E09静态阶段不是L3 |
| 4 权限/旧数据/契约一致 | 分批有证据，候选仍须核；生产七类能力/TEMP归档/删除边界继续关闭 |
| 5 开发提交与产物 | 分批推送回执存在；88项继承改动保留，不能声称已全部整合或被忽略产物已异地保存 |
| 6 看板/验收索引/外部范围 | 本页补逐项映射；开发缺口不归C档，后续仍需最终交付链接 |
| 7 最终报告 | 未进入最终交付；C01–C08及原身份/原设备/实际规模/部署保持未验 |
| 8 最后自审 | 本次是中途盘点，不能代替最终候选完成度自审 |
| 9 O/U必做 | 各项已有开发切片，剩余组合/生命周期/最终候选未完成 |

Chrome1540 `blocked by policy`不换端口/包装重试；1522/1523、1043/A1.4、原native崩溃/恢复与本轮原生断言失败等原件和计数不清零。若有新材料/具体修复假设，先记录新增依据、旧指纹、直接依赖和一次性输入再入队。没有新依据不机械重复已验或失败链。

后续增量：[实际作业Activity生命周期](GOAL_D17_ACTIVITY_LIFECYCLE_COMBINATION_2026-10-17.md)已补FORMAL/TEMP四原生阶段及Python全表消费，原应用83表328行/491全部文件不变；[TEMP产品仓库675](GOAL_E06_TEMP_REPOSITORY_RESTORE_L1_2026-10-17.md)已补真实会话/仓库原ID恢复；[旧worker677](GOAL_D15_OLD_WORKER_COMPATIBILITY_L1_2026-10-17.md)已补指定格式新建→旧worker→旧/新消费；[R08导出套件680](GOAL_D11_R08_EXPORT_INTEGRATION_L1_2026-10-17.md)已补完整14项隔离执行；[D13 HTTP/Session 19444](GOAL_D13_RECOVERY_HTTP_SESSION_L1_2026-10-17.md)已补2项真实入口；[D13正常P5退役1564](GOAL_D13_NORMAL_RETIREMENT_2026-10-17.md)已补真实P5排空/进程证据保持/独立后继、Windows重命名拒绝围栏与HTTP/生产调度/导出有界L1，以及修改后完整普通回归。上表D13的“完整P5正常退役”具体单元按本报告增量收口，全部文件入口/错根错库/水位反例及整体组合仍未完成。各自输入/复用SHA见正式报告，不把新增报告混入原60份绑定清单，不再将这些具体单元列为缺证；整套组合/候选仍未完成。下一步逐项核D13–D16开发剩余及E06/E07/E08受影响组合，有确切缺口才新增，不重复旧恢复或无新依据失败链。
