# E07 丢失成功回执后的原批次恢复组合开发备份

664 三阶段 Android/真实 HTTP/MySQL 组合通过：从产品实际待审状态导出一致 Room 快照，原服务成功归档后完整恢复；新 Android JVM 从该快照查询原批次，首份成功响应故意丢失，关闭重开 Room 后由产品恢复入口取得并验证原签成功。仅本组合单元通过，不计 E07 整体 L2、D13–D15 整体、D18 L3 或总目标完成。

## 输入、决定和影响

- 固定 `D:/acrylic-pda-outbound`、`codex/development`；输入 HEAD/远端 `e44c8b172dbe246a1de3792acaad38b75072fd9a`。本聊天唯一写者，无子代理；清理聊天 complete-released、实际 idle，无在途清理。87 项继承改动保持，不改生产代码/模型/迁移/契约/Web；版本事实只读看板生成块。
- 证据根 `.local/goal-20261016/e07-lost-success-restore01/`。input01 接续上批提交后全量 5361 项；input03 共 5363 项、SHA `d493c30ca7855a93ced3ffd3d1c65b4ce0c70353fadad9cd90d1d82de79103cc`，绑定最终运行。input04/05 保存运行中及结束后的全部路径/SHA、binary diff、未跟踪原件、旧看板、唯一写权、进程/监听和磁盘。
- `design01.md` 记录自主默认：以原生 SQLite `VACUUM INTO ?` 导出新文件，不回写状态或删除成功证据制造截点；导出前后源全表相同。恢复网络边界只允许同一原 ID 的 GET，不允许 POST、下载或新签名；恢复后的成功只能来自真实服务原回执。合成会话/钥/来源始终明确，不冒充原 PDA/Keystore。
- 新隔离路径 `.local/e2a-cross-20260925-664/`，数据库 `acrylic_e2a_cross_20260925_664` 及空伴随恢复 schema；DB19374、HTTP19434、测试内部适配19435。目录/端口运行前复核未占用。测试装配确认/归档仍不注册生产能力。

