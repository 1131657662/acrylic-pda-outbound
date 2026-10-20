# 历史归档索引

本文只做历史证据的短索引：当前状态、活跃写权和下一动作只看 [看板](../ORCHESTRATION_STATUS.md)，文档职责和零记忆顺序见 [项目文档地图](../PROJECT_DOCUMENT_MAP.md)。逐轮原索引已原样归档为 [2026-10-20 原历史索引](../archive/2026-10-20/HISTORY_README.md)。`GOAL_*.md` 验收报告不移动、不改写。

## 最近可复核的专项报告

- D13补[原来源只读HTTP/P5修复](GOAL_D13_SOURCE_EVIDENCE_READ_2026-10-20.md)：修复JSON_OBJECT/DATE_FORMAT有限分类漏项；最终run04 IT1/0/0/0，37业务GET、12组178表/8文件守恒，夹具授权完整恢复、原签名验真/P5后继STARTED。普通回归4798项零失败、19跳过单列。旧失败及run03夹具缺口保留；仅有界L1，非D13整体/最终候选。

- [D13多块回滚原件与重试](GOAL_D13_MULTICHUNK_ROLLBACK_RETRY_2026-10-20.md)：新隔离IT1/0/0/0、13 TCP/9块，审计尾500后180表守恒、仅新增完整原正文，原ID重试/重放签名账务一致，P5后继STARTED；非物理文件回滚、全生命周期或最终候选。

- [D13 FORMAL分块故障补证](GOAL_D13_FORMAL_CHUNK_FAULTS_2026-10-20.md)：新隔离IT1/0/0/0、11 TCP；并发幂等/审计尾回滚/锁后撤权及原签名、全库快照独立核同，P5后继STARTED；仅有界L1，原失败与未知围栏证据保持。

- [M71真实客户端与window02回退](DEPLOY_REAL_CLIENT_PATH_ASSESSMENT_2026-10-20.md)：真实账号归属确认；历史APK真机安装启动通过、固定候选Android编译失败；新维护检查失败后101表/220文件恢复核同，未真实演练/部署。
- [固定候选十九项跳过](DEPLOY_FIXED_CANDIDATE_SKIPPED_TESTS_2026-10-20.md)：同SHA原XML逐方法分类为环境2/条件17，全部不计通过；Windows异常具体根因不超证据归因。

- [D13 FORMAL分块文件入口](GOAL_D13_FORMAL_SUBMISSION_CHUNKS_2026-10-20.md)：P5下DATABASE()兼容修复，原IT及14 HTTP/9块/签名账务消费通过；完整普通XML4782项零失败、19跳过，ACL子进程环境修复有独立3项复验；首轮失败保留，非D13整体或最终候选。

- [O04原SAF包当前运行库消费](GOAL_O04_RETAINED_SAF_CURRENT_RUNTIME_CONSUMER_2026-10-20.md)：复用已有原包/历史覆盖，运行库变化后CLI一次exit0、460原事实断言/五表265格核同、16原成员保持；仅合成文件，非原身份/设备或完整O04。

- [O02 Android原产物电脑CLI消费者](GOAL_O02_CURRENT_ANDROID_PC_CLI_CONSUMER_2026-10-20.md)：7原ZIP/10成功/14预期拒绝；首轮超时保留，合成包外登记不作真实信任。
- [O02 Android原产物Excel转换消费者](GOAL_O02_CURRENT_ANDROID_XLSX_CONSUMER_2026-10-20.md)：5次CLI/25表/139396格与63原成员核同，含12639行规模/危险文本/处置/空包；非真实身份、原生Excel或完整O02/O05验收。

- [O02 rescue完整主机回归](GOAL_O02_NATIVE_FULL_HOST_REGRESSION_2026-10-20.md)：短TEMP/正确资源下同次65项零失败零跳过；旧失败保持，非跨版本拼接，非设备或完整O02验收。

- [O02原生SQLite主机短路径复验](GOAL_O02_NATIVE_SQLITE_SHORT_PATH_RECOVERY_2026-10-20.md)：新路径长度依据重新入队，原19失败+2guard实际21项零失败/跳过；旧CANTOPEN与首轮资源失败原件保留，不计完整O02。

- [E06 Android消费实际服务端SKU向量](GOAL_E06_ANDROID_SKU_PARITY_CONSUMER_2026-10-20.md)：47组全字段/顺序/错误一致，1项实际执行零失败零跳过；源码及继承守恒，仅主机解析范围。

