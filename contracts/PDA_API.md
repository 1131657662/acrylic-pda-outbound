# 设备身份、资料准备与正式订单首轮提交

版本0.7.0，2026-09-17。以下接口已实现；完整字段见[OpenAPI](openapi.yaml)。当前完成服务端设备登记、人员/设备认证、订单领取和不可变资料准备包，正式Android App已接下载校验、Room原子激活、下载确认和B模式本地扫码。已接正式订单首轮冻结、上传、短装审批、出库记账和回执验签。召回续作、临时单、更正、授权续期、增量同步和完整上线验收仍待完成。

## 设备登记与身份

管理员通过Web会话、CSRF、`device:manage`和相应仓库权限调用`POST /api/v1/devices?warehouseId=...`。写操作要求`Idempotency-Key`和`X-Server-Recovery-Epoch`。正文为`deviceCode`、`name`、`publicKey`；公钥是P-256的X509 SubjectPublicKeyInfo DER标准Base64。代码和公钥摘要全局唯一，设备身份与仓库绑定，当前不提供换密钥或迁仓入口。重装或密钥丢失需登记新身份，不能继承旧机占用。

设备私钥由正式App的Android Keystore生成及保存；服务器只登记公钥。人员登录令牌使用独立Keystore AES-GCM密钥加密保存，禁用备份和设备转移；不通过接口、日志或备份导出私钥。

`GET /devices`按仓库分页；`POST /devices/{id}/status`正文为`expectedVersion`、`status`（ACTIVE/DISABLED）、`reason`。成功增加设备版本，撤销已有登录会话；重新启用也不会恢复旧令牌。重复请求用原键、原正文取回原操作回执。设备停用不改订单占用、历史离线凭据或本地记录；离线设备不会即时得知后台停用。

## PDA登录与刷新

1. `POST /api/v1/pda/auth/challenges`提交`deviceId`、`purpose`、`bindingSha256`。LOGIN绑定实际登录的username UTF-8字节的SHA-256小写十六进制；REFRESH绑定refreshToken，均不得自行改写大小写或增加空白。
2. 返回`challengeId`、`signedText`、`expiresAt`。设备对原样signedText的UTF-8字节执行`SHA256withECDSA`，将ASN.1 DER签名编码为标准Base64。挑战有效120秒，成功消费后不能重放。
3. `POST /pda/auth/login`提交`username`、`password`、`deviceId`、`challengeId`、`signature`。服务器同时验证密码、人员启用状态、`pda:login`、设备仓库授权、设备启用状态和私钥证明。
4. 得到随机`accessToken`和`refreshToken`。后续PDA接口使用`Authorization: Bearer <accessToken>`；数据库仅保存SHA-256摘要。access最长15分钟，refresh绝对期限为首次登录起30天。
5. access到期后，取得新的REFRESH挑战，再向`POST /pda/auth/refresh`提交`deviceId`、`challengeId`、`refreshToken`、`signature`。成功原子轮换两个令牌，旧令牌失效，30天上限不延长。刷新响应丢失时可重新登录，再查询活动占用；刷新接口不承诺回放已轮换的明文令牌。

signedText共有六行，分隔符为LF（`\n`），末行后没有额外换行：

```text
acrylic-pda-proof-v1
<challengeId>
<deviceId>
<LOGIN或REFRESH>
<随机nonce>
<bindingSha256>
```

每次PDA请求实时检查人员/设备状态、授权版本、登录权限及仓库范围。`GET /pda/devices/me`返回当前设备；Web Cookie不能代替PDA Bearer，PDA Bearer也不能进入Web管理接口。认证路径按来源IP限制每15分钟300次请求（含挑战、登录及刷新），超限返回429及`Retry-After: 900`。

登录和刷新响应另含字符串`userAuthVersion`、`deviceVersion`。它们必须与原离线凭据一致；权限变更后重新登录不能让旧凭据恢复有效。

`POST /pda/auth/logout`只撤销当前登录会话，**不释放订单，也不改写离线凭据有效期**。App即使离线退出也清除本机登录凭据，保留订单资料及领取意图；离线退出无法即时通知服务端撤销会话，重新作业须登录。

## 领取与恢复

`GET /pda/orders`列出当前设备仓库的READY订单，要求`pda:assign`，支持`cursor`、`limit`。列表不是承诺，领取时重新核对。

`POST /pda/orders/{id}/assignments`要求`pda:assign`、`pda:scan`、原幂等键和当前服务器恢复代次。正文：

```json
{"expectedVersion":"2","areaNote":"东侧第一堆"}
```

`expectedVersion`来自列表rowVersion，不能用计划revision替代。设备和仓库由已认证身份决定，不采信客户端指定身份。areaNote可空，仅供现场核对，默认分堆模式不建立具体条码预留。

