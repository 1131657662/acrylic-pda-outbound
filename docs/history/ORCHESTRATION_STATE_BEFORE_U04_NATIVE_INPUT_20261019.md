# U04原生组合验证前断点（历史归档）

原始字节保存在 `.local/u04-independent-native-input-run01/board-before.md`；历史写权/环境不恢复生效。

# 开发看板

<!-- CURRENT_STATE_START -->
版本事实见看板生成事实块；归档不复述。

总目标 D01–D18、§8、O01–O05/U01–U04 与 UI 重设计保持 active、未完成；只有第8节全部闸门在实际最终候选上满足才可 complete。局部L1、合成输入及开发备份不计整体L2/L3。

## 输入、写权与继承保护
- 固定树 `D:/acrylic-pda-outbound`、分支 `codex/development`；本切片输入HEAD及origin开发分支 `971f66eedaf9bd5050b7bf7f9e790c66121d327c`，origin/main只读 `c6e9e0c238c657baecbf699ba6fdbada5d979b86`；本切片开发提交 `47f58615842a15aa076e2631e94c9fbb33e002ae` 已推送并核ls-remote，完整回执见 `.local/u04-independent-accessibility-run01/git-delivery.json`。
- fresh list_threads确认本聊天 `01a1520d-c412-7402-b77d-311a043b3d1e` 为唯一活动项目写者，其他项目聊天notLoaded。无在途重型任务。完整tracked/untracked路径/SHA、四文件patch、973项测试输入零漂移和继承保护见同根 `closeout.json`；218项继承中217项原SHA不变，只有看板事实刷新，新增进度索引另列；取消链及其他WIP保留。
- 替换前结论见[无障碍切片前断点](ORCHESTRATION_STATE_BEFORE_U04_INDEPENDENT_ACCESSIBILITY_20261019.md)；原始字节另存同根 `board-before.md`。更早证据及E08/D17 handoff沿原索引，不恢复旧写权或暂停。

## 本轮首个只读任务
- [四字号tar审计](GOAL_U04_RETAINED_DETAILS_TAR_AUDIT_2026-10-19.md)：八份tar逐成员比较、24个Room成员独立Windows tar复核，Room主库/WAL/SHM及schema/83表257行守恒、完整性ok/外键无错。原 `archiveUnchanged=false` 保留。
- 差异为每档新增16个instrumentation证据成员、目录mtime、部分profileInstalled与WorkManager调度字段；无业务资料内容变化/删除。输入、命令、逐项差异和输出哈希在 `compare01/`、`runtime-details01/`、`final-manifest.json`。原run02 APK/tar/日志/AVD/唯一证据不覆盖、不删、不重跑。
- [takeover12取消链审计](GOAL_E08_RECOVERED_CONFIRMATION_WIP_AUDIT_2026-10-19_TAKEOVER12.md)仍有效：无SyncWorker restore caller，确认Dialog无产品caller；R09只读原件展示保留。原身份/可信时效/保管仍未证。

## 后续有界切片验收索引
- [TEMP满队列L1](GOAL_U04_TEMP_QUEUE_OVERFLOW_L1_2026-10-19.md)与[独立满队列L1](GOAL_U04_INDEPENDENT_QUEUE_OVERFLOW_L1_2026-10-19.md)：预修复反例失败，修复后各4项通过；65条已接收输入持久化。仅合成主机L1。
- [独立声效提示L1](GOAL_U04_INDEPENDENT_SOUND_READINESS_L1_2026-10-19.md)：显示2项通过；声音/Room组合SQLite失败保留。开发交付 `a2591fd35f01ec8f1675a6e7a7c76965dcdba732`，ADB只读枚举启动daemon事实已勘误。
- [文本按变化更新L1](GOAL_U04_INDEPENDENT_INCREMENTAL_RENDER_L1_2026-10-19.md)：预修复3项反例失败，修复后与声效回归5项通过；提交 `42562412ca9372728239ef2d1a798ff97728c8d9` 已推送，设备性能未验。
- [首屏/次级面板L1](GOAL_U01_U04_INDEPENDENT_FIRST_SCREEN_L1_2026-10-19.md)：最终16项主机测试及instrumentation编译通过；失败/几何诊断保留。开发提交 `e35e9f2c`，事实记录 `3f450bd76ceca833d440c549623c115b121ed742`；只计合成UI，原生IME/实体键未验。
- [剩余数/最近保存L1](GOAL_U01_U04_INDEPENDENT_PROGRESS_L1_2026-10-19.md)：同一Room快照发布有效/剩余/超计划及任务专属最近保存；七套件27项、引擎3项通过，EXPLAIN未见临时排序，任务切换竞态守卫已测。run02误用默认build及未收XML事实保留；历史分页末行仍仅指该页末条。
- [Activity手输交接L1](GOAL_U01_U04_INDEPENDENT_ACTIVITY_INPUT_L1_2026-10-19.md)：成功手输后收起面板、清焦点并请求隐藏IME，候选须显式确认；预修复1失败，最终17项主机测试通过。原生IME、实体键及真机未验；完整证据在 `.local/u04-independent-activity-run01/`。

