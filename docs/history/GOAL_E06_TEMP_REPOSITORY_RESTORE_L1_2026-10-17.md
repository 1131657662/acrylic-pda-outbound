# TEMP 产品会话、未知结果与原 ID 恢复 L1

本批实际通过源与恢复两段产品会话/仓库、真实 HTTP/MySQL、非空恢复和 Python 原件消费。只计合成隔离 L1 开发备份，不计 E06 L2、E07 整体、D18 L3 或总目标通过。无页面、模型或迁移修改；版本事实见看板生成块。

## 输入、写权与提交范围

固定树 `D:/acrylic-pda-outbound`，分支 `codex/development`，执行输入 HEAD/实核开发远端均 `68fdc5528b1023025e6a37cd99dc498a283b6f98`。本聊天 `01a13a57-8082-7dd2-a08f-987483dcda4b` 唯一写者；清理聊天已明确停止并释放，接续时实核 idle，其余项目聊天 idle/notLoaded。

`.local/goal-20261017-resume/input23.json` 与同号附件保存 5475 项输入、全部差异、未跟踪原件、远端、worktrees、写权、进程/监听、磁盘及旧看板，清单 SHA256 `7df816802302225c23892be5037c1fd03e08193df7bb7b45d0de287e20f8d786`。旧看板另存 `ORCHESTRATION_STATE_before_temp_repository_report_20261017.md`。

两个 Android 测试拟作明确路径开发备份；三个 Java 运行器依赖未提交的 `E2aTestPorts.java` 及继承 helper 端口改动，继续保全在工作树和快照，不夹带提交。675 由完整脏树及本地运行器执行，不能声称仅本次 Git 提交可重建完整 675。作者、执行和自审为同一助手；Python 为独立算法消费者，不是独立人员审查。

## 改动、影响层与必跑映射