事务采用READ COMMITTED，按服务器恢复代次共享锁→仓库→设备→订单锁执行，等待设备锁后再次检查会话/权限。检查READY、版本、发布代次、当前库存和映射基线后，原子写入：

- `order_assignment`与唯一活动占用（每单最多一个ACTIVE/RECALL_PENDING），授权代次递增。
- `offline_authorization`及原始签名凭据，签发起固定48小时。
- 订单ASSIGNED、rowVersion递增、幂等记录、ORDER_ASSIGNED审计及同步outbox。

任一步失败全部回滚；签名密钥不可用返回503，订单不占用。已成功领取后，即使响应丢失、下载未完成、登录到期、退出、设备停用或离线凭据到期，**占用持续存在**。这些条件都不创建放锁、续期或转派窗口。

结果未知时先保留原键和原正文。同人员、同设备重新登录后重试可取回同一assignmentId、offlineAuthorizationId和原签名，不延长48小时。换键直接重领返回ORDER_ASSIGNED，同键更换正文或设备返回IDEMPOTENCY_CONTENT_MISMATCH。

`GET /pda/assignments`按本机返回活动占用（`beforeId`、`limit`），包括已过期但未解除的占用。原人员可以恢复原凭据；同机其他人员只能看到占用信息，`offlineToken`为null，不能代用前一人的凭据。另一设备查不到该占用的签名。`GET /orders/{id}/assignment?warehouseId=...`是Web摘要接口，要求`order:read`，从不返回离线token。

`downloadAckAt`在收到有效本地激活确认前为null。领取成功不表示已下载完成，下载确认也不是出库回执。已领取订单不能走未领取的修订、发布、作废入口，召回与换机需要后续受控流程。

## 固定下载包与原子激活

1. `POST /pda/assignments/{id}/preparation`携带当前恢复代次头，需要原人员、原设备、`pda:assign`和`pda:scan`。服务端检查ACTIVE、48小时期限、凭据撤销与权限版本，再在事务内持久化唯一准备包。重复调用返回原包，不需要另设幂等键、不重取状态、不续期。
2. 回执的`manifest`是原始JSON字符串，先核对其UTF-8 SHA-256，再使用公钥验证`signedManifest`。JWT固定RS256，`aud=acrylic-pda-preparation`，绑定清单SHA、授权ID和原到期时间。Manifest结构见[preparation-schemas.yaml](preparation-schemas.yaml)。
3. `GET /pda/assignments/{id}/content/{part}?bundleId=...`下载`plan`、`inventory`、`availability`三个gzip文件。响应为`application/gzip`原始字节及`Cache-Control: no-store`，不是HTTP自动解压后的JSON。每次下载重新验证人员/设备及凭据。原文每份上限16MiB；App也限制压缩大小16MiB。
4. `plan`是原发布快照文本；`inventory`为原快照按ASCII条码排序的`ParsedBale`数组，二者必须匹配离线凭据摘要。`availability`为准备时冻结的`baleId/barcode/dispatchCycle/available`数组，**没有增量同步游标，不代表持续最新状态或条码预留**。
5. App先写私有暂存文件，核对压缩/解压摘要、大小、行数、签名上下文、条码/ID唯一性、整数克和原重量、出库周期、产品计划及政策。全部通过才在单一Room事务写完整订单及库存、待确认状态。失败不显示就绪，不覆盖旧完整版本。
6. 本机激活后发送`POST /pda/assignments/{id}/download-ack`，携带恢复代次头，正文如下。服务端核对原清单及身份，返回`assignmentId/bundleId/downloadAckAt`。同包重试返回原确认时间，审计只记录一次。

```json
{
  "bundleId": "<准备包UUID>",
  "manifestSha256": "<原清单SHA-256>",
  "assignmentEpoch": "1",
  "inventorySnapshotId": "<固定库存UUID>",
  "activatedAt": "2026-09-17T04:10:55Z"
}
```

`activatedAt`由服务器时间锚点和设备单调时钟计算；不得早于授权签发或超过服务端当前时间300秒。App同时核对设备启动次数及墙钟漂移；重启或改时后保留资料并要求联网校时，不自行延长离线期限。

下载中断可以重新下载原包；确认响应丢失后本机保留完整版本与待确认记录，重启后可点“重试下载确认”。领取结果未知保留原请求键和正文，原人员重新登录后可恢复原请求或本机活动授权。所有这些状态均保留服务端独占占用。

## 离线凭据与服务端密钥

`GET /pda/signing-key`返回`keyId`、`algorithm=RS256`、RSA公钥DER Base64。服务端从配置路径加载PKCS8私钥和X509公钥，校验密钥配对及至少2048位RSA；不会自动生成或在启动时替换正式密钥。

