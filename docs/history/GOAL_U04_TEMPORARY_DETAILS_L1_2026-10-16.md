# U04 TEMP明细有限分页 L1 开发备份（2026-10-16）

TEMP明细增加每页最多20条的前后页、状态筛选、原序号与完整条码末次定位。最终APK四档字号及阻断前驱的真实交互、独立Python全表消费通过；原模拟器字节与业务事实保持。仅本切片L1开发备份，不计U整体、E08 L2、D18 L3、第8节或总目标完成。

## 输入、写权与方案

- 固定树`D:/acrylic-pda-outbound`、`codex/development`，输入HEAD及远端实核`b7436c7dea746ae175dbe236edbb6203c2553cf2`。本聊天`01a13a57-8082-7dd2-a08f-987483dcda4b`唯一写者，无子代理；清理聊天在2026-10-16T22:35:57+08:00再次确认全部释放、无在途写入。
- 证据根`.local/goal-20261016-resume/temporary-details01/`；`design01.md`按已有授权选择独立TEMP reader和XML，复用中立行投影及既有中文状态，不提取只有两处使用且身份/前驱规则不同的页面基类。固定页上限与游标，不累加历史页。
- `input49`为本切片基线；`input50`至`input54`及同号附件保存全部tracked/untracked长度/SHA、binary diff、未跟踪副本、worktree、旧看板、写权、进程/监听和C/D余量。最终产品/测试16路径绑定`input53.json`，5452项，清单SHA`91e65c107e2913f9c184dd2461070e7ab60e610cd340c074303803c484523b12`；`input54`同SHA。`source-review01.json`确认86项继承改动逐字保持，含Android/UI/文档/工具及`server/physical-disposition-last-request.json`。
- 作者、自审均当前助手；Python为独立算法消费者，不称独立人员审查。版本事实仅见看板生成块。

## 改动路径 → 影响层 → 必跑 → 复用

以下Android路径均相对`android/app/src/`，逐路径长度/SHA见`source-review01.json`。

| 路径 | 影响层与必跑 | 复用依据 |
| --- | --- | --- |
| `main/java/com/acrylic/outbound/pda/temporary/TemporaryScanDao.kt`、`TemporaryDetailsReader.kt`、`TemporaryDetailsPrefix.kt`、`TemporaryDetailsQuery.kt`、`TemporaryDetailsTotals.kt` | Room只读快照、当前输入LEFT JOIN原scan、已验前驱；非空SCAN/VOID输入、分页/倒页/筛选/末次定位、身份拒绝、原签/全表守恒 | Reader/Prefix/Query/Totals在input50→53逐字不变；host03的21项及host04直接受影响6项实际执行，复用路径和SHA在review的reuse区 |
| `main/AndroidManifest.xml`、`main/java/com/acrylic/outbound/pda/ui/TemporaryDetailsActivity.kt`、`TemporaryDetailsText.kt`、`main/res/layout/activity_temporary_details.xml` | 产品入口、中文资格、系统栏/IME及大字重排；最终debug APK/lint、320dp四字号真实点击/键盘/重建/辅助页返回 | 无模型/schema/迁移、服务、HTTP、MySQL或Web变化；未受影响链按input49/53路径SHA及已有报告复用，不免除后续L2/L3 |
| `test/java/com/acrylic/outbound/pda/temporary/TemporaryDetailsFixture.kt`、`TemporaryDetailsFailure.kt`、`TemporaryDetailsReaderTest.kt`、`TemporaryDetailsPrefixExport.kt` | 真实主机Room/SCAN/VOID产生合成输入，失败库保全与签名原文导出 | 原Adjustment/ContinuationWork测试路径SHA见review，不以fixture冒充原PDA事实 |
| `androidTest/java/com/acrylic/outbound/pda/TemporaryDetailsDeviceTest.kt`、`TemporaryDetailsDeviceUi.kt`、`TemporaryDetailsPrefixDeviceTest.kt` | 原生真实触摸/键盘/滚动安全区、筛选、定位、身份变化拒绝及只读前驱 | 最终设备内产品/测试APK逐轮SHA核对；所有输入明确合成，不授予生产或实物资格 |
| 本报告、进度/历史索引、看板及旧结论归档 | 可追溯结果、限制、失败与提交范围 | 不混入86项继承内容，不使用`git add .` |

## 实现与实际验证

读取事务前后核原人原机与订单资格，固定内部SQL词汇、用户值仅绑定参数。原capture保留VOID和未处理输入及原序号；完整条码定位末次原记录，定位时回到全部状态，无匹配明确提示。每页最多20条，前后页/首批及重建保持筛选/游标。取消协程继续抛出，失败不显示成功。

R06前驱只读已核验不可变原文；阻断目标无需扫描下载ack即可核原，不能获得新扫描或激活资格。历史换机投影仍只读，不恢复取消的承接/多跳/激活。汇总明确“有效原记录”“前驱原记录仅供只读核对，不授予新作业或实物放行资格”；调整、取消、应急导出沿原入口/权限，本批只验导航与返回，不执行这些业务命令。

`host03.py`实际Reader6、Adjustment6、ContinuationWork9，共21项，0失败/错误/跳过、exit0；修复fixture及增加前驱导出后，`host04.py`对受影响Reader6项复验，0失败/错误/跳过。未受影响15项按上述路径/SHA复用。覆盖空/非空、20/20/10分页及倒页、四种未处理状态、筛选、序号/完整条码末次定位、无效/溢出输入、跨身份拒绝、前驱与本轮汇总和原事件不变。

