# E08 FORMAL/TEMP 结单入口 L1 开发备份（2026-10-16）

本切片将结单页面按原批次状态展示下一动作，并默认折叠诊断详情。TEMP冻结失败后的原批次分段补验、FORMAL新隔离副本的查询/验签/重启通过；不能计E08整体、U整体、L2、D18 L3或总目标通过。

## 输入、写权与影响映射

- 固定树 `D:/acrylic-pda-outbound`，分支 `codex/development`，输入HEAD `43bbb84708018afe68a921638b68650f6ac906c6`，读取时远端同SHA。唯一写者为聊天 `01a13a57-8082-7dd2-a08f-987483dcda4b`；清理聊天idle且complete-released，进度聊天只读idle，无子代理。
- 证据根 `.local/goal-20261016/e08-submission-entry01/`。input01–09、完整diff、全部tracked/untracked哈希、未跟踪原件、写权、进程/监听/磁盘和旧看板保留。input09与build04前input08同5313项，SHA `6e462bf1f39620b35c49f958cc8c672c22385eb0659e169dabe7497cc5f9acfd`。本报告及索引属于后加文档，不改测试输入。
- 版本事实见看板事实块。代码未改变schema、协议、权限或生产开关；本批不提交继承的恢复确认/worker等改动。测试APK使用整棵脏树构建，因此产品APK不能宣称只由本提交重建；完整输入按上述清单取回。

| 改动路径 → 影响层 | 必跑清单 | 复用依据（路径+SHA） |
| --- | --- | --- |
| 两个Submission Activity、两个NextAction和共享XML → FORMAL/TEMP真实页面 | Android实际入口、状态按钮、折叠/展开、原ID恢复、大字号与重启 | TEMP entry01→resume01实际原模拟器；FORMAL02新隔离AVD及独立SQLite/签名消费者，明细见下文 |
| SubmissionActionTest与TEMP测试 → 页面/原批次持久化 | 定向单测、产品/测试APK、lint、原设备全表守恒 | build03原XML及build03-hashes.json；build04产品APK同SHA，单测UP-TO-DATE只复用 |
| FORMAL fixture/test → 丢响应与worker并发夹具 | 所有首次查询断连、显式开放原结果、同ID成功、强停重启 | build04 APK与formal02 opening/restore原日志；原响应/DB依据formal02/input-reuse.json的逐文件SHA |
| 未改服务端/消费者产品代码 → 历史HTTP/数据库业务 | 本批直接入口只覆盖回放/离线；受影响真实HTTP组合仍须另跑 | 原FORMAL输入 `.local/d01-d18-20261008/d07-formal-pda-ui-06/before.db` SHA `3ab74d2a8d01868f60d4d601b8e905ffc860584859613531a2c5e547e5b0cd69`，不是新真实MySQL通过 |

本批源码/测试输入：

| 路径 | SHA-256 |
| --- | --- |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/SubmissionActivity.kt` | `0948fa47b68a8eaad0fd8ca2d0ceaaef473d2783dd722fb8d77f3441c08e0c79` |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/TemporarySubmissionActivity.kt` | `1c01de7aa8b85657c39cd64b667011362477aa7305ab5c60911a7633858b86d8` |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/SubmissionNextAction.kt` | `dab7fa86810824ffdac4a04b5eb69760e630a6504151c5e3fbdc3f185c09b1eb` |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/TemporarySubmissionNextAction.kt` | `428658e59c54f585e5a872a7140e0b59dd6777127a055cd92b076875f2b1e961` |
| `android/app/src/main/res/layout/activity_submission.xml` | `c014143a301515eb49aafb9953aeb8a3035c08d03ee540710030c9dae71fe706` |
| `android/app/src/test/java/com/acrylic/outbound/pda/ui/SubmissionActionTest.kt` | `84d5ab708037366a7a5ddcce141edb8b5c7f3b76dc3dfcb4781e10b2fd962305` |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/TemporarySubmissionEntryDeviceTest.kt` | `1e8d3a73662bf60ac0a88760792c1d2ba189c960306f64211c118977b1f27b25` |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/TemporarySubmissionOfflineResumeDeviceTest.kt` | `d028b58d3102f0d05093dc18387f06896c117ef3e984cb6c26ea2637004f82ad` |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/FormalFaultSubmissionDeviceTest.kt` | `bde603c31934840383b527c53654d283b37e1f59e7dca6b01cd5a191cf4e9523` |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/FormalFaultUiFixture.kt` | `6a0cf108e86ccb2a4a260f7ffe0cf1718ac1c504f7cbb9a5817de5289d76ae9a` |

## 实际执行与失败保留

