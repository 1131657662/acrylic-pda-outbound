# R06 E2a 原机证据接收合同

日期：2026-09-21。设计基线为 development `d774b56fefb9cfac9fbbe3da2e79d783dbea5487`，生产协议仍为 V34、Android code20/Room17。本文件是下一实现批的可审合同，不是已实现 API、迁移或设备验收结论。

## 1. 范围与不变量

本批只接收已有 TEMP `S` 的原机证据：同一来源、同一人、同一设备、同一仓库、同一 `workRound` 和同一 `sourceRecoveryEpoch`。输入可以是原键查询、原回执、停止证明 `STOP_PROOF`、冻结 `manifest`、分页 `page`、逐条 `item` 和已有私有对象引用。接收成功表示服务器已保存并核对到某一证据阶段，不表示业务决定、记账、放行或车辆清空。

本批明确不创建或补建 `S`、FH、assignment、canonical order、payload、upload、成功终态、释放、续扫、确认或处置结果；不调用七类命令代替证据接收。`NORMAL_CONFIRM.reason=""`、`localDeletionAllowed=false`、现有生产 handler/POST、V34 和 R09 fence 保持不变。

复用 V34 已存在的 `source_evidence_object`、`source_evidence_set`、`source_evidence_item`、`source_evidence_verification`。它们保存事实对象、集合、明细和验证版本；E2a 只新增一个独立接收原键/不可变服务器回执的最小持久化缺口（暂名 `source_evidence_intake_receipt`），不得把 `temporary_command_receipt` 的七类 `command_type` CHECK 当成接收入口。迁移编号、列的最终名字和 FK 由主协调指定的唯一迁移执行者分配，本合同不预占 V35。

## 2. 身份、原键与原回执

接收原键为客户端生成的规范 ASCII UUID `originalKey`，唯一范围是 `(authenticatedPrincipalId, routeScope, originalKey)`；固定 `routeScope=r06.e2a.evidence.v1`、`issuer=acrylic-outbound`、服务器回执 `aud=acrylic-r06-e2a-intake-v1`。签署原回执时的 actor/session 保存在可信上下文；当前本人重登录可按原键回放，不要求当前 session 等于历史 session。每个 intake operation（OPEN、每一页、STOP、VERIFY）使用独立稳定原键，后续页或验证不得用改变正文的重试复用旧键。接收正文使用严格规范 UTF-8：键按 Java UTF-16 排序，数组顺序保留，拒绝重复键、尾随内容、浮点、非安全整数和 `null`/缺失混淆。规范正文摘要为 `H(UTF8("ACRYLIC-R06/E2A/INTAKE/1\n") || canonicalBytes)`，小写 SHA-256。

服务器成功接收后一次生成 `originalResponseUtf8`、`originalResponseSha256`、`receiptVersion=1`、`receiptId`、`recordedAt` 和服务器 RSA 签名回执。再次以同身份和同原键读取时逐字节返回原响应；同键不同规范字节或不同摘要返回 `CONFLICT`，不生成第二回执。认证失败、正文格式/大小非法、篡改或锁/来源不匹配在接收前拒绝，不插入新的接收记录；网络未知或 404 不推断“未提交”。`currentEffect` 是单独动态评估，不能写回或重签覆盖 `originalResponseUtf8`。

服务器回执和设备停止证明是两个信任域。E2a JWT 使用三段 ASCII compact serialization、base64url 无填充，`alg=RS256`、`typ=JWT`、配置 keyring 的 `kid`、`iss=acrylic-outbound`、`aud=acrylic-r06-e2a-intake-v1`；不加入 `exp`，`recordedAt` 使用 UTC RFC3339 毫秒字符串。设备 stop envelope 为 `{core,keyId,algorithm,signature}`，其 `signature` 不在 `core` 内；签名输入仅为 `UTF8("ACRYLIC-R06/E2A/STOP/1\n") || canonical(core)`，不包含自身签名或 hash。服务器回执的签名输入是 JWT header.payload；两者均不把对方签名或摘要写入自身 core。EC 公钥是标准 base64 解码后的 DER X.509 SubjectPublicKeyInfo，`keyId` 固定为该 DER 字节 SHA-256 的小写十六进制，P-256 使用 `SHA256withECDSA`，签名是标准 base64 编码的 ASN.1 DER ECDSA 字节；不得改用 PEM、JOSE 64-byte `(r||s)` 或另一种 keyId 算法。

