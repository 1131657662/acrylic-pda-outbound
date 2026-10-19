# E07/D06-D07 撤回重发入口审计（2026-10-18）

本审计对应剩余计划 A2.1，输入树为 `codex/development`、HEAD
`93710ec5cff979a567654084c80abe089e9a73b7`。它只核对可复用入口和缺口，不把服务端隔离候选拼成完整 D06/D07，也不恢复 R07 已取消的换机承接、多跳或接收者确认/激活链。

## 入口与范围

| 路径 | 已有事实 | 仍缺/直接验证 |
| --- | --- | --- |
| `server/src/main/java/com/acrylic/outbound/recall/RecallController.java` | `POST /orders/{order}/change-requests` 将 `FAULT_WITHDRAWAL` 路由到故障撤回；普通 `RECALL` 和 resolve 保持独立 | 需在当次真实 HTTP 上覆盖默认关闭、无权/跨仓、成功终态和竞争；不能只测 MockMvc |
| `server/src/main/java/com/acrylic/outbound/recall/RecallFaultWithdrawals.java` | 默认 `outbound.recall.fault-withdrawal-enabled=false`；有权仓库人员、恢复代次、订单版本、原活动领取和幂等键均受检；事务内生成撤回回执 | 需 Android/真实 Web 入口消费回执，验证丢响应/重启/末 SQL 失败及旧正文异文拒绝 |
| `server/src/main/java/com/acrylic/outbound/recall/RecallAccess.java` | 写操作要求 `order:recall`、仓库和身份写锁；设备端恢复权限与领取者/设备匹配 | 需真实会话权限矩阵和撤权/锁等待后撤权；不能用合成身份冒充现场权限 |
| `server/src/main/java/com/acrylic/outbound/recall/RecallFaultState.java` | 仅已领取未成功、无 dispatch/来源冲突订单可撤回；原 submission 终态保护；撤回后 assignment/offline authorization 失效并写审计 | 需确认跨仓/成功/已有来源证据/重复撤回的真实 HTTP/MySQL 事实，以及占用和账务全表守恒 |
| `server/src/main/java/com/acrylic/outbound/recall/RecallFaultStock.java` | 有已收包占用时要求 `order:dispose`、逐包实物凭据、锁定 `REVIEW_RESERVED` 占用并写 `fault_withdrawal_stock`；失败回滚 | 需正向卸货/释放、无权、周期/来源冲突、并发和末 SQL 回滚的真实组合；不能把“全部拒绝”当正向 |
| `server/src/main/java/com/acrylic/outbound/recall/RecallFaultReset.java` | 保留原 revision，创建新 revision，清当前 submission/slot 并递增 work round，为重发布准备 DRAFT | 需实际后台重新发布、新设备从头领取→下载→扫码→提交→成功回执，验证旧资格/迟到上传不生效 |
| `server/src/main/java/com/acrylic/outbound/orders/OrderReleaseService.java`、`OrderPublicationStore.java` | 发布受 `order:publish`、版本、策略和库存预检保护；发布历史/文档任务/outbox append-only | 需 Web 实际发布入口和重复发布/并发领取/丢响应/最终 SQL 失败验证 |
| `server/src/main/java/com/acrylic/outbound/pda/AssignmentService.java`、`PdaOrdersController.java` | 新设备领取检查 READY、当前库存基线、恢复代次和新 assignment；重复领取拒绝 | 需 Android 产品入口消费新 assignment，不迁移坏机进度；原 PDA/原身份与现场设备另列 |
| `web/src/orders/OrderFaultWithdrawal.vue` | 订单详情的异常页按 `order:recall` 和状态显示撤回；持久化原键/正文/恢复代次，结果未知时重试原请求；已收包额外要求实物核验 | 需 Chrome 当次真实场景、权限/身份切换、存储失败和服务器 4xx/5xx 错误恢复；既有 Chrome 策略阻断不能绕过 |
| `web/src/orders/OrderRecall.vue`、`OrderDetailPage.vue` | 普通停止/零包作废和故障撤回入口分开；撤回完成后文案要求后台核对草稿并重新发布 | 需确认撤回组件与发布组件的实际返回、错误和刷新行为；不新增旧换机页面验收 |
| `server/src/main/resources/db/migration/V83__fault_order_withdrawal.sql`、`V84__fault_withdrawal_restart_facts.sql` | 迁移保存故障撤回状态、凭据、释放记录及新 revision 的 operation revision 约束 | 需非空迁移组合、全表守恒、数据库恢复/回退证据；迁移事实以看板事实块为准 |

## 可复用与边界

- run725 `E2aFaultWithdrawalRecoveryIT` 已覆盖一次真实隔离 HTTP/MySQL、旧资格拒绝、幂等、竞争、五个恢复截点和认证停机；它没有 Android、Chrome、外部 Python、原模拟器、原身份或现场实物证据，因此只可按路径和输入 SHA 复用为服务端候选。
- run728–732 是另一条 Android/恢复前缀失败序列，不能作为 D06/D07 的修复证据，也不应重启。730–732 同一审计前缀缺口已按三次规则暂缓；该暂缓不阻塞撤回重发入口的独立 Web/Android 组合设计。
- R07 生产开关、七类决定能力、`localDeletionAllowed=false`、原库/现网和正式签名边界均保持关闭。合成身份、隔离数据库、Debug 签名和测试 fixture 只能证明开发输入行为。

## 下一验证单元

新建全新隔离编号，先验证 `FAULT_WITHDRAWAL` Web/HTTP/MySQL 与非空迁移，再接新设备 Android 从头作业；覆盖正向、权限、成功终态、旧资格、同键/异文幂等、竞争、重启/丢响应、占用/末 SQL 回滚。Chrome 若仍被既有策略阻断只登记缺口，不改端口或包装绕过。未完成上述组合前，D06、D07、D10、D14、D17、E06 L2、D18 L3 和第8节闸门均保持未完成。
