# E07 撤回恢复受影响组合验收（654–657）

本页记录 E07 恢复/外置副本范围的一项隔离组合开发验收。654 执行故障订单撤回、重新发布和新设备成功流程的五个恢复截点；655 以独立只读消费者复核输入守恒。结果不等同于 E07 L2、D18 L3、第 8 节闸门或总目标完成。

## 输入绑定与影响范围

- 工作区：`D:/acrylic-pda-outbound`。654 原执行日志没有执行前源码清单、完整命令及运行后环境记录，不能倒填其执行 HEAD。复核时分支为 `codex/development`，HEAD 与实际远端同为 `65bd5b37265563a9ce372afea5a713a38bd957e1`；下表 SHA 绑定本次复核及 656 新执行输入，不追认 654 的原输入。
- 接续前的旧看板结论已经保全为 `docs/history/ORCHESTRATION_STATE_before_e07_655_20261016.md`，SHA-256 为 `CDD80A76B6AD3143B79F95E975ED93EE4F890CA9206BB881119042018D87E7C1`；完整接管快照位于 `.local/goal-20261016/resume-20261016-040646/`。
- 改动路径 → 影响层 → 必跑清单 → 复用依据：

  | 改动路径 | 影响层 | 必跑清单 | 输入 SHA-256 |
  | --- | --- | --- | --- |
  | `server/src/test/java/com/acrylic/outbound/e2averify/E2aFaultWithdrawalRecoveryIT.java` | 撤回、重发、恢复截点、真实 HTTP/MySQL | 正向撤回/重发/成功路径、旧文拒绝、同键幂等、权限/事务、五截点、停机 | `89C055F8336FB0CD9A3A1346481614E72343C03156CF5C67534AB8C6B3D1D037` |
  | `server/src/test/java/com/acrylic/outbound/e2averify/E2aPointInTimeCheckpoint.java` | 检查点 dump、binlog 与文件守恒 | 五截点建立/恢复、非空迁移、原 binlog 与存储校验 | `3CF1C5E29C1A75668B39DAAFD8DB364E9DC324D80106342903882DC1C92BBDD1` |
  | `server/src/test/java/com/acrylic/outbound/e2averify/E2aPointInTimeCommands.java` | 截点命令与重放 | 截点命令成功、失败边界、重放结果与身份绑定 | `F13D7680D903E9FD171C5A0442693DB9D5423786366781070384523BA946B194` |
  | `server/src/test/java/com/acrylic/outbound/e2averify/E2aCrossSourceHttpIT.java` | HTTP 事实读取与守恒 | 五截点 HTTP 回读、旧/变更请求拒绝、成功回放 | `C57A244B3F96976C1538C18E9DB6AB12F8C0A3EA4E57718185505E29E17A6F1C` |
  | `server/src/test/java/com/acrylic/outbound/e2averify/E2aCrossSourceProfile.java` | 隔离 MySQL/HTTP 运行配置 | 新数据库、端口、关闭和身份记录 | `B48746FC09BCF3E31F1C8666E1DC2269C055CB7CEAEC0FD1FC73F472165D3AF0` |
  | `tools/e2a-cross-source-verify.ps1` | 运行器、证据封存、停机复核 | 命令参数、退出码、监听清零、磁盘事实 | `998080A14E7D3DF4EDDF179FD3B8A265F8514EDCB8C011C6A2BB12E18C988886` |

## 654 实际执行结果

- 证据目录：`.local/goal-20261016/e07-l2-combination-654/`。隔离数据库为 `acrylic_e2a_cross_20261016_654`，MySQL 端口 `19364`，HTTP 端口 `19454`；没有复用 1538、旧失败库或共享数据库身份。
- `E2aFaultWithdrawalRecoveryIT`：1 test、0 failures、0 errors、0 skipped；`integration-result.xml` 和 `integration.log` 保留，Gradle `integrationTest` 为 `BUILD SUCCESSFUL`。
- `before-withdrawal`、`after-withdrawal`、`after-republish`、`before-success`、`after-success` 五个截点均保留 `before.json`、SQL dump/replay、HTTP 结果、原件配置和 storage。
- 五个截点的 HTTP 事实相同：旧 submission 为 409、旧 plan 为 409、变更后的撤回为 409，`sameReceipt=true`，`successReplay=true`。
- 每个截点为 179 张表；迁移历史见看板事实块及原证据。最终 storage 为 2 个文件。原 binlog 为 537752 bytes，SHA-256 为 `f7309a2f9502df402182118585567fc3c3ac481da824a34bb7f20375c21d6fef`。
- `connected-identity.tsv` 与 `shutdown-identity.tsv` 均绑定端口 19364、数据库 `acrylic_e2a_cross_20261016_654` 和账号 `e2a_cross_20261016_654@127.0.0.1`，两份身份内容一致；`test-completed.marker`、`private/shutdown.log` 与退出记录保留。

## 655 独立消费者

