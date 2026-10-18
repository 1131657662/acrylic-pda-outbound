# E07/D06-D07 撤回重发恢复候选（run741）

日期：2026-10-18。运行树为 `D:/acrylic-pda-outbound`，分支为
`codex/development`，运行输入源码 HEAD 与 `origin/codex/development` 均为
`d6fa75fc76807bf52c080928d94d71cb29c1c93f`。本次只使用全新的 D 盘隔离实例，未连接原库或现网，未使用正式签名，未恢复已取消的换机进度承接、多跳或接收者确认/激活链。

## 执行与结果

实际命令：

```text
powershell -NoProfile -ExecutionPolicy Bypass -File tools/e2a-cross-source-verify.ps1 -Run 741 -WithdrawalRecovery -DbPort 19641 -HttpPort 19642
```

隔离实例为 `acrylic_e2a_cross_20260925_741`，MySQL 端口为 `19641`，HTTP 端口为
`19642`；运行结束后由隔离账号认证停机，两个端口均已释放。集成测试
`com.acrylic.outbound.e2averify.E2aFaultWithdrawalRecoveryIT` 实际执行 `1` 项，失败 `0`、错误 `0`、跳过 `0`，Gradle 与 runner 退出码均为 `0`。

测试覆盖真实 Spring HTTP/MySQL 下的故障撤回、原键重放、异文拒绝、重新发布、新设备从头领取/下载/提交、成功终态保护及竞争路径，并在以下五个非空截点重建数据库后重放原 binlog 和 HTTP 读取：

| 截点 | 重建 schema | dump SHA-256 |
| --- | --- | --- |
| `before-withdrawal` | `acrylic_e2a_cross_20260925_741_copy` | `5ff073bce690031078dea2c1a1a9275d72c1e43ffbab6ca12b8fbdaa3fac7593` |
| `after-withdrawal` | `acrylic_e2a_cross_20260925_741_conflict` | `5e3f7183d3c0f88a1157f23ace425bb79d4dc932256b9959e433885c73d0121b` |
| `after-republish` | `acrylic_e2a_cross_20260925_741_missing` | `a1578420273fb3281833b3a607a1de3d9723e1f2d52dbb2060689de8cc6709ae` |
| `before-success` | `acrylic_e2a_cross_20260925_741_gap` | `15460d0fa3b1142bb55ad537c485b86cf3766bba28d0cc7075b16eea780087e2` |
| `after-success` | `acrylic_e2a_cross_20260925_741_restore` | `29ed72fbf9e5f5803fe6f7a62ad5bdce011393121f608d4318064c0db2338ccf` |

集成 XML SHA-256 为 `55AA3B359B245B1F5B7165A8E9A7228D2ADBAA1046EA9F08899C4FA26EDE5D98`；原 binlog
SHA-256 为 `E6F1CBB860F535FC333A1CE1B464611602DCC6DDE5EE2B13E9138E49D59C3E9E`。连接与停机身份文件哈希一致，说明测试使用同一隔离 datadir、端口、schema 和账号。

## 判定与边界

run741 是可复现的真实 HTTP/MySQL 撤回恢复候选，证明当前实现可在隔离输入上完成五截点恢复和账务守恒。输入中的人员、设备、仓库、签名和浏览器会话均为合成隔离事实；本轮没有 Android instrumentation、Chrome、外部 Python 消费、原 PDA/原模拟器、现场身份、原回执或外置介质。因此本记录只作为 E07/D06-D07 的局部开发证据，不计 D06/D07 L2、E06 L2、D17、D18 L3 或总目标完成。

## 改动与复用

本切片未修改产品源码或运行器；受影响路径、源码 SHA、命令和结果索引见
`.local/goal-20261018-resume/e07-withdrawal-run741/source-sha256.json` 与
`.local/goal-20261018-resume/e07-withdrawal-run741/result-summary.json`。直接受影响验证为本次真实隔离 HTTP/MySQL 与五截点重建；Android、Chrome、Python、原模拟器和现场身份仍需后续全链候选，不以本次结果复用替代。