JWT头固定`alg=RS256`、`typ=JWT`、`kid`。声明绑定iss/acrylic-outbound、aud/acrylic-pda-offline、jti、sub/人员、deviceId、warehouseId、assignmentId、orderId、revision、assignmentEpoch、serverRecoveryEpoch、inventorySnapshotId、policyVersion、autoDeductInventory、planSha256、inventorySha256、startOrderScanSeq、allocationVersion、userAuthVersion、deviceVersion、scope/pda:scan、iat和exp；`exp-iat=172800`秒。整数版本/序号以字符串传输，iat/exp为整数秒。

planSha256对数据库保存的发布snapshot_json文本UTF-8字节取SHA-256；inventorySha256沿用库存内容摘要。准备接口已提供与两者一致的固定内容。它们与准备清单的manifestSha256分别校验，不是提交正文摘要；客户端不得重序列化后假定原字节相同。

正式App通过HTTPS获取公钥、匹配kid、限定RS256，验证签名、签发者、受众、完整人员/设备/仓库/订单上下文、权限版本及固定48小时。debug仅允许回环开发HTTP。重开页面或操作时重新校验资料，到期则显示阻断原因，既有数据及占用保留；扫码接收、确认和最终保存均复核授权；已到期或时间不可信的原确认保留为待恢复，不写新明细。当前接口不续签，也不把JWT用作在线Bearer。正式密钥轮换及历史验签公钥保留随运维和授权续期流程接入。

## 错误与重试

| 状态/错误码 | 处理 |
| --- | --- |
| 400 INVALID_DEVICE_KEY / INVALID_REQUEST | 修正公钥格式或字段；不得换身份绕过校验 |
| 401 INVALID_CREDENTIALS / INVALID_DEVICE_PROOF / DEVICE_CHALLENGE_EXPIRED | 核对人员与设备，获取新挑战重新认证 |
| 401 AUTH_EXPIRED / DEVICE_DISABLED / UNAUTHENTICATED | 重新认证或联系管理员；保留本地数据及占用 |
| 403 PERMISSION_DENIED | 联系管理员检查人员权限 |
| 404 ORDER_NOT_FOUND / DEVICE_NOT_FOUND | 无此资源或不在授权范围，不泄露其他仓库数据 |
| 409 ORDER_ASSIGNED | 查询本机占用或联系主管，不直接抢占、释放或自动重领 |
| 409 IDEMPOTENCY_CONTENT_MISMATCH | 恢复原键、原正文和原设备；先查询结果 |
| 409 ORDER_VERSION_CONFLICT / ORDER_STATE_CONFLICT / ORDER_BASELINE_CHANGED | 刷新核对；基线变化需后台重新发布 |
| 409 ORDER_RECOVERY_REQUIRED / SERVER_RECOVERY_EPOCH_MISMATCH | 按恢复流程对账，不替换代次强行提交 |
| 409 DEVICE_VERSION_CONFLICT | 刷新设备状态后重新核对 |
| 404 ASSIGNMENT_NOT_FOUND / NOT_FOUND | 非原人员/设备，或下载包/文件不存在；保留本机资料 |
| 409 OFFLINE_AUTH_EXPIRED / OFFLINE_AUTH_REAUTHORIZATION_REQUIRED / ASSIGNMENT_NOT_ACTIVE | 停止资料激活；保留占用并联系主管，重新登录不会续签旧授权 |
| 409 PREPARATION_INCONSISTENT / DOWNLOAD_CONFIRMATION_MISMATCH | 不激活或不确认；核对固定版本，不强行替换摘要 |
| 400 INVALID_ACTIVATION_TIME | 联网核对时间；保留本机版本和待确认记录 |
| 413 PREPARATION_TOO_LARGE | 联系管理员核查资料大小；不切换为不校验下载 |
| 429 LOGIN_RATE_LIMIT | 按Retry-After等待 |
| 503 SIGNING_KEY_UNAVAILABLE / SERVER_RECOVERY_REQUIRED | 等服务恢复；不绕过签名、不放锁 |

服务端验证覆盖签名伪造、账号绑定、挑战重放/过期、刷新轮换、权限撤销、仓库隔离、两机抢单、同键重试、作废/停用竞争、事务回滚、固定包、错误确认及到期保锁。Android主机测试直接消费服务端生成的签名包，验证损坏/截断、签名篡改、激活回滚、持久恢复与时钟异常。独立模拟器与真实测试MySQL已跑通设备登记至下载确认和本地扫码，主机测试验证到期后停止新增保存并保留原意图；没有在M71上执行这版App的现场验收。

## 正式订单首轮冻结与提交

当前仅支持`FORMAL`来源、`workRound="1"`、`continuationId=null`。冻结前核对所有输入终态及汇总；0包、未决输入、未确认失败不能冻结，任一产品短装必须填原因。Room V3新增提交表，单事务保存FROZEN审计、固定正文及摘要，再冻结本地订单。重复操作返回原批次，不能修改正文、继续扫码或撤销。

