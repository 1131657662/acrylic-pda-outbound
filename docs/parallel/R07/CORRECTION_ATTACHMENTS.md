# R07 受控更正附件接口

本接口是显式开关下的开发能力；版本事实见[看板生成事实块](../../ORCHESTRATION_STATUS.md)。实现与验证见[附件验收](../../history/R07_CORRECTION_ATTACHMENTS_2026-10-01.md)。未改正式 OpenAPI 发布契约，不代表生产开放。

## 启用与权限

必须同时显式设置 `outbound.corrections.commands.enabled=true` 和 `outbound.corrections.attachments.enabled=true`。两者缺省均关闭。仅开启 commands 时沿用既有 CREATE 元数据行为；不声称该模式有真实附件保障。附件开关开启后，所有新 CREATE 都经受控附件消费器；已提交旧单据及原键回执不被追补或改写。

Web 使用既有登录会话与 CSRF；PDA 路径使用既有 PDA 鉴权链。下面所有路径有 `/api/v1/pda/corrections/attachments` 别名，但本批只实测 Web。`warehouseId` 必填且服从当前身份仓库范围。

| 方法与路径 | 输入 | 结果/权限 |
| --- | --- | --- |
| `PUT /api/v1/corrections/attachments/{uploadKey}?warehouseId={uuid}` | UUID 原键；`Content-Type: application/octet-stream`；`X-Server-Recovery-Epoch`；`X-Content-SHA256`（小写64位）；正文为原始字节 | 需 `outbound:correct`。成功200，标准 envelope 的 data 为 `{attachmentId,evidenceSha256,sizeBytes}`；sizeBytes 为十进制字符串 |
| `GET /api/v1/corrections/attachments/{attachmentId}?warehouseId={uuid}` | 附件UUID | 需 `outbound:correct`，只读当前上传人/仓库的原件 |
| `GET /api/v1/corrections/attachments/{attachmentId}/review?warehouseId={uuid}` | 附件UUID | 需 `correction:approve`，只读已绑定同仓库、同上传人申请单据明细的原件；他人未提交草稿不开放 |

上传实际读取最多10MiB+1字节，拒绝空件和超限，服务端独立计算SHA；不信 Content-Length。原键在上传人/仓库内唯一，同字节重试返回原ID，异字节409 `IDEMPOTENCY_CONTENT_MISMATCH`。同一服务实例最多4个同时进入的上传，满时429 `ATTACHMENT_UPLOAD_BUSY`；这不是跨实例频率/总配额控制。客户端保留原键，遵循调用端退避策略；不自动重签或补造附件。

下载固定 `application/octet-stream`、`Content-Disposition: attachment`、UUID `.bin` 文件名、`Cache-Control: no-store`、`X-Content-Type-Options: nosniff`。不接收路径或解释客户端文件类型。实际字节及元数据存在同一数据库事务中，没有文件/数据库双写窗口。

## CREATE 引用

已有 CREATE 路径和外层命令结构不变。明细中 `attachments` 的每个元素必须包含上传响应 data 中的三个字段：

```json
{
  "attachmentId": "服务器生成的UUID",
  "evidenceSha256": "原件的小写SHA-256",
  "sizeBytes": "实际字节数"
}
```

服务器重读受控原件，重新核对实际长度/SHA，并要求上传人与申请人相同、仓库相同。每条最多5件、每命令实际引用总量最多50MiB；同条不接受重复ID，同原件在不同明细/后续命令的每次引用都计容量。未提供附件的合法非实物更正仍按原业务规则处理。实物行原本要求的 evidenceId/evidenceSha256 必须指向该行附件清单中的**同一项**；仅声明摘要/大小不能绕过。

成功 CREATE 的文档、明细、签名回执、审计和附件引用由同一外事务提交。任何失败都回滚，不消耗原键；已成功命令重放原回执且不增加引用。附件原件与引用禁止 UPDATE/DELETE；不提供自动清理或本地删除能力。

错误分类：坏结构/声明不符为400 `INVALID_CORRECTION_ATTACHMENT`；实际上传摘要不符为400 `ATTACHMENT_HASH_MISMATCH`；容量超限为400 `ATTACHMENT_LIMIT_EXCEEDED`；缺失/非所属原件为404 `CORRECTION_ATTACHMENT_NOT_FOUND`；实物引用未闭合为400 `CORRECTION_EVIDENCE_REQUIRED`。既有身份、仓库、恢复epoch及CSRF守卫继续生效。

本增量不包含抬头附件、前端页面、交接/故障证据上传、附件内容真实性/实物合法性审核、跨实例总配额、90天/5年留存等待或删除任务。上传成功不构成主管批准、恢复放行或删除资格；`localDeletionAllowed=false`。
