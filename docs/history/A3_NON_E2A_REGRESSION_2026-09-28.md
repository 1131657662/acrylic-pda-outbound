# A3 非e2a回归接续：首批停报与修复后续验（2026-09-28）

接续索引：§1–4保留496–500当时停报事实；用户授权JWT修复后，39d1b84及501–505验收见[JWT记录](R06_JWT_CLASSIFICATION_FIX_2026-09-28.md)，余7类506–512结果见§5。旧“未修/未启动/待决策”仅对应各节时间点，不恢复为接续指令。

## 1. 范围与输入

用户已接受D按已验范围交付，11c20e8已推送；R07抬头撤销仅记录，4197427已推送。本次A3输入为主树D:/acrylic-pda-outbound、codex/development、4197427，测试期间源码零改动。源码版本事实见[看板生成块](../ORCHESTRATION_STATUS.md)。

优先选择共享权限、来源候选及复核证据读取类。首批计划12类，复用tools/e2a-batch-it.ps1和-NonE2a，每类一个独立空库/账号/attempt。为确保逐类失败即停，忽略目录中的run-batch.ps1逐类调用原批次入口，非零退出即停止；未修改仓库工具、测试或生产代码，没有新增fixture。

执行前在34棵工作树.local目录查重496–543，无冲突；实际仅使用496–500。主树测试端口空闲，无其他活动测试写者，复用gitignored测试密钥。使用主树已提交源码；旧验证树D:/acrylic-pda-worktrees/r06-e2a-migration保持detached ea4d166及原有dirty/untracked，未同步、重置或提交。

清点136个非e2a源文件：既有批次报告中54类有非全跳过绿，82类缺少该入口的绿记录。82类包含R08专用入口已验类、单类已验记录、特殊环境与fixture类，**不是精确未跑总数**。更早旧schema XML不冒充本次共享迁移回归。统计局限见[工具自验](TEST_INFRA_SELFCHECK_2026-09-28.md)。

## 2. 首批结果

| attempt | 类 | tests / failures / errors / skipped | 人工分类 |
| --- | --- | --- | --- |
| 496 | DeviceTemporaryOrderIT | 7/0/0/0 | 通过：设备资格、权限与原事实保持 |
| 497 | UserTemporaryOrderIT | 7/0/0/0 | 通过：独立角色配置与授权范围 |
| 498 | TemporaryDuplicateAccessIT | 5/0/0/0 | 通过：候选、跨仓隔离与权限重验 |
| 499 | TemporaryReviewAccessIT | 9/0/0/0 | 通过：复核/提醒权限与来源可见性 |
| 500 | TemporarySourceEvidenceIntegrityIT | 5/1/0/0 | **真实业务分类缺陷，立即停报**；其余4项通过 |

合计5类、33项：32通过、1失败、0error、0skip；4类全绿，1类红。各XML证明迁移完成到源码所定义上限，连接均为127.0.0.1:19330/acrylic_e2a_cross_20260925_<attempt>。证据等级是**真实独立MySQL+Spring/MockMvc，不是外部真实HTTP**。非e2a不写e2a one-shot markers，started/completed=false不等于未执行；以新XML、用例数、迁移日志和退出码核验。

首批余7类未启动：SourceFoundationConcurrencyIT、TemporaryAcknowledgementIT、TemporaryPreparationIT、TemporarySubmissionTransportIT、DeviceTemporaryOrderAtomicityIT、PcConcurrencyIT、PcExceptionConcurrencyIT。第二批只准备14类manifest，未运行；不为完成计划越过真bug停报边界。

## 3. attempt500根因与影响

失败用例TemporarySourceEvidenceIntegrityIT.invalidStoredSignatureIsPreservedAsUnverifiedEvidence，第55行：合法来源ABORT后，把已存回执signedReceipt临时改为invalid.token.signature，再通过MockMvc读取证据；预期terminalProof.verification=INVALID_SIGNATURE，实际UNVERIFIABLE_KEY。

只读根因链：

