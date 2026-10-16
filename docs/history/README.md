# 历史归档

- [E07 正式原批次恢复与D16原生结构边界](GOAL_E07_FORMAL_RESTORE_NATIVE_RETENTION_2026-10-16.md)：665实际Android/HTTP/MySQL恢复及独立Python通过，591原件不变；101项留存CLI回归和47项原生trigger精确保护通过，有效目标guard正确拒绝。失败保全，非E07 L2/D16整体/D18 L3。

- [E07 丢失成功回执的原批次恢复组合](GOAL_E07_LOST_SUCCESS_RESTORE_COMBINATION_2026-10-16.md)：664三阶段Android/真实HTTP/MySQL及Python通过；原待审Room→首响应丢失→关闭重开原ID重查成功，仅三表受控增量，611原件守恒。首轮消费者元数据断言失败保留并只读补核；非E07 L2/D18 L3。

- [E07 Android原批次与整库恢复组合](GOAL_E07_ANDROID_ARCHIVE_RESTORE_COMBINATION_2026-10-16.md)：663实际冻结/HTTP成功归档→完整SQL/文件恢复→新Android JVM消费原Room/原签通过；独立Python核179业务表/83本地表及599原件守恒。661/662失败保全，非E07整体L2或D18 L3。

- [D16 无关审计引用保留 L1](GOAL_D16_UNRELATED_AUDIT_L1_2026-10-16.md)：82项实际CLI/全表恢复、并发/锁后撤权/末DELETE回滚通过，两代原签审计及依赖完整保留；404case/37次真实合成提交独立核对，失败原件保留，仅L1开发备份，非D16整体/L2/L3。

- [E07 旧恢复客户端非空升级 L1](GOAL_E07_LEGACY_RECOVERY_UPGRADE_L1_2026-10-16.md)：20项主机测试、两阶段原生迁移/强停重启及独立Python通过，23旧表42行/原签与恢复屏障保持，75原件多次SHA一致；首轮消费者封装错误保留，仅L1开发备份，非E07 L2/D18 L3。

- [D16 其他订单防重保留 L1](GOAL_D16_UNRELATED_GUARD_L1_2026-10-16.md)：49项实际CLI/恢复/并发/撤权/回滚通过，无关防重链完整保留、目标guard继续拒绝；失败原件保留，D16整体及L2/L3未完成。

- [E07 644原回执与固定版本导出独立消费](GOAL_E07_644_RECEIPT_EXPORT_CONSUMER_2026-10-16.md)：92份RSA/6份Excel及原payload/审批交叉绑定通过，526原件多次SHA不变；不启动旧库，不计全表恢复或E07 L2/D18 L3。

- [E08 独立口令标签与键盘布局 L1](GOAL_E08_CREDENTIAL_LAYOUT_L1_2026-10-16.md)：11项/APK/lint、四字号直接页面交互通过，83表/304原件不变；完整SKU返回导航失败保留，仅布局开发备份，非U整体/L2/L3。

- [E08 更新页系统栏与大字 L1](GOAL_E08_UPDATE_LAYOUT_L1_2026-10-16.md)：29项/APK/lint、四字号实际导航与全表/原件保持通过，截图取证补正保留；仅页面开发备份，非E08/U整体或L2/L3。

- [E08 设置分组与连接诊断 L1](GOAL_E08_SETTINGS_DIAGNOSIS_L1_2026-10-16.md)：6项定向、APK/lint、四档字号实际交互及全表/原件保持通过，滚动和取证失败保留；仅开发切片，不计E08/U整体或L2/L3。

- [D16合成隔离整单消费者 L1](GOAL_D16_WHOLE_ORDER_L1_2026-10-16.md)：实际CLI整单删除/备份恢复、27项定向、并发/锁后撤权/末DELETE回滚通过，仅guard-free初轮FORMAL合成副本；正常链及复杂引用/D13–D15组合仍未完成，非D16整体或L2/L3。

- [E08 FORMAL/TEMP结单入口 L1](GOAL_E08_SUBMISSION_ENTRY_L1_2026-10-16.md)：TEMP原批次分段补验、FORMAL新隔离副本原签回执及强停重启、独立Python和APK/lint通过；首轮失败及HTTP跳过保留，仅L1开发备份，不计E08/U整体、L2/L3或总目标。

- [E02 保存扫码分段接续与失败确认反馈 L1](GOAL_E02_SAVE_SCAN_ACK_L1_2026-10-16.md)：原身份续备、原pending接续、改后原订单确认反馈、18项定向/APK/lint及独立Python通过；历史失败与binlog取证补正保留，仅L1开发备份，不计U整体/L2/L3或总目标。

- [E02 草稿视口与页面编辑 L1](GOAL_E02_DRAFT_VIEWPORT_EDITOR_L1_2026-10-16.md)：真实触摸与页面编辑/重建/丢弃通过，83表守恒，build10定向11项及APK/lint通过；旧失败保留，仅L1开发备份。

- [E07 撤回恢复受影响组合验收（654–657）](GOAL_E07_WITHDRAWAL_RECOVERY_COMBINATION_654_ACCEPTANCE_2026-10-16.md)：654/655 原结果保留并明示原输入缺口；656 新隔离五截点真实 HTTP/MySQL 1/0/0/0，5301 项输入未变，657 只读消费 1362 项证据双次守恒，认证停机。外层 UTF-8 取证错误保留并只读补核。仅 E07 受影响组合开发验收，不计 E07 L2、D18 L3、第 8 节闸门或总目标。

- [E07/O04 独立采集检查点 L1（642）](GOAL_E07_O04_INDEPENDENT_CHECKPOINT_2026-10-16.md)：641 隔离恢复切片由独立只读 Python 消费，双次 854 文件清单、SHA/长度、七组 HTTP 响应、179 表/622 行事实与文件守恒均通过；首轮附件长度/库存前置解析失败原件保留。仅 L1 开发验收，不计 E07 L2、D18 L3 或总目标。

- [E07 受影响组合验收（647）](GOAL_E07_COMBINATION_647_ACCEPTANCE_2026-10-16.md)：新隔离实例 647 的真实 HTTP/MySQL、独立检查点恢复、非空迁移、179 表/622 行与 859 文件双次守恒、7 组 HTTP 200/OK、独立只读消费者及认证停机通过；645/646 运行器预检失败原件保留。仅组合开发验收，不计 E07 L2、D18 L3 或总目标。

- [E07/O03原件中转跨截点组合](GOAL_E07_RELAY_CHECKPOINT_ACCEPTANCE_2026-10-15.md)：625真实HTTP/MySQL 1/1、原包/两原签/同键并发与前缀拒绝、独立Python及540表/830文件保全通过；消费者登录审计误判原件保留。仅开发备份，非E07 L2/D14整体/D18 L3。

- [E07更正前截点不退库及原binlog重建](GOAL_E07_RETURN_CHECKPOINT_ACCEPTANCE_2026-10-15.md)：624前缀拒绝/五原签重放/default-off与540表保全通过；623 CSRF夹具原失败保留。仅开发备份，非E07 L2/D18 L3。

- [E07独立退库审批与新周期HTTP组合](GOAL_E07_RETURNED_STOCK_HTTP_ACCEPTANCE_2026-10-15.md)：622真实HTTP/MySQL、180表/32份RSA/3项引用/新旧周期独立消费及507文件认证停机保全通过；仅组合开发备份，不计E07 L2/D18 L3。

- [E07更正账务、缺记账与退库候选组合](GOAL_E07_RETURNED_STOCK_COMBINATION_ACCEPTANCE_2026-10-15.md)：621真实HTTP/MySQL、180表/103份RSA/补记账与冲正独立消费及674文件保全通过；原消费者失败保留，仅局部组合开发备份。

- [E07更正身份与两阶段撤回组合](GOAL_E07_IDENTITY_COMBINATION_ACCEPTANCE_2026-10-15.md)：新620真实HTTP/MySQL、独立Python和180表保全通过；619运行器与消费者原失败保留，仅组合开发备份。

- [E07撤回五检查点恢复](GOAL_E07_WITHDRAWAL_CHECKPOINT_ACCEPTANCE_2026-10-15.md)：新616五检查点真实恢复/HTTP重放1/1、独立Python/原RSA回执消费及617六库保全通过；613/614/615原失败与系统重启事实保留。仅开发备份，E07 L2/D14整体/D18 L3未完成。

- [E07原检查点实物与运维组合](GOAL_E07_PHYSICAL_OPERATIONS_ACCEPTANCE_2026-10-15.md)：新611实物与独立JVM重放、612运维各1/1，非空升级、Node/Python消费及两库181表保全通过；414/416停机文件不变。仅开发备份，E07 L2/D14-D15整体/D18 L3未完成。

- [E07全局编号历史与只读恢复对账](GOAL_E07_NUMBER_RECONCILIATION_ACCEPTANCE_2026-10-15.md)：新610真实HTTP/MySQL 1/1、相关回归54/54、Node/Python独立摘要/成员消费、非空升级与181表保全通过；609产品失败及消费者/预检原失败保留。仅开发切片，非E07 L2/D14整体/D18 L3。

- [D盘第三轮清理](D_DISK_ROUND3_CLEANUP_2026-10-15.md)：独立清理聊天已完成并释放范围；源码/配置及继承改动保护回执保留，共享写权归开发聊天。

- [E07历史覆盖与恢复后非空升级](GOAL_E07_HISTORY_COVERAGE_ACCEPTANCE_2026-10-15.md)：新608实际HTTP/MySQL 1/1、独立历史/137表成员消费者及全表/文件守恒；606/607失败保全，原输入5255项和来源393项不变。仅开发切片，不计E07 L2/D14整体/D18 L3。

- [E07 恢复归档消费与主管故障围栏](GOAL_E07_RECEIVED_ARCHIVE_FAULT_ACCEPTANCE_2026-10-15.md)：604/605 两个新隔离方法各 1/1 通过，原字节与数据库守恒，普通写入和删除边界保持关闭；不计 E07 L2/D18 L3。

- [E07 原检查点/binlog 重建开发切片](GOAL_E07_CHECKPOINT_REBUILD_ACCEPTANCE_2026-10-15.md)：新603实际重建、精确触发器/全表/文件、三项写前拒绝及认证停机；601/602原失败保留，不计E07 L2/D18 L3。

- [P07 显式原签还原 L1](GOAL_P07_RESTORE_ACCEPTANCE_2026-10-15.md)：最终实际IT38/38、Java输入不变的33项复用、JAR成功；布尔/整数误接受已复现修复，原失败/真实还原中止/末替换失败原件保留。仅工具切片，UNKNOWN/pending及E07组合仍未完成。

- [P07 原签全集封存 L1](GOAL_P07_INVENTORY_ACCEPTANCE_2026-10-15.md)：首次实际IT21/21、定向33/33；回读自审后Python供应5/5，其他输入按hash复用。真实中止/双侧缺件/旧备份边界通过，显式恢复未完成。

- [P07 中止诊断与启动拒绝 L1](GOAL_P07_INTERRUPTION_ACCEPTANCE_2026-10-15.md)：最终专项 IT 10/10、CLI/锁回归25/25；按产品输入哈希复用2005项及供应4项。真实中止与全部失败原件保留，显式可信恢复仍未完成。

- [可重建开发产物清理](D_DISK_REBUILDABLE_CLEANUP_2026-10-15.md)：第二轮16目录、22,858文件；保护原件核验与范围释放回执独立保存。

- [D15 身份绑定任务监控 L1](GOAL_D15_JOBS_MONITOR_ACCEPTANCE_2026-10-15.md)：真实 MySQL/CLI 15/15、配置/ACL 4/4；原失败/隔离库保留，仅工具切片。

- [D盘开发产物清理](D_DISK_CLEANUP_2026-10-15.md)：清理聊天独立回执，12冷AVD无损压缩、57可重提取DLL删除；原件/失败库/继承改动保持，写权已释放。

- [P07 外部秘密供应与停机重锚 L1](GOAL_P07_OFFLINE_REKEY_ACCEPTANCE_2026-10-15.md)：40/40 专项/回归，包装改动后实际 Python 产品入口 4/4、JAR 构建通过；原件/失败保留，D13/E07/L3 其余必做继续。

- [U01 共享扫码布局依赖开发备份](GOAL_U01_SCAN_DEPENDENCY_2026-10-15.md)：精确暂存树 23/23、Debug APK/lint 通过，补齐此前遗漏依赖；设备入口未验，不计完整 L1、U01/U02、E06 L2 或 D18 L3。

- [O01/U01 Android 交互定向验收](GOAL_O01_U01_ANDROID_ACCEPTANCE_2026-10-15.md)：工作树定向单测 26/26、lint 与 Debug APK 通过；测试清单及提交依赖补核，仍仅开发证据。

- [D10 故障撤回/重发真实 HTTP 验收](GOAL_D10_FAULT_WITHDRAWAL_HTTP_ACCEPTANCE_2026-10-15.md)：隔离 MySQL 上 FaultWithdrawal/FaultRestart 的真实 API、HTTP 和消费者夹具 15/15 通过，附件单测 17/17，Web typecheck/contract/build 通过；失败 dump 权限与成功 `--no-tablespaces` 重试均保留。仅收口当前 D10 切片，不计 E06 L2、D18 L3 或总目标。

- [E06 L2 Android 验收（未完成）](GOAL_E06_L2_ACCEPTANCE_2026-10-15.md)：当前 HEAD 下短 Java 临时路径独立 Android/Room 测试 90/0/0/0，Debug 与 AndroidTest APK、lint 成功；深路径 31 项同根因 SQLite 打开失败保全，未执行 instrumentation，1540 Chrome 受运行器审批阻断。仅 Android 开发证据，E06 L2 整体、D18 L3 与总目标继续。

- [O04 独立采集验收](GOAL_O04_INDEPENDENT_COLLECTION_ACCEPTANCE_2026-10-15.md)：短路径 Java 临时目录下 90 项独立 Android/Room/救援单测 0 失败、0 错误、1 个既有跳过；真实历史服务端凭据向量与独立加密包输出已绑定。仅收口 O04 开发切片，不计 E06 L2、U03 完整人工关联、D18 L3 或总目标。

- [E08 UI 后续短路径复验](GOAL_E08_UI_FOLLOWUP_ACCEPTANCE_2026-10-15.md)：恢复卡片读取 2/2、恢复确认重试 1/1，在 D 盘 Java 临时目录下共 3/3 通过；输入快照、命令和 Gradle 日志保留。仅定向开发复验，长路径失败、真机/U01–U04、E06 L2、D18 L3 与总目标继续。

- [E08 UI 定向验收](GOAL_E08_UI_TARGETED_ACCEPTANCE_2026-10-15.md)：六个 UI/恢复相关主机测试共 13/13 通过，lint 成功；输入、缓存失败与重试、监听和磁盘证据均保留。仅开发定向证据，不计 U01–U04、E06 L2、D18 L3 或总目标。

- [E09 Web 候选阶段验收](GOAL_E09_WEB_CANDIDATE_ACCEPTANCE_2026-10-15.md)：typecheck、契约校验、Web 构建及临时审查 7/7 通过，输入哈希与工作树状态可追溯；浏览器、服务端/数据库、文件、恢复组合仍未验，仅阶段候选成果。

- [E08 页面与作业体验开发验收](GOAL_E08_UI_ACCEPTANCE_2026-10-15.md)：Work/Submission/Temporary/Handover 重设计与恢复确认交互、lint/debug/staging 构建及 13 项主机测试（11 通过、2 项 Robolectric SQLite 打开失败）；仅 UI 开发备份，真机/长时/E06 L2/D18 L3/总目标继续。

- [E06受影响组合补验与未完成边界](GOAL_E06_COMBINATION_PARTIAL_2026-10-14.md)：原AVD/SAF、真实HTTP/MySQL、非空与独立消费已执行；Chrome预览自动审批阻断，L2未完成；[组合设计](GOAL_E06_COMBINATION_DESIGN_2026-10-14.md)。

- [E06多任务人工关联网页L1](GOAL_E06_MULTITASK_ASSOCIATION_WEB_L1_2026-10-14.md)：实际Chrome、主/成员回读、消费者和精确全表增量；[设计](GOAL_E06_MULTITASK_ASSOCIATION_WEB_DESIGN_2026-10-14.md)，E06组合继续。

- [E06短装批准网页L1](GOAL_E06_ASSOCIATION_SHORTFALL_WEB_L1_2026-10-14.md)：显式批准、实际Chrome双页/丢响应原键与全表守恒；[设计](GOAL_E06_ASSOCIATION_SHORTFALL_WEB_DESIGN_2026-10-14.md)，多任务与组合继续。

- [E06人工关联短装批准服务L1](GOAL_E06_ASSOCIATION_SHORTFALL_L1_2026-10-14.md)：原/目标两差额明确批准，真实正向/默认关/事务/固定钥与全表守恒；[设计](GOAL_E06_ASSOCIATION_SHORTFALL_DESIGN_2026-10-14.md)，网页与组合继续。

- [E06人工关联消费完整冲突决定L1](GOAL_E06_CONFLICT_ASSOCIATION_L1_2026-10-14.md)：真实正向、双开关/权限/事务/原键和固定钥/新JVM守恒；[方案](GOAL_E06_CONFLICT_ASSOCIATION_DESIGN_2026-10-14.md)，网页及组合继续。

- [E06跨机实物冲突服务L1](GOAL_E06_CONFLICT_L1_2026-10-14.md)：真实决定正向/事务/权限/不可变与固定外部钥消费；[方案](GOAL_E06_CONFLICT_DESIGN_2026-10-14.md)，网页/关联消费及组合继续，非完整E06通过。

- [E06人工关联网页L1](GOAL_E06_ASSOCIATION_WEB_L1_2026-10-14.md)：真实Chrome完整分页/双页原键/丢响应与空本地存储读回、数据库守恒；[方案](GOAL_E06_ASSOCIATION_WEB_DESIGN_2026-10-14.md)，冲突处置与组合继续，非完整E06通过。

- [E06人工关联服务端首切片L1](GOAL_E06_ASSOCIATION_L1_2026-10-14.md)：真实正向/事务/权限/原键与固定钥消费；[方案](GOAL_E06_ASSOCIATION_DESIGN_2026-10-14.md)，网页及跨机冲突处置继续，非完整E06通过。

- [E06实物登记限定补验](GOAL_E06_PHYSICAL_ACCEPTANCE_2026-10-14.md)：新副本严格非空/default-off/组合守恒及末SQL/并发/锁后撤权；[SQL证据/JDBC修复](GOAL_E06_SQL_REPAIR_2026-10-14.md)，原失败保全且计数继承，人工关联和冲突继续。

- [E06主管完整核对依据](GOAL_E06_RECONCILIATION_READ_ACCEPTANCE_2026-10-14.md)：完整候选、库存和已接收原件摘要/分页、未知钥/库存变化/资源上限及真实页面；[设计](GOAL_E06_RECONCILIATION_READ_DESIGN_2026-10-14.md)，实物与人工关联继续。

- [E06独立任务完整结束边界](GOAL_E06_CLOSURE_ACCEPTANCE_2026-10-14.md)：原机原签名封存/真实SAF、固定钥与电脑/HTTP/新JVM核验、最终回归及封存替换反例；[设计](GOAL_E06_CLOSURE_DESIGN_2026-10-14.md)，实物、完整候选与人工关联继续。

- [E06独立原件与主管核原授权](GOAL_E06_REVIEW_ACCEPTANCE_2026-10-14.md)：实际Android原包/非空恢复/双权限核原签发、固定原件新JVM及旧中转受影响组合；[设计](GOAL_E06_REVIEW_DESIGN_2026-10-14.md)，结束边界/实物与人工关联继续。

- [E06独立采集只读应急导出](GOAL_E06_INDEPENDENT_EXPORT_2026-10-14.md)：原任务实际SAF保存/注销新进程/文件失败、包外登记根及电脑逐值核验、原非空库守恒；主管对账继续。

- [E06独立SCAN/VOID与三来源防重](GOAL_E06_SCAN_VOID_2026-10-14.md)：原任务扫码/实物VOID、串行队列及双向防重，原机/独立消费/回归通过；导出和主管对账继续。

- [E06本地任务计划](GOAL_E06_LOCAL_TASK_2026-10-13.md)：原请求/资料/签名身份固定、原子计划审计及离线原机页面/恢复；扫码和对账继续。

