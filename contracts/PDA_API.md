# 设备身份、资料准备与正式订单首轮提交

契约版本0.16.0，2026-09-18。以下接口已实现；完整字段见[OpenAPI](openapi.yaml)。当前完成服务端设备登记、人员/设备认证、订单领取和不可变资料准备包，正式Android App已接下载校验、Room原子激活、下载确认和B模式本地扫码。已接正式订单首轮冻结、上传、短装审批、出库记账、回执验签、受控召回、原人原机全量保留续作及受控联网授权续期。主管批准的卸货释放/隔离减包续作已接入；换机、临时单、更正、增量同步和完整上线验收仍待完成。

## 设备登记与身份

Web `/devices` 已接以下接口，侧栏入口要求`device:manage`。页面按当前仓库分页，登记前核对PDA公钥；启停前核对设备并填写原因。设备ID可在“查看身份”复制。完整操作见[设备管理说明](../docs/WEB_DEVICE_GUIDE.md)。

管理员通过Web会话、CSRF、`device:manage`和相应仓库权限调用`POST /api/v1/devices?warehouseId=...`。写操作要求`Idempotency-Key`和`X-Server-Recovery-Epoch`。正文为`deviceCode`、`name`、`publicKey`；公钥是P-256的X509 SubjectPublicKeyInfo DER标准Base64。代码和公钥摘要全局唯一，设备身份与仓库绑定，当前不提供换密钥或迁仓入口。重装或密钥丢失需登记新身份，不能继承旧机占用。

设备私钥由正式App的Android Keystore生成及保存；服务器只登记公钥。人员登录令牌使用独立Keystore AES-GCM密钥加密保存，禁用备份和设备转移；不通过接口、日志或备份导出私钥。

`GET /devices`按仓库分页；`POST /devices/{id}/status`正文为`expectedVersion`、`status`（ACTIVE/DISABLED）、`reason`。成功增加设备版本，撤销已有登录会话；重新启用也不会恢复旧令牌。重复请求用原键、原正文取回原操作回执。设备停用不改订单占用、历史离线凭据或本地记录；离线设备不会即时得知后台停用。

Web设备写请求在发送前按人员/仓库保存到当前标签页sessionStorage，包含原正文、键和恢复代次；刷新后由用户恢复，不自动发送，不存登录令牌或私钥。重试始终携带原恢复代次，不能用最新GET或CSRF响应的代次替换。校验操作回执的状态、版本及启停目标设备后才清除原请求；响应未知、恢复请求失败或回执不匹配继续保留。首次明确4xx失败可返回修改输入，旧版本冲突须关闭窗口并刷新设备列表后重新核对。关闭整个标签页的恢复不作保证。

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

正式App通过HTTPS获取公钥、匹配kid、限定RS256，验证签名、签发者、受众、完整人员/设备/仓库/订单上下文、权限版本及固定48小时。debug仅允许回环开发HTTP。重开页面或操作时重新校验资料，到期则显示阻断原因，既有数据及占用保留；扫码接收、确认和最终保存均复核授权；已到期或时间不可信的原确认保留为待恢复，不写新明细。续期通过独立联网接口办理，不把JWT用作在线Bearer。正式密钥轮换及历史验签公钥保留随运维和授权续期流程接入。

### 受控联网授权续期（0.16.0）

`POST /pda/assignments/{id}/renewal`要求当前有效的PDA在线会话、`pda:assign`及`pda:scan`，并携带原人员、原设备、原订单、原授权ID、授权代次、订单版本和本机未决输入数量。服务器重新锁定作业与订单，校验人员/设备授权版本、恢复代次、订单仍为`ASSIGNED`、作业仍为`ACTIVE`且不存在提交事实；召回、冻结/上传、已终结、版本变化或`pendingInputCount>0`均拒绝并保留占用。

续期写入同一作业下新的授权历史行，新的JWT从服务器当前时间起固定48小时，绑定原作业、订单、仓库、设备、人员、快照、计划、恢复代次和前一授权ID。旧授权的`issued_at`、`expires_at`和签名正文不改写，只记录替代关系并撤销其继续使用资格；原订单占用、扫码事实、审批、审计及本机历史保留。响应丢失使用同一`Idempotency-Key`重试，返回首次签发的相同新授权；换键或修改正文返回幂等冲突，不能生成第二份续期。

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

首轮使用`FORMAL`来源、`workRound="1"`、`continuationId=null`；主管批准后的新轮次见下文“受控续作与第二轮提交”。冻结前核对所有输入终态及汇总；0包、未决输入、未确认失败不能冻结，任一产品短装必须填原因。Room V3新增提交表，单事务保存FROZEN审计、固定正文及摘要，再冻结本地订单。重复操作返回原批次，不能修改正文、继续扫码或撤销。

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

