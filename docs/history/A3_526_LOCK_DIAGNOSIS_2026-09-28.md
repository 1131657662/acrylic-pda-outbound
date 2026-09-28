# A3 attempt 526 并发失败定性（2026-09-28）

> 用户后续决定：按“疑似产品缺陷，尚未确证”挂[A3-526待专项](A3_526_LOCK_PENDING_SCOPE_2026-09-28.md)，不再阻断其他A3类。以下保留当时取证及停报结论，不能引用“判定b”代替专项确证。

## 1. 结论与授权边界

**判定 b：产品条码发布查询在本机真实 MySQL 上出现与 SKIP LOCKED 预期不符的锁等待，按产品侧问题停报。** 不是“15秒太短”的证据，也不是已证明的鉴权锁反序。不能用延长超时、改变隔离级别或删除“跳过未提交变更”断言将其改绿。未修改产品、测试、超时、隔离级别、契约或配置；A3第2–4批未启动，R09未启动。

本次只完成产品查询行为层的定性：SQL执行计划/索引访问与MySQL引擎的具体成因尚未确定，**不宣称已查明引擎bug或生产死锁**。测试写者直接执行库存DML和BarcodeChanges，没有走真实业务入口的完整身份锁前缀；真实入口下的可达性、发生频率和生产影响仍未验证。修复/进一步产品侧评估属于R05同步与R06共享锁边界，需单独授权。

输入主树D:/acrylic-pda-outbound，codex/development，43f813a；单助手。用户授权先采集锁证据，测试侧问题才允许修测试，产品侧问题立即停报；限定两轮。本次进行了两次原样重现，没有第三次执行。源码版本事实见[看板生成块](../ORCHESTRATION_STATUS.md)。

## 2. 两次原样重现及采集限制

执行前跨34棵工作树核对527–529无已用attempt目录；实际只用527、528。两次均使用tools/e2a-batch-it.ps1、主树、-NonE2a，每次全新独立MySQL，跑同一个BarcodeSyncConcurrencyIT类，源码哈希保持。证据等级为真实MySQL事务+Spring/MockMvc，非外部HTTP。

| attempt | tests/failures/errors/skipped | 目标用例耗时 | 结果与采集状态 |
| --- | --- | --- | --- |
| 527 | 4/0/0/0 | 0.702秒 | 原测试全绿；本机采集器因Windows路径分隔符比较错误提前退出，**没有锁证据**，不能覆盖526红 |
| 528 | 4/1/0/0 | 15.690秒 | 原目标用例再次commit timeout；其他3项绿；取得43份连续锁快照、INNODB_TRX、InnoDB状态和Java线程栈 |

采集器只读隔离端口19330的performance_schema/information_schema，先核对datadir；未接系统3306、未授额外生产权限。修正527采集器的路径归一化后启动528。528的43份快照成功保存；检测到等待消失后，追加采集的SHOW GLOBAL STATUS语句因PowerShell传参引号丢失报SQL语法错误，采集器退出。**没有成功保存完整的解除后InnoDB状态或全程死锁计数**，不以工具退出成功冒充完整采集。该错误发生在锁证据保存之后，不是IT失败原因。

526历史红、527采集失败、528 IT红及采集收尾失败全部保留；没有改测试掩盖。528第四个用例主动注入read failure并期望500，本身通过，不另算产品bug。

## 3. 实际锁等待链

528在本机13:45:07.679至13:45:22.297之间的43次采样，均为同一条数据库等待边。

| 角色 | 事务 / 连接 / P_S线程 | 状态与锁 |
| --- | --- | --- |
| 写者（后台测试线程） | trx4664 / connection60 / thread97 | READ COMMITTED；RUNNING，连接Sleep，无等待SQL；已修改2行，持inventory_position和barcode_sync_pending主键记录的X,REC_NOT_GAP锁 |
| 读者（MockMvc请求线程） | trx4665 / connection62 / thread99 | READ COMMITTED；LOCK WAIT；已持warehouse、app_user、device、pda_session、user_management_guard及barcode_sync_stream锁；等待pending主键记录X,REC_NOT_GAP |

等待对象：`acrylic_e2a_cross_20260925_528.barcode_sync_pending`，`PRIMARY`，主键三元组如下（均为本次合成fixture ID）：

- recovery_epoch：1
- warehouse_id：01a0e68b-a5c0-78ef-af04-b1a76ca0a4a7
- bale_id：01a0e68b-a6e0-70cb-9430-49c98d8f4aea

读者已取得warehouse_id二级索引记录锁，等待同一条pending记录的聚簇主键锁；这只是实际持锁集合，**未采集EXPLAIN，不把它写成完整执行计划或引擎根因**。warehouse主键锁为GRANTED，排除上轮“卡在鉴权仓库锁”的候选。