1. build03定向6通过、1跳过，产品/测试APK及lint成功；424警告、0错误。`TemporarySuccessHttpTest`跳过未执行。build04退出0，产品/测试APK成功，单测和lint任务UP-TO-DATE，复用build03原XML/lint，不计新执行。命令和退出码见build03/04-command.json、result.json与android-build03/04.log。
2. TEMP entry01在原订单 `73a1391f-e140-41f2-9b8c-cf5ab6eaf42c` 取消冻结后再确认冻结，重建成功，随后旧英文网络错误断言失败。已有业务写入，完整前后tar/DB/日志/截图保留；未重置或重做冻结。仅沿原submission `8396b6c8-d3f3-4b41-a827-0f24dc6e4525`、payload SHA `803e6bde94c8e3ab7968d3476ca28562378acf841475b27c3db6c647a64bd883`由resume01补实际查询、详情展开、页面重建通过。该结果是分段补验，不能记首次整套通过。
3. TEMP独立Python消费者首次误把STOP sourceId当订单ID；第二次漏manifest域前缀。consumer01/02-failure.json与旧脚本均保留，没有重跑业务。修正为产品协议后consumer03实际执行通过：83表受控差异、原ECDSA STOP签名、72请求/69原对象及manifest/page链、原采集/扫码/设备绑定保留；失败后与resume01后业务表完全相同。1207原件双次SHA/长度不变。
4. FORMAL01首次UI GET断连，但UploadWorker再次查询同ID并取得原签名成功回执，旧固定调用次数断言失败。失败副本确实写成SYNCED，完整AVD、前后tar/DB、原日志/截图/65文件清单保留，认证停止模拟器；不复用、不重置、不追认通过。根因和实际差异见formal01/failure-analysis01.json。
5. FORMAL夹具修正为首阶段所有同ID查询均断连；取消并等待原repository后显式开放原回执，记录不可取阶段请求数。新副本FORMAL02（E08FormalAction20261016B，5568/5569）opening.log及restore.log分别 `OK (1 test)`，runner退出0。640×960、字号2.0，实际保留的“提交与回执”入口→丢响应→先查原批次→原RSA回执验签→强停/重启通过，异身份不可见。只测保留入口，不恢复已取消换机承接/多跳/激活。
6. 独立工具consume_formal02.py实际执行通过：旧63表迁为83表、新20表全空；首阶段2次断连，第3次GET同ID取得原结果，无POST；只改变提交状态和Room元数据，其余业务行守恒；重启后全部业务表不变、无新增请求。是合成会话/MockWebServer原响应回放，不是真实MySQL、原FORMAL身份或独立人员审查。consume_formal01.py只是未执行旧成功模板，不能作通过依据。
7. lost-result、verified-result、restarted PNG已打开自审：大字号可滚动，无横向裁切。作者自审与独立工具消费者分开记录，没有真机或Chrome新通过；1540 Chrome自动审批阻断继续保留，不绕过。

## 产物、环境与保全

- 证据根下产品APK `build04-artifacts/app-debug.apk` SHA `bebabdf097f5768e5a8e7f4a992c85f0a901e5197e39a645ec15bd4c69fb0682`；测试APK `build04-artifacts/app-debug-androidTest.apk` SHA `d0ff0b0c4affcf1fa692895756c057db495790bbae6a9be1d60036a8ec57d580`，已核实际路径/字节，保存原件，未发布。
- preserve_final01.py退出0；FORMAL01旧65文件未变、FORMAL02证据和完整AVD93文件双次SHA/长度不变，formal01-preservation02-recheck.json及formal02-preservation02-recheck.json为索引。
- postformal01-original-temp-result.json确认原TEMP设备83表与resume01后完全相同、App无进程、reverse为空；原TEMP AVD TemporaryAdjustment1364保留运行于5564/5565，ADB5037；FORMAL01/02停止，5566–5569零监听。未新建MySQL，共享mysqld4632/5440身份未核清，不连接。
- input09 disks记录C/D余量83,083,309,056 / 34,543,534,080 bytes；完整进程/监听在processes-09/listeners-09.json；重型前复测。证据、APK、AVD与临时目录位于D盘，未清理原件或全局改环境。

## 未完成与下一步

TEMP无真实HTTP受理/成功回执，本批FORMAL为回放；受影响真实HTTP/MySQL及完整Android组合、E06/E07 L2、E09候选和D18 L3仍须按实际输入执行。该提交仅L1开发备份，继承改动及失败计数保持；明确路径提交推送后继续D16整单事实清理真实缺口及其余可独立工作。生产删除、七类决定能力、TEMP归档边界和localDeletionAllowed=false均保持关闭，不合main、不部署/Release、不用正式签名、不接原库/现网。
