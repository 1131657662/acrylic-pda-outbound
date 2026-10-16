# D17/U04 实际作业Activity生命周期组合

FORMAL/TEMP四次分离原生instrumentation通过：实际扫码Activity暂停后不接收广播，返回/重建保留原待确认输入；另一PID通过实际按钮确认，只保存一次，重复广播准确INVALID并由页面确认处理结果。Python核全表/事件/文件守恒通过。本批是有界测试组合开发备份，非D17/U整体、E08 L2、D18 L3或总目标完成。

## 输入、决定与影响

固定树`D:/acrylic-pda-outbound`、分支`codex/development`，运行HEAD/开发远端`e76979aa887aae790ead004b2e548d63cfd4b93a`。本聊天唯一写者，清理/其他项目聊天实核idle，无子代理。版本事实只见看板生成块；生产七类能力、TEMP归档及`localDeletionAllowed=false`不变。

`.local/goal-20261017-resume/input12`至`input15.json`及同号附件保全所有tracked/untracked、binary diff、未跟踪原件、worktrees、写权、实际远端、进程/监听/C/D余量和旧看板。最终运行input15共5466项，SHA`c39ee08c1924093897522d76a60d1522704bc742310a8343c5f0041d2d4c15ce`；`activity-lifecycle02/source-binding01/02.json`执行前后全部逐SHA一致。其后仅报告/矩阵/索引/看板变化。

按总目标第3节自主采用`activity-lifecycle01/design01.md`，补正见`activity-lifecycle02/design01.md`。原5564上新增独立debug包`lifecyclefm02`/`lifecycletm02`，包/设备/原APK/绑定预检；不写原应用数据库，不新建AVD/服务/MySQL。分别复制原签名合成种子，未改写；FORMAL SHA`7372b0b2bb48e440a451dd013e0fa049360e906ab467e511ece6137c52a0bdae`，TEMP SHA`b59254fc5560a94488da7d052470d96d33e70b370b24b69fd335d0fb43675cf4`。

身份/时间明确合成：新应用SessionVault保存种子身份，缺少时补显式合成localSessionId；实际elapsed/boot锚定种子的合成server instant。新应用实际AndroidKeyStore AES保管合成Session，不是原PDA身份/登录/服务授时证明。产品`PdaApplication`、`SessionWorkAccess`、`TemporarySession`、引擎/接收器/Activity回调均未替换或注入。FORMAL未配置在线撤回传输入口，按产品已有逻辑退回本地事实，不验证实时撤回获取。