主输入由实际TEMP产品SCAN/VOID产生：`host03-input/synthetic-temporary.db` SHA`8c9d427b4a397722ee0ccb0c4f3d1654690692496a722c4b8a0a70857b1d5b9b`，83表303行、50 capture、1 ACTIVE、45 VOID、4未处理。前驱另用`host04-prefix-input/`新输入，83表65行，目标仍`CONTINUATION_BLOCKED`。均为合成身份/签名，不称原设备现场事实。

`build04.py`实际assembleDebug、assembleDebugAndroidTest、lintDebug退出0；lint 481项Warning、0 Error/Fatal，不称无警告。最终产品`build04-artifacts/app-debug.apk` SHA`29b5b3e376f2fbc01419b6aa19d6b4b3864abe4b8a077efd03715c0a5103771c`，测试APK SHA`269eee711bcc1919f1b5de0365a93aaa8f819a68b870d79d1680f809991fc85c`。均保留，非正式签名或Release。

新AVD1703 `TemporaryDetailsU041703`/emulator-5574，480×800、240dpi即320dp。`device04.py`在每轮核实际设备内两APK SHA；`final100-01`、`final130-01`、`final150-01`、`final200-01`均实际通过分页/倒页、筛选、序号/条码定位、无匹配、重建、身份拒绝、真实IME及安全区、取消/应急导出页面返回，ADB exit0且`OK (1 test)`。`consume01.py`逐轮独立消费原tar的新副本，完整性/FK与83表303行逐值保持。作者实看最终100%/200%滚动位置、中文资格/键盘区域截图；图片为App Canvas，实际IME可见和控件可达由原生insets与真实触摸断言证明。

新AVD1704 `TemporaryDetailsU041704`/emulator-5576使用新前驱输入；`prefix-device02.py`的`prefix200-02`在200%实际通过前驱显示/筛选/条码定位、跨身份拒绝、调整禁用。`consume-prefix03.py`独立核83表65行全部不变、原payload SHA`9476ea35b9fe8b23028413272ad1c61df4a77bf282746f24d1af76cbdd2b8d2f`，并用cryptography独立验证合成RSA原签、算法/keyId/audience/receipt。无新capture/event，保留输入既有1条`TEMP_CONTINUATION_ACTIVATED`，目标仍BLOCKED且ackJson为空。

## 失败与纠正

完整指纹/计数见`failures01.json`，不清零、不因换上下文重复原失败。

- host01产品问题1次：读取汇总误调用要求续作ack的扫描汇总。改独立只读Totals后补验。旧继承teardown删除了那份确切失败DB，`host01-failure.json`明确该限制；原日志/XML保留，不能把重建库称原失败库。
- host02 fixture问题1次：激活后读取已替换catalog的旧fixture条码；改为激活前保存。原日志/XML/堆栈与teardown前实际`VACUUM INTO`的`host02-failures/24c09980-51b8-4d8c-b558-fbc3c89bc2b4/failed.sqlite`保留。
- temp100-01运行器问题1次：直接点击屏幕外应急页返回。原tar及独立消费者确认83表303行未变；修复为真实拖动reveal、安全区断言及实际点击，旧APK成功后最终APK另完成上述四档。
- prefix200-01系统异常1次：真实焦点为`Application Not Responding: com.android.systemui`。完整logcat/window/ANR XML与83表65行不变证据保留；点击已观察的Wait后核launcher焦点，再运行新prefix200-02，未改产品。
- 三项只读审计器错误分别为import路径、误认为目标无既有安装事件、误把X509 DER公钥当JWK。原件保留，consumer03纠正后独立通过，没有为审计修复重跑业务。

## 保全、环境与未验

原5564仅捕获前后tar：两tar SHA同为`21703060fdb3c67c6219d8c5fca0caa1c0d30a9810ce393438e0008e114feb06`。`finish01.py`先直接比较两原tar全部455文件长度/SHA，再从两份新消费副本核83表328行相同；不比较此前被SQLite消费者checkpoint的解包目录。原设备、ADB及共享MySQL/真机均未更改。

1703恢复原字号1.0和IME设置0，核reverse为空、强停应用、正常`emu kill`；1704已恢复字号并停机。`environment-finish01.json`及同号进程/监听证明5574–5577零监听，无本批emulator/qemu/Java残留。原5564/5565、emulator12912/qemu12108、ADB8500/5037保留，共享mysqld4632/5440不连接/复用/停止，真机22D2910042未操作。

最终停机采样C/D余量77436452864/18235404288 bytes，见`final-disks01.json`；各次重型动作前采样与增长预算在原运行器证据中。AVD、tmp、APK、日志和所有证据均D盘，仅子进程设置环境，无本批清理。原失败、新AVD、数据库及最终APK保留。

`seal01.py`流式双核1770文件、12559082116 bytes，清单SHA`b643b96f77ca06f99163da82e8c243e9dec674b49ff7e14ea5567bec2ac71966`；见`evidence-manifest01.json`、`manifest-double-check01.json`。这只是本机保存，不称异地备份。提交前后全量Git/改动/写权快照与推送回执保存在父证据根。

后续继续U/O完整组合、D17、E06/E07/E08剩余L2、D16正常目标合法退出及可取回/恢复组合、E09实际候选与D18全链L3。Chrome1540旧审批阻断及历史失败/暂缓保留，无新依据不换包装重试。七类生产能力、TEMP生产归档、`localDeletionAllowed=false`不变；不接原库/现网、不合main、不发布部署。总目标active。
