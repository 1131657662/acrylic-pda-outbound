# E07/D06-D07 撤回重发恢复候选（run745）

本记录收口一次新的隔离撤回重发恢复候选。运行树为
`D:/acrylic-pda-outbound`，分支为 `codex/development`，输入 HEAD 与远端为
`cdb0913f0cd8a2552302aefcb27db97ed5383dd2`。本次不连接原库或现网，不使用正式签名，
不恢复已取消的换机进度承接、多跳或接收者确认/激活链。

## 范围与执行

| 路径 | 影响层 | 本次验证 |
| --- | --- | --- |
| `tools/e2a-cross-source-verify.ps1` | 隔离运行器 | 新建专用 attempt、账号和端口，认证停机后校验监听清零 |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aFaultWithdrawalRecoveryIT.java` | 服务端集成测试 | 真实 HTTP/MySQL 执行坏机离线撤回、旧资格拒绝、重新发布、新设备从头作业及成功终态保护 |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aPointInTimeCheckpoint.java`、`E2aArchiveSnapshotRestore.java` | 截点恢复与事实消费 | 五个非空截点重建数据库、回放原 binlog、复核 HTTP 读取、表/文件事实守恒 |

实际命令：

```text
powershell -NoProfile -ExecutionPolicy Bypass -File tools/e2a-cross-source-verify.ps1 -Run 745 -WithdrawalRecovery -DbPort 19745 -HttpPort 19746 -AttemptPath .local/e2a-cross-20260925-745
```

隔离数据库为 `acrylic_e2a_cross_20260925_745`，账号、datadir、密钥和存储均在
`.local/e2a-cross-20260925-745/` 的私有 attempt 内。19745–19748 在收口时无监听，
MySQL 由隔离账号认证停机；`connected-identity.tsv` 与 `shutdown-identity.tsv`
SHA-256 均为
`8E7814ED377C0732C7580523FDBB31623B254D6F9E11E3094041BFEDB3982DD0`。

## 结果

- `integration-result.xml` 为 `tests=1, skipped=0, failures=0, errors=0`，Gradle 和运行器退出码均为 0；集成 XML SHA-256 为
  `D5954B982A675E7292258A6349EBDEC46433C4B69BFF5EE7DCDF38944DA63322`。
- 真实 HTTP/MySQL 流程完成撤回、旧资格/异文拒绝、同键幂等、重新发布和新设备从头领取/冻结/成功上传；旧轮次保持撤回，新轮次使用不同设备与工作轮次并进入 `SUCCEEDED`。
- 五个截点 `before-withdrawal`、`after-withdrawal`、`after-republish`、`before-success`、`after-success` 均生成 `before.json`、重建快照、HTTP 回放和迁移事实；最终快照包含 179 张表与 2 个存储文件。最终事实 SHA-256 为
  `5D51CC966D2FB23263F499839186A1A0FF538202C737C173EEC62E3C8C4FA8B7`，触发器事实 SHA-256 为
  `9A6FBAB6510FB9D05302798AB023AED09F36967C41FA86FD45314FB360712228`。
- 原 binlog 为 536758 bytes，记录与实际 SHA-256 均为
  `5F2E40AD5A9DBED4F06AE722812F41487693A8AC8DDCCC65E5D9D12597A1DAE4`；恢复后 HTTP 读取与原事实校验由测试完成。

直接受影响验证为本次真实隔离 HTTP/MySQL、非空迁移、五截点重建、原 binlog 回放和认证停机。复用依据为当前树的
`E2aCrossSourceHttpIT`、`E2aPointInTimeCheckpoint`、`E2aArchiveSnapshotRestore` 以及
前序撤回重发报告的范围约束；没有把前序业务通过拼接为本次结果。

## 边界与后续

本候选的身份、设备、仓库、订单、签名和数据库均为合成隔离输入；没有 Android 客户端行为、Chrome、独立 Python 消费、原模拟器、真实 PDA/原身份、外置介质或现场回执。因此它只推进 E07/D06-D07 的服务端恢复候选，不计 D06/D07 L2、E07/D14 整体、D17、E06 L2、O05、D18 L3、第 8 节闸门或总目标完成。原 attempt、日志、五个截点、数据库文件、原 binlog、密钥和停机事实继续保全，后续不得把本候选当作统一最终候选。

