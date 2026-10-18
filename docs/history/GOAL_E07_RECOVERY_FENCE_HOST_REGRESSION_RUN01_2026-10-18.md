# E07 恢复围栏主机回归 run01

本批验证当前工作树中恢复围栏与 SQL 选择器的定向主机单测，确认继承的 E07/D13 WIP 没有主机层回归。未启动 HTTP、MySQL、Android、Chrome、Python 或原模拟器。

## 范围与验证

| 路径 | 影响层 | 直接验证 | 复用/边界 |
| --- | --- | --- | --- |
| `server/src/main/java/com/acrylic/outbound/recovery/RecoveryAuthorityLeaseChannel.java` | 跨进程 lease 读取/发布 | `RecoveryLeaseRetirementTest` | 只覆盖主机内存与文件边界，不证明真实进程部署 |
| `server/src/main/java/com/acrylic/outbound/recovery/RecoveryEffectGate.java` | 效果门默认关闭与准入拒绝 | `RecoveryEffectGateTest` | 不开放任何生产开关 |
| `server/src/main/java/com/acrylic/outbound/recovery/RecoveryFenceAuthority.java` | 退役、围栏和授权状态 | `RecoveryLeaseRetirementTest` | 不替代真实 HTTP/MySQL 事务 |
| `server/src/main/java/com/acrylic/outbound/recovery/RecoverySelectSql.java` | 恢复查询 SQL 形状 | `RecoverySelectSqlTest` | 不替代非空迁移或数据库方言组合 |

命令：

```text
gradlew.bat -p server test --tests com.acrylic.outbound.recovery.RecoveryLeaseRetirementTest --tests com.acrylic.outbound.recovery.RecoverySelectSqlTest --tests com.acrylic.outbound.recovery.RecoveryEffectGateTest --no-daemon --console=plain
```

结果：Gradle exit `0`，`BUILD SUCCESSFUL`；原始日志位于 `.local/goal-20261018-resume/server-host-run01/test.log`，SHA-256 `9D77800497DDE366901E2C9E3D6A3B8603079FD933EE2C1915DB5C6F1444A8AF`。

## 判定

这是作者同一会话的主机回归证据，不计 E07/D13 整体、E06 L2、D18 L3、第 8 节闸门或总目标。真实隔离 MySQL/HTTP、Android 客户端、Chrome、Python 消费、非空迁移、原模拟器和现场身份仍须在统一候选上执行。
