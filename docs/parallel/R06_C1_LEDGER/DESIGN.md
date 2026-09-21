# C1 原命令台账与不可变回执方案

日期：2026-09-21。只读源码基线 `c3009b691d9d2c0afc32dad6e8f2293d36742a3f`，分支 `codex/r06-c1-ledger`。本批仅设计文档，未修改业务源码、Flyway、API、签名器、Android 或数据库；下文表名、字段和协议常量均是待实施审查的具体提案，不是生产协议已冻结的声明。

依据：[M2b](../../R06_M2B_IMPLEMENTATION_PLAN.md)、[命令与证据](../R06_M2B/COMMAND_EVIDENCE_CONTRACT.md)、[C0](../R06_COMMAND_VECTORS/README.md)。事务与恢复见 [TRANSACTIONS](TRANSACTIONS.md)，升级和验收见 [MIGRATION_ACCEPTANCE](MIGRATION_ACCEPTANCE.md)，实际交付见 [HANDOFF](HANDOFF.md)。普通有权上传者正常直接确认目标保留，`NORMAL_CONFIRM.reason=""` 合法；本批不增加审批人、必填理由或任何决定/关联/放行能力。

## 1. 基线核对与方案选择

| 当前源码事实 | 对 C1 的约束 |
| --- | --- |
| [IdempotencyRequests](../../../server/src/main/java/com/acrylic/outbound/common/IdempotencyRequests.java) 的 `find/record` 只存摘要与资源 UUID；[V3](../../../server/src/main/resources/db/migration/V3__inventory_preflight.sql) 唯一键为 principal/route/key | 复用唯一身份，不把这张表当作原响应库；不存在行的读不是唯一性裁决 |
| [JsonText](../../../server/src/main/java/com/acrylic/outbound/common/JsonText.java) 按注入 ObjectMapper 编码并摘要；[SubmissionJson](../../../server/src/main/java/com/acrylic/outbound/submissions/SubmissionJson.java) 另有严格解析、键排序和安全整数约束 | 新命令明确采用后一规范，不能直接调用前者 digest 就声称符合 C0 |
| [OrderReceipts](../../../server/src/main/java/com/acrylic/outbound/orders/OrderReceipts.java) / [DeviceReceipts](../../../server/src/main/java/com/acrylic/outbound/devices/DeviceReceipts.java) 从历史列构造 DTO；[PcReceipts](../../../server/src/main/java/com/acrylic/outbound/pc/PcReceipts.java) 保存 body_json 后再解析 | 它们是现有功能，不批量改造；C1 必须另外保留已输出的精确字节，不能由当前资源或 JSON 列重建 |
| [SubmissionReceipts](../../../server/src/main/java/com/acrylic/outbound/submissions/SubmissionReceipts.java) 的成功/取消用 `acrylic-pda-receipt`；[OfflineSigner](../../../server/src/main/java/com/acrylic/outbound/pda/OfflineSigner.java) 生成 RS256 JWT | 可以复用 signer 封装，命令 audience 与成功凭证分开；签名证明内容完整性，不证明服务器取了正确业务事实 |
| [TemporarySubmissionIntake](../../../server/src/main/java/com/acrylic/outbound/temporary/TemporarySubmissionIntake.java) 始终进入 REVIEW_REQUIRED | 新台账不直接移除该保护，不把保存命令等同于确认出库 |
| [E1 原回执读取](../../../server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceProof.java) 返回 originalStoredReceiptUtf8 并核验已存 token，只认识当前公钥 | C1 不重签旧 ABORT；历史密钥环仍需实施，未知 key 为不可核验，不回退为信任 |

选择“既有幂等索引 + 新 TEMP 不可变回执表 + 单独决定版本行”。它保留全局唯一键的现有语义，不给旧设备/订单/PC 回执换协议。单独建第二套 actor/key 索引会引入两个权威；直接扩展所有旧回执会扩大兼容面，均不作为本批实现路径。

## 2. 身份、字节与摘要域

唯一身份固定为 `(authenticated principalId, registered routeScope, originalKey)`，不包含 sessionId、deviceId、warehouseId、commandType、当前代次或当前目标。它们均在正文或服务器原上下文绑定；同一人把同键改仓、换命令类型或换目标必须冲突。不同人使用相同 UUID 不共享回执，也不能仅凭已知 commandId 查询他人记录。

提案 scope 为 `r06.command.v1`；七类命令和所有 Web/PDA/重试别名映射到同一 scope。查询只做精确相等，不能使用 `LIKE 'r06.%'` 后取首条。originalKey 固定规范小写 UUID，等于 `commandId`；兼容公共 helper 的 16 至 80 ASCII 范围，但本协议自己执行更窄校验。客户端不能提交 actor、permissionSnapshot 或 currentEffect。路径/header 与正文中的 S、warehouse、key、代次必须一致，否则在新记录前拒绝。

