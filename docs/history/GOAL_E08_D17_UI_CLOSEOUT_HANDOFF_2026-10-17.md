# 新会话继续指令：E08 / D17 UI构建收口后接续

请在固定目录`D:/acrylic-pda-outbound`接续。上一会话已按用户要求完成最小“构建/lint事实、资源停止、D盘清理和交接”切片并停下；长期目标paused而非complete。本指令提交给新会话即明确恢复长期目标，旧暂停不覆盖此次恢复。先完成下述只读接管与业务范围核对，再沿既有计划推进，不重做已验链，不恢复已取消的换机进度承接、多跳、接收者确认/激活。

## 1. 精确位置与阅读顺序

固定工作树`D:/acrylic-pda-outbound`，分支`codex/development`。交接文档提交前HEAD、实际远端`refs/heads/codex/development`及最后推送均为`979777f421bdcaefb9f741b38422dbd0875262fa`；下方最终Git快照将在收尾提交推送后更新。暂存区原为空，本次仅文档开发备份。

本次业务切片归属为**E08 / D17 UI构建与环境收口**，业务适用性待核、UI L1未通过。旧报告名`docs/history/GOAL_D06_RECOVERED_CONFIRMATION_UI_BUILD_CLOSEOUT_2026-10-17.md`保留以保护历史，但正文已更正：现行D06是坏机后台撤回，D07是重发及新机从头作业。不得按旧名称续跑接收机确认/激活。

先读实际`AGENTS.md`、`docs/GIT_WORKFLOW.md`、`docs/ORCHESTRATION_STATUS.md`当前区及生成事实块，再读路线、进度、剩余计划、总目标第4/5/7/8节及相关政策/验收页。旧`D01_D18_HANDOFF_2026-10-14_E06_PHYSICAL_WIP.md`只作历史来源；1524候选、1522/1523旧待查和旧暂停不恢复。版本数字只见看板生成块，不在其他文档复述。

接管前只读重查实际分支/HEAD/远端、所有tracked/untracked及哈希、本机快照、目标状态、最新写权登记和实际活动写者/重型任务、进程/监听/隔离库身份及C/D/E余量。先保存旧结论，再`powershell -NoProfile -File tools/version_facts.ps1 -Write`更新事实块、校正当前区；`-Check`不检查日期/HEAD/dirty/worktrees漂移，须实查。不能确认唯一写者时仅只读保全，不覆盖文件或数据库。

## 2. 已有可复用证据及逐D剩余验收

本节“已有”均限正式报告所列输入和范围，**不表示对应D整体完成**；“剩余”列包含尚未执行/尚未满足的部分，不能把已做过的整批写成未开始。以下文件名均位于`D:/acrylic-pda-outbound/docs/history/`，SHA为本次实读原报告文件SHA-256。补充报告完整路径、长度、原SHA与最后提交记录见`D:/acrylic-pda-outbound/.local/goal-20261017-resume/handoff-final01/evidence-bindings.json`（34条，SHA-256 `0bdc26a4430d44b6e376dc2bf6ffa4295afe8e673aaeff867d01a6f84061d61d`）。原60报告绑定仍见`.local/goal-20261017-resume/matrix-report-bindings01.json`，不改写旧清单。

