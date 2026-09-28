# R06 JWT状态误分类：先定性与最小修复（2026-09-28）

## 1. 只读定性：a，分类不精确

输入a257e11，主树D:/acrylic-pda-outbound、codex/development，与origin一致且干净；仅当前助手写入，测试端口空闲。第一步只读，未改生产代码。源码版本事实见[看板生成块](../ORCHESTRATION_STATUS.md)。历史500的实际失败见[A3记录](A3_NON_E2A_REGRESSION_2026-09-28.md)。

损坏样本invalid.token.signature在SourceEvidenceProof.verify第41行触发SubmissionJson.parse的JSON解析失败；后者包装成BusinessException(400, INVALID_SUBMISSION)。verify第53行把所有BusinessException透传，read第29–30行把它们统一映射成UNVERIFIABLE_KEY，误覆盖应有的INVALID_SIGNATURE。异常发生在publicKeyFor之前，与本机缺钥无关。

该字段是terminalProof.verification，只读证据状态，**不是认证HTTP错误码**。详情GET按设计允许读取不可信的原证据并返回200，不代表损坏JWT通过验签。证据链：

- SourceEvidenceService.read先调用SourceEvidenceAccess，按当前temporary_order:confirm、仓库和原来源身份鉴权，随后只读返回证据；不会凭verification授予读取权限。
- SourceEvidenceView.assessment对所有非VERIFIED状态加入VERIFIED_TERMINAL_PROOF_NOT_AVAILABLE；七项写入/确认/释放/续扫/删除等能力无条件false。
- SourceEvidenceIntakeLocalPayload.requireHead只接受VERIFIED；INVALID_SIGNATURE与UNVERIFIABLE_KEY都被同一E2A_ABORT_PROOF_UNVERIFIABLE拒绝，不能进入后续可信事实接纳。
- 检索服务端、Web、Android生产调用方：没有发现根据这两个具体状态区分授权或自动重试的逻辑。它们会影响人工排查含义（损坏签名与缺少可信钥），但没有证据表明本缺陷导致应拒绝的放行或有效JWT误拒。
- 契约temporary-source-evidence-schemas.yaml已分别列出这两个状态；路径契约和R06_SOURCE_EVIDENCE_GUIDE说明只读、不放行。无需更改或发布契约。

**判定a，不是b。**“拒绝”指拒绝把JWT当可信凭据，而非拒绝合法鉴权后的证据查看请求。该判断限于本次已知样本及已检索消费路径，不宣称全系统无其他安全问题。

## 2. 已授权的最小路线与验收

仅收窄SourceEvidenceProof.verify的BusinessException分类：真正SIGNING_KEY_UNAVAILABLE继续透传给现有read映射；解析/其他业务错误返回INVALID_SIGNATURE。不改RSA验签、算法/类型/keyId、issuer/audience/receipt匹配、可信钥来源、权限或能力决定逻辑。

补覆盖可解码但非JSON的JWT头、已签名但非JSON的claims、实际key provider不可用及有效签名；IT保留500原断言并补七项能力false、原证据不改，以及实际历史公钥不可用的只读状态。定向IT复验后跑认证相关IT，证据等级分列；每个新attempt执行前查重，500及所有红记录保留。

本项按用户已给的a分支授权执行，不另索授权；若出现新的安全边界问题或其他真bug，立即停报。R09 C2只另写范围/验收，不接生产入口。A3剩余7类在本项完成且有余量时继续。