`commandId` 是客户端原键，不是全局数据库主键；不同principal相同commandId合法。服务器独立生成 `receipt.id` 和既有 `api_idempotency_request.id`，后者的resource_id指向前者；receiptId可放进签名回执用于引用，但不能替代actor/scope/key鉴权查询。不能把客户端commandId直接写作receipt表PK，也不加全局commandId唯一索引。

令 `C(x)` 为 C0 指定的规范 UTF-8，`H(bytes)` 为小写 SHA-256。对象键按 Java UTF-16 排序、数组原序、无空白/尾换行；不做 Unicode 归一化或 trim，null/缺失不同，版本/轮次/代次是无前导零十进制字符串。严格拒绝重复键、尾随 JSON、浮点和非安全整数；控制字符转义必须消费完整 token，继续使用 C0 修正后的 Java 对照向量。

| C1 推荐域，尚待共同协议批确认 | 精确输入 |
| --- | --- |
| `commandSha256` | `H(concat(UTF8("ACRYLIC-R06/COMMAND/1\n"), C(command)))` |
| `unsignedReceiptSha256` | `H(concat(UTF8("ACRYLIC-R06/RECEIPT/1\n"), C(unsignedReceipt)))`，不把摘要自身或 signedReceipt 放入输入 |
| `derivedControlSha256` | `H(concat(UTF8("ACRYLIC-R06/DERIVED-CONTROL/1\n"), C(derivedControl)))`，明确为后来派生，不冒充 V33 当时已签的控制摘要 |
| `originalResponseSha256` | `H(originalResponseUtf8)`，含原 signedReceipt、原空白/尾换行 |
| 既有 payload/文件摘要 | 保持原 `H(C(payload))` 或文件实际字节摘要，按原对象类型使用，不替换为命令摘要 |

表中concat表示按顺序拼接原字节，`\n`是单个LF字节0A。C0的 `ACRYLIC-R06-C0/...` 和 `*-proposal` 常量、签名夹具原样保留；采纳C1推荐常量时必须新增版本明确的服务器/Android共用向量，不能改名后继续声称原153项已验证新域。未完成该批时不得启用新的route/audience。

`command` 精确覆盖 commandVersion/type/id、warehouse、稳定 source、submissionId/workRound、原 authorization/freezeDevice/sourceRecoveryEpoch、transportRecoveryEpoch、reason，以及 expected 中的 canonicalOrderId/mappingVersion/currentSubmissionId/scopeRound/slotVersion/decisionVersion/receivedPayloadSha256/declaredUploadSha256/planSha256/targetRevision/candidateContextSha256/userAuthVersion。

处置/续作再显式绑定 expected.evidenceSetId、evidenceSetSha256、verificationId、verificationVersion、verificationAlgorithmVersion、dispositionManifestSha256/dispositionManifestVersion、retainedManifestSha256/retainedManifestVersion。前五项对应E2真实集合与追加验证记录；处置清单绑定逐包原cycle/去向/实物凭据，保留清单绑定续作全集。每个族有明确必填/显式null规则，不适用字段用null，不能省略或从header补入。完整清单以不可变对象引用及摘要携带，不能只散列汇总数量。nullable原字段语义沿C0，但新增E2字段须由共同协议批显式升级schema与新向量，不让C0解析器静默接受。

## 3. 持久模型及唯一裁决

下列新表名只是实施位置建议。采用项目 MySQL 8 现有 BINARY(16) UUID、UTC DATETIME(3)、BIGINT 版本、ascii_bin 摘要；不引入 PostgreSQL 专用类型或在线 DDL 语法。原 JSON 字节使用 MEDIUMBLOB，不使用会重新编码的 MySQL JSON。提案命令及原回执各最多 256 KiB，大候选/证据另建不可变清单引用；这是传输资源上限，不是包数或车次上限。

| 表 / 键 | 必须持久的事实与约束 |
| --- | --- |
| 既有 `api_idempotency_request` | 继续唯一 `(principal_id,route_scope,idempotency_key)`；request_sha256 存新命令域摘要，resource_id 指向新 receipt.id；不改旧行/旧 route |
| 新 `temporary_command_receipt` | `id` 主键；`api_request_id` NOT NULL UNIQUE FK 到既有 request.id；command_version、canonical_command_bytes、command_sha256、receipt_version、original_response_bytes、original_response_sha256、unsigned_receipt_sha256、signature_key_id、signed_receipt、recorded_at 全部非空；仅完整提交时插入，无持久 RUNNING 状态 |
| 新回执的关系列 | warehouse_id、scope_id、submission_id、work_round、creator_device_id/client_order_id、temporary_authorization_id、original_operator_id、source_recovery_epoch 均非空；original_canonical_order_id、original_mapping_id、evidence_set_id、evidence_verification_id 按真实场景可空；FK 限制删除，不级联删除原事实 |
| 新 `temporary_decision_state` | scope_id 主键/FK；decision_version 非负 NOT NULL；新决定在持 C 锁后按旧版本条件更新一次，0 行表示冲突。它是可变并发门闩，不是原回执正文 |
| 新 `temporary_decision_event` | id 主键；scope_id + resulting_decision_version 唯一；receipt_id NOT NULL FK；decision_kind/原上下文非空。一次明确操作若组合两项决定，使用一条版本事件及其有类型的决定明细，不让两项分别消耗同一旧版本 |

