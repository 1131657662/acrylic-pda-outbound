# E07 恢复与 O05 副本专项验收（2026-10-14）

本页记录 E07 同机隔离恢复和 O05 加密副本工具的实际证据。范围来自开发输入 `344b20fe7f066635e45a386656395500dfa68998`，不接原库、现网或外置介质，不把同机结果当现场事实，也不收口 E06 L2、D18 L3 或总目标。

## O05 副本

- 设计：[GOAL_E07_COPY_DESIGN_2026-10-14.md](GOAL_E07_COPY_DESIGN_2026-10-14.md)。工具只复制已通过包外信任根、签名和范围核验的加密原包；写入后 `fsync`、回读，失败保留 partial；取回必须提供副本目录外另保管的 `copy.json` SHA256。
- 专项测试：`.local/goal-20261014/e07-copy-tests02.log`，原始记录 14/14 通过，覆盖原字节、信任根/登记范围漂移、截断/损坏、错误口令、覆盖和路径拒绝；接管后以 `.local/goal-20261014/e07-copy-resume04/tests-copy-rerun.log` 重跑当前 15 项为 15/15，通过 `INDEPENDENT_CLOSURE_VECTOR_DIR` 绑定保全向量。
- 受影响回归：`.local/goal-20261014/e07-copy-tests-regression03.log` 原始记录及 `.local/goal-20261014/e07-copy-resume04/tests-regression-rerun03.log` 当前重跑均为副本、独立采集、封存、审阅和 relay 共 41/41；独立采集绑定 `.local/goal-20261014/e06-l2-android01/vectors`，relay 使用新隔离根 `.local/goal-20261014/e07-copy-regression04`。
- 失败保全：`.local/goal-20261014/e07-copy-regression01.log` 保留首轮漏设 `PDA_RESCUE_TEST_ROOT` 的 5 项环境错误；不计产品失败，也不删除原日志。
- 实际 CLI：`.local/goal-20261014/e07-copy-cli01/result.json` 和 `e07-copy-cli01.log`。真实 SAF 加密包创建、取回、清单/报告覆盖拒绝、目标覆盖拒绝、损坏包拒绝均通过；原包 SHA256 为 `b979d685981a6ea6f6e0db37874cf917057d54ee7ede0c3a609591ca5fd86966`，`copyRecordSha256` 为 `7b93c0eee8107dc4f755577ef43d33e4cec44399b62dfc0446a55fc819e3a47b`，取回报告 SHA256 为 `aeb72e6308742a6182dbac1c567fd065ac0dfb77bac2392c6b5c1d7f33aa92cc`。
- 结果边界：`externalStorageVerified=false`、`businessReceiptVerified=false`、`localDeletionAllowed=false`；`sameMachineOnly=true`、`physicalDevice=false`、`syntheticOriginalAvd=true`。不保存口令、token 或私钥。

## E07 恢复

- 输入及恢复设计：[GOAL_E07_RESTORE_DESIGN_2026-10-14.md](GOAL_E07_RESTORE_DESIGN_2026-10-14.md)。使用已认证停机的 1539 dump、`private/storage` 和固定运行 JAR，恢复到新隔离编号 1541/19332；1539 源库不重启、不复用。
- 恢复结果：`.local/goal-20261014/e07-restore-1541/restore01/result.json`。180 表、64 个非空表逐列逐行多重集和摘要与源一致；15 个 `private/storage` 文件逐字节一致；实际耗时约 16 秒；`sameMachineOnly=true`、`notProductionRpoRto=true`。
- 独立消费：`consumer01/result.json` 证明原 key/成员、原 ZIP/附件和固定外部公钥验签可读；新多任务 key 返回 409 `INDEPENDENT_MULTITASK_DISABLED`；业务事实不变。第二任务库存、实物和主管均为合成输入，不能冒充现场身份。
- 保全：`.local/goal-20261014/preserve-1541-e07-restore-01/result.json` 记录 1541 停机、零监听、dump 成功、347 个保护文件长度/hash 不变；预览与 1519 保留，源证据未删除。
- 写前拒绝：`.local/goal-20261014/e07-restore-negative03/result.json` 和 `.log` 覆盖缺失 dump、损坏 dump、缺失私有文件、损坏存储文件，4/4 在创建目标前拒绝；目标 `restore-target-1542` 未创建。首轮 `.local/goal-20261014/e07-restore-negative02.log` 保留了误取目录的脚本失败，未触及源资料或数据库。

## 未完成与后续

本页只收口 O05 工具专项和 E07 同机恢复专项。异地/真实外置介质保管、原 PDA 现场、P07 历史锚点与重锚、E08 留存/UI、E09 候选、E06 L2、D18 L3 及第 8 节其他闸门仍未完成；总目标继续 `active`。1541 及所有失败/保全目录必须保留，不能复用为后续隔离输入。