| D项 | 已验范围和主要文字报告 | 原报告SHA-256 | 未执行/未满足的剩余 |
| --- | --- | --- | --- |
| D01 | 27项范围/60旧报告绑定已盘点；`GOAL_D01_REQUIREMENT_EVIDENCE_MATRIX_2026-10-17.md` | `cc3255499d43fdde331f9fe5b84191e5897e8b7b02f13d044a2093606bb6ff04` | 接管及每批增量输入/写权仍须重查 |
| D02 | 同人同机续作、Android/HTTP/MySQL正向已有；`D01_D18_EXECUTION_2026-10-07.md` | `c67be3d50b20dc63bb178a2e58ce9197ea1fa27b1cd41d1989cdb19ae2305d0a` | 候选受影响正向/队列/恢复 |
| D03 | 高轮/分块/关联/账务链已有；`D01_D18_EXECUTION_2026-10-07.md` | `c67be3d50b20dc63bb178a2e58ce9197ea1fa27b1cd41d1989cdb19ae2305d0a` | 候选高水位、并发、原键/异文/末SQL与客户端 |
| D04 | P04主管排除/映射/成功验签已有；`GOAL_E04_P04_NATIVE_PRODUCT_2026-10-12.md` | `23b7670860807b8c2071bc70f41f5fde725bcd88072b04654923e8941624fd23` | 候选跨来源组合；1043/A1.4暂缓，非旧“未授权” |
| D05 | 零草稿及非零主管取消、实物/冲突已有；`GOAL_E04_ZERO_DRAFT_CANCEL_2026-10-12.md` | `e447b619b7535263649f91143bcdb585a079c3adac2fcbf723e192a39f77802e` | E06新增关联/实物受影响组合 |
| D06 | 坏机后台撤回有E01及D10 HTTP证据；`GOAL_E01_RECALL_REPUBLISH_2026-10-11.md` | `3f5fd52b9922f7b6fc702ce2b017486608afe0e6eb4fa1d12373d347a748b584` | 候选无STOP撤回/竞争/旧上传拒绝/末SQL及Web |
| D07 | 重新发布、新机从头作业及旧凭据隔离已有；`GOAL_E01_RECALL_REPUBLISH_2026-10-11.md` | `3f5fd52b9922f7b6fc702ce2b017486608afe0e6eb4fa1d12373d347a748b584` | 实际Android/HTTP/MySQL/Web候选组合；不恢复进度承接 |
| D08 | HEADER补偿及净值多来源已有；`GOAL_E04_P06_HEADER_COMPENSATION_2026-10-12.md` | `7c7f8c882dbbbc2a670736d3332d6fe269b237f84ee8a8afb4ae9621e8e25b82` | 候选退货/错误/重量/抬头/撤销/并发/账务 |
| D09 | 对账、退库新周期及多来源解隔离已有；`GOAL_E04_P06_RECONCILIATION_2026-10-13.md` | `a4eb6a37711001782a613a9df3e89a6be1598a3b917b29095820f352cb6e8e56` | 候选新周期出库/旧周期防重/账务唯一性 |
| D10 | 故障撤回HTTP及权限/附件报告已有；`GOAL_D10_FAULT_WITHDRAWAL_HTTP_ACCEPTANCE_2026-10-15.md` | `9ca1b63896b8cc30c742d0b1749d980abac5906e1c9079454cca8777267323a9` | 三容量真实字节、撤权/原请求恢复与产品入口候选覆盖 |
| D11 | run680完整导出14项L1通过；`GOAL_D11_R08_EXPORT_INTEGRATION_L1_2026-10-17.md` | `ba43df3e3f6badb95f93faf37aa14781873fef653ae9f9c5535fe1bde33e7146` | 多来源净值/固定版本/原顺序受影响L2及L3 |
| D12 | O03中转/原回执及恢复worker已有；`GOAL_E05_O03_ACCEPTANCE_2026-10-13.md` | `4abdc882e9914aa408891b88e0195c598dcee8f2dfa2883a1a1efc88976c366f` | 候选任务/下载/重启/授权变化/损坏/Android文件组合 |
| D13 | HTTP/Session、lease、worker文件导出及805/806/812受影响L2已有；`GOAL_D13_RECOVERY_HTTP_SESSION_L1_2026-10-17.md` | `2d643cee6cbb618248f341704f11cbe2932fc80e6cc013720609920d0a108f9d` | 整体入口/Android/完整外部Python/原模拟器及候选恢复全链仍未完 |
| D14 | run814独立截点组合及667/675产品仓库恢复已有；`GOAL_E07_D14_INDEPENDENT_CHECKPOINT_COMBINATION_2026-10-17.md` | `e5ee118053aa04f6aefa7a9d25f647a8fad0a45faba42c6392b37e1404283701` | 其余受影响截点/客户端/O05与候选组合，未计D14整体 |
| D15 | 旧worker677、新旧新回退673、恢复worker670/任务1668/非空升级1550有界证据已有；`GOAL_D15_OLD_WORKER_COMPATIBILITY_L1_2026-10-17.md` | `b69a1df1b34db32a4680dbeffe6a55882bae62dabfd5fc71cdf35e9f7ea051db` | 旧写入/多任务格式、完整恢复可取回组合；真实Linux/异地另列 |
| D16 | 隔离整单消费者、复杂引用/trigger及拒绝保护已有；`GOAL_D16_WHOLE_ORDER_L1_2026-10-16.md` | `816ce4b3835acc6a757e9b88a8e36e564c2f9feb6c825e7b801662dc17d6812b` | 正常成功目标有效guard尚在，缺可信合法退出依据，整体未完 |
| D17 | Activity/广播四原生阶段、四字号/导航/反馈/队列等切片已有；`GOAL_D17_ACTIVITY_LIFECYCLE_COMBINATION_2026-10-17.md` | `d9ff770821e92a60323d2b8592e94534ff048f6672117057696b2ce9b6f70a0c` | 整套UI业务组合、旧BCD/原材料/兼容候选；本轮UI仅build/lint |
| D18 | Web候选静态阶段已有；`GOAL_E09_WEB_CANDIDATE_ACCEPTANCE_2026-10-15.md` | `b38f4304370052cbc887ad0f684852fd82dabb62de299e89dba95e9c3d9b6185` | 尚无统一最终候选；Android/HTTP/MySQL/Chrome/Python/非空迁移/原模拟器/恢复全链L3尚未执行 |

