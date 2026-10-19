# E07/D06-D07 撤回重发服务端组合（run733，2026-10-18）

本报告登记一次全新的隔离撤回重发候选。它验证坏机离线后的有权撤回、旧资格失效、重新发布、新设备从头作业、真实 HTTP/MySQL 和五个恢复检查点；所有身份、数据库、签名和文件均为一次性隔离合成输入，不恢复已取消的换机承接、多跳或接收者确认/激活链。

## 输入与隔离

- 工作树为 `D:/acrylic-pda-outbound`，分支 `codex/development`，输入 HEAD `93710ec5cff979a567654084c80abe089e9a73b7`；本次没有修改产品源码。
- 命令为 `powershell -NoProfile -ExecutionPolicy Bypass -File tools/e2a-cross-source-verify.ps1 -Run 733 -WithdrawalRecovery -DbPort 19343 -HttpPort 19450`。
- attempt 为 `.local/e2a-cross-20260925-733/`，隔离库为 `acrylic_e2a_cross_20260925_733`，身份文件记录端口 `19343`、账号 `e2a_cross_20260925_733@127.0.0.1`，binlog 开启；`connected-identity.tsv` 与 `shutdown-identity.tsv` 字节一致。
- 运行前后输入、工作树、进程/监听/磁盘、attempt 文件哈希和命令快照保存在 `.local/goal-20261018-resume/e07-d06-d07-run733/`。运行结束后 `19343`、`19450` 及恢复辅助监听均清零，MySQL 由隔离账号认证停机。

## 实际结果

- `integration-result.xml` 为 `E2aFaultWithdrawalRecoveryIT`，`tests=1, skipped=0, failures=0, errors=0`；Gradle 退出 0，`test-completed.marker` 存在。
- `before-withdrawal`、`after-withdrawal`、`after-republish`、`before-success`、`after-success` 五个非空检查点均生成 SQL/存储快照和 `http-result.json`。每个检查点都记录旧 submission/旧计划/异文撤回为 HTTP 409，`sameReceipt=true`、`successReplay=true`，账务快照保持一致。
- 撤回回执为 `WITHDRAWN`，原订单回到 `DRAFT`，`restartRevision=2`，`mayResumeScanning=false`；重新发布后新领取为 `workRound=2`，新 submission 以 2 件、601500 克完成 `SUCCEEDED`/`COMPLETED`。旧轮次上传、旧计划下载和异文请求均被拒绝。
- `final-facts.json` 包含 179 张表、2 个非空存储文件及迁移历史；`final-triggers.json`、原 binlog 和五组检查点事实均保留。原 binlog SHA-256 为 `C73C8FF8298D0473F0FA8789BD89767B7EC26E84C8E9C48330C5687D46B31494`。
- 本次未执行 Android 产品客户端、Chrome、外部 Python 消费者、原模拟器、真实 PDA/原身份或外置介质取回。因此这是 D06/D07 撤回重发的真实隔离 HTTP/MySQL 服务端候选，不计 D06/D07 L2、E07/D14 整体、D18 L3 或第 8 节闸门。

## 关键证据哈希

| 路径 | SHA-256 |
| --- | --- |
| `tools/e2a-cross-source-verify.ps1` | `66F9FCF696B7DDE1B0B8CD4F14791E07AA7A5B2BCAA76D289FA47B7FEFCCA8C4` |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aFaultWithdrawalRecoveryIT.java` | `89C055F8336FB0CD9A3A1346481614E72343C03156CF5C67534AB8C6B3D1D037` |
| `server/src/test/java/com/acrylic/outbound/inventory/FaultWithdrawalRecoveryScenario.java` | `42A130C888B448F388FE2785AF4D44009D2D8BFB2E6BFBCD9920730E4D04353A` |
| `.local/e2a-cross-20260925-733/integration-result.xml` | `D8CEE7B7D488455AEB94A45435916A8A39BEFCC05E73EBBDE4C54C934D58FB60` |
| `.local/e2a-cross-20260925-733/integration.log` | `1EAE3E9E25624A1B79E8C1D6459E0444135322BD7FDE22B536A329EABCE78FAC` |
| `.local/e2a-cross-20260925-733/private/scenario.json` | `1D352EBBC8E6718FFFD1B2C7FE8D62732D2FB79ACDAB6A1F577A5E40CE788D3F` |
| `.local/e2a-cross-20260925-733/final-facts.json` | `D885DD16851AD2D02D7723D47618FF82C4F0535C04B6E8A607EDE8536F9E3735` |
| `.local/e2a-cross-20260925-733/final-triggers.json` | `66F0B873F94F9908EC6BFEFC1FA1C541EC75874D28390B940FEA09214A198544` |
| `.local/e2a-cross-20260925-733/connected-identity.tsv` | `88CFF648193FFC236EFBF572B22A756B3F5368DB61A2F9CD3A7120E10816AEE0` |
| `.local/e2a-cross-20260925-733/shutdown-identity.tsv` | `88CFF648193FFC236EFBF572B22A756B3F5368DB61A2F9CD3A7120E10816AEE0` |
| `.local/goal-20261018-resume/e07-d06-d07-run733/input-manifest.json` | `4E34116C474BBB1FA54884AE30FEDA40B45C32B82EF65EB8FDB0B8721CB7BBD2` |

## 范围结论

run733 补足了撤回重发入口的一次新隔离真实 HTTP/MySQL 组合，并保留五个恢复截点、旧资格拒绝、同键幂等、异文拒绝、新轮次成功和认证停机事实。它没有提供 Android、Chrome、外部 Python、原模拟器、现场身份或外置副本证据；后续不得把该候选当作统一最终候选，也不得据此开放生产决定能力或改变 `localDeletionAllowed=false`。
