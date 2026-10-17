# E07/D14 独立检查点恢复组合验收（2026-10-17）

本报告记录 run 814 的独立检查点恢复与只读消费者复核。范围是 E07/D14 的受影响组合开发验收，不扩展为 E07 L2、D14 整体、D18 L3 或总目标完成。固定树为 `D:/acrylic-pda-outbound`，分支为 `codex/development`，执行时 HEAD 为 `cbeee674`；不接原库或现网，不使用现场身份或正式签名。

## 输入与边界

- 新隔离检查点目录：`.local/goal-20261017-resume/e07-combination814/`；数据库身份、端口和 datadir 均由本轮接手者重新核对，未复用 1538、旧失败库或历史 1522/1523 输入。
- run 813 的预创建 attempt 失败（`Attempt already exists`）原件保留于 `.local/goal-20261017-resume/e07-combination813/`；该失败发生在业务启动前，没有数据库或业务写入，不清零失败计数。
- run 814 使用独立数据库/端口和新证据根，执行完成后认证停机，连接与停机身份逐字一致，目标端口无监听；数据库目录、binlog、dump、原件与日志继续保全。

| 改动路径 | 影响层与必跑清单 | 复用/绑定依据 |
| --- | --- | --- |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aIndependentCheckpointIT.java` 及其独立场景/HTTP/恢复辅助类 | E07 独立来源、原件、恢复前后事实、真实 HTTP/MySQL、非空迁移、binlog 重建、default-off 与停机身份 | run 814 `test-completed.marker`、测试结果和 `.local/goal-20261017-resume/e07-combination814/` 全量输入 |
| `tools/e07-independent-checkpoint-consumer.py` | 独立只读 Python 消费、双次输入 manifest、HTTP body/status、全表/文件和身份守恒 | 本脚本 SHA-256 `8c223b05213d9d569e15d281b6cee228afde853693e0d0eb2b6611dbd7b9228e`；消费者结果绑定同一输入根 |
| 本报告、历史索引、进度索引和看板 | 验收记录与接续断点 | 更新前快照 `docs/history/ORCHESTRATION_STATE_before_e07_consumer_2026-10-17.md` |

## 实际验证

run 814 的 `E2aIndependentCheckpointIT` 为 1 test、0 failures、0 errors、0 skipped，Gradle 构建成功。场景覆盖独立来源和目标、原始 relay/attachment、接收与关联、恢复前后事实、binlog 重建、非空迁移、default-off 拒绝以及 `localDeletionAllowed=false`。数据库认证停机完成，connected/shutdown identity 完全一致，`test-completed.marker` 已生成。

随后执行：

```text
python tools/e07-independent-checkpoint-consumer.py --source D:/acrylic-pda-outbound/.local/goal-20261017-resume/e07-combination814 --output D:/acrylic-pda-outbound/.local/goal-20261017-resume/e07-combination814-consumer
```

独立消费者结果为 `passed=true`、`failures=[]`，且声明未建立数据库或网络连接。输入 before/after manifest 均为 854 个文件且完全一致；恢复前后均为 179 张表、622 行，文件字节和 SHA 守恒。记录的 HTTP 端点 `association`、`attachment`、`full-candidates`、`initial-inventory`、`physical-inventory`、`physical`、`receive` 均为 200/`OK`。关联回执保持 `pendingReconciliation=true`、`releaseAllowed=false`；所有库存/关联响应保持 `localDeletionAllowed=false`。

关键证据：

| 证据 | 字节 | SHA-256 |
| --- | ---: | --- |
| `.local/goal-20261017-resume/e07-combination814/independent-checkpoint/scenario.json` | 46220 | `09bb360323c0e13b67f929e3d19e3e4b764843f5a61b349e05570ece20d4ad12` |
| `.local/goal-20261017-resume/e07-combination814/final-facts.json` | 819374 | `9167ab722b90ba36b2a81ecf3b02b13bd12a2bd4645b899be4bdc6a4de29ce43` |
| `.local/goal-20261017-resume/e07-combination814/checkpoints/before-association/rebuilt.json` | 664823 | `2b062e7ae8cc0ecf5176bc0156a23cdfb6d18328d92f626facaee5cc8e18fc36` |
| `.local/goal-20261017-resume/e07-combination814/connected-identity.tsv` | 151 | `158548c463b496190645216b842d2a0381bed9e0712e4f0cc8c43c14e56aa997` |
| `.local/goal-20261017-resume/e07-combination814/shutdown-identity.tsv` | 151 | `158548c463b496190645216b842d2a0381bed9e0712e4f0cc8c43c14e56aa997` |
| `.local/goal-20261017-resume/e07-combination814/test-completed.marker` | 144 | `73398480c67a19141150c7d10aad01eb5ef7c823f3c7409ccdc4d400f12b02a7` |
| `.local/goal-20261017-resume/e07-combination814-consumer/consumer-result.json` | 370599 | `636277dfc8d1498fb97a9b2107e07c767943f568e272fe9ec020490bc60314ae` |

## 结论与未覆盖

本批满足 E07/D14 独立检查点恢复受影响组合的开发验收：真实隔离 HTTP/MySQL、非空迁移事实、原件与签名绑定、恢复前后全表/文件守恒、独立只读消费、default-off 拒绝及认证停机均有当次证据。它不宣称 E07 L2、D14 整体、D15–D18、E06/E08/E09、O/U、Android、Chrome、原模拟器、异地/现场保管或 D18 L3 通过。生产七类决定、`localDeletionAllowed=false`、原库/现网、部署和发布边界保持关闭。

下一步保留本批证据，继续 E07 外置副本、E08/UI、D14–D18、O01–O05/U01–U04；候选输入冻结后按 D18 L3 要求执行 Android、真实 HTTP/MySQL、Chrome、Python、非空迁移、原模拟器和恢复全链回归。