| 对象 | 签名/信任根 | 签名覆盖 | 不代表 |
| --- | --- | --- | --- |
| `STOP_PROOF` | 原机 P-256 (`secp256r1`) EC 私钥及设备登记的 X.509 SubjectPublicKeyInfo 公钥/历史 keyring | stop envelope、原 S/round、来源、stopEventId、capture/audit 截点、freezeDevice、sourceRecoveryEpoch、`manifestId/version` 引用和 `localFrozenEvidenceSha256`；`SHA256withECDSA`、ASN.1 DER、标准 base64 | 服务器已收到、已验收、车辆为空或可释放 |
| 原始 TEMP 回执 | 原业务既有 RS256、`typ=JWT`、原 audience 和历史 keyring | 原业务回执原字节；未知历史 key 只保留 raw 并标为不可可信，不回退当前 key | E2a 接收成功 |
| E2a 接收回执 | 服务器 RSA >=2048 位、`alg=RS256`、`typ=JWT`、配置 keyring 的 `kid` | 接收阶段、原键、对象/集合摘要、验证结果、当前能力全 false | 原机确实停止或业务决定 |

缺少设备 EC 签名、公钥或重启耐久证据时，合法来源材料可以被保存为 `UNVERIFIABLE` 的非可信证据，但不得取得可信包占用或 `VERIFIED_COMPLETE_AT_STOP`。未知历史公钥与合法原来源上送的证据可进入 `UNVERIFIABLE`；外层 JWT 结构非法、签名篡改、错误 issuer/audience/alg/typ、超限或来源身份不匹配必须在接收前拒绝。合法 RSA 重签但错 actor、原人、设备、会话、权限、source、version 或可信 facts 的原回执由独立消费者判为不可信，不得作为可信占用依据。

## 3. 无环封套、manifest 与分页

摘要构建是有向且无环的：

1. 每个 `item` 先保存原始字节和 `itemBytesSha256`。
2. `pageCore` 固定包含 `manifestId`、`manifestVersion`、`snapshotId`、`pageNo`、`pageCount`、`isFinal`、`itemCount`、`byteCount`、单向 `prevPageHash`（首项为 null）和有序 item 引用；不包含自身 `pageHash`、next hash、STOP 摘要或服务器回执。`pageHash=H(UTF8("ACRYLIC-R06/E2A/PAGE/1\n") || canonical(pageCore))`，page envelope 可带 pageHash 但不进入 core。
3. `manifestCore` 按 pageNo 顺序包含集合身份、固定 `pageCount`、每页 `pageHash`、entry/byte 总数和 `endPageNo`，不包含 `setHash`、`manifestBytesHash`、STOP、签名或 verification。`manifestBytesHash=H(UTF8("ACRYLIC-R06/E2A/MANIFEST/1\n") || canonical(manifestCore))`；`setHash=H(UTF8("ACRYLIC-R06/E2A/SET/1\n") || canonical(manifestCore))`。两者只作为 envelope 派生字段，不回写 manifest core。
4. `STOP_PROOF` 最后只引用 `manifestId/version`、`manifestBytesHash` 和 `setHash`，其 EC 签名不覆盖服务器回执或 verification；服务器回执最后引用 STOP 原字节摘要、manifest 摘要和 verification ID。

接收器固定技术上限：单个 page 规范字节不超过 256 KiB，单个证据 object 不超过 32 MiB，一个 set 最多 4096 pages、总原始字节不超过 128 MiB；服务端流式校验，不把全 set 一次载入内存。这些是传输/资源上限，不是每车包数、重量或业务数量上限。超过上限返回明确错误，不生成接收回执。

