# E07 旧恢复客户端非空升级 L1 开发备份（2026-10-16）

保留的历史合成模拟器数据库在新隔离 AVD 上经过产品迁移链升级，原成功批次/签名及两代审计检查点保持；真实强停后由新进程重开，观察到 NORMAL 仍不能解除原 RECONCILING 屏障。此项仅补客户端 L1，不计 E07 L2、D14/D15 整体、D18 L3 或总目标通过。

## 输入、决策与影响映射

- 固定树 `D:/acrylic-pda-outbound`，分支 `codex/development`，输入 HEAD/远端 `5e5f7a96f49d52b697d756957faab8bab9c03a1b`；唯一写者为聊天 `01a13a57-8082-7dd2-a08f-987483dcda4b`。清理聊天 notLoaded 且已有 complete-released 回执，进度聊天 idle；无子代理。
- 证据根 `.local/goal-20261016/e07-legacy-recovery01/`。input02–04 全部 5351 个 tracked/untracked 输入 SHA `d0e4b4f5799ce19cccb97b5ee4650e817bc2aecb7466e4744db00405cd07f59f`，完整 diff、未跟踪原件、工作树清单、旧看板和环境/写权均保全。input05 仅看板变化；提交前后另存快照。
- 采用 `design01.md` 的最小方案：不重启或推进旧 R05 服务/数据库/AVD，只在新副本执行产品 `PdaDatabase.open`；不手改恢复模式，不引入 DTO 替代原事实。无产品、schema、API、UI 或权限改动；版本事实见看板生成块。
- 原输入 `.local/r05-recovery-ui/outbound-pda.db`，327680 bytes，SHA `b113b73a4c972c1f4bcfeac021ed8d4ee920aa4906b1fe8e6be5f631039c5536`。无 WAL/SHM/journal；只读核验后固定到 `input/source.sqlite`，且植入前设备字节一致。与 `.local/r05-archive-recovery/outbound-pda.db` 的对照和 SHA 见 `input/input.json`，22 表相同，仅允许的同步缓存变化。
- APK 来自完整脏树，不能称仅由此提交重建；继承改动按全量输入保留，不混入本提交。两个测试 SHA 分别为 Facts `21e4413a0464f2a9e5976c95a4649ac3c7d050a920d05d11a0581cb001db1977`、DeviceTest `aa38817bd4afcebb5b348648e5ddb9229a797e0a2434e95a1162245b6c647c59`。

| 改动路径 → 影响层 | 必跑清单 | 复用依据（路径+SHA） |
| --- | --- | --- |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/LegacyRecoveryUpgradeFacts.kt` → 非空存量消费者 | 产品迁移、所有旧列/行、原成功回执/归档、两代审计、完整性/FK | 上述原 SQLite SHA；`native01/upgrade.log` 与独立 `consumer03/result.json` |
| 同目录 `LegacyRecoveryUpgradeDeviceTest.kt` → 重启/恢复屏障 | 两次真实原生 instrument、force-stop/不同 PID、观察 NORMAL 后仍拒绝正常写入 | `native01/restart.log`；两阶段输出及 tar 的 SHA 见 `native01/*-hash.json` |
| 两个测试 → 构建与受影响归档/恢复回归 | debug/test APK、相关主机测试、lint、独立 Python 实际消费 | `build01-command.json`、原 XML 与 `build01-hashes.json`；无服务端变更，旧真实 HTTP/MySQL 证据只按各自报告复用，不计本次执行 |

## 实际验证与自审

1. `build01` exit0，20 项主机测试，0 失败/错误/跳过：AuditArchiveMigration 1、AuditArchiveVerifier 7、SubmissionArchiveMigration 2、SubmissionArchiveVerifier 6、RecoveryProtection 4。单测任务实际执行；产品 APK、部分 lint 任务 UP-TO-DATE 明确复用。lint 438 警告、0 错误。debug/test APK 均安装成功。
2. 新 AVD `E07LegacyRecovery20261016A`，`emulator-5570`，启动前已核 5570/5571 空闲及 AVD 身份不存在，预估增长 7 GiB，C/D 余量见 `native01/preflight.json`。定义、完整 AVD、启动/停止命令、日志/PID 均保留，路径在证据目录内。
3. upgrade 方法 1 项通过，3.195 秒：产品迁移后原 23 表/42 行的全部旧列不变，成功批次仍 SYNCED；生产 verifier 核原回执/归档、两代审计前缀和确认点，篡改 localDeletionAllowed 拒绝，恢复屏障仍拒绝正常操作。
4. 真实强停后 restart 方法 1 项通过，3.847 秒：进程 PID 从 2574 变为 3301；所有原事实/原签再核，实际 `RecoveryProtection.observe` 接收 NORMAL 包仍保持原 RECONCILING，`requireNormal` 拒绝。
5. 独立 Python `consumer02/03` 实际消费原 SQLite 及升级/重启捕获副本：所有旧列/行保持；59 张新增表全部为空，仅受控迁移元数据及新增事件顺序列填充，事件顺序为 1–15；升级/重启业务事实一致。原成功/归档和两代审计共 12 行次签名核验，另 3 次核恢复请求内原签归档；原公钥 SHA `9402b43dbfa3d72e7bdfb78c18cc1cf69bbe514fe96b1ff0d671c79d52604077` 保持。完整性及 FK 通过。
6. consumer01 的 `KeyError: originDeviceId` 源于将 restoring 请求按普通归档封装解析；原脚本、失败记录和首轮清单保留。修正为显式核恢复封装、原签及原代次证明绑定后，只读重验，不重跑业务、不改原库。
7. 自审 `review01.json` 核输入仅看板变化；75 份原件共 5555688171 bytes（含完整 AVD）多次 SHA/长度一致。代码仅测试，隔离门要求 debug、ranchu 和精确 runId；不触碰物理 PDA。测试阶段由主机明确顺序调度，不依赖 JUnit 方法排序。

## 产物、停机与限制

- 产品 APK `build01-artifacts/app-debug.apk`，15802694 bytes，SHA `bf4428d36f6ae773aaaae6dc31e4dbbae8aa0c10c9a8bf918d965e57550e5c69`；测试 APK 3405037 bytes，SHA `6461eaf6a344856e42e3ae5c5211af625f0e0e1447df9f8002aefee6796fbd86`。均为本机开发签名，没有发布。
- `native01/result.json` 记录通过及模拟器退出；补环境快照核 5570/5571 零监听，无本批 Java/MySQL/构建任务。原 TEMP AVD、共享 mysqld 和物理设备未操作。完成时 C/D 余量 `83,032,907,776 / 27,015,462,912` bytes，后续精确采样见 `disks-05.json`，重型前再测。
- 原数据库、捕获 tar/DB/WAL/SHM、原日志、完整 AVD、APK 和清单均在 D 盘保留；子进程 TEMP/TMP/Java 临时目录在 D 盘，无全局环境修改。未虚称这些本机产物已异地备份。
- 验证的是历史合成模拟器原件；未核原 Keystore/真实 PDA 身份、现场事实、真实 HTTP/MySQL 恢复组合、Chrome 或外部保管介质。未执行一次性旧恢复 advance，不恢复已取消换机接收/多跳/激活链。
- 七类生产能力、TEMP 生产归档边界及 `localDeletionAllowed=false` 保持。下一步继续 E07 受影响组合、D16 复杂引用/D13–D15、E08/U 剩余及 E09；本备份不满足总目标第 8 节。