补充复用入口（同目录，SHA在上述34条绑定或原60条绑定中）：
- D05：`GOAL_E04_P05_NONZERO_CANCEL_2026-10-12.md`；D09：`GOAL_E04_P06_RETURNED_STOCK_2026-10-13.md`及各来源quarantine报告。
- D13：`GOAL_D13_RECOVERY_LEASE_PUBLICATION_L1_2026-10-17.md`、`GOAL_D13_RECOVERY_WORKER_FILE_EXPORT_L1_2026-10-17.md`、`GOAL_D13_RECOVERY_COMBINATION_L2_2026-10-17.md`。805/806/812不是D13整体或D18 L3。
- D14：`GOAL_E07_FORMAL_REPOSITORY_RESTORE_COMBINATION_2026-10-16.md`、`GOAL_E06_TEMP_REPOSITORY_RESTORE_L1_2026-10-17.md`；run814的854文件未漂移、179表622行守恒，Python消费者SHA `636277dfc8d1498fb97a9b2107e07c767943f568e272fe9ec020490bc60314ae`。
- D15：`GOAL_D15_PROGRAM_ROLLBACK_2026-10-16.md`、`GOAL_D15_RESTORED_EXPORT_WORKER_2026-10-16.md`。driver exit1及离线补核分开，不能改说原driver成功；资源user unit是合成负载，非项目Linux启停。
- D16：`GOAL_D16_RESTORED_FORMAL_REFERENCE_COMBINATION_2026-10-16.md`；D17：`GOAL_D17_NATIVE_FOREGROUND_QUEUE_2026-10-17.md`及进度索引中的四字号/队列/反馈/声音报告。
- E06多任务网页正式L1与接续复核为`GOAL_E06_MULTITASK_ASSOCIATION_WEB_L1_2026-10-14.md`、`GOAL_E06_MULTITASK_ASSOCIATION_WEB_L1_REVIEW_2026-10-17.md`。input01/typecheck02/build02/contract01/consumers03/old-consumers01/02/chrome01/finish01及preserve-1538输入、八项日志、347文件保全已核。当前源码漂移另列，不能再称“缺正式报告”，也不能计E06完整L2。1539的Android/HTTP/表事实及Python/Excel补核不能免除剩余L2。

报告hash只绑定引用文本；复用业务通过前仍须比较原source/input清单与候选产品输入，逐路径给出SHA及无影响理由，漂移只补直接受影响验证。

## 3. 在途/未提交WIP、通过与失败

本次文档收尾未改变继承的Android/server/web/tools改动：`handoff-final01/source-protection-review.json`核对收尾前38条受保护改动，哈希全部未变。AGENTS.md与docs/GIT_WORKFLOW.md包含继承改动及本轮持续清理规则，留作明确文档WIP，不整文件混入本次提交。其他继承文档/工具/源码与未跟踪内容均保持；最终全部status路径、原字节哈希、dirty副本及patch统一保存为`handoff-final01/dirty-final.json`、`dirty-final.zip`、`tracked-final.patch`与`input-final.json`。必须合并status路径取全集，不能漏掉仅显示modified的R06_A1_1_CODEX_2026-09-30.md。

