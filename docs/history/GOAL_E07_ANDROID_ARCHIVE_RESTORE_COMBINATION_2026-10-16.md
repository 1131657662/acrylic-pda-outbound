# E07 Android 原批次与整库恢复组合开发备份

663 新隔离输入通过 Android 原批次冻结、真实 HTTP/MySQL 成功归档、完整 SQL/文件恢复及原 Room 副本重查。独立 Python 核对原签、全表和字节通过。本页只收这个组合单元，不计 E07 整体 L2、D13–D15 整体、D18 L3 或总目标完成。

## 输入与方案

- 固定树 `D:/acrylic-pda-outbound`、分支 `codex/development`，输入 HEAD/远端同为 `402d9d590ef0305182e88bc1ba6b64b3b59ba258`。本聊天唯一写者，无子代理；清理聊天既有 complete-released 回执保留，实际状态为 idle，其他同目录聊天未执行写入。87 项继承改动保持，没有代提交。
- 证据根 `.local/goal-20261016/e07-android-archive-restore01/`。input01–06 保存全树路径/长度/SHA、binary diff、未跟踪原件、旧看板、写权、进程/监听和磁盘。最终 input06 共 5360 项，SHA `db7a91abbf2aa02e7a2a79cfa9a5b3e01c68b73d647e18c0e9e1a08ded28c8ac`，绑定 663；源码自审核对运行后四个新增文件均未漂移。
- 自主方案见 `design01.md`：保留原 Room；新库从空库经非空升级，产品代码实际冻结/上传/成功/归档；关闭来源服务后完整 dump/import、复制私有文件及隔离签名配置；新 Android JVM 打开原 Room 副本，真实 HTTP 消费恢复后原回执。服务端在同一直接 Java 驱动内关闭、重启 Spring context，未声称服务端独立 JVM/灾难重建。
- 新实例 `.local/e2a-cross-20260925-663/`，数据库 `acrylic_e2a_cross_20260925_663`、伴随恢复 schema，DB19373、HTTP19434、测试内部适配19435；运行前核实目录和端口未占用。内部确认/归档适配只在 test 装配，不注册生产 POST 或操作权限。