- [E06独立sku资料切片](GOAL_E06_SKU_CATALOG_2026-10-13.md)：原字节/预检/原子启用，实际SAF/新进程/旧表守恒；剩余独立采集继续。

- [O03受控原件中转验收](GOAL_E05_O03_ACCEPTANCE_2026-10-13.md)：原未上传批次电脑正向、原机文件消费/新进程、规范原键跨JVM及受影响组合；[中间断点](GOAL_E05_O03_PROGRESS_2026-10-13.md)与原失败保留。

- [E05净值导出切片收口与新会话接续](D01_D18_HANDOFF_2026-10-13_E05_NET_CLOSE.md)：本切片验收、继承改动快照、预览保留、写权释放和O03后续指令；总目标未完成。
- [E05净值汇总验收](GOAL_E05_NET_SUMMARY_2026-10-13.md)与[重复产物清理](D_DISK_CLEANUP_2026-10-13_E05.md)：真实HTTP/文件/页面/非空组合和原失败分别保全；清理仅可原字节恢复的闲置重复JAR。

- [E04/P06 FORMAL原实物处置解隔离](GOAL_E04_P06_FORMAL_QUARANTINE_2026-10-13.md)：原批准字节与恢复/STOP/续作/应用签名、实物附件及独立批准，真实HTTP/原副本非空/Web/Chrome/新JVM/固定钥和组合通过；失败保留，未关闭来源与历史贡献继续。

- [E04/P06 PC原处置解隔离](GOAL_E04_P06_PC_QUARANTINE_2026-10-13.md)：原数据库审批/应用与逐包摘要，真实实物附件、独立批准、HTTP/非空兼容/Web/Chrome/新JVM/固定钥和受影响组合通过；失败保留，其余来源及总目标继续。

- [E04/P06已关闭TEMP实物解隔离](GOAL_E04_P06_QUARANTINE_2026-10-13.md)：原签名/上游/owner与实物附件固定、独立批准及原键恢复，原非空HTTP、最终Web/Chrome、固定钥/SQL、新JVM和受影响组合通过；其余来源与总目标继续。

- [E04/P06返库库存账恢复](GOAL_E04_P06_RETURNED_STOCK_2026-10-13.md)：原退货/错误出库贡献固定、独立实物核验与主管审批后恢复库存账投影；真实HTTP、非空迁移、组合分页、Web/签名/视觉复核通过。真正解隔离与其余总目标继续。

- [E04/P06缺账逐笔补扣](GOAL_E04_P06_RECONCILIATION_2026-10-13.md)：实际证据/独立审批、真实HTTP反向账务与原键、Web/独立签名SQL、新JVM/非空历史兼容；失败保全，其他库存差异与总目标继续。

- [E04/P04原机产品闭环与历史兼容](GOAL_E04_P04_NATIVE_PRODUCT_2026-10-12.md)：原Keystore页面上传、自行取得可信映射/验签、新进程及52项组合；原历史LINK恢复消费与原机SQL守恒，P04子项收口，失败保全和其余总目标继续。

- [D01–D18阶段收口与新会话断点](D01_D18_HANDOFF_2026-10-12_P04.md)：P04开发阶段备份、验证边界、写权/预览环境和磁盘管理；总目标仍未完成。

- [E04候选逐项排除（阶段备份）](GOAL_E04_CANDIDATE_EXCLUSION_2026-10-12.md)：主管签名排除与selection消费、服务完整回归、Chrome/HTTP及Android原件消费证据；后续客户端闭环和非空兼容补证见上项，最终候选组合未验，失败原件保全。

- [E04普通本机零作业取消](GOAL_E04_ZERO_DRAFT_CANCEL_2026-10-12.md)：保留原事实的取消、队列/Room/新进程产品及受影响回归；[前看板](ORCHESTRATION_STATE_before_e04_cancel_2026-10-12.md)和失败原件保留，非零取消/P04/P06继续。

- [E03 FORMAL、规模与中断](GOAL_E03_FORMAL_SCALE_2026-10-12.md)：原Keystore停服/断网产品、电脑原件/签名/表格和新进程未完成保全；[前看板](ORCHESTRATION_STATE_before_e03_formal_2026-10-12.md)及原失败保留，后续E04–E09继续。

- [E03 Web主管只读救援产品](GOAL_E03_RESCUE_WEB_2026-10-12.md)：实际Chrome/HTTP审批撤销与原键恢复、独立签名及数据库守恒；完整O02仍未完成。[前看板](ORCHESTRATION_STATE_before_e03_web_2026-10-12.md)保留原断点。

- [E03原人耐久救援申请产品](GOAL_E03_RESCUE_REQUEST_PRODUCT_2026-10-12.md)：先落盘、原键新进程实际HTTP与签名消费、身份/停服拒绝保全；[前看板](ORCHESTRATION_STATE_before_e03_request_2026-10-12.md)保留原断点。

- [E03 Android主管只读救援消费](GOAL_E03_SUPERVISOR_ANDROID_2026-10-12.md)：预置/注销/停服原单导出及耐久撤销、真实HTTP与独立电脑消费；原人申请/Web及完整O02继续。前看板见[主管Android前件](ORCHESTRATION_STATE_before_e03_supervisor_android_2026-10-12.md)。

- [E03主管预授权救援](GOAL_E03_DELEGATED_RESCUE_2026-10-12.md)：来源协议、实际服务链/HTTP/事务及非空升级定向证据，失败/漂移原件保留；Android主管预置与跨人产品继续。

- [E03本人只读救援产品切片](GOAL_E03_PRODUCT_READ_2026-10-12.md)：真实预置/停服TEMP原件/SAF、电脑登记核验与新进程取消；主管/规模继续，未计完整O02。前看板见[产品前](ORCHESTRATION_STATE_before_e03_product_2026-10-12.md)、[前驱前](ORCHESTRATION_STATE_before_e03_prefix_2026-10-12.md)、[本人交付前](ORCHESTRATION_STATE_before_e03_own_delivery_2026-10-12.md)。

- [E03应急包格式与电脑核验](GOAL_E03_EMERGENCY_EXPORT_2026-10-11.md)：合成跨运行库验证、原件/处置守恒、失败partial保留及Excel独立消费；产品接线继续。前件看板见[格式自审前](ORCHESTRATION_STATE_before_e03_format_review_2026-10-11.md)。

- [E02高频首页与失败队列](GOAL_E02_HIGH_FREQUENCY_2026-10-11.md)：已验首页切片、首屏/草稿暂缓原因及原件位置，总目标继续。
- [总目标E00证据映射](GOAL_E00_BASELINE_2026-10-11.md)与[E01故障撤回/独立重发验收](GOAL_E01_RECALL_REPUBLISH_2026-10-11.md)：单写者保全、真实HTTP/并发/尾写回滚、Web持久原键、Android采集验签/重启及非空升级；总目标仍active。
- E01看板前件：[启动前](ORCHESTRATION_STATE_before_goal_e01_2026-10-11.md)、[重启补证前](ORCHESTRATION_STATE_before_goal_e01_restart_2026-10-11.md)。历史不能恢复已取消换机承接。
- [E02已扫码包调整](GOAL_E02_SCAN_ADJUSTMENT_2026-10-11.md)与[E02交付前看板](ORCHESTRATION_STATE_before_goal_e02_delivery_2026-10-11.md)：正式/TEMP原行事务、主管/身份/冻结保护、定位页面、真实HTTP/模拟器及停服正向；高频优化和其余总目标继续。

- [十步完成度统计与无人值守计划修订依据（2026-10-11）](TEN_STEP_COMPLETION_AUDIT_2026-10-11.md)：逐包计分/证据、授权下的默认设计、同根因失败暂缓及批次安排；仅计划制定，没有启动业务开发。
- [制定无人值守计划前的看板原件](ORCHESTRATION_STATE_before_unattended_plan_2026-10-11.md)：原Git/写权/失败/未验保全，旧方案等待不恢复。

- [PDA断网补救与UI/UX分析纳入计划（2026-10-11）](PDA_OFFLINE_UI_PLAN_2026-10-11.md)：两份报告逐节对应、O/U完成标准、待定政策与撤回重发边界；仅文档更新。
- [纳入两份报告前的看板原件](ORCHESTRATION_STATE_before_offline_ui_plan_2026-10-11.md)：原输入/写权/失败/未验保全，不恢复旧接续指令。

- [R07取消换机接续，改为故障订单撤回重发（2026-10-11）](R07_RECALL_REPUBLISH_SCOPE_2026-10-11.md)：用户范围替换、旧测试安全停机与写权交接、文档更新及新方案验收边界；旧换机继续指令不恢复。
- 修订前原件：[看板](ORCHESTRATION_STATE_before_recall_republish_2026-10-11.md)、[R07政策](R07_POLICY_before_recall_republish_2026-10-11.md)、[总目标计划](AUTONOMOUS_PLAN_before_recall_republish_2026-10-11.md)。仅作历史，取消不计实现通过。

- [D07/R09限定恢复Android耐久接收确认（2026-10-11）](D07_R09_RECOVERED_ANDROID_CONFIRMATION_2026-10-11.md)：实际POST/native Room/原键、两Android JVM与独立消费；页面/激活/最终提交继续。
- [Android确认开发前看板](ORCHESTRATION_STATE_before_confirmation_android_2026-10-11.md)与[收口前看板](ORCHESTRATION_STATE_before_confirmation_android_close_2026-10-11.md)：原结论和失败保全，旧指令不恢复。

- [D07/R09限定恢复接收确认产品HTTP（2026-10-11）](D07_R09_RECOVERED_CONFIRMATION_HTTP_2026-10-11.md)：真实POST/首次竞争/Socket丢正文、专用签名与两JVM原回执、守恒/独立消费；Android及激活链继续。
- [产品HTTP确认前看板](ORCHESTRATION_STATE_before_confirmation_http_2026-10-11.md)：原结论/失败保全，旧指令不恢复。

- [D07/R09限定恢复接收确认服务（2026-10-11）](D07_R09_RECOVERED_CONFIRMATION_2026-10-11.md)：专用RS256/原键、新JVM原回执、实际MySQL与原表守恒/独立消费；HTTP/Android和激活链继续。

- [D07/R09限定恢复Android原件暂存（2026-10-11）](D07_R09_RECOVERED_ANDROID_STAGE_2026-10-11.md)：同一LOGIN/HTTP/native Room/新JVM、原表守恒与独立消费；确认/激活/最终提交继续。

- [D07/R09恢复本人产品HTTP下载（2026-10-11）](D07_R09_RECOVERED_DOWNLOAD_HTTP_2026-10-11.md)：固定服务端信任、原Bearer/字节、HTTP并发/权限反例及新JVM；Android/确认/激活/最终提交继续。

- [D07/R09恢复本人原正文读取（2026-10-11）](D07_R09_RECOVERED_DOWNLOAD_2026-10-11.md)：原manifest/正文、签名清单、全原表/并发/尾回滚/新JVM及独立消费；产品HTTP/Android链继续。
- [原正文读取收口前看板](ORCHESTRATION_STATE_before_download_close_2026-10-11.md)：原结论和失败保留，旧指令不恢复。

- [D07/R09恢复指定接收者本人会话准入（2026-10-11）](D07_R09_RECIPIENT_SESSION_2026-10-11.md)：真实HTTP认证、精确签名登录效果/全原表守恒、尾回滚、新JVM原token及独立消费；下载确认/激活/最终提交继续。
- [本人会话开发前看板](ORCHESTRATION_STATE_before_receiving_session_2026-10-11.md)及[收口前看板](ORCHESTRATION_STATE_before_session_close_2026-10-11.md)：原结论和失败保留，旧指令不恢复。

- [D07/R09恢复交接PREPARE（2026-10-11）](D07_R09_RECOVERED_PREPARATION_2026-10-11.md)：限定资格准备、精确签名效果与原全表守恒，实际MySQL/新JVM/独立消费者；下载确认/激活/最终提交仍待接续。
- [恢复准备前看板](ORCHESTRATION_STATE_before_received_prepare_2026-10-11.md)：替换前结论及失败保留，旧指令不恢复。

- [D07/R09本代次恢复交接新资格（2026-10-11）](D07_R09_RECIPIENT_QUALIFICATION_2026-10-11.md)：独立签名限定资格、全原表守恒、实际MySQL/新JVM/独立消费与受影响回归；准备/激活/最终提交仍需继续。
- [新资格收口前看板](ORCHESTRATION_STATE_before_qualification_close_2026-10-11.md)：原结论/失败与在途接续保留。

- [D07/R09恢复态指定接收者准入（2026-10-11）](D07_R09_RECIPIENT_ADMISSION_2026-10-11.md)：可信登记、全原表/精确签名效果、实际MySQL反例/新JVM/独立消费；新资格及最终链未完成。
- [指定接收者准入前看板（2026-10-11）](ORCHESTRATION_STATE_before_recipient_resume_2026-10-11.md)与[准入收口前看板](ORCHESTRATION_STATE_before_recipient_close_2026-10-11.md)：原结论/在途断点保全，旧指令不恢复。

- [范围双侧提交后的收口与新会话指令（2026-10-11）](D01_D18_PAUSE_2026-10-11_SCOPE_RELEASE.md)：提交/最终快照、无在途任务、保全及接收者/目标代次约束；总目标暂停未完成。
- [范围双侧协议收口前看板（2026-10-11）](ORCHESTRATION_STATE_before_scope_release_pause_2026-10-11.md)：替换前结论/失败保全，不恢复旧指令。
- [D07/R09已核范围双侧提交（2026-10-11）](D07_R09_SCOPE_RELEASE_2026-10-11.md)：非空迁移守恒、两侧确认、真实halt/新JVM和丢确认围栏、独立消费；不代新资格/最终交接。
- [范围双侧提交前看板（2026-10-11）](ORCHESTRATION_STATE_before_scope_release_2026-10-11.md)：原结论与失败保全，不恢复旧指令。

- [D07/R09独立运维批准核验（2026-10-10）](D07_R09_OPERATIONS_APPROVAL_2026-10-10.md)：批准后固定范围、实际闭事务和尾部物料回滚、独立消费；不代两阶段释放。
- [运维批准前看板（2026-10-10）](ORCHESTRATION_STATE_before_operations_approval_2026-10-10.md)：旧结论/失败保全，不恢复旧指令。

- [D07/R09恢复态主管实物确认（2026-10-10）](D07_R09_PHYSICAL_REVIEW_2026-10-10.md)：原事实/精确APPROVE效果、实际MySQL回滚、跨JVM重放和独立消费；运维与两阶段释放仍待接续。
- [实物确认前看板（2026-10-10）](ORCHESTRATION_STATE_before_physical_review_2026-10-10.md)：旧结论/失败保全，不恢复旧指令。

- [D07/R09原终点与主管围栏效果核对（2026-10-10）](D07_R09_RELEASE_RECONCILIATION_2026-10-10.md)：原事实严格保留、精确已签命令效果、实际MySQL尾写回滚及独立消费；实物/运维审批及释放仍缺。
- [原终点核对前看板（2026-10-10）](ORCHESTRATION_STATE_before_release_reconciliation_2026-10-10.md)：旧结论/失败保全，不恢复旧指令。

- [D07/R09固定仓库及子表覆盖（2026-10-10）](D07_R09_RELEASE_SCOPE_COVERAGE_2026-10-10.md)：准备/基线/PC/同步/证据子表完整成员与尾写回滚、独立消费者；业务语义和释放仍缺。
- [子表覆盖前看板（2026-10-10）](ORCHESTRATION_STATE_before_release_scope_2026-10-10.md)：旧结论/失败保全，不恢复旧指令。

- [D07/R09原全局编号历史核对（2026-10-10）](D07_R09_RELEASE_NUMBER_HISTORY_2026-10-10.md)：逐笔分配与两端/目标全局映射核对，实际MySQL拒绝/回滚、独立回读，原失败保留；不计范围释放。
- [全局编号核对前看板（2026-10-10）](ORCHESTRATION_STATE_before_release_number_2026-10-10.md)：旧结论/失败保全，不恢复旧指令。

- [D07/R09释放前原历史覆盖（2026-10-10）](D07_R09_RELEASE_HISTORY_2026-10-10.md)：原时间窗与固定清单/控制绑定、漏窗拒绝、实际恢复和独立回读；语义闭合和可信释放仍缺。
- [历史覆盖前看板（2026-10-10）](ORCHESTRATION_STATE_before_release_history_2026-10-10.md)：旧结论/失败保全，不恢复旧指令。

- [D07/R09主管围栏后实际范围清单（2026-10-10）](D07_R09_RELEASE_INVENTORY_2026-10-10.md)：原事实/签名围栏绑定、仓库清单与全局摘要、尾写回滚、容量拒绝及独立回读；可信释放和新资格尚未完成。
- [范围核对前看板（2026-10-10）](ORCHESTRATION_STATE_before_received_release_2026-10-10.md)：原结论/失败保全，不恢复旧指令。

- [D07/R09恢复态主管故障围栏（2026-10-10）](D07_R09_RECEIVED_FAULT_2026-10-10.md)：1250实际新目标、原事务/来源守恒、真实HTTP拒绝、反例/独立工具与失败保全，非范围释放或最终交接。
- [主管围栏后的暂停与接续（2026-10-10）](D01_D18_PAUSE_2026-10-10_RECOVERED_FAULT.md)：用户要求本步收口、实际Git/全部dirty/环境断点与原计划接续指令。
- [主管围栏前看板（2026-10-10）](ORCHESTRATION_STATE_before_received_fault_2026-10-10.md)：原结论/失败保全，不恢复旧指令。

- [D07/R09已收正文原归档closed/native消费（2026-10-10）](D07_R09_RECEIVED_ARCHIVE_2026-10-10.md)：实际新目标原字节/验证行/owner与回滚、独立消费和普通回归；不代业务准入或最终交接。
- [已收正文归档消费前看板（2026-10-10）](ORCHESTRATION_STATE_before_received_archive_2026-10-10.md)：原结论/失败保全，不恢复旧指令。

- [D07/R09已收正文实际时间截点（2026-10-10）](D07_R09_RECEIVED_CHECKPOINT_2026-10-10.md)：扫码前dump与原binlog全量重建，1233/1237及写前拒绝、独立原字节消费、普通回归和失败库分别保全；closed/native与最终交接仍缺。
- [真实截点前看板归档（2026-10-10）](ORCHESTRATION_STATE_before_received_checkpoint_2026-10-10.md)：原结论保全，不恢复旧状态或指令。

- [D07/R09已收正文原verification恢复（2026-10-10）](D07_R09_RECEIVED_VERIFICATION_RESTORE_2026-10-10.md)：新实际来源原行pin与单表恢复，原失败/独立消费分列，不代时间截点或业务闭环。
- [已收正文原行恢复前看板归档（2026-10-10）](ORCHESTRATION_STATE_before_received_verification_2026-10-10.md)：原结论/失败/限制保全，不恢复旧状态。

- [D07/R09 native verification原行绑定（2026-10-10）](D07_R09_NATIVE_VERIFICATION_BINDING_2026-10-10.md)：原完整行摘要/事务锁/篡改回滚；合成MySQL与真实恢复拒绝分列，非原native正向或完整交接链。
- [native绑定前看板归档（2026-10-10）](ORCHESTRATION_STATE_before_native_binding_2026-10-10.md)：原结论/失败/限制保全，不恢复旧指令或状态。

- [D07/R09恢复归档同事务消费（2026-10-10）](D07_R09_ARCHIVE_TRANSACTION_CONSUMER_2026-10-10.md)：消费者原子提交/回滚与原件前后核验，业务接入与native事实限制另列。
- [D07/R09恢复后原TEMP归档读取（2026-10-10）](D07_R09_RECOVERED_ARCHIVE_READ_2026-10-10.md)：closed重验完整恢复原列/文件并返回原字节及来源pin，不造native verification或准入。
- [D07/R09原E2A与占用跨截点恢复（2026-10-10）](D07_R09_TEMP_EVIDENCE_RESTORE_2026-10-10.md)：原四表闭事务恢复、并发库存缺陷修复、独立消费和成功目标重开，不代最终接收链。
- [四表恢复前看板归档（2026-10-10）](ORCHESTRATION_STATE_before_temp_evidence_restore_2026-10-10.md)：替换前结论保全，不恢复旧状态。