- 消费者脚本与输出位于 `.local/goal-20261016/e07-withdrawal-consumer-655/`；输出 `consumer-result.json` 的 `passed=true`，脚本 SHA-256 为 `924E350EB74049CEE250C26F292037CEAD75A04EC0B944604BFBFBC7F0014AF1`。
- 消费者未连接数据库或网络，读取 654 的 1362 项输入；两次清单中的路径、长度和 SHA-256 全部一致，`inputUnchanged=true`。
- 655 输出复核五个截点的 179 表、迁移历史、HTTP 409/幂等事实、2 个最终 storage 文件和原 binlog SHA；失败列表为空，退出码为 0。该脚本为同一助手执行的独立工具消费者，不称独立人员验收。

## 656 执行前绑定补验与 657 消费

针对上述取证缺口，新建 `.local/goal-20261016/e07-l2-combination-656/`；使用新库 `acrylic_e2a_cross_20261016_656`、账号 `e2a_cross_20261016_656@127.0.0.1`、数据库端口 19366 和 HTTP 起始端口 19456。未复用 654 或失败库。

- 执行前控制证据在 `.local/goal-20261016/e07-input-binding-656/`：`command.json` 记录精确 HEAD、分支、命令、运行器 SHA、临时目录、预计增长及 C/D 余量；`input-before.json` 绑定 5301 项输入；`ownership.json` 记录唯一写者。完整 Git、binary diff、61 个未跟踪原件及旧看板保全于 `.local/goal-20261016/resume-20261016-052214/`。
- 实际命令为 `powershell.exe -NoProfile -File tools/e2a-cross-source-verify.ps1 -Run 656 -WithdrawalRecovery -AttemptPath D:/acrylic-pda-outbound/.local/goal-20261016/e07-l2-combination-656 -DbPort 19366 -HttpPort 19456 -DatabaseName acrylic_e2a_cross_20261016_656 -DatabaseUser e2a_cross_20261016_656`。TEMP/TMP 和 Java 临时目录仅在子进程设为 D 盘路径。
- 原 XML 为 1/0/0/0、97.415 秒，Gradle `BUILD SUCCESSFUL`，运行器记录 `testExit=0` 和认证停机。连接与停机身份逐字节一致，datadir 指向该 attempt；19366/19456/19457/19458 零监听，没有本批 Java/MySQL 残留。
- 外层脚本在业务完成后以系统默认编码读取 UTF-8 清单，中文路径误解码，外层退出 1；没有产品再写入。原错误保留于控制目录 `wrapper-postcapture-failure.txt`。随后显式 UTF-8 只读复核，`runtime-after-corrected.json` 记录 5301 项输入全部未变、停机/监听/进程及磁盘，未重跑业务。
- 657 复制未修改的 655 消费脚本，SHA 为 `924E350EB74049CEE250C26F292037CEAD75A04EC0B944604BFBFBC7F0014AF1`，实际消费 656。证据在 `.local/goal-20261016/e07-withdrawal-consumer-657/`，退出 0、`passed=true`；1362 项路径/长度/SHA 双次清单相同，无数据库或网络连接。
- 五截点均为旧 submission/旧 plan/异文撤回 409、`sameReceipt=true`、`successReplay=true`；179 表及迁移历史、最终 2 个 storage 文件符合原事实。原 binlog 为 537742 bytes，SHA 为 `ddc4f9b1995a2b735aa6af8144a61e81758662ff6429633e025bbce05ae9261a`，原 dump/replay/config/storage/datadir/XML/日志全部保留。
- 补取证时 C/D 可用分别为 83236741120/56796782592 bytes；执行前分别为 83239079936/57196544000 bytes。无清理动作。

## 运行边界与历史失败

- 654/655 完成后 19364、19454 均无监听；共享 `mysqld` 进程只作观察，未复用其数据目录。运行期间未覆盖既有源码、数据库、证据或未跟踪改动。
- 650 的 `FileAlreadyExistsException: private/client.cnf`、651 的 HTTP `ConnectException/ClosedChannelException`、652 的 Web 登录 403、653 的 `SHOW MASTER STATUS` 权限失败均保留原件；后续修复输入由 656 实际验证，不重复失败库。旧 1522/1523 及其他暂缓项保留，不因换会话清零。
- 生产决定 handler/POST/放行/证据写入/关闭能力、TEMP 生产归档边界、`localDeletionAllowed=false`、原库/现网、正式签名、发布和部署边界均保持关闭。

## 结论与下一步

656/657 补齐了本切片的执行前输入绑定和运行后取证；654/655 保留为原结果与只读复核，不能与新执行混写。该单元可按上述路径和 SHA 复用，不收口 E07 L2、D14/D15 整体、D18 L3 或总目标。Android、Chrome、原模拟器及最终候选整合仍须按受影响范围实际执行。真实异地介质/保管回取属总目标第 9 节 C06，继续列未验，不阻断本机恢复工具、E08/UI 和 E09 的独立开发。下一步先核对 E02 两类三次失败的新诊断依据及后续 UI 修改，补对应客户端验收，再推进剩余组合与候选闸门。
