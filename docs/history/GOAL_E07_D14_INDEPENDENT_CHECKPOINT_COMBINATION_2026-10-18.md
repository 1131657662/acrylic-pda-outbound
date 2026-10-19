# E07/D14 独立检查点恢复组合（2026-10-18）

本报告登记一次新的隔离候选。组合验证了真实 HTTP/MySQL 上的原始独立原件、关联意图、截点前缀恢复和原 binlog 重建；监督者、操作员、实物凭证和目标订单均为隔离合成输入，不能替代现场身份、原 PDA、真实实物或生产审批。该候选不把取消的接收确认/激活链重新纳入范围。

## 输入与隔离

- 工作树：`D:/acrylic-pda-outbound`，分支 `codex/development`，输入 HEAD `93710ec5cff979a567654084c80abe089e9a73b7`；本次没有产品源码改动。
- 候选：run `722`；attempt `.local/e2a-cross-20260925-722/`；数据库 `acrylic_e2a_cross_20260925_722`；账号 `e2a_cross_20260925_722@127.0.0.1`。
- 端口：MySQL `19332`，HTTP `19450`，脚本保留的辅助端口 `19451/19452`。结束时四端口均无监听，数据库通过认证 `mysqladmin shutdown` 停止。
- 原始输入 `.local/goal-20261013/e06-closure-computer-02/actual-original.zip` SHA-256 `1577709AFAC180638244EA37623E13D63A57A2F73C520DF595A7DF6BA9C0C8E6`；候选保存的 `independent-checkpoint/original.zip` SHA 相同。
- 执行命令：`powershell -NoProfile -File tools/e2a-cross-source-verify.ps1 -Run 722 -IndependentCheckpoint -DbPort 19332 -HttpPort 19450`，子进程 `TEMP/TMP` 指向 `.local/e2a-independent-checkpoint-host-tmp01`。

## 实际结果

- `integration-result.xml` 的 `com.acrylic.outbound.e2averify.E2aIndependentCheckpointIT` 为 `tests=1, failures=0, errors=0, skipped=0`；Gradle 退出 0，`test-completed.marker` 存在。
- `connected-identity.tsv` 与 `shutdown-identity.tsv` 均确认 datadir 为该 attempt、端口 `19332`、专库、隔离账号、binlog 开启及信任函数配置；停机后无该候选进程或端口。
- 初始库和恢复库均通过非空迁移与检查点事实核对：两份快照各含 179 张表、99 条迁移历史；`before.json`/`rebuilt.json` 的表、文件、schema 和迁移事实由测试直接比较相等。
- HTTP 证据覆盖：原件接收、附件和实物核对、完整候选/库存读取、关联正向；缺失关联 `404`、证据转业务 `409`、操作员越权 `403`、默认关闭主管 `409`；恢复后原键同结果 `200`、按任务/按 ID 查询 `200`、异文 `409`、越权 `403`、原件字节回放 `200`。
- 恢复前后均断言目标订单保持 `DRAFT`，没有 `submission_scope`、`dispatch_record` 或 `order_result_version` 业务处理记录；关联表在缺失/默认关闭路径保持空。测试还校验原件、签名证据、物理附件和截点 binlog 重建。
- 主要证据：`.local/e2a-cross-20260925-722/integration-result.xml`、`integration.log`、`connected-identity.tsv`、`shutdown-identity.tsv`、`final-facts.json`、`original-binlog.bin`、`checkpoints/before-association/`、`independent-checkpoint/`。

## 输入代码绑定

本次运行直接消费的运行器/测试输入 SHA-256：

| 路径 | SHA-256 |
| --- | --- |
| `tools/e2a-cross-source-verify.ps1` | `66F9FCF696B7DDE1B0B8CD4F14791E07AA7A5B2BCAA76D289FA47B7FEFCCA8C4` |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aIndependentCheckpointIT.java` | `0756E01F505FD947BC246C332C895B4454C6F54C9A7F758ED5C89CB718F5D500` |
| `server/src/test/java/com/acrylic/outbound/inventory/IndependentCheckpointScenario.java` | `A44F24E43AD269B7B10A6465E9473DEC1484952E28CA17A3221D2557827DB3D5` |
| `server/src/test/java/com/acrylic/outbound/inventory/IndependentCheckpointTarget.java` | `CDFDEA60521D0C12590BBAC22D4AA426A3560EA71BEE7D65BEFA065AA29017DE` |
| `server/src/test/java/com/acrylic/outbound/e2averify/IndependentCheckpointSeed.java` | `C93F2B4EC4DEA81B0F676E381647D645A83A61C49A924A70E7DC7B1E8DD97700` |
| `server/src/test/java/com/acrylic/outbound/inventory/IndependentCheckpointHttp.java` | `20B5BCF5C623D969F230F27BC1AA1FE6434DDC114FA24025903BBEB55D6B5B53` |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aPointInTimeCheckpoint.java` | `3CF1C5E29C1A75668B39DAAFD8DB364E9DC324D80106342903882DC1C92BBDD1` |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aPointInTimeCommands.java` | `F13D7680D903E9FD171C5A0442693DB9D5423786366781070384523BA946B194` |

## 范围结论

该候选是 E07/D14 的受影响组合开发证据，证明原件和无业务处理的独立截点恢复路径在该隔离输入上可重放。未执行 Android、Chrome、外部 Python 消费者、原模拟器、现场身份、外置介质或恢复全链，因此不计 E07 L2、D14 整体、D18 L3、第 8 节闸门或总目标完成。旧 run 813/814 证据及继承 WIP 继续保留，禁止将本候选当作统一最终候选。