同一事务先在完整业务锁内预留既有request行，预分配服务端receipt/业务事件ID，完成检查和版本裁决后形成并签名完整回执。回执只用FK引用既有原上下文、证据集/验证与原映射；先插入receipt，再插入以receipt为父的决定、新映射、处置/续作及应用事件。签名可含预分配的新事件ID，关系约束由后写的业务事件指回receipt闭合；不在receipt上再设必须先有新事件的非空反向FK。公共 helper 当前 `record` 不返回request.id，可在同事务按精确唯一键读取，不必修改所有调用者。

现有resource_id是通用引用，没有目标FK；新表的反向FK可验证request存在，而“resource_id必须等于本receipt.id”由窄仓储插入和每次读取的交叉核验保证。新服务禁止仅提交预留行，也禁止签好回执后捕获后续业务写错误并提交；故障注入须证明request/receipt/业务事件总是同时可见或同时回滚。具体FK顺序由共同迁移批按实际schema逐条验证，不能依赖MySQL不支持的延迟外键检查。

同键同规范字节返回原响应；同键异摘要或规范字节不同返回冲突，不吞掉唯一键异常继续执行。不同键同 decisionVersion 由 C 锁、条件版本更新和事件唯一约束共同裁决，只能一个事务改变事实。幂等索引不能代替共享记账、dispatch 周期唯一性或 E2 的共同包互斥。

新行不允许业务 UPDATE/DELETE；更正追加新命令/事件，当前效力只查询派生。此约定不声称可防有数据库管理权的人篡改，读取仍核全部摘要和签名，异常时关闭能力并保留原值待核对。归档/清理不得在90天后删除幂等键造成旧命令可再执行；随业务证据至少5年并保留未终结来源链，具体清理继续由 R09 单列实现。

“拒绝临时单”是REJECT业务决定，若合法执行则正常记录不可变命令/回执并保持占用；鉴权失败、格式错误、版本冲突、签名或审计失败则无已提交命令，不伪造成功回执或事后独立事务补失败决定。客户端即使收到明确错误也保留原意图供核对，网络未知时继续原键查询。只有已提交键有服务器永久同键异文约束，未提交错误的日志不是业务台账。

## 4. 不可变回执与当前效力

unsignedReceipt 从锁内可信事实生成：receiptVersion、commandId/key/scope/type/hash、originalContext、实际 actor/principal/userAuthVersion/device/session（适用时）、服务器权限快照、originalOperatorId、业务决定、recordedAt 及能力。权限快照至少包含实际用到的权限码、有序完整有效权限集合、当时角色成员引用、仓库归属和设备/资格版本；不保存密码、Cookie、Bearer、CSRF 或私钥。

推荐 claims 为 `iss=acrylic-outbound`、`aud=acrylic-r06-command-receipt-v1`、`receipt=unsignedReceipt`，由 OfflineSigner 签 RS256，固定可信 kid/公钥环。它是历史业务证明，不是登录 token，不给它新增会使历史证据过期的登录 exp；现有登录期限不变。`recordedAt` 是事务中签名所用的服务器时刻，仅在事务提交后可见，不能声称精确数据库 COMMIT 的物理时刻；若协议继续命名 committedAt，须明示相同语义。

生成一次外层回执 JSON 字节、核对 token 内 receipt 与外层去除 signedReceipt 完全相等，再存原字节和摘要。controller 首次成功与原键恢复都直接输出该字节值；不得把 JsonNode 重新序列化后称为原响应。精确范围是业务回执 UTF-8 内容，不含 HTTP 头、压缩传输包、requestId 或 Cookie。

查询封套分别返回 originalCommandReceiptUtf8、originalResponseSha256、currentEffect。封套可能因当前评估变化，但其 originalCommandReceiptUtf8 解码后必须逐字节等于首次业务回执。currentEffect 含 evaluatedAt、当前来源/目标/轮次/决定版本、适用性、原因及缺口，不能写回原记录或塞入原签名；失效多原因应完整列出，不只展示第一项。

旧决定因撤权、候选、mapping/current/round 或恢复代次变化不再适用，原回执仍保留。成功出库已提交则事实不可撤销，currentEffect 只能说明当前后续动作资格和上下文变化，不能把历史 SUCCEEDED 改成失败或重开；更正走 R07。本方案所有能力仍为 false，台账 `RECORDED` 或验签成功不是放行证明。