| 改动路径 → 影响层 | 本批必跑 | 复用依据（路径+SHA） |
| --- | --- | --- |
| `ArchiveRestoreRoom.kt`、`TemporaryArchiveRestoreHttpTest.kt`（Android temporary 测试目录） → 原生一致截点/分段调度 | 源实际待审/成功归档；一致快照全表不变；三阶段实际执行 | [上批663报告](GOAL_E07_ANDROID_ARCHIVE_RESTORE_COMBINATION_2026-10-16.md) 及其 input06 只按原范围；本批 input03 核改后路径SHA并重新运行 |
| 新 `ArchiveRecoveryQueryConnection.kt`、`TemporaryLostSuccessRestore.kt` → 产品恢复入口与严格GET边界 | 首响应丢失、RESULT_UNKNOWN、关闭重开、原成功验签、重放零新增GET、正文/guard/E2A及其余表不变 | `TemporarySubmissionRepository`、Responses、Success等产品依赖逐路径/SHA见 `reused-input-sha01.json`，清单SHA `f5ce8073a5441935b79598a50074abc1543061d4dd82c94c23168fb0bcfe293a` |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aAndroidArchiveRestore.java` → 恢复服务第三阶段 | 非空迁移/完整SQL与私有文件/配置恢复、三阶段HTTP、全表守恒与认证停机 | 同清单绑定未改依赖；664实际重跑该恢复组合，未复用663或失败库 |
| 报告/索引/本机Python消费者 → 可追溯性 | 精确XML、原签与SQL/SQLite、仅三表受控变化、失败和全目录哈希、自审 | 既有权限/事务/并发/尾SQL历史按663报告原输入范围保留，未记本轮执行；最终L2/L3另跑 |

## 实际执行

1. 服务端 `build_driver.py` 编译/classpath 成功25秒，Android `compile_android.py` 编译成功22秒。直接 Java 驱动启动服务，期间三次 Android Gradle 顺序执行，单次仅一个构建；命令/磁盘/退出码均有原记录。
2. `run_combination664.py` 退出0。source/restored/lost-success XML 各 1 test、0 failures/errors/skipped；每个阶段使用新的 Android Gradle/JVM，目标任务 `--rerun` 实际执行。后两阶段构建分别24秒、21秒。服务端为同一 Java 驱动内关闭再启动 Spring context，不声称服务端独立 JVM 或灾难重建。
3. 来源产品实际准备、扫码、冻结与首次上传到 REVIEW_REQUIRED，核没有成功证据，原生导出 `android-pending-room/outbound-pda.db` 及全表事实。随后服务确认成功、Android 原签成功消费和归档；完整 SQL、8 个私有文件及隔离签名配置实际恢复，HTTP查询前后179业务表237行、DDL和迁移历史全部相同。dump729285 bytes，SHA `b0a25c4e8e6f6fd662ca76c2039970b68f1c2ec533c3bcdbb51173dddf0a4675`。
4. 第三阶段复制原待审 Room 到新 `android-lost-success-room/`，原 ID/正文不变。真实 GET 返回已成功回执，但连接在产品收存前故意抛 IOException；实际结果为 RESULT_UNKNOWN，尚无成功证据。关闭重开 Room，产品 `TemporarySubmissionRepository.upload` 第二次 GET 原结果，验签后 SYNCED。`checked` 及再调用 upload 仍成功，GET总数保持2；网络边界禁止新POST和新签名。
5. 成功重查后仅 `temporary_submission`、`temporary_success_response`、`temporary_success_evidence` 三表有预期差异，增加一份成功原响应和一条已核验证据；原guard、冻结正文、STOP/E2A请求、原采集/审计及其余表保持。第一响应丢失后仅提交表状态/错误变化。源成功Room与其恢复副本仍83表94行相同；原待审Room及该截点另按受控差异核验。

## 独立消费、自审和失败

- `run_consumers.py` 的全表/签名消费者及 STOP 消费者退出0；核 SQL180个CREATE/336个INSERT与快照逐表行数、83表SQLite integrity/FK、原冻结payload、RSA成功/归档、公钥固定、设备P256 STOP、20请求/1页16项/16357 bytes页链。`consumer01/result.json` 和 `stop-consumer01.json` 保留。
- 首个新增截点消费者 `lost-consumer01` 对整个HTTP envelope逐字相等的断言失败。原业务3阶段均已通过，不重跑；`envelope-diagnosis01.json` 证明仅 `requestId`、`serverTime` 不同，所有其余字段及签名回执data逐值一致。原脚本/traceback/中间副本保留，不将失败计通过或清零。
- 定向新 `consumer_lost02.py` 保留三份完整响应SHA，严格核两项动态元数据之外的字段、同一原签data，并核产品逐字节保留第二响应且首份丢失响应未进入产品响应表。退出0；`lost-consumer02/result.json` 独立核原待审/UNKNOWN/最终Room的83表与精确差异，确认原ID/正文、guard及其他事实守恒，不只是读取完成标志。
- 611 个实例原件共263823439 bytes多次路径/长度/SHA不变；清单SHA `c427f3011245566f973c37f787cd23182f00a9dcdcab0eb64b5d7bf243d43942`。第二成功响应原字节SHA `347bf89225bf97798ac992f1c4708d7abdff7346b36af9dd27c043f265117f7c`，原签data与源成功一致；归档响应仍逐字节相同。
- `review-result01.json` 核5361继承项仅三处本批已跟踪测试和看板受控变化，五个代码路径与input03相同，70个复用依赖无漂移；328个既有本批证据再核不变，663和664原实例全清单保持。作者自审与独立Python算法分列，不声称独立人员评审。

## 环境与下一步

664 connected/shutdown身份字节相同，已认证停机；19374/19434/19435零监听，无本批服务/构建残留。663及661/662旧失败原件继续保留；共享mysqld4632/5440、原TEMP AVD/ADB只观察，不接原库或现网。结束采样C/D余量 `83,013,410,816 / 24,891,977,728` bytes，精确值见input05及后续快照；证据/数据库/dump/binlog/临时目录均D盘，未清理原件或虚称异地备份。

本批未执行Chrome、原模拟器/物理PDA、APK、完整普通回归、外置保管介质、其他来源所有恢复截点或D18最终全链。生产默认、七类决定、TEMP归档生产边界和 `localDeletionAllowed=false` 保持；不合main、不发布部署、不用正式签名，不恢复已取消链。接续D16正常防重/复杂引用资格与E07/E08受影响组合、E09候选；总目标第8节未全满足，保持active。