全输入5501项快照：`.local/goal-20261017-resume/resume-20261017-0849/input.json`，SHA `42d8f5d3febab41ae9e3a1c4104b6d56d2416d0da6aa292f04f049b3a68dacfe`，同目录dirty-files.zip/tracked.patch/写权/进程/监听。后续`resume-20261017-closeout/dirty-hashes.json` SHA `3C12591CB3FBE40C999D9E089C62C1694A13A55511CC04E02B5EDDCB20690FF2`；收尾前`handoff-final01/dirty-before.json` SHA `b2354fd08591e3cb162f4875107d536bb2b52e6d50be38b19f8c86c92e9381d4`及dirty-before.zip保留。最终清单以`handoff-final01/`为准，不用旧脏项数替代实查。

继承WIP重点：PdaApplication/SyncWorker/HandoverActivity/HandoverViewModel/RecoveredHandoverCardReader/TemporaryOrdersActivity、TemporaryRecoveredConfirmationWorker、RecoveredHandoverConfirmationDialog/Display、RecoveredConfirmationDeviceTest/UiFixture、HighFrequencyOfflineDeviceTest及相关单测；服务e2averify/inventory恢复、独立截点、TEMP产品仓库等运行器；历史文档和工具。`server/physical-disposition-last-request.json`仍未跟踪，禁止清理或上传。完整路径/哈希/原字节副本在最终快照，不能只保护本段列名。

本轮通过仅限：
- `android-ui-run02/`默认关闭属性及`android-ui-run03/`启用`-PpdaIsolatedRecoveredHandover=true`的assembleDebug/assembleDebugAndroidTest/lintDebug成功。run02 build.log SHA `07CD2F7D8419E7B754219FA47EAFF4BABFE53369420C681EFB339DD41D3BC825`；run03 build.log SHA `2FF099C765723F4949EBB530190B7FE69DA4392EEE980F731F92495EA117AED9`；run03 build-command.txt SHA `4AF5A29B4FD44DB9B3673D3177CBD71333B9CCEA55D57ADEACC3DFB02F8C77BA`。目录均在`.local/goal-20261017-resume/`。
- APK保留：`android/app/build/outputs/apk/debug/app-debug.apk` SHA `4ACC1C8A3E60131824833BCD63A94B96E4BF3D7EB23A5D6C6DE50FC9516CE9AF`；`android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` SHA `6B0CF24219D2F370F5312DEF73D27AE2F768FFBA781A2A54DD59F5E705BB9507`。仅Debug签名，未安装、未跑instrumentation。
- run02独立AVD曾boot，随后已停。业务正向/取消/丢响应/重试/登录切换/重启、截图、Room/HTTP/SQL守恒未跑，UI L1未通过。尤其fixture请求handovers/{caseId}/recovered-confirmation并导入开发接收机钥，适用范围必须先审计，不将构建通过当作继续开发旧链的依据。

本轮失败原件均保留（路径前缀`.local/goal-20261017-resume/`）：
- `android-ui-run03/build-failure01.log`：java.io.tmpdir目录不存在，SHA `975EDFD1704B210D808F3AFAA75E10032C9C1F9A30242571A478B567801EDC3F`；建目录后仅构建复验成功。
- `recovered-confirmation-ui-run03/private/mysqld.log`：Windows basedir quoting的`Too many arguments`；另有重复进程ibdata1不可写。SHA `60CC0434DBD8A9FBE67CD2F894F5CEBCC945BED9AAC0AE4FAA07AC4C102C37BC`。
- 同目录`mysqld-skip-grants.log`：port0且无TCP/pipe；SHA `2411331AD30EB47B525B502A56647E7D71DAF9074DFDA837C22ECA9D81E9A16D`。
- `mysqld-skip-pipe.log`曾pipe ready，后Force停止；SHA `EAE72A2077EF38A15D92B8AFBBE9F3DE75F690595A4FB2F0048B2EE3AE3528CE`。没有形成Android业务验收通过，不再原样重启旧run03。

