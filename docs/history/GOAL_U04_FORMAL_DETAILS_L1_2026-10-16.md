# U04 正式明细有限分页 L1 开发备份（2026-10-16）

正式订单明细改为每页最多20条的只读前后页，增加状态筛选、原序号和完整条码末次定位，保留原行及原序号。最终APK四档字号实际交互、逐表消费及原模拟器保全均通过。仅本切片L1开发备份，不计U整体、E08 L2、D18 L3、第8节或总目标完成。

## 输入、写权与设计

- 固定树`D:/acrylic-pda-outbound`、`codex/development`，输入HEAD及实核远端`c61cf6acaf2cdc0965c3b7c0348ee00c4b479a7b`。本聊天`01a13a57-8082-7dd2-a08f-987483dcda4b`唯一写者，无子代理；清理聊天实核idle且已释放全部窗口。
- 证据根`.local/goal-20261016-resume/formal-details01/`。设计见`design01.md`，按总目标已有授权自主采用固定上限keyset页，避免不断累加记录和页游标；沿原XML/青绿标识，不新建业务命令或依赖。
- `input39`保存接手基线；`input40`至`input46`及同号附件保存全部tracked/untracked长度/SHA、binary diff、未跟踪原件、worktree、旧看板、写权、进程/监听和C/D余量。最终产品/测试输入绑定`input46.json`，5436项，SHA`b1bc75023c6c1af1077aae0cae95d925660404181620ca0b4ef835ffc674eb60`；其后仅报告/索引/断点变化。
- `source-review04.json`逐文件绑定11个本切片路径，核86项继承改动逐字保持，包含继承Android/UI/工具/文档及`server/physical-disposition-last-request.json`。作者、自审均当前助手，独立Python算法消费不称独立人员审查。

## 改动路径 → 层 → 必跑 → 复用

| 路径 | 影响层与必跑 | 复用依据 |
| --- | --- | --- |
| `work/ScanDao.kt`、`work/WorkDetailRow.kt`、`work/WorkDetailsReader.kt` | Room只读投影；真实非空Room分页/倒页、状态、定位、身份拒绝及扫码/VOID回归 | 工作目录均在`android/app/src/main/java/com/acrylic/outbound/pda/`；逐路径SHA见`source-review04.json`。这三路径及Reader测试在input44→46逐字不变，复用host03实际23项 |
| `ui/WorkDetailsActivity.kt`、`ui/WorkDetailsText.kt`、`res/layout/activity_work_details.xml`、AndroidManifest | 产品入口、中文状态/资格、系统栏/IME、大字；最终debug APK/lint和四字号真实交互 | 未改模型/schema、签名、保存、调整/导出权限、服务或迁移；无关HTTP/MySQL/Chrome链按input39/46路径SHA及既有报告复用，不能免除后续L2/L3 |
| `WorkDetailsReaderTest.kt`、`FormalDetailsDeviceTest.kt`、`FormalDetailsDeviceUi.kt` | 主机与原生消费者；SCAN/VOID实际生成输入，真实触摸/键盘/页面重建，Python逐表守恒 | 路径分别在`android/app/src/test/.../work/`与`android/app/src/androidTest/.../pda/`；合成身份明确标记，不称原PDA现场事实 |
| `FormalDetailsScrollDiagnostic.kt` | 仅诊断原生滚动范围/坐标，受显式debug/模拟器/参数约束 | 直接scroll/rectangle诊断结果不计用户成功路径；验收要求真实MotionEvent、实际安全区和实际点击 |
| 本报告/索引/看板及旧结论归档 | 证据、失败、环境和Git范围 | 版本只见看板生成块；不复制时效版本数字 |

## 产品行为与定向验证

原人原机身份在读取事务前后核对，SQL词汇固定、数据使用绑定值。每次只渲染一页，不累积历史页；已保存、已撤销、未处理输入分别筛选。序号精确定位，完整条码取末次原记录，定位重置为全部原记录；无匹配明确提示，原记录不变。主管保留和等待落盘不显示成新保存。页面重建保留筛选及页起点，取消协程继续抛出。

原调整/应急导出入口沿既有权限；本批不执行调整或导出命令。自审发现资格异常英文直接出现在按钮，改为中文摘要并可展开原原因；进一步修复禁用按钮文字对比度，使用项目正文色。原始`No value for timeAnchor`不丢弃，不伪造缺失的可信时间资料。

`host03.py`实际运行Reader 6、Adjustment 8、Engine 3、Flow 6，共23项，0失败/错误/跳过、exit0，日志/XML/命令保存。覆盖20/20/10前后页、空单、四种未处理状态、重复条码末次定位、非法/溢出输入、跨身份拒绝、主管保留前缀及原事件/汇总保持。此后只改UI文案/布局和原生取证，四个Room/Reader路径input44→46字节一致，未机械重跑未受影响主机业务。

合成输入通过真实LocalActivation、WorkReception/Processor和VOID产生，50个capture、46个scan、37 ACTIVE、9 VOID、4个未处理输入，83表342行、37有效guard、有效总量37包/37克。`host03-input/synthetic-formal.db` SHA`d06ac41f66f8619892f6fd9e93624e4b66dd1639ea87dfcdc05ce4368e716713`。输入不代表原设备、实物或现场身份。

