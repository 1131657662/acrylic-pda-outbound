# 独立采集引用的旧同步缓存保护 L1（2026-10-16）

修复旧同步条码投影清理遗漏独立来源引用的问题。修复前 pending、saved、VOID 历史三个原生 Room 场景均实际误删；补全保护集合后25项主机回归及原隔离模拟器消费者通过。仅为缓存保护 L1 开发备份，不计 D16 整单事实清理、E08/U 整体、L2、D18 L3 或总目标通过。

## 输入、设计与验证范围

固定树 `D:/acrylic-pda-outbound`，分支 `codex/development`，输入 HEAD/实核远端 `e9000e2200322801629a698f23deda2f2016c258`。唯一写者为本聊天 `01a13a57-8082-7dd2-a08f-987483dcda4b`；清理聊天已释放写权且实际 idle，无子代理。86项继承改动、原请求文件和全部未跟踪原件保护，暂存开始为空。

证据根 `.local/goal-20261016-resume/independent-cache-retention01/`。input29 保存实施前5418项，SHA `c7b631d05513bb1cada6b3549169b79f8b572cd14a6b5e9b435b66a11a5193e0`；input30/31 保存原生验证源码5420项，SHA 均为 `29bf4908d6d1a8c6edfa7f98e91c6cf0fdd23713d1d93b616a7cd7ca880761f1`。input32绑定最终测试源码与报告前件5422项，SHA `35f5c0a8b2bd7dbb508a451f6f2c9b745bb97eaaae99f34526b06716321678a5`，source-review02保存最终路径哈希。同号差异、status、未跟踪副本、写权、worktree、进程/监听/磁盘附件齐全。

按 `R05_RETENTION_GUIDE.md` 条件5，历史和在途的任意采集/扫码/防重引用均应保留旧投影。自主选择保守补全既有 UNION：增加 independent_capture、independent_guard、independent_scan；三列实际 schema 均 NOT NULL，不引入 NOT IN 空值漏洞。保留既有命名空间、可信时间、90天、维护互斥、事务、分页和业务事实边界；不新增迁移/模型/权限或清理开关。先验标准与理由见本地 `design01.md`。

