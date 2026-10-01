# 更正上下文与原回执查询

版本事实见[看板](../../ORCHESTRATION_STATUS.md)，实际验证见[验收](../../history/R07_CORRECTION_READS_2026-10-01.md)。所有入口仅在 `outbound.corrections.commands.enabled=true` 时存在，缺省关闭；本接口文档不代表正式 OpenAPI 发布。

Web路径前缀 `/api/v1/corrections`，PDA别名 `/api/v1/pda/corrections`；后者使用既有设备鉴权链，本批未做Bearer设备组合实测。仓库UUID查询参数 `warehouseId` 必填。返回 JSON 原对象、`no-store` 和 `nosniff`。

| GET路径 | 权限与返回 |
| --- | --- |
| `/orders/{orderId}/context?warehouseId=...` | `outbound:correct` 且同仓，返回 `context`、CREATE的 `expected`、全false的 `capabilities` |
| `/{documentId}/context?warehouseId=...&commandType=APPROVE` | 同仓 `correction:approve`；返回文档事实、APPROVE的expected、已验签原CREATE正文 `originalCommand` 和全false能力 |
| `/{documentId}/context?warehouseId=...&commandType=WITHDRAW` | 同仓主管查询撤销上下文；是否原审批人、状态等执行条件仍由POST重验 |
| `/{documentId}/context?warehouseId=...&commandType=WITHDRAW_CONFIRM` | 同仓原申请人及 `outbound:correct`；返回申请原文与确认上下文，其他建档人不可读取 |
| `/commands/{commandId}?warehouseId=...&commandType=...` | 原请求人、同仓、同命令类型及对应当前权限；返回数据库里**精确原响应字节**。原请求有设备绑定时还要求原设备；重新登录不会要求旧session仍存活 |

原键查询覆盖既有命令类型。APPROVE/WITHDRAW需审批权限，其余沿用更正权限；不得借GET触发审批、重新签名或POST重试。命令或身份不匹配返回404；缺权限403；已存在原件不可核验返回409 `CORRECTION_RECEIPT_INTEGRITY_FAILURE`。**404不证明原命令失败或可换新键**：客户端保留原请求与原键，等待受控核对。

读取前核原命令/响应SHA、规范编码、回执行ID/原键/人/单/订单/事件/时间/状态/expected、命令仓库与签名主体、全false能力位，以及RS256签名及issuer/audience。文档上下文额外核原CREATE摘要/申请人/订单/仓库与文档一致。历史公钥可配置验证旧原件；当前 OfflineSigner 公钥接口仍要求当前签名配置就绪，缺钥返回503，不临时生成密钥或重签原件。

GET采用只读一致快照，但它不是预约/锁定命令。把返回的expected原样带入后续POST，POST仍自行重验；过期上下文需重新核对。`originalCommand`包含申请人的原始声明，实际冻结行/完整账务预览、净结果/列表/分页查询尚未由本增量提供。接口不授库存释放、续扫或删除能力。
