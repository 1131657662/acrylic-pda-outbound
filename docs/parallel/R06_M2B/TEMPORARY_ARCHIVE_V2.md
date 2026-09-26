# TEMP 成功归档原件契约 V2（内部实现）

2026-09-25。仅覆盖首轮、原映射、真实 NORMAL_CONFIRM 已提交的 TEMP 成功。生产 `POST /pda/submissions/{id}/archive-confirmation` 继续明确拒绝 TEMP；内部服务/隔离测试适配器不构成该路由开放。FORMAL archiveVersion1 不变。

## 请求与原键

请求只有两个小写 SHA-256 字段：`payloadSha256` 为原冻结正文规范 UTF-8 字节 hash，`receiptSha256` 为完整原成功回执（含 signedReceipt）的规范 UTF-8 字节 hash。服务端以原 `(submissionId,recoveryEpoch)` 保存唯一证明；不同请求摘要不能替换旧证明。Android在联网前保存原请求及从原资格/冻结正文/已验成功回执得到的上下文。

服务器要求原PDA人员、设备、仓及当前有效会话的 `pda:submit` 权限，沿既有恢复屏障和原代次限制。历史资格单纯到期不取消既成成功；当前签发归档仍不能越过恢复屏障。无跨代次自动补证，R09须另行闭合。

## 签名证明

签名字段 `signedArchive`；JWT `iss=acrylic-outbound`、`aud=acrylic-pda-archive`、claim `archive` 为去掉 signedArchive 的完整证明。RS256；历史回放核原 key，不重签。字段如下：

| 字段 | 约束 |
| --- | --- |
| archiveVersion | JSON整数2 |
| submissionId/orderId | 原S及已成功正式化目标UUID |
| assignmentId | 必须null，不伪装FORMAL assignment |
| source | 原 `{kind:TEMP,creatorDeviceId,clientOrderId}` |
| authorizationId | 原临时资格UUID |
| warehouseId/operatorId/freezeDeviceId | 原仓、原操作员、原冻结设备；不是当前请求者替代原身份 |
| workRound/mappingVersion | 当前实现均十进制字符串 `1` |
| sourceRecoveryEpoch/recoveryEpoch | 原来源代次和本归档代次，当前实现两者相等 |
| planSha256 | 原冻结计划摘要 |
| payloadSha256/payloadBytes | 原冻结正文文件SHA-256与十进制字节数 |
| receiptSha256/receiptBytes | 完整原成功回执规范文件SHA-256与十进制字节数 |
| commandReceiptId | 唯一真实 NORMAL_CONFIRM 的回执UUID |
| commandResponseSha256/commandBytes | 确认命令完整原响应字节SHA-256与十进制字节数；不是命令领域摘要，也不是重序列化结果 |
| completedAt | 原成功回执时间 |
| archivedAt | 不早于completedAt的UTC归档时间 |
| retainUntil | 不早于archivedAt加5个日历年的UTC时间 |
| storagePolicy | SERVER_DURABLE_FILES_V1 |
| localDeletionAllowed | 恒false |

## 服务端证据闭包

- 原正文文件必须存在、hash一致，重新确认文件耐久；原控制头与正文来源一致。
- 唯一 NORMAL_CONFIRM 命令须通过既有 `CommandReceiptIntegrity`：原请求/回执签名、历史身份/映射、决定事件、审计、短装引用（如有）、实际结果快照和原业务回执一致。合成 INTERNAL_TEST_ONLY、只有 SHORT_APPROVE 或直接改状态不能生成证明。
- 原正文、成功回执规范字节、确认命令原字节三份文件按内容hash保存并确认耐久。初次失败可留下无业务授权的孤立文件，重试可复用；已有证明后任何文件缺失均拒绝，不静默重建或重签。
- 独立RC事务拥有最外层提交边界；拒绝加入未提交的调用者事务。锁/当前身份保护沿既有 `SubmissionAccess` 和 `SubmissionStore.lockExisting`；签名/插入失败回滚证明行，业务成功和账务不重做。
- V27 `submission_archive` 已能保存三文件引用的签名正文，服务端迁移保持V36。

## Android 原件耐久

Room23分别保存原请求、每份原HTTP响应（S/epoch/hash）和首次接受的证明。RAW先独立提交再验签；无效、冲突和丢回执重试原件保留。重新核验当前本机原冻结/资格/已验成功回执后才接受；原请求/上下文/成功原件变化则拒绝。

严格UTF-8/JSON和JWT验证，响应与签名完全一致；拒绝重复字段、尾随内容、错误字段类型、未知字段、错来源/摘要/大小/代次/留存期限或能力变化。历史读取重新核签，不仅检查VERIFIED标签。

归档消费者不更新原提交/draft/取消意图/guard，不提供删除DAO，不改变90天保护或任何放行/续扫能力。完整生产接线、历史真实keyring、设备/断电、跨代次恢复及R09新归档引用闭包仍需对应证据；以当前看板和当次验收记录为准。
