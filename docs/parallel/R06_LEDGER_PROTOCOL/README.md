# 第六轮 C1b 实际回执独立消费者

> 2026-09-25后续：非零候选独立消费已扩展到21候选五页实际HTTP、完整上下文/游标/指纹重建及非零短装批准，attempt61 Python54/54。见[验收](../../history/R06_NONZERO_CANDIDATE_ACCEPTANCE_2026-09-25.md)。仍限首映射/首轮，普通确认拒绝未排除候选，生产能力保持关闭。

> 2026-09-25：新增真实内部决定消费者 `decision_consumer.py`；首映射/首轮/零候选的 NORMAL_CONFIRM、SHORT_APPROVE 和批准引用闭包在独立非空MySQL/真实HTTP attempt43中通过，Python46/46。详见[本次验收](../../history/R06_RECORDED_DECISION_CONSUMER_ACCEPTANCE_2026-09-25.md)。下文为旧骨架历史，旧固定版本的有效验收见[FINAL_REVIEW](FINAL_REVIEW.md)，其manifest与向量保持原样。新消费者无生产写权限或放行能力。

复现当前内部消费者：服务端树运行 `tools/e2a-cross-source-verify.ps1 -Run <未使用编号> -DecisionProtocol`；工具检查端口、独占新目录并认证shutdown。只消费保留向量时，显式设置 `R06_DECISION_VECTORS` 为该次 `decision-vectors.json`，主树运行 `C:/Python314/python.exe -W error::ResourceWarning -m unittest discover -s docs/parallel/R06_LEDGER_PROTOCOL -p "test_decision*.py" -v`。无变量时真实决定测试明确skip；不能把skip或保留向量离线消费写成新实库/HTTP验证。

W3 独立树 `codex/r06-ledger-protocol`，开发基线 `82a2e13cac306f877ed5e2c887970adf3d8e57f0`。当前是适配骨架与审查材料，**尚无固定生产提交、编译或动态测试结果**。代码暂对应 W1 已确认的候选 codec wire；源码固定后再运行并记录实际结果。

[验收矩阵](REVIEW_MATRIX.md) 和 [最小卡早审](IMPLEMENTATION_REVIEW.md) 定义边界。[可信样本](trusted_facts.py) 独立规定 Web/PDA 的 actor、原来源、原操作员、权限和版本，不从 Java 运行结果反推预期。四组正向样本覆盖无正文、仅 declared 无 received、已接收正文及短装合成命令；正常理由为空。它们只是传给 codec 的合成服务器上下文，不是真实数据库授权或业务决定。

[Java 适配器](LedgerProtocolDriver.java) 计划直接编译固定生产的 `CommandReceiptCodec.sign(CommandWrite)` 与 `CommandSignature.verify(...)`，不用替身。签名器临时生成 RSA 测试密钥，私钥仅在临时目录保留并清理；不读取业务配置或任何库/设备。运行器缺少 `source_manifest.json` 时拒绝执行，不能将未冻结源码计作最终验证。

[消费者](receipt_consumer.py) 分别校验原始 UTF-8/hash、RS256/kid/issuer/audience、签名内外一致、实际完整 wire 和独立可信预期。沿用第五轮独立 canonical 原语，只复用已审字节规则；新回执结构与语义另验。[负向样本](ledger_cases.py) 包含合法重签的错 actor/原人/设备/会话/权限/来源/版本、null 替换、错 receiptVersion 类型/溢出、漏字段及 currentEffect 混入原回执。

`CommandSignature` 本身仅承担密码学核验，因此合法 RSA 的错语义样本可通过该层，却必须被独立消费者拒绝。生产的服务器语义来源与 `CommandReceiptIntegrity` 还须由源码复审及 W2 的真实数据库测试证明；本目录不能用合成可信 facts 冒充数据库事实。历史权限/会话不要求仍 active 或等于当前值，当前读权由实时鉴权决定。

原 bytes 的变化与 currentEffect 的变化分别检查；未知历史 kid 不回退当前密钥。生产 handler 和 POST 保持关闭，合成回执必须是 `INTERNAL_TEST_ONLY`、`businessDecision=null`，所有能力 false。第五轮 233 项只算既有基础回归，不是本轮实际回执通过证据。无数据库、HTTP、Android 耐久或现场放行结论。