| 路径（相对仓库） | 影响层与本批必跑 | 复用依据 |
| --- | --- | --- |
| `android/app/src/test/java/com/acrylic/outbound/pda/temporary/TemporaryRepositorySession.kt` | 真正 SessionVault 存读、PdaSession/TemporarySession 身份与 HTTP；源/恢复分别 Android JVM | `JvmSessionKeyStore.kt`、667 正式仓库报告及逐路径 SHA 见下述复用清单；AES 钥为 JVM 合成 |
| 同目录 `TemporaryRepositoryRestoreHttpTest.kt` | 原 pending Room→丢 GET 成功下行→持久 UNKNOWN→关闭重开原 ID SYNCED；SQL/files 恢复后第二 JVM 同原件；三表增量/其余守恒 | `ArchiveRestoreRoom.kt`、664 原 TEMP 报告及逐路径 SHA 见复用清单；本次重跑直接受影响产品链 |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aTemporaryRepositoryAndroid.java`、`E2aTemporaryRepositoryProxy.java`、`E2aTemporaryRepositoryRestore.java` | 编译、仅 loopback 原 ID GET、真实 HTTP/MySQL、非空迁移、DDL/文件守恒、dump/认证停机/零监听 | 五新增路径 SHA 见 `new-code-sha01.json`；未提交依赖限制如上 |

证据根 `.local/goal-20261017-resume/temp-repository01/`：`design01.md` 保存自主默认及验收标准；`new-code-sha01.json` SHA256 `8f41522a7dc218ed53793cb51fe7b6136b27254f4fce023a39b00cd3388e4a73`；`reused-input-sha01.json` 432 项逐路径/长度/SHA，清单 SHA256 `55ff707738703769e6bdd87b5a965daa96dba4d5aceae6caec5dedeb46cc046a`。权限、并发、尾 SQL、default-off 未修改链仅按适用输入复用，不记本轮真跑。无页面变化，不为此切片重跑 Chrome；1540 自动审批阻断仍保留，不绕过。

## 实际业务与独立消费

- `build01`、`compile01`、`run675` 均 exit 0；原件 `.local/e2a-cross-20260925-675/android-source.xml`、`temp-product-source.xml`、`temp-product-restored.xml` 各 1 项、零 failure/error/skip。`run675.log` SHA256 `d385489fc2542e1e808a6f9dc289fc748e4a6d364a0d1d432d76243a3d08788c`。
- 使用真实 SessionVault/PdaSession/TemporarySession/TEMP 仓库。代理先取得原 ID GET 的真实 SUCCEEDED 完整字节，再断下行；产品持久化 RESULT_UNKNOWN，无成功证据。关闭并复制 UNKNOWN 原件，再重开按原 ID 查询、验签入库为 SYNCED。SQL/私有文件恢复后，第二 Android JVM 从同一 UNKNOWN 副本恢复。
- 代理只接受 loopback 原 ID GET，不允许重复 POST；源两次 GET、恢复一次 GET，SYNCED 重试不再发请求。原 payload、原批次/摘要、响应字节和成功回执绑定，既存事实不被重新生成。
- Room 83 表：UNKNOWN 共 71 行，源/恢复成功各 73 行，仅 `temporary_submission`、`temporary_success_evidence`、`temporary_success_response` 受控变化。服务查询前后、恢复查询前后 snapshot 相同，8 个文件、DDL、非空迁移及 dump 绑定通过。
- `.local/goal-20261017-resume/temp-repository02/consumer07.json` 73 项全 true，绑定 58 个输入文件；SHA256 `1f23ebe1469b081964a8abd9eceb1daec8429090761f47d549866bbf1d1854ea`。675 `private/archive-snapshot.sql` 为 729215 bytes，SHA256 `c213409d0894190c177fafd9522faa5a0b598a1fd95268b5343e8e36324ce739`。消费者实际解析 HTTP/JWS、Room、SQL、命令和停机原件，并非仅搜索通过文本。

## 失败原件与保全

674 首个指纹：`E2aRealHttpConnection.kt:26 fixed19434 rejects new19452 before product submission`。非空迁移及合成 seed 已写，未到产品提交，失败库/datadir、`failure674.json` 和 run674 原件全部保留，不复用。修复假设为保持既有严格连接不变，实核空闲后新分配 675 DB19385/HTTP19434/proxy19435/预留19436；setup 的旧内部适配先关闭，再交产品代理。675 完整正向复验通过。

离线消费者 01 ERROR `ValueError`（细因未充分核，不推测）；02 `room_state_transitions`；03 `dump_create_table_binding`；04 `source_success_evidence_binding`（应绑定产品回执对象摘要）；05 `dump_migration_insert_binding`（Windows 行尾）；06 `runner_wrapper_binding`（行尾）；07 通过。历次脚本、结果和修前原件保留，修复只消费既有业务原件，未重跑业务。同根因计数不清零。

675 日志记录认证停机，消费者确认隔离端口零监听；不复用 675 或旧失败库。接续实核仅保留原 TEMP AVD emulator12912/qemu12108、ADB 与共享 MySQL4632/5440，后者身份未知只观察，不连接或停止。报告前 C/D 余量 78591324160/14424973312 bytes；无本批 Java/Gradle/MySQL 重型任务，无清理，重型前复测。所有新证据、数据库、SQL 和临时文件在 D 盘。

## 限制与后续

身份、授权、时间、JVM AES 钥及内部确认归档适配均为合成，不代表原 PDA、真实 Keystore、生产归档或现场回执。七类生产决定能力、TEMP 生产归档、`localDeletionAllowed=false` 均保持关闭；不操作原库/现网，不合 main、不发布部署、不用正式签名。

本批补足旧直接 GET 后抛错未经过产品会话/仓库的缺口，不覆盖旧失败。继续 D16 正常目标合法退出资格、D04 旧前缀新依据、E06/E07/E08 受影响组合及 E09/D18 实际候选；未验、暂缓和现场材料逐项保留，不以此 L1 或开发备份结束总目标。
