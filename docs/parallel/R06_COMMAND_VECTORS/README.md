# R06 C0 命令、原回执与签名向量

2026-09-20，固定业务基线 `47dc8f2a1d8f7d9aabf67de9bdcfde684bba5ec5`，独立分支 `codex/r06-command-vectors`。承接[命令证据方案](../R06_M2B/COMMAND_EVIDENCE_CONTRACT.md)及[C0交接范围](../R06_M2B/HANDOFF.md)。**本目录是固定版本的实施提案和离线测试材料；新字段、route scope、hash域、audience没有接入生产接口、数据库台账、Android或现网。** 后续采纳须由共同协议执行者显式确认版本与集成，不能让旧解析器静默接受。

本批只定义字节和身份绑定，不决定业务权限、锁集合、排除全集或实物结果。`CONTEXT_MATCH_ONLY` 只说明给定的可信上下文相同，不意味着命令可以执行；需要由L1/L2和命令台账执行者在同一事务中实时授权、核对真实上下文、业务规则及唯一性。样例的 `RECORDED` 是纯合成历史，不是一次服务端决定或出库成功。

## 1. 固定规范字节

沿用当前 [SubmissionJson](../../../server/src/main/java/com/acrylic/outbound/submissions/SubmissionJson.java)：UTF-8、对象键按Java `String` 的UTF-16代码单元顺序排序，数组保持原序，无空白或尾换行。数字仅安全整数 `[-9007199254740991,9007199254740991]`；版本/序号/代次使用十进制字符串，`0`或不带前导零的最多18位数字。JSON数字 `1.0`/`1e0`、重复键（含转义后重复）、尾随第二份JSON、NaN均拒绝；`-0`规范为`0`。不做Unicode NFC/NFD转换。明确null与缺字段不同，空集合不证明来源或实物完整。

本目录Python参考实现限定有效Unicode标量的UTF-8文本，不声称覆盖所有畸形编码或未配对代理字符的Jackson行为。命令字段名均ASCII；向量额外包含中文、组合字符、非BMP字符以及数字形式键，防止错误使用语言默认排序。30条静态向量含完整命令、nullable命令、派生控制和原回执；每条合法样本有人工核对的规范文本和独立Node SHA-256常量。非法样本同时要求真实Java parser与Python拒绝。

## 2. 摘要域

令 `C(x)` 为上述规范UTF-8字节，`H(x)` 为SHA-256的小写64位十六进制。下面前缀最后的 `\n` 是单个LF字节 `0A`，不是反斜杠与字母n。摘要字段不能互相替用。

| 名称 | 精确输入及用途 |
| --- | --- |
| 原payloadSha256 | `H(C(payload))`，保持当前实现，不引入新前缀、不重写旧正文 |
| commandSha256 | `H(UTF8("ACRYLIC-R06-C0/COMMAND/1\n") || C(command))`，覆盖完整命令，包括type、commandId、source、target、全部expected、代次和reason |
| unsignedReceiptSha256 | `H(UTF8("ACRYLIC-R06-C0/RECEIPT/1\n") || C(unsignedOriginalReceipt))`；fixture中的`receiptSha256`即此域，不包括signedReceipt，不放进自身产生循环摘要 |
| derivedControlSha256 | `H(UTF8("ACRYLIC-R06-C0/DERIVED-CONTROL/1\n") || C(derivedControl))`；明确是后来计算的派生控制摘要，V33不存在当时签署的此字段 |
| originalResponseSha256 | `H(原响应UTF-8字节)`，包含原signedReceipt及原空白；用于逐字节恢复，不能重编码后替代 |

派生控制固定字段：controlVersion=1、warehouseId、source、submissionId、workRound、authorizationId、freezeDeviceId、sourceRecoveryEpoch、assignmentId和continuationId。TEMP首轮后两者为null。canonical/mapping/transport/currentEffect不是原来源身份，故不混入这个派生来源摘要；它们在命令expected及不可变原回执上下文中单独绑定。

## 3. 命令身份与expected