- [部署真实数据本机副本溯源](DEPLOY_LOCAL_DATA_PROVENANCE_2026-10-20.md)：逐项列1514/1445/1457、M71及其他候选路径/时间/哈希/出处；无符合非合成MySQL闸门的可用副本，真实库链继续阻断，未连接或导出。
- [test 数据来源核查与导出闸门](DEPLOY_TEST_DATA_SOURCE_AUDIT_2026-10-20.md)：只读核对 test 主机、发布来源、备份链及证据目录；未证明真实业务演练或脱敏生产同步，未导出，真实库链继续阻断。
- [test 真实业务演练与数据预检](DEPLOY_TEST_BUSINESS_REHEARSAL_2026-10-20.md)：按用户更正完成快照/备份/隔离同步，模拟器启动被审批策略拒绝后终止演练；test恢复101表/129333行及218文件一致、原服务/访问配置恢复，PDA03实际联机且订单不变；无演练导出，真实库链未验。
- [本机 Codex 模拟器权限诊断](CODEX_EMULATOR_PERMISSION_DIAGNOSIS_2026-10-20.md)：本地规则未拒绝、实际沙箱已关闭；新授权后启动仍被执行时策略拒绝，规则详情/例外入口未暴露，未改配置/ACL，未实际启动，不计D/O/U。
- [Codex Windows策略拒绝续查](CODEX_WINDOWS_POLICY_FOLLOWUP_2026-10-20.md)：ACL差异已核但因果未证，未扩大权限/切unelevated/重启或复验；配置/ACL守恒，WSL2/M71仅枚举，阻断保留、不计D/O/U。
- [预检四断言语义审查](DEPLOY_ASSERTION_REVIEW_2026-10-20.md)与[工具链统一](DEPLOY_TOOLCHAIN_REMEDIATION_2026-10-20.md)：测试缺陷分类、包源/构建/运行时一致性；固定候选复验结果分别登记，非部署放行。

- [E06当前源封存原件条件消费者](GOAL_E06_CURRENT_SEALED_CONSUMERS_2026-10-20.md)：原件/采集/闭合/SKU向量24项零失败零跳过，历史字节守恒；非业务HTTP、设备或最终候选。

- [服务端普通测试完整回归](GOAL_SERVER_ORDINARY_FULL_REGRESSION_2026-10-20.md)：310 suite、4775项，4756通过、0失败、19跳过；输入源码/继承改动守恒，非IT或最终部署候选。

- [D13孤儿owner生命周期测试修复](GOAL_D13_ORPHAN_OWNER_LIFECYCLE_TEST_REPAIR_2026-10-20.md)：真实子JVM死亡输入替代延迟退休误判，21项定向通过、产品不变；非完整恢复或部署放行。
- [D04审计前缀流式JDBC测试覆盖修复](GOAL_D04_AUDIT_STREAMING_TEST_REPAIR_2026-10-20.md)：修正失效mock，18项定向通过、产品不变；原失败保留，非MySQL/HTTP或完整回归。