`manifest` 必须包含 `schemaVersion=1`、`manifestId`、`manifestVersion`、来源/S/round、`pageCount`、`entryCount`、`byteCount`、有序 page 根、`endPageNo=pageCount` 和固定 `snapshotId`；`setHash` 只作为封套字段，不进入 manifest core。V34 的 `source_evidence_set.manifest_bytes` 约束为 1..262144 字节，manifest 不得超过 256 KiB；page 单体上限仍为 256 KiB，4096 只是 page 数上限，所有上限必须同时满足。每个 page 必须包含 `pageNo`、固定 `pageCount`、`snapshotId`、`isFinal`（仅 `pageNo=pageCount` 为 true）、`prevPageHash`、`pageHash`、`itemCount` 和 item 顺序；不允许 next hash 或普通 page 自报最终状态。服务端分阶段保存对象/page，拒绝重复、错序、跨 manifest、陈旧 snapshot、重复 item 和错误 hash；缺页可保持 `PARTIAL`，只有 finalize/complete 在缺页、终止标记缺失或集合不一致时失败且不生成 `VERIFIED_COMPLETE_AT_STOP`；未经独立核验的声明不得取得可信占用。续传 page 使用新的 operation key，但必须引用同一 manifest/version/snapshot；同页同摘要重试幂等，异摘要冲突。V34 的 `MANIFEST_PAGE` 是现有 object kind；`source_evidence_item.item_kind` 没有 PAGE，页顺序和根由 manifest bytes 及窄写者校验，不宣称已有 PAGE 枚举或数据库闭合。`stop_object_id` 是 set 外层引用，在 finalize 时绑定，不进入 manifest core 或任一派生 hash。

静态向量生成步骤（待实现者执行，不代表本轮已通过）：按上述字段顺序用 UTF-8 canonical JSON 生成空集 `pageCount=1, entryCount=0, byteCount=0` 的 pageCore；以首 `prevPageHash=null` 计算 pageHash，再生成含该 pageHash 的 manifestCore，依次计算 manifestBytesHash、setHash，最后以 STOP envelope 引用两者并计算 EC 签名。向量必须保存每一步原始 bytes、长度和小写 SHA-256；任何把派生 hash/signature 写回 core 的实现都应被负向测试拒绝。

## 4. STOP_PROOF、完整性与状态

先生成不含 manifest/set/page 摘要及最终 STOP 签名的停止审计事件；该事件随全设备 audit 进入 page/manifest。随后生成 `STOP_PROOF.core`，固定绑定 source identity、S、round、原人、freezeDevice、sourceRecoveryEpoch、stopEventId、captureHighWatermark、全设备 `auditThroughSeq`、停止事务版本和已计算的 `manifestId/version`、`manifestBytesHash`、`setHash`；core 仅排除自身签名和自身 hash。外层 stop envelope 携带 `keyId/algorithm/signature`，`signature=ECDSA(domain || canonical(core))`，以签名绑定集合。服务器回执最后引用 STOP 原字节摘要、manifest 摘要和 verification ID。停止审计事件本身不包含 manifest/page hash 或最终 STOP 签名，避免审计事件与外层证明形成闭环。全设备审计同时包含 FORMAL 与 TEMP，共用原 device localSeq；`INVALID`、`VOID`、`BUSINESS_REJECTED`、失败、丢弃和未决记录必须逐条保留，不能被 summary 隐去。summary 必须由明细重算，不能用 MAX(seq) 或 count=0 代替连续性/完整性。

只有在原机事务已停止该来源新输入和扫码、停止状态与冻结集合已耐久、重启后仍能恢复同一 set、EC 签名和服务器已知事实逐项核对时，才允许验证结果 `VERIFIED_COMPLETE_AT_STOP`。没有 Android 耐久证据、停止事件、历史 key 或集合连续性时只能 `PARTIAL` 或 `UNVERIFIABLE`；电子空集不证明车辆为空。

接收/验证状态与业务效力严格分离：

| 状态 | 含义 | 能力 |
| --- | --- | --- |
| `RECEIVED` | 原对象/集合已耐久并通过结构及身份接收 | 仅可重读原证据 |
| `PARTIAL` | 缺页、缺明细、缺停止耐久或仅部分事实 | 保守保留已知占用，不释放/续扫/删除 |
| `CONFLICT` | 同键/manifest/page/set 或事实摘要不一致 | 保留原证据和冲突原因，不接受新可信集合 |
| `UNVERIFIABLE` | 未知历史 key、缺 EC 公钥、签名不可核或关键事实缺失 | 不取得可信占用，不得放行 |
| `VERIFIED_COMPLETE_AT_STOP` | 仅表示停止截点电子事实完整且可核 | 仍不等于 RECONCILED、处置完成、车辆为空或放行 |

新事实、前驱、current S/round、mapping 或权限变化只使历史验证的当前效力失效，不改写原回执。`currentEffect` 返回 `evaluatedAt`、当前身份/权限、来源/目标/轮次、适用性、失效原因和缺口；所有能力默认 false。

