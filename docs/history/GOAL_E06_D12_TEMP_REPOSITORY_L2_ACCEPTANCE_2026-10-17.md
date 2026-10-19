# E06/D12 TEMP 仓库恢复 L2 候选验收（2026-10-17）

本报告只登记一次新的隔离候选，不把局部通过提升为 E06、D12、D17、E07、D18 或总目标完成。范围是 TEMP 产品会话在上游成功但响应丢失后持久化 `RESULT_UNKNOWN`，服务端 SQL/文件恢复后按原 ID 查询并回到 `SYNCED`；取消的 recovered-confirmation worker/dialog/activation 链未参与。

## 输入和隔离

- 固定树：`D:/acrylic-pda-outbound`，分支 `codex/development`，HEAD/远端均为 `e3cebea70eb89576fc014c20128ea4940f3d273b`。
- 候选 run：692；隔离 datadir：`.local/e2a-cross-20260925-692/data`；数据库：`acrylic_e2a_cross_20260925_692`；身份：`e2a_cross_20260925_692@127.0.0.1`。
- 端口：MySQL `19694`，HTTP `19695`，代理 `19696`；候选结束时三端口及临时内部适配器 `19435` 均无监听。
- 输入快照：`retry-692/input-before.json` SHA-256 `f0c31cad3e010c2cfe3f5a96756d75c7be0fbd52b289ca9524d0df0348e231a6`；结束快照：`retry-692/input-after.json` SHA-256 `4c67fcea430c2a043a4db098655d0d74621becbb93506eb815834fdbf6d17e5e`。
- 命令登记：`retry-692/run692-command.json` SHA-256 `e87c52931c6307da74f73fbd0d489ef5034da9ad21190e60f092f27c60725bea`；六个实际源码输入及 SHA 见 `retry-692/new-code-sha01.json`（SHA-256 `0aff0ecbe32183bbe129d259935de61ea1aa80f66369677b7d5a59f95bce2a27`）。

## 实际结果

- runner exit `0`，结果文件 `retry-692/runner692-result.json` SHA-256 `97d0a8c8147cc7480062520b1f8bf8deec1f0e9453c70648f32b17b74e560b8a`；日志末尾记录 authenticated MySQL shutdown；`connected-identity.tsv` 与 `shutdown-identity.tsv` SHA 均为 `dadcee576c608b114b26165cb3dac8a2f1c8c70a77d4699d086c3ff256de3d3a`。
- 非空 Flyway：99 条成功迁移，应用表 179、dump 表 180、storage 文件 8；`private/archive-snapshot.sql` 729219 bytes，SHA-256 `06e416de58cad45441bdc1e00c61d935f18a17dc008656eadeac4667f2fa2b1b`。
- Android source/restored XML 均为 1 test / 0 failure / 0 error / 0 skipped；产品两个 JVM 阶段均实际通过。Room 三阶段均 83 表，行总数 source/restored 73、unknown 71，状态为 `SYNCED → RESULT_UNKNOWN → SYNCED`；仅 `temporary_submission`、`temporary_success_response`、`temporary_success_evidence` 变化。
- 代理 GET 序列为 source `[dropped=true, dropped=false]`、restored `[dropped=false]`；消费者校验原 ID、payload SHA、RS256 JWS `kid`、响应字节、三表变化、SQL dump/迁移、storage 文件、XML、runner 和停机身份共 75 项通过。
- 独立 Python 消费者报告：`retry-692/consumer692-final.json`，SHA-256 `15b923f7355ea58e2b7c03979c864a3654bd54f66172fc372f457ceb1bc59c77`；结果文件 `consumer692-final-result.json` SHA-256 `e42f30b94a4ccb8992273e22328b96d25aa6c5131b1f5ab8fbc4721e118608d1`，exit `0`。
- Chrome：不适用。该候选没有 Web/UI 产品入口或页面变更；Android/HTTP/SQL/文件路径已由实际产品测试和独立消费者覆盖，未将“不适用”写成 Chrome 业务通过。

## 失败和边界

- 旧候选 run 691 因 Android 夹具固定校验 `19434` 拒绝新隔离 HTTP 端口，在预备阶段失败；其 datadir、日志、XML、SQL、binlog 和停机身份保留于 `.local/e2a-cross-20260925-691/`，未重用。修复为 `E2A_HTTP_PORT` 环境端口后，Android 编译 exit `0`，随后 run 692 完成上述候选。
- 输入使用合成隔离身份、测试签名和合成 Room 前驱，不能代替原 PDA、原身份、现场回执、真机或生产审批；未连接原库/现网，未使用正式签名，`localDeletionAllowed=false` 保持。
- 本候选没有覆盖 Web Chrome、原模拟器恢复全链、D18 L3 组合、D16 可信合法退出或第 8 节其余闸门；因此只计作 E06/D12 的局部开发 L2 证据。隔离 datadir、dump/binlog、HTTP/Android 原件和失败 run 691 按当前清理规则继续保留，直到阶段收口核对。

