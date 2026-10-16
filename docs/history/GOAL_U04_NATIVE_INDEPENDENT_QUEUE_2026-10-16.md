# U04 独立队列原生故障、原ID恢复与满入口（2026-10-16）

三个分离原生 instrumentation 阶段实际通过：末条保存事件 SQL 失败不报成功，复制失败库后按原 capture ID 恢复一次，满入口拒绝新输入且65条已接收输入全部耐久保留。各阶段原业务库83表328行及原业务文件不变。本批是 U04/D17 有界组合开发备份，不计 U 整体、E08 L2、D18 L3 或总目标完成。

## 输入与验收映射

树 `D:/acrylic-pda-outbound`，分支 `codex/development`，输入 HEAD/实核远端 `e2e3ee471dc160c68662c15d38e5e44ec5ffcefb`。本聊天唯一写者，清理聊天 idle 且已释放，无子代理；86项继承脏内容保护。input35/36.json 全量5424项，SHA均 `ad5abfb88623d2b505afd25e08578f099f52b8688d2c55cefc479af744a9f1fa`，差异/未跟踪副本/旧看板/写权/进程/监听/磁盘同号附件齐全。

本地证据根 `.local/goal-20261016-resume/native-independent-queue01/`；`design01.md` 在执行前记录范围与默认：复用前一缓存切片真实生成并已核验的合成 Room/签名/时间锚点，不重新签发原身份或修改系统时间。每阶段使用单独命名数据库，原主业务库从不作为夹具；失败库不原地解除触发器。原生 engine 消费者不冒充页面交互、真实 HTTP/MySQL 或现场原 PDA。

| 改动路径 → 影响层 | 本批必跑 | 复用路径与 SHA 依据 |
| --- | --- | --- |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/independent/NativeIndependentQueueFixture.kt` → 合成原生 Room/授权/前后取证 | 绑定输入 SHA、独立库、事务外回调、全表JSON/DB、原库守恒、构建/lint | `source-review01.json` 保存本文件及全部既存 Android/server/Web 路径+SHA，input35全部字节执行后核同 |
| 同目录 `IndependentQueueDeviceTest.kt` → 队列故障/恢复/背压消费者 | 三次独立instrumentation：fault/recover/overflow，全部实际通过 | 前切片 `host03-tests.json` 的25项定向路径+SHA登记在source-review01；产品/既存测试没有修改，不机械重跑原HTTP/Chrome链 |
| 报告/进度索引/看板 → 接续证据 | manifest双核、Git与继承保护、生成事实与上限 | 缓存切片/声效切片正式报告按索引保留；没有降低最终L3必跑范围 |

## 三阶段实际事实

- **fault**：输入原 `host02-db/pending-before.db`，SHA `6fb56ebc2db21db12e28399cf4cd72645313622e320dde9dc8dd015f889d3f00`。真实 engine 处理原接收输入为 WAITING_CONFIRM，再显式确认；合成触发器在末条 INDEPENDENT_SAVED 插入报 `SQLITE_CONSTRAINT_TRIGGER`。原 capture 全字段不变、状态 CONFIRM_REQUESTED 和原确认时间耐久保留；scan/guard/汇总均未提交，无 SAVED 回调，故障后新输入被拒。仅两条先前合法事件及对应序号/状态变化；完整83表与含触发器库保留，输出 SHA `53d60b3e36e221bd78de8ec6b7ca8797577fbe52c84497afe30a9430b75a786c`。
- **recover**：先强停并复制上述失败库到新命名数据库，在新副本解除本批合成触发器。另一次原生进程运行真实 engine，原 task/capture/原输入和确认意图保持；只新增一条 scan、guard、SAVED事件及状态/汇总更新，事务外成功回调一次。暂停拒新输入，关闭并新建 engine 再授权不重复保存或回调。输出 SHA `5c120021c23f928b3ff9c3188e903b43e530f3f2464b74daede9bf5a1f3902a5`，原失败库及触发器保持。
- **overflow**：另取已验 `host02-db/saved-before.db`，SHA `1f2db13c9cf4119cd2a3b0c0cff9e8f9a9b99436af27b258fbc00b1c84ec05d2`。阻塞首个任务，接收一个执行中加64个排队输入，下一个明确拒绝并显示入口已满；暂停后释放阻塞。65个已接收输入全部保存为 RECEIVED，原序号2–66连续且ID唯一；原已保存capture、scan、guard和有效总数1保持，没有新增SAVED回调。只增加65个capture/state/接收事件及设备/任务序号，输出 SHA `4f08fa48763fd03caf4a24fa5f2f5aae43df0a719e33a14db0f88da9896fae3c`。

三个阶段各自 `OK (1 test)`、ADB退出0，原始日志/命令/输入记录/完整前后tar均保存。业务阶段一次执行，没有失败重试。fault是预期注入且测试通过，不能省略其原数据库、异常或触发器证据。复制恢复是本批设计的独立业务阶段，不把它写成原失败库“修好后覆盖”。

独立 `consume.py` 实际读取每阶段 SQLite/JSON，完整性与外键通过，分别只允许上述3/5/5张表变化，其余逐值不变；原capture字段与行序、事件类型/数量、原ID/正文/回调和所有拒绝均有断言。三个阶段原主业务库均83表328行无变化。433原 files/no_backup/shared_prefs 中430逐字不变，变化的3个文件仅 WorkManager DB/WAL/SHM；各阶段新增7/12/17个累计合成输入和证据文件，没有其他删除或改写。`runtime-consumer01.json` 保存完整运行元数据，前后各阶段3个已取消工作、0个活动工作。作者、测试和自审同一助手，独立Python工具消费不当独立人员审查。

## 构建、封存与边界

`build01.py` 实际 assembleDebug、assembleDebugAndroidTest、lintDebug 成功；lint 0错误/严重错误、438警告。产品 APK 与前缓存切片字节相同，SHA `c183d27c046c6afe7742b2612d078f0d3c73b4dae191b77fa8c7227bf0eb7192`；测试 APK SHA `e2c23ad67868b58cb1ead9ff0b888eb32f56822ee48ee365207a5307e84b7803`。产物及覆盖前两原APK保留，无卸载、清数据或正式签名。TEMP/TMP/Java临时目录仅任务子进程 D盘，重型前余量与预计增长保存；没有新服务/数据库端口。

3176份证据381046366 bytes 两次长度/SHA核同，清单 SHA `a7c944cf46e313d7f03be6e2d9e55df48a8d1801b5f5e4e1bb638a9c591c2a45`。字号1.0、IME设置0、reverse空、App强停；原AVD/ADB保留，真机未操作，共享 mysqld4632/5440不连接/停止。封存C/D余量78062534656/43651395584 bytes，无清理。

没有修改产品、迁移、权限或服务端；七类生产决定能力、TEMP生产归档和 `localDeletionAllowed=false` 保持关闭。本批不证明全部来源/页面生命周期、真实USB/原身份、系统强杀时刻、物理断电、目标PDA长时性能或最终候选全链；E06/E07/E08剩余L2、D16正常目标合法退出、E09候选和D18 L3仍未完成。下一步以当前源码核对D17兼容及其余U故障/布局验收单元，持续推进独立工作，不重试Chrome1540审批阻断。