- [隔离部署预检与补充复验](DEPLOY_PREFLIGHT_2026-10-20.md)：固定提交编译打包通过，初轮4764项11失败；补前置后定向57项4失败，Linux激活stub自测8项通过；工具链仍阻断，未部署。
- [O03当前Android封存电脑返还文件消费者](GOAL_O03_CURRENT_ANDROID_SEALED_RETURN_2026-10-20.md)：原电脑输出实际由产品双层验签消费，原件/继承改动守恒；主机有界组合，非原机持久文件、HTTP或最终候选。
- [O03处理响应与返还下载一致性修复](GOAL_O03_RESULT_DOWNLOAD_BINDING_2026-10-20.md)：23项定向测试、封存传输字节离线回放通过；冲突保全/原键保持，非新HTTP或O03整体通过。
- [O02 TEMP首轮历史成功原文件离线核验](GOAL_O02_TEMP_RECEIPT_OFFLINE_2026-10-20.md)：36项TEMP与30项FORMAL回归通过，封存原文件CLI退出0；仅原范围/映射下历史回执，不确认应急包完整性、现时状态或放行/删除，非O02整体通过。
- [O02 FORMAL历史成功原回执离线核验](GOAL_O02_FORMAL_RECEIPT_OFFLINE_2026-10-20.md)：30项测试及封存原包CLI通过；历史回执与现时业务状态明确分开，原件/继承改动守恒，非O02整体或最终组合通过。
- [D06/D07 默认关闭Chrome/HTTP](GOAL_D06_D07_DEFAULT_OFF_CHROME_HTTP_2026-10-20.md)：四项真实消费者通过；原守恒断言失败保全，独立复核确认仅登录审计新增，业务表/原审计保持；非整体或统一候选通过。
- [U04 TEMP真实IME四字号消费者](GOAL_U04_TEMP_NATIVE_IME_2026-10-20.md)：同原记录/产品APK，真实输入/筛选/清除/重建四档各1项通过，83表/会话密文守恒；重启围栏不变，不代表完整UI或可信保留通过。
- [U04 TEMP新产品APK四字号受影响消费者](GOAL_U04_TEMP_NEW_APK_STATUS_CONSUMER_2026-10-20.md)：复用原记录，四档各1项通过，83表/会话密文守恒，重启围栏保持；不代表TEMP真实IME、可信保留或完整UI通过。
- [U04 筛选真实IME四字号](GOAL_U04_ADJUSTMENT_FILTER_NATIVE_IME_2026-10-20.md)：补IME留白，实际四档各1项通过，83表/会话密文守恒；14项定向回归通过，两次失败保全，非空保留/完整UI及L2/L3仍未验。
- [U04 正式订单四字号状态筛选消费者](GOAL_U04_FORMAL_STATUS_FILTER_NATIVE_CONSUMER_2026-10-20.md)：同一已签名合成正式输入四档各1项通过，83表/会话内容守恒；产品未改、未预采密文SHA、主管保留仅空结果，不代表U04/D17整体通过。
- U04原记录状态筛选：[重启前100%/200%消费者](GOAL_U04_STATUS_FILTER_NATIVE_CONSUMER_2026-10-20.md)、[重启围栏下130%/150%消费者](GOAL_U04_STATUS_FILTER_REBOOT_FONT_CONSUMER_2026-10-20.md)。同22条合成记录，83表/身份守恒；新轮保留130%失败并补围栏断言，不拼成同条件四字号、正式/可信保留输入或U04/D17通过。
- [U04 保留明细四字号 tar 只读审计](GOAL_U04_RETAINED_DETAILS_TAR_AUDIT_2026-10-19.md)：逐成员区分 Room DB/WAL、应用资料和运行元数据；输入 SHA、命令和输出哈希留存。
- [U04 独立采集大字首屏原生 L1](GOAL_U04_INDEPENDENT_LARGE_FONT_L1_2026-10-19.md)：合成隔离输入的 Android L1；原身份、现场、实体键、真机和 L2/L3 未验。
- [恢复租约退休与效果门 L1](GOAL_RECOVERY_LEASE_RETIREMENT_L1_2026-10-19.md)：文件树/主机层 fail-closed 证据；不等同于真实恢复业务链。
- [E07/D06-D07 Web fail-closed 当前源 run02](GOAL_E07_D06_D07_WEB_FAIL_CLOSED_CURRENT_RUN02_2026-10-19.md)：门控单测和类型检查；真实 HTTP、MySQL、Chrome、Android 和现场未验。
- [E08/O02 当前源电脑只读消费者 run04](GOAL_E08_O02_CURRENT_SOURCE_CONSUMER_RUN04_2026-10-19.md)：合成封存向量只读消费和副本往返；业务回执、外置介质和删除能力未证明。
- [E08/O02 Android rescue 主机回归](GOAL_E08_O02_ANDROID_RESCUE_HOST_L1_2026-10-20.md)：失败原件保全；定向主机子集不替代宽过滤器失败类或设备/真实链路。

## 失败、接续与边界证据

- 失败报告、前置看板快照、接续 handoff 和清理记录仍按原文件名留在本目录；它们记录当时输入和限制，不覆盖当前看板。
- 需要检索早期 D01–D18、R06/R07、E06/E07/E08、O01–O05 或 U01–U04 记录时，先在 [原历史索引](../archive/2026-10-20/HISTORY_README.md) 按日期/主题定位，再打开对应原报告。
- 未经当前看板重新核实，不从历史报告恢复 PID、服务、设备、数据库、角色、生产开关或已取消范围。

## 索引归档映射

- 原 `docs/history/README.md`：[`../archive/2026-10-20/HISTORY_README.md`](../archive/2026-10-20/HISTORY_README.md)
- 原 `docs/DEVELOPMENT_PROGRESS.md`：[`../archive/2026-10-20/DEVELOPMENT_PROGRESS.md`](../archive/2026-10-20/DEVELOPMENT_PROGRESS.md)
- 原字节、长度和 SHA-256：`.local/goal-20261020-resume/index-compaction01/pre-index-manifest.json`