接收生命周期固定为：`OPEN` 只建立 intake envelope 和 manifest identity，不插入 `source_evidence_set`；`PAGE` 以新 operation key 逐页写 `MANIFEST_PAGE` object，缺页期间 intake 可为 `PARTIAL`；`STOP` 只写并核验独立 STOP object，不能覆盖既有 object；只有 STOP 已持久化且匹配来源/S/round、manifest/version、setHash，才可插入 V34 要求 `stop_object_id NOT NULL` 的 `source_evidence_set`，并满足 `manifest_bytes` 1..262144、`audit_through_seq>0`。所有页、STOP 和 manifest 引用齐全后才可 `VERIFY`，追加一条 verification 版本并生成接收回执。manifest/set 一旦 finalize，其 core、`manifestBytesSha256`、`setHash` 和 `stop_object_id` 不 UPDATE；迟到页只能引用同一 manifest 并触发新的验证版本，不能补写已冻结根。缺页或未知 key 的材料可以保存为 `PARTIAL/UNVERIFIABLE`，但 finalize 失败不返回 complete。`PARTIAL` 不妨碍对已独立核验的逐包事实在 B/K 锁下做保守占用：原资格、原捕获、仓、原 cycle 与当前 bale（B）状态必须逐项核准；另核 K 不存在或精确本来源同 cycle，前者才可插入 V34 `SOURCE_RESERVED`，后者保持既有 K；他源或其他 owner 只记录冲突，绝不覆盖。未经独立核验的 `UNVERIFIABLE` 声明、客户端列表或无 STOP 且尚未建立 set 的阶段不能创建 K。

操作边界固定为四个内部接收动作：`OPEN` 创建 manifest identity；`PAGE` 接受一个有序页；`STOP` 接受一个独立 STOP object；`VERIFY` 只读重算并追加 verification。每个动作有自己的原键和 canonical request；PAGE/STOP/VERIFY 的请求必须引用 OPEN 返回的 manifestId/version，不能把后续正文塞回 OPEN 原键。GET 原键回放只返回已提交 envelope、原 response bytes 和动态 `currentEffect`，不执行 VERIFY 或任何业务动作。

最小 `source_evidence_intake_receipt`（暂名，待迁移分配）必须 INSERT-only，至少保存：`id BINARY(16)/receiptVersion INT`、`principalId BINARY(16)/routeScope VARCHAR(64)/originalKey BINARY(16)` 唯一键、request canonical bytes/hash、original response UTF-8 bytes/hash、服务器 `kid`/JWT bytes、`operationKind/status/recordedAt`、warehouse/scope/submission/workRound、creatorDevice/clientOrder、originalOperator、temporaryAuthorization、sourceRecoveryEpoch、以及可空的 `objectId/setId/verificationId` 复合引用。receipt→set 必须用现有 `(id,scope_id,submission_id,work_round)` 父键；receipt→verification 只能沿现有 `(id,set_id,scope_id)` 唯一父键并通过同一 set 复合引用传递 submission/round，不得假定 V34 存在不存在的联合唯一键。复合 FK 必须同时绑定来源上下文；不在 receipt 上复制 item/page。`currentEffect` 只读派生或追加版本事件，禁止 UPDATE/DELETE 原回执。V34 的 set 当前没有 canonical snapshot、actor/device/epoch 的显式列，stop_object_id 也没有 kind 约束；这些是待迁移/窄写者校验项，不可通过当前字段默认值伪造。

操作路由与状态字段固定如下：

| 操作 | 原键正文 | 允许阶段 | 持久结果 |
| --- | --- | --- | --- |
| `OPEN` | manifest identity、来源/S/round、snapshot | 无既有 intake | 仅 intake envelope；不建 set |
| `PAGE` | 同一 manifest/version/snapshot、pageCore | `OPEN` 后 | `MANIFEST_PAGE` object；缺页保持 `PARTIAL` |
| `STOP` | stop envelope、manifest/set 引用 | 对应 `OPEN` | STOP object；未匹配不得建 set |
| `VERIFY` | manifest/page/STOP 引用与验证参数 | STOP、页闭合后 | 追加 verification；缺页只能失败或 PARTIAL |

所有 ID/版本/序号使用 `BINARY(16)`/`BIGINT`，摘要使用小写 ASCII `CHAR(64)`，时间使用 UTC `DATETIME(3)`；字段类型沿 V34，不以 JSON 字符串替代可约束父键。

## 5. 锁、权限与事务边界