- [D07/R09原TEMP准备跨截点恢复（2026-10-10）](D07_R09_TEMP_PREPARATION_RESTORE_2026-10-10.md)：实际dump/新空目标、五原分片/ACK闭事务、失败保全、幂等/冲突/回滚与成功目标重启，非D07整体。
- [准备恢复前看板归档（2026-10-10）](ORCHESTRATION_STATE_before_temp_preparation_restore_2026-10-10.md)：原范围提交前结论保全，不恢复旧状态。

- [D07/R09受约束原端权限窗口（2026-10-10）](D07_R09_CONTROLLED_PERMISSION_2026-10-10.md)：旧特权会话排空、精确GRANT/后台写者、原日志独立pin；1170/1172/1173失败保全及1174真实MySQL/独立消费者分列，非业务恢复。
- [受约束原端前看板归档（2026-10-10）](ORCHESTRATION_STATE_before_controlled_permission_2026-10-10.md)：旧断点保全，不恢复旧状态。

- [D07/R09原权限截点与logged连续性（2026-10-10）](D07_R09_PERMISSION_LOG_BOUNDARIES_2026-10-10.md)：原FTWRL权限/微秒/binlog两边界及prefix、CRC/ROW/XID和修改还原拒绝；1166/1167失败保全、1169真实MySQL和独立消费者分列，未授权实际恢复。
- [权限连续性前看板归档（2026-10-10）](ORCHESTRATION_STATE_before_permission_continuity_2026-10-10.md)：旧结论和限制保全，不恢复当前状态或指令。

- [D07/R09原未收正文同来源组合（2026-10-10）](D07_R09_UNSUBMITTED_COMPOSITION_2026-10-10.md)：五原件/source pin与独立占用pin、E2A/ACK/资格/权限语义同来源重读；定向和recovery回归分列，连续权限历史与实际恢复仍缺。

- [D07/R09原未收正文占用与E2A绑定（2026-10-09）](D07_R09_UNSUBMITTED_OCCUPANCY_2026-10-09.md)：原无hash ABORTED、完整集合/对象引用及SOURCE_RESERVED独立pin消费；1162/1163失败保留、1164实际MySQL/Node分列，非权限历史或实际恢复。

- [D07/R09原TEMP准备与ACK采集（2026-10-09）](D07_R09_TEMP_PREPARATION_CAPTURE_2026-10-09.md)：原五gzip/三签名及纳秒command只读采集/独立pin，1161真实MySQL和Node逐列消费、受影响回归分列，权限历史/实际恢复仍缺。
- [D07/R09原提交占用观察（2026-10-09）](D07_R09_ORIGINAL_OCCUPANCY_2026-10-09.md)：原NORMAL实例只读占用/终态采集、独立pin离线消费，1158夹具失败保留、1159真实MySQL及独立逐列消费分列；非实际恢复。
- [D07/R09原设备登记根采集（2026-10-09）](D07_R09_DEVICE_REGISTRATION_2026-10-09.md)：故障前实际MySQL时间/实例与直接/配对公钥、独立pin读取；1156失败保全、1157与独立消费者/回归分列，实际恢复仍缺。
- [D07/R09原TEMP正文同一pin组合（2026-10-09）](D07_R09_NATIVE_COMPOSITION_2026-10-09.md)：原E2A与原准备/ACK/终态/权限/占用，冻结与STOP分界绑定；只读前置，实际恢复仍缺。
- [D07/R09原TEMP E2A字节与STOP核验（2026-10-09）](D07_R09_ORIGINAL_EVIDENCE_2026-10-09.md)：真实原编码、页链/水位/冻结与STOP分界，保留1151只读消费者；独立根保管与实际恢复仍缺。
- [D07/R09第一轮同一来源组合核验（2026-10-09）](D07_R09_INITIAL_COMPOSITION_2026-10-09.md)：同一pin原准备/ACK/资格/终态/审计/权限/占用、独立双水位与原capture迁移；只读前置，实际恢复仍缺。
- [D07/R09原FORMAL续期准备绑定前置（2026-10-09）](D07_R09_FORMAL_RENEWALS_2026-10-09.md)：完整原代次/前驱、首份准备不续期、逐原保存窗口；实际恢复仍缺。
- [D07/R09原FORMAL下载确认前置（2026-10-09）](D07_R09_FORMAL_ACTIVATION_2026-10-09.md)：库外pin、原审计/assignment与无签名ACK绑定；当次回归和保留原件缺失分列。
- [D07/R09原TEMP激活ACK前置（2026-10-09）](D07_R09_TEMP_ACTIVATION_2026-10-09.md)：原请求摘要、公钥回执与时间边界只读核验；控制、权限、占用和实际恢复仍缺。

- [D07/R09原准备包前置（2026-10-09）](D07_R09_PREPARATION_2026-10-09.md)：原签名/字节/库存周期/计划扫描基线；当次回归与原准备缺失分列，ACK与实际恢复仍缺。
- [D07/R09原TEMP基线前置（2026-10-09）](D07_R09_TEMP_BASELINE_2026-10-09.md)：原政策/规则/游标/映射/首轮计划含义只读校验；ACK与实际恢复仍缺。
- [D07/R09原占用与权限语义前置（2026-10-09）](D07_R09_SOURCE_STATE_2026-10-09.md)：完整owner列和原凭据版本/历史窗口，只读前置；当次回归、失败和保留资料缺口分列，非实际恢复通过。
- [D07/R09关闭恢复权威与事务前置（2026-10-09）](D07_R09_CLOSED_TRANSACTION_2026-10-09.md)：独立pin、耐久事务/独占租约与实例/投影验证；1152和1155通过、1153/1154失败保全、Python及普通server回归分列，原占用/权限和业务重建仍缺。

- [D07/R09最小可信来源读取层（2026-10-09）](D07_R09_TRUSTED_SOURCE_2026-10-09.md)：外部固定原件/控制范围、公钥验原回执/终态/原审计与逐保存时间资格；原激活/准备基线及受控恢复写入仍待实现，总目标active、未完成。
- [D盘空间恢复（2026-10-09）](D_DISK_RECOVERY_2026-10-09.md)：22份停止运行页面RAM快照逐hash可逆压缩，D盘不足解除，无文件删除、失败/唯一原件及继承dirty保留；开发目标继续暂停。

- [FORMAL子项后的暂停与接续（2026-10-09）](D01_D18_PAUSE_2026-10-09_FORMAL.md)：用户要求本步后收口；附件/绑定和实际PDA页面证据、失败与skip、全部dirty及环境快照、Git回执和可直接接续的指令。总目标暂停、未完成。
- [D07 FORMAL可信归档故障链（2026-10-09）](D07_FORMAL_FAULT_EXECUTION_2026-10-09.md)：可信归档/主管围栏、非空附件迁移、权限/绑定/坏原件反例、实际页面和最终记账/重启及独立工具；不外推D07整体。

- [D07 TEMP故障附件（2026-10-09）](D07_FAULT_ATTACHMENTS_2026-10-09.md)：实际原字节与原键、原预约/签名有序引用、缺件及损坏拒绝、接收最终链与关闭写门下载、页面恢复/布局和失败保全；不外推D07/D10整体。

- [D07 TEMP故障产品页面（2026-10-09）](D07_TEMP_FAULT_UI_2026-10-09.md)：真实表单/可信读面/接收最终链、未核明原键/旧存储及浏览器边界、布局和回归；页面子项收口，不计D07整体通过。

- [D07 TEMP故障可信归档链（2026-10-09）](D07_TEMP_FAULT_EXECUTION_2026-10-09.md)：原Android归档与独立主管围栏、接收最终提交/重启、非空迁移/SQL与Room回滚；开发范围记录，故障页面/FORMAL/R09缺口分列，不计D07整体通过。

- [D06 LOCAL独立冻结与前置拒绝（2026-10-09）](D06_LOCAL_EXECUTION_2026-10-09.md)：1124真实三轮LOCAL-only至最终成功、1125无canonical、REQUEST/APPROVE/PREPARE拒绝与独立消费者；失败/回归/当批产物分列，不计D06整体通过。

- [D01–D18 可控暂停断点（2026-10-08）](D01_D18_PAUSE_2026-10-08.md)：用户要求收口；TEMP专用STOP开发备份、1100–1102失败与保全、全部继承改动、环境及接续第一步；总目标未完成。
- [D06 STOP暂停前看板快照](ORCHESTRATION_STATE_before_d06_stop_pause_2026-10-08.md)：历史快照，接续以主看板及暂停记录为准。

- [B4 TEMP 续作票据收口节点（2026-10-06）](ORCHESTRATION_STATE_before_b4_continuation_checkpoint_2026-10-06.md)：本次看板替换前快照；下次接续入口见 `AUTONOMOUS_PLAN_2026-10-05_UNATTENDED.md` 收口节点章节和 `AUTONOMOUS_PROGRESS_2026-10-06.md` 第191条。

- [B4 TEMP 续作票据验证收口（2026-10-06）](ORCHESTRATION_STATE_before_b4_continuation_close_2026-10-06.md)：看板替换前断点；当日详细结果见 `AUTONOMOUS_PROGRESS_2026-10-06.md` 第190条。

- [本机资料归档位置](../LOCAL_ARCHIVE_LOCATIONS.md)：D 盘旧目录和历史测试资料的 E 盘位置；查找旧文档中引用的本机路径时先看此页。

