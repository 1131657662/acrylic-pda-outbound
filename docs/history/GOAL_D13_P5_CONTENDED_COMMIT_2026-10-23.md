# D13 P5 真实提交后 F1 文件锁争用：有界验证

输入 `eaec7df2aedaa1673f0fc08cbb46746047e99b5d`，树 `D:/acrylic-pda-outbound`、分支 `codex/development`。本片只改测试夹具、专项 IT、隔离运行器和独立原件消费者；产品、配置、契约、迁移、Android/Web 未改。版本事实见看板生成块。总目标 active、未完成，不计 D13 整体或最终候选验收。

## 判据与边界

先只读核对 `RecoveryFenceAuthority`、`RecoveryFenceSequence` 与 OS 锁实现。产品 P5 使用本 JVM `Runtime.halt(70)`，不是渲染器的外部 PID 终止。尚未找到安全可控的真实 OS 拒绝自 halt 方法；SecurityManager、模拟异常或跳过 halt 均不作为真实拒绝证据，该缺口保持未验。重新入队需有隔离方法、真实拒绝回执及存活观察，不影响系统服务、原身份或生产。

按总目标第3.1节已授权默认，选取独立可执行分支：在真实 driver commit 成功后、模拟 SQLState 08007 丢确认之前，由父进程取得 `fenced.lock` byte0 独占 OS 锁，并持续到子进程实际退出。若提前持锁，会先被正常效果门拒绝，不能覆盖目标分支。`boundary-audit.md`、`boundary-inputs.json` 保存依据、输入 SHA 与行号。

安装身份、旧 admission 和运输丢确认是 **MODEL**；真实执行包含 MySQL driver commit、P5 Authority、独立 JVM、OS 文件锁及产品 halt。不增加产品故障开关，不以合成身份代替原身份或实际错库证据。

## 实际结果

新隔离 run1658 专项 **1测试、0失败、0错误、0跳过，3.924秒**，编译与 IT 各 exit0。随后独立 Python 原件消费者及补充核验 exit0；本片没有重跑旧矩阵、旧 run03、run1658 或已生成输出的消费者。

- 正常提交由独立连接观察0→1；子进程已插入第二行但尚未提交时仍为1，owner 锁持有、gate 正向允许。
- 实际 driver commit 后发布 `DRIVER_COMMITTED`，独立连接观察2行。父进程再取得有效独占锁，发出 `lose-ack`；该锁持续到真实 child exit70，随后释放。
- F1 严格为 `FENCE_F1_PRIME_UNCONFIRMED`，并有 `CONTROL_FENCE_LOCK_UNCONFIRMED`，没有 F1_CONFIRMED；F2 审计与 F3 释放确认，F4 保持 writers，实际 halt70、owner 锁释放，没有正常 commit 返回标记。
- 旧句柄先拒绝 `RECOVERY_LEASE_OUTSTANDING`，再拒绝 `RECOVERY_LEASE_FENCED`；delegate/行增量均0。同 JVM 新 Authority 严格 FENCED；独立新 JVM OUTSTANDING、exit1，没有 EFFECT，行数仍为2。
- 独立 SQL 原输出仅 id1/id2；离线 binlog 恰有两次 INSERT，无该表 UPDATE/DELETE 或被拒绝的 id99 效果。
- 扫描前保存完整 P5 原树7文件。扫描后仅孤儿 lease 退休及 index 变化，其余逐路径、长度、SHA 保持。lease/process/index 原 HMAC 与相互绑定通过独立文件核验；**UNKNOWN 是未签名审计，不是签名原件**。
- 运行期间2836源码路径、109文件依赖逐 SHA 保持。测试回调默认空操作保持旧 lost-commit 行为；新增场景只在显式 allowlist 中选择。

原件消费者读取原字节、HMAC、子进程回执、SQL/binlog及身份/停库文件，不调用产品观察或决策 API。作者自审和独立工具消费均由同一助手执行，**没有独立人员审查**。未运行真实 HTTP、Android/APK、模拟器、真机、完整恢复或部署；普通回归未重跑，既有结果仅按输入范围引用。

## 命令与证据

证据根 `.local/goal-20261023-resume/d13-p5-lock-contention01/`，全部输出为新路径，旧原件不覆盖。实际执行入口：