`build11.py`实际assembleDebug/assembleDebugAndroidTest/lintDebug退出0，lint无Fatal/Error、451项Warning；原XML保留，不称无警告。最终产品APK SHA`245e409679a0ecd4a2e8446fa81ad41417f2dc728fe55c385d74210723ab1c29`，测试APK SHA`d36b7e181d97840135109aaae321b601a8c6e377a4a1d9ba68120e554a6e36b1`，均在`build11-artifacts/`保留。不是正式签名或Release。

`four_fonts11.py`顺序安装并核设备内APK哈希，运行`release100-01/release130-01/release150-01/release200-01`，每次ADB退出0、`OK (1 test)`。实际通过资格中文/原原因弹窗、三页及倒页、首末页按钮、筛选、序号/条码定位、无匹配、重建、身份变化拒绝、真实IME、返回。真实稳停拖动后独立断言控件在系统栏/IME安全区内再实际点击；保留100/130/150/200%长车号/品名及无横溢/省略检查。

每轮`consume02.py <label>`独立消费tar/SQLite，完整性/FK与83表342行逐值一致。四份`*-consumer02.json`均通过。截图与文本在每轮消费目录`files/formal-details-evidence/<label>/`；作者实看最终100%和200%资格文案、弹窗及键盘安全区。app Canvas图片不冒充IME本身截图，真实键盘可见由原生insets断言证明。

## 失败、纠正和保全

- host01运行器清单含不存在的WorkStoreTest，实际16项先通过后清单断言失败；改为WorkFlowTest。build01 Espresso重名编译失败后定向修复。原日志和旧XML均保存。
- host01/02导出只创建未step的WAL checkpoint游标，主文件副本少尾部4行；原生末页读6条而非10条失败。旧46行库和1701完整保留；host03实际step并断言非busy、关闭生产者、复制后独立打开核50行，未降低20/20/10标准。
- preflight01把不存在databases目录的诊断文字当内容，APK已装但尚无业务写入；保留记录后定向修正。
- 三种滚动验证器失败原日志、坐标和tar保留：快速滑动过冲、慢速滑动不前进、原生scrollTo未达控件。每轮83表业务不变；诊断新坐标/范围证明后改稳停MotionEvent拖动，仍执行相同可达/点击和业务断言。直接滚动诊断不是验收通过；失败计数不清零。
- 四字号旧APK完整交互曾通过，仅作历史；最终资格/对比度修复后重新执行上述四轮。build09/10及其100/130%结果亦保留，不替代build11证据。
- review03只读审计默认GBK解码失败，review04显式UTF-8后通过；没有重跑业务。finish02复用已存在解包目录失败；finish03比较先前已被SQLite消费者checkpoint的解包副本失败。原始tar前后455个文件长度/SHA全部相同，tar SHA同为`21703060fdb3c67c6219d8c5fca0caa1c0d30a9810ce393438e0008e114feb06`。finish04从两原tar另取新消费副本，83表328行逐值一致；不修改原设备或原tar。原失败脚本及诊断缺WAL输出保留。
- 先前重复无必要确认及未经证实的“真机/可见性过滤”推断已撤回，真实根因是合成WAL副本漏尾行。没有等待这些请求或更改业务标准。

## 环境、边界与下一步

新AVD1702身份`FormalDetailsU041702`、5572/5573实际核对，最终四轮完成后恢复其字号/IME设置并停机；失败1701/5570/5571保留。`final-environment01.json`及final03进程/监听证明5570–5573零监听、无本批emulator/qemu残留，无Java/Gradle重型任务。原TEMP AVD12912/qemu12108、5564/5565与ADB8500/5037保留；真机未操作，共享mysqld4632/5440身份未知，不连接/复用/停止。

原5564前后455文件逐字相同、83表328行保持，见`original5564-consumer04.json`。构建前C/D余量77452075008/31073251328 bytes，预计新增≤1GiB；停机后采样77448675328/30864261120 bytes，实际终值见final03-disks。新增AVD/日志/证据/tmp均D盘，仅任务子进程环境，无本批清理。

封存3970份证据12853102777 bytes两次长度/SHA核同，清单SHA`6f0418da6f84149e5a97baf09724c70df8a475c571fdafceeafafc9742832858`；见本证据根`evidence-manifest01.json`、`manifest-double-check01.json`。提交前全量快照input47共5437项、SHA`018594ad1489c56edd0c9761a86c56270f7bbba9f2286d81b82272158cb40aae`，暂存清单和推送回执另存在父证据根，按明确路径保存本切片，不混入86项继承内容。

后续直接补TEMP明细状态筛选/原序号与条码定位/未处理输入及有界分页，沿原签/身份和历史只读前驱保护，不恢复取消的换机接续。随后继续D17、E06/E07/E08剩余L2、D16正常有效guard目标合法退出、E09实际候选和D18全链L3。生产七类能力、TEMP生产归档与`localDeletionAllowed=false`不变；不接原库/现网、不合main、不发布部署。总目标active。