## 第九切片：队列计数无障碍隔离
- [验收报告](GOAL_U04_INDEPENDENT_ACCESSIBILITY_L1_2026-10-19.md)：队列独立可见且非live，操作/保存反馈仍polite。after03显示/增量渲染/首屏共10/0/0/0、退出0；前两轮夹具断言失败及首次Activity的2项SQLite错误保留，Activity未重跑。
- 本切片仅改显示器、布局和两个显示测试；无引擎/Room/schema/网络/权限变化，未构建或安装APK。输入/运行器/配置/日志/XML及SHA在 `.local/u04-independent-accessibility-run01/`，最终973项输入零漂移。只计作者自审与合成Robolectric UI L1，TalkBack、原生Activity/IME、M71/四字号设备及U04/D17整体未验。

## 实际环境、失败与清理
- fresh `environment-closeout.json`：系统MySQL80父4288/子5188、3306/33060，配置指向ProgramData系统datadir；auto.cnf权限拒绝、UUID未知，未连接/停止。无Java/Gradle/模拟器进程；ADB2436/5037只读枚举M71，不操作设备数据。debug keystore SHA `0539b3d0b80ab4619a08db639d433ec2f3ac0a8d7b2fbdc32865d5cfc6b0b6cb`，未用正式签名。
- 本次采样C/D/E余量见同根environment-closeout，分别73,124,573,184 / 60,933,435,392 / 195,634,053,120字节；全部监听和完整WIP见closeout。旧SQLite、恢复run03、A1.4、1522/1523、调度偏差及Chrome策略失败继续保留，不无新依据重跑。
- 本次六个构建/缓存/temp目录拟清理被自动审批执行前拒绝 `blocked by policy`，未重试，6441文件/120,192,634字节仍在、释放0；精确清单见 `cleanup-rejected.json`。原拒绝时间未记录，记录时间不冒充拒绝时间。旧scratch/声效缓存被拒目录也保留。
- 前序获准清理的逐项哈希、释放/卷余量与PS5长路径/部分删除及被覆盖前清单缺口，见各验收报告和前断点；不改写旧失败。APK/tar、原资料/身份/签名/唯一证据和继承WIP保留。

## 下一独立任务与总闸门
- 本切片已精确提交推送；下一项审计独立页原生消费者组合：先审计既有fixture/运行器的适用范围和新输入，补首屏/手输IME/实体键/四字号设备证据；不得以主机测试冒充设备运行，不重复已验tar或旧用例。
- 受影响路径仍须按路径→影响层→必跑→复用依据补Android、真实HTTP/MySQL、Chrome/Python及非空迁移组合。D16可信合法退出依据、D18统一最终候选/原模拟器/恢复全链、旧BCD/原材料/现场项继续单列，不被局部通过覆盖。
- 生产决定七类能力、TEMP生产归档及 `localDeletionAllowed=false` 保持；不恢复换机承接、多跳、接收者确认/激活；不启动旧run03、不接原库/现网、不用正式签名、不部署/Release/合main。总目标保持active。
<!-- CURRENT_STATE_END -->

> 长期规则见 AGENTS.md；版本事实只读生成块；历史从 docs/history/README.md 检索。