```text
C:/Python314/python.exe docs/parallel/R09/tests/run_p5_lost_commit.py --scenario contended --run 1658 --port 19834 --output .local/goal-20261023-resume/d13-p5-lock-contention01/run1658-p5-contended-commit --build .local/s23j
C:/Python314/python.exe .local/goal-20261023-resume/d13-p5-lock-contention01/consume01.py
```

完整编译/IT、身份、SQL、binlog、shutdown 命令及 exit0 回执在 run 目录的 `*-command.json`；消费者命令与结果在 `consumer01/`。身份前后原字节相同：回环端口19834、datadir `.local/mysql19834-r09batch/data`、schema `p5_lost_1658`、账户 `p5_lost_1658@127.0.0.1`、UUID `09de4482-ce44-11f1-9aa4-d8bbc13a2b36`；认证 shutdown exit0、进程退出0、端口释放。未重启该实例，未连接系统3306/33060、原库或现网。

| 原件 | SHA-256 |
| --- | --- |
| 专项 XML | 9143940eb3eb9fb86297970d3a588aad53a18a7ce08bd893604615f9b0fc19e6 |
| source-before/after.json | e25f8d7276093a8ef9f4c5037bc6108a9bab196777001845bc7dc7293f1b46fd |
| binlog.stdout | 3c641c8118a917e94aea17570dca70fdfee48e5593e34d9854c308348fa02478 |
| real-records.tsv | 0aec3f55a8a7bc6d0756f3480e6399365ced68da44df179c8a04d25e07acda25 |
| independent-verification.json | d1d847f97eb0f2c404ffc61078ae97859c7d9b4b48945722c3073591c2c7189e |
| consumer01/raw-supplement.json | 12ef590139b7788e682b15c664d334400aa026756170171c1947c190ca67c43c |

`snapshot-before.json`、`originals-before.zip`保存40继承原件；resume01/02/03、pre-run01快照保存后续输入原字节/SHA及patch。`closeout03/document-originals.zip/json`保存两索引和看板改前原字节，并记录本报告原先不存在。`source-review.json`记录7代码路径作者自审与 SHA；`p5-tree-conservation.json`保存逐成员差异；敏感扫描、精确暂存patch、Git回执、最终40路径SHA/ZIP及完整证据manifest同置closeout03。history仅暂存本片入口，继承索引内容留工作树，看板不混入开发备份。忽略原件仍在本机，不虚称已异地备份。

fresh进程/监听、ADB、Debug Keystore、C/D/E余量及写权记录在closeout03。本聊天是源码/索引/看板唯一写者；清理聊天另有磁盘授权，避开共享写权。ADB首次指定路径不存在、exit1，未操作设备；依据fresh进程实际ExecutablePath改用既有SDK只读枚举成功，纠正记录保留。系统数据库只观察、内部身份未认证。

本片无IT失败；上述ADB路径失败不记业务验收。前片IT及消费者失败保持原报告与原件，不被本片通过覆盖。

## 收口与未完成

前片删除已被执行策略拒绝，本片未换通道重试。只对本片已结束 `.local/s23j` 及已认证停机19834的 `data` 执行无损 NTFS 压缩：**3727文件长度/SHA全部保持，减少分配190183327字节，删除0**。D卷观测33684758528→33876541440字节；卷净变化与文件分配减少分列。`compact-plan/result`记录明确绝对路径、fresh消费者检查、依赖输入、命令/退出码和逐文件前后哈希；P5原件、XML、关键日志、SQL/binlog、私有身份、最终APK与继承改动保留。压缩与提交不计验收。

本片只补真实commit后F1锁争用的有界故障分支，不覆盖其余P5故障、真实OS拒绝自终止、物理断电、所有JDBC/文件入口、实际错库运行准入或完整应用恢复。D13–D15整体、D16有效guard/复杂引用、D18实际最终候选及总目标第8节仍未满足；下一片先按剩余缺口和可执行条件选独立项，无新方法不重跑OS拒绝自halt。

生产开关/TEMP归档、正式签名、原库/现网、main、部署/Release继续关闭，`localDeletionAllowed=false`；取消的换机承接、多跳、接收者确认/激活链及旧run03不恢复。总目标保持active、未完成。
