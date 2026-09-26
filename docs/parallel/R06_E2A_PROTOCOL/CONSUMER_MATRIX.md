# R06 E2a 独立协议消费者矩阵（准备稿）

状态：`PREPARED / WAITING_FOR_W1_INTERFACE_CARD`。本文件只定义独立消费者的检查面和证据格式；未实现生产 codec、未生成生产向量、未运行 Gradle/数据库/HTTP，也不构成证据已接收或业务放行。

## 约束来源与冻结边界

- 基线：`7a30523743a4317d2f9f007a6b99613b168de449`。
- 合同：`docs/parallel/R06_E2A/CONTRACT.md`；实施顺序：`IMPLEMENTATION_PLAN.md`。
- W1 接口卡待提供：生产 codec 的逐字段 wire schema、canonical JSON 字段顺序/空值规则、域前缀、签名输入、错误码，以及可复现向量的原始字节、长度和小写 SHA-256。
- 消费者只读取 W1 固定产物，不复制或手造平行生产实现。未收到接口卡前，所有行均为准备项，不计作者、独立或组合通过。

## 消费矩阵

| ID | 检查对象 | 正向断言 | 必须拒绝的负向变体 | W1 输入依赖 | 状态 |
|---|---|---|---|---|---|
| C01 | wire envelope shape | 字段集合、类型、版本、routeScope、issuer/audience/alg/typ 与合同完全一致；未知字段按接口卡规则处理 | 缺字段、额外字段、错误类型/版本、错误 route/issuer/audience/alg/typ、超长字段 | wire schema + canonical 示例 | WAITING |
| C02 | canonical bytes | 相同语义对象产生唯一 UTF-8 canonical bytes；逐字段 raw bytes/hash 可重算 | key 重排、空值替换、Unicode/数字格式变化、hash 预先写回 core | canonical fixture + bytes/hash | WAITING |
| C03 | operation/original key | OPEN/PAGE/STOP/VERIFY 各自原键隔离；同键同 canonical 重试可重放原 response bytes | 同键异 bytes、跨 operation 复用 key、把正文塞进 OPEN、response 改写 | operation schema + response fixture | WAITING |
| C04 | page core/hash | pageCore 不含 pageHash/nextHash/STOP；`pageHash = H(PAGE domain || canonical(pageCore))`；首 prev 为 null | 错 prev、next 自引用、错 pageNo/pageCount/isFinal、跨 snapshot/manifest、item 顺序改变 | page vector + domain prefix | WAITING |
| C05 | manifest core/hash | page 根按 pageNo 有序；manifestCore 不含 setHash/manifestBytesHash/STOP/signature/verification；两摘要独立派生 | 把派生摘要写回 core、页重复/漏页/错序、pageCount/endPageNo 不一致、超过 256 KiB | manifest vector + limits | WAITING |
| C06 | set/manifest no-cycle | page → manifestCore → manifest/set hash → STOP 单向闭包；无自引用或回写 | `setHash`/签名进入 manifestCore、STOP 反向进入 page/manifest、循环引用 | complete empty + non-empty vectors | WAITING |
| C07 | STOP_PROOF EC | P-256 公钥标准 base64→DER X.509 SPKI；keyId 为 DER SHA-256 小写 hex；ECDSA SHA-256 签名为 DER | raw/uncompressed key 当 SPKI、hex/base64 混用、P-384/RSA、JOSE r\|s、签名覆盖 response/verification、错误 actor/device/epoch | EC key + signed STOP raw/core/hash | WAITING |
| C08 | STOP audit binding | stop event 先于 STOP_PROOF；core 绑定 source/S/round、stopEventId、auditThroughSeq、manifest/set 摘要且不形成审计环 | stop event 含最终 manifest/signature、auditThroughSeq=0、缺 FORMAL/TEMP 明细、摘要闭环 | STOP raw/core + audit fixture | WAITING |
| C09 | RSA receipt | 服务器回执使用 RS256；JWT 结构/alg/issuer/aud/typ、kid、原 response bytes/hash、STOP/manifest/verification 引用可验证 | 合法 RSA 重签但错 actor/source/version/facts、篡改 payload/signature、错误 key/alg/issuer/aud、回执改写 | RSA keyring + receipt fixture | WAITING |
| C10 | keyring/identity facts | 已知 key 与可信 identity/source/recovery epoch/设备/权限 facts 全部匹配才可可信 | 未知历史 key、错原人/设备/session/权限/source/version、无 keyring、过期 facts | keyring + identity fixture | WAITING |
| C11 | paging limits/state | page ≤256 KiB、object ≤32 MiB、≤4096 pages、set ≤128 MiB；缺页可 PARTIAL，VERIFY 不伪造 complete | 超限、重复 item/page、混 manifest/snapshot、缺终止标记、迟到页改写冻结根 | limit fixtures + expected errors | WAITING |
| C12 | status/currentEffect | RECEIVED/PARTIAL/CONFLICT/UNVERIFIABLE/VERIFIED_COMPLETE_AT_STOP 语义与能力边界分离；currentEffect 只读派生 | PARTIAL/UNVERIFIABLE 取得可信占用、历史回执 UPDATE/DELETE、GET 触发 VERIFY/业务动作 | status/error mapping card | WAITING |
| C13 | legal re-sign negative | 使用合法 RSA 重签的结构可解析但 facts 不符时，消费者返回不可信/UNVERIFIABLE 并保留原字节 | 将“RSA 签名有效”误当作 actor/source/epoch 可信，或升级为 RECEIVED/COMPLETE | negative receipt vectors | WAITING |
| C14 | cross-language replay | W1 raw bytes/hash 在 Python/Java（必要时 Android）消费者重算一致；stdout 完整保存 | 任一语言 canonical/hash/signature 不一致、行尾/编码差异、大小写摘要差异 | reproducible vector bundle | WAITING |

## 证据记录格式

收到 W1 接口卡后，逐个 case 建立独立记录，至少保存：

1. 输入 JSON/原始 envelope、每个 core 的 UTF-8 原始 bytes（长度和十六进制或安全 base64）及小写 SHA-256。
2. pageHash、manifestBytesHash、setHash、STOP 原字节摘要、EC DER 公钥/keyId/签名、RSA receipt/JWT 原字节与 keyring kid。
3. 消费者命令、完整 stdout/stderr、退出码、运行时版本；负向项记录错误类别和拒绝发生在持久化前/后的边界。
4. 证据标签严格区分 `PREPARED`、`AUTHOR`、`INDEPENDENT`、`COMBINED`；本准备稿只能使用 `PREPARED`。

## 阻断与下一步

当前唯一协议阻断是 W1 接口卡和固定生产向量尚未提交。收到后先做 C01–C06 的纯规范/摘要重算，再做 C07–C13 签名与负向消费，最后做 C14 跨语言复放；任何失败直接回报 W1 与主协调，不修改 W1 生产实现。重型 Gradle、启库、HTTP、文件写、K 写服务、决定 handler/POST、Android/设备和部署均不在本树范围。