## 4. E01–E09尚未执行/未收口的部分

各批已有工作，以下为剩余范围，不是整批从未开始：
- E01：D06/D07/D10实际候选撤回→重发→新设备从头作业、权限/竞争/末SQL及灾后截点组合。旧交接取消。
- E02：O01/U01/U02高频、保存语义、VOID与队列/冻结/生命周期的候选组合及真实设备项。
- E03：O02/U03全来源断网一致快照、文件往返、原件回读/电脑转换与恢复候选组合；真实USB/MTP/OTG另列。
- E04：D04/D05/D08/D09/D10候选跨来源、实物/取消/更正/新周期账务组合；P04/P05/P06已做部分不重做，1043/A1.4按暂缓条件。
- E05：净值/O03候选撤回/恢复代次/原机回执消费、导出任务/授权下载组合；复用E05正式报告。
- E06：O04多任务/关联/冲突/实物各切片受影响完整L2；1538不可复用，1522/1523旧CLI失败不原样重跑，严格非空/default-off/全表守恒已有后补验。
- E07：D13整体、D14/D15完整组合及O05外置副本/未保护增量/恢复可取回；805/806/812、814及旧截点只按未受影响输入复用；同机副本不冒充外置介质。
- E08：D16正常资格缺口、D17/U01–U04整套实际UI及兼容；本轮recovered-confirmation WIP先审计有效范围。
- E09：统一最终候选、D18全链L3、逐门自审和开发交付；当前完全未达到最终总目标通过条件。

O01–O05/U01–U04均已有局部证据，仍需：O01队列/VOID候选竞争；O02各来源离线导出/独立文件往返；O03旧资格拒绝与原回执/恢复组合；O04完整独立采集多任务L2；O05副本可取回/恢复及真实外置条件；U01高频导航、U02耐久保存/未知结果、U03保全/独立采集、U04辅助页/大字/键盘/队列/生命周期均需实际候选组合。UI重设计不是静态视觉通过；现场P50/P95、30分钟、实体键/操作员走查另列，不转移可隔离开发缺口。

## 5. 下一可执行项与具体第一步

第一条只读命令在固定树执行`git status --short --branch --untracked-files=all`、`git rev-parse HEAD origin/codex/development`、`git ls-remote origin refs/heads/codex/development`；随后按第1节完成全部哈希/写权/环境接管和旧结论快照。**不先启动服务、模拟器或数据库。**

接管后第一个有界工作是审计上述worker/dialog/fixture/bridge与现行总目标第4/5节、R07政策、D06/D07及D13/D14灾后恢复范围的逐路径关系，保存“路径→仍有效范围或已取消范围→调用事实→处置建议→直接受影响验证”清单。继承源码先保留，不删除、不让新接收者/激活恢复为必做。只有适用性明确，才选择有效E06/E07/E08矩阵中的一个最小受影响组合并分配全新隔离身份；未核清的旧UI链不阻塞其他独立项。

## 6. 唯一写权与禁止项

本次收口写者为聊天`01a13a57-8082-7dd2-a08f-987483dcda4b`；交接终点已登记停止/释放。清理聊天`01a13cb4-88f1-7fc3-9af2-7a0fde6bd26e`已明确无在途动作、不再改源码/证据/看板，最新只读复查idle。新会话必须查实际活动后登记自身为唯一写者及输入；旧owner、released=false、release.json不证明当前写权。无法确认时只读保全。单助手，不创建并行写者或旧固定角色。

保护全部继承/未提交/未跟踪内容。提交用明确路径，禁止git add .；沿已有授权仅codex/development开发备份，不合main、不发布/部署/Release、不用正式签名、不接原库现网、不绕设备或自动审批阻断。七类生产决定handler/POST/放行/证据写入/关闭与TEMP生产归档保持关闭，localDeletionAllowed=false，D16标准不变。合成身份/主管/实物/库存/签名/DTO/fixture/模拟器不能冒充原PDA、原身份、原资料或现场回执。

## 7. 运行环境与隔离身份