当前完整保存本单审计，但不推进全设备连续审计确认、不触发清理。原人原机全量保留续作见下节；换机、临时单、更正净结果、独立审计同步、灾后恢复UUID代次控制及文件生成仍需后续实现。


## 受控召回与原机停止（0.4.0）

Web订单详情可申请原机召回，须 `order:recall`、仓库权限、当前 `expectedVersion`、原因及原 `Idempotency-Key`。申请将订单/原授权置为 `RECALL_PENDING`。与首轮提交串行：提交先完成不能召回；召回先到的旧提交最多待复核，审批不能出库。PDA进入作业或首页联网刷新时查询召回；断网不能即时收到新指令。

| 接口 | 身份与用途 |
| --- | --- |
| `GET/POST /orders/{order}/change-requests?warehouseId=...` | Web查询/申请；写入要求CSRF、恢复代次、幂等键 |
| `GET /pda/assignments/{id}/change-request` | 原人员原设备，`pda:recover`，返回 `{request:null或召回}` |
| `POST /pda/recovery-packages` | 原人员原设备上传固定恢复正文，32MiB上限；恢复代次必填 |
| `GET /recovery-packages/{id}` | Web仓库内查看本单摘要、有效/VOID明细与输入统计 |
| `POST /pda/change-requests/{id}/stop-ack` | 原人员原设备，绑定已接收包ID/SHA、高水位和审计覆盖终点 |
| `POST /change-requests/{id}/resolve?warehouseId=...` | Web主管 `order:recall` + `order:cancel`，只支持 `CANCEL_EMPTY` |

PDA收到召回后先暂停接收，再在Room持久停扫。操作员查看未保存和失败列表、明确核对后，未保存输入转带原因的 `BUSINESS_REJECTED`，失败确认另记审计；原ID、原扫码序号、原人原机和确认意图保留。已保存/VOID行不改。待落盘队列归零，冻结恢复包与 `RECALL_STOPPED` 同事务保存；重启、上传失败、登录过期均不重新扫码。

恢复正文采用独立 `recoveryPackageId`、规范UTF-8/SHA256及 `schemaVersion=2`，允许有效包为0，不能用作出库提交。原 `captures/scans/events` 与全部本设备连续 `auditEvents` 一同提交；`auditCoverage` 当前仅原设备一项，`afterSeq:"0"`，`throughSeq` 等于停止事件序号。末事件 `RECALL_STOPPED` 的 `detail` 绑定本恢复包ID。服务端验证原授权保存窗口、快照、汇总、连续性和已提交/已恢复审计；此前已提交的输入/扫描/事件不得遗漏或改写。详见 [recall-schemas.yaml](recall-schemas.yaml)。本轮不推进通用审计清理游标，也不清理本机记录。

恢复上传仅接收证据并保留安全包的 `RECOVERY_RESERVED`；不会覆盖其他单占用或直接出库。恢复证据固定后，不再接收该授权新旧提交正文。停止确认必须再引用原 `recoveryPackageId/payloadSha256/captureHighWatermark/auditThroughSeq`；单事务撤销原离线资格、保存签名停止证明和审计/outbox，订单保持 `RECALL_PENDING`。旧离线资格到期/撤销不阻止原证据接收，但有效在线人员/设备身份仍必需；停用设备不能绕过认证。

停止/作废证明固定RS256、`aud=acrylic-pda-recall`，声明 `receipt` 绑定除 `signedReceipt` 外的完整响应，含原订单/授权/恢复包/摘要/高水位。PDA只有验签及绑定核对通过才置 `RECALLED`；丢失响应重试同一原包及原停止确认，返回首次证明。`mayResumeScanning=false`、`stockReleased=false` 始终不变，停止证明不是放行或续作凭据。

主管零包作废需原机STOPPED、完整原包摘要、当前版本、有效包0且订单无尚待交接或出库包占用，填写 `physicalEvidence`（1–1000字）并二次确认。持久保存核验人/时间/说明；未成功且未终结提交原子置ABORTED并签名，原槽和证据保留。有包、缺证据或已出库均拒绝。主要错误：`RECALL_HAS_LOADED_BALES`、`RECALL_STOP_REQUIRED`、`RECOVERY_ALREADY_FIXED`、`RECALL_STATE_CONFLICT`（409）；异文重试 `IDEMPOTENCY_CONTENT_MISMATCH`，证据错误 `INVALID_SUBMISSION`（400）。所有错误都不自动放锁、改派或续扫。

召回停止证明本身不提供续作资格，须另走下节主管批准及原机激活流程。独立凭据减包续作见0.6.0节；当前不提供换机、故障原机替代证明、临时单、更正净结果、恢复UUID代次及文件生成。尚未准备完成或原本机作业/审计损坏时保留待召回，不能伪造零包证明。


## 受控续作与第二轮提交（0.5.0）

