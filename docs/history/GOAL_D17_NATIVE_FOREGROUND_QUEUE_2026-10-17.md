# D17/U04 原生广播暂停与原ID跨进程恢复组合

FORMAL/TEMP四个分离instrumentation阶段实际通过：原手输待确认输入经暂停/注销广播后保留，在不同PID中按原capture ID确认且只保存一次，重复广播准确拒绝，随后暂停不接收。Python全表、原输入/事件/防重及原模拟器守恒核对通过。仅有界测试组合开发备份，不收口D17、U整体、E08 L2或D18 L3。

## 输入、决定与影响映射

树`D:/acrylic-pda-outbound`、分支`codex/development`，运行HEAD/实际远端`19c61023c757c4af40f5670ba16ce1d6f465329a`。本恢复聊天唯一写者，清理聊天实际idle且已释放，无子代理；继承内容保护。版本事实见看板生成块。

`.local/goal-20261017-resume/input05`至`input09.json`及同号diff、未跟踪副本、status/worktrees、旧看板、写权、进程/监听/磁盘和远端保全。运行input08/09共5461项，SHA`4f6fd2890c9d0aacefc620d2874170e11e704ddf5772f44a4d09ebe4381c5bd6`；`foreground-queue02/source-binding01.json`核5457项继承输入逐字保持。input07是主机输入，SHA`507e69417abc663476012a13716c180389fa7185e6821b0e9ed77f71fc02b822`。

先写`foreground-queue01/design01.md`。采用原生接收器和产品引擎隔离组合，身份/时间边界明确合成，实际核正式JWT及TEMP原授权/摘要；不修改产品、迁移、权限、Web或服务。两类来源保留各自断言，共用证据/数据库原语，不引入业务抽象。两种新Room种子由主机产品激活/计划/接收流程生成，包含原签名合成fixture和一条RECEIVED手输输入；原AVD业务库不作fixture。成功`fq-20261017-03`与失败`fq-20261017-02`数据库分名，不复用失败副本。