采样时间`2026-10-17T09:43:57.8220100+08:00`，详见`.local/goal-20261017-resume/handoff-final01/environment-release.json`；09:40:34旧采样environment-final.json继续保留。本轮无Java/Gradle重型任务，已停目标PID与目标端口均为空。共享MySQL父PID4632/服务5440保留，3306/33060监听，库身份/命令行未知，只观察不连接或停止；原AVD TemporaryAdjustment1364的emulator12912/qemu12108及5564/5565保留；ADB PID8500→5037、8792→5040保留。完整监听/PID/命令行以该JSON为准，Node/Codex工具进程不当清理垃圾。

- 已停止run02 emulator23964/qemu25908，AVD`D06Confirmation1079-isolated`位于`.local/goal-20261017-resume/android-ui-run02/avd`，5580/5581零监听；其default_boot可重建快照已清，其余AVD/身份未删。
- 已停止run03 mysqld22920，原datadir`.local/goal-20261017-resume/recovered-confirmation-ui-run03/data`已清；曾使用named pipe MYSQLUI03、skip-networking/skip-grant-tables，不能称真实有权业务HTTP验收。19534/19535零监听，未复用旧失败库；停止采用Force，未声称SQL优雅shutdown。原`private/server-private.der`/server-public.der、storage、recovered-fault及JSON/日志仍保留，不清密钥或唯一证据。
- 已验旧隔离680：127.0.0.1:23308/acrylic_r08_it_680停机；677：DB19387/HTTP19461停机；805/806/812对应19500/19520批次及814按各报告封存；1538、1522/1523和所有旧失败库禁止复用。新编号/端口/路径/DB名必须统一核未占用再分配，不预先宣称某端口安全可用。
- SDK`C:/Users/qq113/AppData/Local/Android/Sdk`；MySQL工具`C:/Program Files/MySQL/MySQL Server 8.0/bin`；ADB按现有server观察，不能kill-server波及他人。Debug Keystore`C:/Users/qq113/.android/debug.keystore`保留；未操作正式签名/原设备钥。

## 8. 磁盘、一次清理与保全

清理后收尾采样`2026-10-17T09:43:57.8220100+08:00`：C可用**77,909,377,024 bytes**；D可用**56,676,663,296 bytes**；E可用**195,634,057,216 bytes**。这是收尾观测值，清理即时前后数值及净增量如下；背景进程与交接快照写入会改变余量。

本次清理仅三个明确目标：
1. `.local/goal-20261017-resume/android-ui-run02/avd/snapshots/default_boot`，4,295,032,832 bytes，可重建快照；ram.img原SHA `BBA36E59CFE7128CA94BCDD249819426B61B38EB289784E98D245A8356263607`。
2. `.local/goal-20261017-resume/recovered-confirmation-ui-run03/data`，201,021,406 bytes，核实停止、无消费者且用途结束的隔离测试库。
3. 同run03的`private/seed.sql`，475,399 bytes，派生测试seed。

删除逻辑文件长度合计**4,496,529,637 bytes**；D可用**52,180,439,040→56,677,748,736 bytes**，卷观测净增加**4,497,309,696 bytes**。逻辑长度与卷增量分别报告，不把分配差异或背景变化伪装成精确文件净释放。没有扩大清理到其他旧库、原资料/身份/签名、日志、最终APK或继承WIP。

清理脚本`.local/goal-20261017-resume/cleanup-run-20261017.ps1`；逐文件解析路径/长度/原SHA、命令、输入与无消费者检查为`cleanup-20261017-pre.json`（SHA `574B4BD7FCD77BDBC46318324EF5F18151EBCFB347625149E94B6C8D2029AC05`）；后回执`cleanup-20261017-post.json`（SHA `4B95520AE7EC1A8E674B2B25D67E5AD3F74BE712310C461F789207A7E0640461`）。构建/SQL准备失败日志未删；依赖锁/构建配置哈希补齐为`handoff-final01/dependency-inputs.json`。停止事实为stop-targets-before.json/stop-result.json。先前一次内联清理被策略拒绝，后续明确路径审计脚本已成功，无待授权清理动作。

## 9. 暂缓与重新入队条件