1. [SourceEvidenceProof.verify](../../server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceProof.java) 第41行解码JWT头并调用SubmissionJson.parse；该三段样本的头部不是合法JSON。
2. [SubmissionJson.parse](../../server/src/main/java/com/acrylic/outbound/submissions/SubmissionJson.java) 第17–19行把JSON解析错误包装成BusinessException，code=INVALID_SUBMISSION。
3. SourceEvidenceProof第53行先捕获所有BusinessException并重抛，绕过返回INVALID_SIGNATURE的后续RuntimeException分支；read第29–30行又将所有BusinessException一律映射为UNVERIFIABLE_KEY。
4. 此路径发生在调用publicKeyFor之前，故不是隔离环境缺少密钥。实际IT失败与源码分支共同支持该判断，不能只凭工具自动分类D断定真bug。

影响为证据状态分类错误，把格式损坏误表示为密钥不可核验；本次没有返回VERIFIED，不声称发现信任放行或权限绕过。这属于R06已验证业务面，与D/R08及R07抬头撤销分开；未证明由共享表迁移引入。

同类其余4项损坏正文、缺文件、损坏分块、错来源正文断言通过。失败用例第55行后的原文保持/gaps/库事实断言未抵达，不声称通过；finally恢复原receipt_text。未改断言、未修代码、未重跑。

## 4. 证据、停工及接续

确认真bug后停止新开发和后续测试，仅保存记录。建议单独授权R06最小修复评估，区分解析/签名错误与真正的SIGNING_KEY_UNAVAILABLE，再复验该类并接续余7类；本记录不授权修复，不自动切R09 C2。

本机证据未提交：

- 主树.local/a3-continuation-20260928/：allocation-check.json、batch1-manifest.json、batch1-results.json（XML哈希）、batch1-console.log、run-batch.ps1、coverage-before.csv、历史清点及旧验证树前后status。batch2-manifest.json只是未运行计划。
- 主树.local/it-batches/下batch-20260928-122937-start496、123014-start497、123051-start498、123131-start499、123214-start500：各自REPORT.md、原日志及逐类XML。
- 主树.local/e2a-cross-20260925-496至500：隔离datadir、迁移/运行日志和storage。500失败原件保留；本轮无需清理datadir。

各harness均确认19330释放，收尾19330/19434/23308空闲，系统3306未动，无在途测试。旧验证树前后status逐字一致。实际使用号止于500，501及以后未启动，下一attempt501+须再查重；R08专用run32未使用。

未验：首批余7类和后续非e2a、IdentityLock/ledgerverify等专用环境、迁移/Android现场组合；没有全量IT通过结论。D范围外见[交付确认](R08_D_DELIVERY_ACCEPTANCE_2026-09-28.md)，抬头撤销见[R07 blocker](R07_3_HEADER_WITHDRAW_BLOCKER_2026-09-28.md)。不改业务代码/ConfirmedV1Adapter/契约/生产开关，不部署或合main。R06缺陷修复及R09 C2是否另立项交用户决策。

## 5. 授权修复后完成原首批余7类

用户已授权R06 JWT先定性再修，并在有余量时继续A3。定性a为4b25bca，最小修复39d1b84，验收记录7bded33；R09 C2独立范围84db51c。原500失败已由501的TemporarySourceEvidenceIntegrityIT六项绿复验，原红不覆盖。501–505是JWT定向/认证验收，不能沿用旧manifest中的同号分配。

此次续跑代码输入39d1b84，期间只有文档提交、源码/工具/fixture零改动。506–512执行前再次跨34棵工作树.local查重并确认端口无其他写者，使用新resume-manifest.json替代原计划号码。仍由忽略目录run-batch.ps1逐类调用e2a-batch-it.ps1/-NonE2a，每类一个独立空库/账号/attempt，任一非零退出即停。没有新增fixture。

