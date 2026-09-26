# R06 Room25 未提交草稿原机签名验收（2026-09-25）

## 范围与完成标准

本项在Room24已封存原件的停止事务中生成并保存唯一原机签名，服务端纯协议验证器独立核验相同规范字节与可信登记公钥。要求原身份/元数据/根/全设备截点绑定，失败整体回滚；重开或重试不重签，旧无签名原件不补签。不是服务器原件接收或完整E2a验收。

## 实际代码与自审

- 主树：LocalDraftStopProof/Signature/Signatures、LocalDraftStopRepository、TemporaryConnection、PdaSession、PdaDatabase及Room25 schema；非空Room24→25只加签名表，不回填。版本code20保持。
- 同事务生成LOCAL_EVIDENCE_STOP、快照、EVIDENCE_STOPPED及原机ECDSA P-256 DER签名；使用独立域ACRYLIC-R06/E2A/LOCAL-DRAFT-STOP/1，不能替代已有S的STOP_PROOF。
- PdaSession要求原机已绑定密钥，publicKey(false)不生成新密钥；签名前后原身份、绑定公钥和资料核验；签名只insert，无替换/补签入口。
- 服务端树：LocalDraftStopProtocol/Claims只核签名及来源/metadata绑定，可信公钥必须由调用者登记上下文提供；规范UTF-8、规范JSON、严格字段/安全整数/UUID/base64/P-256/keyId校验。
- VerifiedSignature只表达签名真实性；未检原准备回执信任、所有原件集合闭合或业务事实，不返回VERIFIED_COMPLETE、不创建S、不占用K、不写业务状态。
- 自审：当前助手实现与复核；没有独立人员审查。Android导出的真实Room合成向量由Java独立语言消费，非独立人员/真实设备证明。

## 当次验证

- Android定向38/38：新签名9项、Room23/24迁移2项、原存储22项、旧冻结签名5项。
- Android全套400项：397通过、3专用HTTP条件skip，100份XML；APK/lint通过，0错误、0fatal、186既有警告。
- 服务端定向33/33：新协议28、旧协议5。服务端全套522项：521通过、1符号链接条件skip，79份XML。
- 负例：密钥不可用/签名途中密钥和会话变化/存储异常全部回滚；缺签名不补造、坏签名/换公钥/改签名声明拒绝；服务端错可信来源八字段、错域、非P256、类型/数量界限/元数据错配/非规范JSON失败封闭。
- 首轮当次编译、定向、全套均通过，无新增失败；Room24 focused2/3和所有更早失败证据保留。
- 两树diff检查通过。未运行新MySQL/HTTP/Web/Chrome；原Room24 attempt54仅为历史冻结链回归，不算本次新场景通过。

## 证据与输入

统一根outputs/r06-e2a-takeover-20260925-01a0d612/：android-draft-signature-focused1.log及-xml、android-draft-signature-full.log及-xml、android-draft-signature-lint.xml、server-draft-signature-focused1.log及-xml、server-draft-signature-full.log及-xml。

APK android-draft-signature.apk SHA256 3c417a206e448605d8af63ae61de820420e6f62fe16ca5385d8af772bab0cb9c。

跨语言local-draft-stop-vector.json仅合成公开资料与公钥，复制为服务端test resources/e2a/local-draft-stop-v1-android.json，无私钥。普通测试不改源码，只在显式LOCAL_DRAFT_STOP_VECTOR环境变量下导出到指定本机证据目录。

源码/fixture清单draft-signature-source-manifest.json包含1279文件，SHA256 7a018f1267784ccf724c57086e9e281ea77e8f92e71f61c8bcc613368035cc1e。Git基线和全部未提交路径见R06_DRAFT_SIGNATURE_WORKTREE_STATUS_2026-09-25.md；均未提交、推送、整合main或部署。

## 未验与下一断点

原件根/数量/序列服务器闭合、原准备信任/其他来源事实、数据库独立接收、原键耐久/丢回执恢复、同写者产品队列继续开发。真机Keystore、OS死亡/断电、长时/现场/独立人员/生产权限审批保留未验。七类生产决定及TEMP生产归档/放行关闭，localDeletionAllowed=false。持续目标active。