离线授权到期后允许冻结及上传原已保存事实，服务端逐条验证保存发生在原48小时内；上传仍须当前有效的原人员/原设备在线身份和`pda:submit`权限。凭据撤销、版本变化、恢复代次冲突和库存冲突不能借过期上传绕过。原占用持续保留。

| 方法/路径（统一`/api/v1`前缀） | 行为 |
| --- | --- |
| POST `/pda/orders/{order}/submissions` | UTF-8正文不超过1MiB；完整校验后原子处理 |
| GET `/pda/submissions/{id}` | 原人员/原机查询原批次；成功返回原不可变回执 |
| POST `/pda/submissions/{id}/upload` | 登记固定控制头、`payloadSha256`、字符串`byteCount/chunkCount` |
| POST `/pda/submissions/{id}/chunks/{index}` | `application/octet-stream`；0起始，每块262144字节，末块按清单余数 |
| POST `/pda/submissions/{id}/finalize` | 全块齐备、长度和摘要通过才进入同一处理事务 |
| POST `/pda/submissions/{id}/abort` | 原控制头及1～500字原因；可先于正文，持久ABORTED墓碑和签名证明 |
| GET `/orders/{order}/submission?warehouseId=...` | Web `order:read`；当前状态、有效/撤销明细和短装原因 |
| POST `/submissions/{id}/review` | Web `submission:review`；CSRF及恢复代次头，绑定原摘要和审批决定 |

PDA写接口要求`X-Server-Recovery-Epoch`，提交ID本身是幂等身份，不另要求`Idempotency-Key`。小正文/分块共用控制头和唯一订单槽。固定正文最大32MiB、最多128块；这是当前传输边界，不是116包限制。超出时保留原冻结记录并人工核对，不能拆成新批次规避约束。Web目前一次加载当前提交明细，尚未验收大单分页性能。

正文`schemaVersion=2`，见[submission-evidence.yaml](submission-evidence.yaml)。包含完整原captures/scans/events、原人员设备/授权/快照/周期、短装原因、预期包数/整数克、输入高水位和终态统计；撤销明细和失败序号仍保留。规范摘要按对象键排序、数组原顺序、UTF-8紧凑JSON计算SHA-256；Long计数/序号/重量为十进制字符串，重复JSON键和尾随内容拒绝。PDA在Room中持久保存规范正文，重试只使用原正文。

服务端独立按原快照重算产品与重量、核对原输入/审计，再按包ID锁定当前周期及库存。全局条码防重不受自动扣账开关影响。短装或冲突持久为`REVIEW_REQUIRED`，安全包保留`REVIEW_RESERVED`，不出库、不放行；主管只能批准当前无其他冲突的短装，不能覆盖周期冲突。审批正文为`payloadSha256/decision(APPROVE或REJECT)/reason`，同决定同原因重试返回原结果。退回和取消保留占用，不自动续扫。

成功事务同时写出库事实、可选库存流水、结果版本1、逐产品汇总、订单COMPLETED、授权CLOSED、审计/outbox和签名回执。关闭本次作业不删除出库条码guard；再次提交或成功后的取消均返回原成功回执。

终态回执JWT固定RS256、`aud=acrylic-pda-receipt`，声明`receipt`绑定除`signedReceipt`外全部响应。PDA先验证签名和完整响应，再比原ID/摘要/来源/轮次/代次、总数/重量、逐产品实绩和原序号，全部通过才记`SYNCED`并显示“云端已确认出库”。回执是永久业务证明，不按登录或离线授权期限自动失效。未成功状态不得视作放行凭据。

响应丢失保存`RESULT_UNKNOWN`，先查原ID；404不表示解冻或放锁，只允许继续原提交。用户主动上传后可排唯一WorkManager任务，仅WiFi自动重试，UI与Worker共用串行入口；首次失败30秒加抖动、指数增长至30分钟，尊重Retry-After，连续8次失败后暂停，待审每30分钟核对。401沿用一次刷新；确定性业务拒绝停止自动重试并保留记录。

主要新增错误：`SUBMISSION_USE_CHUNKS`（413，保留正文改分块）、`SUBMISSION_NOT_FOUND`（404，原身份查询）、`SUBMISSION_ABORTED/SUBMISSION_REJECTED`（409，不复活批次）、`SUBMISSION_STILL_CONFLICTED`（409，审批不能覆盖冲突）、`IDEMPOTENCY_CONTENT_MISMATCH`（409，恢复原正文）。任何失败都不能自动生成第二批次、释放占用或删除本地证据。

当前完整保存本单审计，但不推进全设备连续审计确认、不触发清理。续作、换机、临时单、更正净结果、独立审计同步、灾后恢复UUID代次控制及文件生成仍需后续实现。
