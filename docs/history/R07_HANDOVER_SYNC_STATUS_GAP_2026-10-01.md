# R07 交接消息与同步消费者不兼容：静态停报（2026-10-01）

## 发现与影响

输入 `c1ef8707bb50ac8477d3bf56033bd5339c6447d6`。核对 [A2.3](../REMAINING_DEVELOPMENT_PLAN.md)“出站消息与交接命令同事务/可恢复，有反例”时，发现消息写入成功不等于订单同步可消费。

- `server/src/main/java/com/acrylic/outbound/handover/HandoverActivation.java` 的 `Activate.execute` 写入 `HANDOVER_ACTIVATED`，payload 包含案件、订单、轮次、新资格、目标设备/人员和时间，**没有 status 字段**。
- `server/src/main/java/com/acrylic/outbound/sync/SyncOrderStatus.java` 只给 ORDER_PUBLISHED/ORDER_ASSIGNED/ORDER_CANCELLED 显式映射；其余类型读取 payload.status。缺字段得到空字符串，随后抛 `IllegalStateException`。
- `SyncStreamStore.publish` 在读取待发布行时调用该转换器；`SyncService.orders` 每次新基线/增量都先发布待办。因此该行进入所选批次时，按源码会阻断整个同步事务，并继续保留未发布行，重试相同输入不能自行消除不兼容。

这是确定的生产代码契约不一致，**本轮只有源码证据，没有新增 HTTP/同步消费反例实跑**。不推断现场发生过故障，不声称所有仓库均受影响。旧751 outbox写入、807回滚与原键恢复结果在其原范围仍成立，均没有覆盖消费者。

## 处理与边界

按有效自主目标“发现真实产品缺陷→报告、跳过依赖项”，暂停 outbox 消费/恢复收口，不修改已验产品或历史迁移，不以新增测试替换消息来刷绿。应另行修正既有消息消费兼容性并同时验证已落盘旧消息、基线/增量、重试及回滚；仅给未来消息补字段不能证明旧行可恢复。

首次同键交接并发是独立原命令服务路径，允许另行验证。所有默认开关保持 false，localDeletionAllowed=false；不部署、不接原库/现网、不合main、不打标签。证据仅为上述输入的源码，可由Git回读；作者/自审同一助手。
