# E07/D14 撤回重发恢复五检查点组合（run725，2026-10-18）

本报告登记一次新的隔离撤回重发恢复候选。它覆盖坏机离线后的有权撤回、旧资格拒绝、重新发布、新轮次成功以及五个恢复检查点；输入身份和数据库均为一次性隔离合成数据，不恢复已取消的换机承接、接收确认或激活链。

## 输入与隔离

- 工作树为 `D:/acrylic-pda-outbound`，分支 `codex/development`，输入 HEAD `93710ec5cff979a567654084c80abe089e9a73b7`；本次未修改产品源码。
- 命令为 `powershell -NoProfile -File tools/e2a-cross-source-verify.ps1 -Run 725 -WithdrawalRecovery -DbPort 19336 -HttpPort 19458`。
- attempt 为 `.local/e2a-cross-20260925-725/`，隔离库为 `acrylic_e2a_cross_20260925_725`，身份文件记录端口 `19336`、账号 `e2a_cross_20260925_725@127.0.0.1`；`connected-identity.tsv` 与 `shutdown-identity.tsv` 字节一致，binlog 开启。
- 运行前后状态、进程/端口/磁盘、attempt 哈希及工作树差异保存在 `.local/goal-20261018-resume/e07-withdrawal-recovery-run01/`。运行结束后 `19336/19458/19459/19460` 均无监听并完成认证停机。

## 实际结果

- `integration-result.xml` 为 `E2aFaultWithdrawalRecoveryIT`，`tests=1, skipped=0, failures=0, errors=0`；Gradle 进程退出 0，完成标记存在。
- 五个检查点（`before-withdrawal`、`after-withdrawal`、`after-republish`、`before-success`、`after-success`）均记录旧 submission/旧计划/变更后撤回请求为 HTTP 409，`sameReceipt=true`、`successReplay=true`。每个检查点的账务事实保持一致，没有重复扣账。
- 场景回执为 `WITHDRAWN`，原订单仍为 `DRAFT`，`restartRevision=2`，`mayResumeScanning=false`，未释放库存；重新发布后的新作业为 `workRound=2`、`SUCCEEDED`/`COMPLETED`，2 件、601500 克。旧轮次不能继续写入，新轮次可独立完成。
- 每个检查点均生成 SQL/重放和存储文件；最终 `final-facts.json`、`final-triggers.json`、原 binlog 与五份检查点事实均保留。最终事实包含 179 张表及 2 个非空业务存储文件；原 binlog SHA-256 为 `74094e8aca83ef327ec41369f81e4ed5f74ccd288690d04bdfabac6aad9229ae`。
- 本次没有执行 Android 客户端、Chrome、外部 Python 消费者、原模拟器、真实 PDA/原身份或外置介质取回；因此它是撤回恢复的真实隔离 HTTP/MySQL 开发候选，不计 E07/D14 L2、O05、D18 L3 或第 8 节闸门。

## 关键证据哈希

| 路径 | SHA-256 |
| --- | --- |
| `tools/e2a-cross-source-verify.ps1` | `66F9FCF696B7DDE1B0B8CD4F14791E07AA7A5B2BCAA76D289FA47B7FEFCCA8C4` |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aFaultWithdrawalRecoveryIT.java` | `89C055F8336FB0CD9A3A1346481614E72343C03156CF5C67534AB8C6B3D1D037` |
| `server/src/test/java/com/acrylic/outbound/inventory/FaultWithdrawalHttp.java` | `1925630CB150C9A38693A4823B46E57989D7888ACDE624B9B14927D74EF67FB3` |
| `.local/e2a-cross-20260925-725/integration-result.xml` | `181AAD0152F2A452AB9C59F5DEB8EBB288A72397A85A8120421BE9C87245DD1A` |
| `.local/e2a-cross-20260925-725/private/scenario.json` | `941E206E3C626DC57E97586EB6C9DA6AACF5E784C82CE3F8FB4D67EC471FE6FA` |
| `.local/e2a-cross-20260925-725/final-facts.json` | `BA3BD8625EBA1321EBEE6E50B44721AD7C3F2C702B6E7BF6733BFAA315ED4A41` |
| `.local/e2a-cross-20260925-725/final-triggers.json` | `A6906CA853D63F152B2E53401B1412DF6125B2613E974AB2D25E8A1361F7F7AB` |
| `.local/e2a-cross-20260925-725/connected-identity.tsv` | `2BFFF8B1B911F06219C9D01A5E3EC700E17216592FF26A29AAC92A38516DCACC` |
| `.local/e2a-cross-20260925-725/shutdown-identity.tsv` | `2BFFF8B1B911F06219C9D01A5E3EC700E17216592FF26A29AAC92A38516DCACC` |
| `.local/e2a-cross-20260925-725/original-binlog.bin` | `74094E8ACA83EF327EC41369F81E4ED5F74CCD288690D04BDFABAC6AAD9229AE` |
| `.local/goal-20261018-resume/e07-withdrawal-recovery-run01/snapshot-hashes.json` | `734B83904283EF6B85E944A7201C3B461CC5DE5E8A88FC3DC3AD8E4DD0D1F79E` |

## 范围结论

run725 补足了 D14 撤回重发在五个恢复截点上的隔离 HTTP/MySQL 证据，且保留失败原件与认证停机记录。它没有提供 Android、Chrome、外部 Python、原模拟器、现场身份或外置副本证据；后续不得把该候选当作统一最终候选，也不得据此开放生产决定能力或改变 `localDeletionAllowed=false`。