- 1043/A1.4旧完整审计前缀及五轮失败：有新原材料或明确新修复假设、独立时间盒及标准后才入队，不重新计数或原样重跑。
- Chrome1540自动审批策略阻断：须新许可或合法可用工具路径；不换端口/包装绕过。保持原拒绝证据。
- 1522/1523旧CLI失败及计数保留，后续严格非空/default-off/正向/全表守恒已补，不因旧接续页机械重试。
- D16正常成功目标有效guard：需可信合法退出依据；不能删guard/改状态/制造资格。未满足不记通过。
- 当前恢复确认UI：先核取消范围与有效灾后恢复语义，范围未明不运行旧链；运行器失败与未通过业务分开，不自行宣布新产品失败计数归零。
- 原资料/旧BCD/原身份/回执、真机、异地介质/Linux现场/真实规模/长时等按材料/设备/环境/授权满足后验；不以合成替代，不把本机可做的缺口推到现场。
- 同子项同根因连续3次无新证据，或主动诊断60分钟未收敛，保全并暂缓该项及确实依赖部分，推进独立项。新会话不清零；重新入队先记录新增依据与旧失败指纹。

## 10. 必须继续沿用的分层验证与快速修复通道

L1：每个切片提交前记录“改动路径→影响层→必跑清单→复用依据（路径+SHA）”。直接受影响产品入口在该切片内实跑：Web对应Chrome成功路径；Android队列/恢复/布局对应客户端行为；Python消费者实际消费输入。L1-only明示开发备份，不能计D整体。

L2：每个D步骤及时执行有明确范围的受影响组合，包括受影响Android、真实HTTP/MySQL、对应Chrome、Python消费及涉及的非空迁移。无影响且无关旧链按路径+SHA复用，不机械重跑。迁移冻结后集中跑非空迁移；其后迁移/模型/转换/存量输入发生影响性变化，补受影响范围。D17等跨批步骤按明确验收单元组合。

L3：D18最终实际候选上执行Android、真实HTTP/MySQL、Chrome、Python、非空迁移、原模拟器和恢复全链。L1/L2不能免除L3必跑，仅复用未受影响且有SHA依据的扩展链。

受影响范围每次跑业务正向及适用的权限、事务、迁移、幂等、并发、回滚、末条SQL失败回滚、同键并发、异文拒绝、实际锁等待后撤权和default-off有/无权测试。生产默认保持关闭。

快速修复通道只适用于失败明确归因运行器/夹具且无产品写入或未核清事实；保留失败指纹/日志，修复后仍补业务验收。归因不清或已发生业务写入则按规则完整保全。每阶段/提交/中断前更新看板当前区（≤100行/8000字符）、证据索引、精确Git/全改动/写权/进程监听/DB/磁盘/失败及下一步。仅总目标第8节全部闸门在实际候选上满足才complete。

## 11. 清理授权与纪律（用户原文，持续规则）

用户本次明确授权清理 D 盘空间。以下原则适用于持续开发，与项目文档第4节的旧纪律冲突时，本次授权优先：

- 可以用文字形式证明的文件，先提取关键内容写成文字摘要（markdown/文本），保留摘要后删除原始大文件。典型：原始测试 XML、构建日志、HTTP 日志、JSON 证据、Playwright trace、中间产物。
- 测试数据不再保留：已认证停机、用途结束的隔离库 datadir、可重建的 dump、binlog、一次性 fixture、测试用临时数据库、重复备份。
- 垃圾清理：构建缓存、Gradle daemon 临时、Conscrypt 临时 DLL、可重建的 AVD 快照、Chrome 临时目录、node_modules 缓存、旧 attempt 目录中可重建的部分。
- 以下仍不可清：原资料、原身份/Keystore、原回执/签名原件、唯一一次性的原证据、最终交付 APK、未提交源码改动、继承改动、生产边界相关的任何文件。
- 清理前：保留输入 SHA/路径/命令/依赖锁/结果/哈希，核对解析后的绝对路径与进程，确认无消费者。
- 清理后：记录释放字节数和前后 D 盘余量。
- 清理节奏：每个切片收口时、每个阶段收尾时，按上述规则清理，不积压。

清理到什么程度、哪些算"可文字化"、哪些算"测试数据"、哪些算"垃圾"——由接手者根据实际状态自行判断。拿不准的保留并记录。