- - [R06 A1.1 收口：停止保全 device 层实质通过（可重复性未建立）](R06_A1_1_2026-09-30.md)：三层 device 证据——**G4-2 成立**（指纹式非空迁移保留，但 `local_draft_stop=0` 判 EMPTY ⇒ 不算非空）、**G4-3 成立**（`verdict=NONEMPTY`，五张表 1/2/1/1/1 迁移前后逐字节相同）、**G1 单次通过**（四组断言＋身份切换三步，两阶段 `numtests=1` 真跑＋真 `am force-stop`）。**G1 可重复性未建立**：第二次在已 seed 设备上因测试不幂等（固定主键合成事件未先删）失败，已离线修复未重跑。未验边界与口径声明见文档；harness 硬化 5 项（含"空跑=失败"反向验证）。
[R06 A1.1 第 2 步：单测缺口 G2/G3 补齐（子步记录，非 A1.1 验收）](R06_A1_1_STEP2_UNIT_COVERAGE_2026-09-30.md)：新增两个测试类并**全绿**——`LocalDraftStopWriterTest` **4/0/0/0**（① 先拒新输入/耐久保存/**失败不签停止**，补齐既有 rollback 测试未断言的"无签名行"与"未入队"）、`LocalDraftSourceBindingMigrationTest` **1/0/0/0**（④ 非空迁移 **27→28**，唯一落在 LocalDraft 时代的未核段：stop/签名/plan/requests/receipt 逐项相等、**迁移不复活已暂停的自动续传**、新表建出为空、FK 干净、密钥不可用即不追补签名）。并发级如实标注：3 条**顺序交织模拟** + 1 条**真实 4 线程串行化/幂等探针**，**真实竞态与真机重启未验**（G1/G4 留第 3 步）。**"6 事件 vs 4 尝试"最终定性＝我的判据误用**（`LocalDraftStopDao.count(id)` 数 `local_draft_stop_item` **明细项**；`event()` 首行 `check(db.inTransaction())` ⇒ 与停止插入同原子；恢复严格断言"事件==1"后 4 并发通过）⇒ **无产品缺陷**；另修正 `stopSigner.calls`（两处自增，非签名次数判据）。未改产品代码、未跑 AVD、失败轮保留。

- [R09 C2 A2.1 裁定 B 实施：更正进门码按命令类型分离 ＋ maker-checker](R09_C2_A2_1_RULING_B_IMPLEMENTATION_2026-09-30.md)：按用户 2026-09-30 裁定 **B**（＋授权扩界）实施——`CorrectionCommand` 新增**唯一**判定 `requiresApproval(kind)` 并让 `Operation` 默认实现委托它；身份门改为 `current(warehouse, approval)`，**审批类只要求 `correction:approve`、其余仍要求 `outbound:correct`**；`CorrectionCommandService` 在 operation 解析前按类型裁决；`Approve.execute` **新增 maker-checker 守卫**（`principalId==requestedBy` ⇒ 新码 `CORRECTION_SELF_APPROVAL_DENIED`）。**明确标注：反例③ 是新增规则**（改前无此守卫；既有相近逻辑只有 `WITHDRAW_CONFIRM` 要求建档人本人确认，方向相反）。**三条反例实测**（复用 773 datadir 起同一 mysqld、**零新增 provision**）：①`WAREHOUSE_ADMIN`（23 码含 `outbound:correct`、无审批码）→ `CORRECTION_PERMISSION_DENIED`；②`SUPERVISOR=[correction:approve,order:handover,order:resume]`（**实测不含** `outbound:correct`）→ `SIGNING_KEY_UNAVAILABLE`，**已越过权限门与全部业务校验**，只在签名环节止步；③建档人自批 → `CORRECTION_SELF_APPROVAL_DENIED`。**失败轮保留**：首轮 `CORRECTION_COMMAND_NOT_OPEN`（operation bean 受 `outbound.corrections.commands.enabled` 控制）。一级回归 **134/2847/0/0/1**。**已登记连带影响**：`E2aCorrectionChainIT` 同一 actor 先建档后批单 ⇒ 现会被自批守卫拒绝（**裁定③的既定后果**，该 IT 只迁到 V48 故不受 V50 影响），修法＝引入独立审批主体，**待一次被授权的 provision 再改（未盲改）**。并逐字固化用户指定的 D 标注：「因共享树移除导致原测试前提（主树是"另一个 worktree"）失效，改为结构性 sibling 前提，**不是削弱断言**」。

- [R09 C2 I3 执行记录：归档、移除、脚本与默认值改写、定向回归](R09_C2_I3_EXECUTION_2026-09-30.md)：**A→E 全部完成**。**A** 归档 `.local/archive/r06-e2a-migration-20260930.zip`（**62.5 MB / 32,383 条目**），回读校验 **PASS**（逐条从包内流出重算 SHA256，全部 32,383 条，0 不一致 0 缺失）；**失败轮**：`bsdtar -T` 漏包 1,903 个（`wchar_t` 路径转换报错，**已排除**路径过长与非 ASCII 两个猜测）⇒ 改用 .NET `ZipArchive` 后通过，证据存 `.local/i3-archive/tar-attempt-failure.txt`。**B** 移除共享树两步共回收 **14.21 GB**（`git worktree remove --force` 回收 12.65 GB 后报 `Filename too long`，残留用 `rd /s /q \\?\…` 清掉 1.56 GB），D 盘可用 18.53→**32.73 GB**；`git worktree list` 已不含该树，**历史／`origin`／主树／`main` 未动**。**C** 8 个 `tools/` 脚本按逐行清单改默认值，其中 `e2a-cross-source-verify.ps1` 与 `e2a-link-counterproof.ps1` **参数化 `-Root`**（我最初误加第二个 `param` 块，已改并入并全部 parse 校验：0 错、1 个 param 块、0 残留引用）。**D** `E2aWorktree.java:15` 默认值改主树（**失败轮**：`E2aMigrationEnvironmentTest` 原把主树当"另一个 worktree"断言拒绝，已改为 `resolveSibling(...)` 结构性前提）。**E** 一级 **134/2847/0/0/1**、E2a 抽样 **1/0/0/0**（legacy runner 不设 `E2A_CROSS_ROOT` ⇒ 证明 D）、R08 抽样 **4/0/0/0**（不传 `-Workspace` ⇒ 证明 C）；两者同时证明**无在跑路径依赖已移除的树**。`.local` 证据未删。

- [R09 C2 A2.1 阻断与诊断：更正审批门的双主体（AND）拓扑](R09_C2_A2_1_SUPERVISOR_SEPARATION_BLOCKER_2026-09-30.md)：**已编码、未提交、待裁定**。**根因（非缺陷、非顺序问题）**：交接内核是一**个 OR 集合**（`order:handover || order:resume`），而更正内核是**两个 AND 主体**——`CorrectionCommand.Actor.current:91` **无条件**要求 `outbound:correct`（所有命令的"进门码"），`mayApprove()` 再要求 `correction:approve`；V50 只切了审批码 ⇒ **没有任何角色同时持有两者** ⇒ 更正 APPROVE 对所有人不可用。**实证（单条 SQL，复用 run 773 datadir，0 新 provision）**：`SUPERVISOR=correction:approve,order:handover,order:resume`；`WAREHOUSE_ADMIN` 23 码含 `outbound:correct`、无三个审批码；**"同时持两码的角色"查询为空**；V49/V50 均 success。顺带结论：`executeOnce` 顺序为 **上下文先于权限**（fail-closed 合理，非缺陷）。修法 **A**（给主管也授 `outbound:correct`：削弱 maker-checker、超出裁定范围）／**B（推荐）**（进门码按命令类型：审批类要 `correction:approve`、其余仍要 `outbound:correct`——真分离，但属产品授权判决改动）／C（本次只切交接码，更正审批暂留管理员＝无能力回归的退路）。附**省磁盘最小验证法**（复用已有 datadir + 单条 SQL／单类单测；全新 schema 才起 attempt）。

- [R09 C2 I3：最终归档 manifest（已生成，待确认后移除）](R09_C2_I3_FINAL_MANIFEST_2026-09-30.md)：按裁定生成 **`manifest.tsv` 32,383 行（sha256＋字节＋路径）**，纳入 **115.9 MB**；排除 **14,175.6 MB / 36,626 文件**，排除规则＝`**/data/**`(13.36 GB 隔离 MySQL 数据目录)、credentials/keys(凭据与私钥，**不得归档**)、`**/storage/**`、`**/private/**`、`build/**`(580 MB，可再生)、`*.log`；被排除材料的**规则汇总**另存 `excluded-summary.tsv`。7 个必保留项（attempt-117 诊断块＋5 个 S38/S39 修复前 handover 缺陷副本＋`CorrectionStore`）**均有哈希可独立取证**。移除动作**未执行**，待确认①范围②是否按 A→E 执行（B 在归档校验通过之后）。

- [R09 C2 四项待授权：A2.1 角色/权限现状登记 · B4 前置补齐①② · I3 待保留清单 · W-2-d 计数器与定时器设计](R09_C2_FOUR_AUTHORIZATIONS_DELIVERABLES_2026-09-30.md)：按用户 2026-09-30 逐项裁定交付四份说明（**均未执行授权内容**）。**A2.1**（[现状登记](R09_C2_A2_1_ROLE_PERMISSION_INVENTORY_2026-09-30.md)）：全库 **12 个角色码，无"主管/SUPERVISOR"角色**；审批类权限码 `order:recall/resume/dispose/handover`、`correction:approve`（V45 自述**草案**）、`outbound:correct` **全部只绑 `WAREHOUSE_ADMIN`**；业务词汇里已有 `short_shipment_policy='SUPERVISOR_APPROVAL'`（V6 唯一取值）但**权限模型无对应主体**；**无金额/范围/代理机制** ⇒ 列 6 个待决问题，**A2.1 保持待确认、不授权编码**。**B4-①**（[历史 keyring 设计](R09_C2_B4_PREREQ_HISTORICAL_KEYRING_DESIGN_2026-09-30.md)）：锚点**从不落盘**（只落 `anchorId=sha256(anchor)`）、**只有当前一代**；给出方案 A（运行期多代 keyring，需落盘包裹锚点＋格式变更）与**推荐方案 B**（停机重锚＋重打存活记录，不落锚点、无格式变更，需外部同时提供新旧锚）。**B4-②**（[重锚时长实测](R09_C2_B4_PREREQ_REKEY_MEASUREMENT_2026-09-30.md)）：合成基准实测 **1k＝2.4 s／10k＝20.6 s／100k＝10.6 min**，且**成本非线性**（2.05→6.38 ms/记录，×3.1）；1M 档按趋势 ≈1.8 h **主动中止并记为外推**；回滚＝**还原快照＋恢复旧锚、不涉及数据库**。**I3**（[待保留清单](R09_C2_I3_RETENTION_LIST_2026-09-30.md)）：实测 **83 项脏内容**（30 改＋53 未跟踪）、detached `ea4d166`、**约 14.3 GB**；除 attempt-117 诊断块外**新发现 5 个 handover 未跟踪副本仍是 S38/S39 修复前的缺陷版**（`disabled_at`／`prepared_ticket_sha256` 截断／回执未校验）⇒ **绝不并入**；含 8 个 `tools/` 脚本默认值的逐行修改清单与三种处置方案。**W-2-d**（[计数器与定时器设计](R09_C2_W2D_ORPHAN_COUNTER_TIMER_DESIGN_2026-09-30.md)）：修正缺口形状——孤儿**在发现它的那一次 `check()` 内同步退休并抛 `RECOVERY_LEASE_OUTSTANDING`**，故拒绝**不依赖定时器**；建议**三层**：第1层同事务四类计数（建议立即做）、第2层**默认关闭**的扫描定时器＋`outbound.recovery.lease.scan.*` 配置项、第3层主动仲裁**不建议现在做**；附改动面清单与 5 条验收口径（含"行为不变""拒绝不变""默认无线程"）。

- [R09 C2 B1 **K1 裁定：通过（附 W-2-d 已知边界）**](R09_C2_B1_K1_VERDICT_2026-09-30.md)：用户 2026-09-30 裁定原文登记与依据/边界固化——①**厂商方言/DDL 变体不需逐条单列**（拒绝为 fail-safe 方向、同一 `unclassified` 分支、逐条只重复验证同一代码路径、边际价值低）；②**改判 K1-1 / K1 为通过**（K1-1②隐式提交满足；①③原缺口四类已补齐实测；K1-2 按 W-2 接受为有界已知边界；K1-3 真实跨进程用例已交付）；③同步看板/欠债条目/导航并**保留失败轮 `.local/r09-k11-20260930/`**；④边界＝只做文档同步、不推四项待授权、不改代码、不部署不合 main。页内固化四条判据的逐条结论、**单一来源证据清单**（含全批 **25 类/729 项/0/0/0** 与 E4 534 项）、以及"**K1 通过 ≠ 生产就绪**"的边界（开关仍全默认 false、W-2-d 缺口保留、`UNCONFIRMED_NO_CROSS_PROCESS_BASIS` 标签原样保留、历史 keyring 仍未实现、长期单列项不解除）。

- [R09 C2 B1 K1-1 证据核实：E1–E6 事实表重建 ＋ 缺口补齐（135 项 SQL 形态）](R09_C2_B1_K1_1_EVIDENCE_VERIFICATION_2026-09-30.md)：按[K1 判据提案](R09_C2_B1_K1_PASS_CRITERIA_PROPOSAL_2026-09-30.md) §6 重建 E1–E6（只读＋复用本会话实测）：E1 `G0K1RealFileLockIT` 10/0/0/0、E2 `G0K1WriterLeaseIdentityRealIT` 5/0/0/0、E3 `G0K1RealJdbcBoundaryIT` 13/0/0/0、E4 `RecoveryEffectAdversarial*` 全家族 501/0/0/0、E5 `RecoveryEffectGateWiringRealIT` 1/0/0/0（走真实 MySQL 与生产注册表）、E6 `RecoveryEffectAdversarialDefaultOffIT` 15/0/0/0。**判据②满足**（`G0K1RealJdbcBoundaryIT:381` 在真实 MySQL 观测 DDL 隐式提交后 `rollback()` 无法撤销）。**缺口补齐（测试侧新增）**：`RecoveryEffectAdversarialSqlSurfaceIT` 由 102→**135 项**，新增 9 种不可归类形态（`SET SESSION`／`SET NAMES`／多语句／注释／`CALL`／`{call …}`）×3 重复＝27 项，外加 `prepareCall`→`execute` 调用体入口 ×2 形态 ×3 重复＝6 项；实测**135/0/0/0**，整批 **25 类/729 项/0 失败/0 错误/0 跳过**；`real-records.tsv` 记 `refusal=CONTROL_EFFECT_UNCLASSIFIED`、`delegate=0`、`observer=1:62:original;`（委托前拒绝＋无效果）。首轮按 `RECOVERY_LEASE_FENCED` 断言致 33 项失败，**失败轮 `.local/r09-k11-20260930/` 保留**；自审发现 `prepareCall` **本身不分类**、分类发生在调用路径（`RecoveryJdbcObjectHandler`）。⇒ K1-1 判据①③原缺口形态**已补齐**，唯一残余＝**厂商方言/DDL 变体未逐条单列**（同一 `unclassified` 分支），是否据此改判属**裁定问题**。

- [R09 C2 B1-3 S43：A5——两树整合 I1＋I2（只读）＋ B7 §4 受影响回归](R09_C2_B1_3_S43_A5_TWO_TREE_I1_I2_2026-09-30.md)：按已获批 [B7 提案](R09_C2_B7_TWO_TREE_INTEGRATION_PROPOSAL_2026-09-30.md)执行。**I1**：共享树与主树唯一实质差异＝`CommandReceiptIntegrity.java` 多 5 行自述 `TEMPORARY DIAGNOSTIC (attempt 117)`（`System.err.println`＋`printStackTrace`），主树/HEAD 为干净版本且不含任何诊断字样 ⇒ **不并入**。**I2**：扫描 `server/src`、`android/app/src`、`web/tools`、`tools`、`report-rendering/src`，命中 9 个文件——`E2aWorktree.java:15` 是 `E2A_CROSS_ROOT` 的**可覆盖默认值**（runner 恒导出）、`r08-provision-and-run.ps1` 与 `e2a-android-link-l4.ps1` 是 `-Workspace` 默认值、其余 6 个为旧 e2a/batch 工具族的回退路径 ⇒ **当前 run 路径不依赖共享树**，主树可独立运行。**B7 §4 回归**：新跑 E2a 门控抽样（run 757，1/0/0/0）与 V36 迁移（attempt5，1/0/0/0，同时证明 attempt 可配置在真跑中生效）；R-1/R-2/R-4/R-6/R-7 标注**同一 HEAD 的既有证据、不重跑**（本批无任何可执行代码改动）。**I3 共享树处置未做**（需单独授权）；共享树 95 项脏内容原样保留、未做任何写操作。

- [R09 C2 B1-3 S42：A4——R09 恢复与运维盘点（A4.1 证据对照；A4.2/A4.3 记阻断与待决）](R09_C2_B1_3_S42_A4_RECOVERY_INVENTORY_2026-09-30.md)：**A4.1 逐项对照既有隔离证据**——共享恢复控制/fence 与截点外 FH＝恢复链 **25 类/696 项/0/0/0**（含 `RecoveryFence{CrossProcess,Integrity,Startup,Unknown,Connection,AdversarialDurable}IT`、`RecoveryEffect*` 全家族、`G0K1*`）；V36 恢复语义＝**6/6**（本轮新跑通 stopwriter attempt3 与 auditarchive attempt7）；V37＝`E2aDraftEvidenceIT`/`E2aUnsubmittedHttpIT`/`E2aWithdrawnCorrectionExportIT` 全通过；V38＝`E2aJobFenceHttpIT` 已验；隔离灾后重建＝`G0K1RealFileLockIT`（强制终止/`halt(70)` 后 OS 释放锁、残留文件≠持锁）＋租约身份 fail-closed。**历史 keyring 未实现**（`RecoveryLeaseRoots.verify()` 只比对当前 `anchorId`）。**A4.2 部分未验**（RPO/RTO 数值目标属运维政策，未定不臆断；异地防覆盖属 C 档）。**A4.3/B9 未实施**（前置＝B4 实施级授权；B4 建议"不保留运行期历史 keyring"，若获批则按设计上不保留收口）。**唯一产品改动**：`CorrectionStore.java:75-81` 注释与 V46 实况对齐（原文称"没有 `order_effective_scan` 表"，实际 V46 新增且 SQL 已 JOIN），纯注释、无行为变化，`compileJava` 通过。

- [R09 C2 B1-3 S41：A3——R08 只读核对 ＋ 导出/汇总/重试/授权回归取证](R09_C2_B1_3_S41_A3_R08_REGRESSION_2026-09-30.md)：用既有 `tools/r08-provision-and-run.ps1`（主树 workspace、**端口必须 23308**、隔离 mysqld）跑通 **7 个 R08 类／47 项／0 失败／0 错误／0 跳过**——`R08ExportIT` 14（PC Excel 冻结原事实＋已验字节下载、PC/PDA 扫码冻结操作人与原始采集时间、PDA PDF 保留输入异常与原日期、占位操作人建任务前拒绝、短装计划含零合计与作废证据、已撤销权限/错误仓库拒绝、篡改产物与快照、并发同请求单任务、幂等重放与格式变更、拒绝当前净值与未完成订单）、`R08SummaryIT` 6（空区间零合计可渲染、区间成员冻结与快照重放、同原键改区间拒、越界区间拒）、`R08CenterPaginationIT` 4、`R08ClockBoundaryIT` 2、`R08ExportFenceIT` 4、`R08RetryIT` 7、`R08AuthorizationRaceIT` 9。**harness 事实**：R08 静态守卫写死 `23308` 与库名；`R08ExportIT` 只要前缀而 `R08CenterFixture` 要求**精确** `acrylic_r08_it` ⇒ 两族不能共库；`-DatabaseSuffix` 参数因脚本无 BOM 被 PS 5.1 截断、实际不可绑定 ⇒ 改用 `-DatabasePrefix`。**未运行** `R08PreviewSeedIT`（需 `R08_PREVIEW_SEED=true`＋`acrylic_r08_preview`，该变量不在 runner 导出清单，记未尝试）。**A3.1 结论**：原结果/调整/当前净值分别取 v1 读取器、更正凭据链、`order_result_version`＋`order_effective_scan`，重算仅作"缺一即拒"校验，**未发现报表自行重算替代服务端净值**；登记一处低危注释漂移。失败轮 `.local/r08-it-1`（自定义端口被守卫拒绝）保留。

- [R09 C2 B1-3 S40：A2.4——R07 政策边界验收（P-R07-06 三天补录窗口 ＋ P-R07-09 附件三项上限）](R09_C2_B1_3_S40_A2_4_POLICY_LIMITS_2026-09-30.md)：新增 `E2aCorrectionPolicyLimitsIT`（run 756，**1/0/0/0/17.4s**，服务层、跑在已验证的 R06 订单上）逐条把**接受/拒绝边界**打实：附件数 6 拒、**单个正好 10MB 接受**／10MB+1 拒、**单行 50MB−5B 接受**／第二行推过 50MB 拒（全部 `ATTACHMENT_LIMIT_EXCEEDED`）；业务时间**正好出库日−3 天接受**／−1ms 拒／−30 天拒／当前+30 分钟拒（`CORRECTION_WINDOW_EXCEEDED`）；每条被拒命令都不落文档。**基准口径已核对**：窗口取 `order_result_version` 的 **v1** 行 `created_at`（后续更正不移动窗口），判定式左右**含端点**。A2.4 逐条结论：P-R07-06／09 **已编码并完成边界验收**；**主管↔权限码映射待业主确认**（全库无"主管"角色）；附件**只校验客户端声明**、无上传端点/真实字节/存储。失败轮 752–755 保留（含一条产品约束的发现：同一文档内同一 dispatch 只能一行，`uq_correction_line_dispatch`）。回归 **133 XML/2843 项/0 失败/0 错误/1 既有跳过**。

- [R09 C2 B1-3 S39：A2.3——R07-1 outbox 落地（ACTIVATE 出站通知）＋ 第三处产品缺陷（prepared_ticket_sha256）](R09_C2_B1_3_S39_A2_3_OUTBOX_2026-09-30.md)：**A2.3 落地**——`HandoverStore` 新增 `nextOutboxEntityVersion`／`insertOutbox`／`serverRecoveryEpoch`，`Activate.execute` 在**同一事务内**写一条 `HANDOVER_ACTIVATED` 出站通知（payload 含 caseId/orderId/workRound/assignmentId/epoch/目标设备人员）。语义：**只有 ACTIVATE 发通知**（交接链唯一的订单状态变更）。定向验证 run 751 **1/0/0/0/13.5s**：`REQUEST/APPROVE/PREPARE outboxDelta=0`、`ACTIVATE outboxDelta=1`、`event=HANDOVER_ACTIVATED version=1 published_seq=null`（待发即可恢复）、同版本唯一、**再激活 `HANDOVER_STATE_INVALID` 且 outbox 不增长**。**第三处产品缺陷（已修）**：`prepared_ticket_sha256` 是 `BINARY(32)` 摘要列，而 `advanceCase` 直接写票据原始字节 ⇒ `Data truncation` ⇒ **PREPARE 从未成功**；改为入库前 `sha256(preparedTicket)`。**两处 harness 夹具缺口**（非产品缺陷，测试内如实补足）：仓库 `mapping_revision=0` 与已发布修订 `1` 不一致；`order_submission_slot` 无行。**已知限制**：outbox INSERT 之后的回滚无法在本 harness 演示（守卫都在 insert 之前、其后唯一步骤被触发器锁死，制造撕裂需删守卫），故同事务性由构造方式＋反例支撑。回归 `gradlew -p server test` **133 XML/2843 项/0 失败/0 错误/1 既有跳过**。**A2.2 矩阵全部收口**。失败轮 747–750 保留。

- [R09 C2 B1-3 S38：两处授权产品修复——激活路径 `disabled_at` 缺陷与回执完整性接线](R09_C2_B1_3_S38_R07_PRODUCT_FIXES_2026-09-30.md)：**产品代码改动**（用户 2026-09-30 授权）。①`HandoverActivation.targetDevice/targetUser` 由查询**不存在的** `disabled_at` 改为读真实列 `status='ACTIVE'`（拒绝码不变）⇒ 激活路径不再 `BadSqlGrammarException`；②`HandoverReceiptCodec` 新增 `verify(row)`（规范化重算摘要比对 + 字段/标识核对 + JWS 签名校验），`HandoverCommandService.replay` 在校验通过前不返回响应 ⇒ **M6 落成**；`HandoverCommand.corrupt()` 首次有了调用点。自审修正两点：签名 key id 改为从 **JWS header** 读取（回执表**无** `signature_key_id` 列）、BINARY(16/32) 列按 UUID 文本/小写 hex 归一化（否则正常重放会被误判损坏）。定向验证 run 746 **1/0/0/0/13.3s**：`M7 恢复代次不符 -> ORDER_RECOVERY_REQUIRED`（此前为 SQL 语法失败）＋`M6 伪造回执重放 -> HANDOVER_RECEIPT_INTEGRITY_FAILURE`（且未新增回执行；用直接 INSERT 造损坏回执，**未绕过任何既有触发器守卫**）。回归 `gradlew -p server test` **133 XML/2843 项/0 失败/0 错误/1 既有跳过**，与修改前基线一致。失败轮 743/744/745 全部保留（测试自身缺陷＋harness XML 解析限制）。

- [R09 C2 B1-3 S37：A2.2 第二批——并发断言落成（M10–M12）＋ R07-1 激活路径产品缺陷（`disabled_at`）](R09_C2_B1_3_S37_A2_CONCURRENCY_AND_DEFECT_2026-09-30.md)：新增 `E2aHandoverConcurrencyIT`（run 742：**1/0/0/0/13.6s**），用真实线程＋`CountDownLatch` 落成三条并发断言——**M10** 双 APPROVE 同版本 → `[HANDOVER_CONTEXT_CHANGED, OK]`（案件终态 APPROVED、只有赢家留回执、清单只写一次）；**M11** REQUEST 与 APPROVE 交错 → `[HANDOVER_STATE_INVALID, HANDOVER_RESULT_UNKNOWN]`（成功数≤1、每个来源始终 1 行案件）；**M12** 同键并发重放 → `[OK, OK]`（回执/案件/审计均仍为 1；**明示口径**：证明并发重放不产生第二条，写入竞争由 M10 覆盖）。**新发现产品缺陷**：`HandoverActivation.java:86,93` 查询 `device`／`app_user` 的 **`disabled_at` 列在全部迁移中不存在**（`V7` 用 `status IN ('ACTIVE','DISABLED')`；全仓 grep `disabled_at` 仅此两处）⇒ **PREPARE→ACTIVATE 链在真实库上必然 `BadSqlGrammarException`**，激活从未能跑通。后果：M7（恢复代次守卫）位于该缺陷之后、**当前无法到达**；修复（2 行，产品改动）**未实施、需授权**。A2.2 矩阵：**14 条已跑通／M6 实现缺口／M7 被缺陷阻塞／M13-① 待 A2.3**。失败轮 737–741 全部保留（737 为产品缺陷，其余为测试自身缺陷）。

- [R09 C2 B1-3 S36：A2.2 零落点断言（第一批：服务层 M5/M8/M9/M13-②＋M6 定性）](R09_C2_B1_3_S36_A2_ASSERTIONS_2026-09-30.md)：新增 `E2aFormalRegisteredOperationsIT`，把 A2.2 矩阵 4 条零落点断言落成同层服务调用（run 736：**1 项/0 失败/0 错误/0 跳过/13.3s**）：M5 清单状态无效→`HANDOVER_CHECKLIST_REQUIRED`、M8 原结果未终结→`HANDOVER_ORIGINAL_RESULT_OPEN`、M9a 案件版本过期→`HANDOVER_CONTEXT_CHANGED`、M9b 案件不存在→`HANDOVER_CASE_NOT_FOUND`（且**未留回执/未留案件行**）；M13-② 以"无半条"断言收口。**M6 定性为实现缺口而非缺测试**：`HandoverCommand.corrupt()`（`HANDOVER_RECEIPT_INTEGRITY_FAILURE`）**零调用点**⇒ R07-1 命令链没有回执完整性校验路径，篡改只命中 `replay` 的逐字节比较（`IDEMPOTENCY_CONTENT_MISMATCH`）；补它需产品改动、单独授权。A2.2 矩阵更新为 **11 条已跑通／1 条实现缺口／3 条仍零落点（M7＋M10–M12）／1 条待 A2.3**。三次失败轮（733/734/735）均为测试自身缺陷，全部保留。

- [R09 C2 B1-3 S35：A2.1/A2.2——R07 待实现清单与 R07-1 端到端断言矩阵](R09_C2_B1_3_S35_A2_R07_MATRIX_2026-09-30.md)：只读核对＋一次实测取证。**A2.1** 逐条给出"政策→现状→退出标准"：R07 **不是"未实现"**而是"主路径已编码、缺口集中"（主管角色不存在、库存差异无审批门、无补扣入口、`MOVEMENT_DATE`/财务锁期例外无证据、故障换机仅枚举占位、撤销缺占用回退、更正表无 append-only 触发器、附件无真实字节校验/存储、`handover` 无 outbox）。**A2.2** 修正剩余计划"14 条全未跑"的口径：实测 **7 条已有落点并已跑通**（`E2aFormalRegisteredCounterexamplesIT` run 718 1/0/0/0，4 反例＋3 幂等，**服务层**）＋**9 条零落点**（反例⑤–⑧、"404 不当成未发生"、3 并发、2 原子性，逐条给出产品侧实现位置与建议落点）。

- [R09 C2 B1 的 K1-2 窗口 W 验收：W-2 有界已知边界](R09_C2_B1_K1_2_WINDOW_W_ACCEPTANCE_2026-09-30.md)：按用户 2026-09-30 裁定落成验收记录。机制事实：孤儿扫描**无定时器**，只在启动／`checkEffect`／`registerOperation` 三处同步触发（`RecoveryFenceAuthority.java:24,35,44`）⇒ **窗口 W＝两次触发之间的间隔**。W-2 四条标准：a（延迟上界＝一个触发间隔）**成立**、b（触发间隔取值）**成立但表述为"由调用频率决定、无配置项"**、c（孤儿期间不得当作无孤儿放行的失败关闭）**成立**、d（未决孤儿计数/运维可见性）**未成立＝缺口**。⇒ K1-2 记"**已接受为有界已知边界**"并附 W-2-d 缺口；**K1 仍未通过**，窗口 W 与 `UNCONFIRMED_NO_CROSS_PROCESS_BASIS` 原样保留。

- [R09 C2 B1-3 S34：A0 收尾批次（A0.1 剩余门控 IT ＋ A0.3 恢复链批次 ＋ 659 根因定位）](R09_C2_B1_3_S34_A0_CLOSURE_2026-09-30.md)：A0.1 用新增 `tools/r09-a01-remaining-it-run.ps1` 逐类一次性 attempt 跑完剩余门控 IT——**19 类尝试／15 类通过／4 类失败／0 跳过**（含补漏的 `E2aWithdrawnCorrectionExportIT`；`E2aServeBindingForAndroidIT` 同时跑通 Android 半场＝L4）⇒ E2a 主树已验 **24→39**；4 个失败均定性为夹具相对现行迁移的漂移或归档子进程环境，**非产品缺陷**。A0.3 恢复链 pinned 批次复跑 **25 类/696 项/0 失败/0 错误/0 跳过**（第一次因保留实例崩溃恢复需约 41 秒而预热超时，属环境问题，失败轮保留）。**659 按裁定⑤完成带日志仪表化并事后撤回**：唯一抛出点＝`TemporaryLinkOperation.java:43`（客户端 `candidateContextSha256` ≠ 服务端 `recomputed`，`total=1`）⇒ 守卫按设计拒绝**过期候选摘要**，**不是产品缺陷**，改为"harness 侧协议未闭合＋单列"，不需再对产品侧仪表化。**A0.2 未执行**：两个 V36 IT 自己生成密钥对，真正阻塞是**一次性 attempt 已被消费**（写死常量＋`test-started.marker`），待用户就"改 attempt 常量／改为可配置"作选择。另附四份提案（K1 判据、B2 范围、B4 锚点、B7 两树整合）；开关仍全默认 false，K1 仍未通过。

- [R09 C2 B1-3 S33：可执行清单固化与用户裁定/授权登记](R09_C2_B1_3_S33_EXECUTION_CHECKLIST_2026-09-29.md)：纯文档轮（未开工）。把十步完成度盘点固化为[可执行清单](../REMAINING_DEVELOPMENT_PLAN.md)（A0 主线收尾／A1 R06／A2 R07／A3 R08／A4 R09／A5 两树整合／C 现场条件项），并登记用户 2026-09-29 裁定/授权七项（K1 判据提案先行、B2 提案先行、允许隔离用等价私钥、锚点提案先行、659 授权一次仪表化、生产边界全关、整合提案先行），同时给出**新会话接续指令**。看板当前区已改为换会话交接态。K1 仍未通过；开关仍全默认 false。

- [R09 C2 B1-3 S32：夹具链与 Android 打通（10 个库存 IT ＋ Android 两项）](R09_C2_B1_3_S32_FIXTURES_ANDROID_2026-09-29.md)：探针证明 openssl 的 DER **无法被 JVM 解码**（`Unable to decode key`）⇒ 改用 JDK 生成 PKCS#8/SPKI 后 `SIGNING_KEY_UNAVAILABLE` 消失；harness 加 `-AppMigrates`＋`tools/GenSigningKey.java`，4 个 Android 测试去掉硬编码旧工作树守卫。结果：库存家族 **10 个 IT 通过**＋**11 个夹具**生成；**Android 两项关闭**（710/711 ✔，Android 套件 0 失败）⇒ **E2a 主树已验证 22→24 个**。未验：659（待裁定）、V36 stopwriter／auditarchive（待裁定私钥物料）。K1 仍未通过；另附 S32 开工前看板归档。

- [R09 C2 B1-3 S31：夹具链——签名守卫的进程内证据](R09_C2_B1_3_S31_SIGNING_BINDING_2026-09-29.md)：`application.yml` L38–41 显示 `outbound.signing.*` 绑定 `OUTBOUND_AUTH_SIGNING_*`（一直在导出）⇒ 推翻"属性空白"；run 692 的进程内打印证明环境与密钥文件均正常（1191 B/294 B，0x30 开头）⇒ `OfflineSigner.keys()` 抛的是 **catch 分支**（try 块内载入/模长/自签验签失败）。下一步：测试侧反射调 `keys()` 打印 cause（不改产品），或授权产品侧仪表化。644／647＋8 个夹具 IT 判未验。E2a 主树已验 22 个不变。K1 仍未通过；另附 S31 开工前看板归档。

- [R09 C2 B1-3 S30：夹具链推进——上下文可起、503=签名密钥守卫](R09_C2_B1_3_S30_FIXTURE_SIGNING_2026-09-29.md)：加 `-AppMigrates` 后 8 个夹具 IT 上下文可起（此前 `server_state` 不存在），但统一 503；MockMvc 输出显示 `SIGNING_KEY_UNAVAILABLE`（离线签名密钥不可用）。harness 已自动生成 PKCS#8/SPKI 密钥对（openssl，文件实测正常）并导出 `OUTBOUND_SIGNING_*`，守卫仍拒 ⇒ 下一步查该 IT 是否覆盖/置空 `outbound.signing.*`（`OfflineSigner.keys()` 空白即抛此码）。644／647 与 8 个夹具 IT 判未验。E2a 主树已验 22 个不变。K1 仍未通过；另附 S30 开工前看板归档。

- [R09 C2 B1-3 S29：树内 chromium 安装＋Android/夹具链根因定位](R09_C2_B1_3_S29_CHROMIUM_ANDROID_CHAIN_2026-09-29.md)：树内安装 Playwright chromium 成功（跨树回退不再需要）；Android 两项的阻塞链定位到 **8 个 inventory 夹具 IT 需要应用自跑迁移**（本 harness 沿用 `OUTBOUND_MIGRATE=false` ⇒ `server_state` 不存在 ⇒ Spring 上下文失败）⇒ 下一步仅需 `OUTBOUND_MIGRATE=true`；harness 补导出 `OUTBOUND_DB_*`（单靠它未通过，如实记录）；改坏运行器已用 `git checkout --` 恢复。E2a 主树已验 22 个不变。K1 仍未通过；另附 S29 开工前看板归档。

- [R09 C2 B1-3 S28：V36 Audit 两 IT——1 通过、1 只差私钥物料](R09_C2_B1_3_S28_V36_AUDIT_2026-09-29.md)：两个 Audit IT 断言 **`@@log_bin == 0`**（与迁移类相反）⇒ 运行器改为按 item 决定 binlog；`E2aV36AuditPrefixIT` **通过**（E2a 主树已验 **21→22**），`E2aV36AuditArchiveIT` 只差私钥 `<attempt>/private/private.pk8`（与 `stopwriter` 的 `stop-it-private.pk8` 同类）。**待裁定**：①659 处置；②是否允许 harness 生成隔离用等价私钥（私钥不入库）。K1 仍未通过；另附 S28 开工前看板归档。

- [R09 C2 B1-3 S27：V36 家族供给与首批运行](R09_C2_B1_3_S27_V36_FAMILY_2026-09-29.md)：新增 `tools/r09-e2a-v36-run.ps1`（datadir 置于 IT 断言的 `<attempt>/data`、导出 `E2A_<PREFIX>_*` 与 `E2A_CROSS_ROOT`、口令落盘复用、一次性拒绝重跑）；`E2aV36MigrationIT`／`E2aV36DeviceKeyIT`／`E2aV36PairedKeyIT` **通过**（E2a 主树已验 **18→21**）；`E2aV36StopWriterIT` 需预置私钥物料、两个 Audit IT 的身份断言仍与供给有差异（**均非产品问题**）。659 仍待裁定。K1 仍未通过；另附 S27 开工前看板归档。

- [R09 C2 B1-3 S26：659 的 409 两次驱动实验](R09_C2_B1_3_S26_659_CONTEXT_409_2026-09-29.md)：①把取候选摘要提到 `facts#1` 之前、②命令改用快照自身的 `candidateContextSha256`（并打日志）——**两次仍 `LINK 409 COMMAND_CONTEXT_CHANGED`**，且日志无 `digest mismatch`（两来源一致）⇒ 排除"候选顺序"与"摘要覆盖"；663 审计**无该 409 事件** ⇒ 字段级差异不在审计中。S25 的 CSRF 修复保留有效；659 仍未验。定位字段须**产品侧仪表化运行（需授权）**，未授权前不做也不当产品缺陷。E2a 主树已验 18 个不变。K1 仍未通过；另附 S26 开工前看板归档。

- [R09 C2 B1-3 S25：659 的 403 根因修复（测试侧）＋推进到业务级 409](R09_C2_B1_3_S25_DRIVER_CSRF_FIX_2026-09-29.md)：根因在 SPA 驱动——`web/tools/e2a-web-command-flow.mjs` 只在登录前取一次 `/auth/csrf`，登录后令牌被轮换而驱动仍用旧令牌发 POST ⇒ 安全层 403（不留审计行）。修法：`readCsrf(label)`＋**登录后重取**；run 661 显示 403 **消失**、`steps` 含 `csrf (after login)`，LINK 前进到 **409 `COMMAND_CONTEXT_CHANGED`**（业务级乐观并发守卫，产品按设计工作）⇒ 阻塞从"安全层"前移到"上下文新鲜度"。下一步：驱动在取回候选摘要后重读 facts 再发 LINK。E2a 主树已验 18 个不变。K1 仍未通过；另附 S25 开工前看板归档。

- [R09 C2 B1-3 S24：659 的 403 定性——非业务层拒绝（安全/凭据）](R09_C2_B1_3_S24_659_SECURITY_LAYER_2026-09-29.md)：读源码排除"缺角色/仓库不匹配"（检查自己授予 `temporary_order:link` 且案卷同仓库）；run 660 的 `browser-command-flow.json` 给出 LINK 真实响应（`ACCESS_DENIED`＋"权限不足或安全凭据失效，请刷新后重试"）；660 datadir 审计**无任何拒绝事件** ⇒ 403 是**安全层（凭据/CSRF）**在业务之前拒绝，与 S22 同源（GET 通过、POST 被拒）⇒ 指向登录后 CSRF 令牌轮换。659 仍判未验但**不是产品缺陷**证据。下一步：查 CSRF 轮换配置或驱动是否重取 `/auth/csrf`。E2a 主树已验 18 个不变。K1 仍未通过；另附 S24 开工前看板归档。

- [R09 C2 B1-3 S23：659 的权限/作用域查证与收窄](R09_C2_B1_3_S23_659_SCOPE_2026-09-29.md)：LINK 需要 `temporary_order:link`（与 IT 一致）；`role_permission`/`user_role` **无作用域列**；659 库中该码有 **2 条授予**；IT 已显式打开 `outbound.temporary.commands.enabled` ⇒ 排除"权限缺失/作用域列绑定"，剩三选一（actor 不持有该码角色／代码级仓库比对／真实产品差异）。下一步：读 `TemporaryCommandBrowserChecks` 的 LINK 构造＋一次查库判定；此前不得当作产品缺陷。E2a 主树已验 18 个不变。K1 仍未通过；另附 S23 开工前看板归档。

- [R09 C2 B1-3 S22：Secure cookie／CSRF 根因——633 与 642 转为已验](R09_C2_B1_3_S22_SECURE_COOKIE_2026-09-29.md)：加 `-GradleInfo`（转发测试 stdout）后读源码与库诊断：655 的库里**无 `WEB_LOGIN_FAILED` 审计行** ⇒ 403 是**认证前的 CSRF 过滤器**拦的；对照通过的 640 显式传 cookie.secure=false ⇒ **Secure cookie 在明文 HTTP 下不被回送**。harness 补 `SERVER_SERVLET_SESSION_COOKIE_SECURE=false` 后 **657 `E2aCorrectionChainIT`、658 `E2aHandoverHttpLayerIT` 通过 ⇒ 633／642 转已验**（E2a 主树累计 **18 个**）；659 仍失败但性质澄清为**作用域/权限码**（登录 200 后 LINK 403，权限行与授予均在）。未做：659 判定、Android 工具链、V36 门＋供给、树内 chromium、Python v2 供给。K1 仍未通过；另附 S22 开工前看板归档。

- [R09 C2 B1-3 S21：权限/角色前提第三次尝试＋决定性诊断](R09_C2_B1_3_S21_PERMISSION_DIAGNOSIS_2026-09-29.md)：补齐参考 runner 的四个环境变量后，651/652/653 的失败点**完全不变** ⇒ 排除"缺参考环境变量"；对 653 的保留 datadir 离线查库显示 `temporary_order:{confirm,create,link,manage}` **权限行 4、授予 7** ⇒ `LINK 403 ACCESS_DENIED` **不是权限缺失**，收窄到**作用域/权限码/临时模块开关**三选一。下一步：给 harness 加测试 stdout 转发（`showStandardStreams`／`--info`）取回 `log=` 与断言上下文再判定。633/642/645 仍判未验；E2a 已验 16 个不变。K1 仍未通过；另附 S21 开工前看板归档。

- [R09 C2 B1-3 S20：报表/渲染服务供给](R09_C2_B1_3_S20_RENDERER_HOME_2026-09-29.md)：读源码定位——`ExportRendererProcess.requireAvailable` 要求 `renderer-home` 下有 `lib/`（PDF 另需 `browsers`），渲染为子进程 `java -cp <home>/lib/* …ReportCli`；未显式传 `renderer-home` 的 IT 必然 `EXPORT_UNAVAILABLE`。harness 补 `OUTBOUND_EXPORTS_RENDERER_HOME`（树内 install 分发）与 `OUTBOUND_EXPORTS_BROWSERS`（树内优先、否则回退到 IT 自己硬编码的 r08-export 副本，已声明）：**648 `E2aTemporaryCommandWebHttpIT` 通过**（E2a 主树累计 **16 个**）；633 推进两级（XLSX→PDF→`real Web login 403`）⇒ 渲染不再是阻塞项，633／642／645 同源卡在**权限/角色种子前提**。未做：权限种子、Android 工具链、V36 门＋供给、树内 chromium 安装、Python v2 供给。K1 仍未通过；另附 S20 开工前看板归档。

- [R09 C2 B1-3 S19：E2a 批次四](R09_C2_B1_3_S19_E2A_BATCH4_2026-09-29.md)：643 `E2aSourceBindingHttpIT` **通过**（E2a 主树累计 **15 个**）；644/647 依赖 Android 单测子进程、645 轨迹止于 `LINK 403 ACCESS_DENIED`（权限未授予）、646 响应 `EXPORT_UNAVAILABLE（报表生成服务尚未配置）`——四个失败**各自定位到具体环境前提**，无 B1-3 相关产品缺陷；646 同时给出 633「渲染必须成功（XLSX）」的同源假设（仍判未验）。未做（独立环境批次）：报表服务配置、权限种子/迁移到较晚版本、Android 工具链、V36 门＋供给、Python v2 供给。K1 仍未通过；另附 S19 开工前看板归档。

- [R09 C2 B1-3 S18：E2a 批次三](R09_C2_B1_3_S18_E2A_BATCH3_2026-09-29.md)：638 临时账务、639 临时候选、640 临时导出、641 临时链接四个 IT **通过**（E2a 主树已验证累计 **14 个**，另有 C1 611）；642 `E2aHandoverHttpLayerIT` 断言实得 -1 ⇒ 源码为 `session.login(outsider) ? … : -1`，即**登录/会话前提未满足、请求未发出**，非产品判定。顺带查明额外环境门清单（迁移执行 1 个＋V36 家族 6 个，未开即 skip，须先补供给而非开门）。**633 收窄**：640 通过 ⇒ 渲染链路整体可用，633 的 XLSX 渲染断言为该路径特有，下一步读其渲染作业日志。未做：余下约 20 个门控 IT、V36 供给、633 判读、642 登录供给、Python v2 供给。K1 仍未通过；另附 S18 开工前看板归档。

- [R09 C2 B1-3 S17：E2a 批次二 + 工作树根收敛](R09_C2_B1_3_S17_E2A_BATCH2_2026-09-29.md)：发现 12 个 E2a 测试文件各自硬编码旧共享工作树路径（632 的 NoSuchFileException 即此），新增单一来源 `E2aWorktree.ROOT`（`E2A_CROSS_ROOT` 覆盖）并替换全部；本批 630 作业围栏、631 决策协议、634 交接模型、635 确认 **通过**（632 失败→635 通过成对留证）；633 `E2aCorrectionChainIT` 未通过（导出/渲染断言，未定性）；636/637 V36 两个 IT **skip**（另有环境门，记未尝试）。E2a 主树已验证 **10 个** IT，C1 侧 611 通过。未做：其余约 25 个门控 IT、V36 环境门、渲染断言判定、Python v2 供给。K1 仍未通过；另附 S17 开工前看板归档。

- [R09 C2 B1-3 S16：E2a 闭合投影攻克 + 修正 schema 抽样](R09_C2_B1_3_S16_PROJECTION_2026-09-29.md)：`E2aClosedProjectionIT` 此前五轮失败（620/624–627）的**根因是供给清单不完整**（该类注释即要求运行器预分配 `${schema}{copy,conflict,missing,gap,overflow,rollback,instance,skip,mapping}` 且为空），非产品缺陷；工具改为精确集合＋`_restore` 后 **628 通过**（67.4s，产物含迁移/投影前后快照：193 872／622 952／624 628 B），并抽样 `E2aCorrectionSchemaIT`（629）通过。E2a 主树已验证 6 个 IT（604/621/622/623/628/629），C1 侧 611 通过。未做：其余约 30 个门控 IT、Python v2 供给（B9）。K1 仍未通过；另附 S16 开工前看板归档。

- [R09 C2 B1-3 S15：E2a 其余 IT 首批重跑](R09_C2_B1_3_S15_E2A_BATCH_2026-09-29.md)：按 S14 配方分批跑 E2a 门控 IT，首批 4 个——`E2aRecoveryWriterTransactionIT`、`E2aLatestSchemaRegressionIT`、`E2aCrossSourceHttpIT` **在主树 B1-3 代码上通过**；`E2aClosedProjectionIT` 未通过（它按用例名运行期拼伴随 schema：copy/instance/conflict/missing…；工具已补 schema 族与一次性实例全局授权并声明该 harness 偏差，最终停在 `Unknown database '<schema>_missing'`；625 attempt 已产出接纳数据源/租约事务证据 ⇒ 失败在投影阶段后段，仍判未验）。未做：其余 30+ 门控 IT、`_missing` 期望判定、Python v2 供给。K1 仍未通过；另附 S15 开工前看板归档。

- [R09 C2 B1-3 S14：C1 实验舱重跑（主树）](R09_C2_B1_3_S14_C1_RERUN_2026-09-29.md)：C1 与 E2a 共用主树 ROOT 与同一一次性布局，故复用 S13 工具，只需补导出 `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`（C1 启动应用不带 datasource 参数）。**结果**：`C1InitialNormalHttpIT` 在**主树 B1-3 代码**上通过（1 用例/0 失败/23.3s），产物含真实 HTTP 判定、丢回执围栏、原字节快照 `before/after` **逐字节相等**，以及 `C1EntryRegression` 复用 attempt105 的入口语料（`rootRequired=RECOVERY_OPERATION_REQUIRED`、`closeWaitedMillis=2512 innodbTrx=0`、`CONTROL_WRITERS_ACTIVE`、丢回执 `committedCounter=2`）。失败轮 `610`（缺数据源环境变量）保留。未做：E2a 其余 IT、C1 smoke 自身仍需 pwsh 7、Python v2 供给。K1 仍未通过；另附 S14 开工前看板归档。

- [R09 C2 B1-3 S13：E2a 实验舱重跑（主树）与实验舱重基](R09_C2_B1_3_S13_E2A_RERUN_2026-09-29.md)：E2a 运行器与 profile 原本硬绑只读共享树（ea4d166＝B1-3 之前），跑也验不到主树；本轮让 profile 的 ROOT 可经 `E2A_CROSS_ROOT` 指向被测树，并新增 5.1 兼容工具 `tools/r09-e2a-entry-rerun.ps1` 自供给一次性 binlog 实例（建库建号＋`GRANT PROCESS/SHUTDOWN`＋`root@127.0.0.1`）后跑 `integrationTest`。**结果**：`E2aRecoveryAdmissionEntryIT` 在**主树 B1-3 代码**上通过（1 用例/0 失败/18.9s），attempt 内留有入口语料证据（`rootRequired=RECOVERY_OPERATION_REQUIRED`、`closeWaitedMillis=2497 innodbTrx=0`、`CONTROL_WRITERS_ACTIVE` 等）。供给配方入档（binlog 必开、账号需 PROCESS、需环回 root、IT 走 `integrationTest`）。未做：C1 版运行器（需 pwsh 7）、E2a 其余 IT、Python v2 供给。K1 仍未通过；另附 S13 开工前看板归档。

- [R09 C2 B1-3 S12：逐根租约发布与孤儿退役](R09_C2_B1_3_S12_LEASE_PUBLICATION_2026-09-29.md)：被接纳的服务态根在 P5 里**发布**租约记录（发布失败即接纳失败）；扫描按设计 §5.3 判孤儿后**退役**（删除记录＋去掉索引条目，绝不改进程记录）再拒绝；修掉"本 JVM 已退役记录的关闭通道让孤儿永不退役"的真实缺陷；初始化窗口自身的根不发布（否则与守卫排序成启动环）。**现场**（实例 19475）：`boot1` 发布 → `child1`（独立 JVM）判孤儿并退役、拒绝启动 → 树空 → `child2`（独立 JVM）`STARTED`；全量单测 133 类 2843 项 0 失败、恢复链 25 类 696 项 0 失败（实例 19476）。未做：Python 侧 v2 供给与封闭权威身份（B9）、生产级 provision、实验舱重跑、§5.6 事件细化。K1 仍未通过；另附 S12 开工前看板归档。

- [R09 C2 B1-3 S11：v2 初始权威形状与显式供给](R09_C2_B1_3_S11_INITIAL_AUTHORITY_V2_2026-09-29.md)：`RecoveryInitialAuthority` 版本感知形状校验（v1 旧链／v2 初始安装＋`closedAuthority`/`leaseRoot`，字段集精确、未实现版本拒）＋显式供给方 `RecoveryInitialAuthorityProvision`（就地提升 v1→v2 并建 P5 树）⇒ **S10 现场实测的"准入 v1 与跨进程 P5 v2 身份互斥"消解**。**现场**（实例 19473）**生产形态整套 opt-in 首次启动成功**，真实 P5 authority 围栏后写／关系读／提交全被拒且计数不变（跨进程效果门落库缺口闭合）；全量单测 132 类 2836 项 0 失败、恢复链 25 类 696 项 0 失败（实例 19474）。**协议语义**：写者协议钉住身份摘要 ⇒ 先提升身份再钉协议；Python 侧供给仍 v1（B9）。未做：第 10/12 行逐根发布/退役、Python v2 供给、生产级 provision 工具、实验舱重跑。K1 仍未通过；另附 S11 开工前看板归档。

- [R09 C2 B1-3 S10：效果门接入真实 JDBC 入口](R09_C2_B1_3_S10_EFFECT_GATE_WIRING_2026-09-29.md)：应用自己的主数据源挂上效果门（默认仍关闭）——本机层与跨进程层都在委托前生效；只读白名单严格性实测（常量读不查、**关系读照查**、未识别 SQL 拒绝）；**现场**（实例 19471）真实应用未围栏 write ok, commit ok、authority 拒绝后写/关系读/提交全部被拒且 counterAfterFence 不变（没落到驱动）；全量单测 132 类 2833 项 0 失败、恢复链 25 类 696 项 0 失败（实例 19472）。**发现**：准入层（v1）与跨进程 P5 层（v2）身份字段集互斥 ⇒ 今天无法同开，跨进程效果门生产形态落库仍未验。未做：第 10/12 行逐根发布/退役、v2 权威供给（B9）、实验舱重跑。K1 仍未通过；另附 S10 开工前看板归档。


- [R09 C2 B1-3 S9：阶段 0 安全前置第一批——P5 根身份/围栏通道/进程记录接线](R09_C2_B1_3_S9_P5_ROOT_WIRING_2026-09-29.md)：`RecoveryLeaseConfiguration`＋`RecoveryLeaseLifecycle` 把设计第 8–9 行的 P5 基座接进应用（显式供给的 P5 根、锚点校验、围栏通道、孤儿扫描、进程记录与生命周期锁、fail-closed 启动、Spring 关停退役 `retire()`）；开关默认 false，不配则一根不取。验证：全量单测 130 类 2825 项 0 失败（新增 7 项含"发布后删租约⇒下次启动 `RECOVERY_LEASE_OUTSTANDING`"）；**现场**（实例 19469）真实应用取 P5 根且 `checkEffect=ok`、发布租约→退役→下次启动因孤儿租约被拒；恢复链 25 类 696 项 0 失败（实例 19470）。发现：lease 配置漏启用准入 settings（现场才暴露的产品缺陷）、P5 根按设计是共享的、v2 初始权威供给仍缺。未做：第 10–12 行（逐根发布/探针、**效果门接入 JDBC 入口**、逐操作退役）、v2 权威供给、实验舱重跑。K1 仍未通过；另附 S9 开工前看板归档。

- [R09 C2 B1-3 S8：入口 IT 重基到请求根语义](R09_C2_B1_3_S8_ENTRY_REBASE_2026-09-29.md)：三处入口验证共用语料按"一个请求＝一个操作根"重写（根内运行、无根必拒 `RECOVERY_OPERATION_REQUIRED`、关闭意图下"保留根继续服务／新根 `CONTROL_CLOSING_INTENT`"）；丢回执故障下沉到请求根数据源之下并补齐启动覆盖（static 工厂避环）；新增可在隔离实例运行的 `RecoveryAdmissionEntryRebaseIT`，现场（实例 19468）live／unknown／lost 三上下文同轮全绿（`closeWaitedMillis=2500`、`innodbTrx=0`、丢回执 `committedCounter=2` 后仍 `CONTROL_WRITERS_ACTIVE`）；全量单测 2818 项 0 失败。发现并补齐实验舱重基缺口（`C1MainProfile`／`E2aCrossSourceHttpIT` 缺 `cleanup-cron=-`，否则连启动都被拒；实验舱本身仍未重跑）。未做：阶段 0 安全前置（第 8–12 行）、效果门接线。开关仍默认 false，K1 仍未通过；另附 S8 开工前看板归档。

- [R09 C2 B1-3 S7：拒绝码 HTTP 映射与无状态拒绝写出口](R09_C2_B1_3_S7_REFUSAL_MAPPING_2026-09-29.md)：`ApiResponses.writeWithoutState`（拒绝不读库）＋`RecoveryRefusalAdvice`（服务中拒绝 ⇒ 503＋原码，排序承重）＋接纳拒绝由过滤器自写 503；超限 ⇒ 413、畸形 multipart ⇒ 400；`body(...)` 读状态失败改为降级报告。现场（实例 19466）：围栏 503 `RECOVERY_LEASE_FENCED`、超限 413、畸形 400、过期权威接纳 503 `CONTROL_INSTANCE_STALE` 且零根，四者 envelope 均 `unknown/UNAVAILABLE`；全量单测 2818 项 0 失败、恢复链 25 类 696 项 0 失败。**口径更正**：撤回 S4 记录"超限 500"定性（探针代理未解包 `InvocationTargetException`）。未做：入口 IT 重基、阶段 0 安全前置。开关仍默认 false，K1 仍未通过；另附 S7 开工前看板归档。

- [R09 C2 B1-3 S6：控制锁按根可嵌套与有界等待](R09_C2_B1_3_S6_CONTROL_LOCK_2026-09-29.md)：`RecoveryControlLock` 改为按根信号量＋线程级根集合（P3=1/P2=2/P5=3），同根重入按误用拒绝（等待式 `CONTROL_COORDINATOR_REENTRANT`）、逆序 `CONTROL_LOCK_ORDER_VIOLATION`；新增 `lockedWaiting`（预算 1..10000ms，跨进程文件锁同预算重试）；`registry.admit` 用 2000ms 有界等待 ⇒ **4 路并发请求实测全部 200**（`concurrentRoots=[4,3,2,1]`，真重叠），S3 遗留的"并发 HTTP 未验"闭合。全量单测 2814 项 0 失败、恢复链 25 类 696 项 0 失败。未做：阶段 0 安全前置（第 8–12 行）、拒绝码 HTTP 映射、入口 IT 重基。开关仍默认 false，K1 仍未通过；另附 S6 开工前看板归档。

- [R09 C2 B1-3 S5：启动守卫与初始化窗口](R09_C2_B1_3_S5_STARTUP_WINDOW_2026-09-29.md)：解决"开开关即起不来"——`RecoveryStartupGuard`（清理未禁用⇒`CONTROL_SESSION_CLEANUP_UNCOVERED`；应用 Bean 显式排到初始化窗口之后）＋`RecoveryInitializationWindow`（`INITIALIZATION` 根覆盖 bean 构造与启动 runner，就绪时退役）；未改启动消费者。现场（实例 19462，已删探针替身）带 admission **真实启动成功**且 S3/S4 判据全绿，同轮另证清理守卫启动失败且端口未占用；全量单测 2806 项 0 失败、恢复链 25 类 696 项 0 失败。未做：阶段 0 安全前置（第 8–12 行）、锁并发（第 13 行）、拒绝码 HTTP 映射、入口 IT 重基。开关仍默认 false，K1 仍未通过；另附 S5 开工前看板归档。

- [R09 C2 B1-3 S4：multipart 与错误分派判据](R09_C2_B1_3_S4_MULTIPART_2026-09-29.md)：**无需产品改动**——真实容器观测证明 multipart 解析/暂存、handler、Session 保存与错误分派都在 S3 的同一请求根内（解析期 `BOUND`、暂存 1 个文件、请求后容器清理、根数＝请求数、14 次保存全 `BOUND`、无泄漏）；全量单测 2801 项 0 失败、恢复链 25 类 696 项 0 失败。发现：multipart 超限部件为容器级拒绝（观测 500 而非应用 413 映射）。**B1-3 S1–S4 全部完成**；开关仍默认 false，K1 仍未通过；另附 S4 开工前看板归档。

- [R09 C2 B1-3 S3：请求根过滤器与真实 Session 覆盖](R09_C2_B1_3_S3_REQUEST_ROOT_SESSION_2026-09-29.md)：`RecoveryRequestAdmissionFilter`＋`RecoveryRequestRootListener`＋`registry.admit`（一请求一根、每次分派绑定、ASYNC/ERROR/嵌套重分派复用同根）；真实容器＋Spring Session JDBC 现场判据通过（session/error/async 三步、根数＝请求数、10 次 Session 保存全部在存活根内、轨迹 REQUEST→ERROR→ASYNC、无根泄漏）；全量单测 2800 项 0 失败、恢复链 25 类 696 项 0 失败。发现未修：启动期无根合法能力缺失（开关打开即 fail closed）、锁非嵌套致并发接纳受限、拒绝码 HTTP 映射未实现。**S4 未开工，开关仍默认 false，K1 仍未通过**；另附 S3 开工前看板归档。

- [R09 C2 B1-3 S2：请求根数据源](R09_C2_B1_3_S2_REQUEST_ROOT_2026-09-29.md)：`RecoveryRequestAdmittedDataSource` 成为唯一 `@Primary`（六码、永不回退旧连接层），旧类降级并由静态白名单强制；`RecoveryOperationRegistry/Lease/SubLease/Token/Kind/Phase/SubOperation` 提供根登记与分派绑定。两级验证：定向单测 47/0（旧类 17/17 仍绿）、全量单测 2790 项 0 失败（无白名单）、恢复链 IT 25 类 696 项 0 失败 / 0 跳过（全新实例 19453）。**S3/S4 未开工，开关仍默认 false，K1 仍未通过**；入口 IT live/unknown 检查待 S3 重基。另附 S2 开工前看板归档。

- [R09 C2 B1-2 跨进程围栏与真实 Authority](R09_C2_B1_2_FENCE_2026-09-29.md)：规则/实现/多 JVM 矩阵、真实效果与故障注入分离、W 与全未确认分支保留；默认关闭，K1 仍未通过。
- [B1-2 开工前看板归档](ORCHESTRATION_STATE_before_R09_C2_B1_2_2026-09-29.md)。

- [R09 C2 B1-1 对抗性覆盖复核](R09_C2_B1_1_ADVERSARIAL_2026-09-29.md)：三处漏口、门内修正、九项清单、真实反例及默认关闭、逐轮失败留存；另附[600 个接口签名表](R09_C2_B1_1_JDBC_SURFACE_2026-09-29.md)。K1 仍未通过，B1 整体未完成。
- [对抗性复核过程断点](ORCHESTRATION_STATE_before_R09_C2_B1_1_ADVERSARIAL_CLOSE_2026-09-29.md)：保留验证进行中与编译失败断点。
- [对抗性复核前看板原区](ORCHESTRATION_STATE_before_R09_C2_B1_1_ADVERSARIAL_2026-09-29.md)：原样保留前轮当前区。

- [R09 C2 B1-1 效果门第一切片](R09_C2_B1_1_EFFECT_GATE_2026-09-29.md)：独立有限授权；规则/实现/用例唯一映射、真实驱动对照、默认关闭与缺权威启动失败证明、隔离身份及逐轮失败保留。126 项定向单测、35 项真实库 IT 通过；MODEL 权威输入不代替跨进程事实。默认关闭、未接线、未部署、未开生产开关；K1 仍未通过，B1 整体未完成，不进入 B1-2。
- [B1-1 前看板原文](ORCHESTRATION_STATE_before_R09_C2_B1_1_2026-09-29.md)：保留交接态原文及原相对链接，仅历史快照。
- [交接前看板原文](ORCHESTRATION_STATE_before_R09_C2_HANDOFF_2026-09-29.md)：保留 DSH 收口前的看板当前区原文（**收口轮 2026-09-29：DSH 停止推进，写权移交 Codex**；K1 仍未通过；单一下一步＝B1-1 待用户明确授权）。
- [R09 C2 G0-K1-3 遗留卫生修正](R09_C2_G0_K1_3_HYGIENE_2026-09-29.md)（纯文档/测试卫生；**B1-1 未获授权、保持停止**）：①[验收记录](R09_C2_G0_ACCEPTANCE_2026-09-28.md) 中“真实 FileLock 轮”段落的旧“矛盾阻断”表述**已就地更正**并指向更正轮；②`G0K1WriterLeaseIdentityRealIT.v4_…` 对**未入库** `.local` 路径的硬断言改为**只记录存在性** ⇒ 干净检出可复现。验证：`runA-clean-sim`（父目录不含上一轮证据）exit 0 / IT 5 项 0 失败 / `previous_round_evidence_present=false`；`runB-normal` exit 0 / 5 项 0 失败 / `=true`；`runC-test` 定向单测 **60 项 0 失败**。未改 `src/main`、未开生产开关；K1 仍未通过、B1 仍待授权。
- [R09 C2 G0-K1-3 更正与闭合轮（锁文件删除/替换走产品路径复验）](R09_C2_G0_K1_3_CORRECTION_2026-09-29.md)（独立有限授权）：真实子JVM走**产品路径**（`RecoveryControlLock.locked`→`RecoveryWriterLease.acquire`→`RecoveryWriterProtocol.validate`原生身份校验），`integrationTest`**5项0失败**（16行TSV：REAL12/MODEL4；失败轮2次保留）。**V1a同名替换⇒`CONTROL_WRITER_LOCK_REPLACED`（3/3）**、**V1b缺失⇒`CONTROL_FILE_MISSING`（3/3）**、不自动重建、无第二持锁者 ⇒ `writers.lock`为**已缓解/已覆盖**，**上一轮“矛盾A/B（阻断）”定性撤回**（原因：`fileKey()`在Windows为空＋未走产品路径＋用例不断言）；`fenced.lock`转**B1承重需求**；保留**可用性威胁**。**K1仍未通过；B1待授权**。
- [R09 C2 恢复链 IT 分批可解读跑批](R09_C2_IT_BATCH_2026-09-29.md)：`tools/r09-recovery-it-batch.ps1`＋一次实跑 24 类/683 项/0 失败；IT-BATCH-PIN 已修（守卫契约化）并在全新专用实例复跑 25 类/696 项/0 失败（恢复链 26 类仅 C1 HTTP 类不在批内）。
- [R09 C2 B1-2 认证锚点补做](R09_C2_B1_2_ANCHOR_SPEC_2026-09-29.md)：方案 A 的记录锚点部分**已实施并验证**（H3 闭合、跨进程缺锚点即启动失败）；H3′（删除记录）亦已由锚定操作索引闭合；剩余为锚点秘密保管/轮换的运维政策。
- [R09 C2 B1-2 P5 认证锚点补充规格](R09_C2_B1_2_ANCHOR_SPEC_2026-09-29.md)：登记 H3／H3′（删除记录）两条已实测放行变体的闭合方案与决定项；**未获决定前不实施**。
- [R09 C2 B1-2 对抗性复核](R09_C2_B1_2_ADVERSARIAL_2026-09-29.md)（DSH 接手 Codex 被 403 中断的会话；同一会话实施＋自审）：H1 本机缓存未绑定文件身份、H2 按文件名跳过审计标记、H3 租约 owner 引用可改写为活属主 —— 前两处已修并验证（3/3、6/6 拒绝），H3 未解决并登记 `DEBT-R09-C2-LEASE-ANCHOR`。最终单测 2038/0、并集 IT 662/0/0；K1 仍未通过、未进入 B1-3。
- [R09 C2 G0-K1-3 真实跨进程 FileLock 验证](R09_C2_G0_K1_3_REAL_FILELOCK_REVERIFY_2026-09-29.md)（独立有限授权）：真实独立JVM子进程（Windows11/NTFS、Java21.0.11；stdout/stderr重定向到文件、无管道），10场景×3重复、`integrationTest` 0失败（52行TSV）。**通过**：F1′（跨进程共享共存＋排他必失败）、F3（`release()`/正常退出可再获取）、F5（`destroyForcibly`/`Runtime.halt(70)`后OS释放且残留文件≠持锁）、第三方无持锁者查询、同JVM重叠抛`OverlappingFileLockException`。**S8/S9的“矛盾阻断”定性已被更正轮按产品路径撤回**（文首有更正块）。
- [R09 C2 G0-K1-1 真实 JDBC 驱动特征验证](R09_C2_G0_K1_REAL_JDBC_REVERIFY_2026-09-29.md)（独立有限授权）：隔离 MySQL 8.0.46＋Connector/J（端口19438、独立datadir/账号/schema；系统3306未使用）；`test`60项0失败、`integrationTest`**13项0失败**、27条`G0K1REAL`记录；**`getAutoCommit()`非提交证据**；**DDL隐式提交**（⇒close期rollback假设列入B1待实施需求）。**K1仍未通过**。
- [R09 C2-G0 K1 授权包（B1 授权前证据封存）](R09_C2_G0_K1_AUTHORIZATION_PACKET_2026-09-28.md)：纯文档封存`K1 仍未通过`的边界、五类验证分类、证据清单、未验边界、B1范围与排除（B9分离）、技术性前置条件与影响/风险边界。**为授权准备，不代表B1获批或运行验收**。
- [R09 C2-G0 K1 模型最终复验](R09_C2_G0_K1_MODEL_FINAL_REVERIFY_2026-09-28.md)：围栏矩阵按**观察时状态**；时序三条轨迹严格断言；JDBC**规则行↔测试方法唯一映射**双向校验（20规则行/17方法/方法名唯一）；`run2` 60项0失败（G0K1 37方法，4组负控分存）。
- [R09 C2-G0 K1 模型更正轮](R09_C2_G0_K1_MODEL_CORRECTION_2026-09-28.md)：围栏矩阵改状态迁移模型、时序加显式调度点、JDBC补可执行信息流；登记第2次失败XML缺失（未补造）；证据改逐轮独立目录。
- [R09 C2-G0 K1 测试侧模型复验](R09_C2_G0_K1_MODEL_REVERIFY_2026-09-28.md)：四项反例（①②③现有代理实测、④文档静态矛盾）与W反例有效；**文首有更正块**。
- [R09 C2-G0 v5 定向收敛与设计复验](R09_C2_G0_V5_REVERIFY_2026-09-28.md)：以`d656ab9`为基线仅收敛K1（冻结JDBC效果入口、`fenced.lock`原子观察、F3改用`FileLock.release()`、六行失败矩阵、逐记录事件语义与命名窗口W）。
- [R09 C2-G0 设计定稿 v5](R09_C2_G0_DESIGN_2026-09-28.md)：经 K1 收敛、模型三轮、真实驱动轮、真实文件锁轮与更正轮修订后的定稿（§4.2 效果门冻结表＋真实驱动证据、§5.3–§5.5 观察时依据与失败矩阵＋真实跨进程证据、§6.5 INITIAL 关闭意图、§9 真实入口清单、§10.2 S13/S17–S21/S20-u）。零生产代码；**K1 未通过，B1 未获授权**。
- [R09 C2-G0 验收与阻断记录 v5](R09_C2_G0_ACCEPTANCE_2026-09-28.md)：**设计/规范验收，非运行验收**；含测试侧各轮执行表、**验证类别（五类必须分开）**、覆盖率映射、授权包轮／真实驱动轮／真实文件锁轮／更正轮声明。
- [R09 C2-G0 v4 定向补正与设计复验](R09_C2_G0_V4_REVERIFY_2026-09-28.md)：K1–K4 首轮（历史；K1 判定与章节检查表述已被取代/撤回）。
- [R09 C2-G0 残余阻断补正与设计复验](R09_C2_G0_RESIDUAL_BLOCKERS_2026-09-28.md)：R1–R5（历史）。
- [R09 C2-G0 定向纠错与设计复验](R09_C2_G0_CORRECTION_REVERIFY_2026-09-28.md)：B01–B06（历史）。
- [更正轮前看板原文](ORCHESTRATION_STATE_before_R09_C2_G0_K1_3_CORRECTION_2026-09-29.md)：保留真实文件锁轮断点原文。
- [真实文件锁轮前看板原文](ORCHESTRATION_STATE_before_R09_C2_G0_K1_3_REAL_FILELOCK_2026-09-29.md)：保留真实驱动轮断点原文。
- [真实驱动轮前看板原文](ORCHESTRATION_STATE_before_R09_C2_G0_K1_REAL_JDBC_2026-09-29.md)：保留 K1 授权包轮断点原文。
- [授权包轮前看板原文](ORCHESTRATION_STATE_before_R09_C2_G0_K1_AUTHORIZATION_2026-09-28.md)：保留 K1 模型最终复验轮断点原文。
- [K1模型最终复验前看板原文](ORCHESTRATION_STATE_before_R09_C2_G0_K1_MODEL_FINAL_2026-09-28.md)：保留 K1 模型更正轮断点原文。
- [K1模型更正前看板原文](ORCHESTRATION_STATE_before_R09_C2_G0_K1_MODEL_CORRECTION_2026-09-28.md)：保留 K1 模型复验轮断点原文。
- [K1模型轮前看板原文](ORCHESTRATION_STATE_before_R09_C2_G0_K1_MODEL_2026-09-28.md)：保留 G0 定稿 v5 断点原文。
- [G0 v5收敛前看板原文](ORCHESTRATION_STATE_before_R09_C2_G0_V5_2026-09-28.md)：保留 G0 定稿 v4 断点原文。
- [G0 v4补正前看板原文](ORCHESTRATION_STATE_before_R09_C2_G0_V4_2026-09-28.md)：保留 G0 定稿 v3 断点原文。
- [G0残余补正前看板原文](ORCHESTRATION_STATE_before_R09_C2_G0_RESIDUAL_2026-09-28.md)：保留 G0 定稿 v2 断点原文。
- [G0纠错前看板原文](ORCHESTRATION_STATE_before_R09_C2_G0_CORRECTION_2026-09-28.md)：保留 G0 定稿 v1 断点原文。
- [G0设计定稿前看板原文](ORCHESTRATION_STATE_before_R09_C2_G0_DESIGN_2026-09-28.md)：保留Codex交回DSH收尾断点原文。
- [R09 C2 G0 范围与验收提案](R09_C2_G0_SCOPE_PROPOSAL_2026-09-28.md)：**已由[设计定稿](R09_C2_G0_DESIGN_2026-09-28.md)取代**，保留原始现状判断与待决清单以可追溯。
- [Codex → DSH 会话交接](SESSION_HANDOFF_2026-09-28.md)：本轮只收尾；50个接续提交、各线状态、精确未验边界、单一接手建议与写权说明。
- [DSH → Codex 原交接](SESSION_HANDOFF_DSH_TO_CODEX_ORIGINAL_2026-09-28.md)：逐字保存旧交接，仅历史，不恢复D片3未做及PDF未读等旧结论。
- [DSH切换工作树清点](DSH_SWITCH_WORKTREE_AUDIT_2026-09-28.md)：34树坐标，6棵有改动树/138个文件及环境监听；只盘点保护，不清理旧树。
- [交回DSH前看板原文](ORCHESTRATION_STATE_before_DSH_RETURN_2026-09-28.md)：保留C1验收后的旧断点。
- [R09_C1_REVERIFY_2026-09-28.md](R09_C1_REVERIFY_2026-09-28.md)：07ddf93修复参数/同类扫描、冒烟2绿；559缺PROCESS红保留，560真HTTP/真MySQL与105连接回归通过，124表原事实守恒；列上下文重启/故障注入/现场等精确边界，C2待授权。
- [C1真实验收前看板原文](ORCHESTRATION_STATE_before_R09_C1_HTTP_ACCEPTANCE_2026-09-28.md)：保存冒烟已绿、尚未执行559的断点。
- [C1冒烟通过前看板原文](ORCHESTRATION_STATE_before_R09_C1_SMOKE_PASS_2026-09-28.md)：保存脚本修复已完成、尚未启动的断点。
- [C1脚本修复/复验前看板原文](ORCHESTRATION_STATE_before_R09_C1_REVERIFY_2026-09-28.md)：保留上轮准备失败的完整断点，不恢复历史指令。

- [R09_C1_SMOKE_BLOCKER_2026-09-28.md](R09_C1_SMOKE_BLOCKER_2026-09-28.md)：0bcf24c新增完整应用冒烟入口，编译通过；本机MySQL主机参数误解析导致准备失败，Spring/IT未执行，559未分配，按用户边界停止，原样错误及清理证据保留。
- [C1冒烟前看板原文](ORCHESTRATION_STATE_before_R09_C1_SMOKE_2026-09-28.md)：保留两批实现及557/558红的收尾断点，历史指令不恢复生效。

- [R09_C1_INITIAL_REGISTRATION_BATCH2_2026-09-28.md](R09_C1_INITIAL_REGISTRATION_BATCH2_2026-09-28.md)：c974188接入初次NORMAL双侧协调器/新存储；557/558重复HTTP mapping启动失败，修正探针后78项单测绿，C1真实验收未完成，红证据及接续条件完整保留。
- [R09_C1_INITIAL_PROTOCOL_BATCH1_2026-09-28.md](R09_C1_INITIAL_PROTOCOL_BATCH1_2026-09-28.md)：37beeaa仅新增独立库外模型/耐久存储与测试，22项绿；c146f5f记录协议、可信原件及第二批DB存储边界。
- [C1第二批收尾前看板原文](ORCHESTRATION_STATE_before_R09_C1_BATCH2_CLOSE_2026-09-28.md)：保存评估时断点；首批按只新增约束延后既有文档同步，历史指令不恢复生效。

- [R09_C1_SCOPE_ASSESSMENT_2026-09-28.md](R09_C1_SCOPE_ASSESSMENT_2026-09-28.md)：独立初次NORMAL范围判c（兼b）；既有组件仅覆盖关闭链后的登记，需新组件/生产适配，按用户边界停报；含双侧证据、文件清单、3–4轮估算及未验，无代码/IT。
- [C1范围评估前看板原文](ORCHESTRATION_STATE_before_R09_C1_ASSESSMENT_2026-09-28.md)：保留C2入口评估收尾断点，历史指令不恢复生效。

- [R09_C2_ENTRY_ASSESSMENT_2026-09-28.md](R09_C2_ENTRY_ASSESSMENT_2026-09-28.md)：逐入口源码/生产面/前置/最小验收，区分C1历史组件与未完成双侧证据；大块实现按入口分批，建议先公共依赖再HTTP/Session。仅评估、无代码/测试，实施待授权。
- [C2入口评估前看板原文](ORCHESTRATION_STATE_before_R09_C2_ASSESSMENT_2026-09-28.md)：保留A3收尾断点，旧推进指令不恢复生效。

- [A3_REGRESSION_CLOSURE_2026-09-28.md](A3_REGRESSION_CLOSURE_2026-09-28.md)：A3主体完成；待核69类中40类已覆盖（39全绿、526待专项），21类转发布前完整回归清单，停止本阶段profile配置与补跑。
- [A3收尾前看板原文](ORCHESTRATION_STATE_before_A3_CLOSURE_2026-09-28.md)：保留Ledger配置前停报断点，旧推进指令不恢复生效。

- [A3_LEDGER_11_PREFLIGHT_BLOCKER_2026-09-28.md](A3_LEDGER_11_PREFLIGHT_BLOCKER_2026-09-28.md)：11类配置前复核触发“需要改守卫即停报”；精确旧datadir非空，新隔离profile未实施，11类全部未执行、仍21类未跑；另10类前置列明，seed标记未删。
- [Ledger 11类配置前看板原文](ORCHESTRATION_STATE_before_A3_LEDGER_11_PREFLIGHT_2026-09-28.md)：保留环境评估断点，历史指令不恢复生效。

- [A3_SPECIAL_ENV_ASSESSMENT_2026-09-28.md](A3_SPECIAL_ENV_ASSESSMENT_2026-09-28.md)：剩余21类精确环境/阶段/原件要求与共用评估；值得分组，建议先Ledger作者2＋验证9类，配置超出换库名，3–5轮仅条件估算。本轮只读、未执行。
- [专用环境评估前看板原文](ORCHESTRATION_STATE_before_A3_SPECIAL_ENV_ASSESSMENT_2026-09-28.md)：保留两批A3回归收尾断点，历史指令不恢复生效。

- [A3_POST526_REGRESSION_2026-09-28.md](A3_POST526_REGRESSION_2026-09-28.md)：526另挂专项后两批接续回归；531–556共26类107项全绿，余21类专项环境/原输入待接续；529/530入口失败、545编排编码失败保留，真实MySQL+MockMvc。
- [本次第2批收尾前看板原文](ORCHESTRATION_STATE_before_A3_POST526_BATCH2_CLOSE_2026-09-28.md)：保留第1批断点，历史指令不恢复生效。

- [A3_526_LOCK_PENDING_SCOPE_2026-09-28.md](A3_526_LOCK_PENDING_SCOPE_2026-09-28.md)：用户将526按疑似缺陷挂待专项，列真实业务入口/执行计划/跳锁/竞争验收条件，不占其他A3回归轮次。

- [A3_526_LOCK_DIAGNOSIS_2026-09-28.md](A3_526_LOCK_DIAGNOSIS_2026-09-28.md)：527绿/528红原样重现，43份锁快照及线程栈确认SKIP LOCKED查询等待pending主键锁；原定性记录保留，后按用户决定改挂疑似缺陷待专项，执行计划与真实写入口未验。

- [A3_NON_E2A_WAVES_2026-09-28.md](A3_NON_E2A_WAVES_2026-09-28.md)：新第1批513–526执行14类，37项36通过/1失败；条码并发commit timeout待定性停报，后续批次未启动。
- [A3_NON_E2A_REMAINING_2026-09-28.md](A3_NON_E2A_REMAINING_2026-09-28.md)：原47类中本次26类绿、21类待跑的逐类目录；8类R08历史验收去重，A3-526疑似缺陷单列待专项，明确各族环境和输入条件。

- [R09_C2_PROJECT_SCOPE_2026-09-28.md](R09_C2_PROJECT_SCOPE_2026-09-28.md)：应用入口接线独立范围、C1依赖与分段验收建议；仅记录，实施待授权，无代码变化。

- [R06_JWT_CLASSIFICATION_FIX_2026-09-28.md](R06_JWT_CLASSIFICATION_FIX_2026-09-28.md)：JWT定性a、39d1b84最小映射修复；单测6项与501–505共39项绿，红记录及同类静态候选清单保留。

- [A3_NON_E2A_REGRESSION_2026-09-28.md](A3_NON_E2A_REGRESSION_2026-09-28.md)：496–500停报红保留；JWT修复后501复验及506–512余7类34项绿，原首批12类均有通过记录，后续未启动。

- [R07_3_HEADER_WITHDRAW_BLOCKER_2026-09-28.md](R07_3_HEADER_WITHDRAW_BLOCKER_2026-09-28.md)：合法零账抬头被WithdrawConfirm空账守卫拒绝；静态确认、未跑HTTP组合，R07独立待办，只记录不修。

- [R08_D_DELIVERY_ACCEPTANCE_2026-09-28.md](R08_D_DELIVERY_ACCEPTANCE_2026-09-28.md)：用户按已验范围接受D整块交付；片1/2/3、四项基本边界及三项修复的证据与范围外限制。

- [R08_D_HEADER_DESIGN_AND_FIX_2026-09-28.md](R08_D_HEADER_DESIGN_AND_FIX_2026-09-28.md)：B3定性A、68a43f8抬头修复及495/run31验收；凭据扫描发现R07抬头撤销空账静态阻断，已停报。

- [R08_D_WITHDRAW_DESIGN_AND_FIX_2026-09-28.md](R08_D_WITHDRAW_DESIGN_AND_FIX_2026-09-28.md)：撤销版本定性A及493修复验收；494抬头零明细凭据缺陷停工，包含全部失败与未验。

- [R08_D_BOUNDARIES_2026-09-28.md](R08_D_BOUNDARIES_2026-09-28.md)：四项基本样本通过证据索引（489/490/493/495）；历史红及组合未验保留，交付决定见D交付确认。

- [R09_JAVA_CONTROL_ACCEPTANCE_2026-09-26.md](R09_JAVA_CONTROL_ACCEPTANCE_2026-09-26.md)：外部可信checkpoint/完整链/严格路径与Python→Java原件消费，Windows/Linux各16项真实进程锁；失败和共享接入限制保留。

- [R06_UNSUBMITTED_SOURCE_ACCEPTANCE_2026-09-25.md](R06_UNSUBMITTED_SOURCE_ACCEPTANCE_2026-09-25.md)：2026-09-26完成其他未提交FORMAL/TEMP原事实核验，attempt67非空MySQL/真实HTTP、613单测通过/1条件skip，64/65失败保留。

- [R06_DRAFT_ARCHIVE_TRUST_ACCEPTANCE_2026-09-25.md](R06_DRAFT_ARCHIVE_TRUST_ACCEPTANCE_2026-09-25.md)：草稿快照历史归档可信核验，attempt63实际Android归档/换钥重启HTTP，62失败资料保留。

- [R06_NONZERO_CANDIDATE_ACCEPTANCE_2026-09-25.md](R06_NONZERO_CANDIDATE_ACCEPTANCE_2026-09-25.md)：21候选五页真实HTTP、独立DB投影与Python54项、非零短装批准/普通确认阻断；完整单测576通过/1条件跳过。

- [管理文档精简前原文（2026-09-25）](management-2026-09-25-before-refresh/README.md)：七份工作区原文按原字节保存，manifest逐项SHA-256校验；包含旧看板历史、派工指令、失败与未验。本次接续核对与全部未提交路径见[快照](MANAGEMENT_REFRESH_2026-09-25_WORKTREE_STATUS.md)，当前事实仍只读主看板。

- [R06_DRAFT_TRANSFER_ACCEPTANCE_2026-09-25.md](R06_DRAFT_TRANSFER_ACCEPTANCE_2026-09-25.md)：Room26原键/回执耐久恢复、Android424/APK/lint及attempt57；对应WORKTREE_STATUS及before_draft_transfer保存路径与旧当前区。

- [R06_DRAFT_INTAKE_ACCEPTANCE_2026-09-25.md](R06_DRAFT_INTAKE_ACCEPTANCE_2026-09-25.md)：V37独立原件接收/可信准备/原响应，服务端577、Android408、attempt56真实HTTP；对应WORKTREE_STATUS与before_draft_intake保存路径和旧当前区。

- [R06_DRAFT_SNAPSHOT_ACCEPTANCE_2026-09-25.md](R06_DRAFT_SNAPSHOT_ACCEPTANCE_2026-09-25.md)：原件根和全设备边界结构闭合、Android406/服务端549；对应WORKTREE_STATUS及before_draft_snapshot保路径和前当前区。

- [R06_DRAFT_SIGNATURE_ACCEPTANCE_2026-09-25.md](R06_DRAFT_SIGNATURE_ACCEPTANCE_2026-09-25.md)：Room25原机事务签名/旧原件不补签、Android400与服务端522全套；对应WORKTREE_STATUS和before_draft_signature保存路径与旧当前区。

- [R06_LOCAL_DRAFT_STOP_ACCEPTANCE_2026-09-25.md](R06_LOCAL_DRAFT_STOP_ACCEPTANCE_2026-09-25.md)：Room24未冻结/零包/未决本机停止原件、23定向/390全套及attempt54冻结HTTP兼容；对应WORKTREE_STATUS与before_local_draft_stop保存全路径和旧当前区。

- [R08_TEMPORARY_EXPORT_ACCEPTANCE_2026-09-25.md](R08_TEMPORARY_EXPORT_ACCEPTANCE_2026-09-25.md)：TEMP原确认导出、全套及attempt53真实HTTP/Chrome/12文件验证；对应WORKTREE_STATUS和before_temporary_export保存全路径及旧当前区。

- [R06_ANDROID_PRODUCT_SUCCESS_ACCEPTANCE_2026-09-25.md](R06_ANDROID_PRODUCT_SUCCESS_ACCEPTANCE_2026-09-25.md)：Android产品成功RAW消费/事务显示/重开复核，367项、APK/lint及attempt48双端1/1和6/6；对应WORKTREE_STATUS及before_android_product_success保存路径和原当前区。

- [R09_TEMPORARY_ARCHIVE_RECOVERY_ACCEPTANCE_2026-09-25.md](R09_TEMPORARY_ARCHIVE_RECOVERY_ACCEPTANCE_2026-09-25.md)：TEMP V2/FORMAL V1混合归档快照及原确认/短装引用恢复；attempt47 IT1/1、11故障及真实HTTP。对应WORKTREE_STATUS与before_temporary_archive_recovery保存全路径和原当前区。

- [R06_RECORDED_DECISION_CONSUMER_ACCEPTANCE_2026-09-25.md](R06_RECORDED_DECISION_CONSUMER_ACCEPTANCE_2026-09-25.md)：首轮零候选真实决定独立消费者，attempt43实库/HTTP/Python46通过；对应WORKTREE_STATUS及before_recorded_decision_consumer保存路径与原当前区。

- [R06_TEMPORARY_CANDIDATES_ACCEPTANCE_2026-09-25.md](R06_TEMPORARY_CANDIDATES_ACCEPTANCE_2026-09-25.md)：正式侧成功/待审TEMP反向提示、稳定回执入口、真实HTTP及Chrome组合；对应WORKTREE_STATUS保存全路径，before_temporary_candidates保存前当前区。

- [R06_TEMPORARY_ARCHIVE_ACCEPTANCE_2026-09-25.md](R06_TEMPORARY_ARCHIVE_ACCEPTANCE_2026-09-25.md)：Room23 TEMP归档V2及同一Android实际冻结至内部成功/归档组合；对应WORKTREE_STATUS保存全路径，before_temporary_archive保存前当前区。

- [R06_TEMPORARY_SUCCESS_ACCEPTANCE_2026-09-25.md](R06_TEMPORARY_SUCCESS_ACCEPTANCE_2026-09-25.md)：Room22 TEMP成功原件严格消费/耐久、非空迁移与独立MySQL/真实HTTP；对应WORKTREE_STATUS保存全路径，before_temporary_success保存原当前区。

- [R06_TEMPORARY_CONFIRMATION_ACCEPTANCE_2026-09-25.md](R06_TEMPORARY_CONFIRMATION_ACCEPTANCE_2026-09-25.md)：首轮正常/独立短装内部决定、原回执及当前效力、完整回滚/真实HTTP/五组MySQL竞争；对应WORKTREE_STATUS保存全路径，before_temporary_confirmation保存前当前区。

- [R06_TEMPORARY_ACCOUNTING_ACCEPTANCE_2026-09-25.md](R06_TEMPORARY_ACCOUNTING_ACCEPTANCE_2026-09-25.md)：TEMP共同记账内部原资格政策适配、失败回滚、真实HTTP与正式续作回归；对应WORKTREE_STATUS列全路径，before_temporary_accounting保存替换前看板。

- [R06_E2A_ABORT_ACCEPTANCE_2026-09-25.md](R06_E2A_ABORT_ACCEPTANCE_2026-09-25.md)：正文前/部分上传后ABORT独立证据、STOP来源绑定、真实HTTP/非空MySQL与失败原件；对应WORKTREE_STATUS保存全部路径，before_abort_evidence保存替换前当前区。

- [R06_E2A_ROOM20_ACCEPTANCE_2026-09-25.md](R06_E2A_ROOM20_ACCEPTANCE_2026-09-25.md)：原请求/原键/STOP签名耐久当次验收；两树路径见同目录 R06_E2A_ROOM20_WORKTREE_STATUS_2026-09-25.md，前当前区见 ORCHESTRATION_STATE_before_android_room20_2026-09-25.md。

- [R06_E2A_CROSS_SOURCE_ACCEPTANCE_2026-09-25.md](R06_E2A_CROSS_SOURCE_ACCEPTANCE_2026-09-25.md)：当前会话跨来源事实、VERIFY 事务修复及独立非空 MySQL/真实 HTTP 结果与限制。
- [R06_E2A_CROSS_SOURCE_WORKTREE_STATUS_2026-09-25.md](R06_E2A_CROSS_SOURCE_WORKTREE_STATUS_2026-09-25.md)：该子项收口两树未提交路径。
- [ORCHESTRATION_STATE_before_cross_source_acceptance_2026-09-25.md](ORCHESTRATION_STATE_before_cross_source_acceptance_2026-09-25.md)：跨来源验收前看板原文，包含旧红测与未验，不作当前事实。

本目录保存已从根目录协作规则移出的完整历史，供需要时定向核查，不作为当前状态入口。

- [ORCHESTRATION_STATE_before_session_handoff_2026-09-24.md](ORCHESTRATION_STATE_before_session_handoff_2026-09-24.md)：新会话交接前当前区原文；最新状态仍以看板顶部为准。
- [R06_E2A_SESSION_HANDOFF_STATUS_2026-09-24.md](R06_E2A_SESSION_HANDOFF_STATUS_2026-09-24.md)：两棵原工作树交接前未提交路径快照。

- [ORCHESTRATION_STATE_before_android_archive_snapshot_2026-09-24.md](ORCHESTRATION_STATE_before_android_archive_snapshot_2026-09-24.md)：原机签名归档快照前的当前区原文。

- [ORCHESTRATION_STATE_before_android_snapshot_2026-09-24.md](ORCHESTRATION_STATE_before_android_snapshot_2026-09-24.md)：Room19 原机全设备快照前的当前区原文。

- [ORCHESTRATION_STATE_before_room19_stop_2026-09-24.md](ORCHESTRATION_STATE_before_room19_stop_2026-09-24.md)：Room19 原机 STOP 同事务落盘前的当前区原文。
- [R06_E2A_ROOM19_STATUS_2026-09-24.md](R06_E2A_ROOM19_STATUS_2026-09-24.md)：本轮两树未提交路径快照与写权断点。

- [ORCHESTRATION_STATE_before_android_transfer_2026-09-24.md](ORCHESTRATION_STATE_before_android_transfer_2026-09-24.md)：E2a Android 增量转入 Room18 主树前的当前状态原文。

- [ORCHESTRATION_STATE_before_android_coverage_2026-09-24.md](ORCHESTRATION_STATE_before_android_coverage_2026-09-24.md)：全设备审计/捕获连续前缀门槛前的当前区原文。

- [ORCHESTRATION_STATE_before_android_wire_2026-09-24.md](ORCHESTRATION_STATE_before_android_wire_2026-09-24.md)：Android E2a 线协议及 STOP 签名原语前的当前区原文。
- [R06_E2A_ANDROID_WIRE_STATUS_2026-09-24.md](R06_E2A_ANDROID_WIRE_STATUS_2026-09-24.md)：两树 Android 线协议增量的未提交路径快照。

- [ORCHESTRATION_STATE_before_history_archive_2026-09-24.md](ORCHESTRATION_STATE_before_history_archive_2026-09-24.md)：历史命令/归档签名轮换兼容前的当前区与两树路径快照。
- [ORCHESTRATION_STATE_before_k_writer_2026-09-24.md](ORCHESTRATION_STATE_before_k_writer_2026-09-24.md)：逐包 K 窄写者前的看板原文与两树未提交路径快照。
- [ORCHESTRATION_STATE_before_keyring_2026-09-24.md](ORCHESTRATION_STATE_before_keyring_2026-09-24.md)：历史公钥轮换前的看板原文与两树未提交路径快照。
- [ORCHESTRATION_STATE_before_source_read_2026-09-24.md](ORCHESTRATION_STATE_before_source_read_2026-09-24.md)：本轮逐包原字节链与原键只读前的看板原文；不作为当前状态。
- [R06_E2A_WORKTREE_STATUS_2026-09-24.md](R06_E2A_WORKTREE_STATUS_2026-09-24.md)：两树本轮管理更新前的未提交路径快照。

- [ORCHESTRATION_STATE_before_archive_coverage_2026-09-24.md](ORCHESTRATION_STATE_before_archive_coverage_2026-09-24.md)：签名归档与审计 PAGE 覆盖增量前的看板当前区原文；不作为当前状态。

- [ORCHESTRATION_STATE_before_audit_endpoint_2026-09-24.md](ORCHESTRATION_STATE_before_audit_endpoint_2026-09-24.md)：AUDIT 原件/原授权及签名 STOP 审计端点写入前的看板当前区原文；不作为当前状态。

- [ORCHESTRATION_STATE_before_audit_prefix_2026-09-24.md](ORCHESTRATION_STATE_before_audit_prefix_2026-09-24.md)：全设备库内审计前缀增量写入前的看板当前区原文；不作为当前状态。

- [ORCHESTRATION_STATE_before_stop_key_writer_2026-09-24.md](ORCHESTRATION_STATE_before_stop_key_writer_2026-09-24.md)：STOP 内部写入公钥来源对齐前的看板当前区原文；不作为当前状态。

- [ORCHESTRATION_STATE_before_direct_key_2026-09-24.md](ORCHESTRATION_STATE_before_direct_key_2026-09-24.md)：直接登记当前公钥修正、attempt2 失败和 attempt3 通过写入前的看板当前区原文；不作为当前状态。

- [ORCHESTRATION_STATE_before_paired_key_2026-09-24.md](ORCHESTRATION_STATE_before_paired_key_2026-09-24.md)：STOP 已存 OPEN/服务器配对公钥重验前的看板当前区原文；不作为当前状态。
- [ORCHESTRATION_STATE_before_raw_closure_2026-09-24.md](ORCHESTRATION_STATE_before_raw_closure_2026-09-24.md)：PAGE 原始对象/文件核对更新前的看板当前区原文；不作为当前状态。
- [ORCHESTRATION_STATE_before_stop_preflight_2026-09-24.md](ORCHESTRATION_STATE_before_stop_preflight_2026-09-24.md)：STOP 隔离实库写者和只读前置门槛更新前的看板当前区原文；不作为当前状态。
- [ORCHESTRATION_STATE_before_page_closure_2026-09-24.md](ORCHESTRATION_STATE_before_page_closure_2026-09-24.md)：PAGE 根只读核对前的看板当前区原文；不作为当前状态。
- [ORCHESTRATION_STATE_before_stop_persistence_2026-09-24.md](ORCHESTRATION_STATE_before_stop_persistence_2026-09-24.md)：STOP 独立持久接收及本轮自审结果写入前看板当前区原文；不作为当前状态。
- [ORCHESTRATION_STATE_before_v36_dynamic_2026-09-24.md](ORCHESTRATION_STATE_before_v36_dynamic_2026-09-24.md)：V36 动态迁移/OPEN 外键自审前的看板当前区原文；不作为当前状态。
- [ORCHESTRATION_STATE_before_stop_parser_2026-09-24.md](ORCHESTRATION_STATE_before_stop_parser_2026-09-24.md)：STOP 纯解析和 attempt4 写者验证收录前的看板当前区原文；不作为当前状态。
- [ORCHESTRATION_STATE_before_stop_compat_2026-09-24.md](ORCHESTRATION_STATE_before_stop_compat_2026-09-24.md)：STOP 事件标识协议兼容复审前的看板当前区原文；不作为当前状态。

- [AGENTS_2026-09-22.md](AGENTS_2026-09-22.md)：2026-09-22 精简前 `AGENTS.md` 的原文完整字节副本。其 SHA-256 为 `C6F6F8F7BDA490DACCDC23F9AA5C44FC6D1F0A2A8598288D20AEAA1372EE7999`。
- [ORCHESTRATION_STATE_before_e2a_attempt6_2026-09-23.md](ORCHESTRATION_STATE_before_e2a_attempt6_2026-09-23.md)：E2a attempt6 结果写入前，总调度看板旧当前区的原文快照；不作为当前状态。
- [ORCHESTRATION_STATE_before_e2a_trigger_preflight_2026-09-23.md](ORCHESTRATION_STATE_before_e2a_trigger_preflight_2026-09-23.md)：E2a 触发器预检续作写入前，总调度看板旧当前区的原文快照；不作为当前状态。
- [ORCHESTRATION_STATE_before_e2a_trigger_decision_2026-09-23.md](ORCHESTRATION_STATE_before_e2a_trigger_decision_2026-09-23.md)：E2a 触发器授权决策卡写入前，总调度看板旧当前区的原文快照；不作为当前状态。
- [ORCHESTRATION_STATE_before_e2a_attempt8_2026-09-23.md](ORCHESTRATION_STATE_before_e2a_attempt8_2026-09-23.md)：单助手接管及 E2a attempt8 动态验收写入前，旧当前区的原文快照；不作为当前状态。
- [ORCHESTRATION_STATE_before_e2a_independent_review_2026-09-23.md](ORCHESTRATION_STATE_before_e2a_independent_review_2026-09-23.md)：attempt8 独立证据复核与原样本完整单测写入前的当前区快照；不作为当前状态。
- [ORCHESTRATION_STATE_before_e2a_intake_read_2026-09-23.md](ORCHESTRATION_STATE_before_e2a_intake_read_2026-09-23.md)：E2a intake 查询只读事务与本轮单测写入前的当前区快照；不作为当前状态。
- [ORCHESTRATION_STATE_before_e2a_intake_recovery_2026-09-23.md](ORCHESTRATION_STATE_before_e2a_intake_recovery_2026-09-23.md)：E2a intake 冲突分类、回滚后独立重查与本轮单测写入前的当前区快照；不作为当前状态。
- [ORCHESTRATION_STATE_before_e2a_open_2026-09-24.md](ORCHESTRATION_STATE_before_e2a_open_2026-09-24.md)：E2a OPEN 接收及分页协议校验续作前的当前区原文快照；不作为当前状态。
- [ORCHESTRATION_STATE_before_e2a_page_read_2026-09-24.md](ORCHESTRATION_STATE_before_e2a_page_read_2026-09-24.md)：PAGE 解析、私有文件存储与已存 OPEN 绑定读取续作前的当前区原文快照；不作为当前状态。
- [ORCHESTRATION_STATE_before_e2a_page_write_2026-09-24.md](ORCHESTRATION_STATE_before_e2a_page_write_2026-09-24.md)：PAGE 写者和原始对象文件接收续作前的当前区原文快照；不作为当前状态。
- [ORCHESTRATION_STATE_before_e2a_item_stage_2026-09-24.md](ORCHESTRATION_STATE_before_e2a_item_stage_2026-09-24.md)：非空 PAGE item 严格解析及 V36 跨页索引续作前的当前区原文快照；不作为当前状态。
- 当前 Git、执行者、证据、环境和下一步只读 [总调度看板](../ORCHESTRATION_STATUS.md) 顶部 `CURRENT_STATE_START/END` 区段。
- 长期规则和新会话接续方式只读根目录 [AGENTS.md](../../AGENTS.md)；仓库操作规则见 [Git 管理说明](../GIT_WORKFLOW.md)。

归档文件不应被改写来表示新状态。阶段收尾和接续断点写入当前状态区段；需要保留的旧证据按专项文档和提交 SHA 定向引用。

原文字节副本中的相对链接仍按仓库根目录解释；归档位置不会改变这些链接的基准，也不得为修正链接而改写副本。
# 2026-09-25 Room27 产品队列

[停止产品入口与持久补传验收](R06_DRAFT_PRODUCT_ACCEPTANCE_2026-09-25.md)：本批实现/自审/最终444项及attempt59；非现场、非上线。接管前原当前区见 [归档](ORCHESTRATION_STATE_before_product_review_2026-09-25.md)。
# 2026-09-26 接续增量

- [R09 Job恢复代次屏障验收](R09_JOB_FENCE_ACCEPTANCE_2026-09-26.md)：V38旧Job保全、实例启动代次/持锁事务；attempt72专项+23回归、attempt71真实导出/浏览器，完整服务端618pass/1skip。

- [R09库外控制本地模块验收](R09_LOCAL_CONTROL_ACCEPTANCE_2026-09-26.md)：追加历史/CAS/进程锁及崩溃恢复，29新测试通过，完整R09 103通过/11跳过；不是共享门禁或生产恢复完成。
- [进入库外控制前看板快照](ORCHESTRATION_STATE_before_recovery_control_2026-09-26.md)。

## 自主开发（2026-09-30 起）

- [自主计划](AUTONOMOUS_PLAN_2026-09-30.md)：R06→R07 按真实断点拆分，保留政策/已验行为/生产边界。
- [自主进度](AUTONOMOUS_PROGRESS_2026-09-30.md)：S1 实际 HTTP 及关联回执独立消费验收、实现 SHA、失败/磁盘/设备证据；其余项未自动标完成。

## 自主接续（2026-10-01，Asia/Shanghai）

- [E07 受影响组合验收（644）](GOAL_E07_COMBINATION_644_ACCEPTANCE_2026-10-16.md)：新隔离实例 644 的真实 HTTP/MySQL、更正账务、撤回、导出和退库周期组合 1/1 通过，认证停机且项目端口清零；643 端口配置失败原件保留。本批仅组合开发验收，不计 E07 L2、D18 L3 或总目标。

- [S2/S4a 范围核对](AUTONOMOUS_SCOPE_AUDIT_2026-10-01.md)：历史复用与权限边界，不重复旧已验工作。
- [S4b 三次失败停报](AUTONOMOUS_PROGRESS_2026-10-01.md)：未完成源码、两次 IT 与一次单测失败、磁盘/设备及提交证据。


## 修订目标恢复（2026-10-01）

- [接续方案](AUTONOMOUS_PLAN_2026-10-01.md)与[恢复后报告 §11 起](AUTONOMOUS_PROGRESS_2026-10-01.md)：取消旧固定失败停止线，R1 既有守卫失败单列。
- [R06 正式单反向候选独立消费](R06_REVERSE_CANDIDATE_CONSUMER_2026-10-01.md)：真实 HTTP 与原始关系消费、失败/保全、回归及未验边界。
- [恢复前看板](ORCHESTRATION_STATE_before_RESUMED_2026-10-01.md)：旧 blocked 原结论保留，不恢复为现行授权。
- [R07 迁移后主管真实签名审批 HTTP 专项](R07_SUPERVISOR_SIGNED_HTTP_2026-10-01.md)：真实 HTTP 验收、资源与未验边界。

- [R07 分离身份误批撤销 HTTP 专项](R07_SUPERVISOR_WITHDRAWAL_HTTP_2026-10-01.md)：两阶段撤销真实 HTTP 验收、资源与未验边界。

## R07-P08-A (2026-10-01)

- [Correction append-only acceptance](AUTONOMOUS_PROGRESS_2026-10-01.md#16-r07-p08-a-append-only-guards-passed-2026-10-01): preserved 803/804 failures, 805 isolated MySQL 1/0/0/0, idempotence probe, offline checks, and server regression. P08-B commitment rollback and field/attachment boundaries remain unverified.

## R07-P08 review stop (2026-10-01)

- [P08 review correction and product-gap stop](R07_P08_REVIEW_STOP_2026-10-01.md): 805 execution artifacts are retained, but the acceptance conclusion is withdrawn because V51 omits primary-key immutability and the negative IT did not attribute rejection to the new triggers. No product fix or new attempt was run.

- [Android续作子项前看板](ORCHESTRATION_STATE_before_android_continuation_2026-10-06.md)：当次实现、失败/跳过及边界见自主进度第194条。
- [续作HTTP回执子项前看板](ORCHESTRATION_STATE_before_continuation_receipt_http_2026-10-06.md)：回执修复、真实HTTP及失败保全见自主进度第195条。
- [续作资格隔离子项前看板](ORCHESTRATION_STATE_before_continuation_intake_guard_2026-10-06.md)：原人原机/首轮复用拒绝、HTTP及受影响回归见自主进度第196条。
- [2026-10-07自主进度](AUTONOMOUS_PROGRESS_2026-10-07.md)：从已推送的受限续作子项接续新轮intake/记账。
- [D01–D18执行记录](D01_D18_EXECUTION_2026-10-07.md)：逐项隔离执行证据、失败及阻断；阶段看板快照见 [D05 前看板](ORCHESTRATION_STATE_before_D05_disposition_2026-10-07.md)、[D06 前看板](ORCHESTRATION_STATE_before_D06_temp_stop_2026-10-07.md)。
- [D06累计历史与多跳接续执行记录](D06_MULTIHOP_EXECUTION_2026-10-08.md)：1110真实多跳、受影响回归、产品页UI-04及失败/消费者证据；混合接续见下一入口，LOCAL-only仍未完成。
- [D06混合交接与续作执行记录](D06_MIXED_EXECUTION_2026-10-09.md)：1118混合链及1120反向四轮，原scan/owner、独立来源、精确SQL/Room尾故障、原件消费者及两端回归；失败/skip保全，LOCAL-only正向与前置拒绝仍未完成。
- [新轮唯一注册前看板](ORCHESTRATION_STATE_before_round_registration_2026-10-07.md)：原断点字节快照；唯一注册、前驱原文绑定和迁移证据见当日自主进度第198条。
- [累积证据验证前看板](ORCHESTRATION_STATE_before_round_evidence_2026-10-07.md)：当次纯验证器、受影响回归及边界见当日自主进度第199条。
- [实际intake开发前看板](ORCHESTRATION_STATE_before_round_accept_2026-10-07.md)：已推送子项及未验证intake接续见当日自主进度第200条。
- [D07 FORMAL故障换机开发记录](D07_FORMAL_FAULT_EXECUTION_2026-10-09.md)：接续全量快照、真实归档围栏/最终记账/重启、非空迁移、并发和独立工具消费；实际PDA页面/非空附件/跨源INSERT及R09未验分列，不计D07整体。
- [E06网页显式引用原冲突决定L1](GOAL_E06_CONFLICT_ASSOCIATION_WEB_L1_2026-10-14.md)：真实回执消费、Chrome原键恢复与全表守恒；冲突登记入口和E06组合继续。
- [E06跨任务实物冲突登记网页L1](GOAL_E06_CONFLICT_WEB_L1_2026-10-14.md)：完整各方/原键/明确编号实际Chrome与全表守恒；短装/多任务及组合继续。
- [E06增量冲突责任链网页L1](GOAL_E06_CONFLICT_EXTENSION_WEB_L1_2026-10-14.md)：全部各方显式追加、历史保全、丢响应/双页原键与全表精确增量；多任务及E06组合继续。

- [E07/O05同机恢复与加密副本](GOAL_E07_RESTORE_COPY_ACCEPTANCE_2026-10-14.md)：1541恢复逐表/逐文件守恒、4/4写前拒绝、O05 14/14专项与41/41回归及实际SAF CLI通过；同机开发演练，异地/现场/D18 L3/总目标继续。

- [P07历史钥及固定引用核验L1](GOAL_P07_KEYRING_ACCEPTANCE_2026-10-15.md)：19/19真实JVM原签→Python离线取回核验，双pin/全集/语义及守恒；不证明异地保管或E07整体。
- [E07闭合恢复跨截点前置验收](GOAL_E07_CLOSED_RECOVERY_ACCEPTANCE_2026-10-15.md)：新隔离实例1546/1547的闭合权威与投影组合通过，JDBC/dump/文件守恒及认证停机通过；1545 ACL失败和运行器误判保全；不计E07 L2、D14/D15整体或D18 L3。
- [D15升级检查点与恢复切片验收](GOAL_D15_UPGRADE_CHECKPOINT_ACCEPTANCE_2026-10-15.md)：1548连接耗尽失败完整保全，1549候选1/1，1550最终1/1；非空增量升级、SQL dump/import、storage/开发签名字节守恒、空伴随恢复与非空覆盖拒绝通过。仅开发切片，不计D15整体、E07 L2、D18 L3或总目标。
