# 交接上下文与原回执查询

版本事实见[看板](../../ORCHESTRATION_STATUS.md)，实际验证见[验收](../../history/R07_HANDOVER_READS_2026-10-01.md)。入口仅在 `outbound.handover.commands.enabled=true` 时注册，缺省关闭。本页描述新增Web查询面，不是正式OpenAPI发布或完整设备交接实现。

路径前缀 `/api/v1/handovers`，使用既有Web会话和实时身份/权限校验。仓库UUID参数 `warehouseId` 必填；当前用户须具有 `order:handover` 或 `order:resume`，并具有该仓库访问权，沿用既有交接命令的权限语义。响应是JSON原对象，带 `Cache-Control: no-store` 和 `X-Content-Type-Options: nosniff`。

| GET路径 | 返回及范围 |
| --- | --- |
| `/scopes/{scopeId}/context?warehouseId=...` | 同仓来源事实 `context`、REQUEST的 `expected`、全false的 `capabilities` |
| `/{caseId}/context?warehouseId=...&commandType=APPROVE` | 同仓案件四维状态与绑定事实、该动作的expected、验签并绑定案件/来源/原申请人的原REQUEST正文 `originalCommand` |
| `/{caseId}/context?warehouseId=...&commandType=PREPARE` | 相同读取约束，提供PREPARE上下文；不生成准备票据 |
| `/{caseId}/context?warehouseId=...&commandType=ACTIVATE` | 相同读取约束，提供ACTIVATE上下文；不分配新资格或改变活动轮次 |
| `/{caseId}/commands/{commandId}?warehouseId=...&commandType=...` | 当前仍有权限的原动作人、同仓、同案件和同命令类型，返回精确原POST响应字节；四种既有命令均适用 |

案件上下文包含 `stopState`、`integrityState`、`originalResultState`、`physicalState`，数字上下文与空值沿用现有命令模型。不返回完整证据包、逐包清单下载、独立阻断清单、动作开放保证或新签名。GET得到expected后POST仍须重新校验；GET不是预约，不宣称四维状态完整就必可执行。

原回执查询核验规范原字节、命令/响应摘要、RS256签名及issuer/audience、receipt/key/type/case/actor/event/expected/time/status/warehouse绑定和全false能力位。请求中存在原设备绑定时仍须原设备；本批Web路由不新增PDA别名，设备组合未验。历史公钥沿既有OfflineSigner配置；缺可用配置不补签或临时生成密钥。

不存在、跨仓、跨案件、非原动作人、类型不匹配均404；未登录401、无交接权限403；已定位原件坏件409 `HANDOVER_RECEIPT_INTEGRITY_FAILURE`；签名配置不可用沿既有503。**404不能被解释为原命令未发生或可换键提交**。保留原键与原正文，按受控核对恢复。

查询使用只读一致快照，不调用命令服务或签名写入，不推送消息、不修改原件/库存/占用；会话框架仍可能更新登录会话，不将业务表不变夸大为数据库完全无写入。GET原回执不修复首次同键并发811，也不消费缺status的旧outbox。生产开关、续扫/放行/库存释放及localDeletionAllowed保持既有关闭边界。