| 改动路径 → 影响层 | 必跑清单 | 复用依据（路径+SHA） |
| --- | --- | --- |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aAndroidArchiveRestore.java`、`E2aAndroidArchiveClient.java` → 隔离真实服务/恢复调度 | 非空迁移、实际 Android 正向、原正文/全 SQL/私有文件与签名配置恢复、HTTP 重查、认证停机 | `reused-input-sha01.json` 为每个实际依赖列路径/SHA，清单 SHA `fbcd8518ac4be184345ed75dc18b38462446eddf1af1a90023fd2939bc8daa61`；本次实际重新执行恢复，不将旧报告当本次业务 |
| `android/app/src/test/java/com/acrylic/outbound/pda/temporary/ArchiveRestoreRoom.kt`、`TemporaryArchiveRestoreHttpTest.kt` → 产品 Room/网络消费者 | 实际冻结/上传/成功/归档、异文签名拒绝并保留、原 Room 副本重开/两次 HTTP 查询、原回执与归档验签、全表/guard 保持、生产入口 default-off | 同清单绑定 TemporaryFreeze/Submission/Success/Archive、StorageChecks 及相关依赖；原服务器权限/事务/并发/尾 SQL 反例只按 [归档恢复历史报告](R09_TEMPORARY_ARCHIVE_RECOVERY_ACCEPTANCE_2026-09-25.md) 原输入范围复用，未记本轮重新执行 |
| 正式报告与索引 → 可追溯性 | 原件全目录哈希、独立 SQLite/RSA/P256/页链与 SQL 消费、继承输入核对 | 所有运行命令、XML、退出码、源码/依赖 SHA 和失败原件在上述证据根 |

未改产品代码、模型、迁移、契约或网页；版本事实只见看板生成块。本批未执行 Chrome、原模拟器、物理 PDA、APK、完整普通回归或最终全链，不豁免后续 L2/L3。

## 实际执行及独立复核

1. `python .../build_driver02.py`：服务端 testClasses/classpath 成功，25 秒。通过直接 Java 驱动运行服务，等待 Android 期间没有第二个 Gradle 构建。两个 Android 子进程顺序执行 `:app:testDebugUnitTest --rerun --tests *TemporaryArchiveRestoreHttpTest --offline --no-daemon --max-workers=1 --console=plain`；`--rerun` 强制实际执行目标测试，依赖按输入增量构建。
2. `python .../run_combination663.py`：退出 0。`run663-command/result.json`、`run663.log` 绑定命令、磁盘和运行器源码；实例 `integration.log`、started/completed 标记保留。来源与恢复 Android XML 各 1 test、0 failures/errors/skipped。source 构建 31 秒，restored 构建和精确命令另见原日志。
3. 来源产品实际读取准备资料、创建计划并扫码，冻结一次原批次，上传待审→测试装配有权确认→成功回执→归档原件/验签；有两条实际防重记录。异文/无效签名向量经产品消费者拒绝，失败原文保留且有效归档保持。完整原 Room 文件在 `android-source-room/outbound-pda.db` 保留，关闭后复制到 `android-restored-room/`。
4. `E2aArchiveSnapshotRestore` 实际 mysqldump/import 到空伴随 schema，核目标数据库/账号/端口/datadir；来源、恢复前 HTTP、恢复后 HTTP 的 179 张业务表 237 行、DDL、迁移历史和 8 个私有文件逐项一致。SQL dump 729293 bytes，SHA `cb53e6110523357a9e5510ac329ab4f9fcb3a9eb6ff80ec2589c1768d7afe68a`；Python 独立核 180 个 CREATE 和 336 个 INSERT（含迁移历史），每表行数与快照相符。
5. 恢复 Android JVM 只打开原 Room 副本：先核全表一致，故意丢弃第一次成功查询，第二次真实 HTTP 取得原签回执；本地已有成功与返回回执一致。准备同一归档请求，生产入口仍返回 `TEMPORARY_CONFIRMATION_REQUIRED`；测试适配重放取得逐字节相同的归档，产品验签后仍 `localDeletionAllowed=false`。Room 前后全表相同，未增加提交或重复记账。
6. `python .../consume.py 663 19373 consumer01`：退出 0。独立 Python 复制两份停止的 SQLite 到消费者自有目录，核 integrity/FK、83 表 94 行与 Android 原全表快照相同；两个 temporary guard 保持。核原冻结正文摘要、stored payload、RSA 公钥固定、成功和归档 JWT 的算法/用途/声明/外层逐值、原字节和配置复制，结果见 `consumer01/result.json`。
7. `python .../consumer_stop.py`：退出 0。独立以准备输入固定的合成 P256 公钥核原 STOP 签名、主体/设备/源/轮次/水位及清单摘要；20 个原请求的正文摘要、1 页16项/16357 bytes 的页链/manifest/set 与恢复后原件相同。不签发新 STOP，不代原 PDA/Keystore 事实。
8. 两个消费者和后续自审多次核 599 个实例文件、261304549 bytes 的路径/长度/SHA 不变；清单 SHA `26647ff6c7a6e14209d7dc4b1a5a847acebc64fff4c45d51115bf786e230d323`。`review-result01.json` 核 5360 项继承输入只有本批两文件与看板发生受控修改，四个新增代码 SHA 与 input06 相同；478 个既有本批证据原件逐文件再核不变。作者自审与独立算法消费分列，不声称独立人员评审。

## 失败、环境及剩余

- 运行器生成预检的选择条件过宽，assert 失败发生在创建实例前；`failure-preflight01.json`、原脚本和源码保留。661 的 Windows PowerShell 模块导入发生重复 TypeData，创建空目录后 ACL 失败，没有库或业务写入；`shell-diagnosis01.json` 精确复现，子进程显式 Windows 模块路径后预检成功；不改全局环境。661 空目录/原日志不复用。
- 662 缺少下载目录，在夹具写 inventory.gz 时失败，尚未开始 Android 产品冻结，但服务器准备数据已写入。保留完整 datadir/binlog/日志/身份/输入；372 文件222374743 bytes 双次 SHA 一致，清单见 `failure662-manifest-before/after.json`，已认证停机/零监听。修复只为新夹具创建目录，663 从新库重新验收；不将662追认为通过，不清零历史失败计数。
- 663 connected/shutdown 身份字节相同；MySQL 认证停机，19371–19373/19434/19435 复核零监听。本批无残留服务/构建。原 TEMP AVD、ADB及共享 mysqld4632/5440只观察，未连接、复用或停止。
- 结束采样 C/D 余量 `83,019,194,368 / 25,195,167,744` bytes；阶段后精确值见后续 input 快照。证据、数据库、dump/binlog和子进程临时目录均在 D 盘保留，预计增长2GiB的前置磁盘检查有记录，未清理原件，未声称已异地备份。
- 没有原身份/Keystore、原物理PDA或灾难重建、完整外置保管回取、Chrome/原模拟器组合；未覆盖其他来源、撤回/更正/实物所有截点。既有647/644/656–657、旧非空客户端及其他报告只按路径/SHA与原范围复用，不能拼作E07 L2或D18候选全链。
- 七类生产决定、TEMP生产归档边界和 `localDeletionAllowed=false` 保持；不接原库/现网，不合main，不发布部署，不用正式签名，不恢复已取消换机承接/多跳/激活链。
- 下一步核 D16 正常带防重历史与复杂引用资格、补实际旧原件消费者；随后 E07/E08 受影响组合和 E09 候选。所有必做闸门未齐，总目标继续 active。