| 改动路径 → 影响层 | 本单元必跑 | 复用依据（路径+SHA） |
| --- | --- | --- |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/queue/WorkActivityLifecycleDeviceTest.kt` → 产品Activity/应用接线 | 两包构建、四次instrumentation、真实按钮、暂停广播屏障、返回/重建、新PID/原ID、保存/重复确认 | input15逐路径SHA；[原生协议报告](GOAL_D17_NATIVE_FOREGROUND_QUEUE_2026-10-17.md)不代本次Activity执行 |
| 本地`isolated.gradle`/`build01.py`/`device01.py` → 唯一包/设备/输入/证据 | 未占用包、种子hash、原APK/绑定、分源安装、前后完整tar、失败不覆盖 | `build-*/command.json`及hashes/result、各阶段命令；debug签名，无卸载/清数据/正式签名 |
| 本地`consume01.py` → SQLite/WAL/文件消费者 | 每源83表/schema/FK/原capture、精确event/scan/guard增量、PID、原应用全部文件 | 直接读tar及只读scratch；同作者独立算法，不称独立人员 |
| 未改Android产品/主机测试/依赖 | 历史7项主机按未受影响输入复用 | `host-reuse-bindings01.json`列831路径/长度/SHA，与input07相同；旧XML在`foreground-queue01/build02-artifacts`，不计新执行 |

没有服务/Web/迁移/权限变化，无新增Chrome、真实HTTP/MySQL或非空升级结论，不免除L2/L3。

## 实际结果

证据根`.local/goal-20261017-resume/activity-lifecycle02/`。FORMAL两APK/lint exit0、2m11s；TEMP仅换隔离包身份构建两APK exit0、34s。lint零错误/严重错误、481警告，只执行一次适用源码lint。完整脏树构建，不声称本测试提交脱离继承输入可独立重建APK。

| 来源/阶段 | 结果与原日志 | PID |
| --- | --- | --- |
| FORMAL retain | OK(1 test)、ADB exit0、10.711s；`formal-retain.log` | 22376 |
| FORMAL consume | OK(1 test)、ADB exit0、49.623s；`formal-consume.log` | 22549 |
| TEMP retain | OK(1 test)、ADB exit0、3.386s；`temporary-retain.log` | 22844 |
| TEMP consume | OK(1 test)、ADB exit0、49.255s；`temporary-consume.log` | 22961 |

- retain实际Activity将RECEIVED变WAITING_CONFIRM，确认动作真实可见可用。转CREATED触发onPause后ready=false/排空，后台/更新互斥可进入释放；有序广播完成屏障后原capture不变、零scan、capture仍1。RESUMED/recreate仍原ID。
- consume不同PID读同一Room/原加密Session，重复生命周期后点击确认，原capture变COMMITTED、页面已保存1。实际同码广播形成序号2的INVALID和精确重复码原因，页面确认处理结果；再暂停广播不新增，唯一scan/guard。未调用engine.confirm代页面动作。
- `consumer01.json` SHA`b31e465d7159dc3faba7fda40c0834ba309c159645298b11c6e8df53f471e85f`：每源83表/schema一致，原capture除state/confirmedAt逐字段相同；旧事件保持，仅追加WAITING_CONFIRM、CONFIRM_REQUESTED、SAVED、RECEIVED、INVALID、FAILURE_ACKNOWLEDGED。暂停仅capture/event/device三表变化；确认FORMAL七表/TEMP六表精确增量，其余表保持。原资料/授权/计划不变，无submission/服务结果。
- 原应用83表328行不变，**业务库/WorkManager在内491个全部原文件长度/SHA完全一致**，无新增/删除；原APK/绑定不变。没有忽略或归一化WorkManager元数据。
- FORMAL产品/测试APK SHA：`ae710f27c3850bf78beda74b99c30eff802cdc10eb545cf45203dc994d4b0657`/`302a8019fd378b59e58b136d1f51a3f32715326eadcd52bb90fa12eac29c8bbb`；TEMP：`4b6f75ba9f17a479945b14e402879a6618fdb261041980b1e37b0635e1550cf4`/`8f8a9ef7fba2229424c73dc4b8707ee724f52e7d280ee7de61593ab531352fd6`。保留在`build-*01`，未发布/异地保存。

## 失败、自审与断点

首轮`activity-lifecycle01`的FORMAL setup缺localSessionId，在Activity打开前失败。业务DB前后SHA均为原种子SHA，零业务写入；合成Session已保存，不称应用全文件无变化。原日志/APK/输入/tar保留，未在该包/库重试；新增fm02/tm02，补合成标识/严格测试包匹配后复验。`failed-manifest01.json` SHA`68d73d0d3e798d1a0401bd039f3f5c29bbae08d2b79c7b533212dcab2104db1c`，1024文件210119551 bytes双核。旧长路径SQLite/原生断言及其他失败计数保持。

自审覆盖测试包白名单/原种子SHA、无产品注入、合成身份/时间、实际回调/按钮、广播屏障、全表/事件守恒。新测试文件/方法符合长度限制，无新增线上权限/秘密；`git diff --check`通过。标准字号的文本/动作断言不称四字号视觉审查；四字号按原页报告及SHA复用。一次报告/索引组合patch因索引锚点不全被原子拒绝，确认报告未写入后分开补入，不影响业务/原证据。

`evidence-manifest01.json`：1166文件275179009 bytes、SHA`798e1e522e6a8a6e412399e099aae82c5bae37e937d0a9e53812380fc7bb1d76`双核。复用/自审/提交元数据另存，不冒充原清单已含；原清单/原件保持。

`device-final01.json`：原App/两成功测试包无PID，font1.0/IME0/reverse空，AVD/ADB保留。无本批Java/Gradle/Kotlin/数据库任务、新监听；共享MySQL身份未知不连接/停止，真机未操作。C/D余量78704898048/15004090368 bytes。新增证据/临时均D盘，复用依赖，仅子进程TEMP/TMP/Java临时，无清理。

仅补有限实际生命周期和新PID原输入恢复，不代表物理断电/原身份/硬件/真实HTTP/MySQL或候选全链。D04旧前缀、D16正常合法资格、E06/E07/E08剩余组合、E09/D18及完整O/U按[矩阵](GOAL_D01_REQUIREMENT_EVIDENCE_MATRIX_2026-10-17.md)继续；Chrome1540审批不绕过，总目标active。