[command_vectors.json](command_vectors.json)给出完整DTO。`commandVersion=1`、commandType采用原方案的七个命令族；本批只验证公共结构，不提供各族执行器。`commandId`固定等于客户端首次持久保存的原UUID键，不能发送或查询一次就重生。UUID严格小写规范形式，reason逐字保留并纳入摘要，不trim后另算摘要。接口别名必须映射到同一登记scope；提案scope为`r06.command.v1-proposal`，不是已注册API。

唯一原键语义保持 `(authenticated principalId, routeScope, originalKey)`。actor从服务器认证取得，客户端命令不允许加actor/权限声明；原device/session（适用时）及真实权限快照由服务器落原回执。换操作者、route或key查询不会获得另一条原回执。测试中的 `currentWriteAllowed`、`currentReadAllowed` 和 `check_new_binding` 的saved/expected上下文全部是调用方提供的离线场景输入，不是可上传的自授权参数；真实生产必须由服务端现时鉴权、锁内读取上下文，不能信任客户端的这些值。回执内的历史权限也不能授予本次写权限。

source固定 `{kind:TEMP,creatorDeviceId,clientOrderId}`；同时绑定原S、round、authorization、freezeDevice、warehouse和sourceRecoveryEpoch。transportRecoveryEpoch为本次命令发送时固定值，重试不偷偷换成新代次。expected明确包含canonicalOrderId、mappingVersion、currentSubmissionId、scopeRound、slotVersion、decisionVersion、receivedPayloadSha256、declaredUploadSha256、planSha256、targetRevision、candidateContextSha256及userAuthVersion。

无canonical时，canonicalOrderId/slotVersion/targetRevision为null，mappingVersion为字符串`0`。declaredUploadSha256可存在，receivedPayloadSha256仍null；不能把已收部分块或完整声明升格成已接受正文。这里的mappingVersion是待新增持久模型的提案，不能由当前V33读取临时伪造。已有目标样例为mappingVersion=`1`，后续变化增加版本。样例a/b/c/d摘要均为标明的合成上下文常量，不声称这些摘要存在对应业务正文或已证明完整候选集。

source、S、round、原资格/冻结机、warehouse、任一expected或代次变化须在当前锁内冲突，不把旧命令作用到新目标。同键异正文（包括只改理由）冲突；同键同正文只返回已存原响应。命令结构保留reason并把它纳入hash；本向量子集要求排除、关联、拒绝、短装、实物处置和续作命令提供非空理由，`NORMAL_CONFIRM`允许空理由以保留既定的有权上传者直接确认目标。这个reason分组是向量校验边界，不是向生产新增未确认的业务门槛；最终命令执行者仍须按既有业务规则确认哪些族要求理由。新键并发争同decisionVersion仍须真实数据库唯一性/版本裁决，本离线模型没有模拟这项事务保证。

## 4. 原回执与当前效力

[receipt_vectors.json](receipt_vectors.json)固定原回执字段：receiptVersion、commandId/原键/scope/type/hash、originalContext、实际actor及当时权限快照、原操作员、decision、committedAt和能力标记。source/current/target的原上下文是决定当时的事实；后来撤权、换目标或换round不能改写。所有本批能力标记均false，不支持成功或释放证明。

建议查询封套分别返回 `originalCommandReceiptUtf8`、`originalResponseSha256` 和 `currentEffect`。前者保存原业务回执对象的精确UTF-8文本（不是完整HTTP头、cookie或外层网络包）；可含空白，查询不重新序列化。客户端先核原字节摘要、解析原signedReceipt并逐字段匹配持久原命令，再展示当前评估。样例故意保存带缩进和尾换行的原响应，证明不能拿规范化JSON冒充原字节。

currentEffect是本次查询派生视图，包含evaluatedAt、appliesToOriginalContext、reason及当前能力；不嵌入原回执，也不沿用旧签名。样例覆盖适用于原上下文、MAPPING_CHANGED、SOURCE_SUPERSEDED、AUTHORIZATION_REVOKED、RECOVERY_EPOCH_CHANGED。其状态仅为提案，尚无实时评估服务。原响应不随这些值变化；撤写权后只有当前仍获准读取的身份能取历史，撤读权则拒绝。放行始终由后续完整成功协议另行证明。