```mermaid
flowchart LR
    R[读事务4665 / 连接62] -->|等待pending主键X锁| W[写事务4664 / 连接60]
    W -->|Java提交屏障，最多15秒| M[测试主线程]
    M -->|同步调用尚未返回| R
```

数据库内只有读者→写者单向边；反向边在Java测试屏障中，不在InnoDB锁图内。43次采样均无数据库环，采集到的SHOW ENGINE INNODB STATUS没有LATEST DETECTED DEADLOCK段。**当次没有数据库死锁证据**，但因缺完整解除后状态/计数，不外推其他时段“绝无死锁”。

## 4. 等待SQL、线程栈与超时

INNODB_TRX直接记录读事务等待的语句：

```sql
SELECT BIN_TO_UUID(bale_id),row_version FROM barcode_sync_pending
WHERE recovery_epoch='1'
  AND warehouse_id=x'01a0e68ba5c078efaf04b1a76ca0a4a7'
  AND pending=1
ORDER BY bale_id LIMIT 200 FOR UPDATE SKIP LOCKED
```

Java测试进程PID17128的线程栈与数据库证据一致：

- 请求线程：[BarcodeSyncConcurrencyIT:41](../../server/src/test/java/com/acrylic/outbound/inventory/BarcodeSyncConcurrencyIT.java) → MockMvc → PdaBarcodeSyncController → [BarcodeSyncService:27](../../server/src/main/java/com/acrylic/outbound/sync/BarcodeSyncService.java) → [BarcodeStreamStore.publish:19](../../server/src/main/java/com/acrylic/outbound/sync/BarcodeStreamStore.java) → JDBC/socket读。
- 后台pool-3-thread-1：[BarcodeSyncConcurrencyIT:36](../../server/src/test/java/com/acrylic/outbound/inventory/BarcodeSyncConcurrencyIT.java)，TIMED_WAITING于CountDownLatch.await；此前change已更新库存并调用[BarcodeChanges](../../server/src/main/java/com/acrylic/outbound/sync/BarcodeChanges.java)。
- 15秒后后台主动抛`IllegalStateException: commit timeout`并回滚，读请求返回空events/highWatermark=2；主线程在第42行pending.get取得该异常。“提交后发布”断言未抵达。目标用例总耗时15.690秒。

因此不是MySQL lock_wait_timeout错误，也不是当次永久阻塞：等待在写者超时回滚后解除。延长屏障只会延后这个环的解除，不能证明SKIP LOCKED路径正确。旧测试的屏障用于保持写事务未提交，以检验产品查询的跳锁语义；此次栈已抵达该查询，不能通过让写者提前提交来代替原断言。

本次排除了“尚未进入SKIP LOCKED就卡在鉴权”的旧猜测。产品声明使用的跳锁查询在该真实环境中实际等待，是保留产品侧阻断的证据；527一次绿不构成稳定正确性的证明。查询为何在不同新库间表现不同，留待独立产品/数据库兼容性评估。

## 5. 证据保全与后续

- `.local/a3-526-diagnosis-20260928/`：分配查重、源码哈希、两次console、528安全响应摘要、旧树前后状态；`capture-528/`内43个sample-*.jsonl、threads-17128.txt及innodb-during.txt。`evidence-hashes.json`记录文件摘要。原XML/日志可能含临时测试凭据，只留本机gitignored目录。
- 原XML：[527批次目录](../../.local/it-batches/batch-20260928-134243-start527/REPORT.md)、[528批次目录](../../.local/it-batches/batch-20260928-134433-start528/REPORT.md)；对应隔离库、日志和storage均保留。采集脚本未提交，不算交付的通用诊断工具。
- 测试/采集均结束，19321/19330/19434/23308无监听，系统3306未动。旧树D:/acrylic-pda-worktrees/r06-e2a-migration的97条未提交/未跟踪记录前后差异0。
- 原A3剩余55类没有新增执行；其中8类R08已有专用入口验收，按用户要求不重复，普通/专项待跑47类另列。两次重现不增加已覆盖的不同类数，也不使A3-BC收口。
- 未验：触发条件/执行计划、引擎版本差异、真实业务写入口的锁前缀组合、外部HTTP、现场吞吐、修复及复验。没有修改生产逻辑、测试断言或任何生产开关。

下一步建议单独授权评估BarcodeStreamStore发布查询在该环境下的跳锁行为，优先固定触发条件并比较访问路径，再决定是否调整SQL/索引或运行环境；此建议不构成实施授权。交回用户决定A3后续或其他方向，R09 C2实施未启动。