| attempt | 类 | tests / failures / errors / skipped | 分类与覆盖 |
| --- | --- | --- | --- |
| 506 | SourceFoundationConcurrencyIT | 2/0/0/0 | 通过：合成来源写者与正式提交两种争锁次序；不代表生产来源接纳 |
| 507 | TemporaryAcknowledgementIT | 9/0/0/0 | 通过：激活事实、回执耐久/丢响应/重放及过期边界 |
| 508 | TemporaryPreparationIT | 5/0/0/0 | 通过：准备分片内容/摘要、固定授权和映射变化后原包重放 |
| 509 | TemporarySubmissionTransportIT | 9/0/0/0 | 通过：分块恢复、同源唯一落单、ABORT竞争、审计冲突回滚 |
| 510 | DeviceTemporaryOrderAtomicityIT | 3/0/0/0 | 通过：设备版本竞争、审计失败回滚、同键单回执 |
| 511 | PcConcurrencyIT | 3/0/0/0 | 通过：PDA/PC抢占、重复开工、跨订单包占用竞争 |
| 512 | PcExceptionConcurrencyIT | 3/0/0/0 | 通过：停止/记录竞争、交接重放、撤回审批/应用竞争 |

**本次续跑7类34项全绿，0失败/0error/0skip。** 原首批12类现均有绿色记录：496–499四类28项、501复验6项、506–512七类34项，合计68项（不同提交与批次的验收汇集，**不是同一次全批执行**）。历史500为5/1/0/0且永久保留；修复后测试增加1项，故与原测试数不同。JWT额外来源/认证四类不混入原12类计数。

证据仍为**真实独立MySQL + Spring/MockMvc，不是外部真实HTTP**；未跳过环境守卫，迁移到测试源码所定义上限，具体源码版本事实见看板。每个attempt的XML、迁移记录与退出码已逐项核对。非e2a started/completed=false是未写e2a one-shot marker，不是未执行。

506复用既有合成来源写者直接登记来源占用，真实事务/锁等待与正式提交竞争有效，但不能替代原身份/可信原件的生产来源接纳验收。其两份锁证据另存于.local/a3-continuation-20260928/source-concurrency-attempt506/，防止后续重跑覆盖。

## 6. 续跑证据与收尾

- .local/a3-continuation-20260928/resume-allocation-check.json、resume-manifest.json（测试源码SHA-256）、resume-console.log、resume-results.json（XML路径/哈希）保留完整分配与输出；旧manifest仅作历史计划，号码不得直接再用。
- .local/it-batches/batch-20260928-130005-start506、130039-start507、130115-start508、130149-start509、130232-start510、130305-start511、130339-start512保存逐类REPORT.md、日志/XML。
- .local/e2a-cross-20260925-506至512保存隔离库/日志/storage；历史500及JWT修复前单测两红原件保留。本次未清理失败证据。
- 旧验证树仍detached ea4d166，续跑前后97条dirty/untracked文本一致（resume-verification-status-after.txt比对原verification-status-after.txt，差异0）；未同步、重置、提交或合并旧树。

测试进程已结束，19330/19434/23308均空闲，系统3306未动，无在途重型任务。实际attempt用至512，后续513+执行前须再查重；R08专用run32未用。只有当前助手写入，最终未提交清单以Git状态核对。

本轮只完成原首批剩余7类，**未启动第二批**。后续普通非e2a类仍需分批；建议下一批从原已准备的UserPasswordPolicyIT、UserManagementConcurrencyIT、UserManagementRollbackIT、PairingApiIT、PairingConcurrencyIT、PdaPreparationIT及订单/发布/条码/Worker相关类中取12–15类，执行前按共享表风险和已有证据去重，重新分配attempt。IdentityLock/ledgerverify专用环境、Android/迁移组合及现场仍单列未验，缺报告绿记录的类数不等同精确未跑数。

原首批完成后交回用户决策：继续A3剩余回归或转其他方向。R09 C2只有范围登记、实施未启动；R07抬头撤销仍只记录。未改任何其他业务/测试代码、ConfirmedV1Adapter、契约或生产默认值/开关，未发布、部署或合main。
