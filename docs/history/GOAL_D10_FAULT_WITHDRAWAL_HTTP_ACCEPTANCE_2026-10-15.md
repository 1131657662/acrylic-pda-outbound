# D10 故障撤回/重发真实 HTTP 验收（2026-10-15）

本报告收口 D10 的故障撤回、重发、新设备独立作业和消费者夹具切片，不代表 D06/D07/D10 全部退出，也不代表 E06 L2、D18 L3 或总目标完成。输入树为 `D:/acrylic-pda-outbound`，分支为 `codex/development`；运行前 HEAD 为 `1bf2abfb4366423241ab9f7a0b7ba6b743fa66ce`。继承的未提交/未跟踪改动未覆盖、未清理、未重排。

## 输入与隔离身份

- 隔离 MySQL：`.local/e2a-cross-20260925-1542/`，datadir 同目录 `data/`，数据库 `acrylic_e2a_cross_20260925_1542`，账号 `e2a_cross_20260925_1542@127.0.0.1`，端口 `19331`。账号密码只保存在该目录 `run-password.txt`，未写入 Git。
- 消费者输入：`.local/goal-20261011/fault-restart-consumer-1542.json`，SHA-256 见 `.local/goal-20261015/d10-regression-01/consumer-input-sha.json`。该文件由真实 IT 生成，身份为合成隔离用户/设备，不能冒充原 PDA 或现场回执。
- 运行前数据库事实为 0 张表；运行后 `180` 张表、`53` 张非空表。`db-before.txt`、`db-after.txt`、`table-facts-after.tsv` 保留身份、端口、datadir 和逐表事实。
- dump：`.local/goal-20261015/d10-regression-01/d10-database-after-notablespaces.sql`，1095727 bytes，SHA-256 `FFB8D15F551BDD081E2C3B86CA9884AD32272C50A8496FA5B16744D3D22D960D`。首次未带 `--no-tablespaces` 的权限失败保留在 `dump-stderr.log`，随后成功重取，不删除失败证据。

## 实际验证

命令为：

```text
gradlew.bat -p server integrationTest
  --tests com.acrylic.outbound.inventory.FaultWithdrawalApiIT
  --tests com.acrylic.outbound.inventory.FaultWithdrawalHttpIT
  --tests com.acrylic.outbound.inventory.FaultRestartApiIT
  --tests com.acrylic.outbound.inventory.FaultRestartConsumerFixtureIT
  --rerun-tasks --console=plain -x :report-rendering:prepareFont
```

结果 XML 和完整日志保存在 `.local/goal-20261015/d10-regression-01/`：

- `FaultWithdrawalApiIT`：7/7；覆盖无 STOP 撤回、原键幂等/异文拒绝、版本冲突、成功终态保护、实物占用保护、跨仓/角色权限和末写失败回滚。
- `FaultWithdrawalHttpIT`：4/4；覆盖真实 HTTP 丢响应重放、发布/领取/下载/新设备提交、撤回与原提交竞争、主管审批与实物撤回竞争、既有 REQUESTED 原请求保留。
- `FaultRestartApiIT`：3/3；覆盖新设备从头作业、已收实物释放后主管复核、末写失败恢复旧轮次/发布。
- `FaultRestartConsumerFixtureIT`：1/1；生成 Web/存储/Android/撤回消费者输入并校验无预先账务。

合计 `15` 项，失败 `0`、错误 `0`、跳过 `0`，Gradle 退出码 `0`。服务端附件定向单测另见同目录 `server-unit-result.json`（17/17），Web typecheck、contract、build 另见 `web-checks-result.json`，均退出码 `0`。

## 资源与边界

运行期间 `19331` 由隔离 mysqld 监听，`3306` 始终为共享 MySQL PID 5288；结束后只停止命令行指向 `e2a-cross-20260925-1542` 的 mysqld，`19331`、`19434` 释放，`3306` 保持。前后进程、监听和 C/D 盘余量见 `processes-http-before.json`、`processes-http-after.json`、`listeners-before-stop.json`、`listeners-after-stop.json`、`disk-http-before.json`、`disk-http-after.json`。

本切片没有现场 PDA、真实原身份、人工主管、外置介质、真机、生产数据库或生产开关证据；`outbound.recall.fault-withdrawal-enabled` 仅在隔离测试 context 打开，生产默认关闭，`localDeletionAllowed=false` 未改变。D10 其余更正/附件消费者、E06 L2、E07/E08/E09、D01–D18 其他闸门、O01–O05/U01–U04 和 D18 L3 仍按看板继续推进。