首次网络发送前须耐久保存原命令，保存失败不发送。丢响应、刷新或重登录用原身份/键/原文查询；未知键不是“确认失败可换键”，损坏摘要或未知验签key保持本机资料并待证据恢复，不降级验签或重做业务。并发锁、网络/存储崩溃、真实重新登录以及PDA持久化属于后续联调，离线回放示例不能代替这些验收。

## 5. 签名与独立核验

复用实际 [OfflineSigner](../../../server/src/main/java/com/acrylic/outbound/pda/OfflineSigner.java) 的RS256 compact JWT封装：`alg=RS256`、`typ=JWT`、kid由X509 DER标准Base64文本的SHA-256前32个十六进制字符加`offline-`产生。签名输入为token中原header段 + ASCII点 + 原payload段，不重新规范化已签token。验签只接受外部可信公钥表中的kid，禁止从token内找替代公钥；RSA至少2048位。

提案claims为 `{iss:"acrylic-outbound",aud:"acrylic-r06-command-receipt-v1-proposal",receipt:unsignedOriginalReceipt}`。此audience故意与已实现`acrylic-pda-receipt`区分，现有Android不支持它。原回执是长期历史证据，不是登录JWT或Bearer凭据；不凭其授予当前权限，不加会使历史证据失效的登录期限，也不变更现有会话到期规则。外层去掉signedReceipt后必须与已签receipt相等，再与原持久命令逐字段绑定；密码、Cookie或密钥不得放入原回执。

[signed_vectors.json](signed_vectors.json)保存17份由真实Java signer生成的签名和测试公钥，全部为合成数据。有效样例之外的错audience/issuer、来源、round、hash、目标、current、mapping、权限版本、代次及actor等仍具有真实RSA签名，故能检查消费者是否只验签而漏绑上下文。另测未知key、签名位损坏、外层篡改、给原回执加入currentEffect、非规范Base64URL和改变原token字节。

`TemporaryCommandVectorOracle.java`在运行时新建RSA密钥对，仅在系统临时目录给实际OfflineSigner使用，finally删除私钥、公钥临时文件和目录；仓库只保存公开验证材料，不读任何原业务密钥。常规验证不重新生成签名夹具或预期hash。`java_oracle.py`显式编译当前6个原生产类及oracle到临时目录；无stub、无Gradle、无Spring上下文、无数据库或网络。

## 6. 运行与边界

依赖Python3.12+、`cryptography==48.0.0`、JDK21，设置JAVA_HOME。Java oracle只读取已有Gradle缓存：Jackson core/databind2.21.4、annotations2.21及Spring context/beans/core6.2.19；缺少精确依赖时明确失败，不自动联网或改变项目依赖。可复用R09 Python环境，但不运行R09脚本或读取其lab配置。执行：

```text
python -W error::ResourceWarning -m unittest discover -s docs/parallel/R06_COMMAND_VECTORS -v
```

仅当有意更新向量并重新独立审查时才运行签名生成器；它接受明示的测试输入/输出路径，生成公开测试签名，不是证据修复工具。

本批不证明MySQL锁闭合、真实原命令台账/事务原子性、异机或跨代次恢复、Android验签/持久化、HTTP/设备/现网行为。TEMP确认/关联/实物处置/共享记账/成功签名和清理仍由后续批次实施。源码/测试事实与失败记录见[状态记录](STATUS.md)。原树、原密钥/库、AVD/M71和现网保持。

尚未覆盖的语义绑定：originalOperatorId、actor.permissionSnapshot、deviceId/sessionId、decision内容及committedAt目前只有签名字节完整性保护，没有连接服务器原事实逐项证明；合法服务器密钥签出的错误原操作人或权限集合，必须由后续服务端/消费者结合可信上下文拒绝。本批仅额外核对actor.principalId与userAuthVersion，不宣称已验证权限组合、异常自批、决定内容或历史密钥轮换。nullable命令有规范字节/字段检查，17个签名上下文样例使用已有canonical；无canonical的真实终态签名由E1/后续消费者验收。本目录Python校验器不是Java生产命令校验的实现，也不提供其已上线结论。
