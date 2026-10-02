# R1 交接写后回滚验收（2026-10-02）

## 范围与实现

本项验证 `HANDOVER_ACTIVATE` 在出站通知、回执和审计已写入后发生异常时，事务是否回滚，以及同一原键是否能在提交、进程上下文重建后返回同一原始回执。测试只使用隔离 MySQL、合成 FORMAL 夹具和测试身份，不声称现场设备、worker、断电或原库证据。

故障注入保持应用的 `AuditWriter.append` SQL 写入，只在 `HANDOVER_ACTIVATE` 审计行之后抛出测试异常。为遵守 Primary 冻结白名单，测试配置使用 BeanPostProcessor 替换应用的同名 writer 实例；没有新增测试 `@Primary`，也没有修改生产配置、迁移或安全默认值。

## 验证结果

- 静态冻结守卫与 typed 快照单测：13 项通过（3 项 Primary/legacy 守卫，10 项快照编码与反例）。
- 842：Spring 组装失败，原因是故障替身作为 `AuditWriter` Bean 造成注入歧义；无业务写入验收，`integration-result.xml`、日志和隔离目录保留。
- 843：真实隔离 MySQL、迁移后服务上下文和故障注入链 1/1/0/0 通过。审计 INSERT 后观察到目标状态、outbox、回执、占用、轮次均已写入；异常后 16 张表快照与基线逐字相等；移除故障后原键提交、同键重放和重建上下文重放均返回同一原始字节，非空占用保持。认证停库，身份回执、原命令、原回执、快照和 JUnit XML 保留。
- 844：静态 BeanPostProcessor 工厂修正后的同链复验 1/1/0/0 通过，启动日志不再出现提前初始化警告；认证停库并保留完整证据。
- 完整服务端单测回归：148 个 JUnit XML 套件、2956 项，0 失败、0 错误、1 个既有 skip。

## 证据索引

- `.local/e2a-cross-20260925-842/`：失败组装证据，未生成完成标记。
- `.local/e2a-cross-20260925-843/`：通过轮次，含 `integration-result.xml`、前后/中间快照、原键命令与回执、认证身份和停库回执。
- `.local/e2a-cross-20260925-844/`：修正后通过轮次，含同类完整证据。
- `server/build/test-results/test/`：本次完整回归 XML；未清理任何失败或历史证据。

## 边界

本项证明隔离服务事务与原键恢复，不替代现场身份、PDA/Keystore、worker 消费、独立 JVM/OS 死亡、断电和长时运行验证。生产默认开关、`localDeletionAllowed=false`、原库/现网、main、发布和部署边界保持。
