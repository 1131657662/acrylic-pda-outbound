# E07/D06-D07 非空迁移候选（run1607/run1608）

日期：2026-10-18。运行输入源码 HEAD 为 `93710ec5cff979a567654084c80abe089e9a73b7`，
所有数据库均为 D 盘隔离实例；未连接原库或现网，未使用正式签名。

## 运行结果

| 运行 | 结果 | 事实 | 原件 |
| --- | --- | --- | --- |
| 1607 | 失败 | fixture/provision 与 dump/认证停机完成；非空迁移集成测试 `FaultWithdrawalMigrationIT > migrationPreservesLegacyRowsAndBackfillsTheirImmutableOperationRevision` 在 `FaultWithdrawalMigrationIT.java:45` 失败，退出码 1 | `.local/goal-20261011/fault-restart-migration-1607/`；迁移日志 SHA-256 `F1C4B6FA86A990654666A9DA705C69443CE063F49F11B0837754408D86B34C98` |
| 1608 | 通过 | 非空迁移集成测试完成，Gradle `integrationTest` 为成功；provision、migration、dump 和认证停机退出码均为 0 | `.local/goal-20261011/fault-restart-migration-1608/` |

run1607 的最终 dump 为 497651 字节，SHA-256
`3FF0835E8CE2CCA6CB269A90FFFCE45090ACE098446E6FF19467F150DB48D609`；失败日志和隔离
实例保留。run1608 的最终 dump 为 505035 字节，SHA-256
`2D14D8C6563D8F4836EDB3394A6C4E85B1034283AA3F26E9E57CF6A2EDC06CD2`；provision、迁移、
dump 和认证停机日志均保留，MySQL 监听已释放。run1608 使用 `.local/it-keys` 的隔离开发
签名配置，不代表现场身份或正式密钥。

## 判定

run1608 只证明该非空隔离迁移候选可执行，不能替代 Android、真实 HTTP、Chrome、Python、
原模拟器和恢复全链。run1607 的同批失败不删除、不重写，待有新诊断依据再入队。本报告不计
D06/D07 L2、D17、E06 L2、D18 L3 或总目标完成。

## 验证与保全

- 运行前输入、源码 HEAD、端口、进程和磁盘快照保存在各运行目录及
  `.local/goal-20261018-resume/e07-unified-run1604-before/`、`e07-unified-run1605-before/`、
  `e07-unified-run1606-before/`。
- 运行器与迁移命令的退出码在 `*.exit` 文件中保留；失败 XML、Gradle 输出、数据库 dump、
  隔离密钥引用、认证停机回执及环境快照均不清理。
