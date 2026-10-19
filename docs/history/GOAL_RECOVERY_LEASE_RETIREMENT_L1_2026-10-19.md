# 恢复租约退休与效果门当前源码 L1（2026-10-19）

本切片验证当前工作树中恢复租约退休与效果门的文件级 fail-closed 行为。它只使用 JUnit 临时目录，不连接 MySQL、不启动 HTTP/模拟器、不恢复已取消的换机承接或接收确认链。

## 输入与范围

- 工作树 `D:/acrylic-pda-outbound`，分支 `codex/development`，输入 HEAD `f02c5a03530558770ffbd4f833a7fa26982e5904`。
- 直接受影响路径及输入 SHA 记录在 `.local/goal-20261019-resume/recovery-lease-retirement-run01/input.json`；该文件 SHA-256 为 `5683133B0AC0945DD4CA1795C441DCA96F6CC08BAF67A6E3C0D4D55370E3349F`。
- 验证范围：`RecoveryLeaseRetirementTest` 的文件租约退休、外部 owner 拒绝、协调器忙重试、最后操作退休、索引替换失败和中断窗口；`RecoveryEffectGateTest` 的协调器忙传播及非忙 authority 失败时本地围栏。

## 命令与结果

```text
JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=D:/acrylic-pda-outbound/.local/goal-20261019-resume/recovery-lease-retirement-run01/java-temp -Duser.timezone=UTC
.\gradlew.bat -p server test --tests com.acrylic.outbound.recovery.RecoveryLeaseRetirementTest --tests com.acrylic.outbound.recovery.RecoveryEffectGateTest --no-daemon --offline --console=plain
```

Gradle 退出码为 0，日志 SHA-256 为 `F3C90063CD877FC6C8A87F5E06904336764D393DA5D81397C8DD3D36C06A9360`，结果回执 SHA-256 为 `B1AD19E81C880DE9FF60DC0A8A5334FDD50B741EAB48C38768012D216711B7CD`。

| 测试类 | tests | failures | errors | skipped | XML SHA-256 |
| --- | ---: | ---: | ---: | ---: | --- |
| `RecoveryEffectGateTest` | 2 | 0 | 0 | 0 | `F3FB10A0EDA01D49D29C857D55878CFB73CC938C81284C9C5924C362313A8C97` |
| `RecoveryLeaseRetirementTest` | 8 | 0 | 0 | 0 | `F438D1C7859435E1E6ABAA4F4773D32774B550EC1B911ADEFD92301870ABD86A` |

## 自审与边界

测试只创建 `@TempDir` 文件树，未写业务数据库、迁移、Room、HTTP 或生产存储。协调器忙时不会把本地 authority 错误升级为围栏；非忙 authority 错误会保留本地阻断；外部 authority 不能退休其他 owner 的记录；中断的 rename/index 阶段保留 `.retiring` 与索引阻断标记，直到明确退休。测试证明的是当前文件租约/效果门实现，不是可信恢复 Authority、真实 MySQL/HTTP 或跨进程业务恢复验收。

运行后仅系统 MySQL80 进程与 3306/33060 监听仍在；项目 Java/Gradle、隔离服务、模拟器和任务端口均为空。证据根 `.local/goal-20261019-resume/recovery-lease-retirement-run01/` 保留命令、输入、日志、JUnit XML、退出码及进程/监听回执。`localDeletionAllowed=false`、生产决定、正式签名、部署、Release、`main` 和原库/现网边界不变。

本切片是 R09/A0.5 的当前源码 L1 开发证据，不计 R09 整体、D13/D14/D18、最终候选或总目标第8节完成。下一步仍需从未完成且不依赖取消链的受影响项选择新的隔离输入，并按 L2/L3 标准补真实 HTTP/MySQL、Android、Chrome、Python、迁移和最终候选组合。
