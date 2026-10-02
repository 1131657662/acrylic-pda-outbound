# R07 历史更正 outbox 同步兼容（2026-10-02）

## 问题与修复

历史 `CORRECTION_EFFECTIVE`、`CORRECTION_WITHDRAWN` 和 `CYCLE_REOPENED` outbox 载荷创建时没有 `status` 字段。同步消费者对这类非显式事件只读取 `payload.status`，因此会把已经持久化的合法历史消息当成不支持的状态，阻断同一批次的发布。

R07 更正设计要求原订单保持 `COMPLETED`。`SyncOrderStatus` 现在只对这三个已知更正事件在 `status` 缺失时回退为 `COMPLETED`；显式空值、非文本、未知状态、损坏 JSON 和其他未知事件仍拒绝。新更正写入继续携带服务端读取的订单状态，不回写历史消息。

## 验证

| 验证 | 结果 | 证据 |
| --- | --- | --- |
| `SyncOrderStatusTest` | 20/0/0/0 | `server/build/test-results/test/TEST-com.acrylic.outbound.sync.SyncOrderStatusTest.xml` |
| 真实隔离 MySQL/HTTP `E2aHandoverSyncHttpIT` | run 849：1/0/0/0；新消息、旧交接消息、缺 `status` 的历史更正消息均发布一次；游标重放响应相同；投影最终为 `COMPLETED`/版本 4 | `.local/e2a-cross-20260925-849/`，含原始 HTTP 响应、回放响应、投影、完成标记和认证停机结果 |
| 完整服务端回归 | 148 套件、2964 项、0 失败、0 错误、1 个既有 skip | `server/build/test-results/test/` |

## 失败与限制

run 848 的首轮真实 HTTP 夹具在插入事件时漏写 `payload` 占位符，测试未进入消费者断言；失败库、日志和未完成标记均保留。修正夹具后使用新库 run 849 重跑，不复用失败库。

这批只证明一个历史更正事件在真实同步链中的兼容和三个事件类型的转换器边界；没有把历史表头撤回恢复语义、占用回退、历史无引用 `REVERSAL` 或生产现场身份写成已完成。没有迁移、生产默认值、原库/现网、部署或设备变化。

## 自主决策

依据 R07 设计中“更正后原订单保持 `COMPLETED`”的规则，对已知更正事件采用缺字段兼容；没有扩大为所有缺 `status` 的同步消息默认完成。生成端与读取端同时保留，避免只修新消息而留下已持久化旧消息无法消费的问题。

## 提交

产品/测试修复已提交并推送：`b87433d`（`fix(sync): 兼容历史更正出站消息`）。