| 改动路径 → 影响层 | 必跑清单与最终 SHA-256 | 复用依据 |
| --- | --- | --- |
| `android/app/src/main/java/com/acrylic/outbound/pda/retention/RetentionDao.kt` → Android 删除 SQL | 三类独立引用、无引用正向删除、原留存/回滚/互斥回归；`146e08a9b57cd4e1d3df0060c0ac310ac90f1ddf7d3951c598ad8aed29430c9c` | 原 RetentionCleaner 与原生 Room 实际运行，未重写清理逻辑 |
| `android/app/src/test/java/com/acrylic/outbound/pda/independent/IndependentProjectionRetentionTest.kt` → 主机消费者 | pending、saved、VOID后历史均保留引用，删除无关投影，重开保持；`2947e1e3cf632612ea7d0b612596026fe4494bd9d615df08ec8a29bcdd318de0` | 产品独立任务/接收/保存/作废夹具，输出完整前后库 |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/IndependentCacheRetentionDeviceTest.kt` → 原生客户端消费者 | SHA 绑定成功输入，独立命名合成库运行真实 cleaner，全表断言；`849290cf090f749837d8c7bf8bd98d4965d2de65e3d004987f7180fd1d698f95` | 原 AVD/身份材料保持，合成库不冒充原现场 |
| 报告/索引/看板 → 开发断点 | 哈希、继承保护、版本事实与上限 | `source-review01.json` 中服务/Web逐路径+SHA绑定及5417项既存非本切片输入不变；未受影响的 HTTP/Chrome/导出链按进度索引复用，本次没有服务或网页改动 |

## 实际执行与失败原件

1. `reproduce01.py`：原产品三个测试全部失败，实际把引用条码 `003A0000001` 与无引用条码 `009A0000009` 同时删除。六份前后 DB、XML、源码原件、日志和 `reproduction-facts01.json` 保留。一次复现中的三个断言失败，不是三次机械重试。
2. 补三表 UNION 后 `host02.py`：新增三项、RetentionCleanerTest、RetentionEvidenceTest、IndependentScanRepositoryTest 共25项全部通过，59秒，0失败/错误/跳过。`host02-db/` 六份成功前后库及四份 XML 保存；独立 `consume_host.py` 核每例83表，其中82表逐值及 DDL 不变，唯一差异为无关投影删除；完整性及外键检查通过。
自审另发现测试证据目录不能作为常规执行前提，将导出改为仅环境变量存在时启用；产品查询和原生测试输入未变。`host03.py` 明确移除该变量后四类25项再次通过，0失败/错误/跳过；源码变更直接影响测试可运行性，故补跑而未重跑无关原生业务。最终测试文件 SHA 以上表及 source-review02 为准，host02导出事实仍有效。

3. `build01.py`：debug 产品/原生测试 APK 与 lint 实际成功，2分12秒。lint 0错误、438警告，原 XML 保留；不将警告归零或当作完整 Android 全套。重型前 C/D 余量、预计增长1GiB和命令保存在 command.json，TEMP/TMP/Java 临时目录仅子进程 D 盘。
4. `device.py prepare/execute`：实核 ranchu、TemporaryAdjustment1364、原绑定/视口/字号/空 reverse，先保存原两 APK 和完整设备 tar，再覆盖安装两 debug APK，无卸载或清数据。只对 emulator-5564 操作；真机不操作。成功主机 `saved-before.db` SHA `1f2db13c9cf4119cd2a3b0c0cff9e8f9a9b99436af27b258fbc00b1c84ec05d2` 实际复制并验 SHA；新 `synthetic-cache-retention-independent-cache01.db` 运行一次，`OK (1 test)`，0.558秒。
5. 独立 `consume_device.py`：实际回读原生 before/after JSON 和 after.db，三者与输入逐值一致；83张合成表仅删除一个无关投影，82表不变。最终库 SHA `8aaee2392cb7151a556c0ee4224aef7555dbb58ad87e3725bd20ad40fda524a3`。原主业务库83表328行逐值不变；428原 files/no_backup/shared_prefs 中426个字节不变，新增7个明确输入/证据文件。

另外两处变化仅为 AndroidX WorkManager WAL/SHM：应用启动重新登记周期工作，测试随后取消工作；前后均为3个 CANCELLED、0个活动工作，原主业务库/身份/业务文件不变。WorkSpec/WorkTag/WorkName 与3项调度 Preference 的完整前后事实保存在 `device01-workdb.json`、`workdb-review02.json`，没有把调度元数据写成逐字不变；`profileInstalled` 实际未变。

封存器首次把 WorkSpec 的 BLOB 直接写 JSON，产生一次 `TypeError: Object of type bytes is not JSON serializable`，业务早已成功且没有再运行。失败脚本 `finish.failed01.py`、半成品 `workdb-review01.json` 保留；只读补核将 BLOB 编为十六进制，输出新 review02 后通过。没有删除或覆盖失败库、日志或原件。

## 自审、产物与环境

自审确认产品仅一条查询的保护集合变化；新文件/方法符合项目分责上限，NOT NULL、全表守恒、主机/原生输入与旧文件 SHA 均实核。作者、执行和自审为同一助手；Python 工具消费不等于独立人员审查。无迁移/服务权限/DTO/签名变更。

保留 `build01-artifacts/app-debug.apk`，15802694 bytes，SHA `c183d27c046c6afe7742b2612d078f0d3c73b4dae191b77fa8c7227bf0eb7192`；原生测试 APK SHA `f0dc4353f384973e0ba13ee68035ed3a579c9ff7bf412d510ed9c1483fa206d3`。覆盖前的产品/测试原 APK 与原 SHA 位于 `device01-original-apks/`，不声称异地备份。

最终982证据文件126434711 bytes 两次长度/SHA核同，清单02 SHA `1f9276b56e3ff8b55927474f0ccb4c7e260dcdba920b7f51b8354557f21f6d93`；首次967件清单仍保留。封存后字号1.0、IME设置0、App强停、reverse空；保留原 AVD/ADB，未启动任何新服务/数据库，19381–19383/19443–19451零监听、无 Gradle/Java 残留。共享 mysqld4632/5440只观察，不连接或停止。C/D余量82349039616/44116946944 bytes，无清理。

所有生产决定能力、TEMP生产归档和 `localDeletionAllowed=false` 保持；不接原库/现网、不合main、不部署/发布、不用正式签名。本次没有执行首页真实HTTP成功刷新链、大规模低端设备性能、D16正常有效guard合法退出、E06/E07/E08整体L2或最终L3；这些不因缓存保护通过而豁免。下一步按当前输入补D17/U高频页、故障组合与E09候选剩余清单，总目标保持active。