1. 原 S1 为 REJECTED/ABORTED；原机完整恢复并取得 STOPPED 证明后，Web POST `/orders/{order}/change-requests`，`type=REOPEN_WORK`。必填旧批次、`expectedWorkRound` 字符串、`expectedRevision` 整数、恢复包、原设备、原因和完整 RETAIN 条码/周期集合。
2. Web POST `/change-requests/{continuationId}/resolve`，`action=REOPEN_WORK`，提交当前订单版本、`retainedManifestSha256` 及实物核验凭据。另需 `order:resume`。锁定来源/订单及库存后，旧 current 清空并记录 superseded 关系；轮次加一，原占用直接变为 CONTINUATION_RESERVED。
3. 原人原机 POST `/pda/continuations/{id}/activate`，提交原恢复正文摘要、保留集合摘要、完整输入高水位加1及计划摘要。保留集合摘要为恢复包 `scans` 中 ACTIVE 行保持原顺序的规范 JSON 数组 SHA-256。返回 RS256、`aud=acrylic-pda-continuation` 的签名票据和固定48小时新资格；到期/停用/版本撤销不放锁，激活和重试不延长期限。
4. PDA 验签和核验本机全部原 captures/scans/events、统计及高水位后，在同一 Room 事务归档旧 current、保存新资格、恢复 SCANNING。原 PreparedOrder 和各轮提交/恢复正文只读保留。稳定本地容器 ID 不替代事实来源：新记录的 wire assignmentId 使用新授权，历史来源不变。
5. S2 使用新 submissionId、workRound、continuationId、顶层新 assignmentId。首轮可缺/空的 continuationId 在后续轮次必填，分块控制头也必须携带。正文前缀必须逐条包含原恢复包 captures/scans/events，所有原行完全一致；新增记录使用新授权，输入序号继续递增。保留包不允许普通 VOID。
6. 分块控制头只固定当前轮次唯一在途身份；完整正文通过后才选择唯一 current 并把保留包原子转为 S2 的 REVIEW_RESERVED。任何保留集合、周期、库存或资格冲突回滚；旧批准/旧上传迟到不复活 S1。短装仍需批准，只有验签成功的 SUCCEEDED 回执可放行。

查询旧授权的 change-request 可取得 `previousSubmission` 与 `continuation` 状态。第二轮及以后再次召回仍需当前原授权的完整恢复证据。当前服务恢复代次继续沿用数字字符串，UUID恢复代次与灾后对账另行实现。

主要409：`WORK_ROUND_CONFLICT`、`CURRENT_SUBMISSION_EXISTS`、`SUBMISSION_SUPERSEDED`、`RETAINED_SET_MISMATCH`、`CONTINUATION_NOT_APPROVED`、`CONTINUATION_NOT_ACTIVATED`、`CONTINUATION_RECONCILIATION_REQUIRED`、`DISPATCH_CYCLE_CONFLICT`。遇到这些错误保留原正文、原键和实物，查询原票据或主管核对，不删记录或盲换ID。

## 卸货释放与隔离后续作（0.6.0 / 契约0.10.0）

主管先POST `/recovery-packages/{id}/disposition-evidence?warehouseId=...`，需order:recall及order:dispose、原恢复代次、Idempotency-Key和CSRF。正文为expectedVersion、payloadSha256和减少行dispositions，每行captureId、expectedDispatchCycle、RELEASE/QUARANTINE、physicalEvidence。返回不可改写的approvedEvidenceId；GET同路径查询。批准不释放库存。

REOPEN_WORK清单覆盖恢复包全部当前有效装载行；减少行必须引用已批准凭据，已批准减少的行不能RETAIN。激活签名携带累计dispositions，绑定captureId、baleId、barcode、expectedDispatchCycle、action、approvedEvidenceId。retainedManifestSha256为原scans中ACTIVE且不在累计处置中的行按原顺序规范化后的SHA256。旧全量保留协议可不带dispositions；新的减少票据不能由旧App误接。

新提交和再次恢复完整继承captures/scans/events及累计dispositions。expectedCount、expectedWeightGrams、captureSummary.saved只计算未被处置排除的ACTIVE行。完整替换正文接收事务同时选择新current、释放RELEASE、建立QUARANTINED占用、转移保留包并核验新增包；任意冲突整笔回滚。独立凭据审批及激活均不释放；shortShipment与0包规则不变。stockReleased=false继续表示不提供通用解锁授权，不能据此推断已批准逐包处置未执行。

新增409：DISPOSITION_EVIDENCE_MISMATCH、DISPOSITION_EVIDENCE_REQUIRED、DISPOSITION_ALREADY_APPROVED、DISPOSITION_STOCK_CONFLICT。隔离占用不能由库存导入、恢复或新订单覆盖；解除隔离另行处理。零包作废只检查本车待装/出库占用，已完成交接的隔离仍永久保留；尚待替换批次的减少包继续阻止作废。
