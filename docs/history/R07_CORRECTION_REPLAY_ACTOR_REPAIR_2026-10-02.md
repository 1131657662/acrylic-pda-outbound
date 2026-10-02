# R07 更正原键 actor 回放验收（2026-10-02）

产品提交`880808e`已推送`codex/development`。作者、自审及隔离消费者均由本助手执行；现场与生产未涉及。

## 变更

`CorrectionCommandService` 在原键回放前绑定存储的 `actor_id` 与当前身份；同仓库但不同身份得到统一资源不存在结果。命令原始规范化字节必须相同，回执通过数据库字段、签名、规范化字节和审计完整性校验后才返回。首次写入的事务边界、生产命令开关默认值与业务效果未放宽。

## 验证

| 证据 | 结果 |
| --- | --- |
| `correction-replay/compile-1/result.json` | `server:testClasses` 成功，新增真实 HTTP IT 编译通过 |
| `correction-replay/offline-2/result.json` | 2956 项，0 失败、0 错误、1 既有跳过；源码哈希回读通过 |
| `.local/e2a-cross-20260925-841/result.json` | `E2aCorrectionReplayOwnershipIT` 1 项，0 失败/错误/跳过；真实隔离 MySQL、Spring HTTP、合成隔离身份 |
| `replay-ownership-evidence.json` | CREATE/APPROVE/WITHDRAW/WITHDRAW_CONFIRM：原 actor 原字节回放；同仓库其他 actor 404；改正文原 actor 409；前后状态相同 |

841 数据库、dump、binlog、XML、日志和认证停机记录保留；未删除 `.local`，未接原库或现网。

## 限制

该验证覆盖服务端真实 HTTP 和隔离 MySQL，不等同真机、现场、多 JVM/OS 死亡、断电或生产组合验收。原 actor 保护以服务端 principal 为边界；生产命令 surface 仍由配置开关关闭。