| 改动路径 → 影响层 | 本单元必跑 | 复用依据（路径+SHA） |
| --- | --- | --- |
| `android/app/src/test/java/com/acrylic/outbound/pda/work/NativeForegroundInputTest.kt` → 两类合成Room出口 | 产品激活/计划/接收、关库后导出、签名/原ID绑定及主机引擎回归 | input07、`foreground-queue01/build02-artifacts/TEST-*.xml`及build02-hashes绑定7项通过；后续仅两原生断言修改，主机/产品输入相同 |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/queue/NativeQueueEvidence.kt` → 专名数据库/有序广播完成屏障 | 两源四次独立instrumentation、强停/新PID、种子hash、前后完整tar、失败不覆盖 | input08逐路径长度/SHA；不替代实际Activity/登录/原PDA事实 |
| 同目录`FormalForegroundDeviceTest.kt`、`TemporaryForegroundDeviceTest.kt` → 各自引擎/ScannerReceiver/UpdateGate | 暂停零接收、待确认原ID保留、新PID确认、事务外SAVED一次、重复码精确INVALID/原因、ACK及更新/后台互斥 | source-binding01四新增测试SHA；独立来源按[原生队列报告](GOAL_U04_NATIVE_INDEPENDENT_QUEUE_2026-10-16.md)、[反馈报告](GOAL_U04_NATIVE_FEEDBACK_L1_2026-10-17.md)及逐路径SHA复用 |
| 本地`foreground-queue02/consume02.py` → 独立SQLite/文件消费者 | 两源83表/schema、原capture逐字段、六事件、唯一scan/guard、原模拟器全表/文件及失败原件再次核同 | 直接读取原SQLite和tar；作者/执行者/自审为同一助手，独立工具算法不冒充独立人员 |

## 实际执行与守恒

- `foreground-queue01/build02.log`：7项主机0失败/错误/跳过、两APK/lint通过，97秒。修正原生断言后`foreground-queue02/build03.log`重建两APK/lint，53秒；没有重跑主机，`host03-reused-output.json`只是前次XML收集，不计新增测试。lint0错误/严重错误、481警告。
- 四次各`OK (1 test)`、ADB退出0，原日志/命令/result/前后tar在`foreground-queue02/`。FORMAL PID21558→21610，TEMP21666→21735；阶段前后显式强停并封存。不是精确OS异常杀进程时刻或物理断电。
- pause：RECEIVED→WAITING_CONFIRM，原手输字段/序号/ID保持，零scan/guard。注销接收器并暂停后，以实际有序广播最终接收器作完成屏障，产品接收0；前台阻止更新/后台，暂停后两互斥可进入/释放。
- restore：新PID读同专名库原WAITING_CONFIRM。明确确认后COMMITTED、唯一scan/guard指向原ID；SAVED回调核事务已结束且保存数1。实际同码广播形成新序号2输入，精确INVALID/重复码原因，ACK留痕；保存数仍1。回调`SAVED,ERROR`，暂停后再次广播不增加接收次数。
- `consumer02.json`严格核两源各83表/schema。seed→pause仅各capture、event及work_device变化；pause→restore仅FORMAL七表、TEMP六表变化。原capture除state/confirmedAt逐字段相同，原event完整保留，只追加WAITING_CONFIRM、CONFIRM_REQUESTED、SAVED、RECEIVED、INVALID、FAILURE_ACKNOWLEDGED六事件。原授权/资料/计划和其他表保持，未生成submission或服务结果。
- 四阶段原模拟器均83表328行逐值不变；464个既存files/shared_prefs/no_backup文件461字节相同，另3个WorkManager数据库/WAL/SHM明确单列，前后3已取消/0活动任务、一已取消任务ID替换。新增16文件仅本批种子/证据；失败轮2417项再次逐路径/长度/SHA不变。

种子FORMAL SHA`7372b0b2bb48e440a451dd013e0fa049360e906ab467e511ece6137c52a0bdae`，TEMP SHA`b59254fc5560a94488da7d052470d96d33e70b370b24b69fd335d0fb43675cf4`。产品APK SHA`29b5b3e376f2fbc01419b6aa19d6b4b3864abe4b8a077efd03715c0a5103771c`与前切片相同，测试APK SHA`e5db9cc1192254f8ccee2a4979fbc6d89fd6ce497ef1a5ee27e37bc6aaec149c`。完整脏树构建，不声称本测试提交脱离继承输入可单独重建该APK。

## 失败、修复与自审

build01新增出口漏设IO调度，被Room主线程限制拒绝；同轮四项SQLite长临时路径错误，原XML/日志/空种子目录保留。沿已有IO模式修复，任务临时改D盘短路径后build02通过；继承同类长路径失败不清零。build01复制的APK/lint可能是既有产物，不据其计新构建。

首次stage枚举了读取种子时生成的空WAL/SHM，首个输入复制后预检拒绝，未执行instrumentation。`stage02.py`核已复制字节后仅续拷另三个明确文件，无重装/覆盖；侧文件保留，后续精确列四文件。

首轮FORMAL restore有真实隔离写入，测试误等BUSINESS_REJECTED；产品在接收验证阶段正确给INVALID，原ID唯一保存、重复未保存。完整保全后才修正精确状态/原因断言，分配新副本复验。`foreground-queue01/native-failure-preservation01.json`为2417文件443286586 bytes双核，清单SHA`8602dbdfdd6960eacbe2b6eaf814abca91b0c78c5f329897b9d93ab4adac8d3b`。

Python首版实际通过；自审将长函数分成capture、事件/进程两责任，原consume.py/consumer01保留，consume02.py离线复核同原件通过，未重跑业务。新增四测试/方法符合长度约束，未嵌秘密或新增签名/HTTP旁路。首次报告/索引组合补丁因索引锚点不全被原子拒绝，确认报告未写入后分开补入，未影响产品或证据。

## 环境、证据与后续

证据4411文件791795141 bytes双次核同，`foreground-queue02/evidence-manifest01.json` SHA`54590acb9daf3f3991d198317139f59364d0b885232488cdc20df151f4d97968`；Git实际回执见上级`native-foreground-git01.json`，提交前后另存全量编号快照。旧看板先归档`ORCHESTRATION_STATE_before_native_foreground_queue_20261017.md`。

原AVD/ADB保留，App强停，font1.0/IME0/reverse空；仅install-r，无卸载/清数据。无本批Java/Gradle/Kotlin/MySQL任务或新增端口；共享mysqld4632/5440身份未知仅观察，真机未操作。最终C/D余量78717718528/15527215104 bytes；证据/tmp均D盘，Gradle复用既有依赖，仅子进程TEMP/TMP/Java临时，无清理。

本单元未验实际Activity前后台/全部三来源页面、硬件声效/扫码枪/原身份、新HTTP/MySQL或Chrome组合；未变产品/Web不机械重跑无关链。七类生产能力/TEMP归档及localDeletionAllowed=false保持关闭，不接原库/现网、不合main、不发布部署、不用正式签名。下一步更新D01–D18/O/U证据与剩余必跑矩阵，选择E07/E08尚缺组合；Chrome1540审批不绕过，D16有效guard合法退出缺口及E09/D18 L3继续，目标active。