现有 `CommandLocks` 的实际顺序是实现依据，不能在其后重复补锁：`identity()` 先执行 E `server_state` 读屏障、G `user_management_guard` 写屏障，再按 UUID 排序锁 `app_user`、仓库读取、按 UUID 排序的设备行和当前 PDA session；`source()` 再按 `temporary_authorization` → `temporary_work_origin` → `submission_scope` → `temporary_decision_state` → `outbound_order`（如有）→ `order_submission_slot`（如有）→ `submission` 加 `FOR UPDATE`。E2a 在 `submission`（S）之后、`bale`/`bale_commitment`（B/K）之前锁证据行，固定表序为 `source_evidence_set` → `source_evidence_object` → `source_evidence_item` → `source_evidence_verification` → `source_evidence_intake_receipt`，V36 之后再锁 `source_evidence_page_item_stage`；每表按 UUID（item/stage 再按 `item_kind,original_record_id`）升序，同一行只锁一次。`SubmissionStore.lockExisting`、`PdaSessionLocks` 和各库存入口的 `FOR UPDATE` 只在同一总序中调用一次。B/K 按 `bale_id` UUID 升序。不存在的证据行不预锁；插入由父 FK/唯一键裁决，不能用字母名称伪造已有锁。

每个写入口严格执行：基础认证与 route/principal 解析 → 第一次按 `(principal,route,originalKey)` 查询（在 evidence-write 和新写 epoch 判定前）→ 上述既有锁集 → 第二次查询并重核 request hash、source/S/round、原人/原机/epoch（仍在新写权限判定前）→ 当前 server recovery epoch、独立 evidence-write 权限和 userAuthVersion 重验 → 文件/DB 写入。读回既有原回执可在写权限失败时按安全范围进行，但不得借写失败重试动作。只有 intake 自然键唯一冲突允许退出原事务后，在独立 read-only 事务按同键读取：同 hash 返回原 response bytes，异 hash 返回 `CONFLICT`，无行返回 `UNKNOWN`，禁止在失败事务内继续写；其它 FK、CHECK、容量、签名或锁失败必须返回明确错误，不得回放成功响应。

客户端不能声明已持有 K 或任意包归属。按上段可信逐包规则核对服务器捕获记录、可信 facts、bale（B）状态和 bale_commitment（K）：K 不存在可建 `SOURCE_RESERVED`，精确本来源同 cycle 保持，他源/终态冲突不覆盖；未经独立核验的声明不得占用，缺证据不得从数量、order_id 或客户端列表推导占用。接收文件先写不可变私有对象并确认大小/hash/耐久，再在短事务中写 object/set/item/verification 引用和 intake receipt；任一步失败不返回 `RECEIVED`，孤儿文件不等于已受理且不自动清理。

读写权限每次按当前认证身份、仓库、原人/设备、资格和 userAuthVersion 重验；原机证据写能力采用已审方案的独立 `pda:recover`，并叠加同人、同机、同仓、当前 NORMAL、同 source epoch 条件；不从 `temporary_order:confirm`、create/scan/submit 推导，也不自动新增授权。现有 `SourceEvidenceAccess` 仅是 Web `temporary_order:confirm` 的只读来源关系校验；E2a PDA intake 的本人读取/写入需新接点，当前 session 可是新会话，但必须重新证明同一本人、原设备、原来源和仓库。历史权限快照只用于解释原事实，不授予新写权。GET 不执行动作，不因读权限获得确认、关联、排除、处置或释放能力。

## 6. 负向矩阵与后续边界

实现验收至少覆盖：同键同字节重试、同键异字节、丢响应后查询、重复/漏页/错序/混 manifest、错误 prev/next hash、终止标记缺失、未知 STOP key、错误 EC actor/device/epoch、合法 RSA 重签错 facts、原回执未知历史 key、摘要环、文件落盘后 DB 失败、DB 引用后文件缺失、并发同源接收、权限撤销、current S/round 变化、FORMAL/TEMP 审计缺段、INVALID/VOID/失败/未决被隐去、K owner/cycle 不一致和超过资源上限。

明确未覆盖且不得声称完成：唯一异常回滚后另一只读支路动态读取、合法 REJECT 夹具、历史 keyring/异地取回、Android 长时/断电耐久、真实 HTTP 丢响应、V34/R09 fence、现场逐包核验、库存释放、最终确认或放行。设计阶段不运行测试、不启库、不修改生产/测试/总契约/迁移。
